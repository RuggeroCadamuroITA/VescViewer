package com.ruggerocadamuro.myapplication.data

import android.content.Context
import android.util.Log
import com.ruggerocadamuro.myapplication.R
import com.ruggerocadamuro.myapplication.data.ble.BleManager
import com.ruggerocadamuro.myapplication.data.settings.AppLocale
import com.ruggerocadamuro.myapplication.data.settings.AppSettings
import com.ruggerocadamuro.myapplication.data.settings.SettingsRepository
import com.ruggerocadamuro.myapplication.data.settings.SpeedUnit
import com.ruggerocadamuro.myapplication.data.settings.TempUnit
import com.ruggerocadamuro.myapplication.data.vesc.VescPacket
import com.ruggerocadamuro.myapplication.data.vesc.VescTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt

object VescMath {
    fun speedKmh(t: VescTelemetry, p: VehicleParams): Float {
        if (!t.erpm.isFinite() || p.polePairs <= 0 || !p.gearRatio.isFinite() || p.gearRatio <= 0f ||
            !p.wheelDiameterCm.isFinite() || p.wheelDiameterCm <= 0f
        ) return 0f
        val mechRpm = t.erpm / p.polePairs / p.gearRatio
        val wheelCircM = p.wheelDiameterCm / 100f * PI.toFloat()
        return (mechRpm * wheelCircM * 60f / 1000f).takeIf(Float::isFinite) ?: 0f
    }

    fun distanceKm(t: VescTelemetry, p: VehicleParams): Float {
        if (p.gearRatio <= 0f || !p.gearRatio.isFinite() || !p.wheelDiameterCm.isFinite() ||
            p.wheelDiameterCm <= 0f || t.tachometerAbs < 0
        ) return 0f
        val motorRevs = t.tachometerAbs / 3f
        val wheelRevs = motorRevs / p.gearRatio
        val wheelCircM = p.wheelDiameterCm / 100f * PI.toFloat()
        return (wheelRevs * wheelCircM / 1000f).takeIf(Float::isFinite) ?: 0f
    }

    fun batteryPercent(voltage: Float, cells: Int): Int {
        if (!voltage.isFinite() || voltage <= 0f || cells <= 0) return 0
        val perCell = voltage / cells
        return (((perCell - 3.3f) / (4.2f - 3.3f) * 100f).coerceIn(0f, 100f)).roundToInt()
    }

    fun displaySpeedKmh(speedKmh: Float, unit: SpeedUnit): Float =
        if (unit == SpeedUnit.MPH) speedKmh * 0.621371f else speedKmh

    fun displayTemperatureCelsius(tempC: Float, unit: TempUnit): Float =
        if (unit == TempUnit.FAHRENHEIT) tempC * 9f / 5f + 32f else tempC

    fun displayDistanceKm(distanceKm: Float, unit: SpeedUnit): Float =
        if (unit == SpeedUnit.MPH) distanceKm * 0.621371f else distanceKm

}

data class VehicleParams(
    val polePairs: Int,
    val wheelDiameterCm: Float,
    val gearRatio: Float,
    val batteryCells: Int
)

data class HistoryPoint(val timestampMs: Long, val powerW: Float, val erpm: Float)

