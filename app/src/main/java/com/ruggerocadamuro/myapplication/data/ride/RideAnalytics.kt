package com.ruggerocadamuro.myapplication.data.ride

import com.ruggerocadamuro.myapplication.data.database.RidePointEntity
import kotlin.math.cos
import kotlin.math.max

object RideAnalytics {
    fun whPerKm(wattHours: Float?, distanceKm: Double): Float? {
        if (wattHours == null || distanceKm <= 0.001) return null
        return wattHours / distanceKm.toFloat()
    }

    fun estimatedRangeKm(
        remainingBatteryPercent: Int?,
        usableBatteryWh: Float?,
        consumedWh: Float?,
        distanceKm: Double
    ): Float? {
        if (remainingBatteryPercent == null || usableBatteryWh == null || consumedWh == null || distanceKm <= 0.001) return null
        val usedWhPerKm = consumedWh / distanceKm.toFloat()
        if (usedWhPerKm <= 0.01f) return null
        val remainingWh = usableBatteryWh * remainingBatteryPercent.coerceIn(0, 100) / 100f
        return max(0f, remainingWh / usedWhPerKm)
    }

    fun nearestPointIndex(points: List<RidePointEntity>, latitude: Double, longitude: Double): Int? {
        if (points.isEmpty()) return null
        val latitudeScale = cos(Math.toRadians(latitude)).coerceAtLeast(0.01)
        return points.indices.minByOrNull { index ->
            val point = points[index]
            val latDistance = point.latitude - latitude
            val lonDistance = (point.longitude - longitude) * latitudeScale
            latDistance * latDistance + lonDistance * lonDistance
        }
    }
}
