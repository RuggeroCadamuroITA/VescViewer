package com.ruggerocadamuro.myapplication.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Insert
    suspend fun insertSession(session: RideSessionEntity): Long

    @Query("UPDATE ride_sessions SET endedAtMs = :endedAtMs, durationMs = :durationMs, distanceGpsM = :distanceGpsM, distanceVescM = :distanceVescM, maxGpsSpeedKmh = :maxGpsSpeedKmh, averageGpsSpeedKmh = :averageGpsSpeedKmh, maxPowerW = :maxPowerW, wattHours = :wattHours, ampHours = :ampHours, endBatteryPercent = :endBatteryPercent, completed = :completed WHERE id = :sessionId")
    suspend fun finishSession(
        sessionId: Long,
        endedAtMs: Long,
        durationMs: Long,
        distanceGpsM: Double,
        distanceVescM: Double,
        maxGpsSpeedKmh: Float,
        averageGpsSpeedKmh: Float,
        maxPowerW: Float,
        wattHours: Float,
        ampHours: Float,
        endBatteryPercent: Int?,
        completed: Boolean
    )

    @Query("UPDATE ride_sessions SET durationMs = :durationMs, distanceGpsM = :distanceGpsM, distanceVescM = :distanceVescM, maxGpsSpeedKmh = :maxGpsSpeedKmh, averageGpsSpeedKmh = :averageGpsSpeedKmh, maxPowerW = :maxPowerW, wattHours = :wattHours, ampHours = :ampHours WHERE id = :sessionId AND completed = 0")
    suspend fun checkpointSession(
        sessionId: Long,
        durationMs: Long,
        distanceGpsM: Double,
        distanceVescM: Double,
        maxGpsSpeedKmh: Float,
        averageGpsSpeedKmh: Float,
        maxPowerW: Float,
        wattHours: Float,
        ampHours: Float
    )

    @Query("UPDATE ride_sessions SET endedAtMs = COALESCE(endedAtMs, :endedAtMs), durationMs = CASE WHEN durationMs > 0 THEN durationMs ELSE MAX(0, :endedAtMs - startedAtMs) END, completed = 1 WHERE completed = 0")
    suspend fun finalizeIncompleteSessions(endedAtMs: Long): Int

    @Query("SELECT * FROM ride_sessions ORDER BY startedAtMs DESC")
    fun observeSessions(): Flow<List<RideSessionEntity>>

    @Query("SELECT * FROM ride_sessions WHERE id = :sessionId LIMIT 1")
    fun observeSession(sessionId: Long): Flow<RideSessionEntity?>

    @Query("SELECT * FROM ride_points WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun observePoints(sessionId: Long): Flow<List<RidePointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: RidePointEntity): Long

    @Query("DELETE FROM ride_points WHERE sessionId = :sessionId")
    suspend fun deletePoints(sessionId: Long)

    @Query("DELETE FROM ride_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("DELETE FROM ride_points WHERE sessionId = :sessionId")
    suspend fun deleteSessionPoints(sessionId: Long)
}

@Dao
interface AlertRuleDao {
    @Query("SELECT * FROM alert_rules ORDER BY key")
    fun observeRules(): Flow<List<AlertRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: AlertRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<AlertRuleEntity>)
}
