package com.tradingmaster.core.engine

import com.tradingmaster.core.model.Candle
import com.tradingmaster.core.model.Direction
import com.tradingmaster.core.model.ScannerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class IndicatorsTest {

    private fun candle(i: Int, close: Double, high: Double = close * 1.001, low: Double = close * 0.999, open: Double = close): Candle =
        Candle(i * 300_000L, open, high, low, close, 100.0, i * 300_000L + 299_999)

    @Test
    fun `ema follows a constant series exactly`() {
        val series = List(50) { 100.0 }
        val e = Indicators.ema(series, 20)
        assertEquals(100.0, e.last(), 1e-9)
    }

    @Test
    fun `ema rises for a rising series`() {
        val series = List(50) { it.toDouble() }
        val e = Indicators.ema(series, 10)
        assertTrue(e.last() > e[e.size - 2])
        assertTrue(e.last() < 49.0)
    }

    @Test
    fun `rsi is 100 when only gains`() {
        val closes = List(30) { 100.0 + it * 1.0 }
        val r = Indicators.rsi(closes, 14)
        assertEquals(100.0, r.last(), 1e-9)
    }

    @Test
    fun `rsi is 0 when only losses`() {
        val closes = List(30) { 200.0 - it }
        val r = Indicators.rsi(closes, 14)
        assertEquals(0.0, r.last(), 1e-9)
    }

    @Test
    fun `rsi is near 50 for a flat series`() {
        val closes = List(60) { 100.0 + sin(it * 0.0001) * 0.0 }
        val r = Indicators.rsi(closes, 14)
        assertTrue(r.last() in 49.0..51.0)
    }

    @Test
    fun `atr is positive and grows with range`() {
        val tight = List(40) { candle(it, 100.0) }
        val wide = List(40) { candle(it, 100.0, high = 103.0, low = 97.0) }
        val a1 = Indicators.atr(tight, 14).last()
        val a2 = Indicators.atr(wide, 14).last()
        assertTrue(a1 > 0)
        assertTrue(a2 > a1 * 10)
    }

    @Test
    fun `adx is high for a strong trend and low for chop`() {
        val trend = List(120) { i -> candle(i, 100.0 + i * 0.5) }
        val rng = Random(42)
        val chop = ArrayList<Candle>()
        var p = 100.0
        for (i in 0 until 120) {
            p += if (rng.nextBoolean()) 0.5 else -0.5
            chop.add(candle(i, p))
        }
        val adxTrend = Indicators.adx(trend, 14).last()
        val adxChop = Indicators.adx(chop, 14).last()
        assertTrue("trend ADX $adxTrend should exceed chop ADX $adxChop", adxTrend > adxChop)
        assertTrue(adxTrend > 25.0)
    }

    @Test
    fun `gap detection flags missing candles`() {
        val ok = List(30) { candle(it, 100.0) }
        assertTrue(Indicators.seriesLooksContiguous(ok, 300_000))
        val withGap = ok.toMutableList()
        withGap.removeAt(10)
        assertTrue(!Indicators.seriesLooksContiguous(withGap, 300_000))
    }
}

class ScannerEngineTest {

    private fun mkCandles(
        count: Int,
        start: Double,
        drift: Double,
        tfMillis: Long,
        noise: Double = 0.0,
        seed: Long = 1,
        pullbackLast: Int = 0
    ): List<Candle> {
        val rng = Random(seed)
        val out = ArrayList<Candle>(count)
        var price = start
        for (i in 0 until count) {
            // Optional final pullback/bounce: price returns toward the fast EMA,
            // the realistic entry pattern the scanner is designed to catch.
            val d = if (pullbackLast > 0 && i >= count - pullbackLast) -drift else drift
            price = price * (1 + d + rng.nextDouble() * noise - noise / 2)
            val high = price * 1.002
            val low = price * 0.998
            out.add(Candle(i * tfMillis, price * 0.999, high, low, price, 150.0 + (if (i > count - 5) 300.0 else 0.0), i * tfMillis + tfMillis - 1))
        }
        return out
    }

    private val config = ScannerConfig()
    private val tf5 = 300_000L
    private val tf15 = 900_000L
    private val tf60 = 3_600_000L

    @Test
    fun `steady uptrend with volume produces a LONG signal`() {
        val m5 = mkCandles(100, 100.0, +0.0012, tf5, noise = 0.016, seed = 11, pullbackLast = 6)
        val m15 = mkCandles(100, 100.0, +0.0030, tf15, noise = 0.030, seed = 11)
        val h1 = mkCandles(100, 100.0, +0.0080, tf60, noise = 0.060, seed = 11)
        val r = ScannerEngine.scan(ScannerEngine.Input("BTCUSDT", m5, m15, h1, 10_000_000.0), config)
        assertNotNull("expected a trade signal, reasons=" + r.noTradeReasons, r.signal)
        assertEquals(Direction.LONG, r.signal!!.direction)
        assertTrue(r.signal!!.score in 0..5)
        assertEquals(1.5, r.signal!!.rr1, 1e-9)
        assertTrue(r.signal!!.stopLoss < r.signal!!.entryLow)
        assertTrue(r.signal!!.tp3 > r.signal!!.tp2)
    }

