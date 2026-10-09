# Engineering Journal & Changelog

> **Obsidian Source:** `D:\ObsidianVault\Vault1\ObsidianVault\Android\Projects\Intercom-Alpha\10-Daily-Notes\`  
> **MemPalace Integration:** Wing `intercom_alpha` (2,426 drawers)

## 📅 2026-10-09: Sprint 23 — Multi-Group Inter-Convoy Gateway & Cross-Mesh Federation (Phase 21)

### Summary
1. **Pure KMP Multi-Group Gateway Engine & Cross-Mesh Protocol (`InterGroupGateway.kt`, `FederationModels.kt`, `CrossMeshRelayEngine.kt`)**:
   - Implemented inter-group gateway contracts (`GroupFederationInvite`, `FederationBridgeState`, `CrossGroupPolicy`, `CrossMeshAudioPacket`).
   - Policy-driven audio and hazard bridging between distinct cryptographic groups with destination group re-encryption.
2. **Selective Inter-Group Audio Demuxing & Loop Guard (`FederationLoopDetector.kt`, `CrossMeshForwardingArbiter.kt`)**:
   - Monotonic `federationPathTrace` and cross-group deduplication eliminating circular feedback loops across redundant gateways.
   - Enforced sub-group isolation policies (`CAPTAIN_ONLY`, `EMERGENCY_SOS_ONLY`, `ALL_MEMBER_BRIDGED`).
3. **Subsystem Hardware Bridge Wiring & Multiplatform Presentation HUD**:
   - Integrated into `IntercomHardwareBridge`, `AndroidPlatformBridge`, and `IntercomBridge` (`iosMain`) with `CancellationHandle` closures.
   - Dynamic Federation badge and diagnostics sheet in Compose Multiplatform and 100% Native iOS SwiftUI (Rule 1).
4. **Dual-Convoy 32-Node Cross-Mesh Simulation Harness & Archify Showcase (`MultiGroupFederationSimulationHarness.kt`)**:
   - Simulated 32 virtual nodes across dual squads with redundant dual-gateway arbitration.
   - Verified zero feedback loops, sub-group isolation, and emergency all-call preemption.
5. **Verification & Release**:
   - Multiplatform verification passed: `./gradlew test :app:androidApp:assembleDebug`.
   - OpenCodeReview Delegation Gate passed with 0 findings.
   - Released `v2.10.0`.

---

## 📅 2026-10-08: Sprint 22 — Dynamic Mesh Directed Routing & AODV-Lite (Phase 20)

### Summary
1. **Pure KMP AODV-Lite Directed Routing Engine (`AodvRoutingEngine.kt`, `RoutingModels.kt`)**:
   - On-demand route discovery (`RREQ`/`RREP`/`RERR`) with monotonic sequence numbers, link delivery ratio & RSSI-based ETX metrics.
2. **Selective Forwarding & RF Airtime Contention Mitigator**:
   - Reduced RF airtime contention by >80% in 24-node convoys compared to raw flooding.
3. **Multiplatform Topology HUD Presentation**:
   - Routing hop visualization in Compose Multiplatform and 100% Native SwiftUI.
4. **24-Node Large-Scale Directed Mesh RF Contention Benchmark**:
   - Verified route convergence and loop-free forwarding. Released `v2.9.0`.

---

## 📅 2026-10-07: Sprint 21 — Autonomous Crash Impact Detection & Offline Voice Command HUD (Phase 19)

### Summary
1. **Pure KMP Crash Detection & Fall-Over IMU State Machine (`CrashDetector.kt`)**:
   - Multi-axis IMU collision shock (8.5g severe impact, 4.5g collision, >60° sustained fall-over) with 30s abortable countdown.
2. **Pure KMP Offline Voice Command & Keyword Spotting DSP (`VoiceCommandRecognizer.kt`)**:
   - Acoustic DSP keyword spotting ("Intercom SOS", "Next Channel", "Prev Channel", "Toggle Mute", "Battery Status", "Cancel Alert") under 80–120 km/h wind noise.
3. **Emergency HUD Integration**:
   - Real-time pre-alert countdown banner across Compose and 100% Native SwiftUI. Released `v2.8.0`.

---

## 📅 2026-10-07: Sprint 20 — Tactical Convoy Geofencing, Autonomous Power Governor & Multi-Node Stress Benchmark Suite (Phase 18)

### Summary
1. **Pure KMP Convoy Geofencing & Split Engine (`ConvoyGeofenceEngine.kt`, `ConvoyGeofenceModels.kt`)**:
   - Great-Circle Haversine tether monitoring: warning at >300m, breach alarm at >600m from lead rider.
   - Hazard waypoint proximity radar: alerts riders entering obstacle radii (e.g. 100m).
   - Single-linkage BFS connected-component clustering for convoy partition/split detection across rider clusters.
2. **Autonomous Power Governor & Motion Watchdog (`BatteryGovernor.kt`, `PowerStateModels.kt`)**:
   - Dynamic radio duty cycling (100% normal -> 50% conservative at <30% -> 20% survival at <15%).
   - Rest-stop stillness watchdog (>10 min at <= 3.0 km/h) transitioning to 5% sleep mode.
   - Instant wake triggers on motion resumption (>= 5.0 km/h), handlebar PTT actuation, or charger connection.
   - Unconditional Emergency SOS preemption restoring 100% duty cycle and full bandwidth.
3. **Hardware Bridge Wiring & Multiplatform Presentation HUD**:
   - Subsystems wired to `IntercomHardwareBridge`, `AndroidPlatformBridge`, and `IntercomBridge` (`iosMain`) with reactive `CancellationHandle` closures.
   - Compose Multiplatform: Dynamic tether boundary ring, obstacle hazard markers, `TETHER`/`PWR` telemetry badges, and high-contrast `GeofenceBreachBanner`.
   - 100% Native iOS SwiftUI: Dynamic tether boundary ring in `TacticalVectorRadarView`, battery governor power mode pill in `TacticalAvionicsBar`, and native tether breach warning banner (Rule 1).
4. **16-Node Virtual Convoy Stress Simulation Harness & Archify Showcase (`ConvoyStressSimulationHarness.kt`)**:
   - Simulated 16 concurrent virtual riders exchanging voice ADPCM packets, OTA route chunks, geofence alarms, and battery status over lossy multi-hop RF mesh.
   - Verified 8 linear hops with 100% voice decryption fidelity and zero lost chunks.
   - Validated memory stability (<80 MB RAM) and zero coroutine leaks.
   - Updated and delivered Archify interactive architecture diagram with 9/9 checks passed, 0 errors, 0 warnings.
5. **Verification & Release**:
   - Multiplatform verification passed: `./gradlew test :app:androidApp:assembleDebug :app:sharedLogic:compileKotlinIosSimulatorArm64` (125 actionable tasks).
   - OpenCodeReview Delegation Gate: 0 findings (`APPROVED`).
   - Tagged production release `v2.7.0` on `main`.

---

## 📅 2026-10-06: Sprint 16 — Mesh Repeater, Audio DSP & Convoy Simulation (Phase 14)

### Summary
1. **Audio DSP Hardening: Pure KMP AGC & Dynamic Range Compression (`AutomaticGainControl.kt`, `DynamicRangeCompressor.kt`)**:
   - Normalizes speech levels to $-18.0\text{ dBFS}$ target within $150\text{ ms}$ with anti-pumping protection below $-50\text{ dBFS}$.
   - Soft-knee $4:1$ compressor with $6.0\text{ dB}$ transition width and lookahead brickwall peak limiter ($\le -0.5\text{ dBFS}$) preventing audio clipping without Java/C dependencies.
2. **Autonomous Stationary Basecamp Mesh Repeater (`MeshRepeaterService.kt`)**:
   - Dedicated low-power relay service operating with zero audio hardware buffer allocations.
   - Sub-$15\text{ ms}$ multi-hop forwarding latency, loop suppression via `PacketDeduplicator`, and 60-second active neighbor table tracking.
   - Periodic `REPEATER_BEACON` broadcasts with battery percentage, solar status, and diagnostic ping/pong responses.
3. **Automated Multi-Node Convoy Mesh Simulation Harness (`ConvoyMeshSimulationHarness.kt`)**:
   - 8-node virtual convoy simulation with distance-bounded RF path loss, TTL decrements, loop suppression, and $25\%$ stochastic packet loss.
   - Validates that `MeshKeyRatchet` skipped-key caching decrypts $100\%$ of out-of-order received packets.
   - High-throughput simulation execution completing in $<5\text{ seconds}$.
4. **Over-The-Air Dynamic Epoch Migration & Group Re-Keying (`GroupEpochManager.kt`)**:
   - RFC 5869 HKDF-SHA256 key derivation with salt and domain separation.
   - Leader-authenticated `EPOCH_ROLLOVER` control frame signed via RFC 2104 HMAC-SHA256.
   - Dual-epoch concurrent decryption across a $30\text{-second}$ window ensuring zero voice packet drops during active speech migration.
   - Consensus `EPOCH_BEACON` telemetry broadcasting and memory zeroization of expired keys.
5. **Verification**:
   - Multiplatform tests: `./gradlew :app:sharedLogic:jvmTest` (100% test pass).
   - Platform compilation: `:app:sharedLogic:compileKotlinIosSimulatorArm64` and `:app:androidApp:assembleDebug` passed.
   - OpenCodeReview Delegation Gate: 0 findings (`APPROVED`).
   - Merged cleanly into `develop`.

---

## 📅 2026-10-06: Sprint 15 — Zero-Scroll Tactical Cockpit HUD & Telemetry Integration (Phase 13)

### Summary
1. **Zero-Scroll Tactical Cockpit HUD (`HomeScreen.kt` & `ContentView.swift`)**:
   - Enforced zero-vertical-scroll layout contract on mobile RAM mounts across Compose Multiplatform (`sharedUI`) and Native iOS SwiftUI (`iosApp`).
   - Integrated 140dp circular tactile PTT actuator, 4-hazard macro strip, circular vector radar, and emergency SOS bar.
2. **Subsystem Telemetry Wiring**:
   - Connected `HelmetAccessoryManager`, `MeshKeyRatchet`, and `CloudRelayBridge` to presentation view models.
3. **v2.2.0 Production Release**:
   - Tagged production release `v2.2.0` with full test and platform verification pass.

---

## 📅 2026-10-04: Sprint 14 — Advanced Convoy Mesh, Forward-Secret Key Ratchet & Live Activity (Phase 12)

### Summary
1. **Pure KMP Perfect Forward Secrecy (PFS) & Ephemeral Key Ratchet Engine (`MeshKeyRatchet.kt`)**:
   - RFC 2104 HMAC-SHA256 and RFC 5869 HKDF-SHA256 implemented in 100% pure Kotlin Multiplatform (Rule 3).
   - Rolling symmetric KDF chain ratcheting deriving ephemeral transmission keys that zeroize previous session keys (`.fill(0)`).
   - Bounded out-of-order skipped key cache (up to 100 keys per peer) allowing disordered packet decryption while enforcing replay protection.
2. **Direct Helmet Intercom Bluetooth GATT Accessory Profile (`HelmetAccessoryManager.kt`)**:
   - Platform-agnostic BLE GATT client profile supporting Cardo PackTalk/Freecom, Sena 50S/RC4, EJEAS EUC, and generic BLE HID handlebar remotes.
   - Standard Bluetooth SIG Battery service (`0x2A19`), Device Info, button opcodes/bitmasks, and 60-second runaway PTT safety watchdog.
   - Dynamic audio route preference arbitration (`BLUETOOTH_SCO`, `A2DP_HIGH_FIDELITY`, `BUILTIN_SPEAKER`) and automatic disconnect release.
3. **Native iOS 18+ Live Activity & Dynamic Island Hands-Free PTT Widget (`IntercomActivityWidget.swift`)**:
   - 100% native SwiftUI / ActivityKit / WidgetKit implementation (Rule 1).
   - Dynamic Island Compact, Minimal, and Expanded layouts displaying live channel, transmitting speaker, RF link margin, compass radar bearing, and interactive PTT button.
   - Lock Screen / StandBy banner with tactile hands-free controls.
   - Intercom session lifecycle integration via `IntercomViewModel.swift`.
4. **Multi-Cluster Cloud Cross-Mesh SFU Signaling & Audio Bridging (`SignalingProtocol.kt`, `SignalingServerManager.kt`, `CloudRelayBridge.kt`)**:
   - Polymorphic `SignalingMessage.ClusterBridge` registered with `#type` class discriminator preventing collision with `MeshPacket.Control.type`.
   - Transcode-free cross-room and cross-cluster routing of 4-bit ADPCM packets with zero latency penalty.
   - Bidirectional room bridging (`bridgeRooms`, `unbridgeRooms`, `getBridgedRooms`) in `SignalingServerManager`.
   - Circular routing loop prevention in `CloudRelayBridge` via `hopLimit` and `clusterPath` traversal inspection.
   - Multi-cluster peer presence aggregation with dynamic remote cluster member tracking.
   - `AdaptiveJitterBuffer` integration for jitter smoothing on incoming cross-mesh audio streams.
