package com.tradingmaster.core.binance

/**
 * Explicit, staged Binance connection test. Each stage is reported separately:
 * public market-data reachability never implies signed Futures access.
 *
 * STAGES
 *  1. PUBLIC      — /fapi/v1/time reachable (exchange reachable, market data available)
 *  2. SIGNED      — signed /fapi/v2/balance succeeds (key + secret valid, IP allowed)
 *  3. FUTURES     — USDT balance row exists (Futures permission on the key)
 *  4. MODE        — leverageBracket readable (account-mode compatibility probe)
 */
data class ConnectionTestStage(
    val name: String,
    val passed: Boolean,
    val detail: String
)

data class ConnectionTestReport(
    val passedAll: Boolean,
    val stages: List<ConnectionTestStage>,
    val exchangeTimeOffsetMs: Long? = null
)

class ConnectionTester(
    private val publicApi: BinancePublicApi,
    private val futuresApi: BinanceFuturesApi,
    private val timeSync: BinanceTimeSync
) {
    suspend fun test(): ConnectionTestReport = callBinance {
        val stages = ArrayList<ConnectionTestStage>()

        // Stage 1: public reachability
        val t0 = try {
            timeSync.refresh()
            callBinance { publicApi.serverTime() }.serverTime
        } catch (e: BinanceError) {
            stages.add(ConnectionTestStage("PUBLIC", false,
                "Cannot reach Binance Futures market data: ${e.message}"))
            return@callBinance ConnectionTestReport(false, stages)
        }
        stages.add(ConnectionTestStage("PUBLIC", true, "Exchange reachable. Server time OK."))
        val offset = timeSync.exchangeTimestamp() - System.currentTimeMillis()

        // Stage 2: signed account request
        val balance = try {
            withBinanceRetry { futuresApi.balance() }
        } catch (e: BinanceError) {
            stages.add(ConnectionTestStage("SIGNED", false,
                "Signed request failed: ${e.message}. Check the API key/secret and any IP restriction."))
            return@callBinance ConnectionTestReport(false, stages, offset)
        }
        stages.add(ConnectionTestStage("SIGNED", true, "Signed account request accepted."))

        // Stage 3: Futures permission (USDT row present)
        val usdt = balance.firstOrNull { it.asset == "USDT" }
        if (usdt == null) {
            stages.add(ConnectionTestStage("FUTURES", false,
                "No USDT Futures balance returned; this key may lack USDⓈ-M Futures permission."))
            return@callBinance ConnectionTestReport(false, stages, offset)
        }
        stages.add(ConnectionTestStage("FUTURES", true,
            "Futures account visible. Available USDT: ${usdt.availableBalance}"))

        // Stage 4: account-mode compatibility probe
        val modeOk = try {
            withBinanceRetry { futuresApi.leverageBracket() }.isNotEmpty()
        } catch (e: BinanceError) {
            false
        }
        stages.add(ConnectionTestStage("MODE", modeOk,
            if (modeOk) "Leverage brackets readable; account mode compatible."
            else "Could not read leverage brackets. If the account is in a special mode, some features may be restricted."))

        ConnectionTestReport(stages.all { it.passed }, stages, offset)
    }
}
