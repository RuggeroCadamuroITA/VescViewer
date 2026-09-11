package com.ruggerocadamuro.myapplication.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.ruggerocadamuro.myapplication.ui.components.HistoryChart
import com.ruggerocadamuro.myapplication.ui.components.RssiIndicator
import com.ruggerocadamuro.myapplication.ui.components.StatCard
import com.ruggerocadamuro.myapplication.ui.components.tempColor
import com.ruggerocadamuro.myapplication.ui.theme.TempThresholds
import kotlin.math.abs

/**
 * Dashboard principale: gerarchia visiva pensata per la lettura rapida durante una sosta.
 * La velocita' e lo stato della batteria sono sempre il primo livello; il resto e' organizzato
 * in card compatte e uniformi. In landscape il blocco principale usa due colonne.
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
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    val alarmResumed = remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(settings.alarmEnabled, alarm.status) {
        if (!alarmResumed.value && settings.alarmEnabled &&
            alarm.status == VescRepository.AlarmStatus.OFF
        ) {
            alarmResumed.value = true
            viewModel.setAlarmEnabled(true)
        }
    }

    val isWide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                DashboardTopBar(
                    state = state,
                    deviceName = deviceName,
                    rssi = rssi,
                    hasLastDevice = settings.lastDeviceAddress.isNotEmpty(),
                    onConnect = { viewModel.connect(settings.lastDeviceAddress, settings.lastDeviceName) },
                    onDisconnect = viewModel::disconnect,
                    onScan = onGoToScan
                )
            }

            if (state == BleManager.ConnectionState.DISCONNECTED &&
                settings.lastDeviceAddress.isEmpty() && telemetry == null
            ) {
                item { EmptyState(onGoToScan) }
            } else {
                item {
                    if (isWide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            SpeedHero(telemetry, settings, Modifier.weight(1.35f))
                            SessionSummary(telemetry, settings, Modifier.weight(0.65f))
                        }
                    } else {
                        SpeedHero(telemetry, settings)
                    }
                }

                if (!isWide) {
                    item {
                        AlarmCard(alarm, settings) { viewModel.setAlarmEnabled(it) }
                    }
                }

                item {
                    SectionHeading(
                        title = "Dati in tempo reale",
                        subtitle = "Aggiornamento automatico ogni 250 ms"
                    )
                }
                item { LiveMetricsGrid(telemetry) }

                item {
                    if (isWide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            MosfetCard(telemetry, settings, Modifier.weight(1f))
                            ConsumptionCard(telemetry, settings, Modifier.weight(1f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            MosfetCard(telemetry, settings)
                            ConsumptionCard(telemetry, settings)
                        }
                    }
                }

                if (isWide) {
                    item { AlarmCard(alarm, settings) { viewModel.setAlarmEnabled(it) } }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        HistoryChart(
                            points = history,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardTopBar(
    state: BleManager.ConnectionState,
    deviceName: String?,
    rssi: Int?,
    hasLastDevice: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onScan: () -> Unit
) {
    val (label, color) = when (state) {
        BleManager.ConnectionState.CONNECTED -> "Connesso" to Color(0xFF35B77A)
        BleManager.ConnectionState.CONNECTING -> "Connessione" to Color(0xFFE3A63C)
        BleManager.ConnectionState.RECONNECTING -> "Riconnessione" to Color(0xFFE3A63C)
        BleManager.ConnectionState.DISCONNECTED -> "Disconnesso" to MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (state == BleManager.ConnectionState.CONNECTED) Icons.Filled.Bluetooth
                else Icons.Filled.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "VESCVIEWER",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
            Text(
                text = deviceName ?: "Nessun dispositivo selezionato",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConnectionStateChip(label, color)
                Spacer(Modifier.width(8.dp))
                RssiIndicator(rssi, showValue = false)
            }
        }
        when (state) {
            BleManager.ConnectionState.CONNECTED -> OutlinedButton(onClick = onDisconnect) {
                Text("Disconnetti")
            }
            else -> {
                if (hasLastDevice) {
                    OutlinedButton(onClick = onConnect) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text("Riconnetti")
                    }
                } else {
                    Button(onClick = onScan) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text("Cerca")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onGoToScan: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                "Pronto per partire?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 18.dp)
            )
            Text(
                "Collega il modulo Bluetooth del tuo VESC per vedere i dati in tempo reale.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(onClick = onGoToScan, modifier = Modifier.padding(top = 20.dp)) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scansiona dispositivi")
            }
        }
    }
}

@Composable
private fun SpeedHero(
    telemetry: VescTelemetry?,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val vehicle = VehicleFor(settings)
    val speedKmh = telemetry?.let { VescMath.speedKmh(it, vehicle) }
    val speed = speedKmh?.let { if (settings.speedUnit == SpeedUnit.MPH) it * 0.621371f else it }
    val speedUnit = if (settings.speedUnit == SpeedUnit.MPH) "mph" else "km/h"
    val voltage = telemetry?.voltage
    val batteryPct = telemetry?.let { VescMath.batteryPercent(it.voltage, settings.batteryCells) }
    val batteryColor = batteryColor(batteryPct)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                    )
                )
            )
        ) {
            Column(Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "LIVE RIDE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.3.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ) {
                        Text(
                            if (telemetry == null) "IN ATTESA" else "DATI LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        speed?.let { "%.1f".format(it) } ?: "—",
                        fontSize = 68.sp,
                        lineHeight = 72.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        speedUnit,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, bottom = 10.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HeroStat(
                        label = "BATTERIA",
                        value = batteryPct?.let { "$it%" } ?: "—",
                        detail = voltage?.let { "%.1f V".format(it) } ?: "Nessun dato",
                        color = batteryColor,
                        modifier = Modifier.weight(1f)
                    )
                    HeroStat(
                        label = "POTENZA",
                        value = telemetry?.powerW?.let { "${it.toInt()} W" } ?: "—",
                        detail = telemetry?.currentBattery?.let { "%.1f A batteria".format(it) } ?: "Nessun dato",
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionSummary(
    telemetry: VescTelemetry?,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeading("Riepilogo sessione", "Valori principali")
            SummaryLine("Tensione pack", telemetry?.voltage?.let { "%.1f V".format(it) } ?: "—")
            SummaryLine("Corrente batteria", telemetry?.currentBattery?.let { "%.1f A".format(it) } ?: "—")
            SummaryLine("ERPM", telemetry?.erpm?.let { "%.0f".format(it) } ?: "—")
            SummaryLine("Duty cycle", telemetry?.dutyCyclePercent?.let { "%.1f%%".format(it) } ?: "—")
            Text(
                "Configurazione ${settings.batteryCells}S",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeroStat(
    label: String,
    value: String,
    detail: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))
            .padding(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LiveMetricsGrid(telemetry: VescTelemetry?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                "Tensione",
                telemetry?.voltage?.let { "%.1f".format(it) } ?: "—",
                "V",
                MaterialTheme.colorScheme.primary,
                Modifier.weight(1f)
            )
            MetricCard(
                "Potenza",
                telemetry?.powerW?.let { "%.0f".format(it) } ?: "—",
                "W",
                MaterialTheme.colorScheme.tertiary,
                Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                "Corrente motore",
                telemetry?.currentMotor?.let { "%.1f".format(it) } ?: "—",
                "A",
                MaterialTheme.colorScheme.secondary,
                Modifier.weight(1f)
            )
            MetricCard(
                "Duty cycle",
                telemetry?.dutyCyclePercent?.let { "%.1f".format(it) } ?: "—",
                "%",
                MaterialTheme.colorScheme.primary,
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier.size(8.dp).clip(CircleShape).background(accent)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = accent)
                if (unit.isNotEmpty()) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 5.dp, bottom = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MosfetCard(
    telemetry: VescTelemetry?,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val tempC = telemetry?.tempMos
    val displayTemp = tempC?.let {
        if (settings.tempUnit == TempUnit.FAHRENHEIT) it * 9f / 5f + 32f else it
    }
    val tempUnit = if (settings.tempUnit == TempUnit.FAHRENHEIT) "°F" else "°C"
    val color = tempColor(
        tempC ?: 0f,
        TempThresholds.MOSFET_WARN,
        TempThresholds.MOSFET_DANGER
    )
    val status = when {
        tempC == null -> "In attesa dei dati"
        tempC >= TempThresholds.MOSFET_DANGER -> "Temperatura alta"
        tempC >= TempThresholds.MOSFET_WARN -> "Controlla la temperatura"
        else -> "Temperatura nella norma"
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp)) {
            SectionHeading("Temperatura MOSFET", "Protezione controller")
            Row(
                modifier = Modifier.padding(top = 14.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    displayTemp?.let { "%.1f".format(it) } ?: "—",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    tempUnit,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, bottom = 5.dp)
                )
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = color)
            LinearProgressIndicator(
                progress = { ((tempC ?: 0f) / 100f).coerceIn(0f, 1f) },
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(7.dp).clip(CircleShape)
            )
        }
    }
}

@Composable
private fun ConsumptionCard(
    telemetry: VescTelemetry?,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val distanceKm = telemetry?.let { VescMath.distanceKm(it, VehicleFor(settings)) }
    val distance = distanceKm?.let {
        if (settings.speedUnit == SpeedUnit.MPH) "%.1f mi".format(it * 0.621371f)
        else "%.1f km".format(it)
    } ?: "—"

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp)) {
            SectionHeading("Consumi e distanza", "Dati della sessione")
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConsumptionValue("Ah", telemetry?.ampHoursConsumed?.let { "%.2f".format(it) } ?: "—", Modifier.weight(1f))
                ConsumptionValue("Wh", telemetry?.wattHoursConsumed?.let { "%.0f".format(it) } ?: "—", Modifier.weight(1f))
                ConsumptionValue("Distanza", distance, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ConsumptionValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String? = null) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AlarmCard(
    alarm: VescRepository.AlarmUiState,
    settings: AppSettings,
    onToggle: (Boolean) -> Unit
) {
    val enabled = alarm.status != VescRepository.AlarmStatus.OFF
    val isAlarm = alarm.status == VescRepository.AlarmStatus.ALARM
    val container = if (isAlarm) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.surface
    val accent = if (isAlarm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = accent)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Anti-allontanamento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when (alarm.status) {
                            VescRepository.AlarmStatus.OFF -> "Protezione disattivata"
                            VescRepository.AlarmStatus.MONITORING -> "Monitoraggio attivo · ${alarm.thresholdDbm} dBm"
                            VescRepository.AlarmStatus.ALARM -> "ALLARME ATTIVO"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = accent
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            if (enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RssiIndicator(alarm.rssi)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${alarm.marginDb} dB",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
                Text(
                    if (alarm.belowSeconds > 0) {
                        "Segnale debole da ${alarm.belowSeconds}s · conferma a ${alarm.debounceSeconds}s"
                    } else {
                        "Margine rispetto alla soglia configurata"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                Text(
                    "Attiva la protezione per ricevere una sirena se il segnale BLE resta sotto " +
                        "${settings.alarmThresholdDbm} dBm per ${settings.alarmDebounceSeconds}s.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun batteryColor(percent: Int?): Color = when {
    percent == null -> Color(0xFF7C8794)
    percent <= 15 -> Color(0xFFE05252)
    percent <= 30 -> Color(0xFFE3A63C)
    else -> Color(0xFF35B77A)
}

private fun VehicleFor(settings: AppSettings) = com.ruggerocadamuro.myapplication.data.VehicleParams(
    polePairs = settings.polePairs,
    wheelDiameterCm = settings.wheelDiameterCm,
    gearRatio = settings.gearRatio,
    batteryCells = settings.batteryCells
)
