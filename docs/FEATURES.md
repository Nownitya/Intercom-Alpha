# Master Feature Catalog & Specification Matrix — Intercom-Alpha

> **Document Version:** 3.0.0-beta1  
> **Status:** Active Catalog (Sprints 01–36 Complete, Sprints 37–39 Planned)  
> **Repository:** `Intercom-Alpha`  

---

## 1. Completed & Verified Features (Through Sprint 36)

| Feature ID | Category | Feature Name | Target Release | DoD Verification Test Suite |
| :--- | :--- | :--- | :--- | :--- |
| **FEAT-01** | Audio | 16kHz PCM Audio Ingestion & Playback | v1.0.0 | `AudioEngineTest.kt` |
| **FEAT-02** | Audio | Dual-Threshold Noise Gate & VOX Detector | v1.0.0 | `VoiceActivityDetectorTest.kt` |
| **FEAT-03** | Audio | Multi-Rate Pure KMP 4-bit IMA ADPCM Codec | v1.0.0 | `AudioCodecTest.kt` |
| **FEAT-04** | Audio | RFC 3550 Adaptive Jitter Buffer (40–150ms) | v1.0.0 | `AdaptiveJitterBufferTest.kt` |
| **FEAT-05** | Audio | Binaural 3D Spatial Audio Convoy Panning | v2.0.0 | `SpatialAudioProcessorTest.kt` |
| **FEAT-06** | Audio | Dynamic Adaptive Bitrate Scaling (16–48 kbps) | v3.0.0-alpha2 | `AdaptiveBitrateEngineTest.kt` |
| **FEAT-07** | Audio | Pure KMP Systematic Block Erasure FEC | v3.0.0-alpha2 | `MeshPacketFecEngineTest.kt` |
| **FEAT-08** | Audio | Pitch-Synchronous Waveform PLC Interpolator | v3.0.0-alpha2 | `AudioLossConcealmentTest.kt` |
| **FEAT-09** | Mesh | Decentralized Leaderless BLE GATT Mesh (TTL=3)| v1.0.0 | `CrossPlatformGattMeshTest.kt` |
| **FEAT-10** | Mesh | Sliding-Window Packet Deduplicator (5s window)| v1.0.0 | `PacketDeduplicatorTest.kt` |
| **FEAT-11** | Mesh | AODV-Lite Dynamic Directed Routing | v2.9.0 | `AodvRoutingTest.kt` |
| **FEAT-12** | Mesh | Multi-Group Convoy Federation Gateway | v2.10.0 | `MultiGroupFederationSimulationHarness.kt`|
| **FEAT-13** | Mesh | Airtime QoS 4-Tier Priority Token Shaper | v2.11.0 | `AirtimeQoSTest.kt` |
| **FEAT-14** | Failover | Link Quality Arbiter ($Q_{link}$) & State Machine | v2.12.0 | `MultiPathFailoverArbiterTest.kt` |
| **FEAT-15** | Failover | Automated Dual-Stream Cellular SFU Hedging | v2.12.0 | `HybridTransportRouterTest.kt` |
| **FEAT-16** | Security| Pure Kotlin RFC 8439 ChaCha20-Poly1305 AEAD | v1.0.0 | `PacketCipherTest.kt` (RFC 8439 KAT) |
| **FEAT-17** | Security| Forward-Secret Epoch Key Rekeying Protocol | v2.13.0 | `GroupRekeyProtocolTest.kt` |
| **FEAT-18** | Safety | Autonomous Crash Impact Detection (>6.5g eCall)| v2.8.0 | `CrashDetectionTest.kt` |
| **FEAT-19** | UI/UX | Zero-Scroll Tactical Cockpit HUD | v3.0.0-alpha1 | `CockpitVisualVerificationTest.kt` |
| **FEAT-20** | UI/UX | 100% Native iOS SwiftUI Cockpit HUD | v3.0.0-alpha1 | `ContentView.swift` Verification |
| **FEAT-21** | UI/UX | Tactile Diagnostics, Offline Trail, & Message Sheets | v3.0.0-alpha1 | `SubScreenInteractionTest.kt` |
| **FEAT-22** | Telemetry| GPS Delta Telemetry Beacon & Off-Grid Radar | v2.0.0 | `TelemetryBeaconTest.kt` |
| **FEAT-23** | Telemetry| OTA BLE Mesh GPX/GeoJSON Trail Sharing | v2.6.0 | `TrailStorageTest.kt` |
| **FEAT-24** | Power | Foreground Service + Partial WakeLock (4h limit)| v1.0.0 | `BackgroundContinuityTest.kt` |
| **FEAT-25** | Mesh | BLE ATT MTU Frame Aggregator (247B MTU) | v3.0.0-alpha3 | `AttMtuAggregatorTest.kt` |
| **FEAT-26** | Mesh | Time-Diversity Matrix Block Interleaver | v3.0.0-alpha3 | `MatrixBlockInterleaverTest.kt` |
| **FEAT-27** | Geo | Pure KMP Offline Vector Topographic Tile Engine (<15ms) | v3.0.0-beta1 | `OfflineVectorTileEngineTest.kt` |
| **FEAT-28** | Geo | Real-Time Convoy Elevation & Incline Engine (Slope %, Δh)| v3.0.0-beta1 | `ConvoyElevationEngineTest.kt` |
| **FEAT-29** | UI/UX | Tactile Trail Elevation Profile HUD & Cockpit ELEV Tab | v3.0.0-beta1 | `AlpineElevationSimulationTest.kt` |
| **FEAT-30** | Mesh | Autonomous Fleet Mesh Congestion Governor & Backoff | v3.0.0-beta2 | `MeshCongestionGovernorTest.kt` |
| **FEAT-31** | Mesh | Adaptive Multi-PHY Switcher (LE 1M, LE 2M, LE Coded S8) | v3.0.0-beta2 | `AdaptivePhyManagerTest.kt` |
