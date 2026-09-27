package org.nowni.intercom_alpha.mesh

import org.nowni.intercom_alpha.randomUUID
import kotlin.random.Random

/**
 * Multiplatform RFC 8439 ChaCha20-Poly1305 AEAD cipher for securing
 * mesh audio frames and control signaling packets.
 */
class PacketCipher(
    val key: ByteArray
) {
    init {
        require(key.size == 32) { "Group key must be exactly 32 bytes (256-bit)" }
    }

    /**
     * Encrypts the [plaintext] with optional [associatedData].
     *
     * @return 12-byte Nonce + Ciphertext + 16-byte Poly1305 Authentication Tag
     */
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray {
        val nonce = ByteArray(12)
        Random.nextBytes(nonce)
        val (ciphertext, tag) = ChaCha20Poly1305.encrypt(key, nonce, plaintext, associatedData)
        
        val result = ByteArray(12 + ciphertext.size + 16)
        nonce.copyInto(result, 0)
        ciphertext.copyInto(result, 12)
        tag.copyInto(result, 12 + ciphertext.size)
        return result
    }

    /**
     * Authenticates and decrypts [encryptedData] with optional [associatedData].
     *
     * @param encryptedData Nonce (12 bytes) + Ciphertext + Tag (16 bytes)
     * @return Decrypted plaintext, or `null` if authentication verification fails or payload is malformed.
     */
    fun decrypt(encryptedData: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray? {
        if (encryptedData.size < 12 + 16) return null
        val nonce = encryptedData.copyOfRange(0, 12)
        val ciphertextSize = encryptedData.size - 12 - 16
        val ciphertext = encryptedData.copyOfRange(12, 12 + ciphertextSize)
        val tag = encryptedData.copyOfRange(12 + ciphertextSize, encryptedData.size)

        return ChaCha20Poly1305.decrypt(key, nonce, ciphertext, tag, associatedData)
    }

    companion object {
        /**
         * Derives a 32-byte group key from an invite secret or group ID using SHA-256.
         */
        fun deriveKey(secret: String): ByteArray {
            return Sha256.digest(secret.encodeToByteArray())
        }

        fun generateRandomKey(): ByteArray {
            val key = ByteArray(32)
            Random.nextBytes(key)
            return key
        }
    }
}

/**
 * Pure Kotlin RFC 8439 ChaCha20-Poly1305 Authenticated Encryption with Associated Data (AEAD).
 */
object ChaCha20Poly1305 {

    fun encrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray = ByteArray(0)
    ): Pair<ByteArray, ByteArray> {
        require(key.size == 32) { "Key must be 32 bytes" }
        require(nonce.size == 12) { "Nonce must be 12 bytes" }

        val otk = poly1305KeyGen(key, nonce)
        val ciphertext = ChaCha20.process(key, nonce, counter = 1, input = plaintext)

        val macData = constructMacData(associatedData, ciphertext)
        val tag = Poly1305.mac(macData, otk)

        return Pair(ciphertext, tag)
    }

    fun decrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        tag: ByteArray,
        associatedData: ByteArray = ByteArray(0)
    ): ByteArray? {
        require(key.size == 32) { "Key must be 32 bytes" }
        require(nonce.size == 12) { "Nonce must be 12 bytes" }
        if (tag.size != 16) return null

        val otk = poly1305KeyGen(key, nonce)
        val macData = constructMacData(associatedData, ciphertext)
        val expectedTag = Poly1305.mac(macData, otk)

        if (!constantTimeEquals(expectedTag, tag)) {
            return null
        }

        return ChaCha20.process(key, nonce, counter = 1, input = ciphertext)
    }

    private fun poly1305KeyGen(key: ByteArray, nonce: ByteArray): ByteArray {
        val block = ChaCha20.process(key, nonce, counter = 0, input = ByteArray(64))
        return block.copyOfRange(0, 32)
    }

    private fun constructMacData(aad: ByteArray, ciphertext: ByteArray): ByteArray {
        val aadPad = (16 - (aad.size % 16)) % 16
        val ctPad = (16 - (ciphertext.size % 16)) % 16
        val totalSize = aad.size + aadPad + ciphertext.size + ctPad + 8 + 8
        val out = ByteArray(totalSize)

        var offset = 0
        aad.copyInto(out, offset)
        offset += aad.size + aadPad // padding zeros are default 0 in ByteArray

        ciphertext.copyInto(out, offset)
        offset += ciphertext.size + ctPad

        writeLongLe(out, offset, aad.size.toLong())
        offset += 8
        writeLongLe(out, offset, ciphertext.size.toLong())

        return out
    }

    private fun writeLongLe(buf: ByteArray, offset: Int, value: Long) {
        for (i in 0 until 8) {
            buf[offset + i] = ((value ushr (i * 8)) and 0xFF).toByte()
        }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
        }
        return diff == 0
    }
}

