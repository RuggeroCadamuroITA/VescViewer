package com.ruggerocadamuro.myapplication.data

import com.ruggerocadamuro.myapplication.data.settings.SpeedUnit
import com.ruggerocadamuro.myapplication.data.settings.TempUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VescMathTest {
    @Test
    fun convertsSpeedTemperatureAndDistanceForDisplayUnits() {
        assertEquals(6.21371f, VescMath.displaySpeedKmh(10f, SpeedUnit.MPH), 0.0001f)
        assertEquals(68f, VescMath.displayTemperatureCelsius(20f, TempUnit.FAHRENHEIT), 0.001f)
        assertEquals(0.621371f, VescMath.displayDistanceKm(1f, SpeedUnit.MPH), 0.0001f)
    }

    @Test
    fun batteryEstimateIsBoundedAndRejectsInvalidInputs() {
        assertEquals(0, VescMath.batteryPercent(0f, 10))
        assertEquals(0, VescMath.batteryPercent(33f, 10))
        assertEquals(100, VescMath.batteryPercent(42f, 10))
        assertEquals(100, VescMath.batteryPercent(60f, 10))
        assertTrue(VescMath.batteryPercent(Float.NaN, 10) == 0)
    }
}
