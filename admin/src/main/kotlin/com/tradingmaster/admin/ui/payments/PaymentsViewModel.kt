package com.tradingmaster.admin.ui.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradingmaster.admin.AdminApp
import com.tradingmaster.admin.data.AdminPayment
import com.tradingmaster.admin.data.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaymentsUiState(
    val payments: List<AdminPayment> = emptyList(),
    val statusFilter: String = "ALL",
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val actionSuccessMessage: String? = null
)

class PaymentsViewModel(
    private val repository: AdminRepository = AdminApp.instance.adminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentsUiState())
    val uiState: StateFlow<PaymentsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, actionSuccessMessage = null) }
        viewModelScope.launch {
            val res = repository.getPayments()
            _uiState.update { state ->
                state.copy(
                    payments = res.getOrDefault(emptyList()),
                    isLoading = false,
                    errorMessage = res.exceptionOrNull()?.message
                )
            }
        }
    }

    fun onStatusFilterChange(filter: String) {
        _uiState.update { it.copy(statusFilter = filter) }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, actionSuccessMessage = null) }
    }

    fun verifyPayment(payment: AdminPayment, verified: Boolean, reference: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val res = repository.verifyPayment(payment.id, verified, reference)
            res.fold(
                onSuccess = {
                    val msg = if (verified) "Payment verified successfully" else "Payment marked as rejected"
                    _uiState.update { it.copy(actionSuccessMessage = msg) }
                    loadData()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                }
            )
        }
    }
}
