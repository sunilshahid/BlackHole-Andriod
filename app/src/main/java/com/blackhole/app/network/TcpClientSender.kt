package com.blackhole.app.network

import android.content.Context
import com.blackhole.app.model.FileItem
import com.blackhole.app.model.TransferState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.ByteBuffer
import java.util.UUID

class TcpClientSender(
    private val context: Context,
    private val onTransferState: (TransferState) -> Unit
) {
    @Volatile var isCancelled = false
    @Volatile var currentSocket: Socket? = null
    @Volatile var currentHttpConnection: HttpURLConnection? = null

    fun cancel() {
        isCancelled = true
        try { currentSocket?.close() } catch (e: Exception) {}
        try { currentHttpConnection?.disconnect() } catch (e: Exception) {}
    }

    suspend fun sendFiles(
        peerIp: String,
        peerPort: Int,
        peerAlias: String,
        myAlias: String,
        files: List<FileItem>,
        protocol: String = "blackhole"
    ): Boolean = withContext(Dispatchers.IO) {
        val resolvedFiles = files.map { f ->
            val sz = resolveFileSize(f)
            if (sz != f.size && sz > 0L) f.copy(size = sz) else f
        }
        if (protocol.equals("localsend", ignoreCase = true)) {
            return@withContext sendLocalSendFiles(peerIp, peerPort, peerAlias, myAlias, resolvedFiles)
        }
        return@withContext sendNativeTcpFiles(peerIp, peerPort, peerAlias, myAlias, resolvedFiles)
    }

    private fun resolveFileSize(file: FileItem): Long {
        if (file.size > 0L) return file.size
        try {
            file.uri?.let { u ->
                context.contentResolver.openFileDescriptor(u, "r")?.use { pfd ->
                    if (pfd.statSize > 0) return pfd.statSize
                }
                context.contentResolver.openAssetFileDescriptor(u, "r")?.use { afd ->
                    if (afd.length > 0) return afd.length
                }
                context.contentResolver.openInputStream(u)?.use { s ->
                    val ch = (s as? java.io.FileInputStream)?.channel
                    if (ch != null && ch.size() > 0) return ch.size()
                    val a = s.available().toLong()
                    if (a > 0) return a
                }
            }
            file.path?.let { p ->
                val f = java.io.File(p)
                if (f.exists() && f.length() > 0) return f.length()
            }
        } catch (_: Exception) {}
        return file.size
    }

    private suspend fun sendNativeTcpFiles(
        peerIp: String,
        peerPort: Int,
        peerAlias: String,
        myAlias: String,
        files: List<FileItem>
    ): Boolean = withContext(Dispatchers.IO) {
        isCancelled = false
        currentSocket = null
        var socket: Socket? = null

        val totalBytes = files.sumOf { it.size }
        val displayTarget = peerAlias.ifEmpty { peerIp }

        // Initial progress: Waiting for receiver to accept
        onTransferState(
            TransferState(
                isActive = true,
                isReceiving = false,
                title = "Sending to $displayTarget",
                fileName = "Waiting for $displayTarget to accept...",
                progress = 0,
                bytesTransferred = 0,
                totalBytes = totalBytes
            )
        )

        try {
            socket = Socket()
            currentSocket = socket
            socket.tcpNoDelay = true
            socket.sendBufferSize = 4 * 1024 * 1024
            socket.connect(InetSocketAddress(peerIp, peerPort), 8000)

            val outputStream = BufferedOutputStream(socket.getOutputStream())
            val inputStream = BufferedInputStream(socket.getInputStream())

            // 1. Prepare Header
            val filesArray = JSONArray()
            files.forEach { f ->
                val fObj = JSONObject().apply {
                    put("name", f.name)
                    put("size", f.size)
                }
                filesArray.put(fObj)
            }

            val headerJson = JSONObject().apply {
                put("type", "TRANSFER_HEADER")
                put("senderAlias", myAlias)
                put("numFiles", files.size)
                put("totalBytes", totalBytes)
                put("files", filesArray)
            }

            val headerBytes = headerJson.toString().toByteArray(Charsets.UTF_8)
            val headerLen = ByteBuffer.allocate(4).putInt(headerBytes.size).array()

            outputStream.write(headerLen)
            outputStream.write(headerBytes)
            outputStream.flush()

            // 2. Wait for ACCEPTED or REJECTED
            val respLenBytes = ByteArray(4)
            if (inputStream.readFully(respLenBytes) != 4) {
                onTransferState(TransferState(isActive = false, errorMessage = "$displayTarget disconnected"))
                return@withContext false
            }
            val respLen = ByteBuffer.wrap(respLenBytes).int
            val respBytes = ByteArray(respLen)
            inputStream.readFully(respBytes)

            val respJson = JSONObject(String(respBytes, Charsets.UTF_8))
            val status = respJson.optString("status", "")

            if (status != "ACCEPTED") {
                onTransferState(
                    TransferState(
                        isActive = false,
                        errorMessage = "$displayTarget declined the transfer."
                    )
                )
                return@withContext false
            }

            // 3. Stream File Buffers
            var totalBytesSent = 0L
            val chunkBuffer = ByteArray(64 * 1024) // 64 KB chunk buffer for smooth wire-speed streaming
            var lastUiUpdate = 0L
            var lastSentBytes = 0L
            var smoothedSpeedMB = 0.0

            for (fileItem in files) {
                if (isCancelled) break

                val rawStream = fileItem.uri?.let { context.contentResolver.openInputStream(it) }
                    ?: java.io.FileInputStream(fileItem.path ?: continue)
                val inStream = java.io.BufferedInputStream(rawStream, 64 * 1024)

                var fileBytesSent = 0L
                while (fileBytesSent < fileItem.size && !isCancelled) {
                    val toRead = minOf(chunkBuffer.size.toLong(), fileItem.size - fileBytesSent).toInt()
                    val r = inStream.read(chunkBuffer, 0, toRead)
                    if (r <= 0) break

                    outputStream.write(chunkBuffer, 0, r)
                    fileBytesSent += r
                    totalBytesSent += r

                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdate >= 80 || fileBytesSent >= fileItem.size || totalBytesSent >= totalBytes) {
                        val deltaMs = now - lastUiUpdate
                        if (deltaMs > 0) {
                            val deltaBytes = totalBytesSent - lastSentBytes
                            val instantSpeed = (deltaBytes / (1024.0 * 1024.0)) / (deltaMs / 1000.0)
                            smoothedSpeedMB = if (smoothedSpeedMB <= 0.01) instantSpeed else (smoothedSpeedMB * 0.75 + instantSpeed * 0.25)
                        }
                        lastUiUpdate = now
                        lastSentBytes = totalBytesSent

                        val pct = if (totalBytes > 0) ((totalBytesSent * 100) / totalBytes).toInt() else 0
                        val remainingBytes = totalBytes - totalBytesSent
                        val etaSec = if (smoothedSpeedMB > 0) (remainingBytes / (smoothedSpeedMB * 1024 * 1024)).toLong() else 0L

                        onTransferState(
                            TransferState(
                                isActive = true,
                                isReceiving = false,
                                title = "Sending to $displayTarget",
                                fileName = fileItem.name,
                                progress = pct,
                                bytesTransferred = totalBytesSent,
                                totalBytes = totalBytes,
                                speedMBs = smoothedSpeedMB,
                                etaSeconds = etaSec
                            )
                        )
                    }
                }

                outputStream.flush()
                inStream.close()
            }

            if (isCancelled) {
                return@withContext false
            }

            onTransferState(
                TransferState(
                    isActive = false,
                    isReceiving = false,
                    title = "Transfer Complete! 🎉",
                    fileName = if (files.size == 1) files[0].name else "${files.size} files",
                    progress = 100,
                    bytesTransferred = totalBytesSent,
                    totalBytes = totalBytes,
                    isComplete = true
                )
            )

            return@withContext true

        } catch (e: Exception) {
            if (!isCancelled) {
                onTransferState(TransferState(isActive = false, errorMessage = e.message ?: "Transfer error"))
            }
            return@withContext false
        } finally {
            currentSocket = null
            try { socket?.close() } catch (e: Exception) {}
        }
    }

    private suspend fun sendLocalSendFiles(
        peerIp: String,
        peerPort: Int,
        peerAlias: String,
        myAlias: String,
        files: List<FileItem>
    ): Boolean = withContext(Dispatchers.IO) {
        isCancelled = false
        val displayTarget = peerAlias.ifEmpty { peerIp }
        val totalBytes = files.sumOf { it.size }
        val isTextMessage = files.size == 1 && (files[0].preview != null || files[0].name.endsWith(".txt", ignoreCase = true))
        val initialStatus = if (isTextMessage) {
            "Waiting for $displayTarget to copy message..."
        } else {
            "Waiting for $displayTarget to accept..."
        }

        onTransferState(
            TransferState(
                isActive = true,
                isReceiving = false,
                title = "Sending to $displayTarget",
                fileName = initialStatus,
                progress = 0,
                bytesTransferred = 0,
                totalBytes = totalBytes
            )
        )

        try {
            // 1. Prepare Upload Request (Supporting both HTTPS and HTTP, v2 and v1)
            val filesObj = JSONObject()
            val fileIdMap = mutableMapOf<String, FileItem>()
            files.forEach { file ->
                val fid = UUID.randomUUID().toString()
                fileIdMap[fid] = file

                val fDto = JSONObject().apply {
                    put("id", fid)
                    put("fileName", file.name)
                    put("size", file.size)
                    val isText = file.preview != null || file.mimeType == "text/plain" || file.name.endsWith(".txt", ignoreCase = true)
                    put("fileType", if (isText) "text/plain" else "other")
                    if (file.preview != null) {
                        put("preview", file.preview)
                    }
                }
                filesObj.put(fid, fDto)
            }

            val reqJson = JSONObject().apply {
                put("info", JSONObject().apply {
                    put("alias", myAlias)
                    put("version", "2.2")
                    put("deviceModel", android.os.Build.MODEL ?: "Android")
                    put("deviceType", "mobile")
                    put("fingerprint", TlsHelper.getDeviceFingerprint(context))
                    put("port", 53317)
                    put("protocol", "http")
                    put("download", true)
                })
                put("files", filesObj)
            }

            var usedScheme = "https"
            var activeConn: HttpURLConnection? = null
            var respBody = ""

            for (scheme in listOf("https", "http")) {
                for (endpoint in listOf("/api/localsend/v2/prepare-upload", "/api/localsend/v1/send-request")) {
                    try {
                        val c = openConnection("$scheme://$peerIp:$peerPort$endpoint").apply {
                            requestMethod = "POST"
                            connectTimeout = 4000
                            readTimeout = 60000
                            doOutput = true
                            setRequestProperty("Content-Type", "application/json")
                        }
                        currentHttpConnection = c
                        OutputStreamWriter(c.outputStream, Charsets.UTF_8).use { it.write(reqJson.toString()) }
                        val code = c.responseCode
                        if (code == 403) {
                            onTransferState(TransferState(isActive = false, errorMessage = "$displayTarget declined the transfer."))
                            return@withContext false
                        }
                        if (code == 204) {
                            activeConn = c
                            usedScheme = scheme
                            respBody = ""
                            break
                        } else if (code in 200..299) {
                            activeConn = c
                            usedScheme = scheme
                            respBody = c.inputStream.bufferedReader(Charsets.UTF_8).readText()
                            break
                        }
                    } catch (e: Exception) {
                        // Try next scheme / fallback
                    }
                }
                if (activeConn != null) break
            }

            if (activeConn == null) {
                onTransferState(TransferState(isActive = false, errorMessage = "Could not connect to $displayTarget."))
                return@withContext false
            }

            val respJson = try { JSONObject(respBody) } catch (e: Exception) { JSONObject() }
            val sessionId = respJson.optString("sessionId", "")
            val remoteFiles = respJson.optJSONObject("files") ?: JSONObject()

            // If empty files map, 204 No Content, or instant text message was accepted
            if (activeConn.responseCode == 204 || remoteFiles.length() == 0 || files.all { it.preview != null }) {
                onTransferState(
                    TransferState(
                        isActive = false,
                        isReceiving = false,
                        title = "Message Sent! ✉️",
                        fileName = if (files.size == 1) files[0].name else "Message delivered",
                        progress = 100,
                        bytesTransferred = totalBytes,
                        totalBytes = totalBytes,
                        isComplete = true
                    )
                )
                return@withContext true
            }

            // 2. Upload Each Authorized File
            var totalBytesSent = 0L
            val chunkBuffer = ByteArray(64 * 1024) // 64 KB chunk buffer for smooth wire-speed streaming
            var lastUiUpdate = 0L
            var lastSentBytes = 0L
            var smoothedSpeedMB = 0.0

            for (fid in fileIdMap.keys) {
                if (isCancelled) break
                val token = remoteFiles.optString(fid, "")
                if (token.isEmpty()) continue
                val fileItem = fileIdMap[fid] ?: continue

                val uploadUrl = "$usedScheme://$peerIp:$peerPort/api/localsend/v2/upload?sessionId=$sessionId&fileId=$fid&token=$token"
                val uploadConn = openConnection(uploadUrl).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 60000
                    doOutput = true
                    setFixedLengthStreamingMode(fileItem.size)
                    setRequestProperty("Content-Type", "application/octet-stream")
                }
                currentHttpConnection = uploadConn

                val rawStream = fileItem.uri?.let { context.contentResolver.openInputStream(it) }
                    ?: java.io.FileInputStream(fileItem.path ?: continue)
                val inStream = java.io.BufferedInputStream(rawStream, 64 * 1024)

                val outStream = java.io.BufferedOutputStream(uploadConn.outputStream, 64 * 1024)
                var fileBytesSent = 0L
                while (fileBytesSent < fileItem.size && !isCancelled) {
                    val toRead = minOf(chunkBuffer.size.toLong(), fileItem.size - fileBytesSent).toInt()
                    val r = inStream.read(chunkBuffer, 0, toRead)
                    if (r <= 0) break

                    outStream.write(chunkBuffer, 0, r)
                    fileBytesSent += r
                    totalBytesSent += r

                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdate >= 80 || fileBytesSent >= fileItem.size || totalBytesSent >= totalBytes) {
                        val deltaMs = now - lastUiUpdate
                        if (deltaMs > 0) {
                            val deltaBytes = totalBytesSent - lastSentBytes
                            val instantSpeed = (deltaBytes / (1024.0 * 1024.0)) / (deltaMs / 1000.0)
                            smoothedSpeedMB = if (smoothedSpeedMB <= 0.01) instantSpeed else (smoothedSpeedMB * 0.75 + instantSpeed * 0.25)
                        }
                        lastUiUpdate = now
                        lastSentBytes = totalBytesSent

                        val pct = if (totalBytes > 0) ((totalBytesSent * 100) / totalBytes).toInt() else 0
                        val remainingBytes = totalBytes - totalBytesSent
                        val etaSec = if (smoothedSpeedMB > 0) (remainingBytes / (smoothedSpeedMB * 1024 * 1024)).toLong() else 0L

                        onTransferState(
                            TransferState(
                                isActive = true,
                                isReceiving = false,
                                title = "Sending to $displayTarget",
                                fileName = fileItem.name,
                                progress = pct,
                                bytesTransferred = totalBytesSent,
                                totalBytes = totalBytes,
                                speedMBs = smoothedSpeedMB,
                                etaSeconds = etaSec
                            )
                        )
                    }
                }

                outStream.flush()
                inStream.close()
                uploadConn.responseCode // Wait for upload completion ack
                uploadConn.disconnect()
            }

            if (isCancelled) {
                return@withContext false
            }

            onTransferState(
                TransferState(
                    isActive = false,
                    isReceiving = false,
                    title = "Transfer Complete! 🎉",
                    fileName = if (files.size == 1) files[0].name else "${files.size} files",
                    progress = 100,
                    bytesTransferred = totalBytesSent,
                    totalBytes = totalBytes,
                    isComplete = true
                )
            )
            return@withContext true

        } catch (e: Exception) {
            if (!isCancelled) {
                onTransferState(TransferState(isActive = false, errorMessage = e.message ?: "Transfer error"))
            }
            return@withContext false
        } finally {
            currentHttpConnection = null
        }
    }

    private fun openConnection(urlStr: String): HttpURLConnection {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        if (conn is javax.net.ssl.HttpsURLConnection) {
            conn.sslSocketFactory = TlsHelper.getTrustAllSSLSocketFactory()
            conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        }
        return conn
    }

    private fun InputStream.readFully(buffer: ByteArray): Int {
        var totalRead = 0
        while (totalRead < buffer.size) {
            val r = read(buffer, totalRead, buffer.size - totalRead)
            if (r == -1) break
            totalRead += r
        }
        return totalRead
    }
}
