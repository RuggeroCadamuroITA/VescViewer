package com.ruggerocadamuro.myapplication.ui.map

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ruggerocadamuro.myapplication.R
import com.ruggerocadamuro.myapplication.ServiceLocator
import com.ruggerocadamuro.myapplication.data.database.RidePointEntity
import com.ruggerocadamuro.myapplication.data.settings.AppSettings
import com.ruggerocadamuro.myapplication.data.settings.SpeedUnit
import com.ruggerocadamuro.myapplication.data.settings.TempUnit
import com.ruggerocadamuro.myapplication.data.VescMath
import com.ruggerocadamuro.myapplication.data.ride.RideAnalytics
import com.ruggerocadamuro.myapplication.ui.components.GlassIconButton
import com.ruggerocadamuro.myapplication.ui.components.ConnectionStateChip
import com.ruggerocadamuro.myapplication.ui.components.GlassPill
import com.ruggerocadamuro.myapplication.ui.components.GlassSurface
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private enum class RouteMetric {
    GPS_SPEED, POWER, BATTERY_CURRENT, VOLTAGE, TEMPERATURE, ERPM
}

@Composable
fun RideMapScreen(
    sessionId: Long,
    settings: AppSettings,
    onBack: () -> Unit,
    viewModel: RideMapViewModel = viewModel()
) {
    val points by viewModel.points.collectAsStateWithLifecycle()
    var metric by remember(sessionId) { mutableStateOf(RouteMetric.GPS_SPEED) }
    var selectedIndex by remember(sessionId) { mutableStateOf<Int?>(null) }
    val latestPoints by rememberUpdatedState(points)
    val selected = selectedIndex?.let { points.getOrNull(it) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var networkAvailable by remember { mutableStateOf(isNetworkAvailable(context)) }

    DisposableEffect(context) {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { networkAvailable = true }
            override fun onLost(network: android.net.Network) { networkAvailable = isNetworkAvailable(context) }
        }
        if (connectivity != null) {
            runCatching {
                connectivity.registerNetworkCallback(
                    android.net.NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(),
                    callback
                )
            }
        }
        onDispose { runCatching { connectivity?.unregisterNetworkCallback(callback) } }
    }

    LaunchedEffect(sessionId) {
        selectedIndex = null
        viewModel.load(sessionId)
    }

    Column(Modifier.fillMaxSize()) {
        MapHeader(metric = metric, onMetric = { metric = it }, onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!networkAvailable) {
                ConnectionStateChip(
                    label = stringResource(R.string.map_offline),
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                )
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
                    Configuration.getInstance().userAgentValue = context.packageName
                    MapView(context).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(
                            org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
                        )
                        overlays.add(
                            MapEventsOverlay(object : MapEventsReceiver {
                                override fun singleTapConfirmedHelper(target: GeoPoint): Boolean {
                                    selectedIndex = nearestPointIndex(latestPoints, target)
                                    return true
                                }

                                override fun longPressHelper(target: GeoPoint): Boolean = false
                            })
                        )
                    }
                },
                update = { map ->
                    map.overlays.removeAll { it is Polyline || it is Marker }
                    if (points.size >= 2) {
                        val coordinates = points.map { GeoPoint(it.latitude, it.longitude) }
                        points.zipWithNext().forEach { (start, end) ->
                            map.overlays.add(
                                Polyline(map).apply {
                                    setPoints(
                                        listOf(
                                            GeoPoint(start.latitude, start.longitude),
                                            GeoPoint(end.latitude, end.longitude)
                                        )
                                    )
                                    outlinePaint.strokeWidth = 12f
                                    outlinePaint.color = metricColor(metricValue(end, metric), metric)
                                }
                            )
                        }
                        selected?.let { point ->
                            map.overlays.add(
                                Marker(map).apply {
                                    position = GeoPoint(point.latitude, point.longitude)
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    title = map.context.getString(
                                        R.string.map_sample_title,
                                        selectedIndex?.plus(1) ?: 1
                                    )
                                }
                            )
                        }
                        if (map.zoomLevelDouble < 3.0) {
                            map.controller.setCenter(coordinates[coordinates.lastIndex / 2])
                            map.controller.setZoom(15.0)
                        }
                        map.invalidate()
                    }
                },
                onRelease = { map -> map.onDetach() }
            )
            if (points.isEmpty()) {
                MapEmptyState(modifier = Modifier.align(Alignment.Center))
            }
            selected?.let { point ->
                PointInspector(
                    point = point,
                    settings = settings,
                    index = selectedIndex ?: 0,
                    count = points.size,
                    onPrevious = { selectedIndex = ((selectedIndex ?: 0) - 1).coerceAtLeast(0) },
                    onNext = { selectedIndex = ((selectedIndex ?: 0) + 1).coerceAtMost(points.lastIndex) },
                    onClose = { selectedIndex = null },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun MapHeader(metric: RouteMetric, onMetric: (RouteMetric) -> Unit, onBack: () -> Unit) {
    GlassSurface(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        glowColor = MaterialTheme.colorScheme.primary
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(
                    onClick = onBack,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.primary
                )
                Icon(Icons.Filled.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        stringResource(R.string.map_header_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(stringResource(R.string.map_inspect_hint), style = MaterialTheme.typography.titleSmall)
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                RouteMetric.entries.forEach { option ->
                    GlassPill(
                        label = metricLabel(option),
                        selected = option == metric,
                        onClick = { onMetric(option) },
                        modifier = Modifier
                    )
                }
            }
        }
    }
}

@Composable
private fun MapEmptyState(modifier: Modifier = Modifier) {
    GlassSurface(
        modifier = modifier.padding(20.dp),
        glowColor = MaterialTheme.colorScheme.primary
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Map,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Text(
                stringResource(R.string.map_empty_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                stringResource(R.string.map_empty_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun PointInspector(
    point: RidePointEntity,
    settings: AppSettings,
    index: Int,
    count: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(modifier.fillMaxWidth(), glowColor = MaterialTheme.colorScheme.primary) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.map_sample_counter, index + 1, count), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.map_coordinates, point.latitude, point.longitude), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GlassIconButton(
                    onClick = onClose,
                    icon = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InspectorValue(
                    stringResource(R.string.metric_gps),
                    point.gpsSpeedKmh?.let {
                        stringResource(
                            if (settings.speedUnit == SpeedUnit.MPH) R.string.unit_speed_mph else R.string.unit_speed_kmh,
                            VescMath.displaySpeedKmh(it, settings.speedUnit)
                        )
                    } ?: "—",
                    Modifier.weight(1f)
                )
                InspectorValue(stringResource(R.string.metric_erpm), "%.0f".format(point.erpm ?: 0f), Modifier.weight(1f))
                InspectorValue(
                    stringResource(R.string.metric_vesc),
                    point.vescSpeedKmh?.let {
                        stringResource(
                            if (settings.speedUnit == SpeedUnit.MPH) R.string.unit_speed_mph else R.string.unit_speed_kmh,
                            VescMath.displaySpeedKmh(it, settings.speedUnit)
                        )
                    } ?: "—",
                    Modifier.weight(1f)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InspectorValue(stringResource(R.string.metric_battery), stringResource(R.string.unit_current, point.currentBatteryA ?: 0f), Modifier.weight(1f))
                InspectorValue(stringResource(R.string.metric_motor), stringResource(R.string.unit_current, point.currentMotorA ?: 0f), Modifier.weight(1f))
                InspectorValue(stringResource(R.string.metric_volt), stringResource(R.string.unit_voltage, point.voltageV ?: 0f), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InspectorValue(stringResource(R.string.metric_rpm), "%.0f".format(point.mechanicalRpm ?: 0f), Modifier.weight(1f))
                InspectorValue(stringResource(R.string.metric_duty), stringResource(R.string.unit_percent, point.dutyCyclePercent ?: 0f), Modifier.weight(1f))
                InspectorValue(
                    stringResource(R.string.metric_mos),
                    point.tempMosC?.let {
                        stringResource(
                            if (settings.tempUnit == TempUnit.FAHRENHEIT) R.string.unit_temperature_f else R.string.unit_temperature_c,
                            VescMath.displayTemperatureCelsius(it, settings.tempUnit)
                        )
                    } ?: "—",
                    Modifier.weight(1f)
                )
            }
            Text(
                stringResource(
                    R.string.map_point_summary,
                    point.ampHours ?: 0f,
                    point.wattHours ?: 0f,
                    point.distanceGpsM,
                    point.altitudeM ?: 0.0
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(
                    onClick = onPrevious,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.map_previous_sample),
                    enabled = index > 0,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(stringResource(R.string.map_scroll_samples), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                GlassIconButton(
                    onClick = onNext,
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.map_next_sample),
                    enabled = index < count - 1,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun InspectorValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)).padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun metricLabel(metric: RouteMetric): String = when (metric) {
    RouteMetric.GPS_SPEED -> stringResource(R.string.metric_gps)
    RouteMetric.POWER -> stringResource(R.string.history_power)
    RouteMetric.BATTERY_CURRENT -> stringResource(R.string.metric_amps)
    RouteMetric.VOLTAGE -> stringResource(R.string.metric_volts)
    RouteMetric.TEMPERATURE -> stringResource(R.string.metric_mos_c)
    RouteMetric.ERPM -> stringResource(R.string.metric_erpm)
}

private fun isNetworkAvailable(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun nearestPointIndex(points: List<RidePointEntity>, target: GeoPoint): Int? =
    RideAnalytics.nearestPointIndex(points, target.latitude, target.longitude)

private fun metricValue(point: RidePointEntity, metric: RouteMetric): Float = when (metric) {
    RouteMetric.GPS_SPEED -> point.gpsSpeedKmh ?: 0f
    RouteMetric.POWER -> point.powerW ?: 0f
    RouteMetric.BATTERY_CURRENT -> point.currentBatteryA ?: 0f
    RouteMetric.VOLTAGE -> point.voltageV ?: 0f
    RouteMetric.TEMPERATURE -> point.tempMosC ?: 0f
    RouteMetric.ERPM -> point.erpm ?: 0f
}

private fun metricColor(value: Float, metric: RouteMetric): Int {
    val scale = when (metric) {
        RouteMetric.GPS_SPEED -> (value / 60f).coerceIn(0f, 1f)
        RouteMetric.POWER -> (value / 5000f).coerceIn(0f, 1f)
        RouteMetric.BATTERY_CURRENT -> (value / 100f).coerceIn(0f, 1f)
        RouteMetric.VOLTAGE -> ((value - 30f) / 20f).coerceIn(0f, 1f)
        RouteMetric.TEMPERATURE -> ((value - 30f) / 70f).coerceIn(0f, 1f)
        RouteMetric.ERPM -> (value / 10000f).coerceIn(0f, 1f)
    }
    return AndroidColor.HSVToColor(floatArrayOf(120f - scale * 120f, 0.8f, 0.95f))
}

class RideMapViewModel(
    private val pointsSource: (Long) -> Flow<List<RidePointEntity>> = {
        ServiceLocator.database.rideDao().observePoints(it)
    }
) : androidx.lifecycle.ViewModel() {
    private val _points = MutableStateFlow<List<RidePointEntity>>(emptyList())
    val points: StateFlow<List<RidePointEntity>> = _points.asStateFlow()
    private var loadJob: Job? = null
    private var loadedSessionId: Long? = null

    fun load(sessionId: Long) {
        if (loadedSessionId == sessionId && loadJob?.isActive == true) return
        loadedSessionId = sessionId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            pointsSource(sessionId).collect { _points.value = it }
        }
    }
}
