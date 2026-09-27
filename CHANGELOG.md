# Changelog — Intercom-Alpha

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased] — Sprint 06: Background Services & OS Power Management

### Planned / In Progress
- **ICA-601**: Android Foreground Service (`IntercomForegroundService.kt`) with sticky notification and Bluetooth SCO lifecycle lock.
- **ICA-602**: Android Partial WakeLock & Battery Optimization helper (`PowerManagerHelper.kt`).
- **ICA-603**: iOS Background Audio & CoreBluetooth Peripheral/Central Background Modes in `Info.plist` & `IntercomAlphaApp.swift`.
- **ICA-604**: iOS Background Task Assertions & Keep-Alive (`BackgroundKeepAlive.kt`).
- **ICA-605**: 30-minute screen-off packet continuity verification test.

---

## [0.5.0] - 2026-09-27 — Sprint 05: Mesh Flooding Relay, Deduplication & Encryption

### Added
- **Pure Kotlin RFC 8439 ChaCha20-Poly1305 AEAD**: Implemented zero-dependency, pure Kotlin cipher and SHA-256 key derivation (`PacketCipher.kt`) running on all KMP targets.
- **Poly1305 26-bit Limb Arithmetic**: 130-bit integer operations over five 64-bit Longs, bypassing Java `BigInteger` allocations. Verified against official RFC 8439 Section 2.8.2 KAT.
- **Sliding-Window Packet Deduplicator**: 5,000ms TTL, 1,000-entry capacity cache with Mutex protection to suppress broadcast storms and cyclic mesh loops (`PacketDeduplicator.kt`).
- **Multi-Hop Relay Flooding**: Automatic TTL decrement ($3 \rightarrow 2 \rightarrow 1$) and forwarding to adjacent peers in `MeshTransport.android.kt` and `MeshTransport.ios.kt`.
- **Simulated Mesh Topology Tests**: Linear 3-node multi-hop simulation in `CrossPlatformGattMeshTest.kt`.
- **Archify Architecture Delivery**: Interactive standalone HTML system diagram at `docs/architecture/intercom-alpha-architecture.html`.

---

## [0.4.0] - 2026-09-27 — Sprint 04: Swift ↔ KMP Bridge & QR Onboarding

### Added
- **Swift ↔ KMP Reactive Bridge**: Non-blocking `IntercomBridge.kt` with cancellation handles in `iosMain`.
- **AVFoundation QR Scanner**: Native iOS camera-based QR code scanner (`QrScannerView.swift`).
- **Offline Group QR Format**: `INTERCOM:v1:` QR generator and parser in `GroupManagerImpl.kt`.

---

## [0.3.0] - 2026-09-26 — Sprint 03: iOS Native Audio & CoreBluetooth Mesh

### Added
- **iOS AVAudioEngine Tap**: Low-latency PCM input and output scheduling in `AudioEngine.ios.kt`.
- **CoreBluetooth Dual-Role**: GATT peripheral and central managers matching Android UUIDs.
- **100% Native SwiftUI UI**: Strict enforcement of Rule 1 ("Do Not Share UI on iOS").

---

## [0.2.0] - 2026-09-25 — Sprint 02: Android Platform Engine & Compose UI

### Added
- **Android Audio Streaming**: `AudioRecord` / `AudioTrack` 16kHz PCM streaming with VOX thresholding.
- **Android BLE GATT Transport**: Advertising, scanning, and read/write characteristics.
- **Bluetooth SCO Headset Routing**: `HeadsetManager.android.kt` with hardware button hook.
- **Jetpack Compose UI**: Cockpit view with live VU meters and PTT button state machine.

---

## [0.1.0] - 2026-09-20 — Sprint 01: Core Networking & Signaling

### Added
- **Polymorphic Signaling Protocol**: JSON and binary protocols in `:core`.
- **Ktor WebSocket Server**: Cloud relay fallback and status API in `:server`.
- **Signaling Client**: Multiplatform WebSocket client in `app/sharedLogic`.
