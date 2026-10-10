# Master Tasks & Engineering Roadmap â€” Intercom-Alpha

> **Document Version:** 2.15  
> **Status:** Active / Tracked  
> **Current Sprint:** Sprint 28 Active (`sprint/sprint-28-edge-acoustic-congestion`) — v2.15.0 Roadmap
> **Obsidian Reference:** `01_Management/Roadmap.md` & `01_Management/Kanban.md`

---

## ðŸ† Sprint History & Completed Milestones

### âœ… Sprint 01: Core Networking & Signaling Protocol (Phase 1)
- **Goal**: Establish polymorphic serialization protocol, Ktor signaling server, and multiplatform client contracts.
- **Completed**:
  - `ICA-101`: Polymorphic binary/JSON signaling protocol in `:core` (`SignalingProtocol.kt`).
  - `ICA-102`: Ktor WebSocket Server in `:server` (`/ws/intercom`, `/api/status`).
  - `ICA-103`: Multiplatform `IntercomSignalingClient.kt` in `app/sharedLogic`.
  - `ICA-104`: Full unit tests for client/server connection and session lifecycle.

### âœ… Sprint 02: Android Platform Engine & Compose UI (Phase 2)
- **Goal**: Implement low-latency audio capture/playback, BLE GATT advertising/scanning, and Compose interface.
- **Completed**:
  - `ICA-201`: Android `AudioRecord` / `AudioTrack` PCM streaming with VOX thresholding.
  - `ICA-202`: Android BLE GATT Server (`BluetoothGattServer`) and Client (`BluetoothGatt`).
  - `ICA-203`: Bluetooth SCO headset routing and hardware button listener (`HeadsetManager.android.kt`).
  - `ICA-204`: Initial Jetpack Compose intercom cockpit interface with live VU metering.

### âœ… Sprint 03: iOS Native Audio, CoreBluetooth Mesh & Cross-Platform GATT (Phase 2)
- **Goal**: Implement iOS platform engine matching Android GATT UUIDs with 100% native SwiftUI.
- **Completed**:
  - `ICA-301`: iOS `AVAudioEngine` input tap and player node scheduling (`AudioEngine.ios.kt`).
  - `ICA-302`: CoreBluetooth GATT dual-role manager + `MultipeerConnectivity` high-speed bridge.
  - `ICA-303`: iOS Headset routing (`AVAudioSessionCategoryPlayAndRecord` VoiceChat mode).
  - `ICA-304`: 100% Native SwiftUI UI (`ContentView.swift`) adhering to "Do Not Share UI with iOS".
  - `ICA-305`: Cross-platform GATT serialization symmetry tests in `CrossPlatformGattMeshTest.kt`.

### âœ… Sprint 04: Swift â†” KMP Bridge & QR Code Onboarding (Phase 3)
- **Goal**: Establish seamless Swift interop and offline QR code group onboarding.
- **Completed**:
  - `ICA-401`: Thread-safe, non-blocking `IntercomBridge.kt` with cancellation handles in `iosMain`.
  - `ICA-402`: `AVFoundation` camera-based offline QR code scanner (`QrScannerView.swift`).
  - `ICA-403`: `INTERCOM:v1:` QR generator and parser in `GroupManagerImpl.kt`.
  - `ICA-404`: Obsidian Vault reorganization into standardized 8-pillar sequential structure.

### âœ… Sprint 05: Mesh Flooding Relay, Deduplication & Encryption (Phase 3)
- **Goal**: Multi-hop BLE mesh relay (TTL=3), sliding-window deduplication, and pure Kotlin ChaCha20-Poly1305 encryption.
- **Completed**:
  - `ICA-501`: Sliding-window `PacketDeduplicator.kt` (5,000ms TTL, 1,000 capacity, storm suppression).
  - `ICA-502`: Inner packet extraction and local audio delivery in Android & iOS transports.
  - `ICA-503`: Multi-hop TTL decrement ($3 \rightarrow 2 \rightarrow 1$) and neighbor forwarding.
  - `ICA-504`: Pure Kotlin RFC 8439 ChaCha20-Poly1305 AEAD cipher and SHA-256 key derivation (`PacketCipher.kt`).
  - `ICA-505`: Multi-hop simulation and relay unit tests in `CrossPlatformGattMeshTest.kt`.
  - `ICA-506`: Multiplatform smoke verification build (100% passing unit tests, clean Android debug APK & iOS simulator framework).

