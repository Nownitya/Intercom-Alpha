# Software Requirements Document (SRD) — Intercom-Alpha

> **Document Version:** 3.0.0-beta1  
> **Standard:** Aligned with IEEE Std 830-1998 (Recommended Practice for Software Requirements Specifications)  
> **Status:** Baseline Engineering Contract  
> **System Scope:** Cross-Platform Kotlin Multiplatform Core, Android Jetpack Compose Cockpit, iOS Native SwiftUI Cockpit  

---

## 1. Introduction

### 1.1 Purpose
This document specifies the software requirements for the **Intercom-Alpha** off-grid mesh voice communication, offline vector navigation, and telemetry platform. It defines functional, non-functional, external interface, and architectural constraints governing the Kotlin Multiplatform (KMP) shared engine and native platform presentation layers.

### 1.2 System Scope
The system consists of:
* `:core`: Pure KMP signaling data contracts, protocol opcodes, and binary packet framing.
* `:server`: Lightweight Ktor WebSocket server for hybrid cellular relay and SFU cross-mesh bridging.
* `:app:sharedLogic`: Pure KMP common logic including audio DSP, adaptive bitrate estimation, block erasure FEC, pitch-synchronous PLC, link quality arbitration, offline vector tile caching/parsing, convoy elevation math, mesh congestion governance, and RFC 8439 ChaCha20-Poly1305 encryption.
* `:app:sharedUI`: Jetpack Compose Multiplatform tactical cockpit screens and viewmodels for Android and Desktop (ELEV tab, HUD overlays, diagnostics).
* `:app:androidApp`: Android native application shell, foreground service with partial wake locks, Bluetooth SCO audio router, and hardware BLE radio controller.
* `:app:iosApp`: 100% Native SwiftUI iOS client consuming shared logic via thread-safe Swift bridge (`IntercomBridge.kt`) with reactive cancellation handles.

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
|       |   - Audio DSP: 4-bit ADPCM / Opus, Jitter Buffer, PLC   |       |
|       |   - Resilience: Block Erasure FEC, Matrix Interleaver   |       |
|       |   - Transport: ATT MTU Packing, Congestion Governor     |       |
|       |   - Navigation: Offline Vector Tile Engine, Elevation   |       |
|       |   - Security: Pure Kotlin RFC 8439 ChaCha20-Poly1305    |       |
|       |   - QoS: 4-Tier Airtime Priority Token-Bucket Shaper    |       |
|       +---------------------------------------------------------+       |
|                                    |                                    |
|       +----------------------------v----------------------------+       |
|       |            :core (Signaling Contracts & Wire Frames)    |       |
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
* **EIR-05 (Offline Map Storage Interface):** Must access local device storage for cached Mapbox Vector Tiles (.mvt) without requiring internet or Mapbox API access.

### 3.2 Functional Requirements Specifications
* **SRS-AUD-01 (Dynamic Bitrate Adjustment):** The system shall compute moving link quality score $Q_{link} \in [0, 100]$ every 250ms and dynamically select codec bitrates:
  * $Q_{link} \ge 80 \implies 48\text{ kbps}$ (High-Fidelity Wideband)
  * $50 \le Q_{link} < 80 \implies 32\text{ kbps}$ (Standard Intelligibility)
  * $Q_{link} < 50 \implies 16\text{ kbps}$ (Narrowband Resilience)
* **SRS-FEC-01 (Forward Error Correction Framing):** For every block of $K$ audio packets, the system shall compute systematic XOR/erasure parity packets based on link loss rate:
  * Loss $< 5\% \implies$ FEC Disabled (0% overhead)
  * $5\% \le \text{Loss} < 15\% \implies$ 4:1 Ratio (25% overhead)
  * $15\% \le \text{Loss} < 25\% \implies$ 3:1 Ratio (33% overhead)
  * $\text{Loss} \ge 25\% \implies$ 2:1 Ratio (50% overhead)
* **SRS-PLC-01 (Packet Loss Concealment):** When a packet is missing and unrecoverable via FEC, the system shall extrapolate the previous pitch period $P$ using circular autocorrelation and apply a $2.5\text{ dB}$ attenuation decay per consecutive lost frame up to 3 frames, muting smoothly thereafter.
* **SRS-ATT-01 (BLE ATT MTU Aggregation):** When negotiated BLE ATT MTU $\ge 247$ bytes, the transmitter shall aggregate 2 to 4 consecutive audio/telemetry frames into a single ATT Write Without Response payload, reducing physical RF advertising bursts by $> 50\%$.
* **SRS-INT-01 (Matrix Block Interleaving):** Audio frames and FEC parity packets shall pass through an $(M \times N)$ block interleaver before transmission, transforming sequential burst losses of up to $N$ packets into single erasures across $M$ distinct blocks.
* **SRS-GEO-01 (Offline Vector Topographic Engine):** The pure KMP vector tile engine shall query local vector tiles and return contour, terrain, and trail geometry with spatial lookups resolving in $< 15\text{ms}$ under a 64-tile in-memory LRU cache.
* **SRS-GEO-02 (Convoy Elevation Math & Warning Engine):** The elevation engine shall compute running slope grade percentage between consecutive GPS fixes. It shall trigger visual warnings:
  * Grade $> 8\% \implies$ Caution (Orange indicator)
  * Grade $> 12\% \implies$ Extreme Ascent/Descent (Red flashing alert)
  * Compute real-time vertical separation ($\Delta h$) between lead rider and sweep rider.
* **SRS-NET-06 (Autonomous Mesh Congestion Control):** The congestion governor shall measure radio channel occupancy, throttling broadcast intervals between 20ms and 250ms and dropping redundant rebroadcasts probabilistically when local neighbor density exceeds 8 nodes.
* **SRS-NET-07 (Adaptive Multi-PHY Switching):** The PHY switching arbiter shall monitor RSSI, SNR, and packet error rates, autonomously selecting LE 2M for high throughput at close range, LE 1M for standard mesh, and LE Coded S8 ($500\text{m}+$ reach) under extreme canyon or alpine attenuation.
* **SRS-CRY-01 (Cryptographic Protection):** All broadcast frames shall be authenticated and encrypted using RFC 8439 ChaCha20-Poly1305 with a 96-bit nonce derived from monotonic sequence counter and group epoch key, implemented in 100% pure Kotlin.
* **SRS-UI-01 (Tactical Zero-Scroll Cockpit HUD):** The user interface shall provide single-touch access to PTT, channels, and elevation profiles. Touch targets must be $\ge 64\text{dp}$ with OLED true-black (#0A0D10) background, 7:1 contrast ratio, and non-blocking tactile modal sheets.
