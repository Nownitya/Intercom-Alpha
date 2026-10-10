# Changelog â€” Intercom-Alpha

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.13.0] - 2026-10-10 — Sprint 26: Long-Haul Fleet Hardening & Low-Bandwidth Visual Telemetry

### Added
- **Tactical Snapshot Mesh Transport Engine (`SnapshotModels.kt`, `TacticalSnapshotProtocol.kt`, `SnapshotTransferManager.kt`)**: Pure KMP progressive transmission of compressed 0.5 fps thumbnail snapshots (<= 1.5 KB payload) over BLE mesh. MTU-bounded chunking (<= 128 bytes) with dual Adler-32 integrity validation and selective out-of-order NACK reassembly over lossy multi-hop channels.
- **Concurrent Multi-Radio Arbiter (`RadioModels.kt`, `MultiRadioArbiter.kt`)**: Dynamically routes high-bandwidth bulk data (tactical snapshots, trail GPX chunks) over Wi-Fi Direct (`P2P_CLUSTER`) when direct proximity is established, while strictly isolating low-latency voice and emergency traffic on BLE mesh, eliminating channel contention.
- **Fleet-Wide Over-The-Air Cryptographic Group Rekeying Protocol (`RekeyModels.kt`, `GroupRekeyProtocol.kt`)**: Forward-secret epoch key derivation via RFC 5869 HKDF-SHA256, dual-epoch key buffer (`CurrentEpoch`, `PreviousEpoch`) with a 10-second transition grace period eliminating dropped packets during key rotation, anti-replay counter protection, HMAC authentication, and memory zeroization.
- **8-Hour Expedition Continuous Soak Benchmark & Architecture Showcase (`ExpeditionSoakSimulationHarness.kt`, `intercom-alpha.architecture.json`, `intercom-alpha-architecture.html`)**: Simulated 20-node expedition convoy across 28,800,000 virtual ms with dynamic topology shifts, periodic voice bursts, snapshot transfers, battery governor transitions, zero memory/coroutine leaks, and updated interactive Archify showcase with 9/9 verification checks passed.

---

## [2.12.0] - 2026-10-10 — Sprint 25: Automated Multi-Path Mesh Failover & Hybrid Cellular/SFU Relay Handover

### Added
- **Pure KMP Multi-Path Link Quality Arbiter & Automated Failover State Machine (`FailoverModels.kt`, `MultiPathFailoverArbiter.kt`, `MultiPathFailoverArbiterTest.kt`)**: Implemented moving-average link scoring evaluating smoothed RSSI, sliding packet loss, and jitter variance into a unified $Q_{link} \in [0, 100]$ metric. Automated state machine dynamically manages transitions across `BLE_PRIMARY`, `DEGRADED_HEDGING`, `CELLULAR_ACTIVE`, and `RECOVERING` with zero Java or C dependencies (Rules 2 & 3).
- **Transport Handover Wire Integration & Seamless Voice Stream Hedging (`CloudRelayBridge.kt`, `HybridTransportRouter.kt`, `HybridTransportRouterTest.kt`)**: Automated dual-stream packet hedging concurrently dispatching sequence-stamped audio frames across BLE GATT mesh and Ktor WebSocket SFU relays during RF degradation boundaries. Receiving nodes utilize `PacketDeduplicator` to play the first arriving frame and drop duplicates with zero audio loss.
- **Subsystem Hardware Bridge Wiring & Multiplatform Tactical Handover HUD (`IntercomHardwareBridge.kt`, `AndroidPlatformBridge.kt`, `IntercomBridge.kt`, `HomeScreen.kt`, `ContentView.swift`)**: Exposes reactive `TransportFailoverState` with Swift-friendly `CancellationHandle` callbacks. Jetpack Compose and 100% Native SwiftUI display active link quality scores, hedging badges, and cellular handover banners honoring Rule 1.
- **Highway Convoy Out-of-Range Handover Simulation Harness (`MultiPathHandoverSimulationHarness.kt`, `intercom-alpha.architecture.json`)**: Simulated 16-node high-speed convoy splitting across highway interchanges with RF loss degradation. Verified 100% audio continuity, zero dropped frames during handover, and correct recovery hysteresis.

---

## [2.11.0] - 2026-10-09 — Sprint 24: Mesh Airtime QoS Traffic Shaping & Airtime Contention Mitigator

### Added
- **Dynamic Airtime QoS Token-Bucket Traffic Shaper (`AirtimeQoSTrafficShaper.kt`, `AirtimeQoSModels.kt`)**: Pure KMP token-bucket bandwidth regulator and packet classifier categorizing traffic into 4 strict tiers (`Priority 0: EMERGENCY`, `Priority 1: VOICE`, `Priority 2: NETWORK_CONTROL`, `Priority 3: TELEMETRY`).
- **Strict Priority Queue & Preemption Arbiter (`PriorityBroadcastManager.kt`)**: High-priority preemption engine ensuring life-safety eCall crash impact alerts and emergency voice broadcasts immediately preempt lower-priority airtime and bypass rate limits.
- **Weighted Fair Queuing (WFQ) Scheduler (`AirtimeQoSScheduler.kt`)**: Enforces fair bandwidth sharing (60/40 ratio) between network routing frames and background GPS telemetry when voice channels are idle.
- **Airtime Saturation Stress Simulation & Zero-Drop Benchmark (`AirtimeQoSTest.kt`)**: Benchmark verifying zero emergency drops and consistent voice playout under 100% mesh channel saturation.