---

## ðŸš€ Active & Upcoming Sprints

### âœ… Sprint 06: Background Services & OS Power Management (Phase 4)
- **Goal**: Ensure continuous screen-off voice communication and relay for 4+ hours on Android and iOS.
- **Completed**:
  - `ICA-601`: Android Foreground Service with `connectedDevice|microphone` types and Bluetooth SCO audio routing lock (`IntercomForegroundService.kt`).
  - `ICA-602`: Android Partial WakeLock and Battery Optimization exemption request helper (`PowerManagerHelper.kt`).
  - `ICA-603`: iOS Background Audio and CoreBluetooth modes in `Info.plist` & `AVAudioSession` lifecycle in `iOSApp.swift`.
  - `ICA-604`: iOS background task assertions and keep-alive (`BackgroundKeepAlive.kt` & `IntercomBridge.kt`).
  - `ICA-605`: Screen-off continuity verification test suite with simulated burst traffic (`BackgroundContinuityTest.kt`).

---

### âœ… Sprint 07: Audio Codec Optimization, Jitter Buffering & Packet Loss Concealment (PLC) (Phase 5)
- **Goal**: Upgrade audio streaming from raw PCM to multi-rate compressed frames with adaptive jitter buffering, PLC, and noise gating.
- **Completed**:
  - `ICA-701`: Adaptive Jitter Buffer & Playout Sequencer with RFC 3550 variance estimation (`AdaptiveJitterBuffer.kt`).
  - `ICA-702`: Packet Loss Concealment (PLC) with pitch period extrapolation, exponential decay, and crossfade (`PacketLossConcealment.kt`).
  - `ICA-703`: Adaptive Noise Gate & VOX Energy DSP with attack, hold, release, and dual-threshold hysteresis (`NoiseGate.kt`).
  - `ICA-704`: Multi-Rate Pure KMP Audio Codec Engine with 4-bit IMA ADPCM and single BLE GATT MTU compliance (`AudioCodec.kt`).
  - `ICA-705`: Audio Pipeline End-to-End Integration & Benchmark Test verifying continuous playout under 20% loss and 50ms jitter (`AudioPipelineIntegrationTest.kt`).

---

## ðŸš€ Active & Upcoming Sprints

### âœ… Sprint 08: Real-World Field Testing & Hardening (Phase 6 - Complete)
- **Sprint Goal**: Verify multi-hop mesh range, highway wind noise intelligibility, and 4-hour battery consumption.
- **Milestone Release**: `v1.0.0` Production Release Tagged

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-801** | Field Range Benchmark Matrix & Diagnostic Mesh Logger | `MeshDiagnostics.kt`, `RangeBenchmark.md` | âœ… Completed |
| **ICA-802** | Highway Wind Noise & Helmet Intelligibility Tuning | `WindNoiseFilter.kt` | âœ… Completed |
| **ICA-803** | Battery Drain Profiling (<30% over 4 hours) | `PowerProfiler.kt` | âœ… Completed |
| **ICA-804** | v1.0 Production Release Readiness Sign-Off | `proguard-rules.pro`, `v1.0-Release-Readiness.md` | âœ… Completed |

---

### âœ… Sprint 09: Cellular Fallback & Cloud Relay Bridge (Phase 7 - v2.0 Roadmap)
- **Sprint Goal**: Bridge separated mesh clusters across long distances via Ktor WebSocket signaling server and cloud relay.
- **Status**: âœ… 100% Complete (All 4 Stories Passed)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-901** | Ktor WebSocket Cloud Mesh Relay Gateway | `:server`, `:core`, `CloudRelayBridge.kt` | âœ… Completed |
| **ICA-902** | Low-Power BLE Proximity Radar & Distance Alert UI | `sharedUI`, `ContentView.swift` | âœ… Completed |
| **ICA-903** | Dynamic Mesh Topology Auto-Healing & Leader Re-Election | `GroupManager.kt`, `MeshTransport.kt` | âœ… Completed |
| **ICA-904** | GitHub Actions Multiplatform CI/CD Pipeline | `.github/workflows/ci.yml` | âœ… Completed |

