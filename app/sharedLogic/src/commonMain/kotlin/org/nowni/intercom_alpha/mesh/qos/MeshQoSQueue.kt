package org.nowni.intercom_alpha.mesh.qos

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.mesh.MeshPacket

/**
 * Thread-safe 4-tier priority queue for mesh packets.
 *
 * Implements strict hierarchical priority with deficit-based anti-starvation:
 * - Priority 0: [QoSPriority.EMERGENCY_CRITICAL] — unconditionally dequeued first.
 * - Priority 1: [QoSPriority.ACTIVE_VOICE] — dequeued second, preempts bulk transfers.
 * - Priority 2: [QoSPriority.ROUTING_CONTROL] — protected from starvation by token allotment.
 * - Priority 3: [QoSPriority.BULK_TELEMETRY] — dequeued only when voice/emergency queues are empty or paced.
 */
class MeshQoSQueue(
    private val voiceDeficitThreshold: Int = 8
) {
    private val mutex = Mutex()

    private val emergencyQueue = ArrayDeque<QueuedMeshPacket>()
    private val voiceQueue = ArrayDeque<QueuedMeshPacket>()
    private val controlQueue = ArrayDeque<QueuedMeshPacket>()
    private val bulkQueue = ArrayDeque<QueuedMeshPacket>()

    private var consecutiveVoiceDispatched: Int = 0

    var totalEnqueued: Long = 0L
        private set
    var totalDispatched: Long = 0L
        private set
    var totalDropped: Long = 0L
        private set
    var voicePreemptionCount: Long = 0L
        private set

    /**
     * Enqueue a packet according to its priority tier.
     *
     * @param packet Outbound or forwarded mesh packet.
     * @param targetPeerId Optional direct unicast peer target.
     * @param explicitPriority Optional override; if null, automatically classified via [QoSPacketClassifier].
     * @return True if enqueued successfully; false if dropped due to capacity overflow.
     */
    suspend fun enqueue(
        packet: MeshPacket,
        targetPeerId: String? = null,
        explicitPriority: QoSPriority? = null
    ): Boolean = mutex.withLock {
        val priority = explicitPriority ?: QoSPacketClassifier.classify(packet)
        val now = currentTimeMillis()
        val item = QueuedMeshPacket(
            priority = priority,
            packet = packet,
            enqueueTimestampMs = now,
            targetPeerId = targetPeerId
        )

        val targetQueue = when (priority) {
            QoSPriority.EMERGENCY_CRITICAL -> emergencyQueue
            QoSPriority.ACTIVE_VOICE -> voiceQueue
            QoSPriority.ROUTING_CONTROL -> controlQueue
            QoSPriority.BULK_TELEMETRY -> bulkQueue
        }

        // Preemption counter: if enqueuing voice/emergency while bulk is waiting
        if ((priority == QoSPriority.EMERGENCY_CRITICAL || priority == QoSPriority.ACTIVE_VOICE) && bulkQueue.isNotEmpty()) {
            voicePreemptionCount++
        }

        // Bounded capacity check
        if (targetQueue.size >= priority.maxQueueCapacity) {
            when (priority) {
                QoSPriority.EMERGENCY_CRITICAL,
                QoSPriority.ACTIVE_VOICE -> {
                    // Drop oldest stale voice packet to make room for fresher real-time audio
                    targetQueue.removeFirstOrNull()
                    totalDropped++
                }
                QoSPriority.ROUTING_CONTROL,
                QoSPriority.BULK_TELEMETRY -> {
                    // Drop incoming tail packet
                    totalDropped++
                    return@withLock false
                }
            }
        }

        targetQueue.addLast(item)
        totalEnqueued++
        true
    }

    /**
     * Dequeues the next highest-priority packet ready for transmission.
     *
     * @param allowBulk If false, suppresses bulk telemetry dequeuing during active voice transmission.
     */
    suspend fun dequeue(allowBulk: Boolean = true): QueuedMeshPacket? = mutex.withLock {
        // 1. Unconditional Emergency
        if (emergencyQueue.isNotEmpty()) {
            consecutiveVoiceDispatched = 0
            val item = emergencyQueue.removeFirst()
            totalDispatched++
            return@withLock item
        }

        // 2. Anti-Starvation Check for Routing Control
        // If voice has had many consecutive dispatches and control is waiting, service 1 control packet
        if (consecutiveVoiceDispatched >= voiceDeficitThreshold && controlQueue.isNotEmpty()) {
            consecutiveVoiceDispatched = 0
            val item = controlQueue.removeFirst()
            totalDispatched++
            return@withLock item
        }

        // 3. Active Voice
        if (voiceQueue.isNotEmpty()) {
            consecutiveVoiceDispatched++
            val item = voiceQueue.removeFirst()
            totalDispatched++
            return@withLock item
        }

        // 4. Routing Control
        if (controlQueue.isNotEmpty()) {
            consecutiveVoiceDispatched = 0
            val item = controlQueue.removeFirst()
            totalDispatched++
            return@withLock item
        }

        // 5. Bulk Telemetry (if permitted by airtime throttle)
        if (allowBulk && bulkQueue.isNotEmpty()) {
            consecutiveVoiceDispatched = 0
            val item = bulkQueue.removeFirst()
            totalDispatched++
            return@withLock item
        }

        null
    }

    /**
     * Inspect snapshot of queue depths and metrics.
     */
    suspend fun getSnapshot(
        isVoiceActive: Boolean = false,
        bulkThrottleActive: Boolean = false,
        estimatedAirtimePercent: Int = 0
    ): QoSSnapshot = mutex.withLock {
        QoSSnapshot(
            emergencyQueueDepth = emergencyQueue.size,
            voiceQueueDepth = voiceQueue.size,
            controlQueueDepth = controlQueue.size,
            bulkQueueDepth = bulkQueue.size,
            totalPacketsEnqueued = totalEnqueued,
            totalPacketsDispatched = totalDispatched,
            totalPacketsDropped = totalDropped,
            voicePreemptionCount = voicePreemptionCount,
            isVoiceActive = isVoiceActive,
            bulkThrottleActive = bulkThrottleActive,
            estimatedAirtimeUtilizationPercent = estimatedAirtimePercent
        )
    }

    suspend fun clear() = mutex.withLock {
        emergencyQueue.clear()
        voiceQueue.clear()
        controlQueue.clear()
        bulkQueue.clear()
        consecutiveVoiceDispatched = 0
    }

    suspend fun size(): Int = mutex.withLock {
        emergencyQueue.size + voiceQueue.size + controlQueue.size + bulkQueue.size
    }
}
