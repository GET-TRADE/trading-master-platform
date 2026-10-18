package com.tradingmaster.core.session

import android.content.Context
import com.tradingmaster.core.model.RiskConfig
import com.tradingmaster.core.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Device identity: a stable installation ID plus a fingerprint of hardware-bound
 * signals. The ID is registered server-side on first approval; re-login on the
 * SAME device matches the existing approved registration. A client-supplied
 * ID alone is never treated as proof of identity — the server validates the
 * session token too.
 */
class DeviceIdentity(context: Context) {
    private val prefs = context.getSharedPreferences("tm_device", Context.MODE_PRIVATE)

    val installationId: String
    val fingerprint: String

    init {
        var id = prefs.getString("installation_id", null)
        if (id == null) {
            id = "dev-" + java.util.UUID.randomUUID().toString()
            prefs.edit().putString("installation_id", id).putLong("created_at", System.currentTimeMillis()).apply()
        }
        installationId = id
        val signals = listOf(
            id,
            android.os.Build.BOARD,
            android.os.Build.MODEL,
            android.os.Build.MANUFACTURER,
            android.os.Build.FINGERPRINT
        ).joinToString("|")
        fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(signals.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    val stableKey: String get() = "$installationId:$fingerprint"

    fun rotate() {
        val id = "dev-" + java.util.UUID.randomUUID().toString()
        prefs.edit().putString("installation_id", id).apply()
    }
}

/**
 * Persistent session + user preferences. The Supabase tokens are stored
 * encrypted with the same Keystore-backed store used for Binance keys.
 */
class SessionManager(private val context: Context, val supabase: SupabaseClient) {

    private val prefs = context.getSharedPreferences("tm_session", Context.MODE_PRIVATE)
    private val creds = com.tradingmaster.core.store.CredentialStore(context)
    private val json = Json { ignoreUnknownKeys = true }

    fun saveSession(access: String, refresh: String?, uid: String) {
        prefs.edit()
            .putString("uid", uid)
            .putString("session_json", json.encodeToString(mapOf("a" to access, "r" to (refresh ?: ""))))
            .apply()
        supabase.restoreSession(access, refresh, uid)
    }

    fun restore(): Boolean {
        val uid = prefs.getString("uid", null) ?: return false
        val raw = prefs.getString("session_json", null) ?: return false
        return try {
            val m = json.decodeFromString<Map<String, String>>(raw)
            supabase.restoreSession(m["a"] ?: return false, m["r"]?.ifEmpty { null }, uid)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun clear() {
        prefs.edit().remove("uid").remove("session_json").apply()
        supabase.clearSession()
    }

    val userId: String? get() = prefs.getString("uid", null)
    val isSignedIn: Boolean get() = userId != null && prefs.contains("session_json")

    // ---------- Risk config (persistent, versioned re-arm required) ----------
    // liveTradingArmed is NOT persisted across app updates: arming state resets
    // unless the persisted app version matches the running version.

    fun saveRiskConfig(config: RiskConfig) {
        val versionReset = config.copy(liveTradingArmed = false)
        prefs.edit()
            .putString("risk_json", json.encodeToString(versionReset))
            .putInt("armed_version", 0)
            .apply()
    }

    fun riskConfig(): RiskConfig = try {
        val stored = prefs.getString("risk_json", null)
            ?: return RiskConfig()
        json.decodeFromString<RiskConfig>(stored)
    } catch (e: Exception) {
        RiskConfig()
    }

    /** Explicit re-arm: stored with the running app version; updates clear it. */
    fun armLiveTrading(versionCode: Int) {
        val c = riskConfig()
        prefs.edit()
            .putString("risk_json", json.encodeToString(c.copy(liveTradingArmed = true)))
            .putInt("armed_version", versionCode)
            .apply()
    }

    /** Returns the risk config with live trading disarmed when the version changed. */
    fun effectiveRiskConfig(versionCode: Int): RiskConfig {
        val c = riskConfig()
        return if (prefs.getInt("armed_version", 0) != versionCode) c.copy(liveTradingArmed = false) else c
    }

    fun disarmLiveTrading() {
        val c = riskConfig()
        prefs.edit().putString("risk_json", json.encodeToString(c.copy(liveTradingArmed = false))).apply()
    }
}
