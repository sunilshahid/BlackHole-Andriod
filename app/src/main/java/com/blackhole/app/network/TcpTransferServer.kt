package com.blackhole.app.network

import android.content.Context
import android.os.Environment
import android.util.Log
import com.blackhole.app.model.IncomingRequest
import com.blackhole.app.model.TransferState
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer

class TcpTransferServer(
    private val context: Context,
    private val onTransferState: (TransferState) -> Unit,
    private val onIncomingRequest: suspend (IncomingRequest) -> Boolean,
    private val onFileSaved: (String, Long) -> Unit
) {
    private var coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    var port: Int = 53317
    var quickSave: Boolean = false
    var quickSaveMode: String = "off"
    var isFavoriteCheck: ((fingerprint: String, ip: String) -> Boolean)? = null
    @Volatile var isTransferCancelled: Boolean = false
    @Volatile private var currentClientSocket: Socket? = null

    var localHttpHandler: LocalHttpServer? = null
    var onTextMessageReceived: ((senderAlias: String, messageText: String) -> Unit)? = null

    fun start() {
        if (isRunning && serverSocket != null && !serverSocket!!.isClosed && serverSocket!!.isBound) {
            return
        }
        isRunning = true

        coroutineScope.launch {
            while (isRunning) {
                try {
                    Log.i("TcpTransferServer", "Binding ServerSocket on 0.0.0.0:$port (reuseAddress=true)...")
                    val ss = ServerSocket()
                    ss.reuseAddress = true
                    ss.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), port), 50)
                    serverSocket = ss
                    Log.i("TcpTransferServer", "ServerSocket SUCCESS: Listening on 0.0.0.0:$port")

                    while (isRunning && !ss.isClosed) {
                        try {
                            val client = ss.accept() ?: break
                            launch {
                                try {
                                    handleClient(client)
                                } catch (e: Exception) {
                                    Log.e("TcpTransferServer", "Error in handleClient: ${e.message}", e)
                                }
                            }
                        } catch (se: SocketException) {
                            if (!isRunning) break
                            Log.w("TcpTransferServer", "SocketException in accept: ${se.message}")
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TcpTransferServer", "Error binding port $port on 0.0.0.0: ${e.message}, will retry in 2s...", e)
                    try { serverSocket?.close() } catch (_: Exception) {}
                    serverSocket = null
                    delay(2000)
                }
            }
        }
    }

    fun restart() {
        stop()
        coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        start()
    }

    fun stop() {
        isRunning = false
        cancelCurrentTransfer()
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
        coroutineScope.cancel()
    }

    fun cancelCurrentTransfer() {
        isTransferCancelled = true
        try { currentClientSocket?.close() } catch (e: Exception) {}
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        val clientIp = socket.inetAddress?.hostAddress ?: "Unknown"
        currentClientSocket = socket

        try {
            val inputStream = BufferedInputStream(socket.getInputStream())
            val outputStream = BufferedOutputStream(socket.getOutputStream())

            // 1. Read first 4 bytes (can be HTTP method or 4-byte network length)
            val lenBytes = ByteArray(4)
            if (inputStream.readFully(lenBytes) != 4) {
                socket.close()
                return@withContext
            }

            // Check if it's an HTTP request from browser
            val prefix = String(lenBytes, Charsets.US_ASCII)
            if (prefix.startsWith("GET") || prefix.startsWith("POST") || prefix.startsWith("HEAD") || prefix.startsWith("OPTI")) {
                val combinedStream = java.io.SequenceInputStream(java.io.ByteArrayInputStream(lenBytes), inputStream)
                localHttpHandler?.handleHttpRequest(socket, combinedStream, outputStream, clientIp)
                return@withContext
            }

            // Check if it's a TLS / HTTPS ClientHello handshake (starts with byte 0x16)
            if (lenBytes[0] == 0x16.toByte()) {
                val sslContext = TlsHelper.getOrCreateSslContext()
                if (sslContext != null) {
                    try {
                        val combinedStream = java.io.SequenceInputStream(java.io.ByteArrayInputStream(lenBytes), inputStream)
                        val delegated = DelegatedSocket(socket, combinedStream)
                        val sslSocket = sslContext.socketFactory.createSocket(delegated, clientIp, socket.port, true) as javax.net.ssl.SSLSocket
                        sslSocket.useClientMode = false // Run in Server Mode
                        sslSocket.startHandshake()
                        val sslIn = BufferedInputStream(sslSocket.inputStream)
                        val sslOut = BufferedOutputStream(sslSocket.outputStream)
                        localHttpHandler?.handleHttpRequest(sslSocket, sslIn, sslOut, clientIp)
                        return@withContext
                    } catch (e: Exception) {
                        e.printStackTrace()
                        try { socket.close() } catch (_: Exception) {}
                        return@withContext
                    }
                }
            }

            val headerLen = ByteBuffer.wrap(lenBytes).int
            if (headerLen <= 0 || headerLen > 10 * 1024 * 1024) {
                socket.close()
                return@withContext
            }

            // 2. Read Header JSON
            val headerData = ByteArray(headerLen)
            if (inputStream.readFully(headerData) != headerLen) {
                socket.close()
                return@withContext
            }
            val headerJson = JSONObject(String(headerData, Charsets.UTF_8))
            val senderAlias = headerJson.optString("senderAlias", "Nearby Device")
            val totalBytes = headerJson.optLong("totalBytes", 0L)
            val filesArray = headerJson.optJSONArray("files")

            val fileNames = mutableListOf<String>()
            val filesList = mutableListOf<Pair<String, Long>>()
            if (filesArray != null) {
                for (i in 0 until filesArray.length()) {
                    val fObj = filesArray.getJSONObject(i)
                    val fname = fObj.optString("name", "file_$i")
                    val fsize = fObj.optLong("size", 0L)
                    fileNames.add(fname)
                    filesList.add(Pair(fname, fsize))
                }
            }

            // 3. Auto-Accept or User Confirmation Prompt
            val isFav = isFavoriteCheck?.invoke("", clientIp) ?: false
            val shouldAutoAccept = (quickSaveMode == "always") || (quickSaveMode == "favorites" && isFav) || quickSave

            if (!shouldAutoAccept) {
                val req = IncomingRequest(
                    senderAlias = senderAlias,
                    senderIp = clientIp,
                    fileCount = filesList.size,
                    totalBytes = totalBytes,
                    fileNames = fileNames
                )

                val accepted = onIncomingRequest(req)
                if (!accepted) {
                    val resp = JSONObject().apply { put("status", "REJECTED") }.toString().toByteArray(Charsets.UTF_8)
                    val respLen = ByteBuffer.allocate(4).putInt(resp.size).array()
                    outputStream.write(respLen)
                    outputStream.write(resp)
                    outputStream.flush()
                    socket.close()
                    return@withContext
                }
            }

            // Send ACCEPTED response
            val resp = JSONObject().apply { put("status", "ACCEPTED") }.toString().toByteArray(Charsets.UTF_8)
            val respLen = ByteBuffer.allocate(4).putInt(resp.size).array()
            outputStream.write(respLen)
            outputStream.write(resp)
            outputStream.flush()

            // 4. Stream and write files to Downloads/BlackHole
            isTransferCancelled = false
            val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BlackHole")
            if (!downloadDir.exists()) downloadDir.mkdirs()

            var totalBytesReceived = 0L
            val chunkBuffer = ByteArray(64 * 1024) // 64 KB chunk for ultra-smooth streaming
            var lastUiUpdate = 0L
            var lastUpdateBytes = 0L
            var smoothedSpeedMB = 0.0

            for ((fileName, fileSize) in filesList) {
                if (isTransferCancelled) break

                val outFile = File(downloadDir, fileName)
                val fos = java.io.BufferedOutputStream(FileOutputStream(outFile), 64 * 1024)
                var fileBytesReceived = 0L

                while (fileBytesReceived < fileSize && !isTransferCancelled && isRunning) {
                    val toRead = minOf(chunkBuffer.size.toLong(), fileSize - fileBytesReceived).toInt()
                    val r = inputStream.read(chunkBuffer, 0, toRead)
                    if (r <= 0) break

                    fos.write(chunkBuffer, 0, r)
                    fileBytesReceived += r
                    totalBytesReceived += r

                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdate >= 80 || fileBytesReceived >= fileSize || totalBytesReceived >= totalBytes) {
                        val deltaMs = now - lastUiUpdate
                        if (deltaMs > 0) {
                            val deltaBytes = totalBytesReceived - lastUpdateBytes
                            val instantSpeed = (deltaBytes / (1024.0 * 1024.0)) / (deltaMs / 1000.0)
                            smoothedSpeedMB = if (smoothedSpeedMB <= 0.01) instantSpeed else (smoothedSpeedMB * 0.75 + instantSpeed * 0.25)
                        }
                        lastUiUpdate = now
                        lastUpdateBytes = totalBytesReceived

                        val pct = if (totalBytes > 0) ((totalBytesReceived * 100) / totalBytes).toInt() else 0
                        val remainingBytes = totalBytes - totalBytesReceived
                        val etaSec = if (smoothedSpeedMB > 0) (remainingBytes / (smoothedSpeedMB * 1024 * 1024)).toLong() else 0L

                        onTransferState(
                            TransferState(
                                isActive = true,
                                isReceiving = true,
                                title = "Receiving from $senderAlias",
                                fileName = fileName,
                                progress = pct,
                                bytesTransferred = totalBytesReceived,
                                totalBytes = totalBytes,
                                speedMBs = smoothedSpeedMB,
                                etaSeconds = etaSec
                            )
                        )
                    }
                }

                fos.flush()
                fos.close()
                onFileSaved(fileName, fileSize)

                // If received file is a text note or small .txt message, trigger message dialog
                if ((fileName.contains("Shared_Note", ignoreCase = true) || fileName.endsWith(".txt", ignoreCase = true)) && fileSize < 65536) {
                    try {
                        val text = outFile.readText(Charsets.UTF_8)
                        onTextMessageReceived?.invoke(senderAlias, text)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Send COMPLETE response
            try {
                val completeResp = JSONObject().apply { put("status", "COMPLETE") }.toString().toByteArray(Charsets.UTF_8)
                val cLen = ByteBuffer.allocate(4).putInt(completeResp.size).array()
                outputStream.write(cLen)
                outputStream.write(completeResp)
                outputStream.flush()
            } catch (e: Exception) {
                // Client might have already closed connection
            }

            onTransferState(
                TransferState(
                    isActive = false,
                    isReceiving = true,
                    title = "Transfer Complete! 🎉",
                    fileName = if (filesList.size == 1) filesList[0].first else "${filesList.size} files",
                    progress = 100,
                    bytesTransferred = totalBytesReceived,
                    totalBytes = totalBytes,
                    isComplete = true
                )
            )

        } catch (e: Exception) {
            if (!isTransferCancelled) {
                onTransferState(
                    TransferState(
                        isActive = false,
                        isReceiving = true,
                        errorMessage = e.message ?: "Transfer interrupted"
                    )
                )
            }
        } finally {
            currentClientSocket = null
            try { socket.close() } catch (e: Exception) {}
        }
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

private class DelegatedSocket(private val raw: Socket, private val inStream: InputStream) : Socket() {
    override fun getInputStream(): InputStream = inStream
    override fun getOutputStream(): OutputStream = raw.getOutputStream()
    override fun close() = raw.close()
    override fun isClosed() = raw.isClosed
    override fun isConnected() = raw.isConnected
    override fun getInetAddress() = raw.inetAddress
    override fun getPort() = raw.port
    override fun getLocalPort() = raw.localPort
    override fun getRemoteSocketAddress() = raw.remoteSocketAddress
}
