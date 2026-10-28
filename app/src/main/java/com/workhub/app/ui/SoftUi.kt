package com.workhub.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.workhub.app.R

/** Prefer platform ClipboardManager — Compose clipboard often fails on emulator. */
fun copyTextToClipboard(context: Context, text: String): Boolean {
    return runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        cm.setPrimaryClip(ClipData.newPlainText("WorkHub", text))
        true
    }.getOrDefault(false)
}

internal val SoftPill = RoundedCornerShape(14.dp)
internal val SoftCard = RoundedCornerShape(16.dp)
internal val SoftRadius = 14.dp
internal val BorderSubtle = Color(0xFFE5E7EB)
internal val PageBg = Color(0xFFF9FAFB)

@Composable
internal fun SoftBackdrop(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg),
        content = content,
    )
}

@Composable
internal fun SoftHeroBand(height: Dp = 220.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFEEF2FF), Color(0xFFF9FAFB), Color.Transparent),
                ),
            ),
    )
}

@Composable
internal fun SoftPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = SoftPill,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = Color(0xFFE5E7EB),
            disabledContentColor = Color(0xFF9CA3AF),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Copy / clear actions — reliable on emulator mouse where drag-select is awkward.
 */
@Composable
internal fun TextFieldClearCopyActions(
    value: String,
    onClear: () -> Unit,
    showClear: Boolean = true,
) {
    if (value.isEmpty()) return
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                val ok = copyTextToClipboard(context, value)
                Toast.makeText(
                    context,
                    context.getString(if (ok) R.string.copied_text else R.string.clipboard_unavailable),
                    Toast.LENGTH_SHORT,
                ).show()
            },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = stringResource(R.string.copy_text),
                modifier = Modifier.size(18.dp),
            )
        }
        if (showClear) {
            IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.clear_text),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun SoftField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    showClearCopy: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        shape = SoftPill,
        label = if (placeholder == null) ({ Text(label) }) else null,
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leading,
        trailingIcon = trailing ?: if (showClearCopy && value.isNotEmpty()) {
            { TextFieldClearCopyActions(value = value, onClear = { onValue("") }) }
        } else {
            null
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            disabledContainerColor = Color(0xFFF9FAFB),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = BorderSubtle,
            disabledBorderColor = BorderSubtle,
        ),
    )
}

fun Modifier.softCardChrome(): Modifier =
    border(1.dp, BorderSubtle, SoftCard)
