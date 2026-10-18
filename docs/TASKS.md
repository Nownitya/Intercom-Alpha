# Master Tasks & Engineering Roadmap — Intercom-Alpha

> **Document Version:** 3.0
> **Status:** Active / Tracked  
> **Current Sprint:** Sprint 36 Closed (`sprint/sprint-36-vector-trail-elevation-hud`) — v3.0.0-beta1 Release  
> **Obsidian Reference:** `01_Management/Roadmap.md` & `01_Management/Kanban.md`

---

## 🏆 Sprint History & Completed Milestones

### ✅ Sprint 01: Core Networking & Signaling Protocol (Phase 1)
- **Goal**: Establish polymorphic serialization protocol, Ktor signaling server, and multiplatform client contracts.
- **Completed**:
  - `ICA-101`: Polymorphic binary/JSON signaling protocol in `:core` (`SignalingProtocol.kt`).
  - `ICA-102`: Ktor WebSocket Server in `:server` (`/ws/intercom`, `/api/status`).
  - `ICA-103`: Multiplatform `IntercomSignalingClient.kt` in `app/sharedLogic`.
  - `ICA-104`: Full unit tests for client/server connection and session lifecycle.

### ✅ Sprint 02: Android Platform Engine & Compose UI (Phase 2)
- **Goal**: Implement low-latency audio capture/playback, BLE GATT advertising/scanning, and Compose interface.
- **Completed**:
  - `ICA-201`: Android `AudioRecord` / `AudioTrack` PCM streaming with VOX thresholding.
  - `ICA-202`: Android BLE GATT Server (`BluetoothGattServer`) and Client (`BluetoothGatt`).
  - `ICA-203`: Bluetooth SCO headset routing and hardware button listener (`HeadsetManager.android.kt`).
  - `ICA-204`: Initial Jetpack Compose intercom cockpit interface with live VU metering.

### ✅ Sprint 03: iOS Native Audio, CoreBluetooth Mesh & Cross-Platform GATT (Phase 2)
- **Goal**: Implement iOS platform engine matching Android GATT UUIDs with 100% native SwiftUI.
- **Completed**:
  - `ICA-301`: iOS `AVAudioEngine` input tap and player node scheduling (`AudioEngine.ios.kt`).
  - `ICA-302`: CoreBluetooth GATT dual-role manager + `MultipeerConnectivity` high-speed bridge.
  - `ICA-303`: iOS Headset routing (`AVAudioSessionCategoryPlayAndRecord` VoiceChat mode).
  - `ICA-304`: 100% Native SwiftUI UI (`ContentView.swift`) adhering to "Do Not Share UI with iOS".
  - `ICA-305`: Cross-platform GATT serialization symmetry tests in `CrossPlatformGattMeshTest.kt`.

### ✅ Sprint 04: Swift ↔ KMP Bridge & QR Code Onboarding (Phase 3)
- **Goal**: Establish seamless Swift interop and offline QR code group onboarding.
- **Completed**:
  - `ICA-401`: Thread-safe, non-blocking `IntercomBridge.kt` with cancellation handles in `iosMain`.
  - `ICA-402`: `AVFoundation` camera-based offline QR code scanner (`QrScannerView.swift`).
  - `ICA-403`: `INTERCOM:v1:` QR generator and parser in `GroupManagerImpl.kt`.
  - `ICA-404`: Obsidian Vault reorganization into standardized 8-pillar sequential structure.

### ✅ Sprint 05: Mesh Flooding Relay, Deduplication & Encryption (Phase 3)
- **Goal**: Multi-hop BLE mesh relay (TTL=3), sliding-window deduplication, and pure Kotlin ChaCha20-Poly1305 encryption.
- **Completed**:
  - `ICA-501`: Sliding-window `PacketDeduplicator.kt` (5,000ms TTL, 1,000 capacity, storm suppression).
  - `ICA-502`: Inner packet extraction and local audio delivery in Android & iOS transports.
  - `ICA-503`: Multi-hop TTL decrement ($3 \rightarrow 2 \rightarrow 1$) and neighbor forwarding.
  - `ICA-504`: Pure Kotlin RFC 8439 ChaCha20-Poly1305 AEAD cipher and SHA-256 key derivation (`PacketCipher.kt`).
  - `ICA-505`: Multi-hop simulation and relay unit tests in `CrossPlatformGattMeshTest.kt`.
  - `ICA-506`: Multiplatform smoke verification build (100% passing unit tests, clean Android debug APK & iOS simulator framework).

---

## 🚀 Active & Upcoming Sprints

### ✅ Sprint 06: Background Services & OS Power Management (Phase 4)
- **Goal**: Ensure continuous screen-off voice communication and relay for 4+ hours on Android and iOS.
- **Completed**:
  - `ICA-601`: Android Foreground Service with `connectedDevice|microphone` types and Bluetooth SCO audio routing lock (`IntercomForegroundService.kt`).
  - `ICA-602`: Android Partial WakeLock and Battery Optimization exemption request helper (`PowerManagerHelper.kt`).
  - `ICA-603`: iOS Background Audio and CoreBluetooth modes in `Info.plist` & `AVAudioSession` lifecycle in `iOSApp.swift`.
  - `ICA-604`: iOS background task assertions and keep-alive (`BackgroundKeepAlive.kt` & `IntercomBridge.kt`).
  - `ICA-605`: Screen-off continuity verification test suite with simulated burst traffic (`BackgroundContinuityTest.kt`).

---

### ✅ Sprint 07: Audio Codec Optimization, Jitter Buffering & Packet Loss Concealment (PLC) (Phase 5)
- **Goal**: Upgrade audio streaming from raw PCM to multi-rate compressed frames with adaptive jitter buffering, PLC, and noise gating.
- **Completed**:
  - `ICA-701`: Adaptive Jitter Buffer & Playout Sequencer with RFC 3550 variance estimation (`AdaptiveJitterBuffer.kt`).
  - `ICA-702`: Packet Loss Concealment (PLC) with pitch period extrapolation, exponential decay, and crossfade (`PacketLossConcealment.kt`).
  - `ICA-703`: Adaptive Noise Gate & VOX Energy DSP with attack, hold, release, and dual-threshold hysteresis (`NoiseGate.kt`).
  - `ICA-704`: Multi-Rate Pure KMP Audio Codec Engine with 4-bit IMA ADPCM and single BLE GATT MTU compliance (`AudioCodec.kt`).
  - `ICA-705`: Audio Pipeline End-to-End Integration & Benchmark Test verifying continuous playout under 20% loss and 50ms jitter (`AudioPipelineIntegrationTest.kt`).

---

## 🚀 Active & Upcoming Sprints

### ✅ Sprint 08: Real-World Field Testing & Hardening (Phase 6 - Complete)
- **Sprint Goal**: Verify multi-hop mesh range, highway wind noise intelligibility, and 4-hour battery consumption.
- **Milestone Release**: `v1.0.0` Production Release Tagged

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-801** | Field Range Benchmark Matrix & Diagnostic Mesh Logger | `MeshDiagnostics.kt`, `RangeBenchmark.md` | ✅ Completed |
| **ICA-802** | Highway Wind Noise & Helmet Intelligibility Tuning | `WindNoiseFilter.kt` | ✅ Completed |
| **ICA-803** | Battery Drain Profiling (<30% over 4 hours) | `PowerProfiler.kt` | ✅ Completed |
| **ICA-804** | v1.0 Production Release Readiness Sign-Off | `proguard-rules.pro`, `v1.0-Release-Readiness.md` | ✅ Completed |

---

### ✅ Sprint 09: Cellular Fallback & Cloud Relay Bridge (Phase 7 - v2.0 Roadmap)
- **Sprint Goal**: Bridge separated mesh clusters across long distances via Ktor WebSocket signaling server and cloud relay.
- **Status**: ✅ 100% Complete (All 4 Stories Passed)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-901** | Ktor WebSocket Cloud Mesh Relay Gateway | `:server`, `:core`, `CloudRelayBridge.kt` | ✅ Completed |
| **ICA-902** | Low-Power BLE Proximity Radar & Distance Alert UI | `sharedUI`, `ContentView.swift` | ✅ Completed |
| **ICA-903** | Dynamic Mesh Topology Auto-Healing & Leader Re-Election | `GroupManager.kt`, `MeshTransport.kt` | ✅ Completed |
| **ICA-904** | GitHub Actions Multiplatform CI/CD Pipeline | `.github/workflows/ci.yml` | ✅ Completed |

