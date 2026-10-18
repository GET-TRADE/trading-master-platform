package com.tradingmaster.core.model

import kotlinx.serialization.Serializable

/**
 * Trading risk configuration. Notional is order size in USDT, NOT maximum loss.
 * Actual loss per trade depends on stop distance, leverage and quantity.
 */
@Serializable
data class RiskConfig(
    val targetNotionalUsdt: Double = 20.0,
    val preferredLeverage: Int = 50,
    val maxSimultaneousPositions: Int = 2,
    /** Live trading is OFF by default and must be explicitly re-armed after every update. */
    val liveTradingArmed: Boolean = false,
    /** Emergency stop blocks NEW entries; it never claims to close existing positions. */
    val emergencyStop: Boolean = false,
    val minRrToEnter: Double = 1.5,
    /** Account is expected to run multi-assets mode. Isolated margin is not exposed. */
    val multiAssetsModeOnly: Boolean = true
) {
    init {
        require(targetNotionalUsdt > 0) { "notional must be positive" }
        require(preferredLeverage in 1..125) { "leverage out of range" }
        require(maxSimultaneousPositions in 1..10) { "position cap out of range" }
    }
}

/** Server-enforced user state snapshot used by preflight checks. */
@Serializable
data class AccountAccessState(
    val approved: Boolean,
    val blocked: Boolean,
    val subscriptionActive: Boolean,
    val subscriptionExpiresAt: Long? = null,
    val appKey: String
)

/** Verified exchange-side facts for one symbol before any live order. */
@Serializable
data class SymbolPreflight(
    val symbol: String,
    val status: String,
    val minQty: Double,
    val stepSize: Double,
    val tickSize: Double,
    val minNotional: Double,
    val maxLeverage: Int,
    val price: Double,
    val freshPriceMillis: Long
)

/** Position/order facts for duplicate-position and margin checks. */
@Serializable
data class ExchangePositionState(
    val symbol: String,
    val positionAmt: Double,
    val openOrders: Int
)

/** Result of the full deterministic preflight before a live order. */
@Serializable
data class PreflightResult(
    val allowed: Boolean,
    val checks: List<PreflightCheck>
)

@Serializable
data class PreflightCheck(
    val name: String,
    val passed: Boolean,
    val detail: String
)

/** Order lifecycle states. Reconcile exchange state on timeouts; never blind-resubmit. */
enum class OrderState {
    PRECHECK,
    ENTRY_SUBMITTED,
    ENTRY_CONFIRMED,
    PROTECTION_SUBMITTED,
    PROTECTION_VERIFIED,
    POSITION_MONITORED,
    CLOSED,
    REJECTED,
    FAILED_RECONCILE
}
