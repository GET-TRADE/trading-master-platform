package com.tradingmaster.core.risk

import com.tradingmaster.core.model.AccountAccessState
import com.tradingmaster.core.model.Direction
import com.tradingmaster.core.model.ExchangePositionState
import com.tradingmaster.core.model.PreflightCheck
import com.tradingmaster.core.model.PreflightResult
import com.tradingmaster.core.model.RiskConfig
import com.tradingmaster.core.model.SymbolPreflight
import com.tradingmaster.core.model.TradeSignal
import kotlin.math.abs
import kotlin.math.floor

/**
 * Deterministic pre-entry risk engine. Runs BEFORE every live order.
 * AI has no authority here. Any single failure blocks the entry.
 */
object RiskEngine {

    data class PreflightInput(
        val signal: TradeSignal,
        val config: RiskConfig,
        val access: AccountAccessState,
        val preflight: SymbolPreflight,
        val positions: List<ExchangePositionState>,
        val availableBalanceUsdt: Double,
        val duplicateSymbolOrderExists: Boolean,
        val priceFreshMs: Long,
        val nowMs: Long
    )

    fun preflight(input: PreflightInput): PreflightResult {
        val checks = ArrayList<PreflightCheck>()
        val s = input.signal
        val p = input.preflight

        fun check(name: String, passed: Boolean, detail: String) {
            checks.add(PreflightCheck(name, passed, detail))
        }

        check("account_approved", input.access.approved && !input.access.blocked,
            if (input.access.approved && !input.access.blocked) "Account approved." else "Account is not approved or is blocked.")

        check("subscription_active", input.access.subscriptionActive,
            if (input.access.subscriptionActive) "Subscription active until ${input.access.subscriptionExpiresAt}." else "Subscription expired or inactive.")

        check("live_trading_armed", input.config.liveTradingArmed,
            if (input.config.liveTradingArmed) "Live trading is explicitly armed by the user." else "Live trading is not armed; order blocked.")

        check("emergency_stop_off", !input.config.emergencyStop,
            if (input.config.emergencyStop) "EMERGENCY STOP IS ON: new entries are blocked." else "Emergency stop is off.")

        check("price_fresh", input.nowMs - input.priceFreshMs <= 10_000,
            "Price age ${input.nowMs - input.priceFreshMs} ms (limit 10 s).")

        check("symbol_trading", p.status == "TRADING", "Symbol status is ${p.status}.")

        // Effective leverage: configured preference capped by the symbol's max.
        val effLev = input.config.preferredLeverage.coerceAtMost(p.maxLeverage)
        check("leverage_compatible", effLev >= 1,
            "Requested ${input.config.preferredLeverage}x, symbol max ${p.maxLeverage}x; using ${effLev}x.")

        // Quantity from notional and leverage with exchange rounding.
        val qty = roundStep(input.config.targetNotionalUsdt / p.price, p.stepSize)
        val notional = qty * p.price
        check("min_notional", notional >= p.minNotional,
            "Order notional %.2f USDT vs symbol minimum %.2f USDT.".format(notional, p.minNotional))

        check("qty_filter", qty >= p.minQty && kotlin.math.abs(qty - roundStep(qty, p.stepSize)) <= p.stepSize * 1e-6,
            "Quantity $qty within LOT_SIZE (min ${p.minQty}, step ${p.stepSize}).")

        check("balance_available", input.availableBalanceUsdt >= notional / effLev,
            "Available %.4f USDT vs required margin %.4f USDT.".format(input.availableBalanceUsdt, notional / effLev))

        check("position_cap", input.positions.count { it.positionAmt != 0.0 } < input.config.maxSimultaneousPositions,
            "${input.positions.count { it.positionAmt != 0.0 }} open of max ${input.config.maxSimultaneousPositions}.")

        check("no_duplicate_entry", !input.duplicateSymbolOrderExists && input.positions.none { it.symbol == s.symbol && it.positionAmt != 0.0 },
            "No existing position/order for ${s.symbol}.")

        val riskUsdt = abs(notional / effLev * ((abs((if (s.direction == Direction.LONG) s.stopLoss - p.price else p.price - s.stopLoss)) / p.price)))
        check("stop_risk_bounded", riskUsdt < input.availableBalanceUsdt * 0.5,
            "Estimated stop-loss risk %.4f USDT (notional %,.2f, leverage %dx). Notional is NOT max loss.".format(riskUsdt, notional, effLev))

        check("rr_acceptable", s.rr1 >= input.config.minRrToEnter,
            "RR to TP1 is ${s.rr1} vs minimum ${input.config.minRrToEnter}.")

        val priceConsistent = if (s.direction == Direction.LONG) p.price in s.entryLow * 0.995..s.entryHigh * 1.005
                              else p.price in s.entryLow * 0.995..s.entryHigh * 1.005
        check("price_in_zone", priceConsistent,
            "Live price %.6f vs entry zone %.6f..%.6f.".format(p.price, s.entryLow, s.entryHigh))

        return PreflightResult(allowed = checks.all { it.passed }, checks = checks)
    }

    fun roundStep(value: Double, step: Double): Double {
        if (step <= 0.0) return value
        // Decimal arithmetic: binary float (12345 * 0.01) yields 123.46000000000001.
        return java.math.BigDecimal(value.toString())
            .divide(java.math.BigDecimal(step.toString()), 18, java.math.RoundingMode.FLOOR)
            .setScale(0, java.math.RoundingMode.FLOOR)
            .multiply(java.math.BigDecimal(step.toString()))
            .toDouble()
    }

    /** Exchange price rounding to tick size for limit orders. */
    fun roundTick(price: Double, tick: Double): Double = roundStep(price, tick)
}
