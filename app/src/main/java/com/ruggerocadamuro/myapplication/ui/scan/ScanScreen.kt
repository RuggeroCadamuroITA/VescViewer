package com.ruggerocadamuro.myapplication.ui.scan

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ruggerocadamuro.myapplication.data.ble.BleManager
import com.ruggerocadamuro.myapplication.ui.components.RssiIndicator

/** Permessi necessari per fare scan/connect BLE su questa versione di Android. */
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

/**
 * Schermata di scansione/pairing: elenco live dei dispositivi BLE nelle
 * vicinanze, con i bridge "probabilmente VESC" (NUS, NRF, Flipsky, HM-10)
 * evidenziati in cima. Nessun dispositivo in archivio: la prima connessione
 * parte sempre da qui.
 */
@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onDeviceSelected: () -> Unit
) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    val error by viewModel.error.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) viewModel.startScan()
    }

    // avvio automatico: se i permessi ci sono gia', scansiona subito
    LaunchedEffect(Unit) {
        if (hasBlePermissions(context)) viewModel.startScan()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (scanning) Icons.AutoMirrored.Filled.BluetoothSearching else Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Trova il tuo VESC", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (scanning) "Ricerca dispositivi in corso..." else "${devices.size} dispositivi trovati",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (!hasBlePermissions(context)) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Permessi Bluetooth richiesti", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Per cercare e collegare il modulo BLE del VESC serve l'accesso al Bluetooth" +
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
                            " e alla posizione (richiesto da Android fino alla versione 11 per lo scan BLE)."
                            else ".",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Button(onClick = { permissionLauncher.launch(blePermissions()) }) {
                        Text("Concedi i permessi")
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (scanning) {
                    OutlinedButton(onClick = { viewModel.stopScan() }) { Text("Ferma ricerca") }
                } else {
                    Button(onClick = { viewModel.startScan() }) { Text("Cerca di nuovo") }
                }
                if (scanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(24.dp).height(24.dp).align(Alignment.CenterVertically)
                    )
                }
            }
        }

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (devices.isEmpty() && !scanning && hasBlePermissions(context)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 32.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.BluetoothDisabled, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Nessun dispositivo. Assicurati che il modulo BLE del VESC sia alimentato e in coppia.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices, key = { it.address }) { device ->
                DeviceRow(device) { viewModel.connect(device, onDeviceSelected) }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: BleManager.ScanDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    device.name ?: "(senza nome)",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (device.looksLikeVesc) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    device.address,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (device.looksLikeVesc) {
                    Text(
                        "Bridge VESC probabile",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            RssiIndicator(device.rssi)
        }
    }
}
