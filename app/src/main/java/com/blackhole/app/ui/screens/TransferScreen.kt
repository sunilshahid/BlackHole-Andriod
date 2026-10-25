package com.blackhole.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.blackhole.app.model.TransferState
import com.blackhole.app.ui.components.ShaVerificationDialog
import com.blackhole.app.ui.components.formatBytes
import com.blackhole.app.ui.components.formatTime
import com.blackhole.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    state: TransferState,
    isSender: Boolean,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdvanced by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var showShaVerify by remember { mutableStateOf(false) }

    if (showShaVerify) {
        ShaVerificationDialog(
            pin = "#102",
            onDismiss = { showShaVerify = false }
        )
    }

    BackHandler {
        if (state.isActive) {
            showCancelConfirm = true
        } else {
            onDone()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = BlackSpace
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ==========================================
            // TOP HEADER (Pinned with Back Navigation)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (state.isActive) {
                            showCancelConfirm = true
                        } else {
                            onDone()
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.width(8.dp))
                val isTextTransfer = state.fileName.contains("note", ignoreCase = true) ||
                                     state.fileName.contains("message", ignoreCase = true) ||
                                     state.fileName.endsWith(".txt", ignoreCase = true)
                Column {
                    Text(
                        text = when {
                            isSender && isTextTransfer -> "Sending message"
                            isSender -> "Sending files"
                            isTextTransfer -> "Receiving message"
                            else -> "Receiving files"
                        },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(2.dp))
                    val subline = when {
                        state.errorMessage != null -> "Transfer failed: ${state.errorMessage}"
                        isSender -> state.title
                        else -> "Save to folder: Downloads/BlackHole"
                    }
                    Text(
                        text = subline,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }

            // Handshake status check
            val isHandshake = state.isActive && (
                state.fileName.startsWith("Waiting", ignoreCase = true) ||
                state.fileName.startsWith("Connecting", ignoreCase = true) ||
                (state.bytesTransferred == 0L && !state.isComplete)
            ) && state.errorMessage == null

            // ==========================================
            // CENTER CONTENT
            // ==========================================
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = if (isHandshake) Alignment.Center else Alignment.TopCenter
            ) {
                if (isHandshake) {
                    // 1. Paired Device Handshake Flow
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Card 1: Sender
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0x0DFFFFFF)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Smartphone, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text("This Device", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                            modifier = Modifier.padding(vertical = 12.dp).size(24.dp)
                        )

                        // Card 2: Receiver
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0x0DFFFFFF)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Computer, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    val targetAlias = state.title.removePrefix("Sending to ").removeSuffix(" (LocalSend)").removeSuffix("...")
                                    Text(targetAlias, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(modifier = Modifier.background(Color(0x1A06B6D4), RoundedCornerShape(50.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                            Text("HTTP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                                        }
                                        Box(modifier = Modifier.background(Color(0x1A10B981), RoundedCornerShape(50.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                            Text("Windows", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Verify Status (Clickable for SHA-256 fingerprint modal)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(Color(0x1A10B981))
                                .clickable { showShaVerify = true }
                                .padding(horizontal = 14.dp, vertical = 5.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Verify (#102)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                        }

                        Spacer(Modifier.height(10.dp))

                        val statusText = if (state.fileName.startsWith("Waiting", ignoreCase = true) || state.fileName.startsWith("Connecting", ignoreCase = true)) {
                            state.fileName
                        } else {
                            "Waiting for response..."
                        }
                        Text(
                            text = statusText,
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                } else {
                    // 2. Active File Row (Pinned to Top)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Top
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0x0DFFFFFF)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color(0x1A06B6D4), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.InsertDriveFile,
                                        contentDescription = null,
                                        tint = AccentCyan,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Spacer(Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = state.fileName.ifEmpty { "Transferring..." },
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "(${formatBytes(state.totalBytes)})",
                                            fontSize = 12.sp,
                                            color = TextMuted,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    if (state.isComplete) {
                                        Text(
                                            text = "Done",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentGreen
                                        )
                                    } else {
                                        LinearProgressIndicator(
                                            progress = { (state.progress / 100f).coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(5.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = AccentCyan,
                                            trackColor = Color(0x1AFFFFFF)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // STICKY BOTTOM PROGRESS CARD / ACTION BAR
            // ==========================================
            if (isHandshake) {
                // When waiting for response, only show sleek Cancel button and NO progress bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { showCancelConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x26EF4444)),
                        shape = RoundedCornerShape(50.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(46.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = AccentRed, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel", color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderGlow, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isDoneOrComplete = state.isComplete || state.progress >= 100
                            val header = when {
                                state.errorMessage != null -> "Failed"
                                isDoneOrComplete -> "Finished"
                                else -> "Total Progress (${formatTime(state.etaSeconds)})"
                            }
                            Text(
                                text = header,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (state.errorMessage != null) AccentRed else Color.White
                            )
                            Text(
                                text = "${state.progress}%",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (state.errorMessage != null) AccentRed else if (isDoneOrComplete) AccentGreen else AccentCyan,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        val isDoneOrComplete = state.isComplete || state.progress >= 100
                        LinearProgressIndicator(
                            progress = { (state.progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = when {
                                state.errorMessage != null -> AccentRed
                                isDoneOrComplete -> AccentGreen
                                else -> AccentCyan
                            },
                            trackColor = Color(0x1AFFFFFF)
                        )

                        Spacer(Modifier.height(14.dp))

                        // Buttons Row: Advanced & Cancel/Done/Close
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showAdvanced = !showAdvanced }
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Advanced", color = TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }

                            Spacer(Modifier.width(10.dp))

                            if (isDoneOrComplete) {
                                Button(
                                    onClick = onDone,
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                    shape = RoundedCornerShape(50.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Done", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            } else if (state.isActive) {
                                Button(
                                    onClick = { showCancelConfirm = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x26EF4444)),
                                    shape = RoundedCornerShape(50.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = AccentRed, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Cancel", color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            } else if (state.errorMessage != null) {
                                Button(
                                    onClick = onDone,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33EF4444)),
                                    shape = RoundedCornerShape(50.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = AccentRed, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Close", color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }

                        // Advanced Metrics
                        AnimatedVisibility(visible = showAdvanced) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(bottom = 8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Speed: ${String.format("%.1f", state.speedMBs)} MB/s", fontSize = 12.sp, color = AccentCyan, fontFamily = FontFamily.Monospace)
                                    Text("${formatBytes(state.bytesTransferred)} / ${formatBytes(state.totalBytes)}", fontSize = 12.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Cancel Confirmation Dialog
    if (showCancelConfirm) {
        Dialog(onDismissRequest = { showCancelConfirm = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x33EF4444), RoundedCornerShape(22.dp))
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
                        textAlign = TextAlign.Center
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
                            Icon(Icons.Default.Close, contentDescription = null, tint = AccentRed, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel", color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
