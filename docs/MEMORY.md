# Engineering Memory & Architecture Knowledge Base — Intercom-Alpha

> **Document Version:** 1.0  
> **Status:** Active / Curated Knowledge Base  
> **MemPalace Memory Wing:** `intercom_alpha` (12,864 drawers)  
> **Graphify Knowledge Graph:** 4,107 nodes · 8,266 edges · 278 communities  
> **Obsidian Reference:** `06_AI-Sync/` & `02_Architecture/ADRs/`

---

## 🏛️ 1. Architecture Decision Records (ADR) Summary

| ADR ID | Decision Title | Status | Impact / Core Rationale |
| :--- | :--- | :--- | :--- |
| **ADR-001** | BLE Mesh as Primary Transport | Accepted | Guarantees 100% offline operation without internet or cellular connectivity. |
| **ADR-002** | Opus Audio Codec for Mesh Voice | Accepted | Delivers optimal voice intelligibility at ultra-low bitrates (16–32 kbps). |
| **ADR-003** | Decentralized Leaderless Mesh Topology | Accepted | Eliminates single points of failure; group survives leader disconnection. |
| **ADR-004** | KMP Shared Logic + 100% Native SwiftUI | Accepted | **Do not share UI on iOS**. Android uses Compose; iOS uses native SwiftUI. |
| **ADR-005** | Dual PTT and VOX Transmission Modes | Accepted | Hands-on PTT for high-speed wind; hands-free VOX for technical downhill tracks. |
| **ADR-006** | Foreground Service & Background Audio | Accepted | Enables continuous voice communication with phone locked in pocket/tank bag. |
| **ADR-007** | Offline QR Code Group Onboarding | Accepted | Zero internet exchange: group credentials encoded in `INTERCOM:v1:` QR codes. |
| **ADR-008** | RFC 8439 ChaCha20-Poly1305 AEAD | Accepted | High-speed, authenticated encryption running in pure Kotlin across all targets. |
| **ADR-009** | Retention of `:core` and `:server` | Accepted | Cloud signaling & SFU relay fallback for remote / mixed-mesh connectivity. |

---

## 💡 2. Proven Solution Blueprints & Design Patterns

### 2.1 Pure Kotlin RFC 8439 ChaCha20-Poly1305 AEAD (`PacketCipher.kt`)
- **Challenge**: Standard crypto libraries (`javax.crypto`, BoringSSL, Apple CryptoKit) are platform-specific and break KMP targets (iOS Simulator, Wasm/JS, Native).
- **Solution**: Implemented RFC 8439 ChaCha20 and Poly1305 in 100% pure Kotlin in `commonMain`:
  - Poly1305 uses Dan Bernstein's 26-bit limb representation over five 64-bit `Long` variables.
  - Intermediate products $(2^{26}) \times (2^{26}) = 2^{52}$ stay comfortably within 63-bit signed `Long` values without overflow, requiring zero BigInteger allocations.
  - Fully verified byte-for-byte against the official RFC 8439 Section 2.8.2 Known Answer Test (KAT).

### 2.2 Sliding-Window Packet Deduplicator (`PacketDeduplicator.kt`)
- **Challenge**: In a decentralized BLE mesh with $\ge 3$ nodes, flooding relays create exponential broadcast storms and routing loops.
- **Solution**:
  - Maintained an in-memory sliding-window cache protected by `kotlinx.coroutines.sync.Mutex`.
  - Composite keys: `audio:$senderId:$seq`, `ctrl:$senderId:$type:$hash`, `relay:$origSender:$innerKey`.
  - Expired keys purged after 5,000 ms; maximum capacity capped at 1,000 entries using oldest-timestamp eviction.

### 2.3 Navigation 3 Polymorphic Backstack Serializer (`AppNavGraph.kt`)
- **Challenge**: `SerializationException: Serializer for subclass 'HomeRoute' is not found in the polymorphic scope of 'NavKey'`.
- **Solution**:
  - Navigation 3's `NavKey` is an open interface. Backstack persistence requires explicit polymorphic serializer registration:
  ```kotlin
  val navConfig = SavedStateConfiguration {
      serializersModule = SerializersModule {
          polymorphic(NavKey::class) {
              subclass(HomeRoute::class, HomeRoute.serializer())
          }
      }
  }
  val backStack = rememberNavBackStack(navConfig, HomeRoute)
  ```

### 2.4 Kotlinx Serialization Discriminator Collision
- **Challenge**: `MeshPacket.Control(val type: ControlType)` collides with default JSON class discriminator `"type"`.
- **Solution**: Configure `Json { classDiscriminator = "#type" }` globally on all mesh serializations.

### 2.5 Swift ↔ KMP Reactive Bridge (`IntercomBridge.kt`)
- **Challenge**: Swift concurrency cannot safely consume Kotlin `StateFlow` / `ReceiveChannel` directly without memory leaks or main-thread blocking.
- **Solution**: Created `IntercomBridge.kt` in `app/sharedLogic/src/iosMain` exposing typed callback closures returning a `CancellationHandle`. The SwiftUI `IntercomViewModel` subscribes on `@MainActor` and releases handles in `deinit`.

### 2.6 Android Foreground Service & Hardware Audio Routing Lock (`IntercomForegroundService.kt`)
- **Challenge**: Android aggressive background execution limits and Doze mode throttle BLE scanning and terminate microphone audio recording when the phone screen turns off.
- **Solution**:
  - Implemented `IntercomForegroundService` declared with `foregroundServiceType="connectedDevice|microphone"` in `AndroidManifest.xml` (satisfying Android 14+ API 34 security mandates).
  - Maintained an active Bluetooth SCO audio routing lock (`HeadsetManager.setScoAudioRoute(true)`), keeping motorcycle helmet audio alive during screen lock.
  - Linked `AudioEngine` (PCM capture/playback) and `MeshTransport` (BLE GATT routing) in persistent background coroutine jobs.
  - Displayed a sticky ongoing notification showing real-time peer counts and PTT status with an explicit "Leave Group" disconnect action.

