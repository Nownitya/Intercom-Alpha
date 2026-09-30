package org.nowni.intercom_alpha.channel

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelManagerTest {

    @Test
    fun testInitialState() {
        val manager = ChannelManager()
        val state = manager.channelState.value
        assertEquals(1, state.activeChannelId)
        assertEquals(16, state.channels.size)
        assertEquals("Channel 1 — Convoy Main", state.activeChannel.name)
        assertTrue(state.monitoredChannelIds.isEmpty())
        assertFalse(state.isScanMode)
    }

    @Test
    fun testSelectActiveChannel() = runTest {
        val manager = ChannelManager()
        manager.selectActiveChannel(2)
        assertEquals(2, manager.channelState.value.activeChannelId)
        assertEquals("Channel 2 — Scouts & Lead", manager.channelState.value.activeChannel.name)

        // Clamping check
        manager.selectActiveChannel(99)
        assertEquals(16, manager.channelState.value.activeChannelId)
        manager.selectActiveChannel(-5)
        assertEquals(1, manager.channelState.value.activeChannelId)
    }

    @Test
    fun testFilteringMatchingChannel() = runTest {
        val manager = ChannelManager(initialActiveChannelId = 2)

        val frameCh2 = ChannelAudioFrame(
            channelId = 2,
            senderId = "rider-1",
            sequence = 100L,
            timestamp = 1000L,
            data = byteArrayOf(1, 2, 3),
            isGlobalBroadcast = false
        )
        val frameCh3 = ChannelAudioFrame(
            channelId = 3,
            senderId = "rider-2",
            sequence = 101L,
            timestamp = 1000L,
            data = byteArrayOf(4, 5, 6),
            isGlobalBroadcast = false
        )

        assertTrue(manager.filterIncomingFrame(frameCh2))
        assertFalse(manager.filterIncomingFrame(frameCh3))
    }

    @Test
    fun testDualWatchMonitoring() = runTest {
        val manager = ChannelManager(initialActiveChannelId = 1)
        manager.setMonitoredChannels(setOf(3, 4))

        val frameCh1 = ChannelAudioFrame(1, "r1", 1L, 1000L, byteArrayOf(1))
        val frameCh3 = ChannelAudioFrame(3, "r3", 2L, 1000L, byteArrayOf(2))
        val frameCh5 = ChannelAudioFrame(5, "r5", 3L, 1000L, byteArrayOf(3))

        assertTrue(manager.filterIncomingFrame(frameCh1))
        assertTrue(manager.filterIncomingFrame(frameCh3)) // Monitored secondary channel accepted
        assertFalse(manager.filterIncomingFrame(frameCh5)) // Non-monitored channel rejected
    }

    @Test
    fun testMuteChannel() = runTest {
        val manager = ChannelManager(initialActiveChannelId = 1)
        val frameCh1 = ChannelAudioFrame(1, "r1", 1L, 1000L, byteArrayOf(1))

        assertTrue(manager.filterIncomingFrame(frameCh1))

        manager.toggleChannelMute(1)
        assertTrue(manager.channelState.value.activeChannel.isMuted)
        assertFalse(manager.filterIncomingFrame(frameCh1)) // Dropped when muted

        manager.toggleChannelMute(1)
        assertFalse(manager.channelState.value.activeChannel.isMuted)
        assertTrue(manager.filterIncomingFrame(frameCh1)) // Restored
    }

    @Test
    fun testGlobalEmergencyBroadcastAlwaysAccepted() = runTest {
        val manager = ChannelManager(initialActiveChannelId = 1)
        manager.toggleChannelMute(16) // Even if channel 16 is muted

        val emergencyFrame = ChannelAudioFrame(
            channelId = 16,
            senderId = "lead-rider",
            sequence = 999L,
            timestamp = 5000L,
            data = byteArrayOf(9, 9, 9),
            isGlobalBroadcast = true
        )

        // Global broadcast must bypass all channel mutes and mismatches
        assertTrue(manager.filterIncomingFrame(emergencyFrame))
    }

    @Test
    fun testScanModeAcceptsAllUnmuted() = runTest {
        val manager = ChannelManager(initialActiveChannelId = 1)
        manager.setScanMode(true)
        manager.toggleChannelMute(4) // Mute channel 4 specifically

        val frameCh2 = ChannelAudioFrame(2, "r2", 1L, 1000L, byteArrayOf(2))
        val frameCh4 = ChannelAudioFrame(4, "r4", 2L, 1000L, byteArrayOf(4))

        assertTrue(manager.filterIncomingFrame(frameCh2)) // Unmuted channel passes scan
        assertFalse(manager.filterIncomingFrame(frameCh4)) // Muted channel dropped even in scan
    }

    @Test
    fun testTagOutgoingFrame() = runTest {
        val manager = ChannelManager(initialActiveChannelId = 3)
        val payload = byteArrayOf(10, 20, 30)

        val frame = manager.tagOutgoingFrame(
            data = payload,
            sequence = 42L,
            senderId = "local-rider",
            isBroadcast = false,
            timestamp = 12345L
        )

        assertEquals(3, frame.channelId)
        assertEquals("local-rider", frame.senderId)
        assertEquals(42L, frame.sequence)
        assertEquals(12345L, frame.timestamp)
        assertTrue(payload.contentEquals(frame.data))
        assertFalse(frame.isGlobalBroadcast)
    }
}
