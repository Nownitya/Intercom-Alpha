package org.nowni.intercom_alpha.headset

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class HeadsetManagerImpl(
    private val context: Context,
    private val scope: CoroutineScope
) : HeadsetManager {

    private val TAG = "HeadsetManagerImpl"

    private val connectedHeadsetsChannel = Channel<List<HeadsetInfo>>(Channel.CONFLATED)
    private val activeHeadsetChannel = Channel<HeadsetInfo?>(Channel.CONFLATED)
    private val batteryLevelChannel = Channel<Int>(Channel.CONFLATED)
    private val buttonEventsChannel = Channel<HeadsetButtonEvent>(Channel.BUFFERED)

    override val connectedHeadsets: ReceiveChannel<List<HeadsetInfo>> = connectedHeadsetsChannel
    override val activeHeadset: ReceiveChannel<HeadsetInfo?> = activeHeadsetChannel
    override val batteryLevel: ReceiveChannel<Int> = batteryLevelChannel
    override val buttonEvents: ReceiveChannel<HeadsetButtonEvent> = buttonEventsChannel

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bluetoothHeadsetProxy: BluetoothHeadset? = null
    private var buttonListener: HeadsetButtonListener? = null
    private var activeDeviceAddress: String? = null
    private var isScoEnabled: Boolean = false
    private var isRunning: Boolean = false

    private val headsetsMap = ConcurrentHashMap<String, HeadsetInfo>()

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadsetProxy = proxy as? BluetoothHeadset
                Log.d(TAG, "BluetoothHeadset service connected")
                refreshConnectedHeadsets()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadsetProxy = null
                Log.d(TAG, "BluetoothHeadset service disconnected")
                refreshConnectedHeadsets()
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    Log.d(TAG, "Headset state changed: $state for device: ${device?.address}")
                    refreshConnectedHeadsets()
                }
                BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED -> {
                    val audioState = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_AUDIO_DISCONNECTED)
                    Log.d(TAG, "Headset SCO audio state: $audioState")
                }
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                    val scoState = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_DISCONNECTED)
                    Log.d(TAG, "SCO audio state updated: $scoState")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun start() = withContext(Dispatchers.Default) {
        if (isRunning) return@withContext
        isRunning = true

        bluetoothAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)

        val filter = IntentFilter().apply {
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        }
        context.registerReceiver(bluetoothReceiver, filter)
    }

    override suspend fun stop() = withContext(Dispatchers.Default) {
        if (!isRunning) return@withContext
        isRunning = false

        setScoAudioRoute(false)

        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Unregister receiver failed", e)
        }

        bluetoothHeadsetProxy?.let { proxy ->
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
            bluetoothHeadsetProxy = null
        }

        headsetsMap.clear()
        connectedHeadsetsChannel.trySend(emptyList())
        activeHeadsetChannel.trySend(null)
    }

    @SuppressLint("MissingPermission")
    override suspend fun connectHeadset(address: String) {
        activeDeviceAddress = address
        refreshConnectedHeadsets()
    }

    override suspend fun disconnectHeadset(address: String) {
        if (activeDeviceAddress == address) {
            activeDeviceAddress = null
        }
        refreshConnectedHeadsets()
    }

    override suspend fun setActiveHeadset(address: String) {
        activeDeviceAddress = address
        refreshConnectedHeadsets()
    }

    override fun setButtonListener(listener: HeadsetButtonListener) {
        this.buttonListener = listener
    }

    override fun setScoAudioRoute(enabled: Boolean) {
        isScoEnabled = enabled
        audioManager?.let { am ->
            if (enabled) {
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                am.startBluetoothSco()
                am.isBluetoothScoOn = true
                Log.d(TAG, "Bluetooth SCO audio route enabled")
            } else {
                am.stopBluetoothSco()
                am.isBluetoothScoOn = false
                am.mode = AudioManager.MODE_NORMAL
                Log.d(TAG, "Bluetooth SCO audio route disabled")
            }
        }
    }

    fun dispatchButtonEvent(event: HeadsetButtonEvent) {
        buttonEventsChannel.trySend(event)
        buttonListener?.onButtonEvent(event)
    }

    @SuppressLint("MissingPermission")
    private fun refreshConnectedHeadsets() {
        val proxy = bluetoothHeadsetProxy ?: return
        val connectedDevices = try {
            proxy.connectedDevices
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission missing for connectedDevices", e)
            emptyList<BluetoothDevice>()
        }

        val list = mutableListOf<HeadsetInfo>()
        for (device in connectedDevices) {
            val addr = device.address
            val name = device.name ?: "Bluetooth Headset ($addr)"
            val info = HeadsetInfo(
                address = addr,
                name = name,
                batteryLevel = -1,
                isConnected = true,
                supportsSco = true,
                supportsHfp = true
            )
            headsetsMap[addr] = info
            list.add(info)
        }

        connectedHeadsetsChannel.trySend(list)
        val active = list.firstOrNull { it.address == activeDeviceAddress } ?: list.firstOrNull()
        activeHeadsetChannel.trySend(active)
    }
}
