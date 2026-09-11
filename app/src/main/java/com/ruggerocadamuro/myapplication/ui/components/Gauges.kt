package com.ruggerocadamuro.myapplication.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ruggerocadamuro.myapplication.data.settings.GaugeStyle
import kotlin.math.cos
import kotlin.math.sin

/**
 * Gauge "analogica": arco a 270 gradi con lancetta, disegnato su Canvas
 * (zero dipendenze esterne). L'arco e' sempre inscritto in un quadrato
 * centrato: anche in card rettangolari non si distorce mai.
 */
@Composable
fun AnalogGauge(
    value: Float,
    maxValue: Float,
    label: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val needleColor = MaterialTheme.colorScheme.onSurface
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val startAngle = 135f
    val sweep = 270f
    val fraction = if (maxValue > 0) (value / maxValue).coerceIn(0f, 1f) else 0f

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // quadrato inscritto nel box disponibile: evita archi ellittici
            val side = minOf(size.width, size.height)
            val stroke = (side * 0.09f).coerceAtLeast(6.dp.toPx())
            val inset = stroke / 2f + 2.dp.toPx()
            val arcSide = (side - inset * 2).coerceAtLeast(0f)
            val arcSize = Size(arcSide, arcSide)
            val topLeft = Offset((size.width - arcSide) / 2f, (size.height - arcSide) / 2f)
            val cx = topLeft.x + arcSide / 2f
            val cy = topLeft.y + arcSide / 2f

            // arco di sfondo
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            // arco valorizzato
            if (fraction > 0.001f) {
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweep * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            // tacche principali (0, 25, 50, 75, 100%)
            val rOuter = arcSide / 2f
            val rInner = rOuter - stroke * 0.9f
            for (i in 0..4) {
                val angle = Math.toRadians((startAngle + sweep * i / 4).toDouble())
                drawLine(
                    color = textColor,
                    start = Offset(
                        cx + (rInner * cos(angle)).toFloat(),
                        cy + (rInner * sin(angle)).toFloat()
                    ),
                    end = Offset(
                        cx + (rOuter * cos(angle)).toFloat(),
                        cy + (rOuter * sin(angle)).toFloat()
                    ),
                    strokeWidth = 2.dp.toPx()
                )
            }
            // lancetta: dal centro verso il valore, senza superare l'anello
            val needleAngle = Math.toRadians((startAngle + sweep * fraction).toDouble())
            val rNeedle = rOuter - stroke * 0.4f
            drawLine(
                color = needleColor,
                start = Offset(cx, cy),
                end = Offset(
                    cx + (rNeedle * cos(needleAngle)).toFloat(),
                    cy + (rNeedle * sin(needleAngle)).toFloat()
                ),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(color = needleColor, radius = stroke * 0.35f, center = Offset(cx, cy))
        }
        // valore al centro dell'arco (nell'apertura dei 270 gradi), leggermente in basso
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 34.dp)
        ) {
            if (label.isNotEmpty()) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = textColor)
            }
            Text(
                gaugeValueText(value),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            if (unit.isNotEmpty()) {
                Text(unit, style = MaterialTheme.typography.labelSmall, color = textColor)
            }
        }
    }
}

/**
 * Formattazione intelligente dei valori gauge: 1 decimale sotto 10
 * (tensione 8,4 V), altrimenti intero (corrente 42 A, potenza 1200 W).
 */
fun gaugeValueText(v: Float): String =
    if (v == 0f) "0"
    else if (kotlin.math.abs(v) < 10f) "%.1f".format(v)
    else "%.0f".format(v)

/**
 * Gauge "digitale": solo numero grande + unita', piu' leggibile al sole.
 */
@Composable
fun DigitalGauge(
    value: Float,
    label: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            gaugeValueText(value),
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Card di telemetria riutilizzabile: sceglie automaticamente lo stile
 * analogico o digitale secondo l'impostazione utente.
 */
@Composable
fun GaugeCard(
    title: String,
    value: Float,
    maxValue: Float,
    unit: String,
    color: Color,
    style: GaugeStyle,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(modifier = Modifier.fillMaxWidth().height(118.dp).padding(top = 4.dp)) {
                when (style) {
                    GaugeStyle.ANALOG -> AnalogGauge(
                        value = value,
                        maxValue = maxValue,
                        label = "",
                        unit = unit,
                        color = color,
                        modifier = Modifier.fillMaxSize()
                    )
                    GaugeStyle.DIGITAL -> DigitalGauge(
                        value = value,
                        label = "",
                        unit = unit,
                        color = color,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/** Colore temperatura: verde sotto soglia, giallo in warn, rosso in danger. */
fun tempColor(tempC: Float, warn: Float, danger: Float): Color = when {
    tempC >= danger -> Color(0xFFE53935)
    tempC >= warn -> Color(0xFFFDD835)
    else -> Color(0xFF43A047)
}
