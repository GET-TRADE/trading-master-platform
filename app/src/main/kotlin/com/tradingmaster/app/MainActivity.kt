package com.tradingmaster.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tradingmaster.app.ui.TmTheme
import com.tradingmaster.app.screens.AuthScreen
import com.tradingmaster.app.screens.SignalDetailScreen
import com.tradingmaster.app.screens.HomeScreen
import com.tradingmaster.app.screens.PendingApprovalScreen
import com.tradingmaster.app.screens.RootViewModel
import com.tradingmaster.app.screens.RootViewModel.AuthGate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as TradingMasterApp).container
        container.session.restore()
        setContent {
            TmTheme {
                val vm: RootViewModel = viewModel(factory = RootViewModel.factory(container))
                val gate by vm.gate.collectAsStateWithLifecycle()
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = when (gate) {
                    AuthGate.SIGNED_OUT, AuthGate.CHECKING -> "auth"
                    AuthGate.PENDING -> "pending"
                    AuthGate.APPROVED -> "home"
                }) {
                    composable("auth") { AuthScreen(nav) }
                    composable("pending") { PendingApprovalScreen(nav) }
                    composable("home") { HomeScreen(nav) }
                    composable("signal/{symbol}") { entry ->
                        SignalDetailScreen(nav, entry.arguments?.getString("symbol") ?: "")
                    }
                }
            }
        }
    }
}
