# Software Requirements Document (SRD) — Intercom-Alpha

> **Document Version:** 3.0.0  
> **Standard:** Aligned with IEEE Std 830-1998 (Recommended Practice for Software Requirements Specifications)  
> **Status:** Baseline Engineering Contract  
> **System Scope:** Cross-Platform Kotlin Multiplatform Core, Android Jetpack Compose Cockpit, iOS Native SwiftUI Cockpit  

---

## 1. Introduction

### 1.1 Purpose
This document specifies the software requirements for the **Intercom-Alpha** off-grid mesh voice communication and telemetry platform, defining the functional, non-functional, external interface, and architectural constraints governing the Kotlin Multiplatform (KMP) shared engine and native platform presentation layers.

### 1.2 System Scope
The system consists of:
* `:core`: Pure KMP signaling data contracts and packet framing.
* `:server`: Lightweight Ktor WebSocket server for hybrid cellular relay and SFU bridging.
* `:app:sharedLogic`: Pure KMP common logic including audio DSP, adaptive bitrate estimation, block erasure FEC, pitch-synchronous PLC, link quality arbitration, and RFC 8439 ChaCha20-Poly1305 encryption.
* `:app:sharedUI`: Jetpack Compose Multiplatform tactical cockpit screens and viewmodels for Android and Desktop.
* `:app:androidApp`: Android native application shell, foreground service with partial wake locks, and Bluetooth SCO audio router.
* `:app:iosApp`: 100% Native SwiftUI iOS client consuming shared logic via thread-safe Swift bridge with reactive cancellation handles.

---

## 2. Overall Description

### 2.1 Product Perspective & System Topology
The system operates primarily as an ad-hoc, leaderless peer-to-peer radio mesh over Bluetooth Low Energy (BLE) GATT. In scenarios where nodes drift beyond radio line-of-sight ($Q_{link} < 70$), nodes transition seamlessly into dual-stream hedging and full cellular WebSocket fallback when internet connectivity is available.

```
+-------------------------------------------------------------------------+
|                              iOS Platform                               |
|       +---------------------------------------------------------+       |
|       |         :app:iosApp (100% Native SwiftUI Cockpit)       |       |
|       +---------------------------------------------------------+       |
|                                    | (IntercomBridge.kt)                |
|       +----------------------------v----------------------------+       |
|       |   :app:sharedLogic (iosMain: CoreBluetooth + AVAudio)   |       |
+-------+---------------------------------------------------------+-------+
                                     |
+------------------------------------v------------------------------------+
|                         Kotlin Multiplatform Core                       |
|       +---------------------------------------------------------+       |
|       |         :app:sharedLogic (commonMain)                   |       |
|       |   - Audio DSP: 4-bit ADPCM / Opus Codec, Jitter Buffer  |       |
|       |   - Resilience: Block Erasure FEC, Pitch PLC, Dynamic BR|       |
|       |   - Transport: Link Quality Arbiter, Multi-Path Hedging |       |
|       |   - Security: Pure Kotlin RFC 8439 ChaCha20-Poly1305    |       |
|       |   - QoS: 4-Tier Airtime Priority Token-Bucket Shaper    |       |
|       +---------------------------------------------------------+       |
|                                    |                                    |
|       +----------------------------v----------------------------+       |
|       |            :core (Signaling Contracts & Frames)         |       |
+-------+---------------------------------------------------------+-------+
                                     |
+------------------------------------v------------------------------------+
|                            Android Platform                             |
|       +---------------------------------------------------------+       |
|       |  :app:sharedLogic (androidMain: AudioRecord + BLE GATT) |       |
|       +---------------------------------------------------------+       |
|                                    |                                    |
|       +----------------------------v----------------------------+       |
|       |       :app:sharedUI (Compose Multiplatform Cockpit)     |       |
|       +---------------------------------------------------------+       |
|                                    |                                    |
|       +----------------------------v----------------------------+       |
|       |  :app:androidApp (Foreground Service & WakeLock Shell)  |       |
+-------------------------------------------------------------------------+
```

---

## 3. Specific Software Requirements

### 3.1 External Interface Requirements
* **EIR-01 (Android BLE Interface):** Must bind to Android `BluetoothGattServer` and `BluetoothGatt` supporting MTU negotiation up to 247 bytes with fallback to 23 bytes.
* **EIR-02 (iOS CoreBluetooth Interface):** Must utilize `CBPeripheralManager` and `CBCentralManager` with background restoration identifiers.
* **EIR-03 (Audio Ingestion Interface):** Android input via `AudioRecord` (16 kHz, 16-bit Mono PCM); iOS input via `AVAudioEngine` input node tap with VoiceProcessingIO enabled.
* **EIR-04 (Cellular SFU Interface):** Must communicate with Ktor WebSocket server over TLS/WSS using binary frame format.

### 3.2 Functional Requirements Specifications
* **SRS-AUD-01 (Dynamic Bitrate Adjustment):** The system shall compute moving link quality score $Q_{link} \in [0, 100]$ every 250ms and dynamically select codec bitrates:
  * $Q_{link} \ge 80 \implies 48	ext{ kbps}$ (High-Fidelity Wideband)
  * $50 \le Q_{link} < 80 \implies 32	ext{ kbps}$ (Standard Intelligibility)
  * $Q_{link} < 50 \implies 16	ext{ kbps}$ (Narrowband Resilience)
* **SRS-FEC-01 (Forward Error Correction Framing):** For every block of $K$ audio packets, the system shall compute systematic XOR/erasure parity packets based on link loss rate:
  * Loss $< 5\% \implies$ FEC Disabled (0% overhead)
  * $5\% \le 	ext{Loss} < 15\% \implies$ 4:1 Ratio (25% overhead)
  * $15\% \le 	ext{Loss} < 25\% \implies$ 3:1 Ratio (33% overhead)
  * $	ext{Loss} \ge 25\% \implies$ 2:1 Ratio (50% overhead)
* **SRS-PLC-01 (Packet Loss Concealment):** When a packet is missing and unrecoverable via FEC, the system shall extrapolate the previous pitch period $P$ using circular autocorrelation and apply a $2.5	ext{ dB}$ attenuation decay per consecutive lost frame up to 3 frames, muting smoothly thereafter.
* **SRS-CRY-01 (Cryptographic Protection):** All broadcast frames shall be authenticated and encrypted using RFC 8439 ChaCha20-Poly1305 with a 96-bit nonce derived from monotonic sequence counter and group epoch key, implemented in 100% pure Kotlin.
