# System Architecture — Intercom-Alpha

> **Document Version:** 3.0.0-beta1  
> **Status:** Active Golden Reference  
> **Repository:** `Intercom-Alpha`  
> **Architectural Pillars:** Pure KMP Engine | 100% Native SwiftUI on iOS | Compose on Android | Closed-Loop Resilient DSP  

---

## 1. High-Level Architectural Blueprint

Intercom-Alpha separates shared business and protocol algorithms from platform presentation layers, ensuring optimal performance, zero native memory leaks, and seamless cross-platform audio parity.

```
+-------------------------------------------------------------------------------+
|                            Presentation Layer                                 |
|   +------------------------------------+   +------------------------------+   |
|   |         iOS Platform               |   |       Android Platform       |   |
|   |   :app:iosApp                      |   |   :app:sharedUI              |   |
|   |   - 100% Native SwiftUI            |   |   - Compose Multiplatform    |   |
|   |   - Tactical Cockpit HUD & ELEV    |   |   - Zero-Scroll Tactile HUD  |   |
|   |   - Native Canvas Elevation Graph  |   |   - Dedicated ELEV Tab & HUD |   |
|   |   - No Shared Compose UI (Rule 1)  |   |   - Glove-Friendly 64dp Hits |   |
|   +-----------------+------------------+   +--------------+---------------+   |
+---------------------|-------------------------------------|-------------------+
                      | (IntercomBridge.kt)                 | (StateFlow)
+---------------------v-------------------------------------v-------------------+
|                     Kotlin Multiplatform Shared Logic                         |
|   :app:sharedLogic (commonMain)                                               |
|   +------------------------+ +-----------------------+ +--------------------+ |
|   |   Audio DSP Subsystem  | |  Resilience & Coding  | | Transport Arbiter  | |
|   |   - Energy VOX Detector| |  - Block Erasure FEC  | | - Q_link Scorer    | |
|   |   - Dual-Thresh Noise  | |  - Pitch-Sync PLC     | | - Multi-Path Hedge | |
|   |   - Jitter Buffer RFC35| |  - Matrix Interleaver | | - Dynamic Failover | |
|   +------------------------+ +-----------------------+ +--------------------+ |
|   +------------------------+ +-----------------------+ +--------------------+ |
|   |   Mesh Protocol & Dedu | |  Pure Kotlin Crypto   | | Airtime & Concession| |
|   |   - Multi-Hop TTL=3    | |  - RFC 8439 ChaCha20  | | - Token-Bucket 4-Tr| |
|   |   - Sliding-Window Dedu| |  - Poly1305 (26-limb) | | - Strict Preemption| |
|   |   - ATT MTU Aggregator | |  - HKDF-SHA256 Ratchet| | - Congestion Gov   | |
|   +------------------------+ +-----------------------+ +--------------------+ |
|   +------------------------+ +-----------------------+ +--------------------+ |
|   | Offline Vector Engine  | | Convoy Elevation Math | | Multi-PHY Arbiter  | |
|   | - Sub-15ms Spatial Qry | | - Slope Grade % Math  | | - LE 1M / LE 2M    | |
|   | - 64-Tile LRU Cache    | | - Caution >8%, Ext>12%| | - LE Coded S8 Long | |
|   | - MVT Protobuf Parsing | | - Lead/Sweep Delta-h  | | - Dynamic Switching| |
|   +------------------------+ +-----------------------+ +--------------------+ |
+-------------------------------------------------------------------------------+
                                        |
+---------------------------------------v---------------------------------------+
|                           Protocol & Cloud Relay                              |
|   :core                                       :server                         |
|   - Polymorphic Binary Signaling Frames       - Ktor 3.x Standalone SFU Relay |
|   - Zero Platform Dependencies                - Hybrid WebSocket Cloud Bridge |
+-------------------------------------------------------------------------------+
```

---

## 2. Architectural Decision Records (ADRs) Index

| ADR ID | Decision Title | Status | Primary Impact & Rationale |
| :--- | :--- | :--- | :--- |
| **ADR-001** | BLE Mesh as Primary Transport | Accepted | 100% off-grid voice operation with zero cellular reliance. |
| **ADR-002** | Opus Audio Codec for Mesh Voice | Accepted | High intelligibility voice compression at 16–48 kbps bitrates. |
| **ADR-003** | Decentralized Leaderless Topology | Accepted | Eliminates single points of failure; mesh survives leader dropout. |
| **ADR-004** | KMP Logic + 100% Native SwiftUI | Accepted | **Rule 1: No Shared UI on iOS**. Android uses Compose; iOS uses native SwiftUI. |
| **ADR-005** | Dual PTT & VOX Transmission Modes| Accepted | PTT for extreme wind/speed; VOX for technical downhill handling. |
| **ADR-006** | Foreground Service & Background Audio | Accepted | Continuous voice relay with smartphone locked in pocket/tank bag. |
| **ADR-007** | Offline QR Code Onboarding | Accepted | 2-second pre-ride onboarding via camera scan of `INTERCOM:v1:` QR. |
| **ADR-008** | Pure KMP RFC 8439 ChaCha20-Poly1305 | Accepted | High-speed authenticated encryption in 100% Kotlin with zero JNI. |
| **ADR-009** | Retention of `:core` and `:server` | Accepted | Cloud signaling and SFU relay fallback for remote convoy splits. |
| **ADR-010** | Pure KMP Audio Codec & MTU Packing | Accepted | Sub-MTU frame compression (< 128 bytes) preventing BLE fragmentation. |
| **ADR-011** | Adaptive Jitter Buffer & PLC | Accepted | RFC 3550 variance delay filter (40–150ms) + pitch loss concealment. |
| **ADR-012** | Mesh Airtime QoS Priority Shaping | Accepted | 4-tier token bucket shaper (Emergency > Voice > Control > Telemetry). |
| **ADR-013** | Automated Multi-Path Mesh Failover | Accepted | Link quality arbiter scoring RSSI/loss with proactive stream hedging. |
| **ADR-014** | Tactical Snapshot Mesh Transport | Accepted | MTU-bounded 0.5 fps thumbnail broadcast with Adler-32 chunking. |
| **ADR-015** | Dynamic Adaptive Bitrate Scaling | Accepted | EWMA $Q_{link}$ scoring dynamically adjusting codec bitrates (16–48 kbps). |
| **ADR-016** | Pure KMP Block Erasure FEC & PLC | Accepted | Multiplatform systematic FEC and pitch-synchronous waveform PLC. |
| **ADR-017** | Zero-Scroll Tactical Cockpit Design | Accepted | High-contrast OLED dark-mode HUD with 64dp+ glove-friendly touch targets. |
| **ADR-018** | BLE ATT MTU Packing & Interleaving | Accepted | Dynamic multi-frame aggregation (>50% RF reduction) and time-diversity block interleaving. |
| **ADR-019** | Offline Vector Tile & Elevation Profiling | Accepted | Pure KMP Mapbox vector tile parser (<15ms query) and real-time convoy elevation HUD. |
| **ADR-020** | Mesh Congestion Governor & Adaptive PHY | Accepted | Channel airtime occupancy throttling and autonomous LE 1M/2M/Coded S8 rate adaptation. |
| **ADR-021** | Context-Adaptive UI Profiles & Customization | Accepted | Zero-gimmick simple UI with activity presets (Motorbike, Bicycle, Hiking, Car, Minimal) and user-configurable widgets. |
