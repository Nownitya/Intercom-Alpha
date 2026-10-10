<div align="center">

# 🏍️ Intercom-Alpha

**Off-Grid BLE Mesh Multiplatform Voice Intercom**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-purple.svg?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20Desktop%20%7C%20Web%20%7C%20Server-blue.svg?style=flat-square)](https://kotlinlang.org/docs/multiplatform.html)
[![Android](https://img.shields.io/badge/Android-API%2033+-green.svg?style=flat-square&logo=android)](https://developer.android.com)
[![iOS](https://img.shields.io/badge/iOS-16.0+%20(100%25%20Native%20SwiftUI)-black.svg?style=flat-square&logo=apple)](https://developer.apple.com/swiftui/)
[![Release](https://img.shields.io/badge/Release-v2.12.0-emerald.svg?style=flat-square)](https://github.com/Nownitya/Intercom-Alpha/releases)
[![Tests](https://img.shields.io/badge/Tests-327%20Passed%20(100%25)-brightgreen.svg?style=flat-square)](#-verification--quality-gates)
[![Crypto](https://img.shields.io/badge/AEAD-RFC%208439%20ChaCha20--Poly1305-orange.svg?style=flat-square)](https://datatracker.ietf.org/doc/html/rfc8439)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square)](./LICENSE)

*Zero-infrastructure, peer-to-peer voice communication for motorcyclists, outdoor expeditions, search-and-rescue, and tactical field teams when cellular and internet connectivity are unavailable.*

[Product Requirements (PRD)](./docs/PRD.md) • [Technical Architecture (TRD)](./docs/TRD.md) • [Business Requirements (BRD)](./docs/BRD.md) • [System Architecture](./docs/ARCHITECTURE.md) • [Roadmap](./docs/TASKS.md)

</div>

---

## ⚡ Core Capabilities (v2.12.0)

* **📡 Zero-Infrastructure Decentralized Mesh**: Peer-to-peer Bluetooth Low Energy (BLE) flooding relay with managed Time-To-Live ($\text{TTL}=3$). No central hub, no cellular towers, and zero single point of failure.
* **🔀 Automated Multi-Path Failover & Stream Hedging (Sprint 25)**: Pure KMP link quality arbiter scoring RSSI, packet loss, and jitter ($Q_{link}$). Automatically hedges voice frames over BLE Mesh and Cellular WebSocket SFU relays during RF disconnects with zero dropped words.
* **🎚️ Airtime QoS Priority Traffic Shaping (Sprint 24)**: Token-bucket rate limiting and strict priority queueing (`Priority 0: EMERGENCY` instant preemption, `Priority 1: VOICE`, `Priority 2: CONTROL`, `Priority 3: TELEMETRY`).
* **🔐 End-to-End RFC 8439 Encryption**: Pure Kotlin multiplatform implementation of ChaCha20-Poly1305 AEAD and SHA-256 key derivation running identically across all targets with zero Java/C dependencies.
* **🔄 Sliding-Window Storm Suppression**: High-throughput `PacketDeduplicator` utilizing a 5,000ms sliding-window cache to eliminate cyclic forwarding storms.
* **📱 100% Native SwiftUI on iOS**: Strict architectural boundary—Compose Multiplatform is never targeted to iOS. The iOS client communicates with shared KMP logic via a reactive, memory-safe `IntercomBridge.kt` with cancellation handles.
* **🏍️ Glove-Friendly Cockpit HUD**: Jetpack Compose and native SwiftUI tactical dashboards featuring glove-friendly touch targets, OLED true-black theme (`#0A0D10`), and real-time avionics telemetry.
* **🎧 Bluetooth SCO Headset Integration**: Native audio routing to motorcycle helmet communicators (Cardo, Sena) with hardware button triggers.
* **🗺️ Tactical Vector Terrain & OTA Route Sharing**: Offline map tile overlays, dynamic convoy geofencing, breadcrumb tracking, and chunked over-the-air BLE GPX/GeoJSON sharing.
* **🚨 Crash Impact Detection & eCall SOS**: Multi-axis IMU collision shock detection (>6.5g) triggering automated high-priority emergency SOS voice broadcasts.

---

## 🏛️ System Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PHYSICAL HARDWARE LAYER                         │
│             Helmet Headset / Bluetooth SCO / Hardware Buttons          │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │                                │
┌───────────────────▼──────────────┐   ┌─────────────▼───────────────────┐
│          Android Client          │   │            iOS Client           │
│     (Jetpack Compose Cockpit)    │   │      (100% Native SwiftUI)      │
└───────────────────┬──────────────┘   └─────────────┬───────────────────┘
                    │                                │ (IntercomBridge.kt)
┌───────────────────▼────────────────────────────────▼───────────────────┐
│                    KMP SHARED LOGIC (:app:sharedLogic)                 │
│  ┌───────────────────────┐  ┌────────────────┐  ┌───────────────────┐  │
│  │ MultiPathFailover     │  │ Airtime QoS    │  │ Audio DSP Engine  │  │
│  │ Link Quality Arbiter  │  │ Traffic Shaper │  │ Jitter / PLC / VOX│  │
│  └───────────┬───────────┘  └───────┬────────┘  └─────────┬─────────┘  │
│              │                      │                     │            │
│  ┌───────────▼──────────────────────▼─────────────────────▼─────────┐  │
│  │ Pure Kotlin RFC 8439 AEAD Cipher (ChaCha20-Poly1305 & KeyRatchet) │  │
│  └──────────────────────────────────┬────────────────────────────────┘  │
│                                     │                                  │
│  ┌──────────────────────────────────▼────────────────────────────────┐  │
│  │ Sliding-Window Loop Suppressor (PacketDeduplicator - TTL 5,000ms) │  │
│  └──────────────────────────────────┬────────────────────────────────┘  │
└─────────────────────────────────────┼──────────────────────────────────┘
                                      │
┌─────────────────────────────────────▼──────────────────────────────────┐
│                         TRANSPORT & NETWORKING                         │
│  ┌──────────────────────────────────┐  ┌────────────────────────────┐  │
│  │ Off-Grid BLE GATT Mesh Flooding  │  │ Cloud WebSocket SFU Relay  │  │
│  │ (TTL=3 Decentralized P2P Mesh)   │  │ (Ktor Hybrid Failover Hub) │  │
│  └──────────────────────────────────┘  └────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 🚀 Quick Start

### Android Debug Build
```bash
./gradlew :app:androidApp:assembleDebug
```

### iOS Simulator Framework Compilation
```bash
./gradlew :app:sharedLogic:compileKotlinIosSimulatorArm64
```

### Full Multiplatform Test Suite
```bash
./gradlew test
```

---

## 🧪 Verification & Quality Gates

Intercom-Alpha enforces strict automated gating on every turn:
1. **Multiplatform Test Suite**: 327 tests passing across shared logic, DSP, ciphers, and multi-node simulations.
2. **Platform Builds**: Android debug APK and iOS ARM64 simulator builds assemble with zero warnings.
3. **OpenCodeReview (OCR)**: Working copy pre-commit preview requires 0 critical, high, or medium findings.
4. **Graphify AST Knowledge Graph**: Automatically synchronized on code and documentation modifications.
