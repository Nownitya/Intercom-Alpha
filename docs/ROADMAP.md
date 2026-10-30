# Master Engineering Roadmap & Milestones — Intercom-Alpha

> **Status:** Active / Tracked  
> **Current Version:** `v3.0.0-beta1` (Verified & Released)  
> **Active Sprint:** Sprint 37 (Phase 32: Autonomous Fleet Mesh Congestion Control & Adaptive PHY)  
> **Milestones:** Phase 1 to Phase 31 (Sprints 01–36) Completed | Phase 32 Active | Phases 33–34 Planned to v3.0.0 GA  

Master Project Hub: [[Intercom-Alpha|Master Project Hub]] • Kanban Board: [[Kanban|Agile Kanban]]

---

## 🏆 Completed Milestone Releases

### Milestone v1.0.0: Core Mesh Voice & Platform Foundation (Phases 1–6, Sprints 01–08)
* [x] Polymorphic signaling protocol and Ktor WebSocket server.
* [x] Android `AudioRecord`/`AudioTrack` PCM streaming with VOX thresholding.
* [x] iOS 100% Native SwiftUI (`ContentView.swift`) adhering to "Do Not Share UI on iOS" (Rule 1).
* [x] Thread-safe Swift <-> KMP bridge with cancellation handles (`IntercomBridge.kt`).
* [x] Offline camera-based QR code onboarding (`INTERCOM:v1:`).
* [x] Sliding-window packet deduplicator (5,000ms window, storm suppression).
* [x] Multi-hop mesh flooding relay (TTL=3).
* [x] Pure Kotlin RFC 8439 ChaCha20-Poly1305 AEAD cipher (`PacketCipher.kt`).
* [x] Long-running background foreground service with partial wake locks.
* [x] RFC 3550 adaptive jitter buffering and basic packet loss concealment.
* [x] Field range benchmark matrix (100m–500m LOS/NLOS).

### Milestone v2.0.0: Cloud Relay, Spatial Audio, GPS Telemetry & Cockpit (Phases 7–11, Sprints 09–13)
* [x] Ktor WebSocket Cloud Mesh Relay Gateway (`:server`, `:core`, `CloudRelayBridge.kt`).
* [x] Multi-channel sub-group partitioning and emergency priority voice preemption.
* [x] Binaural 3D spatial audio panning based on convoy formation.
* [x] GPS mesh telemetry beacon and delta coordinate broadcast.
* [x] Initial Compose Cockpit Dashboard and SwiftUI Native Cockpit HUD.
* [x] Cryptographic memory zeroization on session close.

### Milestone v2.12.0: Advanced Mesh Routing & Automated Multi-Path Failover (Phases 12–23, Sprints 14–25)
* [x] Stationary Basecamp Repeater UI and ultra-low-power cockpit mode.
* [x] Offline vector terrain overlays and OTA GPX/GeoJSON trail sharing.
* [x] Tactical convoy geofencing with straggler audio/visual alerts.
* [x] Autonomous crash impact detection (>6.5g) with eCall SOS mesh broadcast.
* [x] Pure KMP AODV-Lite dynamic directed routing (RREQ / RREP / RERR).
* [x] Multi-group inter-convoy gateway and cross-mesh federation.
* [x] Airtime QoS token-bucket traffic shaper with 4-tier strict preemption.
* [x] Pure KMP multi-path link quality arbiter ($Q_{link}$) and automated cellular/SFU stream hedging.

### Milestone v2.16.0: Fleet Hardening & Low-Bandwidth Visual Telemetry (Phases 24–27, Sprints 26–29)
* [x] Progressive 0.5 fps thumbnail snapshot mesh transport engine.
* [x] Concurrent multi-radio arbiter (Wi-Fi Direct P2P + BLE Mesh).
* [x] Fleet-wide over-the-air forward-secret group rekeying protocol.
* [x] Satellite communicator lifeline and alpine long-range RF gateway.
* [x] 8-hour continuous expedition soak benchmark with zero coroutine leaks.
* [x] Unified bridge interface certification across 32-node simulated fleet.

