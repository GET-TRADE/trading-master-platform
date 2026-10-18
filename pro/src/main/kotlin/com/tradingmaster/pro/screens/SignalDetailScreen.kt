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
import androidx.navigation.NavController
import com.tradingmaster.core.ai.AiAnalysis
import com.tradingmaster.core.ai.AiGatewayClient.AiHealth
import com.tradingmaster.core.model.ScanResult
import com.tradingmaster.core.model.TradeSignal
import com.tradingmaster.pro.ProApp
import com.tradingmaster.pro.ProContainer
import com.tradingmaster.pro.model.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SignalDetailScreen(nav: NavController, symbol: String) {
    val app = nav.context.applicationContext as ProApp
    val container = app.container
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true } }

    var result by remember { mutableStateOf<ScanResult?>(null) }
    var aiHealth by remember { mutableStateOf<AiHealth?>(null) }
    var aiAnalysis by remember { mutableStateOf<AiAnalysis?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var paperTradeMsg by remember { mutableStateOf<String?>(null) }
    var isOpeningPaperTrade by remember { mutableStateOf(false) }

    LaunchedEffect(symbol) {
        withContext(Dispatchers.IO) {
            // Check in-memory history first or load from Room database
            val db = container.database
            val recent = db.signalHistoryDao().recentTrades(100)
            val match = recent.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
            if (match != null) {
                runCatching {
                    result = json.decodeFromString(ScanResult.serializer(), match.payloadJson)
                }
            }
            // Fetch AI health
            runCatching {
                aiHealth = container.aiGatewayClient.health()
            }
        }
    }

    val res = result
    if (res == null || res.signal == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Signal details loading or not found for $symbol.")
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { nav.popBackStack() }) {
                Text("Back to Scanner")
            }
        }
        return
    }

    val s = res.signal!!
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss UTC", Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${s.symbol} ${s.direction}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Score ${s.score}/5",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            "Ranking score: ${s.score}/5 (ranking classification, not probability of success)",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.secondary
        )
        Text("Reference price: ${"%.6f".format(s.referencePrice)}")

        // AI Health Banner
        val h = aiHealth
        if (h != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (h.available) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        "AI Health Banner: ${if (h.available) "AI Services Available" else "AI Services Unavailable"}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (h.available) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onErrorContainer
                    )
                    if (!h.available) {
                        Text(
                            "Gateway report: ${h.detail}. AI is unavailable; analysis will use deterministic local fallbacks.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else if (h.configuredProviders.isNotEmpty()) {
                        Text(
                            "Configured providers: ${h.configuredProviders.joinToString(", ")}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Section: Price Targets & Risk/Reward
        SectionCard("Entry / Stop / Take-Profit Targets") {
            DetailLine("Entry Zone", "${"%.6f".format(s.entryLow)} – ${"%.6f".format(s.entryHigh)}")
            DetailLine("Stop-Loss", "%.6f".format(s.stopLoss))
            DetailLine("TP1 Target", "${"%.6f".format(s.tp1)} (R:R ${"%.2f".format(s.rr1)})")
            DetailLine("TP2 Target", "${"%.6f".format(s.tp2)} (R:R ${"%.2f".format(s.rr2)})")
            DetailLine("TP3 Target", "${"%.6f".format(s.tp3)} (R:R ${"%.2f".format(s.rr3)})")
        }

        // Section: Score Explanation
        SectionCard("Score Components") {
            s.scoreExplanation.forEach { BulletPoint(it) }
        }

        // Section: Setup Reasons
        SectionCard("Setup Reasons") {
            s.reasons.forEach { BulletPoint(it) }
        }

        // Section: Invalidation Conditions
        SectionCard("Invalidation Conditions") {
            s.invalidations.forEach { BulletPoint(it) }
        }

        // Section: Warnings
        SectionCard("Risk Warnings") {
            s.warnings.forEach { BulletPoint(it) }
        }

        // Section: Timeframe Timestamps
        SectionCard("Candle Timestamps & Generation Time") {
            DetailLine("Signal Generated At", dateFormat.format(Date(s.generatedAt)))
            DetailLine("5m Candle Close Time", dateFormat.format(Date(s.m5CloseTime)))
            DetailLine("15m Candle Close Time", dateFormat.format(Date(s.m15CloseTime)))
            DetailLine("1h Candle Close Time", dateFormat.format(Date(s.h1CloseTime)))
        }

        // Action: Open Paper Trade
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Simulated Paper Trading", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Button(
                    onClick = {
                        scope.launch {
                            isOpeningPaperTrade = true
                            paperTradeMsg = null
                            try {
                                val trade = withContext(Dispatchers.IO) {
                                    val t = container.paperEngine.open(s, System.currentTimeMillis())
                                    container.database.paperTradeDao().insert(t.toEntity())
                                    t
                                }
                                paperTradeMsg = "Paper trade opened for ${trade.symbol} at ${"%.6f".format(trade.entryPrice)}!"
                            } catch (e: Exception) {
                                paperTradeMsg = "Failed to open paper trade: ${e.message}"
                            } finally {
                                isOpeningPaperTrade = false
                            }
                        }
                    },
                    enabled = !isOpeningPaperTrade,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isOpeningPaperTrade) "Opening..." else "Open paper trade")
                }
                if (paperTradeMsg != null) {
                    Text(paperTradeMsg!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        // Action: Analyze with AI
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("AI Candidate Analysis (advisory only)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "AI analysis is advisory only. Risk rules and preflight checks are strictly enforced and cannot be bypassed.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )

                Button(
                    onClick = {
                        scope.launch {
                            isAnalyzing = true
                            try {
                                val payload = json.encodeToString(TradeSignal.serializer(), s)
                                val analysis = withContext(Dispatchers.IO) {
                                    container.aiGatewayClient.analyzeCandidate(payload)
                                }
                                aiAnalysis = analysis
                            } catch (e: Exception) {
                                aiAnalysis = AiAnalysis(
                                    succeeded = false,
                                    modelUsed = null,
                                    analysis = null,
                                    risks = emptyList(),
                                    error = "Invocation error: ${e.message}",
                                    fellBack = true
                                )
                            } finally {
                                isAnalyzing = false
                            }
                        }
                    },
                    enabled = !isAnalyzing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isAnalyzing) "Analyzing candidate..." else "Analyze with AI")
                }

                val analysis = aiAnalysis
                if (analysis != null) {
                    Spacer(Modifier.height(6.dp))
                    if (analysis.succeeded && analysis.analysis != null) {
                        Text(
                            "Model Used: ${analysis.modelUsed ?: "Gateway default"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(analysis.analysis ?: "", fontSize = 12.sp)
                        if (analysis.risks.isNotEmpty()) {
                            Text("Identified Risks:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            analysis.risks.forEach { BulletPoint(it) }
                        }
                    } else {
                        // Fallback summary on AI failure
                        Text(
                            "deterministic fallback — AI unavailable",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                        if (analysis.error != null) {
                            Text("Error: ${analysis.error}", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                        Text(
                            "Deterministic Fallback Summary: ${s.symbol} ${s.direction} setup with score ${s.score}/5. " +
                                    "Entry zone: ${"%.6f".format(s.entryLow)} - ${"%.6f".format(s.entryHigh)}. " +
                                    "Stop loss: ${"%.6f".format(s.stopLoss)}. TP1: ${"%.6f".format(s.tp1)} (R:R ${"%.2f".format(s.rr1)}). " +
                                    "Components: ${s.scoreExplanation.joinToString("; ")}. Risk checks: advisory only - verify on exchange.",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            content()
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BulletPoint(text: String) {
    Text("• $text", fontSize = 12.sp)
}
