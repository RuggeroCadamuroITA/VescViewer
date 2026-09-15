package com.ruggerocadamuro.myapplication.data.recording

import android.location.Location
import com.ruggerocadamuro.myapplication.ServiceLocator
import com.ruggerocadamuro.myapplication.data.VescMath
import com.ruggerocadamuro.myapplication.data.VescRepository
import com.ruggerocadamuro.myapplication.data.database.RideDao
import com.ruggerocadamuro.myapplication.data.database.RidePointEntity
import com.ruggerocadamuro.myapplication.data.database.RideSessionEntity
import com.ruggerocadamuro.myapplication.data.vesc.VescTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

/** Runtime state shared by the dashboard, notification and map screens. */
data class RecordingState(
    val sessionId: Long? = null,
    val active: Boolean = false,
    val paused: Boolean = false,
    val startedAtMs: Long? = null,
    val pointsSaved: Int = 0,
    val distanceM: Double = 0.0,
    val error: RecordingError? = null,
    val locationStatus: LocationStatus = LocationStatus.UNKNOWN
)

enum class LocationStatus {
    UNKNOWN, READY, WAITING, STALE, PERMISSION_MISSING, PROVIDER_DISABLED, UNAVAILABLE
}

enum class RecordingError {
    PERSISTENCE
}

/** Pure session-counter rules, kept separate so reset/wrap behavior is testable. */
internal object RideRecordingMath {
    fun counterDelta(current: Float?, baseline: Float?): Float {
        val value = current?.takeIf { it.isFinite() } ?: return 0f
        val start = baseline?.takeIf { it.isFinite() }
        return if (start == null) value.coerceAtLeast(0f)
        else if (value >= start) value - start
        else value.coerceAtLeast(0f) // controller reset/wrap: restart the session delta
    }
}

/**
 * Combines location samples with the latest VESC snapshot. The service owns
 * location delivery; this class owns adaptive filtering and Room persistence.
 * All lifecycle and sample commands are serialized by [operationMutex].
 */
