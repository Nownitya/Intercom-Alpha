package org.nowni.intercom_alpha.group

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.mesh.ControlType
import org.nowni.intercom_alpha.mesh.MeshPacket
import org.nowni.intercom_alpha.mesh.PacketCipher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupEpochManagerTest {

    private val testGroupId = "convoy-alpha-group"
    private val leaderId = "rider-leader"
    private val peer1Id = "rider-scout"
    private val peer2Id = "rider-sweeper"
    private val initialSecret = PacketCipher.deriveKey("shared-alpha-secret-2026")

    @Test
    fun testLeaderInitiatesRolloverAndPeersVerifyDerivation() = runTest {
        val leaderManager = GroupEpochManager(
            groupId = testGroupId,
            localPeerId = leaderId,
            leaderId = leaderId,
            initialMasterSecret = initialSecret,
            initialEpochNumber = 1L
        )

        val peer1Manager = GroupEpochManager(
            groupId = testGroupId,
            localPeerId = peer1Id,
            leaderId = leaderId,
            initialMasterSecret = initialSecret,
            initialEpochNumber = 1L
        )

        val peer2Manager = GroupEpochManager(
            groupId = testGroupId,
            localPeerId = peer2Id,
            leaderId = leaderId,
            initialMasterSecret = initialSecret,
            initialEpochNumber = 1L
        )

        val baseTime = currentTimeMillis()

        // Leader initiates rollover to Epoch 2
        val rolloverPacket = leaderManager.initiateRollover(
            transitionWindowMs = 30_000L,
            timestampMs = baseTime
        )

        assertEquals(ControlType.EPOCH_ROLLOVER, rolloverPacket.type)
        assertEquals(2L, leaderManager.currentEpoch.epochNumber)
        assertEquals(1L, leaderManager.previousEpoch?.epochNumber)
        assertTrue(leaderManager.isInTransitionWindow(baseTime + 5_000L))

        // Peer 1 and Peer 2 receive and process rollover
        val peer1Success = peer1Manager.handleEpochRollover(rolloverPacket, currentTimeMs = baseTime + 10L)
        val peer2Success = peer2Manager.handleEpochRollover(rolloverPacket, currentTimeMs = baseTime + 20L)

        assertTrue(peer1Success, "Peer 1 must accept valid leader rollover")
        assertTrue(peer2Success, "Peer 2 must accept valid leader rollover")

        assertEquals(2L, peer1Manager.currentEpoch.epochNumber)
        assertEquals(2L, peer2Manager.currentEpoch.epochNumber)

        // Verify leader and peers derived the identical secret
        val testMessage = "Voice frame encrypted with Epoch 2 key".encodeToByteArray()
        val encryptedByLeader = leaderManager.encrypt(testMessage)

        val decryptedByPeer1 = peer1Manager.decrypt(encryptedByLeader, timestampMs = baseTime + 100L)
        val decryptedByPeer2 = peer2Manager.decrypt(encryptedByLeader, timestampMs = baseTime + 100L)

        assertNotNull(decryptedByPeer1)
        assertNotNull(decryptedByPeer2)
        assertEquals("Voice frame encrypted with Epoch 2 key", decryptedByPeer1.decodeToString())
        assertEquals("Voice frame encrypted with Epoch 2 key", decryptedByPeer2.decodeToString())
    }

    @Test
    fun testDualEpochConcurrentDecryptionDuringTransitionWindow() = runTest {
        val leader = GroupEpochManager(
            groupId = testGroupId,
            localPeerId = leaderId,
            leaderId = leaderId,
            initialMasterSecret = initialSecret,
            initialEpochNumber = 1L
        )

        val peer = GroupEpochManager(
            groupId = testGroupId,
            localPeerId = peer1Id,
            leaderId = leaderId,
            initialMasterSecret = initialSecret,
            initialEpochNumber = 1L
        )

        val baseTime = currentTimeMillis()

        // A lagging peer sends a packet using Epoch 1 before noticing rollover
        val epoch1Message = "Lagging rider transmission under Epoch 1".encodeToByteArray()
        val epoch1Ciphertext = peer.encrypt(epoch1Message)

        // Leader rolls over to Epoch 2
        val rolloverPacket = leader.initiateRollover(
            transitionWindowMs = 30_000L,
            timestampMs = baseTime
        )
        peer.handleEpochRollover(rolloverPacket, currentTimeMs = baseTime + 50L)

        // New transmission under Epoch 2
        val epoch2Message = "Lead rider transmission under Epoch 2".encodeToByteArray()
        val epoch2Ciphertext = leader.encrypt(epoch2Message)

        // During transition window (e.g. at baseTime + 5,000ms), BOTH must decrypt concurrently!
        val rxTime = baseTime + 5_000L

        val rxEpoch1OnLeader = leader.decrypt(epoch1Ciphertext, timestampMs = rxTime)
        assertNotNull(rxEpoch1OnLeader, "Leader must decrypt Epoch 1 frames during transition window")
        assertEquals("Lagging rider transmission under Epoch 1", rxEpoch1OnLeader.decodeToString())

        val rxEpoch2OnPeer = peer.decrypt(epoch2Ciphertext, timestampMs = rxTime)
        assertNotNull(rxEpoch2OnPeer, "Peer must decrypt Epoch 2 frames")
        assertEquals("Lead rider transmission under Epoch 2", rxEpoch2OnPeer.decodeToString())
    }

    @Test
    fun testZeroPacketDropsDuringContinuousVoiceStreamMigration() = runTest {
        val sender = GroupEpochManager(
            groupId = testGroupId,
            localPeerId = leaderId,
            leaderId = leaderId,
            initialMasterSecret = initialSecret,
            initialEpochNumber = 1L
        )

        val receiver = GroupEpochManager(
            groupId = testGroupId,
            localPeerId = peer1Id,
            leaderId = leaderId,
            initialMasterSecret = initialSecret,
            initialEpochNumber = 1L
        )

        val baseTime = currentTimeMillis()
        val decryptedFrames = mutableListOf<String>()

        // Transmit 40 voice frames sequentially:
        // Frames 1..20 sent on Epoch 1
        // At frame 21, rollover occurs to Epoch 2
        // Frames 21..40 sent on Epoch 2
        val laggingPeer = GroupEpochManager(
            groupId = testGroupId,
            localPeerId = peer2Id,
            leaderId = leaderId,
            initialMasterSecret = initialSecret,
            initialEpochNumber = 1L
        )

        for (frameSeq in 1..40) {
            val frameTime = baseTime + (frameSeq * 20L) // 20ms audio frame intervals

            if (frameSeq == 21) {
                // Leader initiates rollover and broadcasts control packet
                val rolloverPacket = sender.initiateRollover(
                    transitionWindowMs = 30_000L,
                    timestampMs = frameTime
                )
                // Receiver processes broadcast rollover at frame 21
                receiver.handleEpochRollover(rolloverPacket, currentTimeMs = frameTime)
            }

            // Normal voice stream from sender
            val framePlaintext = "pcm-voice-frame-$frameSeq".encodeToByteArray()
            val ciphertext = sender.encrypt(framePlaintext)

            val decrypted = receiver.decrypt(ciphertext, timestampMs = frameTime)
            assertNotNull(decrypted, "Frame $frameSeq must not be dropped during dynamic migration")
            decryptedFrames.add(decrypted.decodeToString())

            // During transition window (frames 22..30), lagging peer is still transmitting on Epoch 1
            if (frameSeq in 22..30) {
                val laggingText = "lagging-voice-frame-$frameSeq".encodeToByteArray()
                val laggingCiphertext = laggingPeer.encrypt(laggingText)
                val laggingDecrypted = receiver.decrypt(laggingCiphertext, timestampMs = frameTime)
                assertNotNull(laggingDecrypted, "Lagging peer Epoch 1 frame $frameSeq must be decrypted concurrently")
                assertEquals("lagging-voice-frame-$frameSeq", laggingDecrypted.decodeToString())
            }
        }

        assertEquals(40, decryptedFrames.size, "Exactly 40/40 primary voice frames must be delivered without drop")
        assertEquals("pcm-voice-frame-1", decryptedFrames.first())
        assertEquals("pcm-voice-frame-40", decryptedFrames.last())
    }

    @Test
    fun testPruningAndZeroizationAfterTransitionWindowExpires() = runTest {
        val manager = GroupEpochManager(
            groupId = testGroupId,
            localPeerId = leaderId,
            leaderId = leaderId,
            initialMasterSecret = initialSecret,
            initialEpochNumber = 1L
        )

        val baseTime = 100_000L
        val shortWindowMs = 2_000L // 2-second transition window

        val oldEpochCiphertext = manager.encrypt("Old epoch confidential data".encodeToByteArray())

        manager.initiateRollover(
            transitionWindowMs = shortWindowMs,
            timestampMs = baseTime
        )

        // 1. Within transition window (< 2000ms): old epoch decrypts
        val insideWindow = manager.decrypt(oldEpochCiphertext, timestampMs = baseTime + 1_000L)
        assertNotNull(insideWindow)
        assertEquals("Old epoch confidential data", insideWindow.decodeToString())
        assertFalse(manager.pruneExpiredEpochs(currentTimeMs = baseTime + 1_000L))

        // 2. Beyond transition window (> 2000ms): prune should execute
        val pruned = manager.pruneExpiredEpochs(currentTimeMs = baseTime + 2_500L)
        assertTrue(pruned, "Expired epoch must be pruned after deadline")
        assertNull(manager.previousEpoch, "Previous epoch must be cleared")

        // 3. Old epoch data arriving after pruning fails decryption
        val afterPrune = manager.decrypt(oldEpochCiphertext, timestampMs = baseTime + 3_000L)
        assertNull(afterPrune, "Packets encrypted under retired epoch key must be rejected")
    }

    @Test
    fun testRogueAndForgedRolloverRejections() = runTest {
        val nonLeader = GroupEpochManager(
            groupId = testGroupId,
            localPeerId = peer1Id,
            leaderId = leaderId,
            initialMasterSecret = initialSecret,
            initialEpochNumber = 1L
        )

        // 1. Non-leader cannot initiate rollover
        var nonLeaderThrew = false
        try {
            nonLeader.initiateRollover()
        } catch (_: IllegalStateException) {
            nonLeaderThrew = true
        }
        assertTrue(nonLeaderThrew, "Non-leader initiation must throw IllegalStateException")

        // 2. Forged signature rollover packet rejected
        val forgedPayload = EpochRolloverPayload(
            groupId = testGroupId,
            newEpochNumber = 2L,
            leaderId = leaderId,
            salt = ByteArray(32) { 0x01 },
            timestampMs = currentTimeMillis(),
            transitionWindowMs = 30_000L,
            signature = ByteArray(32) { 0x99.toByte() } // Corrupt signature
        )
        val forgedPacket = MeshPacket.Control(
            senderId = leaderId,
            type = ControlType.EPOCH_ROLLOVER,
            payload = kotlinx.serialization.json.Json.encodeToString(forgedPayload).encodeToByteArray()
        )

        assertFalse(nonLeader.handleEpochRollover(forgedPacket), "Forged signature rollover must be rejected")

        // 3. Outdated epoch number rejected
        val outdatedPayload = EpochRolloverPayload(
            groupId = testGroupId,
            newEpochNumber = 1L, // Same as current epoch, must be strictly greater
            leaderId = leaderId,
            salt = ByteArray(32) { 0x02 },
            timestampMs = currentTimeMillis(),
            transitionWindowMs = 30_000L,
            signature = ByteArray(32)
        )
        val outdatedPacket = MeshPacket.Control(
            senderId = leaderId,
            type = ControlType.EPOCH_ROLLOVER,
            payload = kotlinx.serialization.json.Json.encodeToString(outdatedPayload).encodeToByteArray()
        )
        assertFalse(nonLeader.handleEpochRollover(outdatedPacket), "Outdated epoch rollover must be rejected")
    }

    @Test
    fun testEpochConsensusBeaconing() = runTest {
        val leader = GroupEpochManager(
            groupId = testGroupId,
            localPeerId = leaderId,
            leaderId = leaderId,
            initialMasterSecret = initialSecret,
            initialEpochNumber = 3L
        )

        val peer = GroupEpochManager(
            groupId = testGroupId,
            localPeerId = peer1Id,
            leaderId = leaderId,
            initialMasterSecret = initialSecret,
            initialEpochNumber = 1L
        )

        val beaconPacket = leader.createEpochBeacon()
        assertEquals(ControlType.EPOCH_BEACON, beaconPacket.type)

        val heardEpoch = peer.handleEpochBeacon(beaconPacket)
        assertEquals(3L, heardEpoch, "Peer must decode current consensus epoch from beacon")
    }
}
