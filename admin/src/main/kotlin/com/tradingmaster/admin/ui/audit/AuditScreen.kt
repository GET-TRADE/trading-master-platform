package com.tradingmaster.admin.ui.audit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradingmaster.admin.AdminApp
import com.tradingmaster.admin.data.AuditLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuditUiState(
    val entries: List<AuditLog> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class AuditViewModel(
    private val repository: com.tradingmaster.admin.data.AdminRepository = AdminApp.instance.adminRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AuditUiState())
    val state: StateFlow<AuditUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            repository.getAuditLogs(100).fold(
                onSuccess = { logs -> _state.value = AuditUiState(entries = logs) },
                onFailure = { e -> _state.value = AuditUiState(errorMessage = e.message ?: "Failed to load audit log") }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScreen(onBackClick: () -> Unit) {
    val vm: AuditViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audit Log") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.errorMessage != null -> Column(
                    Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error)
                }
                state.entries.isEmpty() -> Text(
                    "No audit entries yet. Privileged admin actions will appear here.",
                    Modifier.align(Alignment.Center).padding(16.dp)
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.entries, key = { it.id }) { entry ->
                        Card(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    entry.action ?: "unknown",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "actor: ${entry.actor ?: "-"} → target: ${entry.target ?: "-"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    entry.createdAt ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace
                                )
                                entry.details?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
