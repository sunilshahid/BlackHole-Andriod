package com.blackhole.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import com.blackhole.app.data.SettingsStore
import com.blackhole.app.model.*
import com.blackhole.app.network.DiscoveryEngine
import com.blackhole.app.network.LocalHttpServer
import com.blackhole.app.network.QuickShareManager
import com.blackhole.app.network.TcpClientSender
import com.blackhole.app.network.TcpTransferServer
import com.blackhole.app.network.WebRtcEngine
import com.blackhole.app.service.BlackHoleService
import com.blackhole.app.ui.components.AppPickerScreen
import com.blackhole.app.ui.components.FavoritesDialog
import com.blackhole.app.ui.components.IncomingRequestDialog
import com.blackhole.app.ui.components.MessageReceivedDialog
import com.blackhole.app.ui.screens.ReceiveScreen
import com.blackhole.app.ui.screens.SendScreen
import com.blackhole.app.ui.screens.SettingsScreen
import com.blackhole.app.ui.screens.TransferScreen
import com.blackhole.app.ui.theme.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

enum class NavTab {
    RECEIVE,
    SEND,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private var discoveryEngine: DiscoveryEngine? = null
    private var tcpServer: TcpTransferServer? = null
    private var tcpSender: TcpClientSender? = null
    private var localHttpServer: LocalHttpServer? = null
    private var webRtcEngine: WebRtcEngine? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize WebRTC and Signaling Engine
        webRtcEngine = WebRtcEngine(this@MainActivity)

        // Request initial runtime permissions
        requestAppPermissions()

