package com.tradingmaster.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.tradingmaster.app.TradingMasterApp
import com.tradingmaster.core.binance.BinanceClientFactory
import com.tradingmaster.core.binance.ConnectionTester
import com.tradingmaster.core.binance.ConnectionTestReport
import com.tradingmaster.core.model.RiskConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val container: com.tradingmaster.app.AppContainer) : ViewModel() {

    data class SettingsUi(
        val hasBinanceKeys: Boolean = false,
        val busy: Boolean = false,
        val testReport: ConnectionTestReport? = null,
        val risk: RiskConfig = RiskConfig(),
        val error: String? = null,
        val info: String? = null
    )

    private val _ui = MutableStateFlow(SettingsUi())
    val ui: StateFlow<SettingsUi> = _ui

    init { refresh() }

    fun refresh() {
        _ui.value = _ui.value.copy(hasBinanceKeys = container.credentials.hasBinanceCredentials())
    }

    fun saveBinanceKeys(apiKey: String, apiSecret: String) {
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            _ui.value = _ui.value.copy(error = "Both the API key and secret are required.")
            return
        }
        container.credentials.saveBinanceCredentials(apiKey.trim(), apiSecret.trim())
        _ui.value = _ui.value.copy(hasBinanceKeys = true, error = null,
            info = "Credentials saved to the Android Keystore. Keys are never sent to the AI provider or stored in the database.")
        refresh()
    }

    fun clearBinanceKeys() {
        container.credentials.clearBinanceCredentials()
        _ui.value = SettingsUi() // resets report too; credentials gone
    }

    /** Staged connection test; each stage is reported separately. */
    fun testConnection() {
        if (!container.credentials.hasBinanceCredentials()) {
            _ui.value = _ui.value.copy(error = "Save your Binance API key and secret first.")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, testReport = null, error = null)
            try {
                val report = withContext(Dispatchers.IO) {
                    val (futuresApi, client) = BinanceClientFactory.signedClient(
                        apiKeyProvider = { container.credentials.binanceApiKey() ?: "" },
                        secretProvider = { container.credentials.binanceApiSecret() ?: "" },
                        timeSync = container.binance.timeSync
                    )
                    ConnectionTester(container.binance.publicApi, futuresApi, container.binance.timeSync).test()
                }
                _ui.value = _ui.value.copy(busy = false, testReport = report)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(busy = false, error = "Connection test failed: ${e.message}")
            }
        }
    }

    fun setEmergencyStop(on: Boolean) {
        val r = _ui.value.risk.copy(emergencyStop = on)
        container.session.saveRiskConfig(r)
        _ui.value = _ui.value.copy(risk = r,
            info = if (on) "EMERGENCY STOP ON: new automatic entries are blocked. Existing positions and exchange-side orders are NOT closed automatically."
                   else "Emergency stop cleared.")
    }

    fun saveRisk(notional: String, leverage: String, maxPositions: String) {
        val n = notional.toDoubleOrNull()
        val lev = leverage.toIntOrNull()
        val mp = maxPositions.toIntOrNull()
        if (n == null || n <= 0 || lev == null || lev !in 1..125 || mp == null || mp !in 1..10) {
            _ui.value = _ui.value.copy(error = "Invalid risk values. Notional must be positive USDT; leverage 1-125; max positions 1-10.")
            return
        }
        val r = _ui.value.risk.copy(targetNotionalUsdt = n, preferredLeverage = lev, maxSimultaneousPositions = mp)
        container.session.saveRiskConfig(r)
        _ui.value = _ui.value.copy(risk = r, error = null,
            info = "Saved. Live trading remains OFF by default and is never armed by an update.")
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { container.supabase.signOut() } }
            container.session.clear()
            withContext(Dispatchers.IO) { runCatching { container.supabase.clearSession() } }
            onSignedOut()
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val c: com.tradingmaster.app.AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(c) as T
    }
}

@Composable
fun SettingsScreen(nav: NavController) {
    val app = nav.context.applicationContext as TradingMasterApp
    val vm: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = SettingsViewModel.Factory(app.container))
    val ui by vm.ui.collectAsState()
    var apiKey by remember { mutableStateOf("") }
    var apiSecret by remember { mutableStateOf("") }
    var notional by remember(ui.risk.targetNotionalUsdt) { mutableStateOf(ui.risk.targetNotionalUsdt.toString()) }
    var leverage by remember(ui.risk.preferredLeverage) { mutableStateOf(ui.risk.preferredLeverage.toString()) }
    var maxPositions by remember(ui.risk.maxSimultaneousPositions) { mutableStateOf(ui.risk.maxSimultaneousPositions.toString()) }

    Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold)

        // ---------- Binance credentials ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Binance API connection", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(if (ui.hasBinanceKeys) "Credentials saved (Keystore-encrypted)."
                    else "No credentials saved. Public scanning works without them.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it },
                    label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = apiSecret, onValueChange = { apiSecret = it },
                    label = { Text("API secret") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.saveBinanceKeys(apiKey, apiSecret); apiKey = ""; apiSecret = "" },
                        modifier = Modifier.weight(1f)) { Text("Save / replace") }
                    OutlinedButton(onClick = { vm.testConnection() }, enabled = ui.hasBinanceKeys && !ui.busy,
                        modifier = Modifier.weight(1f)) { Text(if (ui.busy) "Testing..." else "Test connection") }
                }
                if (ui.hasBinanceKeys) {
                    TextButton(onClick = { vm.clearBinanceKeys() }) { Text("Remove credentials") }
                }
                Text("Withdrawal permissions must never be enabled on this key. Restrict the key to your IP if possible. Credentials never leave this device except to Binance directly.",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)

                ui.testReport?.let { report ->
                    Spacer(Modifier.height(4.dp))
                    report.stages.forEach { stage ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${stage.name}: ${if (stage.passed) "PASS" else "FAIL"}",
                                fontSize = 12.sp,
                                color = if (stage.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                        Text(stage.detail, fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    if (!report.passedAll) {
                        Text("Public market data working does NOT mean authenticated Futures access works. Fix the failing stage above.",
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // ---------- Risk management ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Risk management", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = notional, onValueChange = { notional = it },
                        label = { Text("Notional USDT") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = leverage, onValueChange = { leverage = it },
                        label = { Text("Leverage") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = maxPositions, onValueChange = { maxPositions = it },
                        label = { Text("Max positions") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Button(onClick = { vm.saveRisk(notional, leverage, maxPositions) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Save risk settings")
                }
                Text("Notional is the order size, NOT your maximum loss. Actual loss depends on stop distance, leverage and quantity. Multi-assets mode only; isolated margin is not exposed.",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
            }
        }

        // ---------- Emergency stop & live trading ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Trading controls", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Emergency stop (blocks new entries)", fontSize = 12.sp)
                    Switch(checked = ui.risk.emergencyStop, onCheckedChange = { vm.setEmergencyStop(it) })
                }
                Text("The emergency stop does not close existing positions or cancel exchange-side orders.",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                Text("Live trading: OFF by default. Background scanning notifies only — it never places orders. Live order execution is not enabled in this version.",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
            }
        }

        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        ui.info?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }

        // ---------- Account ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Account", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Device ID: ${app.container.deviceIdentity.installationId.take(12)}…", fontSize = 11.sp)
                Text("One approved device per user. A device change requires administrator approval.",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                OutlinedButton(onClick = {
                    vm.signOut { nav.navigate("auth") { popUpTo(0) { inclusive = true } } }
                }, modifier = Modifier.fillMaxWidth()) { Text("Log out") }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
