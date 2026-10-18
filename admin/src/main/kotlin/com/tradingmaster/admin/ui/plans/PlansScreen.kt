package com.tradingmaster.admin.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.tradingmaster.admin.ui.components.AdminTopBar
import com.tradingmaster.admin.ui.components.EmptyState
import com.tradingmaster.admin.ui.components.ErrorBanner
import com.tradingmaster.admin.ui.components.LoadingState
import com.tradingmaster.admin.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(
    onBackClick: () -> Unit,
    viewModel: PlansViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    var editingPlan by remember { mutableStateOf<AdminPlan?>(null) } // null = not editing, if non-null or new
    var showCreateDialog by remember { mutableStateOf(false) }

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
                title = "Subscription Plans",
                onBackClick = onBackClick,
                actions = {
                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Plan") },
                text = { Text("New Plan") },
                containerColor = MaterialTheme.colorScheme.primary
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

            if (uiState.isLoading && uiState.plans.isEmpty()) {
                LoadingState(message = "Loading subscription plans...")
            } else if (uiState.plans.isEmpty()) {
                EmptyState(message = "No subscription plans configured yet.")
            } else {
                Text(
                    text = "Configured Plans (${uiState.plans.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                uiState.plans.forEach { plan ->
                    PlanCard(
                        plan = plan,
                        onEditClick = { editingPlan = plan }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(80.dp)) // Extra padding for FAB
        }
    }

    // Create / Edit Dialog
    if (showCreateDialog) {
        PlanEditorDialog(
            title = "Create New Plan",
            initialName = "",
            initialPricePkr = "0.0",
            initialDurationDays = "30",
            initialFeatures = "",
            onConfirm = { name, price, duration, features ->
                showCreateDialog = false
                viewModel.createPlan(name, price, duration, features)
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    editingPlan?.let { plan ->
        PlanEditorDialog(
            title = "Edit Plan: ${plan.name}",
            initialName = plan.name,
            initialPricePkr = plan.pricePkr.toString(),
            initialDurationDays = plan.durationDays.toString(),
            initialFeatures = plan.features.joinToString("\n"),
            onConfirm = { name, price, duration, features ->
                val pId = plan.id
                editingPlan = null
                viewModel.updatePlan(pId, name, price, duration, features)
            },
            onDismiss = { editingPlan = null }
        )
    }
}

@Composable
private fun PlanCard(
    plan: AdminPlan,
    onEditClick: () -> Unit
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
                        text = plan.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ID: ${plan.id}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(status = if (plan.active) "active" else "disabled")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "PKR ${plan.pricePkr}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${plan.durationDays} Days",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (plan.features.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Features:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                plan.features.forEach { feature ->
                    Text(
                        text = "• $feature",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onEditClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.height(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Edit Plan Details")
            }
        }
    }
}

@Composable
private fun PlanEditorDialog(
    title: String,
    initialName: String,
    initialPricePkr: String,
    initialDurationDays: String,
    initialFeatures: String,
    onConfirm: (name: String, pricePkr: Double, durationDays: Int, features: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var priceStr by remember { mutableStateOf(initialPricePkr) }
    var durationStr by remember { mutableStateOf(initialDurationDays) }
    var featuresText by remember { mutableStateOf(initialFeatures) }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                validationError?.let { err ->
                    Text(text = err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Plan Name") },
                    placeholder = { Text("e.g. Pro Monthly") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Price (PKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = durationStr,
                    onValueChange = { durationStr = it },
                    label = { Text("Duration (Days)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = featuresText,
                    onValueChange = { featuresText = it },
                    label = { Text("Features (one per line)") },
                    placeholder = { Text("Feature 1\nFeature 2") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        validationError = "Plan name cannot be empty."
                        return@Button
                    }
                    val price = priceStr.toDoubleOrNull()
                    if (price == null || price < 0) {
                        validationError = "Please enter a valid price."
                        return@Button
                    }
                    val duration = durationStr.toIntOrNull()
                    if (duration == null || duration <= 0) {
                        validationError = "Please enter a valid duration in days."
                        return@Button
                    }

                    val featuresList = featuresText
                        .split("\n", ",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }

                    onConfirm(name, price, duration, featuresList)
                }
            ) {
                Text("Save Plan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
