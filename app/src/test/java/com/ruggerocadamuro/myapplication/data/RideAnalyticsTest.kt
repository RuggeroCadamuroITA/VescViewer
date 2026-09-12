package com.ruggerocadamuro.myapplication.data

import com.ruggerocadamuro.myapplication.data.database.RidePointEntity
import com.ruggerocadamuro.myapplication.data.ride.RideAnalytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RideAnalyticsTest {
    @Test
    fun whPerKmRequiresPositiveDistance() {
        assertNull(RideAnalytics.whPerKm(100f, 0.0))
        assertEquals(50f, RideAnalytics.whPerKm(100f, 2.0)!!, 0.001f)
    }

    @Test
    fun rangeUsesRemainingEnergyAndObservedConsumption() {
        val range = RideAnalytics.estimatedRangeKm(
            remainingBatteryPercent = 50,
            usableBatteryWh = 1000f,
            consumedWh = 200f,
            distanceKm = 10.0
        )
        assertEquals(25f, range!!, 0.001f)
    }

    @Test
    fun nearestPointFindsClosestRecordedSample() {
        val points = listOf(
            RidePointEntity(sessionId = 1, timestampMs = 1, latitude = 45.0, longitude = 9.0),
            RidePointEntity(sessionId = 1, timestampMs = 2, latitude = 45.01, longitude = 9.01),
            RidePointEntity(sessionId = 1, timestampMs = 3, latitude = 45.02, longitude = 9.02)
        )
        assertEquals(1, RideAnalytics.nearestPointIndex(points, 45.009, 9.011))
    }
}
