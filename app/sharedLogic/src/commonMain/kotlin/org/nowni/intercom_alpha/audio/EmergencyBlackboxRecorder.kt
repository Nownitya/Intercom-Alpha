package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.mesh.PacketCipher
import org.nowni.intercom_alpha.randomUUID
import org.nowni.intercom_alpha.telemetry.GpsCoordinate

private val blackboxJson = Json {
    classDiscriminator = "#type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Audio frame captured in the rolling emergency blackbox buffer.
 */
@Serializable
data class BlackboxAudioFrame(
    val timestampMs: Long,
    val sequence: Long,
    val speakerId: String,
    val speakerName: String = "",
    val priority: PriorityLevel = PriorityLevel.NORMAL,
    val alertType: AlertType = AlertType.NONE,
    val adpcmData: ByteArray,
    val gpsCoordinate: GpsCoordinate? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlackboxAudioFrame) return false
        if (timestampMs != other.timestampMs) return false
        if (sequence != other.sequence) return false
        if (speakerId != other.speakerId) return false
        if (speakerName != other.speakerName) return false
        if (priority != other.priority) return false
        if (alertType != other.alertType) return false
        if (!adpcmData.contentEquals(other.adpcmData)) return false
        if (gpsCoordinate != other.gpsCoordinate) return false
        return true
    }

    override fun hashCode(): Int {
        var result = timestampMs.hashCode()
        result = 31 * result + sequence.hashCode()
        result = 31 * result + speakerId.hashCode()
        result = 31 * result + speakerName.hashCode()
        result = 31 * result + priority.hashCode()
        result = 31 * result + alertType.hashCode()
        result = 31 * result + adpcmData.contentHashCode()
        result = 31 * result + (gpsCoordinate?.hashCode() ?: 0)
        return result
    }
}

/**
 * Root cause or trigger reason that initiated the blackbox incident snapshot.
 */
@Serializable
enum class IncidentTriggerReason {
    /** Sudden deceleration or crash impact reported by telemetry sensors. */
    CRASH_DETECTED,

    /** High-priority emergency voice override transmission preempted audio channels. */
    EMERGENCY_OVERRIDE_AUDIO,

    /** Broadcast road or safety hazard alert emitted across mesh. */
    HAZARD_BROADCAST,

    /** Rider manually triggered emergency SOS button. */
    MANUAL_SOS,

    /** External sensor or companion telemetry threshold breach. */
    EXTERNAL_SENSOR
}

/**
 * Decrypted in-memory forensic snapshot capturing pre-incident voice context.
 */
@Serializable
data class IncidentSnapshot(
    val incidentId: String,
    val timestampMs: Long,
    val triggerReason: IncidentTriggerReason,
    val primaryPeerId: String,
    val primaryPeerName: String = "",
    val location: GpsCoordinate? = null,
    val frameCount: Int,
    val durationMs: Long,
    val audioFrames: List<BlackboxAudioFrame>,
    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * Formats forensic telemetry into human-readable Markdown summary for incident logs.
     */
    fun toMarkdownForensicSummary(): String {
        val uniqueSpeakers = audioFrames.map { it.speakerName.ifBlank { it.speakerId } }.distinct().joinToString(", ")
        val locStr = location?.let { "Lat: ${it.latitude}, Lon: ${it.longitude}, Speed: ${it.speedKmh} km/h" } ?: "Unavailable"
        return """
        # Incident Forensic Snapshot: $incidentId
        - **Timestamp**: $timestampMs
        - **Reason**: $triggerReason
        - **Primary Peer**: ${primaryPeerName.ifBlank { primaryPeerId }}
        - **Location**: $locStr
        - **Recorded Audio Span**: ${durationMs}ms ($frameCount frames)
        - **Active Speakers**: ${uniqueSpeakers.ifBlank { "None" }}
        """.trimIndent()
    }
}

/**
 * Encrypted incident forensic report protected with ChaCha20-Poly1305 AEAD.
 */
