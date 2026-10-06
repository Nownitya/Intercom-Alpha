package org.nowni.intercom_alpha.group

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.mesh.ControlType
import org.nowni.intercom_alpha.mesh.HkdfSha256
import org.nowni.intercom_alpha.mesh.HmacSha256
import org.nowni.intercom_alpha.mesh.MeshPacket
import org.nowni.intercom_alpha.mesh.PacketCipher
import kotlin.random.Random

/**
 * Representation of an active cryptographic epoch for group mesh communications.
 */
class GroupEpoch(
    val epochNumber: Long,
    val masterSecret: ByteArray, // 32-byte RFC 8439 ChaCha20-Poly1305 key
    val activatedAtMs: Long,
    val leaderId: String
) {
    val cipher: PacketCipher = PacketCipher(masterSecret)

    fun zeroize() {
        masterSecret.fill(0)
    }
}

/**
 * Wire payload broadcast by group leader when rotating group cryptographic master secrets.
 */
@Serializable
data class EpochRolloverPayload(
    val groupId: String,
    val newEpochNumber: Long,
    val leaderId: String,
    val salt: ByteArray,
    val timestampMs: Long,
    val transitionWindowMs: Long = 30_000L,
    val signature: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EpochRolloverPayload) return false
        if (groupId != other.groupId) return false
        if (newEpochNumber != other.newEpochNumber) return false
        if (leaderId != other.leaderId) return false
        if (!salt.contentEquals(other.salt)) return false
        if (timestampMs != other.timestampMs) return false
        if (transitionWindowMs != other.transitionWindowMs) return false
        if (!signature.contentEquals(other.signature)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = groupId.hashCode()
        result = 31 * result + newEpochNumber.hashCode()
        result = 31 * result + leaderId.hashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + transitionWindowMs.hashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}

/**
 * Wire telemetry beacon broadcast by nodes to announce their active consensus epoch.
 */
@Serializable
data class EpochBeaconPayload(
    val groupId: String,
    val currentEpochNumber: Long,
    val leaderId: String,
    val timestampMs: Long
)

/**
 * Decentralized Over-The-Air (OTA) Group Re-Keying and Dynamic Epoch Migration Manager.
 *
 * Implements smooth cryptographic key rotation across mesh peers:
 * - Dual-epoch concurrent decryption during a configurable transition window (default: 30 seconds).
 * - Cryptographic key derivation via pure Kotlin RFC 5869 HKDF-SHA256 from previous master secret and leader salt.
 * - Authenticated rollover messages verified via leader RFC 2104 HMAC-SHA256 signatures.
 * - Continuous zero-packet-drop playout for voice streams during active epoch migration.
 * - Timed retirement and strict memory zeroization for superseded epoch keys.
 */