---
## [2.10.0] - 2026-10-09 â€” Sprint 23: Multi-Group Inter-Convoy Gateway & Cross-Mesh Federation

### Added
- **Pure KMP Multi-Group Gateway Engine & Cross-Mesh Federation Protocol (`FederationModels.kt`, `InterGroupGateway.kt`, `CrossMeshRelayEngine.kt`)**: Defines inter-group gateway contracts (`GroupFederationInvite`, `FederationBridgeState`, `CrossGroupPolicy`, `CrossMeshAudioPacket`). Implements gateway leader arbitration, policy-driven audio and hazard bridging between distinct cryptographic groups (Squad A vs Squad B) with key translation and destination group re-encryption, preventing unauthorized spillover while enabling seamless intercom between road captains with zero Java/C dependencies (Rules 2 & 3).
- **Selective Inter-Group Audio Demuxing, Sub-Group Isolation & Anti-Feedback Loop Guard (`FederationLoopDetector.kt`, `CrossMeshForwardingArbiter.kt`)**: Integrates cross-mesh audio bridging into `MeshTransport` and audio pipeline. Prevents circular acoustic and relay feedback loops across redundant gateway bridges via monotonic `federationPathTrace` and cross-group deduplication. Enforces sub-group isolation (`CAPTAIN_ONLY`, `EMERGENCY_SOS_ONLY`, and `ALL_MEMBER_BRIDGED`).
- **Subsystem Hardware Bridge Wiring & Multiplatform Federation HUD Presentation (`IntercomHardwareBridge.kt`, `AndroidPlatformBridge.kt`, `IntercomBridge.kt`, `HomeScreen.kt`, `ContentView.swift`)**: Wired `InterGroupGateway` into hardware bridges with typed `CancellationHandle` observers. Exposes reactive `FederationSnapshot` (connected sibling groups, bridge policy, gateway link RSSI, relayed packet counts) and gateway control hooks. Presents Multi-Group Federation badge and diagnostics sheet in Compose Multiplatform (`HomeScreen.kt`) and 100% Native iOS SwiftUI (`ContentView.swift` / `IntercomViewModel.swift`) strictly respecting Rule 1.
- **Dual-Convoy 32-Node Cross-Mesh Simulation Harness & Archify Showcase (`MultiGroupFederationSimulationHarness.kt`, `intercom-alpha.architecture.json`)**: Simulated dual-squad convoy (Squad Alpha 16 nodes, Squad Bravo 16 nodes) with 2 dual-role gateway nodes. Validates sub-group isolation, <25ms gateway re-encryption latency, zero broadcast loops across redundant gateways, and emergency all-call preemption across both meshes. Updated Archify interactive architecture specifications.

---

## [2.9.0] - 2026-10-08 â€” Sprint 22: Dynamic Mesh Directed Routing, AODV-Lite Route Optimization & RF Collision Reduction

### Added
- **Pure KMP AODV-Lite Directed Routing Engine (`RoutingModels.kt`, `RoutingTable.kt`, `AodvRoutingEngine.kt`)**: Implemented on-demand route discovery (`RREQ`/`RREP`/`RERR`) with monotonic sequence numbers, link delivery ratio & RSSI-based ETX metrics, 3-failure link break circuit breaker, and route error self-healing with zero Java or C dependencies (Rules 2 & 3).
- **Selective Unicast/Multicast Forwarding & RF Airtime Contention Mitigator (`LinkMetricCalculator.kt`, `MeshTransport.kt`)**: Directed forwarding reducing RF airtime contention by >80% in 24-node convoys compared to raw flooding.
- **Multiplatform Routing Topology HUD Presentation (`HomeScreen.kt`, `ContentView.swift`)**: Tactical routing metric pills and route hop visualization across Compose and 100% Native SwiftUI.
- **24-Node Large-Scale Directed Mesh RF Contention Benchmark (`DirectedMeshSimulationHarness.kt`)**: Automated benchmark verifying route convergence, loop freedom, and high-density airtime conservation.

---

## [2.8.0] - 2026-10-07 â€” Sprint 21: Autonomous Crash Impact Detection, eCall SOS & Offline Voice Command HUD

### Added
- **Pure KMP Crash Detection & Fall-Over IMU State Machine (`SensorModels.kt`, `CrashDetector.kt`)**: Multi-axis IMU collision shock (8.5g severe impact, 4.5g hard collision, >60Â° sustained fall-over) with 30s abortable countdown.
- **Pure KMP Offline Voice Command & Keyword Spotting DSP (`VoiceCommandModels.kt`, `VoiceCommandRecognizer.kt`)**: Acoustic DSP keyword spotter ("Intercom SOS", "Next Channel", "Prev Channel", "Toggle Mute", "Battery Status", "Cancel Alert") under 80â€“120 km/h wind noise.
- **Subsystem Platform Hardware Sensor Bridge Wiring & Emergency HUD (`HomeScreen.kt`, `ContentView.swift`)**: Real-time crash pre-alert countdown banner with 1-tap abort actuator across Compose and 100% Native SwiftUI.
- **Crash Impact & Voice Command Simulation Harness (`CrashAndVoiceSimulationHarness.kt`)**: 5,000-sample streaming simulation verifying zero false positives during aggressive cornering and rough terrain.

