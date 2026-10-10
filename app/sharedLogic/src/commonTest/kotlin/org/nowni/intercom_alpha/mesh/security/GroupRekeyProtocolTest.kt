package org.nowni.intercom_alpha.mesh.security

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupRekeyProtocolTest {

    private val testBaseKey = ByteArray(32) { (it + 1).toByte() }
    private val groupId = "alpha-expedition-mesh"
    private val leaderNodeId = "rider-leader"
    private val memberNodeId = "rider-scout"

    @Test
    fun testHkdfDerivationReproducibility() {
        val salt = ByteArray(16) { 0x42 }
        val key1 = GroupRekeyProtocol.deriveEpochKey(testBaseKey, epoch = 2L, salt = salt)
        val key2 = GroupRekeyProtocol.deriveEpochKey(testBaseKey, epoch = 2L, salt = salt)
        val keyEpoch3 = GroupRekeyProtocol.deriveEpochKey(testBaseKey, epoch = 3L, salt = salt)

        assertEquals(32, key1.size)
        assertTrue(key1.contentEquals(key2), "HKDF output must be deterministic for identical inputs")
        assertFalse(key1.contentEquals(keyEpoch3), "Different epoch must derive different key")
    }

    @Test
    fun testInitiateAndReceiveRekeyOverMesh() = runTest {
        var simulatedTime = 100_000L
        val leader = GroupRekeyProtocol(
            groupId = groupId,
            localNodeId = leaderNodeId,
            baseKeyInput = testBaseKey,
            timeProvider = { simulatedTime }
        )
        val scout = GroupRekeyProtocol(
            groupId = groupId,
            localNodeId = memberNodeId,
            baseKeyInput = testBaseKey,
            timeProvider = { simulatedTime }
        )

        assertEquals(1L, leader.keyBuffer.activeEpoch)
        assertEquals(1L, scout.keyBuffer.activeEpoch)

        // Leader initiates epoch rotation
        val rekeyPacket = leader.initiateRekey(reason = RekeyTriggerReason.SCHEDULED_ROTATION)
        assertEquals(2L, rekeyPacket.targetEpoch)
        assertEquals(2L, leader.keyBuffer.activeEpoch)
        assertEquals(1L, leader.keyBuffer.previousEpoch)
        assertTrue(leader.keyBuffer.isGracePeriodActive)

        // Scout receives rekey control packet over mesh
        val result = scout.handleIncomingRekeyPacket(rekeyPacket, now = simulatedTime)
        assertTrue(result is RekeyResult.Accepted)
        assertEquals(2L, result.epoch)
        assertEquals(2L, scout.keyBuffer.activeEpoch)
        assertEquals(1L, scout.keyBuffer.previousEpoch)
        assertTrue(scout.keyBuffer.isGracePeriodActive)

        // Keys on both nodes must match
        assertTrue(leader.keyBuffer.activeKey.contentEquals(scout.keyBuffer.activeKey))
    }

    @Test
    fun testDualEpochGracePeriodDecryption() = runTest {
        var simulatedTime = 100_000L
        val leader = GroupRekeyProtocol(
            groupId = groupId,
            localNodeId = leaderNodeId,
            baseKeyInput = testBaseKey,
            gracePeriodMs = 10_000L,
            timeProvider = { simulatedTime }
        )
        val scout = GroupRekeyProtocol(
            groupId = groupId,
            localNodeId = memberNodeId,
            baseKeyInput = testBaseKey,
            gracePeriodMs = 10_000L,
            timeProvider = { simulatedTime }
        )

        // 1. Scout produces message in epoch 1
        val messageFromEpoch1 = "Tactical voice frame payload epoch 1".encodeToByteArray()
        val (epoch1, ciphertext1) = scout.encrypt(messageFromEpoch1, now = simulatedTime)
        assertEquals(1L, epoch1)

        // 2. Leader rotates to epoch 2, but scout hasn't rotated yet
        val rekeyPacket = leader.initiateRekey(reason = RekeyTriggerReason.SCHEDULED_ROTATION)

        // 3. Leader receives epoch 1 packet during grace period (5 seconds elapsed)
        simulatedTime += 5_000L
        val decryptedByLeader = leader.decrypt(
            epoch = epoch1,
            ciphertext = ciphertext1,
            now = simulatedTime
        )
        assertNotNull(decryptedByLeader)
        assertEquals("Tactical voice frame payload epoch 1", decryptedByLeader.decodeToString())

        // 4. Auto-decrypt also succeeds during grace period
        val autoDecrypted = leader.decryptAuto(ciphertext1, now = simulatedTime)
        assertNotNull(autoDecrypted)
        assertEquals("Tactical voice frame payload epoch 1", autoDecrypted.decodeToString())

        // 5. Scout now applies rekey packet
        scout.handleIncomingRekeyPacket(rekeyPacket, now = simulatedTime)

        // 6. Leader sends message in epoch 2
        val messageFromEpoch2 = "Tactical voice frame payload epoch 2".encodeToByteArray()
        val (epoch2, ciphertext2) = leader.encrypt(messageFromEpoch2, now = simulatedTime)
        assertEquals(2L, epoch2)

        val decryptedByScout = scout.decrypt(epoch2, ciphertext2, now = simulatedTime)
        assertNotNull(decryptedByScout)
        assertEquals("Tactical voice frame payload epoch 2", decryptedByScout.decodeToString())
    }

    @Test
    fun testGracePeriodExpirationAndZeroization() = runTest {
        var simulatedTime = 100_000L
        val leader = GroupRekeyProtocol(
            groupId = groupId,
            localNodeId = leaderNodeId,
            baseKeyInput = testBaseKey,
            gracePeriodMs = 10_000L,
            timeProvider = { simulatedTime }
        )

        val message = "Epoch 1 legacy message".encodeToByteArray()
        val (epoch1, ciphertext) = leader.encrypt(message, now = simulatedTime)

        // Initiate rekey -> enters grace period
        leader.initiateRekey()
        assertTrue(leader.keyBuffer.isGracePeriodActive)

        // Advance simulated time past grace period (+10,001 ms)
        simulatedTime += 10_001L

        // Decryption of old epoch must fail
        val decrypted = leader.decrypt(epoch = epoch1, ciphertext = ciphertext, now = simulatedTime)
        assertNull(decrypted, "Decryption of old epoch after grace period expiration must fail")

        // Previous epoch buffer should be wiped
        assertNull(leader.keyBuffer.previousKey)
        assertNull(leader.keyBuffer.previousEpoch)
        assertFalse(leader.keyBuffer.isGracePeriodActive)
    }

    @Test
    fun testAntiReplayProtection() = runTest {
        var simulatedTime = 100_000L
        val leader = GroupRekeyProtocol(
            groupId = groupId,
            localNodeId = leaderNodeId,
            baseKeyInput = testBaseKey,
            timeProvider = { simulatedTime }
        )
        val scout = GroupRekeyProtocol(
            groupId = groupId,
            localNodeId = memberNodeId,
            baseKeyInput = testBaseKey,
            timeProvider = { simulatedTime }
        )

        val packet = leader.initiateRekey()
        val firstResult = scout.handleIncomingRekeyPacket(packet, now = simulatedTime)
        assertTrue(firstResult is RekeyResult.Accepted)

        // Replaying exact same packet
        val replayedResult = scout.handleIncomingRekeyPacket(packet, now = simulatedTime)
        assertTrue(replayedResult is RekeyResult.Rejected)
        assertTrue(replayedResult.reason.contains("Replay detected"))
    }

    @Test
    fun testRejectStaleEpochPacket() = runTest {
        var simulatedTime = 100_000L
        val leader = GroupRekeyProtocol(
            groupId = groupId,
            localNodeId = leaderNodeId,
            baseKeyInput = testBaseKey,
            initialEpoch = 5L,
            timeProvider = { simulatedTime }
        )
        val scout = GroupRekeyProtocol(
            groupId = groupId,
            localNodeId = memberNodeId,
            baseKeyInput = testBaseKey,
            initialEpoch = 5L,
            timeProvider = { simulatedTime }
        )

        val packetWithLowerEpoch = RekeyControlPacket(
            groupId = groupId,
            initiatorId = leaderNodeId,
            targetEpoch = 3L, // Lower than active epoch 5
            epochKeyDerivationSalt = ByteArray(16),
            timestamp = simulatedTime,
            antiReplayCounter = 10L,
            authSignature = ByteArray(32)
        )

        val result = scout.handleIncomingRekeyPacket(packetWithLowerEpoch, now = simulatedTime)
        assertTrue(result is RekeyResult.Rejected)
        assertTrue(result.reason.contains("Stale or current epoch"))
    }

    @Test
    fun testRejectForgedSignaturePacket() = runTest {
        var simulatedTime = 100_000L
        val leader = GroupRekeyProtocol(
            groupId = groupId,
            localNodeId = leaderNodeId,
            baseKeyInput = testBaseKey,
            timeProvider = { simulatedTime }
        )
        val scout = GroupRekeyProtocol(
            groupId = groupId,
            localNodeId = memberNodeId,
            baseKeyInput = testBaseKey,
            timeProvider = { simulatedTime }
        )

        val legitimatePacket = leader.initiateRekey()
        // Tamper with signature
        val tamperedSignature = legitimatePacket.authSignature.copyOf()
        tamperedSignature[0] = (tamperedSignature[0].toInt() xor 0xFF).toByte()

        val forgedPacket = legitimatePacket.copy(authSignature = tamperedSignature)
        val result = scout.handleIncomingRekeyPacket(forgedPacket, now = simulatedTime)
        assertTrue(result is RekeyResult.Rejected)
        assertTrue(result.reason.contains("signature verification failed"))
    }

    @Test
    fun testWipeZeroizesKeys() = runTest {
        val leader = GroupRekeyProtocol(
            groupId = groupId,
            localNodeId = leaderNodeId,
            baseKeyInput = testBaseKey
        )
        leader.initiateRekey()
        assertTrue(leader.keyBuffer.activeKey.any { it != 0.toByte() })

        leader.wipe()
        assertEquals(0L, leader.keyBuffer.activeEpoch)
        assertTrue(leader.keyBuffer.activeKey.all { it == 0.toByte() })
        assertNull(leader.keyBuffer.previousKey)
    }
}
