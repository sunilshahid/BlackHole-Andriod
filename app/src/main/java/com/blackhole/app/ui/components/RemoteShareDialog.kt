package com.blackhole.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.blackhole.app.model.FileItem
import com.blackhole.app.model.TransferState
import com.blackhole.app.network.WebRtcEngine
import com.blackhole.app.ui.screens.getFileItemFromUri
import com.blackhole.app.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun RemoteShareDialog(
    selectedFiles: List<FileItem>,
    webRtcEngine: WebRtcEngine? = null,
    onFilesAdded: ((List<FileItem>) -> Unit)? = null,
    onTransferStateChange: ((TransferState) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var roomCode by remember { mutableStateOf("...") }
    var statusText by remember { mutableStateOf("Connecting to signaling network...") }
    var currentFiles by remember { mutableStateOf(selectedFiles) }

    fun startSharingWithFiles(filesToShare: List<FileItem>) {
        webRtcEngine?.createRoom(
            files = filesToShare,
            onStatus = { st -> statusText = st },
            onRoomCreated = { code, _ ->
                roomCode = code
                statusText = "Waiting for recipient to connect..."
            },
            onProgress = { state ->
                onTransferStateChange?.invoke(state)
                if (state.isActive) {
                    onDismiss()
                }
            },
            onComplete = {
                onTransferStateChange?.invoke(
                    TransferState(
                        isActive = false,
                        isComplete = true,
                        title = "Transfer Complete",
                        progress = 100
                    )
                )
                onDismiss()
            },
            onError = { err ->
                statusText = err
            }
        )
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newItems = uris.map { getFileItemFromUri(context, it) }
            val merged = currentFiles + newItems
            currentFiles = merged
            onFilesAdded?.invoke(newItems)
            startSharingWithFiles(merged)
        }
    }

    LaunchedEffect(Unit) {
        val awake = webRtcEngine?.ensureSignalingAwake({ st ->
            statusText = st
        }) ?: true

        if (awake) {
            startSharingWithFiles(currentFiles)
        } else {
            statusText = "Signaling network unreachable. Please check connection."
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    val cleanDigits = roomCode.replace("-", "").filter { it.isLetterOrDigit() }
    val roomUrl = "https://share.sunilshahid.com/?code=$cleanDigits"

    // Generate QR Code bitmap from direct link
    val qrBitmap = remember(roomUrl) {
        if (cleanDigits.length >= 6) {
            try {
                val writer = QRCodeWriter()
                val bitMatrix = writer.encode(roomUrl, BarcodeFormat.QR_CODE, 200, 200)
                val width = bitMatrix.width
                val height = bitMatrix.height
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bmp.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
                    }
                }
                bmp
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun copyLink() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("BlackHole Room Link", roomUrl))
        Toast.makeText(context, "Direct link copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    fun copyCode() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("BlackHole Room Code", roomCode))
        Toast.makeText(context, "Room code copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    Dialog(onDismissRequest = {
        webRtcEngine?.cancel()
        onDismiss()
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderGlow, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row with Close 'X'
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
                                .background(Color(0x268B5CF6))
                                .border(1.dp, Color(0x4D8B5CF6), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = AccentViolet,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Internet Share",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Send via 6-digit room code",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            webRtcEngine?.cancel()
                            onDismiss()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 1. QR Code prominently on TOP
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Direct Transfer QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(
                            color = AccentViolet,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 2. Room Code Display Box below QR
                Text(
                    text = "RECIPIENT ENTER THIS CODE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan,
                    letterSpacing = 1.sp
                )

                Spacer(Modifier.height(6.dp))

                Surface(
                    onClick = { copyCode() },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x268B5CF6),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0x808B5CF6)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = roomCode,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White,
                            letterSpacing = 4.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = Color(0xFFC4B5FD),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Tap to copy code",
                                fontSize = 10.sp,
                                color = Color(0xFFC4B5FD)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 3. Direct URL Pill below code
                Surface(
                    onClick = { copyLink() },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x0DFFFFFF),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = roomUrl,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AccentGreen,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Link",
                            tint = TextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 4. Staged Files Section / Add Files Button
                if (currentFiles.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x1A10B981))
                            .border(1.dp, Color(0x3310B981), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${currentFiles.size} file(s) staged: ${currentFiles.first().name}${if (currentFiles.size > 1) "..." else ""}",
                            fontSize = 11.sp,
                            color = AccentGreen,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(6.dp))
                        TextButton(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("+ Add", fontSize = 11.sp, color = AccentViolet)
                        }
                    }
                } else {
                    Surface(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0x1AEAB308),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4DEAB308)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFFFDE047),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Select Files to Send",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFDE047)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 5. Live Waiting Status with Pulsing Dot
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AccentCyan.copy(alpha = pulseAlpha))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