5. **Verification**:
   - Multiplatform tests: `./gradlew test` (100% pass across `:core`, `:server`, `:app:sharedLogic`, and `:app:androidApp`).
   - Platform compilation: `:app:sharedLogic:compileKotlinIosSimulatorArm64` and `:app:androidApp:assembleDebug` both passed.
   - OpenCodeReview Delegation Gate: 0 findings (`APPROVED`).
   - Knowledge graph updated via `graphify update .` (2,289 nodes, 4,488 edges, 172 communities).

---

## 📅 2026-10-01: Sprint 12 — Multiplatform Cockpit UI & Bridge Integration (Phase 10 - v2.0 Roadmap)

### Summary
1. **KMP Swift Bridge Extension for v2.0 Subsystems (`IntercomBridge.kt`)**:
   - Extended `IntercomBridge.kt` with pure KMP functional interface `CancellationHandle` for safe lifecycle binding.
   - Exposed reactive observers for `ChannelManager`, `ProximityRadar`, `ConvoyGpsTracker`, `MeshTextManager`, `PriorityBroadcastManager`, `EmergencyBlackboxRecorder`, and `AdaptiveBitrateController`.
   - Guaranteed thread safety via `SupervisorJob` on `Dispatchers.Main` with zero memory leaks.
2. **Native iOS SwiftUI Cockpit v2.0 (`ContentView.swift` & `IntercomViewModel.swift`)**:
   - Upgraded native SwiftUI interface strictly obeying Rule 1 (100% native SwiftUI, zero shared Compose on iOS).
   - Ergonomic high-contrast elements: `ChannelDialView` (CH 1–16, Dual-Watch badges), `ConvoyRadarStripView` (relative bearing indicators $\pm 180^\circ$, directional arrows), `QuickHazardBar` (haptic 1-tap alerts), and `EmergencySosBanner`.
