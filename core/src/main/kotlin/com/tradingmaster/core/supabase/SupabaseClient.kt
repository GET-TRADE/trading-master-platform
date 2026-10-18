package com.tradingmaster.core.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

/**
 * Thin Supabase client over OkHttp: Auth (GoTrue), PostgREST data access,
 * and Edge Function invocation. Session tokens are kept in memory and
 * persisted by the caller (encrypted on device).
 *
 * SECURITY: no service-role keys ever appear in the app. Only the anon key,
 * which is safe to embed, plus the user's own access token.
 */
class SupabaseClient(
    private val baseUrl: String,
    private val anonKey: String
) {
    init {
        require(baseUrl.startsWith("https://")) { "Supabase URL must be HTTPS" }
    }

    val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(java.time.Duration.ofSeconds(15))
        .readTimeout(java.time.Duration.ofSeconds(30))
        .build()

    private val jsonMedia = "application/json".toMediaType()

    @Volatile
    var accessToken: String? = null
        private set

    @Volatile
    var refreshToken: String? = null
        private set

    @Volatile
    var userId: String? = null
        private set

    fun restoreSession(token: String, refresh: String?, uid: String?) {
        accessToken = token
        refreshToken = refresh
        userId = uid
    }

    fun clearSession() {
        accessToken = null
        refreshToken = null
        userId = null
    }

    // ---------- Auth ----------

    suspend fun signUp(email: String, password: String, whatsapp: String, appKey: String): AuthResult =
        authCall {
            val body = buildString {
                append("{")
                append("\"email\":").append(jsonEscaped(email)).append(",")
                append("\"password\":").append(jsonEscaped(password)).append(",")
                append("\"data\":{\"whatsapp\":").append(jsonEscaped(whatsapp)).append(",")
                append("\"app_key\":").append(jsonEscaped(appKey)).append("}")
                append("}")
            }
            post("$baseUrl/auth/v1/signup", body, apiKeyHeader = true)
        }.let { AuthResult.parse(it) }

    suspend fun signIn(email: String, password: String): AuthResult = authCall {
        val body = "{\"email\":${jsonEscaped(email)},\"password\":${jsonEscaped(password)}}"
        post("$baseUrl/auth/v1/token?grant_type=password", body, apiKeyHeader = true)
    }.let { AuthResult.parse(it) }

    suspend fun requestPasswordReset(email: String): Boolean = withContext(Dispatchers.IO) {
        val body = "{\"email\":${jsonEscaped(email)}}"
        val r = post("$baseUrl/auth/v1/recover", body, apiKeyHeader = true)
        r.isSuccessful
    }

    suspend fun signOut() {
        val token = accessToken ?: return
        withContext(Dispatchers.IO) {
            http.newCall(Request.Builder()
                .url("$baseUrl/auth/v1/logout")
                .header("Authorization", "Bearer $token")
                .header("apikey", anonKey)
                .post("{}".toRequestBody(jsonMedia))
                .build()).execute().close()
        }
        clearSession()
    }

    // ---------- PostgREST ----------

    suspend fun select(table: String, columns: String = "*", filters: Map<String, String> = emptyMap(),
                       order: String? = null, limit: Int? = null): JsonElement = dataCall {
        val sb = StringBuilder("$baseUrl/rest/v1/$table?select=")
            .append(URLEncoder.encode(columns, "UTF-8"))
        filters.forEach { (k, v) ->
            val (col, op, valPart) = parseFilter(k, v)
            sb.append("&").append(col).append("=").append(op).append(".")
                .append(URLEncoder.encode(valPart, "UTF-8"))
        }
        order?.let { sb.append("&order=").append(URLEncoder.encode(it, "UTF-8")) }
        limit?.let { sb.append("&limit=").append(it) }
        get(sb.toString())
    }

    /** PostgREST filter key format: "column:op" where op in eq,neq,gt,gte,lt,lte,like,ilike,in". */
    private fun parseFilter(key: String, raw: String): Triple<String, String, String> {
        val idx = key.indexOf(':')
        return if (idx > 0) Triple(key.substring(0, idx), key.substring(idx + 1), raw)
        else Triple(key, "eq", raw)
    }

    suspend fun insert(table: String, bodyJson: String): JsonElement = dataCall {
        post("$baseUrl/rest/v1/$table", bodyJson, prefer = "return=representation")
    }

    suspend fun update(table: String, filter: Pair<String, String>, bodyJson: String): JsonElement = dataCall {
        val url = "$baseUrl/rest/v1/$table?${filter.first}=eq.${URLEncoder.encode(filter.second, "UTF-8")}"
        patch(url, bodyJson, prefer = "return=representation")
    }

    /** Call a PostgREST RPC (SECURITY DEFINER function) with the user session. */
    suspend fun rpc(fn: String, bodyJson: String = "{}"): JsonElement = withContext(Dispatchers.IO) {
        val token = accessToken ?: throw SupabaseError.Auth("Not signed in")
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/" + fn)
            .header("Authorization", "Bearer $token")
            .header("apikey", anonKey)
            .post(bodyJson.toRequestBody(jsonMedia))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw SupabaseError.from(resp.code, text)
            if (text.isEmpty()) JsonObject(emptyMap()) else json.parseToJsonElement(text)
        }
    }

    /** Invoke an Edge Function with the current user session. */
    suspend fun invokeFunction(name: String, bodyJson: String = "{}"): JsonElement = withContext(Dispatchers.IO) {
        val token = accessToken ?: throw SupabaseError.Auth("Not signed in")
        val req = Request.Builder()
            .url("$baseUrl/functions/v1/$name")
            .header("Authorization", "Bearer $token")
            .header("apikey", anonKey)
            .post(bodyJson.toRequestBody(jsonMedia))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw SupabaseError.from(resp.code, text)
            if (text.isEmpty()) JsonObject(emptyMap()) else json.parseToJsonElement(text)
        }
    }

    /** Invoke an Edge Function anonymously (e.g. health checks). */
    suspend fun invokeFunctionAnon(name: String, bodyJson: String = "{}"): JsonElement =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("$baseUrl/functions/v1/$name")
                .header("apikey", anonKey)
                .post(bodyJson.toRequestBody(jsonMedia))
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) throw SupabaseError.from(resp.code, text)
                if (text.isEmpty()) JsonObject(emptyMap()) else json.parseToJsonElement(text)
            }
        }

    // ---------- internals ----------

    private suspend fun authCall(block: suspend () -> okhttp3.Response): String =
        withContext(Dispatchers.IO) { block().use { resp -> resp.body?.string() ?: "" } }

    private suspend fun dataCall(block: suspend () -> okhttp3.Response): JsonElement =
        withContext(Dispatchers.IO) {
            block().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) throw SupabaseError.from(resp.code, text)
                json.parseToJsonElement(if (text.isEmpty()) "[]" else text)
            }
        }

    private fun get(url: String): okhttp3.Response {
        val token = accessToken ?: throw SupabaseError.Auth("Not signed in")
        return http.newCall(Request.Builder().url(url)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $token")
            .build()).execute()
    }

    private fun post(url: String, body: String, apiKeyHeader: Boolean = false, prefer: String? = null): okhttp3.Response {
        val b = Request.Builder().url(url)
            .post(body.toRequestBody(jsonMedia))
        if (apiKeyHeader) b.header("apikey", anonKey)
        else {
            val token = accessToken ?: throw SupabaseError.Auth("Not signed in")
            b.header("apikey", anonKey).header("Authorization", "Bearer $token")
        }
        prefer?.let { b.header("Prefer", it) }
        return http.newCall(b.build()).execute()
    }

    private fun patch(url: String, body: String, prefer: String? = null): okhttp3.Response {
        val token = accessToken ?: throw SupabaseError.Auth("Not signed in")
        val b = Request.Builder().url(url)
            .patch(body.toRequestBody(jsonMedia))
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $token")
        prefer?.let { b.header("Prefer", it) }
        return http.newCall(b.build()).execute()
    }

    private fun jsonEscaped(s: String): String {
        val out = StringBuilder()
        for (c in s) when (c) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            else -> out.append(c)
        }
        return "\"${out}\""
    }

    // ---------- Result / error types ----------

    data class AuthResult(
        val success: Boolean,
        val userId: String?,
        val accessToken: String?,
        val refreshToken: String?,
        val needsEmailConfirmation: Boolean,
        val error: String?
    ) {
        companion object {
            fun parse(raw: String): AuthResult {
                return try {
                    val obj = Json.parseToJsonElement(raw).jsonObject
                    val access = obj["access_token"]?.toString()?.trim('"')
                    val refresh = obj["refresh_token"]?.toString()?.trim('"')
                    val uid = obj["user"]?.let { (it as? JsonObject)?.get("id")?.toString()?.trim('"') }
                        ?: obj["id"]?.toString()?.trim('"')
                    if (access != null) {
                        AuthResult(true, uid, access, refresh, false, null)
                    } else {
                        val msg = obj["msg"]?.toString()?.trim('"')
                            ?: obj["error_description"]?.toString()?.trim('"')
                            ?: obj["message"]?.toString()?.trim('"')
                            ?: "Sign-up recorded; confirm your email to activate access."
                        AuthResult(false, null, null, null, !obj.containsKey("error"), msg)
                    }
                } catch (e: Exception) {
                    AuthResult(false, null, null, null, false, e.message)
                }
            }
        }
    }

    sealed class SupabaseError(message: String) : Exception(message) {
        class Auth(msg: String) : SupabaseError(msg)
        class Network(msg: String) : SupabaseError(msg)
        class Server(code: Int, msg: String) : SupabaseError("Backend error $code: $msg")

        companion object {
            fun from(code: Int, body: String): SupabaseError = when (code) {
                401, 403 -> Auth(body.take(200))
                in 500..599 -> Server(code, body.take(200))
                else -> Server(code, body.take(200))
            }
        }
    }
}
