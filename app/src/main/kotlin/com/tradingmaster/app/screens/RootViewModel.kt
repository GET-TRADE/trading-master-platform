package com.tradingmaster.app.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tradingmaster.app.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Root gate. The server's get_my_access is the source of truth:
 *  - SIGNED_OUT  -> auth screens
 *  - PENDING     -> pending-approval screen (registration alone grants nothing)
 *  - APPROVED    -> full app (subscription state is checked per feature)
 */
class RootViewModel(private val container: AppContainer) : ViewModel() {

    enum class AuthGate { CHECKING, SIGNED_OUT, PENDING, APPROVED }

    private val _gate = MutableStateFlow(AuthGate.CHECKING)
    val gate: StateFlow<AuthGate> = _gate

    init { refresh() }

    fun refresh() {
        if (!container.session.isSignedIn) {
            _gate.value = AuthGate.SIGNED_OUT
            return
        }
        viewModelScope.launch {
            try {
                val raw = container.supabase.rpc("get_my_access", "{\"app_key\":\"tm-user\"}")
                val status = raw.jsonObject["status"]?.jsonPrimitive?.content ?: "pending"
                _gate.value = if (status.equals("approved", true)) AuthGate.APPROVED else AuthGate.PENDING
            } catch (e: Exception) {
                // Network failures during a signed-in session keep the user out
                // of trading surfaces rather than letting them through.
                _gate.value = AuthGate.PENDING
            }
        }
    }

    fun signedOut() { _gate.value = AuthGate.SIGNED_OUT }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = RootViewModel(c) as T
    }

    companion object {
        fun factory(c: AppContainer) = Factory(c)
    }
}