3. **Compose Multiplatform Cockpit Components (`sharedUI`)**:
   - `HomeUiState.kt` and `HomeViewModel.kt`: Centralized reactive coordinator binding all shared subsystem StateFlows.
   - Modular UI components: `ChannelSelectorBar`, `EmergencyPreemptionBanner`, `ProximityRadarView`, `QuickHazardBar`, and `MessageHistoryDialog`.
   - Adaptive responsive cockpit layout supporting Android and Desktop.
4. **Multiplatform End-to-End Presentation Integration Tests**:
   - `IntercomBridgeContractTest.kt` in `sharedLogic/commonTest` verifying complete bridge reactive flow.
   - `HomeViewModelTest.kt` in `sharedUI/commonTest` testing UI interactions, hazard dispatches, channel switching, and SOS triggers.
5. **Pure KMP Bounded Memory Blackbox (`EmergencyBlackboxRecorder.kt`)**:
   - Pure KMP `MiniLz` LZ77 compression reduces cached incident report sizes by ~95% (< 50 KB each), guaranteeing total memory footprint remains strictly under 1 MB (< 3 MB threshold).
6. **Verification**:
   - `./gradlew test`: 100% passing across all modules (151+ tests).
   - `./gradlew :app:sharedLogic:compileKotlinIosSimulatorArm64`: Passed cleanly in 39s (Rule 6).
   - OpenCodeReview Delegation Gate: 0 findings (`APPROVED`).

