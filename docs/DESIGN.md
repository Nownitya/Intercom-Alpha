# UI/UX Design Specification — Intercom-Alpha

> **Document Version:** 2.12  
> **Status:** Active / Production Ready (`v2.12.0`)  
> **Design Philosophy:** Rugged, High-Contrast, Glove-Friendly Tactical Cockpit

---

## 🏍️ 1. Design Philosophy & Ergonomics

Intercom-Alpha is designed for high-stress outdoor environments (motorcycling, downhill mountain biking, convoy driving). The interface prioritizes **maximum glanceability**, **high-contrast sunlight readability**, and **glove-friendly touch targets**.

### Core Ergonomic Principles
1. **Glove-Friendly Touch Targets**: Primary action buttons (PTT, Mute, Route) must have a minimum touch target of $84 \times 84$ dp.
2. **Glanceable Status**: Riders should determine transmission status, group connection, and battery level in $< 0.5$ seconds with peripheral vision.
3. **Screen-Off Priority**: Every core feature (PTT, VOX, Volume) must be fully operable via hardware helmet buttons without turning the screen on.
4. **Zero Light Pollution at Night**: Deep true-black backgrounds (`#0A0D10`) avoid blinding riders in dark visors.

---

## 🎨 2. Color Palette & Design Tokens

### 2.1 Color Tokens

| Token | Hex Code | Purpose |
| :--- | :--- | :--- |
| `--bg-base` | `#0A0D10` | True-black OLED background |
| `--bg-surface` | `#15181C` | Card and modal surface background |
| `--bg-elevated` | `#1E2328` | Elevated containers and toolbars |
| `--border-subtle` | `rgba(255, 255, 255, 0.08)` | Subdued separators |
| `--border-focus` | `rgba(255, 255, 255, 0.24)` | Active focus borders |
| `--accent-ptt` | `#E14A0E` | Push-to-Talk active transmission (International Orange) |
| `--accent-ptt-glow`| `rgba(225, 74, 14, 0.35)` | Glowing pulse ring during voice transmission |
| `--status-online` | `#10B981` | Connected peer indicator (Emerald Green) |
| `--status-warning`| `#F59E0B` | VOX threshold / high latency warning (Amber) |
| `--status-error` | `#EF4444` | Disconnected / mesh packet drop (Crimson) |
| `--status-cloud` | `#3B82F6` | Cellular WebSocket cloud relay active (Sky Blue) |
| `--text-primary` | `#F8FAFC` | Primary labels, headers, active titles |
| `--text-secondary`| `#94A3B8` | Subtitles, peer counts, battery info |
| `--text-dim` | `#64748B` | Timestamp, GATT telemetry, metadata |

---

## 🎛️ 3. Tactical Avionics HUD & Specialized Components

### 3.1 Tactical Avionics Bar (`TacticalAvionicsBar`)
Located at the top of the cockpit display:
- **RF Quality Gauge**: Real-time link quality percentage ($Q_{link}$) with color-coded warning thresholds.
- **Failover Mode Badge**:
  - `BLE DIRECT`: Solid green badge.
  - `HEDGING`: Pulsing amber badge indicating active dual-stream packet hedging.
  - `CELLULAR SFU`: Blue badge indicating cloud relay routing.
- **Airtime QoS Pill**: Displays current airtime utilization percentage and priority queue depth.

### 3.2 Tactical Vector Radar (`TacticalVectorRadarView`)
Renders spatial positioning of convoy members:
- **Central Indicator**: Rider's vehicle orientation.
- **Lead Rider Tether Ring**: Dynamic radial geofence boundary around the road captain.
- **Member Blips**: Vector markers color-coded by hop count and speaking status.
- **Breach Warning Banner**: High-visibility crimson warning when convoy members drift beyond geofence boundaries.

### 3.3 Crash Pre-Alert Countdown Banner (`CrashAlertBanner`)
Upon detection of high-g collision impact (>6.5g):
- High-contrast emergency modal banner with 15-second countdown timer.
- Prominent 1-tap "I AM OK / CANCEL SOS" abort actuator.
- Haptic buzzer patterns confirming state transitions.

### 3.4 Over-The-Air Route Beaming Sheet (`TrailFileManagerSheet`)
Enables riders to beam GPX/GeoJSON trail tracks over BLE mesh:
- Route difficulty badges: `EASY` (green), `MODERATE` (blue), `HARD` (amber), `EXPERT` (red).
- Chunk transfer progress bar showing `X / Y chunks` and Adler-32 verification badge.
- 1-tap "BEAM TO CONVOY" actuator.

---

## 📱 4. Platform UI Isolation Architecture (Rule 1)

* **Android Cockpit**: Implemented using Jetpack Compose in `:app:sharedUI` (`HomeScreen.kt`, `CockpitScreen.kt`). Uses Navigation 3 with polymorphic routes.
* **iOS Cockpit**: Implemented using **100% Native SwiftUI** in `:app:iosApp` (`ContentView.swift`, `CockpitView.swift`). Connects to KMP shared logic exclusively via reactive `IntercomBridge.kt` with view lifecycle-bound `CancellationHandle` closures.
