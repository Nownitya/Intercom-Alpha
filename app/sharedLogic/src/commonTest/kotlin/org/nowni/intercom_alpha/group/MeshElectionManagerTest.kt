package org.nowni.intercom_alpha.group

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MeshElectionManagerTest {

    @Test
    fun testInitialStateAndHeartbeatTracking() = runTest {
        val manager = MeshElectionManager(
            groupId = "group-100",
            localPeerId = "node-bob",
            initialLeaderId = "node-alice",
            heartbeatTimeoutMs = 15_000L
        )

        assertEquals(ElectionState.IDLE, manager.state.value)
        assertEquals("node-alice", manager.currentLeaderId.value)

        // Heartbeat received at t=5000ms
        manager.recordLeaderHeartbeat("node-alice", term = 1L, timestamp = 5000L)
        assertEquals(ElectionState.IDLE, manager.state.value)

        // Check health at t=10000ms (5000ms elapsed < 15000ms threshold)
        val triggered = manager.checkLeaderHealth(now = 10000L)
        assertFalse(triggered)
        assertEquals(ElectionState.IDLE, manager.state.value)
    }

    @Test
    fun testLeaderTimeoutDetectionAndVictory() = runTest {
        val manager = MeshElectionManager(
            groupId = "group-100",
            localPeerId = "node-bob",
            initialLeaderId = "node-alice",
            heartbeatTimeoutMs = 15_000L,
            electionTimeoutMs = 3_000L
        )

        manager.recordLeaderHeartbeat("node-alice", term = 1L, timestamp = 1000L)

        // Advance to t=17000ms (16000ms elapsed > 15000ms threshold)
        val triggered = manager.checkLeaderHealth(now = 17000L)
        assertTrue(triggered)
        assertEquals(ElectionState.ELECTING, manager.state.value)

        // Check that an ELECTION_CLAIM was broadcast
        val claimMsg = manager.outgoingMessages.tryReceive().getOrNull()
        assertNotNull(claimMsg)
        assertEquals(ElectionMessageType.ELECTION_CLAIM, claimMsg.type)
        assertEquals("node-bob", claimMsg.senderId)
        assertEquals(2L, claimMsg.term)

        // Advance past election timeout (t=20500ms > 17000 + 3000ms)
        var electedLeader: String? = null
        var electedTerm: Long? = null
        manager.onLeaderElected = { leader, term ->
            electedLeader = leader
            electedTerm = term
        }

        val resolved = manager.checkLeaderHealth(now = 20500L)
        assertTrue(resolved)
        assertEquals(ElectionState.LEADER_ELECTED, manager.state.value)
        assertEquals("node-bob", manager.currentLeaderId.value)
        assertEquals("node-bob", electedLeader)
        assertEquals(2L, electedTerm)

        // Verify COORDINATOR_ANNOUNCE was broadcast
        val announceMsg = manager.outgoingMessages.tryReceive().getOrNull()
        assertNotNull(announceMsg)
        assertEquals(ElectionMessageType.COORDINATOR_ANNOUNCE, announceMsg.type)
        assertEquals("node-bob", announceMsg.senderId)
    }

    @Test
    fun testYieldToHigherLinkMarginCandidate() = runTest {
        val manager = MeshElectionManager(
            groupId = "group-100",
            localPeerId = "node-bob",
            initialLeaderId = "node-alice",
            heartbeatTimeoutMs = 15_000L,
            electionTimeoutMs = 3_000L
        )
        manager.updateLocalLinkMargin(10.0) // Local has moderate 10 dB link margin

        // Leader times out at t=16000ms
        manager.checkLeaderHealth(now = 16000L)
        assertEquals(ElectionState.ELECTING, manager.state.value)
        // Drain local claim
        manager.outgoingMessages.tryReceive()

        // Remote competitor "node-charlie" has strong relay link margin (+25 dB)
        val charlieClaim = ElectionMessage(
            type = ElectionMessageType.ELECTION_CLAIM,
            groupId = "group-100",
            senderId = "node-charlie",
            term = 2L,
            linkMarginDb = 25.0,
            timestamp = 16500L
        )
        manager.processIncomingMessage(charlieClaim, now = 16500L)

        // At t=19500ms (election timeout reached), bob should NOT declare victory
        manager.checkLeaderHealth(now = 19500L)
        assertEquals(ElectionState.LEADER_SUSPECTED, manager.state.value)
        assertFalse(manager.currentLeaderId.value == "node-bob")

        // Charlie declares victory with coordinator announcement
        val announceFromCharlie = ElectionMessage(
            type = ElectionMessageType.COORDINATOR_ANNOUNCE,
            groupId = "group-100",
            senderId = "node-charlie",
            term = 2L,
            linkMarginDb = 25.0,
            timestamp = 19600L
        )
        manager.processIncomingMessage(announceFromCharlie, now = 19600L)

        assertEquals(ElectionState.LEADER_ELECTED, manager.state.value)
        assertEquals("node-charlie", manager.currentLeaderId.value)
    }

    @Test
    fun testTieBreakerByLexicographicalId() = runTest {
        val manager = MeshElectionManager(
            groupId = "group-100",
            localPeerId = "node-bravo", // "bravo" < "zulu"
            initialLeaderId = "node-alpha",
            heartbeatTimeoutMs = 15_000L,
            electionTimeoutMs = 3_000L
        )
        manager.updateLocalLinkMargin(20.0)

        manager.checkLeaderHealth(now = 16000L)
        assertEquals(ElectionState.ELECTING, manager.state.value)

        // Remote competitor "node-zulu" with EXACT same margin (20.0 dB)
        val zuluClaim = ElectionMessage(
            type = ElectionMessageType.ELECTION_CLAIM,
            groupId = "group-100",
            senderId = "node-zulu",
            term = 2L,
            linkMarginDb = 20.0,
            timestamp = 16200L
        )
        manager.processIncomingMessage(zuluClaim, now = 16200L)

        // Zulu is lexicographically greater than Bravo, so Bravo yields
        manager.checkLeaderHealth(now = 19500L)
        assertEquals(ElectionState.LEADER_SUSPECTED, manager.state.value)
        assertFalse(manager.currentLeaderId.value == "node-bravo")
    }

    @Test
    fun testCoordinatorEmitsHeartbeat() = runTest {
        val manager = MeshElectionManager(
            groupId = "group-100",
            localPeerId = "node-leader",
            initialLeaderId = "node-leader"
        )

        val emitted = manager.emitCoordinatorHeartbeat(now = 5000L)
        assertTrue(emitted)

        val msg = manager.outgoingMessages.tryReceive().getOrNull()
        assertNotNull(msg)
        assertEquals(ElectionMessageType.HEARTBEAT, msg.type)
        assertEquals("node-leader", msg.senderId)
        assertEquals(5000L, msg.timestamp)
    }
}