---

### âœ… Sprint 10: Multi-Channel Intercom, Priority Emergency & Audio Spatialization (Phase 8 - v2.0 Roadmap)
- **Sprint Goal**: Implement multi-channel sub-group partitioning, hazard emergency voice override, and binaural stereo audio spatialization.
- **Sprint Branch**: `sprint/sprint-10-multi-channel-spatial-audio`
- **Status**: âœ… 100% Complete (All 4 Stories Passed)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-1001** | Multi-Channel Group Partitioning & Channel Manager | `ChannelManager.kt`, `ChannelManagerTest.kt` | âœ… Completed |
| **ICA-1002** | Emergency Priority Voice & Hazard Alert Broadcast | `PriorityBroadcastManager.kt`, `PriorityBroadcastManagerTest.kt` | âœ… Completed |
| **ICA-1003** | Binaural Stereo Audio Spatializer & Convoy Panning | `SpatialAudioProcessor.kt`, `SpatialAudioProcessorTest.kt` | âœ… Completed |
| **ICA-1004** | Adaptive RF Interference & Frequency Map Diagnostic Engine | `RfInterferenceDetector.kt`, `RfInterferenceDetectorTest.kt` | âœ… Completed |

---

### âœ… Sprint 11: Convoy Situational Awareness, GPS Mesh Telemetry & Blackbox Voice Cache (Phase 9 - v2.0 Roadmap)
- **Sprint Goal**: Implement offline canned hazard text messaging, decentralized GPS mesh coordinate beaconing, LinkQuality-driven adaptive bitrate controller, and rolling emergency voice blackbox recorder.
- **Sprint Branch**: `sprint/sprint-11-convoy-gps-blackbox`
- **Status**: âœ… 100% Complete (All 4 Stories Passed & Merged into develop)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-1101** | Offline Mesh Canned Hazard & Quick Text Messaging | `MeshTextManager.kt`, `MeshTextManagerTest.kt` | âœ… Completed |
| **ICA-1102** | Decentralized GPS Mesh Telemetry & Convoy Tracking | `ConvoyGpsTracker.kt`, `ConvoyGpsTrackerTest.kt` | âœ… Completed |
| **ICA-1103** | Dynamic Adaptive Bitrate & Codec Profile Switching | `AdaptiveBitrateController.kt`, `AdaptiveBitrateControllerTest.kt` | âœ… Completed |
| **ICA-1104** | Rolling Emergency Voice Blackbox & Encrypted Incident Cache | `EmergencyBlackboxRecorder.kt`, `EmergencyBlackboxRecorderTest.kt` | âœ… Completed |

---

### âœ… Sprint 12: Multiplatform Cockpit UI & Bridge Integration (Phase 10 - v2.0 Roadmap)
- **Sprint Goal**: Deliver ergonomic cockpit UI components across Android Compose and 100% native iOS SwiftUI, complete with 16-channel dial, radar targets, convoy telemetry, quick hazard alerts, emergency preemption banner, and end-to-end integration tests.
- **Sprint Branch**: `sprint/sprint-12-cockpit-ui-integration`

| Story ID | Story Title | Key Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-1201** | KMP Swift Bridge Extension for v2.0 Subsystems | `IntercomBridge.kt` | âœ… Completed |
| **ICA-1202** | Native iOS SwiftUI Cockpit v2.0 UI Upgrade | `ContentView.swift`, `IntercomViewModel.swift` | âœ… Completed |
| **ICA-1203** | Android & Desktop Compose Multiplatform Cockpit Components | `HomeUiState.kt`, `HomeViewModel.kt`, `HomeScreen.kt`, `ChannelSelectorBar.kt`, `ProximityRadarView.kt`, `QuickHazardBar.kt`, `EmergencyPreemptionBanner.kt` | âœ… Completed |
| **ICA-1204** | Multiplatform End-to-End Presentation Integration Test | `CancellationHandle.kt`, `IntercomBridgeContractTest.kt`, `HomeViewModelTest.kt` | âœ… Completed |

---

