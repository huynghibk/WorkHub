package com.workhub.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.workhub.app.R

object AppPackages {
    const val ZALO = "com.zing.zalo"
    const val MESSENGER = "com.facebook.orca"
    const val WECHAT = "com.tencent.mm"
    const val MAPS = "com.google.android.apps.maps"
}

fun openGoogleMaps(context: Context, query: String? = null) {
    val coord = query?.trim()?.takeIf {
        it.matches(Regex("""^-?\d+(\.\d+)?\s*,\s*-?\d+(\.\d+)?$"""))
    }
    val uri = when {
        !coord.isNullOrBlank() -> {
            val parts = coord.split(",").map { it.trim() }
            Uri.parse("geo:${parts[0]},${parts[1]}?q=${Uri.encode(coord)}")
        }
        query.isNullOrBlank() -> Uri.parse("geo:0,0?q=")
        else -> Uri.parse("geo:0,0?q=" + Uri.encode(query))
    }
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage(AppPackages.MAPS)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    if (query.isNullOrBlank()) "https://maps.google.com"
                    else "https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query),
                ),
            ),
        )
    }
}

fun openGoogleSearch(context: Context, query: String = "") {
    val uri = if (query.isBlank()) {
        Uri.parse("https://www.google.com")
    } else {
        Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
    }
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}

fun openOrInstallApp(context: Context, packageName: String, webFallback: String) {
    val launch = context.packageManager.getLaunchIntentForPackage(packageName)
    if (launch != null) {
        context.startActivity(launch)
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webFallback)))
    }
}

fun openZalo(context: Context) =
    openOrInstallApp(context, AppPackages.ZALO, "https://zalo.me/")

fun openMessenger(context: Context) =
    openOrInstallApp(context, AppPackages.MESSENGER, "https://www.messenger.com/")

fun openWeChat(context: Context) =
    openOrInstallApp(context, AppPackages.WECHAT, "https://www.wechat.com/")

fun shareTextToPackage(context: Context, packageName: String, text: String, webFallback: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        setPackage(packageName)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        openOrInstallApp(context, packageName, webFallback)
    }
}

fun shareLocationToChatApp(context: Context, packageName: String, mapsUrl: String, webFallback: String) {
    shareTextToPackage(
        context,
        packageName,
        context.getString(R.string.share_location_text, mapsUrl),
        webFallback,
    )
}

fun currentMapsUrl(context: Context): String? {
    val location = lastKnownLocation(context) ?: return null
    return mapsUrlFrom(location.latitude, location.longitude)
}

fun mapsDirectionsUrl(from: String, to: String): String =
    "https://www.google.com/maps/dir/?api=1&origin=${Uri.encode(from)}&destination=${Uri.encode(to)}"

@Composable
fun AttachedLocationBar(
    locationUrl: String?,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (locationUrl.isNullOrBlank()) return
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(locationUrl)),
                    )
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            Text(
                stringResource(R.string.location_attached_label),
                maxLines = 1,
            )
        }
        if (onClear != null) {
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.location_clear),
                )
            }
        }
    }
}

@Composable
fun LocationAttachActions(
    enabled: Boolean = true,
    locationUrl: String? = null,
    onLocationChange: (String?) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AttachedLocationBar(
            locationUrl = locationUrl,
            onClear = { onLocationChange(null) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(enabled = enabled, onClick = { showPicker = true }) {
                Text(
                    if (locationUrl.isNullOrBlank()) {
                        stringResource(R.string.attach_location)
                    } else {
                        stringResource(R.string.location_change)
                    },
                )
            }
            TextButton(
                enabled = enabled,
                onClick = { openGoogleMaps(context) },
            ) {
                Text(stringResource(R.string.app_maps))
            }
        }
    }
    if (showPicker) {
        LocationPickerDialog(
            onDismiss = { showPicker = false },
            onPicked = { mapsUrl, _ ->
                onLocationChange(normalizeMapsShareLink(mapsUrl) ?: mapsUrl)
                showPicker = false
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.maps_attached),
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }
}
