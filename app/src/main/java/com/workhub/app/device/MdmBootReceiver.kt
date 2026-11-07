package com.workhub.app.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Sau khi máy khởi động lại, bật lại service MDM nếu đã đăng ký. */
class MdmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        MdmGuardService.startIfNeeded(context)
    }
}
