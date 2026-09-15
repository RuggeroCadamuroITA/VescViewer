package com.ruggerocadamuro.myapplication.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ruggerocadamuro.myapplication.ServiceLocator
import com.ruggerocadamuro.myapplication.data.settings.AppLanguage
import com.ruggerocadamuro.myapplication.data.settings.AppLocale
import com.ruggerocadamuro.myapplication.data.settings.AppSettings
import com.ruggerocadamuro.myapplication.data.settings.GaugeStyle
import com.ruggerocadamuro.myapplication.data.settings.SettingsRepository
import com.ruggerocadamuro.myapplication.data.settings.SpeedUnit
import com.ruggerocadamuro.myapplication.data.settings.TempUnit
import com.ruggerocadamuro.myapplication.data.settings.ThemeMode
import com.ruggerocadamuro.myapplication.service.AlarmForegroundService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel delle impostazioni: espone lo snapshot corrente come StateFlow e
 * i setter che persistono su DataStore. Tutte le modifiche sono applicate
 * "a caldo": chi osserva [settings] (tema, gauge, unita') si aggiorna subito,
 * senza restart dell'app.
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: SettingsRepository = ServiceLocator.settingsRepository

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /**
     * null finche' DataStore non ha risposto: serve a non mostrare per un
     * istante il setup guidato a chi l'ha gia' completato.
     */
    val setupCompleted: StateFlow<Boolean?> = repo.settings
        .map { it.setupCompleted }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Lingua chiara al primo setup guidato: finche' e' null l'app la chiede. */
    fun setLanguage(language: AppLanguage) {
        AppLocale.apply(getApplication(), language)
        viewModelScope.launch { repo.setLanguage(language) }
    }

    /** Chiude il setup iniziale: l'app parte direttamente sulla dashboard. */
    fun completeSetup() = viewModelScope.launch { repo.setSetupCompleted(true) }

    fun setAccentColor(index: Int) = viewModelScope.launch { repo.setAccentColorIndex(index) }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }

    fun setGaugeStyle(style: GaugeStyle) = viewModelScope.launch { repo.setGaugeStyle(style) }

    fun setSpeedUnit(unit: SpeedUnit) = viewModelScope.launch { repo.setSpeedUnit(unit) }

    fun setTempUnit(unit: TempUnit) = viewModelScope.launch { repo.setTempUnit(unit) }

    fun setAuthTimeoutMinutes(value: Int) = viewModelScope.launch { repo.setAuthTimeoutMinutes(value) }

    fun setPolePairs(value: Int) = viewModelScope.launch { repo.setPolePairs(value) }

    fun setWheelDiameterCm(value: Float) = viewModelScope.launch { repo.setWheelDiameterCm(value) }

    fun setGearRatio(value: Float) = viewModelScope.launch { repo.setGearRatio(value) }

    fun setBatteryCells(value: Int) = viewModelScope.launch { repo.setBatteryCells(value) }

    /**
     * Accende/spegne la protezione anti-allontanamento: avvia (o ferma) il
     * foreground service che tiene vivo il monitoraggio RSSI a schermo spento
     * e persiste la scelta, cosi' al prossimo avvio l'app la ripristina.
     */
    fun setAlarmEnabled(enabled: Boolean) {
        AlarmForegroundService.setEnabled(getApplication(), enabled)
        viewModelScope.launch { repo.setAlarmEnabled(enabled) }
    }

    fun setAlarmThresholdDbm(value: Int) = viewModelScope.launch { repo.setAlarmThresholdDbm(value) }

    fun setAlarmDebounceSeconds(value: Int) = viewModelScope.launch { repo.setAlarmDebounceSeconds(value) }

    fun setAlarmAutoStop(value: Boolean) = viewModelScope.launch { repo.setAlarmAutoStopOnReturn(value) }

    fun setAlarmOnDisconnect(value: Boolean) = viewModelScope.launch { repo.setAlarmOnDisconnect(value) }
    fun setLowBatteryAlertEnabled(value: Boolean) = viewModelScope.launch { repo.setLowBatteryAlertEnabled(value) }
    fun setLowBatteryAlertPercent(value: Int) = viewModelScope.launch { repo.setLowBatteryAlertPercent(value) }
    fun setHighTemperatureAlertEnabled(value: Boolean) = viewModelScope.launch { repo.setHighTemperatureAlertEnabled(value) }
    fun setHighTemperatureAlertC(value: Float) = viewModelScope.launch { repo.setHighTemperatureAlertC(value) }
    fun setHighCurrentAlertEnabled(value: Boolean) = viewModelScope.launch { repo.setHighCurrentAlertEnabled(value) }
    fun setHighCurrentAlertA(value: Float) = viewModelScope.launch { repo.setHighCurrentAlertA(value) }
    fun setLowVoltageAlertEnabled(value: Boolean) = viewModelScope.launch { repo.setLowVoltageAlertEnabled(value) }
    fun setLowVoltageAlertV(value: Float) = viewModelScope.launch { repo.setLowVoltageAlertV(value) }

}
