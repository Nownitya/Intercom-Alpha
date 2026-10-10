# Product Requirements Document (PRD) — Intercom-Alpha

> **Document Version:** 2.12  
> **Status:** Released (`v2.12.0`) / Active Sprint 26 (`sprint/sprint-26-fleet-hardening`)  
> **Last Updated:** 2026-10-10  
> **Repository:** `Intercom-Alpha`  
> **Related Documents:** `docs/FEATURES.md`, `docs/BRD.md`, `docs/TRD.md`, `docs/ARCHITECTURE.md`

---

## 1. Product Vision & Goals

### 1.1 Vision Statement
**Intercom-Alpha** transforms standard Android and iOS smartphones into resilient, decentralized, off-grid convoy communicators. It provides real-time, low-latency group voice over Bluetooth Low Energy (BLE) Mesh with zero internet, zero accounts, and zero monthly subscriptions, augmented by automated hybrid cellular cloud relay handover when groups drift out of radio range.

### 1.2 Core Product Pillars
1. **Zero-Setup Offline Onboarding:** Join a convoy in 2 seconds via camera scan of an offline `INTERCOM:v1:` QR code containing group credentials and session encryption keys.
2. **Leaderless Multi-Hop Mesh Flooding:** Conversations traverse up to 3 intermediate hops ($TTL=3$), extending communication range beyond physical radio line-of-sight.
3. **Adaptive Audio Intelligibility:** Pure KMP DSP pipeline combining RFC 3550 adaptive jitter buffering (40ms–150ms), pitch-extrapolated Packet Loss Concealment (PLC), and dual-threshold wind noise gating.
4. **Airtime QoS Traffic Shaping:** Strict priority queueing ensuring life-safety emergency broadcasts preempt all background voice and telemetry packets without dropped frames.
5. **Seamless Multi-Path Failover:** Continuous link quality scoring ($Q_{link}$) with proactive stream hedging preventing audio gaps during highway convoy separations.
6. **Strict Platform Isolation:** Pure Kotlin Multiplatform for shared protocol/audio logic, Jetpack Compose for Android, and 100% Native SwiftUI for iOS (strictly no shared Compose UI on iOS).

---

## 2. User Personas & Core Workflows

### 2.1 Personas
* **Lead Rider (Navigator):** Needs tactical voice access to warn the group of road hazards, route changes, or overtaking vehicles; monitors convoy member distance and straggler warnings.
* **Sweep / Tail Rider:** Often lags behind due to traffic or red lights; relies on multi-hop relay and automated cellular failover to maintain contact with the lead.
* **Solo Off-Grid Adventurer:** Downhill MTB or backcountry enduro rider who depends on hands-free VOX activation and automated crash impact eCall SOS broadcasts.

### 2.2 End-to-End User Journeys
```
[Pre-Ride]
Lead rider taps "Create Group" ──► App displays INTERCOM:v1: QR Code
Follower riders tap "Join" ──► Scan QR via camera ──► Mutual AES/ChaCha20 key established

[On-Road (In RF Range)]
Rider presses helmet button / speaks via VOX ──► Mic capture (16kHz PCM) ──► AudioCodec compress
──► Pure KMP ChaCha20 AEAD Encrypt ──► Local BLE GATT Flooding (TTL=3) ──► Peers receive & playout

[Highway Split (RF Degradation)]
Rider drifts > 200m away ──► Link Quality Arbiter detects Q_link < 70 ──► Hedging Mode activates
──► Audio frames concurrently sent over BLE & Cellular SFU ──► Receiver deduplicates frames
──► Q_link drops < 30 ──► Full cellular handover with zero dropped words

[Accident / Emergency]
Sudden deceleration (> 6.5g) detected ──► 15-second countdown HUD with haptic abort option
──► SOS Beacon broadcast preempts all mesh traffic (Priority 0) ──► High-priority voice alarm to all peers
```

---

## 3. Detailed Functional Requirements Matrix

### 3.1 Audio Capture, Processing & Codec (FR-AUD)
| ID | Requirement | Priority | Target Release | Status |
|---|---|---|---|---|
| **FR-AUD-01** | Low-latency audio capture via Android `AudioRecord` and iOS `AVAudioEngine` input tap. | P0 | v1.0.0 | Verified |
| **FR-AUD-02** | Configurable Push-To-Talk (PTT) via screen UI and external Bluetooth media buttons (`KEY_HEADSETHOOK`). | P0 | v1.0.0 | Verified |
| **FR-AUD-03** | Hands-free Voice Activity Detection (VOX) with adjustable energy threshold (-20 dBFS to -60 dBFS). | P0 | v1.0.0 | Verified |
| **FR-AUD-04** | Dual-threshold adaptive noise gate (-35 dBFS attack, -42 dBFS release) to suppress helmet wind roar. | P0 | v1.0.0 | Verified |
| **FR-AUD-05** | Pure KMP multi-rate 4-bit IMA ADPCM audio codec fitting frames within single BLE GATT MTUs. | P0 | v1.0.0 | Verified |
| **FR-AUD-06** | Adaptive Jitter Buffer (RFC 3550 variance filter) with dynamic 40ms–150ms target delay. | P0 | v1.0.0 | Verified |
| **FR-AUD-07** | Packet Loss Concealment (PLC) via pitch autocorrelation and -3 dB cumulative attenuation. | P0 | v1.0.0 | Verified |
| **FR-AUD-08** | Binaural 3D spatial audio processor panning peer voices according to relative convoy position. | P1 | v2.0.0 | Verified |