### âœ… Sprint 13: v2.0 Production Release & Hardening (Phase 11 - v2.0 Roadmap)
- **Sprint Goal**: Wire Android platform hardware engine to Compose Cockpit UI, validate release build variants, verify battery/memory bounds, and perform v2.0.0 production release tagging.
- **Sprint Branch**: `sprint/sprint-13-v2-production-release`
- **Status**: âœ… 100% Complete (All 4 Stories Passed & v2.0.0 Tagged)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-1301** | Android Platform Hardware-to-Cockpit Service Bridge | `IntercomForegroundService.kt`, `AndroidPlatformBridge.kt`, `HomeViewModel.kt`, `HomeUiState.kt` | âœ… Completed |
| **ICA-1302** | Multiplatform Production Release Build & ProGuard Hardening | `build.gradle.kts`, `proguard-rules.pro`, `libs.versions.toml` | âœ… Completed |
| **ICA-1303** | Long-Running Battery, WakeLock & Memory Profiling Gate | `PowerProfilerTest.kt`, `IntercomForegroundService.kt` | âœ… Completed |
| **ICA-1304** | Architecture Sync, Documentation & v2.0.0 Release Tagging | `docs/PRD.md`, `CHANGELOG.md`, `TASKS.md`, `SPRINT.md`, `ARCHITECTURE.md` | âœ… Completed |

---

### âœ… Sprint 14: Advanced Convoy Mesh, Forward-Secret Key Ratchet & Live Activity (Phase 12 - v2.1 Roadmap)
- **Sprint Goal**: Deliver pure KMP Perfect Forward Secrecy (PFS) session key ratcheting, direct helmet Bluetooth GATT accessory integration (Cardo/Sena/EJEAS), native iOS 18+ Live Activity / Dynamic Island PTT widget, and multi-cluster cloud cross-mesh SFU bridging.
- **Sprint Branch**: `sprint/sprint-14-advanced-convoy-mesh`
- **Status**: âœ… 100% Complete (All 4 Stories Passed & v2.1.0 Release Tagged)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-1401** | Pure KMP Perfect Forward Secrecy (PFS) & Ephemeral Key Ratchet Engine | `MeshKeyRatchet.kt`, `PacketCipher.kt`, `MeshKeyRatchetTest.kt` | âœ… Completed |
| **ICA-1402** | Direct Helmet Intercom Bluetooth GATT Accessory Profile (Cardo / Sena / EJEAS) | `HelmetAccessoryManager.kt`, `HeadsetManager.kt`, `HelmetAccessoryManagerTest.kt` | âœ… Completed |
| **ICA-1403** | iOS 18+ Live Activity & Dynamic Island Hands-Free PTT Widget | `IntercomActivityAttributes.swift`, `IntercomActivityWidget.swift`, `IntercomViewModel.swift`, `IntercomBridge.kt` | âœ… Completed |
| **ICA-1404** | Multi-Cluster Cloud Cross-Mesh SFU Signaling & Audio Bridging | `SignalingProtocol.kt`, `SignalingServerManager.kt`, `CloudRelayBridge.kt`, `MultiClusterBridgeTest.kt` | âœ… Completed |

---

### ðŸš€ Sprint 15: Zero-Scroll Tactical Cockpit HUD & Hardware Telemetry Integration (Phase 13 - v2.2 Roadmap)
- **Sprint Goal**: Deliver end-to-end integration of Sprint 14 subsystems (`HelmetAccessoryManager`, `MeshKeyRatchet`, `CloudRelayBridge`) into presentation models, and build the Zero-Scroll Tactical Cockpit HUD across Compose Multiplatform (`sharedUI`) and 100% Native iOS SwiftUI (`iosApp`).
- **Sprint Branch**: `sprint/sprint-15-cockpit-hud-integration`
- **Status**: âœ… 100% Complete (All 4 Stories Implemented & Verified)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-1501** | Subsystem Telemetry Wiring (Accessory, PFS Ratchet, SFU Cloud) | `IntercomHardwareBridge.kt`, `AndroidPlatformBridge.kt`, `IntercomBridge.kt`, `HomeViewModel.kt`, `HomeUiState.kt` | âœ… Completed |
| **ICA-1502** | Compose Multiplatform Zero-Scroll Cockpit HUD | `HomeScreen.kt`, `PttButton.kt`, `QuickHazardBar.kt`, `ProximityRadarView.kt` | âœ… Completed |
| **ICA-1503** | Native iOS SwiftUI Tactical Cockpit HUD | `ContentView.swift`, `IntercomViewModel.swift` | âœ… Completed |
| **ICA-1504** | Multiplatform Verification Gates & Presentation Regression Tests | `HomeViewModelTest.kt`, Gradle automated tests, OpenCodeReview | âœ… Completed |

