package com.ruggerocadamuro.myapplication.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ruggerocadamuro.myapplication.ServiceLocator
import com.ruggerocadamuro.myapplication.data.HistoryPoint
import com.ruggerocadamuro.myapplication.data.VescRepository
import com.ruggerocadamuro.myapplication.data.ble.BleManager
import com.ruggerocadamuro.myapplication.data.settings.AppSettings
import com.ruggerocadamuro.myapplication.data.vesc.VescTelemetry
import com.ruggerocadamuro.myapplication.service.AlarmForegroundService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel della dashboard: espone i flussi del [VescRepository] (telemetria,
 * connessione, RSSI, allarme) e le azioni utente (connetti/disconnetti,
 * allarme on/off).
 */
class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.vescRepository
    private val settingsRepo = ServiceLocator.settingsRepository

    val connectionState: StateFlow<BleManager.ConnectionState> = repo.connectionState
    val telemetry: StateFlow<VescTelemetry?> = repo.telemetry
    val history: StateFlow<List<HistoryPoint>> = repo.history
    val rssi: StateFlow<Int?> = repo.rssi
    val deviceName: StateFlow<String?> = repo.deviceName
    val alarmState: StateFlow<VescRepository.AlarmUiState> = repo.alarmState
    val events = repo.events

    /** Impostazioni condivise (gauge, unita', parametri allarme). */
    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val lastDeviceAddress: String get() = settings.value.lastDeviceAddress
    val lastDeviceName: String get() = settings.value.lastDeviceName

    fun connect(address: String, name: String?) = repo.connect(address, name)

    fun disconnect() = repo.disconnect()

    /**
     * Toggle allarme. L'abilitazione avvia prima il foreground service
     * (serve su Android 12+ per poter acquisire il tipo connectedDevice a
     * processo vivo in foreground) e poi il monitoraggio nel repository.
     */
    fun setAlarmEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        if (enabled) {
            AlarmForegroundService.start(context)
            repo.enableAlarm()
        } else {
            repo.disableAlarm()
            AlarmForegroundService.stop(context)
        }
        viewModelScope.launch { settingsRepo.setAlarmEnabled(enabled) }
    }
}
