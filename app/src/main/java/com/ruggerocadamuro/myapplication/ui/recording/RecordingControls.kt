package com.ruggerocadamuro.myapplication.ui.recording

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ruggerocadamuro.myapplication.ui.components.GlassCard

@Composable
fun RecordingControls(
    active: Boolean,
    paused: Boolean,
    pointsSaved: Int,
    distanceM: Double,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (!active) "Registra uscita" else if (paused) "Registrazione in pausa" else "Registrazione attiva")
            if (active) {
                Text("$pointsSaved campioni · %.0f m".format(distanceM))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = if (paused) onResume else onPause) {
                        Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null)
                        Text(if (paused) "Riprendi" else "Pausa", modifier = Modifier.padding(start = 5.dp))
                    }
                    Button(onClick = onStop) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Text("Termina", modifier = Modifier.padding(start = 5.dp))
                    }
                }
            } else {
                Button(onClick = onStart) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("Avvia sessione", modifier = Modifier.padding(start = 5.dp))
                }
            }
        }
    }
}
