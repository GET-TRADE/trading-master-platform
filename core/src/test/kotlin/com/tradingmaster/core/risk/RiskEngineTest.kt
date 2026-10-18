package com.tradingmaster.core.risk

import com.tradingmaster.core.model.AccountAccessState
import com.tradingmaster.core.model.Direction
import com.tradingmaster.core.model.ExchangePositionState
import com.tradingmaster.core.model.OrderState
import com.tradingmaster.core.model.RiskConfig
import com.tradingmaster.core.model.SymbolPreflight
import com.tradingmaster.core.model.TradeSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskEngineTest {

    private val signal = TradeSignal(
        symbol = "BTCUSDT", direction = Direction.LONG,
        referencePrice = 100.0, entryLow = 99.8, entryHigh = 100.2,
        stopLoss = 99.0, tp1 = 101.5, tp2 = 102.0, tp3 = 102.5,
        rr1 = 1.5, rr2 = 2.0, rr3 = 2.5, score = 4,
        scoreExplanation = listOf("x"), reasons = listOf("x"), invalidations = listOf("x"),
        m5CloseTime = 1L, m15CloseTime = 1L, h1CloseTime = 1L, generatedAt = 1L
    )

    private val preflight = SymbolPreflight(
        symbol = "BTCUSDT", status = "TRADING",
        minQty = 0.001, stepSize = 0.001, tickSize = 0.01,
        minNotional = 5.0, maxLeverage = 50, price = 100.0, freshPriceMillis = 1_000_000
    )

    private fun input(
        config: RiskConfig = RiskConfig(),
        access: AccountAccessState = AccountAccessState(true, false, true, 2_000_000_000_000, "tm-user"),
        balance: Double = 100.0,
        positions: List<ExchangePositionState> = emptyList(),
        duplicate: Boolean = false
    ) = RiskEngine.PreflightInput(
        signal = signal, config = config, access = access, preflight = preflight,
        positions = positions, availableBalanceUsdt = balance,
        duplicateSymbolOrderExists = duplicate,
        priceFreshMs = 995_000, nowMs = 1_000_000
    )

    @Test
    fun `all checks pass allows the order`() {
        val r = RiskEngine.preflight(input())
        assertTrue("failed: " + r.checks.filter { !it.passed }.joinToString(), r.allowed)
        assertEquals(13, r.checks.size)
    }

    @Test
    fun `emergency stop blocks entry`() {
        val r = RiskEngine.preflight(input(config = RiskConfig(emergencyStop = true)))
        assertFalse(r.allowed)
        assertTrue(r.checks.any { it.name == "emergency_stop_off" && !it.passed })
    }

    @Test
    fun `live trading must be explicitly armed`() {
        val r = RiskEngine.preflight(input(config = RiskConfig(liveTradingArmed = false)))
        assertFalse(r.allowed)
        assertTrue(r.checks.any { it.name == "live_trading_armed" && !it.passed })
    }

    @Test
    fun `expired subscription blocks entry even if the UI shows active`() {
        val r = RiskEngine.preflight(input(access = AccountAccessState(true, false, false, 1_000_000_000_000, "tm-user")))
        assertFalse(r.allowed)
    }

    @Test
    fun `position cap blocks the third position`() {
        val pos = listOf(
            ExchangePositionState("ETHUSDT", 1.0, 0),
            ExchangePositionState("SOLUSDT", 2.0, 0)
        )
        val r = RiskEngine.preflight(input(positions = pos))
        assertFalse(r.allowed)
    }

    @Test
    fun `duplicate symbol blocks entry`() {
        val r = RiskEngine.preflight(input(positions = listOf(ExchangePositionState("BTCUSDT", 0.5, 1))))
        assertFalse(r.allowed)
    }

    @Test
    fun `insufficient balance blocks entry`() {
        val r = RiskEngine.preflight(input(balance = 0.05))
        assertFalse(r.allowed)
    }

    @Test
    fun `stale price blocks entry`() {
        val r = RiskEngine.preflight(input())
        // default priceFreshMs=995_000 vs now=1_000_000 is 5s -> fresh. Make it stale:
        val stale = RiskEngine.preflight(
            RiskEngine.PreflightInput(
                signal, RiskConfig(),
                AccountAccessState(true, false, true, null, "tm-user"),
                preflight, emptyList(), 100.0, false,
                priceFreshMs = 980_000, nowMs = 1_000_000
            )
        )
        assertFalse(stale.allowed)
        assertTrue(r.checks.any { it.name == "price_fresh" && it.passed })
    }

    @Test
    fun `leverage is capped by symbol maximum`() {
        val capped = preflight.copy(maxLeverage = 20)
        val r = RiskEngine.preflight(
            RiskEngine.PreflightInput(
                signal, RiskConfig(preferredLeverage = 50),
                AccountAccessState(true, false, true, null, "tm-user"),
                capped, emptyList(), 100.0, false, 995_000, 1_000_000
            )
        )
        assertTrue(r.checks.any { it.name == "leverage_compatible" && it.detail.contains("using 20x") })
    }

    @Test
    fun `quantity respects step size`() {
        assertEquals(0.004, RiskEngine.roundStep(0.00456, 0.001), 1e-12)
        assertEquals(0.0, RiskEngine.roundStep(0.0004, 0.001), 1e-12)
        assertEquals(123.45, RiskEngine.roundTick(123.4567, 0.01), 1e-9)
    }
}

class OrderStateMachineTest {

    private val rec = OrderRecord(
        symbol = "BTCUSDT", clientOrderId = "c1",
        state = OrderState.PRECHECK, updatedAt = 1L
    )

    @Test
    fun `happy path transitions are legal`() {
        val path = listOf(
            OrderState.ENTRY_SUBMITTED, OrderState.ENTRY_CONFIRMED,
            OrderState.PROTECTION_SUBMITTED, OrderState.PROTECTION_VERIFIED,
            OrderState.POSITION_MONITORED, OrderState.CLOSED
        )
        var r = rec
        path.forEach { r = OrderStateMachine.transition(r, it, 2L) }
        assertEquals(OrderState.CLOSED, r.state)
        assertTrue(OrderStateMachine.isProtected(rec.copy(state = OrderState.PROTECTION_VERIFIED)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cannot skip protection verification`() {
        var r = OrderStateMachine.transition(rec, OrderState.ENTRY_SUBMITTED, 2L)
        r = OrderStateMachine.transition(r, OrderState.ENTRY_CONFIRMED, 3L)
        OrderStateMachine.transition(r, OrderState.POSITION_MONITORED, 4L) // must throw
    }

    @Test
    fun `timeout forces reconciliation`() {
        val submitted = OrderStateMachine.transition(rec, OrderState.ENTRY_SUBMITTED, 2L)
        val timedOut = OrderStateMachine.onTimeout(submitted, 3L)
        assertEquals(OrderState.FAILED_RECONCILE, timedOut.state)
        assertTrue(OrderStateMachine.blocksNewEntries(timedOut))
    }

    @Test
    fun `unverified protection never counts as protected`() {
        assertFalse(OrderStateMachine.isProtected(rec.copy(state = OrderState.PROTECTION_SUBMITTED)))
    }
}