---

### ✅ Sprint 10: Multi-Channel Intercom, Priority Emergency & Audio Spatialization (Phase 8 - v2.0 Roadmap)
- **Sprint Goal**: Implement multi-channel sub-group partitioning, hazard emergency voice override, and binaural stereo audio spatialization.
- **Sprint Branch**: `sprint/sprint-10-multi-channel-spatial-audio`
- **Status**: ✅ 100% Complete (All 4 Stories Passed)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-1001** | Multi-Channel Group Partitioning & Channel Manager | `ChannelManager.kt`, `ChannelManagerTest.kt` | ✅ Completed |
| **ICA-1002** | Emergency Priority Voice & Hazard Alert Broadcast | `PriorityBroadcastManager.kt`, `PriorityBroadcastManagerTest.kt` | ✅ Completed |
| **ICA-1003** | Binaural Stereo Audio Spatializer & Convoy Panning | `SpatialAudioProcessor.kt`, `SpatialAudioProcessorTest.kt` | ✅ Completed |
| **ICA-1004** | Adaptive RF Interference & Frequency Map Diagnostic Engine | `RfInterferenceDetector.kt`, `RfInterferenceDetectorTest.kt` | ✅ Completed |

---

### ✅ Sprint 11: Convoy Situational Awareness, GPS Mesh Telemetry & Blackbox Voice Cache (Phase 9 - v2.0 Roadmap)
- **Sprint Goal**: Implement offline canned hazard text messaging, decentralized GPS mesh coordinate beaconing, LinkQuality-driven adaptive bitrate controller, and rolling emergency voice blackbox recorder.
- **Sprint Branch**: `sprint/sprint-11-convoy-gps-blackbox`
- **Status**: ✅ 100% Complete (All 4 Stories Passed & Merged into develop)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-1101** | Offline Mesh Canned Hazard & Quick Text Messaging | `MeshTextManager.kt`, `MeshTextManagerTest.kt` | ✅ Completed |
| **ICA-1102** | Decentralized GPS Mesh Telemetry & Convoy Tracking | `ConvoyGpsTracker.kt`, `ConvoyGpsTrackerTest.kt` | ✅ Completed |
| **ICA-1103** | Dynamic Adaptive Bitrate & Codec Profile Switching | `AdaptiveBitrateController.kt`, `AdaptiveBitrateControllerTest.kt` | ✅ Completed |
| **ICA-1104** | Rolling Emergency Voice Blackbox & Encrypted Incident Cache | `EmergencyBlackboxRecorder.kt`, `EmergencyBlackboxRecorderTest.kt` | ✅ Completed |

---

### ✅ Sprint 12: Multiplatform Cockpit UI & Bridge Integration (Phase 10 - v2.0 Roadmap)
- **Sprint Goal**: Deliver ergonomic cockpit UI components across Android Compose and 100% native iOS SwiftUI, complete with 16-channel dial, radar targets, convoy telemetry, quick hazard alerts, emergency preemption banner, and end-to-end integration tests.
- **Sprint Branch**: `sprint/sprint-12-cockpit-ui-integration`

| Story ID | Story Title | Key Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-1201** | KMP Swift Bridge Extension for v2.0 Subsystems | `IntercomBridge.kt` | ✅ Completed |
| **ICA-1202** | Native iOS SwiftUI Cockpit v2.0 UI Upgrade | `ContentView.swift`, `IntercomViewModel.swift` | ✅ Completed |
| **ICA-1203** | Android & Desktop Compose Multiplatform Cockpit Components | `HomeUiState.kt`, `HomeViewModel.kt`, `HomeScreen.kt`, `ChannelSelectorBar.kt`, `ProximityRadarView.kt`, `QuickHazardBar.kt`, `EmergencyPreemptionBanner.kt` | ✅ Completed |
| **ICA-1204** | Multiplatform End-to-End Presentation Integration Test | `CancellationHandle.kt`, `IntercomBridgeContractTest.kt`, `HomeViewModelTest.kt` | ✅ Completed |

---

### ✅ Sprint 13: v2.0 Production Release & Hardening (Phase 11 - v2.0 Roadmap)
- **Sprint Goal**: Wire Android platform hardware engine to Compose Cockpit UI, validate release build variants, verify battery/memory bounds, and perform v2.0.0 production release tagging.
- **Sprint Branch**: `sprint/sprint-13-v2-production-release`
- **Status**: ✅ 100% Complete (All 4 Stories Passed & v2.0.0 Tagged)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-1301** | Android Platform Hardware-to-Cockpit Service Bridge | `IntercomForegroundService.kt`, `AndroidPlatformBridge.kt`, `HomeViewModel.kt`, `HomeUiState.kt` | ✅ Completed |
| **ICA-1302** | Multiplatform Production Release Build & ProGuard Hardening | `build.gradle.kts`, `proguard-rules.pro`, `libs.versions.toml` | ✅ Completed |
| **ICA-1303** | Long-Running Battery, WakeLock & Memory Profiling Gate | `PowerProfilerTest.kt`, `IntercomForegroundService.kt` | ✅ Completed |
| **ICA-1304** | Architecture Sync, Documentation & v2.0.0 Release Tagging | `docs/PRD.md`, `CHANGELOG.md`, `TASKS.md`, `SPRINT.md`, `ARCHITECTURE.md` | ✅ Completed |

---

### ✅ Sprint 14: Advanced Convoy Mesh, Forward-Secret Key Ratchet & Live Activity (Phase 12 - v2.1 Roadmap)
- **Sprint Goal**: Deliver pure KMP Perfect Forward Secrecy (PFS) session key ratcheting, direct helmet Bluetooth GATT accessory integration (Cardo/Sena/EJEAS), native iOS 18+ Live Activity / Dynamic Island PTT widget, and multi-cluster cloud cross-mesh SFU bridging.
- **Sprint Branch**: `sprint/sprint-14-advanced-convoy-mesh`
- **Status**: ✅ 100% Complete (All 4 Stories Passed & v2.1.0 Release Tagged)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-1401** | Pure KMP Perfect Forward Secrecy (PFS) & Ephemeral Key Ratchet Engine | `MeshKeyRatchet.kt`, `PacketCipher.kt`, `MeshKeyRatchetTest.kt` | ✅ Completed |
| **ICA-1402** | Direct Helmet Intercom Bluetooth GATT Accessory Profile (Cardo / Sena / EJEAS) | `HelmetAccessoryManager.kt`, `HeadsetManager.kt`, `HelmetAccessoryManagerTest.kt` | ✅ Completed |
| **ICA-1403** | iOS 18+ Live Activity & Dynamic Island Hands-Free PTT Widget | `IntercomActivityAttributes.swift`, `IntercomActivityWidget.swift`, `IntercomViewModel.swift`, `IntercomBridge.kt` | ✅ Completed |
| **ICA-1404** | Multi-Cluster Cloud Cross-Mesh SFU Signaling & Audio Bridging | `SignalingProtocol.kt`, `SignalingServerManager.kt`, `CloudRelayBridge.kt`, `MultiClusterBridgeTest.kt` | ✅ Completed |

---