/**
 * Pure Kotlin RFC 8439 ChaCha20 stream cipher.
 */
object ChaCha20 {

    fun process(key: ByteArray, nonce: ByteArray, counter: Int, input: ByteArray): ByteArray {
        val output = ByteArray(input.size)
        val state = IntArray(16)
        val workingState = IntArray(16)
        val keyStreamBlock = ByteArray(64)

        var blockCounter = counter
        var inOffset = 0
        var remaining = input.size

        while (remaining > 0) {
            setupState(state, key, nonce, blockCounter)
            state.copyInto(workingState)

            for (i in 0 until 10) {
                // Column round
                quarterRound(workingState, 0, 4, 8, 12)
                quarterRound(workingState, 1, 5, 9, 13)
                quarterRound(workingState, 2, 6, 10, 14)
                quarterRound(workingState, 3, 7, 11, 15)
                // Diagonal round
                quarterRound(workingState, 0, 5, 10, 15)
                quarterRound(workingState, 1, 6, 11, 12)
                quarterRound(workingState, 2, 7, 8, 13)
                quarterRound(workingState, 3, 4, 9, 14)
            }

            for (i in 0 until 16) {
                workingState[i] += state[i]
                writeIntLe(keyStreamBlock, i * 4, workingState[i])
            }

            val take = minOf(remaining, 64)
            for (i in 0 until take) {
                output[inOffset + i] = (input[inOffset + i].toInt() xor keyStreamBlock[i].toInt()).toByte()
            }

            inOffset += take
            remaining -= take
            blockCounter++
        }

        return output
    }

    private fun setupState(state: IntArray, key: ByteArray, nonce: ByteArray, counter: Int) {
        // "expand 32-byte k" constants
        state[0] = 0x61707865
        state[1] = 0x3320646e
        state[2] = 0x79622d32
        state[3] = 0x6b206574

        // 256-bit key
        for (i in 0 until 8) {
            state[4 + i] = readIntLe(key, i * 4)
        }

        // 32-bit counter
        state[12] = counter

        // 96-bit nonce
        for (i in 0 until 3) {
            state[13 + i] = readIntLe(nonce, i * 4)
        }
    }

    private fun quarterRound(state: IntArray, a: Int, b: Int, c: Int, d: Int) {
        state[a] += state[b]; state[d] = (state[d] xor state[a]).rotateLeft(16)
        state[c] += state[d]; state[b] = (state[b] xor state[c]).rotateLeft(12)
        state[a] += state[b]; state[d] = (state[d] xor state[a]).rotateLeft(8)
        state[c] += state[d]; state[b] = (state[b] xor state[c]).rotateLeft(7)
    }

