package com.workhub.app.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.workhub.app.R
import com.workhub.app.data.LocalAppGraph
import com.workhub.app.device.DeviceCommandRouter

private const val FIND_CHANNEL = "workhub_find_device"
const val ACTION_STOP_FIND_ALARM = "com.workhub.app.STOP_FIND_ALARM"

object DeviceFindAlarm {
    @Volatile private var ringtone: Ringtone? = null
    @Volatile private var previousVolume: Int? = null

    fun start(context: Context) {
        stop(context)
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        previousVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        audio.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0,
        )
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val tone = RingtoneManager.getRingtone(context.applicationContext, uri)
        if (Build.VERSION.SDK_INT >= 28) {
            tone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            tone.isLooping = true
        }
        tone.play()
        ringtone = tone
        vibrate(context)
        showStopNotification(context)
    }

    fun stop(context: Context) {
        runCatching { ringtone?.stop() }
        ringtone = null
        previousVolume?.let { vol ->
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.setStreamVolume(AudioManager.STREAM_ALARM, vol, 0)
        }
        previousVolume = null
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.cancel()
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(FIND_NOTIFY_ID)
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 300, 500), 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 500, 300, 500), 0)
        }
    }

    private const val FIND_NOTIFY_ID = 71001

    private fun showStopNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    FIND_CHANNEL,
                    context.getString(R.string.mdm_find_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
        val stop = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_STOP_FIND_ALARM).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, FIND_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.mdm_find_title))
            .setContentText(context.getString(R.string.mdm_find_body))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(0, context.getString(R.string.mdm_find_stop), stop)
            .build()
        manager.notify(FIND_NOTIFY_ID, notification)
    }
}

class StopFindAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_STOP_FIND_ALARM) {
            DeviceFindAlarm.stop(context)
        }
    }
}

@Composable
fun DeviceCommandBinder() {
    val context = LocalContext.current
    val graph = LocalAppGraph.current
    DisposableEffect(Unit) {
        val receiver = StopFindAlarmReceiver()
        val filter = IntentFilter(ACTION_STOP_FIND_ALARM)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            DeviceFindAlarm.stop(context)
        }
    }
    LaunchedEffect(Unit) {
        // Khi MdmGuardService đang chạy, service đã xử lý lệnh — tránh double lock/wipe/alarm.
        graph.realtime.events.collect { event ->
            if (com.workhub.app.device.MdmGuardService.isRunning()) return@collect
            DeviceCommandRouter.handle(context, graph, event)
        }
    }
}
