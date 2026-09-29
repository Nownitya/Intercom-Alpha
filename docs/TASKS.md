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

### ⚪ Sprint 08: Real-World Field Testing & Hardening (Phase 6)
- **Sprint Goal**: Verify multi-hop mesh range, highway wind noise intelligibility, and 4-hour battery consumption.

| Task ID | Item | Target Files | Status |
| :--- | :--- | :--- | :--- |
| **ICA-801** | Field Range Benchmark Matrix (100m to 500m) | `docs/testing/field-reports/` | ⚪ Backlog |
| **ICA-802** | Highway Wind Noise & Helmet Intelligibility Tuning | Audio DSP config | ⚪ Backlog |
| **ICA-803** | Battery Drain Profiling (<30% over 4 hours) | Profiler logs & reports | ⚪ Backlog |
| **ICA-804** | v1.0 Production Release Readiness Sign-Off | Release tags & artifacts | ⚪ Backlog |
