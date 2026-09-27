package org.nowni.intercom_alpha.mesh

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.group.GroupManager.Companion.INVITE_CODE_VERSION
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class MeshTransportImpl(
    private val context: Context,
    private val scope: CoroutineScope
) : MeshTransport, LifecycleEventObserver {

    private val TAG = "MeshTransportImpl"
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "#type"
    }

    private val peersChannel = Channel<List<Peer>>(Channel.CONFLATED)
    private val incomingAudioChannel = Channel<MeshPacket.Audio>()
    private val incomingControlChannel = Channel<MeshPacket.Control>()
    private val connectionStateChannel = Channel<ConnectionState>(Channel.CONFLATED)

    override val peers: ReceiveChannel<List<Peer>> = peersChannel
    override val incomingAudio: ReceiveChannel<MeshPacket.Audio> = incomingAudioChannel
    override val incomingControl: ReceiveChannel<MeshPacket.Control> = incomingControlChannel
    override val connectionState: ReceiveChannel<ConnectionState> = connectionStateChannel

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var gattServer: BluetoothGattServer? = null
    private var gattServerCallback: BluetoothGattServerCallback? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null
    private var config: MeshConfig? = null
    private var localPeerId: String = UUID.randomUUID().toString()
    private var sequenceCounter: Long = 0
    private val connectedPeers = ConcurrentHashMap<String, Peer>()
    private val discoveredDevices = ConcurrentHashMap<String, BluetoothDevice>()
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var connectionStateValue = ConnectionState.DISCONNECTED
    private val deduplicator = PacketDeduplicator()

    private companion object {
        const val SERVICE_UUID = MeshTransport.GATT_SERVICE_UUID
        const val CHARACTERISTIC_UUID = MeshTransport.GATT_CHARACTERISTIC_UUID
        const val MANUFACTURER_ID = MeshTransport.BLE_MANUFACTURER_ID
    }

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

        gattServerCallback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Device connected: ${device.address}")
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Device disconnected: ${device.address}")
                    removePeer(device.address)
                }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
                processIncomingPacket(device.address, value)
            }

            override fun onNotificationSent(device: BluetoothDevice, status: Int) {
                // Notification sent
            }
        }
    }

    override suspend fun start(groupId: String, peerName: String, meshConfig: MeshConfig) {
        config = meshConfig.copy(groupId = groupId, peerName = peerName)
        localPeerId = UUID.randomUUID().toString()
        isRunning = true
        connectionStateValue = ConnectionState.CONNECTING
        connectionStateChannel.trySend(connectionStateValue)

        setupGattServer()
        startAdvertising(peerName)
        startScanning()

        connectionStateValue = ConnectionState.CONNECTED
        connectionStateChannel.trySend(connectionStateValue)

        scope.launch(Dispatchers.Main) {
            while (isRunning) {
                updatePeersList()
                cleanupStalePeers()
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    override suspend fun stop() {
        isRunning = false
        stopAdvertising()
        stopScanning()
        closeGattServer()
        connectedPeers.clear()
        discoveredDevices.clear()
        deduplicator.clear()
        connectionStateValue = ConnectionState.DISCONNECTED
        connectionStateChannel.trySend(connectionStateValue)
    }

    override suspend fun sendAudio(data: ByteArray, profile: AudioProfile) {
        val packet = MeshPacket.Audio(
            senderId = localPeerId,
            sequence = sequenceCounter++,
            timestamp = System.currentTimeMillis(),
            data = data,
            profile = profile
        )
        broadcastPacket(packet)
    }

    override suspend fun sendControl(type: ControlType, payload: ByteArray) {
        val packet = MeshPacket.Control(
            senderId = localPeerId,
            type = type,
            payload = payload
        )
        broadcastPacket(packet)
    }

    override fun updateConfig(newConfig: MeshConfig) {
        config = newConfig
        if (isRunning) {
            restartAdvertising()
        }
    }

    private fun setupGattServer() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        gattServer = bluetoothManager?.openGattServer(context, gattServerCallback!!)

        val service = BluetoothGattService(UUID.fromString(SERVICE_UUID), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            UUID.fromString(CHARACTERISTIC_UUID),
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val descriptor = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic.addDescriptor(descriptor)
        service.addCharacteristic(characteristic)
        gattServer?.addService(service)
    }

    private fun closeGattServer() {
        gattServer?.close()
        gattServer = null
    }

    private fun startAdvertising(peerName: String) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addManufacturerData(MANUFACTURER_ID, buildAdvertiseData(peerName))
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d(TAG, "Advertising started")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Advertising failed: $errorCode")
            }
        }

        bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private fun stopAdvertising() {
        advertiseCallback?.let {
            bluetoothLeAdvertiser?.stopAdvertising(it)
        }
    }

    private fun restartAdvertising() {
        stopAdvertising()
        config?.let { startAdvertising(it.peerName) }
    }

    private fun buildAdvertiseData(peerName: String): ByteArray {
        val buffer = ByteBuffer.allocate(32)
        buffer.put(INVITE_CODE_VERSION.toByte())
        buffer.put((config?.groupId?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)).copyOf(16))
        buffer.put(peerName.toByteArray(Charsets.UTF_8).copyOf(16))
        return buffer.array()
    }

    private fun startScanning() {
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        val filters = mutableListOf<ScanFilter>()
        val filter = ScanFilter.Builder()
            .setManufacturerData(MANUFACTURER_ID, ByteArray(0))
            .build()
        filters.add(filter)

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val rssi = result.rssi
                val scanRecord = result.scanRecord

                if (scanRecord != null) {
                    val mfgData = scanRecord.getManufacturerSpecificData(MANUFACTURER_ID)
                    if (mfgData != null && mfgData.size >= 33) {
                        val buffer = ByteBuffer.wrap(mfgData)
                        val version = buffer.get()
                        val groupIdBytes = ByteArray(16)
                        buffer.get(groupIdBytes)
                        val peerNameBytes = ByteArray(16)
                        buffer.get(peerNameBytes)

                        val groupId = String(groupIdBytes, Charsets.UTF_8).trim { it.code == 0 }
                        val peerName = String(peerNameBytes, Charsets.UTF_8).trim { it.code == 0 }

                        if (groupId == config?.groupId && peerName != config?.peerName) {
                            discoveredDevices[device.address] = device
                            connectToDevice(device, peerName, rssi)
                        }
                    }
                }
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: $errorCode")
            }
        }

        bluetoothLeScanner?.startScan(filters, settings, scanCallback)
    }

    private fun stopScanning() {
        scanCallback?.let {
            bluetoothLeScanner?.stopScan(it)
        }
    }

    private fun connectToDevice(device: BluetoothDevice, peerName: String, rssi: Int) {
        device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices()
                    val peer = Peer(
                        id = device.address,
                        name = peerName,
                        rssi = rssi,
                        lastSeen = System.currentTimeMillis(),
                        isConnected = true,
                        audioProfile = config?.audioProfile ?: AudioProfile.default
                    )
                    connectedPeers[device.address] = peer
                    updatePeersList()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    removePeer(device.address)
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(UUID.fromString(SERVICE_UUID))
                    val characteristic = service?.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID))
                    characteristic?.let {
                        gatt.setCharacteristicNotification(it, true)
                        val descriptor = it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                processIncomingPacket(device.address, characteristic.value ?: ByteArray(0))
            }
        })
    }

    private fun processIncomingPacket(senderId: String, data: ByteArray) {
        scope.launch {
            try {
                val text = data.decodeToString()
                val packet = json.decodeFromString<MeshPacket>(text)

                if (!deduplicator.shouldProcess(packet)) {
                    Log.v(TAG, "Dropping duplicate mesh packet from $senderId")
                    return@launch
                }

                when (packet) {
                    is MeshPacket.Audio -> incomingAudioChannel.trySend(packet)
                    is MeshPacket.Control -> incomingControlChannel.trySend(packet)
                    is MeshPacket.Discovery -> handleDiscovery(senderId, packet)
                    is MeshPacket.Relay -> handleRelay(senderId, packet)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse packet", e)
            }
        }
    }

    private fun handleDiscovery(senderId: String, packet: MeshPacket.Discovery) {
        // Handle discovery packet
    }

    private fun handleRelay(senderId: String, packet: MeshPacket.Relay) {
        // 1. Deliver inner packet to local channels
        when (val inner = packet.packet) {
            is MeshPacket.Audio -> incomingAudioChannel.trySend(inner)
            is MeshPacket.Control -> incomingControlChannel.trySend(inner)
            is MeshPacket.Discovery -> handleDiscovery(packet.originalSenderId, inner)
            is MeshPacket.Relay -> {}
        }

        // 2. Multi-hop flooding relay: decrement TTL and forward to other peers
        if (packet.ttl > 1 && config?.enableRelay == true) {
            val relayPacket = packet.copy(ttl = packet.ttl - 1)
            broadcastPacket(relayPacket, excludeSender = senderId)
        }
    }

    private fun broadcastPacket(packet: MeshPacket, excludeSender: String? = null) {
        val text = json.encodeToString(packet)
        val data = text.toByteArray(Charsets.UTF_8)
        connectedPeers.forEach { (address, _) ->
            if (address != excludeSender) {
                sendToPeer(address, data)
            }
        }
    }

    private fun sendToPeer(address: String, data: ByteArray) {
        gattServer?.connectedDevices?.forEach { device ->
            if (device.address == address) {
                val service = gattServer?.getService(UUID.fromString(SERVICE_UUID))
                val characteristic = service?.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID))
                if (characteristic != null) {
                    characteristic.value = data
                    gattServer?.notifyCharacteristicChanged(device, characteristic, false)
                }
            }
        }
    }

    private fun removePeer(address: String) {
        connectedPeers.remove(address)
        updatePeersList()
    }

    private fun updatePeersList() {
        val peerList = connectedPeers.values.toList()
            .sortedByDescending { it.lastSeen }
        peersChannel.trySend(peerList)
    }

    private fun cleanupStalePeers() {
        val now = System.currentTimeMillis()
        val staleThreshold = 10000L // 10 seconds
        connectedPeers.entries.removeIf { (_, peer) ->
            now - peer.lastSeen > staleThreshold
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        // Handle lifecycle events
    }
}