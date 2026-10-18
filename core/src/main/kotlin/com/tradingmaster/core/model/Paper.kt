package com.tradingmaster.core.model

import kotlinx.serialization.Serializable

/** A simulated position. Paper trading NEVER sends exchange orders. */
@Serializable
data class PaperTrade(
    val id: Long = 0,
    val symbol: String,
    val direction: Direction,
    val entryPrice: Double,
    val qty: Double,
    val notionalUsdt: Double,
    val stopLoss: Double,
    val tp1: Double,
    val tp2: Double,
    val tp3: Double,
    val openedAt: Long,
    val closedAt: Long? = null,
    val status: PaperStatus = PaperStatus.OPEN,
    val exitPrice: Double? = null,
    val realizedPnlUsdt: Double? = null,
    val feesUsdt: Double = 0.0,
    val slippageCostUsdt: Double = 0.0,
    val partialExits: Int = 0,
    val closeReason: PaperCloseReason? = null
)

enum class PaperStatus { OPEN, CLOSED }

enum class PaperCloseReason { STOP, TP1, TP2, TP3, MANUAL, INVALIDATED }

/** Simulation assumptions, disclosed to the user. */
@Serializable
data class PaperSimAssumptions(
    val takerFeePctPerSide: Double = 0.05,
    val adverseSlippagePctPerSide: Double = 0.03,
    /** Funding is NOT modeled; disclosed as a limitation. */
    val modelsFunding: Boolean = false
)

/**
 * Backtest assumptions: signals from completed candles, entry on next candle,
 * taker fees, adverse slippage, stop-first when a candle touches both stop and target.
 */
@Serializable
data class BacktestAssumptions(
    val takerFeePctPerSide: Double = 0.05,
    val adverseSlippagePctPerSide: Double = 0.03,
    val notionalUsdt: Double = 20.0,
    val stopFirstOnAmbiguousCandle: Boolean = true
)

@Serializable
data class BacktestReport(
    val symbol: String,
    val trades: Int,
    val wins: Int,
    val losses: Int,
    val winRatePct: Double,
    val grossPnlUsdt: Double,
    val netPnlUsdt: Double,
    val feesUsdt: Double,
    val profitFactor: Double?,
    val expectancyPerTradeUsdt: Double,
    val maxDrawdownUsdt: Double,
    val avgWinUsdt: Double,
    val avgLossUsdt: Double,
    val assumptions: BacktestAssumptions,
    val dataFromMs: Long,
    val dataToMs: Long,
    /** Disclosed limitations; never claim an edge from this report. */
    val limitations: List<String> = DEFAULT_LIMITATIONS
) {
    companion object {
        val DEFAULT_LIMITATIONS = listOf(
            "Funding payments, liquidation, spread, latency and exact exchange fills are not modeled.",
            "Stop-first handling is used when a candle touches both stop and target.",
            "This report is an estimate over the tested window and is not proof of a trading edge."
        )
    }
}
