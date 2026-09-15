package com.ruggerocadamuro.myapplication.ui.scan

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ruggerocadamuro.myapplication.R
import com.ruggerocadamuro.myapplication.data.ble.BleManager
import com.ruggerocadamuro.myapplication.ui.components.BleGlyph
import com.ruggerocadamuro.myapplication.ui.components.GlassButton
import com.ruggerocadamuro.myapplication.ui.components.GlassOutlineButton
import com.ruggerocadamuro.myapplication.ui.components.GlassSurface
import com.ruggerocadamuro.myapplication.ui.components.RssiIndicator

/** Permissions required for BLE scan/connect on this Android version. */
fun blePermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

fun hasBlePermissions(context: android.content.Context): Boolean =
    blePermissions().all {
        ContextCompat.checkSelfPermission(context, it) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onDeviceSelected: () -> Unit
) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) viewModel.startScan()
    }

    LaunchedEffect(Unit) {
        if (hasBlePermissions(context)) viewModel.startScan()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScannerHeader(scanning = scanning, deviceCount = devices.size)

        AnimatedVisibility(
            visible = !hasBlePermissions(context),
            enter = fadeIn() + slideInVertically { -it / 6 }
        ) {
            GlassSurface(Modifier.fillMaxWidth(), glowColor = MaterialTheme.colorScheme.tertiary) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.permissions_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.permissions_text) +
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) stringResource(R.string.permissions_text_legacy) else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    GlassButton(
                        onClick = { permissionLauncher.launch(blePermissions()) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.permissions_action), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (hasBlePermissions(context)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassOutlineButton(
                    onClick = if (scanning) viewModel::stopScan else viewModel::startScan,
                    modifier = Modifier.weight(1f),
                    compact = true
                ) {
                    if (scanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.scan_stop), color = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.scan_again), color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (scanning) {
                    Text(
                        "BLE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        error?.let {
            GlassSurface(Modifier.fillMaxWidth(), glowColor = MaterialTheme.colorScheme.error) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                    Text(it, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }

        if (devices.isEmpty() && !scanning && hasBlePermissions(context)) {
            GlassSurface(Modifier.fillMaxWidth(), glowColor = MaterialTheme.colorScheme.onSurfaceVariant) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier.size(58.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.BluetoothDisabled, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.no_devices_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.no_devices_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(devices, key = { it.address }) { device ->
                DeviceRow(device) { viewModel.connect(device, onDeviceSelected) }
            }
        }
    }
}

@Composable
private fun ScannerHeader(scanning: Boolean, deviceCount: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(50.dp).background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                RoundedCornerShape(16.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            if (scanning) {
                Icon(
                    Icons.AutoMirrored.Filled.BluetoothSearching,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(27.dp)
                )
            } else {
                BleGlyph(color = MaterialTheme.colorScheme.primary, iconSize = 24.dp, labelSize = 9.sp)
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.header_link_devices),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(stringResource(R.string.devices_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                if (scanning) stringResource(R.string.devices_scanning) else stringResource(R.string.devices_found, deviceCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeviceRow(device: BleManager.ScanDevice, onClick: () -> Unit) {
    val accent = if (device.looksLikeVesc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        glowColor = accent,
        onClick = onClick
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(
                    if (device.looksLikeVesc) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                if (device.looksLikeVesc) {
                    BleGlyph(color = accent, iconSize = 20.dp, labelSize = 8.sp)
                } else {
                    Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        device.name ?: stringResource(R.string.device_unnamed),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (device.looksLikeVesc) accent else MaterialTheme.colorScheme.onSurface
                    )
                    if (device.looksLikeVesc) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 5.dp).size(16.dp))
                    }
                }
                Text(
                    device.address,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (device.looksLikeVesc) {
                    Text(stringResource(R.string.device_vesc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            RssiIndicator(device.rssi, showValue = true)
        }
    }
}
