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
| **ADR-014** | Post-Development UI/UX Fusion & Expressive Language | Accepted | Post-development UI/UX phase fusing Material 3 Expressive, SwiftUI iOS 18+, Skeuomorphism, Neobrutalism, Claymorphism, Minimalism & Liquid Glass. Zero AI-generated artifice. |

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

### 2.11 Concurrent Multi-Radio Arbiter (`MultiRadioArbiter.kt`, `RadioModels.kt`)
- **Challenge**: Offloading heavy bulk data (tactical snapshot thumbnails, GPX route packs) without creating medium contention, RF collisions, or connection dropouts on low-latency voice and life-safety SOS channels.
- **Solution**:
  - Concurrent multi-radio state tracking across `BLE_MESH`, `WIFI_DIRECT`, and `CELLULAR_SFU`.
  - Automated payload classification segregating `EMERGENCY_CRITICAL` and `VOICE_REALTIME` onto BLE Mesh, while routing `BULK_HIGH_BANDWIDTH` data to high-speed Wi-Fi Direct (P2P Cluster).
  - Graceful fallback: when Wi-Fi Direct disconnects or RSSI degrades below -80 dBm, bulk transfers fall back seamlessly to BLE mesh with QoS token-bucket pacing.

### 2.12 Satellite Communicator Emergency Gateway Bridge (`SatelliteGatewayBridge.kt`, `SatelliteModels.kt`)
- **Challenge**: Off-grid emergency dispatch when entire convoy mesh is stranded beyond cellular coverage and local BLE mesh limits.
- **Solution**:
  - Implemented pure KMP bridge abstraction supporting Garmin inReach (RockBLOCK/Iridium SBD 340-byte MO-SBD envelopes) and satellite fallback protocols.
  - Rate-limited priority queuing with exponential backoff retry buffer adhering to strict satellite transponder airtime and power limits.
  - Emergency SOS packets prioritize GPS location, rider health vitals, and short battery-conserving distress payloads.

### 2.13 Adaptive Long-Range BLE Coded PHY Arbiter (`AdaptivePhyArbiter.kt`, `AlpineRfModels.kt`)
- **Challenge**: Severe RF multi-path fading, non-line-of-sight (NLOS) alpine canyon rock absorption, and diffraction knife-edge losses causing catastrophic connection dropouts at >100m distances.
- **Solution**:
  - Alpine propagation attenuation model incorporating knife-edge diffraction loss and dense vegetation margins.
  - Peer-specific link tracking dynamically shifting between `LE_2M` (close line-of-sight convoy, high bandwidth), `LE_1M` (standard terrain), and `LE_CODED_S8` (extreme distance / deep mountain valleys with +8 dB link budget).
  - Fast-track emergency threshold triggers immediate step-up to `LE_CODED_S8` when effective RSSI drops <= -100 dBm or packet loss >= 30%.

### 2.14 Wearable Smartwatch Quick-PTT Companion Protocol (`WearableCompanionProtocol.kt`, `WearableCompanionModels.kt`)
- **Challenge**: Riders unable to reach phone or handlebar PTT button while navigating extreme technical alpine terrain, and lack of real-time health telemetry under severe cold/hypothermia conditions.
- **Solution**:
  - Low-overhead framing (<32-byte BLE notifications) for watch companion connections (Apple Watch / Wear OS).
  - Sub-millisecond PTT state broadcast (`PRESS_PTT`, `RELEASE_PTT`, `TOGGLE_MUTE`).
  - Biometric vitals transmission (heart rate, SpO2, skin temp) feeding into automated crash / hypothermia SOS alerts.

### 2.15 Zero-Scroll Tactical Cockpit & Spatial Radar HUD (`HomeScreen.kt`, `ContentView.swift`, `TactileRadarCard.kt`, `TactileRadarCard.swift`)
- **Challenge**: Legacy tactical interfaces featured deep nested vertical scrolling, diagnostic debug strips cluttering the primary viewport, and small touch targets impossible to operate with heavy motorcycle/ski gloves.
- **Solution**:
  - Re-architected both Android (Material 3 Expressive) and iOS (100% Native SwiftUI iOS 18+ HIG) to fit entirely within a single zero-scroll viewport.
  - Secondary diagnostics and convoy telemetry tucked cleanly into gesture-driven sheets (`ModalBottomSheet` on Android, HIG `.insetGrouped` sheet on iOS).
  - Oversized 160dp tactile master PTT actuator, skeuomorphic rotary channel selector with detent haptics, mil-spec guarded SOS flip-cover, and 84dp glove-friendly spatial radar zoom button with liquid glass HUD overlays.
