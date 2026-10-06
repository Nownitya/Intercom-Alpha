package org.nowni.intercom_alpha.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.nowni.intercom_alpha.MainActivity
import org.nowni.intercom_alpha.audio.AudioEngine
import org.nowni.intercom_alpha.audio.AudioEngineConfig
import org.nowni.intercom_alpha.audio.AudioEngineImpl
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.group.GroupManager
import org.nowni.intercom_alpha.group.GroupManagerImpl
import org.nowni.intercom_alpha.headset.HeadsetButtonEvent
import org.nowni.intercom_alpha.headset.HeadsetManager
import org.nowni.intercom_alpha.headset.HeadsetManagerImpl
import org.nowni.intercom_alpha.mesh.ControlType
import org.nowni.intercom_alpha.mesh.MeshConfig
import org.nowni.intercom_alpha.mesh.MeshRepeaterService
import org.nowni.intercom_alpha.mesh.MeshTransport
import org.nowni.intercom_alpha.mesh.MeshTransportImpl
import org.nowni.intercom_alpha.mesh.Peer
import org.nowni.intercom_alpha.power.PowerManagerHelper
import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import org.nowni.intercom_alpha.audio.BlackboxAudioFrame
import org.nowni.intercom_alpha.audio.PriorityLevel
import org.nowni.intercom_alpha.bridge.AndroidPlatformBridge
import org.nowni.intercom_alpha.bridge.ControlPayloadTag
import org.nowni.intercom_alpha.channel.ChannelAudioFrame
import org.nowni.intercom_alpha.telemetry.ConvoyGpsTracker
import org.nowni.intercom_alpha.telemetry.GpsCoordinate
import org.nowni.intercom_alpha.text.MeshTextManager

class IntercomForegroundService : Service() {

    private val TAG = "IntercomForegroundService"
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val audioEngine: AudioEngine by lazy { AudioEngineImpl(applicationContext, serviceScope) }
    val meshTransport: MeshTransport by lazy { MeshTransportImpl(applicationContext, serviceScope) }
    val headsetManager: HeadsetManager by lazy { HeadsetManagerImpl(applicationContext, serviceScope) }
    val groupManager: GroupManager by lazy { GroupManagerImpl(meshTransport) }
    val powerManagerHelper: PowerManagerHelper by lazy { PowerManagerHelper(applicationContext) }

    private var audioRxJob: Job? = null
    private var audioTxJob: Job? = null
    private var peersJob: Job? = null
    private var headsetEventsJob: Job? = null
    private var controlRxJob: Job? = null
    private var locationListener: LocationListener? = null

    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<Peer>>(emptyList())
    val connectedPeers: StateFlow<List<Peer>> = _connectedPeers.asStateFlow()

    private var currentGroupId: String = "mesh-default"
    private var currentPeerName: String = Build.MODEL ?: "Android Rider"
    private var currentAudioProfile: AudioProfile = AudioProfile.MEDIUM
    private var isSessionActive = false

    inner class LocalBinder : Binder() {
        val service: IntercomForegroundService
            get() = this@IntercomForegroundService
    }

    private val binder = LocalBinder()

