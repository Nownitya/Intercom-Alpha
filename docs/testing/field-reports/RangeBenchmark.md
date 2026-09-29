# Field Range Benchmark Matrix & Testing Protocol — Intercom-Alpha

> **Document Version:** 1.0  
> **Milestone:** Phase 6 / Sprint 08 (`ICA-801`)  
> **Status:** Active Field Protocol  
> **Obsidian Reference:** `03_Specifications/Protocols/Field-Range-Benchmark.md`

---

## 🎯 1. Executive Summary

This protocol defines the standardized methodology for measuring, validating, and reporting physical radio range, Packet Delivery Ratio (PDR), latency, and multi-hop forwarding efficiency for Intercom-Alpha across motorcycle, bicycle, and outdoor convoy environments.

All telemetry collected during field tests is computed by the pure KMP [`MeshDiagnostics`](file:///d:/Codes/Antigravity/Project4/Intercom-Alpha/app/sharedLogic/src/commonMain/kotlin/org/nowni/intercom_alpha/mesh/MeshDiagnostics.kt) engine.

---

## 📐 2. RF Link Budget & Target Benchmarks

### 2.1 Theoretical Link Budget
* **BLE Transmit Power ($P_{tx}$):** $+4\text{ dBm}$ (Class 2 standard) to $+8\text{ dBm}$ (High Power Android/iOS).
* **Receiver Sensitivity ($S_{rx}$):** $-93\text{ dBm}$ (Standard LE 1M PHY) / $-98\text{ dBm}$ (Coded PHY S=2).
* **Total Link Budget:** $97\text{ dB}$ to $101\text{ dB}$.
* **Fading Margin Target:** $\ge 10\text{ dB}$ for robust uninterrupted voice.

### 2.2 Acceptance Criteria Matrix

| Environment | Test Distance | Min PDR (%) | Target RSSI | Max Latency | Voice Intelligibility |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Line-of-Sight (LOS)** | 100 meters | $\ge 95\%$ | $\ge -78\text{ dBm}$ | $< 60\text{ ms}$ | Crystal Clear (MOS > 4.0) |
| **Line-of-Sight (LOS)** | 250 meters | $\ge 85\%$ | $\ge -85\text{ dBm}$ | $< 80\text{ ms}$ | High Intelligibility (MOS > 3.5) |
| **Line-of-Sight (LOS)** | 500 meters | $\ge 65\%$ (Direct)<br>$\ge 85\%$ (Relayed) | $\ge -90\text{ dBm}$ | $< 180\text{ ms}$ | Intelligible with PLC |
| **Obstructed (NLOS)** | 50 meters | $\ge 85\%$ | $\ge -82\text{ dBm}$ | $< 70\text{ ms}$ | High Intelligibility |
| **Obstructed (NLOS)** | 100 meters | $\ge 70\%$ | $\ge -88\text{ dBm}$ | $< 100\text{ ms}$ | Usable with minor PLC |
| **Multi-Hop (2 Hops)** | 350 meters | $\ge 85\%$ | $\ge -80\text{ dBm}$ | $< 140\text{ ms}$ | High Intelligibility |
| **Multi-Hop (3 Hops)** | 600 meters | $\ge 80\%$ | $\ge -80\text{ dBm}$ | $< 220\text{ ms}$ | Clear Convoy Relay |

---

## 🏍️ 3. Physical Test Configurations

### 3.1 Device Placement Profiles
1. **Jacket Chest Pocket (Recommended):** Antenna facing forward; minimal body absorption when facing oncoming riders.
2. **Handlebar Mount:** Optimal open-air RF radiation pattern; maximum range performance.
3. **Pants Pocket / Tank Bag:** High body shielding (attenuation $-8\text{ dB}$ to $-14\text{ dB}$); evaluates worst-case antenna detuning.

### 3.2 Environmental Test Zones
* **Zone A (Open Highway / Desert / Airstrip):** Free-space path loss exponent $n \approx 2.1$. Measures pure open-air RF ceiling.
* **Zone B (Winding Mountain Switchback):** Rock walls, dense trees, non-line-of-sight bends. Path loss exponent $n \approx 3.2$. Evaluates multi-hop relay hop-over when rider A loses line of sight to rider C, but rider B is at the hairpin turn apex.
* **Zone C (Urban Convoy / Heavy Traffic):** Moving metal vehicles, 2.4 GHz Wi-Fi interference. Evaluates frequency-hopping robustness and deduplication.

---

## 🔬 4. Multi-Hop Relay Test Methodology

```text
[Rider 1: Lead] <────── 200m ──────> [Rider 2: Mid-Convoy] <────── 200m ──────> [Rider 3: Sweep]
  Direct Distance: 400m (Out of direct 1-hop range or < -92 dBm)
  Relay Route: Rider 1 ➔ Rider 2 (TTL=3) ➔ Rider 3 (TTL=2)
  Expected Hop Count Distribution at Rider 3:
    Hop 1: 0%  (Direct link severed)
    Hop 2: 95%+ (Packets routed cleanly through Rider 2)
```

1. Position Rider 1 and Rider 3 at 400 meters apart until direct voice drops.
2. Position Rider 2 at the 200-meter midpoint.
3. Verify that voice transmission immediately re-engages across Rider 1 and 3.
4. Verify via `MeshDiagnostics.getPeerStats("rider-1").hopDistribution` that packets show `lastHopCount = 2`.

---

## 📝 5. Field Test Log Template

Testers must execute the run and append results using the following matrix:

```markdown
### 📋 Test Run Record
- **Date & Time:** 2026-XX-XX HH:MM
- **Location:** [e.g., Highway 101 / Angeles Crest Highway]
- **Weather / Temp:** Clear, 22°C, Dry
- **Rider Count:** 3 (Alpha, Beta, Charlie)
- **Device Models:** Pixel 8 (Android 15), iPhone 15 Pro (iOS 18)
- **Audio Profile:** MEDIUM (24 kHz IMA ADPCM, 20ms frames)

| Distance (m) | Terrain Type | Nodes Involved | Measured RSSI | PDR (%) | Avg Hops | Latency (ms) | Subjective Voice Quality |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 100m | Open LOS | Alpha ➔ Beta | -72 dBm | 98.4% | 1.0 | 45 ms | 5/5 (Flawless) |
| 250m | Open LOS | Alpha ➔ Beta | -84 dBm | 89.2% | 1.0 | 52 ms | 4/5 (Clean voice) |
| 400m | Mountain Bend | Alpha ➔ Charlie (via Beta) | -76 dBm | 91.5% | 2.0 | 125 ms | 4.5/5 (Continuous relay) |
```

---

## 🛠️ 6. Programmatic Telemetry Dump

To output a formatted markdown diagnostics snapshot directly from code or test scripts:

```kotlin
val diagnostics = MeshDiagnostics()
// ... during or after test run ...
val report = diagnostics.generateReport()
println(report.toMarkdownSummary())
```
