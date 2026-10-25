package com.blackhole.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackhole.app.model.AppSettings
import com.blackhole.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSettings: AppSettings,
    localIp: String,
    onSave: (AppSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var alias by remember(currentSettings) { mutableStateOf(currentSettings.alias) }
    var quickSave by remember(currentSettings) { mutableStateOf(currentSettings.quickSave) }
    var quickSaveMode by remember(currentSettings) { mutableStateOf(currentSettings.quickSaveMode) }
    var autoCopyText by remember(currentSettings) { mutableStateOf(currentSettings.autoCopyText) }
    var port by remember(currentSettings) { mutableStateOf(currentSettings.port.toString()) }
    var themeMode by remember(currentSettings) { mutableStateOf(currentSettings.themeMode) }
    var encryptionEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
        Text(
            text = "Settings",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.textPrimary
        )

        // 0. Theme Mode Card (System Default, Dark, Light)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AppTheme.borderSubtle, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = AppTheme.cardBackground),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Theme Mode",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.textPrimary
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Choose whether BlackHole follows system theme or stays dark / light",
                    fontSize = 12.sp,
                    color = AppTheme.textSecondary
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val themeOptions = listOf("system" to "System", "dark" to "Dark", "light" to "Light")
                    themeOptions.forEach { (mode, label) ->
                        val isSelected = themeMode.lowercase() == mode
                        Button(
                            onClick = {
                                themeMode = mode
                                onSave(currentSettings.copy(themeMode = mode))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) AccentViolet else AppTheme.cardSurface,
                                contentColor = if (isSelected) Color.White else AppTheme.textSecondary
                            ),
                            border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, AppTheme.borderSubtle) else null
                        ) {
                            Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }

        // 1. Device Alias Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Device Name (Alias)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "How this device appears to nearby peers on the radar",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentViolet,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = CardSurface,
                        unfocusedContainerColor = CardSurface
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // 2. Destination Folder Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0x2606B6D4), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Destination Folder",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Downloads / BlackHole",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                }
            }
        }

        // 3. Quick Save (Auto-Accept)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = "Quick Save (Auto-Accept)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Automatically accept and save incoming transfers without prompt",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf("off" to "Off", "favorites" to "Favorites", "always" to "Always")
                    modes.forEach { (mKey, mLabel) ->
                        val isSel = quickSaveMode == mKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) AccentViolet else Color(0x14FFFFFF))
                                .border(1.dp, if (isSel) AccentViolet else BorderSubtle, RoundedCornerShape(10.dp))
                                .clickable {
                                    quickSaveMode = mKey
                                    quickSave = (mKey == "always")
                                    onSave(currentSettings.copy(quickSaveMode = mKey, quickSave = (mKey == "always")))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mLabel,
                                fontSize = 13.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) Color.White else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // 3b. Auto-Copy Text
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Copy Text",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Automatically copy received text messages to clipboard",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Switch(
                    checked = autoCopyText,
                    onCheckedChange = {
                        autoCopyText = it
                        onSave(currentSettings.copy(autoCopyText = it))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentViolet,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = CardSurface
                    )
                )
            }
        }

        // 4. Port Configuration
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Port",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Default port 53317 (BlackHole Standard)",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    modifier = Modifier.width(100.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentViolet,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = CardSurface,
                        unfocusedContainerColor = CardSurface
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        // 5. Hardware End-to-End Encryption
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "End-to-End Encryption (AES-256-GCM)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Hardware accelerated encryption for all LAN packets",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Switch(
                    checked = encryptionEnabled,
                    onCheckedChange = { encryptionEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentGreen,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = CardSurface
                    )
                )
            }
        }

        // 6. Network & Protocol Info Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Network & Protocol",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Badge(containerColor = Color(0x3310B981)) {
                        Text(
                            text = "CONNECTED",
                            color = AccentGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Local IP", fontSize = 12.sp, color = TextMuted)
                    Text(
                        text = if (localIp.isNotBlank()) localIp else "Not connected",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AccentCyan
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Protocol", fontSize = 12.sp, color = TextMuted)
                    Text("BlackHole v1.0 (Zero-Knowledge)", fontSize = 12.sp, color = TextSecondary)
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Compatibility", fontSize = 12.sp, color = TextMuted)
                    Text("Windows, Android, Mac, Web", fontSize = 12.sp, color = AccentViolet)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Save Button
        Button(
            onClick = {
                val newPort = port.toIntOrNull() ?: 53317
                val newAlias = alias.trim().ifEmpty { "BlackHole-Android" }
                onSave(
                    currentSettings.copy(
                        alias = newAlias,
                        quickSave = quickSaveMode == "always" || quickSave,
                        quickSaveMode = quickSaveMode,
                        autoCopyText = autoCopyText,
                        port = newPort
                    )
                )
                Toast.makeText(context, "Settings saved successfully", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(listOf(AccentViolet, AccentCyan)),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Save Settings",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
