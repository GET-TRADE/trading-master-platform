package com.tradingmaster.admin.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradingmaster.admin.AdminApp
import com.tradingmaster.admin.data.AdminPlan
import com.tradingmaster.admin.data.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlansUiState(
    val plans: List<AdminPlan> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val actionSuccessMessage: String? = null
)

class PlansViewModel(
    private val repository: AdminRepository = AdminApp.instance.adminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlansUiState())
    val uiState: StateFlow<PlansUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, actionSuccessMessage = null) }
        viewModelScope.launch {
            val res = repository.getPlans()
            _uiState.update { state ->
                state.copy(
                    plans = res.getOrDefault(emptyList()),
                    isLoading = false,
                    errorMessage = res.exceptionOrNull()?.message
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, actionSuccessMessage = null) }
    }

    fun createPlan(name: String, pricePkr: Double, durationDays: Int, features: List<String>) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val res = repository.createPlan(name, pricePkr, durationDays, features)
            res.fold(
                onSuccess = {
                    _uiState.update { it.copy(actionSuccessMessage = "Plan '$name' created successfully") }
                    loadData()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                }
            )
        }
    }

    fun updatePlan(planId: String, name: String, pricePkr: Double, durationDays: Int, features: List<String>) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val res = repository.updatePlan(planId, name, pricePkr, durationDays, features)
            res.fold(
                onSuccess = {
                    _uiState.update { it.copy(actionSuccessMessage = "Plan '$name' updated successfully") }
                    loadData()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                }
            )
        }
    }
}
