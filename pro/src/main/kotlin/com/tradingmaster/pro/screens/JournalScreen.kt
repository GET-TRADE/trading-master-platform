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
import com.tradingmaster.core.model.PaperStatus
import com.tradingmaster.core.model.PaperTrade
import com.tradingmaster.pro.ProApp
import com.tradingmaster.pro.ProContainer
import com.tradingmaster.pro.model.PaperMetrics
import com.tradingmaster.pro.model.toModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JournalViewModel(private val container: ProContainer) : ViewModel() {

    data class JournalUiState(
        val busy: Boolean = false,
        val trades: List<PaperTrade> = emptyList(),
        val metrics: PaperMetrics = PaperMetrics()
    )

    private val _ui = MutableStateFlow(JournalUiState())
    val ui: StateFlow<JournalUiState> = _ui

    init {
        loadTrades()
    }

    fun loadTrades() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            val list = withContext(Dispatchers.IO) {
                container.database.paperTradeDao().all(300).map { it.toModel() }
            }
            val computed = PaperMetrics.compute(list)
            _ui.value = JournalUiState(busy = false, trades = list, metrics = computed)
        }
    }

    fun clearAllTrades() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            withContext(Dispatchers.IO) {
                container.database.paperTradeDao().clearAll()
            }
            loadTrades()
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val c: ProContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = JournalViewModel(c) as T
    }
}

@Composable
fun JournalScreen(nav: NavController) {
    val app = nav.context.applicationContext as ProApp
    val vm: JournalViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = JournalViewModel.Factory(app.container)
    )
    val ui by vm.ui.collectAsState()
    val m = ui.metrics

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Paper Trading Journal", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        // Summary Card with PaperMetrics
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Aggregate Metrics (Paper Metrics)", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Trades: ${m.totalTrades} (${m.openTrades} open / ${m.closedTrades} closed)", fontSize = 12.sp)
                    Text(
                        "Win Rate: ${"%.2f".format(m.winRatePct)}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Wins / Losses: ${m.wins} W / ${m.losses} L", fontSize = 12.sp)
                    Text(
                        "Net PnL: ${"%.4f".format(m.totalNetPnlUsdt)} USDT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (m.totalNetPnlUsdt >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Simulated Fees: ${"%.4f".format(m.totalFeesUsdt)} USDT", fontSize = 12.sp)
                    Text("Expectancy: ${"%.4f".format(m.expectancyUsdt)} USDT", fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Avg Win: ${"%.4f".format(m.avgWinUsdt)} USDT", fontSize = 12.sp)
                    Text("Avg Loss: ${"%.4f".format(m.avgLossUsdt)} USDT", fontSize = 12.sp)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = { vm.loadTrades() }, enabled = !ui.busy) {
                Text("Refresh")
            }
            OutlinedButton(
                onClick = { vm.clearAllTrades() },
                enabled = !ui.busy && ui.trades.isNotEmpty()
            ) {
                Text("Clear History")
            }
        }

        // List of Trades
        Text("Recorded Simulated Positions", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        if (ui.trades.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "No paper trades found. Scan the market and open a paper trade from any signal detail view.",
                    modifier = Modifier.padding(14.dp),
                    fontSize = 12.sp
                )
            }
        } else {
            ui.trades.forEach { t ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${t.symbol} ${t.direction}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                "Status: ${t.status}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (t.status == PaperStatus.OPEN) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            "Entry ${"%.6f".format(t.entryPrice)} • Stop ${"%.6f".format(t.stopLoss)} • TP1 ${"%.6f".format(t.tp1)}",
                            fontSize = 11.sp
                        )

                        if (t.status == PaperStatus.CLOSED) {
                            Text(
                                "Exit ${t.exitPrice?.let { "%.6f".format(it) } ?: "-"} • Net PnL ${"%.4f".format(t.realizedPnlUsdt ?: 0.0)} USDT (${t.closeReason ?: "CLOSED"})",
                                fontSize = 11.sp,
                                color = if ((t.realizedPnlUsdt ?: 0.0) >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text("Open position • Partial exits: ${t.partialExits}", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
