package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.SoundOptimizationSuggestion
import com.example.data.model.AudioPreset
import com.example.data.model.ChatMessage
import com.example.ui.viewmodel.EditorViewModel
import com.example.ui.viewmodel.VoiceSample
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.math.PI
import kotlin.math.sin

// Theme Custom Light High Density Studio Colors
val DeepSlateBg = Color(0xFFFDF8FF)
val CardSlateBg = Color(0xFFFFFFFF)
val AccentCyan = Color(0xFF6750A4)
val AccentCoral = Color(0xFFB13812)
val GridGreen = Color(0xFF2E7D32)
val TextGray = Color(0xFF49454F)

val VisualizerCardBg = Color(0xFFEADDFF)
val NeutralPanelBg = Color(0xFFF3EDF7)
val DeepPurple = Color(0xFF21005D)
val BorderColor = Color(0xFFCAC4D0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    // --- State collections ---
    val voiceSamples by viewModel.voiceSamples.collectAsStateWithLifecycle()
    val selectedSample by viewModel.selectedSample.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.playbackProgress.collectAsStateWithLifecycle()

    val isRecordingMic by viewModel.isRecordingMic.collectAsStateWithLifecycle()
    val recordingDuration by viewModel.recordingDuration.collectAsStateWithLifecycle()
    val recordingAmplitudes by viewModel.recordingAmplitudes.collectAsStateWithLifecycle()
    val isMimicking by viewModel.isMimicking.collectAsStateWithLifecycle()

    // Sliders
    val pitchShift by viewModel.pitchShift.collectAsStateWithLifecycle()
    val speedMultiplier by viewModel.speedMultiplier.collectAsStateWithLifecycle()
    val voiceVolume by viewModel.voiceVolume.collectAsStateWithLifecycle()
    val noiseReductionEnabled by viewModel.noiseReductionEnabled.collectAsStateWithLifecycle()
    val noiseReductionLevel by viewModel.noiseReductionLevel.collectAsStateWithLifecycle()
    val selectedBgmTrack by viewModel.selectedBgmTrack.collectAsStateWithLifecycle()
    val bgmVolume by viewModel.bgmVolume.collectAsStateWithLifecycle()
    val equalizerBass by viewModel.equalizerBass.collectAsStateWithLifecycle()
    val equalizerTreble by viewModel.equalizerTreble.collectAsStateWithLifecycle()

    // Statuses
    val isRendering by viewModel.isRendering.collectAsStateWithLifecycle()
    val renderProgress by viewModel.renderProgress.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    val savedPresets by viewModel.savedPresets.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()

    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val successMessage by viewModel.successMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Launchers for permissions and importing local files
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val resolver = context.contentResolver
            var name = "imported_audio.wav"
            try {
                resolver.query(it, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIdx)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            viewModel.importAudioFile(it, name)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (recordGranted) {
            viewModel.startMicRecording()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Microphone access is required to record voice audio.", duration = SnackbarDuration.Short)
            }
        }
    }

    // Trigger snackbars for feedback
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            viewModel.dismissError()
        }
    }
    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.dismissSuccess()
        }
    }

    Scaffold(
        modifier = modifier.background(DeepSlateBg),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(40.dp)
                                .background(AccentCyan, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Mixer Icon",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Vocalise Pro",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1C1B1F)
                            )
                            Text(
                                text = "AI ENGINE ACTIVE",
                                fontSize = 10.sp,
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.downloadAudioMix() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("export_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download Mix",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download Mix", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CardSlateBg,
                    titleContentColor = Color(0xFF1C1B1F)
                )
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = DeepSlateBg
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 14.dp, bottom = 40.dp)
            ) {
                // SECTION 1: Active Voice Sample Picker
                item {
                    Text(
                        text = "1. SELECT RAW CREATOR VOICE TRACK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextGray,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    VoiceTrackPicker(
                        samples = voiceSamples,
                        selectedSample = selectedSample,
                        isRecordingMic = isRecordingMic,
                        recordingDuration = recordingDuration,
                        recordingAmplitudes = recordingAmplitudes,
                        onSelected = { viewModel.selectVoiceSample(it) },
                        onRecordToggle = {
                            if (isRecordingMic) {
                                viewModel.stopMicRecording()
                            } else {
                                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                            }
                        },
                        onImportClick = {
                            importFileLauncher.launch("audio/*")
                        }
                    )
                }

                // SECTION 2: High fidelity Waveform Visualization Desk
                item {
                    Text(
                        text = "2. REAL-TIME MULTI-WAVE STUDIO MONITOR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextGray,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AudioWorkspaceMonitor(
                        isPlaying = isPlaying,
                        playbackProgress = playbackProgress,
                        pitchShift = pitchShift,
                        speedMultiplier = speedMultiplier,
                        voiceVolume = voiceVolume,
                        noiseReductionEnabled = noiseReductionEnabled,
                        noiseReductionLevel = noiseReductionLevel,
                        selectedBgmTrack = selectedBgmTrack,
                        bgmVolume = bgmVolume,
                        onPlayToggle = { viewModel.togglePlayback() }
                    )
                }

                // SECTION 3: Smart Audio mixing controls tab desk
                item {
                    Text(
                        text = "3. HARDWARE TUNING DESK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextGray,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    HardwareTuningDesk(
                        pitchShift = pitchShift,
                        speedMultiplier = speedMultiplier,
                        voiceVolume = voiceVolume,
                        noiseReductionEnabled = noiseReductionEnabled,
                        noiseReductionLevel = noiseReductionLevel,
                        selectedBgmTrack = selectedBgmTrack,
                        bgmVolume = bgmVolume,
                        equalizerBass = equalizerBass,
                        equalizerTreble = equalizerTreble,
                        savedPresets = savedPresets,
                        onPitchChange = viewModel::setPitchShift,
                        onSpeedChange = viewModel::setSpeedMultiplier,
                        onVoiceVolChange = viewModel::setVoiceVolume,
                        onNoiseEnableToggle = viewModel::setNoiseReductionEnabled,
                        onNoiseLevelChange = viewModel::setNoiseReductionLevel,
                        onBgmTrackChange = viewModel::setSelectedBgmTrack,
                        onBgmVolChange = viewModel::setBgmVolume,
                        onBassChange = viewModel::setEqualizerBass,
                        onTrebleChange = viewModel::setEqualizerTreble,
                        onSavePreset = { viewModel.saveCustomPreset(it) },
                        onLoadPreset = { viewModel.loadPreset(it) },
                        onDeletePreset = { viewModel.deletePreset(it) }
                    )
                }

                // SECTION 4: AI Smart Sound Optimization Assistant Chat
                item {
                    Text(
                        text = "4. VOXAI SMART MIXING ASSISTANT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextGray,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AiAssistantDeskCard(
                        chatMessages = chatMessages,
                        isAiLoading = isAiLoading,
                        onSendPrompt = { viewModel.askAiAssistant(it) },
                        onApplyPresetSuggestion = { json ->
                            try {
                                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                                val adapter = moshi.adapter(SoundOptimizationSuggestion::class.java)
                                val sug = adapter.fromJson(json)
                                if (sug != null) {
                                    viewModel.applySuggestion(sug)
                                }
                            } catch (e: Exception) {
                                // Ignore or report
                            }
                        },
                        onClearChat = { viewModel.clearHistory() }
                    )
                }

                // SECTION 5: AI Voice Cloning & Mimic Studio
                item {
                    Text(
                        text = "5. AI VOICE CLONING & MIMIC STUDIO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextGray,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    VoiceMimicStudioCard(
                        voiceSamples = voiceSamples,
                        isMimicking = isMimicking,
                        onMimicClick = { target, text -> viewModel.mimicVoice(target, text) }
                    )
                }
            }
        }

        // --- Simulated Rendering Dialog Overlay ---
        if (isRendering) {
            Dialog(onDismissRequest = {}) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardSlateBg),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = AccentCyan,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "AI Sound Processor",
                            color = Color(0xFF1C1B1F),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Merging layers, de-noising voice, adjusting EQ & rendering video... ($renderProgress%)",
                            color = TextGray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { renderProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = AccentCyan,
                            trackColor = BorderColor
                        )
                    }
                }
            }
        }
    }
}

