package com.ruggerocadamuro.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Indicatore segnale RSSI: 4 barre proporzionali alla qualita' + valore in dBm.
 * Sempre visibile in dashboard.
 */
@Composable
fun RssiIndicator(rssi: Int?, modifier: Modifier = Modifier, showValue: Boolean = true) {
    val bars = when {
        rssi == null -> 0
        rssi >= -60 -> 4
        rssi >= -70 -> 3
        rssi >= -80 -> 2
        else -> 1
    }
    val activeColor = when {
        rssi == null -> MaterialTheme.colorScheme.outline
        rssi >= -70 -> Color(0xFF43A047)
        rssi >= -80 -> Color(0xFFFDD835)
        else -> Color(0xFFE53935)
    }
    val inactive = MaterialTheme.colorScheme.surfaceVariant

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.Bottom) {
            for (i in 1..4) {
                val h = (i * 5).dp
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .padding(horizontal = 1.dp)
                        .width(5.dp)
                        .height(h)
                        .clip(CircleShape)
                        .background(if (i <= bars) activeColor else inactive)
                )
            }
        }
        if (showValue) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = rssi?.let { "$it dBm" } ?: "-- dBm",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Puntino + testo per lo stato di connessione BLE. */
@Composable
fun ConnectionStateChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .width(8.dp)
                .height(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Card compatta per valori secondari (Wh, Ah, distanza, potenza...). */
@Composable
fun StatCard(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}
