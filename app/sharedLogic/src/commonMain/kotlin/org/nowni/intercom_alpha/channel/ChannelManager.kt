package org.nowni.intercom_alpha.channel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.nowni.intercom_alpha.currentTimeMillis

/**
 * Definition of an individual sub-group communication channel.
 */
@Serializable
data class IntercomChannel(
    val id: Int,
    val name: String,
    val isEmergencyChannel: Boolean = false,
    val isMuted: Boolean = false
)

/**
 * Immutable snapshot of active intercom channel configuration and monitoring.
 */
@Serializable
data class ChannelState(
    val activeChannelId: Int = 1,
    val monitoredChannelIds: Set<Int> = emptySet(),
    val channels: List<IntercomChannel> = defaultChannels(),
    val isScanMode: Boolean = false
) {
    val activeChannel: IntercomChannel
        get() = channels.firstOrNull { it.id == activeChannelId }
            ?: IntercomChannel(activeChannelId, "Channel $activeChannelId")

    companion object {
        fun defaultChannels(): List<IntercomChannel> {
            return (1..16).map { id ->
                when (id) {
                    1 -> IntercomChannel(id = 1, name = "Channel 1 — Convoy Main")
                    2 -> IntercomChannel(id = 2, name = "Channel 2 — Scouts & Lead")
                    3 -> IntercomChannel(id = 3, name = "Channel 3 — Sweep & Tail")
                    4 -> IntercomChannel(id = 4, name = "Channel 4 — Support Vehicle")
                    16 -> IntercomChannel(id = 16, name = "Channel 16 — Emergency SOS", isEmergencyChannel = true)
                    else -> IntercomChannel(id = id, name = "Channel $id")
                }
            }
        }
    }
}

/**
 * Channel-tagged audio frame exchanged over the mesh transport.
 */
@Serializable
data class ChannelAudioFrame(
    val channelId: Int,
    val senderId: String,
    val sequence: Long,
    val timestamp: Long,
    val data: ByteArray,
    val isGlobalBroadcast: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChannelAudioFrame) return false
        if (channelId != other.channelId) return false
        if (senderId != other.senderId) return false
        if (sequence != other.sequence) return false
        if (timestamp != other.timestamp) return false
        if (!data.contentEquals(other.data)) return false
        if (isGlobalBroadcast != other.isGlobalBroadcast) return false
        return true
    }

    override fun hashCode(): Int {
        var result = channelId
        result = 31 * result + senderId.hashCode()
        result = 31 * result + sequence.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + isGlobalBroadcast.hashCode()
        return result
    }
}

/**
 * Sub-channel partitioning, Dual-Watch monitoring, and frame isolation manager.
 *
 * Supports up to 16 distinct sub-channels across the shared BLE mesh network.
 * Allows riders to partition large touring groups (e.g. Lead Pack vs Sweep Pack)
 * while maintaining multi-channel Dual-Watch monitoring and global emergency overrides.
 */
class ChannelManager(
    initialActiveChannelId: Int = 1
) {
    private val mutex = Mutex()

    private val _channelState = MutableStateFlow(
        ChannelState(
            activeChannelId = initialActiveChannelId.coerceIn(1, 16),
            monitoredChannelIds = emptySet(),
            channels = ChannelState.defaultChannels(),
            isScanMode = false
        )
    )

    /**
     * Reactive StateFlow stream of current channel configuration observed by Compose/SwiftUI.
     */
    val channelState: StateFlow<ChannelState> = _channelState.asStateFlow()

    /**
     * Switches local transmission and primary reception to [channelId].
     */
    suspend fun selectActiveChannel(channelId: Int) {
        val validId = channelId.coerceIn(1, 16)
        mutex.withLock {
            val current = _channelState.value
            if (current.activeChannelId != validId) {
                _channelState.value = current.copy(activeChannelId = validId)
            }
        }
    }

    /**
     * Configures Dual-Watch monitored sub-channels that will be unmuted simultaneously with primary channel.
     */
    suspend fun setMonitoredChannels(channelIds: Set<Int>) {
        val sanitized = channelIds.filter { it in 1..16 }.toSet()
        mutex.withLock {
            _channelState.value = _channelState.value.copy(monitoredChannelIds = sanitized)
        }
    }

    /**
     * Toggles mute state of a specific channel.
     */
    suspend fun toggleChannelMute(channelId: Int) {
        mutex.withLock {
            val current = _channelState.value
            val updatedChannels = current.channels.map { ch ->
                if (ch.id == channelId) ch.copy(isMuted = !ch.isMuted) else ch
            }
            _channelState.value = current.copy(channels = updatedChannels)
        }
    }

    /**
     * Customizes display name for a specific channel.
     */
    suspend fun renameChannel(channelId: Int, newName: String) {
        mutex.withLock {
            val current = _channelState.value
            val updatedChannels = current.channels.map { ch ->
                if (ch.id == channelId) ch.copy(name = newName) else ch
            }
            _channelState.value = current.copy(channels = updatedChannels)
        }
    }

    /**
     * Toggles scan mode across all unmuted channels.
     */
    suspend fun setScanMode(enabled: Boolean) {
        mutex.withLock {
            _channelState.value = _channelState.value.copy(isScanMode = enabled)
        }
    }

    /**
     * Evaluates whether an incoming [frame] should be accepted for local audio playout.
     *
     * Rules:
     * 1. Global broadcasts (e.g. Emergency SOS) are ALWAYS accepted regardless of channel or mute.
     * 2. Scan Mode: accepts any unmuted channel.
     * 3. Normal Mode: accepts if matches activeChannel (and not muted) OR in monitoredChannelIds (and not muted).
     * 4. Muted channels are dropped unless global broadcast.
     */
    fun filterIncomingFrame(frame: ChannelAudioFrame): Boolean {
        if (frame.isGlobalBroadcast) {
            return true
        }

        val state = _channelState.value
        val frameChannel = state.channels.firstOrNull { it.id == frame.channelId }
        val isChannelMuted = frameChannel?.isMuted ?: false

        if (isChannelMuted) {
            return false
        }

        if (state.isScanMode) {
            return true
        }

        return frame.channelId == state.activeChannelId || state.monitoredChannelIds.contains(frame.channelId)
    }

    /**
     * Tags outgoing audio payload with active channel metadata for mesh broadcast.
     */
    fun tagOutgoingFrame(
        data: ByteArray,
        sequence: Long,
        senderId: String,
        isBroadcast: Boolean = false,
        timestamp: Long = currentTimeMillis()
    ): ChannelAudioFrame {
        val activeId = _channelState.value.activeChannelId
        return ChannelAudioFrame(
            channelId = activeId,
            senderId = senderId,
            sequence = sequence,
            timestamp = timestamp,
            data = data,
            isGlobalBroadcast = isBroadcast
        )
    }
}
