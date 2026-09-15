package com.ruggerocadamuro.myapplication.ui.theme

import androidx.compose.ui.graphics.Color
import com.ruggerocadamuro.myapplication.R

/** Core palette for the Telemetry Cockpit visual language. */
val Obsidian = Color(0xFF07080C)
val ObsidianElevated = Color(0xFF11131A)
val ObsidianSurface = Color(0xFF171922)
val ObsidianSurfaceVariant = Color(0xFF252733)
val CockpitCoral = Color(0xFFFFA987)
val CockpitCoralDark = Color(0xFFC65D42)
val CockpitMint = Color(0xFF7DE2BF)
val CockpitSky = Color(0xFF8FD2FF)
val CockpitRed = Color(0xFFFF716D)
val CockpitText = Color(0xFFF6F3F0)
val CockpitTextMuted = Color(0xFFB8B6C0)
val CockpitOutline = Color(0xFF555663)

// Semantic colours are deliberately independent from the selected accent.
val LightTemperatureOk = Color(0xFF45C992)
val LightTemperatureWarning = Color(0xFFFFC857)
val LightTemperatureCritical = Color(0xFFFF716D)

/**
 * The accent palette remains user-selectable, but all options are tuned for
 * readable telemetry instead of decorative gradients.
 */
data class AccentOption(val labelRes: Int, val dark: Color, val light: Color)

val AccentPalette: List<AccentOption> = listOf(
    AccentOption(R.string.accent_purple, Color(0xFFD0BCFF), Color(0xFF6750A4)),
    AccentOption(R.string.accent_vesc_green, Color(0xFF7DE2BF), Color(0xFF006B5B)),
    AccentOption(R.string.accent_blue, Color(0xFF8FD2FF), Color(0xFF006493)),
    AccentOption(R.string.accent_coral, CockpitCoral, CockpitCoralDark),
    AccentOption(R.string.accent_red, Color(0xFFFFB4AB), Color(0xFFBA1A1A)),
    AccentOption(R.string.accent_yellow, Color(0xFFFFC857), Color(0xFF795900)),
    AccentOption(R.string.accent_magenta, Color(0xFFFFABF2), Color(0xFF9C278F)),
    AccentOption(R.string.accent_cyan, Color(0xFF84F1E0), Color(0xFF006B60))
)

/** BLE badge background: keeps the Bluetooth glyph legible in both themes. */
val BleBadgeBackground = Color(0xFFE6E8ED)

/** MOSFET thresholds used by the dashboard and map inspector. */
object TempThresholds {
    const val MOSFET_WARN = 55f
    const val MOSFET_DANGER = 75f
}
