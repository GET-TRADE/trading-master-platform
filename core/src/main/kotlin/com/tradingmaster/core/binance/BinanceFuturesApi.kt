package com.tradingmaster.core.binance

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Signed USDⓈ-M Futures endpoints. All requests are HMAC-SHA256 signed by the
 * auth interceptor; the caller never handles the secret directly.
 *
 * SECURITY: this API NEVER exposes withdrawal endpoints. Withdrawal permissions
 * must never be granted to the API key used with this app.
 */
interface BinanceFuturesApi {

    @GET("/fapi/v2/balance")
    suspend fun balance(): List<AccountBalance>

    @GET("/fapi/v2/positionRisk")
    suspend fun positionRisk(): List<AccountPosition>

    @GET("/fapi/v1/openOrders")
    suspend fun openOrders(@Query("symbol") symbol: String? = null): List<OpenOrder>

    @GET("/fapi/v1/order")
    suspend fun queryOrder(
        @Query("symbol") symbol: String,
        @Query("orderId") orderId: Long? = null,
        @Query("origClientOrderId") clientOrderId: String? = null
    ): OpenOrder

    @POST("/fapi/v1/order")
    suspend fun placeOrder(
        @Query("symbol") symbol: String,
        @Query("side") side: String,           // BUY | SELL
        @Query("type") type: String,           // MARKET | LIMIT | STOP_MARKET | TAKE_PROFIT_MARKET
        @Query("quantity") quantity: String? = null,
        @Query("reduceOnly") reduceOnly: Boolean? = null,
        @Query("price") price: String? = null,
        @Query("stopPrice") stopPrice: String? = null,
        @Query("closePosition") closePosition: Boolean? = null,
        @Query("timeInForce") timeInForce: String? = null, // GTC for LIMIT
        @Query("newClientOrderId") newClientOrderId: String? = null,
        @Query("newOrderRespType") newOrderRespType: String = "ACK"
    ): PlaceOrderAck

    @POST("/fapi/v1/order")
    suspend fun cancelOrder(
        @Query("symbol") symbol: String,
        @Query("orderId") orderId: Long
    ): PlaceOrderAck

    @GET("/fapi/v1/leverageBracket")
    suspend fun leverageBracket(@Query("symbol") symbol: String? = null): List<LeverageBracketInfo>

    @POST("/fapi/v1/leverage")
    suspend fun setLeverage(
        @Query("symbol") symbol: String,
        @Query("leverage") leverage: Int
    ): kotlinx.serialization.json.JsonObject

    @GET("/fapi/v1/openOrder")
    suspend fun openOrderByClient(
        @Query("symbol") symbol: String,
        @Query("origClientOrderId") clientOrderId: String
    ): OpenOrder
}
