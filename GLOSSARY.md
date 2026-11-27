# Project Glossary — Intercom-Alpha

Authoritative domain language for the Intercom-Alpha system. All code, UI tokens, and agent communications must strictly adhere to these definitions.

## Rider intercom

- **Creator** starts a group.
- **Admin** manages membership. A group can have multiple admins.
- **Admission order** is the agreed sequence of joined members, used to select a successor when the last admin intentionally leaves.
- **Relay** is a peer forwarding traffic between reachable neighbors.
- **Hotspot host** supplies a local Wi-Fi network. This role is independent of admin authority.
- **Partition** occurs when members remain in the same group but no current network path connects some of them.

## Delivery planning

- **Sprint** is the delivery unit for a milestone outcome.
- **Sprint phase** is an independently closeable group of work whose ICA ID combines the sprint and two-digit phase number.
- **Story** is the implementation and review unit. Ralph uses the phase's ICA ID for its existing story entry.
- **GSD phase** is the historical or current roadmap reference for a workstream.

---

## 🏔️ Domain Vocabulary

### 1. Activity Profile (`ActivityProfile`)
A user-selected operational context that dynamically tailors visible HUD components, touch target geometry, and sensor polling rates.
* **`Motorbike` (Dual-Sport / Enduro / ADV)**: Glove-optimized (84pt PTT, guarded SOS, extreme contrast, slope % caution alerts, maximum haptics).
* **`Bicycle` (Road / Gravel / MTB)**: Streamlined, low-distraction audio-centric HUD with buddy proximity.
* **`Hiking` (Mountain Trail / Alpine Trekking)**: Topo elevation cross-section, compass bearing, barometric pressure, offline waypoint markers.
* **`Overland` (Car / 4x4 Convoy Driving)**: Dashboard-friendly view with high-visibility convoy spacing and large channel selectors.
* **`Minimal` (Casual / Everyday)**: Stripped-down zero-clutter interface showing only active channel, signal bar, who is speaking, and PTT.

### 2. Core Invariants (`CoreControls`)
Controls and telemetry elements guaranteed to always be visible and operable regardless of activity profile, theme, or screen configuration:
* **Active Channel / Group Dial**: Identifier and selector for the active mesh frequency or channel.
* **Push-To-Talk (PTT) Actuator**: Primary audio transmission actuator with tactile feedback.
* **Speaker Status**: Visual indicator of who is currently transmitting on the channel.
* **Link Health**: Real-time indication of mesh connectivity and network size.
* **Guarded SOS**: Emergency broadcast slider/action with multi-stage false-positive prevention.

### 3. Auxiliary Widgets (`AuxiliaryWidgets`)
Configurable, non-mandatory HUD modules that users can enable, disable, or reorder in Settings:
* **Dual VU Meter**: Visual audio dBFS and packet drop indicators.
* **Relative Radar Compass**: 360-degree vector peer locator.
* **Alpine Elevation HUD**: Elevation profile, grade %, and summit altitude delta.
* **Convoy Fleet Telemetry**: Inter-rider distance, hop count, and convoy order (Lead, Scout, Sweep).
* **RF Link Inspector**: Raw SNR (dBm), PHY rate (1M/2M/Coded S8), and packet error rate (PER).

### 4. Tactical Theme (`TacticalTheme`)
A semantic color and contrast token contract designed for field visibility:
* **`TacticalAmber`**: Primary default. OLED Pitch Black (`#000000`) background, Slate 900 (`#0F172A`) surfaces, locked Tactical Amber (`#F59E0B`) interaction accent.
* **`AlpineHighVis`**: High-key daylight theme for blazing snow/glare conditions.
* **`ForestEmerald`**: Low-light night vision green accent for forest and tactical trail navigation.
* **`DesertOchre`**: Warm sand and high-contrast earth tones for desert and dual-sport enduro.
* **`Monochrome`**: Pure high-contrast grayscale for zero-color distraction.

### 5. PTT Actuator (`PttActuator`)
The primary voice transmission control. Supports:
* **Hold-to-Talk**: Active transmission while depressed; immediate release.
* **Latched Hands-Free**: Optional 15-second auto-releasing voice transmission for technical terrain where both hands must remain on handlebars.

### 6. Battery-Saver Utilitarian Rendering (`LowPowerUiMode`)
A rendering discipline prioritizing zero GPU strain, static typography, and event-driven updates over continuous 60fps animations:
* **Event-Driven Repaints**: UI updates strictly when state changes (e.g. 5-second GPS delta packet, PTT press, speaker change), completely eliminating continuous 60fps display loops.
* **Text-First Vector Telemetry**: Replaces heavy rotating radar canvas and contour graphs with lightweight, static numeric cards (`[Lead: 140m Ahead ↑] [Sweep: 320m Behind ↓]`, `[Alt: 1,840m | Grade: +4.2%]`) when low-graphics mode is enabled.
* **Discrete 4-Segment LED Indicator**: Replaces continuous high-frequency audio visualizers with a lightweight 4-segment discrete LED level indicator updated at low frequency (100ms).
