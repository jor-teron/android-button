package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.LiveSensorReading
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.EmeraldActive
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import kotlinx.coroutines.delay

@Composable
fun LiveShakeMeter(
    sensorReading: LiveSensorReading,
    threshold: Float,
    isServiceActive: Boolean,
    modifier: Modifier = Modifier
) {
    val currentAcceleration = sensorReading.netAcceleration
    val maxScale = 36f

    var peakAccel by remember { mutableFloatStateOf(0f) }
    var showTriggeredBadge by remember { mutableStateOf(false) }

    LaunchedEffect(currentAcceleration) {
        if (currentAcceleration > peakAccel) {
            peakAccel = currentAcceleration
        }
        if (currentAcceleration >= threshold && isServiceActive) {
            showTriggeredBadge = true
        }
    }

    LaunchedEffect(showTriggeredBadge) {
        if (showTriggeredBadge) {
            delay(1200)
            showTriggeredBadge = false
            peakAccel = 0f
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_shake_meter_card"),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        shape = RoundedCornerShape(20.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    if (showTriggeredBadge) CyanNeon else DarkBorder,
                    DarkBorder
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isServiceActive) Color(0xFF003844) else DarkSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "Sensor",
                            tint = if (isServiceActive) CyanNeon else TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Live Shake Calibrator",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (isServiceActive) "Move phone to test sensitivity" else "Service paused",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                // Live Reading Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0D131D))
                        .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${String.format("%.1f", currentAcceleration)} m/s²",
                        color = if (currentAcceleration >= threshold) CyanNeon else TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Seismograph / Acceleration Bar Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0B0F17))
                    .border(1.dp, Color(0xFF1E2838), RoundedCornerShape(10.dp))
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val width = size.width
                    val height = size.height

                    // Fill bar based on current acceleration
                    val progress = (currentAcceleration / maxScale).coerceIn(0f, 1f)
                    val barWidth = width * progress

                    val barColor = if (currentAcceleration >= threshold) {
                        CyanNeon
                    } else {
                        Color(0xFF0096C7)
                    }

                    if (barWidth > 0) {
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset.Zero,
                            size = Size(barWidth, height),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                    }

                    // Threshold Line Marker
                    val thresholdX = (threshold / maxScale).coerceIn(0f, 1f) * width
                    drawLine(
                        color = Color(0xFFF59E0B),
                        start = Offset(thresholdX, 0f),
                        end = Offset(thresholdX, height),
                        strokeWidth = 3.dp.toPx()
                    )
                }

                // Threshold Label
                Text(
                    text = "Threshold: ${threshold.toInt()}",
                    color = Color(0xFFF59E0B),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "0 m/s² (Idle)",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
                Text(
                    text = "Peak: ${String.format("%.1f", peakAccel)} m/s²",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "36 m/s² (Max)",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }

            // Trigger Alert Banner
            AnimatedVisibility(
                visible = showTriggeredBadge,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF003844))
                        .border(1.dp, CyanNeon, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Triggered",
                        tint = CyanNeon,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SHAKE DETECTED! Screen wake command sent",
                        color = CyanNeon,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
