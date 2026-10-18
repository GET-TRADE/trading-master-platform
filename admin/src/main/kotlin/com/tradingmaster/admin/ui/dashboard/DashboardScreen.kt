package com.tradingmaster.admin.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradingmaster.admin.ui.components.AdminTopBar
import com.tradingmaster.admin.ui.components.ConfirmDialog
import com.tradingmaster.admin.ui.components.StatusChip

@Composable
fun DashboardScreen(
    onNavigateToUsers: () -> Unit,
    onNavigateToPending: () -> Unit,
    onNavigateToPayments: () -> Unit,
    onNavigateToPlans: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAiConfig: () -> Unit,
    onNavigateToHealth: () -> Unit,
    onNavigateToAudit: () -> Unit,
    onNavigateToErrors: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val summary by viewModel.summary.collectAsState()
    var showSignOutDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    if (showSignOutDialog) {
        ConfirmDialog(
            title = "Sign Out",
            message = "Are you sure you want to sign out of Trading Master Admin?",
            confirmText = "Sign Out",
            isDestructive = true,
            onConfirm = {
                showSignOutDialog = false
                viewModel.signOut(onSignOut)
            },
            onDismiss = { showSignOutDialog = false }
        )
    }

    Scaffold(
        topBar = {
            AdminTopBar(
                title = "Admin Dashboard",
                actions = {
                    IconButton(onClick = { viewModel.refreshSummary() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showSignOutDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Sign Out",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Quick Status Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "System Summary",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryStat(label = "Total Users", value = summary.totalUsers.toString())
                        SummaryStat(label = "Pending Approvals", value = summary.pendingUsers.toString(), highlight = summary.pendingUsers > 0)
                        SummaryStat(label = "Pending Payments", value = summary.pendingPayments.toString(), highlight = summary.pendingPayments > 0)
                    }
                }
            }

            Text(
                text = "Management Modules",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Grid of Navigation Cards
            DashboardItemCard(
                title = "Users Management",
                subtitle = "Approve, block, set subscription, or delete users",
                icon = Icons.Default.Person,
                badge = if (summary.totalUsers > 0) "${summary.totalUsers} users" else null,
                onClick = onNavigateToUsers
            )

            DashboardItemCard(
                title = "Pending Approvals",
                subtitle = "Review new registration requests & WhatsApp contact",
                icon = Icons.Default.CheckCircle,
                badge = if (summary.pendingUsers > 0) "${summary.pendingUsers} pending" else null,
                onClick = onNavigateToPending
            )

            DashboardItemCard(
                title = "Payments Verification",
                subtitle = "Review & verify manual Binance Pay / TRC20 payments",
                icon = Icons.Default.ShoppingCart,
                badge = if (summary.pendingPayments > 0) "${summary.pendingPayments} pending" else null,
                onClick = onNavigateToPayments
            )

            DashboardItemCard(
                title = "Subscription Plans",
                subtitle = "Create & edit pricing plans and duration",
                icon = Icons.Default.List,
                onClick = onNavigateToPlans
            )

            DashboardItemCard(
                title = "Payment Settings",
                subtitle = "Binance Pay ID & TRC20 wallet addresses",
                icon = Icons.Default.Settings,
                onClick = onNavigateToSettings
            )

            DashboardItemCard(
                title = "AI Gateway Config",
                subtitle = "Configure providers, model order & fallback rules",
                icon = Icons.Default.Build,
                onClick = onNavigateToAiConfig
            )

            DashboardItemCard(
                title = "Backend Health & System",
                subtitle = "Edge function status, DB health & GitHub links",
                icon = Icons.Default.Lock,
                statusChip = summary.healthStatus,
                onClick = onNavigateToHealth
            )

            DashboardItemCard(
                title = "Audit Log",
                subtitle = "Track administrative operations & history",
                icon = Icons.Default.List,
                onClick = onNavigateToAudit
            )

            DashboardItemCard(
                title = "System Error Log",
                subtitle = "Monitor system exceptions & error events",
                icon = Icons.Default.Warning,
                onClick = onNavigateToErrors
            )

            DashboardItemCard(
                title = "About & Application Info",
                subtitle = "Version, package info & documentation links",
                icon = Icons.Default.Info,
                onClick = onNavigateToAbout
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SummaryStat(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun DashboardItemCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badge: String? = null,
    statusChip: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (badge != null) {
                StatusChip(status = badge)
            } else if (statusChip != null) {
                StatusChip(status = statusChip)
            }
        }
    }
}
