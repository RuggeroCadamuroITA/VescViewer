package com.ruggerocadamuro.myapplication.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Modalita' tema: chiaro / scuro / segue il sistema.
 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** Stile delle gauge: lancetta analogica oppure numero grande digitale. */
enum class GaugeStyle { ANALOG, DIGITAL }

enum class SpeedUnit { KMH, MPH }

enum class TempUnit { CELSIUS, FAHRENHEIT }

/**
 * Variante di icona app: DEFAULT = activity principale, V1/V2 = activity-alias
 * abilitati via PackageManager (vedi SettingsViewModel.applyIconVariant).
 */
enum class IconVariant { DEFAULT, V1, V2 }

/**
 * Snapshot di tutte le impostazioni utente.
 * Persistite in DataStore Preferences, applicate a caldo senza restart.
 */
data class AppSettings(
    val accentColorIndex: Int = -1,          // -1 = colore di default / dinamico
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val gaugeStyle: GaugeStyle = GaugeStyle.ANALOG,
    val iconVariant: IconVariant = IconVariant.DEFAULT,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    // parametri veicolo (per convertire ERPM -> velocita' e tachimetro -> distanza)
    val polePairs: Int = 7,                  // coppie polari del motore
    val wheelDiameterCm: Float = 25.4f,      // diametro ruota in cm (10")
    val gearRatio: Float = 1.0f,             // rapporto di riduzione motore/ruota
    val batteryCells: Int = 10,              // celle in serie (es. 10S)
    // allarme anti-allontanamento
    val alarmEnabled: Boolean = false,
    val alarmThresholdDbm: Int = -75,        // sotto questa soglia -> allarme
    val alarmDebounceSeconds: Int = 5,       // secondi consecutivi sotto soglia
    val alarmAutoStopOnReturn: Boolean = true,
    val alarmOnDisconnect: Boolean = true,   // tratta la disconnessione improvvisa come allarme
    // ultimo dispositivo collegato (per la riconnessione rapida)
    val lastDeviceAddress: String = "",
    val lastDeviceName: String = ""
)

