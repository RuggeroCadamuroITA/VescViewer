package com.ruggerocadamuro.myapplication.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Version 1 is the first exported schema. Future changes must increment the
 * version and register an explicit migration before shipping.
 */
@Database(
    entities = [RideSessionEntity::class, RidePointEntity::class, AlertRuleEntity::class],
    version = 1,
    exportSchema = true
)
abstract class VescDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
    abstract fun alertRuleDao(): AlertRuleDao

    companion object {
        @Volatile private var instance: VescDatabase? = null

        fun get(context: Context): VescDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                VescDatabase::class.java,
                "vesc_viewer.db"
            ).build().also { instance = it }
        }
    }
}
