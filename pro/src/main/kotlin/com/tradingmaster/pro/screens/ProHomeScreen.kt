package com.tradingmaster.pro.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

/** Shell with bottom navigation across Pro module screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProHomeScreen(nav: NavController) {
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf("Scan", "Backtest", "Journal", "AI", "Settings")

    Scaffold(
        topBar = { TopAppBar(title = { Text("Trading Master Pro — ${titles[tab]}") }) },
        bottomBar = {
            NavigationBar {
                titles.forEachIndexed { i, title ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(title.take(1)) },
                        label = { Text(title, fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> ScannerScreen(nav)
                1 -> BacktestScreen(nav)
                2 -> JournalScreen(nav)
                3 -> AiScreen()
                else -> SettingsScreen(nav)
            }
        }
    }
}
