package com.patryk.speedometer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.chart.layout.HorizontalLayout
import com.patrykandpatrick.vico.core.chart.values.AxisValuesOverrider
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import com.patryk.speedometer.SpeedViewModel
import com.patryk.speedometer.data.SpeedUnit
import com.patryk.speedometer.data.db.Sample
import com.patryk.speedometer.data.db.Session
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

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

        session?.let { SessionStatsPanel(it, unit) }

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

/**
 * Glanceable header of critical session stats — max, avg, distance, duration — in a single
 * row of large high-contrast numbers above the graph.
 */
@Composable
fun SessionStatsPanel(
    session: Session,
    unit: SpeedUnit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatCell("%.1f".format(unit.convert(session.maxSpeedMps)), "MAX ${unit.label}")
            StatCell("%.1f".format(unit.convert(session.avgSpeedMps)), "AVG ${unit.label}")
            StatCell(formatDistance(session.distanceM, unit), "DIST")
            StatCell(formatDuration(session.startMs, session.endMs), "TIME")
        }
        HorizontalDivider()
    }
}

@Composable
private fun StatCell(value: String, caption: String) {
    Column(
        modifier = Modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
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
    // Axis bounds + label spacing, computed in the effect and read by the Chart below.
    var maxX by remember { mutableFloatStateOf(1f) }
    var niceMaxY by remember { mutableFloatStateOf(1f) }
    var yLabelCount by remember { mutableIntStateOf(2) }
    var yStep by remember { mutableFloatStateOf(1f) }
    var pointsPerStep by remember { mutableIntStateOf(10) }
    var xUnitPerBin by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(samples, unit) {
        hasData = false
        // Find first sample with a real speed; anything before that is a
        // pre-GPS-lock artefact. If no non-zero sample exists, bail out.
        val first = samples.indexOfFirst { it.speedMps > 0f }
        if (first == -1) return@LaunchedEffect
        val chartSamples = if (first > 0) samples.drop(first) else samples
        if (chartSamples.size < 2) return@LaunchedEffect

        val startMs = chartSamples.first().timestampMs
        val durationMs = chartSamples.last().timestampMs - startMs
        if (durationMs <= 0L) return@LaunchedEffect
        val useMinutes = durationMs >= 5 * 60 * 1000L
        xAxisLabel = if (useMinutes) "minutes" else "seconds"
        val xDivisor = if (useMinutes) 60_000f else 1_000f

        // Despike at native resolution first: single-sample GPS error spikes are removed
        // while genuine multi-sample peaks survive into the resample below.
        val nativeIntervalMs = (durationMs / (chartSamples.size - 1)).coerceAtLeast(1L)
        val despiked = despike(chartSamples.map { it.speedMps }, nativeIntervalMs)

        // Choose a "nice" time tick, then a uniform grid whose spacing divides it evenly,
        // so X labels land on round multiples (0, xStep, 2·xStep …).
        val durationUnits = durationMs / xDivisor
        val xStep = niceStep(durationUnits, 6)
        val pps = (300f / (durationUnits / xStep)).roundToInt().coerceIn(5, 60)
        val binWidthUnits = xStep / pps
        val binWidthMs = binWidthUnits * xDivisor
        val binCount = ceil(durationUnits / binWidthUnits).toInt().coerceAtLeast(2)

        // Peak-preserving uniform resample: each bin keeps the max speed within it.
        val binSpeeds = resampleMaxPerBin(chartSamples, despiked, startMs, binWidthMs, binCount)
        // X is the integer bin INDEX — Vico rejects x values with >2 decimal places, and a
        // fractional bin width (e.g. 10/60 min) would. The bottom-axis formatter multiplies
        // the index back into real time units for the label text.
        val entries = binSpeeds.indices.map { i ->
            entryOf(i.toFloat(), unit.convert(binSpeeds[i]))
        }
        if (entries.isEmpty()) return@LaunchedEffect

        val maxPlottedY = entries.maxOf { it.y }
        val (nMax, count, step) = niceSpeedAxis(maxPlottedY)
        niceMaxY = nMax
        yLabelCount = count
        yStep = step
        maxX = (binSpeeds.size - 1).toFloat()
        xUnitPerBin = binWidthUnits
        pointsPerStep = pps

        // setEntriesSuspending waits for Vico's background model transformation to finish
        // before returning, so cachedInternalModel is set when hasData = true and Chart
        // enters composition. setEntries() (non-suspend) starts the transformation async
        // and can complete before Chart subscribes, causing the model delivery to be missed.
        producer.setEntriesSuspending(listOf(entries))
        hasData = true
    }

    when {
        hasData -> BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Size per-point spacing so the whole session's natural width ≈ the plot width.
            // At the default (minimum) zoom the entire session fits — AutoScaleUp.Full fills
            // any slack — and pinch-zoom then expands beyond it with scrolling to inspect
            // detail. (56.dp reserves room for the start axis + its labels.)
            val plotWidth = (maxWidth - 56.dp).coerceAtLeast(1.dp)
            val pointSpacing = (plotWidth / maxX).coerceAtLeast(0.5.dp)
            Chart(
                chart = lineChart(
                    spacing = pointSpacing,
                    axisValuesOverrider = AxisValuesOverrider.fixed(
                        minX = 0f, maxX = maxX, minY = 0f, maxY = niceMaxY,
                    ),
                ),
                chartModelProducer = producer,
                startAxis = rememberStartAxis(
                    title = unit.label,
                    itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = yLabelCount),
                    valueFormatter = { v, _ -> formatAxisInt(v, yStep) },
                ),
                bottomAxis = rememberBottomAxis(
                    title = "Time ($xAxisLabel)",
                    itemPlacer = AxisItemPlacer.Horizontal.default(spacing = pointsPerStep),
                    valueFormatter = { v, _ -> "%.0f".format(v * xUnitPerBin) },
                ),
                // Scroll + zoom enabled (defaults); the spacing above makes the default
                // view the fully-zoomed-out session.
                horizontalLayout = HorizontalLayout.FullWidth(),
                modifier = Modifier.fillMaxSize(),
            )
        }
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

private fun formatDuration(startMs: Long, endMs: Long): String {
    if (endMs <= startMs) return "–"
    val totalSecs = (endMs - startMs) / 1000
    val hours = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return when {
        hours > 0 -> "${hours}h ${mins}m"
        mins > 0 -> "${mins}m ${secs}s"
        else -> "${secs}s"
    }
}

/**
 * Resample [speeds] (parallel to [samples], despiked) onto a uniform time grid of [binCount]
 * bins of [binWidthMs] starting at [startMs]. Each bin keeps the MAXIMUM speed of the samples
 * that fall in it (peak-preserving); empty bins carry the previous bin's value forward.
 */
private fun resampleMaxPerBin(
    samples: List<Sample>,
    speeds: List<Float>,
    startMs: Long,
    binWidthMs: Float,
    binCount: Int,
): FloatArray {
    val bins = FloatArray(binCount) { Float.NaN }
    for (i in samples.indices) {
        val rel = samples[i].timestampMs - startMs
        val idx = (rel / binWidthMs).toInt().coerceIn(0, binCount - 1)
        val s = speeds[i]
        if (bins[idx].isNaN() || s > bins[idx]) bins[idx] = s
    }
    var last = 0f
    for (i in 0 until binCount) {
        if (bins[i].isNaN()) bins[i] = last else last = bins[i]
    }
    return bins
}

/** Smallest "nice" step (1·10ⁿ, 2·10ⁿ, 5·10ⁿ) giving at most [targetTicks] intervals over [range]. */
private fun niceStep(range: Float, targetTicks: Int): Float {
    if (range <= 0f) return 1f
    val raw = range / targetTicks
    val mag = 10f.pow(floor(log10(raw.toDouble())).toFloat())
    return when {
        1f * mag >= raw -> 1f * mag
        2f * mag >= raw -> 2f * mag
        5f * mag >= raw -> 5f * mag
        else -> 10f * mag
    }
}

/** Returns (niceMax, labelCount, step) for a Y axis starting at 0 and ending on a round multiple. */
private fun niceSpeedAxis(maxVal: Float): Triple<Float, Int, Float> {
    if (maxVal <= 0f) return Triple(1f, 2, 1f)
    val step = niceStep(maxVal, 6)
    val niceMax = ceil(maxVal / step) * step
    val count = (niceMax / step).toInt() + 1
    return Triple(niceMax, count, step)
}

private fun formatAxisInt(value: Float, step: Float): String =
    if (step >= 1f) "%.0f".format(value) else "%.1f".format(value)

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
