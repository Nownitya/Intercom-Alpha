package org.nowni.intercom_alpha.crypto

import org.nowni.intercom_alpha.mesh.HkdfSha256
import org.nowni.intercom_alpha.mesh.HmacSha256
import kotlin.test.*

class MeshKeyRatchetTest {

    private val masterSecret = "convoy-super-secret-group-key-32b!".encodeToByteArray()

    @Test
    fun testHmacSha256OfficialTestVectors() {
        // RFC 4231 Test Case 1
        // Key: 20 bytes of 0x0b
        val key1 = ByteArray(20) { 0x0b.toByte() }
        val data1 = "Hi There".encodeToByteArray()
        val expected1 = "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7"
        val actual1 = HmacSha256.mac(key1, data1).toHex()
        assertEquals(expected1, actual1, "RFC 4231 Test Case 1 failed")

        // RFC 4231 Test Case 2
        // Key: "Jefe", Data: "what do ya want for nothing?"
        val key2 = "Jefe".encodeToByteArray()
        val data2 = "what do ya want for nothing?".encodeToByteArray()
        val expected2 = "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843"
        val actual2 = HmacSha256.mac(key2, data2).toHex()
        assertEquals(expected2, actual2, "RFC 4231 Test Case 2 failed")
    }

    @Test
    fun testHkdfSha256OfficialTestVectors() {
        // RFC 5869 Test Case 1
        val ikm = ByteArray(22) { 0x0b.toByte() }
        val salt = ByteArray(13) { i -> i.toByte() }
        val info = ByteArray(10) { (0xf0 + it).toByte() }
        val length = 42

        val okm = HkdfSha256.deriveSecrets(salt, ikm, info, length)
        assertEquals(42, okm.size)

        // Expected OKM first 16 bytes: 3cb25f25faacd57a90434f64d0362f2a
        val expectedPrefix = "3cb25f25faacd57a90434f64d0362f2a"
        assertEquals(expectedPrefix, okm.copyOfRange(0, 16).toHex())
    }

    @Test
    fun testSequentialEncryptionAndDecryption() {
        val alice = MeshKeyRatchet(localNodeId = "Alice", masterSecret = masterSecret)
        val bob = MeshKeyRatchet(localNodeId = "Bob", masterSecret = masterSecret)

        for (i in 0 until 10) {
            val message = "Hello from Alice message #$i".encodeToByteArray()
            val envelope = alice.encrypt(message)

            assertEquals(i, envelope.ratchetStep)
            assertEquals("Alice", envelope.senderId)

            val decrypted = bob.decrypt(envelope)
            assertNotNull(decrypted)
            assertEquals("Hello from Alice message #$i", decrypted.decodeToString())
        }

        assertEquals(10, alice.currentSendingStep)
    }

    @Test
    fun testOutOfOrderPacketArrival() {
        val alice = MeshKeyRatchet(localNodeId = "Alice", masterSecret = masterSecret)
        val bob = MeshKeyRatchet(localNodeId = "Bob", masterSecret = masterSecret)

        // Alice produces 5 messages (steps 0, 1, 2, 3, 4)
        val envelopes = (0 until 5).map { i ->
            alice.encrypt("Message $i".encodeToByteArray())
        }

        // Bob receives in order: 3, 1, 0, 4, 2
        val deliveryOrder = listOf(3, 1, 0, 4, 2)
        val decryptedResults = mutableMapOf<Int, String>()

        for (step in deliveryOrder) {
            val envelope = envelopes[step]
            val decrypted = bob.decrypt(envelope)
            assertNotNull(decrypted, "Step $step should decrypt even out of order")
            decryptedResults[step] = decrypted.decodeToString()
        }

        // Verify all 5 messages recovered accurately
        for (i in 0 until 5) {
            assertEquals("Message $i", decryptedResults[i])
        }

        // Verify skipped keys cache is now drained
        assertEquals(0, bob.getSkippedKeyCount("Alice"))
    }

    @Test
    fun testReplayAttackPrevention() {
        val alice = MeshKeyRatchet(localNodeId = "Alice", masterSecret = masterSecret)
        val bob = MeshKeyRatchet(localNodeId = "Bob", masterSecret = masterSecret)

        val envelope = alice.encrypt("Secret broadcast".encodeToByteArray())

        // First decryption succeeds
        val firstAttempt = bob.decrypt(envelope)
        assertNotNull(firstAttempt)

        // Replayed message with the same step MUST fail (key already consumed)
        val replayAttempt = bob.decrypt(envelope)
        assertNull(replayAttempt, "Replayed message key should not be usable twice")
    }

