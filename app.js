// AlzheimAR Web Simulator Logic

document.addEventListener('DOMContentLoaded', () => {
    // Initialize Lucide Icons
    lucide.createIcons();

    // Sound Synthesizer using Web Audio API (Calming Chime)
    const playCalmChime = (type = 'success') => {
        try {
            const AudioContext = window.AudioContext || window.webkitAudioContext;
            if (!AudioContext) return;
            const ctx = new AudioContext();
            
            // Calming double-tone frequency configurations
            let f1 = 523.25; // C5
            let f2 = 659.25; // E5
            let duration = 1.0;
            
            if (type === 'sos') {
                f1 = 440.00; // A4 (low warning tone)
                f2 = 554.37; // C#5
                duration = 1.5;
            } else if (type === 'battery') {
                f1 = 392.00; // G4
                f2 = 493.88; // B4
                duration = 1.2;
            }

            // Synthesize Tone 1
            const osc1 = ctx.createOscillator();
            const gain1 = ctx.createGain();
            osc1.type = 'sine';
            osc1.frequency.setValueAtTime(f1, ctx.currentTime);
            gain1.gain.setValueAtTime(0.12, ctx.currentTime);
            gain1.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + duration);
            osc1.connect(gain1);
            gain1.connect(ctx.destination);

            // Synthesize Tone 2 (offset slightly for bell-like effect)
            const osc2 = ctx.createOscillator();
            const gain2 = ctx.createGain();
            osc2.type = 'sine';
            osc2.frequency.setValueAtTime(f2, ctx.currentTime + 0.15);
            gain2.gain.setValueAtTime(0.0, ctx.currentTime);
            gain2.gain.setValueAtTime(0.12, ctx.currentTime + 0.15);
            gain2.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + duration);
            osc2.connect(gain2);
            gain2.connect(ctx.destination);

            osc1.start(ctx.currentTime);
            osc2.start(ctx.currentTime + 0.15);
            
            osc1.stop(ctx.currentTime + duration);
            osc2.stop(ctx.currentTime + duration);
        } catch (e) {
            console.log("Audio synthesis blocked or unsupported:", e);
        }
    };

    // Calming Text to Speech in Bahasa Indonesia
    const speakVoicePrompt = (text) => {
        if (!('speechSynthesis' in window)) return;
        
        // Cancel any speaking audio
        window.speechSynthesis.cancel();
        
        const utterance = new SpeechSynthesisUtterance(text);
        utterance.lang = 'id-ID'; // Indonesian
        utterance.rate = 0.85;     // Calm, slower speed
        utterance.pitch = 1.0;     // Normal pitch

        // Try to find an Indonesian voice
        const voices = window.speechSynthesis.getVoices();
        const idVoice = voices.find(voice => voice.lang.includes('id'));
        if (idVoice) {
            utterance.voice = idVoice;
        }
        
        window.speechSynthesis.speak(utterance);
    };

    // State Variables
    let currentScenario = 'night-toilet';
    let isBatteryLow = false;
    let faceRecognitionTimer = null;
    let gestureProgressTimer = null;
    let gestureStartTime = null;

    // Element Selectors
    const scenarioBtns = document.querySelectorAll('.scenario-btn');
    const sceneBg = document.getElementById('scene-bg-element');
    const lensViewport = document.getElementById('lens-viewport');
    const audioPromptText = document.getElementById('audio-prompt-text');
    const playAudioBtn = document.getElementById('play-audio-btn');
    
    // AR elements
    const arNightToilet = document.getElementById('ar-night-toilet');
    const arMakeTea = document.getElementById('ar-make-tea');
    const arFaceRec = document.getElementById('ar-face-rec');
    const arSafeZone = document.getElementById('ar-safe-zone');
    const arMedPrompt = document.getElementById('ar-med-prompt');
    const arSosActive = document.getElementById('ar-sos-active');
    
    // Telemetry Dashboard
    const telemetryActivity = document.getElementById('telemetry-activity');
    const telemetryDevice = document.getElementById('telemetry-device');
    const phoneBatteryLvl = document.getElementById('phone-battery-lvl');
    const batteryToggle = document.getElementById('battery-toggle');
    const batteryIcon = document.getElementById('battery-icon');
    const mapPatientMarker = document.getElementById('map-patient-marker');
    const mapGpsText = document.getElementById('map-gps-text');
    const geofenceBadge = document.getElementById('geofence-badge');
    const syncLogList = document.getElementById('sync-log-list');
    const caregiverAlertsArea = document.getElementById('caregiver-alerts-area');
    
    // Modality triggers
    const voiceTrigger = document.getElementById('voice-trigger');
    const gestureTrigger = document.getElementById('gesture-trigger');

    // Scenarios Configuration
    const scenarios = {
        'night-toilet': {
            bg: 'assets/bedroom_night.png',
            activity: 'Walking (Night)',
            gps: 'GPS: -6.2088, 106.8456',
            markerPos: { top: '50%', left: '50%' },
            geofence: 'ok',
            audio: 'Jalan ini akan menuntun Ibu.',
            log: 'Night navigation triggered. Passive glowing path projected.'
        },
        'make-tea': {
            bg: 'assets/kitchen_tea.png',
            activity: 'Cooking (Kitchen)',
            gps: 'GPS: -6.2089, 106.8455',
            markerPos: { top: '48%', left: '46%' },
            geofence: 'ok',
            audio: 'Langkah berikutnya ada di sini.',
            log: 'Task Guidance active: Making Tea. Contextual task hotspots loaded.'
        },
        'face-recognition': {
            bg: 'assets/budi_face.png',
            activity: 'Sitting (Living Room)',
            gps: 'GPS: -6.2089, 106.8455',
            markerPos: { top: '51%', left: '53%' },
            geofence: 'ok',
            audio: 'Budi – Cucu Pertama Anda.',
            log: 'Gaze recognition: Registered face identified. Bounding frame loaded.'
        },
        'safe-zone': {
            bg: 'assets/garden_path.png',
            activity: 'Walking (Outdoor)',
            gps: 'GPS: -6.2094, 106.8462',
            markerPos: { top: '22%', left: '78%' }, // near boundary
            geofence: 'warn',
            audio: 'Kita pulang dulu ya, Bu.',
            log: 'Geofence buffer warning. Emerald return-arrow projected on glasses.'
        },
        'med-prompt': {
            bg: 'assets/bedroom_night.png', // reusing bedroom asset
            activity: 'Standing (Bedroom)',
            gps: 'GPS: -6.2088, 106.8456',
            markerPos: { top: '50%', left: '50%' },
            geofence: 'ok',
            audio: 'Waktunya minum obat pagi, Ibu.',
            log: 'Medication schedule trigger: Obat Pagi. Cognitive overlay prompt displayed.'
        }
    };

    // Add row helper for live log
    const addSyncLog = (text) => {
        const now = new Date();
        const timeStr = now.toTimeString().split(' ')[0];
        const row = document.createElement('div');
        row.className = 'log-row';
        row.innerHTML = `
            <span class="log-time">${timeStr}</span>
            <span class="log-txt">${text}</span>
        `;
        syncLogList.prepend(row);
        
        // Cap logs at 20 entries
        if (syncLogList.children.length > 20) {
            syncLogList.removeChild(syncLogList.lastChild);
        }
    };

    // Raise Caregiver Alert box helper
    const addCaregiverAlert = (title, desc, isEmerald = false) => {
        // Clear previous alerts if there are too many
        if (caregiverAlertsArea.children.length > 1) {
            caregiverAlertsArea.innerHTML = '';
        }
        
        const alertBox = document.createElement('div');
        alertBox.className = `c-alert-box ${isEmerald ? 'green-alert' : ''}`;
        alertBox.innerHTML = `
            <i class="lucide-icon" data-lucide="${isEmerald ? 'check-circle' : 'alert-triangle'}"></i>
            <div class="c-alert-info">
                <span class="c-alert-title">${title}</span>
                <span class="c-alert-desc">${desc}</span>
            </div>
        `;
        caregiverAlertsArea.prepend(alertBox);
        lucide.createIcons();

        // Autoclose alert after 8 seconds
        setTimeout(() => {
            alertBox.style.opacity = '0';
            setTimeout(() => alertBox.remove(), 300);
        }, 8000);
    };

    // Show/Hide AR Overlays
    const hideAllAROverlays = () => {
        arNightToilet.classList.add('hidden');
        arMakeTea.classList.add('hidden');
        arFaceRec.classList.add('hidden');
        arSafeZone.classList.add('hidden');
        arMedPrompt.classList.add('hidden');
        arSosActive.classList.add('hidden');
    };

    // Render Scenario function
    const renderScenario = (scenKey) => {
        currentScenario = scenKey;
        const conf = scenarios[scenKey];
        if (!conf) return;

        // Reset visual overlays
        hideAllAROverlays();
        
        // 1. Update background image
        sceneBg.style.backgroundImage = `url('${conf.bg}')`;

        // 2. Set telemetry dashboard data
        telemetryActivity.innerHTML = `<i data-lucide="footprints"></i> ${conf.activity}`;
        mapGpsText.innerText = conf.gps;
        mapPatientMarker.style.top = conf.markerPos.top;
        mapPatientMarker.style.left = conf.markerPos.left;

        if (conf.geofence === 'warn') {
            geofenceBadge.className = 'status-badge status-warn';
            geofenceBadge.innerText = 'Boundary Warn';
            addCaregiverAlert('Geofence Buffer Warning', 'Patient approaching boundaries. Glasses guiding patient home.', true);
        } else {
            geofenceBadge.className = 'status-badge status-ok';
            geofenceBadge.innerText = 'Within Zone';
        }

        // 3. Audio & subtitle updates
        audioPromptText.innerText = `VOICE CUE: "${conf.audio}"`;
        
        // 4. Activate specific AR overlay
        if (scenKey === 'night-toilet') {
            arNightToilet.classList.remove('hidden');
        } else if (scenKey === 'make-tea') {
            arMakeTea.classList.remove('hidden');
            // Reset task hotspots
            document.querySelectorAll('.gaze-target-hotspot').forEach(hot => {
                hot.classList.remove('active');
            });
            // Auto highlight step 1
            const firstHotspot = document.querySelector('.gaze-target-hotspot[data-step="1"]');
            if (firstHotspot) firstHotspot.classList.add('active');
            updateTeaStepText("Nyalakan kompor", 1);
        } else if (scenKey === 'face-recognition') {
            arFaceRec.classList.remove('hidden');
            
            // Slide 8 rule: Auto-dismisses face card label after 8 seconds
            if (faceRecognitionTimer) clearTimeout(faceRecognitionTimer);
            
            const faceLabel = document.querySelector('.face-label-card');
            const faceBox = document.querySelector('.face-bounding-box');
            
            faceLabel.style.display = 'flex';
            faceBox.style.borderColor = 'rgba(0, 168, 107, 0.35)';
            
            faceRecognitionTimer = setTimeout(() => {
                faceLabel.style.display = 'none';
                faceBox.style.borderColor = 'transparent';
                addSyncLog('NUI gaze overlay auto-dismissed (8s elapsed).');
            }, 8000);
        } else if (scenKey === 'safe-zone') {
            arSafeZone.classList.remove('hidden');
        } else if (scenKey === 'med-prompt') {
            arMedPrompt.classList.remove('hidden');
            
            // Auto-dismiss medication reminder after 12s
            setTimeout(() => {
                if (currentScenario === 'med-prompt') {
                    arMedPrompt.classList.add('hidden');
                    addSyncLog('Medication reminder auto-dismissed (12s compliance logged).');
                }
            }, 12000);
        }

        // Play chime & voice prompt
        playCalmChime();
        speakVoicePrompt(conf.audio);

        // Add log
        addSyncLog(conf.log);
        lucide.createIcons();
    };

    // Update Make Tea HUD titles
    const updateTeaStepText = (label, step) => {
        const headerCard = document.querySelector('#ar-make-tea .ar-card-top span');
        if (headerCard) {
            headerCard.innerText = `Panduan Membuat Teh (${step}/4): ${label}`;
        }
    };

    // Event listeners for Scenario Buttons
    scenarioBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            scenarioBtns.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            renderScenario(btn.getAttribute('data-scenario'));
        });
    });

    // Make Tea Hotspot Clicks (Gaze Simulation)
    document.querySelectorAll('.gaze-target-hotspot').forEach(hotspot => {
        hotspot.addEventListener('click', (e) => {
            // Remove active class from all hotspots
            document.querySelectorAll('.gaze-target-hotspot').forEach(h => h.classList.remove('active'));
            
            // Mark clicked hotspot as active
            hotspot.classList.add('active');
            
            const stepNum = hotspot.getAttribute('data-step');
            const stepLabel = hotspot.querySelector('.step-label').innerText;
            
            updateTeaStepText(stepLabel, stepNum);
            playCalmChime();
            
            // Voice cue transition
            let promptText = `Langkah berikutnya ada di sini: ${stepLabel}`;
            audioPromptText.innerText = `VOICE CUE: "${promptText}"`;
            speakVoicePrompt(stepLabel);
            
            addSyncLog(`Gaze locked on item: ${stepLabel}. Compliance logged.`);
        });
    });

    // Voice Trigger Simulator ("Tolong")
    voiceTrigger.addEventListener('click', () => {
        hideAllAROverlays();
        arSosActive.classList.remove('hidden');
        audioPromptText.innerText = 'VOICE CUE: "Bantuan sedang dalam perjalanan. Tetap tenang."';
        
        playCalmChime('sos');
        speakVoicePrompt("Bantuan sedang dalam perjalanan. Tetap tenang.");
        
        addSyncLog('Voice Keyword Alert: "Tolong" detected by NPU.');
        addCaregiverAlert('CRITICAL SOS ALERT', 'Patient Ibu Wati said "Tolong". GPS coordinates tracked.');
        
        // Revert back after 6 seconds
        setTimeout(() => {
            if (!arSosActive.classList.contains('hidden')) {
                renderScenario(currentScenario);
            }
        }, 6000);
    });

    // Gesture Trigger (Fist Raise - click & hold 3s)
    const startGestureTimer = () => {
        gestureStartTime = Date.now();
        gestureTrigger.classList.add('holding');
        const progressBar = gestureTrigger.querySelector('.progress-bar');
        
        let width = 0;
        gestureProgressTimer = setInterval(() => {
            width += 3.33; // 100% over 3 seconds (30 steps of 100ms)
            if (width >= 100) {
                width = 100;
                clearInterval(gestureProgressTimer);
                triggerGestureSos();
            }
            progressBar.style.width = width + '%';
        }, 100);
    };

    const stopGestureTimer = () => {
        if (gestureProgressTimer) {
            clearInterval(gestureProgressTimer);
            gestureProgressTimer = null;
        }
        gestureTrigger.classList.remove('holding');
        gestureTrigger.querySelector('.progress-bar').style.width = '0%';
    };

    const triggerGestureSos = () => {
        hideAllAROverlays();
        arSosActive.classList.remove('hidden');
        audioPromptText.innerText = 'VOICE CUE: "Bantuan sedang dalam perjalanan. Tetap tenang."';
        
        playCalmChime('sos');
        speakVoicePrompt("Bantuan sedang dalam perjalanan. Tetap tenang.");
        
        addSyncLog('Gesture detected: 6-axis IMU detected sustained Closed-Fist SOS.');
        addCaregiverAlert('CRITICAL SOS GESTURE', 'Sustained fist gesture detected. Contacting first responder.', false);
        
        stopGestureTimer();

        // Revert back after 6s
        setTimeout(() => {
            if (!arSosActive.classList.contains('hidden')) {
                renderScenario(currentScenario);
            }
        }, 6000);
    };

    gestureTrigger.addEventListener('mousedown', startGestureTimer);
    gestureTrigger.addEventListener('mouseup', stopGestureTimer);
    gestureTrigger.addEventListener('mouseleave', stopGestureTimer);
    
    // Support mobile touches for hold SOS
    gestureTrigger.addEventListener('touchstart', (e) => {
        e.preventDefault();
        startGestureTimer();
    });
    gestureTrigger.addEventListener('touchend', stopGestureTimer);

    // Battery Toggle Simulator
    batteryToggle.addEventListener('click', () => {
        isBatteryLow = !isBatteryLow;
        if (isBatteryLow) {
            // Low battery
            batteryToggle.innerHTML = '<i data-lucide="battery-warning" id="battery-icon"></i> <span>Battery: 15%</span>';
            batteryToggle.classList.add('active');
            phoneBatteryLvl.innerHTML = '<i data-lucide="battery-warning" class="text-red"></i> 15%';
            phoneBatteryLvl.style.color = '#ef4444';
            
            // Trigger low battery chime + speech alert (calming, voice-only for patient)
            playCalmChime('battery');
            speakVoicePrompt("Kacamata hampir habis, harap diletakkan di dock pengisi daya.");
            
            addSyncLog('System warning: Battery low (<20%). Device logged 15% status.');
            addCaregiverAlert('DEVICE BATTERY CRITICAL', 'AlzheimAR glasses are at 15%. Prompt caregiver to dock device.', false);
        } else {
            // Recovered battery
            batteryToggle.innerHTML = '<i data-lucide="battery-medium" id="battery-icon"></i> <span>Battery: 85%</span>';
            batteryToggle.classList.remove('active');
            phoneBatteryLvl.innerHTML = '<i data-lucide="battery"></i> 85%';
            phoneBatteryLvl.style.color = '';
            
            addSyncLog('System status: Device charging dock disconnected. Battery stabilized at 85%.');
        }
        lucide.createIcons();
    });

    // Play Audio Cue Bar Button click
    playAudioBtn.addEventListener('click', () => {
        const conf = scenarios[currentScenario];
        if (conf) {
            playCalmChime();
            speakVoicePrompt(conf.audio);
            addSyncLog(`Audio playback requested for cue: "${conf.audio}"`);
        }
    });

    // Init first load
    renderScenario('night-toilet');
    addSyncLog('NUI initialization: system self-check complete.');
    addSyncLog('Biometric security AES-256 decrypted successfully.');
});
