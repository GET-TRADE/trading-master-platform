package com.tradingmaster.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tradingmaster.app.TradingMasterApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Pending / rejected / blocked account screen. Polls the server; never lets the user through. */
class PendingViewModel(private val container: AppContainer) : ViewModel() {

    data class PendingUi(val status: String? = null, val subscriptionActive: Boolean = false, val checking: Boolean = false)

    private val _ui = MutableStateFlow(PendingUi(checking = true))
    val ui: StateFlow<PendingUi> = _ui

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(checking = true)
            val access = try {
                withContext(Dispatchers.IO) { container.supabase.rpc("get_my_access", "{\"app_key\":\"tm-user\"}") }
            } catch (e: Exception) { null }
            if (access == null) {
                _ui.value = PendingUi(status = null, checking = false)
                return@launch
            }
            val status = access.jsonObject["status"]?.jsonPrimitive?.content
            val subActive = access.jsonObject["subscription_active"]?.jsonPrimitive?.content == "true"
            _ui.value = PendingUi(status = status, subscriptionActive = subActive, checking = false)
        }
    }

    fun signOut() {
        viewModelScope.launch { container.session.clear(); }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PendingViewModel(c) as T
    }
}

@Composable
fun PendingApprovalScreen(nav: NavController) {
    val app = nav.context.applicationContext as TradingMasterApp
    val vm: PendingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = PendingViewModel.Factory(app.container))
    val ui by vm.ui.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Account review", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        when (ui.status) {
            "blocked" -> Text("Your account has been blocked by an administrator.", color = MaterialTheme.colorScheme.error)
            "rejected" -> Text("Your signup was rejected. Contact the administrator for details.", color = MaterialTheme.colorScheme.error)
            else -> Text("Your account is PENDING approval. The administrator reviews new signups; registration alone does not grant trading access. You will be notified via WhatsApp.")
        }
        if (ui.status == "approved" && !ui.subscriptionActive) {
            Text("Your account is approved, but no active subscription was found. Log out and log back in to refresh.")
        }
        Text("Status: ${ui.status ?: "unavailable (check connection)"}", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
        Button(onClick = { vm.refresh() }, enabled = !ui.checking) { Text(if (ui.checking) "Checking..." else "Refresh status") }
        OutlinedButton(onClick = {
            vm.signOut()
            nav.navigate("auth") { popUpTo(0) { inclusive = true } }
        }) { Text("Log out") }
        Spacer(Modifier.height(24.dp))
    }
}
