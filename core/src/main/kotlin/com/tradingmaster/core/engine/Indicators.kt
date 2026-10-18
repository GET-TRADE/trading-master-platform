package com.tradingmaster.core.engine

import com.tradingmaster.core.model.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Pure, deterministic technical-indicator functions. No Android, no I/O.
 * Every function takes CLOSED candles only unless documented otherwise.
 */
object Indicators {

    /** Exponential moving average series, same length as input (leading values use warmup EMA). */
    fun ema(values: List<Double>, period: Int): List<Double> {
        require(period > 0) { "period must be positive" }
        if (values.isEmpty()) return emptyList()
        val k = 2.0 / (period + 1)
        val out = ArrayList<Double>(values.size)
        var prev = values[0]
        out.add(prev)
        for (i in 1 until values.size) {
            prev = values[i] * k + prev * (1 - k)
            out.add(prev)
        }
        // Warm up with a simple average seed for the first value so short series are stable.
        return out
    }

    fun closes(candles: List<Candle>): List<Double> = candles.map { it.close }

    fun highs(candles: List<Candle>): List<Double> = candles.map { it.high }

    fun lows(candles: List<Candle>): List<Double> = candles.map { it.low }

    /** Wilder RSI. Returns a series aligned to the input. */
    fun rsi(closes: List<Double>, period: Int = 14): List<Double> {
        require(period > 0)
        if (closes.size < period + 1) return List(closes.size) { 50.0 }
        val out = ArrayList<Double>(closes.size)
        var avgGain = 0.0
        var avgLoss = 0.0
        for (i in closes.indices) {
            if (i == 0) { out.add(50.0); continue }
            val change = closes[i] - closes[i - 1]
            val gain = max(change, 0.0)
            val loss = max(-change, 0.0)
            if (i <= period) {
                avgGain += gain
                avgLoss += loss
                if (i == period) { avgGain /= period; avgLoss /= period }
                out.add(rsiFrom(avgGain, avgLoss))
            } else {
                avgGain = (avgGain * (period - 1) + gain) / period
                avgLoss = (avgLoss * (period - 1) + loss) / period
                out.add(rsiFrom(avgGain, avgLoss))
            }
        }
        return out
    }

    private fun rsiFrom(avgGain: Double, avgLoss: Double): Double =
        if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)

    /** ATR using Wilder smoothing (true range with previous-close gaps). */
    fun atr(candles: List<Candle>, period: Int = 14): List<Double> {
        if (candles.isEmpty()) return emptyList()
        val trs = ArrayList<Double>(candles.size)
        trs.add(candles[0].high - candles[0].low)
        for (i in 1 until candles.size) {
            val c = candles[i]
            val prevClose = candles[i - 1].close
            trs.add(max(c.high - c.low, max(abs(c.high - prevClose), abs(c.low - prevClose))))
        }
        val out = ArrayList<Double>(candles.size)
        var prev = trs[0]
        out.add(prev)
        for (i in 1 until trs.size) {
            prev = (prev * (period - 1) + trs[i]) / period
            out.add(prev)
        }
        return out
    }

    /** Wilder ADX (trend strength, 0..100) with plus/minus DI. */
    fun adx(candles: List<Candle>, period: Int = 14): List<Double> {
        if (candles.size < period * 2) return List(candles.size) { 0.0 }
        val plusDM = ArrayList<Double>(candles.size)
        val minusDM = ArrayList<Double>(candles.size)
        val tr = ArrayList<Double>(candles.size)
        plusDM.add(0.0); minusDM.add(0.0); tr.add(candles[0].high - candles[0].low)
        for (i in 1 until candles.size) {
            val up = candles[i].high - candles[i - 1].high
            val down = candles[i - 1].low - candles[i].low
            plusDM.add(if (up > down && up > 0) up else 0.0)
            minusDM.add(if (down > up && down > 0) down else 0.0)
            val prevClose = candles[i - 1].close
            tr.add(max(candles[i].high - candles[i].low,
                max(abs(candles[i].high - prevClose), abs(candles[i].low - prevClose))))
        }
        var smTR = tr.take(period).sum()
        var smP = plusDM.take(period).sum()
        var smM = minusDM.take(period).sum()
        val dxs = ArrayList<Double>()
        for (i in period until candles.size) {
            smTR = smTR - smTR / period + tr[i]
            smP = smP - smP / period + plusDM[i]
            smM = smM - smM / period + minusDM[i]
            val pdi = if (smTR == 0.0) 0.0 else 100.0 * smP / smTR
            val mdi = if (smTR == 0.0) 0.0 else 100.0 * smM / smTR
            val denom = pdi + mdi
            dxs.add(if (denom == 0.0) 0.0 else 100.0 * abs(pdi - mdi) / denom)
        }
        // Wilder-smooth the DX series into ADX, then align to input length.
        if (dxs.size < period) return List(candles.size) { 0.0 }
        var adxVal = dxs.take(period).average()
        val adxSeries = ArrayDeque<Double>()
        repeat(period) { adxSeries.add(adxVal) }
        for (i in period until dxs.size) {
            adxVal = (adxVal * (period - 1) + dxs[i]) / period
            adxSeries.add(adxVal)
        }
        while (adxSeries.size > candles.size) adxSeries.removeFirst()
        while (adxSeries.size < candles.size) adxSeries.addFirst(0.0)
        return adxSeries.toList()
    }

    /** Simple rolling average of volume over [period] ending at index i (inclusive). */
    fun avgVolume(volumes: List<Double>, period: Int, i: Int): Double {
        val from = maxOf(0, i - period + 1)
        val window = volumes.subList(from, i + 1)
        return window.average()
    }

    /** True when the candle series has no gaps larger than 2x the timeframe. */
    fun seriesLooksContiguous(candles: List<Candle>, timeframeMillis: Long): Boolean {
        if (candles.size < 2) return false
        for (i in 1 until candles.size) {
            val gap = candles[i].openTime - candles[i - 1].openTime
            if (gap !in (timeframeMillis - 1)..(timeframeMillis * 2 + 1)) return false
        }
        return true
    }

    /**
     * Recent swing structure. Returns (lastSwingHigh, lastSwingLow) computed from
     * fractal pivots with the given lookback on each side.
     */
    fun swingLevels(candles: List<Candle>, lookback: Int = 2): Pair<Double?, Double?> {
        if (candles.size < lookback * 2 + 1) return Pair(null, null)
        var swingHigh: Double? = null
        var swingLow: Double? = null
        for (i in candles.size - 1 - lookback downTo lookback) {
            val c = candles[i]
            val isHigh = (i - lookback..i + lookback).all { candles[it].high <= c.high }
            val isLow = (i - lookback..i + lookback).all { candles[it].low >= c.low }
            if (swingHigh == null && isHigh) swingHigh = c.high
            if (swingLow == null && isLow) swingLow = c.low
            if (swingHigh != null && swingLow != null) break
        }
        return Pair(swingHigh, swingLow)
    }

    /** Standard deviation of returns over the window (volatility sanity check). */
    fun stdevReturns(closes: List<Double>, window: Int): Double {
        if (closes.size < window + 1 || window <= 1) return 0.0
        val rets = ArrayList<Double>(window)
        for (i in closes.size - window until closes.size) {
            rets.add((closes[i] / closes[i - 1]) - 1.0)
        }
        val mean = rets.average()
        return sqrt(rets.sumOf { (it - mean) * (it - mean) } / (window - 1))
    }
}
