package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.nowni.intercom_alpha.currentTimeMillis
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/**
 * Qualitative health classification of a peer mesh radio link.
 */
@Serializable
enum class LinkQuality {
    /** Optimal link: PDR >= 95% and Link Margin >= 15 dB */
    EXCELLENT,
    /** Stable link: PDR >= 85% and Link Margin >= 10 dB */
    GOOD,
    /** Usable link under fading/interference: PDR >= 70% and Link Margin >= 3 dB */
    MARGINAL,
    /** Critical packet loss or fading: PDR < 70% or Link Margin < 3 dB */
    CRITICAL,
    /** Peer has not been heard from within the timeout threshold */
    LOST
}

/**
 * Diagnostic statistics aggregated for a specific mesh peer.
 */
@Serializable
data class PeerStats(
    val peerId: String,
    val packetsReceived: Long,
    val packetsExpected: Long,
    val packetsLost: Long,
    val duplicatesFiltered: Long,
    val currentRssi: Int,
    val minRssi: Int,
    val maxRssi: Int,
    val smoothedRssi: Double,
    val pdr: Double,
    val lastHopCount: Int,
    val hopDistribution: Map<Int, Long>,
    val minLatencyMs: Long,
    val maxLatencyMs: Long,
    val avgLatencyMs: Double,
    val jitterMs: Double,
    val estimatedDistanceMeters: Double,
    val linkMarginDb: Double,
    val linkQuality: LinkQuality,
    val lastSeenTimestamp: Long
)

/**
 * Complete snapshot report of all mesh links across the network.
 */
@Serializable
data class MeshDiagnosticsReport(
    val timestamp: Long,
    val activePeerCount: Int,
    val totalPacketsReceived: Long,
    val totalPacketsLost: Long,
    val totalDuplicatesFiltered: Long,
    val overallPdr: Double,
    val avgHopCount: Double,
    val avgLinkMarginDb: Double,
    val peerStats: List<PeerStats>
) {
    /**
     * Renders a human-readable markdown table summarizing current network telemetry.
     */
    fun toMarkdownSummary(): String {
        val sb = StringBuilder()
        sb.appendLine("### 📡 Mesh Network Diagnostics Report (${timestamp})")
        sb.appendLine("- **Active Peers:** $activePeerCount")
        sb.appendLine("- **Total Received:** $totalPacketsReceived | **Lost:** $totalPacketsLost | **Duplicates Filtered:** $totalDuplicatesFiltered")
        val pdrPct = round(overallPdr * 1000.0) / 10.0
        val marginFormatted = round(avgLinkMarginDb * 10.0) / 10.0
        val hopsFormatted = round(avgHopCount * 100.0) / 100.0
        sb.appendLine("- **Overall PDR:** $pdrPct% | **Avg Hop Count:** $hopsFormatted | **Avg Link Margin:** ${marginFormatted} dB")
        sb.appendLine()
        sb.appendLine("| Peer ID | Quality | PDR (%) | RSSI (dBm) | Margin (dB) | Est. Dist (m) | Hops (1/2/3) | Latency (ms) |")
        sb.appendLine("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |")

        for (peer in peerStats) {
            val peerPdr = round(peer.pdr * 1000.0) / 10.0
            val peerDist = round(peer.estimatedDistanceMeters * 10.0) / 10.0
            val peerMargin = round(peer.linkMarginDb * 10.0) / 10.0
            val h1 = peer.hopDistribution[1] ?: 0L
            val h2 = peer.hopDistribution[2] ?: 0L
            val h3 = peer.hopDistribution[3] ?: 0L
            val lat = if (peer.avgLatencyMs > 0) "${round(peer.avgLatencyMs)} ms" else "N/A"

            sb.appendLine("| `${peer.peerId}` | ${peer.linkQuality} | $peerPdr% | ${peer.currentRssi} | ${peerMargin} dB | ~${peerDist}m | $h1/$h2/$h3 | $lat |")
        }

        return sb.toString()
    }
}

