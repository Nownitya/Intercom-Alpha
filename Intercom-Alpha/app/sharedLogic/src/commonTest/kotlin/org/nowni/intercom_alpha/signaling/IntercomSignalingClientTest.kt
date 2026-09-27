package org.nowni.intercom_alpha.signaling

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.protocol.SignalingMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntercomSignalingClientTest {

    @Test
    fun testClientInitialState() {
        val client = IntercomSignalingClient()
        assertEquals(SignalingConnectionState.Disconnected, client.connectionState.value)
    }

    @Test
    fun testRoomStateFlow() = runTest {
        val client = IntercomSignalingClient()
        assertEquals(null, client.currentRoomState.value)
    }
}
