package com.tradingmaster.core.binance

import com.tradingmaster.core.model.Candle
import com.tradingmaster.core.model.Timeframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import java.io.IOException

/**
 * HMAC-SHA256 request signing + server-time synchronization.
 * The secret is supplied by a credential-provider lambda (Android Keystore backed
 * in the app) and is never logged.
 */
class BinanceAuthInterceptor(private val apiKeyProvider: () -> String,
                             private val secretProvider: () -> String,
                             private val timeSync: BinanceTimeSync) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val key = apiKeyProvider()
        val secret = secretProvider()
        if (key.isBlank() || secret.isBlank()) {
            throw BinanceError.Auth(-1, "API credentials are not configured")
        }
        val timestamp = timeSync.exchangeTimestamp()
        val url = request.url
        val newQuery = url.newBuilder()
            .addQueryParameter("timestamp", timestamp.toString())
            .addQueryParameter("recvWindow", "5000")
            .build()
        val signed = newQuery.query!!.replace(Regex("%2F"), "/")
        val sig = hmacSha256(signed, secret)
        val newUrl = newQuery.newBuilder().addQueryParameter("signature", sig).build()
        return chain.proceed(request.newBuilder().url(newUrl)
            .header("X-MBX-APIKEY", key).build())
    }

    companion object {
        fun hmacSha256(data: String, secret: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
            return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * Keeps a cached exchange-server-time offset refreshed on each signed batch.
 * Prevents timestamp drift rejections.
 */
class BinanceTimeSync(private val api: BinancePublicApi) {
    private val mutex = Mutex()
    private var offsetMs: Long = 0

    suspend fun refresh() = withContext(Dispatchers.IO) {
        try {
            val t = api.serverTime().serverTime
            mutex.withLock { offsetMs = t - System.currentTimeMillis() }
        } catch (_: Exception) { /* keep last known offset */ }
    }

    fun exchangeTimestamp(): Long = System.currentTimeMillis() + offsetMs
}

/** Bounded retry with exponential backoff for rate limits and transient failures. */
suspend fun <T> withBinanceRetry(
    maxAttempts: Int = 3,
    baseDelayMs: Long = 500,
    block: suspend () -> T
): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: HttpException) {
            val err = BinanceError.from(e.code(), e.message() ?: "")
            if ((err is BinanceError.RateLimited || e.code() >= 500) && attempt < maxAttempts - 1) {
                attempt++
                kotlinx.coroutines.delay(baseDelayMs * (1L shl (attempt - 1)))
            } else throw err
        } catch (e: IOException) {
            if (attempt < maxAttempts - 1) {
                attempt++
                kotlinx.coroutines.delay(baseDelayMs * (1L shl (attempt - 1)))
            } else throw BinanceError.fromThrowable(e)
        }
    }
}

/** Convenience wrapper converting HttpException into a classified BinanceError. */
suspend fun <T> callBinance(block: suspend () -> T): T {
    try {
        return block()
    } catch (e: HttpException) {
        val body = try { e.response()?.errorBody()?.string() ?: "" } catch (_: Exception) { "" }
        throw BinanceError.from(e.code(), body.ifEmpty { e.message() ?: "" })
    } catch (e: IOException) {
        throw BinanceError.fromThrowable(e)
    }
}

object BinanceClientFactory {
    val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    fun publicClient(baseUrl: String = BinancePublicApi.BASE_MAINNET): Pair<BinancePublicApi, OkHttpClient> {
        val client = OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .readTimeout(java.time.Duration.ofSeconds(20))
            .build()
        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BinancePublicApi::class.java)
        return api to client
    }

    fun signedClient(
        baseUrl: String = BinancePublicApi.BASE_MAINNET,
        apiKeyProvider: () -> String,
        secretProvider: () -> String,
        timeSync: BinanceTimeSync
    ): Pair<BinanceFuturesApi, OkHttpClient> {
        val auth = BinanceAuthInterceptor(apiKeyProvider, secretProvider, timeSync)
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .readTimeout(java.time.Duration.ofSeconds(20))
            .build()
        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BinanceFuturesApi::class.java)
        return api to client
    }

    /** Fetch closed candles; drops the last (forming) candle when [dropForming]. */
    suspend fun klines(api: BinancePublicApi, symbol: String, tf: Timeframe, limit: Int = 200): List<Candle> =
        withBinanceRetry {
            val raw = api.klines(symbol, tf.label, limit)
            val all = raw.map { parseKline(it) }
            // Drop the forming candle: its closeTime is in the future.
            all.dropLast(1)
        }
}