### Milestone v3.0.0-alpha1: Dedicated UI/UX Design System Foundations (Phase 28, Sprints 30–32)
* [x] Dedicated tactical design tokens, OLED true-black palette (#0A0D10), and 7:1 contrast ratio.
* [x] Zero-Scroll Tactical Cockpit HUD overhaul with 64dp+ glove-friendly touch targets.
* [x] Live VU meter and decibel visualizer responsive to real-time speech.
* [x] Tactile non-blocking sheet overlays: Offline Trail Sheet, Diagnostics Sheet, and Message Dialog.

### Milestone v3.0.0-alpha2: Resilient Media Transmission & Closed-Loop DSP (Phase 29, Sprints 33–34)
* [x] EWMA link quality scoring ($Q_{link}$) and dynamic adaptive bitrate scaling (16 kbps to 48 kbps).
* [x] Pure KMP systematic block erasure Forward Error Correction (FEC) with dynamic parity framing (4:1, 3:1, 2:1).
* [x] Pitch-synchronous waveform Packet Loss Concealment (PLC) with progressive 2.5 dB attenuation decay.
* [x] Closed-loop FEC redundancy governor bound to real-time link quality.
* [x] Verified zero dropped emergency frames and continuous speech playout under 25% burst packet loss.

### Milestone v3.0.0-alpha3: Ultra-Low Latency BLE ATT MTU Frame Packing & Interleaving (Phase 30, Sprint 35)
* [x] ATT MTU Frame Aggregator packing 2–4 voice frames into 247-byte payloads (>50% RF packet reduction).
* [x] Pure KMP Matrix Block Interleaver ($(M \times N)$ time dispersion converting radio burst drops into single erasures).
* [x] Adaptive frame assembly engine bound to negotiated BLE ATT MTU sizes (64–512 bytes).
* [x] High-density multi-node audio packet relay benchmark under severe packet collision scenarios.

### Milestone v3.0.0-beta1: Offline Vector Topographic & Trail Elevation Profile HUD (Phase 31, Sprint 36)
* [x] `OfflineVectorTileEngine`: Sub-15ms spatial query, in-memory LRU bounding-box caching, Mapbox Vector Tile (MVT) parsing.
* [x] `ConvoyElevationEngine`: Real-time moving slope grade calculation, steep descent/ascent warnings (>8% Caution, >12% Extreme), lead/tail vertical delta ($\Delta h$).
* [x] `TrailElevationHudOverlay` & Cockpit UI integration: Dedicated ELEV tab in Tactical Cockpit HUD, high-contrast OLED elevation profile with convoy rider pins and 64dp zoom controls.
* [x] Multiplatform bridge exposure via `IntercomBridge.kt` for native SwiftUI HUD consumption.
* [x] 12-node alpine elevation pass simulation test (`AlpineElevationSimulationTest`) with 100% test pass (506 tests).

---

## 🚀 Forward Roadmap to v3.0.0 Production GA

### 📍 Phase 32: Autonomous Fleet Mesh Congestion Control & Adaptive PHY (Sprint 37) — [ACTIVE SPRINT]
* **Target Release:** `v3.0.0-beta2`
* **Sprint Branch:** `sprint/sprint-37-fleet-congestion-control`
* **Goals:**
  * `ICA-3701`: Mesh Congestion Governor & Dynamic Broadcast Backoff (channel airtime measurement, adaptive backoff).
  * `ICA-3702`: Adaptive Multi-PHY Switching Manager (LE 1M, LE 2M, LE Coded S8 rate negotiation based on SNR/RSSI/BER).
  * `ICA-3703`: Cockpit RF & PHY Telemetry Indicators & Multiplatform Bridge Bindings.
  * `ICA-3704`: 32-Node High-Density RF Congestion & Alpine Range Simulation Test Suite.

### 📍 Phase 33: Multi-Squad Basecamp Master Dispatch Console & Fleet Telemetry (Sprint 38)
* **Target Release:** `v3.0.0-rc1` (Release Candidate 1)  
* **Goals:**
  * `ICA-3801`: Standalone Desktop/Web Basecamp Console connecting via stationary BLE/USB repeater node.
  * `ICA-3802`: Multi-squad real-time tracking radar with fleet-wide battery health and telemetry aggregation.
  * `ICA-3803`: Centralized all-call emergency broadcast override and audio logging blackbox exporter.
  * `ICA-3804`: Field deployment simulation harness with 3 remote squads and 1 basecamp master node.

### 📍 Phase 34: End-to-End Field Soak Benchmark, Battery Profiling & v3.0.0 GA (Sprint 39)
* **Target Release:** `v3.0.0` Production General Availability (GA)  
* **Goals:**
  * `ICA-3901`: 12-hour continuous multiplatform field expedition soak test across mixed Android/iOS convoy.
  * `ICA-3902`: Full battery profiling audit ensuring $< 7.5\%$ consumption per hour under continuous background relay.
  * `ICA-3903`: Release signing, ProGuard / R8 minification, native binary symbol stripping, and Play Store / TestFlight bundle generation.
  * `ICA-3904`: Production release documentation sign-off, public API documentation, and v3.0.0 release tagging.
