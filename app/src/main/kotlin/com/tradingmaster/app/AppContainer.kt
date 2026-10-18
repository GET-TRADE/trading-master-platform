package com.tradingmaster.app

import android.app.Application
import com.tradingmaster.core.binance.BinanceClientFactory
import com.tradingmaster.core.binance.BinanceTimeSync
import com.tradingmaster.core.binance.ScannerService
import com.tradingmaster.core.config.BackendConfig
import com.tradingmaster.core.session.DeviceIdentity
import com.tradingmaster.core.session.SessionManager
import com.tradingmaster.core.store.CredentialStore
import com.tradingmaster.core.store.TmDatabase
import com.tradingmaster.core.supabase.SupabaseClient

/**
 * Manual dependency container. Kept intentionally simple and debuggable.
 * Binance credentials live only in the Keystore-backed CredentialStore.
 */
class AppContainer(app: Application) {
    val backend: BackendConfig = BackendConfig.user(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    val supabase: SupabaseClient = SupabaseClient(backend.supabaseUrl, backend.supabaseAnonKey)
    val session: SessionManager = SessionManager(app, supabase)
    val deviceIdentity: DeviceIdentity = DeviceIdentity(app)
    val db: TmDatabase = TmDatabase.get(app)
    val credentials: CredentialStore = CredentialStore(app)
    val binance: BinanceClientFactoryClients = BinanceClientFactoryClients()

    val scannerService: ScannerService by lazy {
        ScannerService(binance.publicApi)
    }

    /** Public (unauthenticated) Binance access is always available for scanning. */
    class BinanceClientFactoryClients {
        val publicApi = BinanceClientFactory.publicClient().first
        val timeSync = BinanceTimeSync(publicApi)
    }
}

class TradingMasterApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
