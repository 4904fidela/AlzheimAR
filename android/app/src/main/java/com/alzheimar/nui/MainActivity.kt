package com.alzheimar.nui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.alzheimar.nui.ui.CaregiverScreen
import com.alzheimar.nui.ui.PatientScreen
import com.alzheimar.nui.ui.theme.AlzheimARTheme
import com.alzheimar.nui.ui.theme.ObsidianBg
import java.text.SimpleDateFormat
import java.util.*

// Global App State to sync Patient activity and Caregiver dashboard in real-time
class AppState {
    var activeScenario by mutableStateOf("night-toilet")
    var gpsCoordinates by mutableStateOf("-6.2088, 106.8456")
    var patientActivity by mutableStateOf("Walking (Night)")
    var isGeofenceBreached by mutableStateOf(false)
    var isSosActive by mutableStateOf(false)
    var batteryPercentage by mutableStateOf(85)
    var teaStep by mutableIntStateOf(1)
    
    val syncLogs = mutableStateListOf<String>()
    
    var ttsEngine: TextToSpeech? = null

    fun addLog(message: String) {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        syncLogs.add(0, "[$timeStr] $message")
        if (syncLogs.size > 30) syncLogs.removeLast()
    }

    fun speak(text: String) {
        ttsEngine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AlzheimAR_Cue")
    }
}

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private val appState = AppState()
    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize TextToSpeech engine
        appState.ttsEngine = TextToSpeech(this, this)
        
        // Check and request runtime permissions
        checkAndRequestPermissions()

        setContent {
            AlzheimARTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianBg
                ) {
                    AppMainLayout(appState)
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 101)
        } else {
            appState.addLog("Biometric security checked. All hardware systems ready.")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = appState.ttsEngine?.setLanguage(Locale("id", "ID"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                appState.ttsEngine?.setLanguage(Locale.US) // fallback
                appState.addLog("TTS: Indonesian language pack missing. Defaulted to English.")
            } else {
                appState.addLog("TTS: Calming voice synthesis initialized in Bahasa Indonesia.")
            }
        } else {
            appState.addLog("TTS: Audio synthesis initialization failed.")
        }
    }

    override fun onDestroy() {
        appState.ttsEngine?.stop()
        appState.ttsEngine?.shutdown()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMainLayout(state: AppState) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // Shared Navigation Header
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = ObsidianBg,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.statusBarsPadding()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Patient View (AR Glasses HUD)", style = MaterialTheme.typography.labelMedium) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Caregiver View (AlzheimCare)", style = MaterialTheme.typography.labelMedium) }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (selectedTab == 0) {
                PatientScreen(state)
            } else {
                CaregiverScreen(state)
            }
        }
    }
}
