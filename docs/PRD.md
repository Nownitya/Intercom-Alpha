# Product Requirements Document (PRD) — Intercom-Alpha

> **Document Version:** 3.0.0-beta1  
> **Status:** Active / Sprints 01–36 Complete / Sprints 37–39 Planned  
> **Target Release:** v3.0.0-beta1 (Sprint 36 Complete) / v3.0.0-beta2 (Sprint 37) / v3.0.0 Production GA (Sprint 39)  
> **Repository:** `Intercom-Alpha`  
> **Related Documents:** `docs/MRD.md`, `docs/BRD.md`, `docs/SRD.md`, `docs/TRD.md`, `docs/SSD.md`, `docs/ARCHITECTURE.md`  

---

## 1. Product Vision & Value Pillars

### 1.1 Vision Statement
**Intercom-Alpha** transforms standard consumer smartphones (Android and iOS) into an enterprise-grade, decentralized, off-grid communication platform. It delivers crystal-clear, ultra-low-latency voice over Bluetooth Low Energy (BLE) Mesh with zero internet, zero accounts, and zero monthly subscriptions, fortified by automated hybrid cellular SFU failover, forward error correction, high-definition offline vector topographic mapping, real-time convoy elevation profile HUD, and a zero-scroll tactile cockpit designed for high-speed operation.

### 1.2 Core Product Pillars
1. **Zero-Setup Offline Onboarding:** Join a convoy in under 2 seconds via camera scan of an offline `INTERCOM:v1:` QR code containing group credentials and cryptographic session keys.
2. **Leaderless Multi-Hop Mesh Flooding:** Conversations traverse up to 3 intermediate hops ($TTL=3$), extending communication range beyond physical radio line-of-sight.
3. **Closed-Loop Audio Intelligibility:** Pure KMP DSP pipeline combining RFC 3550 adaptive jitter buffering (40ms–150ms), pitch-extrapolated Packet Loss Concealment (PLC), systematic block erasure Forward Error Correction (FEC), and dynamic adaptive bitrate scaling (16–48 kbps).
4. **Airtime QoS Traffic Shaping:** Strict 4-tier priority token-bucket regulator ensuring life-safety emergency broadcasts preempt all voice and background telemetry packets with zero dropped emergency frames.
5. **Seamless Multi-Path Failover:** Continuous link quality scoring ($Q_{link}$) with proactive stream hedging preventing audio gaps during highway convoy separations.
6. **Zero-Scroll Tactile Cockpit & Elevation HUD:** High-contrast OLED dark-mode HUD with 64dp+ glove-friendly hit targets, single-tap PTT pill, live VU audio visualizer, real-time convoy elevation profile curve, and non-blocking modal sheet overlays.
7. **Strict Platform Isolation:** Pure Kotlin Multiplatform for shared protocol/audio/geo logic, Jetpack Compose for Android, and 100% Native SwiftUI for iOS (strictly no shared Compose UI on iOS).

---

## 2. User Journeys & Operational Workflows

```
[Pre-Ride Staging]
Lead rider taps "Create Group" ──> App displays INTERCOM:v1: QR Code
Follower riders tap "Join" ──> Scan QR via camera ──> ChaCha20 session key ratcheted (Time: < 2.5s)

[On-Road Tactical Mesh Communication]
Rider presses helmet button / speaks via VOX ──> 16kHz PCM capture ──> Dual-threshold noise gate (-35dB attack)
──> Dynamic Bitrate Engine adjusts 16–48 kbps ──> Block Erasure FEC parity attached
──> ATT MTU Aggregator packs 2–4 frames ──> Matrix Block Interleaver disperses burst drops
──> Pure KMP ChaCha20-Poly1305 AEAD Encrypt ──> BLE GATT Flooding (TTL=3) ──> Peers receive & decode

[Alpine Pass & Mountain Trail Navigation]
Convoy enters backcountry alpine trail ──> Pure KMP Offline Vector Tile Engine parses local MVT contours (<15ms)
Riders ascend mountain pass ──> Convoy Elevation Engine computes moving slope grade %
Grade exceeds 8% (Caution) or 12% (Extreme) ──> Cockpit HUD displays prominent alpine slope warnings
Lead and sweep riders separate ──> HUD tracks lead/tail vertical delta (Δh) & announces upcoming pass summits

[Severe RF Degradation & Packet Loss]
Convoy encounters tunnel / distance loss (Packet Drop > 20%) ──> Receiver FEC engine reconstructs dropped packets
Burst drop > 25% ──> Predictive PLC interpolator synthesizes natural pitch waveform with 2.5dB attenuation decay
Link Quality Arbiter detects Q_link < 70 ──> Dual-Stream Hedging activates across Cellular WebSocket SFU
Audio plays seamlessly with ZERO clipped words

[Accident / Impact Emergency Event]
Multi-axis accelerometer registers impact (> 6.5g) ──> 15-second countdown with haptic abort vibration
Countdown elapses ──> Priority 0 SOS Beacon floods mesh, preempting all voice & background traffic
Emergency voice beacon & GPS coordinates broadcast to all convoy members
```

---

## 3. Functional Requirements Matrix (Through Sprint 36 & Forward)

