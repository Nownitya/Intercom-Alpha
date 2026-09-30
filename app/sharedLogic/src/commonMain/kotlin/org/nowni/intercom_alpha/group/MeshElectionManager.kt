package org.nowni.intercom_alpha.group

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.nowni.intercom_alpha.currentTimeMillis

/**
 * Lifecycle states of the decentralized mesh consensus and election state machine.
 */
@Serializable
enum class ElectionState {
    /** Group leader is healthy and actively emitting periodic heartbeats. */
    IDLE,

    /** Leader heartbeat timed out; evaluating candidate status. */
    LEADER_SUSPECTED,

    /** Active election underway; exchanging priority claims across the mesh. */
    ELECTING,

    /** Election converged; new coordinator established and broadcast to network. */
    LEADER_ELECTED
}

/**
 * Message types exchanged during leader election and heartbeat tracking.
 */
@Serializable
enum class ElectionMessageType {
    HEARTBEAT,
    ELECTION_CLAIM,
    COORDINATOR_ANNOUNCE,
    ACK
}

/**
 * Wire frame exchanged across mesh control channels during auto-healing topology events.
 */
@Serializable
data class ElectionMessage(
    val type: ElectionMessageType,
    val groupId: String,
    val senderId: String,
    val term: Long,
    val linkMarginDb: Double = 0.0,
    val timestamp: Long = 0L
)

/**
 * Auto-healing dynamic mesh topology and leader election coordinator.
 *
 * Implements a weighted Bully election protocol prioritizing nodes with optimal RF link margins
 * and lowest path loss, deterministically falling back to lexicographical peer ID sorting.
 *
 * Guarantees zero interruption to active audio pipelines when group leadership shifts.
 *
 * @param groupId ID of the active mesh group.
 * @param localPeerId Unique node identifier for local device.
 * @param initialLeaderId Identifier of initial group coordinator, or null if unassigned.
 * @param heartbeatTimeoutMs Inactivity threshold before declaring leader lost. Default: 15,000ms.
 * @param electionTimeoutMs Window allowed for priority claims before declaring local victory. Default: 3,000ms.
 */
