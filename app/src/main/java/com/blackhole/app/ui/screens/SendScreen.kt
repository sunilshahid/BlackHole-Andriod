package com.blackhole.app.ui.screens

import android.app.Activity
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.rememberLazyListState
import com.blackhole.app.model.AppSettings
import com.blackhole.app.model.FavoriteDevice
import com.blackhole.app.model.FileItem
import com.blackhole.app.model.PeerDevice
import com.blackhole.app.model.TransferState
import com.blackhole.app.network.WebRtcEngine
import com.blackhole.app.ui.components.AppPickerDialog
import com.blackhole.app.ui.components.EmbeddedMediaPickerDialog
import com.blackhole.app.ui.components.FavoritesDialog
import com.blackhole.app.ui.components.LiveTransferCard
import com.blackhole.app.ui.components.PeerDeviceCard
import com.blackhole.app.ui.components.RemoteShareDialog
import com.blackhole.app.ui.components.RoomCodeDialog
import com.blackhole.app.ui.components.WebShareDialog
import com.blackhole.app.ui.theme.*
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    selectedFiles: List<FileItem>,
    onFilesAdded: (List<FileItem>) -> Unit,
    onFileRemoved: (Int) -> Unit = {},
    onClearFiles: () -> Unit,
    discoveredPeers: List<PeerDevice>,
    transferState: TransferState,
    onSendToPeer: (PeerDevice) -> Unit,
    onSendToIp: (String, Int) -> Unit,
    onCancelTransfer: () -> Unit,
    onRescanPeers: () -> Unit,
    onOpenWebPortal: () -> Unit,
    localIp: String = "",
    settings: AppSettings = com.blackhole.app.model.AppSettings(),
    webRtcEngine: WebRtcEngine? = null,
    onTransferStateChange: ((TransferState) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onOpenAppPicker: () -> Unit = {},
    onToggleFavorite: (PeerDevice) -> Unit = {},
    onRemoveFavoriteDevice: (String) -> Unit = {},
    onUpdateFavoriteAlias: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current

    var showDirectIpDialog by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showFavoritesDialog by remember { mutableStateOf(false) }
    var showRemoteShareDialog by remember { mutableStateOf(false) }
    var showWebShareDialog by remember { mutableStateOf(false) }
    var showEmbeddedMediaPicker by remember { mutableStateOf(false) }
    var showEditSelectionDialog by remember { mutableStateOf(false) }
    var showAddSelectionSheet by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val pullDistance = remember { mutableStateOf(0f) }

    // Pick Any File
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val items = uris.map { uri ->
                getFileItemFromUri(context, uri)
            }
            onFilesAdded(items)
        }
    }

    // Pick Media (Images / Videos) - Fallback
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val items = uris.map { uri ->
                getFileItemFromUri(context, uri)
            }
            onFilesAdded(items)
        }
    }

    // Pick Media via Native Android Gallery View System (LG Gallery, Samsung Gallery, Google Photos, etc.)
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = mutableListOf<Uri>()
            result.data?.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i).uri?.let { uris.add(it) }
                }
            } ?: result.data?.data?.let { uris.add(it) }

            if (uris.isNotEmpty()) {
                val items = uris.map { uri ->
                    getFileItemFromUri(context, uri)
                }
                onFilesAdded(items)
            }
        }
    }

    val openNativeGallery: () -> Unit = {
        try {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            galleryPickerLauncher.launch(intent)
        } catch (e: Exception) {
            // Fallback to system document picker if ACTION_PICK fails
            try {
                mediaPickerLauncher.launch("image/*")
            } catch (err: Exception) {
                Toast.makeText(context, "Could not open gallery: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Pick Folder (Open Document Tree)
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            val item = FileItem(
                name = treeUri.lastPathSegment?.substringAfterLast(':') ?: "Selected_Folder",
                size = 1024L,
                uri = treeUri,
                mimeType = "vnd.android.document/directory"
            )
            onFilesAdded(listOf(item))
            Toast.makeText(context, "Folder staged for transfer", Toast.LENGTH_SHORT).show()
        }
    }

    val totalBytes = selectedFiles.sumOf { it.size }
    val formattedTotalSize = formatBytes(totalBytes)

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            if (dragAmount > 0 && lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0) {
                                pullDistance.value += dragAmount
                                if (pullDistance.value > 120f) {
                                    onRescanPeers()
                                    pullDistance.value = 0f
                                }
                            }
                        },
                        onDragEnd = { pullDistance.value = 0f }
                    )
                },
            contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // ==========================================
        // 1. SELECTION SECTION (Matching Desktop App)
        // ==========================================
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Selection",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(Modifier.height(10.dp))

                if (selectedFiles.isEmpty()) {
                    // Empty State: 6 Action Tiles (File, Media, Folder, Text, Paste, App)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Row 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SelectionActionTile(
                                title = "File",
                                icon = Icons.Default.InsertDriveFile,
                                tint = AccentCyan,
                                modifier = Modifier.weight(1f),
                                onClick = { filePickerLauncher.launch("*/*") }
                            )
                            SelectionActionTile(
                                title = "Media",
                                icon = Icons.Default.Image,
                                tint = AccentGreen,
                                modifier = Modifier.weight(1f),
                                onClick = { showEmbeddedMediaPicker = true }
                            )
                            SelectionActionTile(
                                title = "Folder",
                                icon = Icons.Default.Folder,
                                tint = AccentViolet,
                                modifier = Modifier.weight(1f),
                                onClick = { folderPickerLauncher.launch(null) }
                            )
                        }

                        // Row 2
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SelectionActionTile(
                                title = "Text",
                                icon = Icons.Default.Notes,
                                tint = AccentPink,
                                modifier = Modifier.weight(1f),
                                onClick = { showTextDialog = true }
                            )
                            SelectionActionTile(
                                title = "Paste",
                                icon = Icons.Default.ContentPaste,
                                tint = AccentGreen,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    handlePasteFromClipboard(context, onFilesAdded)
                                }
                            )
                            SelectionActionTile(
                                title = "App",
                                icon = Icons.Default.Apps,
                                tint = AccentCyan,
                                modifier = Modifier.weight(1f),
                                onClick = onOpenAppPicker
                            )
                        }
                    }
                } else {
                    // Active State: Selection Card with Thumbnails and Category Icons
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderGlow, RoundedCornerShape(18.dp)),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header Row: Count & Size + Edit Button + Clear (✕)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text(
                                            text = "Files: ${selectedFiles.size}",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Size: $formattedTotalSize",
                                            fontSize = 13.sp,
                                            color = AccentCyan,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    TextButton(
                                        onClick = { showEditSelectionDialog = true },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.textButtonColors(contentColor = AccentCyan)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Edit", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                IconButton(onClick = onClearFiles) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = AccentRed)
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // Horizontal scroll of file preview tiles (Thumbnails, Video clapper, Doc, 3-line Text, App)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(selectedFiles) { file ->
                                    SelectionPreviewTile(
                                        file = file,
                                        onClick = { showEditSelectionDialog = true }
                                    )
                                }
                                item {
                                    AddSelectionTile(
                                        onClick = { showAddSelectionSheet = true }
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "⚡ Ready to send to nearby peer or web link",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. NEARBY DEVICES (With 4 Action Icons Header)
        // ==========================================
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Nearby devices",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Badge(containerColor = Color(0x338B5CF6)) {
                        Text(
                            text = "${discoveredPeers.size}",
                            color = AccentViolet,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(2.dp)
                        )
                    }
                }

                // 4 Circular Glass Action Buttons (Matching PC action-circle-btn)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 1. Favorites Action Button
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x33F43F5E), CircleShape)
                            .clickable { showFavoritesDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Favorites",
                            tint = Color(0xFFF43F5E),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    // 2. Direct IP / Manual Send
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x1FFFFFFF), CircleShape)
                            .clickable { showDirectIpDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Direct IP",
                            tint = Color(0xFFD4D4D8),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    // 3. Web Share Link / Hotspot
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x1FFFFFFF), CircleShape)
                            .clickable { showWebShareDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Share via Link",
                            tint = Color(0xFFD4D4D8),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    // 4. Send with Room Code
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x1FFFFFFF), CircleShape)
                            .clickable { showRemoteShareDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Send with Room Code",
                            tint = Color(0xFFD4D4D8),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }

        // Peer Cards or PC Radar Scanning
        if (discoveredPeers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    PcRadarScanningView()
                }
            }
        } else {
            val sortedPeers = discoveredPeers.sortedWith(
                compareByDescending<PeerDevice> { peer ->
                    settings.favorites.any { (it.fingerprint.isNotEmpty() && peer.fingerprint.isNotEmpty() && it.fingerprint.equals(peer.fingerprint, ignoreCase = true)) || (it.ip.isNotEmpty() && it.ip == peer.ip) }
                }.thenBy { it.alias.lowercase() }
            )

            items(sortedPeers, key = { it.ip + ":" + it.port }) { peer ->
                val isFav = settings.favorites.any { 
                    (it.fingerprint.isNotEmpty() && peer.fingerprint.isNotEmpty() && it.fingerprint.equals(peer.fingerprint, ignoreCase = true)) || (it.ip.isNotEmpty() && it.ip == peer.ip) 
                }
                PeerDeviceCard(
                    device = peer.copy(isFavorite = isFav),
                    hasSelectedFiles = selectedFiles.isNotEmpty(),
                    isSending = transferState.isActive,
                    onSendClicked = {
                        if (selectedFiles.isEmpty()) {
                            filePickerLauncher.launch("*/*")
                        } else {
                            onSendToPeer(peer)
                        }
                    },
                    onToggleFavorite = {
                        onToggleFavorite(peer)
                    }
                )
            }
        }
    }

    // Modal: Favorites Dialog
    if (showFavoritesDialog) {
        FavoritesDialog(
            favorites = settings.favorites,
            onSendToFavorite = { ip, port, alias ->
                showFavoritesDialog = false
                if (selectedFiles.isEmpty()) {
                    filePickerLauncher.launch("*/*")
                } else {
                    onSendToIp(ip, port)
                }
            },
            onRemoveFavorite = { id ->
                onRemoveFavoriteDevice(id)
            },
            onUpdateAlias = { id, newAlias ->
                onUpdateFavoriteAlias(id, newAlias)
            },
            onDismiss = { showFavoritesDialog = false }
        )
    }

    // Modal 1: Direct IP Send Dialog
    if (showDirectIpDialog) {
        DirectIpSendDialog(
            hasSelectedFiles = selectedFiles.isNotEmpty(),
            onDismiss = { showDirectIpDialog = false },
            onSend = { ip, port ->
                showDirectIpDialog = false
                if (selectedFiles.isEmpty()) {
                    filePickerLauncher.launch("*/*")
                } else {
                    onSendToIp(ip, port)
                }
            }
        )
    }

    // Modal 2: Text Share Dialog
    if (showTextDialog) {
        ShareTextDialog(
            onDismiss = { showTextDialog = false },
            onSendText = { text ->
                showTextDialog = false
                saveTextAsFileItem(context, text)?.let { item ->
                    onFilesAdded(listOf(item))
                    Toast.makeText(context, "Text note ready to send", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Modal 3: Web Share Dialog (Local Web Portal)
    if (showWebShareDialog) {
        WebShareDialog(
            localIp = localIp,
            settings = settings,
            selectedFiles = selectedFiles,
            onDismiss = { showWebShareDialog = false }
        )
    }

    // Modal 4: Remote Share (Send with Room Code Dialog)
    if (showRemoteShareDialog) {
        RemoteShareDialog(
            selectedFiles = selectedFiles,
            webRtcEngine = webRtcEngine,
            onFilesAdded = onFilesAdded,
            onTransferStateChange = onTransferStateChange,
            onDismiss = { showRemoteShareDialog = false }
        )
    }

    // Modal 5: Embedded Media Picker (Inside-App Photo Grid)
    if (showEmbeddedMediaPicker) {
        EmbeddedMediaPickerDialog(
            onDismiss = { showEmbeddedMediaPicker = false },
            onMediaSelected = { mediaItems ->
                showEmbeddedMediaPicker = false
                if (mediaItems.isNotEmpty()) {
                    onFilesAdded(mediaItems)
                    Toast.makeText(context, "${mediaItems.size} photo(s) selected", Toast.LENGTH_SHORT).show()
                }
            },
            onOpenSystemGallery = {
                showEmbeddedMediaPicker = false
                openNativeGallery()
            }
        )
    }

    // Modal 6: Selection Edit Dialog (View & delete individual items, or delete all)
    if (showEditSelectionDialog) {
        SelectionEditDialog(
            selectedFiles = selectedFiles,
            onFileRemoved = onFileRemoved,
            onClearFiles = onClearFiles,
            onDismiss = { showEditSelectionDialog = false }
        )
    }

    // Modal 7: Add to Selection Sheet (6 action tiles: File, Media, Folder, Text, Paste, App)
    if (showAddSelectionSheet) {
        AddSelectionSheet(
            onDismiss = { showAddSelectionSheet = false },
            onPickFile = { filePickerLauncher.launch("*/*") },
            onPickMedia = { showEmbeddedMediaPicker = true },
            onPickText = { showTextDialog = true },
            onPaste = { handlePasteFromClipboard(context, onFilesAdded) },
            onPickFolder = { folderPickerLauncher.launch(null) },
            onPickApp = onOpenAppPicker
        )
    }
}
}

// -------------------------------------------------------------
// UI Subcomponents
// -------------------------------------------------------------

@Composable
fun SelectionActionTile(
    title: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(78.dp)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

@Composable
fun ShareTextDialog(
    onDismiss: () -> Unit,
    onSendText: (String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("blackhole_text_prefs", Context.MODE_PRIVATE) }
    var history by remember {
        val raw = prefs.getString("history", "") ?: ""
        mutableStateOf(if (raw.isEmpty()) emptyList() else raw.split("\u0000").filter { it.isNotBlank() })
    }
    var textInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Share Text Note", color = Color.White, fontWeight = FontWeight.Bold)
                if (history.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            prefs.edit().remove("history").apply()
                            history = emptyList()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear History",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column {
                Text(
                    "Type or paste any text or link to send as a note:",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Paste link, message or code snippet...", color = TextMuted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentPink,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = CardSurface,
                        unfocusedContainerColor = CardSurface
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                if (history.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Recent (Memory):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentCyan
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(history) { pastNote ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CardSurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3306B6D4)),
                                modifier = Modifier.clickable {
                                    textInput = pastNote
                                }
                            ) {
                                Text(
                                    text = if (pastNote.length > 22) pastNote.take(22) + "..." else pastNote,
                                    fontSize = 11.sp,
                                    color = Color(0xFFE2E8F0),
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = textInput.trim()
                    if (trimmed.isNotEmpty()) {
                        val updated = (listOf(trimmed) + history.filter { it != trimmed }).take(15)
                        prefs.edit().putString("history", updated.joinToString("\u0000")).apply()
                        history = updated
                    }
                    onSendText(textInput)
                },
                enabled = textInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPink)
            ) {
                Text("Stage Text", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
fun DirectIpSendDialog(
    hasSelectedFiles: Boolean,
    onDismiss: () -> Unit,
    onSend: (String, Int) -> Unit
) {
    var ipText by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("53317") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        title = {
            Text(
                "Enter address",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    "Target IP & Port (e.g. 192.168.0.x):",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = ipText,
                    onValueChange = { ipText = it },
                    placeholder = { Text("192.168.0.x", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = CardSurface,
                        unfocusedContainerColor = CardSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    placeholder = { Text("53317", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = CardSurface,
                        unfocusedContainerColor = CardSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = portText.toIntOrNull() ?: 53317
                    if (ipText.isNotBlank()) {
                        onSend(ipText.trim(), port)
                    }
                },
                enabled = ipText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
            ) {
                Text("Confirm", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}


@Composable
fun PcRadarScanningView(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSpin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarAngle"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DotPulse"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Rotating Dashed Ring + Cyan Dot (Matching Windows Radar)
        Box(
            modifier = Modifier.size(54.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(54.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = (size.minDimension / 2f) - 3.dp.toPx()
                rotate(degrees = angle, pivot = center) {
                    drawCircle(
                        color = Color(0x668B5CF6),
                        radius = radius,
                        center = center,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                        )
                    )
                }
            }

            // Glowing Cyan Center Dot
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF06B6D4).copy(alpha = pulseAlpha))
                    .border(1.5.dp, Color.White.copy(alpha = pulseAlpha), CircleShape)
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Scanning for nearby BlackHole devices on Wi-Fi...",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Ensure the receiver has BlackHole open on the same Wi-Fi.",
            fontSize = 12.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

// Helpers
private fun handlePasteFromClipboard(context: Context, onFilesAdded: (List<FileItem>) -> Unit) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = clipboard.primaryClip
    if (clip != null && clip.itemCount > 0) {
        val item = clip.getItemAt(0)
        val text = item.text?.toString()
        val uri = item.uri

        if (uri != null) {
            onFilesAdded(listOf(getFileItemFromUri(context, uri)))
            Toast.makeText(context, "Pasted file from clipboard", Toast.LENGTH_SHORT).show()
        } else if (!text.isNullOrBlank()) {
            saveTextAsFileItem(context, text)?.let { fileItem ->
                onFilesAdded(listOf(fileItem))
                Toast.makeText(context, "Pasted text note from clipboard", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
    }
}

private fun saveTextAsFileItem(context: Context, text: String): FileItem? {
    return try {
        val file = File(context.cacheDir, "Shared_Note_${System.currentTimeMillis()}.txt")
        FileOutputStream(file).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        FileItem(
            name = file.name,
            size = file.length(),
            path = file.absolutePath,
            mimeType = "text/plain",
            preview = text
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun getFileItemFromUri(context: Context, uri: Uri): FileItem {
    var name = "unknown_file"
    var size = 0L

    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // Comprehensive fallbacks for size when cursor returns 0 or null
    if (size <= 0L) {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val s = pfd.statSize
                if (s > 0) size = s
            }
        } catch (_: Exception) {}
    }
    if (size <= 0L) {
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                val s = afd.length
                if (s > 0) size = s
            }
        } catch (_: Exception) {}
    }
    if (size <= 0L) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val ch = (stream as? java.io.FileInputStream)?.channel
                if (ch != null) {
                    val s = ch.size()
                    if (s > 0) size = s
                } else {
                    val s = stream.available().toLong()
                    if (s > 0) size = s
                }
            }
        } catch (_: Exception) {}
    }
    if (size <= 0L && uri.scheme == "file") {
        try {
            val f = java.io.File(uri.path ?: "")
            if (f.exists() && f.length() > 0) size = f.length()
        } catch (_: Exception) {}
    }

    if (name == "unknown_file" || name.isEmpty()) {
        uri.lastPathSegment?.let { seg ->
            val clean = seg.substringAfterLast('/')
            if (clean.isNotEmpty()) name = clean
        }
    }

    val mime = context.contentResolver.getType(uri) ?: "*/*"
    return FileItem(name = name, size = size, uri = uri, mimeType = mime)
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

// -------------------------------------------------------------
// Selection Preview & Edit Subcomponents
// -------------------------------------------------------------

enum class FileCategory {
    IMAGE, VIDEO, TEXT, APP, DOCUMENT
}

fun getFileCategory(file: FileItem): FileCategory {
    val name = file.name.lowercase()
    val mime = file.mimeType.lowercase()
    return when {
        mime.startsWith("image/") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".bmp") -> FileCategory.IMAGE
        mime.startsWith("video/") || name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mov") || name.endsWith(".avi") || name.endsWith(".webm") || name.endsWith(".3gp") -> FileCategory.VIDEO
        mime.startsWith("text/") || name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".log") || name.startsWith("shared_note_") -> FileCategory.TEXT
        mime == "application/vnd.android.package-archive" || name.endsWith(".apk") -> FileCategory.APP
        else -> FileCategory.DOCUMENT
    }
}

fun loadThumbnail(context: Context, file: FileItem): ImageBitmap? {
    return try {
        val uri = file.uri
        if (uri != null) {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            var inSampleSize = 1
            val reqDim = 150
            if (options.outHeight > reqDim || options.outWidth > reqDim) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= reqDim && (halfWidth / inSampleSize) >= reqDim) {
                    inSampleSize *= 2
                }
            }
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)?.asImageBitmap()
            }
        } else if (!file.path.isNullOrBlank()) {
            val f = File(file.path)
            if (f.exists()) {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(f.absolutePath, options)
                var inSampleSize = 1
                val reqDim = 150
                if (options.outHeight > reqDim || options.outWidth > reqDim) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while ((halfHeight / inSampleSize) >= reqDim && (halfWidth / inSampleSize) >= reqDim) {
                        inSampleSize *= 2
                    }
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                }
                BitmapFactory.decodeFile(f.absolutePath, decodeOptions)?.asImageBitmap()
            } else null
        } else null
    } catch (e: Exception) {
        null
    }
}

@Composable
fun SelectionPreviewTile(
    file: FileItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val category = remember(file) { getFileCategory(file) }
    var thumbnail by remember(file) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(file) {
        if (category == FileCategory.IMAGE) {
            withContext(Dispatchers.IO) {
                thumbnail = loadThumbnail(context, file)
            }
        }
    }

    Box(
        modifier = modifier
            .size(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            category == FileCategory.IMAGE && thumbnail != null -> {
                Image(
                    bitmap = thumbnail!!,
                    contentDescription = file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            category == FileCategory.IMAGE -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AccentGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Image, contentDescription = "Image", tint = AccentGreen, modifier = Modifier.size(32.dp))
                }
            }
            category == FileCategory.VIDEO -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AccentViolet.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Movie, contentDescription = "Video", tint = AccentViolet, modifier = Modifier.size(32.dp))
                }
            }
            category == FileCategory.TEXT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AccentPink.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Notes, contentDescription = "Text", tint = AccentPink, modifier = Modifier.size(32.dp))
                }
            }
            category == FileCategory.APP -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AccentCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Apps, contentDescription = "App", tint = AccentCyan, modifier = Modifier.size(32.dp))
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x2606B6D4)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.InsertDriveFile, contentDescription = "Document", tint = AccentCyan, modifier = Modifier.size(32.dp))
                }
            }
        }

        // Bottom label pill
        val label = when (category) {
            FileCategory.VIDEO -> "VIDEO"
            FileCategory.TEXT -> "TEXT"
            FileCategory.APP -> "APP"
            FileCategory.DOCUMENT -> file.name.substringAfterLast('.', "DOC").uppercase().take(4)
            else -> if (thumbnail == null) "IMG" else ""
        }
        if (label.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0x99000000))
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AddSelectionTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x0D06B6D4))
            .border(1.dp, Color(0x3306B6D4), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add", tint = AccentCyan, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text("Add", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentCyan)
        }
    }
}

