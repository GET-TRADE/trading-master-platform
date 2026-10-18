package com.tradingmaster.admin.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradingmaster.admin.AdminApp
import com.tradingmaster.admin.data.AdminPlan
import com.tradingmaster.admin.data.AdminRepository
import com.tradingmaster.admin.data.AdminUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UsersUiState(
    val users: List<AdminUser> = emptyList(),
    val plans: List<AdminPlan> = emptyList(),
    val searchQuery: String = "",
    val statusFilter: String = "ALL",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val actionSuccessMessage: String? = null
)

class UsersViewModel(
    private val repository: AdminRepository = AdminApp.instance.adminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UsersUiState())
    val uiState: StateFlow<UsersUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, actionSuccessMessage = null) }
        viewModelScope.launch {
            val usersRes = repository.getUsers()
            val plansRes = repository.getPlans()

            _uiState.update { state ->
                state.copy(
                    users = usersRes.getOrDefault(emptyList()),
                    plans = plansRes.getOrDefault(emptyList()),
                    isLoading = false,
                    errorMessage = usersRes.exceptionOrNull()?.message ?: plansRes.exceptionOrNull()?.message
                )
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onStatusFilterChange(filter: String) {
        _uiState.update { it.copy(statusFilter = filter) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, actionSuccessMessage = null) }
    }

    fun setUserStatus(user: AdminUser, newStatus: String) {
        val appKey = user.appKey ?: "tm-user"
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val res = repository.setUserStatus(user.userId, appKey, newStatus)
            res.fold(
                onSuccess = {
                    _uiState.update { it.copy(actionSuccessMessage = "User status set to $newStatus") }
                    loadData()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                }
            )
        }
    }

    fun deleteUser(user: AdminUser) {
        val appKey = user.appKey ?: "tm-user"
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val res = repository.deleteUser(user.userId, appKey)
            res.fold(
                onSuccess = {
                    _uiState.update { it.copy(actionSuccessMessage = "User deleted successfully") }
                    loadData()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                }
            )
        }
    }

    fun approveDevice(user: AdminUser) {
        val deviceId = user.deviceId ?: return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val res = repository.approveDevice(deviceId)
            res.fold(
                onSuccess = {
                    _uiState.update { it.copy(actionSuccessMessage = "Device approved") }
                    loadData()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                }
            )
        }
    }

    fun revokeDevice(user: AdminUser) {
        val deviceId = user.deviceId ?: return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val res = repository.revokeDevice(deviceId)
            res.fold(
                onSuccess = {
                    _uiState.update { it.copy(actionSuccessMessage = "Device revoked") }
                    loadData()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                }
            )
        }
    }

    fun setSubscription(user: AdminUser, planId: String, days: Int) {
        val appKey = user.appKey ?: "tm-user"
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val res = repository.setSubscription(user.userId, appKey, planId, days)
            res.fold(
                onSuccess = {
                    _uiState.update { it.copy(actionSuccessMessage = "Subscription updated successfully") }
                    loadData()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                }
            )
        }
    }
}
