# Technical Requirements Document (TRD) — Intercom-Alpha

> **Document Version:** 2.12  
> **Status:** Approved / Active (`v2.12.0`)  
> **Target Platforms:** Android 13+ (API 33+), iOS 16+, JVM / Ktor Server  
> **Core Stack:** Kotlin Multiplatform 2.1+, Jetpack Compose, SwiftUI, Ktor 3.x, Koin 4.0.2  
> **Repository:** `Intercom-Alpha`  
> **Related Documents:** `docs/BRD.md`, `docs/PRD.md`, `docs/ARCHITECTURE.md`, `docs/MEMORY.md`

---

## 1. System Topology & Module Structure

```
                           ┌────────────────────────┐
                           │      :app:iosApp       │
                           │  (100% Native SwiftUI) │
                           └───────────┬────────────┘
                                       │ (IntercomBridge.kt)
┌──────────────────────┐   ┌───────────▼────────────┐   ┌──────────────────────┐
│   :app:androidApp    │   │   :app:sharedLogic     │   │     :app:sharedUI    │
│  (Android Application├───► (KMP Common Protocols, ◄───┤ (Compose Multiplatform│
│    Shell & Service)  │   │   Audio DSP, Mesh Engine)│ │    Android/Desktop)  │
└──────────────────────┘   └───────────┬────────────┘   └──────────────────────┘
                                       │
                           ┌───────────▼────────────┐
                           │         :core          │
                           │ (Signaling Contracts & │
                           │  Polymorphic Frames)   │
                           └───────────┬────────────┘
                                       │
                           ┌───────────▼────────────┐
                           │        :server         │
                           │ (Ktor WebSocket Server │
                           │  & Cross-Mesh SFU)     │
                           └────────────────────────┘
```

### Module Responsibilities & Constraints
1. **`:core` (KMP)**: Polymorphic protocol data models (`SignalingProtocol.kt`). Zero platform-specific dependencies.
2. **`:server` (JVM)**: Standalone Ktor WebSocket server (`/ws/intercom`) retained for remote cellular bridging (ADR-009).
3. **`:app:sharedLogic` (KMP)**: All business logic, packet deduplication, audio DSP contracts, link arbiters, and encryption.
   - `commonMain`: 100% pure Kotlin. No Java (`java.security.*`) or C/JNI dependencies.
   - `androidMain`: Android-specific audio records, BLE GATT bindings, SCO routing.
   - `iosMain`: Objective-C/Swift interop bridge (`IntercomBridge.kt`), CoreBluetooth and AVAudioEngine glue.
4. **`:app:sharedUI` (Compose Multiplatform)**: UI screens and viewmodels for Android and Desktop. **Forbidden on iOS**.
5. **`:app:androidApp` (Android)**: Application manifest, `IntercomForegroundService`, and platform bootstrap.
6. **`:app:iosApp` (SwiftUI)**: 100% native iOS SwiftUI client consuming shared logic via reactive callbacks.

---

## 2. Wire Protocol & Packet Framing

### 2.1 Polymorphic Serialization Configuration
To eliminate discriminator collision between protocol packet types and message structures, all `kotlinx.serialization` instances must enforce:
```kotlin
val JsonWireFormat = Json {
    classDiscriminator = "#type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}
```

### 2.2 Binary Mesh Packet Layout
Mesh packets fit within the negotiated BLE GATT MTU (default negotiated: 185–512 bytes):

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| Magic (0xCAFE)| Version (0x01)| Packet Type   | TTL (3..1)    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Sequence Number (32-bit)                |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Timestamp (32-bit Unix)                 |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                     Source Node ID (64-bit UUID)              |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                   Payload Length (16-bit)     | Reserved      |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                 Encrypted Payload (ADPCM / Opus / JSON)       |
|                               ...                             |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                  Poly1305 MAC Tag (16 bytes / 128 bits)       |
|                               ...                             |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### 2.3 Sliding-Window Loop Suppression
* **Engine**: `PacketDeduplicator.kt`.
* **Window Duration**: 5,000ms TTL.
* **Storage**: Circular array backed by a hash index capped at 1,000 entries.
* **Rule**: If `PacketDeduplicator.shouldProcess(packet)` returns `false`, packet is dropped immediately before forwarding or playout.

---

## 3. Audio DSP & Codec Pipeline Specifications

```
Mic In (16kHz PCM) ──► Noise Gate ──► IMA ADPCM Enc (4-bit) ──► PacketCipher (ChaCha20)
                                                                       │
                                                                   BLE Flooding
                                                                       │
Speaker (SCO Out)  ◄── PLC Extrap ◄── Jitter Buffer ◄── Decrypt ◄──────┘
```

### 3.1 Adaptive Noise Gate
* **Attack Threshold**: -35 dBFS.
* **Release Threshold**: -42 dBFS (7 dB hysteresis preventing syllable flutter).
* **Attack Time**: 5ms; **Hold Time**: 150ms; **Release Time**: 100ms.

### 3.2 Pure KMP Multi-Rate Audio Codec
* **Quantization**: 4-bit IMA ADPCM step index table (89 quantized step values).
* **Sample Rate**: 16,000 Hz mono (20ms frame = 320 PCM samples = 160 compressed bytes).
* **MTU Fit**: 160-byte audio payload + 24-byte header + 16-byte MAC = 200 bytes total, fitting within a single BLE packet without fragmentation.

