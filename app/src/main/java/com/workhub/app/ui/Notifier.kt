package com.workhub.app.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.workhub.app.R
import com.workhub.app.data.LocalAppGraph
import com.workhub.shared.NotificationDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppVisibility {
    @Volatile var foreground: Boolean = true
    @Volatile var channelId: String? = null
}

private const val ALERT_CHANNEL = "workhub"

@Composable
fun NotificationBinder() {
    val context = LocalContext.current
    val graph = LocalAppGraph.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var resumeTick by remember { mutableIntStateOf(0) }
    val seen = remember { mutableSetOf<String>() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    AppVisibility.foreground = true
                    graph.realtime.restart()
                    resumeTick += 1
                }
                Lifecycle.Event.ON_STOP -> {
                    AppVisibility.foreground = false
                    // Drop dead sockets before WeChat / other apps take over.
                    graph.realtime.stop()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            AppVisibility.foreground = true
            graph.realtime.start()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            graph.realtime.stop()
        }
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(resumeTick) {
        fun present(note: NotificationDto) {
            if (!seen.add(note.id)) return
            val sameChat = AppVisibility.foreground && note.channelId != null && note.channelId == AppVisibility.channelId
            if (!sameChat) showWorkHubNotification(context, note)
        }
        try {
            withContext(Dispatchers.IO) {
                val missed = graph.api.unreadNotifications()
                missed.forEach(::present)
                if (missed.isNotEmpty()) graph.api.markNotificationsRead(missed.map { it.id })
            }
        } catch (_: Exception) {
        }
    }
    LaunchedEffect(Unit) {
        fun present(note: NotificationDto) {
            if (!seen.add(note.id)) return
            val sameChat = AppVisibility.foreground && note.channelId != null && note.channelId == AppVisibility.channelId
            if (!sameChat) showWorkHubNotification(context, note)
        }
        graph.realtime.events.collect { event ->
            val note = event.notification
            if (event.type == "notification" && note != null) {
                present(note)
                try {
                    withContext(Dispatchers.IO) {
                        graph.api.markNotificationsRead(listOf(note.id))
                    }
                } catch (_: Exception) {
                }
            }
        }
    }
}

private fun showWorkHubNotification(context: Context, note: NotificationDto) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            ALERT_CHANNEL,
            context.getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_HIGH,
        )
        manager.createNotificationChannel(channel)
    }
    val title = if (note.kind == "task_assigned") {
        context.getString(R.string.notif_assigned, note.title)
    } else {
        note.title
    }
    val body = if (note.kind == "task_assigned") {
        context.getString(R.string.notif_assigned_by, note.body)
    } else {
        note.body
    }
    val notification = NotificationCompat.Builder(context, ALERT_CHANNEL)
        .setSmallIcon(android.R.drawable.stat_notify_chat)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setContentIntent(NotificationIntents.contentIntent(context, note))
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .build()
    if (Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    ) {
        NotificationManagerCompat.from(context).notify(note.id.hashCode(), notification)
    }
}
