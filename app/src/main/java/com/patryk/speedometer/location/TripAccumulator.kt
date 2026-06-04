package com.patryk.speedometer.location

import android.location.Location

data class TripStats(
    val clampedSpeedMps: Float?,
    val maxSpeedMps: Float,
    val avgSpeedMps: Float,
    val distanceM: Double,
)

/**
 * Per-session speed/distance accumulator with GPS jitter rejection.
 *
 * Distance gate logic:
 *   - Skips fixes with accuracy worse than MAX_ACCURACY_M (indoor GPS noise).
 *   - Skips segments where Doppler speed < STILL_SPEED_MPS so stationary
 *     position random-walk never accumulates. Doppler speed is reliable on
 *     minSdk 31 (fused provider).
 *   - Clamps each segment to speed*dt*MAX_STEP_FACTOR so a single GPS jump
 *     can't inject more distance than the phone physically traveled.
 *
 * Raw (unclamped) Location.speed should be stored for DB/export — callers
 * are responsible for that; this class only drives live UI + session summaries.
 *
 * Not thread-safe; call from a single coroutine collector.
 */
class TripAccumulator {

    private var speedSamples = 0
    private var speedSumMps = 0.0
    private var maxMps = 0f
    private var totalDistanceM = 0.0
    private var prevLocation: Location? = null
    private var firstFixTimeMs = -1L

    fun reset() {
        speedSamples = 0
        speedSumMps = 0.0
        maxMps = 0f
        totalDistanceM = 0.0
        prevLocation = null
        firstFixTimeMs = -1L
    }

    fun add(location: Location): TripStats {
        val raw: Float? = if (location.hasSpeed()) location.speed else null

        // Clamp near-zero Doppler speed to 0 — stops stationary noise from
        // inflating max/avg and prevents the distance gate from opening.
        val clamped: Float? = when {
            raw == null -> null
            raw < STILL_SPEED_MPS -> 0f
            else -> raw
        }

        if (firstFixTimeMs == -1L) firstFixTimeMs = location.time
        val warmedUp = (location.time - firstFixTimeMs) >= WARMUP_MS

        if (warmedUp && clamped != null && clamped > 0f) {
            if (clamped > maxMps) maxMps = clamped
            speedSumMps += clamped
            speedSamples++
        }

        // Distance: only count when both GPS accuracy and Doppler confirm movement.
        val prev = prevLocation
        if (prev != null &&
            location.hasAccuracy() && location.accuracy <= MAX_ACCURACY_M &&
            raw != null && raw >= STILL_SPEED_MPS
        ) {
            val d = prev.distanceTo(location).toDouble()
            val dtSec = (location.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1e9
            val segment = if (dtSec in 0.0..MAX_DT_SEC) {
                minOf(d, raw * dtSec * MAX_STEP_FACTOR)
            } else {
                d
            }
            totalDistanceM += segment
        }

        prevLocation = location

        return TripStats(
            clampedSpeedMps = clamped,
            maxSpeedMps = maxMps,
            avgSpeedMps = if (speedSamples > 0) (speedSumMps / speedSamples).toFloat() else 0f,
            distanceM = totalDistanceM,
        )
    }

    companion object {
        const val STILL_SPEED_MPS = 0.5f
        const val MAX_ACCURACY_M = 20f
        const val MAX_STEP_FACTOR = 1.5
        const val MAX_DT_SEC = 10.0
        const val WARMUP_MS = 5_000L
    }
}