### 2.16 Tactile Sub-Screens & Secondary Overlays Architecture (`OfflineTrailCockpitSheet.kt`, `TrailFileManagerSheet.kt`, `TactileDiagnosticsSheet.kt`, `MessageHistoryDialog.kt`)
- **Challenge**: Legacy secondary screens and dialogs suffered from plain material styling, tiny touch targets inaccessible with winter/motorcycle gloves, lack of link quality diagnostics, and jittery layout shifts during frequent telemetry updates.
- **Solution**:
  - Refactored Android and iOS secondary overlays with `IntercomTokens` / `TacticalTokens`: frosted liquid glass chassis, claymorphic elevation profiles, and neobrutalist high-contrast borders.
  - Implemented 84dp glove-friendly touch targets across route recording, waypoint drops, trail GPX management, and canned hazard chips.
  - Formatted all elevation, GPS coordinates, $Q_{link}$ scores, RSSI link margins, RF duty-cycle, and battery metrics using monospaced tabular numerals (`FontFamily.Monospace` / `.monospacedDigit()`) ensuring zero jitter during real-time mesh streaming.
  - Wired real-time RF diagnostics sheet into the zero-scroll cockpit header with instantaneous link-quality breakdown.

### 2.17 Dynamic Adaptive Mesh Bitrate Tuning & Link Quality Scoring (`MeshLinkQualityScorer.kt`, `AdaptiveBitrateEngine.kt`)
- **Challenge**: BLE mesh audio transmission over varying outdoor terrain experiences sudden RF multi-path fading, shadow zones, and packet collisions. Fixed-bitrate codecs cause abrupt speech dropouts, severe buffer underruns, or unnecessary channel congestion when links degrade.
- **Solution**:
  - Implemented real-time composite link quality scoring ($Q_{link} \in [0, 100]$) fusing EWMA RSSI link margin (35%), moving Packet Delivery Ratio (PDR, 40%), RFC 3550 interarrival jitter variance (15%), and ETX hop cost (10%).
  - Added physical RF sanity bounds where severe packet loss ($PDR < 0.30$) hard-caps maximum achievable scores to $<30$ regardless of raw RSSI.
  - Implemented asymmetric bitrate hysteresis: rapid step-down (<200ms) upon link degradation downshifting from 48 kbps HIGH down to 16 kbps ULTRA_LOW and 60ms consolidated frames; conservative step-up requiring 3 consecutive pristine evaluation cycles before upgrading to prevent audio flapping.
  - Provided cluster governance policies (`WORST_LINK` protecting trailing sweep riders vs `CLUSTER_AVERAGE` optimizing majority throughput) with manual pin overrides.
  - Dynamic encoding profile byte embedded directly in packet headers (`packet[1] = profile.ordinal`) enabling seamless receiver decode without out-of-band renegotiation.

---

## ⚠️ 3. Critical Technical Gotchas & Edge Cases

* **JSON Class Discriminator Collision**: Always use `Json { classDiscriminator = "#type" }` to prevent colliding with `MeshPacket.Control.type`.
* **Poly1305 on KMP**: Represent 130-bit integers as five 26-bit limbs over 64-bit Longs to avoid Java `BigInteger` allocations on high-frequency audio packet paths.
* **Navigation 3 Backstack Persistence**: Register polymorphic routes under `polymorphic(NavKey::class)` within `SavedStateConfiguration`.
* **iOS View Lifecycle**: Never subscribe to KMP flows in SwiftUI without capturing the returned `CancellationHandle` and canceling it in `.onDisappear`.
* **Android WakeLock Timeout**: Always pass a maximum timeout (4 hours) to `PowerManager.newWakeLock(PARTIAL_WAKE_LOCK, ...)` to prevent battery drain if the service dies abnormally.
* **Kotlin Multiplatform Regex**: Never use `RegexOption.DOT_MATCHES_ALL` in `commonMain` code because it is unavailable in JS/Wasm targets; use `[\s\S]*?` pattern which works identically across JVM, Android, iOS Native, JS, and Wasm.
* **Pure KMP String Formatting**: Never use `String.format(...)` in `commonMain` or `commonTest` as it is JVM-only and breaks compilation on JS, Wasm, and iOS targets; use standard library `padStart`, `padEnd`, or string templates instead.

