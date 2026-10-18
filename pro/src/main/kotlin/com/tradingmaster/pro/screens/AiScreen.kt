package com.tradingmaster.pro.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tradingmaster.core.ai.AiGatewayClient.AiHealth
import com.tradingmaster.pro.ProApp
import com.tradingmaster.pro.ProContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiViewModel(private val container: ProContainer) : ViewModel() {

    data class AiUiState(
        val busy: Boolean = false,
        val health: AiHealth? = null,
        val error: String? = null
    )

    private val _ui = MutableStateFlow(AiUiState())
    val ui: StateFlow<AiUiState> = _ui

    init {
        refreshHealth()
    }

    fun refreshHealth() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            try {
                val h = withContext(Dispatchers.IO) { container.aiGatewayClient.health() }
                _ui.value = _ui.value.copy(busy = false, health = h)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = "Failed to fetch AI gateway health: ${e.message}"
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val c: ProContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AiViewModel(c) as T
    }
}

@Composable
fun AiScreen() {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as ProApp
    val vm: AiViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = AiViewModel.Factory(app.container)
    )
    val ui by vm.ui.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("AI Gateway Status & Policy", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Gateway Provider Health", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                val h = ui.health
                if (h != null) {
                    Text(
                        "Status: ${if (h.available) "AVAILABLE" else "UNAVAILABLE"}",
                        fontWeight = FontWeight.Bold,
                        color = if (h.available) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                    )
                    Text("Configured Providers: ${if (h.configuredProviders.isEmpty()) "None" else h.configuredProviders.joinToString(", ")}", fontSize = 12.sp)
                    if (h.detail.isNotBlank()) {
                        Text("Detail: ${h.detail}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (ui.busy) {
                    Text("Checking AI gateway health...", fontSize = 12.sp)
                } else {
                    Text("AI health status unavailable.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }

                if (ui.error != null) {
                    Text(ui.error!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }

                Button(
                    onClick = { vm.refreshHealth() },
                    enabled = !ui.busy,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(if (ui.busy) "Checking..." else "Refresh AI Health")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("AI Safety & Risk Policies", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("• AI responses are generated via backend gateway models with strict fallback chain.", fontSize = 11.sp)
                Text("• AI analysis is ADVISORY ONLY and never bypasses preflight risk checks.", fontSize = 11.sp)
                Text("• If AI is unavailable, deterministic local fallbacks are displayed. No fake AI or fabricated confidence scores are used.", fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
