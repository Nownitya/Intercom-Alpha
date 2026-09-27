package org.nowni.intercom_alpha.group

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.mesh.MeshTransport
import org.nowni.intercom_alpha.mesh.Peer
import org.nowni.intercom_alpha.randomUUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class GroupManagerImpl(
    private val transport: MeshTransport,
    private val localPeerId: String = randomUUID()
) : GroupManager {

    private val json = Json { ignoreUnknownKeys = true }

    private val currentGroupChannel = Channel<Group?>(Channel.CONFLATED)
    private val peersChannel = Channel<List<Peer>>(Channel.CONFLATED)
    private val isLeaderChannel = Channel<Boolean>(Channel.CONFLATED)
    private val inviteCodeChannel = Channel<String>(Channel.CONFLATED)

    override val currentGroup: ReceiveChannel<Group?> = currentGroupChannel
    override val peers: ReceiveChannel<List<Peer>> = peersChannel
    override val isLeader: ReceiveChannel<Boolean> = isLeaderChannel
    override val inviteCode: ReceiveChannel<String> = inviteCodeChannel

    private var activeGroup: Group? = null

    override suspend fun createGroup(name: String, config: GroupConfig): Group {
        val group = Group(
            id = randomUUID(),
            name = name,
            leaderId = localPeerId,
            createdAt = currentTimeMillis(),
            config = config
        )
        activeGroup = group
        currentGroupChannel.trySend(group)
        isLeaderChannel.trySend(true)

        val code = generateInviteCode()
        inviteCodeChannel.trySend(code)
        return group
    }

    override suspend fun joinGroup(inviteCode: String): Group {
        val info = parseInviteCode(inviteCode)
            ?: throw IllegalArgumentException("Invalid or expired invite code")

        val group = Group(
            id = info.groupId,
            name = info.groupName,
            leaderId = info.leaderId,
            createdAt = currentTimeMillis(),
            config = GroupConfig()
        )
        activeGroup = group
        currentGroupChannel.trySend(group)
        isLeaderChannel.trySend(info.leaderId == localPeerId)
        inviteCodeChannel.trySend(inviteCode)
        return group
    }

    override suspend fun leaveGroup() {
        activeGroup = null
        currentGroupChannel.trySend(null)
        isLeaderChannel.trySend(false)
        inviteCodeChannel.trySend("")
    }

    override suspend fun updatePeerName(name: String) {
        // Broadcast peer info update over mesh control channel
    }

    override suspend fun kickPeer(peerId: String) {
        // Kick peer if leader
    }

    override suspend fun transferLeadership(peerId: String) {
        val current = activeGroup ?: return
        if (current.leaderId == localPeerId) {
            val updated = current.copy(leaderId = peerId)
            activeGroup = updated
            currentGroupChannel.trySend(updated)
            isLeaderChannel.trySend(peerId == localPeerId)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun generateInviteCode(): String {
        val group = activeGroup ?: return ""
        val info = InviteInfo(
            groupId = group.id,
            groupName = group.name,
            leaderId = group.leaderId,
            expiresAt = currentTimeMillis() + GroupManager.INVITE_CODE_EXPIRY_MS
        )
        val jsonStr = json.encodeToString(info)
        val b64Str = Base64.encode(jsonStr.encodeToByteArray())
        return "${GroupManager.INVITE_CODE_PREFIX}v${GroupManager.INVITE_CODE_VERSION}:$b64Str"
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun parseInviteCode(code: String): InviteInfo? {
        if (!code.startsWith(GroupManager.INVITE_CODE_PREFIX)) return null
        val parts = code.substring(GroupManager.INVITE_CODE_PREFIX.length).split(":")
        if (parts.size != 2) return null

        val versionStr = parts[0]
        if (!versionStr.startsWith("v")) return null
        val version = versionStr.substring(1).toIntOrNull() ?: return null
        if (version != GroupManager.INVITE_CODE_VERSION) return null

        return try {
            val b64Str = parts[1]
            val jsonBytes = Base64.decode(b64Str)
            val jsonStr = jsonBytes.decodeToString()
            val info = json.decodeFromString<InviteInfo>(jsonStr)
            if (currentTimeMillis() > info.expiresAt) null else info
        } catch (e: Exception) {
            null
        }
    }
}
