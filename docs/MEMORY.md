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
  * **Upgrade Hysteresis**: 5-second observation window requiring sustained $Q_{link} \ge 85$ before stepping up bitrate.

---

## 2. Hard-Won Gotchas & Critical Traps Avoided

1. **Android BLE Scan Throttling:** Android limits BLE background scans to 5 scans per 30-second window. Continuous scanning causes `SCAN_FAILED_APPLICATION_REGISTRATION_FAILED`. Always use opportunistic or batch scanning with a 15-second scan window.
2. **iOS Background Execution Timeout:** iOS suspends apps after 30 seconds unless configured with `audio` and `bluetooth-central` background modes in `Info.plist` AND actively driving an `AVAudioEngine` or `AVAudioSessionCategoryPlayAndRecord` stream.
3. **Bluetooth SCO Headset Sampling Rate:** Many motorcycle helmet headsets only support 8kHz or 16kHz mono sampling over Bluetooth SCO (HFP profile). Never attempt to open a 44.1kHz or 48kHz SCO stream; always resample in the DSP layer.
4. **Galois Field Allocation Trap in FEC:** Creating dynamic arrays inside Galois Field polynomial division causes massive allocation churn. Use systematic interleaved XOR block parity for fast, zero-allocation packet reconstruction.
