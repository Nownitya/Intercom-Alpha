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
