package com.workhub.app.device

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.workhub.app.R

/**
 * Device Admin nội bộ cho máy không có Google services (OS nội địa Trung…).
 * Khóa / xóa dữ liệu qua [android.app.admin.DevicePolicyManager], lệnh từ server qua WebSocket.
 */
class WorkHubDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        LocalDeviceAdmin.applyOwnerProtections(context)
        Toast.makeText(context, R.string.enroll_admin_enabled, Toast.LENGTH_SHORT).show()
        if (MdmPrefs.isEnrolled(context)) {
            MdmGuardService.startIfNeeded(context)
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        MdmPrefs.setEnrolled(context, false)
        MdmGuardService.stop(context)
        Toast.makeText(context, R.string.enroll_admin_disabled, Toast.LENGTH_LONG).show()
    }
}
