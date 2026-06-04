package com.patryk.speedometer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SampleDao {
    @Insert
    suspend fun insert(sample: Sample)

    @Query("SELECT * FROM samples WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun samplesForSession(sessionId: Long): Flow<List<Sample>>

    @Query("SELECT * FROM samples WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun samplesForSessionOnce(sessionId: Long): List<Sample>

    @Query("DELETE FROM samples WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)
}