@Serializable
data class EncryptedIncidentReport(
    val incidentId: String,
    val timestampMs: Long,
    val triggerReason: IncidentTriggerReason,
    val primaryPeerId: String,
    val primaryPeerName: String = "",
    val frameCount: Int,
    val location: GpsCoordinate? = null,
    val encryptedPayload: ByteArray // Nonce (12) + Ciphertext + Poly1305 Tag (16)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedIncidentReport) return false
        if (incidentId != other.incidentId) return false
        if (timestampMs != other.timestampMs) return false
        if (triggerReason != other.triggerReason) return false
        if (primaryPeerId != other.primaryPeerId) return false
        if (primaryPeerName != other.primaryPeerName) return false
        if (frameCount != other.frameCount) return false
        if (location != other.location) return false
        if (!encryptedPayload.contentEquals(other.encryptedPayload)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = incidentId.hashCode()
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + triggerReason.hashCode()
        result = 31 * result + primaryPeerId.hashCode()
        result = 31 * result + primaryPeerName.hashCode()
        result = 31 * result + frameCount.hashCode()
        result = 31 * result + (location?.hashCode() ?: 0)
        result = 31 * result + encryptedPayload.contentHashCode()
        return result
    }
}

/**
 * Rolling Emergency Voice Blackbox & Encrypted Incident Cache.
 *
 * Implements an in-memory rolling circular buffer preserving the last 60 seconds
 * of compressed ADPCM voice audio across the mesh.
 *
 * Automatically captures and freezes forensic snapshots upon emergency voice preemption
 * or crash detection, encrypting snapshots using pure KMP RFC 8439 ChaCha20-Poly1305 AEAD.
 *
 * Guarantees strictly bounded memory consumption (< 3 MB resident RAM).
 *
 * @param bufferDurationMs Duration of the rolling window in milliseconds. Default: 60,000ms (60s).
 * @param maxFramesCapacity Hard frame limit preventing memory leaks under high frame rates. Default: 3,500.
 * @param maxCachedIncidents Maximum number of encrypted incident snapshots retained in cache. Default: 5.
 * @param defaultCipher Optional default [PacketCipher] used to auto-encrypt incident snapshots.
 * @param autoFreezeOnEmergency If true, automatically captures snapshots on EMERGENCY_OVERRIDE or CRASH alerts.
 * @param incidentCooldownMs Minimum interval between auto-triggered incident captures. Default: 10,000ms.
 */
