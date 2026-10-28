package com.workhub.app.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.workhub.app.MainActivity
import com.workhub.shared.NotificationDto

object NotificationIntents {
    const val EXTRA_OPEN_ROUTE = "workhub_open_route"
    const val EXTRA_CHANNEL_ID = "workhub_channel_id"
    const val EXTRA_NOTIFICATION_ID = "workhub_notification_id"

    fun contentIntent(context: Context, note: NotificationDto): PendingIntent {
        val route = when {
            note.kind == "task_assigned" -> "tasks"
            note.channelId != null -> "chat"
            else -> "home"
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_ROUTE, route)
            putExtra(EXTRA_NOTIFICATION_ID, note.id)
            note.channelId?.let { putExtra(EXTRA_CHANNEL_ID, it) }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(
            context,
            note.id.hashCode(),
            intent,
            flags,
        )
    }
}
