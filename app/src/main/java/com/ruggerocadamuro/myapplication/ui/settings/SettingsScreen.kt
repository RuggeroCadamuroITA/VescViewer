package com.ruggerocadamuro.myapplication.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ruggerocadamuro.myapplication.R
import com.ruggerocadamuro.myapplication.data.settings.GaugeStyle
import com.ruggerocadamuro.myapplication.data.settings.SpeedUnit
import com.ruggerocadamuro.myapplication.data.settings.TempUnit
import com.ruggerocadamuro.myapplication.data.settings.ThemeMode
import com.ruggerocadamuro.myapplication.ui.components.AccentColorChooser
import com.ruggerocadamuro.myapplication.ui.components.GlassCard
import com.ruggerocadamuro.myapplication.ui.components.LanguageChooser
import com.ruggerocadamuro.myapplication.ui.components.NumericSetting
import com.ruggerocadamuro.myapplication.ui.components.SliderSetting
import com.ruggerocadamuro.myapplication.ui.components.rememberLanguageApplier

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val applyLanguage = rememberLanguageApplier { viewModel.setLanguage(it) }

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
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.settings_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SettingsSection(
            title = stringResource(R.string.section_language),
            subtitle = stringResource(R.string.section_language_subtitle),
            icon = Icons.Filled.Translate
        ) {
            SettingLabel(stringResource(R.string.setting_language))
            Text(
                stringResource(R.string.setting_language_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LanguageChooser(
                selected = settings.language,
                onSelect = applyLanguage,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        SettingsSection(
            title = stringResource(R.string.section_appearance),
            subtitle = stringResource(R.string.section_appearance_subtitle),
            icon = Icons.Filled.Brightness6
        ) {
            SettingLabel(stringResource(R.string.setting_accent))
            Text(
                stringResource(R.string.setting_accent_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AccentColorChooser(
                selectedIndex = settings.accentColorIndex,
                onSelect = viewModel::setAccentColor,
                modifier = Modifier.padding(top = 6.dp)
            )
            SettingLabel(stringResource(R.string.setting_theme))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)
                    ) {
                        Text(
                            stringResource(
                                when (mode) {
                                    ThemeMode.LIGHT -> R.string.theme_light
                                    ThemeMode.DARK -> R.string.theme_dark
                                    ThemeMode.SYSTEM -> R.string.theme_system
                                }
                            )
                        )
                    }
                }
            }
            SettingLabel(stringResource(R.string.setting_gauge))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                GaugeStyle.entries.forEachIndexed { index, style ->
                    SegmentedButton(
                        selected = settings.gaugeStyle == style,
                        onClick = { viewModel.setGaugeStyle(style) },
                        shape = SegmentedButtonDefaults.itemShape(index, GaugeStyle.entries.size)
                    ) {
                        Text(
                            stringResource(
                                if (style == GaugeStyle.ANALOG) R.string.gauge_analog
                                else R.string.gauge_digital
                            )
                        )
                    }
                }
            }
            // L'icona non e' piu' selezionabile: la dicitura chiarisce il perche'.
            Text(
                stringResource(R.string.setting_icon_fixed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        SettingsSection(
            title = stringResource(R.string.section_units),
            subtitle = stringResource(R.string.section_units_subtitle),
            icon = Icons.Filled.Speed
        ) {
            SettingLabel(stringResource(R.string.setting_speed_unit))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SpeedUnit.entries.forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = settings.speedUnit == unit,
                        onClick = { viewModel.setSpeedUnit(unit) },
                        shape = SegmentedButtonDefaults.itemShape(index, SpeedUnit.entries.size)
                    ) {
                        Text(stringResource(if (unit == SpeedUnit.KMH) R.string.unit_kmh else R.string.unit_mph))
                    }
                }
            }
            SettingLabel(stringResource(R.string.setting_temp_unit))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TempUnit.entries.forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = settings.tempUnit == unit,
                        onClick = { viewModel.setTempUnit(unit) },
                        shape = SegmentedButtonDefaults.itemShape(index, TempUnit.entries.size)
                    ) {
                        Text(
                            stringResource(
                                if (unit == TempUnit.CELSIUS) R.string.unit_celsius
                                else R.string.unit_fahrenheit
                            )
                        )
                    }
                }
            }
        }

        SettingsSection(
            title = stringResource(R.string.section_vehicle),
            subtitle = stringResource(R.string.section_vehicle_subtitle),
            icon = Icons.Filled.BatteryChargingFull
        ) {
            NumericSetting(
                label = stringResource(R.string.setting_pole_pairs),
                valueText = "${settings.polePairs}",
                value = settings.polePairs.toFloat(),
                min = 1f,
                max = 30f,
                integer = true,
                onChange = { viewModel.setPolePairs(it.toInt()) }
            )
            NumericSetting(
                label = stringResource(R.string.setting_wheel_diameter),
                valueText = "%.1f".format(settings.wheelDiameterCm),
                value = settings.wheelDiameterCm,
                min = 5f,
                max = 200f,
                integer = false,
                onChange = viewModel::setWheelDiameterCm
            )
            NumericSetting(
                label = stringResource(R.string.setting_gear_ratio),
                valueText = "%.2f".format(settings.gearRatio),
                value = settings.gearRatio,
                min = 0.1f,
                max = 50f,
                integer = false,
                onChange = viewModel::setGearRatio
            )
            NumericSetting(
                label = stringResource(R.string.setting_battery_cells),
                valueText = "${settings.batteryCells}",
                value = settings.batteryCells.toFloat(),
                min = 2f,
                max = 20f,
                integer = true,
                onChange = { viewModel.setBatteryCells(it.toInt()) }
            )
        }

        SettingsSection(
            title = stringResource(R.string.section_security),
            subtitle = stringResource(R.string.section_security_subtitle),
            icon = Icons.Filled.NotificationsActive
        ) {
            // L'interruttore dell'allarme vive solo qui: la dashboard resta una
            // plancia di sola lettura e non mostra comandi.
            SwitchSetting(
                stringResource(R.string.alarm_switch),
                stringResource(
                    if (settings.alarmEnabled) R.string.alarm_switch_on else R.string.alarm_switch_off
                ),
                settings.alarmEnabled,
                viewModel::setAlarmEnabled
            )
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            SliderSetting(
                label = stringResource(R.string.setting_rssi_threshold),
                valueText = "${settings.alarmThresholdDbm} dBm",
                value = settings.alarmThresholdDbm.toFloat(),
                min = -90f,
                max = -60f,
                step = 1f
            ) { viewModel.setAlarmThresholdDbm(it.toInt()) }
            SliderSetting(
                label = stringResource(R.string.setting_debounce),
                valueText = stringResource(R.string.seconds_value, settings.alarmDebounceSeconds),
                value = settings.alarmDebounceSeconds.toFloat(),
                min = 1f,
                max = 30f,
                step = 1f
            ) { viewModel.setAlarmDebounceSeconds(it.toInt()) }
            SwitchSetting(
                stringResource(R.string.alarm_autostop),
                stringResource(R.string.alarm_autostop_desc),
                settings.alarmAutoStopOnReturn,
                viewModel::setAlarmAutoStop
            )
            SwitchSetting(
                stringResource(R.string.alarm_on_disconnect),
                stringResource(R.string.alarm_on_disconnect_desc),
                settings.alarmOnDisconnect,
                viewModel::setAlarmOnDisconnect
            )
        }

        SettingsSection(
            title = stringResource(R.string.section_background),
            subtitle = stringResource(R.string.section_background_subtitle),
            icon = Icons.Filled.Security
        ) {
            Text(
                stringResource(R.string.background_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    } else {
                        Intent(Settings.ACTION_SETTINGS)
                    }
                    runCatching { context.startActivity(intent) }
                },
                modifier = Modifier.padding(top = 4.dp)
            ) { Text(stringResource(R.string.open_battery_settings)) }
        }

        Text(
            stringResource(R.string.settings_footer),
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
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = MaterialTheme.colorScheme.primary
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            content()
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 3.dp)
    )
}

@Composable
private fun SwitchSetting(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
