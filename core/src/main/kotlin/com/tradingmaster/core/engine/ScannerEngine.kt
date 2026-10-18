package com.tradingmaster.core.engine

import com.tradingmaster.core.model.Candle
import com.tradingmaster.core.model.Direction
import com.tradingmaster.core.model.ScannerConfig
import com.tradingmaster.core.model.ScanResult
import com.tradingmaster.core.model.Timeframe
import com.tradingmaster.core.model.TradeSignal
import kotlin.math.abs
import kotlin.math.max

/**
 * Deterministic futures scanner. Same inputs always produce the same output.
 * Uses only CLOSED candles. NO TRADE is a valid result and thresholds never
 * auto-lower to generate more signals.
 *
 * Timeframe roles:
 *  - 5m  : entry timing, volume confirmation, entry extension
 *  - 15m : setup confirmation (trend + momentum)
 *  - 1h  : trend confirmation (direction gate)
 */
object ScannerEngine {

    data class Input(
        val symbol: String,
        val m5: List<Candle>,
        val m15: List<Candle>,
        val h1: List<Candle>,
        val quoteTurnover24hUsd: Double? = null,
        val nowMs: Long = System.currentTimeMillis()
    )

    fun scan(input: Input, config: ScannerConfig): ScanResult {
        val reasons = ArrayList<String>()

        // ---- Data completeness and freshness ----
        if (input.m5.size < 60 || input.m15.size < 60 || input.h1.size < 60) {
            reasons.add("Insufficient candle history (need >= 60 closed candles per timeframe).")
        }
        if (!Indicators.seriesLooksContiguous(input.m5, Timeframe.M5.candleMillis())) {
            reasons.add("5m candle series has gaps; data may be stale or incomplete.")
        }
        if (reasons.isNotEmpty()) {
            return noTrade(input, reasons)
        }

        val m5 = input.m5
        val m15 = input.m15
        val h1 = input.h1

        val closes5 = Indicators.closes(m5)
        val closes15 = Indicators.closes(m15)
        val closes1h = Indicators.closes(h1)

        // ---- Indicator computation ----
        val emaFast5 = Indicators.ema(closes5, config.emaFast).last()
        val emaMid5 = Indicators.ema(closes5, config.emaMid).last()
        val emaSlow5 = Indicators.ema(closes5, config.emaSlow).last()
        val emaMid5Prev = Indicators.ema(closes5, config.emaMid).let { it[it.size - 2] }

        val emaMid15 = Indicators.ema(closes15, config.emaMid).last()
        val emaSlow15 = Indicators.ema(closes15, config.emaSlow).last()

        val emaMid1h = Indicators.ema(closes1h, config.emaMid).last()
        val emaSlow1h = Indicators.ema(closes1h, config.emaSlow).last()

        val rsi5 = Indicators.rsi(closes5, config.rsiPeriod).last()
        val rsi15 = Indicators.rsi(closes15, config.rsiPeriod).last()

        val atr5 = Indicators.atr(m5, config.atrPeriod).last()
        val adx15 = Indicators.adx(m15, config.adxPeriod).last()

        val last = m5.last()
        val price = last.close
        val volAvg5 = Indicators.avgVolume(m5.map { it.volume }, 20, m5.size - 1)
        val relVol = if (volAvg5 > 0) last.volume / volAvg5 else 0.0

        val (swingHigh, swingLow) = Indicators.swingLevels(m15)
        val (swingHigh1h, swingLow1h) = Indicators.swingLevels(h1)

        val atr = if (atr5 > 0) atr5 else Double.MAX_VALUE

        // ---- Directional consistency across timeframes ----
        val long1h = emaMid1h > emaSlow1h
        val short1h = emaMid1h < emaSlow1h
        val long15 = emaMid15 > emaSlow15 && adx15 >= config.minAdx
        val short15 = emaMid15 < emaSlow15 && adx15 >= config.minAdx
        val long5 = emaFast5 > emaMid5 && emaMid5 > emaSlow5
        val short5 = emaFast5 < emaMid5 && emaMid5 < emaSlow5
        val fastSlopeUp = emaMid5 > emaMid5Prev
        val fastSlopeDown = emaMid5 < emaMid5Prev

        val direction = when {
            long1h && long15 && long5 -> Direction.LONG
            short1h && short15 && short5 -> Direction.SHORT
            else -> {
                reasons.add("Timeframes conflict: 1h/15m/5m trends are not aligned.")
                return noTrade(input, reasons)
            }
        }

        if (direction == Direction.LONG && !fastSlopeUp) {
            reasons.add("Fast EMA slope is not confirming the LONG direction.")
        }
        if (direction == Direction.SHORT && !fastSlopeDown) {
            reasons.add("Fast EMA slope is not confirming the SHORT direction.")
        }

        // ---- Momentum ----
        val rsiOk = if (direction == Direction.LONG) {
            rsi5 in config.rsiLongMin..config.rsiLongMax && rsi15 >= 50.0
        } else {
            rsi5 in config.rsiShortMin..config.rsiShortMax && rsi15 <= 50.0
        }
        if (!rsiOk) reasons.add(
            "RSI momentum not confirming (5m ${"%.1f".format(rsi5)}, 15m ${"%.1f".format(rsi15)})."
        )

        // ---- Volume confirmation ----
        if (relVol < config.minRelativeVolume5m) {
            reasons.add("Relative 5m volume %.2fx below required %.2fx.".format(relVol, config.minRelativeVolume5m))
        }

        // ---- Entry extension / chase prevention ----
        val extension = abs(price - emaFast5) / atr
        if (extension > config.maxEntryExtensionAtr) {
            reasons.add("Entry overextended: price is %.2f ATR from fast EMA (max %.2f).".format(extension, config.maxEntryExtensionAtr))
        }

        // ---- Volatility sanity ----
        if (atr5 <= 0 || Indicators.stdevReturns(closes5, 20) <= 0) {
            reasons.add("Volatility is zero or invalid; data may be stale.")
        }

        if (reasons.isNotEmpty()) return noTrade(input, reasons)

        // ---- Stop and targets ----
        val stopLoss: Double
        val entryLow: Double
        val entryHigh: Double
        if (direction == Direction.LONG) {
            stopLoss = price - config.stopAtrMultiple * atr
            entryLow = max(price - 0.25 * atr, emaFast5 * 0.9995)
            entryHigh = price + 0.10 * atr
        } else {
            stopLoss = price + config.stopAtrMultiple * atr
            entryLow = price - 0.10 * atr
            entryHigh = minOf(price + 0.25 * atr, emaFast5 * 1.0005)
        }

        // Stop invalid if the 5m range already violates it.
        val stopValid = if (direction == Direction.LONG) stopLoss < entryLow else stopLoss > entryHigh
        if (!stopValid) {
            reasons.add("Stop placement is invalid relative to the entry zone.")
            return noTrade(input, reasons)
        }

        val risk = abs(entryMid(entryLow, entryHigh) - stopLoss)
        if (risk <= 0) {
            reasons.add("Risk distance is invalid (zero).")
            return noTrade(input, reasons)
        }

        val tp1 = entryMid(entryLow, entryHigh) + (if (direction == Direction.LONG) 1.0 else -1.0) * config.minRr1 * risk
        val tp2 = entryMid(entryLow, entryHigh) + (if (direction == Direction.LONG) 1.0 else -1.0) * config.minRr2 * risk
        val tp3 = entryMid(entryLow, entryHigh) + (if (direction == Direction.LONG) 1.0 else -1.0) * config.minRr3 * risk

        // ---- Support/resistance feasibility ----
        val invalidations = ArrayList<String>()
        if (direction == Direction.LONG) {
            swingHigh?.let { if (tp1 > it) { reasons.add("TP1 sits above the nearest swing high; target not feasible."); } }
            swingLow1h?.let {
                if (it < stopLoss) invalidations.add("Close of the 1h candle below %.6f invalidates the setup.".format(it))
                if (it > stopLoss) invalidations.add("Loss of 15m structure at the nearest swing low invalidates the setup.")
            }
            invalidations.add("5m close below the stop invalidates the setup immediately.")
        } else {
            swingLow?.let { if (tp1 < it) { reasons.add("TP1 sits below the nearest swing low; target not feasible."); } }
            swingHigh1h?.let {
                if (it > stopLoss) invalidations.add("Close of the 1h candle above %.6f invalidates the setup.".format(it))
                if (it < stopLoss) invalidations.add("Reclaim of 15m structure at the nearest swing high invalidates the setup.")
            }
            invalidations.add("5m close above the stop invalidates the setup immediately.")
        }

        if (reasons.isNotEmpty()) return noTrade(input, reasons)

        // ---- Score (0..5 ranking; not a probability) ----
        val scoreParts = ArrayList<String>()
        var score = 0
        if (direction == Direction.LONG && emaFast5 > emaMid5 && emaMid5 > emaSlow5) {
            score++; scoreParts.add("+1 5m EMA stack aligned (9>21>50).")
        }
        if (direction == Direction.SHORT && emaFast5 < emaMid5 && emaMid5 < emaSlow5) {
            score++; scoreParts.add("+1 5m EMA stack aligned (9<21<50).")
        }
        if (adx15 >= config.minAdx + 8.0) { score++; scoreParts.add("+1 strong 15m trend (ADX %.1f).".format(adx15)) }
        if (relVol >= config.minRelativeVolume5m * 1.5) { score++; scoreParts.add("+1 strong 5m volume (%.2fx average).".format(relVol)) }
        if (extension <= config.maxEntryExtensionAtr / 2.0) { score++; scoreParts.add("+1 entry close to fast EMA (not extended).") }
        if ((direction == Direction.LONG && rsi5 in 55.0..70.0) || (direction == Direction.SHORT && rsi5 in 30.0..45.0)) {
            score++; scoreParts.add("+1 RSI in the healthy momentum window.")
        }
        scoreParts.add("Score ranks this setup against others; it is not a probability of success.")

        val reasonsList = ArrayList<String>()
        reasonsList.add("1h and 15m trend aligned with the 5m stack (%s).".format(if (direction == Direction.LONG) "up" else "down"))
        reasonsList.add("ADX %.1f confirms trend strength on 15m.".format(adx15))
        reasonsList.add("Volume %.2fx the 20-candle 5m average.".format(relVol))
        reasonsList.add("Entry within %.2f ATR of the 9 EMA.".format(extension))
        if (swingHigh != null || swingLow != null) reasonsList.add("Nearest swing levels: high %s, low %s.".format(swingHigh ?: "-", swingLow ?: "-"))

        val signal = TradeSignal(
            symbol = input.symbol,
            direction = direction,
            referencePrice = price,
            entryLow = entryLow,
            entryHigh = entryHigh,
            stopLoss = stopLoss,
            tp1 = tp1,
            tp2 = tp2,
            tp3 = tp3,
            rr1 = config.minRr1,
            rr2 = config.minRr2,
            rr3 = config.minRr3,
            score = score.coerceIn(0, 5),
            scoreExplanation = scoreParts,
            reasons = reasonsList,
            invalidations = invalidations,
            m5CloseTime = last.closeTime,
            m15CloseTime = m15.last().closeTime,
            h1CloseTime = h1.last().closeTime,
            generatedAt = input.nowMs
        )
        return ScanResult(
            symbol = input.symbol,
            generatedAt = input.nowMs,
            signal = signal,
            quoteTurnover24hUsd = input.quoteTurnover24hUsd
        )
    }

    private fun entryMid(low: Double, high: Double): Double = (low + high) / 2.0

    private fun noTrade(input: Input, reasons: List<String>): ScanResult {
        require(reasons.isNotEmpty()) { "NO TRADE must always carry at least one reason" }
        return ScanResult(
            symbol = input.symbol,
            generatedAt = input.nowMs,
            signal = null,
            noTradeReasons = reasons.distinct(),
            quoteTurnover24hUsd = input.quoteTurnover24hUsd
        )
    }
}
