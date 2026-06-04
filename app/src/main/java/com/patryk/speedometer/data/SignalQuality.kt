package com.patryk.speedometer.data

import android.location.Location
import android.os.Build

enum class SignalQuality(val label: String) {
    GOOD("Good"),
    OK("OK"),
    POOR("Poor"),
    UNKNOWN("–"),
}

fun Location.signalQuality(): SignalQuality {
    if (!hasAccuracy()) return SignalQuality.UNKNOWN

    val horizGood = accuracy < 10f
    val horizOk = accuracy < 25f

    val speedAccOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasSpeedAccuracy()) {
        speedAccuracyMetersPerSecond < 2.0f
    } else true

    return when {
        horizGood && speedAccOk -> SignalQuality.GOOD
        horizOk && speedAccOk -> SignalQuality.OK
        else -> SignalQuality.POOR
    }
}
