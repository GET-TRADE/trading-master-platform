package com.tradingmaster.pro.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tradingmaster.pro.ProContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Root ViewModel gate for Trading Master Pro.
 * Server status via `get_my_access` with app_key "tm-pro" determines access gate:
 * - SIGNED_OUT -> AuthScreen
 * - PENDING -> PendingScreen (registration alone does not grant trading access)
 * - APPROVED -> ProHomeScreen
 */
class RootViewModel(private val container: ProContainer) : ViewModel() {

    enum class AuthGate { CHECKING, SIGNED_OUT, PENDING, APPROVED }

    private val _gate = MutableStateFlow(AuthGate.CHECKING)
    val gate: StateFlow<AuthGate> = _gate

    init {
        refresh()
    }

    fun refresh() {
        if (!container.session.isSignedIn) {
            _gate.value = AuthGate.SIGNED_OUT
            return
        }
        viewModelScope.launch {
            try {
                val raw = container.supabase.rpc("get_my_access", "{\"app_key\":\"tm-pro\"}")
                val status = raw.jsonObject["status"]?.jsonPrimitive?.content ?: "pending"
                _gate.value = if (status.equals("approved", ignoreCase = true)) AuthGate.APPROVED else AuthGate.PENDING
            } catch (e: Exception) {
                _gate.value = AuthGate.PENDING
            }
        }
    }

    fun signedOut() {
        _gate.value = AuthGate.SIGNED_OUT
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val c: ProContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = RootViewModel(c) as T
    }

    companion object {
        fun factory(c: ProContainer) = Factory(c)
    }
}
