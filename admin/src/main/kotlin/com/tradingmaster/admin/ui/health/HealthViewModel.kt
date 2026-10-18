package com.tradingmaster.admin.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradingmaster.admin.AdminApp
import com.tradingmaster.admin.data.AdminRepository
import com.tradingmaster.admin.data.BackendHealthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HealthUiState(
    val healthState: BackendHealthState = BackendHealthState(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val actionSuccessMessage: String? = null
)

class HealthViewModel(
    private val repository: AdminRepository = AdminApp.instance.adminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthUiState())
    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()

    init {
        checkHealth()
    }

    fun checkHealth() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, actionSuccessMessage = null) }
        viewModelScope.launch {
            val res = repository.getBackendHealth()
            _uiState.update { state ->
                res.fold(
                    onSuccess = { health ->
                        state.copy(
                            healthState = health,
                            isLoading = false
                        )
                    },
                    onFailure = { err ->
                        state.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Failed to perform health check."
                        )
                    }
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, actionSuccessMessage = null) }
    }
}
