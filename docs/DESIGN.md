# UI/UX Design Specification — Intercom-Alpha

> **Document Version:** 1.0  
> **Status:** Active / Engineering Ready  
> **Design Philosophy:** Rugged, High-Contrast, Glanceable Cockpit Interface

---

## 🎨 1. Design Philosophy & Ergonomics

Intercom-Alpha is designed for high-stress outdoor environments (motorcycling, downhill mountain biking, convoy driving). The interface prioritizes **maximum glanceability**, **high-contrast sunlight readability**, and **glove-friendly touch targets**.

### Core Ergonomic Principles
1. **Glove-Friendly Touch Targets**: Primary action buttons (PTT, Mute, Route) must have a minimum touch target of $84 \times 84$ dp.
2. **Glanceable Status**: Riders should determine transmission status, group connection, and battery level in $< 0.5$ seconds with peripheral vision.
3. **Screen-Off Priority**: Every core feature (PTT, VOX, Volume) must be fully operable via hardware helmet buttons without turning the screen on.
4. **Zero Light Pollution at Night**: Deep true-black backgrounds (`#0B0E11`) avoid blinding riders in dark visors.

---

## 🌈 2. Color Palette & Design Tokens

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
| `--text-primary` | `#F8FAFC` | Primary labels, headers, active titles |
| `--text-secondary`| `#94A3B8` | Subtitles, peer counts, battery info |
| `--text-dim` | `#64748B` | Timestamp, GATT telemetry, metadata |

---

## 🔤 3. Typography Hierarchy

| Style | Font Family | Size / Weight | Line Height | Tracking | Usage |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Display** | System Grotesque / Inter | 32sp / Bold (700) | 38sp | -0.02em | PTT state ("TRANSMITTING", "LISTENING") |
| **Headline** | System Grotesque / Inter | 20sp / SemiBold (600) | 26sp | -0.01em | Group Name, Screen Titles |
| **Body Large** | System Grotesque / Inter | 16sp / Regular (400) | 22sp | 0.00em | Peer names, primary settings |
| **Body Medium**| System Grotesque / Inter | 14sp / Medium (500) | 20sp | +0.01em | Secondary labels, button captions |
| **Telemetry** | JetBrains Mono / SF Mono | 12sp / Medium (500) | 16sp | +0.04em | RSSI (-58 dBm), Bitrate (32 kbps), Codecs |

---

## 🔘 4. Core Component Specifications

### 4.1 Push-To-Talk (PTT) Button Component

```
  ┌──────────────────────────────────────────────┐
  │                                              │
  │                  ▲ DRAG UP                   │
  │                   TO LOCK                    │
  │                                              │
  │              ╭──────────────╮                │
  │             │   ((  ●  ))    │                │
  │             │                │                │
  │             │      HOLD      │                │
  │             │    TO TALK     │                │
  │             │                │                │
  │              ╰──────────────╯                │
  │                                              │
  │               VOX: ACTIVE (-40dB)            │
  └──────────────────────────────────────────────┘
```

#### States & Interactions:
1. **Idle State**:
   - Background: Dark Charcoal (`#1E2328`) with subtle white border.
   - Text: `"HOLD TO TALK"` in secondary text color.
2. **Pressed State (Transmitting)**:
   - Trigger: User touches and holds the button.
   - Background: Vibrant Orange (`#E14A0E`) with an animated expanding pulse ring.
   - Text: `"TRANSMITTING"` in primary white text.
   - Haptics: Short 20ms tactile impact vibration on press.
3. **Locked State (Hands-Free)**:
   - Trigger: User drags finger upward $> 50$ dp while pressing.
   - Background: Amber Ring (`#F59E0B`) with padlock icon.
   - Text: `"LOCKED ON"` with tap-to-release hint.
   - Haptics: Double-pulse tactile vibration on lock engaged.
4. **Released State**:
   - Haptics: Light release vibration tick.

---

### 4.2 Live Audio Visualizer & RMS Volume Meter

- **Dual-Channel VU Meter**:
  - Top Bar: **Microphone Level** (local input).
  - Bottom Bar: **Master Mesh Level** (composite incoming peer audio).
- **Decibel Scale**: $-60\text{ dB}$ (noise floor) up to $0\text{ dB}$ (clipping).
- **VOX Gate Indicator**: A vertical neon tick marks the active VOX threshold. When mic level exceeds the tick, voice transmission triggers instantly.

---

### 4.3 Mesh Peer Card & Topology Grid

Each connected rider appears as a high-contrast card in the peer grid:

```
┌────────────────────────────────────────┐
│ 🟢 Marco (Leader)          -58 dBm 📶  │
│ 🔋 85%  ·  Opus 32kbps  ·  Hop: 1 (Direct)│
│ [━━━━━━━●━━━━━━━━━━━━━━━━━━━━━━━━━━━━] │  ← Speaking Audio Meter
└────────────────────────────────────────┘
```
- **Pulsing Green Halo**: Appears around the card avatar when the peer is actively speaking over the mesh.
- **Hop Badge**:
  - `Direct (1 Hop)`: Green badge for immediate BLE neighbor.
  - `Relayed (2 Hops)`: Cyan badge for packets forwarded through an intermediate rider.
  - `Multi-Hop (3 Hops)`: Amber badge indicating max TTL multi-hop distance.

---

### 4.4 Offline QR Scanner & Share Modal

- **Scanner View**: Full-screen camera view with a high-contrast white reticle and instant auto-focus. No internet connection indicator needed.
- **Share View**: High-density QR code rendered at 300 DPI with the screen automatically boosted to 100% brightness for scanning through helmet visors or tinted goggles.

---

## 📱 5. Primary Screen Layouts

### 5.1 Screen 1: Active Intercom Cockpit (`HomeRoute`)
1. **Top Bar**: Active Group Name (`"Rally Team Alpha"`), peer counter (`"4/8 online"`), and battery/headset status icon.
2. **Center Stage**: Giant circular PTT Button ($200 \times 200$ dp) with drag-to-lock guide.
3. **Lower Half**: 2-column scrollable grid of Peer Cards with live speaking indicators.
4. **Bottom Bar**: VOX toggle switch, Audio Route selector (Bluetooth SCO / Phone / Speaker), and Group Settings button.

### 5.2 Screen 2: Group Management & QR Sheet
1. **Header**: Group Title, Leader identity badge, and creation timestamp.
2. **Center**: Large centered QR code displaying `INTERCOM:v1:<base64>`.
3. **Actions**:
   - `"Scan QR Code"` button (opens camera).
   - `"Share Invite Text"` button.
   - `"Leave Group"` (red destructive button).
