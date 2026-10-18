package com.tradingmaster.app.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.tradingmaster.app.TradingMasterApp
import com.tradingmaster.core.binance.ScannerService
import com.tradingmaster.core.model.ScanResult
import com.tradingmaster.core.model.ScanUniverse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ScannerViewModel(private val container: com.tradingmaster.app.AppContainer) : ViewModel() {

    data class ScannerUi(
        val busy: Boolean = false,
        val signals: List<ScanResult> = emptyList(),
        val scannedCount: Int = 0,
        val errors: List<String> = emptyList(),
        val manualSymbol: String = "",
        val lastRunAt: Long? = null
    )

    private val _ui = MutableStateFlow(ScannerUi())
    val ui: StateFlow<ScannerUi> = _ui
    private val json = Json { ignoreUnknownKeys = true }

    fun setManualSymbol(s: String) { _ui.value = _ui.value.copy(manualSymbol = s.uppercase().trim()) }

    /** Selected signal payloads shared with the detail screen (in-memory for this session). */
    val selectedSignals = mutableMapOf<String, ScanResult>()

    fun runScan(manualOnly: Boolean) {
        val manual = if (manualOnly && _ui.value.manualSymbol.isNotBlank()) _ui.value.manualSymbol else null
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, errors = emptyList())
            try {
                val universe = ScanUniverse(manualSymbol = manual)
                val summary = withContext(Dispatchers.IO) {
                    container.scannerService.scanUniverse(universe)
                }
                // Persist the run and every trade signal to local history.
                withContext(Dispatchers.IO) {
                    val db = container.db
                    val runId = db.scanRunDao().insert(
                        com.tradingmaster.core.store.ScanRunEntity(
                            startedAt = System.currentTimeMillis(),
                            marketsScanned = summary.universeSize,
                            signalsFound = summary.signals.size
                        )
                    )
                    summary.signals.forEach { r ->
                        db.signalHistoryDao().insert(
                            com.tradingmaster.core.store.SignalHistoryEntity(
                                symbol = r.symbol,
                                generatedAt = r.generatedAt,
                                isTrade = true,
                                payloadJson = json.encodeToString(r)
                            )
                        )
                    }
                    db.signalHistoryDao().prune(System.currentTimeMillis() - 30L * 24 * 3600 * 1000)
                }
                _ui.value = _ui.value.copy(
                    busy = false,
                    signals = summary.signals,
                    scannedCount = summary.universeSize,
                    errors = summary.errors.map { "${it.symbol}: ${it.message}" },
                    lastRunAt = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(busy = false,
                    errors = listOf("Scan failed: ${e.message}. Check your connection. NO TRADE is assumed when data is unavailable."))
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val c: com.tradingmaster.app.AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ScannerViewModel(c) as T
    }
}

@Composable
fun ScannerScreen(nav: NavController) {
    val app = nav.context.applicationContext as TradingMasterApp
    val vm: ScannerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = ScannerViewModel.Factory(app.container))
    val ui by vm.ui.collectAsState()

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Futures scanner", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("USDT perpetuals • closed 5m/15m/1h candles • min $5M turnover • up to 100 markets",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)

        OutlinedTextField(
            value = ui.manualSymbol,
            onValueChange = { vm.setManualSymbol(it) },
            label = { Text("Scan a single symbol (e.g. BTCUSDT)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.runScan(false) }, enabled = !ui.busy, modifier = Modifier.weight(1f)) {
                Text(if (ui.busy) "Scanning..." else "Scan markets")
            }
            OutlinedButton(onClick = { vm.runScan(true) }, enabled = !ui.busy && ui.manualSymbol.isNotBlank(),
                modifier = Modifier.weight(1f)) { Text("Scan symbol") }
        }

        if (ui.errors.isNotEmpty()) {
            Text("Errors: ${ui.errors.size}", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            ui.errors.take(3).forEach { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.error) }
        }
        Text("Scanned ${ui.scannedCount} • signals ${ui.signals.size} • NO TRADE is a valid result",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ui.signals, key = { it.symbol }) { r ->
                val s = r.signal!!
                Card(modifier = Modifier.fillMaxWidth().clickable {
                    vm.selectedSignals[r.symbol] = r
                    nav.navigate("signal/${r.symbol}")
                }) {
                    Column(Modifier.padding(10.dp)) {
                        Text("${s.symbol}  ${s.direction}", fontWeight = FontWeight.Bold)
                        Text("Score ${s.score}/5 • entry ${"%.6f".format(s.entryLow)}–${"%.6f".format(s.entryHigh)}", fontSize = 12.sp)
                        Text("Stop ${"%.6f".format(s.stopLoss)} • TP1 ${"%.6f".format(s.tp1)} (R:R ${s.rr1})", fontSize = 12.sp)
                    }
                }
            }
            if (!ui.busy && ui.signals.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Text("NO TRADE on the scanned universe. Thresholds are never lowered to force signals.",
                            Modifier.padding(12.dp), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
