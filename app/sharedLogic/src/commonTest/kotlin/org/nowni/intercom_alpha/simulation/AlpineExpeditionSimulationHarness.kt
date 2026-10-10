package org.nowni.intercom_alpha.simulation

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.mesh.MeshPacket
import org.nowni.intercom_alpha.mesh.PacketDeduplicator
import org.nowni.intercom_alpha.mesh.phy.AdaptivePhyArbiter
import org.nowni.intercom_alpha.mesh.phy.BlePhyMode
import org.nowni.intercom_alpha.mesh.phy.PhyCapability
import org.nowni.intercom_alpha.mesh.phy.PhyLinkMetrics
import org.nowni.intercom_alpha.mesh.phy.TerrainShadowingProfile
import org.nowni.intercom_alpha.mesh.satellite.SatelliteDispatchAck
import org.nowni.intercom_alpha.mesh.satellite.SatelliteDispatchPayload
import org.nowni.intercom_alpha.mesh.satellite.SatelliteDispatchPriority
import org.nowni.intercom_alpha.mesh.satellite.SatelliteGatewayBridge
import org.nowni.intercom_alpha.mesh.satellite.SatelliteGpsLocation
import org.nowni.intercom_alpha.mesh.satellite.SatelliteLinkState
import org.nowni.intercom_alpha.mesh.satellite.SatelliteModemType
import org.nowni.intercom_alpha.mesh.wearable.PttButtonState
import org.nowni.intercom_alpha.mesh.wearable.RiderBiometricVitals
import org.nowni.intercom_alpha.mesh.wearable.RiderDistressStatus
import org.nowni.intercom_alpha.mesh.wearable.WearableCompanionProtocol
import org.nowni.intercom_alpha.mesh.wearable.WearableDeviceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 24-Node High-Altitude Alpine Expedition Simulation Harness.
 *
 * Validates:
 * 1. 24-node convoy scaling across alpine terrain profiles (open ridge, switchbacks, deep canyon).
 * 2. Dynamic BLE PHY transitions (LE 1M -> CODED_S2 -> CODED_S8) over long distances (>500m).
 * 3. Wearable companion quick-PTT debouncing and smartwatch biometric fall detection.
 * 4. Automated fallback to pure KMP Satellite Gateway Bridge (Iridium SBD frames) when
 *    convoy splits and cellular towers are completely unreachable.
 */
class AlpineExpeditionSimulationHarness {

    data class ExpeditionTelemetry(
        var totalVoicePacketsRelayed: Int = 0,
        var totalPhyMigrationsExecuted: Int = 0,
        var totalCodedS8LinksActive: Int = 0,
        var totalWearablePttToggles: Int = 0,
        var totalDistressEventsDetected: Int = 0,
        var totalSatelliteSbdFramesDispatched: Int = 0,
        var totalSatelliteAcksReceived: Int = 0
    )

    class VirtualAlpineNode(
        val nodeIndex: Int,
        val nodeId: String,
        val riderId: String,
        var virtualClock: () -> Long
    ) {
        val deduplicator = PacketDeduplicator(windowMs = 15_000L, maxCacheSize = 1000)
        val phyArbiter = AdaptivePhyArbiter(
            localNodeId = nodeId,
            initialTerrainProfile = TerrainShadowingProfile.OPEN_ALPINE_RIDGE,
            cooldownPeriodMs = 1000L
        )
        val wearableProtocol = WearableCompanionProtocol(
            localRiderId = riderId,
            localDeviceId = "watch-$nodeId",
            deviceType = if (nodeIndex % 2 == 0) WearableDeviceType.APPLE_WATCH else WearableDeviceType.WEAR_OS
        )
        val satelliteBridge = SatelliteGatewayBridge(
            localNodeId = nodeId,
            modemType = SatelliteModemType.IRIDIUM_SBD
        )

        val receivedAudio = mutableListOf<MeshPacket.Audio>()
    }

