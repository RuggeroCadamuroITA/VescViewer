package com.ruggerocadamuro.myapplication.ui.recording

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ruggerocadamuro.myapplication.ui.components.GlassButton
import com.ruggerocadamuro.myapplication.ui.components.GlassSurface

/** Compact live-session control, deliberately separate from the primary speed hero. */
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
    val statusColor by animateColorAsState(
        targetValue = when {
            !active -> MaterialTheme.colorScheme.onSurfaceVariant
            paused -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(240),
        label = "recording_status_color"
    )
    val pulse by rememberInfiniteTransition(label = "recording_pulse").animateFloat(
        initialValue = 0.72f,
        targetValue = if (active && !paused) 1f else 0.72f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "recording_dot_alpha"
    )

    GlassSurface(modifier.fillMaxWidth(), glowColor = statusColor) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(10.dp).background(statusColor.copy(alpha = if (active) pulse else 1f), CircleShape)
                )
                Spacer(Modifier.width(9.dp))
                AnimatedContent(
                    targetState = when {
                        !active -> "idle"
                        paused -> "paused"
                        else -> "active"
                    },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "recording_label"
                ) { state ->
                    val stateLabel = when (state) {
                        "active" -> stringResource(com.ruggerocadamuro.myapplication.R.string.recording_active)
                        "paused" -> stringResource(com.ruggerocadamuro.myapplication.R.string.recording_paused)
                        else -> stringResource(com.ruggerocadamuro.myapplication.R.string.recording_idle)
                    }
                    Text(
                        stateLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.weight(1f))
                if (active) {
                    Text(
                        stringResource(com.ruggerocadamuro.myapplication.R.string.recording_stats, pointsSaved, distanceM),

                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        stringResource(com.ruggerocadamuro.myapplication.R.string.recording_source),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (active) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        onClick = if (paused) onResume else onPause,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(
                                if (paused) com.ruggerocadamuro.myapplication.R.string.recording_resume
                                else com.ruggerocadamuro.myapplication.R.string.recording_pause
                            ),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    GlassSurface(
                        modifier = Modifier.weight(0.78f),
                        shape = RoundedCornerShape(16.dp),
                        glowColor = MaterialTheme.colorScheme.error,
                        onClick = onStop
                    ) {
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(com.ruggerocadamuro.myapplication.R.string.recording_stop), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                GlassButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(com.ruggerocadamuro.myapplication.R.string.recording_start), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
