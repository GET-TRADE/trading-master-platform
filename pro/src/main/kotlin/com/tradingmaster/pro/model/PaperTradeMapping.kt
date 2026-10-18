package com.tradingmaster.pro.model

import com.tradingmaster.core.model.Direction
import com.tradingmaster.core.model.PaperCloseReason
import com.tradingmaster.core.model.PaperStatus
import com.tradingmaster.core.model.PaperTrade
import com.tradingmaster.core.store.PaperTradeEntity

fun PaperTradeEntity.toModel(): PaperTrade = PaperTrade(
    id = id,
    symbol = symbol,
    direction = Direction.valueOf(direction),
    entryPrice = entryPrice,
    qty = qty,
    notionalUsdt = notionalUsdt,
    stopLoss = stopLoss,
    tp1 = tp1,
    tp2 = tp2,
    tp3 = tp3,
    openedAt = openedAt,
    closedAt = closedAt,
    status = PaperStatus.valueOf(status),
    exitPrice = exitPrice,
    realizedPnlUsdt = realizedPnlUsdt,
    feesUsdt = feesUsdt,
    slippageCostUsdt = slippageCostUsdt,
    partialExits = partialExits,
    closeReason = closeReason?.let { runCatching { PaperCloseReason.valueOf(it) }.getOrNull() }
)

fun PaperTrade.toEntity(): PaperTradeEntity = PaperTradeEntity(
    symbol = symbol,
    direction = direction.name,
    entryPrice = entryPrice,
    qty = qty,
    notionalUsdt = notionalUsdt,
    stopLoss = stopLoss,
    tp1 = tp1,
    tp2 = tp2,
    tp3 = tp3,
    openedAt = openedAt,
    closedAt = closedAt,
    status = status.name,
    exitPrice = exitPrice,
    realizedPnlUsdt = realizedPnlUsdt,
    feesUsdt = feesUsdt,
    slippageCostUsdt = slippageCostUsdt,
    partialExits = partialExits,
    closeReason = closeReason?.name
)
