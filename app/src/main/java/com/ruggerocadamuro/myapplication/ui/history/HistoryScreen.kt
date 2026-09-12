package com.ruggerocadamuro.myapplication.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ruggerocadamuro.myapplication.ServiceLocator
import com.ruggerocadamuro.myapplication.data.database.RideSessionEntity
import com.ruggerocadamuro.myapplication.ui.components.GlassCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(onOpenMap: (Long) -> Unit, viewModel: HistoryViewModel = viewModel()) {
    val sessions by viewModel.sessions.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Route, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text("Uscite", style = MaterialTheme.typography.headlineSmall)
                Text("Percorsi e telemetria salvati sul dispositivo", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (sessions.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth(), glowColor = MaterialTheme.colorScheme.primary) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("Nessuna uscita registrata", style = MaterialTheme.typography.titleMedium)
                    Text("Avvia una sessione dalla dashboard per vedere qui il percorso.", modifier = Modifier.padding(top = 6.dp))
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sessions, key = { it.id }) { session ->
                    SessionRow(session, onOpenMap = { onOpenMap(session.id) })
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: RideSessionEntity, onOpenMap: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), glowColor = MaterialTheme.colorScheme.primary) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                session.title.ifBlank { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(session.startedAtMs)) },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "%.2f km · %.0f min · max %.1f km/h".format(
                    session.distanceGpsM / 1000.0,
                    session.durationMs / 60_000.0,
                    session.maxGpsSpeedKmh
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onOpenMap) {
                    Icon(Icons.Filled.Map, contentDescription = null)
                    Text("Apri mappa", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

class HistoryViewModel : ViewModel() {
    val sessions: StateFlow<List<RideSessionEntity>> = ServiceLocator.database.rideDao()
        .observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
