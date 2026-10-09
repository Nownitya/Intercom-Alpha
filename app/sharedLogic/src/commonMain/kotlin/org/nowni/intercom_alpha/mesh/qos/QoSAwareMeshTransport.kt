package org.nowni.intercom_alpha.mesh.qos

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.mesh.*

/**
 * QoS-Aware Mesh Transport Decorator.
 *
 * Wraps an underlying [MeshTransport] and routes all outbound transmissions
 * (voice frames, routing messages, telemetry, bulk file chunks) through
 * a [MeshQoSScheduler].
 *
 * Enforces:
 * 1. Immediate preemption of bulk data during active voice transmission.
 * 2. Strict priority queueing (Emergency > Voice > Control > Bulk).
 * 3. Airtime duty cycle pacing delays preventing 2.4 GHz RF collisions.
 * 4. Starvation protection for routing control packets.
 */
class QoSAwareMeshTransport(
    val underlyingTransport: MeshTransport,
    override val qosScheduler: MeshQoSScheduler = MeshQoSScheduler(),
    scope: CoroutineScope? = null
) : MeshTransport {

    override val peers: ReceiveChannel<List<Peer>> get() = underlyingTransport.peers
    override val incomingAudio: ReceiveChannel<MeshPacket.Audio> get() = underlyingTransport.incomingAudio
    override val incomingControl: ReceiveChannel<MeshPacket.Control> get() = underlyingTransport.incomingControl
    override val connectionState: ReceiveChannel<ConnectionState> get() = underlyingTransport.connectionState

    private var workerJob: Job? = null
    private var localPeerId: String = "local-node"
    private var audioSequence: Long = 0L

    init {
        scope?.let { startDispatchWorker(it) }
    }

    /**
     * Spawns an asynchronous queue dispatch worker servicing the priority queues.
     */
    fun startDispatchWorker(coroutineScope: CoroutineScope) {
        workerJob?.cancel()
        workerJob = coroutineScope.launch {
            while (isActive) {
                val queued = qosScheduler.dequeuePacket()
                if (queued != null) {
                    val pacing = qosScheduler.calculatePacingDelayMs(queued.priority)
                    if (pacing > 0) {
                        delay(pacing)
                    }
                    underlyingTransport.sendPacket(queued.packet)
                } else {
                    delay(5)
                }
            }
        }
    }

    /**
     * Manually triggers dequeuing and dispatching a single packet from the scheduler.
     * Useful for synchronous step-by-step simulations and deterministic tests.
     */
    suspend fun stepDispatch(): QueuedMeshPacket? {
        val queued = qosScheduler.dequeuePacket() ?: return null
        val pacing = qosScheduler.calculatePacingDelayMs(queued.priority)
        if (pacing > 0) {
            delay(pacing)
        }
        underlyingTransport.sendPacket(queued.packet)
        return queued
    }

    override suspend fun start(groupId: String, peerName: String, config: MeshConfig) {
        localPeerId = peerName
        underlyingTransport.start(groupId, peerName, config)
    }

    override suspend fun stop() {
        workerJob?.cancel()
        workerJob = null
        qosScheduler.clear()
        underlyingTransport.stop()
    }

    override suspend fun sendAudio(data: ByteArray, profile: AudioProfile) {
        val packet = MeshPacket.Audio(
            senderId = localPeerId,
            sequence = audioSequence++,
            timestamp = currentTimeMillis(),
            data = data,
            profile = profile
        )
        qosScheduler.enqueuePacket(packet, explicitPriority = QoSPriority.ACTIVE_VOICE)
        if (workerJob == null) {
            val pacing = qosScheduler.calculatePacingDelayMs(QoSPriority.ACTIVE_VOICE)
            if (pacing > 0) delay(pacing)
            underlyingTransport.sendAudio(data, profile)
        }
    }

    override suspend fun sendControl(type: ControlType, payload: ByteArray) {
        val packet = MeshPacket.Control(
            senderId = localPeerId,
            type = type,
            payload = payload
        )
        val priority = QoSPacketClassifier.classify(packet)
        qosScheduler.enqueuePacket(packet, explicitPriority = priority)
        if (workerJob == null) {
            val pacing = qosScheduler.calculatePacingDelayMs(priority)
            if (pacing > 0) delay(pacing)
            underlyingTransport.sendControl(type, payload)
        }
    }

    override fun updateConfig(config: MeshConfig) {
        underlyingTransport.updateConfig(config)
    }
}