    private fun readIntLe(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xFF) or
                ((buf[offset + 1].toInt() and 0xFF) shl 8) or
                ((buf[offset + 2].toInt() and 0xFF) shl 16) or
                ((buf[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun writeIntLe(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}

/**
 * Pure Kotlin RFC 8439 Poly1305 One-Time Authenticator using 26-bit limbs.
 */
object Poly1305 {

    fun mac(message: ByteArray, key: ByteArray): ByteArray {
        require(key.size == 32) { "Poly1305 key must be 32 bytes" }

        // Clamp r directly on byte array per RFC 8439
        val clampedR = ByteArray(16)
        key.copyInto(clampedR, 0, 0, 16)
        clampedR[3] = (clampedR[3].toInt() and 15).toByte()
        clampedR[7] = (clampedR[7].toInt() and 15).toByte()
        clampedR[11] = (clampedR[11].toInt() and 15).toByte()
        clampedR[15] = (clampedR[15].toInt() and 15).toByte()
        clampedR[4] = (clampedR[4].toInt() and 252).toByte()
        clampedR[8] = (clampedR[8].toInt() and 252).toByte()
        clampedR[12] = (clampedR[12].toInt() and 252).toByte()

        val r0 = (readLongLe(clampedR, 0, 4) and 0x3ffffffL)
        val r1 = ((readLongLe(clampedR, 3, 4) ushr 2) and 0x3ffffffL)
        val r2 = ((readLongLe(clampedR, 6, 4) ushr 4) and 0x3ffffffL)
        val r3 = ((readLongLe(clampedR, 9, 4) ushr 6) and 0x3ffffffL)
        val r4 = ((readLongLe(clampedR, 12, 4) ushr 8) and 0x3ffffffL)

        // Precompute 5 * r for reduction mod 2^130 - 5
        val s1 = r1 * 5
        val s2 = r2 * 5
        val s3 = r3 * 5
        val s4 = r4 * 5

        var h0 = 0L
        var h1 = 0L
        var h2 = 0L
        var h3 = 0L
        var h4 = 0L

        var offset = 0
        val len = message.size

        while (offset < len) {
            val blockSize = minOf(16, len - offset)
            val block = ByteArray(17)
            message.copyInto(block, 0, offset, offset + blockSize)
            block[blockSize] = 0x01.toByte() // Append 0x01 byte per RFC 8439

            val b0 = (readLongLe(block, 0, 4) and 0x3ffffffL)
            val b1 = ((readLongLe(block, 3, 4) ushr 2) and 0x3ffffffL)
            val b2 = ((readLongLe(block, 6, 4) ushr 4) and 0x3ffffffL)
            val b3 = ((readLongLe(block, 9, 4) ushr 6) and 0x3ffffffL)
            val b4 = ((readLongLe(block, 12, 5) ushr 8) and 0x3ffffffL)

            h0 += b0
            h1 += b1
            h2 += b2
            h3 += b3
            h4 += b4

            // Multiply h * r mod 2^130 - 5
            val d0 = h0 * r0 + h1 * s4 + h2 * s3 + h3 * s2 + h4 * s1
            val d1 = h0 * r1 + h1 * r0 + h2 * s4 + h3 * s3 + h4 * s2
            val d2 = h0 * r2 + h1 * r1 + h2 * r0 + h3 * s4 + h4 * s3
            val d3 = h0 * r3 + h1 * r2 + h2 * r1 + h3 * r0 + h4 * s4
            val d4 = h0 * r4 + h1 * r3 + h2 * r2 + h3 * r1 + h4 * r0

            // Carry propagation
            var c: Long
            h0 = d0 and 0x3ffffffL; c = d0 ushr 26
            val t1 = d1 + c
            h1 = t1 and 0x3ffffffL; c = t1 ushr 26
            val t2 = d2 + c
            h2 = t2 and 0x3ffffffL; c = t2 ushr 26
            val t3 = d3 + c
            h3 = t3 and 0x3ffffffL; c = t3 ushr 26
            val t4 = d4 + c
            h4 = t4 and 0x3ffffffL; c = t4 ushr 26
            h0 += c * 5
            c = h0 ushr 26
            h0 = h0 and 0x3ffffffL
            h1 += c

            offset += blockSize
        }

        // Full carry
        var c = h1 ushr 26; h1 = h1 and 0x3ffffffL
        h2 += c; c = h2 ushr 26; h2 = h2 and 0x3ffffffL
        h3 += c; c = h3 ushr 26; h3 = h3 and 0x3ffffffL
        h4 += c; c = h4 ushr 26; h4 = h4 and 0x3ffffffL
        h0 += c * 5; c = h0 ushr 26; h0 = h0 and 0x3ffffffL
        h1 += c

        // Compute h - p (mod 2^130 - 5)
        var g0 = h0 + 5; c = g0 ushr 26; g0 = g0 and 0x3ffffffL
        var g1 = h1 + c; c = g1 ushr 26; g1 = g1 and 0x3ffffffL
        var g2 = h2 + c; c = g2 ushr 26; g2 = g2 and 0x3ffffffL
        var g3 = h3 + c; c = g3 ushr 26; g3 = g3 and 0x3ffffffL
        var g4 = h4 + c - (1L shl 26)

        // Select h if h < p else g
        val mask = (g4 ushr 63) - 1
        val nmask = mask.inv()
        h0 = (h0 and nmask) or (g0 and mask)
        h1 = (h1 and nmask) or (g1 and mask)
        h2 = (h2 and nmask) or (g2 and mask)
        h3 = (h3 and nmask) or (g3 and mask)
        h4 = (h4 and nmask) or ((g4 and 0x3ffffffL) and mask)

        // Convert back to 128-bit integer
        val word0 = h0 or (h1 shl 26) or ((h2 and 0xFFFL) shl 52)
        val word1 = (h2 ushr 12) or (h3 shl 14) or (h4 shl 40)

        // Read s and add to h (mod 2^128)
        val s0 = readLongLe(key, 16, 8)
        val s1Key = readLongLe(key, 24, 8)

        val out = ByteArray(16)
        val uLo = add64(word0, s0)
        val uHi = word1 + s1Key + uLo.second

        writeLongLe(out, 0, uLo.first)
        writeLongLe(out, 8, uHi)

        return out
    }

    private fun add64(a: Long, b: Long): Pair<Long, Long> {
        val aLo = a and 0xFFFFFFFFL
        val aHi = a ushr 32
        val bLo = b and 0xFFFFFFFFL
        val bHi = b ushr 32

        val sumLo = aLo + bLo
        val carryLo = sumLo ushr 32
        val sumHi = aHi + bHi + carryLo
        val carryHi = sumHi ushr 32

        val res = (sumLo and 0xFFFFFFFFL) or ((sumHi and 0xFFFFFFFFL) shl 32)
        return Pair(res, carryHi)
    }

    private fun compareUnsigned(a: Long, b: Long): Int {
        return (a xor Long.MIN_VALUE).compareTo(b xor Long.MIN_VALUE)
    }

    private fun readLongLe(buf: ByteArray, offset: Int, count: Int): Long {
        var res = 0L
        for (i in 0 until count) {
            if (offset + i < buf.size) {
                res = res or ((buf[offset + i].toLong() and 0xFFL) shl (i * 8))
            }
        }
        return res
    }

    private fun writeLongLe(buf: ByteArray, offset: Int, value: Long) {
        for (i in 0 until 8) {
            buf[offset + i] = ((value ushr (i * 8)) and 0xFF).toByte()
        }
    }
}

/**
 * Pure Kotlin FIPS 180-4 SHA-256 implementation.
 */
object Sha256 {

    private val K = intArrayOf(
        0x428a2f98.toInt(), 0x71374491.toInt(), 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
        0x3956c25b.toInt(), 0x59f111f1.toInt(), 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01.toInt(), 0x243185be.toInt(), 0x550c7dc3.toInt(),
        0x72be5d74.toInt(), 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6.toInt(), 0x240ca1cc.toInt(),
        0x2de92c6f.toInt(), 0x4a7484aa.toInt(), 0x5cb0a9dc.toInt(), 0x76f988da.toInt(),
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
        0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351.toInt(), 0x14292967.toInt(),
        0x27b70a85.toInt(), 0x2e1b2138.toInt(), 0x4d2c6dfc.toInt(), 0x53380d13.toInt(),
        0x650a7354.toInt(), 0x766a0abb.toInt(), 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
        0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070.toInt(),
        0x19a4c116.toInt(), 0x1e376c08.toInt(), 0x2748774c.toInt(), 0x34b0bcb5.toInt(),
        0x391c0cb3.toInt(), 0x4ed8aa4a.toInt(), 0x5b9cca4f.toInt(), 0x682e6ff3.toInt(),
        0x748f82ee.toInt(), 0x78a5636f.toInt(), 0x84c87814.toInt(), 0x8cc70208.toInt(),
        0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt()
    )

    fun digest(data: ByteArray): ByteArray {
        var h0 = 0x6a09e667.toInt()
        var h1 = 0xbb67ae85.toInt()
        var h2 = 0x3c6ef372.toInt()
        var h3 = 0xa54ff53a.toInt()
        var h4 = 0x510e527f.toInt()
        var h5 = 0x9b05688c.toInt()
        var h6 = 0x1f83d9ab.toInt()
        var h7 = 0x5be0cd19.toInt()

        val bitLen = data.size.toLong() * 8
        val padLen = (64 - ((data.size + 9) % 64)) % 64
        val padded = ByteArray(data.size + 1 + padLen + 8)
        data.copyInto(padded, 0)
        padded[data.size] = 0x80.toByte()

        for (i in 0 until 8) {
            padded[padded.size - 1 - i] = ((bitLen ushr (i * 8)) and 0xFF).toByte()
        }

        val w = IntArray(64)
        for (chunk in padded.indices step 64) {
            for (i in 0 until 16) {
                val off = chunk + i * 4
                w[i] = ((padded[off].toInt() and 0xFF) shl 24) or
                        ((padded[off + 1].toInt() and 0xFF) shl 16) or
                        ((padded[off + 2].toInt() and 0xFF) shl 8) or
                        (padded[off + 3].toInt() and 0xFF)
            }
            for (i in 16 until 64) {
                val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
                val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }

            var a = h0
            var b = h1
            var c = h2
            var d = h3
            var e = h4
            var f = h5
            var g = h6
            var h = h7

            for (i in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = h + s1 + ch + K[i] + w[i]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj

                h = g
                g = f
                f = e
                e = d + temp1
                d = c
                c = b
                b = a
                a = temp1 + temp2
            }

            h0 += a
            h1 += b
            h2 += c
            h3 += d
            h4 += e
            h5 += f
            h6 += g
            h7 += h
        }

        val out = ByteArray(32)
        val hashes = intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7)
        for (i in 0 until 8) {
            out[i * 4] = ((hashes[i] ushr 24) and 0xFF).toByte()
            out[i * 4 + 1] = ((hashes[i] ushr 16) and 0xFF).toByte()
            out[i * 4 + 2] = ((hashes[i] ushr 8) and 0xFF).toByte()
            out[i * 4 + 3] = (hashes[i] and 0xFF).toByte()
        }
        return out
    }
}
