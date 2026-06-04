package com.patryk.speedometer.ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patryk.speedometer.SpeedUiState
import com.patryk.speedometer.data.SignalQuality
import com.patryk.speedometer.data.SpeedUnit
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedScreen(
    state: SpeedUiState,
    unit: SpeedUnit,
    isRecording: Boolean,
    samplingIntervalMs: Long,
    onUnitChange: (SpeedUnit) -> Unit,
    onRecordToggle: () -> Unit,
    onViewGraph: (() -> Unit)?,
    onOpenHistory: () -> Unit,
    onOpenLiveMap: () -> Unit,
    onResetStats: () -> Unit,
    onIntervalChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettings by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top row: signal quality + history + settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isRecording) RecordingIndicator()
                SignalChip(quality = state.signalQuality, accuracyM = state.accuracyM)
            }
            Row {
                if (!isRecording) {
                    IconButton(onClick = onResetStats) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset stats")
                    }
                }
                IconButton(onClick = onOpenLiveMap) {
                    Icon(Icons.Default.Map, contentDescription = "Live map")
                }
                IconButton(onClick = onOpenHistory) {
                    Icon(Icons.Default.History, contentDescription = "Session history")
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }

        // Big readout — centred, takes remaining vertical space
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (state.acquiring) AcquiringState() else SpeedReadout(state = state, unit = unit)
        }

        // Stats grid — always visible, cells show "–" while acquiring
        StatsGrid(state = state, unit = unit)

        // "View last session" link
        if (onViewGraph != null) {
            TextButton(
                onClick = onViewGraph,
                modifier = Modifier.padding(top = 4.dp),
            ) { Text("View last session →") }
        } else {
            Spacer(modifier = Modifier.height(40.dp)) // keep layout stable
        }

        // Record button — full width, tall for gloved taps
        Button(
            onClick = onRecordToggle,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 0.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(
                if (isRecording) Icons.Default.StopCircle else Icons.Default.FiberManualRecord,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = if (isRecording) "STOP REC" else "START REC",
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        UnitSelector(
            selected = unit,
            onUnitChange = onUnitChange,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
        ) {
            SettingsSheet(
                intervalMs = samplingIntervalMs,
                onIntervalChange = { onIntervalChange(it); showSettings = false },
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    }
}

// ─── Readout ─────────────────────────────────────────────────────────────────

@Composable
private fun SpeedReadout(state: SpeedUiState, unit: SpeedUnit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatSpeed(state.smoothedSpeedMps ?: state.speedMps, unit),
            fontSize = 112.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 112.sp,
        )
        Text(
            text = unit.label,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AcquiringState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "–",
            fontSize = 112.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 112.sp,
        )
        Text(
            text = "Acquiring GPS…",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Cold fix can take 20–40s outdoors",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ─── Recording indicator ─────────────────────────────────────────────────────

@Composable
private fun RecordingIndicator() {
    val transition = rememberInfiniteTransition(label = "rec")
    val color by transition.animateColor(
        initialValue = Color(0xFFF44336),
        targetValue = Color(0x44F44336),
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "dot",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("●", color = color, fontSize = 18.sp)
        Text(
            text = " REC",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFFF44336),
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

// ─── Stats grid ──────────────────────────────────────────────────────────────

@Composable
private fun StatsGrid(state: SpeedUiState, unit: SpeedUnit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatCell("MAX", if (state.acquiring) "–" else formatSpeed(state.maxSpeedMps.takeIf { it > 0f }, unit, includeUnit = true))
            StatCell("AVG", if (state.acquiring) "–" else formatSpeed(state.avgSpeedMps.takeIf { it > 0f }, unit, includeUnit = true))
            StatCell("VERT", formatVertical(state.verticalSpeedMps, unit))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatCell("ALT", state.altitudeM?.let { formatAltitude(it, unit) } ?: "–")
            StatCell("DIST", if (state.acquiring) "–" else formatDistance(state.distanceM, unit))
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
        }
        Text(
            text = value.ifEmpty { " " },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ─── Signal quality ──────────────────────────────────────────────────────────

@Composable
private fun SignalChip(quality: SignalQuality, accuracyM: Float?) {
    val (dot, color) = when (quality) {
        SignalQuality.GOOD -> "●" to Color(0xFF4CAF50)
        SignalQuality.OK -> "●" to Color(0xFFFFC107)
        SignalQuality.POOR -> "●" to Color(0xFFF44336)
        SignalQuality.UNKNOWN -> "○" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = accuracyM?.let { "±%.0fm".format(it) } ?: "–"
    Text(
        text = "$dot GPS $label",
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

// ─── Unit selector ───────────────────────────────────────────────────────────

@Composable
private fun UnitSelector(
    selected: SpeedUnit,
    onUnitChange: (SpeedUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val units = SpeedUnit.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        units.forEachIndexed { index, unit ->
            SegmentedButton(
                selected = unit == selected,
                onClick = { onUnitChange(unit) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = units.size),
            ) { Text(unit.label) }
        }
    }
}

// ─── Settings sheet ──────────────────────────────────────────────────────────

private data class IntervalOption(val ms: Long, val label: String, val description: String)

private val INTERVALS = listOf(
    IntervalOption(500L,   "0.5s", "Max precision, but GPS may duplicate values vs 1s. Best for ski runs or takeoffs."),
    IntervalOption(1_000L, "1s",   "Standard GPS rate. Best balance of detail and battery for most activities."),
    IntervalOption(2_000L, "2s",   "Good detail with moderate battery use. Great for driving or cycling."),
    IntervalOption(5_000L, "5s",   "Low precision but minimal battery drain. Best for long hikes or runs."),
)

@Composable
private fun SettingsSheet(
    intervalMs: Long,
    onIntervalChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = INTERVALS.find { it.ms == intervalMs } ?: INTERVALS[1]
    Column(modifier = modifier) {
        Text("Recording interval", style = MaterialTheme.typography.titleMedium)
        Text(
            "Higher frequency = more detail, more battery drain.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            INTERVALS.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option.ms == intervalMs,
                    onClick = { onIntervalChange(option.ms) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = INTERVALS.size),
                ) { Text(option.label) }
            }
        }
        Text(
            text = selected.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

// ─── Formatters ──────────────────────────────────────────────────────────────

private fun formatSpeed(speedMps: Float?, unit: SpeedUnit, includeUnit: Boolean = false): String {
    if (speedMps == null) return "–"
    val formatted = String.format(Locale.US, "%.1f", unit.convert(speedMps))
    return if (includeUnit) "$formatted ${unit.label}" else formatted
}

private fun formatDistance(meters: Double, unit: SpeedUnit): String = when (unit) {
    SpeedUnit.MPH -> {
        val feet = meters * 3.28084
        if (feet < 5280.0) "%.0f ft".format(feet) else "%.2f mi".format(feet / 5280.0)
    }
    SpeedUnit.KMH -> if (meters < 1000.0) "%.0f m".format(meters)
                     else "%.2f km".format(meters / 1000.0)
    SpeedUnit.MPS -> if (meters < 1000.0) "%.0f m".format(meters)
                     else "%.2f km".format(meters / 1000.0)
}

private fun formatAltitude(meters: Double, unit: SpeedUnit): String = when (unit) {
    SpeedUnit.MPH -> {
        val feet = meters * 3.28084
        if (feet < 5280.0) "%.0f ft".format(feet) else "%.2f mi".format(feet / 5280.0)
    }
    SpeedUnit.KMH -> if (meters < 1000.0) "%.0f m".format(meters)
                     else "%.2f km".format(meters / 1000.0)
    SpeedUnit.MPS -> "%.0f m".format(meters)
}

private fun formatVertical(mps: Float?, unit: SpeedUnit): String {
    if (mps == null) return "–"
    val arrow = if (mps >= 0f) "▲" else "▼"
    return when (unit) {
        SpeedUnit.MPH -> "%s%.0f fpm".format(arrow, abs(mps) * 196.85f)
        else -> "%s%.1f m/s".format(arrow, abs(mps))
    }
}
