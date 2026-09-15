package com.ruggerocadamuro.myapplication.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.ruggerocadamuro.myapplication.data.HistoryPoint
import com.ruggerocadamuro.myapplication.data.VescMath
import com.ruggerocadamuro.myapplication.data.VehicleParams
import com.ruggerocadamuro.myapplication.data.ble.BleManager
import com.ruggerocadamuro.myapplication.data.settings.AppSettings
import com.ruggerocadamuro.myapplication.data.settings.SpeedUnit
import com.ruggerocadamuro.myapplication.data.settings.TempUnit
import com.ruggerocadamuro.myapplication.data.vesc.VescTelemetry
import com.ruggerocadamuro.myapplication.ui.components.BleBadge
import com.ruggerocadamuro.myapplication.ui.components.BleGlyph
import com.ruggerocadamuro.myapplication.ui.components.ConnectionStateChip
import com.ruggerocadamuro.myapplication.ui.components.GlassCard
import com.ruggerocadamuro.myapplication.ui.components.GlassButton
import com.ruggerocadamuro.myapplication.ui.components.GlassIconButton
import com.ruggerocadamuro.myapplication.ui.components.GlassOutlineButton
import com.ruggerocadamuro.myapplication.ui.components.HistoryChart
import com.ruggerocadamuro.myapplication.ui.components.RssiIndicator
import com.ruggerocadamuro.myapplication.ui.components.tempColor
import com.ruggerocadamuro.myapplication.ui.theme.TempThresholds

/**
 * Dashboard orizzontale "da cruscotto": quando il telefono viene ruotato la
 * schermata non e' piu' una lista scorrevole ma una plancia unica che entra
 * tutta nello schermo, pensata per essere letta con un colpo d'occhio
 * mentre si guida.
 *
 * Struttura: barra di stato compatta in alto, tre pannelli affiancati
 * (velocita' / metriche elettriche / stato termico e consumi) e, solo sugli
 * schermi abbastanza alti, una striscia con lo storico. Ogni pannello usa
 * `weight` e `fillMaxHeight`, quindi la plancia si adatta senza scorrere.
 *
 * Nessun comando dell'allarme: la plancia e' di sola lettura, la protezione
 * anti-allontanamento si accende dalle impostazioni.
 */
