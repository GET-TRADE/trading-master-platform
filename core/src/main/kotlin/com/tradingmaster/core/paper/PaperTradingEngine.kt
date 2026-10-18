package com.tradingmaster.core.paper

import com.tradingmaster.core.model.Candle
import com.tradingmaster.core.model.Direction
import com.tradingmaster.core.model.PaperCloseReason
import com.tradingmaster.core.model.PaperSimAssumptions
import com.tradingmaster.core.model.PaperTrade
import com.tradingmaster.core.model.TradeSignal

/**
 * Paper trading simulation. Completely isolated from live execution:
 * this engine has NO exchange client and never sends orders.
 *
 * Execution model:
 *  - entry fills with adverse slippage + taker fee on the notional
 *  - when one candle touches BOTH stop and target, stop is assumed first (conservative)
 *  - partial exits: TP1 closes half, TP2 closes half of remainder, TP3 closes the rest
 */
class PaperTradingEngine(private val assumptions: PaperSimAssumptions) {

    fun open(signal: TradeSignal, nowMs: Long): PaperTrade {
        val entry = (signal.entryLow + signal.entryHigh) / 2.0
        val slipped = if (signal.direction == Direction.LONG) entry * (1 + assumptions.adverseSlippagePctPerSide / 100)
                      else entry * (1 - assumptions.adverseSlippagePctPerSide / 100)
        val notional = signal.notionalAssumption
        val qty = notional / slipped
        val fee = notional * assumptions.takerFeePctPerSide / 100
        return PaperTrade(
            symbol = signal.symbol,
            direction = signal.direction,
            entryPrice = slipped,
            qty = qty,
            notionalUsdt = notional,
            stopLoss = signal.stopLoss,
            tp1 = signal.tp1,
            tp2 = signal.tp2,
            tp3 = signal.tp3,
            openedAt = nowMs,
            feesUsdt = fee,
            slippageCostUsdt = abs(entry - slipped) * qty
        )
    }

    /**
     * Progress an open trade over one CLOSED candle.
     * Returns the updated trade; status becomes CLOSED when fully exited.
     */
    fun step(trade: PaperTrade, candle: Candle): PaperTrade {
        if (trade.status == com.tradingmaster.core.model.PaperStatus.CLOSED) return trade

        val long = trade.direction == Direction.LONG
        val stopHit = if (long) candle.low <= trade.stopLoss else candle.high >= trade.stopLoss
        val tp1Hit = if (long) candle.high >= trade.tp1 else candle.low <= trade.tp1
        val tp2Hit = if (long) candle.high >= trade.tp2 else candle.low <= trade.tp2
        val tp3Hit = if (long) candle.high >= trade.tp3 else candle.low <= trade.tp3

        // Conservative intrabar ordering: stop first when ambiguous.
        if (stopHit && (tp1Hit || tp2Hit || tp3Hit)) {
            return closeAt(trade, trade.stopLoss, PaperCloseReason.STOP)
        }
        if (stopHit) return closeAt(trade, trade.stopLoss, PaperCloseReason.STOP)
        if (tp3Hit) return closeAt(trade, trade.tp3, PaperCloseReason.TP3)
        if (tp2Hit && trade.partialExits >= 1) return closeAt(trade, trade.tp2, PaperCloseReason.TP2)
        if (tp1Hit && trade.partialExits == 0) {
            // First target: close half, keep running.
            val halfQty = trade.qty / 2.0
            val pnl = pnlPerUnit(trade, trade.tp1) * halfQty
            val exitFee = halfQty * trade.tp1 * assumptions.takerFeePctPerSide / 100
            return trade.copy(
                qty = trade.qty - halfQty,
                partialExits = trade.partialExits + 1,
                realizedPnlUsdt = (trade.realizedPnlUsdt ?: 0.0) + pnl - exitFee,
                feesUsdt = trade.feesUsdt + exitFee
            )
        }
        if (tp2Hit && trade.partialExits == 1) {
            val quarterQty = trade.qty
            val pnl = pnlPerUnit(trade, trade.tp2) * quarterQty
            val exitFee = quarterQty * trade.tp2 * assumptions.takerFeePctPerSide / 100
            return trade.copy(
                qty = trade.qty - quarterQty,
                partialExits = trade.partialExits + 1,
                realizedPnlUsdt = (trade.realizedPnlUsdt ?: 0.0) + pnl - exitFee,
                feesUsdt = trade.feesUsdt + exitFee
            )
        }
        return trade
    }

    fun closeManually(trade: PaperTrade, price: Double, nowMs: Long): PaperTrade =
        closeAt(trade, price, PaperCloseReason.MANUAL).copy(closedAt = nowMs)

    private fun closeAt(trade: PaperTrade, price: Double, reason: PaperCloseReason): PaperTrade {
        val exitSlipped = if (trade.direction == Direction.LONG) price * (1 - assumptions.adverseSlippagePctPerSide / 100)
                          else price * (1 + assumptions.adverseSlippagePctPerSide / 100)
        val pnl = pnlPerUnit(trade, exitSlipped) * trade.qty
        val exitFee = trade.qty * exitSlipped * assumptions.takerFeePctPerSide / 100
        return trade.copy(
            status = com.tradingmaster.core.model.PaperStatus.CLOSED,
            exitPrice = exitSlipped,
            realizedPnlUsdt = (trade.realizedPnlUsdt ?: 0.0) + pnl - exitFee,
            feesUsdt = trade.feesUsdt + exitFee,
            closeReason = reason
        )
    }

    private fun pnlPerUnit(trade: PaperTrade, exit: Double): Double =
        if (trade.direction == Direction.LONG) exit - trade.entryPrice else trade.entryPrice - exit
}

private val TradeSignal.notionalAssumption: Double get() = 20.0
