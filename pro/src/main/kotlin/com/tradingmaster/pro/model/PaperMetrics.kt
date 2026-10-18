package com.tradingmaster.pro.model

import com.tradingmaster.core.model.PaperStatus
import com.tradingmaster.core.model.PaperTrade

data class PaperMetrics(
    val totalTrades: Int = 0,
    val openTrades: Int = 0,
    val closedTrades: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val winRatePct: Double = 0.0,
    val totalNetPnlUsdt: Double = 0.0,
    val totalFeesUsdt: Double = 0.0,
    val avgWinUsdt: Double = 0.0,
    val avgLossUsdt: Double = 0.0,
    val expectancyUsdt: Double = 0.0
) {
    companion object {
        fun compute(trades: List<PaperTrade>): PaperMetrics {
            val open = trades.count { it.status == PaperStatus.OPEN }
            val closed = trades.filter { it.status == PaperStatus.CLOSED }
            val closedCount = closed.size
            if (closedCount == 0) {
                return PaperMetrics(
                    totalTrades = trades.size,
                    openTrades = open,
                    closedTrades = 0,
                    totalFeesUsdt = trades.sumOf { it.feesUsdt }
                )
            }
            val winList = closed.filter { (it.realizedPnlUsdt ?: 0.0) > 0 }
            val lossList = closed.filter { (it.realizedPnlUsdt ?: 0.0) <= 0 }
            val wins = winList.size
            val losses = lossList.size
            val winRate = (wins.toDouble() / closedCount.toDouble()) * 100.0
            val netPnl = closed.sumOf { it.realizedPnlUsdt ?: 0.0 }
            val totalFees = trades.sumOf { it.feesUsdt }
            val winSum = winList.sumOf { it.realizedPnlUsdt ?: 0.0 }
            val lossSum = lossList.sumOf { it.realizedPnlUsdt ?: 0.0 }
            val avgWin = if (wins > 0) winSum / wins else 0.0
            val avgLoss = if (losses > 0) lossSum / losses else 0.0
            val expectancy = netPnl / closedCount

            return PaperMetrics(
                totalTrades = trades.size,
                openTrades = open,
                closedTrades = closedCount,
                wins = wins,
                losses = losses,
                winRatePct = winRate,
                totalNetPnlUsdt = netPnl,
                totalFeesUsdt = totalFees,
                avgWinUsdt = avgWin,
                avgLossUsdt = avgLoss,
                expectancyUsdt = expectancy
            )
        }
    }
}
