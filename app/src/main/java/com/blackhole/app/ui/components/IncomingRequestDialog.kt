package com.blackhole.app.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.blackhole.app.model.IncomingRequest
import com.blackhole.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomingRequestDialog(
    request: IncomingRequest?,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    if (request == null) return

    var showOptions by remember { mutableStateOf(false) }
    var showShaVerify by remember { mutableStateOf(false) }

    val isDesktop = request.senderAlias.contains("PC", ignoreCase = true) ||
            request.senderAlias.contains("Desktop", ignoreCase = true) ||
            request.senderAlias.contains("Laptop", ignoreCase = true) ||
            request.senderAlias.contains("Windows", ignoreCase = true)

    val deviceIcon = if (isDesktop) Icons.Default.Computer else Icons.Default.Smartphone
    val deviceBadge = if (isDesktop) "Windows" else "Android"
    val portPin = "#${(request.totalBytes % 900L + 100L)}"

    if (showShaVerify) {
        ShaVerificationDialog(
            pin = portPin,
            onDismiss = { showShaVerify = false }
        )
    }

    Dialog(onDismissRequest = onDecline) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderGlow, RoundedCornerShape(26.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(26.dp)
        ) {
            if (!showOptions) {
                // Main Request Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Center Hero Device Icon
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    listOf(Color(0x4D8B5CF6), Color(0x1A06B6D4))
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .border(1.dp, BorderGlow, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = deviceIcon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // 2. Sender Name
                    Text(
                        text = request.senderAlias,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    // 3. Pills Row: #Pin (clickable for SHA verify), OS Badge, IP
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(Color(0x1A06B6D4))
                                .border(1.dp, Color(0x4006B6D4), RoundedCornerShape(50.dp))
                                .clickable { showShaVerify = true }
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = portPin,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(Color(0x1A8B5CF6), RoundedCornerShape(50.dp))
                                .border(1.dp, Color(0x408B5CF6), RoundedCornerShape(50.dp))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = deviceBadge,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentViolet
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(Color(0x1AFFFFFF), RoundedCornerShape(50.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = request.senderIp,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // 4. Natural Language Prompt Line
                    val filePrompt = if (request.fileCount == 1) "a file" else "${request.fileCount} files"
                    Text(
                        text = "wants to send you $filePrompt",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "(${formatBytes(request.totalBytes)})",
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(Modifier.height(14.dp))

                    // 5. Options Button (Opens full Options subview)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showOptions = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Options",
                            tint = TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Options",
                            fontSize = 13.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // 6. Action Buttons: Dual Pill Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onDecline,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0x33EF4444),
                                contentColor = Color(0xFFFCA5A5)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4DF87171)),
                            shape = RoundedCornerShape(50.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Decline",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Button(
                            onClick = onAccept,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            shape = RoundedCornerShape(50.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Accept",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = Color.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            } else {
                // Dedicated Options Subview (Matching media_1792560802796_d8c1af9d.png)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Header Bar with Back Arrow
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = { showOptions = false }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Options",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Section 1: Save to folder
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x0DFFFFFF), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Save to folder", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                            Icon(Icons.Default.Edit, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Downloads/BlackHole",
                            fontSize = 12.sp,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Section 2: Files List
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x0DFFFFFF), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Files", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                            Text("💡", fontSize = 14.sp)
                        }

                        Spacer(Modifier.height(8.dp))

                        val displayFiles = if (request.fileNames.isNotEmpty()) request.fileNames else listOf("Incoming file")
                        LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                            items(displayFiles) { name ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.InsertDriveFile,
                                        contentDescription = null,
                                        tint = AccentCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = name,
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Unchanged • ${formatBytes(request.totalBytes)}",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                    }
                                    Checkbox(
                                        checked = true,
                                        onCheckedChange = {},
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = AccentCyan,
                                            checkmarkColor = Color.Black
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Back / Accept Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { showOptions = false },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                            shape = RoundedCornerShape(50.dp)
                        ) {
                            Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
