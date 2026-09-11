package com.ruggerocadamuro.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ruggerocadamuro.myapplication.ui.dashboard.DashboardScreen
import com.ruggerocadamuro.myapplication.ui.dashboard.DashboardViewModel
import com.ruggerocadamuro.myapplication.ui.scan.ScanScreen
import com.ruggerocadamuro.myapplication.ui.scan.ScanViewModel
import com.ruggerocadamuro.myapplication.ui.scan.blePermissions
import com.ruggerocadamuro.myapplication.ui.settings.SettingsScreen
import com.ruggerocadamuro.myapplication.ui.settings.SettingsViewModel
import com.ruggerocadamuro.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val settings by settingsViewModel.settings.collectAsState()
            MyApplicationTheme(
                themeMode = settings.themeMode,
                accentColorIndex = settings.accentColorIndex
            ) {
                MainApp()
            }
        }
    }
}

/** Navigazione interna a 3 schede (stato semplice, nessuna libreria extra). */
private enum class Tab { DASHBOARD, SCAN, SETTINGS }

@Composable
private fun MainApp() {
    var tab by remember { mutableIntStateOf(Tab.DASHBOARD.ordinal) }
    val context = LocalContext.current
    val dashboardViewModel: DashboardViewModel = viewModel()
    val scanViewModel: ScanViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    // ---------------------------------------------------------------
    // Permessi runtime:
    //  - Android 12+ (API 31+): BLUETOOTH_SCAN + BLUETOOTH_CONNECT
    //  - Android < 12: ACCESS_FINE_LOCATION (obbligatorio per lo scan BLE)
    //  - Android 13+ (API 33+): POST_NOTIFICATIONS per la notifica del
    //    foreground service dell'allarme
    // ---------------------------------------------------------------
    val blePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val missingBle = blePermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingBle.isNotEmpty()) {
            blePermissionLauncher.launch(missingBle.toTypedArray())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.DASHBOARD.ordinal,
                    onClick = { tab = Tab.DASHBOARD.ordinal },
                    icon = { Icon(Icons.Filled.Speed, contentDescription = null) },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = tab == Tab.SCAN.ordinal,
                    onClick = { tab = Tab.SCAN.ordinal },
                    icon = { Icon(Icons.Filled.Bluetooth, contentDescription = null) },
                    label = { Text("Dispositivi") }
                )
                NavigationBarItem(
                    selected = tab == Tab.SETTINGS.ordinal,
                    onClick = { tab = Tab.SETTINGS.ordinal },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Impostazioni") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (Tab.entries[tab]) {
                Tab.DASHBOARD -> DashboardScreen(dashboardViewModel, onGoToScan = { tab = Tab.SCAN.ordinal })
                Tab.SCAN -> ScanScreen(scanViewModel, onDeviceSelected = { tab = Tab.DASHBOARD.ordinal })
                Tab.SETTINGS -> SettingsScreen(settingsViewModel)
            }
        }
    }
}
