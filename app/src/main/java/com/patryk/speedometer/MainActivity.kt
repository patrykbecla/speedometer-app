package com.patryk.speedometer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patryk.speedometer.data.RecordingState
import com.patryk.speedometer.service.RecordingService
import com.patryk.speedometer.ui.GraphScreen
import com.patryk.speedometer.ui.HistoryScreen
import com.patryk.speedometer.ui.LocationPermissionGate
import com.patryk.speedometer.ui.SpeedScreen
import com.patryk.speedometer.ui.SpeedometerTheme

sealed interface Screen {
    data object Speed : Screen
    data object History : Screen
    data class Graph(val sessionId: Long) : Screen
}

class MainActivity : ComponentActivity() {

    private val viewModel: SpeedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            SpeedometerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LocationPermissionGate {
                        AppContent(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppContent(viewModel: SpeedViewModel, modifier: Modifier = Modifier) {
    var navStack by remember { mutableStateOf(listOf<Screen>(Screen.Speed)) }
    val currentScreen = navStack.last()

    fun push(screen: Screen) { navStack = navStack + screen }
    fun pop() { if (navStack.size > 1) navStack = navStack.dropLast(1) }

    BackHandler(enabled = navStack.size > 1) { pop() }

    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val unit by viewModel.unit.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val samplingIntervalMs by viewModel.samplingIntervalMs.collectAsStateWithLifecycle()
    val lastSessionId by viewModel.lastSessionId.collectAsStateWithLifecycle()
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // Dismiss graph when a new recording starts
    LaunchedEffect(isRecording) {
        if (isRecording) navStack = listOf(Screen.Speed)
    }

    // Location collection tied to activity lifecycle (for the live readout)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.startLocationUpdates()
                Lifecycle.Event.ON_STOP -> viewModel.stopLocationUpdates()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopLocationUpdates()
        }
    }

    val bgLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* recording works via foreground service either way */ }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* best-effort; foreground service notification still shows on Android 13 */ }

    fun toggleRecording() {
        if (isRecording) {
            (recordingState as? RecordingState.Recording)?.let { viewModel.setLastSession(it.sessionId) }
            context.startService(RecordingService.stopIntent(context))
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            context.startForegroundService(RecordingService.startIntent(context))
        }
    }

    when (val screen = currentScreen) {
        Screen.Speed -> SpeedScreen(
            state = speed,
            unit = unit,
            isRecording = isRecording,
            samplingIntervalMs = samplingIntervalMs,
            onUnitChange = viewModel::setUnit,
            onRecordToggle = ::toggleRecording,
            onViewGraph = if (!isRecording && lastSessionId != null) {
                { push(Screen.Graph(lastSessionId!!)) }
            } else null,
            onOpenHistory = { push(Screen.History) },
            onResetStats = viewModel::resetStats,
            onIntervalChange = viewModel::setSamplingInterval,
            modifier = modifier,
        )
        Screen.History -> HistoryScreen(
            sessions = allSessions,
            unit = unit,
            onSessionClick = { id -> push(Screen.Graph(id)) },
            onDeleteSession = viewModel::deleteSession,
            onRenameSession = viewModel::renameSession,
            onBack = ::pop,
            modifier = modifier,
        )
        is Screen.Graph -> GraphScreen(
            sessionId = screen.sessionId,
            viewModel = viewModel,
            unit = unit,
            onBack = ::pop,
            onExportCsv = { viewModel.exportCsv(context, screen.sessionId) },
            onExportGpx = { viewModel.exportGpx(context, screen.sessionId) },
            modifier = modifier,
        )
    }
}