### 🚀 Sprint 15: Zero-Scroll Tactical Cockpit HUD & Hardware Telemetry Integration (Phase 13 - v2.2 Roadmap)
- **Sprint Goal**: Deliver end-to-end integration of Sprint 14 subsystems (`HelmetAccessoryManager`, `MeshKeyRatchet`, `CloudRelayBridge`) into presentation models, and build the Zero-Scroll Tactical Cockpit HUD across Compose Multiplatform (`sharedUI`) and 100% Native iOS SwiftUI (`iosApp`).
- **Sprint Branch**: `sprint/sprint-15-cockpit-hud-integration`
- **Status**: ✅ 100% Complete (All 4 Stories Implemented & Verified)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-1501** | Subsystem Telemetry Wiring (Accessory, PFS Ratchet, SFU Cloud) | `IntercomHardwareBridge.kt`, `AndroidPlatformBridge.kt`, `IntercomBridge.kt`, `HomeViewModel.kt`, `HomeUiState.kt` | ✅ Completed |
| **ICA-1502** | Compose Multiplatform Zero-Scroll Cockpit HUD | `HomeScreen.kt`, `PttButton.kt`, `QuickHazardBar.kt`, `ProximityRadarView.kt` | ✅ Completed |
| **ICA-1503** | Native iOS SwiftUI Tactical Cockpit HUD | `ContentView.swift`, `IntercomViewModel.swift` | ✅ Completed |
| **ICA-1504** | Multiplatform Verification Gates & Presentation Regression Tests | `HomeViewModelTest.kt`, Gradle automated tests, OpenCodeReview | ✅ Completed |

---

### ✅ Sprint 16: Mesh Repeater, Audio DSP & Convoy Simulation (Phase 14 - v2.3 Roadmap)
- **Sprint Goal**: Implement pure KMP speech dynamic range normalizers (AGC/DRC), autonomous stationary basecamp mesh repeater mode, automated 8-node convoy simulation harness, and over-the-air cryptographic epoch migration.
- **Sprint Branch**: `sprint/sprint-16-repeater-dsp-simulation` $\rightarrow$ `develop`
- **Status**: ✅ 100% Complete (All 4 Stories Passed & Merged to develop)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-1601** | Pure KMP Automatic Gain Control (AGC) & Dynamic Range Compression (DRC) | `AutomaticGainControl.kt`, `DynamicRangeCompressor.kt`, `AudioEngine.kt` | ✅ Completed |
| **ICA-1602** | Autonomous Mesh Repeater & Basecamp Beacon Mode | `MeshRepeaterService.kt`, `MeshTransport.kt` | ✅ Completed |
| **ICA-1603** | Automated Multi-Node Mesh Field Simulation Harness | `ConvoyMeshSimulationHarness.kt` | ✅ Completed |
| **ICA-1604** | Seamless Mesh OTA Group Re-Keying & Dynamic Epoch Migration | `GroupEpochManager.kt`, `GroupManager.kt` | ✅ Completed |

---

### ✅ Sprint 17: Basecamp Stationary Repeater UI & Low-Power Cockpit Mode (Phase 15 - v2.3 Roadmap)
- **Sprint Goal**: Implement dedicated Basecamp Stationary Repeater UI, high-contrast OLED power-saving screens, 100% native SwiftUI StandBy landscape cockpit, and live remote basecamp telemetry with 1-tap diagnostic pings on the main cockpit HUD.
- **Sprint Branch**: `sprint/sprint-17-basecamp-repeater-ui` $\rightarrow$ `develop`
- **Status**: ✅ 100% Complete (All 4 Stories Passed & Verified)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-1701** | Basecamp Repeater State Management & Platform Bridge Wiring | `RepeaterViewModel.kt`, `RepeaterUiState.kt`, `IntercomHardwareBridge.kt`, `AndroidPlatformBridge.kt`, `IntercomBridge.kt` | ✅ Completed |
| **ICA-1702** | Compose Multiplatform Basecamp Repeater Screen & OLED Dark Standby | `BasecampRepeaterScreen.kt`, `AppNavGraph.kt`, `HomeScreen.kt` | ✅ Completed |
| **ICA-1703** | Native iOS SwiftUI Basecamp Repeater Cockpit & StandBy Display | `BasecampRepeaterView.swift`, `ContentView.swift`, `IntercomViewModel.swift` | ✅ Completed |
| **ICA-1704** | Remote Repeater Telemetry & Diagnostic Ping Cockpit HUD Integration | `HomeScreen.kt`, `HomeViewModel.kt`, `HomeUiState.kt`, `ContentView.swift`, `HomeViewModelTest.kt` | ✅ Completed |

---

### 🚀 Sprint 18: Offline Vector Terrain & Trail Map Overlay (Phase 16 - v2.5 Roadmap)
- **Sprint Goal**: Implement pure KMP offline vector geometry projection, trail tracks, elevation contours, waypoint breadcrumbs, and seamless radar integration across Compose Multiplatform (`sharedUI`) and 100% Native iOS SwiftUI (`iosApp`).
- **Sprint Branch**: `sprint/sprint-18-offline-vector-terrain-map`
- **Status**: ✅ 100% Complete (All 4 Stories Passed & Verified)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-1801** | Pure KMP Offline Vector Trail & Terrain Geometry Engine | `VectorMapModels.kt`, `TrailMapEngine.kt`, `TrailMapManager.kt`, `TrailMapManagerTest.kt` | ✅ Completed |
| **ICA-1802** | Subsystem Bridge Wiring & Reactive Presentation Integration | `IntercomHardwareBridge.kt`, `IntercomBridge.kt`, `HomeViewModel.kt`, `HomeViewModelTest.kt` | ✅ Completed |
| **ICA-1803** | Compose Multiplatform Radar Topo Contour & Vector Trail Overlay | `ProximityRadarView.kt`, `OfflineTrailCockpitSheet.kt`, `HomeScreen.kt` | ✅ Completed |
| **ICA-1804** | Native iOS SwiftUI Radar Topographic Overlay & Offline Trail Cockpit Sheet | `ContentView.swift`, `OfflineTrailCockpitSheet.swift`, `IntercomViewModel.swift` | ✅ Completed |

---

### 🚀 Sprint 19: Over-The-Air BLE Mesh GPX/GeoJSON Trail Sharing & Storage Manager (Phase 17 - v2.6 Roadmap)
- **Sprint Goal**: Implement pure KMP chunked file transmission engine (Adler32, sliding-window retransmission, MTU <= 120 bytes), GPX/GeoJSON parsers and exporters, and seamless over-the-air route beaming across Compose Multiplatform (`sharedUI`) and 100% Native iOS SwiftUI (`iosApp`).
- **Sprint Branch**: `sprint/sprint-19-mesh-trail-sharing`
- **Status**: ✅ 100% Complete (All 4 Stories Passed & Verified)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-1901** | Pure KMP Chunked Mesh File Transfer & GPX/GeoJSON Protocol Engine | `MeshFileModels.kt`, `MeshFileTransferEngine.kt`, `MeshFileTransferManager.kt`, `MeshFileTransferTest.kt` | ✅ Completed |
| **ICA-1902** | Subsystem Hardware Bridge Wiring & Reactive State Management | `IntercomHardwareBridge.kt`, `IntercomBridge.kt`, `HomeViewModel.kt`, `HomeViewModelTest.kt` | ✅ Completed |
| **ICA-1903** | Compose Multiplatform Trail File Manager & Over-The-Air Beam Cockpit Sheet | `TrailFileManagerSheet.kt`, `OfflineTrailCockpitSheet.kt`, `HomeScreen.kt` | ✅ Completed |
| **ICA-1904** | Native iOS SwiftUI Trail File Manager & Over-The-Air Beam Sheet | `TrailFileManagerSheet.swift`, `OfflineTrailCockpitSheet.swift`, `IntercomViewModel.swift` | ✅ Completed |

---