---

## 📅 2026-10-01: Sprint 11 — Convoy Situational Awareness, GPS Mesh Telemetry & Blackbox Voice Cache (Phase 9 - v2.0 Roadmap)

### Summary
1. **Offline Mesh Canned Hazard & Quick Text Messaging (`MeshTextManager.kt`)**:
   - 10 standardized canned convoy hazard alerts (`FUEL_LOW`, `HAZARD_ROAD`, `STOP_IMMEDIATE`, `MECHANICAL_ISSUE`) and custom text payloads (<280 chars).
   - Peer-to-peer delivery ACK tracking with automatic receipt generation upon packet arrival.
   - Bounded sliding-window history (max 100 messages) preventing RAM leaks with reactive unread badge state flow.
2. **Decentralized GPS Mesh Telemetry & Convoy Tracking (`ConvoyGpsTracker.kt`)**:
   - Pure KMP Great-Circle Haversine distance, forward azimuth, and relative bearing ($-180^\circ \dots +180^\circ$) relative to local rider heading.
   - Motion-aware adaptive beaconing: stationary (15s), city (6s), highway cruise (3s) to preserve BLE airtime.
   - Sudden emergency deceleration watchdog (>35 km/h drop in <1.5s) triggering urgent priority broadcast.
   - Dynamic Lead Scout, Sweep Rider, and closest peer role assignment with 30s stale rider timeout pruning.
