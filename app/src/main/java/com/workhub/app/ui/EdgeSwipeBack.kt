package com.workhub.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * iPhone-like swipe from the left edge to go back.
 * Invokes the platform back dispatcher so nested [androidx.activity.compose.BackHandler]s work.
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
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.position.x > edgePx) return@awaitEachGesture
        var dragX = 0f
        var dragY = 0f
        var tracking = true
        drag(down.id) { change ->
            val delta = change.positionChange()
            dragX += delta.x
            dragY += delta.y
            // Cancel if clearly a vertical scroll.
            if (tracking && kotlin.math.abs(dragY) > 36f && kotlin.math.abs(dragY) > kotlin.math.abs(dragX)) {
                tracking = false
                return@drag
            }
            if (tracking && dragX > 12f) {
                change.consume()
            }
        }
        if (tracking && dragX >= triggerPx) {
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
