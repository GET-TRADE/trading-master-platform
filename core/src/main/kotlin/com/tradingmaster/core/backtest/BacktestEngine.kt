package com.tradingmaster.core.backtest

import com.tradingmaster.core.engine.ScannerEngine
import com.tradingmaster.core.model.BacktestAssumptions
import com.tradingmaster.core.model.BacktestReport
import com.tradingmaster.core.model.Candle
import com.tradingmaster.core.model.Direction
import com.tradingmaster.core.model.ScannerConfig

/**
 * Chronological backtester. Strict no-lookahead:
 *  - at each step, signals are computed ONLY from closed candles up to that point
 *  - entry happens on the NEXT candle, at its open, with adverse slippage and taker fee
 *  - when a candle touches both stop and target, the stop is assumed first (conservative)
 *
 * The report carries disclosed limitations and never claims an edge.
 */
class BacktestEngine(
    private val config: ScannerConfig = ScannerConfig(),
    private val assumptions: BacktestAssumptions = BacktestAssumptions()
) {

    data class Window(val m5: List<Candle>, val m15: List<Candle>, val h1: List<Candle>)

    data class TradeOutcome(
        val entryIdx: Int,
        val direction: Direction,
        val entryPrice: Double,
        val exitPrice: Double,
        val grossPnlUsdt: Double,
        val feesUsdt: Double
    ) {
        val netPnlUsdt: Double get() = grossPnlUsdt - feesUsdt
    }

    private class OpenTrade(
        val direction: Direction,
        val entryPrice: Double,
        val stop: Double,
        val tp1: Double,
        val entryIdx: Int,
        val feeEntry: Double
    )

    fun run(symbol: String, window: Window, warmup: Int = 60): BacktestReport {
        val m5 = window.m5
        require(m5.size > warmup + 20) { "not enough 5m candles for a backtest" }
        val feePct = assumptions.takerFeePctPerSide / 100.0
        val slipPct = assumptions.adverseSlippagePctPerSide / 100.0
        val notional = assumptions.notionalUsdt

        val outcomes = ArrayList<TradeOutcome>()
        var open: OpenTrade? = null
        var i = warmup

        while (i < m5.size) {
            val c = m5[i]

            if (open != null) {
                val long = open.direction == Direction.LONG
                val stopHit = if (long) c.low <= open.stop else c.high >= open.stop
                val tpHit = if (long) c.high >= open.tp1 else c.low <= open.tp1

                if (stopHit) {
                    // Stop-first handling also applies when both levels are touched.
                    val exitPx = if (long) open.stop * (1 - slipPct) else open.stop * (1 + slipPct)
                    outcomes.add(closeOutcome(open, exitPx, feePct, notional))
                    open = null
                } else if (tpHit) {
                    val exitPx = if (long) open.tp1 * (1 - slipPct) else open.tp1 * (1 + slipPct)
                    outcomes.add(closeOutcome(open, exitPx, feePct, notional))
                    open = null
                }
                i++
                continue
            }

            // Generate a signal from candles strictly before the entry candle.
            val sliceM5 = m5.subList(0, i)
            if (sliceM5.size >= 60) {
                val lastClosed = sliceM5.last()
                val m15Slice = window.m15.filter { it.openTime <= lastClosed.openTime }
                val h1Slice = window.h1.filter { it.openTime <= lastClosed.openTime }
                if (m15Slice.size >= 60 && h1Slice.size >= 60) {
                    val result = ScannerEngine.scan(
                        ScannerEngine.Input(symbol, sliceM5, m15Slice, h1Slice, nowMs = c.openTime),
                        config
                    )
                    val s = result.signal
                    if (s != null) {
                        val entryPx = when (s.direction) {
                            Direction.LONG -> c.open * (1 + slipPct)
                            Direction.SHORT -> c.open * (1 - slipPct)
                        }
                        open = OpenTrade(
                            direction = s.direction,
                            entryPrice = entryPx,
                            stop = s.stopLoss,
                            tp1 = s.tp1,
                            entryIdx = i,
                            feeEntry = notional * feePct
                        )
                    }
                }
            }
            i++
        }

        // Still open at data end: close at the last close, disclosed as an estimate.
        open?.let {
            val exitPx = if (it.direction == Direction.LONG) m5.last().close * (1 - slipPct)
                         else m5.last().close * (1 + slipPct)
            outcomes.add(closeOutcome(it, exitPx, feePct, notional))
        }

        return buildReport(symbol, outcomes, m5.first().openTime, m5.last().closeTime)
    }

    private fun closeOutcome(t: OpenTrade, exitPx: Double, feePct: Double, notional: Double): TradeOutcome {
        val gross = if (t.direction == Direction.LONG) (exitPx - t.entryPrice) * (notional / t.entryPrice)
                    else (t.entryPrice - exitPx) * (notional / t.entryPrice)
        val exitFee = notional * feePct
        return TradeOutcome(t.entryIdx, t.direction, t.entryPrice, exitPx, gross, t.feeEntry + exitFee)
    }

    private fun buildReport(symbol: String, outcomes: List<TradeOutcome>, from: Long, to: Long): BacktestReport {
        val trades = outcomes.size
        val wins = outcomes.count { it.netPnlUsdt > 0 }
        val losses = trades - wins
        val net = outcomes.sumOf { it.netPnlUsdt }
        val fees = outcomes.sumOf { it.feesUsdt }
        val gross = net + fees
        val winSum = outcomes.filter { it.netPnlUsdt > 0 }.sumOf { it.netPnlUsdt }
        val lossSum = kotlin.math.abs(outcomes.filter { it.netPnlUsdt <= 0 }.sumOf { it.netPnlUsdt })
        val pf = when {
            trades == 0 -> null
            lossSum == 0.0 -> Double.POSITIVE_INFINITY
            else -> winSum / lossSum
        }

        var cum = 0.0
        var peak = 0.0
        var dd = 0.0
        outcomes.sortedBy { it.entryIdx }.forEach {
            cum += it.netPnlUsdt
            if (cum > peak) peak = cum
            val d = peak - cum
            if (d > dd) dd = d
        }

        return BacktestReport(
            symbol = symbol,
            trades = trades,
            wins = wins,
            losses = losses,
            winRatePct = if (trades == 0) 0.0 else 100.0 * wins / trades,
            grossPnlUsdt = gross,
            netPnlUsdt = net,
            feesUsdt = fees,
            profitFactor = pf,
            expectancyPerTradeUsdt = if (trades == 0) 0.0 else net / trades,
            maxDrawdownUsdt = dd,
            avgWinUsdt = if (wins == 0) 0.0 else winSum / wins,
            avgLossUsdt = if (losses == 0) 0.0 else -lossSum / losses,
            assumptions = assumptions,
            dataFromMs = from,
            dataToMs = to
        )
    }
}