3. **Dynamic Adaptive Bitrate Controller (`AdaptiveBitrateController.kt`)**:
   - Closed-loop RF metric mapping evaluating composite `InterferenceLevel` and `LinkQuality`.
   - Asymmetric hysteresis: Instant step-down protects audio continuity upon RF degradation; 3-cycle conservative step-up prevents flapping.
   - Manual override mode with reactive `currentProfile: StateFlow<AudioProfile>`.
4. **Rolling Emergency Voice Blackbox & Encrypted Incident Cache (`EmergencyBlackboxRecorder.kt`)**:
   - 60-second rolling circular buffer (`ArrayDeque<BlackboxAudioFrame>`) storing compressed ADPCM audio frames.
   - Automatic snapshot freeze upon `EMERGENCY_OVERRIDE` preemption or crash detection with 10s cooldown suppression.
   - Pure KMP RFC 8439 ChaCha20-Poly1305 AEAD incident report encryption with associated data verification (`incidentId`).
   - Bounded memory footprint strictly maintained below 3 MB resident RAM (< 1.5 MB typical).
   - Human-readable Markdown forensic incident log export (`toMarkdownForensicSummary()`).
5. **Verification**:
   - `./gradlew test`: 100% passing across all modules.
   - `./gradlew :app:sharedLogic:compileKotlinIosSimulatorArm64`: Passed cleanly.
   - OpenCodeReview: 0 findings (`APPROVED`).
   - Merged into `develop` highway.

---

## 📅 2026-09-30: Sprint 10 — Multi-Channel Intercom, Priority Emergency & Audio Spatialization (Phase 8 - v2.0 Roadmap)

### Summary
1. **Multi-Channel Sub-Group Partitioning (`ChannelManager.kt`)**:
   - 16 configurable sub-channels with convoy presets (Convoy Main, Scouts & Lead, Sweep & Tail, Support, SOS).
   - Dual-Watch secondary monitoring and scan mode across all unmuted channels.
   - Global emergency broadcast bypass allowing SOS frames to penetrate any sub-channel mutes.
2. **Emergency Priority Audio & Alert Tone Synthesis (`PriorityBroadcastManager.kt`)**:
   - Preemption engine classifying traffic into `NORMAL`, `PRIORITY`, and `EMERGENCY_OVERRIDE`.
   - Incoming emergency bursts instantly override ongoing normal chatter with 600ms hangover guard.
   - Mathematical sine synthesis (`AlertChimeSynthesizer`) generating 48kHz 16-bit PCM hazard (880Hz) and emergency (880Hz->1760Hz) alert chimes in pure Kotlin.
3. **3D Binaural Stereo Spatializer (`SpatialAudioProcessor.kt`)**:
   - Transforms mono voice streams into 3D stereo audio panned by rider bearing (-90° to +90°).
   - Constant-power ILD law preserving overall perceived volume ($g_L^2 + g_R^2 \approx 1.0$).
   - Psychoacoustic ITD circular delay line delaying opposite ear by up to 31 samples (~0.65ms).
   - Distance attenuation curve with 0.35 gain safety floor ensuring clarity.
4. **Adaptive RF Interference Detection (`RfInterferenceDetector.kt`)**:
   - Sliding-window telemetry evaluating Packet Delivery Ratio (PDR) and RSSI standard deviation.
   - Classifies RF environments (`LOW`, `MODERATE`, `SEVERE`) and recommends dynamic packet redundancy scaling (1x -> 2x -> 3x), scan rate throttling, and randomized backoff windows.
5. **Verification**:
   - `./gradlew test`: 100% passing across all modules.
   - `./gradlew :app:sharedLogic:compileKotlinIosSimulatorArm64`: Passed cleanly.
   - OpenCodeReview: 0 findings (`APPROVED`).

---

## 📅 2026-09-30: Sprint 09 — Cellular Fallback & Cloud Relay Bridge (Phase 7 - v2.0 Roadmap)

### Summary
1. **Ktor WebSocket Cloud Relay Gateway (`CloudRelayBridge.kt`)**:
   - Cellular fallback bridge routing encrypted `MeshPacket` instances via Ktor WebSocket server (`:server`) using pure KMP Base64.
   - Multi-transport coordination (`BLE_ONLY`, `FALLBACK_ONLY`, `HYBRID_ALWAYS`).
   - Integrated with `PacketDeduplicator` to eliminate infinite bounce loops between BLE and cloud relays.
