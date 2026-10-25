package com.blackhole.app.model

import android.net.Uri

data class PeerDevice(
    val ip: String,
    val port: Int = 53317,
    val alias: String,
    val deviceType: String = "mobile",
    val fingerprint: String = "",
    val protocol: String = "blackhole", // "blackhole", "localsend", "quickshare"
    val isHttps: Boolean = false,
    val isFavorite: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)

data class FileItem(
    val name: String,
    val size: Long,
    val uri: Uri? = null,
    val path: String? = null,
    val mimeType: String = "*/*",
    val preview: String? = null // in-memory preview for instant text notes
)

data class FavoriteDevice(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fingerprint: String = "",
    val ip: String,
    val port: Int = 53317,
    val alias: String,
    val customAlias: String = ""
)

data class TransferState(
    val isActive: Boolean = false,
    val isReceiving: Boolean = false,
    val title: String = "",
    val fileName: String = "",
    val progress: Int = 0,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val speedMBs: Double = 0.0,
    val etaSeconds: Long = 0L,
    val isComplete: Boolean = false,
    val errorMessage: String? = null
)

data class IncomingRequest(
    val senderAlias: String,
    val senderIp: String,
    val fileCount: Int,
    val totalBytes: Long,
    val fileNames: List<String>
)

data class AppSettings(
    val alias: String = "BlackHole-Android",
    val destinationFolder: String = "Download/BlackHole",
    val quickSave: Boolean = false,
    val quickSaveMode: String = "off", // "off", "favorites", "always"
    val autoCopyText: Boolean = true,
    val port: Int = 53317,
    val pin: String = "775",
    val themeMode: String = "system", // "system", "dark", "light"
    val favorites: List<FavoriteDevice> = emptyList()
)

data class TransferHistoryItem(
    val title: String,
    val size: Long,
    val status: String,
    val isIncoming: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
