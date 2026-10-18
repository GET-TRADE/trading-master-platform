package com.tradingmaster.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.tradingmaster.app.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.withContext

data class AuthUiState(
    val mode: Mode = Mode.LOGIN,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val needsEmailConfirm: Boolean = false
) {
    enum class Mode { LOGIN, SIGNUP, RESET }
}

class AuthViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui

    fun setMode(mode: AuthUiState.Mode) {
        _ui.value = AuthUiState(mode = mode)
    }

    fun login(email: String, password: String, onApprovedRoute: () -> Unit, onPendingRoute: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            _ui.value = _ui.value.copy(error = "Enter your email and password.")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, info = null)
            val res = withContext(Dispatchers.IO) {
                container.supabase.signIn(email.trim(), password)
            }
            if (!res.success) {
                _ui.value = _ui.value.copy(busy = false, error = res.error ?: "Login failed. Check your email and password.")
                return@launch
            }
            container.session.saveSession(res.accessToken!!, res.refreshToken, res.userId!!)
            // Re-login on the same approved device must not need a new approval.
            val deviceRes = withContext(Dispatchers.IO) {
                container.supabase.rpc("register_device",
                    "{\"device_key\":\"${container.deviceIdentity.stableKey}\",\"app_key\":\"tm-user\"}")
            }
            val access = withContext(Dispatchers.IO) {
                container.supabase.rpc("get_my_access", "{\"app_key\":\"tm-user\"}")
            }
            val status = access.jsonObject["status"]?.jsonPrimitive?.content
            _ui.value = _ui.value.copy(busy = false)
            if (status == "approved") onApprovedRoute() else onPendingRoute()
        }
    }

    fun signup(email: String, password: String, whatsapp: String) {
        if (email.isBlank() || password.length < 8 || whatsapp.isBlank()) {
            _ui.value = _ui.value.copy(error = "Email, WhatsApp number and a password of at least 8 characters are required.")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, info = null)
            val res = withContext(Dispatchers.IO) {
                container.supabase.signUp(email.trim(), password, whatsapp.trim(), container.backend.appKey)
            }
            if (res.success) {
                container.session.saveSession(res.accessToken!!, res.refreshToken, res.userId!!)
                withContext(Dispatchers.IO) {
                    runCatching {
                        container.supabase.rpc("register_device",
                            "{\"device_key\":\"${container.deviceIdentity.stableKey}\",\"app_key\":\"tm-user\"}")
                    }
                }
                _ui.value = _ui.value.copy(busy = false, needsEmailConfirm = true,
                    info = "Registration received. Your account is PENDING admin approval. Approvals are handled by the administrator; registration alone does not grant trading access.")
            } else {
                _ui.value = _ui.value.copy(busy = false,
                    info = if (res.needsEmailConfirmation) "Check your inbox to confirm your email, then wait for admin approval." else null,
                    error = res.error)
            }
        }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) {
            _ui.value = _ui.value.copy(error = "Enter the email you registered with.")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, info = null)
            val ok = withContext(Dispatchers.IO) { container.supabase.requestPasswordReset(email.trim()) }
            _ui.value = _ui.value.copy(busy = false,
                info = if (ok) "If that email is registered, a password-reset link has been sent." else "Could not send the reset email. Check your connection and try again.")
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthViewModel(c) as T
    }
}

@Composable
fun AuthScreen(nav: NavController) {
    // Container is provided via the activity's local composition in production wiring.
    val app = nav.context.applicationContext as com.tradingmaster.app.TradingMasterApp
    val vm: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = AuthViewModel.Factory(app.container))
    val ui by vm.ui.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Trading Master", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Binance USDⓈ-M Futures scanner", fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        TabRow(selectedTabIndex = ui.mode.ordinal) {
            AuthUiState.Mode.values().forEach { m ->
                Tab(selected = ui.mode == m, onClick = { vm.setMode(m) },
                    text = { Text(when (m) { AuthUiState.Mode.LOGIN -> "Login"; AuthUiState.Mode.SIGNUP -> "Sign up"; AuthUiState.Mode.RESET -> "Reset" }) })
            }
        }

        OutlinedTextField(value = email, onValueChange = { email = it },
            label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth())

        if (ui.mode != AuthUiState.Mode.RESET) {
            OutlinedTextField(value = password, onValueChange = { password = it },
                label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth())
        }
        if (ui.mode == AuthUiState.Mode.SIGNUP) {
            OutlinedTextField(value = whatsapp, onValueChange = { whatsapp = it },
                label = { Text("WhatsApp number (for admin contact)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth())
            Text("Your account starts PENDING and must be approved by an administrator.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        }

        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        ui.info?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) }

        Button(
            onClick = {
                when (ui.mode) {
                    AuthUiState.Mode.LOGIN -> vm.login(email, password, { nav.navigate("home") { popUpTo("auth") { inclusive = true } } },
                        { nav.navigate("pending") { popUpTo("auth") { inclusive = true } } })
                    AuthUiState.Mode.SIGNUP -> vm.signup(email, password, whatsapp)
                    AuthUiState.Mode.RESET -> vm.resetPassword(email)
                }
            },
            enabled = !ui.busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(when (ui.mode) { AuthUiState.Mode.LOGIN -> "Login"; AuthUiState.Mode.SIGNUP -> "Create account"; AuthUiState.Mode.RESET -> "Send reset link" })
        }
        if (ui.mode == AuthUiState.Mode.SIGNUP && ui.needsEmailConfirm) {
            Button(onClick = { vm.setMode(AuthUiState.Mode.LOGIN) }, modifier = Modifier.fillMaxWidth()) {
                Text("Go to login")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
