package org.nowni.intercom_alpha.crypto

import kotlinx.serialization.Serializable
import org.nowni.intercom_alpha.mesh.ChaCha20Poly1305
import org.nowni.intercom_alpha.mesh.HkdfSha256
import kotlin.random.Random

/**
 * Serialized envelope containing ratcheted ciphertext and cryptographic metadata.
 */
@Serializable
data class RatchetEnvelope(
    val senderId: String,
    val ratchetStep: Int,
    val sequenceNumber: Long,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val tag: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RatchetEnvelope) return false
        if (senderId != other.senderId) return false
        if (ratchetStep != other.ratchetStep) return false
        if (sequenceNumber != other.sequenceNumber) return false
        if (!nonce.contentEquals(other.nonce)) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!tag.contentEquals(other.tag)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = senderId.hashCode()
        result = 31 * result + ratchetStep
        result = 31 * result + sequenceNumber.hashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + tag.contentHashCode()
        return result
    }
}

/**
 * Pure Kotlin Multiplatform Perfect Forward Secrecy (PFS) session key ratchet engine
 * for mesh broadcast channels and peer-to-peer voice streams.
 *
 * Implements symmetric KDF chain ratcheting with zero Java (java.security.*) or C/JNI dependencies:
 * - Ephemeral session keys advance per packet or time window.
 * - Out-of-order packet decryption via bounded skipped-key cache.
 * - Strict zeroization (key.fill(0)) ensuring compromised future keys cannot decrypt past sessions.
 */
