package com.ruggerocadamuro.myapplication.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ruggerocadamuro.myapplication.data.settings.GaugeStyle
import com.ruggerocadamuro.myapplication.data.settings.IconVariant
import com.ruggerocadamuro.myapplication.data.settings.SpeedUnit
import com.ruggerocadamuro.myapplication.data.settings.TempUnit
import com.ruggerocadamuro.myapplication.data.settings.ThemeMode
import com.ruggerocadamuro.myapplication.ui.theme.AccentPalette

/**
 * Schermata impostazioni: tutto applicato a caldo (DataStore + StateFlow),
 * senza restart dell'app.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Impostazioni", style = MaterialTheme.typography.titleLarge)

        // -------------------------------------------------- Aspetto
        SettingsSection("Aspetto") {
            Text("Colore accento", style = MaterialTheme.typography.titleSmall)
            Text(
                "Applicato subito a tutta l'interfaccia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                // primo cerchio: colore di default (accento = -1)
                ColorDot(
                    color = MaterialTheme.colorScheme.primary,
                    selected = settings.accentColorIndex == -1,
                    label = "Auto"
                ) { viewModel.setAccentColor(-1) }
                AccentPalette.forEachIndexed { index, option ->
                    ColorDot(
                        color = option.dark,
                        selected = settings.accentColorIndex == index,
                        label = option.label
                    ) { viewModel.setAccentColor(index) }
                }
            }

            Text("Modalità tema", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)
                    ) {
                        Text(
                            when (mode) {
                                ThemeMode.LIGHT -> "Chiaro"
                                ThemeMode.DARK -> "Scuro"
                                ThemeMode.SYSTEM -> "Sistema"
                            }
                        )
                    }
                }
            }

            Text("Stile delle gauge", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                GaugeStyle.entries.forEachIndexed { i, style ->
                    SegmentedButton(
                        selected = settings.gaugeStyle == style,
                        onClick = { viewModel.setGaugeStyle(style) },
                        shape = SegmentedButtonDefaults.itemShape(i, GaugeStyle.entries.size)
                    ) {
                        Text(if (style == GaugeStyle.ANALOG) "Analogiche" else "Digitali")
                    }
                }
            }

            Text("Icona dell'app", style = MaterialTheme.typography.titleSmall)
            Text(
                "Il launcher aggiorna l'icona entro pochi secondi. " +
                    "Su Android la tecnica usa activity-alias nel Manifest.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                listOf(
                    IconVariant.DEFAULT to "Originale",
                    IconVariant.V1 to "Smeraldo",
                    IconVariant.V2 to "Arancio"
                ).forEach { (variant, label) ->
                    Card(
                        modifier = Modifier
                            .clickable { viewModel.applyIconVariant(variant) }
                            .weight(1f)
                    ) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .background(
                                        when (variant) {
                                            IconVariant.DEFAULT -> Color(0xFF6650a4)
                                            IconVariant.V1 -> Color(0xFF00695C)
                                            IconVariant.V2 -> Color(0xFFE65100)
                                        },
                                        CircleShape
                                    )
                            )
                            Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                            Text(
                                if (settings.iconVariant == variant) "Attiva" else "Scegli",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (settings.iconVariant == variant) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------- Unita'
        SettingsSection("Unità di misura") {
            Text("Velocità", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SpeedUnit.entries.forEachIndexed { i, unit ->
                    SegmentedButton(
                        selected = settings.speedUnit == unit,
                        onClick = { viewModel.setSpeedUnit(unit) },
                        shape = SegmentedButtonDefaults.itemShape(i, SpeedUnit.entries.size)
                    ) { Text(if (unit == SpeedUnit.KMH) "km/h" else "mph") }
                }
            }
            Text("Temperature", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TempUnit.entries.forEachIndexed { i, unit ->
                    SegmentedButton(
                        selected = settings.tempUnit == unit,
                        onClick = { viewModel.setTempUnit(unit) },
                        shape = SegmentedButtonDefaults.itemShape(i, TempUnit.entries.size)
                    ) { Text(if (unit == TempUnit.CELSIUS) "°C" else "°F") }
                }
            }
        }

        // -------------------------------------------------- Veicolo
        SettingsSection("Parametri veicolo") {
            Text(
                "Servono per convertire gli ERPM in velocità reale e il tachimetro in distanza.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SliderSetting(
                "Coppie polari motore", "${settings.polePairs}",
                settings.polePairs.toFloat(), 1f, 30f, 1f
            ) { viewModel.setPolePairs(it.toInt()) }
            SliderSetting(
                "Diametro ruota", "%.1f cm".format(settings.wheelDiameterCm),
                settings.wheelDiameterCm, 10f, 120f, 1f
            ) { viewModel.setWheelDiameterCm(it) }
            SliderSetting(
                "Riduzione (motore : ruota)", "%.2f".format(settings.gearRatio),
                settings.gearRatio, 1f, 10f, 1f
            ) { viewModel.setGearRatio(it) }
            SliderSetting(
                "Celle batteria (S)", "${settings.batteryCells}S",
                settings.batteryCells.toFloat(), 6f, 20f, 1f
            ) { viewModel.setBatteryCells(it.toInt()) }
        }

        // -------------------------------------------------- Allarme
        SettingsSection("Allarme anti-allontanamento") {
            Text(
                "Soglia segnale: sotto questo RSSI (dBm) per N secondi consecutivi scatta la sirena.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SliderSetting(
                "Soglia RSSI", "${settings.alarmThresholdDbm} dBm",
                settings.alarmThresholdDbm.toFloat(), -90f, -60f, 1f
            ) { viewModel.setAlarmThresholdDbm(it.toInt()) }
            SliderSetting(
                "Antirimbalzo (debounce)", "${settings.alarmDebounceSeconds}s sotto soglia",
                settings.alarmDebounceSeconds.toFloat(), 1f, 30f, 1f
            ) { viewModel.setAlarmDebounceSeconds(it.toInt()) }
            SwitchSetting(
                "Auto-stop al rientro",
                "La sirena si ferma da sola quando il segnale torna sopra soglia",
                settings.alarmAutoStopOnReturn
            ) { viewModel.setAlarmAutoStop(it) }
            SwitchSetting(
                "Allarme anche a disconnessione improvvisa",
                "Se il link BLE cade di colpo (furto/rullo) tratta l'evento come segnale perso",
                settings.alarmOnDisconnect
            ) { viewModel.setAlarmOnDisconnect(it) }

            HorizontalDivider(Modifier.padding(vertical = 6.dp))

            Text(
                "Affidabilità in background",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "Per far funzionare l'allarme a schermo spento Android deve lasciare vivere il " +
                    "processo: escludi l'app dall'ottimizzazione batteria.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    // Intent verso le impostazioni di sistema dell'ottimizzazione batteria
                    val i = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    } else {
                        Intent(Settings.ACTION_SETTINGS)
                    }
                    runCatching { context.startActivity(i) }
                },
                modifier = Modifier.padding(top = 4.dp)
            ) { Text("Apri ottimizzazione batteria") }
        }

        Text(
            "VESC Companion · lettura telemetria via BLE (COMM_GET_VALUES / SELECTIVE). " +
                "L'app è di sola lettura: non invia mai comandi di controllo al motore.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(36.dp)
                .background(color, CircleShape)
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                    shape = CircleShape
                )
                .clickable(onClick = onClick)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
    }
}

@Composable
private fun SliderSetting(
    label: String,
    valueText: String,
    value: Float,
    min: Float,
    max: Float,
    steps: Float,
    onChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = onChange,
            valueRange = min..max,
            steps = steps.toInt() - 1
        )
    }
}

@Composable
private fun SwitchSetting(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
