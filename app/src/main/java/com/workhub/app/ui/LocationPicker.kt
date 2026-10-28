package com.workhub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Attach-location uses the same Google Maps WebView that already loads in-app.
 * Search / pan / pin in Maps, then attach the current place link.
 */
@Composable
fun LocationPickerDialog(
    onDismiss: () -> Unit,
    onPicked: (mapsUrl: String, label: String) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = true,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.White),
        ) {
            MapsInAppScreen(
                onBack = onDismiss,
                onUseLink = { link ->
                    val label = labelFromMapsLink(link)
                    onPicked(link, label)
                },
            )
        }
    }
}

private fun labelFromMapsLink(link: String): String {
    Regex("""[?&]q=(-?\d+\.?\d*),(-?\d+\.?\d*)""").find(link)?.let { m ->
        return "${m.groupValues[1]}, ${m.groupValues[2]}"
    }
    Regex("""@(-?\d+\.?\d*),(-?\d+\.?\d*)""").find(link)?.let { m ->
        return "${m.groupValues[1]}, ${m.groupValues[2]}"
    }
    val q = Regex("""[?&](?:q|query)=([^&]+)""").find(link)?.groupValues?.getOrNull(1)
    return if (!q.isNullOrBlank()) {
        android.net.Uri.decode(q).replace('+', ' ')
    } else {
        ""
    }
}
