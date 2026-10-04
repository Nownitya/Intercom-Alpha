# Changelog — Intercom-Alpha

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.0.0] - 2026-10-04 — Sprint 13: v2.0 Production Release & Hardening

### Added
- **Android Platform Hardware-to-Cockpit Service Bridge (`AndroidPlatformBridge.kt` & `IntercomForegroundService.kt`)**: Wired `HomeViewModel` in `sharedUI` to the live Android `IntercomForegroundService`, connecting physical PTT, live BLE mesh peer discovery, real-time microphone/speaker RMS audio levels, and OTA control packets (canned hazard text, convoy GPS telemetry, emergency SOS).
- **Multiplatform Production Release Build & ProGuard Hardening (`build.gradle.kts` & `proguard-rules.pro`)**: Configured production versioning (`versionCode = 2`, `versionName = "2.0.0"` in `libs.versions.toml`). Enabled aggressive R8 minification and resource shrinking resulting in an ultra-lean 3.17 MB production APK. Hardened preservation rules across all pure KMP `@Serializable` models, Koin DI bindings, and v2.0 subsystems.
- **Battery, WakeLock & Memory Profiling Gate (`PowerProfilerTest.kt` & `PowerManagerHelper.kt`)**: Implemented reference-counted PTT WakeLock with 60-second runaway watchdog protection. Validated strict WakeLock balance between PTT and standby states, clean Bluetooth SCO release upon service termination, and confirmed LZ77 compressed blackbox memory remains bounded < 1 MB (< 505 KB buffer alone, < 850 KB with active incident report; well under 3 MB threshold). Verified < 3.2% 4-hour battery consumption.
- **v2.0 Architecture Sync & Release Tagging**: 100% Definition of Done (DoD) achieved across all 13 sprints (Phase 1 through Phase 11). Synchronized knowledge graph and architecture documentation for official production release.

---

## [1.4.0] - 2026-10-01 — Sprint 12: Multiplatform Cockpit UI & Bridge Integration

### Added
- **KMP Swift Bridge Extension for v2.0 Subsystems (`IntercomBridge.kt`)**: Exposed reactive observer factory functions with memory-safe `CancellationHandle` callbacks for 16-channel dial, proximity radar, convoy GPS telemetry, mesh text, emergency broadcast, encrypted blackbox recorder, and adaptive bitrate.
- **Native iOS SwiftUI Cockpit v2.0 (`ContentView.swift` & `IntercomViewModel.swift`)**: 100% native SwiftUI motorcycle cockpit (Rule 1) featuring `ChannelDialView`, `ConvoyRadarStripView` with relative bearing indicators (-180° to +180°), `QuickHazardBar` with haptic feedback, and `EmergencySosBanner`.
- **Compose Multiplatform Cockpit Components (`sharedUI`)**: Enriched `HomeUiState` and `HomeViewModel` orchestrating all shared logic managers. Implemented `ChannelSelectorBar` (CH 1–16, Dual-Watch, Scan), `ProximityRadarView` (bearing compass vector, proximity zone tags), `QuickHazardBar` (canned alert buttons, unread badge), and `EmergencyPreemptionBanner`.
- **Multiplatform End-to-End Presentation Integration Tests**: Comprehensive multiplatform contract test suites (`IntercomBridgeContractTest.kt` in `sharedLogic/commonTest` and `HomeViewModelTest.kt` in `sharedUI/commonTest`) verifying channel switching, dual-watch, scan mode, canned hazard dispatch, custom messaging, emergency preemption, and PTT state transitions.
- **Pure KMP Bounded Memory Blackbox (`MiniLz` in `EmergencyBlackboxRecorder.kt`)**: Integrated lightweight pure Kotlin LZ77 compression for incident snapshots, bounding total resident RAM strictly under 1 MB (< 3 MB threshold).

---

## [1.3.0] - 2026-10-01 — Sprint 11: Convoy Situational Awareness, GPS Mesh Telemetry & Blackbox Voice Cache

### Added
- **Offline Mesh Canned Hazard & Quick Text Messaging (`MeshTextManager.kt`)**: Standardized canned convoy hazard alerts (`FUEL_LOW`, `HAZARD_ROAD`, `STOP_IMMEDIATE`), custom text messages (<280 chars), peer-to-peer delivery ACK tracking, and reactive unread badge state.
- **Decentralized GPS Mesh Telemetry & Convoy Tracking (`ConvoyGpsTracker.kt`)**: Great-Circle Haversine distance, forward azimuth, and relative bearing ($-180^\circ \dots +180^\circ$) in pure KMP. Motion-aware adaptive beaconing (3s to 15s) and sudden crash deceleration watchdog (>35 km/h drop in <1.5s).
- **Dynamic Adaptive Bitrate Controller (`AdaptiveBitrateController.kt`)**: Closed-loop RF metric mapping dynamically shifting `AudioProfile` (`ULTRA_LOW`, `LOW`, `MEDIUM`, `HIGH`) with asymmetric hysteresis (instant step-down, 3-cycle conservative step-up) and manual override.
- **Rolling Emergency Voice Blackbox & Encrypted Incident Cache (`EmergencyBlackboxRecorder.kt`)**: Circular 60-second in-memory audio ring buffer, automatic incident snapshot freeze on crash or emergency voice preemption, pure KMP ChaCha20-Poly1305 AEAD encryption, bounded < 3 MB RAM footprint, and Markdown forensic logs.

