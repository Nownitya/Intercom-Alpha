package org.nowni.intercom_alpha.mesh.phy

import kotlinx.serialization.Serializable

/**
 * Bluetooth Low Energy PHY Layer Modulation Modes.
 *
 * Intercom-Alpha dynamically migrates between these modes based on
 * alpine topography, packet loss ratios, and link budgets.
 */
@Serializable
enum class BlePhyMode(
    val bitRateKbps: Int,
    val nominalSensitivityDbm: Int,
    val maxEffectiveRangeMeters: Int,
    val codingMultiplier: Int
) {
    /** High-bandwidth, low airtime mode for close proximity convoys (<30-50m). */
    LE_2M(bitRateKbps = 2000, nominalSensitivityDbm = -90, maxEffectiveRangeMeters = 50, codingMultiplier = 1),

    /** Standard BLE 1 Mbps modulation; standard default for general operations (~100m). */
    LE_1M(bitRateKbps = 1000, nominalSensitivityDbm = -93, maxEffectiveRangeMeters = 120, codingMultiplier = 1),

    /** LE Coded S=2 (Forward Error Correction 500 kbps); medium-to-long reach (~250-350m). */
    CODED_S2(bitRateKbps = 500, nominalSensitivityDbm = -98, maxEffectiveRangeMeters = 350, codingMultiplier = 2),

    /** LE Coded S=8 (Maximum FEC 125 kbps); extreme reach (>500-1000m line-of-sight across alpine gorges). */
    CODED_S8(bitRateKbps = 125, nominalSensitivityDbm = -103, maxEffectiveRangeMeters = 1000, codingMultiplier = 8)
}

/**
 * Alpine Topographical Shadowing and Propagation Profiles.
 */
@Serializable
enum class TerrainShadowingProfile(
    val pathLossExponent: Double,
    val diffractionMarginDb: Double,
    val description: String
) {
    /** Open crest line-of-sight with near free-space path loss. */
    OPEN_ALPINE_RIDGE(pathLossExponent = 2.0, diffractionMarginDb = 2.0, description = "Open line of sight"),

    /** Sharp mountain switchback with rock corner diffraction. */
    MOUNTAIN_SWITCHBACK(pathLossExponent = 2.8, diffractionMarginDb = 10.0, description = "Corner hairpin diffraction"),

    /** Deep gorge/canyon with high vertical rock walls and severe shadowing. */
    DEEP_CANYON_GORGE(pathLossExponent = 3.5, diffractionMarginDb = 16.0, description = "Deep rock canyon shadowing"),

    /** Alpine coniferous forest with dense pine foliage RF absorption. */
    DENSE_ALPINE_PINE_FOREST(pathLossExponent = 3.0, diffractionMarginDb = 8.0, description = "Foliage RF attenuation")
}

/**
 * Hardware and Radio Capabilities of a node or peer.
 */
@Serializable
data class PhyCapability(
    val peerId: String,
    val supportedModes: Set<BlePhyMode> = setOf(BlePhyMode.LE_1M, BlePhyMode.LE_2M, BlePhyMode.CODED_S2, BlePhyMode.CODED_S8),
    val supportsHighTxPower: Boolean = true,
    val maxTxPowerDbm: Int = 8
) {
    fun isModeSupported(mode: BlePhyMode): Boolean = supportedModes.contains(mode)
}

/**
 * Dynamic RF and link metric tracking for an active peer.
 */
@Serializable
data class PhyLinkMetrics(
    val peerId: String,
    val currentPhy: BlePhyMode,
    val rssi: Int,
    val snrDb: Double = 0.0,
    val packetLossRatio: Double = 0.0,
    val elevationDeltaMeters: Double = 0.0,
    val estimatedDistanceMeters: Double = 0.0,
    val consecutiveWeakSamples: Int = 0,
    val consecutiveStrongSamples: Int = 0,
    val lastNegotiationTimestampMs: Long = 0L,
    val timestampMs: Long = 0L
)

/**
 * Event triggered when a PHY mode migration is initiated or confirmed.
 */
@Serializable
data class PhyNegotiationEvent(
    val peerId: String,
    val previousPhy: BlePhyMode,
    val targetPhy: BlePhyMode,
    val reason: String,
    val timestampMs: Long
)

/**
 * Protocol Control Packet for Over-The-Air PHY Mode Negotiation between nodes.
 */
@Serializable
data class PhyNegotiationPacket(
    val sourceId: String,
    val targetId: String,
    val requestedPhy: BlePhyMode,
    val sequenceNumber: Long,
    val timestampMs: Long,
    val isAcknowledgement: Boolean = false,
    val accepted: Boolean = true
)