---

### âœ… Sprint 16: Mesh Repeater, Audio DSP & Convoy Simulation (Phase 14 - v2.3 Roadmap)
- **Sprint Goal**: Implement pure KMP speech dynamic range normalizers (AGC/DRC), autonomous stationary basecamp mesh repeater mode, automated 8-node convoy simulation harness, and over-the-air cryptographic epoch migration.
- **Sprint Branch**: `sprint/sprint-16-repeater-dsp-simulation` $\rightarrow$ `develop`
- **Status**: âœ… 100% Complete (All 4 Stories Passed & Merged to develop)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-1601** | Pure KMP Automatic Gain Control (AGC) & Dynamic Range Compression (DRC) | `AutomaticGainControl.kt`, `DynamicRangeCompressor.kt`, `AudioEngine.kt` | âœ… Completed |
| **ICA-1602** | Autonomous Mesh Repeater & Basecamp Beacon Mode | `MeshRepeaterService.kt`, `MeshTransport.kt` | âœ… Completed |
| **ICA-1603** | Automated Multi-Node Mesh Field Simulation Harness | `ConvoyMeshSimulationHarness.kt` | âœ… Completed |
| **ICA-1604** | Seamless Mesh OTA Group Re-Keying & Dynamic Epoch Migration | `GroupEpochManager.kt`, `GroupManager.kt` | âœ… Completed |

---

### âœ… Sprint 17: Basecamp Stationary Repeater UI & Low-Power Cockpit Mode (Phase 15 - v2.3 Roadmap)
- **Sprint Goal**: Implement dedicated Basecamp Stationary Repeater UI, high-contrast OLED power-saving screens, 100% native SwiftUI StandBy landscape cockpit, and live remote basecamp telemetry with 1-tap diagnostic pings on the main cockpit HUD.
- **Sprint Branch**: `sprint/sprint-17-basecamp-repeater-ui` $\rightarrow$ `develop`
- **Status**: âœ… 100% Complete (All 4 Stories Passed & Verified)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-1701** | Basecamp Repeater State Management & Platform Bridge Wiring | `RepeaterViewModel.kt`, `RepeaterUiState.kt`, `IntercomHardwareBridge.kt`, `AndroidPlatformBridge.kt`, `IntercomBridge.kt` | âœ… Completed |
| **ICA-1702** | Compose Multiplatform Basecamp Repeater Screen & OLED Dark Standby | `BasecampRepeaterScreen.kt`, `AppNavGraph.kt`, `HomeScreen.kt` | âœ… Completed |
| **ICA-1703** | Native iOS SwiftUI Basecamp Repeater Cockpit & StandBy Display | `BasecampRepeaterView.swift`, `ContentView.swift`, `IntercomViewModel.swift` | âœ… Completed |
| **ICA-1704** | Remote Repeater Telemetry & Diagnostic Ping Cockpit HUD Integration | `HomeScreen.kt`, `HomeViewModel.kt`, `HomeUiState.kt`, `ContentView.swift`, `HomeViewModelTest.kt` | âœ… Completed |

---

### ðŸš€ Sprint 18: Offline Vector Terrain & Trail Map Overlay (Phase 16 - v2.5 Roadmap)
- **Sprint Goal**: Implement pure KMP offline vector geometry projection, trail tracks, elevation contours, waypoint breadcrumbs, and seamless radar integration across Compose Multiplatform (`sharedUI`) and 100% Native iOS SwiftUI (`iosApp`).
- **Sprint Branch**: `sprint/sprint-18-offline-vector-terrain-map`
- **Status**: âœ… 100% Complete (All 4 Stories Passed & Verified)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-1801** | Pure KMP Offline Vector Trail & Terrain Geometry Engine | `VectorMapModels.kt`, `TrailMapEngine.kt`, `TrailMapManager.kt`, `TrailMapManagerTest.kt` | âœ… Completed |
| **ICA-1802** | Subsystem Bridge Wiring & Reactive Presentation Integration | `IntercomHardwareBridge.kt`, `IntercomBridge.kt`, `HomeViewModel.kt`, `HomeViewModelTest.kt` | âœ… Completed |
| **ICA-1803** | Compose Multiplatform Radar Topo Contour & Vector Trail Overlay | `ProximityRadarView.kt`, `OfflineTrailCockpitSheet.kt`, `HomeScreen.kt` | âœ… Completed |
| **ICA-1804** | Native iOS SwiftUI Radar Topographic Overlay & Offline Trail Cockpit Sheet | `ContentView.swift`, `OfflineTrailCockpitSheet.swift`, `IntercomViewModel.swift` | âœ… Completed |

