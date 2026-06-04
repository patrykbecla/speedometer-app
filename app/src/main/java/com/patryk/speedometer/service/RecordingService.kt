package com.patryk.speedometer.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.patryk.speedometer.MainActivity
import com.patryk.speedometer.data.RecordingRepository
import com.patryk.speedometer.data.SettingsRepository
import com.patryk.speedometer.data.db.Sample
import com.patryk.speedometer.location.LocationProvider
import com.patryk.speedometer.location.TripAccumulator
import com.patryk.speedometer.location.TripStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var repository: RecordingRepository
    private lateinit var settings: SettingsRepository
    private lateinit var locationProvider: LocationProvider

    private var sessionId = -1L
    private var collectJob: Job? = null

    private val accumulator = TripAccumulator()
    private var lastStats = TripStats(null, 0f, 0f, 0.0)

    override fun onCreate() {
        super.onCreate()
        repository = RecordingRepository.getInstance(this)
        settings = SettingsRepository(this)
        locationProvider = LocationProvider(this)
        createNotificationChannel()
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRecording()
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("Recording…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )

        scope.launch {
            val intervalMs = settings.samplingIntervalMs.first()
            accumulator.reset()
            sessionId = repository.startSession()
            collectJob = launch {
                locationProvider.locationUpdates(intervalMs).collect { loc ->
                    handleLocation(loc)
                }
            }
        }

        return START_STICKY
    }

    private suspend fun handleLocation(loc: Location) {
        lastStats = accumulator.add(loc)

        // Persist raw (unclamped) speed so CSV/GPX export stays faithful.
        repository.saveSample(
            Sample(
                sessionId = sessionId,
                timestampMs = loc.time,
                speedMps = if (loc.hasSpeed()) loc.speed else 0f,
                lat = loc.latitude,
                lng = loc.longitude,
                altitudeM = if (loc.hasAltitude()) loc.altitude else 0.0,
                accuracyM = if (loc.hasAccuracy()) loc.accuracy else 0f,
            )
        )
    }

    private fun stopRecording() {
        collectJob?.cancel()
        scope.launch {
            if (sessionId != -1L) {
                repository.finalizeSession(
                    sessionId,
                    lastStats.maxSpeedMps,
                    lastStats.avgSpeedMps,
                    lastStats.distanceM,
                )
                sessionId = -1L
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    // ─── Notification ────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recording",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Active recording indicator" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Speedometer recording")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_menu_compass)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        )
        .addAction(
            android.R.drawable.ic_media_pause,
            "Stop",
            PendingIntent.getService(
                this, 1,
                stopIntent(this),
                PendingIntent.FLAG_IMMUTABLE,
            )
        )
        .build()

    companion object {
        const val ACTION_STOP = "com.patryk.speedometer.STOP_RECORDING"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "recording"

        fun startIntent(context: Context) = Intent(context, RecordingService::class.java)

        fun stopIntent(context: Context) =
            Intent(context, RecordingService::class.java).apply { action = ACTION_STOP }
    }
}