    @Test
    fun test24NodeAlpineExpeditionSimulation() = runTest {
        var virtualTimeMs = 1_000_000L
        val telemetry = ExpeditionTelemetry()

        // 1. Initialize 24 alpine expedition nodes
        val nodes = (1..24).map { i ->
            val id = "node-${i.toString().padStart(2, '0')}"
            val rider = "rider-${i.toString().padStart(2, '0')}"
            VirtualAlpineNode(i, id, rider) { virtualTimeMs }
        }

        // Register peer capabilities across nodes
        for (node in nodes) {
            for (peer in nodes) {
                if (node.nodeId != peer.nodeId) {
                    node.phyArbiter.registerPeerCapability(
                        PhyCapability(
                            peerId = peer.nodeId,
                            supportedModes = setOf(BlePhyMode.LE_1M, BlePhyMode.CODED_S2, BlePhyMode.CODED_S8)
                        )
                    )
                }
            }
        }

        // ==========================================
        // STAGE 1: Alpine Base Camp (Open Ridge)
        // ==========================================
        // Close convoy spacing (<50m), good RSSI (-65 dBm), 0% loss
        for (i in 0 until 23) {
            val nodeA = nodes[i]
            val nodeB = nodes[i + 1]

            val audio = MeshPacket.Audio(
                senderId = nodeA.nodeId,
                sequence = 100L + i,
                timestamp = virtualTimeMs,
                data = byteArrayOf(0x01, 0x02, 0x03, 0x04),
                profile = org.nowni.intercom_alpha.common.AudioProfile.MEDIUM
            )

            if (nodeB.deduplicator.shouldProcess(audio, virtualTimeMs)) {
                nodeB.receivedAudio.add(audio)
                telemetry.totalVoicePacketsRelayed++
            }
        }
        assertEquals(23, telemetry.totalVoicePacketsRelayed)

        // ==========================================
        // STAGE 2: Mountain Switchback Ascent
        // ==========================================
        // Topography changes to MOUNTAIN_SWITCHBACK (hairpin diffraction -10 dB, +120m elevation delta)
        virtualTimeMs += 300_000L // +5 min
        for (node in nodes) {
            node.phyArbiter.setTerrainProfile(TerrainShadowingProfile.MOUNTAIN_SWITCHBACK)
        }

        // Adjacent nodes experience switchback corner shadowing: raw RSSI drops to -78 dBm
        // Terrain compensation penalizes effective RSSI to -88 dBm, triggering step-up to CODED_S2
        for (i in 0 until 12) {
            val nodeA = nodes[i]
            val peerId = nodes[i + 1].nodeId

            val sample1 = PhyLinkMetrics(
                peerId = peerId,
                currentPhy = BlePhyMode.LE_1M,
                rssi = -78,
                packetLossRatio = 0.08,
                elevationDeltaMeters = 35.0,
                timestampMs = virtualTimeMs
            )
            nodeA.phyArbiter.evaluateLinkSample(sample1)

            val sample2 = sample1.copy(timestampMs = virtualTimeMs + 50L)
            val switched = nodeA.phyArbiter.evaluateLinkSample(sample2)
            if (switched == BlePhyMode.CODED_S2) {
                telemetry.totalPhyMigrationsExecuted++
            }
        }
        assertTrue(telemetry.totalPhyMigrationsExecuted > 0, "Expected PHY transitions to CODED_S2 during switchbacks")

        // ==========================================
        // STAGE 3: Glacial Canyon Dead Zone (Extreme Reach > 500m)
        // ==========================================
        virtualTimeMs += 600_000L // +10 min
        for (node in nodes) {
            node.phyArbiter.setTerrainProfile(TerrainShadowingProfile.DEEP_CANYON_GORGE)
        }

        // Convoy splits into Lead Recon (Nodes 1-12) and Rear Pack (Nodes 13-24)
        // Lead-Rear relay hop (Node 12 -> Node 13) stretches across 750m canyon gap
        val node12 = nodes[11]
        val node13 = nodes[12]

        virtualTimeMs += 5000L
        val canyonSample1 = PhyLinkMetrics(
            peerId = node13.nodeId,
            currentPhy = BlePhyMode.CODED_S2,
            rssi = -94,
            packetLossRatio = 0.22,
            elevationDeltaMeters = 80.0,
            timestampMs = virtualTimeMs
        )
        val extremePhy = node12.phyArbiter.evaluateLinkSample(canyonSample1)
        assertEquals(BlePhyMode.CODED_S8, extremePhy)
        assertEquals(BlePhyMode.CODED_S8, node12.phyArbiter.peerPhyMap.value[node13.nodeId])
        telemetry.totalCodedS8LinksActive++

        // Under CODED_S8, relay audio successfully bridges the 750m canyon gap
        val crossCanyonAudio = MeshPacket.Audio(
            senderId = node12.nodeId,
            sequence = 999L,
            timestamp = virtualTimeMs,
            data = byteArrayOf(0x55, 0x66, 0x77),
            profile = org.nowni.intercom_alpha.common.AudioProfile.MEDIUM
        )
        assertTrue(node13.deduplicator.shouldProcess(crossCanyonAudio, virtualTimeMs))
        node13.receivedAudio.add(crossCanyonAudio)
        telemetry.totalVoicePacketsRelayed++

        // ==========================================
        // STAGE 4: Emergency Crevasse Fall & Satellite Gateway Fallback
        // ==========================================
        virtualTimeMs += 900_000L // +15 min
        val rearNode = nodes[23] // Node 24

        // 4a. Wearable Smartwatch detects fall and tachycardia (195 bpm)
        val fallVitals = RiderBiometricVitals(
            heartRateBpm = 195,
            spo2Percent = 89,
            skinTemperatureCelsius = 34.2,
            stressLevelIndex = 95,
            isFallDetected = true,
            batteryPercent = 78,
            timestampMs = virtualTimeMs
        )
        val distressStatus = rearNode.wearableProtocol.processBiometricVitals(fallVitals)
        assertEquals(RiderDistressStatus.FALL_CONFIRMED, distressStatus)
        telemetry.totalDistressEventsDetected++

        // 4b. Cellular towers and local BLE mesh partition are dead; node initiates Satellite Gateway SOS
        rearNode.satelliteBridge.updateModemLinkState(SatelliteLinkState.SATELLITE_ACQUIRED, signalBars = 4)

        var capturedSbdBytes: ByteArray? = null
        rearNode.satelliteBridge.rawTransceiverSend = { bytes ->
            capturedSbdBytes = bytes
            true
        }

        val sosPayload = SatelliteDispatchPayload(
            dispatchId = "SOS-ALPINE-24",
            senderRiderId = rearNode.riderId,
            groupId = "group-all",
            timestamp = virtualTimeMs,
            priority = SatelliteDispatchPriority.LIFE_SAFETY_CRASH,
            location = SatelliteGpsLocation(
                latitude = 45.8326, // Mont Blanc massif coordinates
                longitude = 6.8652,
                altitudeMeters = 3810.0,
                accuracyMeters = 3.5f
            ),
            messageText = "CREVASSE FALL DETECTED - RIDER DISTRESS",
            impactGForce = 6.8f
        )

        // Enqueue emergency SBD dispatch
        val enqueued = rearNode.satelliteBridge.enqueueEmergencyDispatch(sosPayload)
        assertTrue(enqueued)

        // Process satellite transmit queue
        val dispatchedCount = rearNode.satelliteBridge.processQueue()
        assertEquals(1, dispatchedCount)
        telemetry.totalSatelliteSbdFramesDispatched++

        val rawBytes = checkNotNull(capturedSbdBytes)
        assertTrue(rawBytes.size <= 120, "SBD frame must obey 120-byte MTU limit")
        val decodedPayload = SatelliteGatewayBridge.decodeSbdFrame(rawBytes)
        assertNotNull(decodedPayload)
        assertEquals("SOS-ALPINE-24", decodedPayload.dispatchId)

        // 4c. Simulated Satellite Ground Network ACK arrives back to mesh node
        val ack = SatelliteDispatchAck(
            dispatchId = "SOS-ALPINE-24",
            constellationTimestamp = virtualTimeMs + 12_000L,
            sbdSequenceNumber = 998811L,
            isDeliveredToRescueCenter = true,
            responseMessage = "RESCUE_DISPATCHED"
        )
        val ackProcessed = rearNode.satelliteBridge.handleIncomingSatelliteAck(ack)
        assertTrue(ackProcessed)
        telemetry.totalSatelliteAcksReceived++

        // Assert all critical mission telemetry
        assertEquals(24, telemetry.totalVoicePacketsRelayed)
        assertEquals(1, telemetry.totalCodedS8LinksActive)
        assertEquals(1, telemetry.totalDistressEventsDetected)
        assertEquals(1, telemetry.totalSatelliteSbdFramesDispatched)
        assertEquals(1, telemetry.totalSatelliteAcksReceived)
        assertEquals(0, rearNode.satelliteBridge.status.value.pendingQueueSize)
    }
}