/**
 * Real-time diagnostic telemetry engine and field range benchmark harness.
 *
 * Tracks RF link health, packet delivery ratios (PDR), multi-hop relay distribution,
 * and estimates physical distance using the Log-Distance Path Loss model.
 *
 * @param referenceRssiAtOneMeter RSSI measured at 1 meter distance in open air (dBm). Default: -59 dBm (Class 2 BLE).
 * @param pathLossExponent Environment path loss exponent (n). 2.0 = free space, 2.5-3.0 = open road/trail, 3.5 = obstructed.
 * @param receiverSensitivityDb Sensitivity threshold of receiver (dBm). Default: -93 dBm.
 * @param peerTimeoutMs Time without packets before marking link as LOST. Default: 10,000 ms.
 */
class MeshDiagnostics(
    val referenceRssiAtOneMeter: Double = -59.0,
    val pathLossExponent: Double = 2.5,
    val receiverSensitivityDb: Double = -93.0,
    val peerTimeoutMs: Long = 10_000L
) {
    private val mutex = Mutex()
    private val peerTrackers = mutableMapOf<String, PeerTracker>()
    private var totalDuplicatesFilteredCount = 0L

    private class PeerTracker(
        val peerId: String,
        var packetsReceived: Long = 0L,
        var maxSequenceSeen: Long = -1L,
        var totalPacketsLost: Long = 0L,
        var duplicatesFiltered: Long = 0L,
        var currentRssi: Int = -70,
        var minRssi: Int = Int.MAX_VALUE,
        var maxRssi: Int = Int.MIN_VALUE,
        var smoothedRssi: Double = -70.0,
        var lastHopCount: Int = 1,
        val hopDistribution: MutableMap<Int, Long> = mutableMapOf(),
        var minLatencyMs: Long = Long.MAX_VALUE,
        var maxLatencyMs: Long = 0L,
        var totalLatencySum: Long = 0L,
        var latencySampleCount: Long = 0L,
        var lastTransitLatency: Long = 0L,
        var jitterEstimateMs: Double = 0.0,
        var lastSeenTimestamp: Long = 0L
    )

    /**
     * Records arrival of a packet from [senderId].
     *
     * @param senderId Identifier of originating node.
     * @param sequence Monotonically increasing packet sequence number.
     * @param hopCount Number of hops traversed (1 = direct neighbor, 2 = 1 relay hop, 3 = 2 relay hops).
     * @param rssi Measured RF signal strength in dBm, if available.
     * @param arrivalTimeMs Local reception timestamp in milliseconds.
     * @param transitLatencyMs Estimated one-way transit delay, if available.
     */
    suspend fun recordPacketReceived(
        senderId: String,
        sequence: Long,
        hopCount: Int,
        rssi: Int? = null,
        arrivalTimeMs: Long = currentTimeMillis(),
        transitLatencyMs: Long? = null
    ) {
        mutex.withLock {
            val tracker = peerTrackers.getOrPut(senderId) {
                PeerTracker(
                    peerId = senderId,
                    currentRssi = rssi ?: -70,
                    smoothedRssi = (rssi ?: -70).toDouble(),
                    lastSeenTimestamp = arrivalTimeMs
                )
            }

            tracker.packetsReceived++
            tracker.lastSeenTimestamp = arrivalTimeMs
            tracker.lastHopCount = max(1, hopCount)
            tracker.hopDistribution[tracker.lastHopCount] =
                (tracker.hopDistribution[tracker.lastHopCount] ?: 0L) + 1L

            // Sequence and loss tracking
            if (tracker.maxSequenceSeen >= 0L) {
                val gap = sequence - tracker.maxSequenceSeen
                if (gap > 1L) {
                    val lostCount = gap - 1L
                    tracker.totalPacketsLost += lostCount
                }
                if (sequence > tracker.maxSequenceSeen) {
                    tracker.maxSequenceSeen = sequence
                }
            } else {
                tracker.maxSequenceSeen = sequence
            }

            // RSSI tracking with Exponential Weighted Moving Average (alpha = 0.2)
            if (rssi != null) {
                tracker.currentRssi = rssi
                tracker.minRssi = min(tracker.minRssi, rssi)
                tracker.maxRssi = max(tracker.maxRssi, rssi)
                tracker.smoothedRssi = 0.8 * tracker.smoothedRssi + 0.2 * rssi.toDouble()
            }

            // Latency & Jitter calculation (RFC 3550)
            if (transitLatencyMs != null && transitLatencyMs >= 0L) {
                tracker.minLatencyMs = min(tracker.minLatencyMs, transitLatencyMs)
                tracker.maxLatencyMs = max(tracker.maxLatencyMs, transitLatencyMs)
                tracker.totalLatencySum += transitLatencyMs
                tracker.latencySampleCount++

                if (tracker.lastTransitLatency > 0L) {
                    val deviation = abs(transitLatencyMs - tracker.lastTransitLatency).toDouble()
                    tracker.jitterEstimateMs += (deviation - tracker.jitterEstimateMs) / 16.0
                }
                tracker.lastTransitLatency = transitLatencyMs
            }
        }
    }

    /**
     * Records a packet explicitly known to be lost/dropped.
     */
    suspend fun recordPacketDropped(senderId: String, count: Long = 1L) {
        mutex.withLock {
            val tracker = peerTrackers.getOrPut(senderId) {
                PeerTracker(peerId = senderId, lastSeenTimestamp = currentTimeMillis())
            }
            tracker.totalPacketsLost += count
        }
    }

    /**
     * Records a duplicate packet intercepted and dropped by loop suppression.
     */
    suspend fun recordDuplicateFiltered(senderId: String) {
        mutex.withLock {
            totalDuplicatesFilteredCount++
            val tracker = peerTrackers.getOrPut(senderId) {
                PeerTracker(peerId = senderId, lastSeenTimestamp = currentTimeMillis())
            }
            tracker.duplicatesFiltered++
        }
    }

    /**
     * Updates RSSI from a periodic BLE beacon or connection poll.
     */
    suspend fun recordRssiUpdate(peerId: String, rssi: Int, now: Long = currentTimeMillis()) {
        mutex.withLock {
            val tracker = peerTrackers.getOrPut(peerId) {
                PeerTracker(
                    peerId = peerId,
                    currentRssi = rssi,
                    smoothedRssi = rssi.toDouble(),
                    lastSeenTimestamp = now
                )
            }
            tracker.currentRssi = rssi
            tracker.minRssi = min(tracker.minRssi, rssi)
            tracker.maxRssi = max(tracker.maxRssi, rssi)
            tracker.smoothedRssi = 0.8 * tracker.smoothedRssi + 0.2 * rssi.toDouble()
            tracker.lastSeenTimestamp = now
        }
    }

    /**
     * Computes snapshot statistics for a specific peer.
     */
    suspend fun getPeerStats(peerId: String, now: Long = currentTimeMillis()): PeerStats? {
        return mutex.withLock {
            val tracker = peerTrackers[peerId] ?: return@withLock null
            buildPeerStats(tracker, now)
        }
    }

    /**
     * Computes snapshot statistics for all tracked peers.
     */
    suspend fun getAllPeerStats(now: Long = currentTimeMillis()): List<PeerStats> {
        return mutex.withLock {
            peerTrackers.values.map { buildPeerStats(it, now) }
        }
    }

    /**
     * Generates a complete network health report.
     */
    suspend fun generateReport(now: Long = currentTimeMillis()): MeshDiagnosticsReport {
        return mutex.withLock {
            val peerStatsList = peerTrackers.values.map { buildPeerStats(it, now) }
            val activePeers = peerStatsList.filter { it.linkQuality != LinkQuality.LOST }

            var totalRecv = 0L
            var totalLost = 0L
            var totalHopsWeighted = 0.0
            var totalMarginSum = 0.0

            for (p in peerStatsList) {
                totalRecv += p.packetsReceived
                totalLost += p.packetsLost
                totalMarginSum += p.linkMarginDb

                var peerHopsSum = 0L
                var peerHopsCount = 0L
                p.hopDistribution.forEach { (hop, count) ->
                    peerHopsSum += hop * count
                    peerHopsCount += count
                }
                if (peerHopsCount > 0L) {
                    totalHopsWeighted += peerHopsSum.toDouble() / peerHopsCount.toDouble()
                } else {
                    totalHopsWeighted += 1.0
                }
            }

            val overallExpected = totalRecv + totalLost
            val overallPdr = if (overallExpected > 0L) totalRecv.toDouble() / overallExpected.toDouble() else 1.0
            val avgHops = if (peerStatsList.isNotEmpty()) totalHopsWeighted / peerStatsList.size else 1.0
            val avgMargin = if (peerStatsList.isNotEmpty()) totalMarginSum / peerStatsList.size else 0.0

            MeshDiagnosticsReport(
                timestamp = now,
                activePeerCount = activePeers.size,
                totalPacketsReceived = totalRecv,
                totalPacketsLost = totalLost,
                totalDuplicatesFiltered = totalDuplicatesFilteredCount,
                overallPdr = overallPdr,
                avgHopCount = avgHops,
                avgLinkMarginDb = avgMargin,
                peerStats = peerStatsList
            )
        }
    }

    /**
     * Resets all accumulated counters and peer state.
     */
    suspend fun reset() {
        mutex.withLock {
            peerTrackers.clear()
            totalDuplicatesFilteredCount = 0L
        }
    }

    /**
     * Calculates estimated physical distance using the Log-Distance Path Loss model:
     *   d = 10 ^ ((A - RSSI) / (10 * n))
     */
    fun estimateDistance(rssi: Double): Double {
        if (rssi >= referenceRssiAtOneMeter) {
            return 1.0
        }
        val exponent = (referenceRssiAtOneMeter - rssi) / (10.0 * pathLossExponent)
        val rawDistance = 10.0.pow(exponent)
        return max(0.5, rawDistance)
    }

    /**
     * Computes link budget margin (dB) relative to receiver sensitivity.
     */
    fun calculateLinkMargin(rssi: Double): Double {
        return rssi - receiverSensitivityDb
    }

    private fun buildPeerStats(tracker: PeerTracker, now: Long): PeerStats {
        val totalExpected = tracker.packetsReceived + tracker.totalPacketsLost
        val pdr = if (totalExpected > 0L) {
            tracker.packetsReceived.toDouble() / totalExpected.toDouble()
        } else {
            1.0
        }

        val minRssiVal = if (tracker.minRssi == Int.MAX_VALUE) tracker.currentRssi else tracker.minRssi
        val maxRssiVal = if (tracker.maxRssi == Int.MIN_VALUE) tracker.currentRssi else tracker.maxRssi
        val linkMargin = calculateLinkMargin(tracker.smoothedRssi)
        val estimatedDist = estimateDistance(tracker.smoothedRssi)

        val avgLat = if (tracker.latencySampleCount > 0L) {
            tracker.totalLatencySum.toDouble() / tracker.latencySampleCount.toDouble()
        } else {
            0.0
        }
        val minLat = if (tracker.minLatencyMs == Long.MAX_VALUE) 0L else tracker.minLatencyMs

        // Determine Link Quality
        val isLost = (now - tracker.lastSeenTimestamp) > peerTimeoutMs
        val quality = when {
            isLost -> LinkQuality.LOST
            pdr >= 0.95 && linkMargin >= 15.0 -> LinkQuality.EXCELLENT
            pdr >= 0.85 && linkMargin >= 10.0 -> LinkQuality.GOOD
            pdr >= 0.70 && linkMargin >= 3.0 -> LinkQuality.MARGINAL
            else -> LinkQuality.CRITICAL
        }

        return PeerStats(
            peerId = tracker.peerId,
            packetsReceived = tracker.packetsReceived,
            packetsExpected = totalExpected,
            packetsLost = tracker.totalPacketsLost,
            duplicatesFiltered = tracker.duplicatesFiltered,
            currentRssi = tracker.currentRssi,
            minRssi = minRssiVal,
            maxRssi = maxRssiVal,
            smoothedRssi = tracker.smoothedRssi,
            pdr = pdr,
            lastHopCount = tracker.lastHopCount,
            hopDistribution = tracker.hopDistribution.toMap(),
            minLatencyMs = minLat,
            maxLatencyMs = tracker.maxLatencyMs,
            avgLatencyMs = avgLat,
            jitterMs = tracker.jitterEstimateMs,
            estimatedDistanceMeters = estimatedDist,
            linkMarginDb = linkMargin,
            linkQuality = quality,
            lastSeenTimestamp = tracker.lastSeenTimestamp
        )
    }
}
