# Engineering Memory & Curated Knowledge Base — Intercom-Alpha

> **Document Version:** 3.0.0-alpha2  
> **Status:** Active Living Repository Knowledge Base  
> **Repository:** `Intercom-Alpha`  
> **Obsidian Reference:** `05_Knowledge-Base/MEMORY.md`  

---

## 1. Proven Architectural Solutions & Design Patterns

### 1.1 Pure Kotlin RFC 8439 ChaCha20-Poly1305 AEAD (`PacketCipher.kt`)
* **Problem**: Standard platform crypto (`javax.crypto`, BoringSSL, Apple CryptoKit) breaks multiplatform compilation on iOS Simulator, Wasm, and JVM targets.
* **Solution**: Implemented ChaCha20 and Poly1305 in 100% pure Kotlin in `commonMain`:
  * Poly1305 employs Dan Bernstein's 26-bit limb representation over five 64-bit `Long` variables.
  * Intermediate products $(2^{26}) 	imes (2^{26}) = 2^{52}$ stay comfortably within 63-bit signed `Long` values without overflow, requiring zero `BigInteger` object allocations.
  * Verified byte-for-byte against the official RFC 8439 Section 2.8.2 Known Answer Test (KAT).

### 1.2 Zero-Allocation Audio DSP Pipeline
* **Problem**: Garbage collection (GC) pauses on Android and reference count sweeps on iOS introduce 10–50ms audio jitter spikes, causing audible pops and clicks.
* **Solution**:
  * All audio buffers are pre-allocated circular ring buffers (`CircularAudioBuffer.kt`).
  * ADPCM quantization tables and pitch autocorrelation buffers are statically allocated.
  * Zero heap object allocations inside the real-time audio frame ingestion loop.

### 1.3 Thread-Safe Swift Bridge with Cancellation Handles (`IntercomBridge.kt`)
* **Problem**: Exposing Kotlin `StateFlow` directly to Swift produces memory leaks and retain cycles when SwiftUI views dismiss.
* **Solution**: Wrapped state streams in callback observers returning a typed `CancellationHandle` closure (`{ job.cancel() }`). SwiftUI views bind these in `.onAppear` and invoke the handle in `.onDisappear`.

### 1.4 Dynamic Adaptive Bitrate with Asymmetric Hysteresis (`AdaptiveBitrateEngine.kt`)
* **Problem**: Flapping between audio bitrates causes noticeable acoustic volume jumps and frame length mismatches.
* **Solution**: Implemented asymmetric state transitions:
  * **Downgrade Threshold**: Immediate single-frame switch down on link degradation ($Q_{link} < 50$ or packet loss $> 15\%$).
### 1.5 Pure KMP BLE ATT MTU Frame Aggregation & Matrix Block Interleaving (`BleMtuAggregator.kt`, `BleFrameInterleaver.kt`)
* **Problem**: In 2.4 GHz dense mesh environments, transmitting single 20ms compressed voice frames (60–80 bytes) produces massive per-packet radio preamble, CRC, and ATT headers. Radio collision rates scale with packet count, choking the medium when 8–16 riders talk.
* **Solution**:
  * Dynamically packs 2–4 frames into negotiated ATT MTU payloads (up to 247 bytes, safe fallback 185 bytes) with compact 2-byte sub-frame length headers and sequence numbers, cutting transmitted RF packets by > 50% (measured ~66.7%).
  * Employs deterministic $(M \times N)$ matrix block interleaving: consecutive frames are dispersed across disjoint BLE packets so radio burst dropouts cause isolated single erasures (e.g. $[f_0, \mathbf{miss}, f_2, f_3, \mathbf{miss}, f_5]$), enabling 100% FEC/PLC reconstruction.
  * Zero-latency emergency SOS bypass: emergency frames flush the aggregation buffer and skip interleaver matrix holding for instantaneous propagation.

### 1.6 Pure KMP Offline Vector Map Tile Parsing & Real-Time Convoy Elevation Profiling (`OfflineVectorTileEngine.kt`, `ConvoyElevationEngine.kt`, `TrailElevationHudOverlay.kt`)
* **Problem**: Off-grid alpine convoys lack cellular network access to stream topographic contour tiles and altitude profiles. Using native C/JNI vector map renderers or Google Maps SDK introduces heavy binary bloat, vendor lock-in, and cross-platform compilation incompatibilities.
* **Solution**:
  * Pure KMP Web Mercator and MVT tile decoder (`OfflineVectorTileEngine.kt`) with zig-zag integer decoding, multi-layer feature parser, and an `ArrayDeque`-backed LRU eviction cache executing spatial bounding box queries in < 15ms.
  * Real-time convoy elevation engine (`ConvoyElevationEngine.kt`) calculating moving slope grade percentage, steep alpine caution (> 8%) and extreme warnings (> 12% grade), vertical altitude separation ($\Delta h$) between convoy lead and tail/sweep riders, and upcoming mountain pass summits along planned route polylines.
  * High-contrast tactile OLED cockpit HUD overlay (`TrailElevationHudOverlay.kt`) with glove-friendly 64dp zoom buttons (1km / 5km / 15km), color-coded rider pins along the altitude profile curve, and reactive bridge streaming (`IntercomBridge.kt`) for 100% native iOS SwiftUI.

---

## 2. Hard-Won Gotchas & Critical Traps Avoided

1. **Android BLE Scan Throttling:** Android limits BLE background scans to 5 scans per 30-second window. Continuous scanning causes `SCAN_FAILED_APPLICATION_REGISTRATION_FAILED`. Always use opportunistic or batch scanning with a 15-second scan window.
2. **iOS Background Execution Timeout:** iOS suspends apps after 30 seconds unless configured with `audio` and `bluetooth-central` background modes in `Info.plist` AND actively driving an `AVAudioEngine` or `AVAudioSessionCategoryPlayAndRecord` stream.
3. **Bluetooth SCO Headset Sampling Rate:** Many motorcycle helmet headsets only support 8kHz or 16kHz mono sampling over Bluetooth SCO (HFP profile). Never attempt to open a 44.1kHz or 48kHz SCO stream; always resample in the DSP layer.
4. **Galois Field Allocation Trap in FEC:** Creating dynamic arrays inside Galois Field polynomial division causes massive allocation churn. Use systematic interleaved XOR block parity for fast, zero-allocation packet reconstruction.
5. **Matrix Block Interleaver Partial-Block Flushes:** When flushing incomplete interleaver blocks (e.g. 4 frames in a $3 \times 3$ block), columns have unequal sizes. Always include `totalPacketsInBlock` and `totalFramesInBlock` in the envelope to allow receivers to differentiate between unpadded matrix slots and dropped radio packets.
6. **Web Mercator Boundary Inset in Spatial Tile Queries:** When computing intersecting tile coordinates for a bounding box, tile boundaries align precisely with adjacent tile boundaries. Applying an epsilon inset (`1e-7`) ensures the query strictly bounds the intended tile area without false adjacent tile hits.
7. **Pure KMP String Formatting on Native Targets:** `String.format` is JVM-only and breaks native compilation on iOS Simulator (`compileKotlinIosSimulatorArm64`). Use standard Kotlin string templates with integer rounding (`((val * 10.0).roundToInt() / 10.0).toString()`).
8. **Zero-Dependency LRU Cache in Multiplatform:** Java's `LinkedHashMap` access-order constructor is not available in KMP `commonMain`. Using `mutableMapOf<K, V>()` paired with an `ArrayDeque<K>()` gives clean, zero-dependency, 100% multiplatform LRU caching.