    @Test
    fun testCorruptedCiphertextRejection() {
        val alice = MeshKeyRatchet(localNodeId = "Alice", masterSecret = masterSecret)
        val bob = MeshKeyRatchet(localNodeId = "Bob", masterSecret = masterSecret)

        val envelope = alice.encrypt("Top secret convoy waypoint".encodeToByteArray())

        // Corrupt ciphertext
        val corruptedCiphertext = envelope.ciphertext.copyOf()
        corruptedCiphertext[0] = (corruptedCiphertext[0].toInt() xor 0xFF).toByte()
        val badEnvelope = envelope.copy(ciphertext = corruptedCiphertext)

        val result = bob.decrypt(badEnvelope)
        assertNull(result, "Tampered ciphertext must fail authentication")
    }

    @Test
    fun testCorruptedTagRejection() {
        val alice = MeshKeyRatchet(localNodeId = "Alice", masterSecret = masterSecret)
        val bob = MeshKeyRatchet(localNodeId = "Bob", masterSecret = masterSecret)

        val envelope = alice.encrypt("Payload".encodeToByteArray())

        // Corrupt auth tag
        val corruptedTag = envelope.tag.copyOf()
        corruptedTag[15] = (corruptedTag[15].toInt() xor 0x01).toByte()
        val badEnvelope = envelope.copy(tag = corruptedTag)

        val result = bob.decrypt(badEnvelope)
        assertNull(result, "Tampered auth tag must fail authentication")
    }

    @Test
    fun testMaxSkippedKeyBoundsProtection() {
        // Create ratchet with tight skipped key limit of 5
        val alice = MeshKeyRatchet(localNodeId = "Alice", masterSecret = masterSecret, maxSkippedKeysPerPeer = 5)
        val bob = MeshKeyRatchet(localNodeId = "Bob", masterSecret = masterSecret, maxSkippedKeysPerPeer = 5)

        // Generate 10 packets from Alice
        val envelopes = (0 until 10).map { alice.encrypt("Msg $it".encodeToByteArray()) }

        // Jump ahead by 8 (exceeds maxSkippedKeysPerPeer of 5)
        val jumpTooFar = bob.decrypt(envelopes[8])
        assertNull(jumpTooFar, "Should reject jump exceeding maxSkippedKeysPerPeer")

        // Jump within limit (step 4, gap of 4 <= 5)
        val validJump = bob.decrypt(envelopes[4])
        assertNotNull(validJump)
        assertEquals("Msg 4", validJump.decodeToString())
    }

    @Test
    fun testMultiPartyMeshCommunication() {
        val alice = MeshKeyRatchet(localNodeId = "Alice", masterSecret = masterSecret)
        val bob = MeshKeyRatchet(localNodeId = "Bob", masterSecret = masterSecret)
        val charlie = MeshKeyRatchet(localNodeId = "Charlie", masterSecret = masterSecret)

        // Alice sends
        val aliceMsg = alice.encrypt("Convoy lead: Turn left".encodeToByteArray())
        assertEquals("Convoy lead: Turn left", bob.decrypt(aliceMsg)?.decodeToString())
        assertEquals("Convoy lead: Turn left", charlie.decrypt(aliceMsg)?.decodeToString())

        // Bob sends
        val bobMsg = bob.encrypt("Convoy mid: Copy that".encodeToByteArray())
        assertEquals("Convoy mid: Copy that", alice.decrypt(bobMsg)?.decodeToString())
        assertEquals("Convoy mid: Copy that", charlie.decrypt(bobMsg)?.decodeToString())

        // Charlie sends
        val charlieMsg = charlie.encrypt("Convoy sweep: Tail clear".encodeToByteArray())
        assertEquals("Convoy sweep: Tail clear", alice.decrypt(charlieMsg)?.decodeToString())
        assertEquals("Convoy sweep: Tail clear", bob.decrypt(charlieMsg)?.decodeToString())
    }

    @Test
    fun testSecureWipe() {
        val alice = MeshKeyRatchet(localNodeId = "Alice", masterSecret = masterSecret)
        alice.encrypt("Sensitive".encodeToByteArray())
        alice.destroy()

        // After destroy, encrypting should fail or use zeroized keys
        assertFails {
            // Next expansion from zeroized key
            alice.encrypt("Another".encodeToByteArray())
        }
    }

    private fun ByteArray.toHex(): String {
        return joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