| ID | Requirement Category | Priority | Release Milestone | Status |
| :--- | :--- | :--- | :--- | :--- |
| **FR-AUD-01** | Low-latency audio capture via Android `AudioRecord` and iOS `AVAudioEngine` input tap | P0 | v1.0.0 | Verified |
| **FR-AUD-02** | Configurable Push-To-Talk (PTT) via screen UI and external Bluetooth media buttons (`KEY_HEADSETHOOK`) | P0 | v1.0.0 | Verified |
| **FR-AUD-03** | Hands-free Voice Activity Detection (VOX) with adjustable energy threshold (-20 dBFS to -60 dBFS) | P0 | v1.0.0 | Verified |
| **FR-AUD-04** | Dual-threshold adaptive noise gate (-35 dBFS attack, -42 dBFS release) to suppress helmet wind roar | P0 | v1.0.0 | Verified |
| **FR-AUD-05** | Multi-rate 4-bit IMA ADPCM and Opus audio codec fitting frames within single BLE GATT MTUs | P0 | v1.0.0 | Verified |
| **FR-AUD-06** | Adaptive Jitter Buffer (RFC 3550 variance filter) with dynamic 40ms–150ms target delay | P0 | v1.0.0 | Verified |
| **FR-AUD-07** | Pure KMP Block Erasure Forward Error Correction (FEC) with dynamic parity framing (4:1, 3:1, 2:1, 4:2 GF) | P0 | v3.0.0-alpha2 | Verified |
| **FR-AUD-08** | Pitch-synchronous waveform Packet Loss Concealment (PLC) with progressive 2.5 dB attenuation decay | P0 | v3.0.0-alpha2 | Verified |
| **FR-AUD-09** | Dynamic Adaptive Bitrate Engine scaling audio encoding between 16 kbps and 48 kbps based on $Q_{link}$ | P0 | v3.0.0-alpha2 | Verified |
| **FR-NET-01** | Decentralized leaderless BLE GATT Mesh flooding with configurable hop limit ($TTL=3$) | P0 | v1.0.0 | Verified |
| **FR-NET-02** | Sliding-window packet deduplicator (5,000ms window, 1,000 entry capacity) to suppress storm loops | P0 | v1.0.0 | Verified |
| **FR-NET-03** | Pure Kotlin RFC 8439 ChaCha20-Poly1305 AEAD cipher with forward-secret session key ratchet | P0 | v1.0.0 | Verified |
| **FR-NET-04** | Automated Multi-Path Mesh Failover with Link Quality Arbiter and dual-stream cellular hedging | P0 | v2.12.0 | Verified |
| **FR-NET-05** | Dynamic Airtime QoS Token-Bucket Traffic Shaper with 4-tier strict preemption | P0 | v2.11.0 | Verified |
| **FR-NET-06** | BLE ATT MTU Frame Aggregator packing 2–4 voice frames into 247-byte payloads (>50% RF reduction) | P0 | v3.0.0-alpha3 | Verified |
| **FR-NET-07** | Time-Diversity Matrix Block Interleaver ($(M \times N)$ dispersion converting radio burst drops into single erasures) | P0 | v3.0.0-alpha3 | Verified |
| **FR-NET-08** | Dynamic BLE Advertising Interval Throttler & Flooding Redundancy Governor | P0 | v3.0.0-beta2 | Ready |
| **FR-NET-09** | Adaptive Multi-PHY Switcher Engine (LE 1M, LE 2M, LE Coded S8) | P0 | v3.0.0-beta2 | Ready |
| **FR-GEO-01** | Pure KMP Offline Vector Map Tile Parser & Spatial Bounding Box Cacher (<15ms query) | P0 | v3.0.0-beta1 | Verified |
| **FR-GEO-02** | Real-Time Convoy Elevation Profile & Altitude Incline Math Engine (slope %, >12% warnings, $\Delta h$) | P0 | v3.0.0-beta1 | Verified |
| **FR-UI-01** | Zero-Scroll Tactical Cockpit HUD with glove-friendly touch targets ($\ge 64$dp) and 7:1 contrast | P0 | v3.0.0-alpha1 | Verified |
| **FR-UI-02** | Live VU meter and RMS decibel visualizer responsive to local and remote speech | P0 | v3.0.0-alpha1 | Verified |
| **FR-UI-03** | Tactile modal sheets: Diagnostics Sheet, Offline Trail Sheet, and Message Dialog | P0 | v3.0.0-alpha1 | Verified |
| **FR-UI-04** | Tactile Trail Elevation HUD Overlay with OLED curve, rider pins, and glove-friendly 64dp scale buttons | P0 | v3.0.0-beta1 | Verified |
| **FR-SAF-01** | Autonomous Crash Impact Detection with 15-second haptic countdown and eCall SOS mesh broadcast | P0 | v2.8.0 | Verified |

---

## 4. Non-Functional Requirements (NFR) & Quality Budgets

* **NFR-LAT-01 (Audio Latency):** One-way acoustic mouth-to-ear latency across 1 BLE hop shall not exceed **120ms**; across 3 hops, latency shall not exceed **220ms**.
* **NFR-PWR-01 (Battery Budget):** Continuous background audio transmission and mesh relay shall consume less than **7.5% battery per hour** on standard smartphone hardware (yielding 12+ hours continuous expedition life).
* **NFR-MEM-01 (Memory Footprint):** App heap memory footprint shall not exceed **85 MB** under 32-node simulated mesh load; zero memory leaks or unmanaged coroutine leaks permitted.
* **NFR-REL-01 (Continuous Playout):** Audio stream playout shall maintain intelligibility under up to **25% random burst packet loss** without audible distortion.
* **NFR-GEO-01 (Offline Map Performance):** Spatial vector bounding box queries shall execute in **< 15ms** from in-memory cache without cellular or internet connectivity.

