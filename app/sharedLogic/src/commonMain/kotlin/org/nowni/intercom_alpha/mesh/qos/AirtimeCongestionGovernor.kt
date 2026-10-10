package org.nowni.intercom_alpha.mesh.qos

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min

/**
 * Airtime transmission record within the sliding evaluation window.
 */
internal data class AirtimeRecord(
    val timestampMs: Long,
    val durationMicros: Long,
    val priority: QoSPriority,
    val isTransmit: Boolean
)

/**
 * Packet delivery report within the sliding evaluation window.
 */
internal data class PacketLossRecord(
    val timestampMs: Long,
    val lostPackets: Int,
    val expectedPackets: Int
)

/**
 * Pure Kotlin Multiplatform Real-Time Channel Airtime Congestion Governor & Adaptive FEC Shaper.
 *
 * Continuously evaluates BLE 2.4 GHz medium utilization and packet collision rates:
 * 1. Computes sliding-window Channel Busy Ratio (CBR) and estimated packet loss ratio.
 * 2. Dynamically classifies congestion severity (`UNCONGESTED`, `MODERATE`, `HIGH`, `CRITICAL`).
 * 3. Adapts Forward Error Correction (FEC) redundancy levels on audio packets to maintain voice intelligibility.
 * 4. Paces or suspends non-voice bulk telemetry queues (GPS beacons, thumbnail chunks) to keep voice latency <= 80ms.
 * 5. Strict adherence to Project Architectural Rules 2 & 3 (100% pure KMP, zero Java/C dependencies).
 *
 * @param config Governor configuration parameters.
 */
