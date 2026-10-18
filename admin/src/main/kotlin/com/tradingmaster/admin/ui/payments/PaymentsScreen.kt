package com.tradingmaster.admin.ui.payments

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tradingmaster.admin.data.AdminPayment
import com.tradingmaster.admin.ui.components.AdminTopBar
import com.tradingmaster.admin.ui.components.EmptyState
import com.tradingmaster.admin.ui.components.ErrorBanner
import com.tradingmaster.admin.ui.components.LoadingState
import com.tradingmaster.admin.ui.components.StatusChip
import com.tradingmaster.admin.ui.theme.StatusApproved
import com.tradingmaster.admin.ui.theme.StatusBlocked

@OptIn(ExperimentalLayoutFlow::class, ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(
    onBackClick: () -> Unit,
    viewModel: PaymentsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedPaymentForVerification by remember { mutableStateOf<AdminPayment?>(null) }
    var referenceInput by remember { mutableStateOf("") }

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
                title = "Payments Verification",
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
                placeholder = { Text("Search by email, reference, ID, method...") },
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
            val filterOptions = listOf("ALL", "pending", "verified", "rejected")
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

            if (uiState.isLoading && uiState.payments.isEmpty()) {
                LoadingState(message = "Loading payment records...")
            } else {
                val filteredPayments = uiState.payments.filter { payment ->
                    val q = uiState.searchQuery.trim().lowercase()
                    val matchesQuery = q.isEmpty() ||
                            payment.id.lowercase().contains(q) ||
                            (payment.email?.lowercase()?.contains(q) == true) ||
                            (payment.userId?.lowercase()?.contains(q) == true) ||
                            (payment.reference?.lowercase()?.contains(q) == true) ||
                            (payment.method?.lowercase()?.contains(q) == true)

                    val matchesFilter = when (uiState.statusFilter.lowercase()) {
                        "all" -> true
                        else -> payment.status.lowercase() == uiState.statusFilter.lowercase()
                    }

                    matchesQuery && matchesFilter
                }

                if (filteredPayments.isEmpty()) {
                    EmptyState(message = "No payments found matching your criteria.")
                } else {
                    Text(
                        text = "Showing ${filteredPayments.size} payments",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    filteredPayments.forEach { payment ->
                        PaymentCard(
                            payment = payment,
                            onVerifyClick = {
                                selectedPaymentForVerification = payment
                                referenceInput = payment.reference ?: ""
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Payment Verification Dialog
    selectedPaymentForVerification?.let { payment ->
        AlertDialog(
            onDismissRequest = { selectedPaymentForVerification = null },
            title = { Text("Verify Payment", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Payment ID: ${payment.id}")
                    Text("User Email: ${payment.email ?: payment.userId ?: "N/A"}")
                    Text("Amount: PKR ${payment.amountPkr}")
                    Text("Method: ${payment.method ?: "Manual"}")

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = referenceInput,
                        onValueChange = { referenceInput = it },
                        label = { Text("Transaction Reference ID") },
                        placeholder = { Text("Enter TxHash or Binance Pay ref") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = payment
                        val ref = referenceInput
                        selectedPaymentForVerification = null
                        viewModel.verifyPayment(p, verified = true, reference = ref)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusApproved)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.height(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Approve & Verify")
                }
            },
            dismissButton = {
                Row {
                    OutlinedButton(
                        onClick = {
                            val p = payment
                            val ref = referenceInput
                            selectedPaymentForVerification = null
                            viewModel.verifyPayment(p, verified = false, reference = ref)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusBlocked)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.height(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reject")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(onClick = { selectedPaymentForVerification = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
private fun PaymentCard(
    payment: AdminPayment,
    onVerifyClick: () -> Unit
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
                        text = payment.email ?: "User ID: ${payment.userId ?: "Unknown"}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Method: ${payment.method ?: "Binance Pay / TRC20"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(status = payment.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Amount: PKR ${payment.amountPkr}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                payment.createdAt?.let { created ->
                    Text(
                        text = created,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            payment.reference?.let { ref ->
                if (ref.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ref ID: $ref",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            payment.proofNote?.let { note ->
                if (note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Note: $note",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (payment.status.lowercase() == "pending") {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onVerifyClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Verify / Review Payment")
                }
            }
        }
    }
}