@Composable
fun SelectionEditDialog(
    selectedFiles: List<FileItem>,
    onFileRemoved: (Int) -> Unit,
    onClearFiles: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .border(1.dp, BorderGlow, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Selection (${selectedFiles.size})",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    TextButton(onClick = {
                        onClearFiles()
                        onDismiss()
                    }) {
                        Text("Delete all", color = AccentRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (selectedFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .padding(vertical = 30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No files selected", color = TextMuted, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(selectedFiles) { index, file ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
                                colors = CardDefaults.cardColors(containerColor = CardSurface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val cat = getFileCategory(file)
                                    val (icon, tint) = when (cat) {
                                        FileCategory.IMAGE -> Icons.Default.Image to AccentGreen
                                        FileCategory.VIDEO -> Icons.Default.Movie to AccentViolet
                                        FileCategory.TEXT -> Icons.Default.Notes to AccentPink
                                        FileCategory.APP -> Icons.Default.Apps to AccentCyan
                                        FileCategory.DOCUMENT -> Icons.Default.InsertDriveFile to AccentCyan
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(tint.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                                    }

                                    Spacer(Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formatBytes(file.size),
                                            fontSize = 11.sp,
                                            color = TextMuted,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    IconButton(onClick = { onFileRemoved(index) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AccentRed, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AddSelectionSheet(
    onDismiss: () -> Unit,
    onPickFile: () -> Unit,
    onPickMedia: () -> Unit,
    onPickText: () -> Unit,
    onPaste: () -> Unit,
    onPickFolder: () -> Unit,
    onPickApp: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderGlow, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add to selection",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 2 Rows x 3 Columns
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SelectionActionTile(
                            title = "File",
                            icon = Icons.Default.InsertDriveFile,
                            tint = AccentCyan,
                            modifier = Modifier.weight(1f),
                            onClick = { onDismiss(); onPickFile() }
                        )
                        SelectionActionTile(
                            title = "Media",
                            icon = Icons.Default.Image,
                            tint = AccentGreen,
                            modifier = Modifier.weight(1f),
                            onClick = { onDismiss(); onPickMedia() }
                        )
                        SelectionActionTile(
                            title = "Folder",
                            icon = Icons.Default.Folder,
                            tint = AccentViolet,
                            modifier = Modifier.weight(1f),
                            onClick = { onDismiss(); onPickFolder() }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SelectionActionTile(
                            title = "Text",
                            icon = Icons.Default.Notes,
                            tint = AccentPink,
                            modifier = Modifier.weight(1f),
                            onClick = { onDismiss(); onPickText() }
                        )
                        SelectionActionTile(
                            title = "Paste",
                            icon = Icons.Default.ContentPaste,
                            tint = AccentGreen,
                            modifier = Modifier.weight(1f),
                            onClick = { onDismiss(); onPaste() }
                        )
                        SelectionActionTile(
                            title = "App",
                            icon = Icons.Default.Apps,
                            tint = AccentCyan,
                            modifier = Modifier.weight(1f),
                            onClick = { onDismiss(); onPickApp() }
                        )
                    }
                }
            }
        }
    }
}
