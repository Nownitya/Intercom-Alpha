# Technical Requirements Document (TRD) — Intercom-Alpha

> **Document Version:** 3.0.0-beta1  
> **Status:** Approved Technical Contract  
> **Target Platforms:** Android 13+ (API 33+), iOS 16+, JVM (Java 17/21)  
> **Core Stack:** Kotlin Multiplatform 2.1+, Jetpack Compose 1.7+, SwiftUI, Ktor 3.x, Koin 4.0.2  
> **Repository:** `Intercom-Alpha`  
> **Related Documents:** `docs/SRD.md`, `docs/SSD.md`, `docs/TSD.md`, `docs/ARCHITECTURE.md`, `docs/MEMORY.md`  

---

## 1. Technical Architecture & Module Structure

```
                           +-------------------------+
                           |       :app:iosApp       |
                           |  (100% Native SwiftUI)  |
                           +-------------------------+
                                        | (IntercomBridge.kt)
+-----------------------+  +-------------------------+  +-----------------------+
|    :app:androidApp    |  |    :app:sharedLogic     |  |     :app:sharedUI     |
|  (Android Application |->|  (KMP Common Protocols, |<-| (Compose Multiplatform|
|    Shell & Service)   |  |   Audio DSP, Geo Engine)|  |    Android/Desktop)   |
+-----------------------+  +-------------------------+  +-----------------------+
                                        |
                           +-------------------------+
                           |          :core          |
                           |  (Signaling Contracts)  |
                           +-------------------------+
                                        |
                           +-------------------------+
                           |         :server         |
                           |  (Ktor WebSocket SFU)   |
                           +-------------------------+
```

### Module Responsibilities & Architectural Constraints
1. **`:core` (KMP)**: Polymorphic protocol data models (`SignalingProtocol.kt`). Zero platform-specific dependencies.
2. **`:server` (JVM)**: Standalone Ktor WebSocket server (`/ws/intercom`) retained for remote cellular bridging (ADR-009).
3. **`:app:sharedLogic` (KMP)**: All business logic, packet deduplication, audio DSP contracts, offline vector tile parser, convoy elevation engine, link arbiters, and pure Kotlin encryption.
   * `commonMain`: 100% pure Kotlin. No Java (`java.security.*`) or C/JNI dependencies.
   * `androidMain`: Android-specific audio records, BLE GATT bindings, SCO routing.
   * `iosMain`: Objective-C/Swift interop bridge (`IntercomBridge.kt`), CoreBluetooth and AVAudioEngine glue.
4. **`:app:sharedUI` (Compose Multiplatform)**: UI screens and viewmodels for Android and Desktop. **Forbidden on iOS**.
5. **`:app:androidApp` (Android)**: Application manifest, `IntercomForegroundService`, and platform bootstrap.
6. **`:app:iosApp` (SwiftUI)**: 100% native iOS SwiftUI client consuming shared logic via reactive callbacks.

---

## 2. Technical Stack Specifications

| Layer / Subsystem | Technology Choice | Target Version | Rationale & Architectural Rule |
| :--- | :--- | :--- | :--- |
| **Language & Toolchain** | Kotlin Multiplatform | **2.1.20** | Unified codebase across Android, iOS Native, JVM, and Wasm |
| **Android Presentation** | Jetpack Compose | **1.7.5** | Modern declarative UI, reactive StateFlow binding, hardware acceleration |
| **iOS Presentation** | Apple SwiftUI | **iOS 16+** | **Rule 1: 100% Native SwiftUI on iOS** (Zero Compose Multiplatform on iOS) |
| **Dependency Injection**| Koin Multiplatform | **4.0.2** | Pure Kotlin lightweight DI without code generation or reflection |
| **Asynchronous Engine** | Kotlin Coroutines & Flow | **1.9.0** | Structured concurrency, reactive streams, and backpressure management |
| **Networking & SFU** | Ktor Client & Server | **3.0.1** | Lightweight multiplatform WebSocket transport for hybrid cellular relay |
| **Shared Audio Codec** | 4-bit IMA ADPCM / Opus | **Pure KMP** | Sub-MTU frame compression (< 128 bytes) with zero native C/JNI bindings |
| **Resilience & FEC** | Systematic Block Erasure | **Pure KMP** | Dynamic parity framing (4:1, 3:1, 2:1) with matrix block interleaving |
| **Vector Map Engine** | MVT Tile Parser & LRU | **Pure KMP** | Sub-15ms spatial query, zero Mapbox runtime dependencies |
| **Elevation Math** | ConvoyElevationEngine | **Pure KMP** | Real-time slope % grading, alpine summit alerts, lead/sweep $\Delta h$ |
| **Cryptographic Engine**| RFC 8439 ChaCha20-Poly1305 | **Pure Kotlin** | **Rule 3**: Pure Kotlin 26-bit limb implementation; zero Java/Apple crypto dependencies |
| **JSON Serialization** | kotlinx.serialization | **1.7.3** | High-performance polymorphic binary and JSON codec (`#type` discriminator) |

---

## 3. Protocol Wire Opcodes & Frame Formats

| Opcode | Packet Type | Payload Description | Target MTU |
| :--- | :--- | :--- | :--- |
| `0x01` | **Voice Frame** | 4-bit ADPCM / Opus compressed audio samples (20ms) | 64–128 bytes |
| `0x02` | **Beacon / SOS** | Node ID, Convoy ID, GPS coordinates, priority level | 32–48 bytes |
| `0x03` | **Control / ACK** | Session key exchange, group join handshake, ACK | 24–64 bytes |
| `0x04` | **Routing (AODV)** | RREQ, RREP, RERR multi-hop path discovery | 36–64 bytes |
| `0x05` | **FEC Parity** | Systematic XOR parity frame for packet erasure recovery | 64–128 bytes |
| `0x06` | **Telemetry** | Speed, heading, battery level, ambient temperature | 28–48 bytes |
| `0x07` | **Vector Tile Query** | Bounding box spatial request for cached terrain contour | 32–64 bytes |
| `0x08` | **Elevation Profile** | Elevation array & moving slope grade % vector | 48–128 bytes |
| `0x09` | **Congestion Metric** | Local channel airtime %, neighbor density, backoff multiplier | 20–32 bytes |

---

## 4. Core Technical Constraints & Non-Negotiable Laws

1. **Law 1: Strict Platform Isolation on iOS:** iOS UI is 100% Native SwiftUI. Under no circumstances may Compose Multiplatform compile to iOS.
2. **Law 2: Pure Multiplatform Cryptography:** All encryption, key derivation (HKDF-SHA256), and authenticated tags in `commonMain` must have zero native dependencies.
3. **Law 3: Zero-Allocation Audio Pipeline:** Inner DSP loops (energy VAD, ADPCM encoding, jitter variance calculation, PLC pitch extrapolation) must reuse pre-allocated ring buffers. Zero object allocations permitted per audio frame.
4. **Law 4: Thread-Safe Swift Bridge:** `IntercomBridge.kt` must manage coroutine lifecycles cleanly and return typed `CancellationHandle` lambdas to SwiftUI views to prevent retain cycles.
5. **Law 5: 100% Automated Multiplatform Test Verification:** Every release candidate must pass all multiplatform unit tests (`./gradlew test`) and compile cleanly across Android and iOS Simulator Arm64 (506 tests passing).
