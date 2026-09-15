package com.ruggerocadamuro.myapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ruggerocadamuro.myapplication.data.settings.AppLocale
import com.ruggerocadamuro.myapplication.ui.auth.AuthScreen
import com.ruggerocadamuro.myapplication.ui.components.CockpitNavItem
import com.ruggerocadamuro.myapplication.ui.components.CockpitNavigation
import com.ruggerocadamuro.myapplication.ui.dashboard.DashboardScreen
import com.ruggerocadamuro.myapplication.ui.dashboard.DashboardViewModel
import com.ruggerocadamuro.myapplication.ui.history.HistoryScreen
import com.ruggerocadamuro.myapplication.ui.map.RideMapScreen
import com.ruggerocadamuro.myapplication.ui.recording.RecordingViewModel
import com.ruggerocadamuro.myapplication.ui.scan.ScanScreen
import com.ruggerocadamuro.myapplication.ui.scan.ScanViewModel
import com.ruggerocadamuro.myapplication.ui.scan.blePermissions
import com.ruggerocadamuro.myapplication.ui.settings.SettingsScreen
import com.ruggerocadamuro.myapplication.ui.settings.SettingsViewModel
import com.ruggerocadamuro.myapplication.ui.setup.SetupScreen
import com.ruggerocadamuro.myapplication.ui.theme.MyApplicationTheme

class MainActivity : FragmentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onStart() {
        super.onStart()
        ServiceLocator.authManager.markForeground()
    }

    override fun onStop() {
        ServiceLocator.authManager.markBackground()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val setupCompleted by settingsViewModel.setupCompleted.collectAsStateWithLifecycle()
            val authUnlocked by ServiceLocator.authManager.unlocked.collectAsStateWithLifecycle()
            LaunchedEffect(settings.authTimeoutMinutes) {
                ServiceLocator.authManager.configureTimeout(settings.authTimeoutMinutes * 60_000L)
            }
            MyApplicationTheme(
                themeMode = settings.themeMode,
                accentColorIndex = settings.accentColorIndex
            ) {
                when {
                    setupCompleted == null -> SetupLoading()
                    setupCompleted == false -> SetupScreen(settingsViewModel)
                    !ServiceLocator.authManager.hasPin() -> AuthScreen(setupMode = true, onAuthenticated = {})
                    !authUnlocked -> AuthScreen(setupMode = false, onAuthenticated = {})
                    else -> MainApp()
                }
            }
        }
    }
}

@Composable
private fun SetupLoading() {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

private enum class Tab { DASHBOARD, SCAN, HISTORY, SETTINGS }
private const val PERMISSIONS_REQUEST_CODE = 4201

@Composable
private fun MainApp() {
    var tab by rememberSaveable { mutableIntStateOf(Tab.DASHBOARD.ordinal) }
    val context = LocalContext.current
    val dashboardViewModel: DashboardViewModel = viewModel()
    val scanViewModel: ScanViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val recordingViewModel: RecordingViewModel = viewModel()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    var mapSessionId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
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
    val navigationItems = listOf(
        CockpitNavItem(stringResource(R.string.nav_dashboard), Icons.Filled.Speed),
        CockpitNavItem(stringResource(R.string.nav_devices), Icons.Filled.Bluetooth),
        CockpitNavItem(stringResource(R.string.nav_rides), Icons.Filled.History),
        CockpitNavItem(stringResource(R.string.nav_settings), Icons.Filled.Settings)
    )

    val screen: @Composable (Long?, Int) -> Unit = screen@{ sessionId, selectedTab ->
        if (sessionId != null) {
            RideMapScreen(
                sessionId = sessionId,
                settings = settings,
                onBack = { mapSessionId = null }
            )
            return@screen
        }
        when (Tab.entries[selectedTab]) {
            Tab.DASHBOARD -> DashboardScreen(
                viewModel = dashboardViewModel,
                onGoToScan = { tab = Tab.SCAN.ordinal },
                recordingViewModel = recordingViewModel
            )
            Tab.SCAN -> ScanScreen(scanViewModel, onDeviceSelected = { tab = Tab.DASHBOARD.ordinal })
            Tab.HISTORY -> HistoryScreen(onOpenMap = { mapSessionId = it })
            Tab.SETTINGS -> SettingsScreen(settingsViewModel)
        }
    }

    if (isLandscape) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                CockpitNavigation(
                    items = navigationItems,
                    selectedIndex = tab,
                    onSelect = { tab = it },
                    landscape = true
                )
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    AnimatedContent(
                        targetState = mapSessionId to tab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "cockpit_page_transition"
                    ) { target -> screen(target.first, target.second) }
                }
            }
        }
    } else {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                CockpitNavigation(
                    items = navigationItems,
                    selectedIndex = tab,
                    onSelect = { tab = it },
                    landscape = false
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AnimatedContent(
                    targetState = mapSessionId to tab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "cockpit_page_transition"
                ) { target -> screen(target.first, target.second) }
            }
        }
    }
}
