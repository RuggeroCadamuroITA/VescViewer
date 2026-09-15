package com.ruggerocadamuro.myapplication.data

import com.ruggerocadamuro.myapplication.data.settings.AppSettings
import com.ruggerocadamuro.myapplication.data.vesc.VescTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryAlertEvaluatorTest {
    private val settings = AppSettings(
        batteryCells = 10,
        lowBatteryAlertEnabled = true,
        lowBatteryAlertPercent = 20,
        highTemperatureAlertEnabled = true,
        highTemperatureAlertC = 75f,
        highCurrentAlertEnabled = true,
        highCurrentAlertA = 80f,
        lowVoltageAlertEnabled = true,
        lowVoltageAlertV = 30f
    )

    private fun telemetry(
        voltage: Float = 40f,
        tempMos: Float = 40f,
        currentBattery: Float = 10f
    ) = VescTelemetry(
        tempMos = tempMos,
        tempMotor = 30f,
        currentBattery = currentBattery,
        currentMotor = currentBattery,
        voltage = voltage,
        erpm = 1000f,
        dutyCyclePercent = 10f,
        ampHoursConsumed = 1f,
        wattHoursConsumed = 10f,
        tachometer = 1,
        tachometerAbs = 1
    )

    @Test
    fun evaluatesEnabledThresholdsAndIgnoresDisabledOnes() {
        val alerts = TelemetryAlertEvaluator.evaluate(
            telemetry(voltage = 28f, tempMos = 80f, currentBattery = 90f),
            settings
        )
        assertEquals(
            setOf(
                TelemetryAlertType.LOW_BATTERY,
                TelemetryAlertType.HIGH_TEMPERATURE,
                TelemetryAlertType.HIGH_CURRENT,
                TelemetryAlertType.LOW_VOLTAGE
            ),
            alerts
        )
        assertTrue(
            TelemetryAlertEvaluator.evaluate(
                telemetry(voltage = 28f, tempMos = 80f, currentBattery = 90f),
                settings.copy(highCurrentAlertEnabled = false)
            ).none { it == TelemetryAlertType.HIGH_CURRENT }
        )
    }

    @Test
    fun requiresThreeConsecutiveSamplesAndResetsAfterRecovery() {
        val engine = TelemetryAlertEngine(debounceSamples = 3, cooldownMs = 30_000L)
        val hot = telemetry(tempMos = 80f)
        assertTrue(engine.update(hot, settings, 0L).isEmpty())
        assertTrue(engine.update(hot, settings, 1_000L).isEmpty())
        assertEquals(setOf(TelemetryAlertType.HIGH_TEMPERATURE), engine.update(hot, settings, 2_000L))
        assertTrue(engine.update(hot, settings, 3_000L).isEmpty())
        assertTrue(engine.update(telemetry(tempMos = 40f), settings, 4_000L).isEmpty())
        assertTrue(engine.update(hot, settings, 35_000L).isEmpty())
        assertTrue(engine.update(hot, settings, 36_000L).isEmpty())
        assertEquals(setOf(TelemetryAlertType.HIGH_TEMPERATURE), engine.update(hot, settings, 37_000L))
    }
}
