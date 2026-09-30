package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.mesh.PacketCipher
import org.nowni.intercom_alpha.telemetry.GpsCoordinate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmergencyBlackboxRecorderTest {

    private fun createDummyAdpcm(size: Int = 120, fillByte: Byte = 0x5A): ByteArray {
        return ByteArray(size) { fillByte }
    }

    private fun createFrame(
        seq: Long,
        timestampMs: Long,
        speakerId: String = "rider-alpha",
        priority: PriorityLevel = PriorityLevel.NORMAL,
        alertType: AlertType = AlertType.NONE,
        coord: GpsCoordinate? = null
    ): BlackboxAudioFrame {
        return BlackboxAudioFrame(
            timestampMs = timestampMs,
            sequence = seq,
            speakerId = speakerId,
            speakerName = "Alpha Leader",
            priority = priority,
            alertType = alertType,
            adpcmData = createDummyAdpcm(size = 120, fillByte = (seq % 128).toByte()),
            gpsCoordinate = coord
        )
    }

    @Test
    fun testRollingBufferPreservesSixtySecondsAndEvictsOld() = runTest {
        val recorder = EmergencyBlackboxRecorder(
            bufferDurationMs = 60_000L,
            autoFreezeOnEmergency = false
        )

        // Enqueue frames across 75 seconds
        recorder.recordFrame(createFrame(seq = 1, timestampMs = 10_000L))
        recorder.recordFrame(createFrame(seq = 2, timestampMs = 30_000L))
        recorder.recordFrame(createFrame(seq = 3, timestampMs = 50_000L))
        recorder.recordFrame(createFrame(seq = 4, timestampMs = 70_000L)) // 70k - 60k = 10k cutoff (seq 1 still valid at boundary)

        assertEquals(4, recorder.bufferedFrameCount.value)

        // Adding frame at 75_000L -> cutoff is 15_000L, so frame seq 1 (at 10_000L) is pruned
        recorder.recordFrame(createFrame(seq = 5, timestampMs = 75_000L))

        assertEquals(4, recorder.bufferedFrameCount.value)
        val buffered = recorder.getBufferedFrames()
        assertEquals(2L, buffered.first().sequence)
        assertEquals(5L, buffered.last().sequence)
        assertEquals(45_000L, recorder.bufferedDurationMs.value) // 75k - 30k
    }

    @Test
    fun testHardCapacityBoundPreventsMemoryExplosion() = runTest {
        val recorder = EmergencyBlackboxRecorder(
            bufferDurationMs = 60_000L,
            maxFramesCapacity = 50,
            autoFreezeOnEmergency = false
        )

        for (i in 1..150) {
            recorder.recordFrame(createFrame(seq = i.toLong(), timestampMs = 1000L + i * 20L))
        }

        assertEquals(50, recorder.bufferedFrameCount.value)
        val buffered = recorder.getBufferedFrames()
        assertEquals(101L, buffered.first().sequence)
        assertEquals(150L, buffered.last().sequence)
    }

    @Test
    fun testAutoFreezeOnEmergencyOverrideFrame() = runTest {
        val recorder = EmergencyBlackboxRecorder(
            bufferDurationMs = 60_000L,
            autoFreezeOnEmergency = true
        )

        // Add 5 normal frames
        for (i in 1..5) {
            val snapshot = recorder.recordFrame(createFrame(seq = i.toLong(), timestampMs = 1000L + i * 20L))
            assertNull(snapshot)
        }

        // Add 6th frame with EMERGENCY_OVERRIDE
        val emergencyFrame = createFrame(
            seq = 6,
            timestampMs = 2000L,
            priority = PriorityLevel.EMERGENCY_OVERRIDE,
            alertType = AlertType.NONE
        )
        val snapshot = recorder.recordFrame(emergencyFrame, now = 2000L)

        assertNotNull(snapshot)
        assertEquals(IncidentTriggerReason.EMERGENCY_OVERRIDE_AUDIO, snapshot.triggerReason)
        assertEquals("rider-alpha", snapshot.primaryPeerId)
        assertEquals("Alpha Leader", snapshot.primaryPeerName)
        assertEquals(6, snapshot.frameCount)
        assertEquals(6, snapshot.audioFrames.size)
    }

    @Test
    fun testAutoFreezeOnCrashAlert() = runTest {
        val recorder = EmergencyBlackboxRecorder(autoFreezeOnEmergency = true)

        val crashCoord = GpsCoordinate(latitude = 45.123, longitude = 6.456, speedKmh = 12f)
        val crashFrame = createFrame(
            seq = 10,
            timestampMs = 5000L,
            priority = PriorityLevel.NORMAL,
            alertType = AlertType.CRASH_DETECTED,
            coord = crashCoord
        )

        val snapshot = recorder.recordFrame(crashFrame, now = 5000L)

        assertNotNull(snapshot)
        assertEquals(IncidentTriggerReason.CRASH_DETECTED, snapshot.triggerReason)
        assertEquals(crashCoord, snapshot.location)
    }

    @Test
    fun testIncidentCooldownSuppressesRapidDuplicateSnapshots() = runTest {
        val recorder = EmergencyBlackboxRecorder(
            autoFreezeOnEmergency = true,
            incidentCooldownMs = 10_000L
        )

        val frame1 = createFrame(seq = 1, timestampMs = 1000L, priority = PriorityLevel.EMERGENCY_OVERRIDE)
        val snapshot1 = recorder.recordFrame(frame1, now = 1000L)
        assertNotNull(snapshot1)

        // Rapid subsequent emergency frame within cooldown (now = 1200ms)
        val frame2 = createFrame(seq = 2, timestampMs = 1200L, priority = PriorityLevel.EMERGENCY_OVERRIDE)
        val snapshot2 = recorder.recordFrame(frame2, now = 1200L)
        assertNull(snapshot2) // Cooldown active

        // After cooldown expires (now = 12000ms)
        val frame3 = createFrame(seq = 3, timestampMs = 12_000L, priority = PriorityLevel.EMERGENCY_OVERRIDE)
        val snapshot3 = recorder.recordFrame(frame3, now = 12_000L)
        assertNotNull(snapshot3)
    }

    @Test
    fun testPureKmpChaCha20Poly1305EncryptionAndDecryption() = runTest {
        val cipher = PacketCipher(PacketCipher.generateRandomKey())
        val recorder = EmergencyBlackboxRecorder(
            defaultCipher = cipher,
            autoFreezeOnEmergency = false
        )

        for (i in 1..10) {
            recorder.recordFrame(createFrame(seq = i.toLong(), timestampMs = 1000L + i * 20L))
        }

        val testLoc = GpsCoordinate(latitude = 37.7749, longitude = -122.4194, speedKmh = 65f, headingDegrees = 180f)
        val snapshot = recorder.freezeSnapshot(
            triggerReason = IncidentTriggerReason.MANUAL_SOS,
            primaryPeerId = "lead-scout",
            primaryPeerName = "Sarah",
            location = testLoc,
            now = 2000L
        )

        // Verify report was auto-encrypted and cached
        val cachedReports = recorder.getEncryptedReports()
        assertEquals(1, cachedReports.size)
        val report = cachedReports.first()

        assertEquals(snapshot.incidentId, report.incidentId)
        assertEquals(IncidentTriggerReason.MANUAL_SOS, report.triggerReason)
        assertEquals("lead-scout", report.primaryPeerId)
        assertEquals("Sarah", report.primaryPeerName)
        assertEquals(testLoc, report.location)
        assertTrue(report.encryptedPayload.size > 12 + 16) // Nonce (12) + Ciphertext + Tag (16)

        // Decrypt report
        val decrypted = recorder.decryptReport(report, cipher)
        assertNotNull(decrypted)
        assertEquals(snapshot.incidentId, decrypted.incidentId)
        assertEquals(snapshot.audioFrames.size, decrypted.audioFrames.size)
        assertEquals(snapshot.primaryPeerName, decrypted.primaryPeerName)
        assertEquals(testLoc, decrypted.location)
        assertEquals(snapshot.audioFrames.first().sequence, decrypted.audioFrames.first().sequence)
    }

    @Test
    fun testDecryptionTamperAndWrongKeyRejection() = runTest {
        val goodCipher = PacketCipher(PacketCipher.generateRandomKey())
        val wrongCipher = PacketCipher(PacketCipher.generateRandomKey())
        val recorder = EmergencyBlackboxRecorder(autoFreezeOnEmergency = false)

        recorder.recordFrame(createFrame(seq = 1, timestampMs = 1000L))
        val snapshot = recorder.freezeSnapshot(
            triggerReason = IncidentTriggerReason.HAZARD_BROADCAST,
            primaryPeerId = "rider-1"
        )
        val report = recorder.encryptSnapshot(snapshot, goodCipher)

        // Decryption with wrong cipher must fail cleanly
        val wrongResult = recorder.decryptReport(report, wrongCipher)
        assertNull(wrongResult)

        // Decryption with tampered ciphertext payload must fail tag check
        val tamperedBytes = report.encryptedPayload.copyOf()
        tamperedBytes[tamperedBytes.size - 5] = (tamperedBytes[tamperedBytes.size - 5].toInt() xor 0xFF).toByte()
        val tamperedReport = report.copy(encryptedPayload = tamperedBytes)

        val tamperedResult = recorder.decryptReport(tamperedReport, goodCipher)
        assertNull(tamperedResult)
    }

    @Test
    fun testMemoryFootprintBoundUnderThreeMegabytes() = runTest {
        val cipher = PacketCipher(PacketCipher.generateRandomKey())
        val recorder = EmergencyBlackboxRecorder(
            bufferDurationMs = 60_000L,
            maxFramesCapacity = 3_000,
            maxCachedIncidents = 5,
            defaultCipher = cipher,
            autoFreezeOnEmergency = false
        )

        // Fill buffer to max capacity with 3,000 frames (60s of 20ms frames)
        for (i in 1..3_000) {
            recorder.recordFrame(createFrame(seq = i.toLong(), timestampMs = 1000L + i * 20L))
        }

        // Trigger 5 cached incidents
        for (inc in 1..5) {
            recorder.freezeSnapshot(
                triggerReason = IncidentTriggerReason.CRASH_DETECTED,
                primaryPeerId = "rider-$inc",
                now = 100_000L + inc * 1000L
            )
        }

        assertEquals(3_000, recorder.bufferedFrameCount.value)
        assertEquals(5, recorder.getEncryptedReports().size)

        val estimatedBytes = recorder.estimateMemoryUsageBytes()
        // Must be strictly bounded well under 3,000,000 bytes (3 MB)
        assertTrue(estimatedBytes < 3_000_000L, "Memory footprint $estimatedBytes exceeded 3 MB threshold")
    }

    @Test
    fun testManualSosAndCrashTelemetryHelpers() = runTest {
        val recorder = EmergencyBlackboxRecorder(autoFreezeOnEmergency = false)

        val coord = GpsCoordinate(latitude = 51.5074, longitude = -0.1278, speedKmh = 45f)
        val crashSnapshot = recorder.recordCrashTelemetry(
            coordinate = coord,
            peerId = "scout-1",
            peerName = "Dave",
            details = "Impact detected at 45 km/h",
            now = 5000L
        )

        assertEquals(IncidentTriggerReason.CRASH_DETECTED, crashSnapshot.triggerReason)
        assertEquals("Impact detected at 45 km/h", crashSnapshot.metadata["details"])

        val sosSnapshot = recorder.triggerManualSos(
            peerId = "scout-1",
            peerName = "Dave",
            location = coord,
            note = "Flat tire in mountain pass",
            now = 6000L
        )

        assertEquals(IncidentTriggerReason.MANUAL_SOS, sosSnapshot.triggerReason)
        assertEquals("Flat tire in mountain pass", sosSnapshot.metadata["note"])

        val markdown = sosSnapshot.toMarkdownForensicSummary()
        assertTrue(markdown.contains("Incident Forensic Snapshot"))
        assertTrue(markdown.contains("MANUAL_SOS"))
        assertTrue(markdown.contains("Dave"))
    }

    @Test
    fun testPauseResumeAndClear() = runTest {
        val recorder = EmergencyBlackboxRecorder(autoFreezeOnEmergency = false)

        recorder.recordFrame(createFrame(seq = 1, timestampMs = 1000L))
        assertEquals(1, recorder.bufferedFrameCount.value)

        recorder.pauseRecording()
        assertFalse(recorder.isRecording.value)

        recorder.recordFrame(createFrame(seq = 2, timestampMs = 1020L))
        assertEquals(1, recorder.bufferedFrameCount.value) // Frame 2 ignored while paused

        recorder.resumeRecording()
        assertTrue(recorder.isRecording.value)

        recorder.recordFrame(createFrame(seq = 3, timestampMs = 1040L))
        assertEquals(2, recorder.bufferedFrameCount.value)

        recorder.clearBuffer()
        assertEquals(0, recorder.bufferedFrameCount.value)
        assertEquals(0L, recorder.bufferedDurationMs.value)
    }

    @Test
    fun testRecordPrioritizedFrameConvenience() = runTest {
        val recorder = EmergencyBlackboxRecorder(autoFreezeOnEmergency = true)

        val prioritizedFrame = PrioritizedAudioFrame(
            priority = PriorityLevel.EMERGENCY_OVERRIDE,
            senderId = "rider-99",
            sequence = 42L,
            timestamp = 1500L,
            data = createDummyAdpcm(size = 80),
            alertType = AlertType.CRASH_DETECTED
        )

        val snapshot = recorder.recordPrioritizedFrame(
            frame = prioritizedFrame,
            speakerName = "Rear Guard",
            now = 1500L
        )

        assertNotNull(snapshot)
        assertEquals(IncidentTriggerReason.CRASH_DETECTED, snapshot.triggerReason)
        assertEquals("rider-99", snapshot.primaryPeerId)
        assertEquals("Rear Guard", snapshot.primaryPeerName)
    }
}
