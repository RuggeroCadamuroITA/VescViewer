package com.ruggerocadamuro.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ruggerocadamuro.myapplication.R
import com.ruggerocadamuro.myapplication.ui.theme.AccentPalette
import com.ruggerocadamuro.myapplication.ui.theme.BleBadgeBackground


/**
 * Campo numerico preciso: il valore viene confermato con Done o quando il
 * campo perde il focus, evitando che uno stato temporaneo (es. "2.") venga
 * subito arrotondato dal DataStore mentre l'utente sta scrivendo.
 */
@Composable
fun NumericSetting(
    label: String,
    valueText: String,
    value: Float,
    min: Float,
    max: Float,
    integer: Boolean,
    onChange: (Float) -> Unit
) {
    var text by remember(value) { mutableStateOf(valueText) }
    var focused by remember { mutableStateOf(false) }

    fun commit() {
        val normalized = text.replace(',', '.')
        val parsed = if (integer) normalized.toIntOrNull()?.toFloat() else normalized.toFloatOrNull()
        if (parsed != null) onChange(parsed.coerceIn(min, max))
        else text = valueText
    }

    LaunchedEffect(value, focused) {
        if (!focused) text = valueText
    }

    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input.filter { it.isDigit() || it == '-' || (!integer && (it == '.' || it == ',')) }
            },
            modifier = Modifier.fillMaxWidth().onFocusChanged {
                if (focused && !it.isFocused) commit()
                focused = it.isFocused
            },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { commit() })
        )
    }
}

/** Superficie vetrosa riutilizzabile: tinta, glow radiale, rim-light e ombra morbida. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    glowColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.975f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "glass_press_scale"
    )
    val clickableModifier = if (onClick != null) Modifier.clickable(
        onClick = {
            pressed = true
            onClick()
            pressed = false
        }
    ) else Modifier
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(clickableModifier)
            .clip(shape)
            .shadow(20.dp, shape, ambientColor = Color.Black.copy(alpha = 0.42f), spotColor = Color.Black.copy(alpha = 0.35f))
            .background(Color.White.copy(alpha = 0.055f), shape)
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.10f), Color.Transparent)
                ),
                shape
            )
    ) {
        Box(
            Modifier.fillMaxSize().blur(36.dp).background(
                Brush.radialGradient(
                    listOf(glowColor.copy(alpha = 0.24f), Color.Transparent)
                )
            )
        )
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.025f)))
        content()
    }
}

/**
 * Slider con etichetta a sinistra e valore a destra: usato dalle impostazioni
 * e dal setup guidato per i parametri dell'allarme.
 */
@Composable
fun SliderSetting(
    label: String,
    valueText: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    onChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = onChange,
            valueRange = min..max,
            steps = ((max - min) / step).toInt() - 1
        )
    }
}

/**
 * Scelta del colore accento: "Auto" piu' la palette. [selectedIndex] null
 * significa "nessuna scelta ancora fatta", stato iniziale del setup guidato,
 * dove il colore va scelto per forza.
 */
@Composable
fun AccentColorChooser(
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AccentDot(
            color = MaterialTheme.colorScheme.primary,
            selected = selectedIndex == -1,
            label = stringResource(R.string.accent_auto)
        ) { onSelect(-1) }
        AccentPalette.forEachIndexed { index, option ->
            AccentDot(color = option.dark, selected = selectedIndex == index, label = option.label) {
                onSelect(index)
            }
        }
    }
}

@Composable
private fun AccentDot(color: Color, selected: Boolean, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(color)
                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                .clickable(onClick = onClick)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Card selezionabile usata nel setup guidato e nelle impostazioni (lingua,
 * tema, unita' di misura): bordo e spunta raccontano la scelta attiva, cosi'
 * si capisce a colpo d'occhio cosa e' stato selezionato.
 */
@Composable
fun ChoiceCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    swatch: Color? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) accent.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        swatch?.let {
            Box(Modifier.size(22.dp).clip(CircleShape).background(it))
            Spacer(Modifier.width(10.dp))
        }
        icon?.let {
            Icon(it, contentDescription = null, tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface
            )
            supporting?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

/**
 * Glifo Bluetooth LE: runa Bluetooth seguita da "LE", cosi' si capisce che il
 * collegamento e' BLE e non il Bluetooth classico. Il colore eredita
 * [LocalContentColor] quando non specificato (icona di navigazione), mentre il
 * badge lo forza a nero.
 */
@Composable
fun BleGlyph(
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    iconSize: Dp = 22.dp,
    labelSize: TextUnit = 9.sp
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Bluetooth,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(iconSize)
        )
        Text(
            text = "LE",
            color = color,
            fontSize = labelSize,
            lineHeight = labelSize,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Badge del logo BLE usato nelle barre superiori: riquadro chiaro con il glifo
 * nero. Il fondo e' volutamente chiaro e fisso (non `primaryContainer`) perche'
 * un accento chiaro rendeva la runa invisibile sul proprio contenitore.
 */
@Composable
fun BleBadge(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    cornerRadius: Dp = 14.dp
) {
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(cornerRadius))
            .background(BleBadgeBackground),
        contentAlignment = Alignment.Center
    ) {
        BleGlyph(color = Color.Black, iconSize = size * 0.48f, labelSize = (size.value * 0.20f).sp)
    }
}

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
                Box(
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
                text = rssi?.let { stringResource(R.string.rssi_value, it) }
                    ?: stringResource(R.string.rssi_unknown),
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
        Box(
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
