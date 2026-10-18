package com.tradingmaster.pro.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.tradingmaster.core.backtest.BacktestEngine
import com.tradingmaster.core.binance.BinanceClientFactory
import com.tradingmaster.core.model.BacktestReport
import com.tradingmaster.core.model.Timeframe
import com.tradingmaster.pro.ProApp
import com.tradingmaster.pro.ProContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BacktestViewModel(private val container: ProContainer) : ViewModel() {

    data class BacktestUiState(
        val symbol: String = "BTCUSDT",
        val busy: Boolean = false,
        val report: BacktestReport? = null,
        val error: String? = null
    )

    private val _ui = MutableStateFlow(BacktestUiState())
    val ui: StateFlow<BacktestUiState> = _ui

    fun setSymbol(s: String) {
        _ui.value = _ui.value.copy(symbol = s.uppercase().trim())
    }

    fun runBacktest() {
        val sym = _ui.value.symbol
        if (sym.isBlank()) {
            _ui.value = _ui.value.copy(error = "Enter a valid trading symbol.")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, report = null)
            try {
                val report = withContext(Dispatchers.IO) {
                    val api = container.binanceApi
                    val m5 = BinanceClientFactory.klines(api, sym, Timeframe.M5, 400)
                    val m15 = BinanceClientFactory.klines(api, sym, Timeframe.M15, 140)
                    val h1 = BinanceClientFactory.klines(api, sym, Timeframe.H1, 80)

                    val engine = container.backtestEngine
                    engine.run(sym, BacktestEngine.Window(m5, m15, h1))
                }
                _ui.value = _ui.value.copy(busy = false, report = report)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = "Backtest failed for $sym: ${e.message}. Verify symbol and network."
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val c: ProContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BacktestViewModel(c) as T
    }
}

@Composable
fun BacktestScreen(nav: NavController) {
    val app = nav.context.applicationContext as ProApp
    val vm: BacktestViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = BacktestViewModel.Factory(app.container)
    )
    val ui by vm.ui.collectAsState()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Chronological Backtester", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Strict no-lookahead backtest • entry on next candle • adverse slippage & taker fees",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.secondary
        )

        OutlinedTextField(
            value = ui.symbol,
            onValueChange = { vm.setSymbol(it) },
            label = { Text("Symbol (e.g. BTCUSDT, ETHUSDT)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { vm.runBacktest() },
            enabled = !ui.busy && ui.symbol.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (ui.busy) "Running backtest..." else "Run Backtest")
        }

        if (ui.busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (ui.error != null) {
            Text(
                ui.error!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp
            )
        }

        val report = ui.report
        if (report != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Backtest Results (${report.symbol})", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Trades Run: ${report.trades}", fontSize = 12.sp)
                        Text(
                            "Win Rate: ${"%.2f".format(report.winRatePct)}% (${report.wins}W / ${report.losses}L)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Gross PnL: ${"%.4f".format(report.grossPnlUsdt)} USDT", fontSize = 12.sp)
                        Text(
                            "Net PnL: ${"%.4f".format(report.netPnlUsdt)} USDT",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (report.netPnlUsdt >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Fees Paid: ${"%.4f".format(report.feesUsdt)} USDT", fontSize = 12.sp)
                        val pfStr = when {
                            report.profitFactor == null -> "—"
                            report.profitFactor!!.isInfinite() -> "∞"
                            else -> "%.2f".format(report.profitFactor)
                        }
                        Text("Profit Factor: $pfStr", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Expectancy: ${"%.4f".format(report.expectancyPerTradeUsdt)} USDT/trade", fontSize = 12.sp)
                        Text("Max Drawdown: ${"%.4f".format(report.maxDrawdownUsdt)} USDT", fontSize = 12.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Avg Win: ${"%.4f".format(report.avgWinUsdt)} USDT", fontSize = 12.sp)
                        Text("Avg Loss: ${"%.4f".format(report.avgLossUsdt)} USDT", fontSize = 12.sp)
                    }

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        "Data Coverage: ${dateFormat.format(Date(report.dataFromMs))} to ${dateFormat.format(Date(report.dataToMs))}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Disclosed Limitations List VERBATIM
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Disclosed Limitations", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    report.limitations.forEach { limitation ->
                        Text("• $limitation", fontSize = 11.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
