# Market Requirements Document (MRD) — Intercom-Alpha

> **Document Version:** 3.0.0-beta1  
> **Status:** Approved / Active Market Strategy  
> **Target Release:** v3.0.0 Production Suite (Milestone v3.0.0-beta1 Verified)  
> **Domain:** Off-Grid Outdoor Communications, Motorcycle Mesh Intercom, Backcountry Fleet Telemetry  
> **Target Audience:** Adventure Motorcyclists, MTB Pelotons, Overlanding Convoys, Alpine SAR Teams  

---

## 1. Executive Summary & Market Sizing

### 1.1 Market Problem & Opportunity
Group voice communication across high-velocity outdoor recreational environments and critical off-grid fleets is severely restricted by existing market dynamics:
1. **Extortionate Hardware Monopolies:** Incumbent motorcycle communicator brands (Cardo Systems Packtalk Edge, Sena Technologies 50S/60S) command price points between **$350 and $550 per rider**. For a recreational convoy of 6 riders or an expedition squad of 10, upfront hardware expenditure exceeds $2,500–$5,000.
2. **Proprietary Ecosystem Lock-In:** Cardo's Dynamic Mesh Communication (DMC) and Sena's Mesh 2.0/3.0 are mutually incompatible closed proprietary protocols. Inter-brand communication defaults to legacy single-hop Bluetooth HFP bridging, degrading audio quality and crippling mesh topology.
3. **Cellular VoIP Fragility:** Consumer VoIP solutions (WhatsApp, Discord, Zello) fail catastrophically once convoys enter mountainous terrain, wilderness canyons, or remote trails where cellular coverage drops to zero.
4. **Hardware Rigidity & Slow Feature Cycles:** Dedicated helmet communicators suffer from fixed DSP hardware, tiny physical buttons unusable with heavy winter/motorcycle gloves, lack of rich telemetry displays, and non-existent integration with navigation or satellite tracking layers.

**Intercom-Alpha** disrupts this market by delivering a **zero-hardware-cost, software-defined off-grid mesh intercom** powered entirely by standard iOS and Android smartphones paired with commodity Bluetooth headsets ($20–$50).

### 1.2 Total Addressable Market (TAM), SAM & SOM
* **Total Addressable Market (TAM):** $3.8 Billion global motorcycle and outdoor sports helmet communication market by 2028 (CAGR 14.2%).
* **Serviceable Addressable Market (SAM):** $1.2 Billion adventure touring, dual-sport, downhill mountain bike, and off-road 4x4 group communications segment.
* **Serviceable Obtainable Market (SOM):** $180 Million target across 4.5 million active smartphone-equipped adventure enthusiasts and commercial tour operators within 3 years of production release.

---

## 2. Competitive Landscape & Teardown

| Capability / Dimension | **Intercom-Alpha (v3.0.0-beta1)** | Cardo Packtalk Edge (DMC) | Sena 50S / 60S (Mesh 3.0) | Zello PTT Mobile | Garmin inReach Messenger |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Hardware Barrier** | **$0** (Uses existing smartphone) | **$389.95** per rider | **$359.00** per rider | $0 (App only) | **$299.99** + $15/mo sub |
| **Interoperability** | **100% Cross-Platform** (iOS + Android) | Closed Cardo DMC ecosystem | Closed Sena Mesh ecosystem | Cross-platform | Closed Iridium ecosystem |
| **Off-Grid Radio Mesh** | **Yes** (BLE Mesh + Wi-Fi Direct) | Yes (Proprietary 2.4 GHz) | Yes (Proprietary 2.4 GHz) | **No** (Cellular/Wi-Fi only) | No (Satellite text only) |
| **Cellular Failover** | **Automated Zero-Drop Hedging** | No | No | N/A (Fails off-grid) | Satellite failover (text only) |
| **Audio Intelligibility** | **Pure KMP ADPCM/Opus + FEC + PLC** | High (JBL custom DSP) | High (Harman Kardon DSP) | Moderate (Opus VoIP) | N/A (No real-time voice) |
| **RF Packet Reduction** | **BLE ATT MTU Packing (>50% drop)** | Fixed single-frame packets | Fixed single-frame packets | N/A (IP UDP) | N/A |
| **Offline Vector & Elevation** | **Pure KMP MVT + Alpine HUD (<15ms)**| No (Audio only) | No (Audio only) | No map support | Basic 2D track only |
| **Onboarding Time** | **< 3s** (Offline Camera QR Scan) | 30s–60s complex pairing | 30s–60s button presses | Account login required | Complex satellite pairing |
| **Visual Telemetry HUD** | **Tactile Zero-Scroll Cockpit HUD** | No (LED light pill / Audio) | Tiny 1-inch LCD screen | Basic chat list | Minimal monochrome screen |
| **Life-Safety eCall SOS** | **Automated Accel Impact + Voice** | Crash detection (Selected) | None | None | Manual SOS button trigger |

