package com.blackhole.app.network

import android.content.Context
import android.os.Environment
import com.blackhole.app.model.FileItem
import com.blackhole.app.model.IncomingRequest
import com.blackhole.app.model.PeerDevice
import com.blackhole.app.model.TransferState
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LocalHttpServer(
    private val context: Context,
    private val getSharedFiles: () -> List<FileItem>,
    private val onIncomingRequest: suspend (IncomingRequest) -> Boolean,
    private val onFileUploaded: (String, Long) -> Unit
) {
    var deviceAlias = "BlackHole-Android"
    var quickSave = false
    var quickSaveMode = "off" // "off", "favorites", "always"
    var autoCopyText = true
    var isFavoriteCheck: ((fingerprint: String, ip: String) -> Boolean)? = null
    var onTextMessageReceived: ((senderAlias: String, messageText: String) -> Unit)? = null
    var onTextMessageRequest: (suspend (senderAlias: String, messageText: String) -> Boolean)? = null
    var onPeerDiscovered: ((PeerDevice) -> Unit)? = null
    var onTransferStateUpdate: ((TransferState) -> Unit)? = null

    data class LocalSendUploadInfo(
        val sessionId: String,
        val fileName: String,
        val size: Long,
        val token: String
    )
    private val pendingSessions = ConcurrentHashMap<String, Map<String, LocalSendUploadInfo>>()
    private val sessionSenderAlias = ConcurrentHashMap<String, String>()

    suspend fun handleHttpRequest(
        socket: Socket,
        inputStream: InputStream,
        outputStream: OutputStream,
        clientIp: String
    ) = withContext(Dispatchers.IO) {
        val headerBuf = java.io.ByteArrayOutputStream()
        var b: Int
        var state = 0
        while (true) {
            b = inputStream.read()
            if (b == -1) break
            headerBuf.write(b)
            if (state == 0 && b == '\r'.code) state = 1
            else if (state == 1 && b == '\n'.code) state = 2
            else if (state == 2 && b == '\r'.code) state = 3
            else if (state == 3 && b == '\n'.code) break
            else state = if (b == '\r'.code) 1 else 0
        }
        val headerText = headerBuf.toString("UTF-8")
        val lines = headerText.split("\r\n").filter { it.isNotEmpty() }
        if (lines.isEmpty()) return@withContext

        val firstLine = lines[0]
        val parts = firstLine.split(" ")
        if (parts.size < 2) return@withContext

        val method = parts[0]
        val fullPath = parts[1]
        val path = if (fullPath.contains("?")) fullPath.substringBefore("?") else fullPath
        val query = if (fullPath.contains("?")) fullPath.substringAfter("?") else ""

        val headers = mutableMapOf<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            val colon = line.indexOf(':')
            if (colon > 0) {
                val k = line.substring(0, colon).trim().lowercase()
                val v = line.substring(colon + 1).trim()
                headers[k] = v
            }
        }

        // CORS Options Pre-flight
        if (method == "OPTIONS") {
            val resp = "HTTP/1.1 204 No Content\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\nAccess-Control-Allow-Headers: *\r\nConnection: close\r\n\r\n"
            outputStream.write(resp.toByteArray(Charsets.UTF_8))
            outputStream.flush()
            socket.close()
            return@withContext
        }

        val myDeviceFingerprint = TlsHelper.getDeviceFingerprint(context)

        // GET /api/localsend/v2/info or /api/localsend/v1/info
        if (method == "GET" && (path == "/api/localsend/v2/info" || path == "/api/localsend/v1/info")) {
            val json = JSONObject().apply {
                put("alias", deviceAlias)
                put("version", "2.2")
                put("deviceModel", android.os.Build.MODEL ?: "Android")
                put("deviceType", "mobile")
                put("fingerprint", myDeviceFingerprint)
                put("port", 53317)
                put("protocol", "http")
                put("download", true)
            }
            sendJsonResponse(outputStream, json.toString())
            socket.close()
            return@withContext
        }

        // POST /api/localsend/v2/register or /api/localsend/v1/register
        if (method == "POST" && (path == "/api/localsend/v2/register" || path == "/api/localsend/v1/register")) {
            val clen = headers["content-length"]?.toIntOrNull() ?: 0
            val body = readBodyString(inputStream, clen)
            val reqJson = try { JSONObject(body) } catch (e: Exception) { JSONObject() }

            val alias = reqJson.optString("alias", "Peer Device")
            val port = reqJson.optInt("port", 53317)
            val devType = reqJson.optString("deviceType", "desktop")
            val fingerprint = reqJson.optString("fingerprint", "")
            val isHttps = reqJson.optString("protocol", "https").equals("https", ignoreCase = true)

            val appName = reqJson.optString("app", "")
            val peerProto = if (appName.equals("BlackHole", ignoreCase = true) || fingerprint.contains("blackhole", ignoreCase = true)) "blackhole" else "localsend"

            val peer = PeerDevice(
                ip = clientIp,
                port = port,
                alias = alias,
                deviceType = if (devType.equals("mobile", ignoreCase = true) || devType.equals("phone", ignoreCase = true)) "mobile" else "desktop",
                protocol = peerProto,
                fingerprint = fingerprint,
                isHttps = isHttps,
                lastSeen = System.currentTimeMillis()
            )
            onPeerDiscovered?.invoke(peer)

            // Return our own registration info
            val respJson = JSONObject().apply {
                put("alias", deviceAlias)
                put("version", "2.2")
                put("deviceModel", android.os.Build.MODEL ?: "Android")
                put("deviceType", "mobile")
                put("fingerprint", myDeviceFingerprint)
                put("port", 53317)
                put("protocol", "http")
                put("download", true)
            }
            sendJsonResponse(outputStream, respJson.toString())
            socket.close()
            return@withContext
        }

        // POST /api/localsend/v2/prepare-upload or /api/localsend/v1/send-request
        if (method == "POST" && (path == "/api/localsend/v2/prepare-upload" || path == "/api/localsend/v1/send-request")) {
            val clen = headers["content-length"]?.toIntOrNull() ?: 0
            val body = readBodyString(inputStream, clen)
            val reqJson = try { JSONObject(body) } catch (e: Exception) { JSONObject() }

            val infoObj = reqJson.optJSONObject("info") ?: JSONObject()
            val senderAlias = infoObj.optString("alias", "User")
            val senderFingerprint = infoObj.optString("fingerprint", "")
            val filesObj = reqJson.optJSONObject("files") ?: JSONObject()

            // 1. Check for Instant Text Message
            var instantText: String? = null
            var firstKey = ""
            val keys = filesObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (firstKey.isEmpty()) firstKey = key
                val fObj = filesObj.getJSONObject(key)
                val fileType = fObj.optString("fileType", "")
                val preview = fObj.optString("preview", "")
                val isText = fileType == "text" || fileType == "text/plain" || fileType.startsWith("text/") || preview.isNotEmpty()
                if (isText && preview.isNotEmpty()) {
                    instantText = preview
                    break
                }
            }

            if (instantText != null) {
                // Auto-copy text if enabled
                if (autoCopyText) {
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("BlackHole Message", instantText)
                        clipboard.setPrimaryClip(clip)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Show Message Received modal and hold connection until user acts
                val accepted = onTextMessageRequest?.invoke(senderAlias, instantText) ?: run {
                    onTextMessageReceived?.invoke(senderAlias, instantText)
                    true
                }
                if (accepted) {
                    val resp = "HTTP/1.1 204 No Content\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
                    outputStream.write(resp.toByteArray(Charsets.UTF_8))
                } else {
                    val resp = "HTTP/1.1 403 Forbidden\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
                    outputStream.write(resp.toByteArray(Charsets.UTF_8))
                }
                outputStream.flush()
                socket.close()
                return@withContext
            }

            // 2. Regular File Transfer Request
            val fnames = mutableListOf<String>()
            var totalSize = 0L
            val tokenMap = mutableMapOf<String, LocalSendUploadInfo>()
            val respFiles = JSONObject()
            val sessionId = UUID.randomUUID().toString()

            val keys2 = filesObj.keys()
            while (keys2.hasNext()) {
                val fileId = keys2.next()
                val fObj = filesObj.getJSONObject(fileId)
                val fname = fObj.optString("fileName", "file")
                val fsz = fObj.optLong("size", 0L)
                val token = UUID.randomUUID().toString().replace("-", "").take(16)

                fnames.add(fname)
                totalSize += fsz
                tokenMap[fileId] = LocalSendUploadInfo(sessionId, fname, fsz, token)
                respFiles.put(fileId, token)
            }

            // Auto-accept check: Always, or Favorites if sender matches favorite fingerprint/IP
            val isFav = isFavoriteCheck?.invoke(senderFingerprint, clientIp) ?: false
            val shouldAutoAccept = (quickSaveMode == "always") || (quickSaveMode == "favorites" && isFav) || quickSave

            var accepted = true
            if (!shouldAutoAccept) {
                accepted = onIncomingRequest(
                    IncomingRequest(
                        senderAlias = senderAlias,
                        senderIp = clientIp,
                        fileCount = fnames.size,
                        totalBytes = totalSize,
                        fileNames = fnames
                    )
                )
            }

            if (accepted) {
                sessionSenderAlias[sessionId] = senderAlias
                pendingSessions[sessionId] = tokenMap
                onTransferStateUpdate?.invoke(
                    com.blackhole.app.model.TransferState(
                        isActive = true,
                        isReceiving = true,
                        title = "Receiving from $senderAlias",
                        fileName = fnames.firstOrNull() ?: "File",
                        progress = 0,
                        bytesTransferred = 0L,
                        totalBytes = totalSize
                    )
                )
                val respJson = JSONObject().apply {
                    put("sessionId", sessionId)
                    put("files", respFiles)
                }
                sendJsonResponse(outputStream, respJson.toString())
            } else {
                sendErrorResponse(outputStream, 403, "Declined")
            }
            socket.close()
            return@withContext
        }

        // POST /api/localsend/v2/upload or /api/localsend/v1/send
        if (method == "POST" && (path == "/api/localsend/v2/upload" || path == "/api/localsend/v1/send")) {
            val sessionId = getQueryParam(query, "sessionId")
            val fileId = getQueryParam(query, "fileId")
            val token = getQueryParam(query, "token")

            val sessionMap = pendingSessions[sessionId]
            val uploadInfo = sessionMap?.get(fileId)

            val expectedSize = uploadInfo?.size ?: headers["content-length"]?.toLongOrNull() ?: 0L
            val clen = headers["content-length"]?.toLongOrNull() ?: expectedSize
            val isChunked = headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true
            val fileName = uploadInfo?.fileName ?: "LocalSend_${System.currentTimeMillis()}.bin"
            val sender = sessionSenderAlias[sessionId] ?: "Nearby Device"

            val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BlackHole")
            if (!downloadDir.exists()) downloadDir.mkdirs()
            val outFile = File(downloadDir, fileName)
            val fos = java.io.BufferedOutputStream(FileOutputStream(outFile), 64 * 1024)

            var lastUpdate = System.currentTimeMillis()
            var lastUpdateBytes = 0L
            var smoothedSpeedMB = 0.0
            var totalRead = 0L

            if (isChunked) {
                fun readStreamLine(): String {
                    val sb = StringBuilder()
                    var ch: Int
                    while (inputStream.read().also { ch = it } != -1) {
                        if (ch == '\n'.code) break
                        if (ch != '\r'.code) sb.append(ch.toChar())
                    }
                    return sb.toString()
                }

                val chunkBuf = ByteArray(64 * 1024)
                while (true) {
                    val line = readStreamLine().substringBefore(";").trim()
                    if (line.isEmpty()) continue
                    val chunkSize = line.toLongOrNull(16) ?: 0L
                    if (chunkSize <= 0L) {
                        readStreamLine()
                        break
                    }

                    var rem = chunkSize
                    while (rem > 0L) {
                        val toRead = minOf(chunkBuf.size.toLong(), rem).toInt()
                        val r = inputStream.read(chunkBuf, 0, toRead)
                        if (r <= 0) break
                        fos.write(chunkBuf, 0, r)
                        rem -= r
                        totalRead += r

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 80 || rem == 0L) {
                            val deltaMs = now - lastUpdate
                            if (deltaMs > 0) {
                                val deltaBytes = totalRead - lastUpdateBytes
                                val instantSpeed = (deltaBytes / (1024.0 * 1024.0)) / (deltaMs / 1000.0)
                                smoothedSpeedMB = if (smoothedSpeedMB <= 0.01) instantSpeed else (smoothedSpeedMB * 0.75 + instantSpeed * 0.25)
                            }
                            lastUpdate = now
                            lastUpdateBytes = totalRead

                            val pct = if (expectedSize > 0) minOf(100, ((totalRead * 100) / expectedSize).toInt()) else 50
                            val eta = if (smoothedSpeedMB > 0 && expectedSize > totalRead) ((expectedSize - totalRead) / (smoothedSpeedMB * 1024 * 1024)).toLong() else 0L
                            onTransferStateUpdate?.invoke(
                                com.blackhole.app.model.TransferState(
                                    isActive = true,
                                    isReceiving = true,
                                    title = "Receiving from $sender",
                                    fileName = fileName,
                                    progress = pct,
                                    bytesTransferred = totalRead,
                                    totalBytes = expectedSize,
                                    speedMBs = smoothedSpeedMB,
                                    etaSeconds = eta
                                )
                            )
                        }
                    }
                    // Read trailing CRLF after chunk data
                    inputStream.read()
                    inputStream.read()
                }
            } else {
                val buf = ByteArray(64 * 1024)
                while (totalRead < clen || clen == 0L) {
                    val toRead = if (clen > 0) minOf(buf.size.toLong(), clen - totalRead).toInt() else buf.size
                    val r = inputStream.read(buf, 0, toRead)
                    if (r <= 0) break
                    fos.write(buf, 0, r)
                    totalRead += r

                    val now = System.currentTimeMillis()
                    if (now - lastUpdate >= 80 || (clen > 0 && totalRead >= clen)) {
                        val deltaMs = now - lastUpdate
                        if (deltaMs > 0) {
                            val deltaBytes = totalRead - lastUpdateBytes
                            val instantSpeed = (deltaBytes / (1024.0 * 1024.0)) / (deltaMs / 1000.0)
                            smoothedSpeedMB = if (smoothedSpeedMB <= 0.01) instantSpeed else (smoothedSpeedMB * 0.75 + instantSpeed * 0.25)
                        }
                        lastUpdate = now
                        lastUpdateBytes = totalRead

                        val pct = if (clen > 0) ((totalRead * 100) / clen).toInt() else 50
                        val eta = if (smoothedSpeedMB > 0 && clen > totalRead) ((clen - totalRead) / (smoothedSpeedMB * 1024 * 1024)).toLong() else 0L
                        onTransferStateUpdate?.invoke(
                            com.blackhole.app.model.TransferState(
                                isActive = true,
                                isReceiving = true,
                                title = "Receiving from $sender",
                                fileName = fileName,
                                progress = pct,
                                bytesTransferred = totalRead,
                                totalBytes = clen,
                                speedMBs = smoothedSpeedMB,
                                etaSeconds = eta
                            )
                        )
                    }
                    if (clen > 0 && totalRead >= clen) break
                }
            }

            fos.flush()
            fos.close()
            onFileUploaded(fileName, totalRead)

            onTransferStateUpdate?.invoke(
                com.blackhole.app.model.TransferState(
                    isActive = false,
                    isReceiving = true,
                    title = "Received from $sender",
                    fileName = fileName,
                    progress = 100,
                    bytesTransferred = totalRead,
                    totalBytes = totalRead,
                    isComplete = true
                )
            )

            sendJsonResponse(outputStream, "{}")
            socket.close()
            return@withContext
        }

        // POST /api/localsend/v2/cancel or /api/localsend/v1/cancel
        if (method == "POST" && (path == "/api/localsend/v2/cancel" || path == "/api/localsend/v1/cancel")) {
            val sessionId = getQueryParam(query, "sessionId")
            if (sessionId.isNotEmpty()) {
                pendingSessions.remove(sessionId)
            }
            sendJsonResponse(outputStream, "{}")
            socket.close()
            return@withContext
        }

        // --- Native BlackHole Endpoints ---

        // 1. Device Info
        if (method == "GET" && path == "/api/info") {
            val json = JSONObject().apply {
                put("alias", deviceAlias)
                put("version", "2.0.0")
                put("deviceType", "mobile")
                put("deviceModel", android.os.Build.MODEL ?: "Android")
                put("status", "online")
            }
            sendJsonResponse(outputStream, json.toString())
            socket.close()
            return@withContext
        }

        // 2. Shared Files List
        if (method == "GET" && path == "/api/files") {
            val files = getSharedFiles()
            val arr = JSONArray()
            files.forEachIndexed { idx, f ->
                val item = JSONObject().apply {
                    put("id", idx)
                    put("name", f.name)
                    put("size", f.size)
                    put("url", "/api/download?id=$idx")
                }
                arr.put(item)
            }
            sendJsonResponse(outputStream, arr.toString())
            socket.close()
            return@withContext
        }

        // 3. Download File
        if (method == "GET" && path == "/api/download") {
            val id = query.substringAfter("id=").substringBefore("&").toIntOrNull() ?: -1
            val files = getSharedFiles()
            if (id in files.indices) {
                val f = files[id]
                val inStream = f.uri?.let { context.contentResolver.openInputStream(it) }
                    ?: f.path?.let { FileInputStream(it) }

                if (inStream != null) {
                    val header = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Disposition: attachment; filename=\"${f.name}\"\r\nContent-Length: ${f.size}\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
                    outputStream.write(header.toByteArray(Charsets.UTF_8))
                    val buf = ByteArray(64 * 1024)
                    var r: Int
                    while (inStream.read(buf).also { r = it } > 0) {
                        outputStream.write(buf, 0, r)
                    }
                    outputStream.flush()
                    inStream.close()
                    socket.close()
                    return@withContext
                }
            }
        }

        // 4. Prepare Upload (Confirmation Check)
        if (method == "POST" && path == "/api/prepare-upload") {
            val clen = headers["content-length"]?.toIntOrNull() ?: 0
            val body = readBodyString(inputStream, clen)

            val reqJson = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
            val senderAlias = reqJson.optString("alias", "Browser User")
            val count = reqJson.optInt("count", 1)
            val size = reqJson.optLong("size", 0L)

            val fnames = mutableListOf<String>()
            val filesArr = reqJson.optJSONArray("files")
            if (filesArr != null) {
                for (i in 0 until filesArr.length()) {
                    fnames.add(filesArr.getJSONObject(i).optString("name", "file"))
                }
            }

            var accepted = true
            if (!quickSave) {
                accepted = onIncomingRequest(
                    IncomingRequest(
                        senderAlias = senderAlias,
                        senderIp = clientIp,
                        fileCount = count,
                        totalBytes = size,
                        fileNames = fnames
                    )
                )
            }

            val status = if (accepted) "ACCEPTED" else "REJECTED"
            sendJsonResponse(outputStream, JSONObject().apply { put("status", status) }.toString())
            socket.close()
            return@withContext
        }

        // 5. Upload File
        if (method == "POST" && path == "/api/upload") {
            val clen = headers["content-length"]?.toLongOrNull() ?: 0L
            val rawName = headers["x-filename"] ?: query.substringAfter("filename=").substringBefore("&")
            val fileName = try { URLDecoder.decode(rawName, "UTF-8") } catch (e: Exception) { "Upload_${System.currentTimeMillis()}.bin" }

            val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BlackHole")
            if (!downloadDir.exists()) downloadDir.mkdirs()
            val outFile = File(downloadDir, fileName)
            val fos = FileOutputStream(outFile)

            val buf = ByteArray(64 * 1024)
            var totalRead = 0L
            while (totalRead < clen || clen == 0L) {
                val toRead = if (clen > 0) minOf(buf.size.toLong(), clen - totalRead).toInt() else buf.size
                val r = inputStream.read(buf, 0, toRead)
                if (r <= 0) break
                fos.write(buf, 0, r)
                totalRead += r
            }

            fos.flush()
            fos.close()
            onFileUploaded(fileName, totalRead)

            if ((fileName.contains("Shared_Note", ignoreCase = true) || fileName.endsWith(".txt", ignoreCase = true)) && totalRead < 65536) {
                try {
                    val text = outFile.readText(Charsets.UTF_8)
                    onTextMessageReceived?.invoke("Web Client", text)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            sendJsonResponse(outputStream, "{\"status\":\"ok\"}")
            socket.close()
            return@withContext
        }

        // Default: Web Portal Landing HTML
        val portalHtml = """
            <!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>
            <title>BlackHole Mobile</title></head>
            <body style='background:#030712;color:#fff;font-family:sans-serif;text-align:center;padding:40px;'>
            <h1 style='color:#8b5cf6;'>BlackHole Hotspot</h1>
            <p>Connected to <b>$deviceAlias</b></p>
            <p style='color:#06b6d4;'>Open the BlackHole App to manage shared files.</p>
            </body></html>
        """.trimIndent()

        val resp = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${portalHtml.toByteArray().size}\r\nConnection: close\r\n\r\n$portalHtml"
        outputStream.write(resp.toByteArray(Charsets.UTF_8))
        outputStream.flush()
        socket.close()
    }

    private fun readBodyString(inputStream: InputStream, clen: Int): String {
        if (clen <= 0) return ""
        val bytes = ByteArray(clen)
        var totalRead = 0
        while (totalRead < clen) {
            val r = inputStream.read(bytes, totalRead, clen - totalRead)
            if (r <= 0) break
            totalRead += r
        }
        return String(bytes, 0, totalRead, Charsets.UTF_8)
    }

    private fun getQueryParam(query: String, param: String): String {
        return query.split("&")
            .firstOrNull { it.startsWith("$param=") }
            ?.substringAfter("$param=")
            ?.let { try { URLDecoder.decode(it, "UTF-8") } catch (e: Exception) { it } }
            ?: ""
    }

    private fun sendJsonResponse(outputStream: OutputStream, jsonStr: String) {
        val bytes = jsonStr.toByteArray(Charsets.UTF_8)
        val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n$jsonStr"
        outputStream.write(resp.toByteArray(Charsets.UTF_8))
        outputStream.flush()
    }

    private fun sendErrorResponse(outputStream: OutputStream, code: Int, msg: String) {
        val resp = "HTTP/1.1 $code $msg\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        outputStream.write(resp.toByteArray(Charsets.UTF_8))
        outputStream.flush()
    }
}