class AirtimeCongestionGovernor(
    val config: CongestionGovernorConfig = CongestionGovernorConfig()
) {
    private val mutex = Mutex()

    private val airtimeRecords = mutableListOf<AirtimeRecord>()
    private val lossRecords = mutableListOf<PacketLossRecord>()
    private val activeSpeakersMap = mutableMapOf<String, Long>()

    private val _metrics = MutableStateFlow(CongestionMetricsSnapshot())
    val metrics: StateFlow<CongestionMetricsSnapshot> = _metrics.asStateFlow()

    /**
     * Records an airtime event (packet transmission or reception on the BLE GATT channel).
     *
     * @param durationMicros Estimated microsecond airtime duration of the packet on the physical link.
     * @param isTransmit True for local transmission, false for incoming received packet.
     * @param priority Packet QoS priority class.
     * @param timestampMs Epoch timestamp in milliseconds.
     */
    suspend fun recordAirtimeEvent(
        durationMicros: Long,
        isTransmit: Boolean,
        priority: QoSPriority,
        timestampMs: Long
    ) = mutex.withLock {
        airtimeRecords.add(
            AirtimeRecord(
                timestampMs = timestampMs,
                durationMicros = max(100L, durationMicros),
                priority = priority,
                isTransmit = isTransmit
            )
        )
        pruneAndEvaluateInternal(timestampMs)
    }

    /**
     * Records observed packet loss or collision metrics from receiver sequence tracking or NACKs.
     *
     * @param lostPackets Number of dropped or unacknowledged packets.
     * @param expectedPackets Total expected packets in the reported sequence range.
     * @param timestampMs Epoch timestamp in milliseconds.
     */
    suspend fun recordPacketLoss(
        lostPackets: Int,
        expectedPackets: Int,
        timestampMs: Long
    ) = mutex.withLock {
        if (expectedPackets > 0) {
            lossRecords.add(
                PacketLossRecord(
                    timestampMs = timestampMs,
                    lostPackets = max(0, lostPackets),
                    expectedPackets = expectedPackets
                )
            )
            pruneAndEvaluateInternal(timestampMs)
        }
    }

    /**
     * Notes that a speaker node is actively transmitting voice frames.
     */
    suspend fun recordActiveVoiceSpeaker(speakerId: String, timestampMs: Long) = mutex.withLock {
        activeSpeakersMap[speakerId] = timestampMs
        pruneAndEvaluateInternal(timestampMs)
    }

    /**
     * Checks if a packet of the given [priority] should be throttled based on congestion pacing.
     *
     * @param priority Packet priority class.
     * @param lastSentTimestampMs Epoch timestamp of the last transmission from this priority queue.
     * @param currentTimestampMs Current epoch timestamp.
     * @return True if transmission must be deferred/throttled; false if admitted immediately.
     */
    fun shouldThrottle(
        priority: QoSPriority,
        lastSentTimestampMs: Long,
        currentTimestampMs: Long
    ): Boolean {
        // Critical emergency and conversational voice frames are never throttled
        if (priority == QoSPriority.EMERGENCY_CRITICAL || priority == QoSPriority.ACTIVE_VOICE) {
            return false
        }

        val snapshot = _metrics.value
        val intervalMs = currentTimestampMs - lastSentTimestampMs

        return when (priority) {
            QoSPriority.EMERGENCY_CRITICAL, QoSPriority.ACTIVE_VOICE -> false
            QoSPriority.ROUTING_CONTROL -> {
                if (snapshot.congestionLevel == ChannelCongestionLevel.CRITICAL) {
                    intervalMs < 250L
                } else {
                    false
                }
            }
            QoSPriority.BULK_TELEMETRY -> {
                if (snapshot.congestionLevel == ChannelCongestionLevel.CRITICAL) {
                    true // Complete suspension under critical saturation
                } else {
                    intervalMs < snapshot.bulkTelemetryPacingMs
                }
            }
        }
    }

    /**
     * Resets all internal event queues and metrics.
     */
    suspend fun reset() = mutex.withLock {
        airtimeRecords.clear(
        )
        lossRecords.clear()
        activeSpeakersMap.clear()
        _metrics.value = CongestionMetricsSnapshot()
    }

    /**
     * Internal pruning and state calculation.
     */
    private fun pruneAndEvaluateInternal(currentTimeMs: Long) {
        val cutoffMs = currentTimeMs - config.evaluationWindowMs

        // 1. Prune airtime events
        airtimeRecords.removeAll { it.timestampMs < cutoffMs }

        // 2. Prune loss records
        lossRecords.removeAll { it.timestampMs < cutoffMs }

        // 3. Prune active speakers (active if seen within last 600ms)
        val speakerCutoff = currentTimeMs - 600L
        activeSpeakersMap.entries.removeAll { it.value < speakerCutoff }

        // 4. Calculate Channel Busy Ratio (CBR)
        var totalAirtimeMicros = 0L
        for (rec in airtimeRecords) {
            totalAirtimeMicros += rec.durationMicros
        }
        val windowMicros = config.evaluationWindowMs * 1000.0f
        val rawCbr = if (windowMicros > 0) (totalAirtimeMicros / windowMicros) else 0.0f
        val clampedCbr = min(1.0f, max(0.0f, rawCbr))

        // 5. Calculate Packet Loss / Collision Rate
        var totalLost = 0
        var totalExpected = 0
        for (loss in lossRecords) {
            totalLost += loss.lostPackets
            totalExpected += loss.expectedPackets
        }
        val collisionRate = if (totalExpected > 0) {
            min(1.0f, totalLost.toFloat() / totalExpected.toFloat())
        } else {
            0.0f
        }

        // 6. Determine Congestion Level
        val level = when {
            clampedCbr >= config.cbrCriticalThreshold || collisionRate >= config.collisionCriticalThreshold -> {
                ChannelCongestionLevel.CRITICAL
            }
            clampedCbr >= config.cbrHighThreshold || collisionRate >= config.collisionHighThreshold -> {
                ChannelCongestionLevel.HIGH
            }
            clampedCbr >= config.cbrModerateThreshold -> {
                ChannelCongestionLevel.MODERATE
            }
            else -> {
                ChannelCongestionLevel.UNCONGESTED
            }
        }

        // 7. Determine Adaptive FEC Redundancy
        val fec = when (level) {
            ChannelCongestionLevel.CRITICAL -> FecRedundancyLevel.HEAVY
            ChannelCongestionLevel.HIGH -> FecRedundancyLevel.MEDIUM
            ChannelCongestionLevel.MODERATE -> FecRedundancyLevel.LIGHT
            ChannelCongestionLevel.UNCONGESTED -> FecRedundancyLevel.NONE
        }

        // 8. Determine Bulk Telemetry Pacing
        val bulkPacingMs = when (level) {
            ChannelCongestionLevel.UNCONGESTED -> 20L
            ChannelCongestionLevel.MODERATE -> 120L
            ChannelCongestionLevel.HIGH -> 600L
            ChannelCongestionLevel.CRITICAL -> Long.MAX_VALUE
        }

        val observedPackets = airtimeRecords.size

        _metrics.value = CongestionMetricsSnapshot(
            channelBusyRatio = clampedCbr,
            estimatedCollisionRate = collisionRate,
            congestionLevel = level,
            fecRedundancy = fec,
            bulkTelemetryPacingMs = bulkPacingMs,
            activeVoiceSpeakers = activeSpeakersMap.size,
            packetsObservedPerSec = observedPackets,
            lastEvaluationTimestampMs = currentTimeMs
        )
    }
}
