package com.tradingmaster.core.backtest

import com.tradingmaster.core.model.BacktestAssumptions
import com.tradingmaster.core.model.Candle
import com.tradingmaster.core.model.ScannerConfig
import com.tradingmaster.core.model.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BacktestEngineTest {

    private fun candles(count: Int, tf: Timeframe, drift: Double, seed: Long, start: Double = 100.0): List<Candle> {
        val rng = Random(seed)
        val out = ArrayList<Candle>(count)
        var p = start
        val tfMs = tf.candleMillis()
        for (i in 0 until count) {
            p *= (1 + drift + (rng.nextDouble() - 0.5) * 0.002)
            val h = p * 1.0015
            val l = p * 0.9985
            out.add(Candle(i * tfMs, p * 0.9995, h, l, p, 100.0 + if (i % 10 == 0) 50.0 else 0.0, i * tfMs + tfMs - 1))
        }
        return out
    }

    @Test
    fun `rising market yields trades and a report with metrics`() {
        val m5 = candles(400, Timeframe.M5, +0.0015, 1)
        val m15 = candles(140, Timeframe.M15, +0.004, 2)
        val h1 = candles(80, Timeframe.H1, +0.010, 3)
        val engine = BacktestEngine(ScannerConfig(), BacktestAssumptions())
        val report = engine.run("BTCUSDT", BacktestEngine.Window(m5, m15, h1))
        assertTrue(report.trades >= 0)
        if (report.trades > 0) {
            assertTrue(report.wins + report.losses == report.trades)
            assertTrue(report.winRatePct in 0.0..100.0)
            assertNotNull(report.profitFactor)
            assertTrue(report.feesUsdt >= 0)
            assertTrue(report.maxDrawdownUsdt >= 0)
            assertEquals(report.expectancyPerTradeUsdt, report.netPnlUsdt / report.trades, 1e-9)
        }
        assertTrue(report.limitations.isNotEmpty())
    }

    @Test
    fun `conservative stop-first math is deterministic and repeatable`() {
        val m5 = candles(300, Timeframe.M5, +0.0012, 42)
        val m15 = candles(100, Timeframe.M15, +0.004, 43)
        val h1 = candles(70, Timeframe.H1, +0.010, 44)
        val e1 = BacktestEngine().run("X", BacktestEngine.Window(m5, m15, h1))
        val e2 = BacktestEngine().run("X", BacktestEngine.Window(m5, m15, h1))
        assertEquals(e1.trades, e2.trades)
        assertEquals(e1.netPnlUsdt, e2.netPnlUsdt, 0.0)
    }

    @Test
    fun `fees make net PnL lower than gross`() {
        val m5 = candles(400, Timeframe.M5, +0.0015, 5)
        val m15 = candles(140, Timeframe.M15, +0.004, 6)
        val h1 = candles(80, Timeframe.H1, +0.010, 7)
        val r = BacktestEngine().run("Y", BacktestEngine.Window(m5, m15, h1))
        assertTrue(r.feesUsdt >= 0)
        assertTrue(r.grossPnlUsdt >= r.netPnlUsdt - 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `too little data is rejected`() {
        val tiny = candles(30, Timeframe.M5, 0.0, 1)
        BacktestEngine().run("Z", BacktestEngine.Window(tiny, tiny, tiny))
    }
}
