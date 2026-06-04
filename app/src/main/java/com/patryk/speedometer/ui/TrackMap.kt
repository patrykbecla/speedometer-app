package com.patryk.speedometer.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.StrokeStyle
import com.google.android.gms.maps.model.StyleSpan
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.patryk.speedometer.data.db.Sample

private const val MAX_POLYLINE_POINTS = 500

@Composable
fun TrackMap(
    samples: List<Sample>,
    currentLatLng: LatLng?,
    maxSpeedMps: Float,
    follow: Boolean,
    mapType: MapType,
    modifier: Modifier = Modifier,
) {
    val cameraPositionState = rememberCameraPositionState()

    // Live mode: keep camera centered on current position
    LaunchedEffect(currentLatLng, follow) {
        if (follow && currentLatLng != null) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f))
        }
    }

    // History mode: fit the full track into view once samples arrive
    LaunchedEffect(samples, follow) {
        if (!follow && samples.size >= 2) {
            val boundsBuilder = LatLngBounds.Builder()
            samples.forEach { boundsBuilder.include(LatLng(it.lat, it.lng)) }
            try {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80)
                )
            } catch (_: Exception) {
                // Map not laid out yet; will retry when samples change again
            }
        }
    }

    val decimated = remember(samples) { decimate(samples, MAX_POLYLINE_POINTS) }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(mapType = mapType),
    ) {
        if (decimated.size >= 2) {
            val points = decimated.map { LatLng(it.lat, it.lng) }
            val spans = decimated.zipWithNext().map { (a, _) ->
                val t = if (maxSpeedMps > 0f) (a.speedMps / maxSpeedMps).coerceIn(0f, 1f) else 0f
                StyleSpan(StrokeStyle.colorBuilder(speedColor(t)).build())
            }
            Polyline(points = points, spans = spans, width = 10f)
        }

        if (currentLatLng != null) {
            Marker(state = MarkerState(position = currentLatLng))
        }
    }

    if (samples.isEmpty() && currentLatLng == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Acquiring GPS…", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun MapTypeToggle(
    current: MapType,
    onToggle: (MapType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val next = when (current) {
        MapType.TERRAIN -> MapType.SATELLITE
        MapType.SATELLITE -> MapType.NORMAL
        else -> MapType.TERRAIN
    }
    val label = when (current) {
        MapType.TERRAIN -> "Terrain"
        MapType.SATELLITE -> "Satellite"
        else -> "Normal"
    }
    ElevatedAssistChip(
        onClick = { onToggle(next) },
        label = { Text(label) },
        modifier = modifier,
        colors = AssistChipDefaults.elevatedAssistChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

private fun decimate(samples: List<Sample>, max: Int): List<Sample> {
    if (samples.size <= max) return samples
    val step = samples.size / max
    // Always include first and last for a complete track
    val decimated = samples.filterIndexed { i, _ -> i % step == 0 }.toMutableList()
    if (decimated.last() != samples.last()) decimated.add(samples.last())
    return decimated
}

private fun speedColor(t: Float): Int {
    // t=0 → green (slow), t=1 → red (fast)
    val hue = (1f - t) * 120f
    return AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 0.85f))
}