        setContent {
            var settings by remember {
                mutableStateOf(SettingsStore.load(this@MainActivity, getDeviceDefaultAlias()))
            }

            BlackHoleTheme(themeMode = settings.themeMode) {
                val coroutineScope = rememberCoroutineScope()
                var currentTab by remember { mutableStateOf(NavTab.RECEIVE) }
                var senderJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

                var selectedFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
                var showAppPicker by remember { mutableStateOf(false) }
                var showGlobalFavoritesDialog by remember { mutableStateOf(false) }
                var discoveredPeers by remember { mutableStateOf<List<PeerDevice>>(emptyList()) }
                var transferHistory by remember { mutableStateOf<List<TransferHistoryItem>>(emptyList()) }

                var senderTransferState by remember { mutableStateOf(TransferState()) }
                var receiverTransferState by remember { mutableStateOf(TransferState()) }

                var pendingRequest by remember {
                    mutableStateOf<Pair<IncomingRequest, CompletableDeferred<Boolean>>?>(null)
                }

                var receivedMessage by remember {
                    mutableStateOf<Pair<String, String>?>(null)
                }

                var pendingTextMessage by remember {
                    mutableStateOf<Triple<String, String, CompletableDeferred<Boolean>>?>(null)
                }

                var quickShareManager by remember { mutableStateOf<QuickShareManager?>(null) }

                val localIp = remember { getLocalIpv4Address() }

                // Handle Shared Content (from other apps)
                LaunchedEffect(intent) {
                    handleShareIntent(intent) { items ->
                        if (items.isNotEmpty()) {
                            selectedFiles = items
                            currentTab = NavTab.SEND
                        }
                    }
                }

                // Initialize Core Engines
                DisposableEffect(Unit) {
                    val isPeerFavorite: (fingerprint: String, ip: String) -> Boolean = { fp, ip ->
                        settings.favorites.any { fav ->
                            (fp.isNotEmpty() && fav.fingerprint.isNotEmpty() && fav.fingerprint.equals(fp, ignoreCase = true)) ||
                            (ip.isNotEmpty() && fav.ip.isNotEmpty() && fav.ip == ip)
                        }
                    }

                    val qs = QuickShareManager(
                        context = this@MainActivity,
                        onPeersDiscovered = { peers ->
                            peers.forEach { discoveryEngine?.registerOrUpdatePeer(it) }
                        },
                        onIncomingFile = { name, size ->
                            transferHistory = listOf(
                                TransferHistoryItem(name, size, "Complete", true)
                            ) + transferHistory
                        },
                        onIncomingText = { sender, text ->
                            if (settings.autoCopyText) {
                                try {
                                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("BlackHole Message", text)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(this@MainActivity, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {}
                            }
                            receivedMessage = sender to text
                        }
                    ).apply {
                        deviceAlias = settings.alias
                    }
                    quickShareManager = qs

                    tcpSender = TcpClientSender(this@MainActivity) { state ->
                        senderTransferState = state
                    }

                    localHttpServer = LocalHttpServer(
                        context = this@MainActivity,
                        getSharedFiles = { selectedFiles },
                        onIncomingRequest = { req ->
                            val isFav = isPeerFavorite("", req.senderIp)
                            val shouldAuto = (settings.quickSaveMode == "always") || (settings.quickSaveMode == "favorites" && isFav) || settings.quickSave
                            if (shouldAuto) {
                                true
                            } else {
                                val deferred = CompletableDeferred<Boolean>()
                                withContext(Dispatchers.Main) {
                                    pendingRequest = req to deferred
                                }
                                deferred.await()
                            }
                        },
                        onFileUploaded = { name, size ->
                            transferHistory = listOf(
                                TransferHistoryItem(name, size, "Complete", true)
                            ) + transferHistory
                        }
                    ).apply {
                        deviceAlias = settings.alias
                        quickSave = settings.quickSave
                        quickSaveMode = settings.quickSaveMode
                        autoCopyText = settings.autoCopyText
                        isFavoriteCheck = isPeerFavorite
                        onTextMessageReceived = { sender, text ->
                            if (settings.autoCopyText) {
                                try {
                                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("BlackHole Message", text)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(this@MainActivity, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {}
                            }
                            receivedMessage = sender to text
                        }
                        onTextMessageRequest = { sender, text ->
                            if (settings.autoCopyText) {
                                try {
                                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("BlackHole Message", text)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(this@MainActivity, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {}
                            }
                            val deferred = CompletableDeferred<Boolean>()
                            withContext(Dispatchers.Main) {
                                pendingTextMessage = Triple(sender, text, deferred)
                            }
                            val res = try {
                                withTimeout(60000L) {
                                    deferred.await()
                                }
                            } catch (e: Exception) {
                                false
                            }
                            withContext(Dispatchers.Main) {
                                if (pendingTextMessage?.third == deferred) {
                                    pendingTextMessage = null
                                }
                            }
                            res
                        }
                        onPeerDiscovered = { peer ->
                            discoveryEngine?.registerOrUpdatePeer(peer)
                        }
                        onTransferStateUpdate = { state ->
                            receiverTransferState = state
                        }
                    }

                    tcpServer = TcpTransferServer(
                        context = this@MainActivity,
                        onTransferState = { state ->
                            receiverTransferState = state
                        },
                        onIncomingRequest = { req ->
                            val isFav = isPeerFavorite("", req.senderIp)
                            val shouldAuto = (settings.quickSaveMode == "always") || (settings.quickSaveMode == "favorites" && isFav) || settings.quickSave
                            if (shouldAuto) {
                                true
                            } else {
                                val deferred = CompletableDeferred<Boolean>()
                                withContext(Dispatchers.Main) {
                                    pendingRequest = req to deferred
                                }
                                deferred.await()
                            }
                        },
                        onFileSaved = { name, size ->
                            transferHistory = listOf(
                                TransferHistoryItem(name, size, "Complete", true)
                            ) + transferHistory
                        }
                    ).apply {
                        port = if (settings.port in 1024..65535) settings.port else 53317
                        quickSave = settings.quickSave
                        quickSaveMode = settings.quickSaveMode
                        isFavoriteCheck = isPeerFavorite
                        localHttpHandler = localHttpServer
                        onTextMessageReceived = { sender, text ->
                            if (settings.autoCopyText) {
                                try {
                                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("BlackHole Message", text)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(this@MainActivity, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {}
                            }
                            receivedMessage = sender to text
                        }
                    }
                    tcpServer?.start()

                    discoveryEngine = DiscoveryEngine(this@MainActivity) { peers ->
                        discoveredPeers = peers
                        val peerIps = peers.map { it.ip }.filter { it.isNotBlank() }
                        if (peerIps.isNotEmpty()) {
                            webRtcEngine?.setKnownPeerIps(peerIps)
                        }
                    }.apply {
                        myAlias = settings.alias
                        myPort = if (settings.port in 1024..65535) settings.port else 53317
                    }
                    discoveryEngine?.start()

                    // Start background foreground service
                    try {
                        BlackHoleService.start(this@MainActivity)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    onDispose {
                        qs.stopAdvertising()
                        qs.stopDiscovery()
                        discoveryEngine?.stop()
                        tcpServer?.stop()
                        webRtcEngine?.cancel()
                        BlackHoleService.stop(this@MainActivity)
                    }
                }

                // Synchronize settings updates
                LaunchedEffect(settings) {
                    val safePort = if (settings.port in 1024..65535) settings.port else 53317
                    val isPeerFavorite: (fingerprint: String, ip: String) -> Boolean = { fp, ip ->
                        settings.favorites.any { fav ->
                            (fp.isNotEmpty() && fav.fingerprint.isNotEmpty() && fav.fingerprint.equals(fp, ignoreCase = true)) ||
                            (ip.isNotEmpty() && fav.ip.isNotEmpty() && fav.ip == ip)
                        }
                    }

                    discoveryEngine?.myAlias = settings.alias
                    discoveryEngine?.myPort = safePort
                    tcpServer?.quickSave = settings.quickSave
                    tcpServer?.quickSaveMode = settings.quickSaveMode
                    tcpServer?.isFavoriteCheck = isPeerFavorite
                    if (tcpServer?.port != safePort) {
                        tcpServer?.port = safePort
                        tcpServer?.restart()
                    }
                    localHttpServer?.deviceAlias = settings.alias
                    localHttpServer?.quickSave = settings.quickSave
                    localHttpServer?.quickSaveMode = settings.quickSaveMode
                    localHttpServer?.autoCopyText = settings.autoCopyText
                    localHttpServer?.isFavoriteCheck = isPeerFavorite
                }

                // Quick Share Lifecycle Handling
                LaunchedEffect(currentTab) {
                    if (currentTab == NavTab.RECEIVE) {
                        quickShareManager?.startAdvertising()
                    } else {
                        quickShareManager?.stopAdvertising()
                    }

                    if (currentTab == NavTab.SEND) {
                        quickShareManager?.startDiscovery()
                    } else {
                        quickShareManager?.stopDiscovery()
                    }
                }

                // Favorites helper methods
                val toggleFavorite: (PeerDevice) -> Unit = { peer ->
                    val existing = settings.favorites.find {
                        (it.fingerprint.isNotEmpty() && peer.fingerprint.isNotEmpty() && it.fingerprint.equals(peer.fingerprint, ignoreCase = true)) ||
                        (it.ip.isNotEmpty() && it.ip == peer.ip)
                    }
                    val newFavorites = if (existing != null) {
                        settings.favorites.filter { it != existing }
                    } else {
                        settings.favorites + FavoriteDevice(
                            id = peer.fingerprint.ifEmpty { peer.ip },
                            fingerprint = peer.fingerprint,
                            ip = peer.ip,
                            port = peer.port,
                            alias = peer.alias
                        )
                    }
                    val updated = settings.copy(favorites = newFavorites)
                    settings = updated
                    SettingsStore.save(this@MainActivity, updated)
                }

                val removeFavoriteDevice: (String) -> Unit = { id ->
                    val newFavorites = settings.favorites.filter { it.id != id }
                    val updated = settings.copy(favorites = newFavorites)
                    settings = updated
                    SettingsStore.save(this@MainActivity, updated)
                }

                val updateFavoriteAlias: (String, String) -> Unit = { id, newAlias ->
                    val newFavorites = settings.favorites.map {
                        if (it.id == id) it.copy(alias = newAlias) else it
                    }
                    val updated = settings.copy(favorites = newFavorites)
                    settings = updated
                    SettingsStore.save(this@MainActivity, updated)
                }

                // Send files helper
                val sendFilesToTarget: (String, Int, String, String) -> Unit = { ip, port, alias, protocol ->
                    if (selectedFiles.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Please select files to send first", Toast.LENGTH_SHORT).show()
                    } else {
                        receiverTransferState = TransferState() // Clear any stale receiver state
                        senderJob = coroutineScope.launch {
                            val success = tcpSender?.sendFiles(
                                peerIp = ip,
                                peerPort = port,
                                peerAlias = alias,
                                myAlias = settings.alias,
                                files = selectedFiles,
                                protocol = protocol
                            ) ?: false

                            val title = if (selectedFiles.size == 1) selectedFiles.first().name else "${selectedFiles.size} Files to $alias"
                            val totalSize = selectedFiles.sumOf { it.size }
                            transferHistory = listOf(
                                TransferHistoryItem(title, totalSize, if (success) "Complete" else "Failed", false)
                            ) + transferHistory
                        }
                    }
                }

                val isSenderTransferring = senderTransferState.isActive || senderTransferState.isComplete || senderTransferState.errorMessage != null
                val isReceiverTransferring = receiverTransferState.isActive || receiverTransferState.isComplete || receiverTransferState.errorMessage != null

                if (isSenderTransferring) {
                    TransferScreen(
                        state = senderTransferState,
                        isSender = true,
                        onCancel = {
                            webRtcEngine?.cancel()
                            senderJob?.cancel()
                            tcpSender?.cancel()
                            senderTransferState = TransferState()
                            selectedFiles = emptyList()
                        },
                        onDone = {
                            webRtcEngine?.cancel()
                            senderJob?.cancel()
                            senderTransferState = TransferState()
                            selectedFiles = emptyList()
                        }
                    )
                } else if (isReceiverTransferring) {
                    TransferScreen(
                        state = receiverTransferState,
                        isSender = false,
                        onCancel = {
                            webRtcEngine?.cancel()
                            tcpServer?.cancelCurrentTransfer()
                            receiverTransferState = TransferState()
                        },
                        onDone = {
                            webRtcEngine?.cancel()
                            receiverTransferState = TransferState()
                        }
                    )
                } else if (showAppPicker) {
                    AppPickerScreen(
                        onDismiss = { showAppPicker = false },
                        onAppsSelected = { apkItems ->
                            showAppPicker = false
                            selectedFiles = selectedFiles + apkItems
                            val msg = if (apkItems.size == 1) "${apkItems.first().name} ready to send" else "${apkItems.size} apps ready to send"
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Official Full BlackHole Cosmic Logo (matching Windows Desktop)
                                        Image(
                                            painter = painterResource(id = R.drawable.app_logo),
                                            contentDescription = "Logo",
                                            modifier = Modifier.size(38.dp)
                                        )

                                        Spacer(Modifier.width(10.dp))

                                        Column {
                                            Text(
                                                text = "BlackHole",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 18.sp,
                                                color = AppTheme.textPrimary
                                            )
                                            Text(
                                                text = "ZERO-KNOWLEDGE LAN & P2P",
                                                color = if (AppTheme.isDark) AccentCyan else Color(0xFF0D9488),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.8.sp
                                            )
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = AppTheme.background,
                                    titleContentColor = AppTheme.textPrimary
                                ),
                                actions = {
                                    if (currentTab == NavTab.RECEIVE) {
                                        IconButton(
                                            onClick = { showGlobalFavoritesDialog = true }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = "Favorites",
                                                tint = Color(0xFFF43F5E),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            )
                        },
                        bottomBar = {
                            // Full-Width Bottom Bar with subtle Top-Corner Curve
                            Surface(
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                                color = if (AppTheme.isDark) Color(0xF7131427) else Color(0xF7FFFFFF),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                ) {
                                    // Subtle top cosmic border line only (flat 0dp bottom)
                                    HorizontalDivider(
                                        color = if (AppTheme.isDark) Color(0x338B5CF6) else Color(0x330D9488),
                                        thickness = 1.dp
                                    )
                                    NavigationBar(
                                        containerColor = Color.Transparent,
                                        tonalElevation = 0.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(80.dp)
                                    ) {
                                        // Tab 1: Receive
                                        NavigationBarItem(
                                            selected = currentTab == NavTab.RECEIVE,
                                            onClick = { currentTab = NavTab.RECEIVE },
                                            icon = {
                                                Icon(
                                                    imageVector = Icons.Default.Wifi,
                                                    contentDescription = "Receive",
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = "Receive",
                                                    fontWeight = if (currentTab == NavTab.RECEIVE) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 12.sp
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = Color.White,
                                                selectedTextColor = if (AppTheme.isDark) AccentCyan else Color(0xFF0D9488),
                                                indicatorColor = if (AppTheme.isDark) AccentCyan else Color(0xFF0D9488),
                                                unselectedIconColor = AppTheme.textMuted,
                                                unselectedTextColor = AppTheme.textMuted
                                            )
                                        )

                                        // Tab 2: Send
                                        NavigationBarItem(
                                            selected = currentTab == NavTab.SEND,
                                            onClick = { currentTab = NavTab.SEND },
                                            icon = {
                                                Icon(
                                                    imageVector = Icons.Default.Send,
                                                    contentDescription = "Send",
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = "Send",
                                                    fontWeight = if (currentTab == NavTab.SEND) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 12.sp
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = Color.White,
                                                selectedTextColor = if (AppTheme.isDark) AccentViolet else Color(0xFF7C3AED),
                                                indicatorColor = if (AppTheme.isDark) AccentViolet else Color(0xFF7C3AED),
                                                unselectedIconColor = AppTheme.textMuted,
                                                unselectedTextColor = AppTheme.textMuted
                                            )
                                        )

                                        // Tab 3: Settings
                                        NavigationBarItem(
                                            selected = currentTab == NavTab.SETTINGS,
                                            onClick = { currentTab = NavTab.SETTINGS },
                                            icon = {
                                                Icon(
                                                    imageVector = Icons.Default.Settings,
                                                    contentDescription = "Settings",
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = "Settings",
                                                    fontWeight = if (currentTab == NavTab.SETTINGS) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 12.sp
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = Color.White,
                                                selectedTextColor = if (AppTheme.isDark) AccentCyan else Color(0xFF0D9488),
                                                indicatorColor = if (AppTheme.isDark) AccentCyan else Color(0xFF0D9488),
                                                unselectedIconColor = AppTheme.textMuted,
                                                unselectedTextColor = AppTheme.textMuted
                                            )
                                        )
                                    }
                                }
                            }
                        },
                        containerColor = AppTheme.background
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (currentTab) {
                                NavTab.SEND -> {
                                    SendScreen(
                                        selectedFiles = selectedFiles,
                                        onFilesAdded = { newItems ->
                                            selectedFiles = selectedFiles + newItems
                                        },
                                        onFileRemoved = { index ->
                                            if (index in selectedFiles.indices) {
                                                selectedFiles = selectedFiles.filterIndexed { i, _ -> i != index }
                                            }
                                        },
                                        onClearFiles = {
                                            selectedFiles = emptyList()
                                        },
                                        discoveredPeers = discoveredPeers,
                                        transferState = senderTransferState,
                                        onSendToPeer = { peer ->
                                            sendFilesToTarget(peer.ip, peer.port, peer.alias, peer.protocol)
                                        },
                                        onSendToIp = { ip, port ->
                                            sendFilesToTarget(ip, port, ip, "blackhole")
                                        },
                                        onCancelTransfer = {
                                            tcpSender?.isCancelled = true
                                            senderTransferState = senderTransferState.copy(isActive = false, errorMessage = "Cancelled by user")
                                        },
                                        onRescanPeers = {
                                            discoveryEngine?.stop()
                                            discoveryEngine?.start()
                                            Toast.makeText(this@MainActivity, "Rescanning network...", Toast.LENGTH_SHORT).show()
                                        },
                                        onOpenWebPortal = {
                                            currentTab = NavTab.RECEIVE
                                        },
                                        localIp = localIp,
                                        settings = settings,
                                        webRtcEngine = webRtcEngine,
                                        onTransferStateChange = { senderTransferState = it },
                                        onOpenAppPicker = { showAppPicker = true },
                                        onToggleFavorite = toggleFavorite,
                                        onRemoveFavoriteDevice = removeFavoriteDevice,
                                        onUpdateFavoriteAlias = updateFavoriteAlias
                                    )
                                }
                                NavTab.RECEIVE -> {
                                    ReceiveScreen(
                                        localIp = localIp,
                                        settings = settings,
                                        transferState = receiverTransferState,
                                        history = transferHistory,
                                        webRtcEngine = webRtcEngine,
                                        onTransferStateChange = { receiverTransferState = it },
                                        onCancelTransfer = {
                                            webRtcEngine?.cancel()
                                            tcpServer?.cancelCurrentTransfer()
                                            receiverTransferState = receiverTransferState.copy(isActive = false, errorMessage = "Cancelled by user")
                                        },
                                        onRemoveFavoriteDevice = removeFavoriteDevice,
                                        onUpdateFavoriteAlias = updateFavoriteAlias,
                                        onSendToFavorite = { ip, port, alias ->
                                            currentTab = NavTab.SEND
                                            sendFilesToTarget(ip, port, alias, "localsend")
                                        }
                                    )
                                }
                                NavTab.SETTINGS -> {
                                    SettingsScreen(
                                        currentSettings = settings,
                                        localIp = localIp,
                                        onSave = { updated ->
                                            settings = updated
                                            SettingsStore.save(this@MainActivity, updated)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Incoming Transfer Request Approval Dialog
                IncomingRequestDialog(
                    request = pendingRequest?.first,
                    onAccept = {
                        senderTransferState = TransferState() // Clear any stale sender state
                        pendingRequest?.second?.complete(true)
                        pendingRequest = null
                    },
                    onDecline = {
                        pendingRequest?.second?.complete(false)
                        pendingRequest = null
                    }
                )

                // Message Received Dialog (Holds Sender until Copied)
                pendingTextMessage?.let { (sender, text, deferred) ->
                    MessageReceivedDialog(
                        senderAlias = sender,
                        messageText = text,
                        onCopy = {
                            deferred.complete(true)
                            pendingTextMessage = null
                        },
                        onClose = {
                            deferred.complete(false)
                            pendingTextMessage = null
                        }
                    )
                }

                receivedMessage?.let { (sender, text) ->
                    MessageReceivedDialog(
                        senderAlias = sender,
                        messageText = text,
                        onClose = { receivedMessage = null }
                    )
                }

                // Global Favorites Dialog
                if (showGlobalFavoritesDialog) {
                    FavoritesDialog(
                        favorites = settings.favorites,
                        onSendToFavorite = { ip, port, alias ->
                            showGlobalFavoritesDialog = false
                            currentTab = NavTab.SEND
                            sendFilesToTarget(ip, port, alias, "localsend")
                        },
                        onRemoveFavorite = removeFavoriteDevice,
                        onUpdateAlias = updateFavoriteAlias,
                        onDismiss = { showGlobalFavoritesDialog = false }
                    )
                }
            }
        }
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
                .launch(ungranted.toTypedArray())
        }
    }

    private fun handleShareIntent(intent: Intent?, onFilesFound: (List<FileItem>) -> Unit) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type ?: return

        when (action) {
            Intent.ACTION_SEND -> {
                (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uri ->
                    val file = getFileItemFromUri(this, uri)
                    onFilesFound(listOf(file))
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris ->
                    val files = uris.map { uri -> getFileItemFromUri(this, uri) }
                    onFilesFound(files)
                }
            }
        }
    }

    private fun getFileItemFromUri(context: Context, uri: Uri): FileItem {
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

    private fun getDeviceDefaultAlias(): String {
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER
        return if (!model.isNullOrEmpty()) {
            "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
        } else {
            "Android-Device"
        }
    }

    private fun getLocalIpv4Address(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: ""
                        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }
}
