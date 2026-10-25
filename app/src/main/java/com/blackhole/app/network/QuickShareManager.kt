package com.blackhole.app.network

import android.content.Context
import android.os.Environment
import com.blackhole.app.model.PeerDevice
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class QuickShareManager(
    private val context: Context,
    private val onPeersDiscovered: (List<PeerDevice>) -> Unit,
    private val onIncomingFile: (String, Long) -> Unit,
    private val onIncomingText: (String, String) -> Unit
) {
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val SERVICE_ID = "com.blackhole.quickshare"
    private val discoveredEndpoints = ConcurrentHashMap<String, PeerDevice>()
    private var isAdvertising = false
    private var isDiscovering = false

    var deviceAlias: String = "BlackHole QuickShare"

    private val incomingPayloads = ConcurrentHashMap<Long, Payload>()

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    val text = String(bytes, Charsets.UTF_8)
                    val sender = discoveredEndpoints[endpointId]?.alias ?: "Quick Share Device"
                    onIncomingText(sender, text)
                }
                Payload.Type.FILE -> {
                    incomingPayloads[payload.id] = payload
                }
                Payload.Type.STREAM -> {}
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                val payload = incomingPayloads.remove(update.payloadId)
                if (payload != null && payload.type == Payload.Type.FILE) {
                    val javaFile = payload.asFile()?.asJavaFile()
                    if (javaFile != null) {
                        val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BlackHole")
                        if (!downloadDir.exists()) downloadDir.mkdirs()
                        val destFile = File(downloadDir, javaFile.name)
                        javaFile.renameTo(destFile)
                        onIncomingFile(destFile.name, destFile.length())
                    }
                }
            }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            // Auto-accept Quick Share connections
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {}

        override fun onDisconnected(endpointId: String) {}
    }

    fun startAdvertising() {
        if (isAdvertising) return
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_STAR)
            .build()

        connectionsClient.startAdvertising(
            deviceAlias,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            isAdvertising = true
        }.addOnFailureListener {
            isAdvertising = false
        }
    }

    fun stopAdvertising() {
        if (!isAdvertising) return
        connectionsClient.stopAdvertising()
        isAdvertising = false
    }

    fun startDiscovery() {
        if (isDiscovering) return
        discoveredEndpoints.clear()

        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_STAR)
            .build()

        val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                val peer = PeerDevice(
                    ip = endpointId,
                    port = 0,
                    alias = info.endpointName,
                    deviceType = "phone",
                    protocol = "quickshare"
                )
                discoveredEndpoints[endpointId] = peer
                onPeersDiscovered(discoveredEndpoints.values.toList())
            }

            override fun onEndpointLost(endpointId: String) {
                discoveredEndpoints.remove(endpointId)
                onPeersDiscovered(discoveredEndpoints.values.toList())
            }
        }

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            isDiscovering = true
        }.addOnFailureListener {
            isDiscovering = false
        }
    }

    fun stopDiscovery() {
        if (!isDiscovering) return
        connectionsClient.stopDiscovery()
        isDiscovering = false
        discoveredEndpoints.clear()
    }

    fun sendPayload(endpointId: String, text: String) {
        val payload = Payload.fromBytes(text.toByteArray(Charsets.UTF_8))
        connectionsClient.sendPayload(endpointId, payload)
    }

    fun sendFile(endpointId: String, file: File) {
        val payload = Payload.fromFile(file)
        connectionsClient.sendPayload(endpointId, payload)
    }

    fun stopAll() {
        stopAdvertising()
        stopDiscovery()
        connectionsClient.stopAllEndpoints()
    }
}
