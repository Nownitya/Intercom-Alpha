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
  - Exposed reactive `observeKeepAliveState` callback returning `CancellationHandle` to Swift.

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
