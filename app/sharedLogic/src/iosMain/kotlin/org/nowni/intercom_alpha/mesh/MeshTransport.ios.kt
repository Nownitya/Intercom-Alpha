package org.nowni.intercom_alpha.mesh

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.randomUUID
import platform.CoreBluetooth.*
import platform.Foundation.*
import platform.MultipeerConnectivity.*
import platform.darwin.NSObject
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), size.toULong())
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len == 0) return ByteArray(0)
    val result = ByteArray(len)
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, this@toByteArray.length)
    }
    return result
}

@OptIn(ExperimentalForeignApi::class)
class MeshTransportImpl(
    private val scope: CoroutineScope
) : MeshTransport {

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "#type"
    }
    private val deduplicator = PacketDeduplicator()

    private val peersChannel = Channel<List<Peer>>(Channel.CONFLATED)
    private val incomingAudioChannel = Channel<MeshPacket.Audio>(Channel.BUFFERED)
    private val incomingControlChannel = Channel<MeshPacket.Control>(Channel.BUFFERED)
    private val connectionStateChannel = Channel<ConnectionState>(Channel.CONFLATED)

    override val peers: ReceiveChannel<List<Peer>> = peersChannel
    override val incomingAudio: ReceiveChannel<MeshPacket.Audio> = incomingAudioChannel
    override val incomingControl: ReceiveChannel<MeshPacket.Control> = incomingControlChannel
    override val connectionState: ReceiveChannel<ConnectionState> = connectionStateChannel

    private var peripheralManager: CBPeripheralManager? = null
    private var centralManager: CBCentralManager? = null
    private var peripheralDelegate: CBPeripheralManagerDelegateProtocol? = null
    private var centralDelegate: CBCentralManagerDelegateProtocol? = null
    private var remotePeripheralDelegate: CBPeripheralDelegateProtocol? = null

    private var transferCharacteristic: CBMutableCharacteristic? = null
    private val discoveredPeripherals = mutableMapOf<String, CBPeripheral>()
    private val connectedPeers = mutableMapOf<String, Peer>()

    // MultipeerConnectivity for high-speed iOS local mesh
    private var mcSession: MCSession? = null
    private var mcAdvertiser: MCNearbyServiceAdvertiser? = null
    private var mcBrowser: MCNearbyServiceBrowser? = null
    private var mcSessionDelegate: MCSessionDelegateProtocol? = null
    private var mcAdvertiserDelegate: MCNearbyServiceAdvertiserDelegateProtocol? = null
    private var mcBrowserDelegate: MCNearbyServiceBrowserDelegateProtocol? = null

    private var config: MeshConfig? = null
    private var localPeerId: String = randomUUID()
    private var sequenceCounter: Long = 0
    private var isRunning = false
    private var connectionStateValue = ConnectionState.DISCONNECTED

    companion object {
        const val SERVICE_UUID_STRING = MeshTransport.GATT_SERVICE_UUID
        const val CHARACTERISTIC_UUID_STRING = MeshTransport.GATT_CHARACTERISTIC_UUID
        const val BLE_MANUFACTURER_ID = MeshTransport.BLE_MANUFACTURER_ID
        const val MC_SERVICE_TYPE = "intercom-mesh"
    }

    override suspend fun start(groupId: String, peerName: String, config: MeshConfig) {
        this.config = config.copy(groupId = groupId, peerName = peerName)
        localPeerId = randomUUID()
        isRunning = true
        connectionStateValue = ConnectionState.CONNECTING
        connectionStateChannel.trySend(connectionStateValue)

        setupCoreBluetooth(peerName)
        setupMultipeerConnectivity(peerName, groupId)

        connectionStateValue = ConnectionState.CONNECTED
        connectionStateChannel.trySend(connectionStateValue)

        scope.launch(Dispatchers.Default) {
            while (isRunning && isActive) {
                updatePeersList()
                cleanupStalePeers()
                delay(1000)
            }
        }
    }

    override suspend fun stop() {
        isRunning = false
        stopCoreBluetooth()
        stopMultipeerConnectivity()
        connectedPeers.clear()
        discoveredPeripherals.clear()
        deduplicator.clear()
        connectionStateValue = ConnectionState.DISCONNECTED
        connectionStateChannel.trySend(connectionStateValue)
    }

    override suspend fun sendAudio(data: ByteArray, profile: AudioProfile) {
        val packet = MeshPacket.Audio(
            senderId = localPeerId,
            sequence = sequenceCounter++,
            timestamp = currentTimeMillis(),
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

    override fun updateConfig(config: MeshConfig) {
        this.config = config
    }

    private fun setupCoreBluetooth(peerName: String) {
        val serviceUuid = CBUUID.UUIDWithString(SERVICE_UUID_STRING)
        val charUuid = CBUUID.UUIDWithString(CHARACTERISTIC_UUID_STRING)

        peripheralDelegate = object : NSObject(), CBPeripheralManagerDelegateProtocol {
            override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
                if (peripheral.state == CBManagerStatePoweredOn) {
                    val characteristic = CBMutableCharacteristic(
                        type = charUuid,
                        properties = CBCharacteristicPropertyWrite or CBCharacteristicPropertyNotify,
                        value = null,
                        permissions = CBAttributePermissionsWriteable
                    )
                    transferCharacteristic = characteristic

                    val service = CBMutableService(type = serviceUuid, primary = true)
                    service.setCharacteristics(listOf(characteristic))
                    peripheral.addService(service)

                    val advertisementData = mapOf<Any?, Any>(
                        CBAdvertisementDataServiceUUIDsKey to listOf(serviceUuid),
                        CBAdvertisementDataLocalNameKey to peerName
                    )
                    peripheral.startAdvertising(advertisementData)
                }
            }

            override fun peripheralManager(peripheral: CBPeripheralManager, didReceiveWriteRequests: List<*>) {
                for (item in didReceiveWriteRequests) {
                    val request = item as? CBATTRequest ?: continue
                    val data = request.value?.toByteArray()
                    if (data != null && data.isNotEmpty()) {
                        processIncomingPacket(request.central.identifier.UUIDString, data)
                    }
                    peripheral.respondToRequest(request, withResult = CBATTErrorSuccess)
                }
            }
        }

        peripheralManager = CBPeripheralManager(delegate = peripheralDelegate, queue = null)

        remotePeripheralDelegate = object : NSObject(), CBPeripheralDelegateProtocol {
            override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) {
                val services = peripheral.services ?: return
                for (item in services) {
                    val service = item as? CBService ?: continue
                    if (service.UUID == serviceUuid) {
                        peripheral.discoverCharacteristics(listOf(charUuid), forService = service)
                    }
                }
            }

            override fun peripheral(peripheral: CBPeripheral, didDiscoverCharacteristicsForService: CBService, error: NSError?) {
                val chars = didDiscoverCharacteristicsForService.characteristics ?: return
                for (item in chars) {
                    val char = item as? CBCharacteristic ?: continue
                    if (char.UUID == charUuid) {
                        peripheral.setNotifyValue(true, forCharacteristic = char)
                    }
                }
            }

            override fun peripheral(peripheral: CBPeripheral, didUpdateValueForCharacteristic: CBCharacteristic, error: NSError?) {
                val data = didUpdateValueForCharacteristic.value?.toByteArray() ?: return
                processIncomingPacket(peripheral.identifier.UUIDString, data)
            }
        }

        centralDelegate = object : NSObject(), CBCentralManagerDelegateProtocol {
            override fun centralManagerDidUpdateState(central: CBCentralManager) {
                if (central.state == CBManagerStatePoweredOn) {
                    central.scanForPeripheralsWithServices(
                        serviceUUIDs = listOf(serviceUuid),
                        options = mapOf<Any?, Any>(CBCentralManagerScanOptionAllowDuplicatesKey to false)
                    )
                }
            }

            override fun centralManager(central: CBCentralManager, didDiscoverPeripheral: CBPeripheral, advertisementData: Map<Any?, *>, RSSI: NSNumber) {
                val peerId = didDiscoverPeripheral.identifier.UUIDString
                val name = didDiscoverPeripheral.name ?: (advertisementData[CBAdvertisementDataLocalNameKey] as? String) ?: "Peer-$peerId"

                discoveredPeripherals[peerId] = didDiscoverPeripheral
                val peer = Peer(
                    id = peerId,
                    name = name,
                    rssi = RSSI.intValue,
                    lastSeen = currentTimeMillis(),
                    isConnected = true,
                    audioProfile = config?.audioProfile ?: AudioProfile.default
                )
                connectedPeers[peerId] = peer
                updatePeersList()

                didDiscoverPeripheral.delegate = remotePeripheralDelegate
                central.connectPeripheral(didDiscoverPeripheral, options = null)
            }

            override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
                didConnectPeripheral.discoverServices(listOf(serviceUuid))
            }

            override fun centralManager(central: CBCentralManager, didDisconnectPeripheral: CBPeripheral, error: NSError?) {
                val peerId = didDisconnectPeripheral.identifier.UUIDString
                removePeer(peerId)
            }
        }

        centralManager = CBCentralManager(delegate = centralDelegate, queue = null)
    }

    private fun stopCoreBluetooth() {
        peripheralManager?.stopAdvertising()
        centralManager?.stopScan()
        discoveredPeripherals.values.forEach { peripheral ->
            centralManager?.cancelPeripheralConnection(peripheral)
        }
        peripheralManager = null
        centralManager = null
    }

    private fun setupMultipeerConnectivity(peerName: String, groupId: String) {
        try {
            val peerId = MCPeerID(displayName = peerName)
            val session = MCSession(peer = peerId, securityIdentity = null, encryptionPreference = MCEncryptionNone)

            mcSessionDelegate = object : NSObject(), MCSessionDelegateProtocol {
                override fun session(session: MCSession, peer: MCPeerID, didChangeState: MCSessionState) {
                    val pId = peer.displayName
                    if (didChangeState == MCSessionState.MCSessionStateConnected) {
                        connectedPeers[pId] = Peer(
                            id = pId,
                            name = peer.displayName,
                            rssi = -40,
                            lastSeen = currentTimeMillis(),
                            isConnected = true,
                            audioProfile = config?.audioProfile ?: AudioProfile.default
                        )
                        updatePeersList()
                    } else if (didChangeState == MCSessionState.MCSessionStateNotConnected) {
                        removePeer(pId)
                    }
                }

                override fun session(session: MCSession, didReceiveData: NSData, fromPeer: MCPeerID) {
                    processIncomingPacket(fromPeer.displayName, didReceiveData.toByteArray())
                }

                override fun session(session: MCSession, didReceiveStream: NSInputStream, withName: String, fromPeer: MCPeerID) {}
                override fun session(session: MCSession, didStartReceivingResourceWithName: String, fromPeer: MCPeerID, withProgress: NSProgress) {}
                override fun session(session: MCSession, didFinishReceivingResourceWithName: String, fromPeer: MCPeerID, atURL: NSURL?, withError: NSError?) {}
            }
            session.delegate = mcSessionDelegate
            mcSession = session

            val discoveryInfo = mapOf<Any?, Any>("groupId" to groupId)
            val advertiser = MCNearbyServiceAdvertiser(peer = peerId, discoveryInfo = discoveryInfo, serviceType = MC_SERVICE_TYPE)
            mcAdvertiserDelegate = object : NSObject(), MCNearbyServiceAdvertiserDelegateProtocol {
                override fun advertiser(advertiser: MCNearbyServiceAdvertiser, didReceiveInvitationFromPeer: MCPeerID, withContext: NSData?, invitationHandler: (Boolean, MCSession?) -> Unit) {
                    invitationHandler(true, mcSession)
                }
            }
            advertiser.delegate = mcAdvertiserDelegate
            advertiser.startAdvertisingPeer()
            mcAdvertiser = advertiser

            val browser = MCNearbyServiceBrowser(peer = peerId, serviceType = MC_SERVICE_TYPE)
            mcBrowserDelegate = object : NSObject(), MCNearbyServiceBrowserDelegateProtocol {
                override fun browser(browser: MCNearbyServiceBrowser, foundPeer: MCPeerID, withDiscoveryInfo: Map<Any?, *>?) {
                    val peerGroupId = withDiscoveryInfo?.get("groupId") as? String
                    if (peerGroupId == config?.groupId) {
                        browser.invitePeer(foundPeer, toSession = mcSession!!, withContext = null, timeout = 10.0)
                    }
                }

                override fun browser(browser: MCNearbyServiceBrowser, lostPeer: MCPeerID) {
                    removePeer(lostPeer.displayName)
                }
            }
            browser.delegate = mcBrowserDelegate
            browser.startBrowsingForPeers()
            mcBrowser = browser
        } catch (e: Exception) {
            println("MultipeerConnectivity setup error: ${e.message}")
        }
    }

    private fun stopMultipeerConnectivity() {
        mcAdvertiser?.stopAdvertisingPeer()
        mcBrowser?.stopBrowsingForPeers()
        mcSession?.disconnect()
        mcAdvertiser = null
        mcBrowser = null
        mcSession = null
    }

    private fun processIncomingPacket(senderId: String, data: ByteArray) {
        scope.launch {
            try {
                val text = data.decodeToString()
                val packet = json.decodeFromString<MeshPacket>(text)

                if (!deduplicator.shouldProcess(packet)) {
                    println("Dropping duplicate mesh packet from $senderId")
                    return@launch
                }

                when (packet) {
                    is MeshPacket.Audio -> incomingAudioChannel.trySend(packet)
                    is MeshPacket.Control -> incomingControlChannel.trySend(packet)
                    is MeshPacket.Discovery -> {}
                    is MeshPacket.Relay -> handleRelay(senderId, packet)
                }
            } catch (e: Exception) {
                println("Failed to parse packet: ${e.message}")
            }
        }
    }

    private fun handleRelay(senderId: String, packet: MeshPacket.Relay) {
        // 1. Deliver inner packet to local channels
        when (val inner = packet.packet) {
            is MeshPacket.Audio -> incomingAudioChannel.trySend(inner)
            is MeshPacket.Control -> incomingControlChannel.trySend(inner)
            is MeshPacket.Discovery -> {}
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
        val data = text.encodeToByteArray()
        val nsData = data.toNSData()

        // 1. MultipeerConnectivity broadcast if available
        mcSession?.let { session ->
            val peers = session.connectedPeers
            if (peers.isNotEmpty()) {
                session.sendData(nsData, toPeers = peers, withMode = MCSessionSendDataMode.MCSessionSendDataUnreliable, error = null)
            }
        }

        // 2. CoreBluetooth GATT update
        transferCharacteristic?.let { char ->
            peripheralManager?.updateValue(nsData, forCharacteristic = char, onSubscribedCentrals = null)
        }

        // 3. Write to connected remote peripherals
        val remoteData = nsData
        discoveredPeripherals.forEach { (address, peripheral) ->
            if (address != excludeSender) {
                val serviceUuid = CBUUID.UUIDWithString(SERVICE_UUID_STRING)
                val charUuid = CBUUID.UUIDWithString(CHARACTERISTIC_UUID_STRING)
                val service = peripheral.services?.filterIsInstance<CBService>()?.find { it.UUID == serviceUuid }
                val characteristic = service?.characteristics?.filterIsInstance<CBCharacteristic>()?.find { it.UUID == charUuid }
                if (characteristic != null) {
                    peripheral.writeValue(remoteData, forCharacteristic = characteristic, type = CBCharacteristicWriteWithResponse)
                }
            }
        }
    }

    private fun removePeer(id: String) {
        connectedPeers.remove(id)
        updatePeersList()
    }

    private fun updatePeersList() {
        val peerList = connectedPeers.values.toList().sortedByDescending { it.lastSeen }
        peersChannel.trySend(peerList)
    }

    private fun cleanupStalePeers() {
        val now = currentTimeMillis()
        val staleThreshold = 10000L
        connectedPeers.entries.removeAll { (_, peer) ->
            now - peer.lastSeen > staleThreshold
        }
    }
}
