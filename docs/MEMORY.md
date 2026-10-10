# Engineering Memory & Architecture Knowledge Base — Intercom-Alpha

> **Document Version:** 2.12  
> **Status:** Active / Curated Knowledge Base (`v2.12.0`)  
> **MemPalace Memory Wing:** `intercom_alpha` (12,864 drawers)  
> **Graphify Knowledge Graph:** 3,675 nodes • 7,940 edges • 266 communities  
> **Obsidian Reference:** `05_Knowledge-Base/MEMORY.md` & `02_Architecture/ADRs/`

---

## 🏛️ 1. Architecture Decision Records (ADR) Summary

| ADR ID | Decision Title | Status | Impact / Core Rationale |
| :--- | :--- | :--- | :--- |
| **ADR-001** | BLE Mesh as Primary Transport | Accepted | Guarantees 100% offline operation without internet or cellular connectivity. |
| **ADR-002** | Opus Audio Codec for Mesh Voice | Accepted | Delivers optimal voice intelligibility at ultra-low bitrates (16–32 kbps). |
| **ADR-003** | Decentralized Leaderless Mesh Topology | Accepted | Eliminates single points of failure; group survives leader disconnection. |
| **ADR-004** | KMP Shared Logic + 100% Native SwiftUI | Accepted | **Strict Rule: No Shared UI on iOS**. Android uses Compose; iOS uses native SwiftUI. |
| **ADR-005** | Dual PTT and VOX Transmission Modes | Accepted | Hands-on PTT for high-speed wind; hands-free VOX for technical downhill tracks. |
| **ADR-006** | Foreground Service & Background Audio | Accepted | Enables continuous voice communication with phone locked in pocket/tank bag. |
| **ADR-007** | Offline QR Code Group Onboarding | Accepted | Zero internet exchange: group credentials encoded in `INTERCOM:v1:` QR codes. |
| **ADR-008** | Pure KMP RFC 8439 ChaCha20-Poly1305 AEAD | Accepted | High-speed, authenticated encryption running in pure Kotlin across all targets. |
| **ADR-009** | Retention of `:core` and `:server` | Accepted | Cloud signaling & SFU relay fallback for remote / mixed-mesh connectivity. |
| **ADR-010** | Pure KMP Audio Codec & MTU Optimization | Accepted | 4-bit IMA ADPCM fitting compressed audio frames into single BLE MTU payloads. |
| **ADR-011** | Adaptive Jitter Buffering & PLC | Accepted | RFC 3550 variance delay filter (40–150ms) + pitch-extrapolated packet loss concealment. |
| **ADR-012** | Mesh Airtime QoS Priority Traffic Shaping | Accepted | Token-bucket rate limiting and strict preemption (Emergency > Voice > Telemetry). |
| **ADR-013** | Automated Multi-Path Mesh Failover & Hedging | Accepted | Link quality arbiter scoring RSSI/loss/jitter with proactive BLE/Cellular stream hedging. |

---

## 💡 2. Proven Solution Blueprints & Design Patterns

### 2.1 Pure Kotlin RFC 8439 ChaCha20-Poly1305 AEAD (`PacketCipher.kt`)
- **Challenge**: Standard crypto libraries (`javax.crypto`, BoringSSL, Apple CryptoKit) are platform-specific and break KMP targets (iOS Simulator, Wasm/JS, Native).
- **Solution**: Implemented RFC 8439 ChaCha20 and Poly1305 in 100% pure Kotlin in `commonMain`:
  - Poly1305 uses Dan Bernstein's 26-bit limb representation over five 64-bit `Long` variables.
  - Intermediate products $(2^{26}) \times (2^{26}) = 2^{52}$ stay comfortably within 63-bit signed `Long` values without overflow, requiring zero BigInteger allocations.
  - Fully verified byte-for-byte against the official RFC 8439 Section 2.8.2 Known Answer Test (KAT).

### 2.2 Sliding-Window Packet Deduplicator (`PacketDeduplicator.kt`)
- **Challenge**: In a decentralized BLE mesh with $\ge 3$ nodes, flooding relays create exponential broadcast storms and routing loops.
- **Solution**:
  - Maintained an in-memory sliding-window cache protected by `kotlinx.coroutines.sync.Mutex`.
  - Composite keys: `audio:$senderId:$seq`, `ctrl:$senderId:$type:$hash`, `relay:$origSender:$innerKey`.
  - Expired keys purged after 5,000 ms; maximum capacity capped at 1,000 entries using oldest-timestamp eviction.

### 2.3 Navigation 3 Polymorphic Backstack Serializer (`AppNavGraph.kt`)
- **Challenge**: `SerializationException: Serializer for subclass 'HomeRoute' is not found in the polymorphic scope of 'NavKey'`.
- **Solution**:
  - Navigation 3's `NavKey` is an open interface. Backstack persistence requires explicit polymorphic serializer registration:
  ```kotlin
  val navConfig = SavedStateConfiguration {
      serializersModule = SerializersModule {
          polymorphic(NavKey::class) {
              subclass(HomeRoute::class, HomeRoute.serializer())
              subclass(GroupSettingsRoute::class, GroupSettingsRoute.serializer())
          }
      }
  }
  ```

### 2.4 Multi-Rate Pure KMP IMA ADPCM Audio Codec (`AudioCodec.kt`)
- **Challenge**: OPUS C-bindings require heavy NDK / CocoaPods dependencies that break multiplatform testing and increase binary footprint.
- **Solution**: Implemented a pure Kotlin 4-bit IMA ADPCM codec:
  - 89-entry step size adaptation table.
  - Compresses 16kHz 16-bit PCM (320 bytes per 20ms frame) down to 160 bytes.
  - Accommodates 160 bytes audio payload + 24 bytes header + 16 bytes MAC = 200 bytes total, fitting cleanly into a single negotiated BLE GATT MTU frame.

