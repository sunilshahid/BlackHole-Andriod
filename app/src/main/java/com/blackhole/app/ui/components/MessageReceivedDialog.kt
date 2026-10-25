package com.blackhole.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.blackhole.app.ui.theme.*

@Composable
fun MessageReceivedDialog(
    senderAlias: String,
    messageText: String,
    onCopy: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val isDesktop = senderAlias.contains("PC", ignoreCase = true) ||
            senderAlias.contains("Desktop", ignoreCase = true) ||
            senderAlias.contains("Laptop", ignoreCase = true) ||
            senderAlias.contains("Windows", ignoreCase = true)

    val deviceIcon = if (isDesktop) Icons.Default.Computer else Icons.Default.Smartphone
    val deviceBadge = if (isDesktop) "Windows" else "Android"

    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderGlow, RoundedCornerShape(26.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(26.dp)
        ) {
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
                        .background(Color(0x1A8B5CF6), RoundedCornerShape(20.dp))
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
                    text = senderAlias,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(6.dp))

                // 3. Device Badge
                Box(
                    modifier = Modifier
                        .background(Color(0x1A8B5CF6), RoundedCornerShape(50.dp))
                        .border(1.dp, Color(0x408B5CF6), RoundedCornerShape(50.dp))
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = deviceBadge,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentViolet
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "sent you a message:",
                    fontSize = 14.sp,
                    color = TextSecondary
                )

                Spacer(Modifier.height(10.dp))

                // 4. Message Bubble
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0x14FFFFFF)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = messageText,
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Spacer(Modifier.height(18.dp))

                // 5. Action Buttons: Verify & Copy
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { /* Verified */ },
                        shape = RoundedCornerShape(50.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4010B981))
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Verify", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("BlackHole Message", messageText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                            onCopy?.invoke()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x2606B6D4)),
                        shape = RoundedCornerShape(50.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4006B6D4))
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy", color = AccentCyan, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(12.dp))

                TextButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Close", color = TextMuted, fontSize = 13.sp)
                }
            }
        }
    }
}