### 🚀 Sprint 20: Tactical Convoy Geofencing, Autonomous Power Governor & Multi-Node Stress Benchmark Suite (Phase 18 - v2.7 Roadmap)
- **Sprint Goal**: Implement real-time convoy tether geofencing and waypoint proximity radar alarms, smart battery duty-cycle governing with motion-aware rest-stop sleep modes, multiplatform HUD presentation, and an automated 16-node virtual convoy stress harness with Archify showcase updates.
- **Sprint Branch**: `sprint/sprint-20-geofence-power-stress`
- **Status**: ✅ 100% Complete (All 4 Stories Passed & Released under v2.7.0)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-2001** | Pure KMP Convoy Geofencing, Hazard Waypoint Radar Alarms & Convoy Split Engine | `ConvoyGeofenceModels.kt`, `ConvoyGeofenceEngine.kt`, `ConvoyGeofenceEngineTest.kt` | ✅ Completed |
| **ICA-2002** | Smart Battery Duty-Cycle Governor & Motion-Aware Power Mode | `PowerStateModels.kt`, `BatteryGovernor.kt`, `BatteryGovernorTest.kt` | ✅ Completed |
| **ICA-2003** | Subsystem Hardware Bridge Wiring & Multiplatform Presentation HUD Integration | `IntercomHardwareBridge.kt`, `IntercomBridge.kt`, `HomeScreen.kt`, `ContentView.swift` | ✅ Completed |
| **ICA-2004** | 16-Node Concurrent Convoy Stress Simulation Harness & Archify Showcase Update | `ConvoyStressSimulationHarness.kt`, `intercom-alpha.architecture.json` | ✅ Completed |

---

### 🚀 Sprint 21: Autonomous Crash Impact Detection, eCall SOS & Offline Voice Command HUD (Phase 19 - v2.8 Roadmap)
- **Sprint Goal**: Implement pure KMP multi-axis IMU crash & fall-over impact detection with 30s abortable countdown, offline keyword-spotting voice commands (eCall SOS, next/prev channel, mute, battery), subsystem platform hardware bridge wiring, and emergency HUD integration across Compose Multiplatform and 100% Native iOS SwiftUI.
- **Sprint Branch**: `sprint/sprint-21-crash-detection-voice-commands`
- **Status**: ✅ 100% Complete (All 4 Stories Passed & Released under v2.8.0)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-2101** | Pure KMP Crash Detection, Fall-Over IMU State Machine & Countdown Arbiter | `SensorModels.kt`, `CrashDetector.kt`, `CrashDetectorTest.kt` | ✅ Completed |
| **ICA-2102** | Pure KMP Offline Voice Command & Keyword Spotting DSP Engine | `VoiceCommandModels.kt`, `VoiceCommandRecognizer.kt`, `VoiceCommandRecognizerTest.kt` | ✅ Completed |
| **ICA-2103** | Subsystem Hardware Bridge Wiring & Multiplatform Emergency HUD Presentation | `IntercomHardwareBridge.kt`, `IntercomBridge.kt`, `HomeScreen.kt`, `ContentView.swift` | ✅ Completed |
| **ICA-2104** | Crash Impact & Voice Command Multiplatform Simulation Harness & Benchmark Suite | `CrashAndVoiceSimulationHarness.kt`, `intercom-alpha.architecture.json` | ✅ Completed |

---

### 🚀 Sprint 22: Dynamic Mesh Directed Routing, AODV-Lite Route Optimization & RF Collision Reduction (Phase 20 - v2.9 Roadmap)
- **Sprint Goal**: Implement pure KMP AODV-Lite on-demand directed routing, dynamic link quality cost metrics (ETX), selective unicast/multicast forwarding, route error self-healing, multiplatform routing topology HUD presentation, and a 24-node RF contention reduction simulation harness.
- **Sprint Branch**: `sprint/sprint-22-mesh-directed-routing`
- **Target Release**: `v2.9.0`
- **Status**: ✅ 100% Complete (All 4 Stories Passed & Verified)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-2201** | Pure KMP AODV-Lite Directed Routing Engine & Route Table Arbiter | `RoutingModels.kt`, `RoutingTable.kt`, `AodvRoutingEngine.kt`, `AodvRoutingEngineTest.kt` | ✅ Completed |
| **ICA-2202** | Selective Unicast/Multicast Forwarding & RF Airtime Contention Mitigator | `LinkMetricCalculator.kt`, `MeshTransport.kt`, `DirectedForwardingTest.kt` | ✅ Completed |
| **ICA-2203** | Subsystem Hardware Bridge Wiring & Multiplatform Mesh Topology HUD Presentation | `IntercomHardwareBridge.kt`, `IntercomBridge.kt`, `HomeScreen.kt`, `ContentView.swift` | ✅ Completed |
| **ICA-2204** | 24-Node Large-Scale Directed Mesh RF Contention Benchmark & Archify Showcase Update | `DirectedMeshSimulationHarness.kt`, `intercom-alpha.architecture.json` | ✅ Completed |

---

### ✅ Sprint 23: Multi-Group Inter-Convoy Gateway & Cross-Mesh Federation (Phase 21 - v2.10 Roadmap)
- **Sprint Goal**: Implement pure KMP inter-group gateway contracts, cross-mesh audio bridging, sub-group isolation, anti-feedback loop guard, multiplatform federation HUD presentation, and a dual-convoy 32-node simulation benchmark.
- **Sprint Branch**: `sprint/sprint-23-multi-group-gateway`
- **Target Release**: `v2.10.0`
- **Status**: ✅ 100% Complete (All 4 Stories Passed & Verified)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-2301** | Pure KMP Multi-Group Gateway Engine & Cross-Mesh Federation Protocol | `FederationModels.kt`, `InterGroupGateway.kt`, `CrossMeshRelayEngine.kt`, `InterGroupGatewayTest.kt` | ✅ Completed |
| **ICA-2302** | Selective Inter-Group Audio Demuxing, Sub-Group Isolation & Anti-Feedback Loop Guard | `FederationLoopDetector.kt`, `CrossMeshForwardingArbiter.kt`, `CrossMeshForwardingTest.kt` | ✅ Completed |
| **ICA-2303** | Subsystem Hardware Bridge Wiring & Multiplatform Federation HUD Presentation | `IntercomHardwareBridge.kt`, `IntercomBridge.kt`, `HomeScreen.kt`, `ContentView.swift` | ✅ Completed |
| **ICA-2304** | Dual-Convoy 32-Node Cross-Mesh Simulation Harness & Archify Showcase Update | `MultiGroupFederationSimulationHarness.kt`, `intercom-alpha.architecture.json` | ✅ Completed |

---

### ✅ Sprint 24: Mesh Airtime QoS Traffic Shaping & Airtime Contention Mitigator (Phase 22 - v2.11 Roadmap)
- **Sprint Goal**: Implement pure KMP 4-tier strict priority voice QoS traffic shaping, preemption arbiter, MeshTransport & file airtime duty-cycle throttling, multiplatform HUD presentation, and a 32-node congested mesh simulation harness.
- **Sprint Branch**: `sprint/sprint-24-adaptive-bitrate-qos`
- **Target Release**: `v2.11.0`
- **Status**: ✅ 100% Complete (All 4 Stories Passed & Released under `v2.11.0`)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-2401** | Pure KMP Mesh Priority QoS Traffic Scheduler & Preemption Arbiter | `QoSModels.kt`, `MeshQoSQueue.kt`, `MeshQoSScheduler.kt`, `MeshQoSSchedulerTest.kt` | ✅ Completed |
| **ICA-2402** | MeshTransport Wire Integration & Bulk File Airtime Throttling | `MeshTransport.kt`, `MeshFileTransferManager.kt`, `MeshTransportQoSTest.kt` | ✅ Completed |
| **ICA-2403** | Subsystem Hardware Bridge Wiring & Multiplatform Tactical QoS HUD Presentation | `IntercomHardwareBridge.kt`, `AndroidPlatformBridge.kt`, `IntercomBridge.kt`, `HomeScreen.kt`, `ContentView.swift` | ✅ Completed |
| **ICA-2404** | 32-Node Congested RF Mesh QoS Simulation Harness & Archify Showcase Update | `CongestedMeshQoSSimulationHarness.kt`, `intercom-alpha.architecture.json` | ✅ Completed |

---

