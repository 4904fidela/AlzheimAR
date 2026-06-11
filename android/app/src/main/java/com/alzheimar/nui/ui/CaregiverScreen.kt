package com.alzheimar.nui.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alzheimar.nui.AppState
import com.alzheimar.nui.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun CaregiverScreen(state: AppState) {
    
    // Animate radar sweep line
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Angle"
    )

    // Parse float offset coordinates from AppState for drawing marker
    val coordinateParts = state.gpsCoordinates.split(",")
    val gpsLat = coordinateParts.getOrNull(0)?.trim()?.toFloatOrNull() ?: -6.2088f
    val gpsLng = coordinateParts.getOrNull(1)?.trim()?.toFloatOrNull() ?: 106.8456f

    // Calculate map pixel relative offsets (centered around -6.2088, 106.8456)
    val mapOffsetX = (gpsLng - 106.8456f) * 150000f // multiplier for visible pixel offset
    val mapOffsetY = (gpsLat - (-6.2088f)) * 150000f

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        // 1. SOS Critical Caregiver Banner Alert (Active Alarms)
        if (state.isSosActive) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SosRed.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(2.dp, SosRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "SOS",
                            tint = SosRed,
                            modifier = Modifier.size(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ALARM SOS AKTIF",
                                color = SosRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Ibu Wati membutuhkan bantuan! GPS: ${state.gpsCoordinates}",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                        Button(
                            onClick = {
                                state.isSosActive = false
                                state.addLog("SOS deactivated via caregiver dashboard.")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SosRed),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Dismiss", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // 2. Geofence warning alert
        if (state.isGeofenceBreached && !state.isSosActive) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SosRed.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SosRed.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NewReleases,
                            contentDescription = "Geofence warning",
                            tint = SosRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Peringatan Geofence",
                                color = SosRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Ibu Wati mendekati batas aman. Kacamata memandu pulang.",
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. Dynamic Location Map Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Radar view box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Color(0xFF13151D))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val centerX = w / 2f
                            val centerY = h / 2f

                            // Draw radar grid rings
                            drawCircle(Color.White.copy(alpha = 0.03f), 40.dp.toPx(), Offset(centerX, centerY))
                            drawCircle(Color.White.copy(alpha = 0.03f), 80.dp.toPx(), Offset(centerX, centerY))
                            
                            // Safe zone geofence (emerald dashed circle)
                            drawCircle(
                                color = EmeraldGreen.copy(alpha = 0.15f),
                                radius = 70.dp.toPx(),
                                center = Offset(centerX, centerY)
                            )
                            drawCircle(
                                color = EmeraldGreen.copy(alpha = 0.4f),
                                radius = 70.dp.toPx(),
                                center = Offset(centerX, centerY),
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )
                            )

                            // Draw Radar Line sweep angle
                            val sweepRadius = 100.dp.toPx()
                            val angleRad = Math.toRadians(sweepAngle.toDouble())
                            val endX = centerX + sweepRadius * Math.cos(angleRad).toFloat()
                            val endY = centerY + sweepRadius * Math.sin(angleRad).toFloat()
                            drawLine(
                                color = EmeraldGreen.copy(alpha = 0.15f),
                                start = Offset(centerX, centerY),
                                end = Offset(endX, endY),
                                strokeWidth = 3f
                            )

                            // Draw Patient marker position
                            // Map coordinates center relative offset
                            val markerX = centerX + mapOffsetX.dp.toPx()
                            val markerY = centerY - mapOffsetY.dp.toPx() // in android y coordinates increase downwards

                            // Draw glowing patient indicator
                            drawCircle(
                                color = if (state.isGeofenceBreached) SosRed else EmeraldLight,
                                radius = 6.dp.toPx(),
                                center = Offset(markerX, markerY)
                            )
                            drawCircle(
                                color = if (state.isGeofenceBreached) SosRed.copy(alpha = 0.3f) else EmeraldLight.copy(alpha = 0.3f),
                                radius = 12.dp.toPx(),
                                center = Offset(markerX, markerY),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }

                        // Coordinates Overlay tag
                        Text(
                            text = "GPS: ${state.gpsCoordinates}",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        )
                    }

                    // Map Info panel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.15f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Safe Zone Geofence status",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        
                        val badgeBg = if (state.isGeofenceBreached) SosRed.copy(alpha = 0.12f) else EmeraldGreen.copy(alpha = 0.12f)
                        val badgeBorder = if (state.isGeofenceBreached) SosRed.copy(alpha = 0.3f) else EmeraldGreen.copy(alpha = 0.3f)
                        val badgeTxt = if (state.isGeofenceBreached) "Wandering Alert" else "Dalam Zona Aman"
                        val badgeColor = if (state.isGeofenceBreached) SosRed else EmeraldLight

                        Box(
                            modifier = Modifier
                                .background(badgeBg, RoundedCornerShape(100.dp))
                                .border(1.dp, badgeBorder, RoundedCornerShape(100.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeTxt,
                                color = badgeColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 4. Quick Status Blocks
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Activity State Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateSurface),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("AKTIVITAS PASIEN", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsRun,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = state.patientActivity,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Battery Card with click toggle trigger
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.batteryPercentage <= 20) SosRed.copy(alpha = 0.1f) else SlateSurface
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (state.batteryPercentage <= 20) SosRed else Color.White.copy(alpha = 0.05f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            // Toggle battery levels to simulate low warning events
                            if (state.batteryPercentage > 20) {
                                state.batteryPercentage = 15
                                state.addLog("Telemetry critical battery warning (<20%): 15%")
                                state.speak("Kacamata hampir habis, harap diletakkan di dock pengisi daya.")
                            } else {
                                state.batteryPercentage = 85
                                state.addLog("Telemetry battery status restored: 85%")
                            }
                        }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "BATERAI ALZHEIMAR",
                            color = Color.Gray,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (state.batteryPercentage <= 20) Icons.Default.BatteryAlert else Icons.Default.Battery5Bar,
                                contentDescription = null,
                                tint = if (state.batteryPercentage <= 20) SosRed else EmeraldLight,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${state.batteryPercentage}%",
                                color = if (state.batteryPercentage <= 20) SosRed else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 5. Scrolling Sync Live Event Log list
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE SYNCHRONIZATION LOG",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .background(EmeraldGreen.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                .border(1.dp, EmeraldGreen.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("LIVE", color = EmeraldLight, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = Color.White.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(8.dp))

                    if (state.syncLogs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Menunggu data telemetry dari glasses...",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.syncLogs) { log ->
                                Text(
                                    text = log,
                                    color = if (log.contains("ALERT") || log.contains("critical")) SosRed else TextMuted,
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
