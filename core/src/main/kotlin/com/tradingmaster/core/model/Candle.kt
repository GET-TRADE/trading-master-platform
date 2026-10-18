package com.tradingmaster.core.model

import kotlinx.serialization.Serializable

/** A single OHLCV candle. Timestamps are exchange server-time epoch milliseconds. */
@Serializable
data class Candle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val closeTime: Long
) {
    val isClosed: Boolean get() = closeTime > 0L
}

enum class Timeframe(val label: String, val minutes: Long) {
    M5("5m", 5),
    M15("15m", 15),
    H1("1h", 60);

    /** Last candle in a list is considered forming (not closed) when fetched live. */
    fun candleMillis(): Long = minutes * 60_000L
}

enum class Direction { LONG, SHORT }