class RideRecorder(
    private val dao: RideDao = ServiceLocator.database.rideDao(),
    private val repository: VescRepository = ServiceLocator.vescRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private var latestLocation: Location? = null
    @Volatile private var latestTelemetry: VescTelemetry? = null
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
    private var operationTail: Job? = null
    private var baselineAmpHours: Float? = null
    private var baselineWattHours: Float? = null
    private var baselineVescDistanceKm: Float? = null
    private var lastSavedVescDistanceKm: Float? = null

    fun start() {
        enqueue { startInternal() }
    }

    private fun enqueue(operation: suspend () -> Unit): Job {
        val previous = operationTail
        return scope.launch {
            previous?.join()
            operationMutex.withLock { operation() }
        }.also { operationTail = it }
    }

    private suspend fun startInternal() {
        if (_state.value.active || session != null) return
        val now = System.currentTimeMillis()
        val initialTelemetry = latestTelemetry ?: repository.telemetry.value
        val params = repository.vehicleParams()
        val created = RideSessionEntity(
            startedAtMs = now,
            startBatteryPercent = initialTelemetry?.let {
                VescMath.batteryPercent(it.voltage, params.batteryCells)
            }
        )
        val id = try {
            dao.insertSession(created)
        } catch (_: Exception) {
            setPersistenceError()
            return
        }

        session = created.copy(id = id)
        totalGpsDistanceM = 0.0
        totalVescDistanceM = 0.0
        speedSum = 0f
        speedSamples = 0
        maxSpeed = 0f
        maxPower = 0f
        lastSavedLocation = null
        lastSavedAtMs = 0L
        baselineAmpHours = initialTelemetry?.ampHoursConsumed
        baselineWattHours = initialTelemetry?.wattHoursConsumed
        baselineVescDistanceKm = initialTelemetry?.let { VescMath.distanceKm(it, params) }
        lastSavedVescDistanceKm = baselineVescDistanceKm
        _state.value = RecordingState(id, active = true, startedAtMs = now)

        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            repository.telemetry.collect { latestTelemetry = it }
        }
        latestLocation?.let { processLocationInternal(it) }
    }

    fun setLocationStatus(status: LocationStatus) {
        _state.value = _state.value.copy(locationStatus = status)
    }

    fun pause() {
        enqueue {
            if (_state.value.active) _state.value = _state.value.copy(paused = true)
        }
    }

    fun resume() {
        enqueue {
            if (_state.value.active) _state.value = _state.value.copy(paused = false, error = null)
        }
    }

    fun onLocation(location: Location) {
        val copy = Location(location)
        enqueue {
            latestLocation = copy
            if (_state.value.active && !_state.value.paused) processLocationInternal(copy)
        }
    }

    private suspend fun processLocationInternal(location: Location) {
        val current = session ?: return
        val now = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
        val previous = lastSavedLocation
        val movedM = previous?.distanceTo(location)?.toDouble() ?: Double.MAX_VALUE
        val elapsed = now - lastSavedAtMs
        // Keep a point at most once per second, but preserve meaningful movement.
        if (previous != null && elapsed < 900L && movedM < 4.0) return
        if (previous != null && movedM.isFinite()) totalGpsDistanceM += movedM

        val telemetry = latestTelemetry ?: repository.telemetry.value
        val params = repository.vehicleParams()
        val gpsSpeed = (location.speed.takeIf { it >= 0f } ?: 0f) * 3.6f
        val vescSpeed = telemetry?.let { VescMath.speedKmh(it, params) }
        val vescDistance = telemetry?.let { VescMath.distanceKm(it, params) }
        val previousVescDistance = lastSavedVescDistanceKm
        if (vescDistance != null && previousVescDistance != null) {
            totalVescDistanceM += if (vescDistance >= previousVescDistance) {
                (vescDistance - previousVescDistance).toDouble() * 1000.0
            } else {
                0.0 // reset/wrap: the next samples continue from the new counter origin
            }
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
            ampHours = telemetry?.let { RideRecordingMath.counterDelta(it.ampHoursConsumed, baselineAmpHours) },
            wattHours = telemetry?.let { RideRecordingMath.counterDelta(it.wattHoursConsumed, baselineWattHours) },
            distanceGpsM = totalGpsDistanceM,
            distanceVescM = totalVescDistanceM
        )
        lastSavedLocation = Location(location)
        lastSavedAtMs = now
        try {
            dao.insertPoint(point)
            dao.checkpointSession(
                sessionId = current.id,
                durationMs = now - current.startedAtMs,
                distanceGpsM = totalGpsDistanceM,
                distanceVescM = totalVescDistanceM,
                maxGpsSpeedKmh = maxSpeed,
                averageGpsSpeedKmh = if (speedSamples == 0) 0f else speedSum / speedSamples,
                maxPowerW = maxPower,
                wattHours = telemetry?.let {
                    RideRecordingMath.counterDelta(it.wattHoursConsumed, baselineWattHours)
                } ?: 0f,
                ampHours = telemetry?.let {
                    RideRecordingMath.counterDelta(it.ampHoursConsumed, baselineAmpHours)
                } ?: 0f
            )
            _state.value = _state.value.copy(
                pointsSaved = _state.value.pointsSaved + 1,
                distanceM = totalGpsDistanceM,
                error = null
            )
        } catch (_: Exception) {
            setPersistenceError()
        }
    }

    fun stop(onFinished: (Boolean) -> Unit = {}) {
        enqueue {
            val current = session
            if (current == null) {
                onFinished(false)
                return@enqueue
            }
            val ended = System.currentTimeMillis()
            val currentTelemetry = latestTelemetry ?: repository.telemetry.value
            val success = try {
                    dao.finishSession(
                        sessionId = current.id,
                        endedAtMs = ended,
                        durationMs = ended - current.startedAtMs,
                        distanceGpsM = totalGpsDistanceM,
                        distanceVescM = totalVescDistanceM,
                        maxGpsSpeedKmh = maxSpeed,
                        averageGpsSpeedKmh = if (speedSamples == 0) 0f else speedSum / speedSamples,
                        maxPowerW = maxPower,
                        wattHours = currentTelemetry?.let {
                            RideRecordingMath.counterDelta(it.wattHoursConsumed, baselineWattHours)
                        } ?: 0f,
                        ampHours = currentTelemetry?.let {
                            RideRecordingMath.counterDelta(it.ampHoursConsumed, baselineAmpHours)
                        } ?: 0f,
                        endBatteryPercent = currentTelemetry?.let {
                            VescMath.batteryPercent(it.voltage, repository.vehicleParams().batteryCells)
                        },
                        completed = true
                    )
                    true
                } catch (_: Exception) {
                    setPersistenceError()
                    false
                }
                if (success) {
                    telemetryJob?.cancel()
                    telemetryJob = null
                    session = null
                    baselineAmpHours = null
                    baselineWattHours = null
                    baselineVescDistanceKm = null
                    lastSavedVescDistanceKm = null
                    _state.value = RecordingState()
                }
            onFinished(success)
        }
    }

    private fun setPersistenceError() {
        _state.value = _state.value.copy(error = RecordingError.PERSISTENCE)
    }
}
