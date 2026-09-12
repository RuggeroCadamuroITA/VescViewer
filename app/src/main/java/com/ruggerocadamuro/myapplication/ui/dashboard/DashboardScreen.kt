package com.ruggerocadamuro.myapplication.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ruggerocadamuro.myapplication.R
import com.ruggerocadamuro.myapplication.data.VescMath
import com.ruggerocadamuro.myapplication.data.VescRepository
import com.ruggerocadamuro.myapplication.data.ble.BleManager
import com.ruggerocadamuro.myapplication.data.settings.AppSettings
import com.ruggerocadamuro.myapplication.data.settings.SpeedUnit
import com.ruggerocadamuro.myapplication.data.settings.TempUnit
import com.ruggerocadamuro.myapplication.data.vesc.VescTelemetry
import com.ruggerocadamuro.myapplication.ui.components.BleBadge
import com.ruggerocadamuro.myapplication.ui.components.ConnectionStateChip
import com.ruggerocadamuro.myapplication.ui.components.GlassCard
import com.ruggerocadamuro.myapplication.ui.components.HistoryChart
import com.ruggerocadamuro.myapplication.ui.components.RssiIndicator
import com.ruggerocadamuro.myapplication.ui.components.StatCard
import com.ruggerocadamuro.myapplication.ui.components.tempColor
import com.ruggerocadamuro.myapplication.ui.theme.LightTemperatureCritical
import com.ruggerocadamuro.myapplication.ui.theme.LightTemperatureOk
import com.ruggerocadamuro.myapplication.ui.theme.LightTemperatureWarning
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

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val isWide = configuration.screenWidthDp >= 600 && !isLandscape

    // ---------------------------------------------------------------
    // Telefono ruotato: la dashboard non e' una lista che scorre ma una
    // plancia unica (vedi [LandscapeDashboard]), tutta visibile nello schermo.
    // ---------------------------------------------------------------
    if (isLandscape) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // Gli inset di sistema sono gia' gestiti dallo Scaffold in MainActivity:
            // riapplicarli qui rubava 48 dp di altezza in orizzontale.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                LandscapeDashboard(
                    state = state,
                    telemetry = telemetry,
                    history = history,
                    rssi = rssi,
                    deviceName = deviceName,
                    settings = settings,
                    onConnect = {
                        viewModel.connect(settings.lastDeviceAddress, settings.lastDeviceName)
                    },
                    onDisconnect = viewModel::disconnect,
                    onScan = onGoToScan
                )
            }
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Vedi sopra: gli inset arrivano gia' dallo Scaffold esterno.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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

                item {
                    SectionHeading(
                        title = stringResource(R.string.live_metrics_title),
                        subtitle = stringResource(R.string.live_metrics_subtitle)
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
    val connectedColor = Color(0xFF35B77A)
    val warningColor = Color(0xFFE3A63C)
    val (label, color) = when (state) {
        BleManager.ConnectionState.CONNECTED ->
            stringResource(R.string.state_connected) to connectedColor
        BleManager.ConnectionState.CONNECTING ->
            stringResource(R.string.state_connecting) to warningColor
        BleManager.ConnectionState.RECONNECTING ->
            stringResource(R.string.state_reconnecting) to warningColor
        BleManager.ConnectionState.DISCONNECTED ->
            stringResource(R.string.state_disconnected) to MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Badge BLE (runa + "LE"): resta sempre leggibile, lo stato del link lo
        // racconta gia' il chip "Connesso/Disconnesso" qui sotto.
        BleBadge()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.brand_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
            Text(
                text = deviceName ?: stringResource(R.string.no_device_selected),
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
                Text(stringResource(R.string.action_disconnect))
            }
            else -> {
                if (hasLastDevice) {
                    OutlinedButton(onClick = onConnect) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.action_reconnect))
                    }
                } else {
                    Button(onClick = onScan) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.action_search))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onGoToScan: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        glowColor = MaterialTheme.colorScheme.primary
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
                // Il glifo deve usare l'"on" del contenitore: un accento chiaro
                // su primaryContainer chiaro spariva del tutto.
                Icon(
                    Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                stringResource(R.string.empty_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 18.dp)
            )
            Text(
                stringResource(R.string.empty_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(onClick = onGoToScan, modifier = Modifier.padding(top = 20.dp)) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.empty_action))
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
    val speedUnit = stringResource(
        if (settings.speedUnit == SpeedUnit.MPH) R.string.unit_mph else R.string.unit_kmh
    )
    val voltage = telemetry?.voltage
    val batteryPct = telemetry?.let { VescMath.batteryPercent(it.voltage, settings.batteryCells) }
    val batteryColor = batteryColor(batteryPct)

    // La card era costruita su `primaryContainer`: con un accento chiaro il
    // contenitore diventava chiaro mentre testi e unita' restavano tinte da
    // "onSurfaceVariant" (chiaro su chiaro) e la card sembrava sbiadita.
    // Ora usa surface + accento, quindi ogni testo ha sempre il suo contrasto.
    GlassCard(
        modifier = modifier,
        glowColor = MaterialTheme.colorScheme.primary
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        Color.Transparent
                    )
                )
            )
        ) {
            Column(Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.live_ride),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.3.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    ) {
                        Text(
                            stringResource(
                                if (telemetry == null) R.string.live_waiting else R.string.live_data
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
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
                        color = MaterialTheme.colorScheme.onSurface
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
                        label = stringResource(R.string.label_battery),
                        value = batteryPct?.let { "$it%" } ?: "—",
                        detail = voltage?.let { "%.1f V".format(it) }
                            ?: stringResource(R.string.no_data),
                        color = batteryColor,
                        modifier = Modifier.weight(1f)
                    )
                    HeroStat(
                        label = stringResource(R.string.label_power),
                        value = telemetry?.powerW?.let { "${it.toInt()} W" } ?: "—",
                        detail = telemetry?.currentBattery?.let {
                            stringResource(R.string.amps_battery, it)
                        } ?: stringResource(R.string.no_data),
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
    GlassCard(
        modifier = modifier,
        glowColor = MaterialTheme.colorScheme.primary
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeading(
                stringResource(R.string.session_summary_title),
                stringResource(R.string.session_summary_subtitle)
            )
            SummaryLine(
                stringResource(R.string.label_pack_voltage),
                telemetry?.voltage?.let { "%.1f V".format(it) } ?: "—"
            )
            SummaryLine(
                stringResource(R.string.label_battery_current),
                telemetry?.currentBattery?.let { "%.1f A".format(it) } ?: "—"
            )
            SummaryLine(
                stringResource(R.string.label_erpm),
                telemetry?.erpm?.let { "%.0f".format(it) } ?: "—"
            )
            SummaryLine(
                stringResource(R.string.label_duty_cycle),
                telemetry?.dutyCyclePercent?.let { "%.1f%%".format(it) } ?: "—"
            )
            Text(
                stringResource(R.string.battery_config, settings.batteryCells),
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
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
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
                stringResource(R.string.label_voltage),
                telemetry?.voltage?.let { "%.1f".format(it) } ?: "—",
                "V",
                MaterialTheme.colorScheme.primary,
                Modifier.weight(1f)
            )
            MetricCard(
                stringResource(R.string.label_power),
                telemetry?.powerW?.let { "%.0f".format(it) } ?: "—",
                "W",
                MaterialTheme.colorScheme.tertiary,
                Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                stringResource(R.string.label_motor_current),
                telemetry?.currentMotor?.let { "%.1f".format(it) } ?: "—",
                "A",
                MaterialTheme.colorScheme.secondary,
                Modifier.weight(1f)
            )
            MetricCard(
                stringResource(R.string.label_duty_cycle),
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
    GlassCard(
        modifier = modifier,
        glowColor = accent
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
    val status = stringResource(
        when {
            tempC == null -> R.string.temp_waiting
            tempC >= TempThresholds.MOSFET_DANGER -> R.string.temp_danger
            tempC >= TempThresholds.MOSFET_WARN -> R.string.temp_warn
            else -> R.string.temp_normal
        }
    )

    GlassCard(
        modifier = modifier,
        glowColor = color
    ) {
        Column(Modifier.padding(18.dp)) {
            SectionHeading(
                stringResource(R.string.mosfet_title),
                stringResource(R.string.mosfet_subtitle)
            )
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
        if (settings.speedUnit == SpeedUnit.MPH) stringResource(R.string.unit_mi, it * 0.621371f)
        else stringResource(R.string.unit_km, it)
    } ?: "—"

    GlassCard(
        modifier = modifier,
        glowColor = MaterialTheme.colorScheme.primary
    ) {
        Column(Modifier.padding(18.dp)) {
            SectionHeading(
                stringResource(R.string.consumption_title),
                stringResource(R.string.consumption_subtitle)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConsumptionValue(
                    stringResource(R.string.label_ah),
                    telemetry?.ampHoursConsumed?.let { "%.2f".format(it) } ?: "—",
                    Modifier.weight(1f)
                )
                ConsumptionValue(
                    stringResource(R.string.label_wh),
                    telemetry?.wattHoursConsumed?.let { "%.0f".format(it) } ?: "—",
                    Modifier.weight(1f)
                )
                ConsumptionValue(stringResource(R.string.label_distance), distance, Modifier.weight(1f))
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

private fun batteryColor(percent: Int?): Color = when {
    percent == null -> Color(0xFF7C8794)
    percent <= 15 -> LightTemperatureCritical
    percent <= 30 -> LightTemperatureWarning
    else -> LightTemperatureOk
}

private fun VehicleFor(settings: AppSettings) = com.ruggerocadamuro.myapplication.data.VehicleParams(
    polePairs = settings.polePairs,
    wheelDiameterCm = settings.wheelDiameterCm,
    gearRatio = settings.gearRatio,
    batteryCells = settings.batteryCells
)
