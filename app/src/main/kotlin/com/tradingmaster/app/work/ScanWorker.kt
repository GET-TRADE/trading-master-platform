package com.tradingmaster.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tradingmaster.app.TradingMasterApp
import com.tradingmaster.core.binance.ScannerService
import com.tradingmaster.core.model.ScanUniverse
import java.util.concurrent.TimeUnit

/**
 * Background scanner: notifies about new signals and updates local records.
 * It NEVER places live orders — background trading stays off by default.
 */
class ScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as TradingMasterApp
        return try {
            val summary = ScannerService(app.container.binance.publicApi).scanUniverse(ScanUniverse())
            // Store signals locally for the journal/history.
            val dao = app.container.db.signalHistoryDao()
            summary.signals.forEach { r ->
                dao.insert(com.tradingmaster.core.store.SignalHistoryEntity(
                    symbol = r.symbol, generatedAt = r.generatedAt, isTrade = true,
                    payloadJson = kotlinx.serialization.json.Json.encodeToString(
                        com.tradingmaster.core.model.ScanResult.serializer(), r)))
            }
            if (summary.signals.isNotEmpty()) notify(summary.signals.size,
                summary.signals.joinToString(limit = 3) { "${it.signal!!.symbol} ${it.signal!!.direction}" })
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun notify(count: Int, preview: String) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Scan signals",
                NotificationManager.IMPORTANCE_DEFAULT))
        }
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Trading Master: $count new signal(s)")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(1001, n) }
    }

    companion object {
        private const val CHANNEL = "tm_scan"
        private const val WORK_NAME = "tm_background_scan"

        /** Every 3 hours; keeps request volume conservative. */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ScanWorker>(3, TimeUnit.HOURS).build()
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
