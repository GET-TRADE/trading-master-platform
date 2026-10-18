package com.tradingmaster.pro.screens

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
import com.tradingmaster.pro.ProApp
import com.tradingmaster.pro.ProContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AuthUiState(
    val mode: Mode = Mode.LOGIN,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val needsEmailConfirm: Boolean = false
) {
    enum class Mode { LOGIN, SIGNUP, RESET }
}

class AuthViewModel(private val container: ProContainer) : ViewModel() {

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
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = res.error ?: "Login failed. Check your email and password."
                )
                return@launch
            }
            container.session.saveSession(res.accessToken!!, res.refreshToken, res.userId!!)

            // Register device with app_key tm-pro
            withContext(Dispatchers.IO) {
                runCatching {
                    container.supabase.rpc(
                        "register_device",
                        "{\"device_key\":\"${container.deviceIdentity.stableKey}\",\"app_key\":\"tm-pro\"}"
                    )
                }
            }

            // Check my access with app_key tm-pro
            val access = withContext(Dispatchers.IO) {
                runCatching {
                    container.supabase.rpc("get_my_access", "{\"app_key\":\"tm-pro\"}")
                }.getOrNull()
            }

            val status = access?.jsonObject?.get("status")?.jsonPrimitive?.content
            _ui.value = _ui.value.copy(busy = false)
            if (status.equals("approved", ignoreCase = true)) onApprovedRoute() else onPendingRoute()
        }
    }

    fun signup(email: String, password: String, whatsapp: String) {
        if (email.isBlank() || password.length < 8 || whatsapp.isBlank()) {
            _ui.value = _ui.value.copy(
                error = "Email, WhatsApp number and a password of at least 8 characters are required."
            )
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, info = null)
            val res = withContext(Dispatchers.IO) {
                container.supabase.signUp(
                    email.trim(),
                    password,
                    whatsapp.trim(),
                    container.backend.appKey
                )
            }
            if (res.success) {
                container.session.saveSession(res.accessToken!!, res.refreshToken, res.userId!!)
                withContext(Dispatchers.IO) {
                    runCatching {
                        container.supabase.rpc(
                            "register_device",
                            "{\"device_key\":\"${container.deviceIdentity.stableKey}\",\"app_key\":\"tm-pro\"}"
                        )
                    }
                }
                _ui.value = _ui.value.copy(
                    busy = false,
                    needsEmailConfirm = true,
                    info = "Registration received. Your account starts PENDING admin approval. Approvals are handled by the administrator; registration alone does not grant trading access."
                )
            } else {
                _ui.value = _ui.value.copy(
                    busy = false,
                    info = if (res.needsEmailConfirmation) "Check your inbox to confirm your email, then wait for admin approval." else null,
                    error = res.error
                )
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
            _ui.value = _ui.value.copy(
                busy = false,
                info = if (ok) "If that email is registered, a password-reset link has been sent." else "Could not send the reset email. Check your connection and try again."
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val c: ProContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthViewModel(c) as T
    }
}

@Composable
fun AuthScreen(nav: NavController) {
    val app = nav.context.applicationContext as ProApp
    val vm: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = AuthViewModel.Factory(app.container)
    )
    val ui by vm.ui.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Trading Master Pro",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = when (ui.mode) {
                AuthUiState.Mode.LOGIN -> "Log in to Pro Terminal"
                AuthUiState.Mode.SIGNUP -> "Create Pro Account"
                AuthUiState.Mode.RESET -> "Reset Password"
            },
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        if (ui.mode != AuthUiState.Mode.RESET) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
        }

        if (ui.mode == AuthUiState.Mode.SIGNUP) {
            OutlinedTextField(
                value = whatsapp,
                onValueChange = { whatsapp = it },
                label = { Text("WhatsApp number (with country code)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = "Notice: Account starts PENDING admin approval upon registration.",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (ui.error != null) {
            Text(
                text = ui.error!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp
            )
        }

        if (ui.info != null) {
            Text(
                text = ui.info!!,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 13.sp
            )
        }

        Button(
            onClick = {
                when (ui.mode) {
                    AuthUiState.Mode.LOGIN -> vm.login(
                        email, password,
                        onApprovedRoute = {
                            nav.navigate("home") { popUpTo(0) { inclusive = true } }
                        },
                        onPendingRoute = {
                            nav.navigate("pending") { popUpTo(0) { inclusive = true } }
                        }
                    )
                    AuthUiState.Mode.SIGNUP -> vm.signup(email, password, whatsapp)
                    AuthUiState.Mode.RESET -> vm.resetPassword(email)
                }
            },
            enabled = !ui.busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (ui.busy) "Processing..." else when (ui.mode) {
                    AuthUiState.Mode.LOGIN -> "Log in"
                    AuthUiState.Mode.SIGNUP -> "Register account"
                    AuthUiState.Mode.RESET -> "Send reset link"
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = {
                    vm.setMode(
                        if (ui.mode == AuthUiState.Mode.LOGIN) AuthUiState.Mode.SIGNUP
                        else AuthUiState.Mode.LOGIN
                    )
                }
            ) {
                Text(
                    if (ui.mode == AuthUiState.Mode.LOGIN) "Need an account? Register"
                    else "Have an account? Log in"
                )
            }

            if (ui.mode == AuthUiState.Mode.LOGIN) {
                TextButton(onClick = { vm.setMode(AuthUiState.Mode.RESET) }) {
                    Text("Forgot password?")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
