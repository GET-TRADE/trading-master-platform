package com.tradingmaster.admin.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradingmaster.admin.AdminApp
import com.tradingmaster.admin.data.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

data class AiConfigUiState(
    val jsonContent: String = "{}",
    val isAiConfigured: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val actionSuccessMessage: String? = null
)

class AiConfigViewModel(
    private val repository: AdminRepository = AdminApp.instance.adminRepository
) : ViewModel() {

    private val jsonParser = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(AiConfigUiState())
    val uiState: StateFlow<AiConfigUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
    }

    fun loadConfig() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, actionSuccessMessage = null) }
        viewModelScope.launch {
            val settingsRes = repository.getSettings()
            val healthRes = repository.getBackendHealth()

            _uiState.update { state ->
                val rawJson = settingsRes.getOrNull()?.aiConfigJson ?: "{}"
                val pretty = try {
                    val elem = jsonParser.parseToJsonElement(rawJson)
                    jsonParser.encodeToString(JsonElement.serializer(), elem)
                } catch (e: Exception) {
                    rawJson
                }

                state.copy(
                    jsonContent = pretty,
                    isAiConfigured = healthRes.getOrNull()?.aiConfigured ?: false,
                    isLoading = false,
                    errorMessage = settingsRes.exceptionOrNull()?.message ?: healthRes.exceptionOrNull()?.message
                )
            }
        }
    }

    fun onJsonContentChange(content: String) {
        _uiState.update { it.copy(jsonContent = content) }
    }

    fun formatJson() {
        try {
            val element = jsonParser.parseToJsonElement(_uiState.value.jsonContent)
            val pretty = jsonParser.encodeToString(JsonElement.serializer(), element)
            _uiState.update { it.copy(jsonContent = pretty, errorMessage = null) }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "JSON Syntax Error: ${e.message}") }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, actionSuccessMessage = null) }
    }

    fun saveConfig() {
        val raw = _uiState.value.jsonContent
        val element = try {
            jsonParser.parseToJsonElement(raw)
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Invalid JSON syntax: ${e.message}") }
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val res = repository.setSettingJson("ai_config", element)
            res.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            actionSuccessMessage = "AI Gateway configuration updated successfully"
                        )
                    }
                    loadConfig()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = err.message) }
                }
            )
        }
    }
}
