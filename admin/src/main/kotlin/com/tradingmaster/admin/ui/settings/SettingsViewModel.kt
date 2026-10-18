package com.tradingmaster.admin.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradingmaster.admin.AdminApp
import com.tradingmaster.admin.data.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val binancePayId: String = "",
    val trc20Address: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val actionSuccessMessage: String? = null
)

class SettingsViewModel(
    private val repository: AdminRepository = AdminApp.instance.adminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, actionSuccessMessage = null) }
        viewModelScope.launch {
            val res = repository.getSettings()
            _uiState.update { state ->
                res.fold(
                    onSuccess = { setting ->
                        state.copy(
                            binancePayId = setting.binancePayId,
                            trc20Address = setting.trc20Address,
                            isLoading = false
                        )
                    },
                    onFailure = { err ->
                        state.copy(
                            isLoading = false,
                            errorMessage = err.message
                        )
                    }
                )
            }
        }
    }

    fun onBinancePayIdChange(value: String) {
        _uiState.update { it.copy(binancePayId = value) }
    }

    fun onTrc20AddressChange(value: String) {
        _uiState.update { it.copy(trc20Address = value) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, actionSuccessMessage = null) }
    }

    fun saveSettings() {
        val current = _uiState.value
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val res1 = repository.setSettingString("binance_pay_id", current.binancePayId.trim())
            val res2 = repository.setSettingString("trc20_address", current.trc20Address.trim())

            val err = res1.exceptionOrNull()?.message ?: res2.exceptionOrNull()?.message
            if (err != null) {
                _uiState.update { it.copy(isSaving = false, errorMessage = err) }
            } else {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        actionSuccessMessage = "Payment receiver settings saved successfully"
                    )
                }
            }
        }
    }
}