### 2.7 Android Partial WakeLock & Battery Optimization Exemption (`PowerManagerHelper.kt`)
- **Challenge**: When devices enter deep Doze, CPU clock gating stops BLE mesh routing loops and causes jitter/loss in packet streaming.
- **Solution**:
  - Created `PowerManagerHelper` acquiring a reference-counted `PARTIAL_WAKE_LOCK` (`IntercomAlpha:MeshAudioWakeLock`) during active mesh sessions.
  - Used `try/finally` wrappers across `handleExplicitDisconnect()` and `onDestroy()` to prevent any possibility of WakeLock leaks.
  - Provided `isIgnoringBatteryOptimizations()` check and intent launcher for `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

### 2.8 iOS Background Audio & CoreBluetooth Background Modes (`Info.plist` & `iOSApp.swift`)
- **Challenge**: iOS aggressively suspends background apps within seconds, terminating CoreBluetooth peripherals and cutting off microphone audio when the device is locked.
- **Solution**:
  - Configured `UIBackgroundModes` with `audio`, `bluetooth-central`, and `bluetooth-peripheral` in `Info.plist`.
  - Initialized `AVAudioSession` category `.playAndRecord` with mode `.voiceChat` and options `[.allowBluetooth, .allowBluetoothA2DP, .defaultToSpeaker]` in `AppDelegate.didFinishLaunchingWithOptions`.
  - Added notification observers for `AVAudioSession.interruptionNotification` to auto-resume audio routing after phone calls and Siri interruptions.
  - Bound lifecycle transitions to `scenePhase` in native SwiftUI.

### 2.9 iOS Background Task Assertions & Watchdog Protection (`BackgroundKeepAlive.kt`)
- **Challenge**: iOS watchdog terminates background applications if CPU processing bursts (e.g. packet relay flooding or audio buffer decompression) run when the app enters background without an active task assertion.
- **Solution**:
  - Implemented `BackgroundKeepAlive` wrapping `UIApplication.beginBackgroundTaskWithName`.
  - Configured an atomic expiration handler that releases assertions cleanly before the OS watchdog threshold is breached.
  - Guarded incoming mesh audio playback and outgoing microphone transmissions with `keepAlive.withKeepAlive(...)`.
### 2.10 Screen-Off Continuity Verification Suite (`BackgroundContinuityTest.kt`)
- **Challenge**: Automated verification of background packet continuity across all KMP targets without needing real physical phone hardware in CI.
- **Solution**:
  - Implemented `SimulatedBackgroundMeshTransport` simulating rapid bursts of incoming audio packets under simulated screen-off conditions.
  - Verified 0 dropped packets across consecutive audio frames under burst load.
  - Verified bounded memory behavior of `PacketDeduplicator` under high load (1,500 unique packets into max 1,000 capacity cache).
### 2.11 Adaptive Jitter Buffer & Playout Sequencer (`AdaptiveJitterBuffer.kt`)
- **Challenge**: BLE mesh audio transmission experiences variable packet transit delays (jitter) and packet reordering. Direct synchronous playback causes stuttering, buffer under-runs, and audible gaps.
- **Solution**:
  - Implemented RFC 3550 statistical inter-arrival jitter estimation: $D(i, j) = (R_j - R_i) - (S_j - S_i)$, $J(i) = J(i-1) + \frac{|D(i,j)| - J(i-1)}{16}$.
  - Dynamic target playout delay adapts between 40ms and 150ms ($D_{target} = baseDelay + 3 \times J$).
  - Priority sequence queuing with automatic stale packet drops ($Seq < nextPlayoutSeq$) and duplicate rejection.
  - Playout clock tick returns `PlayoutFrame.Concealment` when a packet gap is encountered at playout deadline, feeding downstream Packet Loss Concealment (PLC).

### 2.12 Packet Loss Concealment & Waveform Extrapolation (`PacketLossConcealment.kt`)
- **Challenge**: Missing BLE packets produce jarring audio dropouts, robotic distortion, or hard audio cutoffs during voice communication.
- **Solution**:
  - Autocorrelation pitch period detection over the rolling PCM history buffer to lock onto speaker fundamental frequency (50 Hz–500 Hz).
  - Waveform extrapolation repeats pitch periods smoothly across dropped frames.
  - Applies cumulative -3 dB exponential attenuation per consecutive frame loss, gracefully muting to silence if packet stream is completely interrupted (> 5 frames).
  - Linear/cosine crossfade blending over frame boundaries upon stream resumption to prevent phase clicks and acoustic transients.

### 2.13 Adaptive Noise Gate & VOX Energy DSP (`NoiseGate.kt`)
- **Challenge**: Wind, road noise, and engine exhaust in helmet intercom environments trigger VOX false positives and degrade intelligibility.
- **Solution**:
  - Dual-threshold hysteresis (`openThresholdDb` = -35 dBFS, `closeThresholdDb` = -42 dBFS) avoids gate chattering near the transition threshold.
  - Fast attack time (5ms) retains transient plosives and speech consonants.
  - Hold window (100ms) preserves gate openness across inter-syllable pauses without premature cutoffs.
  - Release phase (50ms) applies smooth sample-by-sample linear gain slew down to floor attenuation (-60 dB), eliminating zipper distortion and audible clicks.

### 2.14 Multi-Rate Pure KMP Audio Codec Engine (`AudioCodec.kt`)
- **Challenge**: 16-bit PCM streaming consumes 48 kB/s (384 kbps) requiring packet fragmentation across BLE GATT MTUs, which drastically increases packet loss in multi-hop mesh environments. C/JNI codecs (e.g. libopus) break iOS multiplatform builds and fail on Wasm/JS targets.
- **Solution**:
  - Pure Kotlin Multiplatform 4-bit IMA ADPCM logarithmic compression engine with an 89-step quantization table and zero external C/JNI or Java dependencies.
  - Profile-matched decimation and anti-aliasing filtering compress 20ms frames into 84–127 bytes, fitting effortlessly inside a single standard BLE GATT MTU (default negotiated $\ge 185$ bytes).
  - Smooth linear/Hermite interpolation upsampling reconstructs target sample counts on decode with high SNR (>12 dB for medium, >20 dB for high).

### 2.15 Full Duplex Mesh Audio Pipeline Session (`AudioEngine.kt` & `AudioPipelineSession`)
- **Challenge**: Seamlessly coordinating the full duplex audio lifecycle between raw hardware microphone/speaker buffers and the variable-delay BLE mesh transport without latency spikes or glitching.
- **Solution**:
  - Outgoing pipeline: Raw PCM input $\to$ `NoiseGate` (attack/hold/release hysteresis + DTX silence suppression) $\to$ `AudioCodec` (4-bit ADPCM single MTU frame).
  - Incoming pipeline: Mesh audio packet $\to$ `AdaptiveJitterBuffer` (RFC 3550 playout deadline sequencing) $\to$ `AudioCodec` (ADPCM decode) $\to$ `PacketLossConcealment` (waveform extrapolation + boundary crossfading) $\to$ hardware speaker.
  - End-to-end integration benchmark verified continuous playout under 20% random packet drops and 50ms transit jitter.

### 2.16 RF Diagnostics & Distance Estimation Engine (`MeshDiagnostics.kt`)
- **Challenge**: Measuring real-world outdoor mesh performance (PDR, RF link budget, multi-hop relay distribution, transit jitter) without introducing platform-specific networking profilers.
- **Solution**:
  - Implemented `MeshDiagnostics.kt` in pure KMP using `kotlinx.coroutines.sync.Mutex` and monotonic arrival timestamps.
  - Calculated Packet Delivery Ratio (PDR) from sequence gap tracking: $\text{PDR} = \frac{\text{received}}{\text{received} + \text{lost}}$.
  - Applied Log-Distance Path Loss model for real-time physical distance estimation: $d = 10^{\frac{A - \text{RSSI}}{10 \cdot n}}$ ($A = -59\text{ dBm}, n = 2.5$).
  - Monitored link budget margin ($M = \text{RSSI} - S_{rx}$) relative to $-93\text{ dBm}$ BLE sensitivity.
  - Established markdown diagnostic report generator (`toMarkdownSummary()`) and testing protocol in `docs/testing/field-reports/RangeBenchmark.md`.

### 2.17 Helmet Wind Noise & Formant Emphasis DSP (`WindNoiseFilter.kt`)
- **Challenge**: Acoustic wind turbulence at 80–120 km/h generates extreme low-frequency buffeting noise (<250 Hz) that swamps microphone inputs and masks speech formants.
- **Solution**:
  - Implemented 4th-order Butterworth High-Pass Filter using cascaded Direct Form II Transposed biquad sections ($Q_1 = 0.5412, Q_2 = 1.3066$).
  - Attenuates 100 Hz buffeting rumble by $>35\text{ dB}$ while preserving passband flatness down to 300 Hz.
  - Implemented parametric peaking EQ centered at 2.2 kHz boosting speech formant band by $+4\text{ dB}$ to $+6\text{ dB}$ to ensure crisp consonant articulation through visor wind noise.
  - Implemented soft-limiting ceiling to protect against digital overflow/clipping on loud shouting.
  - Zero C/JNI or Java-only dependencies; 100% pure Kotlin Multiplatform.

### 2.18 Battery Drain Profiling & Screen-Off Power Optimization (`PowerProfiler.kt`)
- **Challenge**: Guaranteeing $<30\%$ battery drain over 4 continuous hours of screen-off voice relay without physical hardware draining unexpected milliamps.
- **Solution**:
  - Implemented multiplatform `PowerProfiler.kt` tracking real-time duty cycle across `IDLE`, `TRANSMITTING`, `RECEIVING`, and `RELAYING`.
  - Modeled hardware current specifications: 20 mA idle, 75 mA Tx, 55 mA Rx, 35 mA relay.
  - Demonstrated through automated benchmark that realistic riding duty cycles consume ~31.5 mA avg, or 126 mAh over 4 hours (approx 3.15% on a 4,000 mAh pack, comfortably within the 30% limit).
  - Added safety watchdog triggering `PowerAlert.RUNAWAY_WAKELOCK` if a transmission exceeds 60 seconds.

### 2.19 Production Release Optimization & R8 Rules (`proguard-rules.pro`)
- **Challenge**: Preventing Android R8 / ProGuard from stripping kotlinx.serialization polymorphic serializers (like `MeshPacket.Control` and `NavKey`) or Koin modules during minified release builds.
- **Solution**:
  - Configured explicit `-keep` rules preserving Companion objects, `$serializer` instances, and `@Serializable` class metadata.
  - Preserved Koin Multiplatform modules, Android ViewModels, and foreground service lifecycle components.
  - Verified `./gradlew assembleRelease` compiles cleanly into signed/unsigned APK packages without runtime reflection missing errors.

### 2.20 Cloud Mesh Relay Gateway & Dual-Transport Bridging (`CloudRelayBridge.kt`)
- **Challenge**: BLE mesh range is limited to line-of-sight RF hops. If riders become separated across cellular distances or a mountain ridge, direct BLE relay drops completely.
- **Solution**:
  - Implemented `CloudRelayBridge.kt` bridging local BLE mesh packets to the Ktor WebSocket server (`:server`) via polymorphic `SignalingMessage.MeshRelay`.
  - Encapsulated packets in pure KMP Base64 (`kotlin.io.encoding.Base64`) with `#type` JSON discriminator.
  - Multi-transport coordination modes: `BLE_ONLY` (default off-grid), `FALLBACK_ONLY` (cellular only), and `HYBRID_ALWAYS` (simultaneous dual-path).
  - Looping prevention: remote incoming cloud packets are passed through `PacketDeduplicator.shouldProcess(packet)` before delivery into local mesh channels, preventing bounce-back storms between BLE and cloud relays.

### 2.21 Low-Power BLE Proximity Radar & Distance UI (`ProximityRadar.kt`)
- **Challenge**: Riders in a motorcycle pack need glanceable cockpit situational awareness of rider separation without manual distance calculations or distraction.
- **Solution**:
  - Implemented `ProximityRadar.kt` translating raw RSSI and path loss into discrete proximity zones: `NEAR` (<15m), `MEDIUM` (15m .. <60m), `FAR` (60m .. <150m), and `OUT_OF_RANGE` (>=150m or timeout).
  - Directional trend detection: positive delta in smoothed RSSI indicates approaching rider; negative indicates receding rider.
  - Generates reactive `radarFlow: StateFlow<RadarSnapshot>` consumed seamlessly by Compose and SwiftUI views.
  - Integrated with `MeshDiagnosticsReport` for cluster-wide link health and hop counts.

### 2.22 Dynamic Mesh Topology Auto-Healing & Bully Consensus (`MeshElectionManager.kt`)
- **Challenge**: In a decentralized intercom mesh, when the original group creator disconnects, moves out of range, or exhausts battery, group membership coordination must not break or sever active audio sessions.
- **Solution**:
  - Implemented `MeshElectionManager.kt` executing a weighted Bully election protocol.
  - Heartbeat watchdog triggers election if leader silence exceeds 15,000ms.
  - Weighting heuristic prioritizes nodes with optimal RF link margins (> 3.0 dB superiority) to maximize relay reliability across the pack, falling back deterministically to lexicographical node ID.
  - Re-election runs completely out-of-band over mesh control frames, preserving 100% of peer-to-peer audio flow with zero packet drops.

