package com.workhub.app.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.workhub.app.R

@Composable
internal fun HideKeyboardButton(always: Boolean = false) {
    val density = LocalDensity.current
    val imeOpen = WindowInsets.ime.getBottom(density) > 0
    if (!always && !imeOpen) return
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    IconButton(
        onClick = {
            focus.clearFocus(force = true)
            keyboard?.hide()
        },
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            Icons.Filled.KeyboardHide,
            contentDescription = stringResource(R.string.hide_keyboard),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
internal fun rememberHideKeyboard(): () -> Unit {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return {
        focus.clearFocus(force = true)
        keyboard?.hide()
    }
}

/**
 * Optional helper — do not attach on app root (breaks TextField selection).
 * Safe to use on empty chrome regions only.
 */
fun Modifier.dismissKeyboardOnTap(): Modifier = composed {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    pointerInput(focus, keyboard) {
        detectTapGestures(
            onTap = {
                focus.clearFocus(force = true)
                keyboard?.hide()
            },
        )
    }
}
