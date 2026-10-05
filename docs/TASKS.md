# Master Tasks & Engineering Roadmap — Intercom-Alpha

> **Document Version:** 1.0  
> **Status:** Active / Tracked  
> **Current Sprint:** Sprint 07 Completed ➔ Sprint 08 Backlog  
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

