package com.tradingmaster.core.binance

/**
 * Error classification for Binance USDⓈ-M Futures API responses.
 * Public market-data success NEVER implies authenticated Futures access.
 */
sealed class BinanceError(message: String) : Exception(message) {
    class Network(cause: Throwable) : BinanceError("Network error: ${cause.message}")
    class Timeout : BinanceError("Request timed out; exchange state unknown until reconciled.")
    class Auth(code: Int, msg: String) : BinanceError("Authentication error $code: $msg (check API key/secret, IP restriction)")
    class Permission(code: Int, msg: String) : BinanceError("Permission error $code: $msg (key may lack Futures permission or the account mode is incompatible)")
    class RateLimited(msg: String) : BinanceError("Rate limited: $msg")
    class Exchange(code: Int, msg: String) : BinanceError("Exchange error $code: $msg")
    class AccountMode(msg: String) : BinanceError("Account-mode incompatibility: $msg")
    class Unknown(msg: String) : BinanceError(msg)

    companion object {
        /**
         * Map an HTTP status + Binance error payload to a classified error.
         * Payload format: {"code": -2015, "msg": "..."}
         */
        fun from(status: Int, body: String): BinanceError {
            val code = Regex("\"code\"\\s*:\\s*(-?\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()
            val msg = Regex("\"msg\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1) ?: body.take(200)
            return when (status) {
                401 -> Auth(status, msg)
                403 -> Permission(status, msg)
                418, 429 -> RateLimited(msg)
                400 -> when (code) {
                    -2014, -2015 -> Auth(code, msg)
                    -2019, -4118, -4119, -4131, -4167 -> Permission(code, msg)
                    -4061, -4014, -4015, -4114 -> AccountMode(msg)
                    else -> Exchange(code ?: status, msg)
                }
                else -> Exchange(code ?: status, msg)
            }
        }

        fun fromThrowable(t: Throwable): BinanceError = when {
            t is java.net.SocketTimeoutException || t is java.net.UnknownHostException -> Timeout()
            t is BinanceError -> t
            else -> Network(t)
        }
    }
}