---

## [2.7.0] - 2026-10-07 â€” Sprint 20: Tactical Convoy Geofencing, Autonomous Power Governor & Multi-Node Stress Benchmark Suite

### Added
- **Pure KMP Convoy Geofencing, Hazard Waypoint Radar Alarms & Convoy Split Engine (`ConvoyGeofenceModels.kt`, `ConvoyGeofenceEngine.kt`)**: Great-Circle Haversine tether monitoring (warn at >300m, breach alarm at >600m from lead rider), hazard obstacle proximity radar (e.g. 100m proximity alarm), single-linkage BFS connected-component clustering for convoy partition/split detection across rider clusters with inter-cluster gap calculation, and automatic control alarm emission with zero Java or C dependencies (Rules 2 & 3).
- **Autonomous Power Governor & Motion Watchdog (`PowerStateModels.kt`, `BatteryGovernor.kt`)**: Multi-tier radio duty-cycle governor (NORMAL 100%, CONSERVATIVE 50% at <30% battery, EXTREME_SURVIVAL 20% at <15% battery), rest-stop stillness watchdog (>10 min at <= 3.0 km/h) shifting to 5% duty-cycle sleep, instant wake on motion resumption (>= 5.0 km/h) or PTT actuation, and unconditional Emergency SOS preemption restoring full 100% duty cycle and maximum audio bandwidth.
- **Subsystem Hardware Bridge Wiring & Multiplatform Presentation HUD Integration (`IntercomHardwareBridge.kt`, `AndroidPlatformBridge.kt`, `IntercomBridge.kt`, `HomeViewModel.kt`, `HomeScreen.kt`, `ContentView.swift`)**: Wired subsystems to hardware bridges and reactive ViewModels with Swift-friendly `CancellationHandle` closures. Compose Multiplatform HUD renders dynamic outer tether boundary ring, obstacle hazard markers, `TETHER` / `PWR` badges, and high-visibility `GeofenceBreachBanner`. 100% Native iOS SwiftUI renders tether boundary ring in `TacticalVectorRadarView`, battery governor power mode pill in `TacticalAvionicsBar`, and native tether breach warning banner complying strictly with Rule 1.
- **16-Node Concurrent Convoy Stress Simulation Harness & Archify Showcase (`ConvoyStressSimulationHarness.kt`, `intercom-alpha.architecture.json`)**: Simulated 16 concurrent virtual nodes in linear formations spanning >3 km with 10% RF loss, out-of-order jitter, and hop-distance limits. Verified 8-hop linear packet forwarding with 100% voice decryption fidelity, concurrent OTA route chunk distribution, geofence split alarms, and autonomous battery governor power scaling. Verified memory stability (<80 MB RAM) and zero coroutine leaks. Synchronized Archify architecture specification and delivered showcase HTML with 9/9 checks passed and 0 errors.

---

## [2.6.0] - 2026-10-07 â€” Sprint 19: Over-The-Air BLE Mesh GPX/GeoJSON Trail Sharing & Storage Manager

### Added
- **Pure KMP Chunked Mesh File Transfer & GPX/GeoJSON Protocol Engine (`MeshFileModels.kt`, `MeshFileTransferEngine.kt`, `MeshFileTransferManager.kt`)**: Implemented pure KMP RFC 1950 Adler-32 checksum algorithm (modulo 65521), MTU chunking engine (<=100 bytes payload) guaranteeing zero packet fragmentation over BLE GATT characteristics, out-of-order `ReassemblyBuffer`, sliding-window queue with selective NACK retransmission over lossy RF hops, and pure KMP GPX 1.1 / RFC 7946 GeoJSON parsers and exporters with zero Java (`java.util.zip`) or external C/JNI dependencies (Rules 2 & 3).
- **Subsystem Hardware Bridge Wiring & Reactive State Management (`IntercomHardwareBridge.kt`, `AndroidPlatformBridge.kt`, `IntercomBridge.kt`, `HomeViewModel.kt`, `AppModule.kt`)**: Injected `MeshFileTransferManager` into shared Koin DI graph, platform foreground service, and native iOS bridge. Connected wire chunk serialization into `ControlType.FILE_CHUNK` (0x0B) and `ControlType.FILE_ACK` (0x0C) control packets. Exposed `StateFlow<List<FileTransferProgress>>` and `StateFlow<List<TrailPolyline>>` with typed `CancellationHandle` observers (Rule 1). Connected `completedTrailReceived` to automatically load newly completed route downloads into `TrailMapManager` for instant vector radar cockpit HUD projection.
- **Compose Multiplatform Trail File Manager & Over-The-Air Beam Cockpit Sheet (`TrailFileManagerSheet.kt`, `OfflineTrailCockpitSheet.kt`, `HomeScreen.kt`)**: Built tactical modal dialog displaying stored routes catalog with difficulty badges (EASY, MODERATE, HARD, EXPERT), distance, elevation ascent, and active route selection. Added live OTA transmission cards with progress bars, percent complete, chunk counters (`X / Y chunks`), direction badges (`OUTGOING` / `INCOMING`), format toggle (`GPX` / `GEOJSON`), 1-tap "âš¡ BEAM TO CONVOY" actuator, and raw route text import dialog. Added "âš¡ OTA ROUTES" quick launcher in the offline trail cockpit header and top quick action row honoring the zero-scroll handlebar ergonomic contract.
- **100% Native iOS SwiftUI Trail File Manager & Over-The-Air Beam Sheet (`TrailFileManagerSheet.swift`, `OfflineTrailCockpitSheet.swift`, `IntercomViewModel.swift`, `ContentView.swift`)**: Built pure SwiftUI implementation adhering strictly to Rule 1 (zero Compose Multiplatform imports on iOS). Features high-contrast dark cockpit aesthetic, active route overview card, format selector, 1-tap beam to convoy with haptic feedback (`UIImpactFeedbackGenerator`), live chunk progress gauges, stored routes library with difficulty badges and active selection checks, raw GPX/GeoJSON text import modal, and seamless integration with `TacticalAvionicsBar` and `OfflineTrailCockpitSheet`.

