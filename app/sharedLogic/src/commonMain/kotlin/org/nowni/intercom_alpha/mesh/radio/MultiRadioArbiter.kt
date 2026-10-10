package org.nowni.intercom_alpha.mesh.radio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nowni.intercom_alpha.mesh.ControlType
import org.nowni.intercom_alpha.mesh.MeshPacket

/**
 * Concurrent Multi-Radio Arbiter (BLE Mesh + Wi-Fi Direct + Cellular SFU).
 *
 * Dynamically routes high-bandwidth bulk payloads (tactical snapshots, trail GPX chunks)
 * over high-speed Wi-Fi Direct (P2P Cluster) when direct radio proximity is established,
 * while strictly maintaining continuous low-latency voice and emergency traffic over BLE mesh,
 * eliminating voice channel contention and medium saturation.
 *
 * Rules 2 & 3: Pure Kotlin Multiplatform with zero Java/C dependencies.
 */
class MultiRadioArbiter(
    val config: MultiRadioArbiterConfig = MultiRadioArbiterConfig()
) {
    private val mutex = Mutex()

    private val _state = MutableStateFlow(MultiRadioState())
    val state: StateFlow<MultiRadioState> = _state.asStateFlow()

    private val _linkMetrics = MutableStateFlow<Map<RadioType, RadioLinkMetrics>>(
        mapOf(
            RadioType.BLE_MESH to RadioLinkMetrics(
                radioType = RadioType.BLE_MESH,
                state = RadioLinkState.CONNECTED,
                rssi = -65,
                estimatedBandwidthKbps = 60
            )
        )
    )
    val linkMetrics: StateFlow<Map<RadioType, RadioLinkMetrics>> = _linkMetrics.asStateFlow()

    /**
     * Ingests latest link telemetry from a physical or virtual radio transceiver.
     */
    suspend fun updateLinkMetrics(metrics: RadioLinkMetrics) = mutex.withLock {
        val updatedMap = _linkMetrics.value.toMutableMap()
        updatedMap[metrics.radioType] = metrics
        _linkMetrics.value = updatedMap

        recomputeState(currentTimeMs = metrics.lastSeenTimestamp)
    }

    /**
     * Explicitly marks a radio interface as disconnected.
     */
    suspend fun notifyRadioDisconnected(radioType: RadioType, currentTimeMs: Long = 0L) = mutex.withLock {
        val current = _linkMetrics.value[radioType] ?: RadioLinkMetrics(radioType = radioType)
        val updatedMap = _linkMetrics.value.toMutableMap()
        updatedMap[radioType] = current.copy(
            state = RadioLinkState.DISCONNECTED,
            lastSeenTimestamp = currentTimeMs
        )
        _linkMetrics.value = updatedMap

        recomputeState(currentTimeMs = currentTimeMs)
    }

    /**
     * Classifies raw transmission payloads based on characteristics and size.
     */
    fun classifyPayload(
        sizeBytes: Int,
        isControl: Boolean = false,
        isVoice: Boolean = false,
        isEmergency: Boolean = false
    ): PayloadBandwidthClass {
        if (isEmergency) return PayloadBandwidthClass.EMERGENCY_CRITICAL
        if (isVoice) return PayloadBandwidthClass.VOICE_REALTIME
        if (isControl) return PayloadBandwidthClass.NETWORK_CONTROL
        if (sizeBytes > 256) return PayloadBandwidthClass.BULK_HIGH_BANDWIDTH
        return PayloadBandwidthClass.NETWORK_CONTROL
    }

    /**
     * Classifies a typed MeshPacket into a bandwidth and priority category.
     */
    fun classifyPacket(packet: MeshPacket): PayloadBandwidthClass {
        var curr = packet
        while (curr is MeshPacket.Relay) {
            curr = curr.packet
        }

        return when (curr) {
            is MeshPacket.Audio -> PayloadBandwidthClass.VOICE_REALTIME
            is MeshPacket.Discovery -> PayloadBandwidthClass.NETWORK_CONTROL
            is MeshPacket.Relay -> PayloadBandwidthClass.NETWORK_CONTROL
            is MeshPacket.Control -> {
                when (curr.type) {
                    ControlType.CRASH_ALERT,
                    ControlType.GEOFENCE_ALARM -> PayloadBandwidthClass.EMERGENCY_CRITICAL

                    ControlType.FEDERATION_AUDIO -> PayloadBandwidthClass.VOICE_REALTIME

                    ControlType.FILE_CHUNK,
                    ControlType.FILE_ACK -> PayloadBandwidthClass.BULK_HIGH_BANDWIDTH

                    else -> {
                        if (curr.payload.size > 256) {
                            PayloadBandwidthClass.BULK_HIGH_BANDWIDTH
                        } else {
                            PayloadBandwidthClass.NETWORK_CONTROL
                        }
                    }
                }
            }
        }
    }

    /**
     * Performs dynamic routing arbitration for a packet or payload stream.
     */
    suspend fun arbitrateRoute(
        payloadClass: PayloadBandwidthClass,
        recipientId: String? = null,
        currentTimeMs: Long = 0L
    ): MultiRadioRouteDecision = mutex.withLock {
        recomputeState(currentTimeMs)
        val currentState = _state.value
        val metricsMap = _linkMetrics.value

        val wifiMetrics = metricsMap[RadioType.WIFI_DIRECT]
        val isWifiUsable = wifiMetrics != null &&
                wifiMetrics.isOperational &&
                wifiMetrics.rssi >= config.wifiDirectRssiThreshold &&
                (currentTimeMs == 0L || (currentTimeMs - wifiMetrics.lastSeenTimestamp) <= config.wifiDirectTimeoutMs)

        val cellularMetrics = metricsMap[RadioType.CELLULAR_SFU]
        val isCellularUsable = cellularMetrics?.isOperational == true

        when (payloadClass) {
            PayloadBandwidthClass.EMERGENCY_CRITICAL -> {
                // Emergency traffic is broadcast with top priority over BLE Mesh.
                // Dual-casts over Cellular SFU if connected for wide-area safety.
                val hedge = if (isCellularUsable) RadioType.CELLULAR_SFU else null
                MultiRadioRouteDecision(
                    primaryRadio = RadioType.BLE_MESH,
                    hedgeRadio = hedge,
                    payloadClass = payloadClass,
                    reason = "Emergency SOS/Crash alert prioritized over BLE mesh with optional cellular hedging"
                )
            }

            PayloadBandwidthClass.VOICE_REALTIME -> {
                // Real-time voice is locked to BLE mesh to maintain steady jitter and avoid Wi-Fi contention
                val hedge = if (isCellularUsable && currentState.cellularConnected) RadioType.CELLULAR_SFU else null
                MultiRadioRouteDecision(
                    primaryRadio = RadioType.BLE_MESH,
                    hedgeRadio = hedge,
                    payloadClass = payloadClass,
                    reason = "Voice stream anchored to low-latency BLE mesh to eliminate Wi-Fi connection contention"
                )
            }

            PayloadBandwidthClass.NETWORK_CONTROL -> {
                // Topology and routing control operates natively over BLE mesh
                MultiRadioRouteDecision(
                    primaryRadio = RadioType.BLE_MESH,
                    hedgeRadio = null,
                    payloadClass = payloadClass,
                    reason = "Control signaling routes natively over decentralized BLE mesh"
                )
            }

            PayloadBandwidthClass.BULK_HIGH_BANDWIDTH -> {
                // High-bandwidth bulk telemetry (snapshots, GPX chunks):
                // If Wi-Fi Direct is available, offload to Wi-Fi Direct!
                if (config.preferWifiDirectForBulk && isWifiUsable) {
                    MultiRadioRouteDecision(
                        primaryRadio = RadioType.WIFI_DIRECT,
                        hedgeRadio = null,
                        payloadClass = payloadClass,
                        reason = "High-bandwidth payload offloaded to Wi-Fi Direct (P2P Cluster); voice channel protected"
                    )
                } else {
                    // Graceful fallback to BLE Mesh
                    val fallbackReason = if (wifiMetrics?.state == RadioLinkState.DISCONNECTED) {
                        "Wi-Fi Direct disconnected; gracefully falling back to BLE mesh"
                    } else if (wifiMetrics != null && wifiMetrics.rssi < config.wifiDirectRssiThreshold) {
                        "Wi-Fi Direct RSSI (${wifiMetrics.rssi} dBm) below threshold (${config.wifiDirectRssiThreshold} dBm); falling back to BLE mesh"
                    } else {
                        "Wi-Fi Direct not active; routing bulk data over BLE mesh"
                    }

                    MultiRadioRouteDecision(
                        primaryRadio = RadioType.BLE_MESH,
                        hedgeRadio = null,
                        payloadClass = payloadClass,
                        reason = fallbackReason
                    )
                }
            }
        }
    }

    private fun recomputeState(currentTimeMs: Long) {
        val metricsMap = _linkMetrics.value

        val bleMetrics = metricsMap[RadioType.BLE_MESH]
        val bleActive = bleMetrics?.isOperational == true

        val wifiMetrics = metricsMap[RadioType.WIFI_DIRECT]
        val wifiConnected = wifiMetrics != null &&
                wifiMetrics.isOperational &&
                wifiMetrics.rssi >= config.wifiDirectRssiThreshold &&
                (currentTimeMs == 0L || (currentTimeMs - wifiMetrics.lastSeenTimestamp) <= config.wifiDirectTimeoutMs)

        val cellMetrics = metricsMap[RadioType.CELLULAR_SFU]
        val cellConnected = cellMetrics?.isOperational == true

        val activeList = ArrayList<RadioType>()
        if (bleActive) activeList.add(RadioType.BLE_MESH)
        if (wifiConnected) activeList.add(RadioType.WIFI_DIRECT)
        if (cellConnected) activeList.add(RadioType.CELLULAR_SFU)

        val bulkRadio = if (wifiConnected && config.preferWifiDirectForBulk) {
            RadioType.WIFI_DIRECT
        } else {
            RadioType.BLE_MESH
        }

        _state.value = MultiRadioState(
            bleActivePeers = if (bleActive) 1 else 0,
            wifiDirectConnected = wifiConnected,
            wifiDirectPeerId = if (wifiConnected) wifiMetrics.peerId else null,
            cellularConnected = cellConnected,
            activeRadios = activeList,
            bulkTransferPreferredRadio = bulkRadio,
            voicePreferredRadio = RadioType.BLE_MESH,
            lastArbitrationTimestamp = currentTimeMs
        )
    }
}
