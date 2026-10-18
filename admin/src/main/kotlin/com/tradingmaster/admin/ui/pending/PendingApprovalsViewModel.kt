package com.tradingmaster.admin.ui.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradingmaster.admin.AdminApp
import com.tradingmaster.admin.data.AdminRepository
import com.tradingmaster.admin.data.AdminUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PendingApprovalsUiState(
    val pendingUsers: List<AdminUser> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val actionSuccessMessage: String? = null
)

class PendingApprovalsViewModel(
    private val repository: AdminRepository = AdminApp.instance.adminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PendingApprovalsUiState())
    val uiState: StateFlow<PendingApprovalsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, actionSuccessMessage = null) }
        viewModelScope.launch {
            val res = repository.getUsers()
            _uiState.update { state ->
                val allUsers = res.getOrDefault(emptyList())
                val pendingList = allUsers.filter { user ->
                    user.status.lowercase() == "pending" ||
                            user.deviceStatus?.lowercase() == "pending"
                }
                state.copy(
                    pendingUsers = pendingList,
                    isLoading = false,
                    errorMessage = res.exceptionOrNull()?.message
                )
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
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

    fun approveDevice(user: AdminUser) {
        val deviceId = user.deviceId ?: return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val res = repository.approveDevice(deviceId)
            res.fold(
                onSuccess = {
                    _uiState.update { it.copy(actionSuccessMessage = "Device approved successfully") }
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
                    _uiState.update { it.copy(actionSuccessMessage = "Device access revoked") }
                    loadData()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                }
            )
        }
    }
}
