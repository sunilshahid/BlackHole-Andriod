package com.blackhole.app.network

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.blackhole.app.model.FileItem
import com.blackhole.app.model.TransferState
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

class WebRtcEngine(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var isEngineReady = false

    private var currentSendingFiles: List<FileItem> = emptyList()
    private var onStatusCallback: ((String) -> Unit)? = null
    private var onRoomCreatedCallback: ((String, String) -> Unit)? = null
    private var onProgressCallback: ((TransferState) -> Unit)? = null
    private var onCompleteCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    private var isReceiving = false

    init {
        mainHandler.post {
            initWebView()
        }
    }

    private fun initWebView() {
        try {
            val wv = WebView(context.applicationContext)
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
            }

            wv.webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    Log.i("WebRtcEngine-JS", "${consoleMessage?.message()} [${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}]")
                    return true
                }
            }

            wv.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    isEngineReady = true
                    Log.i(TAG, "Headless WebRTC engine loaded successfully")

                    // Pass local IPs to JS
                    val ips = getLocalIpList()
                    val ipsJson = JSONArray(ips).toString()
                    wv.evaluateJavascript("window.blackholeEngine.setLocalIps($ipsJson);", null)
                }
            }

            wv.onResume()
            wv.loadUrl("file:///android_asset/webrtc/engine.html")
            webView = wv
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC WebView", e)
        }
    }

    fun setKnownPeerIps(peerIps: List<String>) {
        mainHandler.post {
            executeWhenReady {
                val ipsJson = JSONArray(peerIps).toString()
                webView?.evaluateJavascript("window.blackholeEngine.setKnownPeerIps($ipsJson);", null)
            }
        }
    }

    suspend fun ensureSignalingAwake(
        onStatus: (String) -> Unit,
        timeoutMs: Long = 45000L
    ): Boolean = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val startTime = System.currentTimeMillis()
        var attempt = 0

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            attempt++
            val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            withContext(Dispatchers.Main) {
                if (attempt == 1) {
                    onStatus("Connecting to secure signaling network...")
                } else {
                    onStatus("Waking up cloud server (${elapsedSec}s) - Render starting...")
                }
            }

            try {
                val req = Request.Builder()
                    .url("https://share.sunilshahid.com/health")
                    .header("Cache-Control", "no-cache")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            onStatus("Cloud server online! Connecting...")
                        }
                        return@withContext true
                    }
                }
            } catch (e: Exception) {
                // Render still waking up
            }
            delay(1500L)
        }
        return@withContext false
    }

    fun createRoom(
        files: List<FileItem>,
        onStatus: (String) -> Unit,
        onRoomCreated: (String, String) -> Unit,
        onProgress: (TransferState) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        currentSendingFiles = files
        onStatusCallback = onStatus
        onRoomCreatedCallback = onRoomCreated
        onProgressCallback = onProgress
        onCompleteCallback = onComplete
        onErrorCallback = onError
        isReceiving = false

        mainHandler.post {
            executeWhenReady {
                val filesArr = JSONArray()
                files.forEach { f ->
                    val obj = JSONObject()
                    obj.put("name", f.name)
                    obj.put("size", f.size)
                    obj.put("mimeType", f.mimeType)
                    filesArr.put(obj)
                }
                val jsonStr = JSONObject.quote(filesArr.toString())
                webView?.evaluateJavascript("window.blackholeEngine.createRoom($jsonStr);", null)
            }
        }
    }

    fun joinRoom(
        code: String,
        onStatus: (String) -> Unit,
        onProgress: (TransferState) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        onStatusCallback = onStatus
        onProgressCallback = onProgress
        onCompleteCallback = onComplete
        onErrorCallback = onError
        isReceiving = true

        mainHandler.post {
            executeWhenReady {
                val cleanCode = code.uppercase().filter { it.isLetterOrDigit() || it == '-' }
                val codeParam = JSONObject.quote(cleanCode)
                webView?.evaluateJavascript("window.blackholeEngine.joinRoom($codeParam);", null)
            }
        }
    }

    fun cancel() {
        mainHandler.post {
            webView?.evaluateJavascript("window.blackholeEngine.cancel();", null)
        }
    }

    private fun executeWhenReady(action: () -> Unit) {
        if (isEngineReady) {
            action()
        } else {
            mainHandler.postDelayed({ executeWhenReady(action) }, 100)
        }
    }

    private fun getLocalIpList(): List<String> {
        val list = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        list.add(addr.hostAddress ?: "")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get local IPs", e)
        }
        return list.filter { it.isNotEmpty() }
    }

    // --- JavaScript Bridge ---
    inner class AndroidBridge {

        @JavascriptInterface
        fun onStatus(msg: String) {
            mainHandler.post {
                onStatusCallback?.invoke(msg)
            }
        }

        @JavascriptInterface
        fun onRoomCreated(code: String, shareUrl: String) {
            mainHandler.post {
                onRoomCreatedCallback?.invoke(code, shareUrl)
            }
        }

        @JavascriptInterface
        fun onConnectionMode(mode: String, route: String) {
            Log.i(TAG, "WebRTC Connection Mode: $mode ($route)")
        }

        @JavascriptInterface
        fun onTransferStarted(fileCount: Int, totalBytes: Long) {
            mainHandler.post {
                onProgressCallback?.invoke(
                    TransferState(
                        isActive = true,
                        isReceiving = isReceiving,
                        title = if (isReceiving) "Receiving $fileCount file(s)..." else "Sending $fileCount file(s)...",
                        progress = 0,
                        bytesTransferred = 0,
                        totalBytes = totalBytes,
                        speedMBs = 0.0,
                        etaSeconds = 0,
                        isComplete = false
                    )
                )
            }
        }

        @JavascriptInterface
        fun onFileStart(fileName: String, totalBytes: Long) {
            mainHandler.post {
                onProgressCallback?.invoke(
                    TransferState(
                        isActive = true,
                        isReceiving = isReceiving,
                        title = if (isReceiving) "Receiving $fileName..." else "Sending $fileName...",
                        fileName = fileName,
                        progress = 0,
                        bytesTransferred = 0,
                        totalBytes = totalBytes,
                        speedMBs = 0.0,
                        etaSeconds = 0,
                        isComplete = false
                    )
                )
            }
        }

        @JavascriptInterface
        fun onProgress(
            fileName: String,
            percent: Int,
            speedBytesPerSec: Long,
            etaSeconds: Int,
            transferredBytes: Long,
            totalBytes: Long
        ) {
            val speedMB = speedBytesPerSec.toDouble() / (1024.0 * 1024.0)
            mainHandler.post {
                onProgressCallback?.invoke(
                    TransferState(
                        isActive = true,
                        isReceiving = isReceiving,
                        title = if (isReceiving) "Receiving via WebRTC..." else "Sending via WebRTC...",
                        fileName = fileName,
                        progress = percent,
                        bytesTransferred = transferredBytes,
                        totalBytes = totalBytes,
                        speedMBs = speedMB,
                        etaSeconds = etaSeconds.toLong(),
                        isComplete = false
                    )
                )
            }
        }

        @JavascriptInterface
        fun writeChunk(fileName: String, offset: Long, dataBase64: String, isLast: Boolean) {
            try {
                val downloadDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "BlackHole"
                )
                if (!downloadDir.exists()) downloadDir.mkdirs()

                val file = File(downloadDir, fileName)
                val bytes = Base64.decode(dataBase64, Base64.NO_WRAP)

                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(offset)
                    raf.write(bytes)
                }

                if (isLast) {
                    Log.i(TAG, "Completed writing file to Downloads/BlackHole: $fileName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing chunk for $fileName", e)
            }
        }

        @JavascriptInterface
        fun onFileComplete(fileName: String, size: Long) {
            Log.i(TAG, "File complete: $fileName ($size bytes)")
        }

        @JavascriptInterface
        fun onTransferComplete() {
            mainHandler.post {
                onProgressCallback?.invoke(
                    TransferState(
                        isActive = false,
                        isReceiving = isReceiving,
                        title = "Transfer Complete",
                        fileName = "All files transferred",
                        progress = 100,
                        isComplete = true
                    )
                )
                onCompleteCallback?.invoke()
            }
        }

        @JavascriptInterface
        fun onError(errorMsg: String) {
            mainHandler.post {
                onProgressCallback?.invoke(
                    TransferState(
                        isActive = false,
                        isReceiving = isReceiving,
                        errorMessage = errorMsg
                    )
                )
                onErrorCallback?.invoke(errorMsg)
            }
        }

        @JavascriptInterface
        fun readChunk(fileIndex: Int, offset: Long, length: Int): String {
            val fileItem = currentSendingFiles.getOrNull(fileIndex) ?: return ""
            try {
                if (fileItem.uri != null) {
                    val pfd = context.contentResolver.openFileDescriptor(fileItem.uri, "r")
                    if (pfd != null) {
                        pfd.use { fd ->
                            FileInputStream(fd.fileDescriptor).use { fis ->
                                fis.channel.position(offset)
                                val buffer = ByteArray(length)
                                var totalRead = 0
                                while (totalRead < length) {
                                    val r = fis.read(buffer, totalRead, length - totalRead)
                                    if (r <= 0) break
                                    totalRead += r
                                }
                                if (totalRead > 0) {
                                    return Base64.encodeToString(buffer, 0, totalRead, Base64.NO_WRAP)
                                }
                            }
                        }
                    }
                } else if (!fileItem.path.isNullOrEmpty()) {
                    RandomAccessFile(File(fileItem.path), "r").use { raf ->
                        raf.seek(offset)
                        val buffer = ByteArray(length)
                        var totalRead = 0
                        while (totalRead < length) {
                            val r = raf.read(buffer, totalRead, length - totalRead)
                            if (r <= 0) break
                            totalRead += r
                        }
                        if (totalRead > 0) {
                            return Base64.encodeToString(buffer, 0, totalRead, Base64.NO_WRAP)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading chunk for fileIndex=$fileIndex offset=$offset", e)
            }
            return ""
        }
    }

    companion object {
        private const val TAG = "WebRtcEngine"
    }
}
