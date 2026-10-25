package com.blackhole.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.blackhole.app.model.TransferState
import com.blackhole.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTransferCard(
    state: TransferState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.isActive && !state.isComplete && state.errorMessage == null) return

    // 0. Paired Handshake Card (Workflow + BlackHole Cosmic UI)
    if (state.isActive && state.fileName.startsWith("Waiting", ignoreCase = true)) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .border(1.dp, BorderGlow, RoundedCornerShape(22.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Card 1: Sender (This Phone)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0x0DFFFFFF)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Smartphone, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("This Device", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.background(Color(0x1A06B6D4), RoundedCornerShape(50.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                    Text("HTTP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                                }
                                Box(modifier = Modifier.background(Color(0x1A8B5CF6), RoundedCornerShape(50.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                    Text("Android", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentViolet)
                                }
                            }
                        }
                    }
                }

                // Arrow Down
                Icon(
                    Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.padding(vertical = 10.dp).size(22.dp)
                )

                // Card 2: Receiver
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0x0DFFFFFF)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Computer, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            val targetAlias = state.title.removePrefix("Sending to ").removeSuffix("...")
                            Text(targetAlias, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.background(Color(0x1A06B6D4), RoundedCornerShape(50.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                    Text("HTTP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                                }
                                Box(modifier = Modifier.background(Color(0x1A10B981), RoundedCornerShape(50.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                    Text("Target", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Verify Badge & Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0x1A10B981), RoundedCornerShape(50.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Verify", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Waiting for response...",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(16.dp))

                // Cancel Button (Soft Red Pill)
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x26EF4444)),
                    shape = RoundedCornerShape(50.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = AccentRed, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cancel", color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        return
    }

    var showAdvanced by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, BorderGlow, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // 1. Stage Title & Subtitle
            Text(
                text = if (state.isComplete) "Finished" else state.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(Modifier.height(12.dp))

            // 2. Active File Item
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x0DFFFFFF), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // File Icon box
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0x1A06B6D4), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.fileName.ifEmpty { "Transferring..." },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "(${formatBytes(state.totalBytes)})",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    if (state.isComplete) {
                        Text(
                            text = "Done",
                            fontSize = 12.sp,
                            color = AccentGreen,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { (state.progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = AccentCyan,
                            trackColor = Color(0x1AFFFFFF)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 3. Bottom Progress Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0x14FFFFFF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val progressHeader = if (state.isComplete) {
                            "Finished"
                        } else {
                            "Total progress (${formatTime(state.etaSeconds)})"
                        }
                        Text(
                            text = progressHeader,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${state.progress}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Full Progress Bar
                    LinearProgressIndicator(
                        progress = { (state.progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = AccentGreen,
                        trackColor = Color(0x1AFFFFFF)
                    )

                    Spacer(Modifier.height(12.dp))

                    // Action Buttons Row: Advanced & Cancel/Done
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Advanced Button
                        TextButton(
                            onClick = { showAdvanced = !showAdvanced },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Advanced",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        if (state.isActive) {
                            // Cancel Button
                            Button(
                                onClick = { showCancelConfirm = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0x26EF4444),
                                    contentColor = Color(0xFFFCA5A5)
                                ),
                                shape = RoundedCornerShape(50.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Advanced Stats Section (Speed & Bytes)
                    AnimatedVisibility(visible = showAdvanced) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Divider(color = BorderSubtle, modifier = Modifier.padding(bottom = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Speed: ${String.format("%.1f", state.speedMBs)} MB/s", fontSize = 11.sp, color = AccentCyan, fontFamily = FontFamily.Monospace)
                                Text("${formatBytes(state.bytesTransferred)} / ${formatBytes(state.totalBytes)}", fontSize = 11.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }

            // Error Message (If Any)
            state.errorMessage?.let {
                Spacer(Modifier.height(10.dp))
                Text(text = it, color = AccentRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    // Cancel Confirmation Dialog
    if (showCancelConfirm) {
        Dialog(onDismissRequest = { showCancelConfirm = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x33EF4444), RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Cancel files transfer",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Do you really want to cancel the files transfer?",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showCancelConfirm = false }) {
                            Text("Continue", color = TextSecondary, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = {
                                showCancelConfirm = false
                                onCancel()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33EF4444)),
                            shape = RoundedCornerShape(50.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = AccentRed, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel", color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val k = 1024.0
    val sizes = arrayOf("B", "KB", "MB", "GB", "TB")
    val i = (Math.log(bytes.toDouble()) / Math.log(k)).toInt().coerceIn(0, sizes.size - 1)
    return String.format("%.2f %s", bytes / Math.pow(k, i.toDouble()), sizes[i])
}

fun formatTime(seconds: Long): String {
    if (seconds <= 0) return "--"
    if (seconds < 60) return "${seconds}s"
    val m = seconds / 60
    val s = seconds % 60
    return "${m}:${String.format("%02d", s)}"
}