---

## [2.5.0] - 2026-10-07 â€” Sprint 18: Offline Vector Terrain & Trail Map Overlay

### Added
- **Pure KMP Offline Vector Trail & Terrain Geometry Engine (`VectorMapModels.kt`, `TrailMapEngine.kt`, `TrailMapManager.kt`)**: Implemented immutable data models (`GeoPoint`, `TrailWaypoint`, `TrailPolyline`, `ContourLine`, `ProjectedRadarPoint`, `RadarRangeZoom`, `TrailMapSnapshot`), Great-Circle Haversine distance, forward azimuth, relative vehicle bearing normalization ($-180^\circ..+180^\circ$), polar-to-Cartesian normalized radar projection, GeoJSON parser, and procedural alpine trail generator with zero Java or external map SDK dependencies (Rules 2 & 3).
- **Subsystem Bridge Wiring & Reactive Presentation Integration (`IntercomHardwareBridge.kt`, `AndroidPlatformBridge.kt`, `IntercomBridge.kt`, `HomeViewModel.kt`, `AppModule.kt`)**: Injected `TrailMapManager` into Koin DI, hardware bridges, and platform engines. Exposed `StateFlow<TrailMapSnapshot>` and Swift-friendly `observeTrailMapSnapshot` callback returning `CancellationHandle` (Rule 1). Automatically streams local Android/iOS GPS coordinates and incoming over-the-air peer GPS telemetry frames directly to `TrailMapManager` for real-time relative radar projection.
- **Compose Multiplatform Radar Topo Contour & Vector Trail Overlay (`ProximityRadarView.kt`, `OfflineTrailCockpitSheet.kt`, `HomeScreen.kt`)**: Upgraded circular proximity radar to render topographic elevation contours with subtle dashed strokes, neon cyan vector trail tracks, and rider breadcrumb dots. Added interactive radar range zoom button (`500m` / `1.5km` / `5.0km`), quick "Trail" cockpit launcher button in top avionics header, and tactical `OfflineTrailCockpitSheet` modal dialog displaying elevation metrics, ascent, distance, and nearest waypoint navigation without violating the handlebar zero-scroll layout constraint.
- **100% Native iOS SwiftUI Radar Topographic Overlay & Offline Trail Cockpit Sheet (`ContentView.swift`, `OfflineTrailCockpitSheet.swift`, `IntercomViewModel.swift`)**: Built pure SwiftUI implementation adhering to Rule 1 with zero Compose imports. Draws vector trail paths and contour arcs using SwiftUI `Path` on `TacticalVectorRadarView`, plots rider breadcrumbs and tactical waypoint pins, provides tactile radar range switcher badge, and presents full-featured `OfflineTrailCockpitSheet` modal with nearest waypoint guidance and waypoint roster.

---

## [2.4.0] - 2026-10-06 â€” Sprint 17: Basecamp Stationary Repeater UI & Dedicated Low-Power Cockpit Mode

### Added
- **Basecamp Stationary Repeater State Management & Platform Bridge Wiring (`MeshRepeaterService.kt`, `RepeaterViewModel.kt`, `IntercomHardwareBridge.kt`, `AndroidPlatformBridge.kt`, `IntercomBridge.kt`)**: Decoupled microphone and speaker pipelines during repeater operation to dedicate 100% of airtime and battery to relaying. Added duty cycle switcher (`CONTINUOUS`, `POWER_SAVER`, `EXTREME_SURVIVAL`), companion ping/pong helpers, and Swift-friendly `CancellationHandle` observers.
- **Compose Multiplatform Basecamp Repeater Screen (`BasecampRepeaterScreen.kt`)**: Dedicated tactical stationary cockpit screen with pure OLED `#000000` power-saving theme, animated RF relay wave, solar/battery telemetry gauges, active neighbor roster, and full-screen `NightStandbyScreen` lock mode with tap-to-wake gesture. Registered in Navigation 3 `AppNavGraph.kt`.
- **100% Native iOS SwiftUI Basecamp Repeater Cockpit (`BasecampRepeaterView.swift`)**: Built pure SwiftUI view complying with Rule 1 with zero Compose imports. Implemented pure OLED black background, responsive landscape cockpit adaptation (`LandscapeStandbyCockpitView`) for iOS 17+ StandBy tent/ridge docks, ultra-dim `NightStandbyOverlayView`, live RF telemetry grid, and diagnostic link ping actuators.
- **Remote Repeater Telemetry & Diagnostic Ping Cockpit HUD Integration (`HomeScreen.kt` & `ContentView.swift`)**: Embedded compact Basecamp Beacon telemetry strip in top avionics header displaying heard repeater name, battery %, solar status, round-trip ping latency, and 1-tap diagnostic ping button directly from the mobile HUD without breaking the single-viewport zero-scroll contract.

