package com.tradingmaster.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tradingmaster.app.TradingMasterApp
import com.tradingmaster.core.model.PaperSimAssumptions
import com.tradingmaster.core.model.PaperStatus
import com.tradingmaster.core.model.PaperTrade
import com.tradingmaster.core.paper.PaperTradingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Paper-trading journal. Fully simulated: no exchange orders are ever sent,
 * and no Binance keys are required. Fees and adverse slippage are simulated;
 * funding is NOT modeled and that limitation is shown.
 */
@Composable
fun PaperJournalScreen(nav: NavController) {
    val app = nav.context.applicationContext as TradingMasterApp
    val trades = remember { mutableStateOf<List<PaperTrade>>(emptyList()) }

    suspend fun load(): List<PaperTrade> = withContext(Dispatchers.IO) {
        val dao = app.container.db.paperTradeDao()
        dao.all(300).map { it.toModel() }
    }

    LaunchedEffect(Unit) { trades.value = load() }

    val closed = trades.value.filter { it.status == PaperStatus.CLOSED }
    val wins = closed.count { (it.realizedPnlUsdt ?: 0.0) > 0 }
    val net = closed.sumOf { it.realizedPnlUsdt ?: 0.0 }
    val fees = closed.sumOf { it.feesUsdt }

    Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Paper trading journal", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Closed: ${closed.size} • Win rate: ${if (closed.isEmpty()) "—" else "${100.0 * wins / closed.size}%"}")
                Text("Net PnL (incl. simulated fees & slippage): ${"%.4f".format(net)} USDT")
                Text("Simulated fees: ${"%.4f".format(fees)} USDT", fontSize = 11.sp)
                Text("Model: 0.05% taker fee/side, 0.03% adverse slippage/side. Funding, liquidation, spread and exact fills are NOT modeled. Paper results are not proof of profitability.",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
            }
        }
        trades.value.forEach { t ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${t.symbol} ${t.direction} • ${t.status}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Entry ${"%.6f".format(t.entryPrice)} • Stop ${"%.6f".format(t.stopLoss)}", fontSize = 11.sp)
                    if (t.status == PaperStatus.CLOSED) {
                        Text("Exit ${t.exitPrice?.let { "%.6f".format(it) } ?: "-"} • PnL ${"%.4f".format(t.realizedPnlUsdt ?: 0.0)} USDT (${t.closeReason})",
                            fontSize = 11.sp,
                            color = if ((t.realizedPnlUsdt ?: 0.0) >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    } else {
                        Text("Open • partial exits ${t.partialExits}", fontSize = 11.sp)
                    }
                }
            }
        }
        if (trades.value.isEmpty()) {
            Text("No paper trades yet. Run a scan and open a paper trade from a signal.", fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Opens a paper trade from a scanned signal and persists it. */
suspend fun openPaperTradeFromSignal(app: TradingMasterApp, signal: com.tradingmaster.core.model.TradeSignal) {
    withContext(Dispatchers.IO) {
        val engine = PaperTradingEngine(PaperSimAssumptions())
        val t = engine.open(signal, System.currentTimeMillis())
        app.container.db.paperTradeDao().insert(t.toEntity())
    }
}

private fun com.tradingmaster.core.store.PaperTradeEntity.toModel() = PaperTrade(
    id = id, symbol = symbol,
    direction = com.tradingmaster.core.model.Direction.valueOf(direction),
    entryPrice = entryPrice, qty = qty, notionalUsdt = notionalUsdt,
    stopLoss = stopLoss, tp1 = tp1, tp2 = tp2, tp3 = tp3,
    openedAt = openedAt, closedAt = closedAt,
    status = PaperStatus.valueOf(status),
    exitPrice = exitPrice, realizedPnlUsdt = realizedPnlUsdt,
    feesUsdt = feesUsdt, slippageCostUsdt = slippageCostUsdt,
    partialExits = partialExits,
    closeReason = closeReason?.let { runCatching { com.tradingmaster.core.model.PaperCloseReason.valueOf(it) }.getOrNull() }
)

private fun PaperTrade.toEntity() = com.tradingmaster.core.store.PaperTradeEntity(
    symbol = symbol, direction = direction.name, entryPrice = entryPrice, qty = qty,
    notionalUsdt = notionalUsdt, stopLoss = stopLoss, tp1 = tp1, tp2 = tp2, tp3 = tp3,
    openedAt = openedAt, closedAt = closedAt, status = status.name,
    exitPrice = exitPrice, realizedPnlUsdt = realizedPnlUsdt, feesUsdt = feesUsdt,
    slippageCostUsdt = slippageCostUsdt, partialExits = partialExits, closeReason = closeReason?.name
)
