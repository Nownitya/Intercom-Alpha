# Intercom-Alpha — Project Overview

> **Offline Mesh Voice Communication for Motorcycle/Bike Groups**
> 
> *Kotlin Multiplatform • Compose Multiplatform (Android/Desktop/Web) • SwiftUI (iOS)*
> *Bluetooth LE Mesh + WiFi Direct • Opus Codec • Zero-Internet P2P*

---

## 🎯 What Is This App?

**Intercom-Alpha** is a peer-to-peer voice communication system designed for motorcycle and bicycle riding groups. It enables riders to talk to each other **without any internet, cellular service, or infrastructure** — just their phones and Bluetooth headsets.

Think of it as a **modern, digital CB radio** that works through helmets, in tunnels, off-grid, and across platforms (Android ↔ iOS).

---

## 🌟 Vision

> **"Voice that just works — anywhere, any device, no setup required."**

| Principle | What It Means |
|-----------|---------------|
| **Offline-First** | No servers, no internet, no accounts. Pure P2P. |
| **Cross-Platform** | Android + iOS interoperate seamlessly via BLE Mesh. |
| **Headset-Native** | Built for Bluetooth headsets (PTT button, SCO audio, battery). |
| **Rider-Focused** | PTT + VOX, glove-friendly UI, 4+ hour battery life. |
| **Open & Private** | No tracking, no cloud, optional E2E encryption. |

---

## 🏗️ Architecture at a Glance

```
┌─────────────────────────────────────────────────────────────────┐
│                        USER INTERFACES                          │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────┐ │
│  │  Android App    │    │    iOS App      │    │ Desktop/Web │ │
│  │  (Compose)      │    │  (SwiftUI)      │    │  (Compose)  │ │
│  └────────┬────────┘    └────────┬────────┘    └──────┬──────┘ │
└───────────┼──────────────────────┼────────────────────┼────────┘
            ▼                      ▼                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                   SHARED LOGIC (KMP)                            │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │ Mesh     │ │ Audio    │ │ Headset  │ │ Group    │           │
│  │Transport │ │ Engine   │ │ Manager  │ │ Manager  │           │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘           │
└───────┼─────────────┼─────────────┼────────────┼────────────────┘
        ▼             ▼             ▼            ▼
┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐
│   ANDROID     │ │     iOS       │ │   DESKTOP     │ │     WEB       │
│  • BLE GATT   │ │ • Multipeer   │ │ • WebRTC*     │ │ • WebRTC*     │
│  • Nearby     │ │ • CoreBluetooth│ │ • WebAudio   │ │ • WebBluetooth│
│  • opus-android│ │ • libopus    │ │ • opus-jni   │ │ • opus-wasm   │
│  • AudioRecord│ │ • AVAudio     │ │ • JavaSound  │ │ • WebAudio    │
│  • Foreground │ │ • Background  │ │               │ │               │
│    Service    │ │    Modes      │ │               │ │               │
└───────────────┘ └───────────────┘ └───────────────┘ └───────────────┘
```
*Desktop/Web: Future signaling/relay support

---

## 🔑 Core Features

### 1. **Mesh Transport** (`MeshTransport`)
- **Primary**: Bluetooth LE Mesh (flooding, TTL=3 relay)
- **Upgrade**: WiFi Direct (Nearby Connections) for higher bandwidth
- **Cross-Platform**: Common packet format, Android ↔ iOS via BLE
- **Leaderless**: Any peer can join/leave; no central coordinator

### 2. **Audio Engine** (`AudioEngine`)
- **Codec**: Opus (5 profiles: 16/24/32/48/64 kbps)
- **Modes**: PTT (Push-to-Talk) + VOX (Voice-Activated)
- **Features**: DTX, FEC, PLC, adaptive jitter buffer
- **Profiles**:
  | Profile | Bitrate | Sample Rate | Channels | Use Case |
  |---------|---------|-------------|----------|----------|
  | ULTRA_LOW | 16 kbps | 16 kHz | Mono | Max range |
  | LOW | 24 kbps | 16 kHz | Mono | Long range |
  | **MEDIUM** | **32 kbps** | **24 kHz** | **Mono** | **Default** |
  | HIGH | 48 kbps | 24 kHz | Mono | Clear voice |
  | MUSIC | 64 kbps | 48 kHz | Stereo | Music share |

### 3. **Headset Manager** (`HeadsetManager`)
- **Audio Routing**: A2DP/HFP → SCO for low-latency voice
- **Button Mapping**: PTT, Volume, Call Answer/End, Voice Assistant
- **Battery Monitoring**: Real-time headset battery level
- **Auto-Reconnect**: Seamless reconnection on disconnect

