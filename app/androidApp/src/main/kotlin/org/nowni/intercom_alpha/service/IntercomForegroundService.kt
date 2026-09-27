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
import org.nowni.intercom_alpha.mesh.MeshConfig
import org.nowni.intercom_alpha.mesh.MeshTransport
import org.nowni.intercom_alpha.mesh.MeshTransportImpl
import org.nowni.intercom_alpha.mesh.Peer
import org.nowni.intercom_alpha.power.PowerManagerHelper

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
            updateNotification()
        }
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

                // 4. Pipe incoming Mesh audio packets directly to AudioEngine playback
                audioRxJob?.cancel()
                audioRxJob = serviceScope.launch(Dispatchers.Default) {
                    for (packet in meshTransport.incomingAudio) {
                        try {
                            val pcmShorts = audioEngine.decode(packet.data, packet.profile)
                            audioEngine.playAudio(pcmShorts)
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
            // Leak-prevention: Ensure partial wake lock is always released
            powerManagerHelper.releaseWakeLock()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "IntercomForegroundService onDestroy")
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
