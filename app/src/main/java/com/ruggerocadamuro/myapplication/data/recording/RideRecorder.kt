package com.ruggerocadamuro.myapplication.data.recording

import android.location.Location
import com.ruggerocadamuro.myapplication.ServiceLocator
import com.ruggerocadamuro.myapplication.data.VescMath
import com.ruggerocadamuro.myapplication.data.VehicleParams
import com.ruggerocadamuro.myapplication.data.database.RidePointEntity
import com.ruggerocadamuro.myapplication.data.database.RideSessionEntity
import com.ruggerocadamuro.myapplication.data.vesc.VescTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** Runtime state shared by the dashboard, notification and map screens. */
data class RecordingState(
    val sessionId: Long? = null,
    val active: Boolean = false,
    val paused: Boolean = false,
    val startedAtMs: Long? = null,
    val pointsSaved: Int = 0,
    val distanceM: Double = 0.0
)

/**
 * Combines location samples with the latest VESC snapshot. The service owns
 * location delivery; this class owns adaptive filtering and Room persistence.
 */
class RideRecorder {
    private val dao = ServiceLocator.database.rideDao()
    private val repository = ServiceLocator.vescRepository
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private var latestLocation: Location? = null
    private var latestTelemetry: VescTelemetry? = null
    private var session: RideSessionEntity? = null
    private var lastSavedLocation: Location? = null
    private var lastSavedAtMs = 0L
    private var totalGpsDistanceM = 0.0
    private var totalVescDistanceM = 0.0
    private var speedSum = 0f
    private var speedSamples = 0
    private var maxSpeed = 0f
    private var maxPower = 0f
    private var telemetryJob: Job? = null

    fun start() {
        if (_state.value.active) return
        scope.launch {
            val now = System.currentTimeMillis()
            val created = RideSessionEntity(
                startedAtMs = now,
                startBatteryPercent = repository.telemetry.value?.let {
                    VescMath.batteryPercent(it.voltage, repository.vehicleParams().batteryCells)
                }
            )
            val id = dao.insertSession(created)
            session = created.copy(id = id)
            totalGpsDistanceM = 0.0
            totalVescDistanceM = 0.0
            lastSavedLocation = null
            lastSavedAtMs = 0L
            _state.value = RecordingState(id, active = true, startedAtMs = now)
            telemetryJob?.cancel()
            telemetryJob = scope.launch {
                repository.telemetry.collect { latestTelemetry = it }
            }
            latestLocation?.let(::onLocation)
        }
    }

    fun pause() {
        if (_state.value.active) _state.value = _state.value.copy(paused = true)
    }

    fun resume() {
        if (_state.value.active) _state.value = _state.value.copy(paused = false)
    }

    fun onLocation(location: Location) {
        latestLocation = location
        val current = session ?: return
        val state = _state.value
        if (!state.active || state.paused) return
        val now = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
        val previous = lastSavedLocation
        val movedM = previous?.distanceTo(location)?.toDouble() ?: Double.MAX_VALUE
        val elapsed = now - lastSavedAtMs
        // Keep a point at most once per second, but preserve meaningful movement.
        if (previous != null && elapsed < 900L && movedM < 4.0) return
        if (previous != null && movedM.isFinite()) totalGpsDistanceM += movedM

        val telemetry = latestTelemetry
        val params = repository.vehicleParams()
        val gpsSpeed = (location.speed.takeIf { it >= 0f } ?: 0f) * 3.6f
        val vescSpeed = telemetry?.let { VescMath.speedKmh(it, params) }
        val vescDistance = telemetry?.let { VescMath.distanceKm(it, params) }
        val previousVescDistance = lastSavedVescDistanceKm
        if (vescDistance != null && previousVescDistance != null) {
            totalVescDistanceM += max(0.0, (vescDistance - previousVescDistance).toDouble() * 1000.0)
        }
        if (vescDistance != null) lastSavedVescDistanceKm = vescDistance
        speedSum += gpsSpeed
        speedSamples++
        maxSpeed = max(maxSpeed, gpsSpeed)
        maxPower = max(maxPower, telemetry?.powerW ?: 0f)

        val point = RidePointEntity(
            sessionId = current.id,
            timestampMs = now,
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeM = if (location.hasAltitude()) location.altitude else null,
            accuracyM = if (location.hasAccuracy()) location.accuracy else null,
            gpsSpeedKmh = gpsSpeed,
            vescSpeedKmh = vescSpeed,
            currentBatteryA = telemetry?.currentBattery,
            currentMotorA = telemetry?.currentMotor,
            voltageV = telemetry?.voltage,
            powerW = telemetry?.powerW,
            erpm = telemetry?.erpm,
            mechanicalRpm = telemetry?.erpm?.div(params.polePairs * params.gearRatio),
            dutyCyclePercent = telemetry?.dutyCyclePercent,
            tempMosC = telemetry?.tempMos,
            ampHours = telemetry?.ampHoursConsumed,
            wattHours = telemetry?.wattHoursConsumed,
            distanceGpsM = totalGpsDistanceM,
            distanceVescM = totalVescDistanceM
        )
        lastSavedLocation = Location(location)
        lastSavedAtMs = now
        scope.launch {
            dao.insertPoint(point)
            _state.value = _state.value.copy(
                pointsSaved = _state.value.pointsSaved + 1,
                distanceM = totalGpsDistanceM
            )
        }
    }

    fun stop() {
        val current = session ?: return
        scope.launch {
            val ended = System.currentTimeMillis()
            val currentTelemetry = repository.telemetry.value
            dao.finishSession(
                sessionId = current.id,
                endedAtMs = ended,
                durationMs = ended - current.startedAtMs,
                distanceGpsM = totalGpsDistanceM,
                distanceVescM = totalVescDistanceM,
                maxGpsSpeedKmh = maxSpeed,
                averageGpsSpeedKmh = if (speedSamples == 0) 0f else speedSum / speedSamples,
                maxPowerW = maxPower,
                wattHours = currentTelemetry?.wattHoursConsumed ?: 0f,
                ampHours = currentTelemetry?.ampHoursConsumed ?: 0f,
                endBatteryPercent = currentTelemetry?.let {
                    VescMath.batteryPercent(it.voltage, repository.vehicleParams().batteryCells)
                },
                completed = true
            )
            telemetryJob?.cancel()
            telemetryJob = null
            session = null
            _state.value = RecordingState()
        }
    }

    private var lastSavedVescDistanceKm: Float? = null
}