### 2.5 Adaptive Jitter Buffer & Packet Loss Concealment (`AdaptiveJitterBuffer.kt`, `PacketLossConcealment.kt`)
- **Challenge**: Packet inter-arrival jitter in BLE multi-hop mesh causes acoustic stutter, and dropped frames create loud pop artifacts.
- **Solution**:
  - RFC 3550 statistical inter-arrival variance estimation:
    $$D(i, j) = (R_j - R_i) - (S_j - S_i)$$
    $$J(i) = J(i-1) + \frac{|D(i,j)| - J(i-1)}{16}$$
  - Dynamic playout delay bounded between 40ms and 150ms ($D_{target} = baseDelay + 3 \times J$).
  - Autocorrelation pitch extrapolation with cumulative -3 dB decay per missing frame, fading to silence after 5 consecutive lost frames.

### 2.6 Dual-Threshold Adaptive Noise Gate & Wind Filter (`NoiseGate.kt`, `WindNoiseFilter.kt`)
- **Challenge**: Motorcycle helmet microphones pick up severe highway wind roar (80–120 km/h) causing constant false VOX triggering.
- **Solution**:
  - Dual-threshold hysteresis: -35 dBFS attack threshold (opens on speech) and -42 dBFS release threshold (closes on silence).
  - 5ms attack, 150ms hold, and 100ms release times prevent clipping consonant beginnings or endings.

### 2.7 AODV-Lite On-Demand Directed Mesh Routing (`AodvRoutingEngine.kt`)
- **Challenge**: Pure flooding flooding saturates the 2.4 GHz RF medium as convoy size grows beyond 8 riders.
- **Solution**:
  - Implemented pure KMP AODV-Lite (RFC 3561 subset): Route Request (RREQ), Route Reply (RREP), and Route Error (RERR).
  - Dynamic unicast routing table with ETX link quality metrics and 30-second soft-state expiration.
  - Decreases RF medium airtime utilization by >80% compared to blind flood relaying.

### 2.8 Airtime QoS Token-Bucket Traffic Shaper (`AirtimeQoSTrafficShaper.kt`)
- **Challenge**: Non-voice traffic (GPS beacons, route discovery) can collide with life-safety emergency SOS broadcasts or active speech.
- **Solution**:
  - 4-tier strict priority queues: `Priority 0: EMERGENCY`, `Priority 1: VOICE`, `Priority 2: NETWORK_CONTROL`, `Priority 3: TELEMETRY`.
  - Emergency SOS packets immediately preempt active non-voice BLE advertisements.
  - Weighted Fair Queuing (60/40 ratio) governs secondary queues when voice channels are idle.

### 2.9 Multi-Path Link Quality Arbiter & Stream Hedging (`MultiPathFailoverArbiter.kt`, `HybridTransportRouter.kt`)
- **Challenge**: Riders drift out of BLE RF range on highway interchanges, causing 2–4 seconds of dead silence before cellular cloud relay fallback reconnects.
- **Solution**:
  - Composite link quality scoring:
    $$Q_{link} = 0.50 \cdot S_{rssi} + 0.35 \cdot S_{loss} + 0.15 \cdot S_{jitter}$$
  - 4-stage state machine: `BLE_PRIMARY` ($Q_{link} \ge 70$) → `DEGRADED_HEDGING` ($30 \le Q_{link} < 70$) → `CELLULAR_ACTIVE` ($Q_{link} < 30$) → `RECOVERING`.
  - During hedging, duplicate sequence-stamped frames are sent across both BLE and Cellular SFU WebSockets. `PacketDeduplicator.kt` plays the earliest arriving packet and drops the duplicate with zero audio drop.

### 2.10 Tactical Snapshot Mesh Transport Engine (`TacticalSnapshotProtocol.kt`, `SnapshotTransferManager.kt`)
- **Challenge**: Transmitting visual reconnaissance thumbnails over lossy multi-hop BLE mesh without fragmenting GATT MTUs or disrupting real-time voice latency.
- **Solution**:
  - Bounded segmented chunking: divides <= 1.5 KB payloads into <= 128-byte segments ensuring zero GATT link-layer fragmentation.
  - Dual RFC 1950 Adler-32 validation: verifies both individual chunk data integrity and full-payload reassembly.
  - Out-of-order reassembly buffer with selective NACK recovery reporting exact missing chunk indices.
  - 0.5 fps framerate throttle (min 2000ms frame interval) and QoS airtime pacing adhering to RF duty cycle budgets.

---

## ⚠️ 3. Critical Technical Gotchas & Edge Cases

* **JSON Class Discriminator Collision**: Always use `Json { classDiscriminator = "#type" }` to prevent colliding with `MeshPacket.Control.type`.
* **Poly1305 on KMP**: Represent 130-bit integers as five 26-bit limbs over 64-bit Longs to avoid Java `BigInteger` allocations on high-frequency audio packet paths.
* **Navigation 3 Backstack Persistence**: Register polymorphic routes under `polymorphic(NavKey::class)` within `SavedStateConfiguration`.
* **iOS View Lifecycle**: Never subscribe to KMP flows in SwiftUI without capturing the returned `CancellationHandle` and canceling it in `.onDisappear`.
* **Android WakeLock Timeout**: Always pass a maximum timeout (4 hours) to `PowerManager.newWakeLock(PARTIAL_WAKE_LOCK, ...)` to prevent battery drain if the service dies abnormally.
* **Kotlin Multiplatform Regex**: Never use `RegexOption.DOT_MATCHES_ALL` in `commonMain` code because it is unavailable in JS/Wasm targets; use `[\s\S]*?` pattern which works identically across JVM, Android, iOS Native, JS, and Wasm.
