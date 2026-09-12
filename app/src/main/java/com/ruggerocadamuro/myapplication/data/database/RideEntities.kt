package com.ruggerocadamuro.myapplication.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A recorded ride and its aggregate statistics. */
@Entity(tableName = "ride_sessions")
data class RideSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val title: String = "",
    val distanceGpsM: Double = 0.0,
    val distanceVescM: Double = 0.0,
    val durationMs: Long = 0L,
    val maxGpsSpeedKmh: Float = 0f,
    val averageGpsSpeedKmh: Float = 0f,
    val maxPowerW: Float = 0f,
    val wattHours: Float = 0f,
    val ampHours: Float = 0f,
    val startBatteryPercent: Int? = null,
    val endBatteryPercent: Int? = null,
    val completed: Boolean = false
)

/** A GPS/VESC snapshot. Every map-selectable point is backed by one row. */
@Entity(
    tableName = "ride_points",
    indices = [Index(value = ["sessionId", "timestampMs"])]
)
data class RidePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double? = null,
    val accuracyM: Float? = null,
    val gpsSpeedKmh: Float? = null,
    val vescSpeedKmh: Float? = null,
    val currentBatteryA: Float? = null,
    val currentMotorA: Float? = null,
    val voltageV: Float? = null,
    val powerW: Float? = null,
    val erpm: Float? = null,
    val mechanicalRpm: Float? = null,
    val dutyCyclePercent: Float? = null,
    val tempMosC: Float? = null,
    val ampHours: Float? = null,
    val wattHours: Float? = null,
    val distanceGpsM: Double = 0.0,
    val distanceVescM: Double = 0.0
)

@Entity(tableName = "alert_rules")
data class AlertRuleEntity(
    @PrimaryKey val key: String,
    val enabled: Boolean = true,
    val threshold: Float,
    val hysteresis: Float = 0f,
    val debounceSeconds: Int = 3,
    val cooldownSeconds: Int = 30,
    val notificationEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = true
)
