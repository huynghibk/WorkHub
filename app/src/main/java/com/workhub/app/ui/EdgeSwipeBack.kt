package com.workhub.app.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Swipe from the left or right screen edge to go back.
 * Right-edge helps when a drawer steals the left edge (e.g. chat sidebar).
 */
fun Modifier.edgeSwipeToBack(
    enabled: Boolean = true,
    edgeWidth: Dp = 28.dp,
    triggerDistance: Dp = 88.dp,
    onBack: () -> Unit,
): Modifier = pointerInput(enabled, edgeWidth, triggerDistance) {
    if (!enabled) return@pointerInput
    val edgePx = edgeWidth.toPx()
    val triggerPx = triggerDistance.toPx()
    val width = size.width.toFloat()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val fromLeft = down.position.x <= edgePx
        val fromRight = down.position.x >= width - edgePx
        if (!fromLeft && !fromRight) return@awaitEachGesture
        var dragX = 0f
        var dragY = 0f
        var tracking = true
        drag(down.id) { change ->
            val delta = change.positionChange()
            dragX += delta.x
            dragY += delta.y
            if (tracking && kotlin.math.abs(dragY) > 36f && kotlin.math.abs(dragY) > kotlin.math.abs(dragX)) {
                tracking = false
                return@drag
            }
            val progress = if (fromLeft) dragX else -dragX
            if (tracking && progress > 12f) {
                change.consume()
            }
        }
        val progress = if (fromLeft) dragX else -dragX
        if (tracking && progress >= triggerPx) {
            onBack()
        }
    }
}

@Composable
fun rememberEdgeSwipeBack(enabled: Boolean = true): Modifier {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    return remember(enabled, dispatcher) {
        Modifier.edgeSwipeToBack(enabled = enabled) {
            dispatcher?.onBackPressed()
        }
    }
}
