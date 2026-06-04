package com.patryk.speedometer.data

import android.content.Context
import com.patryk.speedometer.data.db.Sample
import com.patryk.speedometer.data.db.Session
import com.patryk.speedometer.data.db.SpeedometerDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface RecordingState {
    data object Idle : RecordingState
    data class Recording(val sessionId: Long) : RecordingState
}

/**
 * Singleton that bridges the foreground service and the ViewModel.
 * The service writes; the ViewModel reads recording state and DB flows.
 */
class RecordingRepository private constructor(context: Context) {
    private val db = SpeedometerDatabase.getInstance(context)
    val sessionDao = db.sessionDao()
    val sampleDao = db.sampleDao()

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    suspend fun startSession(): Long {
        val id = sessionDao.insert(Session(startMs = System.currentTimeMillis()))
        _state.value = RecordingState.Recording(id)
        return id
    }

    suspend fun finalizeSession(id: Long, maxSpeedMps: Float, avgSpeedMps: Float, distanceM: Double) {
        sessionDao.getById(id)?.copy(
            endMs = System.currentTimeMillis(),
            maxSpeedMps = maxSpeedMps,
            avgSpeedMps = avgSpeedMps,
            distanceM = distanceM,
        )?.let { sessionDao.update(it) }
        _state.value = RecordingState.Idle
    }

    suspend fun saveSample(sample: Sample) = sampleDao.insert(sample)

    companion object {
        @Volatile private var INSTANCE: RecordingRepository? = null

        fun getInstance(context: Context): RecordingRepository =
            INSTANCE ?: synchronized(this) {
                RecordingRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