### 2.23 GitHub Actions Multiplatform Matrix CI/CD (`.github/workflows/ci.yml`)
- **Challenge**: Guaranteeing multiplatform build stability (Android SDK, iOS Kotlin Native Simulator ARM64, and JVM test suites) on every sprint branch and PR without manual developer validation.
- **Solution**:
  - Implemented `.github/workflows/ci.yml` with dual-stage parallel pipelines.
  - Stage 1 runs multiplatform tests (`./gradlew test`) and iOS compilation (`./gradlew :app:sharedLogic:compileKotlinIosSimulatorArm64`).
  - Stage 2 runs Android release build (`./gradlew :app:androidApp:assembleRelease`) to verify ProGuard / R8 rules and uploads the APK artifact for release distribution.
  - Configured branch filters for Multi-Tier GitFlow (`main`, `develop`, `sprint/**`, `release/**`, tags `v*`).

### 2.24 Multi-Channel Sub-Group Partitioning & Dual-Watch (`ChannelManager.kt`)
- **Challenge**: Large motorcycle riding groups often have subgroups (e.g. Lead Scouts vs Sweep/Trailer vs Support Vehicle) who need private channels while retaining the ability to hear emergency broadcasts and monitor a secondary channel.
- **Solution**:
  - Implemented `ChannelManager.kt` providing 16 distinct sub-channels.
  - Sub-channel filtering accepts audio frames matching the active channel or configured `monitoredChannelIds` (Dual-Watch).
  - Emergency SOS frames (`isGlobalBroadcast = true`) unconditionally bypass channel mutes and channel mismatches across all riders.
  - Scan mode allows monitoring all unmuted channels concurrently.
  - Emits reactive `channelState: StateFlow<ChannelState>` for instant cockpit channel switching.

### 2.25 Emergency Priority Audio Preemption & Chime Synthesis (`PriorityBroadcastManager.kt`)
- **Challenge**: Critical safety warnings (e.g. "Gravel in turn", "Deer ahead", or crash detection) must never be drowned out by normal chatter, blocked by sub-channel mutes, or delayed by buffering.
- **Solution**:
  - Implemented `PriorityBroadcastManager.kt` managing three traffic tiers: `NORMAL`, `PRIORITY`, and `EMERGENCY_OVERRIDE`.
  - Immediate preemption: incoming emergency frames immediately override and suppress ongoing conversational streams with zero delay.
  - Hangover window: 600ms hangover guard prevents normal chatter from cutting in during micro-pauses in emergency voice messages.
  - Mathematical acoustic synthesis: `AlertChimeSynthesizer` generates pure 48kHz 16-bit PCM alert tones (880Hz single-tone hazard and 880Hz -> 1760Hz two-tone emergency) in pure Kotlin with 8% fade envelope to prevent speaker popping, requiring zero external audio assets.

### 2.26 3D Binaural Stereo Audio Spatializer & ITD Panning (`SpatialAudioProcessor.kt`)
- **Challenge**: In a group ride, mono audio makes it impossible to intuitively distinguish which rider is talking without glancing at the phone screen.
- **Solution**:
  - Implemented `SpatialAudioProcessor.kt` translating incoming mono 48kHz voice streams into spatialized stereo PCM.
  - Constant-power sine/cosine Interaural Level Difference (ILD) panning law across [-90.0°, +90.0°] azimuth preserves total perceived acoustic energy ($g_L^2 + g_R^2 \approx 1.0$).
  - Psychoacoustic Interaural Time Difference (ITD) circular delay emulation offsets the sound reaching the contralateral ear by up to 31 samples (~0.65ms), creating rich 3D helmet soundstage.
  - Distance attenuation curve provides depth cues while strictly clamping minimum gain to 0.35 to guarantee intelligibility.

### 2.27 Adaptive RF Spectrum Interference Detection (`RfInterferenceDetector.kt`)
- **Challenge**: When riding through urban corridors or areas with heavy 2.4 GHz Wi-Fi saturation, BLE packet delivery rates plunge due to packet collisions.
- **Solution**:
  - Implemented `RfInterferenceDetector.kt` evaluating PDR stability and RSSI variance ($\sigma = \sqrt{\frac{1}{N} \sum (x_i - \mu)^2}$) across a 15-second sliding window.
  - Classifies RF environments into `LOW`, `MODERATE`, and `SEVERE`.
  - Emits real-time policy advice recommending redundant packet copies (1x -> 2x -> 3x), dynamic scan rate throttling, and randomized backoff windows to prevent collision storms.

### 2.28 Offline Mesh Quick Text & Canned Hazard Protocol (`MeshTextManager.kt`)
- **Challenge**: In high-speed highway wind noise or emergency situations, riders may be unable to hear voice clearly or speak without excessive turbulence.
- **Solution**:
  - Implemented `MeshTextManager.kt` managing standardized canned convoy alerts (`FUEL_LOW`, `HAZARD_ROAD`, `STOP_IMMEDIATE`, `MECHANICAL_ISSUE`) and custom text payloads (< 280 chars).
  - Compact serialized JSON wire frames (`MeshTextWireFrame`) routed over BLE mesh.
  - Automatic peer-to-peer receipt acknowledgment (`DELIVERY_ACK`) confirms message arrival at destination.
  - Thread-safe mutex with bounded in-memory sliding history (max 100 messages) preventing RAM accumulation.
  - Exposes reactive `messages: StateFlow<List<MeshTextMessage>>` and unread badge counter.

### 2.29 Decentralized GPS Mesh Telemetry & Convoy Tracking (`ConvoyGpsTracker.kt`)
- **Challenge**: BLE RSSI distance estimates are sensitive to human body absorption, motorcycle orientation, and terrain multipath, failing to provide precise rider bearing and formation status.
- **Solution**:
  - Implemented `ConvoyGpsTracker.kt` computing exact Great-Circle Haversine distance and forward azimuth in pure KMP with zero platform dependencies.
  - Normalizes relative rider bearing ($-180^\circ \dots +180^\circ$) relative to local rider heading, directly driving 3D `SpatialAudioProcessor` panning and cockpit radar vectors.
  - Motion-aware adaptive beaconing: stationary (15s), city (6s), and highway cruise (3s) to conserve BLE airtime and battery.
  - Automatic emergency crash/hard-braking detection ($>35\text{ km/h}$ drop in $<1.5\text{s}$) triggering instant priority telemetry broadcast.
  - Dynamic formation role detection (Lead Scout, Sweep/Tail, Closest Rider) with automatic 30s stale rider pruning.
### 2.30 Dynamic Adaptive Bitrate & Codec Profile Switching (`AdaptiveBitrateController.kt`)
- **Challenge**: Variable BLE RF conditions (tunnel transit, line-of-sight occlusion, terrain obstacles) cause audio dropouts if high bitrate profiles are maintained, while permanently using low bitrates degrades audio quality unnecessarily.
- **Solution**:
  - Implemented `AdaptiveBitrateController.kt` in `commonMain` evaluating composite RF metrics (`InterferenceLevel` and `LinkQuality`).
  - Asymmetric hysteresis: Instantaneous step-down upon RF degradation/critical packet loss prevents packet queue explosion; step-up requires 3 consecutive evaluation cycles of pristine link quality to avoid flapping and audio stutter.
  - Manual override support with instant fallback to closed-loop AUTO mode.
  - Reactive `currentProfile: StateFlow<AudioProfile>` consumed directly by the multiplatform audio engine.

### 2.31 Rolling Emergency Voice Blackbox & Encrypted Incident Cache (`EmergencyBlackboxRecorder.kt`)
- **Challenge**: In motorcycle accidents, severe terrain incidents, or sudden communication loss, investigators and riding partners need the final 60 seconds of voice conversations and sensor data leading up to the event, without risking memory exhaustion or exposing sensitive voice data to third parties.
- **Solution**:
  - Implemented `EmergencyBlackboxRecorder.kt` in `commonMain` featuring an in-memory rolling circular buffer (`ArrayDeque<BlackboxAudioFrame>`) storing 60 seconds of compressed ADPCM audio frames.
  - Hard capacity limits (3,500 frames) and strict cache bounding (5 encrypted reports) ensure heap usage remains strictly below 3 MB RAM (< 1.5 MB typical resident).
  - Automatic snapshot freeze triggers on `EMERGENCY_OVERRIDE` preemption, crash detection, emergency stop, or road hazard broadcasts with a 10-second cooldown window to eliminate duplicate spam.
  - Incident snapshots are encrypted using pure KMP RFC 8439 ChaCha20-Poly1305 AEAD (`PacketCipher`), authenticating metadata with associated data verification (`incidentId`).
  - Generates comprehensive forensic Markdown summaries (`toMarkdownForensicSummary()`) detailing incident time, trigger reason, GPS coordinates, vehicle speed, and active speaking riders.

### 2.32 KMP Swift Bridge Extension for v2.0 Subsystems (`IntercomBridge.kt`)
- **Challenge**: Seamlessly exposing rich KMP features (16-channel management, RF radar snapshots, convoy GPS bearings, emergency preemption, and blackbox incident reports) to native Swift / SwiftUI on iOS without memory leaks, retain cycles, or shared UI contamination (Rule 1).
- **Solution**:
  - Enhanced `IntercomBridge.kt` in `iosMain` providing reactive observer factory functions returning `CancellationHandle` functional interfaces (`fun cancel()`).
  - Swift views and `@MainActor` ViewModels bind directly to bridge callbacks tied to the SwiftUI view lifecycle (`.onAppear { handle = bridge.observe... }` / `.onDisappear { handle.cancel() }`).
  - Exposes thread-safe dispatch commands (`selectActiveChannel`, `sendCannedMessage`, `updateGpsLocation`, `triggerManualSos`, `setManualOverride`) executing asynchronously within the bridge's SupervisorJob scope on `Dispatchers.Main`.
  - Integrates the audio pipeline so incoming mesh frames are continuously buffered into `EmergencyBlackboxRecorder`, and outgoing compression respects `AdaptiveBitrateController` profile shifts.

