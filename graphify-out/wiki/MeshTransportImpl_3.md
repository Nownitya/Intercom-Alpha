# MeshTransportImpl

> God node · 35 connections · `app/sharedLogic/src/iosMain/kotlin/org/nowni/intercom_alpha/mesh/MeshTransport.ios.kt`

**Community:** [MeshTransportImpl](MeshTransportImpl.md)

## Connections by Relation

### calls
- randomUUID() `INFERRED`

### contains
- [MeshTransport.ios.kt](MeshTransport.ios.kt.md) `EXTRACTED`

### implements
- [MeshTransport](MeshTransport.md) `EXTRACTED`

### method
- .start() `EXTRACTED`
- .processIncomingPacket() `EXTRACTED`
- .broadcastPacket() `EXTRACTED`
- .sendAudio() `EXTRACTED`
- .setupCoreBluetooth() `EXTRACTED`
- .setupMultipeerConnectivity() `EXTRACTED`
- .removePeer() `EXTRACTED`
- .updatePeersList() `EXTRACTED`
- .sendControl() `EXTRACTED`
- .handleRelay() `EXTRACTED`
- .stop() `EXTRACTED`
- .cleanupStalePeers() `EXTRACTED`
- .updateConfig() `EXTRACTED`
- .stopCoreBluetooth() `EXTRACTED`
- .stopMultipeerConnectivity() `EXTRACTED`

### references
- Peer `EXTRACTED`
- MeshPacket `EXTRACTED`
- MeshConfig `EXTRACTED`
- ConnectionState `EXTRACTED`
- CBPeripheralManager `EXTRACTED`
- CBCentralManager `EXTRACTED`
- ReceiveChannel `EXTRACTED`
- CBPeripheralManagerDelegateProtocol `EXTRACTED`
- CBCentralManagerDelegateProtocol `EXTRACTED`
- CBPeripheralDelegateProtocol `EXTRACTED`
- MCSession `EXTRACTED`
- MCNearbyServiceAdvertiser `EXTRACTED`
- MCNearbyServiceBrowser `EXTRACTED`
- MCSessionDelegateProtocol `EXTRACTED`
- MCNearbyServiceAdvertiserDelegateProtocol `EXTRACTED`
- MCNearbyServiceBrowserDelegateProtocol `EXTRACTED`
- CBMutableCharacteristic `EXTRACTED`

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*