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
import androidx.navigation.NavController
import com.tradingmaster.app.TradingMasterApp
import com.tradingmaster.core.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Scan and signal history from the local Room database (last 30 days kept). */
@Composable
fun SignalHistoryScreen(nav: NavController) {
    val app = nav.context.applicationContext as TradingMasterApp
    val history = remember { mutableStateOf<List<ScanResult>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            history.value = app.container.db.signalHistoryDao()
                .recentTrades(200)
                .mapNotNull { runCatching {
                    SignalJson.decodeFromString(ScanResult.serializer(), it.payloadJson)
                }.getOrNull() }
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Signal history", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Stored locally on this device only.", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(history.value, key = { "${it.symbol}_${it.generatedAt}" }) { r ->
                val s = r.signal!!
                Card(Modifier.fillMaxWidth().clickable {
                    nav.navigate("signal/${r.symbol}")
                }) {
                    Column(Modifier.padding(10.dp)) {
                        Text("${s.symbol} ${s.direction} • score ${s.score}/5", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(s.generatedAt)) + " • TP1 R:R ${s.rr1}", fontSize = 11.sp)
                    }
                }
            }
            if (history.value.isEmpty()) {
                item { Text("No signals recorded yet.", fontSize = 12.sp) }
            }
        }
    }
}
