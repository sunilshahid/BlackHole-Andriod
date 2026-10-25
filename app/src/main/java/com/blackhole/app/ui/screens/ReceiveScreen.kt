package com.blackhole.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackhole.app.model.AppSettings
import com.blackhole.app.model.FavoriteDevice
import com.blackhole.app.model.TransferHistoryItem
import com.blackhole.app.model.TransferState
import com.blackhole.app.network.WebRtcEngine
import com.blackhole.app.ui.components.FavoritesDialog
import com.blackhole.app.ui.components.RoomCodeDialog
import com.blackhole.app.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ReceiveScreen(
    localIp: String,
    settings: AppSettings,
    transferState: TransferState,
    history: List<TransferHistoryItem>,
    onCancelTransfer: () -> Unit,
    webRtcEngine: WebRtcEngine? = null,
    onTransferStateChange: ((TransferState) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onRemoveFavoriteDevice: (String) -> Unit = {},
    onUpdateFavoriteAlias: (String, String) -> Unit = { _, _ -> },
    onSendToFavorite: (String, Int, String) -> Unit = { _, _, _ -> }
) {
    var showRoomCodeDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        // Cosmic Black Hole Vacuum Suction Animation
        BlackHoleSuctionView(
            modifier = Modifier.size(200.dp)
        )

        Spacer(Modifier.height(18.dp))

        // Device Name / Alias
        Text(
            text = settings.alias.ifEmpty { "Android Device" },
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            letterSpacing = (-0.5).sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(10.dp))

        // Badges Row: #PIN, OS Badge, IP:Port (Guaranteed Single-Line Fit)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PIN Badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0x268B5CF6),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4D8B5CF6))
            ) {
                Text(
                    text = "#${settings.pin}",
                    color = Color(0xFFC4B5FD),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Spacer(Modifier.width(6.dp))

            // OS Badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0x1A06B6D4),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3306B6D4))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "Android",
                        color = AccentCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // IP & Port Badge (maxLines = 1, softWrap = false)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0x1A10B981),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3310B981))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(AccentGreen, CircleShape)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "${if (localIp.isNotBlank()) localIp else "127.0.0.1"}:${settings.port}",
                        color = AccentGreen,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Status Subline
        Text(
            text = "Ready to receive incoming transfers",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(22.dp))

        // Bottom Center: Cosmic Room Code Button
        Surface(
            onClick = { showRoomCodeDialog = true },
            shape = RoundedCornerShape(24.dp),
            color = Color(0x1F8B5CF6),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4D8B5CF6))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = "Room Code",
                    tint = Color(0xFFC4B5FD),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Room Code",
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (showRoomCodeDialog) {
        RoomCodeDialog(
            webRtcEngine = webRtcEngine,
            onTransferStateChange = onTransferStateChange,
            onDismiss = { showRoomCodeDialog = false }
        )
    }
    }
}

/**
 * Cosmic Black Hole Suction Animation
 * Draws an accretion disk, pulsing event horizon, and spiraling particles
 * that are gravitationally sucked inward into the singularity.
 */
