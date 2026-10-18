package com.tradingmaster.core.paper

import com.tradingmaster.core.model.Candle
import com.tradingmaster.core.model.Direction
import com.tradingmaster.core.model.PaperCloseReason
import com.tradingmaster.core.model.PaperSimAssumptions
import com.tradingmaster.core.model.PaperStatus
import com.tradingmaster.core.model.TradeSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperTradingEngineTest {

    private val a = PaperSimAssumptions()
    private val engine = PaperTradingEngine(a)

    private val longSignal = TradeSignal(
        symbol = "BTCUSDT", direction = Direction.LONG,
        referencePrice = 100.0, entryLow = 99.9, entryHigh = 100.1,
        stopLoss = 98.5, tp1 = 102.0, tp2 = 103.0, tp3 = 104.0,
        rr1 = 1.5, rr2 = 2.0, rr3 = 2.5, score = 3,
        scoreExplanation = listOf(), reasons = listOf(), invalidations = listOf(),
        m5CloseTime = 0, m15CloseTime = 0, h1CloseTime = 0, generatedAt = 0
    )

    private fun candle(low: Double, high: Double, close: Double = high) =
        Candle(0L, close, high, low, close, 1.0, 1L)

    @Test
    fun `entry pays taker fee and adverse slippage`() {
        val t = engine.open(longSignal, nowMs = 5L)
        assertEquals(20.0, t.notionalUsdt, 1e-9)
        val expectedEntry = 100.0 * (1 + a.adverseSlippagePctPerSide / 100)
        assertEquals(expectedEntry, t.entryPrice, 1e-9)
        assertTrue(t.feesUsdt > 0)
        assertEquals(20.0 * a.takerFeePctPerSide / 100, t.feesUsdt, 1e-9)
        assertEquals(PaperStatus.OPEN, t.status)
    }

    @Test
    fun `stop candle closes the trade at a loss with fees and slippage`() {
        var t = engine.open(longSignal, 5L)
        t = engine.step(t, candle(98.0, 99.5)) // touches stop
        assertEquals(PaperStatus.CLOSED, t.status)
        assertEquals(PaperCloseReason.STOP, t.closeReason)
        assertTrue("loss expected, got ${t.realizedPnlUsdt}", (t.realizedPnlUsdt ?: 0.0) < 0)
        assertTrue(t.feesUsdt > 20.0 * a.takerFeePctPerSide / 100) // entry + exit fee
    }

    @Test
    fun `candle touching both stop and target resolves stop first`() {
        var t = engine.open(longSignal, 5L)
        t = engine.step(t, candle(97.0, 103.0)) // both levels
        assertEquals(PaperCloseReason.STOP, t.closeReason)
        assertEquals(t.stopLoss * (1 - a.adverseSlippagePctPerSide / 100), t.exitPrice!!, 1e-9)
    }

    @Test
    fun `tp1 closes half the position and keeps it running`() {
        var t = engine.open(longSignal, 5L)
        val qty0 = t.qty
        t = engine.step(t, candle(99.5, 102.5))
        assertEquals(PaperStatus.OPEN, t.status)
        assertEquals(1, t.partialExits)
        assertEquals(qty0 / 2, t.qty, 1e-9)
        assertTrue((t.realizedPnlUsdt ?: 0.0) > 0)
    }

    @Test
    fun `full tp1-to-tp3 sequence closes in profit`() {
        var t = engine.open(longSignal, 5L)
        t = engine.step(t, candle(99.5, 102.5))  // TP1, half out
        t = engine.step(t, candle(100.0, 103.5)) // TP2 area closes remaining
        assertEquals(PaperStatus.CLOSED, t.status)
        assertTrue("net profit expected, got ${t.realizedPnlUsdt}", (t.realizedPnlUsdt ?: 0.0) > 0)
    }

    @Test
    fun `flat candle changes nothing`() {
        var t = engine.open(longSignal, 5L)
        val before = t.copy()
        t = engine.step(t, candle(99.5, 100.8))
        assertEquals(before.qty, t.qty, 0.0)
        assertEquals(PaperStatus.OPEN, t.status)
    }

    @Test
    fun `short trade mirrors correctly`() {
        val shortSignal = longSignal.copy(direction = Direction.SHORT, stopLoss = 101.5, tp1 = 98.0, tp2 = 97.0, tp3 = 96.0)
        var t = engine.open(shortSignal, 5L)
        val expectedEntry = 100.0 * (1 - a.adverseSlippagePctPerSide / 100)
        assertEquals(expectedEntry, t.entryPrice, 1e-9)
        t = engine.step(t, candle(98.0, 100.5)) // tp1 below; stop above not touched
        assertEquals(PaperStatus.OPEN, t.status) // TP1 is a partial exit; trade keeps running
        assertEquals(1, t.partialExits)
        assertTrue((t.realizedPnlUsdt ?: 0.0) > 0)
        t = engine.step(t, candle(97.5, 99.0)) // tp2 closes the remainder
        assertEquals(PaperStatus.CLOSED, t.status)
        assertTrue((t.realizedPnlUsdt ?: 0.0) > 0)
    }
}