@Composable
fun LandscapeDashboard(
    state: BleManager.ConnectionState,
    telemetry: VescTelemetry?,
    history: List<HistoryPoint>,
    rssi: Int?,
    deviceName: String?,
    settings: AppSettings,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onScan: () -> Unit,
    recordingActive: Boolean,
    recordingPaused: Boolean,
    recordingPoints: Int,
    recordingDistanceM: Double,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Lo storico entra solo se, tolti barra e pannelli, resta spazio
        // sufficiente a non comprimere la plancia (telefoni ruotati ~360 dp:
        // niente grafico; tablet o finestre alte: grafico visibile).
        val showHistory = maxHeight >= 440.dp
        val nothingToShow = state == BleManager.ConnectionState.DISCONNECTED &&
            settings.lastDeviceAddress.isEmpty() && telemetry == null

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!nothingToShow) {
                LandscapeTopBar(
                    state = state,
                    deviceName = deviceName,
                    rssi = rssi,
                    hasLastDevice = settings.lastDeviceAddress.isNotEmpty(),
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                    onScan = onScan,
                    recordingActive = recordingActive,
                    recordingPaused = recordingPaused,
                    recordingPoints = recordingPoints,
                    recordingDistanceM = recordingDistanceM,
                    onStartRecording = onStartRecording,
                    onPauseRecording = onPauseRecording,
                    onResumeRecording = onResumeRecording,
                    onStopRecording = onStopRecording
                )
            }

            if (nothingToShow) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LandscapeEmptyState(onScan)
                }
            } else {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpeedHeroPanel(telemetry, settings, Modifier.weight(1.1f).fillMaxHeight())
                    ElectricMetricsPanel(telemetry, Modifier.weight(1.2f).fillMaxHeight())
                    StatusPanel(
                        telemetry = telemetry,
                        settings = settings,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }

                if (showHistory) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = MaterialTheme.colorScheme.primary
                    ) {
                        HistoryChart(
                            points = history,
                            accentColor = MaterialTheme.colorScheme.primary,
                            chartHeight = 56.dp,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LandscapeTopBar(
    state: BleManager.ConnectionState,
    deviceName: String?,
    rssi: Int?,
    hasLastDevice: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onScan: () -> Unit,
    recordingActive: Boolean,
    recordingPaused: Boolean,
    recordingPoints: Int,
    recordingDistanceM: Double,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit
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
        modifier = Modifier.fillMaxWidth().height(54.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BleBadge(size = 36.dp, cornerRadius = 12.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = deviceName ?: stringResource(R.string.no_device),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        ConnectionStateChip(label, color)
        Spacer(Modifier.width(10.dp))
        RssiIndicator(rssi, showValue = true)
        Spacer(Modifier.width(8.dp))
        LandscapeRecordingControl(
            active = recordingActive,
            paused = recordingPaused,
            points = recordingPoints,
            distanceM = recordingDistanceM,
            onStart = onStartRecording,
            onPause = onPauseRecording,
            onResume = onResumeRecording,
            onStop = onStopRecording
        )
        Spacer(Modifier.width(8.dp))
        when (state) {
            BleManager.ConnectionState.CONNECTED -> GlassOutlineButton(
                onClick = onDisconnect,
                compact = true
            ) {
                Text(stringResource(R.string.action_disconnect), fontSize = 13.sp)
            }

            else -> if (hasLastDevice) {
                GlassOutlineButton(
                    onClick = onConnect,
                    compact = true,
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_reconnect), fontSize = 13.sp)
                }
            } else {
                GlassButton(
                    onClick = onScan,
                    compact = true,
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_search), fontSize = 13.sp)
                }
            }
        }
    }
}

/** Plancia compatta mostrata quando non c'e' ancora nessun dispositivo collegato. */
@Composable
private fun LandscapeRecordingControl(
    active: Boolean,
    paused: Boolean,
    points: Int,
    distanceM: Double,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    val accent = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.12f))
            .padding(start = 9.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
        Text(
            if (active) stringResource(R.string.recording_compact_active, points, distanceM)
            else stringResource(R.string.recording_compact_idle),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            maxLines = 1
        )
        if (!active) {
            GlassIconButton(
                onClick = onStart,
                icon = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.recording_start_accessibility),
                tint = accent,
                size = 48.dp
            )
        } else {
            GlassIconButton(
                onClick = if (paused) onResume else onPause,
                icon = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = if (paused) {
                    stringResource(R.string.recording_resume_accessibility)
                } else {
                    stringResource(R.string.recording_pause_accessibility)
                },
                tint = accent,
                size = 48.dp
            )
            GlassIconButton(
                onClick = onStop,
                icon = Icons.Filled.Stop,
                contentDescription = stringResource(R.string.recording_stop_accessibility),
                tint = MaterialTheme.colorScheme.error,
                size = 48.dp
            )
        }
    }
}

@Composable
private fun LandscapeEmptyState(onScan: () -> Unit) {
    GlassCard(
        glowColor = MaterialTheme.colorScheme.primary
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(54.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                // "on" del contenitore: con un accento chiaro su primaryContainer
                // chiaro la runa spariva.
                BleGlyph(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    iconSize = 24.dp,
                    labelSize = 10.sp
                )
            }
            Spacer(Modifier.width(18.dp))
            // weight(1f): senza, la riga di testo si prendeva tutta la larghezza
            // e spingeva il pulsante fuori dallo schermo in orizzontale.
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.empty_text_landscape),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(24.dp))
            GlassButton(onClick = onScan, compact = true) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_scan))
            }
        }
    }
}

/**
 * Pannello principale: velocita' con il numero piu' grande della plancia,
 * piu' i due valori che contano di piu' in movimento (batteria e potenza).
 */
