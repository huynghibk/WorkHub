package com.workhub.app.ui

import android.app.Activity
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.workhub.app.data.LocalAppGraph
import com.workhub.app.data.UserSettings

internal val Royal = Color(0xFF1D4ED8)
internal val TaskWashLight = Color(0xFFEFF6FF)
internal val NoteWashLight = Color(0xFFECFDF5)
internal val NoteRail = Color(0xFF047857)
internal val DoneGreen = Color(0xFF15803D)
internal val SwipeDone = Color(0xFF16A34A)
internal val SwipeDelete = Color(0xFFDC2626)

private val LightColors = lightColorScheme(
    primary = Royal,
    onPrimary = Color.White,
    primaryContainer = TaskWashLight,
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF0F766E),
    onSecondary = Color.White,
    secondaryContainer = NoteWashLight,
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = DoneGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCFCE7),
    onTertiaryContainer = Color(0xFF14532D),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0B1220),
    primaryContainer = Color(0xFF172554),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF134E4A),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFF4ADE80),
    onTertiary = Color(0xFF052E16),
    tertiaryContainer = Color(0xFF14532D),
    onTertiaryContainer = Color(0xFFDCFCE7),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF1E1E24),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF2A2A32),
    onSurfaceVariant = Color(0xFFB4B4B4),
    outline = Color(0xFF2E2E36),
    outlineVariant = Color(0xFF2E2E36),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
)

@Composable
fun WorkHubTheme(content: @Composable () -> Unit) {
    val settings by LocalAppGraph.current.userPrefs.state.collectAsState(initial = UserSettings())
    val dark = when (settings.themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AndroidColor.TRANSPARENT
            window.navigationBarColor = AndroidColor.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}

@Composable
internal fun isAppDarkTheme(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
internal fun taskWash(): Color = MaterialTheme.colorScheme.primaryContainer

@Composable
internal fun noteWash(): Color = MaterialTheme.colorScheme.secondaryContainer

@Composable
internal fun taskRail(): Color = MaterialTheme.colorScheme.primary