package com.tradingmaster.admin

import android.app.Application
import com.tradingmaster.admin.data.AdminRepository
import com.tradingmaster.core.config.BackendConfig
import com.tradingmaster.core.session.SessionManager
import com.tradingmaster.core.supabase.SupabaseClient

class AdminContainer(app: Application) {
    val backendConfig: BackendConfig = BackendConfig.admin(
        BuildConfig.SUPABASE_URL,
        BuildConfig.SUPABASE_ANON_KEY
    )
    val supabaseClient: SupabaseClient = SupabaseClient(
        backendConfig.supabaseUrl,
        backendConfig.supabaseAnonKey
    )
    val sessionManager: SessionManager = SessionManager(app, supabaseClient).apply {
        restore()
    }
    val adminRepository: AdminRepository = AdminRepository(
        supabaseClient,
        sessionManager
    )
}

class AdminApp : Application() {

    lateinit var container: AdminContainer
        private set

    val backendConfig: BackendConfig get() = container.backendConfig
    val supabaseClient: SupabaseClient get() = container.supabaseClient
    val sessionManager: SessionManager get() = container.sessionManager
    val adminRepository: AdminRepository get() = container.adminRepository

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AdminContainer(this)
    }

    companion object {
        lateinit var instance: AdminApp
            private set
    }
}
