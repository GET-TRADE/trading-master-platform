package com.tradingmaster.core.config

/**
 * Backend configuration. Values are injected via BuildConfig fields on each
 * app module (SUPABASE_URL / SUPABASE_ANON_KEY), with defaults pointing at
 * the documented local/test backend. Production values are set through
 * environment/CI secrets, never committed.
 */
data class BackendConfig(
    val supabaseUrl: String,
    val supabaseAnonKey: String,
    val appKey: String
) {
    init {
        require(supabaseUrl.startsWith("https://") || supabaseUrl.startsWith("http://localhost")) {
            "Supabase URL must be HTTPS in production"
        }
    }

    companion object {
        /** App keys separate access records between the two user-facing apps. */
        const val APP_KEY_USER = "tm-user"
        const val APP_KEY_PRO = "tm-pro"
        const val APP_KEY_ADMIN = "tm-admin"

        fun user(url: String, anonKey: String) = BackendConfig(url, anonKey, APP_KEY_USER)
        fun pro(url: String, anonKey: String) = BackendConfig(url, anonKey, APP_KEY_PRO)
        fun admin(url: String, anonKey: String) = BackendConfig(url, anonKey, APP_KEY_ADMIN)
    }
}
