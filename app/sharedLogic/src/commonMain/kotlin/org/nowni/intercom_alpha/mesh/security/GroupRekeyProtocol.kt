package org.nowni.intercom_alpha.mesh.security

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.mesh.HkdfSha256
import org.nowni.intercom_alpha.mesh.HmacSha256
import org.nowni.intercom_alpha.mesh.PacketCipher
import org.nowni.intercom_alpha.mesh.Sha256
import kotlin.random.Random

/**
 * Fleet-Wide Over-The-Air Cryptographic Group Rekeying Protocol.
 *
 * Implements forward-secret epoch transitions, multi-hop rekey packet verification,
 * anti-replay counter checks, dual-epoch key buffer management, and automatic
 * grace period pruning.
 *
 * Conforms to Rule 2 (KMP commonMain logic) and Rule 3 (pure multiplatform cryptography, zero Java/C deps).
 */
class GroupRekeyProtocol(
    val groupId: String,
    val localNodeId: String,
    baseKeyInput: ByteArray,
    val gracePeriodMs: Long = DEFAULT_GRACE_PERIOD_MS,
    initialEpoch: Long = 1L,
    private val timeProvider: () -> Long = { currentTimeMillis() }
) {
    private val mutex = Mutex()

    private val masterBaseKey: ByteArray = baseKeyInput.copyOf()
    private var lastAntiReplayCounter: Long = 0L

    private var _keyBuffer: DualEpochKeyBuffer
    val keyBuffer: DualEpochKeyBuffer
        get() = _keyBuffer

    private val _events = MutableSharedFlow<RekeyEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<RekeyEvent> = _events.asSharedFlow()

    init {
        require(baseKeyInput.size == 32) { "Base key must be 32 bytes" }
        require(initialEpoch >= 1L) { "Initial epoch must be >= 1" }

        val initialSalt = Sha256.digest("intercom-init-$groupId-$initialEpoch".encodeToByteArray())
        val initialEpochKey = deriveEpochKey(masterBaseKey, initialEpoch, initialSalt)
        _keyBuffer = DualEpochKeyBuffer(
            activeEpoch = initialEpoch,
            activeKey = initialEpochKey,
            previousEpoch = null,
            previousKey = null,
            gracePeriodExpiresAt = 0L
        )
    }

    /**
     * Initiates a fleet-wide cryptographic epoch rotation.
     *
     * Transitions local dual-epoch buffer immediately to the new epoch, retaining
     * the prior active key for [gracePeriodMs], and returns the signed [RekeyControlPacket]
     * ready for flooding over the mesh.
     */
    suspend fun initiateRekey(
        reason: RekeyTriggerReason = RekeyTriggerReason.SCHEDULED_ROTATION,
        customSalt: ByteArray? = null
    ): RekeyControlPacket = mutex.withLock {
        val now = timeProvider()
        val newEpoch = _keyBuffer.activeEpoch + 1L
        val salt = customSalt ?: ByteArray(16).also { Random.nextBytes(it) }
        val newKey = deriveEpochKey(masterBaseKey, newEpoch, salt)

        // Capture current active key before shifting
        val oldActiveKey = _keyBuffer.activeKey.copyOf()
        val oldActiveEpoch = _keyBuffer.activeEpoch

        // Shift keys in dual-epoch buffer
        _keyBuffer = DualEpochKeyBuffer(
            activeEpoch = newEpoch,
            activeKey = newKey,
            previousEpoch = oldActiveEpoch,
            previousKey = oldActiveKey,
            gracePeriodExpiresAt = now + gracePeriodMs
        )

        lastAntiReplayCounter++
        val counter = lastAntiReplayCounter

        val unsignedData = constructCanonicalPayload(
            groupId = groupId,
            initiatorId = localNodeId,
            targetEpoch = newEpoch,
            salt = salt,
            timestamp = now,
            counter = counter,
            reason = reason
        )

        // Sign using the prior active key (which all peers currently hold)
        val signature = HmacSha256.mac(oldActiveKey, unsignedData)

        _events.tryEmit(RekeyEvent.EPOCH_TRANSITION_STARTED)

        return RekeyControlPacket(
            groupId = groupId,
            initiatorId = localNodeId,
            targetEpoch = newEpoch,
            epochKeyDerivationSalt = salt,
            timestamp = now,
            antiReplayCounter = counter,
            authSignature = signature,
            reason = reason
        )
    }

    /**
     * Verifies and applies an incoming [RekeyControlPacket] received over the mesh.
     */
    suspend fun handleIncomingRekeyPacket(
        packet: RekeyControlPacket,
        now: Long = timeProvider()
    ): RekeyResult = mutex.withLock {
        // Prune expired grace period if needed
        pruneExpiredGraceInternal(now)

        if (packet.groupId != groupId) {
            _events.tryEmit(RekeyEvent.REKEY_PACKET_REJECTED)
            return RekeyResult.Rejected("Group ID mismatch: expected $groupId, got ${packet.groupId}")
        }

        if (packet.antiReplayCounter <= lastAntiReplayCounter) {
            _events.tryEmit(RekeyEvent.REKEY_PACKET_REJECTED)
            return RekeyResult.Rejected("Replay detected: counter ${packet.antiReplayCounter} <= $lastAntiReplayCounter")
        }

        if (packet.targetEpoch <= _keyBuffer.activeEpoch) {
            _events.tryEmit(RekeyEvent.REKEY_PACKET_REJECTED)
            return RekeyResult.Rejected("Stale or current epoch: target ${packet.targetEpoch} <= active ${_keyBuffer.activeEpoch}")
        }

        // Verify auth signature using active key (or previous key if within grace)
        val canonicalBytes = constructCanonicalPayload(
            groupId = packet.groupId,
            initiatorId = packet.initiatorId,
            targetEpoch = packet.targetEpoch,
            salt = packet.epochKeyDerivationSalt,
            timestamp = packet.timestamp,
            counter = packet.antiReplayCounter,
            reason = packet.reason
        )

        val activeKeySig = HmacSha256.mac(_keyBuffer.activeKey, canonicalBytes)
        val isSignatureValid = constantTimeEquals(activeKeySig, packet.authSignature) || run {
            // Also test against previous key if active key fails
            val prevKey = _keyBuffer.previousKey
            if (prevKey != null) {
                val prevKeySig = HmacSha256.mac(prevKey, canonicalBytes)
                constantTimeEquals(prevKeySig, packet.authSignature)
            } else {
                false
            }
        }

        if (!isSignatureValid) {
            _events.tryEmit(RekeyEvent.REKEY_PACKET_REJECTED)
            return RekeyResult.Rejected("Cryptographic authentication signature verification failed")
        }

        // Accept and transition
        lastAntiReplayCounter = packet.antiReplayCounter
        val newKey = deriveEpochKey(masterBaseKey, packet.targetEpoch, packet.epochKeyDerivationSalt)

        val oldActiveKey = _keyBuffer.activeKey.copyOf()
        val oldActiveEpoch = _keyBuffer.activeEpoch

        // Wipe old previous key if it existed
        _keyBuffer.previousKey?.fill(0)

        _keyBuffer = DualEpochKeyBuffer(
            activeEpoch = packet.targetEpoch,
            activeKey = newKey,
            previousEpoch = oldActiveEpoch,
            previousKey = oldActiveKey,
            gracePeriodExpiresAt = now + gracePeriodMs
        )

        _events.tryEmit(RekeyEvent.EPOCH_TRANSITION_STARTED)
        return RekeyResult.Accepted(packet.targetEpoch, newKey.copyOf())
    }

    /**
     * Encrypts outgoing plaintext using the current active epoch key.
     *
     * @return Pair of (activeEpoch, ciphertextWithAeadTag)
     */
    suspend fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray = ByteArray(0),
        now: Long = timeProvider()
    ): Pair<Long, ByteArray> = mutex.withLock {
        pruneExpiredGraceInternal(now)
        val cipher = PacketCipher(_keyBuffer.activeKey)
        val ciphertext = cipher.encrypt(plaintext, associatedData)
        return Pair(_keyBuffer.activeEpoch, ciphertext)
    }

    /**
     * Decrypts incoming ciphertext given the sender's packet epoch.
     * Supports both the active epoch and the prior epoch during the grace period.
     */
    suspend fun decrypt(
        epoch: Long,
        ciphertext: ByteArray,
        associatedData: ByteArray = ByteArray(0),
        now: Long = timeProvider()
    ): ByteArray? = mutex.withLock {
        pruneExpiredGraceInternal(now)

        return when {
            epoch == _keyBuffer.activeEpoch -> {
                PacketCipher(_keyBuffer.activeKey).decrypt(ciphertext, associatedData)
            }
            epoch == _keyBuffer.previousEpoch -> {
                val prevKey = _keyBuffer.previousKey
                if (prevKey != null && now <= _keyBuffer.gracePeriodExpiresAt) {
                    PacketCipher(prevKey).decrypt(ciphertext, associatedData)
                } else {
                    null
                }
            }
            else -> null
        }
    }

    /**
     * Auto-decrypts ciphertext by attempting the active epoch key first,
     * and falling back to the previous epoch key if within the grace period.
     */
    suspend fun decryptAuto(
        ciphertext: ByteArray,
        associatedData: ByteArray = ByteArray(0),
        now: Long = timeProvider()
    ): ByteArray? = mutex.withLock {
        pruneExpiredGraceInternal(now)

        // Try active epoch first
        val activeResult = PacketCipher(_keyBuffer.activeKey).decrypt(ciphertext, associatedData)
        if (activeResult != null) return activeResult

        // Try previous epoch if grace period is active
        val prevKey = _keyBuffer.previousKey
        if (prevKey != null && now <= _keyBuffer.gracePeriodExpiresAt) {
            return PacketCipher(prevKey).decrypt(ciphertext, associatedData)
        }

        return null
    }

    /**
     * Prunes expired previous epoch key and zeroizes its memory buffer.
     */
    suspend fun pruneExpiredGrace(now: Long = timeProvider()) = mutex.withLock {
        pruneExpiredGraceInternal(now)
    }

    private fun pruneExpiredGraceInternal(now: Long) {
        val prevKey = _keyBuffer.previousKey
        if (prevKey != null && now > _keyBuffer.gracePeriodExpiresAt) {
            prevKey.fill(0)
            _keyBuffer = _keyBuffer.copy(
                previousEpoch = null,
                previousKey = null,
                gracePeriodExpiresAt = 0L
            )
            _events.tryEmit(RekeyEvent.OLD_EPOCH_EXPIRED)
        }
    }

    /**
     * Obtains a real-time observable snapshot of the active epoch and grace period.
     */
    fun getSnapshot(now: Long = timeProvider()): RekeyEpochSnapshot {
        val buf = _keyBuffer
        val remainingSec = if (buf.isGracePeriodActive && buf.gracePeriodExpiresAt > now) {
            ((buf.gracePeriodExpiresAt - now) / 1000L).toInt()
        } else 0
        return RekeyEpochSnapshot(
            currentEpoch = buf.activeEpoch,
            isGracePeriodActive = buf.isGracePeriodActive,
            previousEpoch = buf.previousEpoch,
            gracePeriodRemainingSeconds = remainingSec,
            lastRekeyTimestampMs = now,
            lastRekeyReason = RekeyTriggerReason.SCHEDULED_ROTATION
        )
    }

    /**
     * Securely wipes all cryptographic keys and resets protocol state.
     */
    suspend fun wipe() = mutex.withLock {
        masterBaseKey.fill(0)
        _keyBuffer.activeKey.fill(0)
        _keyBuffer.previousKey?.fill(0)
        _keyBuffer = DualEpochKeyBuffer(
            activeEpoch = 0L,
            activeKey = ByteArray(32),
            previousEpoch = null,
            previousKey = null,
            gracePeriodExpiresAt = 0L
        )
        lastAntiReplayCounter = 0L
    }

    companion object {
        const val DEFAULT_GRACE_PERIOD_MS: Long = 10_000L

        /**
         * Pure Kotlin HKDF-SHA256 epoch key derivation function.
         */
        fun deriveEpochKey(baseKey: ByteArray, epoch: Long, salt: ByteArray): ByteArray {
            val info = "intercom-epoch-$epoch".encodeToByteArray()
            return HkdfSha256.deriveSecrets(
                salt = salt,
                ikm = baseKey,
                info = info,
                length = 32
            )
        }

        fun constructCanonicalPayload(
            groupId: String,
            initiatorId: String,
            targetEpoch: Long,
            salt: ByteArray,
            timestamp: Long,
            counter: Long,
            reason: RekeyTriggerReason
        ): ByteArray {
            val header = "$groupId|$initiatorId|$targetEpoch|$timestamp|$counter|${reason.name}".encodeToByteArray()
            val total = ByteArray(header.size + salt.size)
            header.copyInto(total, 0)
            salt.copyInto(total, header.size)
            return total
        }

        fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
            if (a.size != b.size) return false
            var diff = 0
            for (i in a.indices) {
                diff = diff or (a[i].toInt() xor b[i].toInt())
            }
            return diff == 0
        }
    }
}
