package com.tradingmaster.pro

import android.content.Context
import com.tradingmaster.core.ai.AiGatewayClient
import com.tradingmaster.core.backtest.BacktestEngine
import com.tradingmaster.core.binance.BinanceClientFactory
import com.tradingmaster.core.binance.BinancePublicApi
import com.tradingmaster.core.binance.ScannerService
import com.tradingmaster.core.config.BackendConfig
import com.tradingmaster.core.model.PaperSimAssumptions
import com.tradingmaster.core.paper.PaperTradingEngine
import com.tradingmaster.core.session.DeviceIdentity
import com.tradingmaster.core.session.SessionManager
import com.tradingmaster.core.store.TmDatabase
import com.tradingmaster.core.supabase.SupabaseClient
import okhttp3.OkHttpClient

class ProContainer(val context: Context) {

    val backend: BackendConfig by lazy {
        BackendConfig.pro(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    }

    val supabase: SupabaseClient by lazy {
        SupabaseClient(backend.supabaseUrl, backend.supabaseAnonKey)
    }

    val deviceIdentity: DeviceIdentity by lazy {
        DeviceIdentity(context)
    }

    val session: SessionManager by lazy {
        SessionManager(context, supabase)
    }

    private val binanceClientPair: Pair<BinancePublicApi, OkHttpClient> by lazy {
        BinanceClientFactory.publicClient()
    }

    val binanceApi: BinancePublicApi get() = binanceClientPair.first

    val scannerService: ScannerService by lazy {
        ScannerService(binanceApi)
    }

    val aiGatewayClient: AiGatewayClient by lazy {
        AiGatewayClient(supabase)
    }

    val database: TmDatabase by lazy {
        TmDatabase.get(context)
    }

    val paperEngine: PaperTradingEngine by lazy {
        PaperTradingEngine(PaperSimAssumptions())
    }

    val backtestEngine: BacktestEngine by lazy {
        BacktestEngine()
    }
}