---

## [2.3.0] - 2026-10-06 â€” Sprint 16: Mesh Repeater, Audio DSP & Convoy Simulation

### Added
- **Pure KMP Automatic Gain Control & Dynamic Range Compression (`AutomaticGainControl.kt` & `DynamicRangeCompressor.kt`)**: Normalizes speech levels to $-18.0\text{ dBFS}$ target within $150\text{ ms}$ with anti-pumping protection below $-50\text{ dBFS}$. Implements soft-knee $4:1$ dynamic range compression and lookahead brickwall peak limiting with guaranteed peak ceiling $\le -0.5\text{ dBFS}$ without Java/C dependencies.
- **Autonomous Stationary Basecamp Mesh Repeater (`MeshRepeaterService.kt`)**: Implemented dedicated low-power packet relay mode bypassing local audio engines, delivering sub-$15\text{ ms}$ multi-hop forwarding, sliding-window deduplication loop suppression, 60s active neighbor table tracking, and periodic `REPEATER_BEACON` telemetry broadcasts.
- **Automated Multi-Node Convoy Mesh Simulation Harness (`ConvoyMeshSimulationHarness.kt`)**: Automated 8-node virtual convoy simulation with distance-bounded RF path loss, $25\%$ stochastic packet drop, and shuffled out-of-order delivery, verifying $100\%$ `MeshKeyRatchet` decryption recovery and `AdaptiveJitterBuffer` playout under 5 seconds.
- **Over-The-Air Dynamic Epoch Migration & Group Re-Keying (`GroupEpochManager.kt`)**: Implemented decentralized key rotation via RFC 5869 HKDF-SHA256, leader-authenticated `EPOCH_ROLLOVER` signed with RFC 2104 HMAC-SHA256, dual-epoch concurrent decryption across a $30\text{-second}$ window with zero packet drops during active speech, and memory zeroization of expired keys.

---

## [2.2.0] - 2026-10-06 â€” Sprint 15: Zero-Scroll Tactical Cockpit HUD & Telemetry Integration

### Added
- **Zero-Scroll Tactical Cockpit HUD (`HomeScreen.kt` & `ContentView.swift`)**: Enforced a zero-vertical-scroll layout contract on mobile handlebar RAM mounts across both Compose Multiplatform (`sharedUI`) and 100% Native iOS SwiftUI (`iosApp`). Top avionics bar, channel dial, vector radar, 140dp PTT, hazard macros, and emergency SOS bar remain permanently visible on mobile screens ($H \le 844$dp).
- **140dp Circular Tactile PTT Actuator (`PttButton.kt` & `TacticalPttButton`)**: High-contrast circular tactile actuator with hot-mic pulsing crimson wave, haptic feedback on touch-down and touch-up, and flanking 58dp touch targets for speaker volume cycling (30% $\to$ 60% $\to$ 100%) and safety VOX locking.
- **Circular Tactical Vector Radar (`ProximityRadarView.kt` & `TacticalVectorRadarView`)**: Circular radar with concentric range rings (100m, 500m, 1.5km), crosshairs, north heading chevron, and dynamically plotted color-coded rider dots (Green < 100m, Cyan < 500m, Amber $\ge$ 500m) with live telemetry callout.
- **Single-Row 4-Column Hazard Macro Strip (`QuickHazardBar.kt` & `TacticalQuickHazardBar`)**: Glove-optimized macro buttons (`âš ï¸ HAZARD`, `â›½ FUEL`, `ðŸ›‘ STOP`, `ðŸ”§ BIKE`) with $\ge 52$dp touch targets and immediate mesh text transmission.
- **Sprint 14 Hardware Telemetry End-to-End Integration**: Wired `HelmetAccessoryManager` (Cardo/Sena Bluetooth GATT battery level and remote PTT click/press events), `MeshKeyRatchet` (Perfect Forward Secrecy session indicator), and `CloudRelayBridge` (multi-cluster cross-mesh SFU indicator) into `HomeViewModel`, `AndroidPlatformBridge`, and native iOS `IntercomBridge` / `IntercomViewModel`.

---

## [2.1.0] - 2026-10-04 â€” Sprint 14: Advanced Convoy Mesh, Forward-Secret Key Ratchet & Live Activity

