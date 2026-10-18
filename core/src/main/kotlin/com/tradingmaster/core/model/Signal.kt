package com.tradingmaster.core.model

import kotlinx.serialization.Serializable

/**
 * A fully-formed scanner signal. Produced only by the deterministic ScannerEngine.
 * A 5/5 score is a ranking result, never a probability of success.
 */
@Serializable
data class TradeSignal(
    val symbol: String,
    val direction: Direction,
    val referencePrice: Double,
    val entryLow: Double,
    val entryHigh: Double,
    val stopLoss: Double,
    val tp1: Double,
    val tp2: Double,
    val tp3: Double,
    val rr1: Double,
    val rr2: Double,
    val rr3: Double,
    /** 0..5 ranking score. */
    val score: Int,
    /** Human-readable explanation of every scoring component. */
    val scoreExplanation: List<String>,
    /** Reasons supporting the setup. */
    val reasons: List<String>,
    /** Conditions that invalidate the setup. */
    val invalidations: List<String>,
    /** Exchange candle open-time of the newest candle used, per timeframe. */
    val m5CloseTime: Long,
    val m15CloseTime: Long,
    val h1CloseTime: Long,
    /** Epoch millis when the signal was generated (device clock). */
    val generatedAt: Long,
    /** Mandatory risk warnings shown with the signal. */
    val warnings: List<String> = DEFAULT_WARNINGS
) {
    companion object {
        val DEFAULT_WARNINGS = listOf(
            "A 5/5 score ranks the setup; it is not a probability of success.",
            "Futures trading involves substantial risk of loss. Leveraged positions can be liquidated.",
            "Verify prices and protective orders on the exchange before acting."
        )
    }
}

/** The outcome of scanning one symbol. NO TRADE is a valid, first-class result. */
@Serializable
data class ScanResult(
    val symbol: String,
    val generatedAt: Long,
    val signal: TradeSignal? = null,
    /** Populated when signal == null. Never empty in that case. */
    val noTradeReasons: List<String> = emptyList(),
    val quoteTurnover24hUsd: Double? = null
) {
    val isTrade: Boolean get() = signal != null
}

/** Universe configuration for a scan run. */
@Serializable
data class ScanUniverse(
    val maxMarkets: Int = 100,
    val minTurnoverUsd: Double = 5_000_000.0,
    val manualSymbol: String? = null
)

/** Per-scan limits, deliberately conservative. */
@Serializable
data class ScannerConfig(
    val maxMarkets: Int = 100,
    val minTurnoverUsd: Double = 5_000_000.0,
    val minRelativeVolume5m: Double = 1.35,
    val rsiPeriod: Int = 14,
    val rsiLongMin: Double = 50.0,
    val rsiLongMax: Double = 78.0,
    val rsiShortMax: Double = 50.0,
    val rsiShortMin: Double = 22.0,
    val emaFast: Int = 9,
    val emaMid: Int = 21,
    val emaSlow: Int = 50,
    val adxPeriod: Int = 14,
    val minAdx: Double = 20.0,
    val atrPeriod: Int = 14,
    val stopAtrMultiple: Double = 1.5,
    /** Max distance of price from fast EMA in ATRs before entry is overextended. */
    val maxEntryExtensionAtr: Double = 1.2,
    val minRr1: Double = 1.5,
    val minRr2: Double = 2.0,
    val minRr3: Double = 2.5,
    val candlesPerTimeframe: Int = 120
) {
    init {
        require(maxMarkets in 1..100) { "maxMarkets must be 1..100" }
        require(minTurnoverUsd >= 0) { "minTurnoverUsd must be >= 0" }
    }
}
