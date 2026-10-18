package com.tradingmaster.core.binance

import com.tradingmaster.core.engine.ScannerEngine
import com.tradingmaster.core.model.Candle
import com.tradingmaster.core.model.ScanResult
import com.tradingmaster.core.model.ScannerConfig
import com.tradingmaster.core.model.ScanUniverse
import com.tradingmaster.core.model.Timeframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Scan orchestration: builds the eligible universe, fetches candles for each
 * symbol in parallel (bounded), and runs the deterministic ScannerEngine.
 */
class ScannerService(
    private val publicApi: BinancePublicApi,
    private val config: ScannerConfig = ScannerConfig()
) {

    /** Eligible universe: TRADING-status USDT perpetuals with sufficient turnover. */
    suspend fun eligibleUniverse(universe: ScanUniverse = ScanUniverse()): List<EligibleSymbol> {
        val info = withBinanceRetry { publicApi.exchangeInfo() }
        val tickers = withBinanceRetry { publicApi.ticker24h() }
        val turnoverBySymbol = tickers.associate { it.symbol to (it.quoteVolume.toDoubleOrNull() ?: 0.0) }

        val perpetuals = info.symbols.filter {
            it.status.equals("TRADING", ignoreCase = true) &&
                (it.contractType == null || it.contractType.equals("PERPETUAL", ignoreCase = true)) &&
                it.quoteAsset == "USDT" && it.baseAsset.isNotEmpty()
        }

        universe.manualSymbol?.let { manual ->
            val m = perpetuals.firstOrNull { it.symbol.equals(manual, ignoreCase = true) }
                ?: throw BinanceError.Exchange(-1121, "Symbol $manual is not a supported USDT perpetual or is not trading.")
            return listOf(m.toEligible(turnoverBySymbol[m.symbol]))
        }

        return perpetuals
            .map { it.toEligible(turnoverBySymbol[it.symbol] ?: 0.0) }
            .filter { it.turnover24hUsd >= universe.minTurnoverUsd }
            .sortedByDescending { it.turnover24hUsd }
            .take(universe.maxMarkets)
    }

    private fun ExchangeSymbol.toEligible(turnover: Double) = EligibleSymbol(
        symbol = symbol,
        stepSize = filters.firstOrNull { it.filterType == "LOT_SIZE" }?.stepSize ?: 0.001,
        tickSize = filters.firstOrNull { it.filterType == "PRICE_FILTER" }?.tickSize ?: 0.01,
        minQty = filters.firstOrNull { it.filterType == "LOT_SIZE" }?.minQty ?: 0.001,
        minNotional = filters.firstOrNull { it.filterType == "MIN_NOTIONAL" }?.notional ?: 5.0,
        turnover24hUsd = turnover
    )

    data class EligibleSymbol(
        val symbol: String,
        val stepSize: Double,
        val tickSize: Double,
        val minQty: Double,
        val minNotional: Double,
        val turnover24hUsd: Double
    )

    /**
     * Scan the whole universe. Signals and NO TRADE reasons are recorded for every symbol.
     * Returns one ScanResult per symbol plus per-symbol errors.
     */
    suspend fun scanUniverse(universe: ScanUniverse = ScanUniverse()): ScanSummary = coroutineScope {
        val eligible = eligibleUniverse(universe)
        val gate = Semaphore(6) // bounded concurrency against exchange limits
        val results = eligible.map { es ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    try {
                        scanSymbol(es) to null
                    } catch (e: Exception) {
                        null to ScanError(es.symbol, e.message ?: "unknown error")
                    }
                }
            }
        }.awaitAll().toList()

        ScanSummary(
            results = results.mapNotNull { it.first },
            errors = results.mapNotNull { it.second },
            universeSize = eligible.size
        )
    }

    /** Fetch the three timeframes and run the engine on one symbol. */
    suspend fun scanSymbol(es: EligibleSymbol): ScanResult {
        val m5 = BinanceClientFactory.klines(publicApi, es.symbol, Timeframe.M5, config.candlesPerTimeframe)
        val m15 = BinanceClientFactory.klines(publicApi, es.symbol, Timeframe.M15, config.candlesPerTimeframe)
        val h1 = BinanceClientFactory.klines(publicApi, es.symbol, Timeframe.H1, config.candlesPerTimeframe)
        return ScannerEngine.scan(
            ScannerEngine.Input(es.symbol, m5, m15, h1, es.turnover24hUsd),
            config
        )
    }

    data class ScanSummary(
        val results: List<ScanResult>,
        val errors: List<ScanError>,
        val universeSize: Int
    ) {
        val signals: List<ScanResult> get() = results.filter { it.isTrade }.sortedByDescending { it.signal!!.score }
    }

    data class ScanError(val symbol: String, val message: String)
}