### Added
- **Pure KMP Perfect Forward Secrecy (PFS) & Ephemeral Key Ratchet (`MeshKeyRatchet.kt` & `PacketCipher.kt`)**: Implemented RFC 2104 HMAC-SHA256 and RFC 5869 HKDF-SHA256 in 100% pure Kotlin Multiplatform (Rule 3). Implemented rolling symmetric KDF chain ratcheting deriving ephemeral keys that immediately zeroize previous secrets (`.fill(0)`), combined with a bounded skipped key cache for disordered mesh packet decryption and replay defense.
- **Direct Helmet Intercom Bluetooth GATT Accessory Profile (`HelmetAccessoryManager.kt`)**: Implemented specialized BLE GATT client profile with parsers for Cardo PackTalk, Sena 50S/RC4, EJEAS EUC, and generic BLE HID handlebar remotes. Integrated SIG Battery telemetry, button press/release opcodes, 60-second runaway PTT safety watchdog, and audio route preference arbitration (`BLUETOOTH_SCO`, `A2DP`, `BUILTIN_SPEAKER`).
- **iOS 18+ Live Activity & Dynamic Island Hands-Free PTT Widget (`IntercomActivityWidget.swift` & `IntercomActivityAttributes.swift`)**: Built 100% native SwiftUI / ActivityKit cockpit widget (Rule 1) with Compact, Minimal, and Expanded Dynamic Island layouts, live radar bearing compass indicators, large hands-on PTT touch trigger, and StandBy/Lock Screen active audio controls tied to `IntercomViewModel`.
- **Multi-Cluster Cloud Cross-Mesh SFU Signaling & Audio Bridging (`SignalingProtocol.kt`, `SignalingServerManager.kt`, `CloudRelayBridge.kt`)**: Added polymorphic `SignalingMessage.ClusterBridge` message with `#type` discriminator. Supported transcode-free routing of 4-bit ADPCM packets across rooms and clusters with zero latency penalty, bidirectional server room bridging, loop prevention via hop limit and path traversal inspection, multi-cluster peer presence aggregation, and adaptive jitter buffer smoothing.

---

## [2.0.0] - 2026-10-04 â€” Sprint 13: v2.0 Production Release & Hardening

### Added
- **Android Platform Hardware-to-Cockpit Service Bridge (`AndroidPlatformBridge.kt` & `IntercomForegroundService.kt`)**: Wired `HomeViewModel` in `sharedUI` to the live Android `IntercomForegroundService`, connecting physical PTT, live BLE mesh peer discovery, real-time microphone/speaker RMS audio levels, and OTA control packets (canned hazard text, convoy GPS telemetry, emergency SOS).
- **Multiplatform Production Release Build & ProGuard Hardening (`build.gradle.kts` & `proguard-rules.pro`)**: Configured production versioning (`versionCode = 2`, `versionName = "2.0.0"` in `libs.versions.toml`). Enabled aggressive R8 minification and resource shrinking resulting in an ultra-lean 3.17 MB production APK. Hardened preservation rules across all pure KMP `@Serializable` models, Koin DI bindings, and v2.0 subsystems.
- **Battery, WakeLock & Memory Profiling Gate (`PowerProfilerTest.kt` & `PowerManagerHelper.kt`)**: Implemented reference-counted PTT WakeLock with 60-second runaway watchdog protection. Validated strict WakeLock balance between PTT and standby states, clean Bluetooth SCO release upon service termination, and confirmed LZ77 compressed blackbox memory remains bounded < 1 MB (< 505 KB buffer alone, < 850 KB with active incident report; well under 3 MB threshold). Verified < 3.2% 4-hour battery consumption.
- **v2.0 Architecture Sync & Release Tagging**: 100% Definition of Done (DoD) achieved across all 13 sprints (Phase 1 through Phase 11). Synchronized knowledge graph and architecture documentation for official production release.

---

## [1.4.0] - 2026-10-01 â€” Sprint 12: Multiplatform Cockpit UI & Bridge Integration

### Added
- **KMP Swift Bridge Extension for v2.0 Subsystems (`IntercomBridge.kt`)**: Exposed reactive observer factory functions with memory-safe `CancellationHandle` callbacks for 16-channel dial, proximity radar, convoy GPS telemetry, mesh text, emergency broadcast, encrypted blackbox recorder, and adaptive bitrate.
- **Native iOS SwiftUI Cockpit v2.0 (`ContentView.swift` & `IntercomViewModel.swift`)**: 100% native SwiftUI motorcycle cockpit (Rule 1) featuring `ChannelDialView`, `ConvoyRadarStripView` with relative bearing indicators (-180Â° to +180Â°), `QuickHazardBar` with haptic feedback, and `EmergencySosBanner`.
- **Compose Multiplatform Cockpit Components (`sharedUI`)**: Enriched `HomeUiState` and `HomeViewModel` orchestrating all shared logic managers. Implemented `ChannelSelectorBar` (CH 1â€“16, Dual-Watch, Scan), `ProximityRadarView` (bearing compass vector, proximity zone tags), `QuickHazardBar` (canned alert buttons, unread badge), and `EmergencyPreemptionBanner`.
- **Multiplatform End-to-End Presentation Integration Tests**: Comprehensive multiplatform contract test suites (`IntercomBridgeContractTest.kt` in `sharedLogic/commonTest` and `HomeViewModelTest.kt` in `sharedUI/commonTest`) verifying channel switching, dual-watch, scan mode, canned hazard dispatch, custom messaging, emergency preemption, and PTT state transitions.
- **Pure KMP Bounded Memory Blackbox (`MiniLz` in `EmergencyBlackboxRecorder.kt`)**: Integrated lightweight pure Kotlin LZ77 compression for incident snapshots, bounding total resident RAM strictly under 1 MB (< 3 MB threshold).

---