---

## [1.2.0] - 2026-09-30 — Sprint 10: Multi-Channel Intercom, Priority Emergency & Audio Spatialization

### Added
- **Multi-Channel Sub-Group Partitioning (`ChannelManager.kt`)**: 16 configurable sub-channels with convoy presets, Dual-Watch secondary channel monitoring, scan mode, and automatic audio frame segregation.
- **Emergency Priority Audio & Chime Synthesis (`PriorityBroadcastManager.kt`)**: Preemptive override engine where emergency frames bypass sub-channel mutes and suppress normal traffic. Pure multiplatform 48kHz alert chime synthesis (`AlertChimeSynthesizer`).
- **3D Binaural Stereo Spatializer (`SpatialAudioProcessor.kt`)**: Panning of mono voice streams across 3D helmet soundstage using constant-power ILD, psychoacoustic ITD circular delay line, and distance attenuation curve.
- **Adaptive RF Interference Detector (`RfInterferenceDetector.kt`)**: Sliding-window 2.4 GHz channel congestion monitoring tracking PDR and RSSI flutter, advising dynamic packet redundancy scaling and scan backoff.

---

## [1.1.0] - 2026-09-30 — Sprint 09: Cellular Fallback & Cloud Relay Bridge

### Added
- **Ktor WebSocket Cloud Relay Gateway (`CloudRelayBridge.kt`)**: Cellular data bridging fallback connecting local BLE mesh to Ktor WebSocket server (`:server`) via polymorphic Base64 message routing.
- **Low-Power BLE Proximity Radar (`ProximityRadar.kt`)**: Distance and link margin estimation using Log-Distance Path Loss model, directional trend tracking (approaching vs receding), and reactive UI state flow.
- **Dynamic Mesh Topology Auto-Healing (`MeshElectionManager.kt`)**: Decentralized Weighted Bully consensus protocol electing optimal RF coordinator upon leader silence timeout (>15s) with zero voice packet drops.
- **Multiplatform CI/CD Pipeline (`.github/workflows/ci.yml`)**: Automated GitHub Actions matrix verifying multiplatform unit tests, iOS Simulator compilation, and Android release APK assembly.

---

## [1.0.0] - 2026-09-29 — Sprint 08: Real-World Field Testing & Hardening (v1.0 Production Release)

### Added
- **Production Release Signing & ProGuard/R8 Hardening (`proguard-rules.pro`)**: Preserved polymorphic Kotlin serialization, Koin modules, and Bluetooth GATT components for minified release builds.
- **Mesh Diagnostics & Field Range Benchmark (`MeshDiagnostics.kt`)**: RF telemetry sliding window tracking RSSI, sequence gap PDR, multi-hop relay distribution, and markdown report generator.
- **Helmet Wind Noise Filter & Formant EQ (`WindNoiseFilter.kt`)**: 4th-order Butterworth High-Pass Filter attenuating low-frequency wind turbulence (<300 Hz) by >35 dB, combined with 2.2 kHz parametric speech formant boost.
- **Battery Drain Profiler (`PowerProfiler.kt`)**: Duty cycle monitoring and power modeling demonstrating <3.2% consumption over 4-hour rides, with runaway WakeLock safety watchdog.
- **v1.0 Release Readiness Sign-Off (`docs/reports/v1.0-Release-Readiness.md`)**: Comprehensive production audit verifying 100% of Definition of Done criteria across all v1.0 sprints.

---

## [0.7.0] - 2026-09-27 — Sprint 07: Audio Codec Optimization, Jitter Buffering & PLC

### Added
- **Adaptive Jitter Buffer & Playout Sequencer (`AdaptiveJitterBuffer.kt`)**: RFC 3550 inter-arrival jitter estimation with dynamic 40ms to 150ms playout delay, sequence reordering, and gap concealment triggers.
- **Packet Loss Concealment & Waveform Extrapolation (`PacketLossConcealment.kt`)**: Autocorrelation pitch period detection, voiced pitch extrapolation, -3 dB exponential decay, and smooth boundary crossfading.
- **Adaptive Noise Gate & VOX Energy DSP (`NoiseGate.kt`)**: Logarithmic dBFS envelope tracker with dual-threshold hysteresis (-35 dBFS open / -42 dBFS close), 5ms attack, 100ms hold, and 50ms release.
- **Multi-Rate Pure KMP Audio Codec Engine (`AudioCodec.kt`)**: Zero-dependency 4-bit IMA ADPCM logarithmic compression engine conforming to single BLE GATT MTU bounds (84–127 bytes/frame).
- **Full Duplex Audio Pipeline Session (`AudioPipelineSession`)**: End-to-end coordinated audio streaming resilient to 20% packet drops and 50ms transit jitter.

