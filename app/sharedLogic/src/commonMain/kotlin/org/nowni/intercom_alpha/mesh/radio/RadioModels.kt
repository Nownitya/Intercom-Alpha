package org.nowni.intercom_alpha.mesh.radio

import kotlinx.serialization.Serializable

/**
 * Physical and virtual radio transceivers available to an Intercom-Alpha node.
 * Rules 2 & 3: Pure Kotlin Multiplatform with zero Java/C dependencies.
 */
@Serializable
enum class RadioType {
    /** Low-energy, resilient, multi-hop mesh (2.4 GHz Bluetooth Low Energy) */
    BLE_MESH,

    /** High-throughput, peer-to-peer Wi-Fi cluster (Wi-Fi Direct / Nearby P2P) */
    WIFI_DIRECT,

    /** Long-range cellular or cloud relay via Ktor SFU WebSocket bridge */
    CELLULAR_SFU
}

/**
 * Link lifecycle status for an individual radio interface.
 */
@Serializable
enum class RadioLinkState {
    DISCONNECTED,
    DISCOVERING,
    CONNECTING,
    CONNECTED,
    DEGRADED
}

/**
 * Payload bandwidth and latency sensitivity classification.
 */
@Serializable
enum class PayloadBandwidthClass {
    /** Life-safety SOS, crash detection alerts, geofence boundary alarms */
    EMERGENCY_CRITICAL,

    /** Ultra-low-latency real-time Opus voice frames (<= 20ms delivery budget) */
    VOICE_REALTIME,

    /** Mesh routing control (AODV RREQ/RREP), join/leave beacons, ping/pong */
    NETWORK_CONTROL,

    /** High-bandwidth bulk telemetry: tactical snapshots (<= 1.5 KB), GPX trail chunks */
    BULK_HIGH_BANDWIDTH
}

/**
 * Real-time radio link quality telemetry for routing arbitration.
 */
@Serializable
data class RadioLinkMetrics(
    val radioType: RadioType,
    val state: RadioLinkState = RadioLinkState.DISCONNECTED,
    val peerId: String = "",
    val rssi: Int = -75,
    val estimatedBandwidthKbps: Int = 50,
    val latencyMs: Long = 40L,
    val packetLossRatio: Float = 0f,
    val lastSeenTimestamp: Long = 0L
) {
    val isOperational: Boolean
        get() = state == RadioLinkState.CONNECTED || state == RadioLinkState.DEGRADED
}

/**
 * Comprehensive routing decision emitted by the multi-radio arbiter.
 */
@Serializable
data class MultiRadioRouteDecision(
    val primaryRadio: RadioType,
    val hedgeRadio: RadioType? = null,
    val payloadClass: PayloadBandwidthClass,
    val reason: String
)

/**
 * Real-time observable snapshot of concurrent multi-radio topology.
 */
@Serializable
data class MultiRadioState(
    val bleActivePeers: Int = 0,
    val wifiDirectConnected: Boolean = false,
    val wifiDirectPeerId: String? = null,
    val cellularConnected: Boolean = false,
    val activeRadios: List<RadioType> = listOf(RadioType.BLE_MESH),
    val bulkTransferPreferredRadio: RadioType = RadioType.BLE_MESH,
    val voicePreferredRadio: RadioType = RadioType.BLE_MESH,
    val lastArbitrationTimestamp: Long = 0L
)

/**
 * Configuration parameters and thresholds for multi-radio arbitration.
 */
@Serializable
data class MultiRadioArbiterConfig(
    val wifiDirectRssiThreshold: Int = -80,
    val wifiDirectTimeoutMs: Long = 3000L,
    val bleTimeoutMs: Long = 4000L,
    val preferWifiDirectForBulk: Boolean = true,
    val keepVoiceOnBle: Boolean = true
)
