# Business Requirements Document (BRD) — Intercom-Alpha

> **Document Version:** 2.12  
> **Status:** Approved / Active (`v2.12.0`)  
> **Product Name:** Intercom-Alpha  
> **Category:** Off-Grid Mesh Voice Communication & Fleet Telemetry Platform  
> **Target Audience:** Motorcycle Convoys, Downhill Cycling Pelotons, Off-Road Overlanders, Search & Rescue Teams

---

## 1. Executive Summary & Market Opportunity

### 1.1 The Market Problem
Group voice communication in remote and high-speed transit environments is currently broken:
1. **Expensive Hardware Monopolies:** Leading motorcycle communicators (Cardo Packtalk, Sena 50S/60S) cost **$300 to $500 per rider**. A convoy of 6 riders requires an upfront hardware investment exceeding $2,000.
2. **Proprietary Ecosystem Walled Gardens:** Cardo's Dynamic Mesh Communication (DMC) and Sena's Mesh 2.0/3.0 are incompatible. Riders across different hardware brands cannot talk to each other without clumsy, single-channel Bluetooth bridge pairings.
3. **Obsolete User Experience:** Existing helmet units rely on complex multi-button taps, tiny LCD screens, or buggy companion apps that offer zero real-time telemetry or cross-mesh bridging.
4. **Cellular VoIP Failure:** Consumer apps (Discord, WhatsApp, Zello) fail catastrophically in mountain passes, deep valleys, and off-grid trails where cellular reception drops to zero.

### 1.2 The Intercom-Alpha Solution
**Intercom-Alpha** is an open, software-defined voice intercom platform that turns standard iOS and Android smartphones into high-performance, decentralized mesh communicators. By pairing commodity smartphones with standard, inexpensive Bluetooth headsets ($20–$50) and leveraging local **Bluetooth Low Energy (BLE) Mesh flooding**, Intercom-Alpha enables seamless, zero-cost, off-grid group intercom. When groups separate beyond radio line-of-sight, the system dynamically bridges separated sub-clusters over cellular WebSocket relays without dropped audio.

```
Hardware Headsets ($400/rider) ──► Replaced by ──► Software BLE Mesh on Smartphones ($0/rider)
Closed Ecosystems (Cardo vs Sena) ──► Replaced by ──► Cross-Platform Multiplatform Standard (Android ↔ iOS)
Dead Zones (0 Cellular Bars) ──► Replaced by ──► Peer-to-Peer Mesh + Autonomous Cloud Failover
```

---

## 2. Business Objectives & Success Criteria

### 2.1 Strategic Goals
* **Democratize Convoy Intercom:** Lower the financial barrier to entry from $400/rider to $0 for anyone with a smartphone and a basic Bluetooth helmet headset.
* **Universal Cross-Platform Interoperability:** Guarantee that Android and iOS riders communicate with symmetric audio quality and latency.
* **Zero Infrastructure Overhead:** Operate core local mesh functionality with $0 server hosting costs, zero cloud dependencies, and zero user account infrastructure.
* **Dual-Market Expansion:** Serve recreational adventure riders (B2C) while creating enterprise and fleet opportunities (B2B) for commercial guide companies, rally events, and emergency responders.

### 2.2 Key Performance Indicators (KPIs)

| Metric | Target | Business Impact |
|---|---|---|
| **Hardware Cost Reduction** | **90% savings** vs Cardo/Sena | Massive consumer adoption velocity |
| **Onboarding Time** | **< 5 seconds** via QR Code | Eliminates pre-ride frustration and churn |
| **Battery Consumption** | **< 7.5% per hour** (4h+ ride) | Guarantees all-day expedition reliability |
| **Zero-Cellular Availability** | **100% uptime** in RF range | Trust in life-safety and emergency scenarios |
| **Failover Audio Continuity** | **0 dropped syllables** | Uninterrupted coordination across highway splits |
| **Crash Safety Response** | **< 1.0s SOS dispatch** | Rapid convoy response in accident scenarios |

---

## 3. Stakeholder Analysis & Target Personas

### 3.1 Consumer Personas (B2C)
1. **Motorcycle Touring Groups (Weekend Warriors & Long-Haul):**
   - *Needs:* Crystal-clear conversation over 80–120 km/h highway wind noise, helmet button integration, reliable group coordination.
   - *Pain Points:* High cost of upgrading entire group to matching Cardo units; Bluetooth bridge latency and frequent disconnections.
