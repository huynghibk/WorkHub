package com.workhub.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream

object AvatarCodec {
    private const val MaxSide = 256
    private const val JpegQuality = 82

    fun encode(context: Context, uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("Không đọc được ảnh")
        stream.use { input ->
            val original = BitmapFactory.decodeStream(input)
                ?: error("Ảnh không hợp lệ")
            val scaled = scaleDown(original, MaxSide)
            if (scaled !== original) original.recycle()
            val bytes = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JpegQuality, bytes)
            if (!scaled.isRecycled) scaled.recycle()
            val b64 = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
            return "data:image/jpeg;base64,$b64"
        }
    }

    fun decode(dataUrl: String?): Bitmap? {
        if (dataUrl.isNullOrBlank()) return null
        val marker = ";base64,"
        val idx = dataUrl.indexOf(marker)
        if (idx < 0) return null
        return runCatching {
            val raw = Base64.decode(dataUrl.substring(idx + marker.length), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(raw, 0, raw.size)
        }.getOrNull()
    }

    private fun scaleDown(source: Bitmap, maxSide: Int): Bitmap {
        val w = source.width
        val h = source.height
        val longest = maxOf(w, h)
        if (longest <= maxSide) return source
        val scale = maxSide.toFloat() / longest.toFloat()
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, nw, nh, true)
    }
}

@Composable
fun ProfileAvatar(
    name: String,
    avatarDataUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    corner: Dp = 14.dp,
    background: Color = Color(0xFF111827),
    contentColor: Color = Color.White,
) {
    val bitmap = remember(avatarDataUrl) { AvatarCodec.decode(avatarDataUrl) }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                name.take(2).uppercase().ifBlank { "?" },
                color = contentColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
