package com.ruggerocadamuro.myapplication.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ruggerocadamuro.myapplication.data.settings.ThemeMode

private val CockpitDarkScheme = darkColorScheme(
    primary = CockpitCoral,
    onPrimary = Obsidian,
    primaryContainer = Color(0xFF4A2A24),
    onPrimaryContainer = Color(0xFFFFDAD0),
    secondary = CockpitMint,
    onSecondary = Color(0xFF00382D),
    secondaryContainer = Color(0xFF164F43),
    onSecondaryContainer = Color(0xFFA5F5DA),
    tertiary = CockpitSky,
    onTertiary = Color(0xFF00344D),
    tertiaryContainer = Color(0xFF164A65),
    onTertiaryContainer = Color(0xFFC5E8FF),
    background = Obsidian,
    onBackground = CockpitText,
    surface = ObsidianElevated,
    onSurface = CockpitText,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = CockpitTextMuted,
    outline = CockpitOutline,
    outlineVariant = Color(0xFF343640),
    error = CockpitRed,
    onError = Color(0xFF3B0909),
    errorContainer = Color(0xFF5A1B1B),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val CockpitLightScheme = lightColorScheme(
    primary = CockpitCoralDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD0),
    onPrimaryContainer = Color(0xFF3B0904),
    secondary = Color(0xFF006B5B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFA5F5DA),
    onSecondaryContainer = Color(0xFF002019),
    tertiary = Color(0xFF006493),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC5E8FF),
    onTertiaryContainer = Color(0xFF001E30),
    background = Color(0xFFF7F5F3),
    onBackground = Color(0xFF1C1B1B),
    surface = Color(0xFFFFFBF9),
    onSurface = Color(0xFF1C1B1B),
    surfaceVariant = Color(0xFFF0E8E5),
    onSurfaceVariant = Color(0xFF514440),
    outline = Color(0xFF82736E),
    outlineVariant = Color(0xFFD5C4BE),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColorIndex: Int = -1,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val selectedAccent = AccentPalette.getOrNull(accentColorIndex)?.let {
        if (darkTheme) it.dark else it.light
    }
    val base = if (darkTheme) CockpitDarkScheme else CockpitLightScheme
    val scheme = selectedAccent?.let { base.copy(primary = it) } ?: base
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = {
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground) {
                Box(
                    Modifier.fillMaxSize().background(
                        if (darkTheme) {
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF232027), Obsidian),
                                radius = 900f
                            )
                        } else {
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFFFE9E0), Color(0xFFF7F5F3)),
                                radius = 1100f
                            )
                        }
                    )
                ) {
                    content()
                }
            }
        }
    )
}
