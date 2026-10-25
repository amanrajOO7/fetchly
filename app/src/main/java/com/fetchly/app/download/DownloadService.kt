package com.fetchly.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fetchly.app.FetchlyApp
import com.fetchly.app.MainActivity
import com.fetchly.app.data.DownloadEntity
import com.fetchly.app.data.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps transfers alive when the app is in the background.
 * It shows one notification with combined progress. It stops itself when nothing is queued or running.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observer: Job? = null

    private val manager: DownloadManager
        get() = (application as FetchlyApp).container.downloads

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires startForeground soon after the service starts, so call it first.
        startForeground(
            NOTIFICATION_ID,
            buildNotification(emptyList()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        if (observer == null) {
            observer = scope.launch {
                manager.observeDownloads().collect { downloads ->
                    val active = downloads.filter {
                        it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
                    }
                    if (active.isEmpty()) {
                        stopSelf()
                    } else {
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, buildNotification(active))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): Nothing? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(active: List<DownloadEntity>): Notification {
        val sized = active.filter { it.totalBytes != null }
        val total = sized.sumOf { it.totalBytes ?: 0L }
        val done = sized.sumOf { it.downloadedBytes }

        val text = when {
            active.isEmpty() -> "Starting…"
            total > 0 -> "${active.size} active · " +
                "${Formatter.formatShortFileSize(this, done)} of ${Formatter.formatShortFileSize(this, total)}"
            else -> "${active.size} active"
        }

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Fetchly")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (total > 0) {
            builder.setProgress(100, ((done * 100) / total).toInt().coerceIn(0, 100), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1

        /** Starts the service. Call it after a download has been added or resumed. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        }
    }
}
