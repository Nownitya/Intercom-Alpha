package org.nowni.intercom_alpha.mesh

import kotlin.test.*

class PacketCipherTest {

    @Test
    fun testRfc8439AeadTestVector() {
        // RFC 8439 Section 2.8.2
        val key = ByteArray(32) { (0x80 + it).toByte() }
        val nonce = byteArrayOf(
            0x07, 0x00, 0x00, 0x00,
            0x40, 0x41, 0x42, 0x43,
            0x44, 0x45, 0x46, 0x47
        )
        val aad = byteArrayOf(
            0x50, 0x51, 0x52, 0x53,
            0xc0.toByte(), 0xc1.toByte(), 0xc2.toByte(), 0xc3.toByte(),
            0xc4.toByte(), 0xc5.toByte(), 0xc6.toByte(), 0xc7.toByte()
        )
        val plaintext = "Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it.".encodeToByteArray()

        val (ciphertext, tag) = ChaCha20Poly1305.encrypt(key, nonce, plaintext, aad)

        assertEquals(114, ciphertext.size)
        assertEquals(16, tag.size)

        // Verify first few bytes and last few bytes of ciphertext per RFC 8439
        // 000: d3 1a 8d 34 64 8e 60 db 7b 86 af bc 53 ef 7e c2
        assertEquals(0xd3.toByte(), ciphertext[0])
        assertEquals(0x1a.toByte(), ciphertext[1])
        assertEquals(0x8d.toByte(), ciphertext[2])
        assertEquals(0x34.toByte(), ciphertext[3])
        assertEquals(0x61.toByte(), ciphertext[112])
        assertEquals(0x16.toByte(), ciphertext[113])

        // Verify tag per RFC 8439:
        // Tag: 1a:e1:0b:59:4f:09:e2:6a:7e:90:2e:cb:d0:60:06:91
        val expectedTag = byteArrayOf(
            0x1a, 0xe1.toByte(), 0x0b, 0x59,
            0x4f, 0x09, 0xe2.toByte(), 0x6a,
            0x7e, 0x90.toByte(), 0x2e, 0xcb.toByte(),
            0xd0.toByte(), 0x60, 0x06, 0x91.toByte()
        )
        assertContentEquals(expectedTag, tag)

        // Test decryption
        val decrypted = ChaCha20Poly1305.decrypt(key, nonce, ciphertext, tag, aad)
        assertNotNull(decrypted)
        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun testPacketCipherEncryptDecryptRoundTrip() {
        val groupKey = PacketCipher.deriveKey("INTERCOM-GROUP-SECRET-42")
        val cipher = PacketCipher(groupKey)

        val message = "Hello, secure BLE mesh voice frame!".encodeToByteArray()
        val encrypted = cipher.encrypt(message)

        // Should contain 12-byte nonce + message.size + 16-byte tag
        assertEquals(12 + message.size + 16, encrypted.size)

        val decrypted = cipher.decrypt(encrypted)
        assertNotNull(decrypted)
        assertContentEquals(message, decrypted)
        assertEquals("Hello, secure BLE mesh voice frame!", decrypted.decodeToString())
    }

    @Test
    fun testPacketCipherTamperDetection() {
        val groupKey = PacketCipher.deriveKey("SECRET-KEY")
        val cipher = PacketCipher(groupKey)

        val message = "Top secret audio frame".encodeToByteArray()
        val encrypted = cipher.encrypt(message)

        // Tamper ciphertext
        val tamperedCiphertext = encrypted.copyOf()
        tamperedCiphertext[15] = (tamperedCiphertext[15].toInt() xor 0x01).toByte()
        val decryptedFail1 = cipher.decrypt(tamperedCiphertext)
        assertNull(decryptedFail1, "Tampered ciphertext should fail authentication")

        // Tamper tag
        val tamperedTag = encrypted.copyOf()
        tamperedTag[tamperedTag.size - 1] = (tamperedTag[tamperedTag.size - 1].toInt() xor 0x01).toByte()
        val decryptedFail2 = cipher.decrypt(tamperedTag)
        assertNull(decryptedFail2, "Tampered tag should fail authentication")

        // Wrong key
        val wrongCipher = PacketCipher(PacketCipher.deriveKey("DIFFERENT-KEY"))
        val decryptedFail3 = wrongCipher.decrypt(encrypted)
        assertNull(decryptedFail3, "Decryption with wrong key should fail authentication")
    }

    @Test
    fun testSha256Digest() {
        val input = "Intercom-Alpha".encodeToByteArray()
        val hash = Sha256.digest(input)
        assertEquals(32, hash.size)

        // Empty string SHA-256
        val emptyHash = Sha256.digest(ByteArray(0))
        val expectedEmptyHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val hex = emptyHash.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        assertEquals(expectedEmptyHex, hex)
    }
}
