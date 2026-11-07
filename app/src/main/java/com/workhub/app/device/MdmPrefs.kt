package com.workhub.app.device

import android.content.Context

/** Đánh dấu máy user đã đăng ký MDM nội bộ → bật service chạy nền. */
object MdmPrefs {
    private const val NAME = "workhub_mdm"
    private const val KEY_ENROLLED = "enrolled"

    fun isEnrolled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENROLLED, false)

    fun setEnrolled(context: Context, value: Boolean) {
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENROLLED, value)
            .apply()
    }
}
