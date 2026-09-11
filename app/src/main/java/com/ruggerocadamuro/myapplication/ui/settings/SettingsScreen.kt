package com.ruggerocadamuro.myapplication.ui.settings

import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ruggerocadamuro.myapplication.data.settings.GaugeStyle
import com.ruggerocadamuro.myapplication.data.settings.IconVariant
import com.ruggerocadamuro.myapplication.data.settings.SpeedUnit
import com.ruggerocadamuro.myapplication.data.settings.TempUnit
import com.ruggerocadamuro.myapplication.data.settings.ThemeMode
import com.ruggerocadamuro.myapplication.ui.theme.AccentPalette

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Impostazioni", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Personalizza la tua esperienza VescViewer", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SettingsSection(
            title = "Aspetto",
            subtitle = "Tema, colori e leggibilità",
            icon = Icons.Filled.Brightness6
        ) {
            SettingLabel("Colore accento")
            Text("Scegli un colore riconoscibile per i dati importanti.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColorDot(MaterialTheme.colorScheme.primary, settings.accentColorIndex == -1, "Auto") {
                    viewModel.setAccentColor(-1)
                }
                AccentPalette.forEachIndexed { index, option ->
                    ColorDot(option.dark, settings.accentColorIndex == index, option.label) {
                        viewModel.setAccentColor(index)
                    }
                }
            }
            SettingLabel("Modalità tema")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)
                    ) {
                        Text(when (mode) {
                            ThemeMode.LIGHT -> "Chiaro"
                            ThemeMode.DARK -> "Scuro"
                            ThemeMode.SYSTEM -> "Sistema"
                        })
                    }
                }
            }
            SettingLabel("Stile dati")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                GaugeStyle.entries.forEachIndexed { index, style ->
                    SegmentedButton(
                        selected = settings.gaugeStyle == style,
                        onClick = { viewModel.setGaugeStyle(style) },
                        shape = SegmentedButtonDefaults.itemShape(index, GaugeStyle.entries.size)
                    ) { Text(if (style == GaugeStyle.ANALOG) "Analogico" else "Digitale") }
                }
            }
            SettingLabel("Icona app")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    IconVariant.DEFAULT to "Originale",
                    IconVariant.V1 to "Smeraldo",
                    IconVariant.V2 to "Arancio"
                ).forEach { (variant, label) ->
                    IconChoice(
                        label = label,
                        color = when (variant) {
                            IconVariant.DEFAULT -> Color(0xFF6650A4)
                            IconVariant.V1 -> Color(0xFF00695C)
                            IconVariant.V2 -> Color(0xFFE65100)
                        },
                        selected = settings.iconVariant == variant,
                        modifier = Modifier.weight(1f)
                    ) { viewModel.applyIconVariant(variant) }
                }
            }
        }

        SettingsSection(
            title = "Unità di misura",
            subtitle = "Come vuoi leggere i valori",
            icon = Icons.Filled.Speed
        ) {
            SettingLabel("Velocità")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SpeedUnit.entries.forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = settings.speedUnit == unit,
                        onClick = { viewModel.setSpeedUnit(unit) },
                        shape = SegmentedButtonDefaults.itemShape(index, SpeedUnit.entries.size)
                    ) { Text(if (unit == SpeedUnit.KMH) "km/h" else "mph") }
                }
            }
            SettingLabel("Temperatura MOSFET")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TempUnit.entries.forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = settings.tempUnit == unit,
                        onClick = { viewModel.setTempUnit(unit) },
                        shape = SegmentedButtonDefaults.itemShape(index, TempUnit.entries.size)
                    ) { Text(if (unit == TempUnit.CELSIUS) "°C" else "°F") }
                }
            }
        }

        SettingsSection(
            title = "Veicolo e batteria",
            subtitle = "Necessari per velocità, distanza e percentuale",
            icon = Icons.Filled.BatteryChargingFull
        ) {
            SliderSetting("Coppie polari", "${settings.polePairs}", settings.polePairs.toFloat(), 1f, 30f, 1f) {
                viewModel.setPolePairs(it.toInt())
            }
            SliderSetting("Diametro ruota", "%.1f cm".format(settings.wheelDiameterCm), settings.wheelDiameterCm, 10f, 120f, 1f) {
                viewModel.setWheelDiameterCm(it)
            }
            SliderSetting("Rapporto di riduzione", "%.2f".format(settings.gearRatio), settings.gearRatio, 1f, 10f, 1f) {
                viewModel.setGearRatio(it)
            }
            SliderSetting("Celle in serie", "${settings.batteryCells}S", settings.batteryCells.toFloat(), 6f, 20f, 1f) {
                viewModel.setBatteryCells(it.toInt())
            }
        }

        SettingsSection(
            title = "Sicurezza",
            subtitle = "Protezione anti-allontanamento Bluetooth",
            icon = Icons.Filled.NotificationsActive
        ) {
            SliderSetting("Soglia RSSI", "${settings.alarmThresholdDbm} dBm", settings.alarmThresholdDbm.toFloat(), -90f, -60f, 1f) {
                viewModel.setAlarmThresholdDbm(it.toInt())
            }
            SliderSetting("Tempo di conferma", "${settings.alarmDebounceSeconds} secondi", settings.alarmDebounceSeconds.toFloat(), 1f, 30f, 1f) {
                viewModel.setAlarmDebounceSeconds(it.toInt())
            }
            SwitchSetting(
                "Arresto automatico",
                "Ferma la sirena quando il segnale torna normale",
                settings.alarmAutoStopOnReturn,
                viewModel::setAlarmAutoStop
            )
            SwitchSetting(
                "Allarme alla disconnessione",
                "Considera la perdita improvvisa del link come segnale perso",
                settings.alarmOnDisconnect,
                viewModel::setAlarmOnDisconnect
            )
        }

        SettingsSection(
            title = "Affidabilità in background",
            subtitle = "Mantieni attivo il monitoraggio a schermo spento",
            icon = Icons.Filled.Security
        ) {
            Text(
                "Android può sospendere le app in background. Per mantenere attivo il monitoraggio RSSI, " +
                    "escludi VescViewer dall'ottimizzazione batteria.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    } else Intent(Settings.ACTION_SETTINGS)
                    runCatching { context.startActivity(intent) }
                },
                modifier = Modifier.padding(top = 4.dp)
            ) { Text("Apri impostazioni batteria") }
        }

        Text(
            "VescViewer · telemetria in sola lettura via BLE. Nessun comando di controllo viene inviato al motore.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            content()
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 3.dp))
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).background(color)
                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                .clickable(onClick = onClick)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun IconChoice(label: String, color: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(if (selected) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick).padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
        Text(if (selected) "Attiva" else "Scegli", style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SliderSetting(
    label: String,
    valueText: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    onChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = onChange,
            valueRange = min..max,
            steps = ((max - min) / step).toInt() - 1
        )
    }
}

@Composable
private fun SwitchSetting(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
