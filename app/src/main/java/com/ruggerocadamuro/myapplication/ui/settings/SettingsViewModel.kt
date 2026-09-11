package com.ruggerocadamuro.myapplication.ui.settings

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ruggerocadamuro.myapplication.ServiceLocator
import com.ruggerocadamuro.myapplication.data.settings.AppSettings
import com.ruggerocadamuro.myapplication.data.settings.GaugeStyle
import com.ruggerocadamuro.myapplication.data.settings.IconVariant
import com.ruggerocadamuro.myapplication.data.settings.SettingsRepository
import com.ruggerocadamuro.myapplication.data.settings.SpeedUnit
import com.ruggerocadamuro.myapplication.data.settings.TempUnit
import com.ruggerocadamuro.myapplication.data.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    fun setAccentColor(index: Int) = viewModelScope.launch { repo.setAccentColorIndex(index) }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }

    fun setGaugeStyle(style: GaugeStyle) = viewModelScope.launch { repo.setGaugeStyle(style) }

    fun setSpeedUnit(unit: SpeedUnit) = viewModelScope.launch { repo.setSpeedUnit(unit) }

    fun setTempUnit(unit: TempUnit) = viewModelScope.launch { repo.setTempUnit(unit) }

    fun setPolePairs(value: Int) = viewModelScope.launch { repo.setPolePairs(value) }

    fun setWheelDiameterCm(value: Float) = viewModelScope.launch { repo.setWheelDiameterCm(value) }

    fun setGearRatio(value: Float) = viewModelScope.launch { repo.setGearRatio(value) }

    fun setBatteryCells(value: Int) = viewModelScope.launch { repo.setBatteryCells(value) }

    fun setAlarmThresholdDbm(value: Int) = viewModelScope.launch { repo.setAlarmThresholdDbm(value) }

    fun setAlarmDebounceSeconds(value: Int) = viewModelScope.launch { repo.setAlarmDebounceSeconds(value) }

    fun setAlarmAutoStop(value: Boolean) = viewModelScope.launch { repo.setAlarmAutoStopOnReturn(value) }

    fun setAlarmOnDisconnect(value: Boolean) = viewModelScope.launch { repo.setAlarmOnDisconnect(value) }

    /**
     * Cambio icona app tramite activity-alias.
     *
     * Android non consente di sostituire a runtime la risorsa icona di una
     * activity: si dichiarano nel Manifest piu' <activity-alias> (uno per
     * variante, ciascuno con il proprio mipmap) e si abilita/disabilita il
     * componente corrispondente con PackageManager.setComponentEnabledSetting().
     * Il launcher aggiorna l'icona dopo qualche secondo (a volte serve un
     * refresh del launcher). Usiamo DONT_KILL_APP per non interrompere
     * l'esecuzione corrente.
     */
    fun applyIconVariant(variant: IconVariant) {
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                val context = getApplication<Application>()
                val pm = context.packageManager
                val pkg = context.packageName

                fun setState(name: String, enable: Boolean) {
                    val component = ComponentName(pkg, name)
                    val desired = if (enable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    pm.setComponentEnabledSetting(
                        component,
                        desired,
                        PackageManager.DONT_KILL_APP
                    )
                }
                setState("$pkg.MainActivity", variant == IconVariant.DEFAULT)
                setState("$pkg.LauncherAlias1", variant == IconVariant.V1)
                setState("$pkg.LauncherAlias2", variant == IconVariant.V2)
                repo.setIconVariant(variant)
            }
        }
    }
}