### 2.33 Native iOS SwiftUI Cockpit v2.0 UI Upgrade (`ContentView.swift` & `IntercomViewModel.swift`)
- **Challenge**: Constructing an ergonomic motorcycle cockpit in 100% native SwiftUI (Rule 1) that accommodates high-vibration glove touches, instant hazard signaling, multi-channel selection, and radar bearing compass without cluttering the screen or blocking audio transmission.
- **Solution**:
  - Implemented `ChannelDialView`: Horizontal scrolling channel picker for channels 1 to 16 with preset labels, neon active selection indicator, and Dual-Watch monitoring tags.
  - Implemented `ConvoyRadarStripView`: Displays live relative bearings ($-180^\circ \dots +180^\circ$) translated into intuitive directional arrows (`arrow.up`, `arrow.up.right`, etc.) alongside ground distance.
  - Implemented `QuickHazardBar`: 1-tap canned message buttons (`Fuel Low`, `Road Hazard`, `Stop`, `Bike Issue`) triggering haptic feedback and immediate mesh broadcast.
  - Implemented `EmergencySosBanner`: Flashing preemption card showing blackbox buffer frame counts and instant SOS trigger.
### 2.34 Compose Multiplatform Cockpit Components (`HomeScreen.kt`, `HomeViewModel.kt`, `sharedUI`)
- **Challenge**: Surfacing v2.0 subsystems (16-channel dial, radar targets, convoy telemetry, quick hazard alerts, and emergency preemption) in Compose Multiplatform across Android and Desktop while maintaining strict separation from iOS (Rule 1).
- **Solution**:
  - `HomeUiState.kt`: Enriched domain model with non-null default snapshots (`ChannelState`, `RadarSnapshot`, `ConvoyTelemetrySnapshot`, `messages`, `unreadTextCount`, `isEmergencyActive`, `blackboxBufferedCount`).
  - `HomeViewModel.kt`: Connected manager StateFlows (`ChannelManager.channelState`, `ProximityRadar.radarFlow`, `ConvoyGpsTracker.convoyState`, `MeshTextManager.messages`, `PriorityBroadcastManager.isEmergencyActive`, `EmergencyBlackboxRecorder.bufferedFrameCount`, `AdaptiveBitrateController.currentProfile`) into a single reactive `_uiState` stream.
  - Modular UI components in `org.nowni.intercom_alpha.ui.components`:
    - `EmergencyPreemptionBanner`: High-contrast emergency alert with real-time blackbox frame counter and reset action.
    - `ChannelSelectorBar`: Horizontal pill selector (CH 1–16) with active channel indicator, Dual-Watch `[DW]` monitoring indicators, and Scan mode toggle.
    - `ProximityRadarView`: Directional vector indicators (-180° to +180°) rendering rider heading arrows ($\uparrow, \nearrow, \rightarrow, \searrow, \downarrow, \dots$), distance, and RF link margin.
    - `QuickHazardBar`: 1-tap canned hazard broadcast triggers (`FUEL_LOW`, `HAZARD_ROAD`, `STOP_IMMEDIATE`, `MECHANICAL_ISSUE`), unread message badge, and dedicated SOS trigger.
    - `MessageHistoryDialog`: Modal dialog displaying complete message history and custom text composer.
  - `HomeScreen.kt`: Integrated cockpit layout with safe-content padding, audio meters, PTT button, and peer list.

### 2.35 Multiplatform Presentation Integration Tests & Pure KMP Memory Bounding
- **Challenge**: Verifying cross-platform reactive state binding across shared logic, bridge listeners, and UI view models while maintaining strict memory bounds (< 3 MB resident RAM) and zero platform leaks.
- **Solution**:
  - `CancellationHandle.kt`: Promoted `CancellationHandle` functional interface to pure KMP in `app/sharedLogic/src/commonMain`, enabling uniform lifecycle-safe callback cancellation across both Swift (`iosMain`) and Kotlin/JVM consumers.
  - Multiplatform contract suites:
    - `IntercomBridgeContractTest.kt` (`sharedLogic/commonTest`): Verified cancellation handle lifecycles, 16-channel state flows, Dual-Watch, proximity radar telemetry streams, mesh text delivery ACK, emergency priority preemption, and link-quality adaptive bitrate transitions.
    - `HomeViewModelTest.kt` (`sharedUI/commonTest`): Verified channel selection, dual-watch, scan mode, canned hazard broadcast dispatch, custom text messaging, unread badge clearing on dialog open, emergency SOS trigger & reset, and PTT press/release state transitions.
  - Zero-dependency Pure KMP Compression (`MiniLz` in `EmergencyBlackboxRecorder.kt`):
    - Replaced uncompressed JSON payload storage with pure Kotlin LZ77 compression and byte-level tagging.
    - Reduced cached incident report payload sizes by ~95% (< 50 KB per report), keeping total circular buffer and 5 cached incident reports strictly under 1 MB resident RAM (< 3 MB threshold).
  - Power & Telemetry Edge-case Fixes:
    - `PowerProfiler.kt`: Added `currentTxStartMs` tracking across intermediate reports to ensure robust runaway wakelock detection (> 60s continuous Tx).
    - `ConvoyGpsTracker.kt`: Added fallback to `coordinate.timestampMs` in `handleIncomingTelemetry` when `frame.timestampMs` is 0L, ensuring accurate stale rider pruning.


### 2.36 Android Platform Hardware-to-Cockpit Service Bridge (`AndroidPlatformBridge.kt` & `IntercomHardwareBridge.kt`)
- **Challenge**: Connecting the presentation layer (`HomeViewModel` in `sharedUI`) to the background Android engine (`IntercomForegroundService` in `androidApp`) to replace mock states with live Bluetooth LE mesh audio, real peers, real-time mic/speaker RMS meters, and OTA control packets, while maintaining 100% KMP architectural cleanliness and zero-leak lifecycle safety.
- **Solution**:
  - `IntercomHardwareBridge.kt`: Defined pure KMP platform hardware contract in `sharedLogic/commonMain` with reactive StateFlows for `isTransmitting`, `connectedPeers`, `inputLevel`, `outputLevel`, `localGpsCoordinate`, and SharedFlows for incoming OTA text, GPS, and emergency frames. Implemented `NoOpIntercomHardwareBridge` for JVM/desktop/test environments.
  - `AndroidPlatformBridge.kt`: Concrete Android singleton in `androidApp` coordinating bidirectional flow with `IntercomForegroundService`. Observes live mic/speaker RMS levels, syncs PTT state across UI and physical helmet buttons, and routes outgoing control packets.
  - `IntercomForegroundService.kt`: Bound `AndroidPlatformBridge.instance` on create/destroy, integrated channel audio filtering and blackbox recording on incoming audio packets, added incoming control packet dispatch (text, GPS, emergency SOS), and streamed real-time Android `LocationManager` updates into `ConvoyGpsTracker`.
  - `AppModule.kt`: Implemented `createSharedUiModule(hardwareBridge)` for type-safe DI injection of `AndroidPlatformBridge.instance` on Android while retaining `NoOpIntercomHardwareBridge` for multiplatform test suites.

### 2.37 Multiplatform Production Release Build & ProGuard Hardening (`proguard-rules.pro`, `build.gradle.kts`)
- **Challenge**: Enabling aggressive R8 code minification and resource shrinking for production Android releases while preventing runtime serialization reflection failures, missing Koin injection bindings, and binary bloat across KMP modules.
- **Solution**:
  - `gradle/libs.versions.toml`: Centralized version catalog declaring `versionCode = "2"` and `versionName = "2.0.0"`.
  - `app/androidApp/build.gradle.kts`: Configured release build type with `isMinifyEnabled = true`, `isShrinkResources = true`, `signingConfig = signingConfigs.getByName("debug")`, and mapped version parameters directly from the catalog.
  - `app/androidApp/proguard-rules.pro`: Hardened v2.0 optimization and preservation rules:
    - Retained `@Serializable`, `KSerializer`, `PolymorphicSerializer`, and SerialName mappings across all mesh control/data frames.
    - Preserved Koin modules and reflection metadata to prevent runtime `NoBeanDefFoundException`.
    - Protected Android Navigation 3 polymorphic `NavKey` serializers and Jetpack Compose runtime internals.
    - Kept Ktor WebSocket networking engines and all v2.0 domain subsystems (`channel`, `radar`, `gps`, `text`, `priority`, `blackbox`, `bitrate`, `spatial`, `cellular`, `crypto`, `bridge`).
  - Achieved a lean 3.17 MB release APK size with 100% build pass and clean Kotlin/Native iOS framework compilation (`compileKotlinIosSimulatorArm64`).

### 2.38 Long-Running Battery, WakeLock & Memory Profiling Gate (`PowerManagerHelper.kt`, `PowerProfilerTest.kt`)
- **Challenge**: Preventing battery drain or runaway CPU WakeLocks during long motorcycle rides while ensuring continuous screen-off mesh voice continuity and bounding RAM usage (< 80 MB total, < 3 MB blackbox cache).
- **Solution**:
  - `PowerManagerHelper.kt`: Implemented reference-counted `pttWakeLock` (`IntercomAlpha:PttTxWakeLock`) with an automatic 60-second runaway watchdog timeout.
  - `IntercomForegroundService.kt`: Balanced PTT wake lock acquisition and release inside `setTransmitting(Boolean)`. Provided leak-prevention `releaseAllWakeLocks()` inside `handleExplicitDisconnect()` and `onDestroy()` `finally` blocks, and cleanly stopped SCO audio routing on service shutdown.
  - `PowerProfilerTest.kt`: Created automated verification suite:
    - Verified strict WakeLock balance between PTT active and standby listening states with 0 leaked locks.
    - Verified Bluetooth SCO audio route cleanly releases (`setScoAudioRoute(false)` and `headsetManager.stop()`) upon service termination.
    - Confirmed LZ77 compressed blackbox stays bounded < 1 MB (< 505 KB buffer alone, < 850 KB with active incident snapshot, < 1.2 MB across 5 incident snapshots; well under 3 MB threshold).
    - Validated 4-hour motorcycle ride power model demonstrating < 3.2% total battery consumption on a reference 4,000 mAh battery (target < 30%).

