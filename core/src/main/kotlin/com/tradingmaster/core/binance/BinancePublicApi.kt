package com.tradingmaster.core.binance

import com.tradingmaster.core.model.Candle
import retrofit2.http.GET
import retrofit2.http.Query

/** Public USDⓈ-M Futures market data. No credentials required. */
interface BinancePublicApi {

    @GET("/fapi/v1/time")
    suspend fun serverTime(): ServerTimeResponse

    @GET("/fapi/v1/exchangeInfo")
    suspend fun exchangeInfo(): ExchangeInfoResponse

    /** interval: "5m" | "15m" | "1h". The last candle may be forming; the caller drops it. */
    @GET("/fapi/v1/klines")
    suspend fun klines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): List<List<kotlinx.serialization.json.JsonElement>>

    @GET("/fapi/v1/ticker/24hr")
    suspend fun ticker24h(): List<Ticker24h>

    @GET("/fapi/v1/ticker/price")
    suspend fun prices(): List<MarkPrice>

    companion object {
        const val BASE_MAINNET = "https://fapi.binance.com"
        /** No public USDⓈ-M testnet with full futures account features is guaranteed:
         *  the app fails closed and stays on mainnet unless the user explicitly
         *  configures a verified test environment. */
        const val BASE_TESTNET = "https://testnet.binancefuture.com"
    }
}

/** Kline row parser: [openTime, open, high, low, close, volume, closeTime, ...] */
fun parseKline(row: List<kotlinx.serialization.json.JsonElement>): Candle {
    fun d(i: Int) = (row[i] as kotlinx.serialization.json.JsonPrimitive).content.toDouble()
    fun l(i: Int) = (row[i] as kotlinx.serialization.json.JsonPrimitive).content.toLong()
    return Candle(l(0), d(1), d(2), d(3), d(4), d(5), l(6))
}
