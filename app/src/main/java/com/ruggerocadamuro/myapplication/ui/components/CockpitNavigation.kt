package com.ruggerocadamuro.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A navigation destination rendered by the Signal Cockpit shell. */
data class CockpitNavItem(
    val label: String,
    val icon: ImageVector
)

/**
 * Navigation deliberately avoids the stock Material bar/rail. The same
 * compact language is used in portrait and landscape, with a single active
 * signal instead of four unrelated floating controls.
 */
@Composable
fun CockpitNavigation(
    items: List<CockpitNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    landscape: Boolean,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = if (landscape) {
            modifier.width(92.dp).fillMaxHeight().padding(8.dp)
        } else {
            modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)
        },
        shape = RoundedCornerShape(if (landscape) 22.dp else 24.dp),
        glowColor = MaterialTheme.colorScheme.primary
    ) {
        if (landscape) {
            Column(
                modifier = Modifier.fillMaxHeight().padding(horizontal = 6.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CockpitMark()
                Spacer(Modifier.height(8.dp))
                items.forEachIndexed { index, item ->
                    CockpitNavigationItem(
                        item = item,
                        selected = index == selectedIndex,
                        landscape = true,
                        onClick = { onSelect(index) }
                    )
                    if (index == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items.forEachIndexed { index, item ->
                    CockpitNavigationItem(
                        item = item,
                        selected = index == selectedIndex,
                        landscape = false,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CockpitMark() {
    Box(
        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun CockpitNavigationItem(
    item: CockpitNavItem,
    selected: Boolean,
    landscape: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val itemShape = RoundedCornerShape(if (landscape) 16.dp else 18.dp)
    Column(
        modifier = modifier
            .then(if (landscape) Modifier.width(76.dp) else Modifier)
            .height(if (landscape) 68.dp else 60.dp)
            .clip(itemShape)
            .background(
                if (selected) accent.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
            )
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = accent.copy(alpha = 0.48f),
                shape = itemShape
            )
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(if (landscape) 23.dp else 21.dp)
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = if (landscape) 10.sp else 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}