### 2.39 Pure KMP Perfect Forward Secrecy & Ephemeral Key Ratchet (`MeshKeyRatchet.kt`, `PacketCipher.kt`)
- **Challenge**: Standard symmetric mesh channel keys provide no forward secrecy if a rider's device is compromised, lost, or inspected offline after a group tour.
- **Solution**:
  - Implemented RFC 2104 HMAC-SHA256 and RFC 5869 HKDF-SHA256 in 100% pure Kotlin Multiplatform (Rule 3) within `PacketCipher.kt`.
  - Created `MeshKeyRatchet.kt` implementing symmetric KDF chain ratcheting. Message keys advance per transmission, immediately zeroizing ephemeral secrets via `.fill(0)`.
  - Maintained bounded out-of-order skipped key cache (`maxSkippedKeysPerPeer = 100`) to recover disordered packet delivery while enforcing replay protection.
  - Provided full zeroization on session termination via `destroy()`.

### 2.40 Direct Helmet Intercom Bluetooth GATT Accessory Profile (`HelmetAccessoryManager.kt`)
- **Challenge**: Riders wearing gloves at highway speeds cannot interact with touchscreen phones; they rely on Cardo PackTalk, Sena 50S/RC4, or EJEAS handlebar remotes.
- **Solution**:
  - Created pure KMP `HelmetAccessoryManager.kt` supporting Cardo, Sena, EJEAS, and generic BLE HID handlebar remotes.
  - Parsed Bluetooth SIG standard Battery level (`0x2A19`), Device Information, button opcodes/bitmasks, and integrated a 60-second runaway PTT safety watchdog.
  - Provided audio route preference arbitration (`BLUETOOTH_SCO`, `A2DP_HIGH_FIDELITY`, `BUILTIN_SPEAKER`) and automatic PTT release on accessory disconnect.

### 2.41 iOS 18+ Live Activity & Dynamic Island Hands-Free Cockpit Widget (`IntercomActivityWidget.swift`)
- **Challenge**: iOS riders require glanceable mesh state and hands-free PTT without keeping the app foregrounded, strictly complying with Rule 1 (100% native SwiftUI / zero shared Compose on iOS).
- **Solution**:
  - Implemented native `ActivityAttributes` and `ContentState` in `IntercomActivityAttributes.swift`.
  - Built `IntercomActivityWidget.swift` with Compact Leading/Trailing, Minimal, and Expanded Dynamic Island layouts, live radar bearing compass indicators, and a Lock Screen / StandBy cockpit card.
  - Tied ActivityKit lifecycle directly to `IntercomViewModel.swift` using iOS 16.2+ / iOS 18+ `ActivityContent`.

### 2.42 Multi-Cluster Cloud Cross-Mesh SFU Signaling & Audio Bridging (`SignalingProtocol.kt`, `SignalingServerManager.kt`, `CloudRelayBridge.kt`)
- **Challenge**: Multiple geographically separated riding convoys need to bridge across cellular networks without audio transcoding or latency overhead, while preventing routing loops.
- **Solution**:
  - Added polymorphic `SignalingMessage.ClusterBridge` message with `#type` class discriminator preventing collision with `MeshPacket.Control.type`.
  - Enabled transcode-free routing of 4-bit ADPCM packets across rooms and clusters with zero latency penalty and bidirectional server room bridging (`bridgeRooms`, `unbridgeRooms`, `getBridgedRooms`).
  - Implemented circular routing loop prevention in `CloudRelayBridge` via `hopLimit` and `clusterPath` traversal inspection.
  - Dynamically aggregated multi-cluster peer presence and smoothed incoming cross-mesh audio frames via `AdaptiveJitterBuffer`.

### 2.43 Zero-Scroll Tactical Cockpit HUD Ergonomics & Subsystem Integration (Sprint 15)
- **Challenge**: Standard mobile layouts require vertical scrolling, hiding the PTT button or critical hazards when mounted on motorcycle handlebars (RAM/QuadLock mounts). Riders wearing thick gloves at highway speeds cannot perform small precision taps or scroll gestures.
- **Solution**:
  - Enforced zero-scroll layout contract: all critical controls (Avionics Bar, Channel Selector, Vector Radar, PTT Actuator, Hazard Strip, Emergency SOS Bar) fit in a non-scrolling single viewport on mobile screens ($H \le 844$dp).
  - Central 140dp circular tactile PTT actuator with pulsating crimson hot-mic wave, tactile haptic feedback on touch-down and touch-up, and safety VOX lock bypass.
  - Flanking quick-actuators: 58dp speaker volume toggle (30% -> 60% -> 100%) and 58dp VOX lock button.
  - Single-row 4-column hazard macro bar (`⚠️ HAZARD`, `⛽ FUEL`, `🛑 STOP`, `🔧 BIKE`) with $\ge 52$dp height touch targets for gloved fingers.
  - Circular vector radar display: concentric distance rings (100m, 500m, 1.5km), heading chevron, crosshairs, and dynamically plotted color-coded rider dots (Green < 100m, Cyan < 500m, Amber $\ge$ 500m) with live telemetry callout.
  - End-to-end hardware telemetry wiring: Cardo/Sena GATT battery level and PTT events (`HelmetAccessoryManager`), Perfect Forward Secrecy session ratchet (`MeshKeyRatchet`), and multi-cluster cloud bridge status (`CloudRelayBridge`) integrated across `HomeViewModel`, `AndroidPlatformBridge`, and native iOS `IntercomBridge` / `IntercomViewModel`.

### 2.44 Pure KMP Automatic Gain Control (AGC) & Dynamic Range Compression (DRC) (`AutomaticGainControl.kt`, `DynamicRangeCompressor.kt`)
- **Challenge**: Motorcycle helmet microphones operate in extreme acoustic dynamic ranges: quiet whispers at stoplights vs. yelling over 100+ km/h wind roar. Uncontrolled gain either causes severe clipping distortion or amplifies background road rumble during speech pauses.
- **Solution**:
  - Implemented `AutomaticGainControl.kt` in pure Kotlin Multiplatform:
    - Normalizes speech energy to target RMS (-18.0 dBFS) across dynamic range.
    - Anti-pumping envelope follower below -50.0 dBFS suspends gain boosts during pauses, gently decaying toward unity gain to prevent amplifying ambient engine or road rumble.
    - Slew-rate filtering with asymmetric attack (80ms) and release (300ms) plus sample-by-sample linear gain interpolation completely eliminates clicks and zipper noise.
  - Implemented `DynamicRangeCompressor.kt` with pure KMP soft-knee compression and lookahead peak limiter:
    - Smooth soft-knee compression curve (threshold: -12.0 dBFS, ratio: 4:1, knee width: 6.0 dB) transitions gradually from linear into compressed behavior without harsh harmonic artifacts.
    - Low-latency lookahead circular buffer (1.5ms) detects transients ahead of time and applies gain attenuation before peaks exit the delay line.
    - Brickwall peak limiter strictly bounds output peak to `ceilingDb` (-0.5 dBFS), guaranteeing zero clipping at 32767 and protecting ADPCM encoder buffers and helmet earpieces.
  - Integrated both processors into `AudioPipelineSession` (`AudioEngine.kt`) on both outgoing microphone and incoming playout paths.

### 2.45 Autonomous Low-Power Mesh Repeater & Basecamp Beacon Service (`MeshRepeaterService.kt`)
- **Challenge**: Convoy riders separated by mountain ridges, switchbacks, or canyons lose direct line-of-sight BLE connectivity. Stationary nodes (basecamp stations or parked trailheads) need to relay mesh packets without wasting battery on unused microphone/speaker pipelines.
- **Solution**:
  - Implemented `MeshRepeaterService.kt` in `commonMain`:
    - Zero audio hardware buffer allocations: Completely bypasses microphone capture and audio playout pipelines, dedicating 100% of CPU and radio airtime to low-latency flooding relay.
    - Sub-15ms multi-hop relay forwarding: Wraps raw packets into `MeshPacket.Relay`, decrements TTL, drops expired frames (TTL <= 1), and rejects self-origin packets.
    - Sliding-window loop suppression: Evaluates incoming packets with `PacketDeduplicator` to eliminate broadcast storm echo loops.
    - Periodic Basecamp Beaconing: Broadcasts low-overhead `RepeaterBeaconPayload` via `ControlType.REPEATER_BEACON` carrying battery level, solar status, uptime, and active neighbor counts.
    - Remote Diagnostic Ping/Pong: Supports `ControlType.REPEATER_PING` and responds with `ControlType.REPEATER_PONG` carrying ping reflection timestamps, battery health, and forwarded packet statistics.
    - Active neighbor table tracks heard rider IDs with 60-second stale pruning.

- **ICA-1603: Automated Multi-Node Mesh Field Simulation Harness (Sprint 16)**:
  - Implemented `ConvoyMeshSimulationHarness.kt` in `commonMain`:
    - Simulates 8-node virtual motorcycle convoy moving in linear formation under stochastic RF conditions.
    - Models distance-bounded direct hop limits (`maxDirectHopDistance`), multi-hop flood forwarding via `MeshPacket.Relay`, bounded TTL drops, and broadcast storm suppression via `PacketDeduplicator`.
    - Simulates 25% stochastic packet drop and out-of-order in-flight delivery.
    - Validates 100% successful decryption recovery of received out-of-order frames via `MeshKeyRatchet` skipped-key caching and jitter playout via `AdaptiveJitterBuffer`.
    - Benchmarking executes in under 5 seconds with zero flaky timings.

