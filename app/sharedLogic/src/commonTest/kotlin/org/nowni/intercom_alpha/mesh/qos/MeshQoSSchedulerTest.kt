package org.nowni.intercom_alpha.mesh.qos

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.mesh.ControlType
import org.nowni.intercom_alpha.mesh.MeshPacket
import kotlin.test.*

class MeshQoSSchedulerTest {

    private fun createDummyAudio(seq: Long): MeshPacket =
        MeshPacket.Audio(
            senderId = "rider-1",
            sequence = seq,
            timestamp = 1000L + seq,
            data = byteArrayOf(1, 2, 3),
            profile = AudioProfile.MEDIUM
        )

    private fun createDummyControl(type: ControlType): MeshPacket =
        MeshPacket.Control(
            senderId = "rider-1",
            type = type,
            payload = byteArrayOf(0x0A)
        )

    @Test
    fun testPacketClassification() {
        assertEquals(QoSPriority.ACTIVE_VOICE, QoSPacketClassifier.classify(createDummyAudio(1)))
        assertEquals(QoSPriority.EMERGENCY_CRITICAL, QoSPacketClassifier.classify(createDummyControl(ControlType.CRASH_ALERT)))
        assertEquals(QoSPriority.EMERGENCY_CRITICAL, QoSPacketClassifier.classify(createDummyControl(ControlType.GEOFENCE_ALARM)))
        assertEquals(QoSPriority.ROUTING_CONTROL, QoSPacketClassifier.classify(createDummyControl(ControlType.ROUTE_REQUEST)))
        assertEquals(QoSPriority.ROUTING_CONTROL, QoSPacketClassifier.classify(createDummyControl(ControlType.ROUTE_REPLY)))
        assertEquals(QoSPriority.BULK_TELEMETRY, QoSPacketClassifier.classify(createDummyControl(ControlType.FILE_CHUNK)))
        assertEquals(QoSPriority.BULK_TELEMETRY, QoSPacketClassifier.classify(createDummyControl(ControlType.POWER_STATUS)))

        // Relay wrapper inspection
        val relayWrappedAudio = MeshPacket.Relay(
            originalSenderId = "rider-2",
            ttl = 2,
            packet = createDummyAudio(2)
        )
        assertEquals(QoSPriority.ACTIVE_VOICE, QoSPacketClassifier.classify(relayWrappedAudio))
    }

    @Test
    fun testStrictPriorityOrdering() = runTest {
        val scheduler = MeshQoSScheduler(voiceHangoverMs = 0L) // Zero hangover for direct order testing

        val bulk = createDummyControl(ControlType.FILE_CHUNK)
        val control = createDummyControl(ControlType.ROUTE_REQUEST)
        val voice = createDummyAudio(1)
        val emergency = createDummyControl(ControlType.CRASH_ALERT)

        // Enqueue in reverse priority
        scheduler.enqueuePacket(bulk)
        scheduler.enqueuePacket(control)
        scheduler.enqueuePacket(voice)
        scheduler.enqueuePacket(emergency)

        // Dequeue must respect strict priority: Emergency -> Voice -> Control -> Bulk
        val p1 = scheduler.dequeuePacket()
        assertNotNull(p1)
        assertEquals(QoSPriority.EMERGENCY_CRITICAL, p1.priority)

        val p2 = scheduler.dequeuePacket()
        assertNotNull(p2)
        assertEquals(QoSPriority.ACTIVE_VOICE, p2.priority)

        val p3 = scheduler.dequeuePacket()
        assertNotNull(p3)
        assertEquals(QoSPriority.ROUTING_CONTROL, p3.priority)

        val p4 = scheduler.dequeuePacket()
        assertNotNull(p4)
        assertEquals(QoSPriority.BULK_TELEMETRY, p4.priority)

        assertNull(scheduler.dequeuePacket())
    }

    @Test
    fun testVoicePreemptionOverBulk() = runTest {
        val queue = MeshQoSQueue()

        // Enqueue 5 bulk file chunks
        for (i in 0 until 5) {
            queue.enqueue(createDummyControl(ControlType.FILE_CHUNK))
        }

        assertEquals(0L, queue.voicePreemptionCount)

        // Enqueue voice frame while bulk is queued
        queue.enqueue(createDummyAudio(10))
        assertEquals(1L, queue.voicePreemptionCount)

        // Voice frame should jump ahead of all bulk packets
        val firstOut = queue.dequeue()
        assertNotNull(firstOut)
        assertEquals(QoSPriority.ACTIVE_VOICE, firstOut.priority)
    }

    @Test
    fun testAntiStarvationForRoutingControl() = runTest {
        val queue = MeshQoSQueue(voiceDeficitThreshold = 4)

        // Enqueue 10 voice packets and 1 routing control packet
        for (i in 1..10) {
            queue.enqueue(createDummyAudio(i.toLong()))
        }
        queue.enqueue(createDummyControl(ControlType.ROUTE_REQUEST))

        // Dequeue first 4 voice packets
        for (i in 1..4) {
            val item = queue.dequeue()
            assertNotNull(item)
            assertEquals(QoSPriority.ACTIVE_VOICE, item.priority)
        }

        // 5th dequeue must be ROUTING_CONTROL to prevent voice starvation!
        val controlItem = queue.dequeue()
        assertNotNull(controlItem)
        assertEquals(QoSPriority.ROUTING_CONTROL, controlItem.priority)

        // 6th dequeue resumes remaining voice packets
        val voiceItem = queue.dequeue()
        assertNotNull(voiceItem)
        assertEquals(QoSPriority.ACTIVE_VOICE, voiceItem.priority)
    }

    @Test
    fun testCapacityBoundsAndDropPolicy() = runTest {
        val queue = MeshQoSQueue()

        // Bulk queue capacity is 256
        for (i in 0 until 256) {
            assertTrue(queue.enqueue(createDummyControl(ControlType.FILE_CHUNK)))
        }

        // 257th bulk packet must be rejected/dropped
        val accepted = queue.enqueue(createDummyControl(ControlType.FILE_CHUNK))
        assertFalse(accepted)
        assertEquals(1L, queue.totalDropped)

        // Voice queue capacity is 128 (drop-oldest policy)
        for (i in 0 until 128) {
            assertTrue(queue.enqueue(createDummyAudio(i.toLong())))
        }
        // 129th voice packet should drop oldest and return true
        assertTrue(queue.enqueue(createDummyAudio(999)))
        assertEquals(2L, queue.totalDropped) // 1 bulk + 1 voice dropped
    }

    @Test
    fun testQoSSnapshotUpdates() = runTest {
        val scheduler = MeshQoSScheduler(voiceHangoverMs = 500L)

        scheduler.enqueuePacket(createDummyAudio(1))
        scheduler.updateAirtimeUtilization(75)

        val snap = scheduler.qosSnapshot.value
        assertEquals(1, snap.voiceQueueDepth)
        assertTrue(snap.isVoiceActive)
        assertTrue(snap.bulkThrottleActive)
        assertEquals(75, snap.estimatedAirtimeUtilizationPercent)
    }
}
