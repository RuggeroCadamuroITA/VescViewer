package com.ruggerocadamuro.myapplication.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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

/**
 * Numeric field for settings. The value is committed on Done or focus loss,
 * so temporary input such as "2." is not written while the user is typing.
 */
@Composable
fun NumericSetting(
    label: String,
    valueText: String,
    value: Float,
    min: Float,
    max: Float,
    integer: Boolean,
    onChange: (Float) -> Unit,
    unitSuffix: String? = null
) {
    var text by remember(value) { mutableStateOf(valueText) }
    var focused by remember { mutableStateOf(false) }

    fun commit() {
        val normalized = text.replace(',', '.')
        val parsed = if (integer) {
            normalized.toIntOrNull()?.toFloat()
        } else {
            normalized.toFloatOrNull()
        }
        if (parsed != null) onChange(parsed.coerceIn(min, max)) else text = valueText
    }

    LaunchedEffect(value, focused) {
        if (!focused) text = valueText
    }

    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.filter {
                it.isDigit() || it == '-' || (!integer && (it == '.' || it == ','))
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                if (focused && !it.isFocused) commit()
                focused = it.isFocused
            },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        label = { Text(label) },
        suffix = unitSuffix?.let { suffixText ->
            { Text(suffixText, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { commit() })
    )
}

/**
 * Shared glass surface for the whole app. It is intentionally restrained:
 * one translucent elevation layer, a soft rim light and a content-aware glow.
 * Pressed cards use a spring scale rather than a generic bounce animation.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    glowColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.975f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "glass_press_scale"
    )
    val clickableModifier = if (onClick != null) {
        Modifier
            .semantics { role = Role.Button }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    } else Modifier

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(clickableModifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), shape)
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.38f),
                        glowColor.copy(alpha = 0.14f),
                        Color.White.copy(alpha = 0.06f)
                    )
                ),
                shape
            )
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            listOf(glowColor.copy(alpha = 0.16f), Color.Transparent)
                        )
                    )
            )
            Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.018f)))
            content()
        }
    }
}

/** Backwards-compatible name used by existing screens. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    glowColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) = GlassSurface(modifier, shape, glowColor, onClick, content)

/** A compact filled CTA with the same motion language as glass cards. */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    content: @Composable () -> Unit
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        glowColor = MaterialTheme.colorScheme.primary,
        onClick = if (enabled) onClick else null
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .background(
                        if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                    .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = if (compact) 10.dp else 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) { content() }
            }
        }
    }
}

/** Outline action with the same dimensions and focus/press language as [GlassButton]. */
@Composable
fun GlassOutlineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    content: @Composable () -> Unit
) {
    val borderColor = if (enabled) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    }
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        glowColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        onClick = if (enabled) onClick else null
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                    .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = if (compact) 9.dp else 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) { content() }
            }
        }
    }
}

/** Icon-only control with a guaranteed touch target for maps and cockpit actions. */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 48.dp
) {
    GlassSurface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(14.dp),
        glowColor = if (enabled) tint else MaterialTheme.colorScheme.outline,
        onClick = if (enabled) onClick else null
    ) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

/** Compact segmented choice control shared by settings and secondary flows. */
@Composable
fun GlassChoiceRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.36f), shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                        else Color.Transparent
                    )
                    .clickable(onClick = { onSelect(index) })
                    .semantics { role = Role.RadioButton },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Slider with a label and value. */
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

/** Accent selection row shared by setup and settings. */
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
            AccentDot(color = option.dark, selected = selectedIndex == index, label = stringResource(option.labelRes)) {
                onSelect(index)
            }
        }
    }
}

@Composable
private fun AccentDot(color: Color, selected: Boolean, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color)
                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                .clickable(onClick = onClick)
                .semantics { role = Role.RadioButton }
        )
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Selectable row used in setup and settings. */
@Composable
fun ChoiceCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    swatch: Color? = null,
    icon: ImageVector? = null
) {
    val accent = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "choice_press_scale"
    )
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
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
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics { role = Role.RadioButton }
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
            supporting?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (selected) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
        }
    }
}

/** BLE glyph: Bluetooth rune followed by LE. */
@Composable
fun BleGlyph(
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    iconSize: Dp = 22.dp,
    labelSize: TextUnit = 9.sp
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
        Text("LE", color = color, fontSize = labelSize, lineHeight = labelSize, fontWeight = FontWeight.Bold)
    }
}

/** BLE logo badge used in headers. */
@Composable
fun BleBadge(modifier: Modifier = Modifier, size: Dp = 44.dp, cornerRadius: Dp = 14.dp) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier.size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f), shape),
        contentAlignment = Alignment.Center
    ) {
        BleGlyph(
            color = MaterialTheme.colorScheme.primary,
            iconSize = size * 0.48f,
            labelSize = (size.value * 0.20f).sp
        )
    }
}

/** RSSI indicator: four bars and the optional dBm value. */
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
                Box(
                    modifier = Modifier.padding(horizontal = 1.dp).width(5.dp).height((i * 5).dp)
                        .clip(CircleShape).background(if (i <= bars) activeColor else inactive)
                )
            }
        }
        if (showValue) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = rssi?.let { stringResource(R.string.rssi_value, it) } ?: stringResource(R.string.rssi_unknown),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Connection status chip. */
@Composable
fun ConnectionStateChip(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(CircleShape).background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.45f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Compact selectable pill for filters and secondary navigation. */
@Composable
fun GlassPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    GlassSurface(
        modifier = modifier.heightIn(min = 48.dp),
        shape = CircleShape,
        glowColor = if (selected) accent else MaterialTheme.colorScheme.outline,
        onClick = onClick
    ) {
        Text(
            text = label,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

/** Animated, accessible toggle used for security and telemetry preferences. */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 2.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "glass_switch_thumb"
    )
    Box(
        modifier = modifier
            .width(52.dp)
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .width(52.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (checked) accent.copy(alpha = 0.82f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
                )
                .border(
                    1.dp,
                    if (checked) accent else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(18.dp)
                )
        )
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(28.dp)
                .clip(CircleShape)
                .background(if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}

/** Compact secondary stat kept for legacy screens. */
@Composable
fun StatCard(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    GlassSurface(modifier = modifier, shape = RoundedCornerShape(18.dp), glowColor = color) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}