class GroupEpochManager(
    val groupId: String,
    val localPeerId: String,
    var leaderId: String,
    initialMasterSecret: ByteArray,
    initialEpochNumber: Long = 1L
) {
    private val json = Json {
        classDiscriminator = "#type"
        ignoreUnknownKeys = true
    }

    var currentEpoch: GroupEpoch = GroupEpoch(
        epochNumber = initialEpochNumber,
        masterSecret = initialMasterSecret.copyOf(),
        activatedAtMs = currentTimeMillis(),
        leaderId = leaderId
    )
        private set

    var previousEpoch: GroupEpoch? = null
        private set

    var transitionDeadlineMs: Long = 0L
        private set

    val isLeader: Boolean
        get() = localPeerId == leaderId

    fun isInTransitionWindow(currentTimeMs: Long = currentTimeMillis()): Boolean {
        return previousEpoch != null && currentTimeMs <= transitionDeadlineMs
    }

    /**
     * Initiates a new cryptographic epoch rollover.
     * Only the group leader may initiate an epoch rollover.
     *
     * @param transitionWindowMs Time window in milliseconds during which both old and new keys coexist.
     * @param timestampMs Timestamp of the rollover event.
     * @return [MeshPacket.Control] frame encapsulating the [EpochRolloverPayload].
     */
    fun initiateRollover(
        transitionWindowMs: Long = 30_000L,
        timestampMs: Long = currentTimeMillis()
    ): MeshPacket.Control {
        check(isLeader) { "Only the group leader ($leaderId) may initiate epoch rollover" }

        val nextEpochNumber = currentEpoch.epochNumber + 1L
        val salt = ByteArray(32)
        Random.nextBytes(salt)

        // 1. Derive next epoch master secret using HKDF-SHA256
        val nextSecret = HkdfSha256.deriveSecrets(
            salt = salt,
            ikm = currentEpoch.masterSecret,
            info = "intercom-alpha:epoch:$nextEpochNumber:$groupId".encodeToByteArray(),
            length = 32
        )

        // 2. Compute leader HMAC-SHA256 signature over rollover parameters using current epoch key
        val signature = computeRolloverSignature(
            key = currentEpoch.masterSecret,
            groupId = groupId,
            newEpochNumber = nextEpochNumber,
            leaderId = localPeerId,
            salt = salt,
            timestampMs = timestampMs,
            transitionWindowMs = transitionWindowMs
        )

        val payload = EpochRolloverPayload(
            groupId = groupId,
            newEpochNumber = nextEpochNumber,
            leaderId = localPeerId,
            salt = salt,
            timestampMs = timestampMs,
            transitionWindowMs = transitionWindowMs,
            signature = signature
        )

        // 3. Shift local state into dual-epoch migration mode
        previousEpoch = currentEpoch
        currentEpoch = GroupEpoch(
            epochNumber = nextEpochNumber,
            masterSecret = nextSecret,
            activatedAtMs = timestampMs,
            leaderId = localPeerId
        )
        transitionDeadlineMs = timestampMs + transitionWindowMs

        val payloadBytes = json.encodeToString(payload).encodeToByteArray()
        return MeshPacket.Control(
            senderId = localPeerId,
            type = ControlType.EPOCH_ROLLOVER,
            payload = payloadBytes
        )
    }

    /**
     * Processes an incoming [MeshPacket.Control] frame containing an [EpochRolloverPayload].
     *
     * @return `true` if rollover was verified and applied, `false` if rejected.
     */
    fun handleEpochRollover(
        packet: MeshPacket.Control,
        currentTimeMs: Long = currentTimeMillis()
    ): Boolean {
        if (packet.type != ControlType.EPOCH_ROLLOVER) return false

        val payload = try {
            json.decodeFromString<EpochRolloverPayload>(packet.payload.decodeToString())
        } catch (_: Exception) {
            return false
        }

        // Validate basic parameters
        if (payload.groupId != groupId) return false
        if (payload.newEpochNumber <= currentEpoch.epochNumber) return false
        if (payload.leaderId != leaderId) return false

        // Verify leader signature against current epoch master secret
        val expectedSignature = computeRolloverSignature(
            key = currentEpoch.masterSecret,
            groupId = payload.groupId,
            newEpochNumber = payload.newEpochNumber,
            leaderId = payload.leaderId,
            salt = payload.salt,
            timestampMs = payload.timestampMs,
            transitionWindowMs = payload.transitionWindowMs
        )

        if (!expectedSignature.contentEquals(payload.signature)) {
            return false // Signature mismatch: rogue or forged rollover rejected
        }

        // Derive identical new epoch master secret
        val nextSecret = HkdfSha256.deriveSecrets(
            salt = payload.salt,
            ikm = currentEpoch.masterSecret,
            info = "intercom-alpha:epoch:${payload.newEpochNumber}:$groupId".encodeToByteArray(),
            length = 32
        )

        // Activate dual-epoch transition window
        previousEpoch = currentEpoch
        currentEpoch = GroupEpoch(
            epochNumber = payload.newEpochNumber,
            masterSecret = nextSecret,
            activatedAtMs = currentTimeMs,
            leaderId = payload.leaderId
        )
        transitionDeadlineMs = currentTimeMs + payload.transitionWindowMs
        return true
    }

    /**
     * Creates an [EPOCH_BEACON] control packet to broadcast current epoch consensus.
     */
    fun createEpochBeacon(timestampMs: Long = currentTimeMillis()): MeshPacket.Control {
        val beacon = EpochBeaconPayload(
            groupId = groupId,
            currentEpochNumber = currentEpoch.epochNumber,
            leaderId = leaderId,
            timestampMs = timestampMs
        )
        return MeshPacket.Control(
            senderId = localPeerId,
            type = ControlType.EPOCH_BEACON,
            payload = json.encodeToString(beacon).encodeToByteArray()
        )
    }

    /**
     * Handles an incoming [EPOCH_BEACON] packet.
     *
     * @return The heard epoch number if valid, or `null`.
     */
    fun handleEpochBeacon(packet: MeshPacket.Control): Long? {
        if (packet.type != ControlType.EPOCH_BEACON) return null
        return try {
            val beacon = json.decodeFromString<EpochBeaconPayload>(packet.payload.decodeToString())
            if (beacon.groupId == groupId) beacon.currentEpochNumber else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Encrypts plaintext using the active epoch key, tagging it with the epoch number.
     *
     * @return 8-byte big-endian epoch number + ChaCha20-Poly1305 ciphertext.
     */
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray {
        val encrypted = currentEpoch.cipher.encrypt(plaintext, associatedData)
        val output = ByteArray(8 + encrypted.size)
        val epochNum = currentEpoch.epochNumber
        for (i in 0 until 8) {
            output[i] = ((epochNum ushr (56 - i * 8)) and 0xFF).toByte()
        }
        encrypted.copyInto(output, 8)
        return output
    }

    /**
     * Decrypts ciphertext supporting dual-epoch concurrent decryption during migration windows.
     *
     * @param data Either tagged (8-byte epoch header + ciphertext) or raw ciphertext.
     * @param timestampMs Arrival timestamp for checking transition window expiry.
     * @return Decrypted plaintext, or `null` if authentication verification fails.
     */
    fun decrypt(
        data: ByteArray,
        timestampMs: Long = currentTimeMillis(),
        associatedData: ByteArray = ByteArray(0)
    ): ByteArray? {
        if (data.isEmpty()) return null

        // 1. Attempt tagged epoch format (8-byte header + 28 bytes minimum ciphertext)
        if (data.size >= 8 + 28) {
            var packetEpoch = 0L
            for (i in 0 until 8) {
                packetEpoch = (packetEpoch shl 8) or (data[i].toLong() and 0xFF)
            }

            val ciphertext = data.copyOfRange(8, data.size)

            if (packetEpoch == currentEpoch.epochNumber) {
                val decrypted = currentEpoch.cipher.decrypt(ciphertext, associatedData)
                if (decrypted != null) return decrypted
            } else if (previousEpoch != null && packetEpoch == previousEpoch?.epochNumber && timestampMs <= transitionDeadlineMs) {
                val decrypted = previousEpoch?.cipher?.decrypt(ciphertext, associatedData)
                if (decrypted != null) return decrypted
            }
        }

        // 2. Fallback: Untagged dual-epoch opportunistic decryption
        val decryptedCurrent = currentEpoch.cipher.decrypt(data, associatedData)
        if (decryptedCurrent != null) return decryptedCurrent

        if (previousEpoch != null && timestampMs <= transitionDeadlineMs) {
            val decryptedPrevious = previousEpoch?.cipher?.decrypt(data, associatedData)
            if (decryptedPrevious != null) return decryptedPrevious
        }

        return null
    }

    /**
     * Prunes and zeroizes expired previous epoch keys once the transition window has elapsed.
     *
     * @return `true` if an expired epoch was pruned and zeroized, `false` otherwise.
     */
    fun pruneExpiredEpochs(currentTimeMs: Long = currentTimeMillis()): Boolean {
        if (previousEpoch != null && currentTimeMs > transitionDeadlineMs) {
            previousEpoch?.zeroize()
            previousEpoch = null
            return true
        }
        return false
    }

    companion object {
        fun computeRolloverSignature(
            key: ByteArray,
            groupId: String,
            newEpochNumber: Long,
            leaderId: String,
            salt: ByteArray,
            timestampMs: Long,
            transitionWindowMs: Long
        ): ByteArray {
            val message = "$groupId:$newEpochNumber:$leaderId:$timestampMs:$transitionWindowMs".encodeToByteArray() + salt
            return HmacSha256.mac(key, message)
        }
    }
}
