package com.tradingmaster.admin.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Guards the parser against the exact nested shape returned by the
 * admin_list_users SQL function (user -> app_access[] + devices[]).
 */
class AdminModelsParserTest {

    private val realShape = """
    [{
      "id": "b0a9208c-0781-42c6-b804-92b98634a4c6",
      "email": "wishal.bhatti.0000@gmail.com",
      "whatsapp": "03071945322",
      "display_name": null,
      "created_at": "2026-10-18T20:12:49.187655+00:00",
      "app_access": [
        {"id": "aa1", "app_key": "tm-user", "status": "approved", "plan_id": null, "subscription_expires_at": null, "updated_at": "2026-10-18T20:29:30+00:00"},
        {"id": "aa2", "app_key": "tm-pro", "status": "pending", "plan_id": null, "subscription_expires_at": null, "updated_at": "2026-10-18T20:14:02+00:00"}
      ],
      "devices": [
        {"id": "d1", "app_key": "tm-user", "device_key": "dev-78bfde60", "status": "pending", "approved_at": null, "revoked_at": null, "updated_at": "2026-10-18T20:13:44+00:00"},
        {"id": "d2", "app_key": "tm-user", "device_key": "dev-7420c177", "status": "pending", "approved_at": null, "revoked_at": null, "updated_at": "2026-10-18T20:23:09+00:00"},
        {"id": "d3", "app_key": "tm-pro", "device_key": "dev-4cc142ce", "status": "pending", "approved_at": null, "revoked_at": null, "updated_at": "2026-10-18T20:25:56+00:00"}
      ]
    }]
    """.trimIndent()

    @Test
    fun `nested app_access rows expand to one AdminUser per app`() {
        val users = JsonParsers.parseUserList(Json.parseToJsonElement(realShape))
        assertEquals(2, users.size)
        val userRow = users.first { it.appKey == "tm-user" }
        val proRow = users.first { it.appKey == "tm-pro" }
        assertEquals("approved", userRow.status)
        assertEquals("pending", proRow.status)
    }

    @Test
    fun `pending device of the matching app is surfaced for approval`() {
        val users = JsonParsers.parseUserList(Json.parseToJsonElement(realShape))
        val proRow = users.first { it.appKey == "tm-pro" }
        assertNotNull("pending pro device must be reachable for admin approval", proRow.deviceId)
        assertEquals("d3", proRow.deviceId)
        assertEquals("pending", proRow.deviceStatus)
    }

    @Test
    fun `legacy flat shape still parses`() {
        val flat = """[{"id":"u1","email":"a@b.c","app_key":"tm-user","status":"approved","device_id":"d9","device_status":"pending"}]"""
        val users = JsonParsers.parseUserList(Json.parseToJsonElement(flat))
        assertEquals(1, users.size)
        assertEquals("tm-user", users[0].appKey)
        assertEquals("d9", users[0].deviceId)
    }
}
