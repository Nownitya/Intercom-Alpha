package org.nowni.intercom_alpha.mesh.failover

import kotlinx.serialization.Serializable

/**
 * Operating mode of the multi-path failover arbiter.
 *
 * Dictates whether outbound packets route directly over BLE mesh, are hedged concurrently
 * over both BLE mesh and Cloud Relay WebSocket, route solely through Cloud Relay, or enter
 * restricted offline mode.
 *
 * Rules 2 & 3: Pure Kotlin Multiplatform with zero Java or platform dependencies.
 */
@Serializable
enum class FailoverMode {
    /** High-quality local BLE link; packets route 100% over offline BLE mesh to save cellular data */
    BLE_DIRECT,

    /** Link degradation detected (high ETX, packet loss, or weak RSSI); critical frames dual-cast over BLE and Cloud */
    MULTI_PATH_HEDGED,

    /** Local BLE mesh unreachable (out-of-range, convoy split); all packets route via Ktor Cloud Relay WebSocket */
    CLOUD_RELAY_ONLY,

    /** Both local BLE mesh and cellular cloud relay are unreachable */
    RESTRICTED_OFFLINE
}

/**
 * Configuration thresholds for the multi-path failover arbiter.
 */
@Serializable
data class FailoverConfig(
    val bleTimeoutMs: Long = 4000L,
    val degradeEtxThreshold: Double = 2.5,
    val degradeLossRatioThreshold: Double = 0.20,
    val degradeRssiThreshold: Int = -88,
    val hysteresisDurationMs: Long = 5000L
)

/**
 * Real-time snapshot of link metrics and handover arbitration state.
 */
@Serializable
data class FailoverMetrics(
    val currentMode: FailoverMode = FailoverMode.RESTRICTED_OFFLINE,
    val meshPeerCount: Int = 0,
    val averageEtx: Double = 1.0,
    val averageDeliveryRatio: Double = 1.0,
    val lastBleActivityMs: Long = 0L,
    val isCloudConnected: Boolean = false,
    val timeInCurrentModeMs: Long = 0L,
    val totalHandovers: Long = 0L,
    val hedgedPacketsCount: Long = 0L,
    val isBleDegraded: Boolean = false
)
