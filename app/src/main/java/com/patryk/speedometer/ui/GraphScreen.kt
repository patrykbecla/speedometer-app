package com.patryk.speedometer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import com.patryk.speedometer.SpeedViewModel
import com.patryk.speedometer.data.SpeedUnit
import com.patryk.speedometer.data.db.Sample
import com.patryk.speedometer.data.db.Session
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Legacy alias kept so callers using the name "GraphScreen" continue to compile.
// MainActivity uses this name in its when-block.
@Composable
fun GraphScreen(
    sessionId: Long,
    viewModel: SpeedViewModel,
    unit: SpeedUnit,
    onBack: () -> Unit,
    onExportCsv: () -> Unit,
    onExportGpx: () -> Unit,
    modifier: Modifier = Modifier,
) = SessionDetailScreen(
    sessionId = sessionId,
    viewModel = viewModel,
    unit = unit,
    onBack = onBack,
    onExportCsv = onExportCsv,
    onExportGpx = onExportGpx,
    modifier = modifier,
)

@Composable
fun SessionDetailScreen(
    sessionId: Long,
    viewModel: SpeedViewModel,
    unit: SpeedUnit,
    onBack: () -> Unit,
    onExportCsv: () -> Unit,
    onExportGpx: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session by viewModel.sessionFlow(sessionId).collectAsStateWithLifecycle(null)
    val samples by viewModel.sessionSamples(sessionId).collectAsStateWithLifecycle(emptyList())

    var selectedTab by remember { mutableIntStateOf(0) }
    var showExportMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // Toolbar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session?.label?.ifBlank { null }
                        ?: session?.startMs?.let { formatDate(it) }
                        ?: "Session",
                    style = MaterialTheme.typography.titleMedium,
                )
                session?.let {
                    Text(
                        text = buildStats(it, unit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                IconButton(onClick = { showExportMenu = true }) {
                    Icon(Icons.Default.Share, contentDescription = "Export")
                }
                DropdownMenu(
                    expanded = showExportMenu,
                    onDismissRequest = { showExportMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Export CSV") },
                        onClick = { onExportCsv(); showExportMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Export GPX") },
                        onClick = { onExportGpx(); showExportMenu = false },
                    )
                }
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Graph") },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Map") },
            )
        }

        when (selectedTab) {
            0 -> SpeedChart(
                samples = samples,
                unit = unit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            1 -> TrackMap(
                samples = samples,
                currentLatLng = null,
                maxSpeedMps = session?.maxSpeedMps ?: 0f,
                follow = false,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
fun SpeedChart(
    samples: List<Sample>,
    unit: SpeedUnit,
    modifier: Modifier = Modifier,
) {
    val producer = remember { ChartEntryModelProducer() }
    var hasData by remember { mutableStateOf(false) }
    var xAxisLabel by remember { mutableStateOf("seconds") }

    LaunchedEffect(samples, unit) {
        hasData = false
        // Find first sample with a real speed; anything before that is a
        // pre-GPS-lock artefact. If no non-zero sample exists, bail out.
        val first = samples.indexOfFirst { it.speedMps > 0f }
        if (first == -1) return@LaunchedEffect
        val chartSamples = if (first > 0) samples.drop(first) else samples

        val startMs = chartSamples.first().timestampMs
        val durationMs = chartSamples.last().timestampMs - startMs
        val useMinutes = durationMs >= 5 * 60 * 1000L
        xAxisLabel = if (useMinutes) "minutes" else "seconds"
        val xDivisor = if (useMinutes) 60_000f else 1_000f

        val samplesToPlot = try {
            lttb(chartSamples, 500)
        } catch (_: Exception) {
            chartSamples
        }
        val intervalMs = if (samplesToPlot.size > 1)
            (samplesToPlot.last().timestampMs - samplesToPlot.first().timestampMs) / (samplesToPlot.size - 1)
        else 1000L
        val smoothed = despike(samplesToPlot.map { it.speedMps }, intervalMs)
        val entries = samplesToPlot.indices.map { i ->
            entryOf(
                (samplesToPlot[i].timestampMs - startMs) / xDivisor,
                unit.convert(smoothed[i]),
            )
        }
        if (entries.isEmpty()) return@LaunchedEffect
        // setEntriesSuspending waits for Vico's background model transformation to finish
        // before returning, so cachedInternalModel is set when hasData = true and Chart
        // enters composition. setEntries() (non-suspend) starts the transformation async
        // and can complete before Chart subscribes, causing the model delivery to be missed.
        producer.setEntriesSuspending(listOf(entries))
        hasData = true
    }

    when {
        hasData -> Chart(
            chart = lineChart(),
            chartModelProducer = producer,
            startAxis = rememberStartAxis(title = unit.label),
            bottomAxis = rememberBottomAxis(title = xAxisLabel),
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        samples.isNotEmpty() -> Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) { Text("No GPS data recorded", style = MaterialTheme.typography.bodyLarge) }
        else -> Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
    }
}

private fun formatDate(ms: Long): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(ms))

private fun buildStats(session: Session, unit: SpeedUnit): String {
    val max = "%.1f ${unit.label}".format(unit.convert(session.maxSpeedMps))
    val avg = "%.1f ${unit.label}".format(unit.convert(session.avgSpeedMps))
    val dist = formatDistance(session.distanceM, unit)
    return "Max $max  ·  Avg $avg  ·  $dist"
}

/**
 * 3-point median filter with interval-gated downward filtering.
 *
 * Upward spikes (speed[i] above both neighbors) are always replaced with the
 * median — no vehicle sustains 2× its normal speed for exactly one sample then
 * instantly recovers, so these are always GPS errors.
 *
 * Downward dips (speed[i] below both neighbors, e.g. a glitched zero) are only
 * replaced when the sampling interval is ≤ 2 s. At longer intervals a genuine
 * stop (e.g. 5 s pause while jogging at 5 s sample rate) is a single-sample
 * dip that is indistinguishable from a glitch, so we leave it alone.
 */
private fun despike(speeds: List<Float>, samplingIntervalMs: Long): List<Float> {
    if (speeds.size < 3) return speeds
    return List(speeds.size) { i ->
        when {
            i == 0 || i == speeds.size - 1 -> speeds[i]
            else -> {
                val a = speeds[i - 1]; val b = speeds[i]; val c = speeds[i + 1]
                val median = maxOf(minOf(a, b), minOf(maxOf(a, b), c))
                when {
                    b > a && b > c -> median                              // upward: always filter
                    b < a && b < c && samplingIntervalMs <= 2_000L -> median  // downward: short interval only
                    else -> b
                }
            }
        }
    }
}

/**
 * Largest-Triangle-Three-Buckets downsampling. Reduces [samples] to at most [threshold] points
 * while preserving visually salient peaks and troughs. Uses sample index as the x-coordinate
 * for the triangle area calculation (timestamps are uniform enough that index works well).
 */
private fun lttb(samples: List<Sample>, threshold: Int): List<Sample> {
    if (samples.size <= threshold) return samples
    val result = ArrayList<Sample>(threshold)
    result.add(samples.first())

    val bucketSize = (samples.size - 2).toDouble() / (threshold - 2)
    var prevSelectedIdx = 0

    for (i in 1 until threshold - 1) {
        val bucketStart = ((i - 1) * bucketSize + 1).toInt()
        val bucketEnd = (i * bucketSize + 1).toInt().coerceAtMost(samples.size - 1)

        // Average of the next bucket — forms the apex of the triangle.
        val nextStart = bucketEnd
        val nextEnd = ((i + 1) * bucketSize + 1).toInt().coerceAtMost(samples.size)
        var sumY = 0.0
        var count = 0
        for (k in nextStart until nextEnd) { sumY += samples[k].speedMps; count++ }
        val avgNextX = (nextStart + nextEnd - 1) / 2.0
        val avgNextY = if (count > 0) sumY / count else 0.0

        val aX = prevSelectedIdx.toDouble()
        val aY = result.last().speedMps.toDouble()
        var maxArea = -1.0
        var selectedIdx = bucketStart

        for (j in bucketStart until bucketEnd) {
            val bX = j.toDouble(); val bY = samples[j].speedMps.toDouble()
            val area = Math.abs(
                aX * (bY - avgNextY) + bX * (avgNextY - aY) + avgNextX * (aY - bY)
            ) * 0.5
            if (area > maxArea) { maxArea = area; selectedIdx = j }
        }

        result.add(samples[selectedIdx])
        prevSelectedIdx = selectedIdx
    }

    result.add(samples.last())
    return result
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
