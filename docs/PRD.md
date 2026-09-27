# Product Requirements Document (PRD) — Intercom-Alpha

> **Document Version:** 1.0  
> **Status:** Active / Engineering Ready  
> **Last Updated:** 2026-09-27  
> **Repository:** `Intercom-Alpha`  
> **Obsidian Reference:** `03_Specifications/` & `PROJECT-SPEC.md`

---

## 🎯 1. Product Vision & Overview

### 1.1 Problem Statement
Motorcyclists, cyclists, and outdoor adventurers often ride in groups through remote environments (mountain passes, forest trails, tunnels, highways) where cellular data is either nonexistent, expensive, or highly unreliable. Existing commercial helmet communicators (e.g., Cardo, Sena) rely on proprietary, expensive hardware intercoms that cost $250–$400+ per unit and do not easily interoperate across brands.

### 1.2 Product Vision
**Intercom-Alpha** transforms standard smartphones into high-performance, decentralized, group voice communicators that operate **100% offline** via peer-to-peer Bluetooth Low Energy (BLE) mesh networks.

```
Rider opens app ➔ Creates or scans QR code ➔ Pairs Bluetooth helmet headset ➔ Real-time offline voice
```

- **Zero Accounts:** No email, phone number, password, or cloud signup.
- **Zero Internet Required:** All voice frames and control signaling route via local BLE GATT mesh.
- **Cross-Platform Interoperability:** Seamless, low-latency audio transmission between Android and iOS.
- **Battery-Centric:** Designed for 4+ hours of continuous background operation with the phone screen off in a pocket or tank bag.

---

## 👥 2. Target Audience & User Personas

| Persona | Environment | Primary Need | Critical Constraint |
| :--- | :--- | :--- | :--- |
| **Motorcycle Touring Groups** | Highways, mountain passes, tunnels | Low-latency voice communication between 2–8 riders | Must work with physical helmet buttons and glove touches |
| **Mountain Bike / Trail Riders** | Remote forests, canyons, downhill trails | Hands-free VOX voice activation without reaching for phone | Zero cellular coverage; high ambient wind/shock noise |
| **Overland & Convoy Drivers** | Remote backroads, desert tracks | Ad-hoc mesh range extension across multiple vehicles | Multi-hop packet relay between convoy vehicles |

---

## 🔑 3. Key Value Propositions & Design Pillars

1. **Instant Offline Onboarding**: A rider creates a group in 1 second; other riders join by scanning an offline QR code containing the group identifier and cryptographic key (`INTERCOM:v1:`).
2. **Multi-Hop Mesh Flooding**: Packets automatically hop across intermediate riders (TTL=3), extending effective communication distance well beyond direct point-to-point BLE range.
3. **Sliding-Window Loop Suppression**: Automatic deduplication caches drop looped packets within a 5,000ms window, preventing broadcast storms.
4. **End-to-End AEAD Encryption**: Audio and control packets are protected using RFC 8439 ChaCha20-Poly1305, ensuring untrusted relays cannot eavesdrop or tamper with voice frames.
5. **Native Platform UX**:
   - **Android**: Jetpack Compose, modern Navigation 3, and Koin Multiplatform.
   - **iOS**: 100% Native SwiftUI and AVFoundation (no shared Compose UI on iOS).

---

## 📋 4. Functional Requirements

### 4.1 Audio Engine & Transmission
- **FR-01 (Audio Capture & Playback)**: Capture microphone audio at 48kHz / 16-bit PCM little-endian; encode/compress frames into low-bandwidth profiles (16–64 kbps).
- **FR-02 (PTT Transmission Mode)**: Push-to-Talk via on-screen button (press, hold, drag to lock) and external Bluetooth media buttons (`KEY_VOLUME_UP`, `KEY_HEADSETHOOK`).
- **FR-03 (VOX Transmission Mode)**: Voice-activated transmission with configurable energy threshold (-20 dB to -60 dB) and hangover time (200–500 ms).
- **FR-04 (RMS Level Metering)**: Real-time audio metering (0.0 to 1.0) published to client UI for both input microphone and incoming peer audio.

### 4.2 Bluetooth & Mesh Networking
- **FR-05 (GATT Mesh Protocol)**: Unified GATT Service UUID `0000180d-0000-1000-8000-00805f9b34fb`, Characteristic UUID `00002a37-0000-1000-8000-00805f9b34fb`.
- **FR-06 (Simultaneous Peripheral & Central)**: Devices must advertise and scan simultaneously to form dynamic peer connections.
- **FR-07 (Multi-Hop Flooding Relay)**: Intermediate nodes automatically decrement packet TTL (from 3 down to 1) and re-broadcast packets to other connected peers.
- **FR-08 (Inner Packet Extraction)**: When receiving a relay packet, intermediate nodes extract the inner audio/control frame and dispatch it to local audio output.
- **FR-09 (Packet Deduplication)**: Sliding-window cache drops duplicate packets within 5,000 ms using composite keys `(senderId, sequence)`.

### 4.3 Group Management & Security
- **FR-10 (Offline Group Creation)**: Leader generates unique group ID and pre-shared cryptographic key.
- **FR-11 (QR Code Invite)**: Encode invite payload as `INTERCOM:v1:<base64(json)>` containing group ID, name, leader ID, and 24-hour expiration timestamp.
- **FR-12 (Packet Encryption)**: Encrypt audio and sensitive control payloads with RFC 8439 ChaCha20-Poly1305 using key derived from QR secret via FIPS 180-4 SHA-256.

### 4.4 Hardware & Headset Integration
- **FR-13 (Bluetooth SCO Routing)**: Route audio bidirectional stream through connected Bluetooth helmets / headsets via SCO audio profiles.
- **FR-14 (Physical Button Mapping)**: Intercept headset button events to trigger PTT_PRESS and PTT_RELEASE without waking the phone screen.

---

## ⚡ 5. Non-Functional Requirements & Performance Targets

| Metric | Target | Measurement Method |
| :--- | :--- | :--- |
| **1-Hop Audio Latency** | < 100 ms | Timestamp round-trip automated test |
| **3-Hop Relay Latency** | < 300 ms | Multi-node simulation benchmark |
| **Open-Air Range (1-Hop)** | ≥ 100 meters | Line-of-sight field testing |
| **Urban Range (1-Hop)** | ≥ 30 meters | Obstructed street field testing |
| **Battery Consumption** | < 30% per 4-hour ride | Continuous background recording & relay |
| **Memory Footprint** | < 80 MB resident RAM | Memory profiler during active mesh session |
| **Crash-Free Sessions** | > 99.5% | Play Console & TestFlight crash analytics |
| **Audio Intelligibility** | PESQ MOS > 3.5 | Clean voice reproduction under wind noise |

---

## 🚫 6. Out of Scope (v1.0)

- Cloud user accounts and profile synchronization.
- Video streaming or camera feeds.
- In-app text chat and image messaging.
- Cellular VoIP relay bridges (cellular fallback will be handled in v2.0).
- Public internet room discovery directories.

---

## 🏁 7. Release Criteria & Definition of Done (DoD)

1. **Multiplatform Test Suite**: 100% passing unit tests on JVM, Android, and iOS Simulator targets (`./gradlew test`).
2. **Build Success**: Clean build of Android debug APK and iOS framework compilation without warnings.
3. **Field Tested Range**: Reliable voice communication over $\ge 2$ hops with active TTL decrement.
4. **Screen-Off Background Execution**: Audio capture, relay, and playback survive screen lock and app backgrounding for $\ge 30$ minutes.