---

### ðŸš€ Sprint 19: Over-The-Air BLE Mesh GPX/GeoJSON Trail Sharing & Storage Manager (Phase 17 - v2.6 Roadmap)
- **Sprint Goal**: Implement pure KMP chunked file transmission engine (Adler32, sliding-window retransmission, MTU <= 120 bytes), GPX/GeoJSON parsers and exporters, and seamless over-the-air route beaming across Compose Multiplatform (`sharedUI`) and 100% Native iOS SwiftUI (`iosApp`).
- **Sprint Branch**: `sprint/sprint-19-mesh-trail-sharing`
- **Status**: âœ… 100% Complete (All 4 Stories Passed & Verified)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-1901** | Pure KMP Chunked Mesh File Transfer & GPX/GeoJSON Protocol Engine | `MeshFileModels.kt`, `MeshFileTransferEngine.kt`, `MeshFileTransferManager.kt`, `MeshFileTransferTest.kt` | âœ… Completed |
| **ICA-1902** | Subsystem Hardware Bridge Wiring & Reactive State Management | `IntercomHardwareBridge.kt`, `IntercomBridge.kt`, `HomeViewModel.kt`, `HomeViewModelTest.kt` | âœ… Completed |
| **ICA-1903** | Compose Multiplatform Trail File Manager & Over-The-Air Beam Cockpit Sheet | `TrailFileManagerSheet.kt`, `OfflineTrailCockpitSheet.kt`, `HomeScreen.kt` | âœ… Completed |
| **ICA-1904** | Native iOS SwiftUI Trail File Manager & Over-The-Air Beam Sheet | `TrailFileManagerSheet.swift`, `OfflineTrailCockpitSheet.swift`, `IntercomViewModel.swift` | âœ… Completed |

---

### ðŸš€ Sprint 20: Tactical Convoy Geofencing, Autonomous Power Governor & Multi-Node Stress Benchmark Suite (Phase 18 - v2.7 Roadmap)
- **Sprint Goal**: Implement real-time convoy tether geofencing and waypoint proximity radar alarms, smart battery duty-cycle governing with motion-aware rest-stop sleep modes, multiplatform HUD presentation, and an automated 16-node virtual convoy stress harness with Archify showcase updates.
- **Sprint Branch**: `sprint/sprint-20-geofence-power-stress`
- **Status**: âœ… 100% Complete (All 4 Stories Passed & Released under v2.7.0)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-2001** | Pure KMP Convoy Geofencing, Hazard Waypoint Radar Alarms & Convoy Split Engine | `ConvoyGeofenceModels.kt`, `ConvoyGeofenceEngine.kt`, `ConvoyGeofenceEngineTest.kt` | âœ… Completed |
| **ICA-2002** | Smart Battery Duty-Cycle Governor & Motion-Aware Power Mode | `PowerStateModels.kt`, `BatteryGovernor.kt`, `BatteryGovernorTest.kt` | âœ… Completed |
| **ICA-2003** | Subsystem Hardware Bridge Wiring & Multiplatform Presentation HUD Integration | `IntercomHardwareBridge.kt`, `IntercomBridge.kt`, `HomeScreen.kt`, `ContentView.swift` | âœ… Completed |
| **ICA-2004** | 16-Node Concurrent Convoy Stress Simulation Harness & Archify Showcase Update | `ConvoyStressSimulationHarness.kt`, `intercom-alpha.architecture.json` | âœ… Completed |

---

