package com.patryk.speedometer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.MapType
import com.patryk.speedometer.SpeedViewModel
import com.patryk.speedometer.data.RecordingState
import kotlinx.coroutines.flow.flowOf

@Composable
fun LiveMapScreen(
    viewModel: SpeedViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val speedState by viewModel.speed.collectAsStateWithLifecycle()
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    var mapType by remember { mutableStateOf(MapType.TERRAIN) }

    val activeSessionId = (recordingState as? RecordingState.Recording)?.sessionId
    val liveSamples by remember(activeSessionId) {
        activeSessionId?.let { viewModel.sessionSamples(it) } ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(emptyList())

    val currentLatLng = if (speedState.lat != null && speedState.lng != null)
        LatLng(speedState.lat!!, speedState.lng!!) else null

    Box(modifier = modifier.fillMaxSize()) {
        TrackMap(
            samples = liveSamples,
            currentLatLng = currentLatLng,
            maxSpeedMps = speedState.maxSpeedMps,
            follow = true,
            mapType = mapType,
            modifier = Modifier.fillMaxSize(),
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }

        MapTypeToggle(
            current = mapType,
            onToggle = { mapType = it },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        )
    }
}
