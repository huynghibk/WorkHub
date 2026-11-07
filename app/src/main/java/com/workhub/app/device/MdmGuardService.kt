package com.workhub.app.device

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.workhub.app.MainActivity
import com.workhub.app.R
import com.workhub.app.data.AppGraph
import com.workhub.app.ui.awaitBestLocation
import com.workhub.app.ui.mapsUrlFrom
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Giữ WebSocket + báo vị trí định kỳ khi máy user đã đăng ký MDM.
 * Chạy foreground để ROM Trung khó giết process (vẫn nên tắt tối ưu pin).
 */
class MdmGuardService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var eventsJob: Job? = null
    private var locationJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running.set(true)
        startAsForeground()
        val graph = AppGraph.get(this)
        graph.realtime.start()
        eventsJob = scope.launch {
            graph.realtime.events.collect { event ->
                DeviceCommandRouter.handle(applicationContext, graph, event)
            }
        }
        locationJob = scope.launch {
            while (isActive) {
                reportLocationOnce(graph)
                delay(LOCATION_INTERVAL_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppGraph.get(this).realtime.start()
        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        eventsJob?.cancel()
        locationJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun reportLocationOnce(graph: AppGraph) {
        if (!MdmPrefs.isEnrolled(this) || !LocalDeviceAdmin.isActive(this)) return
        val session = graph.session.current()
        if (session.token.isNullOrBlank()) return
        val loc = awaitBestLocation(this, timeoutMs = 20_000) ?: return
        val url = mapsUrlFrom(loc.latitude, loc.longitude)
        runCatching {
            graph.api.reportDeviceLocation(url, LocalDeviceAdmin.localDeviceId(this))
        }
    }

    private fun startAsForeground() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.mdm_guard_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.mdm_guard_title))
            .setContentText(getString(R.string.mdm_guard_body))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                NOTIFY_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFY_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "workhub_mdm_guard"
        private const val NOTIFY_ID = 71010
        private const val LOCATION_INTERVAL_MS = 10 * 60_000L
        private val running = AtomicBoolean(false)

        fun isRunning(): Boolean = running.get()

        fun startIfNeeded(context: Context) {
            if (!MdmPrefs.isEnrolled(context)) return
            if (!LocalDeviceAdmin.isActive(context)) return
            val app = context.applicationContext
            val intent = Intent(app, MdmGuardService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(Intent(context, MdmGuardService::class.java))
        }
    }
}