@Composable
fun BlackHoleSuctionView(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "BlackHoleSuction")

    // Disk Rotation
    val diskAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "DiskAngle"
    )

    // Event Horizon Pulse
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    // Suction Flow Phase (0.0 -> 1.0)
    val suctionPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SuctionPhase"
    )

    val particleColors = listOf(
        Color(0xFF06B6D4), // Cyan
        Color(0xFF8B5CF6), // Violet
        Color(0xFFEC4899), // Pink
        Color(0xFF10B981)  // Emerald
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f
        val horizonRadius = maxRadius * 0.38f * pulseScale

        // 1. Accretion Swirl Background (Rotating Dashed Ring 1)
        rotate(degrees = diskAngle, pivot = center) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x338B5CF6),
                        Color(0x1A06B6D4),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius
                ),
                radius = maxRadius * 0.95f,
                center = center
            )

            drawCircle(
                color = Color(0x338B5CF6),
                radius = maxRadius * 0.82f,
                center = center,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 18f), 0f)
                )
            )

            drawCircle(
                color = Color(0x2606B6D4),
                radius = maxRadius * 0.62f,
                center = center,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 14f), 0f)
                )
            )
        }

        // 2. Gravitational Inflow / Suction Particles with Glowing Tail
        // 4 particles distributed across 4 quadrants spiraling into the center
        val numParticles = 4
        for (i in 0 until numParticles) {
            val offsetPhase = (suctionPhase + (i.toFloat() / numParticles)) % 1f

            // Distance from center shrinks non-linearly (accelerates inward like gravity)
            val r = maxRadius * 0.90f * (1f - (offsetPhase * offsetPhase)) + (horizonRadius * 0.15f)

            // Spiral angle sweeps around as it pulls inward
            val baseAngle = (i * (2 * PI / numParticles)).toFloat()
            val totalAngle = baseAngle + (offsetPhase * 4.2f * PI.toFloat())

            val px = center.x + r * cos(totalAngle)
            val py = center.y + r * sin(totalAngle)

            // Fade in as it starts, bright in mid flight, fades to 0 at singularity entry
            val alpha = when {
                offsetPhase < 0.15f -> offsetPhase / 0.15f
                offsetPhase > 0.82f -> (1f - offsetPhase) / 0.18f
                else -> 0.95f
            }

            // Size scales down as particle is compressed into singularity
            val particleRadius = (5.dp.toPx()) * (1f - (offsetPhase * 0.65f))
            val baseColor = particleColors[i % particleColors.size]

            // 2a. Draw Decreasing Trailing Tail curving along the spiral path
            var prevX = px
            var prevY = py
            val tailSteps = 10
            for (s in 1..tailSteps) {
                val trailPhase = offsetPhase - (s * 0.012f)
                if (trailPhase <= 0f) break

                val tr = maxRadius * 0.90f * (1f - (trailPhase * trailPhase)) + (horizonRadius * 0.15f)
                val trailAngle = baseAngle + (trailPhase * 4.2f * PI.toFloat())
                val tx = center.x + tr * cos(trailAngle)
                val ty = center.y + tr * sin(trailAngle)

                val stepFactor = 1f - (s.toFloat() / tailSteps)
                val tailWidth = (particleRadius * 1.8f * stepFactor).coerceAtLeast(1.5f)
                val tailAlpha = (alpha * 0.65f * stepFactor).coerceIn(0f, 1f)

                drawLine(
                    color = baseColor.copy(alpha = tailAlpha),
                    start = Offset(prevX, prevY),
                    end = Offset(tx, ty),
                    strokeWidth = tailWidth,
                    cap = StrokeCap.Round
                )

                prevX = tx
                prevY = ty
            }

            // 2b. Glowing Head Halos & Core
            // Outer soft glow halo
            drawCircle(
                color = baseColor.copy(alpha = (alpha * 0.35f).coerceIn(0f, 1f)),
                radius = particleRadius * 2.8f,
                center = Offset(px, py)
            )
            // Mid glow halo
            drawCircle(
                color = baseColor.copy(alpha = (alpha * 0.7f).coerceIn(0f, 1f)),
                radius = particleRadius * 1.6f,
                center = Offset(px, py)
            )
            // Particle core
            drawCircle(
                color = baseColor.copy(alpha = alpha.coerceIn(0f, 1f)),
                radius = particleRadius,
                center = Offset(px, py)
            )
            // Bright center glint
            drawCircle(
                color = Color.White.copy(alpha = (alpha * 0.9f).coerceIn(0f, 1f)),
                radius = particleRadius * 0.45f,
                center = Offset(px, py)
            )
        }

        // 3. Black Hole Event Horizon Singularity (Deep Black Disk with glowing rim)
        // Outer horizon glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x998B5CF6),
                    Color(0x3306B6D4),
                    Color.Transparent
                ),
                center = center,
                radius = horizonRadius * 1.55f
            ),
            radius = horizonRadius * 1.55f,
            center = center
        )

        // Event Horizon Core (absolute black radial void)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF010103),
                    Color(0xFF090A14),
                    Color(0xFF140D2E)
                ),
                center = center,
                radius = horizonRadius
            ),
            radius = horizonRadius,
            center = center
        )

        // Event Horizon Boundary Rim
        drawCircle(
            color = Color(0xB38B5CF6),
            radius = horizonRadius,
            center = center,
            style = Stroke(width = 2.5.dp.toPx())
        )

        drawCircle(
            color = Color(0x6606B6D4),
            radius = horizonRadius * 0.94f,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )

        // Singularity Center Core Glow
        drawCircle(
            color = Color(0x33C4B5FD),
            radius = horizonRadius * 0.28f,
            center = center
        )
    }
}
