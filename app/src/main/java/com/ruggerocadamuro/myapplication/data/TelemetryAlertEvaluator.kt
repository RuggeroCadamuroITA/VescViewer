package com.ruggerocadamuro.myapplication.data

import com.ruggerocadamuro.myapplication.data.settings.AppSettings
import com.ruggerocadamuro.myapplication.data.vesc.VescTelemetry
import kotlin.math.abs

/** The user-configurable telemetry conditions supported by the alert engine. */
enum class TelemetryAlertType {
    LOW_BATTERY,
    HIGH_TEMPERATURE,
    HIGH_CURRENT,
    LOW_VOLTAGE
}

/** Pure threshold evaluator; invalid telemetry never creates a safety alert. */
object TelemetryAlertEvaluator {
    fun evaluate(telemetry: VescTelemetry, settings: AppSettings): Set<TelemetryAlertType> {
        if (!telemetry.isSane()) return emptySet()
        return buildSet {
            if (settings.lowBatteryAlertEnabled &&
                VescMath.batteryPercent(telemetry.voltage, settings.batteryCells) <= settings.lowBatteryAlertPercent
            ) add(TelemetryAlertType.LOW_BATTERY)
            if (settings.highTemperatureAlertEnabled && telemetry.tempMos >= settings.highTemperatureAlertC) {
                add(TelemetryAlertType.HIGH_TEMPERATURE)
            }
            if (settings.highCurrentAlertEnabled && abs(telemetry.currentBattery) >= settings.highCurrentAlertA) {
                add(TelemetryAlertType.HIGH_CURRENT)
            }
            if (settings.lowVoltageAlertEnabled && telemetry.voltage <= settings.lowVoltageAlertV) {
                add(TelemetryAlertType.LOW_VOLTAGE)
            }
        }
    }
}

/**
 * Small stateful debounce/cooldown layer kept independent from Android so its
 * threshold transitions can be regression-tested without a device.
 */
class TelemetryAlertEngine(
    private val debounceSamples: Int = 3,
    private val cooldownMs: Long = 30_000L
) {
    private val consecutive = mutableMapOf<TelemetryAlertType, Int>()
    private val active = mutableSetOf<TelemetryAlertType>()
    private val lastNotifiedAt = mutableMapOf<TelemetryAlertType, Long>()

    fun update(telemetry: VescTelemetry, settings: AppSettings, nowMs: Long): Set<TelemetryAlertType> {
        val crossed = TelemetryAlertEvaluator.evaluate(telemetry, settings)
        val fired = mutableSetOf<TelemetryAlertType>()
        TelemetryAlertType.entries.forEach { type ->
            if (type in crossed) {
                val samples = (consecutive[type] ?: 0) + 1
                consecutive[type] = samples
                if (samples >= debounceSamples && type !in active) {
                    active += type
                    val last = lastNotifiedAt[type]
                    if (last == null || nowMs - last >= cooldownMs) {
                        lastNotifiedAt[type] = nowMs
                        fired += type
                    }
                }
            } else {
                consecutive[type] = 0
                active -= type
            }
        }
        return fired
    }

    fun reset() {
        consecutive.clear()
        active.clear()
        lastNotifiedAt.clear()
    }
}
