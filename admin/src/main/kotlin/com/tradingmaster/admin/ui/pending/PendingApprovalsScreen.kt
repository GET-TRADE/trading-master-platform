package com.tradingmaster.admin.ui.pending

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradingmaster.admin.data.AdminUser
import com.tradingmaster.admin.ui.components.AdminTopBar
import com.tradingmaster.admin.ui.components.ConfirmDialog
import com.tradingmaster.admin.ui.components.EmptyState
import com.tradingmaster.admin.ui.components.ErrorBanner
import com.tradingmaster.admin.ui.components.LoadingState
import com.tradingmaster.admin.ui.components.StatusChip
import com.tradingmaster.admin.ui.theme.StatusApproved
import com.tradingmaster.admin.ui.theme.StatusBlocked

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PendingApprovalsScreen(
    onBackClick: () -> Unit,
    viewModel: PendingApprovalsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var pendingActionDialog by remember { mutableStateOf<Pair<AdminUser, String>?>(null) } // user to action string

    LaunchedEffect(uiState.actionSuccessMessage) {
        uiState.actionSuccessMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AdminTopBar(
                title = "Pending Approvals",
                onBackClick = onBackClick,
                actions = {
                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
            uiState.errorMessage?.let { error ->
                ErrorBanner(
                    errorMessage = error,
                    onRetry = { viewModel.loadData() }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by email, phone, or device ID...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isLoading && uiState.pendingUsers.isEmpty()) {
                LoadingState(message = "Checking for pending approvals...")
            } else {
                val filteredList = uiState.pendingUsers.filter { user ->
                    val q = uiState.searchQuery.trim().lowercase()
                    q.isEmpty() ||
                            user.email.lowercase().contains(q) ||
                            user.userId.lowercase().contains(q) ||
                            (user.whatsapp?.lowercase()?.contains(q) == true) ||
                            (user.deviceId?.lowercase()?.contains(q) == true)
                }

                if (filteredList.isEmpty()) {
                    EmptyState(message = "No pending user or device approval requests.")
                } else {
                    Text(
                        text = "Pending Requests (${filteredList.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    filteredList.forEach { user ->
                        PendingUserCard(
                            user = user,
                            onAction = { action -> pendingActionDialog = Pair(user, action) },
                            onOpenWhatsApp = { phone ->
                                try {
                                    val cleaned = phone.replace("+", "").replace(" ", "").trim()
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleaned"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Handle missing browser / WhatsApp app gracefully
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    pendingActionDialog?.let { (user, action) ->
        val title = when (action) {
            "approve" -> "Approve User"
            "reject" -> "Reject User"
            "approve_device" -> "Approve Device"
            "revoke_device" -> "Revoke Device"
            else -> "Confirm Action"
        }
        val message = when (action) {
            "approve" -> "Are you sure you want to approve user account ${user.email}?"
            "reject" -> "Are you sure you want to reject user account ${user.email}?"
            "approve_device" -> "Approve device ${user.deviceId ?: ""} for user ${user.email}?"
            "revoke_device" -> "Revoke device authorization for ${user.deviceId ?: ""}?"
            else -> "Proceed with this request?"
        }
        val isDestructive = action in listOf("reject", "revoke_device")

        ConfirmDialog(
            title = title,
            message = message,
            confirmText = if (isDestructive) "Proceed" else "Approve",
            isDestructive = isDestructive,
            onConfirm = {
                pendingActionDialog = null
                when (action) {
                    "approve" -> viewModel.setUserStatus(user, "approved")
                    "reject" -> viewModel.setUserStatus(user, "rejected")
                    "approve_device" -> viewModel.approveDevice(user)
                    "revoke_device" -> viewModel.revokeDevice(user)
                }
            },
            onDismiss = { pendingActionDialog = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingUserCard(
    user: AdminUser,
    onAction: (String) -> Unit,
    onOpenWhatsApp: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.email,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ID: ${user.userId}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(status = user.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            user.createdAt?.let { created ->
                Text(
                    text = "Registered: $created",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            user.whatsapp?.let { phone ->
                if (phone.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "WhatsApp: $phone",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { onOpenWhatsApp(phone) },
                            modifier = Modifier.height(30.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "WhatsApp", modifier = Modifier.height(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Chat", fontSize = 11.sp)
                        }
                    }
                }
            }

            user.deviceId?.let { devId ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Device: ${devId.take(16)}...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatusChip(status = user.deviceStatus ?: "pending")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (user.status.lowercase() == "pending") {
                    Button(
                        onClick = { onAction("approve") },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusApproved)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.height(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Approve User")
                    }

                    OutlinedButton(
                        onClick = { onAction("reject") },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusBlocked)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.height(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reject User")
                    }
                }

                user.deviceId?.let {
                    if (user.deviceStatus?.lowercase() == "pending") {
                        Button(
                            onClick = { onAction("approve_device") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Approve Device", fontSize = 12.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onAction("revoke_device") }
                        ) {
                            Text("Revoke Device", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
