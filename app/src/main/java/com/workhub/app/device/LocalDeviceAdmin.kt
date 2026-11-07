package com.workhub.app.device

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.UserManager
import android.provider.Settings

object LocalDeviceAdmin {
    fun component(context: Context): ComponentName =
        ComponentName(context, WorkHubDeviceAdminReceiver::class.java)

    fun manager(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isActive(context: Context): Boolean =
        runCatching { manager(context).isAdminActive(component(context)) }.getOrDefault(false)

    /** Device Owner = máy công ty; user không tự gỡ admin / gỡ app được. */
    fun isDeviceOwner(context: Context): Boolean =
        runCatching { manager(context).isDeviceOwnerApp(context.packageName) }.getOrDefault(false)

    /** Mở màn hình hệ thống để user bật quyền quản trị thiết bị cho WorkHub. */
    fun requestEnableIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(com.workhub.app.R.string.enroll_admin_explain),
            )
        }

    fun requestIgnoreBatteryIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(context.packageName)) return null
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Khi đã là Device Owner (IT set bằng ADB sau reset): khóa gỡ app + hạn chế tắt bảo vệ.
     */
    fun applyOwnerProtections(context: Context) {
        if (!isDeviceOwner(context)) return
        val dpm = manager(context)
        val admin = component(context)
        val pkg = context.packageName
        runCatching { dpm.setUninstallBlocked(admin, pkg, true) }
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT) }
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET) }
    }

    fun lockNow(context: Context): Boolean {
        if (!isActive(context)) return false
        return runCatching {
            manager(context).lockNow()
            true
        }.getOrDefault(false)
    }

    /** Factory reset / xóa dữ liệu công việc. Cần Device Admin đã bật. */
    fun wipeData(context: Context): Boolean {
        if (!isActive(context)) return false
        return runCatching {
            val flags = if (Build.VERSION.SDK_INT >= 34) {
                DevicePolicyManager.WIPE_SILENTLY
            } else {
                0
            }
            manager(context).wipeData(flags)
            true
        }.getOrDefault(false)
    }

    fun localDeviceId(context: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty().ifBlank { "unknown" }
        return "local:$androidId"
    }

    fun deviceLabel(): String {
        val model = Build.MODEL.orEmpty().ifBlank { "Android" }
        val brand = Build.BRAND.orEmpty()
        return listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Android device" }
    }

    /** Lệnh ADB (máy công ty, sau factory reset) để thành Device Owner — user không tắt được. */
    fun deviceOwnerAdbCommand(context: Context): String =
        "adb shell dpm set-device-owner ${context.packageName}/.device.WorkHubDeviceAdminReceiver"
}
