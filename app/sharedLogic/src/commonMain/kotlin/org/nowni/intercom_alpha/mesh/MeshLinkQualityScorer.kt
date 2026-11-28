package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Categorical grade derived from the numerical composite [Q_link] score (0..100).
 */
@Serializable
enum class LinkQualityGrade(val minScore: Int, val description: String) {
    EXCELLENT(85, "Pristine RF link (High 48 kbps voice)"),
    GOOD(70, "Stable RF link (Standard 32 kbps voice)"),
    FAIR(50, "Moderate fading / noise (Low 24 kbps voice)"),
    POOR(30, "Severe packet loss (Ultra-Low 16 kbps voice)"),
    CRITICAL(0, "Near link collapse / dropout risk");

    companion object {
        fun fromScore(score: Int): LinkQualityGrade = when {
            score >= EXCELLENT.minScore -> EXCELLENT
            score >= GOOD.minScore -> GOOD
            score >= FAIR.minScore -> FAIR
            score >= POOR.minScore -> POOR
            else -> CRITICAL
        }
    }
}

/**
 * Detailed breakdown of metrics contributing to a peer's composite [Q_link] score.
 */
@Serializable
data class PeerLinkScore(
    val peerId: String,
    val score: Int,
    val grade: LinkQualityGrade,
    val smoothedRssiDbm: Double,
    val linkMarginDb: Double,
    val pdr: Double,
    val jitterMs: Double,
    val etx: Double,
    val packetsReceived: Long,
    val packetsLost: Long,
    val lastSeenTimestampMs: Long
)

/**
 * Cluster-wide aggregated link health telemetry.
 */
@Serializable
data class ClusterLinkHealth(
    val peerCount: Int,
    val averageScore: Int,
    val worstScore: Int,
    val bestScore: Int,
    val overallGrade: LinkQualityGrade,
    val peerScores: List<PeerLinkScore>
)

/**
 * Real-Time Mesh Link Quality Scorer & EWMA Loss Estimator.
 *
 * Implements a closed-loop multi-metric scoring engine calculating composite [Q_link] in [0, 100]:
 * - Link Margin ($M = \text{RSSI}_{\text{smoothed}} - S_{\text{rx}}$ relative to $-93\text{ dBm}$, weight 35%)
 * - Packet Delivery Ratio (PDR over sequence intervals, weight 40%)
 * - Jitter Variance (RFC 3550 inter-arrival jitter, weight 15%)
 * - ETX Path Cost (Expected Transmission Count / Multi-Hop efficiency, weight 10%)
 *
 * Fully compliant with Rules 2 & 3: 100% pure Kotlin Multiplatform with zero Java/C dependencies.
 */
