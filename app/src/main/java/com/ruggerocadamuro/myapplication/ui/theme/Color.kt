package com.ruggerocadamuro.myapplication.ui.theme

import androidx.compose.ui.graphics.Color

// Colori del template originale
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ---------------------------------------------------------------------
// Palette di accento selezionabile dalle impostazioni.
// Per ogni accento esistono le due varianti (chiara per tema scuro,
// scura per tema chiaro) usate come "primary" del rispettivo scheme.
// ---------------------------------------------------------------------
data class AccentOption(val label: String, val dark: Color, val light: Color)

val AccentPalette: List<AccentOption> = listOf(
    AccentOption("Viola", Color(0xFFD0BCFF), Color(0xFF6650a4)),
    AccentOption("Verde VESC", Color(0xFF80D6B5), Color(0xFF006963)),
    AccentOption("Azzurro", Color(0xFF9CCFFF), Color(0xFF006494)),
    AccentOption("Arancio", Color(0xFFFFB68F), Color(0xFFBF3600)),
    AccentOption("Rosso", Color(0xFFFFB4AB), Color(0xFFBA1A1A)),
    AccentOption("Giallo", Color(0xFFE9C419), Color(0xFF6D4E00)),
    AccentOption("Magenta", Color(0xFFFFABF2), Color(0xFF9C27B0)),
    AccentOption("Ciano", Color(0xFF84F1E0), Color(0xFF006B60))
)

/** Soglie di temperatura per la colorazione verde/giallo/rossa. */
object TempThresholds {
    const val MOSFET_WARN = 55f   // gradi C
    const val MOSFET_DANGER = 75f
    const val MOTOR_WARN = 60f
    const val MOTOR_DANGER = 85f
}
