# Design System & UI/UX Specification — Intercom-Alpha

> **Document Version:** 3.0.0  
> **Status:** Active Design Contract  
> **Theme:** Zero-Scroll Tactical Cockpit HUD  
> **Target Frameworks:** Jetpack Compose Multiplatform (Android/Desktop) & 100% Native SwiftUI (iOS)  

---

## 1. Tactical Design Philosophy & Core Principles

The Intercom-Alpha interface is engineered specifically for **harsh outdoor, high-speed, and gloved-hand environments**:
1. **Zero-Scroll Viewport:** All mission-critical controls (PTT pill, VU meter, Channel Dial, Proximity Radar, SOS) must fit completely on screen without scrolling.
2. **Glove-First Geometry:** Minimum touch target size of **64dp x 64dp** for primary actions, with generous padding to prevent accidental mis-hits while riding over rough terrain.
3. **High-Contrast Tactical Palette:** Pure OLED pitch black background with neon tactical accents providing a minimum **7:1 contrast ratio**, ensuring readability under direct blazing sunlight.
4. **Haptic & Acoustic Multimodal Feedback:** Every critical state change (VOX open, PTT transmit start, channel switch, packet loss alert, SOS beacon) triggers unique haptic vibration signatures and acoustic audio cues into helmet headsets.

---

## 2. Color Palette & Tactical Tokens

| Token Name | Hex Code | Purpose & Semantic Role |
| :--- | :--- | :--- |
| `TacticalBlack` | `#0A0D10` | Screen background; preserves OLED battery life and reduces glare |
| `CockpitSurface`| `#141A22` | Card, container, and sheet surface elevation |
| `CockpitBorder` | `#232E3D` | High-visibility container borders and dividers |
| `NeonGreen` | `#00E676` | Active transmission indicator, optimal link health, PTT ready |
| `CyanRelay` | `#00E5FF` | Multi-hop mesh relay active, cellular failover hedging mode |
| `WarningAmber` | `#FFD600` | Packet loss warning (>15%), weak link margin, battery < 20% |
| `EmergencyRed` | `#FF1744` | Crash impact detection, eCall SOS active, immediate hazard |
| `MutedGray` | `#90A4AE` | Inactive channels, secondary labels, timestamp telemetry |

---

## 3. Cockpit Component Layout

```
+-------------------------------------------------------------------------+
| [GROUP: ALPINE-CONVOY]    [MESH: 8 NODES]    [Q_LINK: 94%]    [BAT: 88%]|
+-------------------------------------------------------------------------+
|                                                                         |
|                       CONVOY RADAR & SPATIAL HUD                        |
|                               (·) Lead                                  |
|                         /            \                                  |
|                  (·) Rider 2       (·) Rider 3                          |
|                                                                         |
+-------------------------------------------------------------------------+
|                                                                         |
|                LIVE AUDIO PLAYSOUND & VU RMS METER                      |
|                [ ||||||||||||||||||||||||||||||........ ] -14 dBFS      |
|                VOX: AUTO-OPEN  |  CODEC: 48 KBPS (WIDEBAND)             |
|                                                                         |
+-------------------------------------------------------------------------+
|                                                                         |
|                         TACTICAL PTT CONTROL PILL                       |
|               +-------------------------------------------+             |
|               |          HOLD TO TALK (PUSH-TO-TALK)      |             |
|               |       [ 64dp Target Height / Glow ]       |             |
|               +-------------------------------------------+             |
|                                                                         |
+-------------------------------------------------------------------------+
| [CH 1: MAIN SQUAD]    [TRAILS SHEET]    [DIAGNOSTICS]    [eCALL SOS]    |
+-------------------------------------------------------------------------+
```
