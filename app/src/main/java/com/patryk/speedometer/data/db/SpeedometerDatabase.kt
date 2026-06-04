package com.patryk.speedometer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Session::class, Sample::class], version = 1, exportSchema = false)
abstract class SpeedometerDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun sampleDao(): SampleDao

    companion object {
        @Volatile private var INSTANCE: SpeedometerDatabase? = null

        fun getInstance(context: Context): SpeedometerDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    SpeedometerDatabase::class.java,
                    "speedometer.db",
                ).build().also { INSTANCE = it }
            }
    }
}
