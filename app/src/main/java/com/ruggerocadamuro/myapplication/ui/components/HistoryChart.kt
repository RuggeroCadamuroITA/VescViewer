package com.ruggerocadamuro.myapplication.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ruggerocadamuro.myapplication.data.HistoryPoint
import kotlin.math.roundToInt

private enum class ChartSeries(val label: String) { POTENZA("Potenza"), RPM("RPM") }

/**
 * Grafico in tempo reale (ultimi 60 s) disegnato su Canvas: nessuna libreria
 * esterna. Serie commutabile Potenza / RPM con un segmented button.
 */
@Composable
fun HistoryChart(
    points: List<HistoryPoint>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    var series by remember { mutableIntStateOf(0) }
    val gridColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Storico (60 s)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            SingleChoiceSegmentedButtonRow {
                ChartSeries.entries.forEachIndexed { index, s ->
                    SegmentedButton(
                        selected = series == index,
                        onClick = { series = index },
                        shape = SegmentedButtonDefaults.itemShape(index, ChartSeries.entries.size),
                        label = { Text(s.label, fontSize = 12.sp) }
                    )
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            if (points.size < 2) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 2f
                )
                return@Canvas
            }

            val now = points.last().timestampMs
            val windowMs = 60_000f
            val values = points.map { if (series == 0) it.powerW else it.erpm }
            val vMax = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
            val vMin = 0f

            // griglia orizzontale (4 linee)
            for (i in 1..3) {
                val y = size.height * i / 4
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5f
                )
            }

            fun xOf(ts: Long): Float = size.width * (1f - (now - ts) / windowMs).coerceIn(0f, 1f)
            fun yOf(v: Float): Float = size.height - (v - vMin) / (vMax - vMin) * size.height * 0.92f - size.height * 0.04f

            val path = Path()
            var started = false
            for (i in points.indices) {
                val x = xOf(points[i].timestampMs)
                val y = yOf(values[i])
                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }

            // riempimento sfumato sotto la curva
            val fill = Path().apply {
                addPath(path)
                lineTo(xOf(points.last().timestampMs), size.height)
                lineTo(xOf(points.first().timestampMs), size.height)
                close()
            }
            drawPath(
                fill,
                brush = Brush.verticalGradient(
                    colors = listOf(accentColor.copy(alpha = 0.35f), Color.Transparent),
                    startY = 0f,
                    endY = size.height
                )
            )
            drawPath(path, color = accentColor, style = Stroke(width = 3f))

            // etichette valori
            val current = values.last()
            val labelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(255, 165, 165, 165)
                textSize = 28f
            }
            drawContext.canvas.nativeCanvas.drawText(
                if (series == 0) "${current.roundToInt()} W" else "${current.roundToInt()} ERPM",
                size.width - 8f,
                yOf(current) - 8f,
                labelPaint.apply { textAlign = android.graphics.Paint.Align.RIGHT }
            )
            drawContext.canvas.nativeCanvas.drawText(
                if (series == 0) "max ${(values.maxOrNull() ?: 0f).roundToInt()} W"
                else "max ${(values.maxOrNull() ?: 0f).roundToInt()}",
                8f,
                30f,
                labelPaint
            )
        }
    }
}
