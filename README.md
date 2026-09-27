<div align="center">

# 🎙️ Intercom-Alpha

**Off-Grid BLE Mesh Multiplatform Voice Intercom**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20Desktop%20%7C%20Web%20%7C%20Server-blue.svg?style=flat-square)](https://kotlinlang.org/docs/multiplatform.html)
[![Android](https://img.shields.io/badge/Android-API%2026+-green.svg?style=flat-square&logo=android)](https://developer.android.com)
[![iOS](https://img.shields.io/badge/iOS-16.0+%20(Native%20SwiftUI)-black.svg?style=flat-square&logo=apple)](https://developer.apple.com/swiftui/)
[![Crypto](https://img.shields.io/badge/AEAD-RFC%208439%20ChaCha20--Poly1305-orange.svg?style=flat-square)](https://datatracker.ietf.org/doc/html/rfc8439)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square)](./LICENSE)

*Zero-infrastructure, peer-to-peer voice communication for motorcyclists, outdoor expeditions, search-and-rescue, and tactical field teams when cellular and internet connectivity are unavailable.*

[Interactive Architecture](./docs/architecture/intercom-alpha-architecture.html) · [Product Requirements](./docs/PRD.md) · [System Architecture](./docs/ARCHITECTURE.md) · [Engineering Rules](./docs/RULES.md) · [Roadmap](./docs/TASKS.md)

</div>

---

## ⚡ Core Capabilities

* **📡 Zero-Infrastructure Decentralized Mesh**: Peer-to-peer Bluetooth Low Energy (BLE) flooding relay with managed Time-To-Live ($\text{TTL}=3$). No central hub, no cellular towers, and no single point of failure.
* **🛡️ End-to-End RFC 8439 Encryption**: Pure Kotlin multiplatform implementation of ChaCha20-Poly1305 AEAD and SHA-256 key derivation running identically on Android, iOS, JVM, and Wasm with zero Java/C library dependencies.
* **🔄 Sliding-Window Storm Suppression**: High-throughput `PacketDeduplicator` utilizing a 5,000ms sliding-window cache to eliminate cyclic forwarding storms in complex multi-node topologies.
* **🍎 100% Native SwiftUI on iOS**: Strict architectural boundary—Compose Multiplatform is never targeted to iOS. The iOS client communicates with shared KMP logic via a reactive, memory-safe `IntercomBridge.kt` with cancellation handles.
* **🤖 Glove-Friendly Android Cockpit**: Jetpack Compose cockpit designed for tactile feedback, high-contrast OLED dark theme (`#0A0D10`), dual VU metering, and International Orange (`#E14A0E`) PTT button with tap-and-drag lock.
* **🎧 Bluetooth SCO Headset Integration**: Native audio routing to motorcycle helmet communicators (Cardo, Sena) with hardware button triggers.
* **📷 Instant Offline QR Pairing**: Pre-shared cryptographic group credentials encoded in `INTERCOM:v1:` QR codes for rapid field onboarding without network access.

---

## 🏛️ System Architecture

```
                  ┌─────────────────────────────────────────┐
                  │       Physical Voice / Hardware         │
                  │   Helmet Headset / Bluetooth SCO / Mic  │
                  └────────────┬───────────────────┬────────┘
                               │                   │
                     ┌─────────▼─────────┐       ┌─▼─────────────────┐
                     │   Android Client  │       │     iOS Client    │
                     │  (Jetpack Compose)│       │ (Native SwiftUI)  │
                     └─────────┬─────────┘       └─┬─────────────────┘
                               │                   │ (IntercomBridge)
  ═════════════════════════════╪═══════════════════╪═══════════════════════
  KMP SHARED LOGIC (`:app:sharedLogic`)            │
                               │                   │
                     ┌─────────▼───────────────────▼─────────┐
                     │       PCM Audio Pipeline (16kHz)      │
                     │        RMS / VOX Gate / Jitter        │
                     └───────────────────┬───────────────────┘
                                         │
                     ┌───────────────────▼───────────────────┐
                     │    Pure Kotlin RFC 8439 AEAD Cipher   │
                     │       (ChaCha20-Poly1305 & SHA256)    │
                     └───────────────────┬───────────────────┘
                                         │
                     ┌───────────────────▼───────────────────┐
                     │   Sliding-Window Packet Deduplicator  │
                     │     (5000ms TTL / Storm Suppression)  │
                     └───────────────────┬───────────────────┘
                                         │
                     ┌───────────────────▼───────────────────┐
                     │      Multi-Hop BLE Mesh Transport     │
                     │  GATT Server/Client · Flooding Relay  │
                     └───────────────────┬───────────────────┘
                                         │
  ═══════════════════════════════════════╪═════════════════════════════════
                                         │ (Peer-to-Peer BLE Radio)
                     ┌───────────────────▼───────────────────┐
                     │  Decentralized BLE Mesh Radio Network │
                     │   Peer A <──> Peer B <──> Peer C ...  │
                     └───────────────────────────────────────┘
```

> 💡 **Explore the complete interactive architecture**: View the standalone [Archify System Diagram](./docs/architecture/intercom-alpha-architecture.html) with trace animations, semantic radar, and exportable vector blueprints.

---

## 📦 Monorepo Structure

```
Intercom-Alpha/
├── app/
│   ├── androidApp/          # Android App (Jetpack Compose, Navigation 3, Koin, Foreground Service)
│   ├── iosApp/              # 100% Native SwiftUI Application (ContentView.swift, QrScannerView.swift)
│   ├── desktopApp/          # Desktop JVM runner
│   ├── webApp/              # WebAssembly (Wasm) & JS browser target
│   ├── sharedLogic/         # Core KMP Business Logic (Mesh, Audio, Crypto, Group, Headset)
│   └── sharedUI/            # Compose Multiplatform UI components (Android & Desktop)
├── core/                    # Shared polymorphic serialization contracts (SignalingProtocol.kt)
├── server/                  # Ktor WebSocket server for cloud relay fallback & SFU bridging
├── docs/                    # Curated engineering documentation (PRD, Architecture, Rules, Tasks, Memory)
├── tasks/                   # Ralph autonomous story execution matrix (prd.json, progress.txt)
├── AGENTS.md                # Autonomous AI agent engineering contract
├── CHANGELOG.md             # Release & milestone changelog
├── CONTRIBUTING.md          # Contribution guidelines & DoD checklist
└── SECURITY.md              # Cryptographic disclosure policy
```

---

## 🚀 Quickstart & Building

### Prerequisites
* **Java Development Kit**: JDK 21 (Temurin / Azul recommended)
* **Android Development**: Android Studio Iguana+ / Android SDK 34 (`build-tools 34.0.0`)
* **iOS Development**: macOS Sonoma with Xcode 15.0+ (for `app/iosApp`)

### Clone & Build
```bash
# Clone the repository
git clone https://github.com/Nownitya/Intercom-Alpha.git
cd Intercom-Alpha

# Run all multiplatform unit tests
./gradlew test

# Build Android Debug APK
./gradlew :app:androidApp:assembleDebug

# Compile iOS KMP Multiplatform framework
./gradlew :app:sharedLogic:compileKotlinIosSimulatorArm64

# Run Ktor signaling server locally
./gradlew :server:run
```

---

## 🧪 Quality & Verification Gates

Intercom-Alpha enforces a non-negotiable **Definition of Done (DoD)** on every pull request:

```bash
# 1. Multiplatform Unit Tests (Core, Server, SharedLogic, SharedUI)
./gradlew test

# 2. Android Debug Compilation
./gradlew :app:androidApp:assembleDebug

# 3. iOS Simulator ARM64 Compilation
./gradlew :app:sharedLogic:compileKotlinIosSimulatorArm64

# 4. OpenCodeReview Delegation Gate
ocr delegate preview --format json
```

---

## 🗺️ Engineering Roadmap & Sprints

| Sprint | Subsystem Milestone | Status | Key Deliverables |
| :--- | :--- | :--- | :--- |
| **Sprint 01** | Core Networking & Protocol | 🟢 Closed | Polymorphic signaling protocol, Ktor WebSocket server, client contracts |
| **Sprint 02** | Android Audio Engine & UI | 🟢 Closed | `AudioRecord` streaming, BLE GATT transport, Compose cockpit UI |
| **Sprint 03** | iOS Audio & CoreBluetooth | 🟢 Closed | `AVAudioEngine` tap, CoreBluetooth dual-role, 100% Native SwiftUI |
| **Sprint 04** | Swift Bridge & QR Pairing | 🟢 Closed | Reactive `IntercomBridge.kt`, camera QR scanner, `INTERCOM:v1:` protocol |
| **Sprint 05** | Mesh Flooding & Crypto | 🟢 Closed | Multi-hop relay ($\text{TTL}=3$), sliding-window deduplication, pure RFC 8439 AEAD |
| **Sprint 06** | Background Services & Power | 🟡 Active | Android Foreground Service, Partial WakeLocks, iOS Background Audio & BT modes |
| **Sprint 07** | Audio DSP & Opus Codec | ⚪ Backlog | Opus KMP bindings, dynamic jitter buffering (50–200ms), PLC noise gate |
| **Sprint 08** | Field Testing & Production | ⚪ Backlog | 500m mesh benchmark, helmet wind noise intelligibility, battery drain tuning |

See [`docs/TASKS.md`](./docs/TASKS.md) for detailed task breakdowns and user stories.

---

## 📄 License

This project is licensed under the **Apache License, Version 2.0** — see the [LICENSE](./LICENSE) file for details.