2. **Low-Power BLE Proximity Radar (`ProximityRadar.kt`)**:
   - Log-Distance Path Loss model distance estimation translating RSSI into `NEAR`, `MEDIUM`, `FAR`, and `OUT_OF_RANGE` zones.
   - Directional trend detection (approaching vs receding) and reactive UI telemetry.
3. **Dynamic Mesh Topology Auto-Healing (`MeshElectionManager.kt`)**:
   - Decentralized Weighted Bully consensus protocol electing optimal RF coordinator upon leader silence timeout (>15s).
   - Zero voice packet drops or audio disruption during re-election.
4. **Multiplatform CI/CD Matrix (`.github/workflows/ci.yml`)**:
   - Two-stage parallel pipeline automating test execution, iOS Simulator compilation, and Android release APK assembly.

---

## 📅 2026-09-29: Sprint 08 — Real-World Field Testing & Hardening (Phase 6 - v1.0 Release)

### Summary
1. **Field Range Benchmark & Mesh Diagnostics (`MeshDiagnostics.kt`)**:
   - Sliding-window telemetry tracking RSSI, sequence gap PDR, multi-hop relay distribution, and distance estimation.
   - Formal field range testing protocol in `docs/testing/field-reports/RangeBenchmark.md`.
2. **Helmet Wind Noise Filter & Formant EQ (`WindNoiseFilter.kt`)**:
   - 4th-order Butterworth High-Pass Filter attenuating wind turbulence (<300 Hz) by >35 dB.
   - 2.2 kHz parametric peaking EQ boosting speech formant articulation through visors.
3. **Battery Drain Profiling (`PowerProfiler.kt`)**:
   - Duty cycle telemetry demonstrating ~3.15% battery consumption over 4 hours with runaway WakeLock watchdog.
4. **v1.0 Production Readiness Sign-Off (`v1.0-Release-Readiness.md`)**:
   - Verified 100% Definition of Done criteria across all v1.0 sprints.
   - Production release tag `v1.0.0` pushed to `main`.

---

## 📅 2026-09-27: Sprint 07 — Audio Codec Optimization, Jitter Buffering & PLC (Phase 5)

### Summary
1. **Adaptive Jitter Buffer (`AdaptiveJitterBuffer.kt`)**:
   - RFC 3550 statistical inter-arrival jitter estimation with dynamic 40ms to 150ms delay and missing sequence PLC triggers.
2. **Packet Loss Concealment (`PacketLossConcealment.kt`)**:
   - Pitch period extrapolation, -3 dB exponential attenuation, and crossfade recovery.
3. **Adaptive Noise Gate (`NoiseGate.kt`)**:
   - Dual-threshold hysteresis (-35 dBFS open / -42 dBFS close) with 5ms attack, 100ms hold, and 50ms release.
4. **Pure KMP Audio Codec (`AudioCodec.kt`)**:
   - Zero-dependency 4-bit IMA ADPCM compression engine fitting 20ms frames inside single BLE GATT MTUs.
5. **Full Duplex Audio Pipeline (`AudioPipelineSession`)**:
   - Verified continuous playout under 20% packet drops and 50ms transit jitter.

---

## 📅 2026-09-27: Sprint 06 — Background Services & OS Power Management (Phase 4)

### Summary
1. **Android Foreground Service (`IntercomForegroundService.kt`)**:
   - Persistent notification, `connectedDevice|microphone` types, and Bluetooth SCO audio routing lock.
2. **Android Partial WakeLock & Optimization Exemption (`PowerManagerHelper.kt`)**:
   - Safe reference-counted WakeLock and battery optimization exemption prompt.
3. **iOS Background Audio & CoreBluetooth (`Info.plist` & `iOSApp.swift`)**:
   - `UIBackgroundModes` audio/bluetooth and `AVAudioSession` VoiceChat lifecycle.
4. **iOS Background Task Assertions (`BackgroundKeepAlive.kt`)**:
   - Reference-counted `UIApplication.beginBackgroundTaskWithName` assertions preventing watchdog termination.
5. **Screen-Off Continuity Verification (`BackgroundContinuityTest.kt`)**:
   - Verified zero packet drops across burst loads under screen-off simulation.

---

## 📅 2026-09-27: Sprint 05 — Multi-Hop Flooding Relay, Sliding-Window Deduplication & ChaCha20-Poly1305 Encryption

### Summary
1. **Sliding-Window Packet Deduplication (`PacketDeduplicator.kt`)**:
   - Implemented thread-safe sliding-window packet deduplication cache in `commonMain` with mutex protection, 5000ms expiration TTL, and 1000 max capacity.
   - Built composite deduplication keys: `audio:senderId:seq`, `ctrl:senderId:type:hash`, and `relay:origSender:innerKey`.
   - Prevents broadcast flooding storms across cyclic multi-peer BLE topologies.
