package com.tradingmaster.core.ai

import com.tradingmaster.core.supabase.SupabaseClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Client for the server-side AI gateway (Supabase Edge Function `ai-gateway`).
 * Provider keys live ONLY on the backend. The gateway enforces an ordered
 * model list with fallback; the app never fabricates an AI response.
 *
 * The response reports which model actually answered so the UI can label it.
 * If all providers fail, [AiAnalysis.error] is set and the UI must show the
 * deterministic fallback instead of a fake analysis.
 */
data class AiAnalysis(
    val succeeded: Boolean,
    val modelUsed: String?,
    val analysis: String?,
    val risks: List<String>,
    val error: String?,
    val fellBack: Boolean
)

class AiGatewayClient(private val supabase: SupabaseClient) {

    /**
     * Ask the gateway to analyze one scanner candidate. Only scanner output
     * (symbol, prices, indicators, reasons) is sent — never Binance API
     * credentials or personal data.
     */
    suspend fun analyzeCandidate(signalJson: String): AiAnalysis {
        val payload = buildString {
            append("{\"action\":\"analyze\",\"candidate\":")
            append(signalJson)
            append("}")
        }
        return try {
            val resp = supabase.invokeFunction("ai-gateway", payload)
            val obj = resp.jsonObject
            val ok = obj["ok"]?.jsonPrimitive?.content == "true"
            if (ok) {
                val risks = obj["risks"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                AiAnalysis(
                    succeeded = true,
                    modelUsed = obj["model_used"]?.jsonPrimitive?.content,
                    analysis = obj["analysis"]?.jsonPrimitive?.content,
                    risks = risks,
                    error = null,
                    fellBack = obj["fallback"]?.jsonPrimitive?.content == "true"
                )
            } else {
                AiAnalysis(false, null, null, emptyList(),
                    obj["error"]?.jsonPrimitive?.content ?: "Gateway reported failure", false)
            }
        } catch (e: Exception) {
            AiAnalysis(false, null, null, emptyList(),
                "AI gateway unavailable: ${e.message}", false)
        }
    }

    /** Provider/model health as reported by the gateway (never guessed client-side). */
    data class AiHealth(val available: Boolean, val configuredProviders: List<String>, val detail: String)

    suspend fun health(): AiHealth = try {
        val resp = supabase.invokeFunction("ai-gateway", "{\"action\":\"health\"}")
        val obj = resp.jsonObject
        AiHealth(
            available = obj["available"]?.jsonPrimitive?.content == "true",
            configuredProviders = obj["providers"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            detail = obj["detail"]?.jsonPrimitive?.content ?: ""
        )
    } catch (e: Exception) {
        AiHealth(false, emptyList(), "Gateway unreachable: ${e.message}")
    }
}