### ðŸš€ Sprint 21: Autonomous Crash Impact Detection, eCall SOS & Offline Voice Command HUD (Phase 19 - v2.8 Roadmap)
- **Sprint Goal**: Implement pure KMP multi-axis IMU crash & fall-over impact detection with 30s abortable countdown, offline keyword-spotting voice commands (eCall SOS, next/prev channel, mute, battery), subsystem platform hardware bridge wiring, and emergency HUD integration across Compose Multiplatform and 100% Native iOS SwiftUI.
- **Sprint Branch**: `sprint/sprint-21-crash-detection-voice-commands`
- **Status**: âœ… 100% Complete (All 4 Stories Passed & Released under v2.8.0)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-2101** | Pure KMP Crash Detection, Fall-Over IMU State Machine & Countdown Arbiter | `SensorModels.kt`, `CrashDetector.kt`, `CrashDetectorTest.kt` | âœ… Completed |
| **ICA-2102** | Pure KMP Offline Voice Command & Keyword Spotting DSP Engine | `VoiceCommandModels.kt`, `VoiceCommandRecognizer.kt`, `VoiceCommandRecognizerTest.kt` | âœ… Completed |
| **ICA-2103** | Subsystem Hardware Bridge Wiring & Multiplatform Emergency HUD Presentation | `IntercomHardwareBridge.kt`, `IntercomBridge.kt`, `HomeScreen.kt`, `ContentView.swift` | âœ… Completed |
| **ICA-2104** | Crash Impact & Voice Command Multiplatform Simulation Harness & Benchmark Suite | `CrashAndVoiceSimulationHarness.kt`, `intercom-alpha.architecture.json` | âœ… Completed |

---

### ðŸš€ Sprint 22: Dynamic Mesh Directed Routing, AODV-Lite Route Optimization & RF Collision Reduction (Phase 20 - v2.9 Roadmap)
- **Sprint Goal**: Implement pure KMP AODV-Lite on-demand directed routing, dynamic link quality cost metrics (ETX), selective unicast/multicast forwarding, route error self-healing, multiplatform routing topology HUD presentation, and a 24-node RF contention reduction simulation harness.
- **Sprint Branch**: `sprint/sprint-22-mesh-directed-routing`
- **Target Release**: `v2.9.0`
- **Status**: âœ… 100% Complete (All 4 Stories Passed & Verified)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-2201** | Pure KMP AODV-Lite Directed Routing Engine & Route Table Arbiter | `RoutingModels.kt`, `RoutingTable.kt`, `AodvRoutingEngine.kt`, `AodvRoutingEngineTest.kt` | âœ… Completed |
| **ICA-2202** | Selective Unicast/Multicast Forwarding & RF Airtime Contention Mitigator | `LinkMetricCalculator.kt`, `MeshTransport.kt`, `DirectedForwardingTest.kt` | âœ… Completed |
| **ICA-2203** | Subsystem Hardware Bridge Wiring & Multiplatform Mesh Topology HUD Presentation | `IntercomHardwareBridge.kt`, `IntercomBridge.kt`, `HomeScreen.kt`, `ContentView.swift` | âœ… Completed |
| **ICA-2204** | 24-Node Large-Scale Directed Mesh RF Contention Benchmark & Archify Showcase Update | `DirectedMeshSimulationHarness.kt`, `intercom-alpha.architecture.json` | âœ… Completed |

---

### âœ… Sprint 23: Multi-Group Inter-Convoy Gateway & Cross-Mesh Federation (Phase 21 - v2.10 Roadmap)
- **Sprint Goal**: Implement pure KMP inter-group gateway contracts, cross-mesh audio bridging, sub-group isolation, anti-feedback loop guard, multiplatform federation HUD presentation, and a dual-convoy 32-node simulation benchmark.
- **Sprint Branch**: `sprint/sprint-23-multi-group-gateway`
- **Target Release**: `v2.10.0`
- **Status**: âœ… 100% Complete (All 4 Stories Passed & Verified)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-2301** | Pure KMP Multi-Group Gateway Engine & Cross-Mesh Federation Protocol | `FederationModels.kt`, `InterGroupGateway.kt`, `CrossMeshRelayEngine.kt`, `InterGroupGatewayTest.kt` | âœ… Completed |
| **ICA-2302** | Selective Inter-Group Audio Demuxing, Sub-Group Isolation & Anti-Feedback Loop Guard | `FederationLoopDetector.kt`, `CrossMeshForwardingArbiter.kt`, `CrossMeshForwardingTest.kt` | âœ… Completed |
| **ICA-2303** | Subsystem Hardware Bridge Wiring & Multiplatform Federation HUD Presentation | `IntercomHardwareBridge.kt`, `IntercomBridge.kt`, `HomeScreen.kt`, `ContentView.swift` | âœ… Completed |
| **ICA-2304** | Dual-Convoy 32-Node Cross-Mesh Simulation Harness & Archify Showcase Update | `MultiGroupFederationSimulationHarness.kt`, `intercom-alpha.architecture.json` | âœ… Completed |