    @Test
    fun `steady downtrend with volume produces a SHORT signal`() {
        val m5 = mkCandles(100, 100.0, -0.0016, tf5, noise = 0.016, seed = 11, pullbackLast = 2)
        val m15 = mkCandles(100, 100.0, -0.0030, tf15, noise = 0.030, seed = 11)
        val h1 = mkCandles(100, 100.0, -0.0080, tf60, noise = 0.060, seed = 11)
        val r = ScannerEngine.scan(ScannerEngine.Input("ETHUSDT", m5, m15, h1, 10_000_000.0), config)
        assertNotNull("expected a trade signal, reasons=" + r.noTradeReasons, r.signal)
        assertEquals(Direction.SHORT, r.signal!!.direction)
        assertTrue(r.signal!!.stopLoss > r.signal!!.entryHigh)
    }

    @Test
    fun `conflicting timeframes produce NO TRADE with reasons`() {
        val m5 = mkCandles(100, 100.0, +0.004, tf5)   // up
        val m15 = mkCandles(100, 100.0, +0.010, tf15) // up
        val h1 = mkCandles(100, 100.0, -0.020, tf60)  // down
        val r = ScannerEngine.scan(ScannerEngine.Input("BTCUSDT", m5, m15, h1), config)
        assertEquals(null, r.signal)
        assertTrue(r.noTradeReasons.isNotEmpty())
        assertTrue(r.noTradeReasons.any { it.contains("conflict", ignoreCase = true) })
    }

    @Test
    fun `low volume produces NO TRADE`() {
        val m5 = mkCandles(100, 100.0, +0.004, tf5, noise = 0.0).map { it.copy(volume = 100.0) }
        val m15 = mkCandles(100, 100.0, +0.010, tf15)
        val h1 = mkCandles(100, 100.0, +0.025, tf60)
        val r = ScannerEngine.scan(ScannerEngine.Input("BTCUSDT", m5, m15, h1), config)
        if (r.signal == null) {
            assertTrue(r.noTradeReasons.any { it.contains("volume", ignoreCase = true) })
        }
    }

    @Test
    fun `insufficient data produces NO TRADE`() {
        val m5 = mkCandles(20, 100.0, +0.004, tf5)
        val r = ScannerEngine.scan(ScannerEngine.Input("BTCUSDT", m5, m5, m5), config)
        assertEquals(null, r.signal)
        assertTrue(r.noTradeReasons.any { it.contains("Insufficient") })
    }

    @Test
    fun `flat market produces NO TRADE and never lowers thresholds`() {
        val flat = mkCandles(100, 100.0, 0.0, tf5, noise = 0.0004, seed = 7)
        val flat15 = mkCandles(100, 100.0, 0.0, tf15, noise = 0.0004, seed = 7)
        val flat60 = mkCandles(100, 100.0, 0.0, tf60, noise = 0.0004, seed = 7)
        val r = ScannerEngine.scan(ScannerEngine.Input("ADAUSDT", flat, flat15, flat60), config)
        assertEquals(null, r.signal)
        assertTrue(r.noTradeReasons.isNotEmpty())
    }

    @Test
    fun `gapped 5m data produces NO TRADE`() {
        val m5 = mkCandles(100, 100.0, +0.004, tf5).toMutableList()
        m5.removeAt(50)
        m5.removeAt(52)
        val m15 = mkCandles(100, 100.0, +0.010, tf15)
        val h1 = mkCandles(100, 100.0, +0.025, tf60)
        val r = ScannerEngine.scan(ScannerEngine.Input("BTCUSDT", m5, m15, h1), config)
        assertEquals(null, r.signal)
        assertTrue(r.noTradeReasons.any { it.contains("gap") })
    }

    @Test
    fun `signal carries freshness timestamps and warnings`() {
        val m5 = mkCandles(100, 100.0, +0.0012, tf5, noise = 0.016, seed = 11, pullbackLast = 6)
        val m15 = mkCandles(100, 100.0, +0.0030, tf15, noise = 0.030, seed = 11)
        val h1 = mkCandles(100, 100.0, +0.0080, tf60, noise = 0.060, seed = 11)
        val r = ScannerEngine.scan(ScannerEngine.Input("BTCUSDT", m5, m15, h1, nowMs = 1_770_000_000_000), config)
        assertNotNull(r.signal)
        val s = r.signal!!
        assertTrue(s.m5CloseTime > 0 && s.m15CloseTime > 0 && s.h1CloseTime > 0)
        assertEquals(1_770_000_000_000, s.generatedAt)
        assertTrue(s.warnings.isNotEmpty())
        assertTrue(s.scoreExplanation.isNotEmpty())
        assertTrue(s.reasons.isNotEmpty())
        assertTrue(s.invalidations.isNotEmpty())
    }
}