### ✅ Sprint 25: Automated Multi-Path Mesh Failover & Hybrid Cellular/SFU Relay Handover (Phase 23 - v2.12 Roadmap)
- **Sprint Goal**: Implement pure KMP multi-path link quality arbiter, automated BLE-to-Cellular failover state machine, packet sequence stream hedging to ensure zero audio frame drops during RF disconnects, multiplatform avionics HUD presentation, and a highway split simulation harness.
- **Sprint Branch**: `sprint/sprint-25-multipath-mesh-failover`
- **Target Release**: `v2.12.0`
- **Status**: ✅ 100% Complete (All 4 Stories Passed & Released under `v2.12.0`)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-2501** | Pure KMP Multi-Path Link Quality Arbiter & Automated Failover State Machine | `FailoverModels.kt`, `MultiPathFailoverArbiter.kt`, `MultiPathFailoverArbiterTest.kt` | ✅ Completed |
| **ICA-2502** | Transport Handover Wire Integration & Seamless Voice Stream Hedging | `CloudRelayBridge.kt`, `HybridTransportRouter.kt`, `HybridTransportRouterTest.kt` | ✅ Completed |
| **ICA-2503** | Subsystem Hardware Bridge Wiring & Multiplatform Tactical Transport Handover HUD | `IntercomHardwareBridge.kt`, `AndroidPlatformBridge.kt`, `IntercomBridge.kt`, `HomeScreen.kt`, `ContentView.swift` | ✅ Completed |
| **ICA-2504** | Highway Convoy Out-of-Range Handover Simulation Harness & Archify Showcase Update | `MultiPathHandoverSimulationHarness.kt`, `intercom-alpha.architecture.json` | ✅ Completed |

---

### ⚡ Sprint 26: Long-Haul Fleet Hardening & Low-Bandwidth Visual Telemetry (Phase 24 - v2.13 Roadmap)
- **Sprint Goal**: Implement ultra-low-framerate tactical snapshot mesh transport (0.5 fps thumbnail broadcast), concurrent multi-radio arbiter (BLE + Wi-Fi Direct), fleet-wide over-the-air group rekeying protocol, and an 8-hour continuous expedition battery & memory soak test.
- **Sprint Branch**: `sprint/sprint-26-fleet-hardening`
- **Target Release**: `v2.13.0`
- **Status**: ✅ 100% Complete (All 4 Stories Passed & Released under `v2.13.0`)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-2601** | Ultra-low-framerate tactical snapshot mesh transport (0.5 fps) | `SnapshotModels.kt`, `TacticalSnapshotProtocol.kt`, `SnapshotTransferManager.kt` | ✅ Completed |
| **ICA-2602** | Dynamic multi-path routing with BLE + Wi-Fi Direct concurrent multi-radio | `RadioModels.kt`, `MultiRadioArbiter.kt` | ✅ Completed |
| **ICA-2603** | Fleet-wide over-the-air cryptographic group rekeying protocol | `RekeyModels.kt`, `GroupRekeyProtocol.kt` | ✅ Completed |
| **ICA-2604** | 8-hour continuous expedition soak benchmark & Archify architecture showcase update | `ExpeditionSoakSimulationHarness.kt`, `intercom-alpha.architecture.json` | ✅ Completed |

#### 📋 Tasks Plugin Checklist (Sprint 26)
- [x] #task [ICA-2601] Implement TacticalSnapshotProtocol chunked thumbnail broadcaster ➕ 2026-10-10 ⏳ 2026-10-12 📅 2026-10-16 ⏫ ✅ 2026-10-10
- [x] #task [ICA-2602] Implement MultiRadioArbiter for concurrent BLE + Wi-Fi Direct ➕ 2026-10-10 ⏳ 2026-10-14 📅 2026-10-18 ⏫ ✅ 2026-10-10
- [x] #task [ICA-2603] Implement GroupRekeyProtocol with forward-secrecy verification ➕ 2026-10-10 ⏳ 2026-10-15 📅 2026-10-19 🔼 ✅ 2026-10-10
- [x] #task [ICA-2604] Execute 8-hour expedition battery & memory soak test benchmark ➕ 2026-10-10 ⏳ 2026-10-16 📅 2026-10-20 🔼 ✅ 2026-10-10

---

### ✅ Sprint 27: Alpine Extreme Reach & Satellite Gateway Integration (Phase 25 - v2.14 Roadmap)
- **Sprint Goal**: Implement satellite emergency communicator bridge (Garmin inReach / Iridium SBD), long-range BLE Coded PHY (S=8) alpine propagation arbiter, wearable smartwatch quick-PTT companion protocol, and 24-node alpine mountain simulation benchmark.
- **Sprint Branch**: `sprint/sprint-27-satellite-alpine-gateway`
- **Target Release**: `v2.14.0`
- **Status**: ✅ 100% Complete (All 4 Stories Passed & Released under `v2.14.0`)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-2701** | Pure KMP Satellite Communicator Emergency Gateway Bridge | `SatelliteModels.kt`, `SatelliteGatewayBridge.kt`, `SatelliteGatewayBridgeTest.kt` | ✅ Completed |
| **ICA-2702** | Long-Range BLE Coded PHY (S=8) & Alpine Terrain Attenuation Model | `AlpineRfModels.kt`, `AdaptivePhyArbiter.kt`, `AdaptivePhyArbiterTest.kt` | ✅ Completed |
| **ICA-2703** | Wearable Quick-PTT Companion Protocol & Biometric Vitals Beacon | `WearableCompanionModels.kt`, `WearableCompanionProtocol.kt`, `WearableCompanionProtocolTest.kt` | ✅ Completed |
| **ICA-2704** | Alpine Expedition Multi-Target Simulation Harness & Archify Showcase Update | `AlpineExpeditionSimulationHarness.kt`, `intercom-alpha.architecture.json` | ✅ Completed |

#### 📋 Tasks Plugin Checklist (Sprint 27)
- [x] #task [ICA-2701] Implement SatelliteGatewayBridge for Iridium SBD & Garmin inReach emergency beacons ➕ 2026-10-10 ⏳ 2026-10-12 📅 2026-10-16 ⏫ ✅ 2026-10-10
- [x] #task [ICA-2702] Implement AdaptivePhyArbiter for BLE Coded PHY (S=8) in alpine canyons ➕ 2026-10-10 ⏳ 2026-10-14 📅 2026-10-18 ⏫ ✅ 2026-10-10
- [x] #task [ICA-2703] Implement WearableCompanionProtocol for smartwatch quick-PTT & heart rate ➕ 2026-10-10 ⏳ 2026-10-15 📅 2026-10-19 🔼 ✅ 2026-10-10
- [x] #task [ICA-2704] Execute 24-node Alpine Expedition simulation harness & Archify showcase update ➕ 2026-10-10 ⏳ 2026-10-16 📅 2026-10-20 🔼 ✅ 2026-10-10
 
---
 
### ✅ Sprint 28: Edge Acoustic Intelligence & Mesh Airtime Congestion Governance (Phase 26 - v2.15 Roadmap)
- **Sprint Goal**: Implement pure KMP spectral subtraction speech enhancer for motorcycle wind noise, offline hands-free wake word engine ("Hey Alpha" / "Emergency SOS"), channel busy ratio (CBR) airtime congestion governor with adaptive FEC, and 32-node alpine blizzard simulation benchmark.
- **Sprint Branch**: `sprint/sprint-28-edge-acoustic-congestion`
- **Target Release**: `v2.15.0`
- **Status**: ✅ Completed (Released under `v2.15.0`)
 
| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-2801** | Pure KMP Spectral Subtraction Speech Enhancer & Wind Noise Suppressor | `SpectralNoiseModels.kt`, `SpectralNoiseSuppressor.kt`, `SpectralNoiseSuppressorTest.kt` | ✅ Completed |
| **ICA-2802** | Hands-Free Acoustic Wake Word Engine & Continuous VOX Arbiter | `WakeWordModels.kt`, `AcousticWakeWordEngine.kt`, `AcousticWakeWordEngineTest.kt` | ✅ Completed |
| **ICA-2803** | Real-Time Channel Airtime Congestion Governor & Adaptive FEC Shaper | `CongestionModels.kt`, `AirtimeCongestionGovernor.kt`, `AirtimeCongestionGovernorTest.kt` | ✅ Completed |
| **ICA-2804** | 32-Node Alpine Blizzard Acoustic & RF Contention Benchmark & Archify Showcase Update | `AlpineBlizzardSimulationHarness.kt`, `intercom-alpha.architecture.json` | ✅ Completed |
 
