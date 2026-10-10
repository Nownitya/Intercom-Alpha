package org.nowni.intercom_alpha.mesh.qos

import kotlinx.serialization.Serializable

/**
 * Airtime congestion severity classification derived from Channel Busy Ratio (CBR).
 */
@Serializable
enum class ChannelCongestionLevel {
    /** CBR < 25%. Free airtime; all queues transmit at full rate with minimal FEC. */
    UNCONGESTED,

    /** CBR 25% - 50%. Moderate RF load; bulk telemetry paced to 50% duty cycle. */
    MODERATE,

    /** CBR 50% - 75%. Heavy RF contention; bulk telemetry throttled, light FEC activated. */
    HIGH,

    /** CBR > 75%. Critical saturation; bulk telemetry suspended, voice FEC maximum. */
    CRITICAL
}

/**
 * Forward Error Correction (FEC) redundancy modes for mesh audio packets.
 */
@Serializable
enum class FecRedundancyLevel(val redundancyRatio: Float) {
    /** 0% overhead: single packet transmission without parity */
    NONE(0.0f),

    /** 25% overhead: 1 parity block per 4 voice frames */
    LIGHT(0.25f),

    /** 50% overhead: 1 parity block per 2 voice frames */
    MEDIUM(0.50f),

    /** 100% overhead: duplicate packet transmission under extreme RF packet collision */
    HEAVY(1.0f)
}

/**
 * Snapshot of real-time airtime congestion diagnostics and pacing policies.
 */
@Serializable
data class CongestionMetricsSnapshot(
    val channelBusyRatio: Float = 0.0f,
    val estimatedCollisionRate: Float = 0.0f,
    val congestionLevel: ChannelCongestionLevel = ChannelCongestionLevel.UNCONGESTED,
    val fecRedundancy: FecRedundancyLevel = FecRedundancyLevel.NONE,
    val bulkTelemetryPacingMs: Long = 20L,
    val activeVoiceSpeakers: Int = 0,
    val packetsObservedPerSec: Int = 0,
    val lastEvaluationTimestampMs: Long = 0L
)

/**
 * Configuration options for the airtime congestion governor.
 */
@Serializable
data class CongestionGovernorConfig(
    val evaluationWindowMs: Long = 1000L,
    val cbrModerateThreshold: Float = 0.25f,
    val cbrHighThreshold: Float = 0.50f,
    val cbrCriticalThreshold: Float = 0.75f,
    val collisionHighThreshold: Float = 0.15f,
    val collisionCriticalThreshold: Float = 0.30f,
    val voiceLatencyBudgetMs: Long = 80L
)
