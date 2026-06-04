package com.patryk.speedometer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patryk.speedometer.data.SpeedUnit
import com.patryk.speedometer.data.db.Session
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    sessions: List<Session>,
    unit: SpeedUnit,
    onSessionClick: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onRenameSession: (Long, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deletingSession by remember { mutableStateOf<Session?>(null) }
    var renamingSession by remember { mutableStateOf<Session?>(null) }
    var renameText by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Session History") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No sessions recorded yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                ),
            ) {
                items(sessions, key = { it.id }) { session ->
                    SessionItem(
                        session = session,
                        unit = unit,
                        onClick = { onSessionClick(session.id) },
                        onDelete = { deletingSession = session },
                        onRename = { renamingSession = session; renameText = session.label },
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    deletingSession?.let { session ->
        AlertDialog(
            onDismissRequest = { deletingSession = null },
            title = { Text("Delete session?") },
            text = { Text("This will permanently delete the session and all its recorded data.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSession(session.id)
                    deletingSession = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSession = null }) { Text("Cancel") }
            },
        )
    }

    // Rename dialog
    renamingSession?.let { session ->
        AlertDialog(
            onDismissRequest = { renamingSession = null },
            title = { Text("Rename session") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Name") },
                    singleLine = true,
                    placeholder = { Text(formatDate(session.startMs)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenameSession(session.id, renameText.trim())
                    renamingSession = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingSession = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SessionItem(
    session: Session,
    unit: SpeedUnit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.label.ifBlank { formatDate(session.startMs) },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = buildSubtitle(session, unit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            IconButton(onClick = onRename) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Rename",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun buildSubtitle(session: Session, unit: SpeedUnit): String {
    val parts = mutableListOf<String>()
    if (session.endMs > session.startMs) parts += formatDuration(session.startMs, session.endMs)
    if (session.maxSpeedMps > 0f) parts += "Max %.1f ${unit.label}".format(unit.convert(session.maxSpeedMps))
    if (session.avgSpeedMps > 0f) parts += "Avg %.1f ${unit.label}".format(unit.convert(session.avgSpeedMps))
    if (session.distanceM >= 1.0) parts += formatDistance(session.distanceM, unit)
    return if (parts.isEmpty()) formatDate(session.startMs) else parts.joinToString("  ·  ")
}

private fun formatDate(ms: Long): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(ms))

private fun formatDuration(startMs: Long, endMs: Long): String {
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
