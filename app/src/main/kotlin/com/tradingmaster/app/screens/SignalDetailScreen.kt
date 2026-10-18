package com.tradingmaster.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tradingmaster.app.TradingMasterApp
import com.tradingmaster.core.model.ScanResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Full signal detail: entry zone, stops, targets, R:R, score, reasons, invalidations, warnings. */
@Composable
fun SignalDetailScreen(nav: NavController, symbol: String) {
    val app = nav.context.applicationContext as TradingMasterApp
    val vm: ScannerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = ScannerViewModel.Factory(app.container))
    val ui by vm.ui.collectAsState()
    // Prefer the in-memory copy; fall back to the latest stored record for this symbol.
    val result: ScanResult? = vm.selectedSignals[symbol]
        ?: ui.signals.firstOrNull { it.symbol == symbol }
        ?: loadLatestSignal(app, symbol)

    if (result == null) {
        Text("Signal not found. Run a scan first.", Modifier.padding(16.dp))
        return
    }
    val s = result.signal!!

    Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${s.symbol} ${s.direction}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Ranking score: ${s.score}/5 (a ranking, not a probability)", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.secondary)
        Text("Reference price: ${"%.6f".format(s.referencePrice)}")

        Section("Entry / stop / targets") {
            Line("Entry zone", "%.6f – %.6f".format(s.entryLow, s.entryHigh))
            Line("Stop-loss", "%.6f".format(s.stopLoss))
            Line("TP1", "%.6f (R:R %.2f)".format(s.tp1, s.rr1))
            Line("TP2", "%.6f (R:R %.2f)".format(s.tp2, s.rr2))
            Line("TP3", "%.6f (R:R %.2f)".format(s.tp3, s.rr3))
        }
        Section("Why this setup") { s.reasons.forEach { Bullet(it) } }
        Section("Score explanation") { s.scoreExplanation.forEach { Bullet(it) } }
        Section("Invalidation conditions") { s.invalidations.forEach { Bullet(it) } }
        Section("Data freshness") {
            Line("5m close", fmtTime(s.m5CloseTime))
            Line("15m close", fmtTime(s.m15CloseTime))
            Line("1h close", fmtTime(s.h1CloseTime))
            Line("Generated", fmtTime(s.generatedAt))
        }
        Section("Risk warnings") {
            s.warnings.forEach { Bullet(it) }
            Bullet("Only verified exchange data is used. Open interest, funding and liquidation metrics are shown only when real data is available.")
        }
        Spacer(Modifier.height(20.dp))
    }
}

internal val SignalJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

private fun loadLatestSignal(app: TradingMasterApp, symbol: String): ScanResult? = try {
    val dao = app.container.db.signalHistoryDao()
    val all = kotlinx.coroutines.runBlocking { dao.recentTrades(500) }
    all.firstOrNull { it.symbol == symbol }
        ?.let { SignalJson.decodeFromString(ScanResult.serializer(), it.payloadJson) }
} catch (e: Exception) { null }

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            content()
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        Text(value, fontSize = 12.sp)
    }
}

@Composable
private fun Bullet(text: String) {
    Text("• $text", fontSize = 12.sp)
}

private fun fmtTime(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
