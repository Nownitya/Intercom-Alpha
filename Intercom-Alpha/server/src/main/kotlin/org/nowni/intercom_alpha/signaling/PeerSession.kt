package org.nowni.intercom_alpha.signaling

import io.ktor.websocket.DefaultWebSocketSession
import org.nowni.intercom_alpha.protocol.PeerInfo

data class PeerSession(
    val session: DefaultWebSocketSession,
    var info: PeerInfo
)