## [1.3.0] - 2026-10-01 â€” Sprint 11: Convoy Situational Awareness, GPS Mesh Telemetry & Blackbox Voice Cache

### Added
- **Offline Mesh Canned Hazard & Quick Text Messaging (`MeshTextManager.kt`)**: Standardized canned convoy hazard alerts (`FUEL_LOW`, `HAZARD_ROAD`, `STOP_IMMEDIATE`), custom text messages (<280 chars), peer-to-peer delivery ACK tracking, and reactive unread badge state.
- **Decentralized GPS Mesh Telemetry & Convoy Tracking (`ConvoyGpsTracker.kt`)**: Great-Circle Haversine distance, forward azimuth, and relative bearing ($-180^\circ \dots +180^\circ$) in pure KMP. Motion-aware adaptive beaconing (3s to 15s) and sudden crash deceleration watchdog (>35 km/h drop in <1.5s).
- **Dynamic Adaptive Bitrate Controller (`AdaptiveBitrateController.kt`)**: Closed-loop RF metric mapping dynamically shifting `AudioProfile` (`ULTRA_LOW`, `LOW`, `MEDIUM`, `HIGH`) with asymmetric hysteresis (instant step-down, 3-cycle conservative step-up) and manual override.
- **Rolling Emergency Voice Blackbox & Encrypted Incident Cache (`EmergencyBlackboxRecorder.kt`)**: Circular 60-second in-memory audio ring buffer, automatic incident snapshot freeze on crash or emergency voice preemption, pure KMP ChaCha20-Poly1305 AEAD encryption, bounded < 3 MB RAM footprint, and Markdown forensic logs.

---

## [1.2.0] - 2026-09-30 â€” Sprint 10: Multi-Channel Intercom, Priority Emergency & Audio Spatialization

### Added
- **Multi-Channel Sub-Group Partitioning (`ChannelManager.kt`)**: 16 configurable sub-channels with convoy presets, Dual-Watch secondary channel monitoring, scan mode, and automatic audio frame segregation.
- **Emergency Priority Audio & Chime Synthesis (`PriorityBroadcastManager.kt`)**: Preemptive override engine where emergency frames bypass sub-channel mutes and suppress normal traffic. Pure multiplatform 48kHz alert chime synthesis (`AlertChimeSynthesizer`).
- **3D Binaural Stereo Spatializer (`SpatialAudioProcessor.kt`)**: Panning of mono voice streams across 3D helmet soundstage using constant-power ILD, psychoacoustic ITD circular delay line, and distance attenuation curve.
- **Adaptive RF Interference Detector (`RfInterferenceDetector.kt`)**: Sliding-window 2.4 GHz channel congestion monitoring tracking PDR and RSSI flutter, advising dynamic packet redundancy scaling and scan backoff.

---

## [1.1.0] - 2026-09-30 â€” Sprint 09: Cellular Fallback & Cloud Relay Bridge

### Added
- **Ktor WebSocket Cloud Relay Gateway (`CloudRelayBridge.kt`)**: Cellular data bridging fallback connecting local BLE mesh to Ktor WebSocket server (`:server`) via polymorphic Base64 message routing.
- **Low-Power BLE Proximity Radar (`ProximityRadar.kt`)**: Distance and link margin estimation using Log-Distance Path Loss model, directional trend tracking (approaching vs receding), and reactive UI state flow.
- **Dynamic Mesh Topology Auto-Healing (`MeshElectionManager.kt`)**: Decentralized Weighted Bully consensus protocol electing optimal RF coordinator upon leader silence timeout (>15s) with zero voice packet drops.
- **Multiplatform CI/CD Pipeline (`.github/workflows/ci.yml`)**: Automated GitHub Actions matrix verifying multiplatform unit tests, iOS Simulator compilation, and Android release APK assembly.

---

## [1.0.0] - 2026-09-29 â€” Sprint 08: Real-World Field Testing & Hardening (v1.0 Production Release)

### Added
- **Production Release Signing & ProGuard/R8 Hardening (`proguard-rules.pro`)**: Preserved polymorphic Kotlin serialization, Koin modules, and Bluetooth GATT components for minified release builds.
- **Mesh Diagnostics & Field Range Benchmark (`MeshDiagnostics.kt`)**: RF telemetry sliding window tracking RSSI, sequence gap PDR, multi-hop relay distribution, and markdown report generator.
- **Helmet Wind Noise Filter & Formant EQ (`WindNoiseFilter.kt`)**: 4th-order Butterworth High-Pass Filter attenuating low-frequency wind turbulence (<300 Hz) by >35 dB, combined with 2.2 kHz parametric speech formant boost.
- **Battery Drain Profiler (`PowerProfiler.kt`)**: Duty cycle monitoring and power modeling demonstrating <3.2% consumption over 4-hour rides, with runaway WakeLock safety watchdog.
- **v1.0 Release Readiness Sign-Off (`docs/reports/v1.0-Release-Readiness.md`)**: Comprehensive production audit verifying 100% of Definition of Done criteria across all v1.0 sprints.

---

## [0.7.0] - 2026-09-27 â€” Sprint 07: Audio Codec Optimization, Jitter Buffering & PLC

