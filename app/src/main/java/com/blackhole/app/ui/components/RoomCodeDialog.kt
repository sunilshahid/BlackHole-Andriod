package com.blackhole.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.blackhole.app.model.TransferState
import com.blackhole.app.network.WebRtcEngine
import com.blackhole.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RoomCodeVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = if (text.text.length > 6) text.text.substring(0, 6) else text.text
        val out = StringBuilder()
        for (i in trimmed.indices) {
            out.append(trimmed[i])
            if (i == 2 && trimmed.length > 3) {
                out.append("-")
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 3) return offset
                if (offset <= 6) return offset + 1
                return 7
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 3) return offset
                if (offset <= 7) return offset - 1
                return 6
            }
        }

        return TransformedText(AnnotatedString(out.toString()), offsetMapping)
    }
}

@Composable
fun RoomCodeDialog(
    webRtcEngine: WebRtcEngine? = null,
    onTransferStateChange: ((TransferState) -> Unit)? = null,
    onDismiss: () -> Unit,
    onConnectCode: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    var rawCode by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }

    fun handleConnect() {
        val cleanCode = rawCode.uppercase().filter { it.isLetterOrDigit() }
        if (cleanCode.length < 6) {
            statusText = "Please enter all 6 characters of the code (e.g. 7K9-X2M)"
            return
        }
        keyboardController?.hide()
        isConnecting = true
        val formattedCode = "${cleanCode.substring(0, 3)}-${cleanCode.substring(3)}"

        scope.launch {
            statusText = "Checking signaling network..."
            val awake = webRtcEngine?.ensureSignalingAwake({ msg ->
                statusText = msg
            }) ?: true

            if (!awake) {
                statusText = "Signaling server unreachable. Please check connection."
                isConnecting = false
                return@launch
            }

            statusText = "Connecting to room $formattedCode..."
            webRtcEngine?.joinRoom(
                code = formattedCode,
                onStatus = { st -> statusText = st },
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
                    isConnecting = false
                }
            )
            onConnectCode?.invoke(formattedCode)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
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
                // Top Header Row
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
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = AccentViolet,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Receive via Room Code",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
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

                Text(
                    text = "Enter the 6-digit room code shown on the sender's device:",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(18.dp))

                // Big Monospace Code Input Box (Alphanumeric A-Z, 0-9)
                OutlinedTextField(
                    value = rawCode,
                    onValueChange = { input ->
                        val clean = input.uppercase().filter { it.isLetterOrDigit() }.take(6)
                        rawCode = clean
                    },
                    visualTransformation = RoomCodeVisualTransformation(),
                    textStyle = LocalTextStyle.current.copy(
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        letterSpacing = 4.sp
                    ),
                    placeholder = {
                        Text(
                            text = "7K9-X2M",
                            color = Color(0x40FFFFFF),
                            fontSize = 26.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            letterSpacing = 4.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { handleConnect() }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentViolet,
                        unfocusedBorderColor = BorderSubtle,
                        focusedContainerColor = Color(0x1A8B5CF6),
                        unfocusedContainerColor = Color(0x0DFFFFFF)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                // Status or Helper Note
                if (isConnecting) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AccentViolet
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = statusText ?: "Connecting...",
                            fontSize = 12.sp,
                            color = AccentViolet
                        )
                    }
                } else if (statusText != null) {
                    Text(
                        text = statusText!!,
                        fontSize = 12.sp,
                        color = Color(0xFFEF4444),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "Encrypted zero-knowledge peer-to-peer room",
                        fontSize = 11.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(BorderSubtle)
                        )
                    ) {
                        Text("Cancel", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    Button(
                        onClick = { handleConnect() },
                        enabled = !isConnecting,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(44.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentViolet,
                            disabledContainerColor = AccentViolet.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = "Receive",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