- **ICA-1604: Seamless Mesh OTA Group Re-Keying & Dynamic Epoch Migration (Sprint 16)**:
  - Implemented `GroupEpochManager.kt` in `commonMain`:
    - Pure multiplatform RFC 5869 HKDF-SHA256 epoch master key derivation and RFC 2104 HMAC-SHA256 leader signature validation for `EPOCH_ROLLOVER`.
    - Dual-epoch concurrent decryption window (default: 30 seconds) ensuring zero frame drops or audio stutter during active voice transmissions.
    - Consensus epoch telemetry beaconing (`EPOCH_BEACON`).
    - Explicit memory zeroization (`masterSecret.fill(0)`) and cleanup of superseded epoch keys.
    - Wired into `GroupManager.kt` and `GroupManagerImpl.kt`.

### 2.46 Basecamp Repeater State Management & Platform Bridge Wiring (Sprint 17 - ICA-1701)
- **Challenge**: Stationing a mobile phone as an autonomous basecamp repeater requires decoupling audio hardware resources (mic/speaker pipelines) to conserve power and airtime while keeping the BLE transport flooding active. In addition, mobile riders need to query basecamp link quality via diagnostic pings.
- **Solution**:
  - Implemented `RepeaterUiState.kt` and `RepeaterViewModel.kt` in `app/sharedUI/src/commonMain/kotlin/org/nowni/intercom_alpha/ui/repeater/`:
    - Reactive `StateFlow<RepeaterUiState>` exposing active neighbor callsigns, battery percentage, solar status, duty cycle modes (`CONTINUOUS`, `POWER_SAVER`, `EXTREME_SURVIVAL`), packet counters, and remote diagnostic ping latency.
    - Standby screen lock toggle for OLED power preservation.
  - Extended `IntercomHardwareBridge.kt` and `AndroidPlatformBridge.kt`:
    - `isRepeaterModeActive`, `repeaterTelemetry`, `incomingRepeaterPongs`, `incomingRepeaterBeacons`.
    - When repeater mode is enabled, `IntercomForegroundService.kt` safely releases microphone recording and audio playback pipelines (`audioEngine.stop()`), eliminating unnecessary CPU wakeups and dedicating 100% of RF airtime to relaying mesh packets.
  - Extended `IntercomBridge.kt` (`iosMain`):
    - Wired `MeshRepeaterService` with reactive Swift-friendly `CancellationHandle` observers (`observeRepeaterTelemetry`, `observeRepeaterModeActive`, `observeLastPingLatency`, `observeLatestBeacon`).
    - Safe audio pipeline pause/resume upon entering/exiting repeater mode.
  - Registered `MeshRepeaterService` and `RepeaterViewModel` in Koin `AppModule.kt`.

### 2.47 Compose Multiplatform Basecamp Repeater Screen & OLED Dark Standby (Sprint 17 - ICA-1702)
- **Challenge**: Operating a phone as a basecamp repeater continuously drains battery if UI rendering consumes display power or CPU cycles.
- **Solution**:
  - Implemented `BasecampRepeaterScreen.kt` in `app/sharedUI/src/commonMain/kotlin/org/nowni/intercom_alpha/ui/repeater/`:
    - Pure OLED `#000000` power-saving color theme with ultra-dim amber/green phosphors.
    - Full-screen `NightStandbyScreen` lock mode with tap-to-wake gesture for unattended overnight tent/ridge operation.
    - Station status bar with dynamic duty cycle mode cycling (`CONTINUOUS` -> `POWER_SAVER` -> `EXTREME_SURVIVAL`).
    - Live solar/battery gauges, RF metrics grid, active heard neighbor list, and 1-tap diagnostic ping/beacon buttons.
  - Registered polymorphic `RepeaterRoute` under `NavKey` with `SavedStateConfiguration` in `AppNavGraph.kt`.
  - Added "Relay" launcher in `HomeScreen.kt`.

### 2.48 Native iOS SwiftUI Basecamp Repeater Cockpit & StandBy Display (Sprint 17 - ICA-1703)
- **Challenge**: Stationing an iOS device on a tent or ridge dock requires 100% native SwiftUI (Rule 1) with zero Compose Multiplatform imports, seamless `CancellationHandle` bridge observation, and adaptive widescreen layout for iOS 17+ StandBy mode.
- **Solution**:
  - Implemented `BasecampRepeaterView.swift` in `app/iosApp/iosApp/`:
    - Pure native SwiftUI with zero Compose Multiplatform imports.
    - Pure OLED `#000000` black background with high-contrast tactical gauges.
    - Full-screen `NightStandbyOverlayView` with time display, minimal amber phosphor status, and tap-to-wake gesture.
    - Responsive landscape adaptation (`LandscapeStandbyCockpitView`) for iOS 17+ StandBy horizontal bedside/tent docks.
    - Master relay activation card with animated RF wave, audio engine suspension banner, battery & solar status, and duty cycle picker.
    - 4-card RF telemetry grid, remote diagnostic ping trigger card with live RTT latency reflection, and active heard neighbors roster.
  - Wired into `IntercomViewModel.swift` with reactive `CancellationHandle` observers tied to `@MainActor` lifecycle.
  - Added Basecamp Repeater button to `TacticalAvionicsBar` and sheet presentation in `ContentView.swift`.

### 2.49 Remote Repeater Telemetry & Diagnostic Ping Cockpit HUD Integration (Sprint 17 - ICA-1704)
- **Challenge**: Mobile riders riding along trails need to know whether stationary basecamp repeaters are within RF relay reach without navigating away from the tactical zero-scroll cockpit HUD.
- **Solution**:
  - `HomeUiState.kt` / `HomeViewModel.kt`: Exposed `latestRepeaterBeacon`, `repeaterPingLatencyMs`, and `isPingingRepeater`.
  - Ingested `hardwareBridge.incomingRepeaterBeacons` and `incomingRepeaterPongs`. Emits `MeshRepeaterService.createDiagnosticPing` upon `HomeUiAction.TriggerRepeaterPing`.
  - `HomeScreen.kt`: Embedded compact Basecamp Beacon telemetry strip in the Top Avionics Bar displaying repeater name, battery %, solar status, round-trip latency, and 1-tap `PING` button. Zero layout shift and adheres to zero-scroll contract.
  - `ContentView.swift`: Integrated Basecamp Beacon badge with RTT latency reflection and 1-tap ping into `TacticalAvionicsBar`.

### 2.50 Pure KMP Offline Vector Trail & Terrain Geometry Engine (Sprint 18 - ICA-1801)
- **Challenge**: Off-grid adventure convoys in deep backcountry operate with zero internet or cellular data. Rendering topographic elevation contours, trail tracks, and GPS breadcrumbs on the handlebar tactical radar requires pure KMP math with zero heavy SDKs (e.g. Mapbox / Google Play Services) to preserve multiplatform compatibility (Android, iOS Native, JVM, Wasm).
- **Solution**:
  - Implemented `VectorMapModels.kt`:
    - Immutable data models: `GeoPoint`, `TrailWaypoint`, `TrailPolyline`, `ContourLine`, `ProjectedRadarPoint`, `RadarRangeZoom` (500m, 1.5km, 5km), and `TrailMapSnapshot`.
  - Implemented `TrailMapEngine.kt`:
    - Haversine distance and forward azimuth calculation in pure Kotlin.
    - Normalized polar-to-Cartesian radar projection relative to observer heading: Ahead ($0^\circ$) mapped to $-Y$, Starboard ($+90^\circ$) mapped to $+X$, Port ($-90^\circ$) mapped to $-X$, Aft ($180^\circ$) mapped to $+Y$.
    - Pure KMP GeoJSON FeatureCollection parser mapping `LineString` to `TrailPolyline` and `Point` to `TrailWaypoint`.
    - Procedural alpine mountain pass generator (`createSampleAlpineTrail`) with contour loops and tactical waypoints.
  - Implemented `TrailMapManager.kt`:
    - Thread-safe `Mutex` coordinator exposing reactive `StateFlow<TrailMapSnapshot>`.
    - Sliding-window breadcrumbs per rider (up to 100 points, filtered by $\ge 10$m delta to conserve memory).
    - Dynamic projection recalculation upon observer movement, vehicle heading change, or radar range zoom cycling.

### 2.51 Subsystem Bridge Wiring & Reactive Presentation Integration (Sprint 18 - ICA-1802)
- **Challenge**: Seamlessly binding offline vector map geometry and radar projections across multiplatform presentation layers (`HomeViewModel.kt`, Android services, and Native iOS SwiftUI `IntercomViewModel`) while maintaining reactive state flows, zero memory leaks, and clean life-cycle subscriptions.
- **Solution**:
  - `IntercomHardwareBridge.kt`: Added `trailMapSnapshot: StateFlow<TrailMapSnapshot>`, `updateTrailMapSnapshot()`, and `trailMapManager` injection in `attachSubsystems()`.
  - `AndroidPlatformBridge.kt`: Connected `trailMapManager.snapshot` to bridge state flow and exposed for background navigation routing.
  - `IntercomBridge.kt` (`iosMain`): Exposed `observeTrailMapSnapshot` callback returning `CancellationHandle` for SwiftUI view lifecycle safety (Rule 1), alongside radar zoom cycling (`cycleRadarZoom()`, `setRadarZoom()`) and sample trail loader.
  - `HomeUiState.kt` / `HomeViewModel.kt`: Centralized `trailMapSnapshot` into `uiState`, automatically streaming local Android/iOS GPS coordinates and incoming over-the-air peer GPS frames to `trailMapManager` for real-time relative radar projection.