#### 📋 Tasks Plugin Checklist (Sprint 28)
- [x] #task [ICA-2801] Implement SpectralNoiseSuppressor for real-time wind noise reduction ➕ 2026-10-10 ⏳ 2026-10-12 📅 2026-10-16 ⏫ ✅ 2026-10-10
- [x] #task [ICA-2802] Implement AcousticWakeWordEngine for hands-free 'Hey Alpha' and 'Emergency SOS' ➕ 2026-10-10 ⏳ 2026-10-14 📅 2026-10-18 ⏫ ✅ 2026-10-10
- [x] #task [ICA-2803] Implement AirtimeCongestionGovernor for CBR channel pacing & adaptive FEC ➕ 2026-10-10 ⏳ 2026-10-15 📅 2026-10-19 🔼 ✅ 2026-10-10
- [x] #task [ICA-2804] Execute 32-node Alpine Blizzard simulation harness & Archify showcase update ➕ 2026-10-10 ⏳ 2026-10-16 📅 2026-10-20 🔼 ✅ 2026-10-10

---

### ✅ Sprint 29: Unified Hardware Bridge Integration & System Certification (Phase 27 - v2.16 Roadmap)
- **Sprint Goal**: Wire Tactical Snapshots, Multi-Radio Arbiter, Group Rekeying, Satellite Gateway, Coded PHY, Wearables, Spectral DSP, Wake Word VOX, and Airtime Governor into unified hardware bridge interfaces across Android and iOS (`IntercomHardwareBridge.kt` and `IntercomBridge.kt`), and execute 32-node end-to-end multiplatform certification.
- **Sprint Branch**: `sprint/sprint-29-unified-bridge-certification`
- **Target Release**: `v2.16.0`
- **Status**: ✅ Completed (Released under `v2.16.0`)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-2901** | Tactical Snapshots, Multi-Radio & Rekeying Hardware Bridge Wiring | `IntercomHardwareBridge.kt`, `IntercomBridge.kt`, `IntercomHardwareBridgeWiringTest.kt` | ✅ Completed |
| **ICA-2902** | Satellite Lifeline, Alpine Coded PHY & Wearable Companion Bridge Wiring | `IntercomHardwareBridge.kt`, `IntercomBridge.kt`, `IntercomSatellitePhyBridgeTest.kt` | ✅ Completed |
| **ICA-2903** | Spectral Noise DSP, Wake Word VOX & Airtime Congestion Governor Bridge Wiring | `IntercomHardwareBridge.kt`, `IntercomBridge.kt`, `IntercomAcousticQoSBridgeTest.kt` | ✅ Completed |
| **ICA-2904** | 32-Node Unified End-to-End System Simulation, Final Architecture Showcase & Certification | `UnifiedSystemCertificationHarness.kt`, `intercom-alpha-architecture.html` | ✅ Completed |

#### 📋 Tasks Plugin Checklist (Sprint 29)
- [x] #task [ICA-2901] Tactical Snapshots, Multi-Radio & Rekeying Hardware Bridge Wiring ➕ 2026-10-10 ⏳ 2026-10-12 📅 2026-10-16 ⏫ ✅ 2026-10-10
- [x] #task [ICA-2902] Satellite Lifeline, Alpine Coded PHY & Wearable Companion Bridge Wiring ➕ 2026-10-10 ⏳ 2026-10-14 📅 2026-10-18 ⏫ ✅ 2026-10-10
- [x] #task [ICA-2903] Spectral Noise DSP, Wake Word VOX & Airtime Congestion Governor Bridge Wiring ➕ 2026-10-10 ⏳ 2026-10-15 📅 2026-10-19 🔼 ✅ 2026-10-10
- [x] #task [ICA-2904] 32-Node Unified End-to-End System Simulation, Final Architecture Showcase & Certification ➕ 2026-10-10 ⏳ 2026-10-16 📅 2026-10-20 🔼 ✅ 2026-10-10

---

### 🎨 Sprint 30: Dedicated UI/UX Design System Foundations (Phase 28 - Part 1)
- **Sprint Goal**: Implement core design tokens, color roles, elevation shadows, neobrutalist borders, claymorphic touch modifiers, and liquid glass materials across Android Jetpack Compose (Material 3: Expressive) and Native iOS SwiftUI (iOS 18+ HIG), adhering strictly to ADR-014 and Rule 1.
- **Sprint Branch**: `sprint/sprint-30-design-system-foundations`
- **Target Release**: `v3.0.0-alpha1`
- **Status**: ✅ 100% Complete (All 4 Stories Passed)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-3001** | Compose Material 3 Expressive Tokens, Neobrutalist & Claymorphic Modifier Engine | `IntercomDesignTokens.kt`, `TactileModifiers.kt`, `IntercomTheme.kt` | ✅ Completed |
| **ICA-3002** | iOS SwiftUI Tactical Design System, Liquid Glass & Tactile Haptic Modifiers | `TactileDesignSystem.swift`, `LiquidGlassView.swift`, `TactileHaptics.swift` | ✅ Completed |
| **ICA-3003** | Expressive Tactical Audio & Cockpit Widget Components (PTT, VU Meter, SOS Switch) | `ExpressivePttActuator.kt`, `TactilePttButton.swift`, `ExpressiveVuMeter.kt` | ✅ Completed |
| **ICA-3004** | Design System Verification Harness & Golden Cross-Platform Render Specification | `DesignSystemTokenTest.kt`, `DesignSystemSnapshotTest.kt` | ✅ Completed |

#### 📋 Tasks Plugin Checklist (Sprint 30)
- [x] #task [ICA-3001] Implement Compose Material 3 Expressive Tokens, Neobrutalist & Claymorphic Modifier Engine ➕ 2026-10-16 ⏳ 2026-10-18 📅 2026-10-22 ⏫ ✅ 2026-10-16
- [x] #task [ICA-3002] Implement iOS SwiftUI Tactical Design System, Liquid Glass & Tactile Haptic Modifiers ➕ 2026-10-16 ⏳ 2026-10-19 📅 2026-10-23 ⏫ ✅ 2026-10-17
- [x] #task [ICA-3003] Implement Expressive Tactical Audio & Cockpit Widget Components (PTT, VU Meter, SOS Switch) ➕ 2026-10-16 ⏳ 2026-10-20 📅 2026-10-24 🔼 ✅ 2026-10-17
- [x] #task [ICA-3004] Execute Design System Verification Harness & Golden Cross-Platform Render Specification ➕ 2026-10-16 ⏳ 2026-10-21 📅 2026-10-25 🔼 ✅ 2026-10-17

---

### 🎛️ Sprint 31: Zero-Scroll Tactical Cockpit Redesign (Phase 28 - Part 2)
- **Sprint Goal**: Redesign Android (`HomeScreen.kt`) and iOS (`ContentView.swift`) main screens into a unified, zero-scroll tactical cockpit adhering to ADR-014 and Rule 1. Integrates oversized Expressive PTT actuator, real-time VU meter with clipping warnings, rotary channel switchers with haptic detents, and mil-spec guarded SOS flip-cover actuators.
- **Sprint Branch**: `sprint/sprint-31-tactical-cockpit-redesign`
- **Target Release**: `v3.0.0-alpha1`
- **Status**: ✅ 100% Complete (All 4 Stories Passed)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-3101** | Android Jetpack Compose Zero-Scroll Tactical Cockpit Integration | `HomeScreen.kt`, `TactileAvionicsHeader.kt`, `TactileChannelDial.kt` | ✅ Completed |
| **ICA-3102** | iOS 100% Native SwiftUI Tactical Cockpit Integration | `ContentView.swift`, `TactileAvionicsHeader.swift`, `TactileChannelDial.swift` | ✅ Completed |
| **ICA-3103** | Glove-Friendly Spatial Radar & Telemetry Liquid Glass Card Integration | `TactileRadarCard.kt`, `TactileRadarCard.swift` | ✅ Completed |
| **ICA-3104** | Tactical Cockpit E2E Interaction Simulation & Archify Showcase Update | `TacticalCockpitInteractionTest.kt`, `intercom-alpha.architecture.json` | ✅ Completed |