    companion object {
        const val CHANNEL_ID = "intercom_mesh_audio_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "org.nowni.intercom_alpha.action.START_SERVICE"
        const val ACTION_STOP = "org.nowni.intercom_alpha.action.STOP_SERVICE"
        const val ACTION_SET_PTT = "org.nowni.intercom_alpha.action.SET_PTT"

        const val EXTRA_PTT_ACTIVE = "org.nowni.intercom_alpha.extra.PTT_ACTIVE"
        const val EXTRA_GROUP_ID = "org.nowni.intercom_alpha.extra.GROUP_ID"
        const val EXTRA_PEER_NAME = "org.nowni.intercom_alpha.extra.PEER_NAME"

        fun startService(
            context: Context,
            groupId: String = "mesh-default",
            peerName: String = Build.MODEL ?: "Android Rider"
        ) {
            val intent = Intent(context, IntercomForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_GROUP_ID, groupId)
                putExtra(EXTRA_PEER_NAME, peerName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, IntercomForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun setPttActive(context: Context, active: Boolean) {
            val intent = Intent(context, IntercomForegroundService::class.java).apply {
                action = ACTION_SET_PTT
                putExtra(EXTRA_PTT_ACTIVE, active)
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        AndroidPlatformBridge.instance.bindService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "Service onStartCommand with action: $action")

        if (action == ACTION_STOP) {
            handleExplicitDisconnect()
            return START_NOT_STICKY
        }

        if (action == ACTION_SET_PTT) {
            val pttActive = intent.getBooleanExtra(EXTRA_PTT_ACTIVE, false)
            setTransmitting(pttActive)
            return START_STICKY
        }

        val requestedGroupId = intent?.getStringExtra(EXTRA_GROUP_ID) ?: currentGroupId
        val requestedPeerName = intent?.getStringExtra(EXTRA_PEER_NAME) ?: currentPeerName
        currentGroupId = requestedGroupId
        currentPeerName = requestedPeerName

        // Promote to foreground service immediately with connectedDevice|microphone types
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            try {
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } catch (e: Exception) {
                Log.w(TAG, "Failed starting foreground with specific types, falling back", e)
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!isSessionActive) {
            startIntercomSession(currentGroupId, currentPeerName)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun setTransmitting(transmitting: Boolean) {
        if (_isTransmitting.value != transmitting) {
            _isTransmitting.value = transmitting
            if (transmitting) {
                powerManagerHelper.acquirePttWakeLock()
            } else {
                powerManagerHelper.releasePttWakeLock()
            }
            updateNotification()
        }
    }

    private var isRepeaterMode: Boolean = false

    fun setRepeaterMode(active: Boolean) {
        Log.d(TAG, "setRepeaterMode($active)")
        isRepeaterMode = active
        if (active) {
            // Release audio recording and playout pipelines to save battery and dedicate RF to relay
            audioTxJob?.cancel()
            audioTxJob = null
            audioRxJob?.cancel()
            audioRxJob = null
            serviceScope.launch {
                try {
                    audioEngine.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping audio engine for repeater mode", e)
                }
            }
        } else {
            // Restore intercom audio pipelines if session is still active
            if (isSessionActive) {
                serviceScope.launch {
                    try {
                        audioEngine.start(AudioEngineConfig())
                    } catch (e: Exception) {
                        Log.e(TAG, "Error restarting audio engine after repeater mode", e)
                    }
                }
            }
        }
        updateNotification()
    }

    private fun startIntercomSession(groupId: String, peerName: String) {
        isSessionActive = true
        // Acquire CPU partial wake lock to prevent Doze mode from sleeping audio & BLE
        powerManagerHelper.acquireWakeLock()

        serviceScope.launch {
            try {
                // 1. Start Bluetooth Headset Manager and establish SCO audio routing lock
                headsetManager.start()
                headsetManager.setScoAudioRoute(true)

                // 2. Start AudioEngine with voice intercom configuration
                audioEngine.start(AudioEngineConfig())

                // 3. Start BLE Mesh Transport
                meshTransport.start(
                    groupId = groupId,
                    peerName = peerName,
                    config = MeshConfig(
                        groupId = groupId,
                        peerName = peerName,
                        audioProfile = currentAudioProfile
                    )
                )

                // 4. Pipe incoming Mesh audio packets directly to AudioEngine playback with Channel filtering and Blackbox caching
                audioRxJob?.cancel()
                audioRxJob = serviceScope.launch(Dispatchers.Default) {
                    for (packet in meshTransport.incomingAudio) {
                        try {
                            val bridge = AndroidPlatformBridge.instance
                            val activeChannelId = bridge.channelManager?.channelState?.value?.activeChannelId ?: 1
                            val channelAudioFrame = ChannelAudioFrame(
                                channelId = activeChannelId,
                                senderId = packet.senderId,
                                sequence = packet.sequence,
                                timestamp = packet.timestamp,
                                data = packet.data
                            )
                            val channelMgr = bridge.channelManager
                            if (channelMgr == null || channelMgr.filterIncomingFrame(channelAudioFrame)) {
                                val pcmShorts = audioEngine.decode(packet.data, packet.profile)
                                audioEngine.playAudio(pcmShorts)

                                bridge.blackboxRecorder?.recordFrame(
                                    BlackboxAudioFrame(
                                        timestampMs = packet.timestamp,
                                        sequence = packet.sequence,
                                        speakerId = packet.senderId,
                                        speakerName = "",
                                        priority = PriorityLevel.NORMAL,
                                        adpcmData = packet.data
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error playing incoming audio packet", e)
                        }
                    }
                }

                // 5. Pipe recorded AudioEngine microphone audio directly to MeshTransport when PTT active
                audioTxJob?.cancel()
                audioTxJob = serviceScope.launch(Dispatchers.Default) {
                    for (pcmShorts in audioEngine.recordedAudio) {
                        if (_isTransmitting.value) {
                            try {
                                val encoded = audioEngine.encode(pcmShorts, currentAudioProfile)
                                meshTransport.sendAudio(encoded, currentAudioProfile)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error encoding and sending audio packet", e)
                            }
                        }
                    }
                }

                // 6. Monitor active mesh peers and refresh sticky notification
                peersJob?.cancel()
                peersJob = serviceScope.launch(Dispatchers.Main) {
                    for (peerList in meshTransport.peers) {
                        _connectedPeers.value = peerList
                        updateNotification()
                    }
                }

                // 7. Observe physical headset button events (PTT buttons on helmet/earpieces)
                headsetEventsJob?.cancel()
                headsetEventsJob = serviceScope.launch(Dispatchers.Main) {
                    for (event in headsetManager.buttonEvents) {
                        when (event) {
                            HeadsetButtonEvent.PTT_PRESS -> setTransmitting(true)
                            HeadsetButtonEvent.PTT_RELEASE -> setTransmitting(false)
                            else -> {}
                        }
                    }
                }

                // 8. Pipe incoming Mesh control packets (canned hazard text, GPS telemetry, emergency SOS, repeater beacons/pongs)
                controlRxJob?.cancel()
                controlRxJob = serviceScope.launch(Dispatchers.Default) {
                    for (controlPacket in meshTransport.incomingControl) {
                        when (controlPacket.type) {
                            ControlType.REPEATER_PONG -> {
                                val pong = MeshRepeaterService.decodePongPayload(controlPacket)
                                if (pong != null) {
                                    AndroidPlatformBridge.instance.postIncomingRepeaterPong(pong)
                                }
                            }
                            ControlType.REPEATER_BEACON -> {
                                val beacon = MeshRepeaterService.decodeBeaconPayload(controlPacket)
                                if (beacon != null) {
                                    AndroidPlatformBridge.instance.postIncomingRepeaterBeacon(beacon)
                                }
                            }
                            else -> {
                                val payload = controlPacket.payload
                                if (payload.isNotEmpty()) {
                                    val tag = payload[0]
                                    val content = payload.copyOfRange(1, payload.size)
                                    when (tag) {
                                        ControlPayloadTag.TAG_TEXT -> {
                                            try {
                                                val wireFrame = MeshTextManager.decodeWireFrame(content)
                                                AndroidPlatformBridge.instance.postIncomingText(wireFrame)
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error decoding incoming text wire frame", e)
                                            }
                                        }
                                        ControlPayloadTag.TAG_GPS -> {
                                            try {
                                                val frame = ConvoyGpsTracker.decodeTelemetryFrame(content)
                                                AndroidPlatformBridge.instance.postIncomingGps(frame)
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error decoding incoming GPS frame", e)
                                            }
                                        }
                                        ControlPayloadTag.TAG_EMERGENCY -> {
                                            AndroidPlatformBridge.instance.postEmergencyAlert()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 9. Stream real-time Android GPS coordinates into ConvoyGpsTracker
                try {
                    val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                    if (locationManager != null &&
                        (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                         checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
                        locationListener = LocationListener { loc ->
                            AndroidPlatformBridge.instance.postLocalGps(
                                GpsCoordinate(
                                    latitude = loc.latitude,
                                    longitude = loc.longitude,
                                    altitudeMeters = loc.altitude,
                                    speedKmh = (loc.speed * 3.6).toFloat(),
                                    headingDegrees = loc.bearing,
                                    accuracyMeters = loc.accuracy,
                                    timestampMs = loc.time
                                )
                            )
                        }
                        locationListener?.let { listener ->
                            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, listener)
                            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 5f, listener)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "GPS location listener registration skipped", e)
                }

                updateNotification()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start intercom session", e)
            }
        }
    }

    private fun handleExplicitDisconnect() {
        Log.d(TAG, "Executing explicit user disconnect: stopping audio, mesh, and notification")
        try {
            isSessionActive = false
            _isTransmitting.value = false
            _connectedPeers.value = emptyList()

            // Cancel streaming pipelines
            audioRxJob?.cancel()
            audioRxJob = null
            audioTxJob?.cancel()
            audioTxJob = null
            peersJob?.cancel()
            peersJob = null
            headsetEventsJob?.cancel()
            headsetEventsJob = null
            controlRxJob?.cancel()
            controlRxJob = null
            locationListener?.let { listener ->
                val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                locationManager?.removeUpdates(listener)
                locationListener = null
            }
            AndroidPlatformBridge.instance.unbindService()

            serviceScope.launch {
                try {
                    headsetManager.setScoAudioRoute(false)
                    headsetManager.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping HeadsetManager", e)
                }
                try {
                    audioEngine.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping AudioEngine", e)
                }
                try {
                    meshTransport.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping MeshTransport", e)
                }

                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } finally {
            // Leak-prevention: Ensure both PTT and session partial wake locks are strictly released
            powerManagerHelper.releaseAllWakeLocks()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "IntercomForegroundService onDestroy")
        AndroidPlatformBridge.instance.unbindService()
        handleExplicitDisconnect()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Intercom Voice & Mesh Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps voice mesh network and audio active in background"
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            pendingIntentFlags
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, IntercomForegroundService::class.java).apply { action = ACTION_STOP },
            pendingIntentFlags
        )

        val peerCount = _connectedPeers.value.size
        val (contentText, subText) = when {
            _isTransmitting.value -> {
                "🔴 Transmitting Voice... ($peerCount peers connected)" to "Push-To-Talk Active"
            }
            peerCount > 0 -> {
                "🟢 Mesh Active: $peerCount peers connected | Listening" to "Connected to $currentGroupId"
            }
            else -> {
                "🟡 Mesh Active: Searching for peers | Listening" to "Connected to $currentGroupId"
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Intercom Mesh Active")
            .setContentText(contentText)
            .setSubText(subText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Leave Group", stopIntent)
            .build()
    }
}