class MeshKeyRatchet(
    val localNodeId: String,
    masterSecret: ByteArray,
    private val maxSkippedKeysPerPeer: Int = 100
) {
    init {
        require(masterSecret.size >= 16) { "Master secret must be at least 16 bytes" }
    }

    private val masterSecretCopy = masterSecret.copyOf()

    // Local sending chain state
    private var sendingChainKey: ByteArray = HkdfSha256.deriveSecrets(
        salt = masterSecretCopy,
        ikm = localNodeId.encodeToByteArray(),
        info = SENDER_CHAIN_INIT_INFO,
        length = 32
    )
    private var sendingRatchetStep: Int = 0
    private var sendingSequenceNumber: Long = 0L
    private var isDestroyed: Boolean = false

    // Inbound peer chain states
    private class PeerRatchetState(
        val peerId: String,
        var chainKey: ByteArray,
        var nextExpectedStep: Int = 0,
        val skippedKeys: MutableMap<Int, ByteArray> = mutableMapOf()
    ) {
        fun wipe() {
            chainKey.fill(0)
            for (key in skippedKeys.values) {
                key.fill(0)
            }
            skippedKeys.clear()
        }
    }

    private val peerStates = mutableMapOf<String, PeerRatchetState>()

    /**
     * Current step of the local sender ratchet.
     */
    val currentSendingStep: Int
        get() = sendingRatchetStep

    /**
     * Total number of tracked remote peer ratchets.
     */
    val trackedPeerCount: Int
        get() = peerStates.size

    /**
     * Returns true if this ratchet instance has been destroyed.
     */
    val isWiped: Boolean
        get() = isDestroyed

    /**
     * Encrypts [plaintext] using an ephemeral forward-secret session key.
     * Advances the sending ratchet by 1 step and zeroizes the temporary message key immediately.
     */
    fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray = ByteArray(0)
    ): RatchetEnvelope {
        check(!isDestroyed) { "MeshKeyRatchet has been destroyed and zeroized" }
        val (step, messageKey) = advanceSendingKey()
        try {
            val nonce = ByteArray(12)
            Random.nextBytes(nonce)
            val (ciphertext, tag) = ChaCha20Poly1305.encrypt(messageKey, nonce, plaintext, associatedData)
            val seq = ++sendingSequenceNumber
            return RatchetEnvelope(
                senderId = localNodeId,
                ratchetStep = step,
                sequenceNumber = seq,
                nonce = nonce,
                ciphertext = ciphertext,
                tag = tag
            )
        } finally {
            messageKey.fill(0)
        }
    }

    /**
     * Decrypts an incoming [envelope], advancing the corresponding peer's ratchet chain
     * or consuming a cached out-of-order skipped key.
     *
     * @return Decrypted plaintext, or `null` if authentication verification fails or packet is expired/replayed.
     */
    fun decrypt(
        envelope: RatchetEnvelope,
        associatedData: ByteArray = ByteArray(0)
    ): ByteArray? {
        check(!isDestroyed) { "MeshKeyRatchet has been destroyed and zeroized" }
        val messageKey = getMessageKeyForEnvelope(envelope) ?: return null
        return try {
            ChaCha20Poly1305.decrypt(
                key = messageKey,
                nonce = envelope.nonce,
                ciphertext = envelope.ciphertext,
                tag = envelope.tag,
                associatedData = associatedData
            )
        } finally {
            messageKey.fill(0)
        }
    }

    /**
     * Advances sending chain and returns a pair of (step, messageKey).
     */
    private fun advanceSendingKey(): Pair<Int, ByteArray> {
        val okm = HkdfSha256.expand(sendingChainKey, CHAIN_ADVANCE_INFO, 64)
        val nextChain = okm.copyOfRange(0, 32)
        val messageKey = okm.copyOfRange(32, 64)
        okm.fill(0)

        // Zeroize previous chain key
        sendingChainKey.fill(0)
        sendingChainKey = nextChain

        val step = sendingRatchetStep++
        return Pair(step, messageKey)
    }

    /**
     * Resolves the ephemeral message key for a received envelope.
     */
    private fun getMessageKeyForEnvelope(envelope: RatchetEnvelope): ByteArray? {
        val peerState = peerStates.getOrPut(envelope.senderId) {
            val initChain = HkdfSha256.deriveSecrets(
                salt = masterSecretCopy,
                ikm = envelope.senderId.encodeToByteArray(),
                info = SENDER_CHAIN_INIT_INFO,
                length = 32
            )
            PeerRatchetState(peerId = envelope.senderId, chainKey = initChain, nextExpectedStep = 0)
        }

        val step = envelope.ratchetStep

        // Case 1: Step is older than expected, check if it was saved in skipped keys
        if (step < peerState.nextExpectedStep) {
            val skipped = peerState.skippedKeys.remove(step)
            return skipped // null if already consumed or expired
        }

        // Case 2: Exact step expected
        if (step == peerState.nextExpectedStep) {
            val okm = HkdfSha256.expand(peerState.chainKey, CHAIN_ADVANCE_INFO, 64)
            val nextChain = okm.copyOfRange(0, 32)
            val messageKey = okm.copyOfRange(32, 64)
            okm.fill(0)

            peerState.chainKey.fill(0)
            peerState.chainKey = nextChain
            peerState.nextExpectedStep++
            return messageKey
        }

        // Case 3: Step is in the future (out-of-order or dropped packets)
        val gap = step - peerState.nextExpectedStep
        if (gap > maxSkippedKeysPerPeer) {
            // Gap is too wide, reject to avoid memory exhaustion
            return null
        }

        // Advance chain up to target step, caching intermediate message keys
        while (peerState.nextExpectedStep < step) {
            val okm = HkdfSha256.expand(peerState.chainKey, CHAIN_ADVANCE_INFO, 64)
            val nextChain = okm.copyOfRange(0, 32)
            val skippedMsgKey = okm.copyOfRange(32, 64)
            okm.fill(0)

            peerState.chainKey.fill(0)
            peerState.chainKey = nextChain

            // Enforce max skipped key bounds
            if (peerState.skippedKeys.size >= maxSkippedKeysPerPeer) {
                val oldestKey = peerState.skippedKeys.keys.minOrNull()
                if (oldestKey != null) {
                    peerState.skippedKeys.remove(oldestKey)?.fill(0)
                }
            }

            peerState.skippedKeys[peerState.nextExpectedStep] = skippedMsgKey
            peerState.nextExpectedStep++
        }

        // Now nextExpectedStep == step
        val okm = HkdfSha256.expand(peerState.chainKey, CHAIN_ADVANCE_INFO, 64)
        val nextChain = okm.copyOfRange(0, 32)
        val targetMessageKey = okm.copyOfRange(32, 64)
        okm.fill(0)

        peerState.chainKey.fill(0)
        peerState.chainKey = nextChain
        peerState.nextExpectedStep++

        return targetMessageKey
    }

    /**
     * Resets or prunes a peer's ratchet state (e.g., upon explicit group re-keying).
     */
    fun resetPeer(peerId: String) {
        peerStates.remove(peerId)?.wipe()
    }

    /**
     * Returns the count of skipped keys currently held in memory for [peerId].
     */
    fun getSkippedKeyCount(peerId: String): Int {
        return peerStates[peerId]?.skippedKeys?.size ?: 0
    }

    /**
     * Securely zeroizes all in-memory keys, chain states, and cached secrets.
     */
    fun destroy() {
        isDestroyed = true
        masterSecretCopy.fill(0)
        sendingChainKey.fill(0)
        for (peer in peerStates.values) {
            peer.wipe()
        }
        peerStates.clear()
    }

    companion object {
        private val SENDER_CHAIN_INIT_INFO = "IntercomAlpha:MeshRatchet:SenderInit:v1".encodeToByteArray()
        private val CHAIN_ADVANCE_INFO = "IntercomAlpha:MeshRatchet:ChainAdvance:v1".encodeToByteArray()
    }
}