#### 📋 Tasks Plugin Checklist (Sprint 31)
- [x] #task [ICA-3101] Implement Android Jetpack Compose Zero-Scroll Tactical Cockpit Integration ➕ 2026-10-17 ⏳ 2026-10-19 📅 2026-10-23 ⏫ ✅ 2026-10-17
- [x] #task [ICA-3102] Implement iOS 100% Native SwiftUI Tactical Cockpit Integration ➕ 2026-10-17 ⏳ 2026-10-20 📅 2026-10-24 ⏫ ✅ 2026-10-17
- [x] #task [ICA-3103] Implement Glove-Friendly Spatial Radar & Telemetry Liquid Glass Card Integration ➕ 2026-10-17 ⏳ 2026-10-21 📅 2026-10-25 🔼 ✅ 2026-10-17
- [x] #task [ICA-3104] Execute Tactical Cockpit E2E Interaction Simulation & Archify Showcase Update ➕ 2026-10-17 ⏳ 2026-10-22 📅 2026-10-26 🔼 ✅ 2026-10-17

---

### 🗺️ Sprint 32: Tactile Sub-Screens & Secondary Overlays Polish (Phase 28 - Part 3)
- **Sprint Goal**: Modernize secondary overlay sheets and full-screen diagnostic destinations across Android Jetpack Compose and iOS 100% Native SwiftUI adhering strictly to ADR-014 and Rule 1. Applies `IntercomDesignTokens` (claymorphic surfaces, neobrutalist borders, 84dp glove targets), monospace tabular telemetry, and non-blocking sheet navigation.
- **Sprint Branch**: `sprint/sprint-32-tactile-subscreens`
- **Target Release**: `v3.0.0-alpha1`
- **Status**: ✅ 100% Complete (All 4 Stories Passed)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-3201** | Android Tactile Offline Trail Sheet & File Manager Modernization | `OfflineTrailCockpitSheet.kt`, `TrailFileManagerSheet.kt` | ✅ Completed |
| **ICA-3202** | Android Tactile Message Dialog & Subsystem Diagnostics Modernization | `MessageHistoryDialog.kt`, `TactileDiagnosticsSheet.kt`, `MainCockpitScreen.kt` | ✅ Completed |
| **ICA-3203** | iOS Native SwiftUI Trail Cockpit, File Manager & Diagnostics Modernization | `ContentView.swift` | ✅ Completed |
| **ICA-3204** | Multiplatform Sub-Screen E2E Interaction Test & Certification | `SubScreenInteractionTest.kt` | ✅ Completed |

#### 📋 Tasks Plugin Checklist (Sprint 32)
- [x] #task [ICA-3201] Modernize Android Tactile Offline Trail Sheet & File Manager ➕ 2026-10-18 ⏳ 2026-10-20 📅 2026-10-24 ⏫ ✅ 2026-10-18
- [x] #task [ICA-3202] Modernize Android Tactile Message Dialog & Subsystem Diagnostics ➕ 2026-10-18 ⏳ 2026-10-21 📅 2026-10-25 ⏫ ✅ 2026-10-18
- [x] #task [ICA-3203] Modernize iOS Native SwiftUI Trail Cockpit, File Manager & Diagnostics ➕ 2026-10-18 ⏳ 2026-10-22 📅 2026-10-26 ⏫ ✅ 2026-10-18
- [x] #task [ICA-3204] Execute Multiplatform Sub-Screen E2E Interaction Test & Certification ➕ 2026-10-18 ⏳ 2026-10-23 📅 2026-10-27 🔼 ✅ 2026-10-18

---

### 📶 Sprint 33: Dynamic Adaptive Mesh Bitrate Tuning (Phase 29 - Part 1)
- **Sprint Goal**: Implement closed-loop dynamic audio codec bitrate adaptation and frame sizing over the BLE mesh. Fuses EWMA RSSI link margin, moving PDR, jitter variance, and ETX into composite $Q_{link}$ scores, automatically scaling audio encoding between 16 kbps and 48 kbps with asymmetric hysteresis to eliminate audio dropouts during fading.
- **Sprint Branch**: `sprint/sprint-33-adaptive-mesh-bitrate`
- **Target Release**: `v3.0.0-alpha2`
- **Status**: ✅ 100% Complete (All 4 Stories Passed)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-3301** | Real-Time Mesh Link Quality Scoring & EWMA Loss Estimator | `MeshLinkQualityScorer.kt`, `MeshLinkQualityScorerTest.kt` | ✅ Completed |
| **ICA-3302** | Dynamic Adaptive Bitrate & Frame Sizing Controller | `AdaptiveBitrateEngine.kt`, `AdaptiveBitrateEngineTest.kt` | ✅ Completed |
| **ICA-3303** | Audio Pipeline Dynamic Frame Multiplexing & Cockpit Telemetry Wiring | `IntercomHardwareBridge.kt`, `IntercomBridge.kt`, `TactileDiagnosticsSheet.kt` | ✅ Completed |
| **ICA-3304** | Multi-Node Dynamic Bitrate Simulation Test & Multiplatform Certification | `AdaptiveBitrateSimulationTest.kt` | ✅ Completed |

#### 📋 Tasks Plugin Checklist (Sprint 33)
- [x] #task [ICA-3301] Implement Real-Time Mesh Link Quality Scoring & EWMA Loss Estimator ➕ 2026-10-18 ⏳ 2026-10-20 📅 2026-10-24 ⏫ ✅ 2026-10-18
- [x] #task [ICA-3302] Implement Dynamic Adaptive Bitrate & Frame Sizing Controller ➕ 2026-10-18 ⏳ 2026-10-21 📅 2026-10-25 ⏫ ✅ 2026-10-18
- [x] #task [ICA-3303] Wire Audio Pipeline Dynamic Frame Multiplexing & Cockpit Telemetry ➕ 2026-10-18 ⏳ 2026-10-22 📅 2026-10-26 ⏫ ✅ 2026-10-18
- [x] #task [ICA-3304] Execute Multi-Node Dynamic Bitrate Simulation Test & Multiplatform Certification ➕ 2026-10-18 ⏳ 2026-10-23 📅 2026-10-27 🔼 ✅ 2026-10-18

---

### 🛡️ Sprint 34: Forward Error Correction (FEC) & Packet Loss Concealment (PLC) Hardening (Phase 29 - Part 2)
- **Sprint Goal**: Implement robust forward error correction (FEC) and pitch-synchronous waveform packet loss concealment (PLC) over the BLE mesh. Reconstruct lost voice frames up to 25% packet drop without retransmissions, smoothly concealing unrecoverable dropouts with natural acoustic decay.
- **Sprint Branch**: `sprint/sprint-34-fec-and-plc`
- **Target Release**: `v3.0.0-alpha2`
- **Status**: ✅ 100% Complete (All 4 Stories Passed)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-3401** | Multiplatform Block Erasure FEC Encoder & Interleaved Block Decoder | `MeshPacketFecEngine.kt`, `MeshPacketFecEngineTest.kt` | ✅ Completed |
| **ICA-3402** | Predictive Packet Loss Concealment (PLC) & Waveform Interpolator | `AudioLossConcealment.kt`, `AudioLossConcealmentTest.kt` | ✅ Completed |
| **ICA-3403** | Closed-Loop FEC Redundancy Governor & Cockpit Telemetry Wiring | `AudioEngine.kt`, `IntercomHardwareBridge.kt`, `IntercomBridge.kt` | ✅ Completed |
| **ICA-3404** | Multi-Node 25% Burst Loss Simulation Test & Multiplatform Certification | `FecLossConcealmentSimulationTest.kt` | ✅ Completed |

