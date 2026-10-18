package com.tradingmaster.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

/** Home shell with bottom navigation across the user app's main surfaces. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController) {
    var tab by remember { mutableStateOf(0) }
    val titles = listOf("Scanner", "Signals", "Paper", "Plan", "Settings")

    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.tradingmaster.app.work.ScanWorker.schedule(nav.context.applicationContext)
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(titles[tab]) }) },
        bottomBar = {
            NavigationBar {
                titles.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(t.take(1)) },
                        label = { Text(t, fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> ScannerScreen(nav)
                1 -> SignalHistoryScreen(nav)
                2 -> PaperJournalScreen(nav)
                3 -> SubscriptionScreen(nav)
                else -> SettingsScreen(nav)
            }
        }
    }
}