// --- SUBCOMPONENT 1: RAW VOICE SAMPLE PICKER ---
fun formatDurationMs(ms: Long): String {
    val totalSec = ms / 1000L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    val tenths = (ms % 1000L) / 100L
    return "${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}.$tenths"
}

@Composable
fun LiveRecordingVisualizer(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val barWidth = 6.dp.toPx()
        val gap = 4.dp.toPx()
        val totalBarWidth = barWidth + gap
        val maxBars = (width / totalBarWidth).toInt()
        
        // Take only the last maxBars from amplitudes
        val visibleAmps = if (amplitudes.size > maxBars) {
            amplitudes.takeLast(maxBars)
        } else {
            amplitudes
        }
        
        // Center the wave vertically
        val centerY = height / 2f
        
        // Draw empty baseline guides if there are no amplitudes
        if (visibleAmps.isEmpty()) {
            val numDummyBars = (width / totalBarWidth).toInt()
            for (i in 0 until numDummyBars) {
                val x = i * totalBarWidth
                val barHeight = 4.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.15f),
                    topLeft = Offset(x, centerY - barHeight / 2f),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                )
            }
        } else {
            // Draw active bars from left to right as a scrolling wave
            val startOffset = width - (visibleAmps.size * totalBarWidth)
            visibleAmps.forEachIndexed { index, amp ->
                val x = startOffset + index * totalBarWidth
                // Calculate height, minimum 4.dp to keep it visible as a line
                val minHeight = 4.dp.toPx()
                val maxHeight = height - 16.dp.toPx()
                val barHeight = (amp * maxHeight).coerceAtLeast(minHeight)
                
                // Beautiful gradient for recording: coral to purple
                val brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFF5252), // Bright red/coral
                        Color(0xFFE040FB)  // Purple/magenta
                    )
                )
                
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(x, centerY - barHeight / 2f),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                )
            }
        }
    }
}

