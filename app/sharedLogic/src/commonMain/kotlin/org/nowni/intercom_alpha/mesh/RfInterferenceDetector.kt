package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.nowni.intercom_alpha.currentTimeMillis
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Qualitative classification of 2.4 GHz RF spectrum interference and packet collision density.
 */
@Serializable
enum class InterferenceLevel {
    /** Clean RF environment; low noise floor and minimal packet collisions. */
    LOW,

    /** Moderate interference (suburban Wi-Fi / multiple Bluetooth peripherals). */
    MODERATE,

    /** Severe interference (urban Wi-Fi saturation, microwave leakage, dense BLE collisions). */
    SEVERE
}

/**
 * Adaptive transmission and reception policies recommended based on real-time RF interference analysis.
 */
@Serializable
data class RfPolicyAdvice(
    val timestamp: Long,
    val interferenceLevel: InterferenceLevel,
    val recommendedRedundancyCopies: Int,
    val recommendedScanIntervalMs: Long,
    val recommendedBackoffMs: Long,
    val pdr: Double,
    val rssiVariance: Double
)

/**
 * Adaptive RF Interference and Spectrum Health Detector.
 *
 * Continuously evaluates packet delivery ratio (PDR) stability and RSSI standard deviation
 * across a sliding observation window to detect channel fading, collisions, and interference.
 *
 * Produces actionable [RfPolicyAdvice] to dynamically scale BLE packet redundancy and random backoff windows.
 *
 * @param windowCapacity Maximum number of packet observations retained in circular memory. Default: 100.
 * @param sampleRetentionMs Maximum age in milliseconds for valid RF samples. Default: 15,000ms.
 */
class RfInterferenceDetector(
    val windowCapacity: Int = 100,
    val sampleRetentionMs: Long = 15_000L
) {
    private val mutex = Mutex()

    private class PacketSample(
        val timestamp: Long,
        val wasReceived: Boolean,
        val rssi: Int?
    )

    private val samples = mutableListOf<PacketSample>()

    private val _policyFlow = MutableStateFlow(
        RfPolicyAdvice(
            timestamp = currentTimeMillis(),
            interferenceLevel = InterferenceLevel.LOW,
            recommendedRedundancyCopies = 1,
            recommendedScanIntervalMs = 100L,
            recommendedBackoffMs = 0L,
            pdr = 1.0,
            rssiVariance = 0.0
        )
    )

    /**
     * Reactive StateFlow stream of active RF policies observed by the mesh transport layer.
     */
    val policyFlow: StateFlow<RfPolicyAdvice> = _policyFlow.asStateFlow()

    /**
     * Records the transmission outcome of an individual packet.
     */
    suspend fun recordPacketOutcome(
        wasReceived: Boolean,
        rssi: Int? = null,
        timestamp: Long = currentTimeMillis()
    ) {
        mutex.withLock {
            addSampleLocked(wasReceived, rssi, timestamp)
            evaluatePolicyLocked(timestamp)
        }
    }

    /**
     * Records a batch of packet outcomes (e.g. from telemetry or multi-packet bursts).
     */
    suspend fun recordBatchOutcomes(
        receivedCount: Int,
        lostCount: Int,
        rssiSamples: List<Int> = emptyList(),
        timestamp: Long = currentTimeMillis()
    ) {
        mutex.withLock {
            val rssiIterator = rssiSamples.iterator()
            for (i in 0 until receivedCount) {
                val rssi = if (rssiIterator.hasNext()) rssiIterator.next() else null
                addSampleLocked(true, rssi, timestamp)
            }
            for (i in 0 until lostCount) {
                addSampleLocked(false, null, timestamp)
            }
            evaluatePolicyLocked(timestamp)
        }
    }

    /**
     * Clears all accumulated RF samples.
     */
    suspend fun reset(timestamp: Long = currentTimeMillis()) {
        mutex.withLock {
            samples.clear()
            _policyFlow.value = RfPolicyAdvice(
                timestamp = timestamp,
                interferenceLevel = InterferenceLevel.LOW,
                recommendedRedundancyCopies = 1,
                recommendedScanIntervalMs = 100L,
                recommendedBackoffMs = 0L,
                pdr = 1.0,
                rssiVariance = 0.0
            )
        }
    }

    private fun addSampleLocked(wasReceived: Boolean, rssi: Int?, now: Long) {
        // Purge expired samples
        val cutoff = now - sampleRetentionMs
        samples.removeAll { it.timestamp < cutoff }

        // Enforce maximum capacity
        if (samples.size >= windowCapacity) {
            samples.removeAt(0)
        }

        samples.add(PacketSample(now, wasReceived, rssi))
    }

    private fun evaluatePolicyLocked(now: Long) {
        if (samples.isEmpty()) {
            return
        }

        val total = samples.size
        val received = samples.count { it.wasReceived }
        val pdr = received.toDouble() / total.toDouble()

        // Calculate RSSI standard deviation
        val rssiValues = samples.mapNotNull { it.rssi }
        val rssiStdDev = if (rssiValues.size >= 3) {
            val mean = rssiValues.average()
            val sumSquaredDiff = rssiValues.sumOf { (it - mean) * (it - mean) }
            val variance = sumSquaredDiff / rssiValues.size.toDouble()
            round(sqrt(variance) * 10.0) / 10.0
        } else {
            0.0
        }

        // Determine interference level:
        // High packet loss (PDR < 0.70) or rapid RSSI flutter (> 7.0 dB std dev) indicates severe congestion
        val level = when {
            pdr < 0.70 || rssiStdDev > 7.0 -> InterferenceLevel.SEVERE
            pdr < 0.88 || rssiStdDev > 3.0 -> InterferenceLevel.MODERATE
            else -> InterferenceLevel.LOW
        }

        val redundancy = when (level) {
            InterferenceLevel.LOW -> 1
            InterferenceLevel.MODERATE -> 2
            InterferenceLevel.SEVERE -> 3
        }

        val scanInterval = when (level) {
            InterferenceLevel.LOW -> 100L
            InterferenceLevel.MODERATE -> 60L
            InterferenceLevel.SEVERE -> 40L
        }

        val backoff = when (level) {
            InterferenceLevel.LOW -> 0L
            InterferenceLevel.MODERATE -> 15L
            InterferenceLevel.SEVERE -> 40L
        }

        _policyFlow.value = RfPolicyAdvice(
            timestamp = now,
            interferenceLevel = level,
            recommendedRedundancyCopies = redundancy,
            recommendedScanIntervalMs = scanInterval,
            recommendedBackoffMs = backoff,
            pdr = round(pdr * 1000.0) / 1000.0,
            rssiVariance = rssiStdDev
        )
    }
}
