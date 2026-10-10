# Intercom-Alpha — Project Overview

> **Offline Mesh Voice Communication for Motorcycle/Bike Groups**  
> *Kotlin Multiplatform • Compose Multiplatform (Android/Desktop/Web) • Native SwiftUI (iOS)*  
> *Bluetooth LE Mesh + WiFi Direct + Cellular/SFU Relay • Pure KMP Audio Engine • Zero-Internet P2P*  
> **Current Version:** `v2.12.0` (Phase 23 / Sprint 25 Completed)

👉 **Project Hub**: [[Intercom-Alpha|Intercom-Alpha Project Hub]] • **Engineering Index**: [[INDEX|Control Tower]]

---

## 🎯 What Is This App?

**Intercom-Alpha** is a peer-to-peer voice communication system designed for motorcycle and bicycle riding groups. It enables riders to talk to each other **without requiring internet, cellular coverage, or infrastructure** — just their smartphones and Bluetooth helmet headsets.

Think of it as a **modern, digital tactical mesh intercom** that works through helmets, in remote mountain twisties, off-grid trails, tunnels, and across platforms (Android ↔ iOS). When groups drift apart beyond RF line-of-sight, the system dynamically bridges separated sub-clusters over cellular WebSockets to an SFU server with zero dropped audio frames.

---

## 👁️ Vision & Architectural Principles

> **"Voice that just works — anywhere, any device, zero manual setup."**

| Principle | Architectural Implementation |
|---|---|
| **Offline-First** | Pure peer-to-peer over BLE GATT flooding (TTL=3) and WiFi Direct. No servers required for local mesh. |
| **Strict Platform Isolation** | **Rule 1: No Shared UI on iOS**. Android uses Jetpack Compose; iOS uses 100% native SwiftUI via reactive `IntercomBridge.kt`. |
| **Multiplatform Logic** | All packet routing, deduplication, encryption, link arbiters, and failover state machines live in pure KMP (`commonMain`). |
| **Pure Cryptography** | RFC 8439 ChaCha20-Poly1305 AEAD cipher implemented in pure Kotlin with zero Java/JNI dependencies (`PacketCipher.kt`). |
| **Headset-Native** | Automatic Bluetooth SCO audio routing, helmet button PTT detection, and low-latency audio capture/playout. |
| **Automated Failover** | Multi-Path Link Quality Arbiter monitors RSSI, packet loss, and jitter; automatically triggers seamless Cellular/SFU hedging during RF dropouts. |

---

## 🏛️ Architecture at a Glance

```
┌────────────────────────────────────────────────────────────────────────┐
│                        USER INTERFACES                                 │
│  ┌───────────────────────┐  ┌──────────────────────┐  ┌─────────────┐  │
│  │     Android App       │  │       iOS App        │  │ Desktop/Web │  │
│  │  (Jetpack Compose)    │  │  (100% Native SwiftUI)│ │  (Compose)   │  │
│  └───────────┬───────────┘  └──────────┬───────────┘  └──────┬──────┘  │
└──────────────┼─────────────────────────┼─────────────────────┼─────────┘
               │                         │ (IntercomBridge.kt) │
┌──────────────▼─────────────────────────▼─────────────────────▼─────────┐
│                   SHARED MULTIPLATFORM LOGIC (KMP)                     │
│  ┌───────────────┐  ┌───────────────┐  ┌──────────────┐ ┌────────────┐ │
│  │ Multi-Path    │  │ Mesh Routing  │  │ Audio Engine │ │ Packet     │ │
│  │ Link Arbiter  │  │ & Deduplicator│  │ Jitter & PLC │ │ Cipher     │ │
│  │ (Failover SM) │  │ (AODV / QoS)  │  │ (Noise Gate) │ │ (ChaCha20) │ │
│  └───────┬───────┘  └───────┬───────┘  └──────┬───────┘ └─────┬──────┘ │
└──────────┼──────────────────┼─────────────────┼───────────────┼────────┘
           │                  │                 │               │
┌──────────▼──────────────────▼─────────────────▼───────────────▼────────┐
│                        PLATFORM TRANSPORTS                             │
│  ┌─────────────────────────┐  ┌────────────────────────┐  ┌──────────┐ │
│  │     Android Engine      │  │       iOS Engine       │  │  Server  │ │
│  │ • BluetoothGatt Dual    │  │ • CoreBluetooth Cent/Per│  │ • Ktor   │ │
│  │ • AudioRecord/Track PCM │  │ • AVAudioEngine Tap    │  │   WS/SFU │ │
│  │ • Foreground Service    │  │ • Background Modes     │  │   Relay  │ │
│  └─────────────────────────┘  └────────────────────────┘  └──────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

---

## ⚡ Core Capabilities (v2.12.0)

### 1. **Mesh Transport & Routing**
- **BLE Mesh Flooding**: Leaderless TTL=3 mesh relaying across unlimited hops.
- **Sliding-Window Deduplication**: 5,000ms TTL sliding cache suppressing packet loops and broadcast storms (`PacketDeduplicator.kt`).
- **Airtime QoS Traffic Shaping**: Dynamic token-bucket prioritization protecting emergency voice over sensor/telemetry payloads (`AirtimeQoSTrafficShaper.kt`).
- **AODV-Lite Directed Routing**: Optimal point-to-point pathing reducing redundant RF airtime contention (`MeshRoutingEngine.kt`).

### 2. **Multi-Path Failover & Stream Hedging (Sprint 25)**
- **Link Quality Arbiter**: Continuous scoring of BLE signal strength (RSSI), packet loss, and jitter (`MultiPathFailoverArbiter.kt`).
- **Automated State Machine**: Transitions through `BLE_PRIMARY` → `DEGRADED_HEDGING` → `CELLULAR_ACTIVE` → `RECOVERING`.
- **Zero-Drop Dual Streaming**: Concurrently hedges critical audio frames across both BLE and Cellular SFU relays during transient RF boundaries.

### 3. **Audio DSP & Codec Pipeline**
- **Adaptive Jitter Buffer**: RFC 3550 variance delay estimation adapting between 40ms and 150ms (`AdaptiveJitterBuffer.kt`).
- **Packet Loss Concealment (PLC)**: Pitch extrapolation with -3 dB decay preventing acoustic pop artifacts during lost packets (`PacketLossConcealment.kt`).
- **Adaptive Noise Gate**: Dual-threshold hysteresis (-35 dBFS / -42 dBFS) attenuating helmet wind noise (`NoiseGate.kt`, `WindNoiseFilter.kt`).
- **Pure KMP Multi-Rate Codec**: IMA ADPCM compression fitting audio frames cleanly within single BLE GATT MTU boundaries.

### 4. **Hardware & Power Autonomy**
- **Continuous Screen-Off Operation**: Android Foreground Service with Partial WakeLock and iOS `UIBackgroundModes` guaranteeing 4+ hours of active mesh relay.
- **Autonomous Power Governor**: Dynamically scales advertising intervals and transmit power according to battery budget and node speed.

---

## 📚 Key Obsidian Hubs
- **Feature Specifications**: [[03_Specifications/Features/INDEX|Complete Feature Specifications MOC]] (`docs/FEATURES.md`)
- **Full Roadmap**: [[01_Management/Roadmap|23-Phase Multiplatform Roadmap]]
- **Kanban Board**: [[01_Management/Kanban|Agile Sprint Board]]
- **Architecture MOC**: [[02_Architecture/Architecture-MOC|Architecture Map of Content]]
- **Active Sprints**: [[01_Management/Sprints/Active/Sprint-26-Convoy-Geofence-And-Mesh-Governor|Sprint 26 Active]]