### 4. **Group Manager** (`GroupManager`)
- **Invite System**: QR codes + alphanumeric codes (offline)
- **Roles**: Leader, members; leadership transfer
- **Config Sync**: Audio profile, encryption, relay settings
- **Expiry**: 24-hour invite codes with versioning

---

## 📁 Project Structure

```
Intercom-Alpha/
├── app/
│   ├── sharedLogic/          # KMP: Business logic (expect/actual)
│   │   ├── commonMain/       # Contracts: MeshTransport, AudioEngine, HeadsetManager, GroupManager
│   │   ├── androidMain/      # Android actuals: BLE GATT, Nearby, opus-android, AudioRecord/Track
│   │   └── iosMain/          # iOS actuals: MultipeerConnectivity, CoreBluetooth, libopus, AVAudioEngine
│   ├── sharedUI/             # Compose Multiplatform (Android, Desktop, Web)
│   ├── androidApp/           # Android entry point + Foreground Service
│   ├── iosApp/               # SwiftUI entry point + ViewModels
│   ├── desktopApp/           # Desktop (JVM) entry point
│   └── webApp/               # Web (Wasm/JS) entry point
├── core/                     # Pure Kotlin shared code (optional)
├── server/                   # Ktor backend (optional, signaling)
├── gradle/libs.versions.toml # Version catalog (single source of truth)
└── README.md
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|------------|
| **Language** | Kotlin 2.4.20 (Multiplatform) |
| **UI (Android/Desktop/Web)** | Compose Multiplatform 1.12.0, Material3 |
| **UI (iOS)** | Native SwiftUI + Swift 5.9 |
| **Navigation** | Navigation 3 (`navigation3-ui` 1.1.2) & Compose Navigation / SwiftUI NavigationStack on iOS |
| **Build** | Gradle 9.5.1 + Kotlin DSL |
| **Serialization** | kotlinx-serialization 1.8.0 (JSON) |
| **Async** | kotlinx-coroutines 1.11.0 |
| **Audio Codec** | Opus (opus-android 1.3.1 / libopus 1.3) |
| **Bluetooth (Android)** | Android BLE GATT Server/Client |
| **Bluetooth (iOS)** | CoreBluetooth GATT Peripheral/Central |
| **Dependency Injection** | Koin Multiplatform 4.0.2 (`koin-core`, `koin-android`, `koin-compose`, `koin-compose-viewmodel`) |
| **Testing** | JUnit, Kotlin Test, Compose Test, XCTest |

---

## 📱 Platform Capabilities

| Feature | Android | iOS | Desktop | Web |
|---------|---------|-----|---------|-----|
| **BLE Mesh** | ✅ GATT Server/Client | ✅ Peripheral/Central | ⚠️ Web Bluetooth | ⚠️ Web Bluetooth |
| **WiFi Direct** | ✅ Nearby Connections | ❌ | ❌ | ❌ |
| **MultipeerConnectivity** | ❌ | ✅ | ❌ | ❌ |
| **Opus Codec** | ✅ opus-android | ✅ libopus (CocoaPods) | ✅ JNI | ✅ Wasm |
| **Audio Capture** | ✅ AudioRecord | ✅ AVAudioEngine | ✅ JavaSound | ✅ WebAudio |
| **Audio Playback** | ✅ AudioTrack | ✅ AVAudioPlayer | ✅ JavaSound | ✅ WebAudio |
| **Headset SCO** | ✅ BluetoothHeadset | ✅ AVAudioSession | ❌ | ⚠️ Web Bluetooth |
| **Headset Buttons** | ✅ BroadcastReceiver | ✅ MPRemoteCommand | ❌ | ❌ |
| **Background Audio** | ✅ Foreground Service | ✅ Background Modes | ✅ | ❌ |
| **QR Scanning** | ✅ ML Kit | ✅ Vision | ✅ ZXing | ✅ Barcode API |

---

## 🚀 Quick Start

### Android
```bash
cd D:\Codes\Antigravity\Project4\Intercom-Alpha
./gradlew :app:androidApp:assembleDebug
# Install APK on device (API 24+)
# Grant: Location, Bluetooth, Microphone, Nearby Devices
```

### iOS
```bash
cd D:\Codes\Antigravity\Project4\Intercom-Alpha\app\iosApp
pod install
open IntercomApp.xcworkspace
# Build & run on PHYSICAL DEVICE (Simulator doesn't support Multipeer)
# Grant: Bluetooth, Local Network, Microphone
```

### Desktop (JVM)
```bash
./gradlew :app:desktopApp:run
```

### Web (Wasm)
```bash
./gradlew :app:webApp:wasmJsBrowserDevelopmentRun
```

---

## 🧪 Testing Checklist

### Must Pass Before Release
- [ ] **Android ↔ Android**: BLE mesh connect, audio flows both ways
- [ ] **iOS ↔ iOS**: Multipeer connect, audio flows both ways  
- [ ] **Android ↔ iOS**: BLE mesh connect (critical cross-platform test)
- [ ] **3+ Devices**: Flooding relay works (TTL=3)
- [ ] **Headset**: Audio routes to SCO, buttons work, battery shows
- [ ] **Background**: 30+ min with screen off, no disconnects
- [ ] **Range**: Open field >500m, Urban >100m, Forest >50m
- [ ] **Battery**: 4-hour ride simulation <30% drain

---

## 📚 Key Documentation (Obsidian Vault)

> Located at: `D:\ObsidianVault\Vault1\ObsidianVault\Android\Projects\Intercom-Alpha\`

| Document | Purpose |
|----------|---------|
| `README.md` | This file — project overview |
| `01-Projects/Build-Test.md` | Build commands, test procedures |
| `01-Projects/Testing-Checklist.md` | Complete functional + stress test matrix |
| `03-Resources/Android-BLE.md` | Android BLE/GATT/Nearby guide |
| `03-Resources/iOS-Multipeer.md` | iOS Multipeer/CoreBluetooth guide |
| `03-Resources/Opus-Codec.md` | Opus integration + tuning |
| `03-Resources/KMP-Setup.md` | KMP project configuration |
| `06-Decisions/README.md` | 8 ADRs (architecture decisions) |
| `05-Meta/Project-Charter.md` | Vision, scope, success criteria |
| `05-Meta/Roadmap.md` | 12-sprint roadmap to v1.0 |
| `09-MemPalace/Project-Map.md` | Mental model (helmet analogy) |
| `09-MemPalace/Codebase-Walkthrough.md` | Source tree guided tour |
| `09-MemPalace/Architecture-Viz.md` | Mermaid diagrams (system, packet flow, state machines) |

---

## 🎯 Current Status (Alpha Scaffold)

| Component | Status | Notes |
|-----------|--------|-------|
| KMP Project Structure | ✅ Done | Gradle, targets, version catalog |
| Shared Contracts | ✅ Done | 5 interfaces + data models |
| Android MeshTransport | ✅ Scaffold | BLE GATT advertise/scan/connect |
| iOS MeshTransport | ⏳ Pending | Multipeer + CoreBluetooth |
| Android AudioEngine | ⏳ Pending | opus-android integration |
| iOS AudioEngine | ⏳ Pending | libopus + AVAudioEngine |
| Android HeadsetManager | ⏳ Pending | BluetoothHeadset + SCO |
| iOS HeadsetManager | ⏳ Pending | AVAudioSession + CoreBluetooth |
| Compose UI (sharedUI) | ✅ Scaffold | PTT, profile selector, peer list |
| SwiftUI (iosApp) | ⏳ Pending | ViewModels + Views |
| Foreground Service | ⏳ Pending | Android background mic |
| iOS Background Modes | ⏳ Pending | Info.plist capabilities |
| QR Invite System | ⏳ Pending | Generation + scanning |
| Encryption (Noise) | ⏳ Pending | ADR-008 proposed |

---

## 🔗 Key Links

- **Codebase**: `D:\Codes\Antigravity\Project4\Intercom-Alpha\`
- **Obsidian Vault**: `D:\ObsidianVault\Vault1\ObsidianVault\Android\Projects\Intercom-Alpha\`
- **Android App**: `app/androidApp/`
- **iOS App**: `app/iosApp/`
- **Shared Logic**: `app/sharedLogic/`
- **Shared UI**: `app/sharedUI/`

---

## 📝 For New Contributors

1. **Read this file first** — understand the vision
2. **Read `05-Meta/Project-Charter.md`** — scope, risks, timeline
3. **Read `09-MemPalace/Codebase-Walkthrough.md`** — navigate the code
4. **Run Android build** — `./gradlew :app:androidApp:assembleDebug`
5. **Pick a task from `05-Meta/Roadmap.md`** — current sprint focus

---

*Last Updated: 2026-09-21*
*Version: Alpha Scaffold Complete — Ready for Build/Test/Iterate*