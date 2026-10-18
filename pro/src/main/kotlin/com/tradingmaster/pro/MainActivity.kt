package com.tradingmaster.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tradingmaster.pro.screens.AuthScreen
import com.tradingmaster.pro.screens.PendingScreen
import com.tradingmaster.pro.screens.ProHomeScreen
import com.tradingmaster.pro.screens.RootViewModel
import com.tradingmaster.pro.screens.RootViewModel.AuthGate
import com.tradingmaster.pro.screens.SignalDetailScreen
import com.tradingmaster.pro.ui.TradingMasterProTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ProApp).container
        container.session.restore()
        setContent {
            TradingMasterProTheme {
                val vm: RootViewModel = viewModel(factory = RootViewModel.factory(container))
                val gate by vm.gate.collectAsStateWithLifecycle()
                val nav = rememberNavController()
                NavHost(
                    navController = nav,
                    startDestination = when (gate) {
                        AuthGate.SIGNED_OUT, AuthGate.CHECKING -> "auth"
                        AuthGate.PENDING -> "pending"
                        AuthGate.APPROVED -> "home"
                    }
                ) {
                    composable("auth") { AuthScreen(nav) }
                    composable("pending") { PendingScreen(nav) }
                    composable("home") { ProHomeScreen(nav) }
                    composable("signal/{symbol}") { entry ->
                        SignalDetailScreen(nav, entry.arguments?.getString("symbol") ?: "")
                    }
                }
            }
        }
    }
}
