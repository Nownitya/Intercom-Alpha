package org.nowni.intercom_alpha.headset

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withContext
import platform.AVFAudio.*
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
class HeadsetManagerImpl(
    private val scope: CoroutineScope
) : HeadsetManager {

    private val connectedHeadsetsChannel = Channel<List<HeadsetInfo>>(Channel.CONFLATED)
    private val activeHeadsetChannel = Channel<HeadsetInfo?>(Channel.CONFLATED)
    private val batteryLevelChannel = Channel<Int>(Channel.CONFLATED)
    private val buttonEventsChannel = Channel<HeadsetButtonEvent>(Channel.BUFFERED)

    override val connectedHeadsets: ReceiveChannel<List<HeadsetInfo>> = connectedHeadsetsChannel
    override val activeHeadset: ReceiveChannel<HeadsetInfo?> = activeHeadsetChannel
    override val batteryLevel: ReceiveChannel<Int> = batteryLevelChannel
    override val buttonEvents: ReceiveChannel<HeadsetButtonEvent> = buttonEventsChannel

    private var buttonListener: HeadsetButtonListener? = null
    private var isRunning = false
    private var routeChangeObserver: NSObject? = null

    override suspend fun start() {
        withContext(Dispatchers.Default) {
            if (isRunning) return@withContext
            isRunning = true

            registerAudioRouteObserver()
            setupRemoteCommands()
            updateAudioRoutes()
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.Default) {
            isRunning = false
            routeChangeObserver?.let {
                NSNotificationCenter.defaultCenter.removeObserver(it)
            }
            routeChangeObserver = null
            connectedHeadsetsChannel.trySend(emptyList())
            activeHeadsetChannel.trySend(null)
        }
    }

    override suspend fun connectHeadset(address: String) {
        setActiveHeadset(address)
    }

    override suspend fun disconnectHeadset(address: String) {
        setScoAudioRoute(false)
        updateAudioRoutes()
    }

    override suspend fun setActiveHeadset(address: String) {
        withContext(Dispatchers.Default) {
            val session = AVAudioSession.sharedInstance()
            val availableInputs = session.availableInputs?.filterIsInstance<AVAudioSessionPortDescription>()
            val targetInput = availableInputs?.find { it.UID == address }
            if (targetInput != null) {
                session.setPreferredInput(targetInput, error = null)
            }
            updateAudioRoutes()
        }
    }

    override fun setButtonListener(listener: HeadsetButtonListener) {
        this.buttonListener = listener
    }

    override fun setScoAudioRoute(enabled: Boolean) {
        val session = AVAudioSession.sharedInstance()
        val options = if (enabled) {
            AVAudioSessionCategoryOptionDefaultToSpeaker or
                    AVAudioSessionCategoryOptionAllowBluetooth or
                    AVAudioSessionCategoryOptionAllowBluetoothA2DP
        } else {
            AVAudioSessionCategoryOptionDefaultToSpeaker
        }
        session.setCategory(AVAudioSessionCategoryPlayAndRecord, withOptions = options, error = null)
        session.setActive(true, error = null)
        updateAudioRoutes()
    }

    private fun registerAudioRouteObserver() {
        routeChangeObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVAudioSessionRouteChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _: NSNotification? ->
            updateAudioRoutes()
        } as? NSObject
    }

    private fun setupRemoteCommands() {
        val commandCenter = MPRemoteCommandCenter.sharedCommandCenter()

        commandCenter.togglePlayPauseCommand.addTargetWithHandler { _ ->
            dispatchButtonEvent(HeadsetButtonEvent.PTT_PRESS)
            MPRemoteCommandHandlerStatusSuccess
        }

        commandCenter.nextTrackCommand.addTargetWithHandler { _ ->
            dispatchButtonEvent(HeadsetButtonEvent.VOLUME_UP)
            MPRemoteCommandHandlerStatusSuccess
        }

        commandCenter.previousTrackCommand.addTargetWithHandler { _ ->
            dispatchButtonEvent(HeadsetButtonEvent.VOLUME_DOWN)
            MPRemoteCommandHandlerStatusSuccess
        }
    }

    private fun dispatchButtonEvent(event: HeadsetButtonEvent) {
        buttonEventsChannel.trySend(event)
        buttonListener?.onButtonEvent(event)
    }

    private fun updateAudioRoutes() {
        val session = AVAudioSession.sharedInstance()
        val currentRoute = session.currentRoute
        val inputs = session.availableInputs?.filterIsInstance<AVAudioSessionPortDescription>() ?: emptyList()
        val outputs = currentRoute.outputs.filterIsInstance<AVAudioSessionPortDescription>()

        val list = mutableListOf<HeadsetInfo>()
        var active: HeadsetInfo? = null

        for (port in inputs) {
            val isBluetooth = port.portType == AVAudioSessionPortBluetoothHFP ||
                    port.portType == AVAudioSessionPortBluetoothA2DP ||
                    port.portType == AVAudioSessionPortBluetoothLE

            val isCurrent = outputs.any { it.UID == port.UID }

            val info = HeadsetInfo(
                address = port.UID,
                name = port.portName,
                batteryLevel = -1,
                isConnected = true,
                supportsA2dp = port.portType == AVAudioSessionPortBluetoothA2DP,
                supportsHfp = port.portType == AVAudioSessionPortBluetoothHFP,
                supportsSco = isBluetooth
            )

            if (isBluetooth) {
                list.add(info)
            }
            if (isCurrent && isBluetooth) {
                active = info
            }
        }

        connectedHeadsetsChannel.trySend(list)
        activeHeadsetChannel.trySend(active)
    }
}
