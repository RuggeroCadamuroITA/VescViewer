package com.ruggerocadamuro.myapplication.ui.map

import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ruggerocadamuro.myapplication.ServiceLocator
import com.ruggerocadamuro.myapplication.data.database.RidePointEntity
import com.ruggerocadamuro.myapplication.ui.components.GlassCard
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polyline
import com.ruggerocadamuro.myapplication.data.ride.RideAnalytics
import kotlin.math.hypot

private enum class RouteMetric { GPS_SPEED, POWER, BATTERY_CURRENT, VOLTAGE, TEMPERATURE, ERPM }

@Composable
fun RideMapScreen(
    sessionId: Long,
    onBack: () -> Unit,
    viewModel: RideMapViewModel = viewModel()
) {
    val points by viewModel.points.collectAsState()
    var metric by remember { mutableStateOf(RouteMetric.GPS_SPEED) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val selected = selectedIndex?.let { points.getOrNull(it) }

    LaunchedEffect(sessionId) { viewModel.load(sessionId) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MapHeader(metric = metric, onMetric = { metric = it }, onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
                    MapView(context).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                        addMapListener(null)
                        overlays.add(
                            MapEventsOverlay(object : MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                    selectedIndex = nearestPointIndex(points, p)
                                    return true
                                }
                                override fun longPressHelper(p: GeoPoint): Boolean = false
                            })
                        )
                    }
                },
                update = { map ->
                    map.overlays.removeAll { it is Polyline }
                    if (points.isNotEmpty()) {
                        val all = points.map { GeoPoint(it.latitude, it.longitude) }
                        points.zipWithNext().forEachIndexed { index, pair ->
                            val line = Polyline(map).apply {
                                setPoints(listOf(
                                    GeoPoint(pair.first.latitude, pair.first.longitude),
                                    GeoPoint(pair.second.latitude, pair.second.longitude)
                                ))
                                outlinePaint.strokeWidth = 10f
                                outlinePaint.color = metricColor(metricValue(pair.second, metric), metric)
                            }
                            map.overlays.add(line)
                        }
                        if (map.boundingBox == null || map.zoomLevelDouble < 3.0) {
                            map.controller.setCenter(all[all.size / 2])
                            map.controller.setZoom(15.0)
                        }
                        map.invalidate()
                    }
                }
            )
            selected?.let { point ->
                PointInspector(
                    point = point,
                    index = selectedIndex ?: 0,
                    count = points.size,
                    onPrevious = { selectedIndex = ((selectedIndex ?: 0) - 1).coerceAtLeast(0) },
                    onNext = { selectedIndex = ((selectedIndex ?: 0) + 1).coerceAtMost(points.lastIndex) },
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

@Composable
private fun MapHeader(metric: RouteMetric, onMetric: (RouteMetric) -> Unit, onBack: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("←", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
            Column {
                Text("Percorso OpenStreetMap", style = MaterialTheme.typography.titleMedium)
                Text("Metrica: ${metric.name.lowercase().replace('_', ' ')}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PointInspector(
    point: RidePointEntity,
    index: Int,
    count: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier.fillMaxWidth(), glowColor = MaterialTheme.colorScheme.primary) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Campione ${index + 1} / $count", style = MaterialTheme.typography.titleSmall)
            Text("GPS %.1f km/h · ERPM %.0f".format(point.gpsSpeedKmh ?: 0f, point.erpm ?: 0f))
            Text("%.1f A batteria · %.1f A motore · %.1f V · %.0f W".format(
                point.currentBatteryA ?: 0f, point.currentMotorA ?: 0f,
                point.voltageV ?: 0f, point.powerW ?: 0f
            ))
            Text("RPM %.0f · Duty %.1f%% · MOS %.1f°C".format(
                point.mechanicalRpm ?: 0f, point.dutyCyclePercent ?: 0f, point.tempMosC ?: 0f
            ))
            Text("%.2f Ah · %.1f Wh · quota %.0f m".format(
                point.ampHours ?: 0f, point.wattHours ?: 0f, point.distanceGpsM
            ), style = MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("‹ precedente", color = if (index > 0) MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.padding(top = 5.dp))
                Text("successivo ›", color = if (index < count - 1) MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

private fun nearestPointIndex(points: List<RidePointEntity>, target: GeoPoint): Int? {
    return RideAnalytics.nearestPointIndex(points, target.latitude, target.longitude)
}

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
    return AndroidColor.HSVToColor(floatArrayOf((120f - scale * 120f), 0.8f, 0.95f))
}

class RideMapViewModel : androidx.lifecycle.ViewModel() {
    private val _points = kotlinx.coroutines.flow.MutableStateFlow<List<RidePointEntity>>(emptyList())
    val points: kotlinx.coroutines.flow.StateFlow<List<RidePointEntity>> = _points.asStateFlow()

    fun load(sessionId: Long) {
        viewModelScope.launch {
            ServiceLocator.database.rideDao().observePoints(sessionId).collect { _points.value = it }
        }
    }
}