class MeshLinkQualityScorer(
    val receiverSensitivityFloorDbm: Double = -93.0,
    val rssiEwmaAlpha: Double = 0.20,
    val maxLinkMarginDb: Double = 30.0,
    val peerTimeoutMs: Long = 15_000L
) {
    private val mutex = Mutex()

    private class PeerTracker(
        val peerId: String,
        var smoothedRssi: Double = -75.0,
        var lastSequence: Long = -1L,
        var totalPacketsReceived: Long = 0L,
        var totalPacketsLost: Long = 0L,
        var lastPacketTransitTimeMs: Long = 0L,
        var jitterMs: Double = 5.0,
        var etx: Double = 1.0,
        var lastSeenTimestampMs: Long = 0L
    ) {
        fun calculatePdr(): Double {
            val totalExpected = totalPacketsReceived + totalPacketsLost
            if (totalExpected <= 0L) return 1.0
            return (totalPacketsReceived.toDouble() / totalExpected.toDouble()).coerceIn(0.0, 1.0)
        }
    }

    private val peerTrackers = mutableMapOf<String, PeerTracker>()

    private val _clusterHealth = MutableStateFlow(
        ClusterLinkHealth(
            peerCount = 0,
            averageScore = 100,
            worstScore = 100,
            bestScore = 100,
            overallGrade = LinkQualityGrade.EXCELLENT,
            peerScores = emptyList()
        )
    )
    val clusterHealth: StateFlow<ClusterLinkHealth> = _clusterHealth.asStateFlow()

    /**
     * Ingests an incoming packet telemetry observation from a specific peer.
     *
     * @param peerId Unique peer identifier.
     * @param sequenceNumber Monotonically increasing sequence number from sender.
     * @param rawRssiDbm Instantaneous measured RSSI in dBm.
     * @param transitTimeMs Measured one-way or round-trip delay in milliseconds.
     * @param hopCount Number of relay hops traversed (1 = direct).
     * @param timestampMs Current local system timestamp in milliseconds.
     * @return The updated [PeerLinkScore] for this peer.
     */
    suspend fun recordPacket(
        peerId: String,
        sequenceNumber: Long,
        rawRssiDbm: Int,
        transitTimeMs: Long = 10L,
        hopCount: Int = 1,
        timestampMs: Long
    ): PeerLinkScore = mutex.withLock {
        val tracker = peerTrackers.getOrPut(peerId) {
            PeerTracker(
                peerId = peerId,
                smoothedRssi = rawRssiDbm.toDouble(),
                lastSequence = sequenceNumber,
                lastSeenTimestampMs = timestampMs
            )
        }

        // 1. Loss detection via sequence number progression
        if (tracker.lastSequence >= 0L && sequenceNumber > tracker.lastSequence) {
            val gap = sequenceNumber - tracker.lastSequence - 1L
            if (gap in 1..200) {
                tracker.totalPacketsLost += gap
            }
        }
        if (sequenceNumber > tracker.lastSequence || tracker.lastSequence < 0L) {
            tracker.lastSequence = sequenceNumber
        }
        tracker.totalPacketsReceived++
        tracker.lastSeenTimestampMs = timestampMs

        // 2. EWMA RSSI smoothing
        tracker.smoothedRssi = (rssiEwmaAlpha * rawRssiDbm.toDouble()) + ((1.0 - rssiEwmaAlpha) * tracker.smoothedRssi)

        // 3. RFC 3550 Jitter Estimation: J(i) = J(i-1) + (|D(i-1, i)| - J(i-1)) / 16
        if (tracker.lastPacketTransitTimeMs > 0L) {
            val delta = kotlin.math.abs(transitTimeMs - tracker.lastPacketTransitTimeMs).toDouble()
            tracker.jitterMs += (delta - tracker.jitterMs) / 16.0
        }
        tracker.lastPacketTransitTimeMs = transitTimeMs

        // 4. Dynamic ETX approximation based on hop count and delivery ratio
        val currentPdr = tracker.calculatePdr().coerceAtLeast(0.1)
        tracker.etx = (hopCount.toDouble() / currentPdr).coerceIn(1.0, 10.0)

        // 5. Compute composite score and publish
        val score = computePeerScore(tracker)
        publishClusterHealth(timestampMs)
        score
    }

    /**
     * Explicitly records dropped packets (e.g. from missing sequence bursts or ACK timeouts).
     */
    suspend fun recordLoss(
        peerId: String,
        lostCount: Long,
        timestampMs: Long
    ): PeerLinkScore = mutex.withLock {
        val tracker = peerTrackers.getOrPut(peerId) {
            PeerTracker(
                peerId = peerId,
                lastSeenTimestampMs = timestampMs
            )
        }
        tracker.totalPacketsLost += max(1L, lostCount)
        tracker.lastSeenTimestampMs = timestampMs

        val score = computePeerScore(tracker)
        publishClusterHealth(timestampMs)
        score
    }

    /**
     * Calculates composite Q_link in [0, 100] for a given tracker.
     */
    private fun computePeerScore(tracker: PeerTracker): PeerLinkScore {
        // Factor 1: Link Margin Factor (Weight: 35%)
        val linkMarginDb = tracker.smoothedRssi - receiverSensitivityFloorDbm
        val factorMargin = when {
            linkMarginDb <= 0.0 -> 0.0
            linkMarginDb >= maxLinkMarginDb -> 100.0
            else -> (linkMarginDb / maxLinkMarginDb) * 100.0
        }

        // Factor 2: Packet Delivery Ratio (Weight: 40%)
        val pdr = tracker.calculatePdr()
        val factorPdr = pdr * 100.0

        // Factor 3: Jitter Factor (Weight: 15%)
        // <=10ms -> 100, >=100ms -> 0
        val factorJitter = when {
            tracker.jitterMs <= 10.0 -> 100.0
            tracker.jitterMs >= 100.0 -> 0.0
            else -> 100.0 - ((tracker.jitterMs - 10.0) / 90.0) * 100.0
        }

        // Factor 4: ETX / Hop Factor (Weight: 10%)
        // ETX = 1.0 -> 100, ETX >= 4.0 -> 0
        val factorEtx = when {
            tracker.etx <= 1.0 -> 100.0
            tracker.etx >= 4.0 -> 0.0
            else -> 100.0 - ((tracker.etx - 1.0) / 3.0) * 100.0
        }

        val rawScore = (
            (0.35 * factorMargin) +
            (0.40 * factorPdr) +
            (0.15 * factorJitter) +
            (0.10 * factorEtx)
        )

        // RF sanity bound: severe packet loss hard-caps maximum achievable link health
        val pdrCap = when {
            pdr < 0.30 -> 28.0
            pdr < 0.60 -> 48.0
            pdr < 0.80 -> 72.0
            else -> 100.0
        }
        val compositeScore = min(rawScore, pdrCap).roundToInt().coerceIn(0, 100)

        return PeerLinkScore(
            peerId = tracker.peerId,
            score = compositeScore,
            grade = LinkQualityGrade.fromScore(compositeScore),
            smoothedRssiDbm = tracker.smoothedRssi,
            linkMarginDb = linkMarginDb,
            pdr = pdr,
            jitterMs = tracker.jitterMs,
            etx = tracker.etx,
            packetsReceived = tracker.totalPacketsReceived,
            packetsLost = tracker.totalPacketsLost,
            lastSeenTimestampMs = tracker.lastSeenTimestampMs
        )
    }

    /**
     * Periodically prunes stale peers and updates the reactive cluster telemetry.
     */
    suspend fun pruneStalePeers(currentTimeMs: Long) = mutex.withLock {
        peerTrackers.entries.removeAll { (_, tracker) ->
            currentTimeMs - tracker.lastSeenTimestampMs > peerTimeoutMs
        }
        publishClusterHealth(currentTimeMs)
    }

    /**
     * Retrieves current score for a specific peer.
     */
    suspend fun getPeerScore(peerId: String): PeerLinkScore? = mutex.withLock {
        peerTrackers[peerId]?.let { computePeerScore(it) }
    }

    /**
     * Resets all peer trackers.
     */
    suspend fun reset() = mutex.withLock {
        peerTrackers.clear()
        _clusterHealth.value = ClusterLinkHealth(
            peerCount = 0,
            averageScore = 100,
            worstScore = 100,
            bestScore = 100,
            overallGrade = LinkQualityGrade.EXCELLENT,
            peerScores = emptyList()
        )
    }

    private fun publishClusterHealth(timestampMs: Long) {
        val activePeers = peerTrackers.values.filter {
            timestampMs - it.lastSeenTimestampMs <= peerTimeoutMs
        }

        if (activePeers.isEmpty()) {
            _clusterHealth.value = ClusterLinkHealth(
                peerCount = 0,
                averageScore = 100,
                worstScore = 100,
                bestScore = 100,
                overallGrade = LinkQualityGrade.EXCELLENT,
                peerScores = emptyList()
            )
            return
        }

        val peerScores = activePeers.map { computePeerScore(it) }
        val avgScore = (peerScores.sumOf { it.score }.toDouble() / peerScores.size.toDouble()).roundToInt()
        val worstScore = peerScores.minOf { it.score }
        val bestScore = peerScores.maxOf { it.score }

        _clusterHealth.value = ClusterLinkHealth(
            peerCount = peerScores.size,
            averageScore = avgScore,
            worstScore = worstScore,
            bestScore = bestScore,
            overallGrade = LinkQualityGrade.fromScore(avgScore),
            peerScores = peerScores
        )
    }
}