### 3.3 Adaptive Jitter Buffer (RFC 3550)
* **Statistical Inter-Arrival Variance**:
  $$D(i, j) = (R_j - R_i) - (S_j - S_i)$$
  $$J(i) = J(i-1) + \frac{|D(i, j)| - J(i-1)}{16}$$
* **Target Delay**: $D_{target} = baseDelay + 3 \times J(i)$, bounded strictly between **40ms and 150ms**.

### 3.4 Packet Loss Concealment (PLC)
* Autocorrelation over the most recent 160 PCM samples to detect pitch period $P \in [32, 320]$ samples.
* Extrapolates pitch waveform during frame loss.
* Exponential attenuation of $-3\text{ dB}$ per consecutive dropped frame; mutes to full silence after 5 consecutive lost frames.
* Cosine crossfade blending (32 samples) upon stream resumption.

---

## 4. Multi-Path Failover & Stream Hedging

### 4.1 Link Quality Metric ($Q_{link}$)
Link quality score $Q_{link} \in [0, 100]$ is evaluated every 250ms by `MultiPathFailoverArbiter.kt`:
$$Q_{link} = 0.50 \cdot S_{rssi} + 0.35 \cdot S_{loss} + 0.15 \cdot S_{jitter}$$

* $S_{rssi}$: Linearly scaled from $RSSI \in [-95\text{ dBm}, -55\text{ dBm}]$.
* $S_{loss}$: Exponential penalty over a sliding 500ms packet loss window.
* $S_{jitter}$: Variance penalty against the 40ms jitter baseline.

### 4.2 State Machine Transition Rules
```
┌──────────────┐     Q_link < 70      ┌──────────────────┐
│ BLE_PRIMARY  ├─────────────────────►│ DEGRADED_HEDGING │
└──────▲───────┘                      └────────┬─────────┘
       │                                       │ Q_link < 30 (1.5s)
       │ Stable Q_link >= 75 (3.0s)            ▼
┌──────┴───────┐   BLE RSSI > -75dBm  ┌──────────────────┐
│  RECOVERING  │◄─────────────────────┤ CELLULAR_ACTIVE  │
└──────────────┘                      └──────────────────┘
```

* **Stream Hedging**: During `DEGRADED_HEDGING`, identical sequence-stamped packets are broadcast via both BLE GATT and Cellular WebSocket. The receiver's `PacketDeduplicator` routes the first arriving packet directly to the jitter buffer and drops the redundant frame with zero overhead.

---

## 5. Airtime QoS Traffic Shaping & Preemption

### 5.1 Priority Hierarchy
* **Priority 0 (EMERGENCY)**: eCall crash impact alerts, manual SOS broadcasts. Bypasses token bucket, preempts current BLE transmission.
* **Priority 1 (VOICE)**: Real-time ADPCM audio frames. Latency deadline $\le 40\text{ms}$.
* **Priority 2 (CONTROL)**: AODV-Lite route discovery, session rekeying frames.
* **Priority 3 (TELEMETRY)**: GPS coordinates, convoy radar beacons, battery status. Governed by token-bucket rate limiter.

---

## 6. Security & Cryptographic Architecture

### 6.1 Cryptographic Primitives (Pure Kotlin)
* **Authenticated Encryption**: RFC 8439 ChaCha20-Poly1305 in `PacketCipher.kt`.
* **Zero Platform Cryptographic Dependencies**: No `javax.crypto.*`, `java.security.*`, or OpenSSL C bindings.
* **Poly1305 Implementation**: Implemented with 130-bit integers partitioned into five 26-bit limbs over 64-bit Kotlin `Long` primitives to eliminate JVM `BigInteger` object allocations on high-frequency audio packet paths.

---

## 7. Platform Specifics & Lifecycle

### 7.1 Android Architecture
* **Foreground Service**: `IntercomForegroundService` declared with `foregroundServiceType="microphone|connectedDevice"`.
* **WakeLock**: `PowerManager.PARTIAL_WAKE_LOCK` acquired with a 4-hour safety timeout to prevent permanent battery drain.
* **Headset Audio**: Explicit Bluetooth SCO audio focus lock via `AudioManager.startBluetoothSco()` and `setCommunicationDevice()`.

### 7.2 iOS Architecture
* **Strict Rule**: 100% Native SwiftUI in `app/iosApp`.
* **Bridge**: `IntercomBridge.kt` (`iosMain`) exposes Swift-friendly flows with `CancellationHandle` closures tied to SwiftUI view lifecycles (`.onAppear` / `.onDisappear`).
* **Audio Session**: `AVAudioSessionCategoryPlayAndRecord` configured with `.modeVoiceChat` and `.allowBluetooth`.
* **Background Execution**: `Info.plist` declares `UIBackgroundModes`: `audio`, `bluetooth-central`, and `bluetooth-peripheral`.

---

## 8. Verification & QA Gates

1. **Unit & Multiplatform Tests**: All shared logic tests run via `./gradlew test` (must pass 100% cleanly).
2. **Platform Builds**: Must pass cleanly:
   - `./gradlew :app:androidApp:assembleDebug`
   - `./gradlew :app:sharedLogic:compileKotlinIosSimulatorArm64`
3. **OpenCodeReview (OCR) Gate**: Working copy pre-commit preview (`ocr delegate preview --format json`) must report **0 Critical, High, or Medium findings**.
