package com.ruggerocadamuro.myapplication.ui.components

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ruggerocadamuro.myapplication.data.settings.AppLanguage

/**
 * Scelta della lingua: due colonne con il nome nativo di ogni lingua.
 * Riutilizzata sia nel setup guidato sia nelle impostazioni.
 *
 * Il nome di ogni lingua resta scritto nella lingua stessa ("Deutsch",
 * "Español"): e' l'unico modo per riconoscerla quando l'interfaccia e' ancora
 * in una lingua che non si legge.
 */
@Composable
fun LanguageChooser(
    selected: AppLanguage?,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppLanguage.entries.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { language ->
                    ChoiceCard(
                        label = language.nativeName,
                        selected = selected == language,
                        onClick = { onSelect(language) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Con un numero dispari di lingue l'ultima riga resta allineata.
                if (pair.size == 1) {
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Applica la lingua scelta al volo. Su Android 13+ la ricreazione la gestisce
 * il sistema (LocaleManager); sotto la 13 ricreiamo noi l'Activity, altrimenti
 * le risorse resterebbero quelle della vecchia lingua.
 */
@Composable
fun rememberLanguageApplier(onApply: (AppLanguage) -> Unit): (AppLanguage) -> Unit {
    val context = LocalContext.current
    return { language ->
        onApply(language)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            (context as? Activity)?.recreate()
        }
    }
}
