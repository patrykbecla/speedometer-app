package com.patryk.speedometer.data

/**
 * Speed units the readout can display. [fromMps] converts a raw m/s value
 * (as reported by [android.location.Location.getSpeed]) into this unit.
 */
enum class SpeedUnit(val label: String, private val fromMps: Float) {
    MPS("m/s", 1f),
    KMH("kmh", 3.6f),
    MPH("mph", 2.2369363f);

    fun convert(speedMps: Float): Float = speedMps * fromMps
}