/** BLE-only telemetry repository. Bluetooth Classic/SPP is intentionally unsupported. */
class VescRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val POLL_PERIOD_MS = 250L
        private const val REQUEST_TIMEOUT_MS = 500L
        private const val ALARM_TICK_MS = 1000L
        private const val HISTORY_WINDOW_MS = 60_000L
    }

    val ble = BleManager(context)
    private val _connectionState = MutableStateFlow(BleManager.ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleManager.ConnectionState> = _connectionState.asStateFlow()
    private val _rssi = MutableStateFlow<Int?>(null)
    val rssi: StateFlow<Int?> = _rssi.asStateFlow()
    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _telemetry = MutableStateFlow<VescTelemetry?>(null)
    val telemetry: StateFlow<VescTelemetry?> = _telemetry.asStateFlow()
    private val _history = MutableStateFlow<List<HistoryPoint>>(emptyList())
    val history: StateFlow<List<HistoryPoint>> = _history.asStateFlow()
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()
    private fun localized(): Context = AppLocale.wrap(context)

    enum class AlarmStatus { OFF, MONITORING, ALARM }
    data class AlarmUiState(
        val status: AlarmStatus = AlarmStatus.OFF,
        val rssi: Int? = null,
        val thresholdDbm: Int = -75,
        val belowSeconds: Int = 0,
        val debounceSeconds: Int = 5,
        val marginDb: Int = 0
    )

    private val _alarmState = MutableStateFlow(AlarmUiState())
    val alarmState: StateFlow<AlarmUiState> = _alarmState.asStateFlow()
    private var pollingJob: Job? = null
    private var alarmJob: Job? = null
    @Volatile private var currentSettings: AppSettings = AppSettings()
    @Volatile private var vehicle = VehicleParams(7, 25.4f, 1f, 10)
    @Volatile private var hasEverConnected = false
    @Volatile private var intentionalDisconnect = false
    enum class CommandPath { UNKNOWN, DIRECT, CAN }

    private var lastConnectedAddress: String? = null
    private var commandPath = CommandPath.UNKNOWN
    private var canId: Int? = null
    private var useSelectiveForPoll = true
    @Volatile private var consecutivePollFailures = 0
    private val alertEngine = TelemetryAlertEngine()
    private var lastAlertSettings = AppSettings()
    private val _telemetryAlerts = MutableSharedFlow<TelemetryAlertType>(extraBufferCapacity = 8)
    val telemetryAlerts: SharedFlow<TelemetryAlertType> = _telemetryAlerts.asSharedFlow()


    init {
        scope.launch {
            settingsRepository.settings.collect { s ->
                currentSettings = s
                vehicle = VehicleParams(s.polePairs, s.wheelDiameterCm, s.gearRatio, s.batteryCells)
                if (s != lastAlertSettings) {
                    alertEngine.reset()
                    lastAlertSettings = s
                }
            }
        }
        scope.launch { ble.state.collect { state ->
            _connectionState.value = state
            if (state == BleManager.ConnectionState.CONNECTED) {
                hasEverConnected = true
                lastConnectedAddress?.let { settingsRepository.setLastDevice(it, ble.deviceName.value ?: it) }
            }
        } }
        scope.launch { ble.deviceName.collect { _deviceName.value = it } }
        scope.launch { ble.rssi.collect { value ->
            _rssi.value = value
            val a = _alarmState.value
            _alarmState.value = a.copy(rssi = value, marginDb = (value ?: a.thresholdDbm) - a.thresholdDbm)
        } }
    }

    fun connect(address: String, name: String? = null) {
        intentionalDisconnect = false
        lastConnectedAddress = address
        _telemetry.value = null
        _history.value = emptyList()
        hasEverConnected = false
        commandPath = CommandPath.UNKNOWN
        canId = null
        useSelectiveForPoll = true
        consecutivePollFailures = 0
        ble.connect(address)
        startPollingWhenConnected()
    }

    fun disconnect() {
        intentionalDisconnect = true
        _alarmState.value = _alarmState.value.copy(
            status = if (_alarmState.value.status == AlarmStatus.ALARM) AlarmStatus.MONITORING else _alarmState.value.status,
            belowSeconds = 0
        )
        ble.disconnect()
        pollingJob?.cancel()
        _telemetry.value = null
        _connectionState.value = BleManager.ConnectionState.DISCONNECTED
    }

    private fun startPollingWhenConnected() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            var detected = false
            while (isActive) {
                if (_connectionState.value != BleManager.ConnectionState.CONNECTED) {
                    delay(300)
                    continue
                }
                if (!detected) {
                    detected = detectCommandPath()
                    continue
                }
                pollOnce()
            }
        }
    }

    private suspend fun requestPayload(packet: ByteArray, commId: Int): ByteArray? =
        ble.request(packet, commId, REQUEST_TIMEOUT_MS)

    private suspend fun detectCommandPath(): Boolean {
        var fwOk = false
        repeat(3) {
            val fw = requestPayload(VescPacket.buildFwVersion(), VescPacket.COMM_FW_VERSION)
            if (fw != null) fwOk = true
        }
        if (!fwOk) {
            _events.tryEmit(localized().getString(R.string.event_module_unresponsive))
            return false
        }
        val canIds = mutableListOf<Int>()
        repeat(2) {
            val resp = requestPayload(VescPacket.buildPingCan(), VescPacket.COMM_PING_CAN)
            if (resp != null && resp.isNotEmpty() && resp[0].toInt() == VescPacket.COMM_PING_CAN) {
                for (i in 1 until resp.size) canIds.add(resp[i].toInt() and 0xFF)
            }
        }
        if (tryTelemetry(null)) {
            commandPath = CommandPath.DIRECT
            canId = null
            Log.i("VescRepo", "Transport: BLE DIRECT")
            return true
        }
        for (id in canIds) {
            if (tryTelemetry(id)) {
                commandPath = CommandPath.CAN
                canId = id
                Log.i("VescRepo", "Transport: BLE CAN (id=$id)")
                return true
            }
        }
        return false
    }

    private suspend fun tryTelemetry(canId: Int?): Boolean {
        for (useSelective in listOf(true, false)) {
            val commId = if (useSelective) VescPacket.COMM_GET_VALUES_SELECTIVE else VescPacket.COMM_GET_VALUES
            val inner = if (useSelective) VescPacket.buildGetValuesSelective() else VescPacket.buildGetValues()
            val packet = if (canId == null) inner else VescPacket.frameCanForward(inner.copyOfRange(1, inner.size), canId)
            val parsed = requestPayload(packet, commId)?.let { VescPacket.parseTelemetry(it) }
            if (parsed != null) {
                useSelectiveForPoll = useSelective
                _telemetry.value = parsed
                emitTelemetryAlerts(parsed)
                pushHistory(parsed)
                return true
            }
        }
        return false
    }

    private suspend fun pollOnce() {
        val started = System.nanoTime()
        val useSelective = useSelectiveForPoll
        val commId = if (useSelective) VescPacket.COMM_GET_VALUES_SELECTIVE else VescPacket.COMM_GET_VALUES
        val inner = if (useSelective) VescPacket.buildGetValuesSelective() else VescPacket.buildGetValues()
        val packet = if (commandPath == CommandPath.CAN && canId != null) {
            VescPacket.frameCanForward(inner.copyOfRange(1, inner.size), canId!!)
        } else inner
        val parsed = requestPayload(packet, commId)?.let { VescPacket.parseTelemetry(it) }
        if (parsed != null) {
            consecutivePollFailures = 0
            _telemetry.value = parsed
            emitTelemetryAlerts(parsed)
            pushHistory(parsed)
        } else if (++consecutivePollFailures >= 20) {
            consecutivePollFailures = 0
            commandPath = CommandPath.UNKNOWN
            canId = null
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        val sleep = POLL_PERIOD_MS - elapsedMs
        if (sleep > 0) delay(sleep)
    }

    private fun emitTelemetryAlerts(telemetry: VescTelemetry) {
        val fired = alertEngine.update(telemetry, currentSettings, System.currentTimeMillis())
        fired.forEach { type ->
            _telemetryAlerts.tryEmit(type)
            _events.tryEmit(localized().getString(alertMessageRes(type)))
            runCatching { TelemetryAlertNotifier.notify(context, type) }
                .onFailure { Log.w("VescRepo", "Telemetry alert notification unavailable", it) }
        }
    }

    private fun alertMessageRes(type: TelemetryAlertType): Int = when (type) {
        TelemetryAlertType.LOW_BATTERY -> R.string.event_alert_low_battery
        TelemetryAlertType.HIGH_TEMPERATURE -> R.string.event_alert_high_temperature
        TelemetryAlertType.HIGH_CURRENT -> R.string.event_alert_high_current
        TelemetryAlertType.LOW_VOLTAGE -> R.string.event_alert_low_voltage
    }

    private fun pushHistory(t: VescTelemetry) {
        val now = System.currentTimeMillis()
        _history.value = (_history.value + HistoryPoint(now, t.powerW, t.erpm))
            .dropWhile { now - it.timestampMs > HISTORY_WINDOW_MS }
    }

    fun enableAlarm() {
        if (_alarmState.value.status != AlarmStatus.OFF) return
        _alarmState.value = _alarmState.value.copy(status = AlarmStatus.MONITORING, belowSeconds = 0)
        startAlarmMonitor()
    }

    fun disableAlarm() {
        alarmJob?.cancel()
        alarmJob = null
        _alarmState.value = AlarmUiState(status = AlarmStatus.OFF, rssi = _alarmState.value.rssi)
    }

    private fun startAlarmMonitor() {
        alarmJob?.cancel()
        alarmJob = scope.launch {
            while (isActive) {
                delay(ALARM_TICK_MS)
                val s = currentSettings
                val a = _alarmState.value
                if (a.status == AlarmStatus.OFF) break
                val linkDown = hasEverConnected && !intentionalDisconnect &&
                    ble.state.value != BleManager.ConnectionState.CONNECTED
                val sample = ble.rssi.value
                val below = if (linkDown) s.alarmOnDisconnect else sample != null && sample < s.alarmThresholdDbm
                val belowSeconds = if (below) a.belowSeconds + 1 else 0
                val newStatus = when {
                    a.status == AlarmStatus.MONITORING && belowSeconds >= s.alarmDebounceSeconds -> AlarmStatus.ALARM
                    a.status == AlarmStatus.ALARM && s.alarmAutoStopOnReturn && !below && !linkDown -> AlarmStatus.MONITORING
                    else -> a.status
                }
                _alarmState.value = a.copy(
                    status = newStatus,
                    belowSeconds = belowSeconds,
                    thresholdDbm = s.alarmThresholdDbm,
                    debounceSeconds = s.alarmDebounceSeconds,
                    marginDb = (sample ?: a.rssi ?: a.thresholdDbm) - s.alarmThresholdDbm
                )
            }
        }
    }

    fun vehicleParams(): VehicleParams = vehicle
}
