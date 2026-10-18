package com.tradingmaster.admin.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AdminUser(
    val userId: String,
    val email: String,
    val whatsapp: String? = null,
    val appKey: String? = null,
    val status: String = "pending",
    val subscriptionExpiresAt: String? = null,
    val deviceId: String? = null,
    val deviceStatus: String? = null,
    val createdAt: String? = null
)

data class AdminPlan(
    val id: String,
    val name: String,
    val pricePkr: Double,
    val durationDays: Int,
    val features: List<String> = emptyList(),
    val active: Boolean = true
)

data class AdminSetting(
    val binancePayId: String = "",
    val trc20Address: String = "",
    val aiConfigJson: String = "{}"
)

data class AdminPayment(
    val id: String,
    val userId: String? = null,
    val email: String? = null,
    val method: String? = null,
    val status: String = "pending",
    val amountPkr: Double = 0.0,
    val reference: String? = null,
    val proofNote: String? = null,
    val createdAt: String? = null
)

data class AuditLog(
    val id: String,
    val actor: String? = null,
    val action: String? = null,
    val target: String? = null,
    val details: String? = null,
    val createdAt: String? = null
)

data class ErrorLog(
    val id: String,
    val userId: String? = null,
    val source: String? = null,
    val message: String? = null,
    val createdAt: String? = null
)

data class BackendHealthState(
    val status: String = "unknown",
    val dbOk: Boolean = false,
    val aiConfigured: Boolean = false,
    val version: String = "unknown",
    val rawJson: String = "{}"
)

object JsonParsers {

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    private fun JsonObject.objs(key: String): List<JsonObject> {
        val arr = this[key] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { it as? JsonObject }
    }

