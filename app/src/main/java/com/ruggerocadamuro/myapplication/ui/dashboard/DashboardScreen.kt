package com.ruggerocadamuro.myapplication.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ruggerocadamuro.myapplication.data.VescMath
import com.ruggerocadamuro.myapplication.data.VescRepository
import com.ruggerocadamuro.myapplication.data.ble.BleManager
import com.ruggerocadamuro.myapplication.data.settings.AppSettings
import com.ruggerocadamuro.myapplication.data.settings.SpeedUnit
import com.ruggerocadamuro.myapplication.data.settings.TempUnit
import com.ruggerocadamuro.myapplication.data.vesc.VescTelemetry
import com.ruggerocadamuro.myapplication.ui.components.ConnectionStateChip
import com.ruggerocadamuro.myapplication.ui.components.GaugeCard
import com.ruggerocadamuro.myapplication.ui.components.HistoryChart
import com.ruggerocadamuro.myapplication.ui.components.RssiIndicator
import com.ruggerocadamuro.myapplication.ui.components.StatCard
import com.ruggerocadamuro.myapplication.ui.components.tempColor
import com.ruggerocadamuro.myapplication.ui.theme.TempThresholds
import kotlin.math.abs

/**
 * Dashboard di telemetria. Layout responsive: in portrait le card sono
 * impilate e scrollabili, in landscape (larghezza >= 600dp) si passa a due
 * colonne affiancate per sfruttare lo spazio.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onGoToScan: () -> Unit
) {
    val state by viewModel.connectionState.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val history by viewModel.history.collectAsState()
    val rssi by viewModel.rssi.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val alarm by viewModel.alarmState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // messaggi evento (es. riconnessione forzata)
    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    // auto-resume dell'allarme: se era attivo prima di un riavvio del processo,
    // lo riattiva (con foreground service) appena la dashboard torna in primo piano
    val alarmResumed = remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(settings.alarmEnabled, alarm.status) {
        if (!alarmResumed.value && settings.alarmEnabled &&
            alarm.status == VescRepository.AlarmStatus.OFF
        ) {
            alarmResumed.value = true
            viewModel.setAlarmEnabled(true)
        }
    }

    val isWide = LocalConfiguration.current.screenWidthDp >= 600

    androidx.compose.material3.Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            DashboardHeader(
                state = state,
                deviceName = deviceName,
                rssi = rssi,
                hasLastDevice = settings.lastDeviceAddress.isNotEmpty(),
                onConnect = { viewModel.connect(settings.lastDeviceAddress, settings.lastDeviceName) },
                onDisconnect = { viewModel.disconnect() },
                onScan = onGoToScan
            )

            if (state == BleManager.ConnectionState.DISCONNECTED &&
                settings.lastDeviceAddress.isEmpty() && telemetry == null
            ) {
                EmptyState(onGoToScan)
            } else if (isWide) {
                // ---- layout landscape / tablet: due colonne ----
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpeedCard(telemetry, settings)
                        AlarmCard(alarm, settings, onToggle = { viewModel.setAlarmEnabled(it) })
                        HistoryChart(history, MaterialTheme.colorScheme.primary)
                    }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GaugesGrid(telemetry, settings, twoColumns = true)
                        ConsumptionRow(telemetry, settings)
                    }
                }
            } else {
                // ---- layout portrait: card impilate ----
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { SpeedCard(telemetry, settings) }
                    item {
                        GaugesGrid(telemetry, settings, twoColumns = false)
                    }
                    item { ConsumptionRow(telemetry, settings) }
                    item {
                        AlarmCard(alarm, settings, onToggle = { viewModel.setAlarmEnabled(it) })
                    }
                    item { HistoryChart(history, MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    state: BleManager.ConnectionState,
    deviceName: String?,
    rssi: Int?,
    hasLastDevice: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onScan: () -> Unit
) {
    val (label, color) = when (state) {
        BleManager.ConnectionState.CONNECTED -> "Connesso" to Color(0xFF43A047)
        BleManager.ConnectionState.CONNECTING -> "Connessione..." to Color(0xFFF9A825)
        BleManager.ConnectionState.RECONNECTING -> "Riconnessione in corso..." to Color(0xFFF9A825)
        BleManager.ConnectionState.DISCONNECTED -> "Disconnesso" to Color(0xFFE53935)
    }
    Card(Modifier.fillMaxWidth().padding(8.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    deviceName ?: "Nessun dispositivo",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ConnectionStateChip(label, color)
                    Spacer(Modifier.width(10.dp))
                    // RSSI/segnale sempre visibile
                    RssiIndicator(rssi)
                }
            }
            when (state) {
                BleManager.ConnectionState.CONNECTED ->
                    OutlinedButton(onClick = onDisconnect) { Text("Disconnetti") }
                else -> Row {
                    if (hasLastDevice) {
                        OutlinedButton(onClick = onConnect) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Text("Riconnetti")
                        }
                    }
                    Button(onClick = onScan, modifier = Modifier.padding(start = 6.dp)) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onGoToScan: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Speed,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(64.dp).width(64.dp)
        )
        Text(
            "Collega il tuo VESC",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "Nessun dispositivo mai collegato. Avvia una scansione BLE per trovare il modulo " +
                "Bluetooth (NUS / Flipsky / HM-10) collegato al tuo Flipsky 75200 Pro.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Button(onClick = onGoToScan) { Text("Scansiona i dispositivi") }
    }
}

/** Card principale: velocita' grande + stima batteria + potenza. */
@Composable
private fun SpeedCard(telemetry: VescTelemetry?, settings: AppSettings) {
    val vehicle = VehicleFor(settings)
    val speedKmh = telemetry?.let { VescMath.speedKmh(it, vehicle) } ?: 0f
    val speed = if (settings.speedUnit == SpeedUnit.MPH) speedKmh * 0.621371f else speedKmh
    val unit = if (settings.speedUnit == SpeedUnit.MPH) "mph" else "km/h"
    val battPct = telemetry?.let { VescMath.batteryPercent(it.voltage, settings.batteryCells) } ?: 0
    val battColor = when {
        battPct <= 15 -> Color(0xFFE53935)
        battPct <= 30 -> Color(0xFFFDD835)
        else -> Color(0xFF43A047)
    }
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "VELOCITÀ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "%.1f".format(speed),
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    unit,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 6.dp, bottom = 11.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Batteria", style = MaterialTheme.typography.labelSmall)
                    Text("$battPct%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = battColor)
                    Text(
                        "${com.ruggerocadamuro.myapplication.ui.components.gaugeValueText(telemetry?.voltage ?: 0f)} V",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Potenza", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "${telemetry?.powerW?.toInt() ?: 0} W",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${com.ruggerocadamuro.myapplication.ui.components.gaugeValueText(telemetry?.currentBattery ?: 0f)} A batteria",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Griglia di gauge: tensione, correnti, duty, temperature. */
@Composable
private fun GaugesGrid(telemetry: VescTelemetry?, settings: AppSettings, twoColumns: Boolean) {
    val t = telemetry
    val style = settings.gaugeStyle
    val tempC = { v: Float -> if (settings.tempUnit == TempUnit.FAHRENHEIT) v * 9f / 5f + 32f else v }
    val tempU = if (settings.tempUnit == TempUnit.FAHRENHEIT) "°F" else "°C"

    if (twoColumns) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GaugeCard("Tensione", t?.voltage ?: 0f, 100f, "V", MaterialTheme.colorScheme.primary, style, Modifier.weight(1f))
            GaugeCard("Duty", abs(t?.dutyCyclePercent ?: 0f), 100f, "%", MaterialTheme.colorScheme.tertiary, style, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GaugeCard("Corrente motore", t?.currentMotor ?: 0f, 150f, "A", MaterialTheme.colorScheme.secondary, style, Modifier.weight(1f))
            GaugeCard("Corrente batteria", t?.currentBattery ?: 0f, 100f, "A", MaterialTheme.colorScheme.secondary, style, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GaugeCard(
                "Temp. MOSFET", tempC(t?.tempMos ?: 0f), if (settings.tempUnit == TempUnit.FAHRENHEIT) 167f else 75f, tempU,
                tempColor(t?.tempMos ?: 0f, TempThresholds.MOSFET_WARN, TempThresholds.MOSFET_DANGER), style, Modifier.weight(1f)
            )
            GaugeCard(
                "Temp. motore", tempC(t?.tempMotor ?: 0f), if (settings.tempUnit == TempUnit.FAHRENHEIT) 185f else 85f, tempU,
                tempColor(t?.tempMotor ?: 0f, TempThresholds.MOTOR_WARN, TempThresholds.MOTOR_DANGER), style, Modifier.weight(1f)
            )
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GaugeCard("Tensione", t?.voltage ?: 0f, 100f, "V", MaterialTheme.colorScheme.primary, style, Modifier.weight(1f))
            GaugeCard("Corrente motore", t?.currentMotor ?: 0f, 150f, "A", MaterialTheme.colorScheme.secondary, style, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GaugeCard("Corrente batteria", t?.currentBattery ?: 0f, 100f, "A", MaterialTheme.colorScheme.secondary, style, Modifier.weight(1f))
            GaugeCard("Duty", abs(t?.dutyCyclePercent ?: 0f), 100f, "%", MaterialTheme.colorScheme.tertiary, style, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GaugeCard(
                "Temp. MOSFET", tempC(t?.tempMos ?: 0f), if (settings.tempUnit == TempUnit.FAHRENHEIT) 167f else 75f, tempU,
                tempColor(t?.tempMos ?: 0f, TempThresholds.MOSFET_WARN, TempThresholds.MOSFET_DANGER), style, Modifier.weight(1f)
            )
            GaugeCard(
                "Temp. motore", tempC(t?.tempMotor ?: 0f), if (settings.tempUnit == TempUnit.FAHRENHEIT) 185f else 85f, tempU,
                tempColor(t?.tempMotor ?: 0f, TempThresholds.MOTOR_WARN, TempThresholds.MOTOR_DANGER), style, Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ConsumptionRow(telemetry: VescTelemetry?, settings: AppSettings) {
    val t = telemetry
    val vehicle = VehicleFor(settings)
    val distance = t?.let { VescMath.distanceKm(it, vehicle) } ?: 0f
    val distStr = if (settings.speedUnit == SpeedUnit.MPH)
        "%.1f mi".format(distance * 0.621371f) else "%.1f km".format(distance)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard("Ah consumati", "%.2f Ah".format(t?.ampHoursConsumed ?: 0f), modifier = Modifier.weight(1f))
        StatCard("Wh consumati", "%.0f Wh".format(t?.wattHoursConsumed ?: 0f), modifier = Modifier.weight(1f))
        StatCard("Distanza", distStr, modifier = Modifier.weight(1f))
    }
}

/**
 * Card allarme anti-allontanamento: toggle ben visibile, stato corrente,
 * margine RSSI rispetto alla soglia e conto alla rovescia del debounce.
 */
@Composable
private fun AlarmCard(
    alarm: VescRepository.AlarmUiState,
    settings: AppSettings,
    onToggle: (Boolean) -> Unit
) {
    val enabled = alarm.status != VescRepository.AlarmStatus.OFF
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (alarm.status) {
                VescRepository.AlarmStatus.ALARM -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = if (alarm.status == VescRepository.AlarmStatus.ALARM) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Allarme anti-allontanamento", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (alarm.status) {
                            VescRepository.AlarmStatus.OFF -> "Disattivato"
                            VescRepository.AlarmStatus.MONITORING ->
                                "Monitoraggio attivo (soglia ${alarm.thresholdDbm} dBm)"
                            VescRepository.AlarmStatus.ALARM -> "ALLARME ATTIVO!"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (alarm.status == VescRepository.AlarmStatus.ALARM) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            if (enabled) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                RssiIndicator(alarm.rssi)
                Text(
                    when {
                        alarm.belowSeconds > 0 ->
                            "Segnale sotto soglia da ${alarm.belowSeconds}s su ${alarm.debounceSeconds}s"
                        else -> "Margine segnale: ${alarm.marginDb} dB sulla soglia"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Text(
                    "Attiva l'allarme: se ti allontani con la bici e il segnale BLE scende sotto " +
                        "${settings.alarmThresholdDbm} dBm per ${settings.alarmDebounceSeconds}s, " +
                        "il telefono emette una sirena a volume massimo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun VehicleFor(settings: AppSettings) = com.ruggerocadamuro.myapplication.data.VehicleParams(
    polePairs = settings.polePairs,
    wheelDiameterCm = settings.wheelDiameterCm,
    gearRatio = settings.gearRatio,
    batteryCells = settings.batteryCells
)