### Added
- **Adaptive Jitter Buffer & Playout Sequencer (`AdaptiveJitterBuffer.kt`)**: RFC 3550 inter-arrival jitter estimation with dynamic 40ms to 150ms playout delay, sequence reordering, and gap concealment triggers.
- **Packet Loss Concealment & Waveform Extrapolation (`PacketLossConcealment.kt`)**: Autocorrelation pitch period detection, voiced pitch extrapolation, -3 dB exponential decay, and smooth boundary crossfading.
- **Adaptive Noise Gate & VOX Energy DSP (`NoiseGate.kt`)**: Logarithmic dBFS envelope tracker with dual-threshold hysteresis (-35 dBFS open / -42 dBFS close), 5ms attack, 100ms hold, and 50ms release.
- **Multi-Rate Pure KMP Audio Codec Engine (`AudioCodec.kt`)**: Zero-dependency 4-bit IMA ADPCM logarithmic compression engine conforming to single BLE GATT MTU bounds (84â€“127 bytes/frame).
- **Full Duplex Audio Pipeline Session (`AudioPipelineSession`)**: End-to-end coordinated audio streaming resilient to 20% packet drops and 50ms transit jitter.

---

## [0.6.0] - 2026-09-27 â€” Sprint 06: Background Services & OS Power Management

### Added
- **Android Foreground Service (`IntercomForegroundService.kt`)**: Background lifecycle with sticky ongoing notification, `connectedDevice|microphone` service types, and Bluetooth SCO audio routing lock.
- **Android Partial WakeLock & Battery Optimization Helper (`PowerManagerHelper.kt`)**: Safe reference-counted WakeLock acquisition and battery optimization exemption launcher.
- **iOS Background Audio & CoreBluetooth Modes (`Info.plist` & `iOSApp.swift`)**: `AVAudioSession` VoiceChat configuration and interruption handling for screen-off execution.
- **iOS Background Task Assertions & Keep-Alive (`BackgroundKeepAlive.kt`)**: Watchdog protection preventing OS background suspension during mesh relay bursts.
- **Screen-Off Packet Continuity Verification (`BackgroundContinuityTest.kt`)**: Automated test verifying 100% packet delivery under rapid burst loads.

---

## [0.5.0] - 2026-09-27 â€” Sprint 05: Mesh Flooding Relay, Deduplication & Encryption

### Added
- **Pure Kotlin RFC 8439 ChaCha20-Poly1305 AEAD**: Implemented zero-dependency, pure Kotlin cipher and SHA-256 key derivation (`PacketCipher.kt`) running on all KMP targets.
- **Poly1305 26-bit Limb Arithmetic**: 130-bit integer operations over five 64-bit Longs, bypassing Java `BigInteger` allocations. Verified against official RFC 8439 Section 2.8.2 KAT.
- **Sliding-Window Packet Deduplicator**: 5,000ms TTL, 1,000-entry capacity cache with Mutex protection to suppress broadcast storms and cyclic mesh loops (`PacketDeduplicator.kt`).
- **Multi-Hop Relay Flooding**: Automatic TTL decrement ($3 \rightarrow 2 \rightarrow 1$) and forwarding to adjacent peers in `MeshTransport.android.kt` and `MeshTransport.ios.kt`.
- **Simulated Mesh Topology Tests**: Linear 3-node multi-hop simulation in `CrossPlatformGattMeshTest.kt`.
- **Archify Architecture Delivery**: Interactive standalone HTML system diagram at `docs/architecture/intercom-alpha-architecture.html`.

---

## [0.4.0] - 2026-09-27 â€” Sprint 04: Swift â†” KMP Bridge & QR Onboarding

### Added
- **Swift â†” KMP Reactive Bridge**: Non-blocking `IntercomBridge.kt` with cancellation handles in `iosMain`.
- **AVFoundation QR Scanner**: Native iOS camera-based QR code scanner (`QrScannerView.swift`).
- **Offline Group QR Format**: `INTERCOM:v1:` QR generator and parser in `GroupManagerImpl.kt`.

---

## [0.3.0] - 2026-09-26 â€” Sprint 03: iOS Native Audio & CoreBluetooth Mesh

### Added
- **iOS AVAudioEngine Tap**: Low-latency PCM input and output scheduling in `AudioEngine.ios.kt`.
- **CoreBluetooth Dual-Role**: GATT peripheral and central managers matching Android UUIDs.
- **100% Native SwiftUI UI**: Strict enforcement of Rule 1 ("Do Not Share UI on iOS").

---

## [0.2.0] - 2026-09-25 â€” Sprint 02: Android Platform Engine & Compose UI

### Added
- **Android Audio Streaming**: `AudioRecord` / `AudioTrack` 16kHz PCM streaming with VOX thresholding.
- **Android BLE GATT Transport**: Advertising, scanning, and read/write characteristics.
- **Bluetooth SCO Headset Routing**: `HeadsetManager.android.kt` with hardware button hook.
- **Jetpack Compose UI**: Cockpit view with live VU meters and PTT button state machine.

---

## [0.1.0] - 2026-09-20 â€” Sprint 01: Core Networking & Signaling

### Added
- **Polymorphic Signaling Protocol**: JSON and binary protocols in `:core`.
- **Ktor WebSocket Server**: Cloud relay fallback and status API in `:server`.
- **Signaling Client**: Multiplatform WebSocket client in `app/sharedLogic`.

