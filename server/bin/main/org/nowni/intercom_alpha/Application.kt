package org.nowni.intercom_alpha

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import org.nowni.intercom_alpha.protocol.PeerInfo
import org.nowni.intercom_alpha.signaling.PeerSession
import org.nowni.intercom_alpha.signaling.SignalingServerManager
import kotlin.time.Duration.Companion.seconds

val signalingManager = SignalingServerManager()

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        get("/") {
            call.respondText(sayHello("Intercom Alpha Server"))
        }

        get("/api/status") {
            val metrics = signalingManager.getMetrics()
            call.respondText(metrics.toString())
        }

        webSocket("/ws/intercom") {
            val peerIdParam = call.request.queryParameters["peerId"] ?: "peer_${System.currentTimeMillis()}"
            val displayNameParam = call.request.queryParameters["name"] ?: "User_$peerIdParam"
            val platformParam = call.request.queryParameters["platform"] ?: "Unknown"

            val peerInfo = PeerInfo(
                peerId = peerIdParam,
                displayName = displayNameParam,
                platform = platformParam
            )
            val peerSession = PeerSession(this, peerInfo)

            signalingManager.registerPeer(peerSession)

            try {
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        signalingManager.handleMessage(peerIdParam, frame.readText())
                    }
                }
            } catch (e: Exception) {
                // Connection error or closed
            } finally {
                signalingManager.unregisterPeer(peerIdParam)
            }
        }
    }
}