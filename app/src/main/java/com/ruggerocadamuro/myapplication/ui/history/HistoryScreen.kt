package com.ruggerocadamuro.myapplication.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ruggerocadamuro.myapplication.R
import com.ruggerocadamuro.myapplication.ServiceLocator
import com.ruggerocadamuro.myapplication.data.database.RideSessionEntity
import com.ruggerocadamuro.myapplication.ui.components.GlassButton
import com.ruggerocadamuro.myapplication.ui.components.GlassSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(onOpenMap: (Long) -> Unit, viewModel: HistoryViewModel = viewModel()) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(70)
        visible = true
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        HistoryHeader(sessionCount = sessions.size)
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it / 8 }
        ) {
            if (sessions.isEmpty()) {
                EmptyHistory()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        SessionRow(session, onOpenMap = { onOpenMap(session.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryHeader(sessionCount: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                RoundedCornerShape(16.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Route, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.history_archive_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.3.sp
            )
            Text(stringResource(R.string.nav_rides), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                if (sessionCount == 0) stringResource(R.string.history_empty_subtitle)
                else stringResource(R.string.history_sessions_saved, sessionCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (sessionCount > 0) {
            GlassSurface(
                shape = CircleShape,
                glowColor = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    sessionCount.toString(),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmptyHistory() {
    GlassSurface(
        Modifier.fillMaxWidth(),
        glowColor = MaterialTheme.colorScheme.primary
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(68.dp).background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
            Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.history_empty_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SessionRow(session: RideSessionEntity, onOpenMap: () -> Unit) {
    val title = session.title.ifBlank {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(session.startedAtMs))
    }
    GlassSurface(
        Modifier.fillMaxWidth(),
        glowColor = MaterialTheme.colorScheme.primary
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (session.completed) stringResource(R.string.history_session_completed)
                        else stringResource(R.string.history_session_recovered),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    stringResource(R.string.unit_km, session.distanceGpsM / 1000.0),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SessionStat(stringResource(R.string.history_duration), stringResource(R.string.unit_duration_minutes, session.durationMs / 60_000.0), Modifier.weight(1f))
                SessionStat(stringResource(R.string.history_max), stringResource(R.string.unit_speed_kmh, session.maxGpsSpeedKmh), Modifier.weight(1f))
                SessionStat(stringResource(R.string.history_energy), stringResource(R.string.unit_power, session.wattHours), Modifier.weight(1f))
            }
            GlassButton(onClick = onOpenMap, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Map, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.history_open_route),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SessionStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)).padding(10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

class HistoryViewModel : ViewModel() {
    val sessions: StateFlow<List<RideSessionEntity>> = ServiceLocator.database.rideDao()
        .observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