#### 📋 Tasks Plugin Checklist (Sprint 34)
- [x] #task [ICA-3401] Implement Multiplatform Block Erasure FEC Encoder & Interleaved Block Decoder ➕ 2026-10-18 ⏳ 2026-10-20 📅 2026-10-24 ⏫ ✅ 2026-10-18
- [x] #task [ICA-3402] Implement Predictive Packet Loss Concealment (PLC) & Waveform Interpolator ➕ 2026-10-18 ⏳ 2026-10-21 📅 2026-10-25 ⏫ ✅ 2026-10-18
- [x] #task [ICA-3403] Wire Closed-Loop FEC Redundancy Governor & Cockpit Telemetry ➕ 2026-10-18 ⏳ 2026-10-22 📅 2026-10-26 ⏫ ✅ 2026-10-18
- [x] #task [ICA-3404] Execute Multi-Node 25% Burst Loss Simulation Test & Multiplatform Certification ➕ 2026-10-18 ⏳ 2026-10-23 📅 2026-10-27 🔼 ✅ 2026-10-18
---

### ✅ Sprint 35: Ultra-Low Latency BLE MTU Frame Packing & Interleaving (Phase 30)
- **Sprint Goal**: Pack multiple compressed voice frames into single optimal 247-byte negotiated BLE ATT MTU payloads with matrix block interleaving, reducing RF packet overhead by > 50%, cutting collisions, and lowering mouth-to-ear latency.
- **Sprint Branch**: `sprint/sprint-35-ble-mtu-packing`
- **Target Release**: `v3.0.0-alpha3`
- **Status**: ✅ 100% Complete (All 4 Stories Passed)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-3501** | Multiplatform BLE ATT MTU Frame Aggregator & Sub-Packet Framer | `BleMtuAggregator.kt`, `BleMtuAggregatorTest.kt` | ✅ Completed |
| **ICA-3502** | Time-Diversity Block Interleaver & Playout De-jittering Unpacker | `BleFrameInterleaver.kt`, `BleFrameInterleaverTest.kt` | ✅ Completed |
| **ICA-3503** | Transport Dynamic MTU Negotiation & Cockpit Packing Telemetry Wiring | `AudioEngine.kt`, `IntercomHardwareBridge.kt`, `IntercomBridge.kt` | ✅ Completed |
| **ICA-3504** | 16-Node High-Throughput MTU Packing & Burst Loss Simulation Test | `BleMtuPackingSimulationTest.kt` | ✅ Completed |

#### 📋 Tasks Plugin Checklist (Sprint 35)
- [x] #task [ICA-3501] Implement Multiplatform BLE ATT MTU Frame Aggregator & Sub-Packet Framer ➕ 2026-10-18 ⏳ 2026-10-20 📅 2026-10-24 🔺 ✅ 2026-10-18
- [x] #task [ICA-3502] Implement Time-Diversity Block Interleaver & Playout De-jittering Unpacker ➕ 2026-10-18 ⏳ 2026-10-21 📅 2026-10-25 🔺 ✅ 2026-10-18
- [x] #task [ICA-3503] Wire Transport Dynamic MTU Negotiation & Cockpit Packing Telemetry ➕ 2026-10-18 ⏳ 2026-10-22 📅 2026-10-26 🔺 ✅ 2026-10-18
- [x] #task [ICA-3504] Execute 16-Node High-Throughput MTU Packing & Burst Loss Simulation Test ➕ 2026-10-18 ⏳ 2026-10-23 📅 2026-10-27 ⏫ ✅ 2026-10-18

---

### ✅ Sprint 36: Offline Vector Topographic & Trail Elevation Profile HUD (Phase 31)
- **Sprint Goal**: Implement high-definition offline vector topographic map rendering and real-time convoy elevation profile HUD. Visualizes route altitude incline, vertical separation between lead and tail riders, steep grade warnings (>12%), and offline tile caching with zero cellular dependency.
- **Sprint Branch**: `sprint/sprint-36-vector-trail-elevation-hud`
- **Target Release**: `v3.0.0-beta1`
- **Status**: ✅ 100% Complete (All 4 Stories Passed)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-3601** | Pure KMP Offline Vector Map Tile Parser & Spatial Bounding Box Cacher | `OfflineVectorTileEngine.kt`, `OfflineVectorTileEngineTest.kt` | ✅ Completed |
| **ICA-3602** | Real-Time Convoy Elevation Profile & Altitude Incline Math Engine | `ConvoyElevationEngine.kt`, `ConvoyElevationEngineTest.kt` | ✅ Completed |
| **ICA-3603** | Tactile Trail Elevation HUD Overlay & Cockpit Sheet Presentation | `TrailElevationHudOverlay.kt`, `MainCockpitScreen.kt`, `IntercomBridge.kt` | ✅ Completed |
| **ICA-3604** | Alpine Mountain Pass Multi-Node Elevation & Trail Sync Simulation Test | `AlpineElevationSimulationTest.kt` | ✅ Completed |

#### 📋 Tasks Plugin Checklist (Sprint 36)
- [x] #task [ICA-3601] Implement Pure KMP Offline Vector Map Tile Parser & Spatial Bounding Box Cacher ➕ 2026-10-18 ⏳ 2026-10-20 📅 2026-10-24 🔺 ✅ 2026-10-18
- [x] #task [ICA-3602] Implement Real-Time Convoy Elevation Profile & Altitude Incline Math Engine ➕ 2026-10-18 ⏳ 2026-10-21 📅 2026-10-25 🔺 ✅ 2026-10-18
- [x] #task [ICA-3603] Present Tactile Trail Elevation HUD Overlay & Cockpit Sheet ➕ 2026-10-18 ⏳ 2026-10-22 📅 2026-10-26 🔺 ✅ 2026-10-18
- [x] #task [ICA-3604] Execute Alpine Mountain Pass Multi-Node Elevation Simulation Test ➕ 2026-10-18 ⏳ 2026-10-23 📅 2026-10-27 ⏫ ✅ 2026-10-18

---

### 🔮 Planned Forward Sprints

#### Sprint 37: Autonomous Fleet Mesh Congestion Control & Adaptive PHY (Phase 32)
- **Target Release:** `v3.0.0-beta2`
- **Stories:**
  - `ICA-3701`: Dynamic BLE Advertising Interval Throttler & Flooding Redundancy Governor.
  - `ICA-3702`: Adaptive Multi-PHY Switcher Engine (LE 1M, LE 2M, LE Coded S8).
  - `ICA-3703`: Hardware Bridge Multi-PHY Binding & Tactical Cockpit RF Pill Telemetry.
  - `ICA-3704`: 24-Node High-Density RF Congestion Simulation & Range Benchmark Test.

#### Sprint 38: Multi-Squad Basecamp Master Dispatch Console & Fleet Telemetry (Phase 33)
- **Target Release:** `v3.0.0-rc1` (Release Candidate 1)
- **Stories:**
  - `ICA-3801`: Standalone Desktop/Web Basecamp Console connecting via stationary BLE/USB repeater node.
  - `ICA-3802`: Multi-squad real-time tracking radar with fleet-wide battery health and telemetry aggregation.
  - `ICA-3803`: Centralized all-call emergency broadcast override and audio logging blackbox exporter.
  - `ICA-3804`: Field deployment simulation harness with 3 remote squads and 1 basecamp master node.

#### Sprint 39: End-to-End Field Soak Benchmark, Battery Profiling & v3.0.0 GA (Phase 34)
- **Target Release:** `v3.0.0` Production General Availability (GA)
- **Stories:**
  - `ICA-3901`: 12-hour continuous multiplatform field expedition soak test across mixed Android/iOS convoy.
  - `ICA-3902`: Full battery profiling audit ensuring < 7.5% consumption per hour under continuous background relay.
  - `ICA-3903`: Release signing, ProGuard / R8 minification, native binary symbol stripping, and Play Store / TestFlight bundle generation.
  - `ICA-3904`: Production release documentation sign-off, public API documentation, and v3.0.0 release tagging.

