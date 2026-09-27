package org.nowni.intercom_alpha.headset

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.Serializable

interface HeadsetManager {
    val connectedHeadsets: ReceiveChannel<List<HeadsetInfo>>
    val activeHeadset: ReceiveChannel<HeadsetInfo?>
    val batteryLevel: ReceiveChannel<Int>
    val buttonEvents: ReceiveChannel<HeadsetButtonEvent>

    suspend fun start()
    suspend fun stop()
    suspend fun connectHeadset(address: String)
    suspend fun disconnectHeadset(address: String)
    suspend fun setActiveHeadset(address: String)
    fun setButtonListener(listener: HeadsetButtonListener)
    fun setScoAudioRoute(enabled: Boolean)
}

@Serializable
data class HeadsetInfo(
    val address: String,
    val name: String,
    val batteryLevel: Int = -1,
    val isConnected: Boolean = false,
    val supportsA2dp: Boolean = false,
    val supportsHfp: Boolean = false,
    val supportsSco: Boolean = false
)

enum class HeadsetButtonEvent {
    PTT_PRESS,
    PTT_RELEASE,
    VOLUME_UP,
    VOLUME_DOWN,
    ANSWER_CALL,
    END_CALL,
    VOICE_ASSISTANT
}

interface HeadsetButtonListener {
    fun onButtonEvent(event: HeadsetButtonEvent)
}