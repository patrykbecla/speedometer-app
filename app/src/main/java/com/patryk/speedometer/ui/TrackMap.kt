package com.patryk.speedometer.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.StrokeStyle
import com.google.android.gms.maps.model.StyleSpan
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.patryk.speedometer.data.db.Sample
import kotlinx.coroutines.launch

private const val MAX_POLYLINE_POINTS = 500
private val GOOGLE_BLUE = Color(0xFF1A73E8)

@Composable
fun TrackMap(
    samples: List<Sample>,
    currentLatLng: LatLng?,
    maxSpeedMps: Float,
    follow: Boolean,
    showMyLocation: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val cameraPositionState = rememberCameraPositionState()
    var hasInitiallyCentered by remember { mutableStateOf(false) }
    var mapType by remember { mutableStateOf(MapType.TERRAIN) }
    val scope = rememberCoroutineScope()

    // Live mode: center only on the first GPS fix; free pan after that
    LaunchedEffect(currentLatLng, follow) {
        if (follow && currentLatLng != null && !hasInitiallyCentered) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f))
            hasInitiallyCentered = true
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

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapType = mapType, isMyLocationEnabled = showMyLocation),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
            ),
        ) {
            if (decimated.size >= 2) {
                val points = decimated.map { LatLng(it.lat, it.lng) }
                val spans = decimated.zipWithNext().map { (a, _) ->
                    val t = if (maxSpeedMps > 0f) (a.speedMps / maxSpeedMps).coerceIn(0f, 1f) else 0f
                    StyleSpan(StrokeStyle.colorBuilder(speedColor(t)).build())
                }
                Polyline(points = points, spans = spans, width = 10f)
            }

            // Only show manual marker when not using the native blue-dot layer
            if (currentLatLng != null && !showMyLocation) {
                Marker(state = MarkerState(position = currentLatLng))
            }
        }

        if (samples.isEmpty() && currentLatLng == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Acquiring GPS…", style = MaterialTheme.typography.titleMedium)
            }
        }

        // Buttons: stacked in bottom-right, matching Google Maps design language
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            // Map type / layers button
            MapIconButton(
                onClick = {
                    mapType = if (mapType == MapType.TERRAIN) MapType.SATELLITE else MapType.TERRAIN
                },
            ) {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = "Map type",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Re-center button (live map only)
            if (showMyLocation) {
                MapIconButton(
                    onClick = {
                        currentLatLng?.let { latLng ->
                            scope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(latLng, 16f)
                                )
                            }
                        }
                    },
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "My location",
                        tint = GOOGLE_BLUE,
                    )
                }
            }
        }
    }
}

@Composable
private fun MapIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

private fun decimate(samples: List<Sample>, max: Int): List<Sample> {
    if (samples.size <= max) return samples
    val step = samples.size / max
    val decimated = samples.filterIndexed { i, _ -> i % step == 0 }.toMutableList()
    if (decimated.last() != samples.last()) decimated.add(samples.last())
    return decimated
}

private fun speedColor(t: Float): Int {
    val hue = (1f - t) * 120f  // 0f=red, 120f=green
    return AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 0.85f))
}