### 3.2 Mesh Transport & Networking (FR-NET)
| ID | Requirement | Priority | Target Release | Status |
|---|---|---|---|---|
| **FR-NET-01** | BLE GATT Dual-Role Server/Client advertising and scanning with standard 128-bit Service UUIDs. | P0 | v1.0.0 | Verified |
| **FR-NET-02** | Multi-hop flood relay decrementing TTL ($3 \rightarrow 2 \rightarrow 1$) across intermediate nodes. | P0 | v1.0.0 | Verified |
| **FR-NET-03** | Sliding-window deduplication cache (5,000ms TTL, 1,000 capacity) suppressing storm loops. | P0 | v1.0.0 | Verified |
| **FR-NET-04** | AODV-Lite route discovery (RREQ/RREP/RERR) for point-to-point unicast route optimization. | P1 | v2.9.0 | Verified |
| **FR-NET-05** | Airtime QoS Traffic Shaper enforcing strict preemption (Emergency > Voice > Control > Telemetry). | P0 | v2.11.0 | Verified |
| **FR-NET-06** | Multi-Path Link Quality Arbiter scoring RSSI, loss, and jitter into unified $Q_{link}$ metric. | P0 | v2.12.0 | Verified |
| **FR-NET-07** | Automated dual-stream packet hedging across BLE and Ktor WebSocket SFU cloud relay. | P0 | v2.12.0 | Verified |

### 3.3 Group Governance & Security (FR-SEC)
| ID | Requirement | Priority | Target Release | Status |
|---|---|---|---|---|
| **FR-SEC-01** | Offline camera QR code generator and scanner encoding `INTERCOM:v1:` payload. | P0 | v1.0.0 | Verified |
| **FR-SEC-02** | Pure Kotlin RFC 8439 ChaCha20-Poly1305 AEAD cipher with zero Java/JNI dependencies. | P0 | v1.0.0 | Verified |
| **FR-SEC-03** | Ephemeral per-session symmetric key derivation with memory zeroization on session close. | P0 | v1.0.0 | Verified |
| **FR-SEC-04** | Forward-secret key ratcheting updating session keys upon group membership alteration. | P1 | v2.1.0 | Verified |
| **FR-SEC-05** | Multi-tenant encryption key management isolating sub-groups and federated convoys. | P1 | v2.10.0 | Verified |

### 3.4 Hardware, Telemetry & Safety (FR-HW)
| ID | Requirement | Priority | Target Release | Status |
|---|---|---|---|---|
| **FR-HW-01** | Bluetooth SCO audio routing with helmet headset button event hooks. | P0 | v1.0.0 | Verified |
| **FR-HW-02** | Android Foreground Service (`microphone|connectedDevice`) and Partial WakeLock (4h safety limit). | P0 | v1.0.0 | Verified |
| **FR-HW-03** | iOS `UIBackgroundModes` (`audio`, `bluetooth-central`, `bluetooth-peripheral`) keep-alive. | P0 | v1.0.0 | Verified |
| **FR-HW-04** | GPS mesh telemetry beacon broadcasting compressed location coordinates. | P1 | v2.0.0 | Verified |
| **FR-HW-05** | Multi-axis accelerometer crash detection (> 6.5g impact) with automated eCall SOS beacon. | P0 | v2.8.0 | Verified |
| **FR-HW-06** | Lead rider dynamic radial geofencing with straggler auditory separation alerts. | P1 | v2.7.0 | Verified |

---

## 4. Non-Functional Requirements (NFR)

* **NFR-01 (Audio Latency):** 1-hop direct BLE transmission latency must remain $\le 80\text{ms}$; 3-hop relay latency must remain $\le 240\text{ms}$.
* **NFR-02 (Power & Battery):** Continuous background relay with phone screen off must consume $< 7.5\%$ battery per hour on standard 4,000 mAh devices.
* **NFR-03 (Memory & Stability):** Zero memory growth (leaks) during continuous 8-hour soak testing; JVM heap allocation capped at 48 MB.
* **NFR-04 (Architecture Law):** Zero Compose UI dependencies compiled into iOS; iOS client strictly 100% native SwiftUI.
* **NFR-05 (Build & Test Quality):** 100% pass rate across multiplatform test suite (`./gradlew test`) and 0 findings in OpenCodeReview.