@Composable
fun VoiceTrackPicker(
    samples: List<VoiceSample>,
    selectedSample: VoiceSample,
    isRecordingMic: Boolean,
    recordingDuration: Long,
    recordingAmplitudes: List<Float>,
    onSelected: (VoiceSample) -> Unit,
    onRecordToggle: () -> Unit,
    onImportClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardSlateBg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (isRecordingMic) {
                // RENDER GORGEOUS STUDIO RECORDING PANEL
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF130526), Color(0xFF090214))
                            )
                        )
                        .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Header row with flashing red indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Flashing Dot Animation
                                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 0.3f,
                                    targetValue = 1.0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(600, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "alpha"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF5252).copy(alpha = alpha))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "LIVE STUDIO CAPTURE",
                                    color = Color(0xFFFF5252),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            
                            Surface(
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "AAC / 44.1 kHz",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Live Digital Counter
                        Text(
                            text = formatDurationMs(recordingDuration),
                            color = Color.White,
                            fontSize = 32.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        
                        Text(
                            text = "ELAPSED TIME",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Dynamic Waveform Visualizer
                        LiveRecordingVisualizer(
                            amplitudes = recordingAmplitudes,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Controls Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Stop & Save Button
                            Button(
                                onClick = onRecordToggle,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF5252),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(24.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(48.dp)
                                    .testTag("stop_recording_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop Recording",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "STOP & LOAD RAW INPUT",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Currently Editing Vocal Clip:",
                    color = AccentCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = selectedSample.name,
                color = Color(0xFF1C1B1F),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = selectedSample.description,
                color = TextGray,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Record & Import action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mic Record Button
                Button(
                    onClick = onRecordToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecordingMic) Color.Red else AccentCyan,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Icon(
                        imageVector = if (isRecordingMic) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Mic Icon",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isRecordingMic) "Stop" else "Record Mic",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // File Import Button
                Button(
                    onClick = onImportClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6750A4),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Folder Icon",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Import Local",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderColor, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Switch Vocal Clip Input:",
                color = TextGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            
            // Scrollable row for all voice samples
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                samples.forEach { sample ->
                    val isSelected = sample.id == selectedSample.id
                    Box(
                        modifier = Modifier
                            .width(105.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) VisualizerCardBg else Color.Transparent)
                            .border(
                                1.dp,
                                if (isSelected) AccentCyan else BorderColor,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelected(sample) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sample.name.substringBefore(" ").trim() + "\n" + sample.name.substringAfter(" ").trim(),
                            color = if (isSelected) AccentCyan else TextGray,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            lineHeight = 11.sp
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
fun VoiceMimicStudioCard(
    voiceSamples: List<VoiceSample>,
    isMimicking: Boolean,
    onMimicClick: (VoiceSample, String) -> Unit
) {
    var selectedTargetIndex by remember { mutableStateOf(0) }
    var scriptText by remember { mutableStateOf("") }

    val activeTargetSample = voiceSamples.getOrNull(selectedTargetIndex)

    Card(
        colors = CardDefaults.cardColors(containerColor = CardSlateBg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = "Voice Clone Icon",
                    tint = AccentCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Acoustic Voice Cloner",
                    color = Color(0xFF1C1B1F),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select any source voice style and type a script. VoxAI will mimic the selected tone to voice your script!",
                color = TextGray,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "1. Target Voice Style to Clone:",
                color = TextGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Selection row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                voiceSamples.forEachIndexed { index, sample ->
                    val isSelected = index == selectedTargetIndex
                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) AccentCyan.copy(alpha = 0.1f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (isSelected) AccentCyan else BorderColor,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedTargetIndex = index }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = sample.name,
                                color = if (isSelected) AccentCyan else Color(0xFF1C1B1F),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Noise: ${sample.baseNoiseLevel.toInt()}%",
                                color = TextGray,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "2. Script Text to Speak / Mimic:",
                color = TextGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = scriptText,
                onValueChange = { scriptText = it },
                placeholder = {
                    Text(
                        text = "e.g. Welcome to my custom high-fidelity audio studio! Let's clone this sound...",
                        fontSize = 11.sp,
                        color = TextGray
                    )
                },
                textStyle = TextStyle(color = Color(0xFF1C1B1F), fontSize = 12.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = Color(0xFF1C1B1F),
                    unfocusedTextColor = Color(0xFF1C1B1F)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    activeTargetSample?.let {
                        onMimicClick(it, scriptText)
                        scriptText = "" // clear
                    }
                },
                enabled = !isMimicking && scriptText.isNotBlank() && activeTargetSample != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCyan,
                    contentColor = Color.White,
                    disabledContainerColor = BorderColor
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                if (isMimicking) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mimicking Neural Resonance...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Mimic Button",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clone & Mimic Voice Style", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- SUBCOMPONENT 2: REAL-TIME WAVE VISUALIZER MONITOR ---
@Composable
fun AudioWorkspaceMonitor(
    isPlaying: Boolean,
    playbackProgress: Float,
    pitchShift: Float,
    speedMultiplier: Float,
    voiceVolume: Float,
    noiseReductionEnabled: Boolean,
    noiseReductionLevel: Float,
    selectedBgmTrack: String,
    bgmVolume: Float,
    onPlayToggle: () -> Unit
) {
    // Phase calculation to animate wave movement when playing
    val infiniteTransition = rememberInfiniteTransition(label = "wave_phase")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val activePhase = if (isPlaying) wavePhase else 0f

    Card(
        colors = CardDefaults.cardColors(containerColor = CardSlateBg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Waveform Screen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VisualizerCardBg)
                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
            ) {
                // Drawing Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val centerY = canvasHeight / 2f

                    // Draw oscilloscope matrix background lines
                    val gridSpacing = 30f
                    for (x in 0..(canvasWidth / gridSpacing).toInt()) {
                        drawLine(
                            color = BorderColor.copy(alpha = 0.4f),
                            start = Offset(x * gridSpacing, 0f),
                            end = Offset(x * gridSpacing, canvasHeight),
                            strokeWidth = 0.5f
                        )
                    }
                    for (y in 0..(canvasHeight / gridSpacing).toInt()) {
                        drawLine(
                            color = BorderColor.copy(alpha = 0.4f),
                            start = Offset(0f, y * gridSpacing),
                            end = Offset(canvasWidth, y * gridSpacing),
                            strokeWidth = 0.5f
                        )
                    }

                    // 1. Draw noise dots if noise reduction is off or partial
                    if (!noiseReductionEnabled || noiseReductionLevel < 100f) {
                        val maxNoisePoints = 60
                        // calculate active noise percentage based on sliders
                        val noiseFactor = if (noiseReductionEnabled) {
                            (100f - noiseReductionLevel) / 100f
                        } else {
                            0.7f // Ambient baseline noise
                        }
                        val pointsCount = (maxNoisePoints * noiseFactor).toInt()

                        // Use a simple seeded approach or deterministic loop to draw noise dots
                        for (i in 0..pointsCount) {
                            val noiseX = (canvasWidth * ((i * 17) % 100) / 100f)
                            val noiseY = centerY + (40f * sin(i.toFloat()) * ((i * 31) % 100 / 100f) * noiseFactor)
                            drawCircle(
                                color = TextGray.copy(alpha = 0.35f * noiseFactor),
                                radius = 2f,
                                center = Offset(noiseX, noiseY)
                            )
                        }
                    }

                    // 2. Draw BGM Wave if there's BGM
                    if (selectedBgmTrack != "none") {
                        val bgmPath = Path()
                        bgmPath.moveTo(0f, centerY)
                        val bgmPoints = 80
                        val bgmWaveAmp = 20f * bgmVolume * (centerY / 50f)
                        val bgmFrequency = 1.8f // very smooth low frequency

                        for (i in 0..bgmPoints) {
                            val x = canvasWidth * (i.toFloat() / bgmPoints)
                            // Simulate smooth ocean wave
                            val angle = (i.toFloat() / bgmPoints) * 2f * PI.toFloat() * bgmFrequency - (activePhase * 0.4f)
                            val y = centerY + sin(angle) * bgmWaveAmp
                            bgmPath.lineTo(x, y)
                        }
                        drawPath(
                            path = bgmPath,
                            color = AccentCoral.copy(alpha = 0.55f),
                            style = Stroke(width = 3f)
                        )
                    }

                    // 3. Draw main Vocal wave
                    val vocalPath = Path()
                    vocalPath.moveTo(0f, centerY)
                    val vocalPoints = 120
                    // Amplitude responds to voiceVolume. Compress height slightly to avoid clipping.
                    val vocalAmp = 40f * voiceVolume * (centerY / 60f)
                    // Speed multiplier speeds up wave oscillation visually. Pitch multiplier makes it wavy.
                    val baseFreq = 3.5f + (pitchShift * 0.3f)
                    val frequency = baseFreq * (1.0f + (speedMultiplier - 1.0f) * 0.4f)

                    for (i in 0..vocalPoints) {
                        val x = canvasWidth * (i.toFloat() / vocalPoints)
                        // Modulate sine wave to look like actual speech patterns
                        val envelope = sin((i.toFloat() / vocalPoints) * PI.toFloat()) // zero at ends, peak in middle
                        val angle = (i.toFloat() / vocalPoints) * 2f * PI.toFloat() * frequency - activePhase
                        // Combine basic sine with some harmonics for vocal texture
                        val waveValue = sin(angle) * 0.8f + sin(angle * 2) * 0.2f
                        val y = centerY + (waveValue * vocalAmp * envelope)
                        vocalPath.lineTo(x, y)
                    }

                    drawPath(
                        path = vocalPath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(AccentCyan, DeepPurple, AccentCyan)
                        ),
                        style = Stroke(width = 4f)
                    )

                    // 4. Draw timeline playhead line
                    val playheadX = canvasWidth * playbackProgress
                    drawLine(
                        color = DeepPurple,
                        start = Offset(playheadX, 0f),
                        end = Offset(playheadX, canvasHeight),
                        strokeWidth = 2.5f
                    )
                }

                // Small badge indicators on visualizer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .align(Alignment.BottomEnd),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (noiseReductionEnabled) {
                        Surface(
                            color = GridGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "NR ACTIVE (${noiseReductionLevel.toInt()}%)",
                                color = GridGreen,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (selectedBgmTrack != "none") {
                        Surface(
                            color = AccentCoral.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "BGM: ${selectedBgmTrack.uppercase()}",
                                color = AccentCoral,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Playback controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPlayToggle,
                    modifier = Modifier
                        .size(36.dp)
                        .background(AccentCyan, CircleShape)
                        .testTag("play_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    LinearProgressIndicator(
                        progress = { playbackProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = AccentCyan,
                        trackColor = BorderColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isPlaying) "STUDIO LIVE MONITOR" else "PAUSED",
                            fontSize = 9.sp,
                            color = if (isPlaying) GridGreen else TextGray,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(playbackProgress * 100).toInt()}%",
                            fontSize = 9.sp,
                            color = TextGray
                        )
                    }
                }
            }
        }
    }
}

// --- SUBCOMPONENT 3: HARDWARE TUNING DESK WITH TABS ---
@Composable
fun HardwareTuningDesk(
    pitchShift: Float,
    speedMultiplier: Float,
    voiceVolume: Float,
    noiseReductionEnabled: Boolean,
    noiseReductionLevel: Float,
    selectedBgmTrack: String,
    bgmVolume: Float,
    equalizerBass: Float,
    equalizerTreble: Float,
    savedPresets: List<AudioPreset>,
    onPitchChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onVoiceVolChange: (Float) -> Unit,
    onNoiseEnableToggle: (Boolean) -> Unit,
    onNoiseLevelChange: (Float) -> Unit,
    onBgmTrackChange: (String) -> Unit,
    onBgmVolChange: (Float) -> Unit,
    onBassChange: (Float) -> Unit,
    onTrebleChange: (Float) -> Unit,
    onSavePreset: (String) -> Unit,
    onLoadPreset: (AudioPreset) -> Unit,
    onDeletePreset: (AudioPreset) -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("🎤 Pitch & Speed", "🛡️ Noise & EQ", "🎵 Music Mixer", "💾 Presets")

    Card(
        colors = CardDefaults.cardColors(containerColor = CardSlateBg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Scrollable / standard Tab Row
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = NeutralPanelBg,
                contentColor = AccentCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = AccentCyan
                    )
                },
                divider = { HorizontalDivider(color = BorderColor, thickness = 1.dp) }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeTab == index) AccentCyan else TextGray,
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            Column(modifier = Modifier.padding(14.dp)) {
                when (activeTab) {
                    0 -> { // Pitch & Speed Tuning
                        Text(
                            "Vocal Pitch Tuner",
                            color = Color(0xFF1C1B1F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Slider(
                                value = pitchShift,
                                onValueChange = onPitchChange,
                                valueRange = -6f..6f,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = AccentCyan,
                                    inactiveTrackColor = BorderColor,
                                    thumbColor = AccentCyan
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pitch_slider")
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "${if (pitchShift > 0) "+" else ""}${String.format("%.1f", pitchShift)} st",
                                color = Color(0xFF1C1B1F),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(55.dp),
                                textAlign = TextAlign.End
                            )
                        }
                        Text(
                            "Modulate the timber and register of the vocals (deep vocal vs chipmunk style).",
                            color = TextGray,
                            fontSize = 9.sp,
                            lineHeight = 11.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "Vocal Speed Multiplier",
                            color = Color(0xFF1C1B1F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = speedMultiplier,
                                onValueChange = onSpeedChange,
                                valueRange = 0.5f..2.0f,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = AccentCyan,
                                    inactiveTrackColor = BorderColor,
                                    thumbColor = AccentCyan
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "${String.format("%.2f", speedMultiplier)}x",
                                color = Color(0xFF1C1B1F),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(55.dp),
                                textAlign = TextAlign.End
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "Vocal Gain Boost (Volume)",
                            color = Color(0xFF1C1B1F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = voiceVolume,
                                onValueChange = onVoiceVolChange,
                                valueRange = 0.0f..2.0f,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = AccentCyan,
                                    inactiveTrackColor = BorderColor,
                                    thumbColor = AccentCyan
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "${(voiceVolume * 100).toInt()}%",
                                color = Color(0xFF1C1B1F),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(55.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }

                    1 -> { // Noise & EQ Tuning
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Remove Ambient Background Noise",
                                    color = Color(0xFF1C1B1F),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Instantly erase hum, fan static, wind, or clicking noises.",
                                    color = TextGray,
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp
                                )
                            }
                            Switch(
                                checked = noiseReductionEnabled,
                                onCheckedChange = onNoiseEnableToggle,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentCyan,
                                    uncheckedThumbColor = TextGray,
                                    uncheckedTrackColor = BorderColor
                                ),
                                modifier = Modifier.testTag("noise_switch")
                            )
                        }

                        if (noiseReductionEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "De-Noise Threshold level",
                                color = Color(0xFF1C1B1F),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Slider(
                                    value = noiseReductionLevel,
                                    onValueChange = onNoiseLevelChange,
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = AccentCyan,
                                        inactiveTrackColor = BorderColor,
                                        thumbColor = AccentCyan
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "${noiseReductionLevel.toInt()}%",
                                    color = Color(0xFF1C1B1F),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(55.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = BorderColor, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Equalizer Bass & Treble
                        Text(
                            "Vocal Equalizer Controls",
                            color = Color(0xFF1C1B1F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            "Vocal Bass Boost (Warmth / Girth)",
                            color = TextGray,
                            fontSize = 10.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = equalizerBass,
                                onValueChange = onBassChange,
                                valueRange = -10f..10f,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = AccentCyan,
                                    inactiveTrackColor = BorderColor,
                                    thumbColor = AccentCyan
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "${if(equalizerBass>0) "+" else ""}${String.format("%.1f", equalizerBass)} dB",
                                color = Color(0xFF1C1B1F),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(55.dp),
                                textAlign = TextAlign.End
                            )
                        }

                        Text(
                            "Vocal Treble Boost (Presence / Clarity)",
                            color = TextGray,
                            fontSize = 10.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = equalizerTreble,
                                onValueChange = onTrebleChange,
                                valueRange = -10f..10f,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = AccentCyan,
                                    inactiveTrackColor = BorderColor,
                                    thumbColor = AccentCyan
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "${if(equalizerTreble>0) "+" else ""}${String.format("%.1f", equalizerTreble)} dB",
                                color = Color(0xFF1C1B1F),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(55.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }

                    2 -> { // Music Mixer
                        Text(
                            "Select Background Music (BGM)",
                            color = Color(0xFF1C1B1F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val bgmTracks = listOf(
                            "none" to "❌ No Background Music",
                            "lofi" to "☕ Sunset Chill Lofi Beats",
                            "epic" to "🎬 Cinematic Orchestral Build",
                            "upbeat" to "⚡ High-Energy Vlog House",
                            "acoustic" to "🎸 Warm Acoustic Folk Indie"
                        )

                        bgmTracks.forEach { (key, title) ->
                            val isSelected = selectedBgmTrack == key
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) VisualizerCardBg else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (isSelected) AccentCyan else BorderColor,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onBgmTrackChange(key) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onBgmTrackChange(key) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = AccentCyan,
                                        unselectedColor = BorderColor
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = title,
                                    color = if (isSelected) DeepPurple else TextGray,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        if (selectedBgmTrack != "none") {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Background Music Volume (Blend)",
                                color = Color(0xFF1C1B1F),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Slider(
                                    value = bgmVolume,
                                    onValueChange = onBgmVolChange,
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = AccentCyan,
                                        inactiveTrackColor = BorderColor,
                                        thumbColor = AccentCyan
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "${(bgmVolume * 100).toInt()}%",
                                    color = Color(0xFF1C1B1F),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(55.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }

                    3 -> { // Presets Saving / Loading
                        var newPresetName by remember { mutableStateOf("") }
                        Text(
                            "Save Current Audio Configuration:",
                            color = Color(0xFF1C1B1F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = newPresetName,
                                onValueChange = { newPresetName = it },
                                placeholder = { Text("e.g. YouTube Podcast Clear", fontSize = 11.sp, color = TextGray) },
                                textStyle = TextStyle(color = Color(0xFF1C1B1F), fontSize = 12.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentCyan,
                                    unfocusedBorderColor = BorderColor,
                                    focusedTextColor = Color(0xFF1C1B1F),
                                    unfocusedTextColor = Color(0xFF1C1B1F)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("preset_name_input")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newPresetName.isNotBlank()) {
                                        onSavePreset(newPresetName)
                                        newPresetName = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("preset_save_button")
                            ) {
                                Text("Save", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = BorderColor, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            "Select Studio Mixing Preset:",
                            color = Color(0xFF1C1B1F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (savedPresets.isEmpty()) {
                            Text(
                                "No custom presets created yet.",
                                color = TextGray,
                                fontSize = 11.sp
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(savedPresets) { preset ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(NeutralPanelBg)
                                            .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                                            .clickable { onLoadPreset(preset) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = preset.name,
                                                color = Color(0xFF1C1B1F),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (preset.isSystem) "System Template" else "User Preset",
                                                color = if (preset.isSystem) AccentCyan else GridGreen,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { onLoadPreset(preset) },
                                                modifier = Modifier.size(28.dp)
                                              ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Apply Preset",
                                                    tint = GridGreen,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            if (!preset.isSystem) {
                                                IconButton(
                                                    onClick = { onDeletePreset(preset) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete Preset",
                                                        tint = AccentCoral,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- SUBCOMPONENT 4: AI ASSISTANT CHAT TERMINAL ---
@Composable
fun AiAssistantDeskCard(
    chatMessages: List<ChatMessage>,
    isAiLoading: Boolean,
    onSendPrompt: (String) -> Unit,
    onApplyPresetSuggestion: (String) -> Unit,
    onClearChat: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()

    // Keep chat scrolled to bottom
    LaunchedEffect(chatMessages.size, isAiLoading) {
        if (chatMessages.isNotEmpty()) {
            lazyListState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    val quickActionPrompts = listOf(
        "🎙️ Broadcaster Voice" to "Optimize my vocals for an rich deep movie-announcer broadcaster format.",
        "🌍 Outdoor Vlog Fix" to "Fix outdoor vlog background noise, wind static, and suggest cinematic BGM.",
        "⚡ TikTok Fast Speed" to "Boost speech speed for high-speed shorts, enhance voice clarity, and add upbeat music.",
        "🎤 De-hum Podcast" to "De-noise a strong background hum static, boost EQ bass and treble for standard podcast dialogue."
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = CardSlateBg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (isAiLoading) AccentCyan else GridGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAiLoading) "VoxAI is analyzing mixing board..." else "VoxAI Assistant Active",
                        color = Color(0xFF1C1B1F),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Clear",
                    color = AccentCoral,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onClearChat() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chat Terminal Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeutralPanelBg)
                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(chatMessages) { msg ->
                        val isUser = msg.sender == "user"
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                        ) {
                            Text(
                                text = if (isUser) "You" else "VoxAI",
                                fontSize = 8.sp,
                                color = if (isUser) AccentCyan else AccentCoral,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                            Surface(
                                color = if (isUser) VisualizerCardBg else Color.White,
                                shape = RoundedCornerShape(
                                    topStart = 8.dp,
                                    topEnd = 8.dp,
                                    bottomStart = if (isUser) 8.dp else 0.dp,
                                    bottomEnd = if (isUser) 0.dp else 8.dp
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isUser) AccentCyan else BorderColor
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = msg.content,
                                        color = Color(0xFF1C1B1F),
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp
                                    )

                                    if (msg.isSuggestion && msg.suggestionJson != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { onApplyPresetSuggestion(msg.suggestionJson) },
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .height(28.dp)
                                                .align(Alignment.End)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Bolt,
                                                contentDescription = "Apply Preset Icon",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "Apply Suggested Mix",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isAiLoading) {
                        item {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = AccentCyan,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "VoxAI is balancing audio desk...",
                                    color = TextGray,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick actions buttons
            Text(
                text = "Tap Quick Optimize Goals:",
                color = TextGray,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 68.dp)
            ) {
                // chunking 2-by-2 items manually to keep grid simple inside LazyColumn
                val pairs = quickActionPrompts.chunked(2)
                items(pairs) { rowList ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowList.forEach { (label, promptText) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                                    .clickable { onSendPrompt(promptText) }
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = label,
                                    color = AccentCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Text Input Box
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Ask VoxAI (e.g. make me sound warmer, boost background music volume)", fontSize = 11.sp, color = TextGray) },
                    textStyle = TextStyle(color = Color(0xFF1C1B1F), fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = Color(0xFF1C1B1F),
                        unfocusedTextColor = Color(0xFF1C1B1F)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("ai_input_field"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            onSendPrompt(textInput)
                            textInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .background(AccentCyan, RoundedCornerShape(12.dp))
                        .testTag("ai_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
