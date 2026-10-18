package com.tradingmaster.admin.data

import com.tradingmaster.core.session.SessionManager
import com.tradingmaster.core.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class AdminRepository(
    private val supabaseClient: SupabaseClient,
    private val sessionManager: SessionManager
) {
    val json = Json { ignoreUnknownKeys = true }

    fun isSignedIn(): Boolean = sessionManager.isSignedIn

    fun getCurrentUserId(): String? = sessionManager.userId

    suspend fun signIn(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val authResult = supabaseClient.signIn(email, password)
            if (!authResult.success || authResult.accessToken == null || authResult.userId == null) {
                val errorMsg = authResult.error ?: "Sign-in failed. Please check your email and password."
                return@withContext Result.failure(Exception(errorMsg))
            }

            // Restore session temporarily to call is_admin()
            supabaseClient.restoreSession(authResult.accessToken!!, authResult.refreshToken, authResult.userId!!)

            // Verify administrator rights
            val isAdminResult = isAdminInternal()
            if (!isAdminResult) {
                supabaseClient.signOut()
                sessionManager.clear()
                return@withContext Result.failure(Exception("This account has no administrator rights"))
            }

            // Save persistent session
            sessionManager.saveSession(authResult.accessToken!!, authResult.refreshToken, authResult.userId!!)
            Result.success(Unit)
        } catch (e: SupabaseClient.SupabaseError.Auth) {
            Result.failure(Exception("Authentication failed: ${e.message}"))
        } catch (e: SupabaseClient.SupabaseError.Network) {
            Result.failure(Exception("Network error: Please check your internet connection."))
        } catch (e: SupabaseClient.SupabaseError.Server) {
            Result.failure(Exception("Backend server error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "An unexpected error occurred during login."))
        }
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabaseClient.signOut()
            sessionManager.clear()
            Result.success(Unit)
        } catch (e: Exception) {
            sessionManager.clear()
            Result.success(Unit)
        }
    }

    private suspend fun isAdminInternal(): Boolean {
        return try {
            val element = supabaseClient.rpc("is_admin", "{}")
            element.jsonPrimitive.booleanOrNull ?: (element.toString().trim() == "true")
        } catch (e: Exception) {
            false
        }
    }

    suspend fun checkIsAdmin(): Result<Boolean> = withContext(Dispatchers.IO) {
        safeApiCall {
            isAdminInternal()
        }
    }

    suspend fun getUsers(): Result<List<AdminUser>> = withContext(Dispatchers.IO) {
        safeApiCall {
            val result = supabaseClient.rpc("admin_list_users", "{}")
            JsonParsers.parseUserList(result)
        }
    }

    suspend fun setUserStatus(userId: String, appKey: String, newStatus: String): Result<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val body = buildJsonObject {
                put("target_user_id", userId)
                put("app_key", appKey)
                put("new_status", newStatus)
            }.toString()
            supabaseClient.rpc("admin_set_user_status", body)
            Unit
        }
    }

    suspend fun deleteUser(userId: String, appKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val body = buildJsonObject {
                put("target_user_id", userId)
                put("app_key", appKey)
            }.toString()
            supabaseClient.rpc("admin_delete_user", body)
            Unit
        }
    }

    suspend fun approveDevice(deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val body = buildJsonObject {
                put("device_id", deviceId)
            }.toString()
            supabaseClient.rpc("admin_approve_device", body)
            Unit
        }
    }

    suspend fun revokeDevice(deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val body = buildJsonObject {
                put("device_id", deviceId)
            }.toString()
            supabaseClient.rpc("admin_revoke_device", body)
            Unit
        }
    }

    suspend fun setSubscription(userId: String, appKey: String, planId: String, days: Int): Result<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val body = buildJsonObject {
                put("target_user_id", userId)
                put("app_key", appKey)
                put("plan_id", planId)
                put("days", days)
            }.toString()
            supabaseClient.rpc("admin_set_subscription", body)
            Unit
        }
    }

    suspend fun getPlans(): Result<List<AdminPlan>> = withContext(Dispatchers.IO) {
        safeApiCall {
            val result = supabaseClient.rpc("admin_list_plans", "{}")
            JsonParsers.parsePlanList(result)
        }
    }

    suspend fun createPlan(
        name: String,
        pricePkr: Double,
        durationDays: Int,
        features: List<String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val body = buildJsonObject {
                put("name", name)
                put("price_pkr", pricePkr)
                put("duration_days", durationDays)
                put("features", buildJsonArray { features.forEach { add(json.parseToJsonElement("\"$it\"")) } })
            }.toString()
            supabaseClient.rpc("admin_create_plan", body)
            Unit
        }
    }

    suspend fun updatePlan(
        planId: String,
        name: String,
        pricePkr: Double,
        durationDays: Int,
        features: List<String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val body = buildJsonObject {
                put("plan_id", planId)
                put("name", name)
                put("price_pkr", pricePkr)
                put("duration_days", durationDays)
                put("features", buildJsonArray { features.forEach { add(json.parseToJsonElement("\"$it\"")) } })
            }.toString()
            supabaseClient.rpc("admin_update_plan", body)
            Unit
        }
    }

    suspend fun getSettings(): Result<AdminSetting> = withContext(Dispatchers.IO) {
        safeApiCall {
            val result = supabaseClient.rpc("admin_get_settings", "{}")
            JsonParsers.parseSettings(result)
        }
    }

    suspend fun setSettingString(key: String, value: String): Result<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val body = buildJsonObject {
                put("key", key)
                put("value", value)
            }.toString()
            supabaseClient.rpc("admin_set_setting", body)
            Unit
        }
    }

    suspend fun setSettingJson(key: String, jsonElement: JsonElement): Result<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val body = buildJsonObject {
                put("key", key)
                put("value", jsonElement)
            }.toString()
            supabaseClient.rpc("admin_set_setting", body)
            Unit
        }
    }

    suspend fun getPayments(): Result<List<AdminPayment>> = withContext(Dispatchers.IO) {
        safeApiCall {
            val result = supabaseClient.rpc("admin_list_payments", "{}")
            JsonParsers.parsePaymentList(result)
        }
    }

    suspend fun verifyPayment(
        paymentId: String,
        verified: Boolean,
        reference: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val body = buildJsonObject {
                put("payment_id", paymentId)
                put("verified", verified)
                put("reference", reference)
            }.toString()
            supabaseClient.rpc("admin_verify_payment", body)
            Unit
        }
    }

    suspend fun getAuditLogs(limit: Int = 50): Result<List<AuditLog>> = withContext(Dispatchers.IO) {
        safeApiCall {
            val body = buildJsonObject {
                put("limit_val", limit)
            }.toString()
            val result = supabaseClient.rpc("admin_get_audit", body)
            JsonParsers.parseAuditList(result)
        }
    }

    suspend fun getErrorLogs(limit: Int = 50): Result<List<ErrorLog>> = withContext(Dispatchers.IO) {
        safeApiCall {
            val body = buildJsonObject {
                put("limit_val", limit)
            }.toString()
            val result = supabaseClient.rpc("admin_get_errors", body)
            JsonParsers.parseErrorList(result)
        }
    }

    suspend fun getBackendHealth(): Result<BackendHealthState> = withContext(Dispatchers.IO) {
        safeApiCall {
            val result = supabaseClient.invokeFunctionAnon("backend-health", "{}")
            JsonParsers.parseBackendHealth(result)
        }
    }

    private inline fun <T> safeApiCall(block: () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: SupabaseClient.SupabaseError.Auth) {
            Result.failure(Exception("Auth error: ${e.message}"))
        } catch (e: SupabaseClient.SupabaseError.Network) {
            Result.failure(Exception("Network error: Unable to connect to server."))
        } catch (e: SupabaseClient.SupabaseError.Server) {
            Result.failure(Exception("Backend server error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "An error occurred during operation."))
        }
    }
}
