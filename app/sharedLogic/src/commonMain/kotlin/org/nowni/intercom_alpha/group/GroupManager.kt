package org.nowni.intercom_alpha.group

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.Serializable
import org.nowni.intercom_alpha.mesh.Peer

interface GroupManager {
    val currentGroup: ReceiveChannel<Group?>
    val peers: ReceiveChannel<List<Peer>>
    val isLeader: ReceiveChannel<Boolean>
    val inviteCode: ReceiveChannel<String>

    suspend fun createGroup(name: String, config: GroupConfig): Group
    suspend fun joinGroup(inviteCode: String): Group
    suspend fun leaveGroup()
    suspend fun updatePeerName(name: String)
    suspend fun kickPeer(peerId: String)
    suspend fun transferLeadership(peerId: String)
    fun generateInviteCode(): String
    fun parseInviteCode(code: String): InviteInfo?

    companion object {
        const val INVITE_CODE_PREFIX = "INTERCOM:"
        const val INVITE_CODE_VERSION = 1
        const val INVITE_CODE_EXPIRY_MS = 24 * 60 * 60 * 1000 // 24 hours
    }
}

@Serializable
data class Group(
    val id: String,
    val name: String,
    val leaderId: String,
    val createdAt: Long,
    val config: GroupConfig
)

@Serializable
data class GroupConfig(
    val maxPeers: Int = 8,
    val audioProfile: org.nowni.intercom_alpha.common.AudioProfile = org.nowni.intercom_alpha.common.AudioProfile.default,
    val enableEncryption: Boolean = false,
    val enableRelay: Boolean = true,
    val autoAcceptJoin: Boolean = false
)

@Serializable
data class InviteInfo(
    val groupId: String,
    val groupName: String,
    val leaderId: String,
    val expiresAt: Long
)