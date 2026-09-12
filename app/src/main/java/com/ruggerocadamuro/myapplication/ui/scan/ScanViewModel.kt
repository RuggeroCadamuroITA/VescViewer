package com.ruggerocadamuro.myapplication.ui.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ruggerocadamuro.myapplication.R
import com.ruggerocadamuro.myapplication.ServiceLocator
import com.ruggerocadamuro.myapplication.data.ble.BleManager
import com.ruggerocadamuro.myapplication.data.settings.AppLocale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel della scansione BLE: mantiene la lista dispositivi deduplicata
 * (per indirizzo), il piu' recente RSSI, e la gestione dello scan.
 */
class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val ble: BleManager = ServiceLocator.vescRepository.ble
    private val settingsRepo = ServiceLocator.settingsRepository

    private val _devices = MutableStateFlow<List<BleManager.ScanDevice>>(emptyList())
    val devices: StateFlow<List<BleManager.ScanDevice>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        if (_scanning.value) return
        _error.value = null
        _devices.value = emptyList()
        scanJob = viewModelScope.launch {
            _scanning.value = true
            try {
                ble.scan().collect { device ->
                    // deduplica per indirizzo, tenendo l'RSSi piu' fresco
                    val current = _devices.value
                        .filterNot { it.address == device.address }
                    _devices.value = (current + device)
                        .sortedWith(compareByDescending<BleManager.ScanDevice> { it.looksLikeVesc }
                            .thenByDescending { it.rssi })
                }
            } catch (e: Exception) {
                _error.value = e.message
                    ?: AppLocale.wrap(getApplication()).getString(R.string.scan_error)
            } finally {
                _scanning.value = false
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _scanning.value = false
    }

    /** Connette al dispositivo scelto e ne salva l'indirizzo come "ultimo usato". */
    fun connect(device: BleManager.ScanDevice, onConnected: () -> Unit) {
        stopScan()
        ServiceLocator.vescRepository.connect(device.address, device.name)
        viewModelScope.launch {
            settingsRepo.setLastDevice(device.address, device.name ?: device.address)
        }
        onConnected()
    }
}
