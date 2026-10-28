package com.workhub.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.workhub.app.R

data class DeviceContact(
    val id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
)

fun loadDeviceContacts(context: Context): List<DeviceContact> {
    val byId = linkedMapOf<String, DeviceContact>()
    val resolver = context.contentResolver
    resolver.query(
        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Email.CONTACT_ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Email.ADDRESS,
        ),
        null,
        null,
        ContactsContract.Contacts.DISPLAY_NAME + " ASC",
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
        val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
        val emailIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
        while (cursor.moveToNext()) {
            val id = cursor.getString(idIndex) ?: continue
            val name = cursor.getString(nameIndex).orEmpty().ifBlank { "—" }
            val email = cursor.getString(emailIndex)?.trim()?.ifBlank { null }
            val current = byId[id]
            byId[id] = DeviceContact(
                id = id,
                name = current?.name ?: name,
                email = email ?: current?.email,
                phone = current?.phone,
            )
        }
    }
    resolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        ),
        null,
        null,
        ContactsContract.Contacts.DISPLAY_NAME + " ASC",
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
        val phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (cursor.moveToNext()) {
            val id = cursor.getString(idIndex) ?: continue
            val name = cursor.getString(nameIndex).orEmpty().ifBlank { "—" }
            val phone = cursor.getString(phoneIndex)?.trim()?.ifBlank { null }
            val current = byId[id]
            byId[id] = DeviceContact(
                id = id,
                name = current?.name ?: name,
                email = current?.email,
                phone = phone ?: current?.phone,
            )
        }
    }
    return byId.values
        .filter { !it.email.isNullOrBlank() || !it.phone.isNullOrBlank() }
        .sortedBy { it.name.lowercase() }
}

fun shareInviteToContacts(context: Context, inviteCode: String, contacts: List<DeviceContact>) {
    val text = context.getString(R.string.invite_share_text, inviteCode)
    val emails = contacts.mapNotNull { it.email }.distinct()
    val phones = contacts.mapNotNull { it.phone }.distinct()
    when {
        emails.isNotEmpty() -> {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, emails.toTypedArray())
                putExtra(Intent.EXTRA_SUBJECT, "WorkHub")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.invite_teammates)))
        }
        phones.isNotEmpty() -> {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:" + phones.joinToString(";"))
                putExtra("sms_body", text)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.invite_teammates)))
        }
        else -> shareInviteText(context, inviteCode)
    }
}
