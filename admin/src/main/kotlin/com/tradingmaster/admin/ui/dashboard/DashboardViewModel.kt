package com.tradingmaster.admin.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradingmaster.admin.AdminApp
import com.tradingmaster.admin.data.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardSummary(
    val totalUsers: Int = 0,
    val pendingUsers: Int = 0,
    val pendingPayments: Int = 0,
    val healthStatus: String = "checking",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class DashboardViewModel(
    private val repository: AdminRepository = AdminApp.instance.adminRepository
) : ViewModel() {

    private val _summary = MutableStateFlow(DashboardSummary())
    val summary: StateFlow<DashboardSummary> = _summary.asStateFlow()

    init {
        refreshSummary()
    }

    fun refreshSummary() {
        _summary.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val usersRes = repository.getUsers()
            val paymentsRes = repository.getPayments()
            val healthRes = repository.getBackendHealth()

            val users = usersRes.getOrDefault(emptyList())
            val pendingUsersCount = users.count { it.status.lowercase() == "pending" }
            val payments = paymentsRes.getOrDefault(emptyList())
            val pendingPaymentsCount = payments.count { it.status.lowercase() == "pending" }
            val healthStatus = healthRes.map { it.status }.getOrDefault("unknown")

            _summary.update {
                it.copy(
                    totalUsers = users.size,
                    pendingUsers = pendingUsersCount,
                    pendingPayments = pendingPaymentsCount,
                    healthStatus = healthStatus,
                    isLoading = false,
                    errorMessage = usersRes.exceptionOrNull()?.message
                )
            }
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            repository.signOut()
            onSignedOut()
        }
    }
}