---

## 3. Target Customer Segments & User Personas

### 3.1 Persona 1: "The Convoy Captain" (Road Captain / Tour Leader)
* **Demographics:** 35–55 years old, organizes weekend motorcycle club rides of 8–16 riders.
* **Core Pain Points:** Frustration waiting 20 minutes before every ride to pair different helmet brands; constant worry about tail riders getting separated at traffic lights or mountain turns.
* **Must-Have Needs:** 2-second onboarding via QR code; automated straggler distance warnings; seamless voice handover to cellular when tail riders drop out of RF range.

### 3.2 Persona 2: "The Backcountry Enduro Rider" (Extreme Trail / MTB)
* **Demographics:** 22–40 years old, rides single-track dirt trails and alpine downhill runs with zero cellular connectivity.
* **Core Pain Points:** Cardo/Sena units get knocked off helmets by tree branches; high wind roar at downhill speeds; hands cannot leave handlebars.
* **Must-Have Needs:** Extreme wind noise suppression; high-sensitivity hands-free VOX; crash impact detection with emergency beacon broadcast.

### 3.3 Persona 3: "The Commercial Guide & SAR Coordinator" (B2B Fleet Operator)
* **Demographics:** Adventure tour companies, national park rangers, search and rescue volunteers.
* **Core Pain Points:** Maintaining an inventory of 30 expensive radio units with dead batteries and broken antennas; inability to track group GPS positions simultaneously with voice.
* **Must-Have Needs:** Low-bandwidth GPS trail overlays, multi-group mesh federation, stationary basecamp repeater support, and zero server infrastructure costs.

---

## 4. Business Model & Go-To-Market Strategy

### 4.1 Monetization Vectors
1. **Core Community Edition (B2C - Free & Open Source):**
   * Full decentralized BLE Mesh voice up to 8 nodes, QR onboarding, VOX/PTT, basic telemetry HUD.
   * Growth catalyst to drive viral grassroots convoy adoption.
2. **Pro Convoy Tier (B2C - $3.99/mo or $29.99/year per rider):**
   * High-definition 48 kbps adaptive audio, cloud SFU cellular bridging with multi-path hedging, offline topographic 3D terrain maps, 24-node mesh federation.
3. **Fleet / Commercial Operator Tier (B2B SaaS - $19.99/month per vehicle/node):**
   * Real-time convoy radar dashboard, remote basecamp repeater console, automated emergency eCall dispatcher integration, telemetry audit logging, and custom group encryption ratchets.

### 4.2 Key Success Metrics & Milestones
* **Market Penetration:** 100,000 active convoy rides logged within 6 months of v3.0 GA release.
* **Virality Coefficient:** $K \ge 2.4$ (Every convoy leader introduces an average of 4–6 new riders via QR onboarding).
* **Audio Satisfaction Index:** 98% intelligibility rating under 100 km/h wind conditions across certified Bluetooth helmet headsets.