2. **Multi-Hop TTL Flooding Relay & Inner Packet Delivery (`MeshTransport.android.kt` & `MeshTransport.ios.kt`)**:
   - Integrated `PacketDeduplicator` into both Android and iOS GATT mesh engines.
   - Unpacked incoming `MeshPacket.Relay` payloads, immediately delivering inner `Audio` and `Control` packets to local channels.
   - Decremented `ttl` from 3 down to 1 and re-broadcasted forwarded packets to all other connected peers without manual intervention.
3. **Pure Kotlin RFC 8439 ChaCha20-Poly1305 AEAD (`PacketCipher.kt`)**:
   - Implemented 100% pure Kotlin RFC 8439 ChaCha20 stream cipher, Poly1305 26-bit limb one-time authenticator, and FIPS 180-4 SHA-256 digest.
   - Zero native or platform-specific C/JNI dependencies: runs identically across Android, iOS Native, JVM, and Wasm/JS.
   - Verified byte-for-byte against the official RFC 8439 Section 2.8.2 test vector.
4. **Multi-Hop Simulation & Relay Unit Tests (`CrossPlatformGattMeshTest.kt` & `PacketCipherTest.kt`)**:
   - Added `SimulatedMeshNode` verifying 3-node topology (A ➔ B ➔ C) with TTL decrement (3 ➔ 2), local delivery at B, packet forwarding to C, broadcast loop suppression, and end-to-end encrypted PCM voice payload delivery.
5. **Verification**:
   - `./gradlew test`: 100% passing across all targets.
   - `./gradlew :app:androidApp:assembleDebug`: BUILD SUCCESSFUL in 8s.
   - `./gradlew :app:sharedLogic:compileKotlinIosSimulatorArm64`: BUILD SUCCESSFUL in 12s.
   - `graphify update .`: Knowledge graph updated to 1,179 nodes, 1,994 edges, 94 communities.

---

## 📅 2026-09-27: Standard Application Flow, Multiplatform Koin DI & Jetpack Navigation Compose

### Summary
1. **Multiplatform Dependency Injection (Koin 4.0.2)**:
   - Added `koin-core`, `koin-android`, `koin-compose`, and `koin-compose-viewmodel` across `sharedLogic`, `sharedUI`, and `androidApp`.
   - Verified seamless compatibility across Android, JVM, iOS Simulator, and native builds.
   - Created `sharedUiModule` in `app/sharedUI/src/commonMain/kotlin/org/nowni/intercom_alpha/di/AppModule.kt` declaring multiplatform ViewModels (`viewModelOf(::HomeViewModel)`).
2. **Android Application & Clean Activity Flow**:
   - Created `IntercomAlphaApplication : Application()` in `app/androidApp/src/main/kotlin/org/nowni/intercom_alpha/IntercomAlphaApplication.kt`.
   - Registered `android:name=".IntercomAlphaApplication"` in `AndroidManifest.xml`.
   - Refactored `MainActivity.kt` to the standard boilerplate calling `IntercomAlphaApp()`.
3. **Type-Safe Compose Multiplatform Navigation & Navigation 3**:
   - Upgraded to modern Navigation 3: added `org.jetbrains.androidx.navigation3:navigation3-ui:1.1.2` (`navigation3-ui`) and `org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10` with Kotlin Serialization.
   - Built `AppNavGraph.kt` with `@Serializable object HomeRoute` and Koin-injected ViewModels via `koinViewModel()`.
   - Verified metadata compilation and compatibility across Android, JVM, and Wasm/JS.
   - Wrapped `IntercomAlphaApp()` in `KoinContext` and `AppNavGraph()`.
4. **Verification**:
   - `./gradlew :app:sharedUI:compileCommonMainKotlinMetadata`: BUILD SUCCESSFUL.
   - `./gradlew :app:androidApp:assembleDebug`: BUILD SUCCESSFUL.
   - `./gradlew :app:sharedLogic:compileKotlinIosSimulatorArm64`: BUILD SUCCESSFUL.
   - `./gradlew test`: 100% passing across all multiplatform modules.
   - `graphify update .`: Knowledge graph refreshed to 1,124 nodes, 1,873 edges, 81 communities.

---

## 📅 2026-09-26: Sprint 04 — Swift ↔ KMP Bridge, QR Scanner & Vault Restructuring

