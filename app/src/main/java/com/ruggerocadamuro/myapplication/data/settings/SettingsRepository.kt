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
 * Snapshot di tutte le impostazioni utente.
 * Persistite in DataStore Preferences, applicate a caldo senza restart.
 */
data class AppSettings(
    val language: AppLanguage? = null,       // null = lingua non ancora scelta (setup in corso)
    val setupCompleted: Boolean = false,     // false = mostra il setup guidato obbligatorio
    val accentColorIndex: Int = -1,          // -1 = colore di default / dinamico
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val gaugeStyle: GaugeStyle = GaugeStyle.ANALOG,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    val authTimeoutMinutes: Int = 5,
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
    // avvisi telemetria
    val lowBatteryAlertEnabled: Boolean = true,
    val lowBatteryAlertPercent: Int = 20,
    val highTemperatureAlertEnabled: Boolean = true,
    val highTemperatureAlertC: Float = 75f,
    val highCurrentAlertEnabled: Boolean = false,
    val highCurrentAlertA: Float = 80f,
    val lowVoltageAlertEnabled: Boolean = false,
    val lowVoltageAlertV: Float = 30f,
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
        val LANGUAGE = stringPreferencesKey("language")
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        val ACCENT = intPreferencesKey("accent_color_index")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val GAUGE_STYLE = stringPreferencesKey("gauge_style")
        val SPEED_UNIT = stringPreferencesKey("speed_unit")
        val TEMP_UNIT = stringPreferencesKey("temp_unit")
        val AUTH_TIMEOUT_MINUTES = intPreferencesKey("auth_timeout_minutes")
        val POLE_PAIRS = intPreferencesKey("pole_pairs")
        val WHEEL_CM = floatPreferencesKey("wheel_diameter_cm")
        val GEAR_RATIO = floatPreferencesKey("gear_ratio")
        val BATTERY_CELLS = intPreferencesKey("battery_cells")
        val ALARM_ENABLED = booleanPreferencesKey("alarm_enabled")
        val ALARM_THRESHOLD = intPreferencesKey("alarm_threshold_dbm")
        val ALARM_DEBOUNCE = intPreferencesKey("alarm_debounce_seconds")
        val ALARM_AUTOSTOP = booleanPreferencesKey("alarm_autostop_on_return")
        val ALARM_ON_DISCONNECT = booleanPreferencesKey("alarm_on_disconnect")
        val LOW_BATTERY_ALERT_ENABLED = booleanPreferencesKey("low_battery_alert_enabled")
        val LOW_BATTERY_ALERT_PERCENT = intPreferencesKey("low_battery_alert_percent")
        val HIGH_TEMPERATURE_ALERT_ENABLED = booleanPreferencesKey("high_temperature_alert_enabled")
        val HIGH_TEMPERATURE_ALERT_C = floatPreferencesKey("high_temperature_alert_c")
        val HIGH_CURRENT_ALERT_ENABLED = booleanPreferencesKey("high_current_alert_enabled")
        val HIGH_CURRENT_ALERT_A = floatPreferencesKey("high_current_alert_a")
        val LOW_VOLTAGE_ALERT_ENABLED = booleanPreferencesKey("low_voltage_alert_enabled")
        val LOW_VOLTAGE_ALERT_V = floatPreferencesKey("low_voltage_alert_v")
        val LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
        val LAST_DEVICE_NAME = stringPreferencesKey("last_device_name")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            language = AppLanguage.fromTag(prefs[Keys.LANGUAGE]),
            setupCompleted = prefs[Keys.SETUP_COMPLETED] ?: false,
            accentColorIndex = prefs[Keys.ACCENT] ?: -1,
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            gaugeStyle = prefs[Keys.GAUGE_STYLE]?.let { runCatching { GaugeStyle.valueOf(it) }.getOrNull() }
                ?: GaugeStyle.ANALOG,
            speedUnit = prefs[Keys.SPEED_UNIT]?.let { runCatching { SpeedUnit.valueOf(it) }.getOrNull() }
                ?: SpeedUnit.KMH,
            tempUnit = prefs[Keys.TEMP_UNIT]?.let { runCatching { TempUnit.valueOf(it) }.getOrNull() }
                ?: TempUnit.CELSIUS,
            authTimeoutMinutes = (prefs[Keys.AUTH_TIMEOUT_MINUTES] ?: 5).coerceIn(0, 60),
            polePairs = (prefs[Keys.POLE_PAIRS] ?: 7).coerceIn(1, 30),
            wheelDiameterCm = (prefs[Keys.WHEEL_CM] ?: 25.4f).coerceIn(5f, 200f),
            gearRatio = (prefs[Keys.GEAR_RATIO] ?: 1f).coerceIn(0.1f, 50f),
            batteryCells = (prefs[Keys.BATTERY_CELLS] ?: 10).coerceIn(2, 20),
            alarmEnabled = prefs[Keys.ALARM_ENABLED] ?: false,
            alarmThresholdDbm = (prefs[Keys.ALARM_THRESHOLD] ?: -75).coerceIn(-100, -40),
            alarmDebounceSeconds = (prefs[Keys.ALARM_DEBOUNCE] ?: 5).coerceIn(1, 60),
            alarmAutoStopOnReturn = prefs[Keys.ALARM_AUTOSTOP] ?: true,
            alarmOnDisconnect = prefs[Keys.ALARM_ON_DISCONNECT] ?: true,
            lowBatteryAlertEnabled = prefs[Keys.LOW_BATTERY_ALERT_ENABLED] ?: true,
            lowBatteryAlertPercent = (prefs[Keys.LOW_BATTERY_ALERT_PERCENT] ?: 20).coerceIn(1, 50),
            highTemperatureAlertEnabled = prefs[Keys.HIGH_TEMPERATURE_ALERT_ENABLED] ?: true,
            highTemperatureAlertC = (prefs[Keys.HIGH_TEMPERATURE_ALERT_C] ?: 75f).coerceIn(40f, 120f),
            highCurrentAlertEnabled = prefs[Keys.HIGH_CURRENT_ALERT_ENABLED] ?: false,
            highCurrentAlertA = (prefs[Keys.HIGH_CURRENT_ALERT_A] ?: 80f).coerceIn(1f, 500f),
            lowVoltageAlertEnabled = prefs[Keys.LOW_VOLTAGE_ALERT_ENABLED] ?: false,
            lowVoltageAlertV = (prefs[Keys.LOW_VOLTAGE_ALERT_V] ?: 30f).coerceIn(1f, 100f),
            lastDeviceAddress = prefs[Keys.LAST_DEVICE_ADDRESS] ?: "",
            lastDeviceName = prefs[Keys.LAST_DEVICE_NAME] ?: ""
        )
    }

    /**
     * Salva la lingua scelta. Oltre a DataStore aggiorna la copia sincrona su
     * SharedPreferences, che Activity e Service leggono in attachBaseContext.
     */
    suspend fun setLanguage(language: AppLanguage) {
        AppLocale.store(context, language)
        edit { it[Keys.LANGUAGE] = language.tag }
    }

    /** Il setup guidato e' stato completato: non va piu' riproposto. */
    suspend fun setSetupCompleted(completed: Boolean) =
        edit { it[Keys.SETUP_COMPLETED] = completed }

    suspend fun setAccentColorIndex(index: Int) =
        edit { it[Keys.ACCENT] = index }

    suspend fun setThemeMode(mode: ThemeMode) =
        edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setGaugeStyle(style: GaugeStyle) =
        edit { it[Keys.GAUGE_STYLE] = style.name }

    suspend fun setSpeedUnit(unit: SpeedUnit) =
        edit { it[Keys.SPEED_UNIT] = unit.name }

    suspend fun setTempUnit(unit: TempUnit) =
        edit { it[Keys.TEMP_UNIT] = unit.name }

    suspend fun setAuthTimeoutMinutes(value: Int) =
        edit { it[Keys.AUTH_TIMEOUT_MINUTES] = value.coerceIn(0, 60) }

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

    suspend fun setLowBatteryAlertEnabled(value: Boolean) = edit { it[Keys.LOW_BATTERY_ALERT_ENABLED] = value }
    suspend fun setLowBatteryAlertPercent(value: Int) = edit { it[Keys.LOW_BATTERY_ALERT_PERCENT] = value.coerceIn(1, 50) }
    suspend fun setHighTemperatureAlertEnabled(value: Boolean) = edit { it[Keys.HIGH_TEMPERATURE_ALERT_ENABLED] = value }
    suspend fun setHighTemperatureAlertC(value: Float) = edit { it[Keys.HIGH_TEMPERATURE_ALERT_C] = value.coerceIn(40f, 120f) }
    suspend fun setHighCurrentAlertEnabled(value: Boolean) = edit { it[Keys.HIGH_CURRENT_ALERT_ENABLED] = value }
    suspend fun setHighCurrentAlertA(value: Float) = edit { it[Keys.HIGH_CURRENT_ALERT_A] = value.coerceIn(1f, 500f) }
    suspend fun setLowVoltageAlertEnabled(value: Boolean) = edit { it[Keys.LOW_VOLTAGE_ALERT_ENABLED] = value }
    suspend fun setLowVoltageAlertV(value: Float) = edit { it[Keys.LOW_VOLTAGE_ALERT_V] = value.coerceIn(1f, 100f) }

    suspend fun setLastDevice(address: String, name: String) = edit {
        it[Keys.LAST_DEVICE_ADDRESS] = address
        it[Keys.LAST_DEVICE_NAME] = name
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vesc_companion_settings")
