package com.tradingmaster.core.binance

import kotlinx.serialization.Serializable

/** exchangeInfo symbol entry (subset used by the app). */
@Serializable
data class SymbolFilter(
    val filterType: String,
    val minQty: Double? = null,
    val maxQty: Double? = null,
    val stepSize: Double? = null,
    val tickSize: Double? = null,
    val minPrice: Double? = null,
    val notional: Double? = null // MIN_NOTIONAL filter value
)

@Serializable
data class ExchangeSymbol(
    val symbol: String,
    val status: String,
    val contractType: String? = null,
    val baseAsset: String,
    val quoteAsset: String,
    val filters: List<SymbolFilter> = emptyList()
)

@Serializable
data class ExchangeInfoResponse(
    val symbols: List<ExchangeSymbol> = emptyList()
)

/** 24h ticker (subset). */
@Serializable
data class Ticker24h(
    val symbol: String,
    val lastPrice: String,
    val quoteVolume: String
)

@Serializable
data class MarkPrice(
    val symbol: String,
    val markPrice: String
)

/** Account / position endpoints. */
@Serializable
data class AccountBalance(
    val asset: String,
    val balance: String,
    val availableBalance: String
)

@Serializable
data class AccountPosition(
    val symbol: String,
    val positionAmt: String,
    val entryPrice: String,
    val leverage: Int,
    val positionSide: String
)

@Serializable
data class OpenOrder(
    val symbol: String,
    val orderId: Long,
    val clientOrderId: String,
    val type: String,
    val side: String,
    val status: String,
    val price: String,
    val origQty: String,
    val executedQty: String,
    val stopPrice: String? = null,
    val closePosition: Boolean? = null,
    val reduceOnly: Boolean? = null,
    val time: Long,
    val updateTime: Long
)

@Serializable
data class PlaceOrderAck(
    val symbol: String,
    val orderId: Long,
    val clientOrderId: String,
    val status: String? = null,
    val executedQty: String? = null
)

@Serializable
data class LeverageBracketInfo(
    val symbol: String,
    val brackets: List<LeverageBracket> = emptyList()
)

@Serializable
data class LeverageBracket(
    val bracket: Int,
    val initialLeverage: Int,
    val notionalCap: Double,
    val notionalFloor: Double
)

@Serializable
data class ServerTimeResponse(
    val serverTime: Long
)
