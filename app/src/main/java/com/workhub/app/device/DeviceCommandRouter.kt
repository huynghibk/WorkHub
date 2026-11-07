package com.workhub.app.device

import android.content.Context
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.workhub.app.R
import com.workhub.app.data.AppGraph
import com.workhub.app.ui.DeviceFindAlarm
import com.workhub.app.ui.awaitBestLocation
import com.workhub.app.ui.mapsUrlFrom
import com.workhub.shared.MdmRemoteSubmitRequest
import com.workhub.shared.WsEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DeviceCommandRouter {
    private const val REMOTE_CHANNEL = "workhub_mdm_remote"
    private const val REMOTE_NOTIFY_ID = 71020

    suspend fun handle(context: Context, graph: AppGraph, event: WsEvent) {
        when (event.type) {
            "play_alarm" -> DeviceFindAlarm.start(context)
            "device_lock" -> LocalDeviceAdmin.lockNow(context)
            "device_wipe" -> LocalDeviceAdmin.wipeData(context)
            "lost_mode_start" -> startLostMode(context, event.alertText.orEmpty())
            "lost_mode_stop" -> stopLostMode(context)
            "location_request" -> {
                val loc = withContext(Dispatchers.IO) { awaitBestLocation(context) } ?: return
                val url = mapsUrlFrom(loc.latitude, loc.longitude)
                runCatching {
                    withContext(Dispatchers.IO) {
                        graph.api.reportDeviceLocation(url, event.deviceName.orEmpty())
                    }
                }
            }
            "remote_camera" -> {
                showRemoteNotice(context, context.getString(R.string.mdm_remote_camera_notice))
                val facing = event.facing ?: "front"
                val file = RemoteCameraCapture.capture(context, facing)
                if (file != null) {
                    val uploaded = runCatching {
                        withContext(Dispatchers.IO) { graph.api.upload(file, file.name) }
                    }.getOrNull()
                    submit(
                        graph,
                        MdmRemoteSubmitRequest(
                            requestId = event.requestId.orEmpty(),
                            kind = "camera",
                            title = "Camera $facing",
                            url = uploaded?.url.orEmpty(),
                            deviceName = LocalDeviceAdmin.localDeviceId(context),
                        ),
                    )
                } else {
                    submit(
                        graph,
                        MdmRemoteSubmitRequest(
                            requestId = event.requestId.orEmpty(),
                            kind = "camera",
                            title = "Camera",
                            body = "Không chụp được (thiếu quyền CAMERA hoặc lỗi phần cứng)",
                            deviceName = LocalDeviceAdmin.localDeviceId(context),
                        ),
                    )
                }
            }
            "remote_media" -> {
                showRemoteNotice(context, context.getString(R.string.mdm_remote_media_notice))
                val limit = event.limit ?: 10
                val images = withContext(Dispatchers.IO) {
                    RemoteCollectors.collectRecentImages(context, limit)
                }
                if (images.isEmpty()) {
                    submit(
                        graph,
                        MdmRemoteSubmitRequest(
                            requestId = event.requestId.orEmpty(),
                            kind = "media",
                            title = "Ảnh",
                            body = "Không đọc được ảnh (thiếu quyền hoặc thư viện trống)",
                            deviceName = LocalDeviceAdmin.localDeviceId(context),
                        ),
                    )
                } else {
                    images.forEach { item ->
                        val uploaded = runCatching {
                            withContext(Dispatchers.IO) { graph.api.upload(item.file, item.title) }
                        }.getOrNull()
                        submit(
                            graph,
                            MdmRemoteSubmitRequest(
                                requestId = event.requestId.orEmpty(),
                                kind = "media",
                                title = item.title,
                                url = uploaded?.url.orEmpty(),
                                deviceName = LocalDeviceAdmin.localDeviceId(context),
                            ),
                        )
                    }
                }
            }
            "remote_sms" -> {
                showRemoteNotice(context, context.getString(R.string.mdm_remote_sms_notice))
                val text = withContext(Dispatchers.IO) {
                    RemoteCollectors.collectSms(context, event.limit ?: 30)
                }
                submit(
                    graph,
                    MdmRemoteSubmitRequest(
                        requestId = event.requestId.orEmpty(),
                        kind = "sms",
                        title = text?.title ?: "SMS",
                        body = text?.body.orEmpty(),
                        deviceName = LocalDeviceAdmin.localDeviceId(context),
                    ),
                )
            }
            "remote_contacts" -> {
                showRemoteNotice(context, context.getString(R.string.mdm_remote_contacts_notice))
                val text = withContext(Dispatchers.IO) {
                    RemoteCollectors.collectContacts(context, event.limit ?: 100)
                }
                submit(
                    graph,
                    MdmRemoteSubmitRequest(
                        requestId = event.requestId.orEmpty(),
                        kind = "contacts",
                        title = text?.title ?: "Danh bạ",
                        body = text?.body.orEmpty(),
                        deviceName = LocalDeviceAdmin.localDeviceId(context),
                    ),
                )
            }
        }
    }

    private suspend fun submit(graph: AppGraph, body: MdmRemoteSubmitRequest) {
        runCatching {
            withContext(Dispatchers.IO) { graph.api.mdmSubmitRemoteResult(body) }
        }
    }

    private fun startLostMode(context: Context, message: String) {
        LostModeActivity.active = true
        showRemoteNotice(context, context.getString(R.string.lost_mode_title))
        val intent = Intent(context, LostModeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(LostModeActivity.EXTRA_MESSAGE, message)
        }
        context.startActivity(intent)
    }

    private fun stopLostMode(context: Context) {
        LostModeActivity.active = false
        DeviceFindAlarm.stop(context)
        // Đóng LostModeActivity bằng broadcast nhẹ — activity tự finish nếu nhận.
        context.sendBroadcast(
            Intent(ACTION_STOP_LOST_MODE).setPackage(context.packageName),
        )
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(REMOTE_NOTIFY_ID)
    }

    fun showRemoteNotice(context: Context, text: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    REMOTE_CHANNEL,
                    context.getString(R.string.mdm_remote_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
        val notification = NotificationCompat.Builder(context, REMOTE_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(context.getString(R.string.mdm_remote_title))
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(REMOTE_NOTIFY_ID, notification)
    }

    const val ACTION_STOP_LOST_MODE = "com.workhub.app.STOP_LOST_MODE"
}