/**
 * Repository DataStore (Preferences): unica fonte di verita' delle impostazioni.
 * Le schermate raccolgono [settings] come Flow e si aggiornano a caldo.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val ACCENT = intPreferencesKey("accent_color_index")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val GAUGE_STYLE = stringPreferencesKey("gauge_style")
        val ICON_VARIANT = stringPreferencesKey("icon_variant")
        val SPEED_UNIT = stringPreferencesKey("speed_unit")
        val TEMP_UNIT = stringPreferencesKey("temp_unit")
        val POLE_PAIRS = intPreferencesKey("pole_pairs")
        val WHEEL_CM = floatPreferencesKey("wheel_diameter_cm")
        val GEAR_RATIO = floatPreferencesKey("gear_ratio")
        val BATTERY_CELLS = intPreferencesKey("battery_cells")
        val ALARM_ENABLED = booleanPreferencesKey("alarm_enabled")
        val ALARM_THRESHOLD = intPreferencesKey("alarm_threshold_dbm")
        val ALARM_DEBOUNCE = intPreferencesKey("alarm_debounce_seconds")
        val ALARM_AUTOSTOP = booleanPreferencesKey("alarm_autostop_on_return")
        val ALARM_ON_DISCONNECT = booleanPreferencesKey("alarm_on_disconnect")
        val LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
        val LAST_DEVICE_NAME = stringPreferencesKey("last_device_name")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            accentColorIndex = prefs[Keys.ACCENT] ?: -1,
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            gaugeStyle = prefs[Keys.GAUGE_STYLE]?.let { runCatching { GaugeStyle.valueOf(it) }.getOrNull() }
                ?: GaugeStyle.ANALOG,
            iconVariant = prefs[Keys.ICON_VARIANT]?.let { runCatching { IconVariant.valueOf(it) }.getOrNull() }
                ?: IconVariant.DEFAULT,
            speedUnit = prefs[Keys.SPEED_UNIT]?.let { runCatching { SpeedUnit.valueOf(it) }.getOrNull() }
                ?: SpeedUnit.KMH,
            tempUnit = prefs[Keys.TEMP_UNIT]?.let { runCatching { TempUnit.valueOf(it) }.getOrNull() }
                ?: TempUnit.CELSIUS,
            polePairs = (prefs[Keys.POLE_PAIRS] ?: 7).coerceIn(1, 30),
            wheelDiameterCm = (prefs[Keys.WHEEL_CM] ?: 25.4f).coerceIn(5f, 200f),
            gearRatio = (prefs[Keys.GEAR_RATIO] ?: 1f).coerceIn(0.1f, 50f),
            batteryCells = (prefs[Keys.BATTERY_CELLS] ?: 10).coerceIn(2, 20),
            alarmEnabled = prefs[Keys.ALARM_ENABLED] ?: false,
            alarmThresholdDbm = (prefs[Keys.ALARM_THRESHOLD] ?: -75).coerceIn(-100, -40),
            alarmDebounceSeconds = (prefs[Keys.ALARM_DEBOUNCE] ?: 5).coerceIn(1, 60),
            alarmAutoStopOnReturn = prefs[Keys.ALARM_AUTOSTOP] ?: true,
            alarmOnDisconnect = prefs[Keys.ALARM_ON_DISCONNECT] ?: true,
            lastDeviceAddress = prefs[Keys.LAST_DEVICE_ADDRESS] ?: "",
            lastDeviceName = prefs[Keys.LAST_DEVICE_NAME] ?: ""
        )
    }

    suspend fun setAccentColorIndex(index: Int) =
        edit { it[Keys.ACCENT] = index }

    suspend fun setThemeMode(mode: ThemeMode) =
        edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setGaugeStyle(style: GaugeStyle) =
        edit { it[Keys.GAUGE_STYLE] = style.name }

    suspend fun setIconVariant(variant: IconVariant) =
        edit { it[Keys.ICON_VARIANT] = variant.name }

    suspend fun setSpeedUnit(unit: SpeedUnit) =
        edit { it[Keys.SPEED_UNIT] = unit.name }

    suspend fun setTempUnit(unit: TempUnit) =
        edit { it[Keys.TEMP_UNIT] = unit.name }

    suspend fun setPolePairs(value: Int) =
        edit { it[Keys.POLE_PAIRS] = value.coerceIn(1, 30) }

    suspend fun setWheelDiameterCm(value: Float) =
        edit { it[Keys.WHEEL_CM] = value.coerceIn(5f, 200f) }

    suspend fun setGearRatio(value: Float) =
        edit { it[Keys.GEAR_RATIO] = value.coerceIn(0.1f, 50f) }

    suspend fun setBatteryCells(value: Int) =
        edit { it[Keys.BATTERY_CELLS] = value.coerceIn(2, 20) }

    suspend fun setAlarmEnabled(enabled: Boolean) =
        edit { it[Keys.ALARM_ENABLED] = enabled }

    suspend fun setAlarmThresholdDbm(value: Int) =
        edit { it[Keys.ALARM_THRESHOLD] = value.coerceIn(-100, -40) }

    suspend fun setAlarmDebounceSeconds(value: Int) =
        edit { it[Keys.ALARM_DEBOUNCE] = value.coerceIn(1, 60) }

    suspend fun setAlarmAutoStopOnReturn(value: Boolean) =
        edit { it[Keys.ALARM_AUTOSTOP] = value }

    suspend fun setAlarmOnDisconnect(value: Boolean) =
        edit { it[Keys.ALARM_ON_DISCONNECT] = value }

    suspend fun setLastDevice(address: String, name: String) = edit {
        it[Keys.LAST_DEVICE_ADDRESS] = address
        it[Keys.LAST_DEVICE_NAME] = name
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vesc_companion_settings")
