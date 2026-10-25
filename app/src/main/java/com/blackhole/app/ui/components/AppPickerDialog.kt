package com.blackhole.app.ui.components

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackhole.app.model.FileItem
import com.blackhole.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class InstalledAppItem(
    val label: String,
    val packageName: String,
    val versionName: String,
    val apkFile: File,
    val iconBitmap: ImageBitmap?
)

private fun Drawable.toSafeBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null && !bitmap.isRecycled) {
        return bitmap
    }
    val w = if (intrinsicWidth > 0) intrinsicWidth.coerceIn(48, 160) else 96
    val h = if (intrinsicHeight > 0) intrinsicHeight.coerceIn(48, 160) else 96
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}

private fun formatAppSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format("%.1f MB", mb)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    onDismiss: () -> Unit,
    onAppsSelected: (List<FileItem>) -> Unit
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    var installedApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var isMultiSelect by remember { mutableStateOf(false) }
    var selectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Load installed applications and their real high-res icons on background thread
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val apps = try {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
            } catch (e: Exception) {
                emptyList()
            }

            val filtered = apps.filter { app ->
                // Filter user apps or apps with active launcher intent
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || pm.getLaunchIntentForPackage(app.packageName) != null
            }.mapNotNull { app ->
                try {
                    val label = pm.getApplicationLabel(app).toString()
                    val apkFile = File(app.sourceDir)
                    if (!apkFile.exists()) return@mapNotNull null

                    val vName = try {
                        val pInfo = pm.getPackageInfo(app.packageName, 0)
                        pInfo.versionName ?: ""
                    } catch (e: Exception) {
                        ""
                    }

                    val iconBmp = try {
                        val drawable = pm.getApplicationIcon(app)
                        drawable.toSafeBitmap().asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }

                    InstalledAppItem(
                        label = label,
                        packageName = app.packageName,
                        versionName = vName,
                        apkFile = apkFile,
                        iconBitmap = iconBmp
                    )
                } catch (e: Exception) {
                    null
                }
            }.sortedBy { it.label.lowercase() }

            withContext(Dispatchers.Main) {
                installedApps = filtered
                isLoading = false
            }
        }
    }

    // Filter apps based on search input
    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            val query = searchQuery.trim().lowercase()
            installedApps.filter {
                it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
            }
        }
    }

    val totalSelectedBytes = remember(selectedPackages, installedApps) {
        installedApps
            .filter { it.packageName in selectedPackages }
            .sumOf { it.apkFile.length() }
    }

    // Full-screen native view (No Dialog window, zero under-bar bleeding)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BlackSpace
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
                // 1. Top App Bar (Back Arrow, Title "Apps (APK)")
                TopAppBar(
                    title = {
                        Text(
                            text = "Apps (APK)",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        if (isMultiSelect && filteredApps.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    selectedPackages = if (selectedPackages.size == filteredApps.size) {
                                        emptySet()
                                    } else {
                                        filteredApps.map { it.packageName }.toSet()
                                    }
                                }
                            ) {
                                Text(
                                    text = if (selectedPackages.size == filteredApps.size) "Deselect All" else "Select All",
                                    color = AccentCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BlackSpace)
                )

                // 2. Search Input Field (Cosmic Theme)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x14FFFFFF),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Search installed apps...",
                                    color = TextMuted,
                                    fontSize = 14.sp
                                )
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Sub-Header Row: "[Count] Apps" on Left, "Select Multiple Apps [Switch]" on Right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isLoading) "Scanning apps..." else "${filteredApps.size} Apps",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isMultiSelect = !isMultiSelect }
                    ) {
                        Text(
                            text = "Select Multiple Apps",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = isMultiSelect,
                            onCheckedChange = { isMultiSelect = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentViolet,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = Color(0x33FFFFFF)
                            )
                        )
                    }
                }

                HorizontalDivider(color = Color(0x1AFFFFFF), thickness = 0.5.dp)

                // 4. Content Area: Loading Indicator or Apps List
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (isLoading) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = AccentCyan,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "Loading installed applications...",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    } else if (filteredApps.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No matching apps found" else "No user apps available",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                val isSelected = app.packageName in selectedPackages

                                Surface(
                                    onClick = {
                                        if (isMultiSelect) {
                                            selectedPackages = if (isSelected) {
                                                selectedPackages - app.packageName
                                            } else {
                                                selectedPackages + app.packageName
                                            }
                                        } else {
                                            // Single-app instant selection
                                            val item = FileItem(
                                                name = "${app.label}.apk",
                                                size = app.apkFile.length(),
                                                path = app.apkFile.absolutePath,
                                                mimeType = "application/vnd.android.package-archive"
                                            )
                                            onAppsSelected(listOf(item))
                                        }
                                    },
                                    color = if (isSelected) Color(0x268B5CF6) else Color.Transparent,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Real App Icon (48dp with squircle / 12dp rounded corners)
                                        if (app.iconBitmap != null) {
                                            Image(
                                                bitmap = app.iconBitmap,
                                                contentDescription = app.label,
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0x2606B6D4)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Android,
                                                    contentDescription = null,
                                                    tint = AccentCyan,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }

                                        Spacer(Modifier.width(14.dp))

                                        // App Details Column
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = app.label,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Spacer(Modifier.height(2.dp))

                                            val subline = buildString {
                                                append(formatAppSize(app.apkFile.length()))
                                                if (app.versionName.isNotEmpty()) {
                                                    append(" • v${app.versionName}")
                                                }
                                            }
                                            Text(
                                                text = subline,
                                                fontSize = 12.sp,
                                                color = TextSecondary,
                                                maxLines = 1
                                            )

                                            Text(
                                                text = app.packageName,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = TextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        // Multi-Select Checkbox
                                        if (isMultiSelect) {
                                            Spacer(Modifier.width(8.dp))
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    selectedPackages = if (checked) {
                                                        selectedPackages + app.packageName
                                                    } else {
                                                        selectedPackages - app.packageName
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = AccentViolet,
                                                    uncheckedColor = TextMuted,
                                                    checkmarkColor = Color.White
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Docked Bottom Button for Multi-Select Mode
                AnimatedVisibility(
                    visible = isMultiSelect && selectedPackages.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        color = Color(0xF7131427),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x338B5CF6)),
                        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "${selectedPackages.size} app(s) selected",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = formatAppSize(totalSelectedBytes),
                                    fontSize = 12.sp,
                                    color = AccentCyan
                                )
                            }

                            Button(
                                onClick = {
                                    val items = installedApps
                                        .filter { it.packageName in selectedPackages }
                                        .map { app ->
                                            FileItem(
                                                name = "${app.label}.apk",
                                                size = app.apkFile.length(),
                                                path = app.apkFile.absolutePath,
                                                mimeType = "application/vnd.android.package-archive"
                                            )
                                        }
                                    onAppsSelected(items)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentViolet),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Send",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
fun AppPickerDialog(
    onDismiss: () -> Unit,
    onAppsSelected: (List<FileItem>) -> Unit
) {
    AppPickerScreen(onDismiss = onDismiss, onAppsSelected = onAppsSelected)
}