---

### âœ… Sprint 24: Mesh Airtime QoS Traffic Shaping & Airtime Contention Mitigator (Phase 22 - v2.11 Roadmap)
- **Sprint Goal**: Implement pure KMP 4-tier strict priority voice QoS traffic shaping, preemption arbiter, MeshTransport & file airtime duty-cycle throttling, multiplatform HUD presentation, and a 32-node congested mesh simulation harness.
- **Sprint Branch**: `sprint/sprint-24-adaptive-bitrate-qos`
- **Target Release**: `v2.11.0`
- **Status**: âœ… 100% Complete (All 4 Stories Passed & Released under `v2.11.0`)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-2401** | Pure KMP Mesh Priority QoS Traffic Scheduler & Preemption Arbiter | `QoSModels.kt`, `MeshQoSQueue.kt`, `MeshQoSScheduler.kt`, `MeshQoSSchedulerTest.kt` | âœ… Completed |
| **ICA-2402** | MeshTransport Wire Integration & Bulk File Airtime Throttling | `MeshTransport.kt`, `MeshFileTransferManager.kt`, `MeshTransportQoSTest.kt` | âœ… Completed |
| **ICA-2403** | Subsystem Hardware Bridge Wiring & Multiplatform Tactical QoS HUD Presentation | `IntercomHardwareBridge.kt`, `AndroidPlatformBridge.kt`, `IntercomBridge.kt`, `HomeScreen.kt`, `ContentView.swift` | âœ… Completed |
| **ICA-2404** | 32-Node Congested RF Mesh QoS Simulation Harness & Archify Showcase Update | `CongestedMeshQoSSimulationHarness.kt`, `intercom-alpha.architecture.json` | âœ… Completed |

---

### âœ… Sprint 25: Automated Multi-Path Mesh Failover & Hybrid Cellular/SFU Relay Handover (Phase 23 - v2.12 Roadmap)
- **Sprint Goal**: Implement pure KMP multi-path link quality arbiter, automated BLE-to-Cellular failover state machine, packet sequence stream hedging to ensure zero audio frame drops during RF disconnects, multiplatform avionics HUD presentation, and a highway split simulation harness.
- **Sprint Branch**: `sprint/sprint-25-multipath-mesh-failover`
- **Target Release**: `v2.12.0`
- **Status**: âœ… 100% Complete (All 4 Stories Passed & Released under `v2.12.0`)

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-2501** | Pure KMP Multi-Path Link Quality Arbiter & Automated Failover State Machine | `FailoverModels.kt`, `MultiPathFailoverArbiter.kt`, `MultiPathFailoverArbiterTest.kt` | âœ… Completed |
| **ICA-2502** | Transport Handover Wire Integration & Seamless Voice Stream Hedging | `CloudRelayBridge.kt`, `HybridTransportRouter.kt`, `HybridTransportRouterTest.kt` | âœ… Completed |
| **ICA-2503** | Subsystem Hardware Bridge Wiring & Multiplatform Tactical Transport Handover HUD | `IntercomHardwareBridge.kt`, `AndroidPlatformBridge.kt`, `IntercomBridge.kt`, `HomeScreen.kt`, `ContentView.swift` | âœ… Completed |
| **ICA-2504** | Highway Convoy Out-of-Range Handover Simulation Harness & Archify Showcase Update | `MultiPathHandoverSimulationHarness.kt`, `intercom-alpha.architecture.json` | âœ… Completed |

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
- **Status**: ✅ 100% Complete (All 4 Stories Passed & Released under `v2.15.0`)
 
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


