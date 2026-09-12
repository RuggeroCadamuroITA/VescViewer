package com.ruggerocadamuro.myapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ruggerocadamuro.myapplication.data.settings.AppLocale
import com.ruggerocadamuro.myapplication.ui.auth.AuthScreen
import com.ruggerocadamuro.myapplication.ui.components.BleGlyph
import com.ruggerocadamuro.myapplication.ui.dashboard.DashboardScreen
import com.ruggerocadamuro.myapplication.ui.dashboard.DashboardViewModel
import com.ruggerocadamuro.myapplication.ui.history.HistoryScreen
import com.ruggerocadamuro.myapplication.ui.map.RideMapScreen
import com.ruggerocadamuro.myapplication.ui.scan.ScanScreen
import com.ruggerocadamuro.myapplication.ui.recording.RecordingViewModel
import com.ruggerocadamuro.myapplication.ui.scan.ScanViewModel
import com.ruggerocadamuro.myapplication.ui.scan.blePermissions
import com.ruggerocadamuro.myapplication.ui.settings.SettingsScreen
import com.ruggerocadamuro.myapplication.ui.settings.SettingsViewModel
import com.ruggerocadamuro.myapplication.ui.setup.SetupScreen
import com.ruggerocadamuro.myapplication.ui.theme.MyApplicationTheme

class MainActivity : FragmentActivity() {

    /**
     * Prima ancora di creare la UI il Context viene avvolto con la lingua
     * scelta dall'utente, cosi' ogni `stringResource` legge le risorse giuste
     * (su Android 13+ la stessa lingua e' gestita anche dal sistema per-app).
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val settings by settingsViewModel.settings.collectAsState()
            val setupCompleted by settingsViewModel.setupCompleted.collectAsState()
            val authUnlocked by ServiceLocator.authManager.unlocked.collectAsState()
            MyApplicationTheme(
                themeMode = settings.themeMode,
                accentColorIndex = settings.accentColorIndex
            ) {
                when {
                    // DataStore non ha ancora risposto: meglio un istante di
                    // attesa che far lampeggiare il setup a chi l'ha gia' fatto.
                    setupCompleted == null -> SetupLoading()
                    setupCompleted == false -> SetupScreen(settingsViewModel)
                    !ServiceLocator.authManager.hasPin() -> AuthScreen(
                        setupMode = true,
                        onAuthenticated = { }
                    )
                    !authUnlocked -> AuthScreen(
                        setupMode = false,
                        onAuthenticated = { }
                    )
                    else -> MainApp()
                }
            }
        }
    }
}

/** Attesa breve mentre si legge se il setup iniziale e' gia' stato completato. */
@Composable
private fun SetupLoading() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

/** Navigazione interna a 3 schede (stato semplice, nessuna libreria extra). */
private enum class Tab { DASHBOARD, SCAN, HISTORY, SETTINGS }

private const val PERMISSIONS_REQUEST_CODE = 4201

@Composable
private fun MainApp() {
    var tab by remember { mutableIntStateOf(Tab.DASHBOARD.ordinal) }
    val context = LocalContext.current
    val dashboardViewModel: DashboardViewModel = viewModel()
    val scanViewModel: ScanViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val recordingViewModel: RecordingViewModel = viewModel()
    var mapSessionId by remember { mutableStateOf<Long?>(null) }

    // ---------------------------------------------------------------
    // Permessi runtime:
    //  - Android 12+ (API 31+): BLUETOOTH_SCAN + BLUETOOTH_CONNECT
    //  - Android < 12: ACCESS_FINE_LOCATION (obbligatorio per lo scan BLE)
    //  - Android 13+ (API 33+): POST_NOTIFICATIONS per la notifica del
    //    foreground service dell'allarme
    // ---------------------------------------------------------------
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // Request permissions through FragmentActivity with a fixed request code.
        // ActivityResultRegistry can generate a request code outside the 16-bit
        // range expected by FragmentActivity on some AndroidX combinations.
        val requested = buildList {
            addAll(blePermissions().toList())
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.distinct().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (requested.isNotEmpty() && context is MainActivity) {
            context.requestPermissions(requested.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    val screen: @Composable () -> Unit = screen@{
        if (mapSessionId != null) {
            RideMapScreen(
                sessionId = mapSessionId!!,
                onBack = { mapSessionId = null }
            )
            return@screen
        }
        when (Tab.entries[tab]) {
            Tab.DASHBOARD -> DashboardScreen(
                dashboardViewModel,
                onGoToScan = { tab = Tab.SCAN.ordinal },
                recordingViewModel = recordingViewModel
            )
            Tab.SCAN -> ScanScreen(scanViewModel, onDeviceSelected = { tab = Tab.DASHBOARD.ordinal })
            Tab.HISTORY -> HistoryScreen(onOpenMap = { mapSessionId = it })
            Tab.SETTINGS -> SettingsScreen(settingsViewModel)
        }
    }

    if (isLandscape) {
        // Telefono ruotato: la barra di navigazione in basso ruba preziose
        // righe di altezza alla plancia, quindi diventa una rail laterale.
        Scaffold { padding ->
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    // Le prime due voci stanno in alto e Impostazioni in fondo:
                    // a meta' del bordo sinistro (dove stanno le voci centrali)
                    // in orizzontale cade il foro della fotocamera frontale, che
                    // coprirebbe l'ultima icona. Il centro resta libero.
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Spacer(Modifier.height(10.dp))
                        NavigationRailItem(
                            selected = tab == Tab.DASHBOARD.ordinal,
                            onClick = { tab = Tab.DASHBOARD.ordinal },
                            icon = { Icon(Icons.Filled.Speed, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_dashboard)) }
                        )
                        NavigationRailItem(
                            selected = tab == Tab.SCAN.ordinal,
                            onClick = { tab = Tab.SCAN.ordinal },
                            icon = { BleGlyph(iconSize = 20.dp, labelSize = 8.sp) },
                            label = { Text(stringResource(R.string.nav_devices)) }
                        )
                        Spacer(Modifier.weight(1f))
                        NavigationRailItem(
                            selected = tab == Tab.HISTORY.ordinal,
                            onClick = { tab = Tab.HISTORY.ordinal },
                            icon = { Icon(Icons.Filled.History, contentDescription = null) },
                            label = { Text("Uscite") }
                        )
                        Spacer(Modifier.weight(1f))
                        NavigationRailItem(
                            selected = tab == Tab.SETTINGS.ordinal,
                            onClick = { tab = Tab.SETTINGS.ordinal },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_settings)) }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    screen()
                }
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.DASHBOARD.ordinal,
                        onClick = { tab = Tab.DASHBOARD.ordinal },
                        icon = { Icon(Icons.Filled.Speed, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_dashboard)) }
                    )
                    NavigationBarItem(
                        selected = tab == Tab.SCAN.ordinal,
                        onClick = { tab = Tab.SCAN.ordinal },
                        icon = { BleGlyph(iconSize = 20.dp, labelSize = 8.sp) },
                        label = { Text(stringResource(R.string.nav_devices)) }
                    )
                    NavigationBarItem(
                        selected = tab == Tab.HISTORY.ordinal,
                        onClick = { tab = Tab.HISTORY.ordinal },
                        icon = { Icon(Icons.Filled.History, contentDescription = null) },
                        label = { Text("Uscite") }
                    )
                    NavigationBarItem(
                        selected = tab == Tab.SETTINGS.ordinal,
                        onClick = { tab = Tab.SETTINGS.ordinal },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_settings)) }
                    )
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                screen()
            }
        }
    }
}