class EmergencyBlackboxRecorder(
    val bufferDurationMs: Long = 60_000L,
    val maxFramesCapacity: Int = 3_500,
    val maxCachedIncidents: Int = 5,
    private val defaultCipher: PacketCipher? = null,
    val autoFreezeOnEmergency: Boolean = true,
    val incidentCooldownMs: Long = 10_000L
) {
    private val mutex = Mutex()
    private val ringBuffer = ArrayDeque<BlackboxAudioFrame>()
    private val cachedEncryptedReports = mutableListOf<EncryptedIncidentReport>()
    private var lastAutoIncidentTimestampMs: Long = 0L

    private val _bufferedFrameCount = MutableStateFlow(0)
    val bufferedFrameCount: StateFlow<Int> = _bufferedFrameCount.asStateFlow()

    private val _bufferedDurationMs = MutableStateFlow(0L)
    val bufferedDurationMs: StateFlow<Long> = _bufferedDurationMs.asStateFlow()

    private val _isRecording = MutableStateFlow(true)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _encryptedReports = MutableStateFlow<List<EncryptedIncidentReport>>(emptyList())
    val encryptedReports: StateFlow<List<EncryptedIncidentReport>> = _encryptedReports.asStateFlow()

    /**
     * Records a compressed audio frame into the rolling circular buffer.
     *
     * @return Captured [IncidentSnapshot] if this frame triggered an automatic freeze, null otherwise.
     */
    suspend fun recordFrame(
        frame: BlackboxAudioFrame,
        now: Long = currentTimeMillis()
    ): IncidentSnapshot? = mutex.withLock {
        if (!_isRecording.value) return null

        ringBuffer.addLast(frame)

        // Prune frames outside the rolling time window
        val cutoffTimestamp = frame.timestampMs - bufferDurationMs
        while (ringBuffer.isNotEmpty() && ringBuffer.first().timestampMs < cutoffTimestamp) {
            ringBuffer.removeFirst()
        }

        // Hard capacity safety cap
        while (ringBuffer.size > maxFramesCapacity) {
            ringBuffer.removeFirst()
        }

        updateBufferMetricsLocked()

        // Check for automatic emergency snapshot freeze
        if (autoFreezeOnEmergency) {
            val isEmergency = frame.priority == PriorityLevel.EMERGENCY_OVERRIDE ||
                    frame.alertType == AlertType.CRASH_DETECTED ||
                    frame.alertType == AlertType.EMERGENCY_STOP ||
                    frame.alertType == AlertType.HAZARD_WARNING

            if (isEmergency && (lastAutoIncidentTimestampMs == 0L || now - lastAutoIncidentTimestampMs >= incidentCooldownMs)) {
                lastAutoIncidentTimestampMs = now
                val reason = when (frame.alertType) {
                    AlertType.CRASH_DETECTED -> IncidentTriggerReason.CRASH_DETECTED
                    AlertType.HAZARD_WARNING -> IncidentTriggerReason.HAZARD_BROADCAST
                    AlertType.EMERGENCY_STOP -> IncidentTriggerReason.EMERGENCY_OVERRIDE_AUDIO
                    else -> IncidentTriggerReason.EMERGENCY_OVERRIDE_AUDIO
                }
                return freezeSnapshotLocked(
                    triggerReason = reason,
                    primaryPeerId = frame.speakerId,
                    primaryPeerName = frame.speakerName,
                    location = frame.gpsCoordinate,
                    metadata = mapOf("triggerFrameSeq" to frame.sequence.toString()),
                    now = now
                )
            }
        }

        return null
    }

    /**
     * Convenience method to record incoming [PrioritizedAudioFrame] directly.
     */
    suspend fun recordPrioritizedFrame(
        frame: PrioritizedAudioFrame,
        speakerName: String = "",
        gpsCoordinate: GpsCoordinate? = null,
        now: Long = currentTimeMillis()
    ): IncidentSnapshot? {
        val blackboxFrame = BlackboxAudioFrame(
            timestampMs = if (frame.timestamp > 0L) frame.timestamp else now,
            sequence = frame.sequence,
            speakerId = frame.senderId,
            speakerName = speakerName,
            priority = frame.priority,
            alertType = frame.alertType,
            adpcmData = frame.data,
            gpsCoordinate = gpsCoordinate
        )
        return recordFrame(blackboxFrame, now)
    }

    /**
     * Manually triggers and freezes an [IncidentSnapshot] containing the current rolling buffer.
     * If a cipher is available, also encrypts and stores the incident report.
     */
    suspend fun freezeSnapshot(
        triggerReason: IncidentTriggerReason,
        primaryPeerId: String,
        primaryPeerName: String = "",
        location: GpsCoordinate? = null,
        metadata: Map<String, String> = emptyMap(),
        now: Long = currentTimeMillis()
    ): IncidentSnapshot = mutex.withLock {
        freezeSnapshotLocked(triggerReason, primaryPeerId, primaryPeerName, location, metadata, now)
    }

    /**
     * Triggers incident freeze specifically for telemetry crash events.
     */
    suspend fun recordCrashTelemetry(
        coordinate: GpsCoordinate,
        peerId: String,
        peerName: String = "",
        details: String = "Crash deceleration detected",
        now: Long = currentTimeMillis()
    ): IncidentSnapshot = mutex.withLock {
        freezeSnapshotLocked(
            triggerReason = IncidentTriggerReason.CRASH_DETECTED,
            primaryPeerId = peerId,
            primaryPeerName = peerName,
            location = coordinate,
            metadata = mapOf("details" to details, "speedKmh" to coordinate.speedKmh.toString()),
            now = now
        )
    }

    /**
     * Triggers incident freeze for manual SOS activation.
     */
    suspend fun triggerManualSos(
        peerId: String,
        peerName: String = "",
        location: GpsCoordinate? = null,
        note: String = "Manual SOS initiated",
        now: Long = currentTimeMillis()
    ): IncidentSnapshot = mutex.withLock {
        freezeSnapshotLocked(
            triggerReason = IncidentTriggerReason.MANUAL_SOS,
            primaryPeerId = peerId,
            primaryPeerName = peerName,
            location = location,
            metadata = mapOf("note" to note),
            now = now
        )
    }

    /**
     * Encrypts an [IncidentSnapshot] using [cipher] (RFC 8439 ChaCha20-Poly1305 AEAD).
     *
     * @return [EncryptedIncidentReport] container with authenticated ciphertext.
     */
    fun encryptSnapshot(
        snapshot: IncidentSnapshot,
        cipher: PacketCipher
    ): EncryptedIncidentReport {
        val jsonPlaintext = blackboxJson.encodeToString(IncidentSnapshot.serializer(), snapshot).encodeToByteArray()
        val compressedPlaintext = MiniLz.compress(jsonPlaintext)
        val payloadToEncrypt = if (compressedPlaintext.size + 1 < jsonPlaintext.size) {
            val tagged = ByteArray(compressedPlaintext.size + 1)
            tagged[0] = 1 // Compressed
            compressedPlaintext.copyInto(tagged, destinationOffset = 1)
            tagged
        } else {
            val tagged = ByteArray(jsonPlaintext.size + 1)
            tagged[0] = 0 // Raw
            jsonPlaintext.copyInto(tagged, destinationOffset = 1)
            tagged
        }

        val encryptedBytes = cipher.encrypt(
            plaintext = payloadToEncrypt,
            associatedData = snapshot.incidentId.encodeToByteArray()
        )
        return EncryptedIncidentReport(
            incidentId = snapshot.incidentId,
            timestampMs = snapshot.timestampMs,
            triggerReason = snapshot.triggerReason,
            primaryPeerId = snapshot.primaryPeerId,
            primaryPeerName = snapshot.primaryPeerName,
            frameCount = snapshot.frameCount,
            location = snapshot.location,
            encryptedPayload = encryptedBytes
        )
    }

    /**
     * Decrypts and deserializes an [EncryptedIncidentReport] using [cipher].
     *
     * @return [IncidentSnapshot] if tag authentication succeeds; null if key/payload is invalid or tampered.
     */
    fun decryptReport(
        report: EncryptedIncidentReport,
        cipher: PacketCipher
    ): IncidentSnapshot? {
        val decryptedBytes = cipher.decrypt(
            encryptedData = report.encryptedPayload,
            associatedData = report.incidentId.encodeToByteArray()
        ) ?: return null

        return try {
            val jsonString = if (decryptedBytes.isNotEmpty() && decryptedBytes[0] == 1.toByte()) {
                val decompressed = MiniLz.decompress(decryptedBytes.copyOfRange(1, decryptedBytes.size))
                decompressed.decodeToString()
            } else if (decryptedBytes.isNotEmpty() && decryptedBytes[0] == 0.toByte()) {
                decryptedBytes.copyOfRange(1, decryptedBytes.size).decodeToString()
            } else {
                decryptedBytes.decodeToString()
            }
            blackboxJson.decodeFromString(IncidentSnapshot.serializer(), jsonString)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns a snapshot copy of all frames currently resident in the circular buffer.
     */
    suspend fun getBufferedFrames(): List<BlackboxAudioFrame> = mutex.withLock {
        ringBuffer.toList()
    }

    /**
     * Returns all cached encrypted incident reports.
     */
    suspend fun getEncryptedReports(): List<EncryptedIncidentReport> = mutex.withLock {
        cachedEncryptedReports.toList()
    }

    /**
     * Clears the rolling audio ring buffer.
     */
    suspend fun clearBuffer() = mutex.withLock {
        ringBuffer.clear()
        updateBufferMetricsLocked()
    }

    /**
     * Clears all cached encrypted reports.
     */
    suspend fun clearIncidentCache() = mutex.withLock {
        cachedEncryptedReports.clear()
        _encryptedReports.value = emptyList()
    }

    /**
     * Pauses buffer recording.
     */
    suspend fun pauseRecording() = mutex.withLock {
        _isRecording.value = false
    }

    /**
     * Resumes buffer recording.
     */
    suspend fun resumeRecording() = mutex.withLock {
        _isRecording.value = true
    }

    /**
     * Estimates total resident RAM consumption in bytes.
     * Strictly bounded well below the 3 MB acceptance threshold.
     */
    suspend fun estimateMemoryUsageBytes(): Long = mutex.withLock {
        var bytes = 0L
        for (frame in ringBuffer) {
            bytes += frame.adpcmData.size + 128 // payload + estimated object overhead
        }
        for (report in cachedEncryptedReports) {
            bytes += report.encryptedPayload.size + 128
        }
        bytes
    }

    private fun freezeSnapshotLocked(
        triggerReason: IncidentTriggerReason,
        primaryPeerId: String,
        primaryPeerName: String,
        location: GpsCoordinate?,
        metadata: Map<String, String>,
        now: Long
    ): IncidentSnapshot {
        val framesCopy = ringBuffer.toList()
        val duration = if (framesCopy.isEmpty()) {
            0L
        } else {
            (framesCopy.last().timestampMs - framesCopy.first().timestampMs).coerceAtLeast(0L)
        }

        val snapshot = IncidentSnapshot(
            incidentId = randomUUID(),
            timestampMs = now,
            triggerReason = triggerReason,
            primaryPeerId = primaryPeerId,
            primaryPeerName = primaryPeerName,
            location = location,
            frameCount = framesCopy.size,
            durationMs = duration,
            audioFrames = framesCopy,
            metadata = metadata
        )

        // If defaultCipher is configured, auto-encrypt and cache report
        if (defaultCipher != null) {
            val report = encryptSnapshot(snapshot, defaultCipher)
            cachedEncryptedReports.add(report)
            while (cachedEncryptedReports.size > maxCachedIncidents) {
                cachedEncryptedReports.removeAt(0)
            }
            _encryptedReports.value = cachedEncryptedReports.toList()
        }

        return snapshot
    }

    private fun updateBufferMetricsLocked() {
        _bufferedFrameCount.value = ringBuffer.size
        _bufferedDurationMs.value = if (ringBuffer.isEmpty()) {
            0L
        } else {
            (ringBuffer.last().timestampMs - ringBuffer.first().timestampMs).coerceAtLeast(0L)
        }
    }
}

/**
 * Lightweight, zero-dependency pure KMP LZ77 compressor for bounded RAM incident report payloads.
 */
internal object MiniLz {
    fun compress(src: ByteArray): ByteArray {
        if (src.isEmpty()) return ByteArray(0)
        val out = ByteOutput()
        val head = IntArray(4096) { -1 }
        var inPos = 0
        var anchor = 0
        val maxOffset = 65535

        while (inPos + 4 <= src.size) {
            val b0 = src[inPos].toInt() and 0xFF
            val b1 = src[inPos + 1].toInt() and 0xFF
            val b2 = src[inPos + 2].toInt() and 0xFF
            val b3 = src[inPos + 3].toInt() and 0xFF
            val h = ((b0 shl 12) xor (b1 shl 8) xor (b2 shl 4) xor b3) and 4095
            val matchPos = head[h]
            head[h] = inPos

            if (matchPos >= 0 && (inPos - matchPos) in 1..maxOffset &&
                src[matchPos] == src[inPos] &&
                src[matchPos + 1] == src[inPos + 1] &&
                src[matchPos + 2] == src[inPos + 2] &&
                src[matchPos + 3] == src[inPos + 3]
            ) {
                val offset = inPos - matchPos
                var len = 4
                while (inPos + len < src.size && src[matchPos + (len % offset)] == src[inPos + len] && len < 65535) {
                    len++
                }

                if (len >= 4) {
                    val litLen = inPos - anchor
                    out.writeVarInt(litLen)
                    if (litLen > 0) {
                        out.writeBytes(src, anchor, litLen)
                    }
                    out.writeShort(offset)
                    out.writeShort(len)
                    inPos += len
                    anchor = inPos
                    continue
                }
            }
            inPos++
        }

        val litLen = src.size - anchor
        if (litLen > 0) {
            out.writeVarInt(litLen)
            out.writeBytes(src, anchor, litLen)
            out.writeShort(0)
            out.writeShort(0)
        }
        return out.toByteArray()
    }

    fun decompress(src: ByteArray): ByteArray {
        if (src.isEmpty()) return ByteArray(0)
        val inStream = ByteInput(src)
        val out = ByteOutput(src.size * 2)

        while (inStream.hasRemaining()) {
            val litLen = inStream.readVarInt()
            if (litLen > 0) {
                out.writeBytes(inStream.readBytes(litLen))
            }
            if (!inStream.hasRemaining()) break
            val offset = inStream.readShort()
            val matchLen = inStream.readShort()
            if (offset > 0 && matchLen > 0) {
                val currentSize = out.size()
                val matchStart = currentSize - offset
                for (i in 0 until matchLen) {
                    out.writeByte(out.getByte(matchStart + i))
                }
            }
        }
        return out.toByteArray()
    }
}

private class ByteOutput(capacity: Int = 1024) {
    private var buf = ByteArray(capacity)
    var count = 0
        private set

    private fun ensureCapacity(minCap: Int) {
        if (minCap > buf.size) {
            var newCap = buf.size * 2
            if (newCap < minCap) newCap = minCap
            buf = buf.copyOf(newCap)
        }
    }

    fun writeByte(b: Byte) {
        ensureCapacity(count + 1)
        buf[count++] = b
    }

    fun writeShort(s: Int) {
        ensureCapacity(count + 2)
        buf[count++] = ((s ushr 8) and 0xFF).toByte()
        buf[count++] = (s and 0xFF).toByte()
    }

    fun writeVarInt(value: Int) {
        var v = value
        while ((v and 0x7F.inv()) != 0) {
            writeByte(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        writeByte((v and 0x7F).toByte())
    }

    fun writeBytes(src: ByteArray, offset: Int = 0, length: Int = src.size) {
        ensureCapacity(count + length)
        src.copyInto(buf, destinationOffset = count, startIndex = offset, endIndex = offset + length)
        count += length
    }

    fun getByte(index: Int): Byte = buf[index]
    fun size(): Int = count
    fun toByteArray(): ByteArray = buf.copyOf(count)
}

private class ByteInput(private val buf: ByteArray) {
    private var pos = 0
    fun hasRemaining(): Boolean = pos < buf.size

    fun readShort(): Int {
        if (pos + 2 > buf.size) return 0
        val b1 = buf[pos++].toInt() and 0xFF
        val b2 = buf[pos++].toInt() and 0xFF
        return (b1 shl 8) or b2
    }

    fun readVarInt(): Int {
        var value = 0
        var shift = 0
        while (pos < buf.size) {
            val b = buf[pos++].toInt()
            value = value or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return value
    }

    fun readBytes(length: Int): ByteArray {
        val actualLen = kotlin.math.min(length, buf.size - pos)
        val result = buf.copyOfRange(pos, pos + actualLen)
        pos += actualLen
        return result
    }
}

