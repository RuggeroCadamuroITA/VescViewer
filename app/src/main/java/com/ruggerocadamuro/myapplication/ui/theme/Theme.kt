package com.ruggerocadamuro.myapplication.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ruggerocadamuro.myapplication.data.settings.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

/**
 * Tema dell'app, esteso per supportare:
 *  - modalita' chiara/scura/sistema (tri-stato, persistita in DataStore)
 *  - colore accento personalizzato dalla palette [AccentPalette]
 *
 * Quando l'utente ha scelto un accento esplicito disattiviamo il dynamic color
 * di Android 12+ (che altrimenti sovrascriverebbe primary), e ricopiamo lo
 * scheme generato con il nostro primary. Con accent = -1 si mantiene il
 * comportamento originale del template (dynamic color su Android 12+).
 */
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

    val accent: Color? = if (accentColorIndex in AccentPalette.indices) {
        val option = AccentPalette[accentColorIndex]
        if (darkTheme) option.dark else option.light
    } else {
        null
    }

    val colorScheme = when {
        accent != null -> {
            val base = if (darkTheme) DarkColorScheme else LightColorScheme
            // mantieni la struttura dello scheme ma sostituisci primary/tertiary
            base.copy(primary = accent)
        }
        // Dynamic color is available on Android 12+
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // status bar coerente con il tema
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        colors = if (darkTheme) {
                            listOf(Color(0xFF14141C), Color(0xFF05050A))
                        } else {
                            listOf(Color(0xFFF4F1F8), Color(0xFFE7E5EC))
                        }
                    )
                )
            ) {
                content()
            }
        }
    )
}