@Composable
private fun SpeedHeroPanel(
    telemetry: VescTelemetry?,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val speedKmh = telemetry?.let { VescMath.speedKmh(it, dashboardVehicle(settings)) }
    val speed = speedKmh?.let { if (settings.speedUnit == SpeedUnit.MPH) it * 0.621371f else it }
    val speedUnit = stringResource(
        if (settings.speedUnit == SpeedUnit.MPH) R.string.unit_mph else R.string.unit_kmh
    )
    val batteryPct = telemetry?.let { VescMath.batteryPercent(it.voltage, settings.batteryCells) }
    val batteryColor = heroBatteryColor(batteryPct)

    // Come nella dashboard in verticale: surface + accento al posto di
    // primaryContainer, per non avere mai testo chiaro su fondo chiaro.
    GlassCard(
        modifier = modifier,
        glowColor = MaterialTheme.colorScheme.primary
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        Color.Transparent
                    )
                )
            )
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.live_ride),
                        style = MaterialTheme.typography.labelSmall,
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
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Il numero si adatta all'altezza reale del pannello: cosi'
                    // resta "hero" anche su schermi bassi, senza mai debordare.
                    val valueSize = (maxHeight.value * 0.62f).coerceIn(28f, 64f)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = speed?.let {
                                stringResource(
                                    if (settings.speedUnit == SpeedUnit.MPH) R.string.unit_speed_mph else R.string.unit_speed_kmh,
                                    it
                                )
                            } ?: "—",
                            fontSize = valueSize.sp,
                            lineHeight = (valueSize * 1.02f).sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = speedUnit,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeroMiniStat(
                        label = stringResource(R.string.label_battery),
                        value = batteryPct?.let { stringResource(R.string.battery_percent, it) } ?: "—",
                        detail = telemetry?.voltage?.let {
                            stringResource(R.string.unit_voltage, it) + " · " + stringResource(R.string.battery_estimate)
                        } ?: stringResource(R.string.no_data),
                        color = batteryColor,
                        modifier = Modifier.weight(1f)
                    )
                    HeroMiniStat(
                        label = stringResource(R.string.label_power),
                        value = telemetry?.powerW?.let { stringResource(R.string.unit_power, it) } ?: "—",
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
private fun HeroMiniStat(
    label: String,
    value: String,
    detail: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            value,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Pannello centrale: griglia 3x2 con le grandezze elettriche del controller.
 * Le celle hanno tutte la stessa altezza garantita da `weight`.
 */
@Composable
private fun ElectricMetricsPanel(
    telemetry: VescTelemetry?,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        glowColor = MaterialTheme.colorScheme.primary
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    stringResource(R.string.label_power),
                    telemetry?.powerW?.let { "%.0f".format(it) } ?: "—",
                    "W",
                    MaterialTheme.colorScheme.tertiary,
                    Modifier.weight(1f).fillMaxHeight()
                )
                MetricTile(
                    stringResource(R.string.label_battery_current),
                    telemetry?.currentBattery?.let { stringResource(R.string.unit_current, it) } ?: "—",
                    "A",
                    MaterialTheme.colorScheme.secondary,
                    Modifier.weight(1f).fillMaxHeight()
                )
                MetricTile(
                    stringResource(R.string.label_voltage),
                    telemetry?.voltage?.let { stringResource(R.string.unit_voltage, it) } ?: "—",
                    "V",
                    MaterialTheme.colorScheme.primary,
                    Modifier.weight(1f).fillMaxHeight()
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    stringResource(R.string.label_motor_current),
                    telemetry?.currentMotor?.let { "%.1f".format(it) } ?: "—",
                    "A",
                    MaterialTheme.colorScheme.secondary,
                    Modifier.weight(1f).fillMaxHeight()
                )
                MetricTile(
                    stringResource(R.string.label_duty_cycle),
                    telemetry?.dutyCyclePercent?.let { stringResource(R.string.unit_percent, it) } ?: "—",
                    "%",
                    MaterialTheme.colorScheme.primary,
                    Modifier.weight(1f).fillMaxHeight()
                )
                MetricTile(
                    stringResource(R.string.label_erpm),
                    telemetry?.erpm?.let { "%.0f".format(it) } ?: "—",
                    "",
                    MaterialTheme.colorScheme.tertiary,
                    Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
        // Due righe e un filo piu' piccole: in orizzontale "Corrente batteria"
        // non sta su una riga sola e veniva tagliata a meta' parola.
        Text(
            label,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val valueSize = (maxHeight.value * 0.55f).coerceIn(16f, 26f)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    fontSize = valueSize.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (unit.isNotEmpty()) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * Pannello destro: temperatura dei MOSFET (l'unica temperatura mostrata
 * dall'app), consumi della sessione e interruttore anti-allontanamento.
 */
@Composable
private fun StatusPanel(
    telemetry: VescTelemetry?,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MosfetTile(telemetry, settings, Modifier.weight(1f).fillMaxWidth())
        ConsumptionTile(telemetry, settings, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun MosfetTile(
    telemetry: VescTelemetry?,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val tempC = telemetry?.tempMos
    val displayTemp = tempC?.let {
        if (settings.tempUnit == TempUnit.FAHRENHEIT) it * 9f / 5f + 32f else it
    }
    val unit = stringResource(
        if (settings.tempUnit == TempUnit.FAHRENHEIT) R.string.unit_fahrenheit else R.string.unit_celsius
    )
    val color = tempColor(tempC ?: 0f, TempThresholds.MOSFET_WARN, TempThresholds.MOSFET_DANGER)
    val status = stringResource(
        when {
            tempC == null -> R.string.temp_waiting
            tempC >= TempThresholds.MOSFET_DANGER -> R.string.temp_danger
            tempC >= TempThresholds.MOSFET_WARN -> R.string.temp_warn
            else -> R.string.temp_normal_short
        }
    )

    Column(
        modifier = modifier.clip(RoundedCornerShape(20.dp))
            // `surface` coinciderebbe col fondo della plancia e la tile
            // scomparirebbe: un filo di surfaceVariant la stacca.
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.mosfet_title_short),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val valueSize = (maxHeight.value * 0.42f).coerceIn(20f, 34f)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    displayTemp?.let {
                        stringResource(
                            if (settings.tempUnit == TempUnit.FAHRENHEIT) R.string.unit_temperature_f else R.string.unit_temperature_c,
                            it
                        )
                    } ?: "—",
                    fontSize = valueSize.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1
                )
                Text(
                    unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 5.dp, bottom = 3.dp)
                )
            }
        }
        Text(status, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
        LinearProgressIndicator(
            progress = { ((tempC ?: 0f) / 100f).coerceIn(0f, 1f) },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(CircleShape)
        )
    }
}

@Composable
private fun ConsumptionTile(
    telemetry: VescTelemetry?,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val distanceKm = telemetry?.let { VescMath.distanceKm(it, dashboardVehicle(settings)) }
    val distance = distanceKm?.let {
        if (settings.speedUnit == SpeedUnit.MPH) stringResource(R.string.unit_mi, it * 0.621371f)
        else stringResource(R.string.unit_km, it)
    } ?: "—"

    Column(
        modifier = modifier.clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.consumption_title_short),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
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

@Composable
private fun ConsumptionValue(label: String, value: String, modifier: Modifier = Modifier) {
    // Tre colonne in un pannello stretto: testi compatti, altrimenti il valore
    // della distanza finiva con i puntini di sospensione ("0,0 …").
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun heroBatteryColor(percent: Int?): Color = when {
    percent == null -> Color(0xFF7C8794)
    percent <= 15 -> Color(0xFFE05252)
    percent <= 30 -> Color(0xFFE3A63C)
    else -> Color(0xFF35B77A)
}

private fun dashboardVehicle(settings: AppSettings) = VehicleParams(
    polePairs = settings.polePairs,
    wheelDiameterCm = settings.wheelDiameterCm,
    gearRatio = settings.gearRatio,
    batteryCells = settings.batteryCells
)