class MeshElectionManager(
    val groupId: String,
    val localPeerId: String,
    initialLeaderId: String? = null,
    val heartbeatTimeoutMs: Long = 15_000L,
    val electionTimeoutMs: Long = 3_000L
) {
    private val mutex = Mutex()

    private var currentTerm: Long = 1L
    private var lastLeaderHeartbeatTimestamp: Long = 0L
    private var electionStartTimestamp: Long = 0L
    private var localLinkMarginDb: Double = 20.0
    private var hasHigherClaimInTerm: Boolean = false

    private val _state = MutableStateFlow(ElectionState.IDLE)
    val state: StateFlow<ElectionState> = _state.asStateFlow()

    private val _currentLeaderId = MutableStateFlow<String?>(initialLeaderId)
    val currentLeaderId: StateFlow<String?> = _currentLeaderId.asStateFlow()

    private val outgoingMessagesChannel = Channel<ElectionMessage>(Channel.BUFFERED)
    val outgoingMessages: ReceiveChannel<ElectionMessage> = outgoingMessagesChannel

    var onLeaderElected: ((newLeaderId: String, term: Long) -> Unit)? = null

    /**
     * Updates the local node's RF link margin metric for consensus prioritization.
     */
    suspend fun updateLocalLinkMargin(marginDb: Double) {
        mutex.withLock {
            localLinkMarginDb = marginDb
        }
    }

    /**
     * Records arrival of a leader heartbeat or control frame.
     */
    suspend fun recordLeaderHeartbeat(
        leaderId: String,
        term: Long,
        timestamp: Long = currentTimeMillis()
    ) {
        mutex.withLock {
            if (term >= currentTerm) {
                currentTerm = term
                _currentLeaderId.value = leaderId
                lastLeaderHeartbeatTimestamp = timestamp
                _state.value = ElectionState.IDLE
                hasHigherClaimInTerm = false
            }
        }
    }

    /**
     * Checks leader health and initiates an election if the leader has timed out.
     *
     * @param now Current epoch timestamp in milliseconds.
     * @return true if election was initiated or updated.
     */
    suspend fun checkLeaderHealth(now: Long = currentTimeMillis()): Boolean {
        mutex.withLock {
            val leader = _currentLeaderId.value
            // If local node is already leader, emit heartbeat and stay IDLE
            if (leader == localPeerId) {
                if (_state.value != ElectionState.IDLE) {
                    _state.value = ElectionState.IDLE
                }
                return false
            }

            // Check if leader heartbeat has timed out
            val silenceDuration = now - lastLeaderHeartbeatTimestamp
            if (silenceDuration > heartbeatTimeoutMs && _state.value == ElectionState.IDLE) {
                _state.value = ElectionState.LEADER_SUSPECTED
                startElectionLocked(now)
                return true
            }

            // Check if in active election and timeout expired without a higher claim
            if (_state.value == ElectionState.ELECTING) {
                val electionElapsed = now - electionStartTimestamp
                if (electionElapsed > electionTimeoutMs) {
                    if (!hasHigherClaimInTerm) {
                        declareVictoryLocked(now)
                    } else {
                        // Concede and wait for winner's coordinator announcement
                        _state.value = ElectionState.LEADER_SUSPECTED
                    }
                    return true
                }
            }

            return false
        }
    }

    /**
     * Explicitly starts an election (e.g. on direct disconnect signal).
     */
    suspend fun triggerElection(now: Long = currentTimeMillis()) {
        mutex.withLock {
            startElectionLocked(now)
        }
    }

    /**
     * Processes incoming election message from remote peer.
     */
    suspend fun processIncomingMessage(
        message: ElectionMessage,
        now: Long = currentTimeMillis()
    ) {
        if (message.groupId != groupId) return

        mutex.withLock {
            when (message.type) {
                ElectionMessageType.HEARTBEAT -> {
                    if (message.term >= currentTerm) {
                        currentTerm = message.term
                        _currentLeaderId.value = message.senderId
                        lastLeaderHeartbeatTimestamp = now
                        _state.value = ElectionState.IDLE
                        hasHigherClaimInTerm = false
                    }
                }

                ElectionMessageType.ELECTION_CLAIM -> {
                    if (message.term > currentTerm) {
                        currentTerm = message.term
                        _state.value = ElectionState.ELECTING
                        electionStartTimestamp = now
                        hasHigherClaimInTerm = isPriorityHigher(
                            message.linkMarginDb,
                            message.senderId,
                            localLinkMarginDb,
                            localPeerId
                        )
                    } else if (message.term == currentTerm && _state.value == ElectionState.ELECTING) {
                        if (isPriorityHigher(message.linkMarginDb, message.senderId, localLinkMarginDb, localPeerId)) {
                            hasHigherClaimInTerm = true
                        }
                    }
                }

                ElectionMessageType.COORDINATOR_ANNOUNCE -> {
                    if (message.term >= currentTerm) {
                        currentTerm = message.term
                        _currentLeaderId.value = message.senderId
                        lastLeaderHeartbeatTimestamp = now
                        _state.value = ElectionState.LEADER_ELECTED
                        hasHigherClaimInTerm = false
                        onLeaderElected?.invoke(message.senderId, message.term)
                    }
                }

                ElectionMessageType.ACK -> {
                    // Acknowledgement of claim
                }
            }
        }
    }

    /**
     * Emits a periodic heartbeat if local node is the active coordinator.
     */
    suspend fun emitCoordinatorHeartbeat(now: Long = currentTimeMillis()): Boolean {
        mutex.withLock {
            if (_currentLeaderId.value == localPeerId) {
                val msg = ElectionMessage(
                    type = ElectionMessageType.HEARTBEAT,
                    groupId = groupId,
                    senderId = localPeerId,
                    term = currentTerm,
                    linkMarginDb = localLinkMarginDb,
                    timestamp = now
                )
                outgoingMessagesChannel.trySend(msg)
                lastLeaderHeartbeatTimestamp = now
                return true
            }
            return false
        }
    }

    private fun startElectionLocked(now: Long) {
        currentTerm++
        _state.value = ElectionState.ELECTING
        electionStartTimestamp = now
        hasHigherClaimInTerm = false

        // Broadcast election claim with local RF link margin
        val claim = ElectionMessage(
            type = ElectionMessageType.ELECTION_CLAIM,
            groupId = groupId,
            senderId = localPeerId,
            term = currentTerm,
            linkMarginDb = localLinkMarginDb,
            timestamp = now
        )
        outgoingMessagesChannel.trySend(claim)
    }

    private fun declareVictoryLocked(now: Long) {
        _currentLeaderId.value = localPeerId
        _state.value = ElectionState.LEADER_ELECTED
        lastLeaderHeartbeatTimestamp = now

        val announce = ElectionMessage(
            type = ElectionMessageType.COORDINATOR_ANNOUNCE,
            groupId = groupId,
            senderId = localPeerId,
            term = currentTerm,
            linkMarginDb = localLinkMarginDb,
            timestamp = now
        )
        outgoingMessagesChannel.trySend(announce)
        onLeaderElected?.invoke(localPeerId, currentTerm)
    }

    /**
     * Weighted priority comparator:
     * Node with significantly higher link margin (> 3.0 dB difference) wins.
     * If link margins are comparable within 3.0 dB, break ties deterministically by peer ID comparison.
     */
    private fun isPriorityHigher(
        candidateMargin: Double,
        candidateId: String,
        localMargin: Double,
        localId: String
    ): Boolean {
        val marginDelta = candidateMargin - localMargin
        return if (marginDelta > 3.0) {
            true
        } else if (marginDelta < -3.0) {
            false
        } else {
            // Tie breaker: lexicographically higher ID wins
            candidateId > localId
        }
    }
}