2. **Enduro & Mountain Bike (MTB) Downhill Riders:**
   - *Needs:* Hands-free VOX activation, zero phone handling, lightweight setup, offline mountain canyon operation.
   - *Pain Points:* No cellular coverage in backcountry singletracks; cannot take hands off handlebars.
3. **Overland Adventure & 4x4 Vehicle Convoys:**
   - *Needs:* Multi-vehicle range extension, breadcrumb GPS trail sharing, basecamp repeater nodes.
   - *Pain Points:* CB/VHF radios require licensing, bulky hardware, and suffer from static distortion.

### 3.2 Commercial & Enterprise Personas (B2B)
1. **Guided Adventure Tour Operators:**
   - Need to provision temporary communication for 10–20 tourist riders without issuing expensive hardware headsets.
2. **Rally & Motorsport Race Marshals:**
   - Need ad-hoc convoy tracking, emergency preemption, and rapid checkpoint radio coordination.
3. **Search & Rescue (SAR) Volunteer Teams:**
   - Need off-grid tactical mesh communication that functions in disaster zones with collapsed cellular infrastructure.

---

## 4. Competitive Landscape & Market Differentiation

| Feature / Capability | Intercom-Alpha | Cardo Packtalk Pro | Sena 50S | Zello PTT | Walkie-Talkies (FRS/GMRS) |
|---|---|---|---|---|---|
| **Per-Rider Hardware Cost** | **$0** (uses phone) | $399 | $359 | $0 | $50–$150 |
| **Works Offline (Zero Cell)**| **Yes (BLE Mesh)** | Yes (Proprietary DMC) | Yes (Proprietary Mesh) | **No** (Requires Cell) | Yes (RF Analog) |
| **Cross-Platform / Brand** | **Yes (Android + iOS)**| **No** (Walled Garden) | **No** (Walled Garden) | Yes | Channel-dependent |
| **Cellular Cloud Failover** | **Automated Hybrid** | None | None | Cell-Only | None |
| **Hands-Free VOX** | Yes (Adaptive DSP) | Yes | Yes | Limited | VOX clips syllables |
| **Tactical GPS Telemetry** | Yes (Off-Grid Mesh)| None | None | Cell-Only | None (Unless GPS Radio) |
| **Emergency SOS Preemption** | **Yes (Priority 0)**| Crash Alert (SMS only) | Crash Alert | None | None |
| **Account Required** | **None (Zero Sign-in)**| Yes (Cardo App) | Yes (Sena App) | Yes (Email/Phone) | None |

---

## 5. Monetization Strategy & Business Model

Intercom-Alpha employs an **Open-Core & Dual-Tier Enterprise Model**:

```
┌────────────────────────────────────────────────────────┐
│             CORE TIER: 100% Free & Open-Source         │
│  • Unlimited off-grid BLE Mesh Voice & Multi-hop Relay │
│  • QR code onboarding, PTT/VOX, Offline Noise Gate     │
│  • Consumer motorcycle & cycling group use             │
└──────────────────────────┬─────────────────────────────┘
                           │ Upgrades to
┌──────────────────────────▼─────────────────────────────┐
│          COMMERCIAL / FLEET TIER (B2B SaaS)            │
│  • Hosted Ktor SFU Cloud Relay for Global Cross-Mesh   │
│  • Multi-Group Inter-Convoy Federation Gateways        │
│  • Dispatch Console & Fleet Blackbox Voice Recorder    │
│  • Satellite Communicator Bridge (Garmin inReach / SOS)│
└────────────────────────────────────────────────────────┘
```

---

## 6. Regulatory, Legal & Safety Compliance

1. **Spectrum & Radio Licensing:**
   - Operates entirely within the globally unlicensed **2.4 GHz ISM band** using standard Bluetooth Low Energy (Bluetooth SIG Core Spec 5.0+). No FCC, CE, or amateur radio operator licenses required.
2. **User Privacy & Data Protection (GDPR / DPDPA / CCPA):**
   - **Zero User Telemetry Collection:** By default, no personal information, voice recordings, or location coordinates are uploaded to external servers.
   - Group onboarding credentials and symmetric encryption keys remain strictly ephemeral on-device.
3. **Road Safety & Hands-Free Laws:**
   - Complies with road transport hands-free legislation: supports physical helmet button triggers (`KEY_HEADSETHOOK`, `KEY_MEDIA_PLAY_PAUSE`) and glove-friendly zero-scroll UI to avoid rider distraction.