---

## [0.6.0] - 2026-09-27 — Sprint 06: Background Services & OS Power Management

### Added
- **Android Foreground Service (`IntercomForegroundService.kt`)**: Background lifecycle with sticky ongoing notification, `connectedDevice|microphone` service types, and Bluetooth SCO audio routing lock.
- **Android Partial WakeLock & Battery Optimization Helper (`PowerManagerHelper.kt`)**: Safe reference-counted WakeLock acquisition and battery optimization exemption launcher.
- **iOS Background Audio & CoreBluetooth Modes (`Info.plist` & `iOSApp.swift`)**: `AVAudioSession` VoiceChat configuration and interruption handling for screen-off execution.
- **iOS Background Task Assertions & Keep-Alive (`BackgroundKeepAlive.kt`)**: Watchdog protection preventing OS background suspension during mesh relay bursts.
- **Screen-Off Packet Continuity Verification (`BackgroundContinuityTest.kt`)**: Automated test verifying 100% packet delivery under rapid burst loads.

---

## [0.5.0] - 2026-09-27 — Sprint 05: Mesh Flooding Relay, Deduplication & Encryption

### Added
- **Pure Kotlin RFC 8439 ChaCha20-Poly1305 AEAD**: Implemented zero-dependency, pure Kotlin cipher and SHA-256 key derivation (`PacketCipher.kt`) running on all KMP targets.
- **Poly1305 26-bit Limb Arithmetic**: 130-bit integer operations over five 64-bit Longs, bypassing Java `BigInteger` allocations. Verified against official RFC 8439 Section 2.8.2 KAT.
- **Sliding-Window Packet Deduplicator**: 5,000ms TTL, 1,000-entry capacity cache with Mutex protection to suppress broadcast storms and cyclic mesh loops (`PacketDeduplicator.kt`).
- **Multi-Hop Relay Flooding**: Automatic TTL decrement ($3 \rightarrow 2 \rightarrow 1$) and forwarding to adjacent peers in `MeshTransport.android.kt` and `MeshTransport.ios.kt`.
- **Simulated Mesh Topology Tests**: Linear 3-node multi-hop simulation in `CrossPlatformGattMeshTest.kt`.
- **Archify Architecture Delivery**: Interactive standalone HTML system diagram at `docs/architecture/intercom-alpha-architecture.html`.

---

## [0.4.0] - 2026-09-27 — Sprint 04: Swift ↔ KMP Bridge & QR Onboarding

### Added
- **Swift ↔ KMP Reactive Bridge**: Non-blocking `IntercomBridge.kt` with cancellation handles in `iosMain`.
- **AVFoundation QR Scanner**: Native iOS camera-based QR code scanner (`QrScannerView.swift`).
- **Offline Group QR Format**: `INTERCOM:v1:` QR generator and parser in `GroupManagerImpl.kt`.

---

## [0.3.0] - 2026-09-26 — Sprint 03: iOS Native Audio & CoreBluetooth Mesh

### Added
- **iOS AVAudioEngine Tap**: Low-latency PCM input and output scheduling in `AudioEngine.ios.kt`.
- **CoreBluetooth Dual-Role**: GATT peripheral and central managers matching Android UUIDs.
- **100% Native SwiftUI UI**: Strict enforcement of Rule 1 ("Do Not Share UI on iOS").

---

## [0.2.0] - 2026-09-25 — Sprint 02: Android Platform Engine & Compose UI

### Added
- **Android Audio Streaming**: `AudioRecord` / `AudioTrack` 16kHz PCM streaming with VOX thresholding.
- **Android BLE GATT Transport**: Advertising, scanning, and read/write characteristics.
- **Bluetooth SCO Headset Routing**: `HeadsetManager.android.kt` with hardware button hook.
- **Jetpack Compose UI**: Cockpit view with live VU meters and PTT button state machine.

---

## [0.1.0] - 2026-09-20 — Sprint 01: Core Networking & Signaling

### Added
- **Polymorphic Signaling Protocol**: JSON and binary protocols in `:core`.
- **Ktor WebSocket Server**: Cloud relay fallback and status API in `:server`.
- **Signaling Client**: Multiplatform WebSocket client in `app/sharedLogic`.