    fun parseUserList(jsonElement: JsonElement): List<AdminUser> {
        return try {
            val array = if (jsonElement is JsonArray) jsonElement else jsonElement.jsonArray
            array.flatMap { element ->
                val obj = element.jsonObject
                val userId = obj.str("user_id") ?: obj.str("id") ?: return@flatMap emptyList()
                val email = obj.str("email") ?: "unknown"
                val whatsapp = obj.str("whatsapp")
                val createdAt = obj.str("created_at")

                val appAccessRows = obj.objs("app_access")
                if (appAccessRows.isEmpty()) {
                    // Legacy flat shape or a user with no access rows yet.
                    listOf(
                        AdminUser(
                            userId = userId,
                            email = email,
                            whatsapp = whatsapp,
                            appKey = obj.str("app_key"),
                            status = obj.str("status") ?: "pending",
                            subscriptionExpiresAt = obj.str("subscription_expires_at"),
                            deviceId = obj.str("device_id"),
                            deviceStatus = obj.str("device_status"),
                            createdAt = createdAt
                        )
                    )
                } else {
                    val devices = obj.objs("devices")
                    appAccessRows.map { aa ->
                        val appKey = aa.str("app_key")
                        // Prefer this app's oldest pending device; fall back to its newest device row.
                        val appDevices = devices.filter { it.str("app_key") == appKey }
                        val shown = appDevices.firstOrNull { it.str("status") == "pending" }
                            ?: appDevices.firstOrNull()
                        AdminUser(
                            userId = userId,
                            email = email,
                            whatsapp = whatsapp,
                            appKey = appKey,
                            status = aa.str("status") ?: "pending",
                            subscriptionExpiresAt = aa.str("subscription_expires_at"),
                            deviceId = shown?.str("id"),
                            deviceStatus = shown?.str("status"),
                            createdAt = createdAt
                        )
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parsePlanList(jsonElement: JsonElement): List<AdminPlan> {
        return try {
            val array = if (jsonElement is JsonArray) jsonElement else jsonElement.jsonArray
            array.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.content ?: "Unnamed Plan"
                val pricePkr = obj["price_pkr"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val durationDays = obj["duration_days"]?.jsonPrimitive?.intOrNull ?: 30
                val active = obj["active"]?.jsonPrimitive?.booleanOrNull ?: true

                val features = try {
                    val featElem = obj["features"]
                    if (featElem is JsonArray) {
                        featElem.map { it.jsonPrimitive.content }
                    } else if (featElem != null) {
                        listOf(featElem.jsonPrimitive.content)
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    emptyList()
                }

                AdminPlan(
                    id = id,
                    name = name,
                    pricePkr = pricePkr,
                    durationDays = durationDays,
                    features = features,
                    active = active
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseSettings(jsonElement: JsonElement): AdminSetting {
        return try {
            val obj = jsonElement.jsonObject
            val binancePayId = obj["binance_pay_id"]?.jsonPrimitive?.content ?: ""
            val trc20Address = obj["trc20_address"]?.jsonPrimitive?.content ?: ""
            val aiConfigJson = obj["ai_config"]?.toString() ?: "{}"

            AdminSetting(
                binancePayId = binancePayId,
                trc20Address = trc20Address,
                aiConfigJson = aiConfigJson
            )
        } catch (e: Exception) {
            AdminSetting()
        }
    }

    fun parsePaymentList(jsonElement: JsonElement): List<AdminPayment> {
        return try {
            val array = if (jsonElement is JsonArray) jsonElement else jsonElement.jsonArray
            array.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val userId = obj["user_id"]?.jsonPrimitive?.content
                val email = obj["user_email"]?.jsonPrimitive?.content ?: obj["email"]?.jsonPrimitive?.content
                val method = obj["method"]?.jsonPrimitive?.content
                val status = obj["status"]?.jsonPrimitive?.content ?: "pending"
                val amountPkr = obj["amount_pkr"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val reference = obj["reference"]?.jsonPrimitive?.content
                val proofNote = obj["proof_note"]?.jsonPrimitive?.content
                val createdAt = obj["created_at"]?.jsonPrimitive?.content

                AdminPayment(
                    id = id,
                    userId = userId,
                    email = email,
                    method = method,
                    status = status,
                    amountPkr = amountPkr,
                    reference = reference,
                    proofNote = proofNote,
                    createdAt = createdAt
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseAuditList(jsonElement: JsonElement): List<AuditLog> {
        return try {
            val array = if (jsonElement is JsonArray) jsonElement else jsonElement.jsonArray
            array.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val actor = obj["actor_id"]?.jsonPrimitive?.content ?: obj["actor"]?.jsonPrimitive?.content
                val action = obj["action"]?.jsonPrimitive?.content
                val target = obj["target_user_id"]?.jsonPrimitive?.content ?: obj["target"]?.jsonPrimitive?.content
                val details = obj["details"]?.jsonPrimitive?.content ?: obj["details"]?.toString()
                val createdAt = obj["created_at"]?.jsonPrimitive?.content

                AuditLog(
                    id = id,
                    actor = actor,
                    action = action,
                    target = target,
                    details = details,
                    createdAt = createdAt
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseErrorList(jsonElement: JsonElement): List<ErrorLog> {
        return try {
            val array = if (jsonElement is JsonArray) jsonElement else jsonElement.jsonArray
            array.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val userId = obj["user_id"]?.jsonPrimitive?.content
                val source = obj["source"]?.jsonPrimitive?.content
                val message = obj["message"]?.jsonPrimitive?.content ?: obj["message"]?.toString()
                val createdAt = obj["created_at"]?.jsonPrimitive?.content

                ErrorLog(
                    id = id,
                    userId = userId,
                    source = source,
                    message = message,
                    createdAt = createdAt
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseBackendHealth(jsonElement: JsonElement): BackendHealthState {
        return try {
            val obj = jsonElement.jsonObject
            val status = obj["status"]?.jsonPrimitive?.content ?: "unknown"
            val dbOk = obj["db"]?.jsonPrimitive?.booleanOrNull ?: false
            val aiConfigured = obj["ai_configured"]?.jsonPrimitive?.booleanOrNull ?: false
            val version = obj["version"]?.jsonPrimitive?.content ?: "unknown"

            BackendHealthState(
                status = status,
                dbOk = dbOk,
                aiConfigured = aiConfigured,
                version = version,
                rawJson = jsonElement.toString()
            )
        } catch (e: Exception) {
            BackendHealthState(status = "error", rawJson = jsonElement.toString())
        }
    }
}
