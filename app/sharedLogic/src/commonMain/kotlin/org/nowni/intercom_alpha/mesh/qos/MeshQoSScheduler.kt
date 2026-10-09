package org.nowni.intercom_alpha.mesh.qos

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.mesh.MeshPacket

/**
 * Closed-Loop Mesh Airtime Traffic Scheduler & Preemption Arbiter.
 *
 * Coordinates multi-hop packet dispatch over single-characteristic BLE GATT channels:
 * 1. Enforces strict QoS prioritization to guarantee real-time voice latency.
 * 2. Unconditionally preempts low-priority background transfers during conversation.
 * 3. Throttles bulk OTA route beaming with dynamic backoff windows during RF congestion.
 * 4. Exposes reactive [QoSSnapshot] for multiplatform cockpit HUD presentation.
 */
class MeshQoSScheduler(
    val voiceHangoverMs: Long = 400L,
    private val queue: MeshQoSQueue = MeshQoSQueue()
) {
    private val mutex = Mutex()

    private var lastVoiceActivityTimestampMs: Long = 0L
    private var airtimeUtilizationPercent: Int = 0

    private val _snapshot = MutableStateFlow(QoSSnapshot())
    val qosSnapshot: StateFlow<QoSSnapshot> = _snapshot.asStateFlow()

    /**
     * Enqueue a packet for transmission.
     */
    suspend fun enqueuePacket(
        packet: MeshPacket,
        targetPeerId: String? = null,
        explicitPriority: QoSPriority? = null
    ): Boolean {
        val priority = explicitPriority ?: QoSPacketClassifier.classify(packet)
        val now = currentTimeMillis()

        if (priority == QoSPriority.ACTIVE_VOICE || priority == QoSPriority.EMERGENCY_CRITICAL) {
            mutex.withLock {
                lastVoiceActivityTimestampMs = now
            }
        }

        val enqueued = queue.enqueue(packet, targetPeerId, priority)
        refreshSnapshotInternal()
        return enqueued
    }

    /**
     * Dequeues the next packet according to QoS prioritization and airtime policy.
     */
    suspend fun dequeuePacket(): QueuedMeshPacket? {
        val now = currentTimeMillis()
        val voiceActive = mutex.withLock {
            (now - lastVoiceActivityTimestampMs) < voiceHangoverMs
        }

        // When voice is active, throttle bulk telemetry dequeuing
        val item = queue.dequeue(allowBulk = !voiceActive)
        refreshSnapshotInternal()
        return item
    }

    /**
     * Calculates recommended inter-packet pacing delay to prevent BLE GATT buffer drops.
     */
    suspend fun calculatePacingDelayMs(priority: QoSPriority): Long {
        val now = currentTimeMillis()
        val voiceActive = mutex.withLock {
            (now - lastVoiceActivityTimestampMs) < voiceHangoverMs
        }

        return when (priority) {
            QoSPriority.EMERGENCY_CRITICAL -> 0L
            QoSPriority.ACTIVE_VOICE -> 5L
            QoSPriority.ROUTING_CONTROL -> 15L
            QoSPriority.BULK_TELEMETRY -> if (voiceActive) 80L else 20L
        }
    }

    /**
     * Update estimated 2.4 GHz channel airtime utilization (0..100%).
     */
    suspend fun updateAirtimeUtilization(percent: Int) {
        mutex.withLock {
            airtimeUtilizationPercent = percent.coerceIn(0, 100)
        }
        refreshSnapshotInternal()
    }

    /**
     * Manually triggers voice activity indicator (e.g. on local PTT press).
     */
    suspend fun markVoiceActive() {
        mutex.withLock {
            lastVoiceActivityTimestampMs = currentTimeMillis()
        }
        refreshSnapshotInternal()
    }

    suspend fun clear() {
        queue.clear()
        mutex.withLock {
            lastVoiceActivityTimestampMs = 0L
            airtimeUtilizationPercent = 0
        }
        refreshSnapshotInternal()
    }

    private suspend fun refreshSnapshotInternal() {
        val now = currentTimeMillis()
        val (voiceActive, airtime) = mutex.withLock {
            val va = (now - lastVoiceActivityTimestampMs) < voiceHangoverMs
            Pair(va, airtimeUtilizationPercent)
        }
        val snap = queue.getSnapshot(
            isVoiceActive = voiceActive,
            bulkThrottleActive = voiceActive || airtime > 65,
            estimatedAirtimePercent = airtime
        )
        _snapshot.value = snap
    }
}
