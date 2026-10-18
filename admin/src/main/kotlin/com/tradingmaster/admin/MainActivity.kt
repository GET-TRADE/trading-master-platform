package com.tradingmaster.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tradingmaster.admin.ui.about.AboutScreen
import com.tradingmaster.admin.ui.ai.AiConfigScreen
import com.tradingmaster.admin.ui.audit.AuditScreen
import com.tradingmaster.admin.ui.dashboard.DashboardScreen
import com.tradingmaster.admin.ui.errors.ErrorsScreen
import com.tradingmaster.admin.ui.health.HealthScreen
import com.tradingmaster.admin.ui.login.LoginScreen
import com.tradingmaster.admin.ui.payments.PaymentsScreen
import com.tradingmaster.admin.ui.pending.PendingApprovalsScreen
import com.tradingmaster.admin.ui.plans.PlansScreen
import com.tradingmaster.admin.ui.settings.SettingsScreen
import com.tradingmaster.admin.ui.theme.AdminTheme
import com.tradingmaster.admin.ui.users.UsersScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = AdminApp.instance.adminRepository
        val startDestination = if (repository.isSignedIn()) "dashboard" else "login"

        setContent {
            AdminTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = {
                                    navController.navigate("dashboard") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("dashboard") {
                            DashboardScreen(
                                onNavigateToUsers = { navController.navigate("users") },
                                onNavigateToPending = { navController.navigate("pending") },
                                onNavigateToPayments = { navController.navigate("payments") },
                                onNavigateToPlans = { navController.navigate("plans") },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToAiConfig = { navController.navigate("ai") },
                                onNavigateToHealth = { navController.navigate("health") },
                                onNavigateToAudit = { navController.navigate("audit") },
                                onNavigateToErrors = { navController.navigate("errors") },
                                onNavigateToAbout = { navController.navigate("about") },
                                onSignOut = {
                                    navController.navigate("login") {
                                        popUpTo("dashboard") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("users") {
                            UsersScreen(onBackClick = { navController.popBackStack() })
                        }

                        composable("pending") {
                            PendingApprovalsScreen(onBackClick = { navController.popBackStack() })
                        }

                        composable("payments") {
                            PaymentsScreen(onBackClick = { navController.popBackStack() })
                        }

                        composable("plans") {
                            PlansScreen(onBackClick = { navController.popBackStack() })
                        }

                        composable("settings") {
                            SettingsScreen(onBackClick = { navController.popBackStack() })
                        }

                        composable("ai") {
                            AiConfigScreen(onBackClick = { navController.popBackStack() })
                        }

                        composable("health") {
                            HealthScreen(onBackClick = { navController.popBackStack() })
                        }

                        composable("audit") {
                            AuditScreen(onBackClick = { navController.popBackStack() })
                        }

                        composable("errors") {
                            ErrorsScreen(onBackClick = { navController.popBackStack() })
                        }

                        composable("about") {
                            AboutScreen(onBackClick = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
