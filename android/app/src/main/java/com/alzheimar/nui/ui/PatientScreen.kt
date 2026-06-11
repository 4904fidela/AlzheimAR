package com.alzheimar.nui.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.speech.tts.TextToSpeech
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.alzheimar.nui.AppState
import com.alzheimar.nui.ui.theme.EmeraldGreen
import com.alzheimar.nui.ui.theme.EmeraldLight
import com.alzheimar.nui.ui.theme.ObsidianBg
import com.alzheimar.nui.ui.theme.SosRed
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientScreen(state: AppState) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    
    // CameraX and ML Kit variables
    var faceBounds by remember { mutableStateOf<Rect?>(null) }
    var previewViewSize by remember { mutableStateOf(Size(0, 0)) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Navigation and path animation variables
    val infiniteTransition = rememberInfiniteTransition(label = "PathFlow")
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Offset"
    )

    // SOS Gesture Hold Variables
    var gestureProgress by remember { mutableFloatStateOf(0f) }
    var isHoldingGesture by remember { mutableStateOf(false) }

    // Trigger TTS when active scenario changes
    LaunchedEffect(state.activeScenario) {
        val confAudio = when (state.activeScenario) {
            "night-toilet" -> "Jalan ini akan menuntun Ibu."
            "make-tea" -> "Langkah berikutnya ada di sini."
            "face-recognition" -> "Budi, Cucu pertama Anda."
            "safe-zone" -> "Kita pulang dulu ya, Bu."
            "med-prompt" -> "Waktunya minum obat pagi, Ibu."
            else -> ""
        }
        if (confAudio.isNotEmpty()) {
            delay(500)
            state.speak(confAudio)
        }
    }

    // SOS hold loop simulator
    LaunchedEffect(isHoldingGesture) {
        if (isHoldingGesture) {
            val startTime = System.currentTimeMillis()
            while (isHoldingGesture && gestureProgress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                gestureProgress = (elapsed / 3000f).coerceAtMost(1f)
                delay(50)
            }
            if (gestureProgress >= 1f) {
                state.isSosActive = true
                state.addLog("SOS Gesture Triggered: 6-axis IMU detected sustained Closed-Fist SOS.")
                state.speak("Bantuan sedang dalam perjalanan. Tetap tenang.")
            }
        } else {
            gestureProgress = 0f
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        
        // 1. Live Camera Preview with Face Detector Analyzers
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    // Face detection analyzer using ML Kit
                    val faceDetectorOptions = FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .build()
                    val faceDetector = FaceDetection.getClient(faceDetectorOptions)

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy, faceDetector) { rect ->
                            if (state.activeScenario == "face-recognition" && !state.isSosActive) {
                                faceBounds = rect
                            } else {
                                faceBounds = null
                            }
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        state.addLog("Camera binding failed: ${e.message}")
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        // Record coordinates on tap to size calculations
                        previewViewSize = Size(size.width, size.height)
                    }
                }
        )

        // 2. HUD Safe Bounds Vignette Border overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                color = Color.Black.copy(alpha = 0.2f),
                size = size
            )
            // Draw visual corner brackets representing safe projection bounds
            val w = size.width
            val h = size.height
            val margin = 50f
            val l = 40f
            
            // Top Left Bracket
            drawLine(EmeraldGreen.copy(alpha = 0.2f), Offset(margin, margin), Offset(margin + l, margin), 3f)
            drawLine(EmeraldGreen.copy(alpha = 0.2f), Offset(margin, margin), Offset(margin, margin + l), 3f)
            // Top Right Bracket
            drawLine(EmeraldGreen.copy(alpha = 0.2f), Offset(w - margin, margin), Offset(w - margin - l, margin), 3f)
            drawLine(EmeraldGreen.copy(alpha = 0.2f), Offset(w - margin, margin), Offset(w - margin, margin + l), 3f)
            // Bottom Left Bracket
            drawLine(EmeraldGreen.copy(alpha = 0.2f), Offset(margin, h - margin), Offset(margin + l, h - margin), 3f)
            drawLine(EmeraldGreen.copy(alpha = 0.2f), Offset(margin, h - margin), Offset(margin, h - margin - l), 3f)
            // Bottom Right Bracket
            drawLine(EmeraldGreen.copy(alpha = 0.2f), Offset(w - margin, h - margin), Offset(w - margin - l, h - margin), 3f)
            drawLine(EmeraldGreen.copy(alpha = 0.2f), Offset(w - margin, h - margin), Offset(w - margin, h - margin - l), 3f)
        }

        // 3. Scenario Overlays Layer
        if (!state.isSosActive) {
            when (state.activeScenario) {
                "night-toilet" -> {
                    // Pulsing green path arrows overlaid at the bottom of the screen
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.6f)
                            .align(Alignment.BottomCenter)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val path = Path().apply {
                                val w = size.width
                                val h = size.height
                                moveTo(w * 0.5f, h * 0.9f)
                                quadraticBezierTo(w * 0.6f, h * 0.6f, w * 0.4f, h * 0.3f)
                            }
                            drawPath(
                                path = path,
                                color = EmeraldGreen,
                                style = Stroke(
                                    width = 12f,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                        floatArrayOf(30f, 25f),
                                        animatedOffset
                                    )
                                )
                            )
                        }
                    }
                    HudNotificationCard(
                        icon = Icons.Default.Navigation,
                        title = "Menuju Kamar Mandi (Toilet)",
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
                "make-tea" -> {
                    // Interactive hotspots overlays
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Step 1: Nyalakan Kompor
                        TaskBubble(
                            step = 1,
                            label = "Nyalakan kompor",
                            active = state.teaStep == 1,
                            offset = Offset(0.35f, 0.6f),
                            onClick = {
                                state.teaStep = 2
                                state.addLog("Patient completed step 1: Kompor dinyalakan.")
                                state.speak("Kompor sudah menyala. Silakan letakkan teko.")
                            }
                        )
                        // Step 2: Kettle - isi air
                        TaskBubble(
                            step = 2,
                            label = "Isi air ke teko",
                            active = state.teaStep == 2,
                            offset = Offset(0.55f, 0.45f),
                            onClick = {
                                state.teaStep = 3
                                state.addLog("Patient completed step 2: Teko diisi air.")
                                state.speak("Air sudah diisi. Letakkan daun teh.")
                            }
                        )
                        // Step 3: Ambil teh
                        TaskBubble(
                            step = 3,
                            label = "Ambil daun teh",
                            active = state.teaStep == 3,
                            offset = Offset(0.78f, 0.35f),
                            onClick = {
                                state.teaStep = 4
                                state.addLog("Patient completed step 3: Teh dimasukkan.")
                                state.speak("Teh dimasukkan. Siapkan cangkir.")
                            }
                        )
                        // Step 4: Ambil cangkir
                        TaskBubble(
                            step = 4,
                            label = "Siapkan cangkir",
                            active = state.teaStep == 4,
                            offset = Offset(0.75f, 0.55f),
                            onClick = {
                                state.teaStep = 1
                                state.addLog("Patient finished making tea tasks. Task guidance reset.")
                                state.speak("Teh hangat sudah selesai dibuat.")
                            }
                        )
                        
                        HudNotificationCard(
                            icon = Icons.Default.List,
                            title = "Panduan Membuat Teh (${state.teaStep}/4)",
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                }
                "face-recognition" -> {
                    // Render ML Kit Detected bounding box overlay
                    faceBounds?.let { bounds ->
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Scale coordinates from camera image sensor coordinates to display size
                            // Simple scaling calculation
                            val scaleX = size.width / 480f
                            val scaleY = size.height / 640f
                            
                            val left = bounds.left * scaleX
                            val top = bounds.top * scaleY
                            val right = bounds.right * scaleX
                            val bottom = bounds.bottom * scaleY
                            
                            // Draw corner crop lines for face frame
                            drawRect(
                                color = EmeraldGreen,
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                                style = Stroke(width = 6f)
                            )
                        }

                        // Overlay label above the bounding box
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                        ) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .offset(y = 100.dp)
                                    .background(ObsidianBg.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                                    .border(1.dp, EmeraldGreen, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Budi",
                                    color = EmeraldLight,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Cucu Pertama Anda",
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } ?: run {
                        // Fallback design if no physical face is detected in front of the lens
                        Box(
                            modifier = Modifier
                                .border(2.dp, EmeraldGreen, RoundedCornerShape(12.dp))
                                .align(Alignment.Center)
                                .size(200.dp)
                        ) {
                            Text(
                                text = "Arahkan kamera ke wajah orang terdaftar...",
                                color = EmeraldLight,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(16.dp)
                            )
                        }
                    }
                }
                "safe-zone" -> {
                    // Safe Zone geofence home navigational compass arrows
                    Box(modifier = Modifier.fillMaxSize()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            
                            // Emerald arrow path guidance
                            val path = Path().apply {
                                moveTo(w * 0.5f, h * 0.85f)
                                lineTo(w * 0.4f, h * 0.65f)
                                lineTo(w * 0.46f, h * 0.65f)
                                lineTo(w * 0.46f, h * 0.4f)
                                lineTo(w * 0.54f, h * 0.4f)
                                lineTo(w * 0.54f, h * 0.65f)
                                lineTo(w * 0.6f, h * 0.65f)
                                close()
                            }
                            drawPath(
                                path = path,
                                color = EmeraldGreen
                            )
                        }
                        HudNotificationCard(
                            icon = Icons.Default.Home,
                            title = "Arah pulang ke rumah",
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
                "med-prompt" -> {
                    // Clean middle overlay card
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                            .background(ObsidianBg.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                            .border(2.dp, EmeraldGreen, RoundedCornerShape(16.dp))
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MedicalServices,
                                contentDescription = "Obat",
                                tint = EmeraldLight,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Waktunya Obat Pagi",
                                color = EmeraldLight,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Ambil obat kapsul putih di meja makan",
                                color = Color.White,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            // SOS Active HUD Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .background(ObsidianBg.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                        .border(2.dp, SosRed, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val pulseTransition = rememberInfiniteTransition(label = "SosPulse")
                    val pulseScale by pulseTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = EaseOutQuad),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "Scale"
                    )

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(SosRed.copy(alpha = 0.15f), CircleShape)
                            .border(2.dp, SosRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneCallback,
                            contentDescription = "SOS Call",
                            tint = SosRed,
                            modifier = Modifier
                                .size(36.dp)
                                .size(36.dp * pulseScale)
                        )
                    }
                    Text(
                        text = "Menghubungi Ibu Sari",
                        color = SosRed,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Bantuan sedang dalam perjalanan",
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = {
                            state.isSosActive = false
                            state.addLog("SOS emergency alert dismissed by first responder.")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SosRed),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Batal SOS", color = Color.White)
                    }
                }
            }
        }

        // 4. Test Scenario Switcher Overlay panel at the top
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(8.dp)
                .align(Alignment.TopCenter),
            colors = CardDefaults.cardColors(containerColor = ObsidianBg.copy(alpha = 0.7f)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "Testing - Select Active AR State:",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val scenariosList = listOf(
                        "night-toilet" to Icons.Default.NightsStay,
                        "make-tea" to Icons.Default.Coffee,
                        "face-recognition" to Icons.Default.Face,
                        "safe-zone" to Icons.Default.PinDrop,
                        "med-prompt" to Icons.Default.MedicalServices
                    )
                    scenariosList.forEach { (key, icon) ->
                        IconButton(
                            onClick = {
                                state.activeScenario = key
                                // Sync coordinates and other metadata
                                when(key) {
                                    "night-toilet" -> {
                                        state.gpsCoordinates = "-6.2088, 106.8456"
                                        state.patientActivity = "Walking (Night)"
                                        state.isGeofenceBreached = false
                                        state.addLog("Active scenario switched to Night Toilet navigation.")
                                    }
                                    "make-tea" -> {
                                        state.gpsCoordinates = "-6.2089, 106.8455"
                                        state.patientActivity = "Cooking (Kitchen)"
                                        state.isGeofenceBreached = false
                                        state.addLog("Active scenario switched to Cooking task guide.")
                                    }
                                    "face-recognition" -> {
                                        state.gpsCoordinates = "-6.2089, 106.8455"
                                        state.patientActivity = "Sitting (Living Room)"
                                        state.isGeofenceBreached = false
                                        state.addLog("Active scenario switched to Face recognition assist.")
                                    }
                                    "safe-zone" -> {
                                        state.gpsCoordinates = "-6.2094, 106.8462"
                                        state.patientActivity = "Walking (Outdoor)"
                                        state.isGeofenceBreached = true
                                        state.addLog("Patient triggered Geofence threshold alert.")
                                    }
                                    "med-prompt" -> {
                                        state.gpsCoordinates = "-6.2088, 106.8456"
                                        state.patientActivity = "Standing (Bedroom)"
                                        state.isGeofenceBreached = false
                                        state.addLog("Active scenario switched to Medication Reminder prompt.")
                                    }
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (state.activeScenario == key) EmeraldGreen else Color.White.copy(alpha = 0.05f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Icon(icon, contentDescription = key, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // 5. Native Modality Simulator Buttons (Bottom Right)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Trigger 1: Voice Alert Simulation ("Tolong")
            FloatingActionButton(
                onClick = {
                    state.isSosActive = true
                    state.addLog("Voice Keyword Alert: 'Tolong' triggered by NPU.")
                    state.speak("Bantuan sedang dalam perjalanan. Tetap tenang.")
                },
                containerColor = SosRed.copy(alpha = 0.9f),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Voice SOS", modifier = Modifier.size(24.dp))
            }

            // Trigger 2: Gesture Hold SOS (Fist Raise 3s)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray.copy(alpha = 0.8f))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isHoldingGesture = true
                                tryAwaitRelease()
                                isHoldingGesture = false
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Circular progress indicator
                if (gestureProgress > 0f) {
                    CircularProgressIndicator(
                        progress = gestureProgress,
                        color = EmeraldGreen,
                        strokeWidth = 4.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Icon(
                    imageVector = Icons.Default.PanTool,
                    contentDescription = "Fist SOS",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun HudNotificationCard(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .padding(bottom = 64.dp, start = 16.dp, end = 16.dp)
            .height(56.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianBg.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, EmeraldGreen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = EmeraldLight,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                color = EmeraldLight,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun BoxScope.TaskBubble(
    step: Int,
    label: String,
    active: Boolean,
    offset: Offset,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1.15f else 1.0f,
        animationSpec = tween(300),
        label = "BubbleScale"
    )

    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxSize()
            .pointerInput(Unit) {}
            .offset(
                x = (offset.x * 280).dp, // basic proportional coordinate mapping
                y = (offset.y * 500).dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(42.dp * scale)
                .clip(CircleShape)
                .background(if (active) EmeraldGreen else ObsidianBg.copy(alpha = 0.8f))
                .border(2.dp, EmeraldGreen, CircleShape)
                .clickable(enabled = active) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step.toString(),
                color = if (active) Color.White else EmeraldLight,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
        if (active) {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianBg.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, EmeraldGreen),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = label,
                    color = EmeraldLight,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// Convert CameraX ImageProxy data streams into ML Kit Face Detector processor
@SuppressLint("UnsafeOptInUsageError")
private fun processImageProxy(
    imageProxy: ImageProxy,
    detector: com.google.mlkit.vision.face.FaceDetector,
    onFaceDetected: (Rect?) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    // Send coordinates of the primary detected face
                    onFaceDetected(faces[0].boundingBox)
                } else {
                    onFaceDetected(null)
                }
            }
            .addOnFailureListener {
                onFaceDetected(null)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}
