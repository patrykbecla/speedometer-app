package com.patryk.speedometer

import android.app.Application
import android.content.Context
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.patryk.speedometer.data.ExportUtils
import com.patryk.speedometer.data.RecordingRepository
import com.patryk.speedometer.data.RecordingState
import com.patryk.speedometer.data.SettingsRepository
import com.patryk.speedometer.data.SignalQuality
import com.patryk.speedometer.data.SpeedUnit
import com.patryk.speedometer.data.db.Sample
import com.patryk.speedometer.data.db.Session
import com.patryk.speedometer.data.signalQuality
import com.patryk.speedometer.location.LocationProvider
import com.patryk.speedometer.location.MovingAverage
import com.patryk.speedometer.location.TripAccumulator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SpeedUiState(
    val acquiring: Boolean = true,
    val speedMps: Float? = null,
    val smoothedSpeedMps: Float? = null,
    val maxSpeedMps: Float = 0f,
    val avgSpeedMps: Float = 0f,
    val distanceM: Double = 0.0,
    val altitudeM: Double? = null,
    val verticalSpeedMps: Float? = null,
    val signalQuality: SignalQuality = SignalQuality.UNKNOWN,
    val accuracyM: Float? = null,
)

class SpeedViewModel(app: Application) : AndroidViewModel(app) {

    private val locationProvider = LocationProvider(app)
    private val settings = SettingsRepository(app)
    val repository = RecordingRepository.getInstance(app)

    val unit: StateFlow<SpeedUnit> = settings.unit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpeedUnit.KMH)

    val allSessions: StateFlow<List<Session>> = repository.sessionDao.allSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val samplingIntervalMs: StateFlow<Long> = settings.samplingIntervalMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1_000L)

    private val _speed = MutableStateFlow(SpeedUiState())
    val speed: StateFlow<SpeedUiState> = _speed.asStateFlow()

    val recordingState: StateFlow<RecordingState> = repository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordingState.Idle)

    val isRecording: StateFlow<Boolean> = recordingState
        .map { it is RecordingState.Recording }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _lastSessionId = MutableStateFlow<Long?>(null)
    val lastSessionId: StateFlow<Long?> = _lastSessionId.asStateFlow()

    private val accumulator = TripAccumulator()
    private val hSmoother = MovingAverage(windowSize = 5)
    private val vSmoother = MovingAverage(windowSize = 10)

    private var prevLocation: Location? = null

    private var collectJob: Job? = null

    fun startLocationUpdates() {
        if (collectJob != null) return
        resetSession()
        collectJob = viewModelScope.launch {
            locationProvider.locationUpdates().collect(::processLocation)
        }
    }

    fun stopLocationUpdates() {
        collectJob?.cancel()
        collectJob = null
        _speed.value = SpeedUiState()
    }

    fun setUnit(unit: SpeedUnit) {
        viewModelScope.launch { settings.setUnit(unit) }
    }

    fun setSamplingInterval(ms: Long) {
        viewModelScope.launch { settings.setSamplingInterval(ms) }
    }

    fun sessionSamples(id: Long): Flow<List<Sample>> = repository.sampleDao.samplesForSession(id)

    fun sessionFlow(id: Long) = repository.sessionDao.getByIdFlow(id)

    fun setLastSession(id: Long) { _lastSessionId.value = id }

    fun resetStats() {
        accumulator.reset()
        hSmoother.reset()
        vSmoother.reset()
        prevLocation = null
        _speed.value = _speed.value.copy(
            maxSpeedMps = 0f,
            avgSpeedMps = 0f,
            distanceM = 0.0,
            verticalSpeedMps = null,
            smoothedSpeedMps = null,
        )
    }

    fun deleteSession(id: Long) = viewModelScope.launch {
        repository.sessionDao.getById(id)?.let { repository.sessionDao.delete(it) }
        if (_lastSessionId.value == id) _lastSessionId.value = null
    }

    fun renameSession(id: Long, label: String) = viewModelScope.launch {
        repository.sessionDao.getById(id)?.copy(label = label)?.let { repository.sessionDao.update(it) }
    }

    fun exportCsv(context: Context, sessionId: Long) = viewModelScope.launch {
        val session = repository.sessionDao.getById(sessionId) ?: return@launch
        val samples = repository.sampleDao.samplesForSessionOnce(sessionId)
        ExportUtils.shareCsv(context, session, samples)
    }

    fun exportGpx(context: Context, sessionId: Long) = viewModelScope.launch {
        val session = repository.sessionDao.getById(sessionId) ?: return@launch
        val samples = repository.sampleDao.samplesForSessionOnce(sessionId)
        ExportUtils.shareGpx(context, session, samples)
    }

    private fun resetSession() {
        accumulator.reset()
        hSmoother.reset()
        vSmoother.reset()
        prevLocation = null
    }

    private fun processLocation(location: Location) {
        val stats = accumulator.add(location)

        // Smooth the clamped speed for display (null stays null — shows "–")
        val smoothed = stats.clampedSpeedMps?.let { hSmoother.add(it) }

        // Altitude + vertical speed (uses its own prevLocation/dt, not accumulator's)
        val altM = if (location.hasAltitude()) location.altitude else null
        val vertMps: Float? = prevLocation
            ?.takeIf { it.hasAltitude() && altM != null }
            ?.let { prev ->
                val dtNanos = location.elapsedRealtimeNanos - prev.elapsedRealtimeNanos
                if (dtNanos > 0) vSmoother.add(((location.altitude - prev.altitude) / (dtNanos / 1e9)).toFloat())
                else vSmoother.current()
            } ?: vSmoother.current()

        prevLocation = location

        _speed.value = SpeedUiState(
            acquiring = false,
            speedMps = stats.clampedSpeedMps,
            smoothedSpeedMps = smoothed,
            maxSpeedMps = stats.maxSpeedMps,
            avgSpeedMps = stats.avgSpeedMps,
            distanceM = stats.distanceM,
            altitudeM = altM,
            verticalSpeedMps = vertMps,
            signalQuality = location.signalQuality(),
            accuracyM = if (location.hasAccuracy()) location.accuracy else null,
        )
    }
}