### 2.52 Compose Multiplatform Radar Topo Contour & Vector Trail Overlay (Sprint 18 - ICA-1803)
- **Challenge**: Rendering offline vector paths, contour lines, waypoints, and peer breadcrumbs directly on top of the Proximity Radar canvas in Compose Multiplatform without degrading rendering performance, causing frame drops during active voice streaming, or breaking the strict single-viewport zero-scroll mobile handlebar layout constraint.
- **Solution**:
  - `ProximityRadarView.kt`:
    - Layered vector canvas drawing underneath concentric radar rings:
      - Topographic elevation contours: Subtle dashed lines with elevation label pills (30% opacity).
      - Neon trail tracks: Cyan glow polyline with directional segments and path vertices.
      - Rider breadcrumb dots: Rolling 100-point history per peer rendered with color accents and fading tails.
      - Tactical waypoints: Projected symbols for Water, Summit, Camp, Hazard, and Waypoint with distance/bearing labels.
    - Interactive tactile range zoom badge: Upper right corner button cycling `500m` -> `1.5km` -> `5.0km` ranges.
  - `OfflineTrailCockpitSheet.kt`:
    - Full-featured tactical dark sheet modal with elevation metrics (max, min, current, total ascent), active trail polyline stats, distance, and nearest waypoint distance/bearing.
    - Radar zoom switcher chips, vector overlay toggle, and sample trail loader.
    - Scrollable waypoint roster with type icons, elevation badges, and coordinates.
  - `HomeScreen.kt`:
    - Wired `trailMapSnapshot`, `onCycleZoom`, and `onOpenTrailSheet` into `ProximityRadarView`.
    - Added quick "Trail" toggle/launcher button in the Top Quick Actions row next to "Relay".
### 2.53 Native iOS SwiftUI Radar Topographic Overlay & Offline Trail Cockpit Sheet (Sprint 18 - ICA-1804)
- **Challenge**: Delivering identical vector trail map capabilities on iOS while maintaining 100% native SwiftUI without any Compose Multiplatform code or external mapping SDKs (Rule 1), rendering vector contours and paths efficiently using SwiftUI `Path`, and providing seamless cockpit integration with `IntercomViewModel`.
- **Solution**:
  - `IntercomViewModel.swift`:
    - Exposed `@Published var trailMapSnapshot: TrailMapSnapshot?` and `@Published var showTrailSheet: Bool`.
    - Bound `bridge.observeTrailMapSnapshot` callback safely to `@MainActor` with `CancellationHandle` registered in lifecycle deinit array.
    - Added Swift controls: `cycleRadarZoom()`, `setRadarZoom()`, `setTrailOverlayEnabled()`, `loadSampleAlpineTrail()`, `toggleTrailSheet()`.
    - Fed live GPS fixes into `bridge.updateTrailLocalGps` for dynamic vector recalculations.
  - `OfflineTrailCockpitSheet.swift`:
    - 100% Native SwiftUI modal (`#14181D` dark tactical theme) compliant with Rule 1.
    - Elevation metrics grid (Current Alt, Lead Rider Delta, Total Distance, Total Ascent).
    - 3-segment radar range zoom buttons (`500m`, `1.5km`, `5.0km`) and vector overlay toggle.
    - Next waypoint guidance banner with distance callout, bearing orientation, and type icon.
    - Scrollable tactical waypoint roster with custom icons (💧 Water, ⛺ Camp, ⚠️ Hazard, 🏔️ Summit, 📍 Marker), elevations, and descriptions.
  - `ContentView.swift`:
    - Upgraded `TacticalVectorRadarView` with SwiftUI `Path` rendering of topographic contours (dashed strokes) and neon cyan trail polyline segments.
    - Plotted rider breadcrumb dots and waypoint pins.
    - Embedded tactile zoom range switcher badge in the radar corner.
    - Integrated "Trail" launcher button in `TacticalAvionicsBar`.

### 2.54 Pure KMP Chunked Mesh File Transfer & GPX/GeoJSON Protocol Engine (Sprint 19 - ICA-1901)
- **Challenge**: BLE mesh MTU limits (single packets ~128 bytes) prevent sending monolithic GPX track files (>10 KB to 100 KB) in a single packet. Third-party XML parsers or Java `java.util.zip.Adler32` break KMP purity across iOS Simulator and native targets (Rules 2 & 3). Flooding RF channels without pacing creates severe packet drops.
- **Solution**:
  - `MeshFileModels.kt`:
    - Pure KMP RFC 1950 `Adler32` checksum using 16-bit low/high sum modulo 65521 arithmetic with zero Java/C platform dependencies.
    - MTU-compliant 100-byte data slicing yielding total chunk packets safely under BLE characteristic limits.
    - Defined `FileTransferType` (`GPX`, `GEOJSON`, `WAYPOINT_PACK`), `FileTransferProgress`, `FileTransferChunk`, and `FileTransferAck`.
  - `MeshFileTransferEngine.kt`:
    - Pure KMP GPX 1.1 exporter and regex-based track point / waypoint parser extracting `<trkpt lat="..." lon="...">`, `<ele>`, `<name>`, and `<wpt>`.
    - RFC 7946 GeoJSON `FeatureCollection` LineString exporter.
    - `ReassemblyBuffer`: Out-of-order chunk assembly, missing index tracking, and Adler32 payload validation upon completion.
  - `MeshFileTransferManager.kt`:
    - Mutex-protected transmission manager with reactive `activeTransfers: StateFlow<List<FileTransferProgress>>` and `storedTrails: StateFlow<List<TrailPolyline>>`.
    - Sliding-window paced transmission queue with selective NACK retransmission for lossy mesh hops.
    - Local trail storage library and direct text import.

### 2.55 Subsystem Hardware Bridge Wiring & Reactive State Management for Mesh Trail Sharing (Sprint 19 - ICA-1902)
- **Challenge**: Integrating `MeshFileTransferManager` across Compose Multiplatform (`sharedUI`), Android platform foreground service, and 100% native iOS SwiftUI (`iosApp`) while enforcing Rule 1 (`CancellationHandle` observers tied to SwiftUI lifecycle, zero Compose imports on iOS) and maintaining non-blocking coroutines without thread locks during file reassembly.
- **Solution**:
  - `IntercomHardwareBridge.kt` & `AndroidPlatformBridge.kt`:
    - Exposed reactive `fileTransfers: StateFlow<List<FileTransferProgress>>` and `storedTrails: StateFlow<List<TrailPolyline>>`.
    - Wired bidirectional hooks: outbox converts typed `FileTransferChunk` and `FileTransferAck` into `MeshPacket.Control` with `ControlType.FILE_CHUNK` (0x0B) and `ControlType.FILE_ACK` (0x0C); inbound parser decodes payloads and feeds into `handleIncomingChunk` / `handleIncomingAck`.
  - `IntercomBridge.kt` (`iosMain`):
    - Instantiated `fileTransferManager` in Swift bridge with `observeFileTransfers` and `observeStoredTrails` returning `CancellationHandle` for safe SwiftUI lifecycle integration.
    - Exposed Swift controls: `sendTrailOverMesh`, `cancelFileTransfer`, `importTrailFromText`, `deleteStoredTrail`.
  - `HomeViewModel.kt` & `HomeUiState.kt`:
    - Synchronized `activeTransfers`, `storedTrails`, and `showTrailManagerSheet`.
    - Added UI actions: `BeamActiveTrail`, `CancelTransfer`, `ImportTrailText`, `SelectStoredTrail`, `DeleteStoredTrail`, `ToggleTrailManagerSheet`.
    - On completed trail receipt, automatically loads into `trailMapManager` for real-time vector cockpit HUD projection.

### 2.56 Compose Multiplatform Trail File Manager & Over-The-Air Beam Cockpit Sheet (Sprint 19 - ICA-1903)
- **Challenge**: Displaying a multi-faceted off-grid file transfer center (stored GPX/GeoJSON trail catalog, live chunk-by-chunk transmission progress gauges, manual file import, and 1-tap beam trigger) within a handlebar RAM-mount mobile view without causing global scrolling conflicts or blocking essential intercom telemetry (Rule 1 & Zero-Scroll contract).
- **Solution**:
  - `TrailFileManagerSheet.kt`:
    - Constrained dialog sheet height to `0.88f` with internal bounded `LazyColumn` for route cards.
    - Rendered trail cards showing difficulty ratings (`EASY`, `MODERATE`, `HARD`, `EXPERT`), total distance (km), elevation ascent (m), waypoint counts, and active selection indicator.
    - Live transmission cards with active progress bars, percent complete, chunk counters (`X / Y chunks`), direction badges (`OUTGOING` / `INCOMING`), and cancellation buttons.
    - Top header actions featuring format selector (`GPX` vs `GEOJSON`) and 1-tap "BEAM TO CONVOY" actuator.
    - Modal GPX/GeoJSON raw text import modal dialog for rapid offline route pasting.
  - `OfflineTrailCockpitSheet.kt`:
    - Integrated prominent "⚡ OTA ROUTES" launcher button in the cockpit header, offering instant routing drawer access alongside the radar overlay toggle.
  - `HomeScreen.kt`:
    - Exposed "OTA / Routes" launcher in the Top Quick Actions row.
    - Full dialog orchestration binding ViewModel state and actions without blocking ongoing audio streaming.

