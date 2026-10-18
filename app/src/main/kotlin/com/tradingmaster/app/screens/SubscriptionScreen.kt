package com.tradingmaster.app.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tradingmaster.app.TradingMasterApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Subscription: status, checkout via the server (manual crypto receivers are
 * admin-configured), payment history, and a WhatsApp support fallback.
 * Manual payments stay PENDING until an administrator verifies them.
 */
@Composable
fun SubscriptionScreen(nav: NavController) {
    val app = nav.context.applicationContext as TradingMasterApp
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    var expiresAt by remember { mutableStateOf<String?>(null) }
    var planName by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var receivers by remember { mutableStateOf<Pair<String, String>?>(null) }
    var adminWhatsapp by remember { mutableStateOf<String?>(null) }
    var payments by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val access = app.container.supabase.rpc("get_my_access", "{\"app_key\":\"tm-user\"}")
                status = access.jsonObject["status"]?.jsonPrimitive?.content
                expiresAt = access.jsonObject["subscription_expires_at"]?.jsonPrimitive?.content
                planName = access.jsonObject["plan_name"]?.jsonPrimitive?.content
            } catch (e: Exception) {
                message = "Could not load your subscription status: ${e.message}"
            }
            try {
                val rows = app.container.supabase.select("subscription_payments", "*")
                payments = rows.jsonArray.map {
                    val o = it.jsonObject
                    val m = o["method"]?.jsonPrimitive?.content ?: "-"
                    val s = o["status"]?.jsonPrimitive?.content ?: "-"
                    val amt = o["amount_pkr"]?.jsonPrimitive?.content ?: "-"
                    "$m • PKR $amt • $s"
                }
            } catch (e: Exception) { /* own-rows read; ignore */ }
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Subscription", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Account status: ${status ?: "unknown"}")
                Text("Plan: ${planName ?: "—"}")
                Text("Expires: ${expiresAt ?: "no active subscription"}", fontSize = 12.sp)
                Text("Payments are verified server-side. A screenshot alone never activates a subscription.",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
            }
        }

        Button(onClick = {
            busy = true; message = null
            val c = app.container
            kotlinx.coroutines.MainScope().launch(Dispatchers.IO) {
                try {
                    // plan_id is intentionally omitted: the server selects the
                    // configured active plan; client-supplied prices are never trusted.
                    val body = buildJsonObject {
                        put("method", JsonPrimitive("trc20"))
                        put("idempotency_key", JsonPrimitive("pay-${System.currentTimeMillis()}"))
                    }.toString()
                    val resp = c.supabase.invokeFunction("create-subscription-payment", body).jsonObject
                    val trc20 = resp["trc20_address"]?.jsonPrimitive?.content ?: ""
                    val bp = resp["binance_pay_id"]?.jsonPrimitive?.content ?: ""
                    val amt = resp["amount_pkr"]?.jsonPrimitive?.content ?: "?"
                    receivers = trc20 to bp
                    adminWhatsapp = resp["admin_whatsapp"]?.jsonPrimitive?.content
                    message = "Payment request created (PKR $amt). Your payment stays PENDING until the administrator verifies it."
                } catch (e: Exception) {
                    message = "Could not create a payment request: ${e.message}"
                } finally { busy = false }
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Working..." else "Pay subscription (TRC20 / Binance Pay)")
        }

        receivers?.let { (trc20, binancePay) ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Send USDT (TRC20) to:", fontSize = 12.sp)
                    Text(trc20.ifBlank { "not configured — contact admin" }, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Or Binance Pay ID: ${binancePay.ifBlank { "not configured" }}", fontSize = 12.sp)
                    Text("After sending, reply on WhatsApp with your transaction hash/reference. An administrator verifies it manually before activation.",
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        OutlinedButton(onClick = {
            val num = adminWhatsapp?.filter { it.isDigit() }
            val url = if (!num.isNullOrEmpty() && num.length >= 10)
                "https://wa.me/$num?text=${Uri.encode("Hello, I need help with my Trading Master subscription.")}"
            else "https://wa.me/"
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }, modifier = Modifier.fillMaxWidth()) { Text("Contact admin on WhatsApp") }

        message?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }

        Text("Payment history", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        payments.forEach { Text(it, fontSize = 11.sp) }
        if (payments.isEmpty()) Text("No payments recorded yet.", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(16.dp))
    }
}
