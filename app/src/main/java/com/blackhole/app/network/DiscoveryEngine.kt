package com.blackhole.app.network

import android.content.Context
import android.net.wifi.WifiManager
import com.blackhole.app.model.PeerDevice
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DiscoveryEngine(
    private val context: Context,
    private val onPeersUpdated: (List<PeerDevice>) -> Unit
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var multicastLock: WifiManager.MulticastLock? = null

    private val discoveredPeers = ConcurrentHashMap<String, PeerDevice>()
    var myAlias: String = "BlackHole-Android"
    var myPort: Int = 53317
    val myFingerprint: String = TlsHelper.getDeviceFingerprint(context)

    fun start() {
        if (isRunning) return
        isRunning = true

        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("BlackHoleMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        startBroadcastSender()
        startBroadcastReceiver()
        startPruningJob()
    }

    fun stop() {
        isRunning = false
        coroutineScope.cancel()
        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun registerOrUpdatePeer(peer: PeerDevice) {
        discoveredPeers[peer.ip] = peer
        notifyPeers()
    }

    private fun startBroadcastSender() {
        coroutineScope.launch {
            val multicastGroup = InetAddress.getByName("224.0.0.167")
            val globalBroadcast = InetAddress.getByName("255.255.255.255")

            while (isRunning) {
                try {
                    // Universal Multicast Beacon (v2.2 Specification)
                    val modelName = android.os.Build.MODEL ?: "Android"
                    val lsJson = JSONObject().apply {
                        put("app", "BlackHole")
                        put("alias", myAlias)
                        put("version", "2.2")
                        put("deviceModel", modelName)
                        put("deviceType", "mobile")
                        put("fingerprint", myFingerprint)
                        put("port", myPort)
                        put("protocol", "http")
                        put("download", true)
                        put("announcement", true)
                    }
                    val lsData = lsJson.toString().toByteArray(Charsets.UTF_8)

                    // Send to global broadcast 255.255.255.255 on default socket
                    try {
                        val defSocket = DatagramSocket()
                        defSocket.broadcast = true
                        defSocket.send(DatagramPacket(lsData, lsData.size, globalBroadcast, 53317))
                        defSocket.send(DatagramPacket(lsData, lsData.size, multicastGroup, 53317))
                        defSocket.close()
                    } catch (e: Exception) {}

                    // Iterate ALL active network interfaces (critical for Wi-Fi hotspot ap0/wlan0)
                    try {
                        val interfaces = NetworkInterface.getNetworkInterfaces()
                        while (interfaces.hasMoreElements()) {
                            val ni = interfaces.nextElement()
                            if (ni.isLoopback || !ni.isUp) continue

                            // A. Send subnet broadcasts for each interface address (e.g. 192.168.43.255)
                            for (ia in ni.interfaceAddresses) {
                                val bcast = ia.broadcast
                                if (bcast != null) {
                                    try {
                                        val s = DatagramSocket()
                                        s.broadcast = true
                                        s.send(DatagramPacket(lsData, lsData.size, bcast, 53317))
                                        s.close()
                                    } catch (e: Exception) {}
                                }
                            }

                            // B. Send multicast pinned to this interface
                            if (ni.supportsMulticast()) {
                                try {
                                    val ms = MulticastSocket()
                                    ms.networkInterface = ni
                                    ms.timeToLive = 1
                                    ms.send(DatagramPacket(lsData, lsData.size, multicastGroup, 53317))
                                    ms.close()
                                } catch (e: Exception) {}
                            }
                        }
                    } catch (e: Exception) {}
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(2500)
            }
        }
    }

    private fun startBroadcastReceiver() {
        coroutineScope.launch {
            var socket: MulticastSocket? = null
            try {
                socket = MulticastSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(53317))
                    soTimeout = 3000
                }

                val multicastGroup = InetAddress.getByName("224.0.0.167")
                val groupSocketAddress = InetSocketAddress(multicastGroup, 53317)

                // Join on default interface
                try {
                    socket.joinGroup(multicastGroup)
                } catch (e: Exception) {}

                // Join on EVERY active network interface (critical for Hotspot ap0/wlan0 vs cellular)
                try {
                    val interfaces = NetworkInterface.getNetworkInterfaces()
                    while (interfaces.hasMoreElements()) {
                        val ni = interfaces.nextElement()
                        if (!ni.isLoopback && ni.isUp && ni.supportsMulticast()) {
                            try {
                                socket.joinGroup(groupSocketAddress, ni)
                            } catch (e: Exception) {}
                        }
                    }
                } catch (e: Exception) {}

                val buffer = ByteArray(65536)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isRunning) {
                    try {
                        socket.receive(packet)
                        val senderIp = packet.address.hostAddress ?: continue

                        // Filter out own local IP addresses
                        if (isLocalIp(senderIp)) continue

                        val msg = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        if (!msg.startsWith("{") || !msg.endsWith("}")) continue

                        val json = JSONObject(msg)
                        val app = json.optString("app", "")
                        val fingerprint = json.optString("fingerprint", "")
                        if (fingerprint == myFingerprint || fingerprint == "blackhole-android-$myFingerprint") continue

                        val existingPeer = discoveredPeers[senderIp]
                        val isBlackHolePeer = app.equals("BlackHole", ignoreCase = true) || 
                                              fingerprint.contains("blackhole", ignoreCase = true)

                        if (isBlackHolePeer) {
                            // Native BlackHole Peer (Phone or PC)
                            val alias = json.optString("alias", "")
                            val port = json.optInt("port", 53317)
                            val devType = json.optString("deviceType", "desktop")

                            if (alias.isNotEmpty() || existingPeer != null) {
                                val peer = PeerDevice(
                                    ip = senderIp,
                                    port = port,
                                    alias = if (alias.isNotEmpty()) alias else existingPeer?.alias ?: "BlackHole Device",
                                    deviceType = if (devType.equals("mobile", ignoreCase = true) || devType.equals("phone", ignoreCase = true)) "mobile" else "desktop",
                                    protocol = "blackhole",
                                    fingerprint = fingerprint,
                                    isHttps = false,
                                    lastSeen = System.currentTimeMillis()
                                )
                                discoveredPeers[senderIp] = peer
                                notifyPeers()
                            }
                        } else if (json.has("version") && (json.has("protocol") || json.has("fingerprint") || json.has("deviceModel"))) {
                            // Universal Compatible Peer
                            val alias = json.optString("alias", "Network Peer")
                            val port = json.optInt("port", 53317)
                            val devType = json.optString("deviceType", "desktop")
                            val isAnnouncement = json.optBoolean("announcement", false)
                            val peerProtocol = json.optString("protocol", "https")
                            val isHttps = peerProtocol.equals("https", ignoreCase = true)

                            val peer = PeerDevice(
                                ip = senderIp,
                                port = port,
                                alias = alias,
                                deviceType = if (devType.equals("mobile", ignoreCase = true) || devType.equals("phone", ignoreCase = true)) "mobile" else "desktop",
                                protocol = "localsend",
                                fingerprint = fingerprint,
                                isHttps = isHttps,
                                lastSeen = System.currentTimeMillis()
                            )
                            discoveredPeers[senderIp] = peer
                            notifyPeers()

                            // If this was an announcement, register ourselves to their HTTP server
                            if (isAnnouncement) {
                                respondRegister(senderIp, port, isHttps)
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        // normal timeout
                    } catch (e: Exception) {
                        // parse or receive error
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                socket?.close()
            }
        }
    }

    private fun respondRegister(targetIp: String, targetPort: Int, isHttps: Boolean = false) {
        coroutineScope.launch {
            try {
                val protocol = if (isHttps) "https" else "http"
                val url = URL("$protocol://$targetIp:$targetPort/api/localsend/v2/register")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    if (this is javax.net.ssl.HttpsURLConnection) {
                        sslSocketFactory = TlsHelper.getTrustAllSSLSocketFactory()
                        hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                    }
                    requestMethod = "POST"
                    connectTimeout = 2500
                    readTimeout = 2500
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }

                val modelName = android.os.Build.MODEL ?: "Android"
                val body = JSONObject().apply {
                    put("app", "BlackHole")
                    put("alias", myAlias)
                    put("version", "2.2")
                    put("deviceModel", modelName)
                    put("deviceType", "mobile")
                    put("fingerprint", myFingerprint)
                    put("port", myPort)
                    put("protocol", "http")
                    put("download", true)
                }.toString()

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
                conn.responseCode // trigger connection
                conn.disconnect()
            } catch (e: Exception) {
                // If register via peer's protocol failed, try alternate protocol once
                if (!isHttps) {
                    try {
                        val url = URL("https://$targetIp:$targetPort/api/localsend/v2/register")
                        val conn = (url.openConnection() as javax.net.ssl.HttpsURLConnection).apply {
                            sslSocketFactory = TlsHelper.getTrustAllSSLSocketFactory()
                            hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                            requestMethod = "POST"
                            connectTimeout = 2000
                            readTimeout = 2000
                            doOutput = true
                            setRequestProperty("Content-Type", "application/json")
                        }
                        val modelName = android.os.Build.MODEL ?: "Android"
                        val body = JSONObject().apply {
                            put("app", "BlackHole")
                            put("alias", myAlias)
                            put("version", "2.2")
                            put("deviceModel", modelName)
                            put("deviceType", "mobile")
                            put("fingerprint", myFingerprint)
                            put("port", myPort)
                            put("protocol", "http")
                            put("download", true)
                        }.toString()
                        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
                        conn.responseCode
                        conn.disconnect()
                    } catch (e2: Exception) {}
                }
            }
        }
    }

    private fun startPruningJob() {
        coroutineScope.launch {
            while (isRunning) {
                delay(4000)
                val now = System.currentTimeMillis()
                var changed = false
                val it = discoveredPeers.entries.iterator()
                while (it.hasNext()) {
                    val entry = it.next()
                    if (now - entry.value.lastSeen > 12000) {
                        it.remove()
                        changed = true
                    }
                }
                if (changed) {
                    notifyPeers()
                }
            }
        }
    }

    private fun notifyPeers() {
        val list = discoveredPeers.values.toList().sortedBy { it.alias }
        onPeersUpdated(list)
    }

    private fun isLocalIp(ip: String): Boolean {
        if (ip == "127.0.0.1" || ip == "localhost") return true
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (iface in interfaces) {
                for (addr in iface.inetAddresses) {
                    if (addr.hostAddress == ip) return true
                }
            }
        } catch (e: Exception) {}
        return false
    }

    companion object {
        fun getLocalIpAddress(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                for (iface in interfaces) {
                    if (iface.isLoopback || !iface.isUp) continue
                    for (addr in iface.inetAddresses) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val host = addr.hostAddress
                            if (host != null && !host.startsWith("169.254.")) {
                                return host
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
            return "127.0.0.1"
        }
    }
}