### 2.57 Native iOS SwiftUI Trail File Manager & Over-The-Air Beam Sheet (Sprint 19 - ICA-1904)
- **Challenge**: Providing a 100% native SwiftUI file transmission and route management center without breaking Rule 1 (zero Compose Multiplatform imports on iOS), ensuring reactive UI binding to KMP flows without memory leaks, and automatically reflecting completed trail downloads onto the vector radar map.
- **Solution**:
  - `TrailFileManagerSheet.swift`:
    - Pure SwiftUI view rendering stored trail catalog, active route card, live OTA chunk transmission cards, and raw GPX/GeoJSON text import modal.
    - Dynamic progress gauges with percent and chunk count (`X / Y chunks`), direction badges (`OUTGOING` / `INCOMING`), and cancellation buttons.
    - 1-tap "BEAM TO CONVOY" button with tactile feedback (`UIImpactFeedbackGenerator`).
    - Difficulty rating badges, distance (`km`), elevation gain (`m`), and waypoint metrics.
  - `IntercomViewModel.swift`:
    - Exposed `@Published` state variables (`activeTransfers`, `storedTrails`, `showTrailManagerSheet`).
    - Bound `observeFileTransfers` and `observeStoredTrails` via thread-safe `CancellationHandle` objects automatically cleaned up on `deinit`.
    - Added UI action methods (`beamActiveTrail`, `beamTrail`, `cancelTransfer`, `importTrailText`, `deleteStoredTrail`, `selectStoredTrail`).
  - `OfflineTrailCockpitSheet.swift` & `ContentView.swift`:
    - Added "OTA ROUTES" quick launcher button in the cockpit header and `TacticalAvionicsBar`.
    - Bound sheet presentation via `.sheet(isPresented: $vm.showTrailManagerSheet)`.
  - `IntercomBridge.kt`:
    - Added `loadTrail` helper method.
    - Connected `fileTransferManager.completedTrailReceived` to automatically load newly completed trail downloads into `trailMapManager` for real-time vector HUD projection.

### 2.58 Pure KMP Convoy Geofencing, Hazard Waypoint Radar Alarms & Convoy Split Engine (Sprint 20 - ICA-2001)
- **Challenge**: In backcountry and off-grid mountain rides, riders frequently get separated from the pack or encounter trail hazards (downed trees, washouts, narrow passes). Traditional geofencing relies on cloud APIs or OS location frameworks (`CoreLocation` geofences, Google Play Geofencing API) which fail without cellular data, consume excessive battery, or lack convoy-relative dynamic distance tracking. Furthermore, convoy cluster bifurcation (the group splitting into two detached packs) is undetectable by static point geofences.
- **Solution**:
  - `ConvoyGeofenceModels.kt`:
    - Defined `GeofenceTetherStatus` (`IN_BOUNDS`, `WARNING`, `BREACH`, `DISCONNECTED`).
    - Defined `RiderTetherAlert`, `HazardProximityAlert`, `ConvoySplitWarning`, and `ConvoyGeofenceSnapshot`.
    - Defined `GeofenceAlarmType` (`TETHER_WARNING`, `TETHER_BREACH`, `HAZARD_APPROACH`, `CONVOY_SPLIT`, `TETHER_RESTORED`) and compact wire packet `GeofenceAlarmPacket`.
  - `MeshTransport.kt`:
    - Added `GEOFENCE_ALARM` (`0x0D`) to `ControlType` for multiplexed BLE mesh transmission.
  - `ConvoyGeofenceEngine.kt`:
    - Pure KMP Great-Circle Haversine distance engine: tracks dynamic distance between local rider and lead rider (or nearest peer if lead is undefined). Triggers `WARNING` at >300m and `BREACH` at >600m.
    - Proximity Radar: evaluates proximity to known trail hazard waypoints (obstacles, hazards, steep terrain) within 150m, computing relative bearing ($-180^\circ \dots +180^\circ$) and approach/recede trends based on speed vector.
    - Single-linkage clustering cluster bifurcation detection: detects when the convoy splits into disjoint clusters separated by >500m gaps, advising tail or sweep repositioning.
    - Alarm wire framing: automatically constructs `GeofenceAlarmPacket` with JSON serialization (`Json { ignoreUnknownKeys = true; encodeDefaults = true }`) dispatched through `outgoingAlarmPackets` channel.
    - StateFlow telemetry: exposes `geofenceState: StateFlow<ConvoyGeofenceSnapshot>` for immediate consumption by HUD viewmodels.

### 2.59 Smart Battery Duty-Cycle Governor & Motion-Aware Power Mode (Sprint 20 - ICA-2002)
- **Challenge**: Off-grid backcountry rides run 6 to 12 hours on single smartphone batteries without access to chargers. Leaving BLE radios scanning and advertising at 100% duty cycle when the battery drops below 30% quickly drains the phone to 0%, stranding riders without communications. However, dumb timers that blindly shut off radios break critical emergency calls, and waking phones from deep sleep without physical interaction causes missed alerts.
- **Solution**:
  - `PowerStateModels.kt`:
    - Defined `PowerPolicy` (`NORMAL`, `CONSERVATIVE`, `EXTREME_SURVIVAL`, `REST_STOP_SLEEP`, `EMERGENCY_OVERRIDE`).
    - Configured `RadioDutyCycleProfile` providing parameterized scan windows/intervals, advertising intervals, GPS beacon rates, and audio profile ceilings.
    - Configured `GovernorConfig` providing configurable thresholds: `<30%` triggers `CONSERVATIVE` (50% duty cycle, medium audio ceiling); `<15%` triggers `EXTREME_SURVIVAL` (20% duty cycle, low audio ceiling).
    - Defined `PowerGovernorState` and serializable `PowerStatusPacket` for multi-node telemetry.
  - `MeshTransport.kt`:
    - Added `POWER_STATUS` (`0x0E`) to `ControlType` for multiplexed BLE mesh telemetry.
  - `BatteryGovernor.kt`:
    - Pure KMP autonomous governor with thread-safe `Mutex` synchronization.
    - Motion-aware rest stop watchdog: monitors speed ($<= 3.0\text{ km/h}$) and stillness. If stillness persists for $>10\text{ minutes}$ (600,000ms), smoothly transitions to `REST_STOP_SLEEP` (5% duty cycle, ultra-low audio, 60s GPS beacon).
    - Multi-trigger instant wake: automatically wakes immediately to battery-appropriate tier upon motion resumption ($\ge 5.0\text{ km/h}$), handlebar PTT button actuation (`onPttPressed()`), or charger connection.
    - Unconditional Emergency SOS preemption: active emergency broadcasts (`updateEmergencyStatus(true)`) instantly force `EMERGENCY_OVERRIDE`, overriding low battery and sleep modes with full 100% duty cycle and maximum audio bandwidth until the emergency clears.

---

## 🗂️ 3. Monorepo File & Component Index

```
Intercom-Alpha/
├── app/
│   ├── androidApp/          # Android Application (Jetpack Compose, Navigation 3, Koin)
│   │   ├── src/main/kotlin/org/nowni/intercom_alpha/
│   │   │   ├── IntercomAlphaApplication.kt  # Koin initialization
│   │   │   └── MainActivity.kt             # Clean entrypoint delegating to IntercomAlphaApp
│   ├── iosApp/              # 100% Native SwiftUI Application
│   │   └── iosApp/
│   │       ├── ContentView.swift           # Primary cockpit UI, PTT gesture, level meters
│   │       ├── IntercomViewModel.swift     # @MainActor bridge consumer
│   │       └── QrScannerView.swift         # AVFoundation offline camera QR scanner
│   ├── sharedLogic/         # Core KMP Business Logic (Android, iOS Native, JVM, Wasm)
│   │   ├── commonMain/.../
│   │   │   ├── audio/AudioEngine.kt        # PCM streaming, VOX, RMS metering contracts
│   │   │   ├── mesh/MeshTransport.kt       # MeshPacket hierarchy & GATT UUIDs
│   │   │   ├── mesh/PacketDeduplicator.kt  # Sliding-window cache for broadcast storm suppression
│   │   │   ├── mesh/PacketCipher.kt        # Pure Kotlin RFC 8439 ChaCha20-Poly1305 AEAD
│   │   │   ├── group/GroupManager.kt       # INTERCOM:v1: QR onboarding & leadership state
│   │   │   └── headset/HeadsetManager.kt   # Bluetooth SCO & PTT button contracts
│   │   ├── androidMain/...                 # Android AudioRecord, BluetoothGatt, SCO manager
│   │   ├── iosMain/...                     # iOS AVAudioEngine, CoreBluetooth, IntercomBridge
│   │   └── commonTest/...                  # CrossPlatformGattMeshTest & PacketCipherTest
│   └── sharedUI/            # Compose Multiplatform UI components (Android + Desktop)
│       └── commonMain/.../navigation/
│           ├── AppNavGraph.kt              # Navigation 3 backstack and route displays
│           └── AppModule.kt                # Koin sharedUiModule declaring ViewModels
├── core/                    # Shared polymorphic protocol contracts (SignalingProtocol.kt)
├── server/                  # Ktor WebSocket Server for cloud fallback & SFU relay
└── docs/                    # Central engineering documentation
    ├── PRD.md               # Product Requirements Document
    ├── ARCHITECTURE.md      # System Architecture Specification
    ├── RULES.md             # Non-negotiable engineering rules & governance
    ├── DESIGN.md            # UI/UX design tokens, PTT states, component specs
    ├── TASKS.md             # Master task matrix & roadmap
    ├── MEMORY.md            # Knowledge base, ADRs, blueprints, and gotchas
    └── architecture/        # Archify interactive diagrams & JSON specifications
```

---

## 🛠️ 4. Maintenance Commands

```bash
# Run 100% of multiplatform unit tests
./gradlew test

# Compile Android Debug APK
./gradlew :app:androidApp:assembleDebug

# Compile iOS Simulator ARM64 framework
./gradlew :app:sharedLogic:compileKotlinIosSimulatorArm64

# Rebuild AST Knowledge Graph
graphify update .

# Validate and deliver Archify architecture diagram
node .agents/skills/archify/bin/archify.mjs deliver architecture docs/architecture/intercom-alpha.architecture.json docs/architecture/intercom-alpha-architecture.html --quality showcase --json
```