### Summary
1. **Swift ↔ KMP Bridge (`IntercomBridge.kt`)**:
   - Implemented `IntercomBridge` in `app/sharedLogic/src/iosMain` providing clean, non-blocking Swift callback observers (`CancellationHandle`) for input/output audio levels, recording state, peers, connection state, headset route, and active group.
   - Piped incoming Mesh audio directly to `audioEngine.playAudio(shorts)` and recorded microphone audio to `meshTransport.sendAudio(...)`.
   - Wired `HeadsetManager` physical button events (PTT_PRESS / PTT_RELEASE) directly to transmission.
2. **SwiftUI ViewModel & Camera QR Scanner**:
   - Created `@MainActor class IntercomViewModel: ObservableObject` in `app/iosApp/iosApp/IntercomViewModel.swift`.
   - Implemented native `AVFoundation` camera-based QR code scanner (`QrScannerView.swift`) and dynamic QR share sheet (`QrShareView`) in `ContentView.swift`.
   - Connected PTT touch drag gestures, audio level meters, dynamic group status, and peer rows directly to live Kotlin/Native shared logic.
3. **Obsidian Vault 8-Pillar Restructuring**:
   - Reorganized `D:\ObsidianVault\Vault1\ObsidianVault\Android\Projects\Intercom-Alpha` from 21 cluttered folders into a clean, sequential `00_Inbox` to `08_Archive` structure.
   - Decoupled all 9 ADRs into MADR markdown files with standardized YAML frontmatter.
   - Created dynamic Dataview dashboards in `INDEX.md` and `Architecture-MOC.md`.
4. **Verification**:
   - `compileKotlinIosArm64` & `compileKotlinIosSimulatorArm64`: ALL PASSED.
   - `assembleDebug` Android APK: BUILD SUCCESSFUL in 8s.
   - `./gradlew test`: 100% passing across all modules.
   - `graphify update .`: Rebuilt with 1,075 nodes, 1,812 edges, 82 communities.

---

## 📅 2026-09-26: Phase 2 & Phase 3 Implementation (Android + iOS + Cross-Platform GATT)

### Summary
Completed the platform implementations for both Android and iOS in `app/sharedLogic`, established a unified single-source GATT mesh protocol, updated native SwiftUI UI in `app/iosApp`, and verified all unit tests across the monorepo.

### Key Milestones
1. **iOS Audio Engine (`AudioEngine.ios.kt`)**:
   - `AVAudioEngine` input tap with RMS volume metering and VOX energy thresholding.
   - `AVAudioPlayerNode` with dynamic buffer scheduling.
   - `AVAudioSession` Voice Chat category with Bluetooth SCO routing.
2. **iOS Transport (`MeshTransport.ios.kt`)**:
   - CoreBluetooth GATT Server (`CBPeripheralManager`) & Client (`CBCentralManager`).
   - `MultipeerConnectivity` (`MCSession`) peer-to-peer Wi-Fi/Bluetooth bridge.
3. **iOS Headset Manager (`HeadsetManager.ios.kt`)**:
   - `AVAudioSession` route changes and `MPRemoteCommandCenter` media button listener.
4. **Native SwiftUI UI (`ContentView.swift`)**:
   - Complied with "Do not share UI - Use SwiftUI" directive.
   - Built interactive PTT button, live audio meters, peer list, and route status.
5. **Cross-Platform GATT Mesh Test Suite (`CrossPlatformGattMeshTest.kt`)**:
   - Validated JSON serialization symmetry for `Audio`, `Control`, `Discovery`, and `Relay` packets.
   - Verified QR invite code generator/parser.
6. **Build & Knowledge Verification**:
   - `compileIosMainKotlinMetadata`, `compileKotlinIosArm64`, `compileKotlinIosSimulatorArm64`: ALL PASSED.
   - `assembleDebug` Android APK: BUILD SUCCESSFUL.
   - Unit tests: 100% passed across all targets.
   - Knowledge Graph: Rebuilt with 967 nodes, 1,606 edges, and exported to Obsidian canvas.
   - MemPalace: Mined 1,391 new drawers into `intercom_alpha` wing (total: 2,426 drawers).

---

## 📅 2026-09-26: Phase 1 Core Networking & Signaling
- Built polymorphic protocol in `:core` (`SignalingProtocol.kt`).
- Built Ktor WebSocket Server in `:server` (`/ws/intercom`, `/api/status`).
- Built KMP Signaling Client in `app/sharedLogic` (`IntercomSignalingClient.kt`).
- Retained `:core` and `:server` per ADR-009 for cloud audio relay & signaling fallback.
