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
    fun nearestPointScalesLongitudeAtHighLatitude() {
        val points = listOf(
            RidePointEntity(sessionId = 1, timestampMs = 1, latitude = 80.01, longitude = 0.0),
            RidePointEntity(sessionId = 1, timestampMs = 2, latitude = 80.0, longitude = 0.02)
        )
        // At 80 degrees north, 0.02 degrees longitude is physically closer
        // than 0.01 degrees latitude because meridians are much narrower.
        assertEquals(1, RideAnalytics.nearestPointIndex(points, 80.0, 0.0))
    }
}
