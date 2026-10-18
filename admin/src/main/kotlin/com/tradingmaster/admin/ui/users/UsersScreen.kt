package com.tradingmaster.admin.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutFlow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradingmaster.admin.data.AdminPlan
import com.tradingmaster.admin.data.AdminUser
import com.tradingmaster.admin.ui.components.AdminTopBar
import com.tradingmaster.admin.ui.components.ConfirmDialog
import com.tradingmaster.admin.ui.components.EmptyState
import com.tradingmaster.admin.ui.components.ErrorBanner
import com.tradingmaster.admin.ui.components.LoadingState
import com.tradingmaster.admin.ui.components.StatusChip

@OptIn(ExperimentalLayoutFlow::class, ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(
    onBackClick: () -> Unit,
    viewModel: UsersViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog States
    var userPendingAction by remember { mutableStateOf<Pair<AdminUser, String>?>(null) } // (User, Action: "approve", "reject", "block", "unblock", "delete", "approve_device", "revoke_device")
    var subDialogUser by remember { mutableStateOf<AdminUser?>(null) }

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
                title = "User Management",
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
                placeholder = { Text("Search users by email, ID, phone or key...") },
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

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips
            val filterOptions = listOf("ALL", "pending", "approved", "blocked", "rejected")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterOptions.forEach { filter ->
                    FilterChip(
                        selected = uiState.statusFilter.lowercase() == filter.lowercase(),
                        onClick = { viewModel.onStatusFilterChange(filter) },
                        label = { Text(filter.uppercase()) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isLoading && uiState.users.isEmpty()) {
                LoadingState(message = "Loading user accounts...")
            } else {
                val filteredUsers = uiState.users.filter { user ->
                    val query = uiState.searchQuery.trim().lowercase()
                    val matchesQuery = query.isEmpty() ||
                            user.email.lowercase().contains(query) ||
                            user.userId.lowercase().contains(query) ||
                            (user.whatsapp?.lowercase()?.contains(query) == true) ||
                            (user.appKey?.lowercase()?.contains(query) == true) ||
                            (user.deviceId?.lowercase()?.contains(query) == true)

                    val matchesFilter = when (uiState.statusFilter.lowercase()) {
                        "all" -> true
                        else -> user.status.lowercase() == uiState.statusFilter.lowercase()
                    }

                    matchesQuery && matchesFilter
                }

                if (filteredUsers.isEmpty()) {
                    EmptyState(message = "No users matching your criteria.")
                } else {
                    Text(
                        text = "Showing ${filteredUsers.size} of ${uiState.users.size} users",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    filteredUsers.forEach { user ->
                        UserCard(
                            user = user,
                            onAction = { action -> userPendingAction = Pair(user, action) },
                            onAdjustSubscription = { subDialogUser = user }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Confirmation Dialogs
    userPendingAction?.let { (user, action) ->
        val title = when (action) {
            "approve" -> "Approve User"
            "reject" -> "Reject User"
            "block" -> "Block User"
            "unblock" -> "Unblock User"
            "delete" -> "Delete User"
            "approve_device" -> "Approve Device"
            "revoke_device" -> "Revoke Device"
            else -> "Confirm Action"
        }
        val message = when (action) {
            "approve" -> "Are you sure you want to approve user ${user.email}?"
            "reject" -> "Are you sure you want to reject user ${user.email}?"
            "block" -> "Are you sure you want to block user ${user.email}?"
            "unblock" -> "Are you sure you want to unblock user ${user.email}?"
            "delete" -> "PERMANENT ACTION: Delete user ${user.email} and all associated records?"
            "approve_device" -> "Approve device ${user.deviceId ?: ""} for user ${user.email}?"
            "revoke_device" -> "Revoke device authorization for device ${user.deviceId ?: ""}?"
            else -> "Proceed with this action?"
        }
        val isDestructive = action in listOf("block", "delete", "reject", "revoke_device")

        ConfirmDialog(
            title = title,
            message = message,
            confirmText = if (isDestructive) "Proceed" else "Confirm",
            isDestructive = isDestructive,
            onConfirm = {
                userPendingAction = null
                when (action) {
                    "approve" -> viewModel.setUserStatus(user, "approved")
                    "reject" -> viewModel.setUserStatus(user, "rejected")
                    "block" -> viewModel.setUserStatus(user, "blocked")
                    "unblock" -> viewModel.setUserStatus(user, "approved")
                    "delete" -> viewModel.deleteUser(user)
                    "approve_device" -> viewModel.approveDevice(user)
                    "revoke_device" -> viewModel.revokeDevice(user)
                }
            },
            onDismiss = { userPendingAction = null }
        )
    }

    // Subscription Adjustment Dialog
    subDialogUser?.let { user ->
        SubscriptionDialog(
            user = user,
            plans = uiState.plans,
            onDismiss = { subDialogUser = null },
            onConfirm = { planId, days ->
                subDialogUser = null
                viewModel.setSubscription(user, planId, days)
            }
        )
    }
}

@OptIn(ExperimentalLayoutFlow::class)
@Composable
private fun UserCard(
    user: AdminUser,
    onAction: (String) -> Unit,
    onAdjustSubscription: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
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
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ID: ${user.userId}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(status = user.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!user.whatsapp.isNull_or_blank()) {
                Text(
                    text = "WhatsApp: ${user.whatsapp}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!user.appKey.isNull_or_blank()) {
                Text(
                    text = "App Key: ${user.appKey}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!user.subscriptionExpiresAt.isNull_or_blank()) {
                Text(
                    text = "Expires: ${user.subscriptionExpiresAt}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!user.deviceId.isNull_or_blank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Device: ${user.deviceId}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    StatusChip(status = user.deviceStatus ?: "unknown")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (user.status.lowercase() != "approved") {
                    Button(
                        onClick = { onAction("approve") },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Approve", fontSize = 12.sp)
                    }
                }

                if (user.status.lowercase() != "rejected") {
                    OutlinedButton(
                        onClick = { onAction("reject") },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Reject", fontSize = 12.sp)
                    }
                }

                if (user.status.lowercase() == "blocked") {
                    Button(
                        onClick = { onAction("unblock") },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Unblock", fontSize = 12.sp)
                    }
                } else if (user.status.lowercase() == "approved") {
                    OutlinedButton(
                        onClick = { onAction("block") },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Block", fontSize = 12.sp)
                    }
                }

                OutlinedButton(
                    onClick = onAdjustSubscription,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.height(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sub", fontSize = 12.sp)
                }

                if (!user.deviceId.isNull_or_blank()) {
                    if (user.deviceStatus?.lowercase() == "approved") {
                        OutlinedButton(
                            onClick = { onAction("revoke_device") },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Revoke Dev", fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = { onAction("approve_device") },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Approve Dev", fontSize = 12.sp)
                        }
                    }
                }

                OutlinedButton(
                    onClick = { onAction("delete") },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.height(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionDialog(
    user: AdminUser,
    plans: List<AdminPlan>,
    onDismiss: () -> Unit,
    onConfirm: (planId: String, days: Int) -> Unit
) {
    var selectedPlan by remember { mutableStateOf(plans.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }
    var customDaysText by remember { mutableStateOf(selectedPlan?.durationDays?.toString() ?: "30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set User Subscription", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "User: ${user.email}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedPlan?.name ?: "Select Plan",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Subscription Plan") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        plans.forEach { plan ->
                            DropdownMenuItem(
                                text = { Text("${plan.name} (${plan.durationDays} days - PKR ${plan.pricePkr.toInt()})") },
                                onClick = {
                                    selectedPlan = plan
                                    customDaysText = plan.durationDays.toString()
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = customDaysText,
                    onValueChange = { customDaysText = it },
                    label = { Text("Duration (Days)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val days = customDaysText.toIntOrNull() ?: selectedPlan?.durationDays ?: 30
                    val planId = selectedPlan?.id ?: "pro_monthly"
                    onConfirm(planId, days)
                }
            ) {
                Text("Save Subscription")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun String?.isNull_or_blank(): Boolean = this == null || this.isBlank()
