package com.tradingmaster.pro.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.tradingmaster.pro.ProApp
import com.tradingmaster.pro.ProContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProSettingsUi(
    val email: String? = null,
    val accessStatus: String? = null,
    val subscriptionActive: Boolean = false,
    val subscriptionExpiresAt: String? = null,
    val deviceKey: String = "",
    val loading: Boolean = true
)

class ProSettingsViewModel(private val container: ProContainer) : ViewModel() {
    private val _state = MutableStateFlow(ProSettingsUi())
    val state: StateFlow<ProSettingsUi> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val access = try {
                container.supabase.rpc("get_my_access", "{\"app_key\":\"tm-pro\"}")
            } catch (e: Exception) { null }
            val obj = access?.let { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it.toString()) }.getOrNull() }
            _state.value = ProSettingsUi(
                email = container.session.userId,
                accessStatus = obj?.let { (it as? kotlinx.serialization.json.JsonObject)?.get("status")?.toString() },
                subscriptionActive = obj?.let { (it as? kotlinx.serialization.json.JsonObject)?.get("subscription_active")?.toString() == "\"true\"" } ?: false,
                deviceKey = container.deviceIdentity.stableKey,
                loading = false
            )
        }
    }

    fun signOut() {
        viewModelScope.launch { container.session.clear() }
    }
}

@Composable
fun SettingsScreen(nav: NavController) {
    val app = nav.context.applicationContext as ProApp
    val vm: ProSettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                ProSettingsViewModel(app.container) as T
        }
    )
    val state by vm.state.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Account", style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Email: ${state.email ?: "-"}")
                Text("Access status: ${state.accessStatus?.trim('"') ?: "-"}")
                Text("Subscription active: ${if (state.subscriptionActive) "yes" else "no"}")
            }
        }

        Text("Device", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Registered device key:",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    state.deviceKey.take(40) + "…",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "One approved device per account. Changing devices requires admin re-approval.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Button(
            onClick = { vm.signOut() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign out")
        }
    }
}
