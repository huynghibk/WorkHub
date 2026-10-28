package com.workhub.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.workhub.app.R
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun BrandHero(
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    compact: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "brand")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFEEF2FF), Color(0xFFF9FAFB), Color(0x00F9FAFB)),
                ),
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            )
            .padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!compact) {
            Image(
                painter = painterResource(R.drawable.ic_brand_mark),
                contentDescription = "WorkHub",
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "WorkHub",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
        }
        ConnectorScene(
            pulse = pulse,
            spin = spin,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.brand_tagline),
            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun ConnectorScene(
    pulse: Float,
    spin: Float,
    modifier: Modifier = Modifier,
) {
    val nodeColors = remember {
        listOf(
            Color(0xFF4F46E5),
            Color(0xFF06B6D4),
            Color(0xFF10B981),
            Color(0xFF0284C7),
            Color(0xFF6366F1),
            Color(0xFF0EA5E9),
        )
    }
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(size.width, size.height) * 0.34f
        val ringStroke = Stroke(width = 2.dp.toPx())
        drawCircle(
            color = Color.White.copy(alpha = 0.45f),
            radius = radius * (0.72f + 0.08f * sin(pulse * Math.PI * 2).toFloat()),
            center = Offset(cx, cy),
            style = ringStroke,
        )
        drawCircle(
            color = Color(0xFF4F46E5).copy(alpha = 0.18f),
            radius = radius * (0.95f + 0.06f * cos(pulse * Math.PI * 2).toFloat()),
            center = Offset(cx, cy),
            style = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), phase = pulse * 40f),
            ),
        )
        val nodes = nodeColors.mapIndexed { index, color ->
            val angle = Math.toRadians((spin + index * (360f / nodeColors.size)).toDouble())
            val orbit = radius * (0.82f + 0.08f * sin((pulse + index * 0.15f) * Math.PI * 2).toFloat())
            Triple(
                Offset(cx + (cos(angle) * orbit).toFloat(), cy + (sin(angle) * orbit).toFloat()),
                color,
                10.dp.toPx() + (index % 3) * 2.dp.toPx(),
            )
        }
        nodes.forEach { (point, color, _) ->
            drawLine(
                color = color.copy(alpha = 0.35f),
                start = Offset(cx, cy),
                end = point,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        for (i in nodes.indices) {
            val a = nodes[i]
            val b = nodes[(i + 2) % nodes.size]
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = a.first,
                end = b.first,
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), phase = -pulse * 24f),
            )
        }
        drawCircle(color = Color.White, radius = 18.dp.toPx(), center = Offset(cx, cy))
        drawCircle(color = Color(0xFF4F46E5), radius = 10.dp.toPx(), center = Offset(cx, cy))
        nodes.forEach { (point, color, radiusPx) ->
            drawCircle(color = Color.White, radius = radiusPx + 3.dp.toPx(), center = point)
            drawCircle(color = color, radius = radiusPx, center = point)
            drawCircle(
                color = Color(0xFF22A06B),
                radius = 3.5.dp.toPx(),
                center = Offset(point.x + radiusPx * 0.55f, point.y + radiusPx * 0.55f),
            )
        }
        val packet = ((pulse * nodes.size) % nodes.size).toInt().coerceIn(0, nodes.lastIndex)
        val next = (packet + 1) % nodes.size
        val t = (pulse * nodes.size) % 1f
        val from = nodes[packet].first
        val to = nodes[next].first
        val moving = Offset(
            from.x + (to.x - from.x) * t,
            from.y + (to.y - from.y) * t,
        )
        drawCircle(color = Color(0xFF06B6D4), radius = 5.dp.toPx(), center = moving)
        drawCircle(color = Color.White.copy(alpha = 0.7f), radius = 2.dp.toPx(), center = moving)
    }
}
