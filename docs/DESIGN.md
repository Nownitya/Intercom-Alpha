# Design System & UI/UX Specification — Intercom-Alpha

> **Document Version:** 3.0.0-beta1  
> **Status:** Active Design Contract  
> **Theme:** Zero-Scroll Tactical Cockpit HUD & Alpine Elevation Display  
> **Target Frameworks:** Jetpack Compose Multiplatform (Android/Desktop) & 100% Native SwiftUI (iOS)  

---

## 1. Tactical Design Philosophy & Core Principles

The Intercom-Alpha interface is engineered specifically for **harsh outdoor, high-speed, and gloved-hand environments**:
1. **Zero-Scroll Viewport:** All mission-critical controls (PTT pill, VU meter, Channel Dial, Proximity Radar, Elevation Profile, SOS) must fit completely on screen without scrolling.
2. **Glove-First Geometry:** Minimum touch target size of **64dp x 64dp** for primary actions, with generous padding to prevent accidental mis-hits while riding over rough terrain.
3. **High-Contrast Tactical Palette:** Pure OLED pitch black background with neon tactical accents providing a minimum **7:1 contrast ratio**, ensuring readability under direct blazing sunlight.
4. **Haptic & Acoustic Multimodal Feedback:** Every critical state change (VOX open, PTT transmit start, channel switch, packet loss alert, slope warning >8% or >12%, SOS beacon) triggers unique haptic vibration signatures and acoustic audio cues into helmet headsets.
5. **Native SwiftUI Elevation Canvas on iOS:** Native SwiftUI `Canvas` and `Path` rendering for 60fps buttery smooth profile scrubbing, strictly complying with **Rule 1: No Shared Compose UI on iOS**.

---

## 2. Color Palette & Tactical Tokens

| Token Name | Hex Code | Purpose & Semantic Role |
| :--- | :--- | :--- |
| `TacticalBlack` | `#0A0D10` | Screen background; preserves OLED battery life and reduces glare |
| `CockpitSurface`| `#141A22` | Card, container, and sheet surface elevation |
| `CockpitBorder` | `#232E3D` | High-visibility container borders and dividers |
| `NeonGreen` | `#00E676` | Active transmission indicator, optimal link health, standard slope (<8%) |
| `CyanRelay` | `#00E5FF` | Multi-hop mesh relay active, cellular failover, summit marker |
| `WarningAmber` | `#FF9100` | Packet loss warning (>15%), caution slope grade (>8%), battery < 20% |
| `EmergencyRed` | `#FF1744` | Crash impact detection, eCall SOS active, extreme slope grade (>12%) |
| `MutedGray` | `#90A4AE` | Inactive channels, secondary labels, distance axis markers |

---

## 3. Cockpit Component Layout

```
+-------------------------------------------------------------------------+
| [GROUP: ALPINE-CONVOY]    [MESH: 8 NODES]    [Q_LINK: 94%]    [BAT: 88%]|
+-------------------------------------------------------------------------+
| [ RADAR TAB ]              [ ELEVATION TAB (ACTIVE) ]    [ DIAGNOSTICS ]|
+-------------------------------------------------------------------------+
|                                                                         |
|                TACTICAL TRAIL ELEVATION PROFILE HUD                     |
|    2,400m |                   /\  (Pass Summit: 2,410m)                 |
|           |                  /  \                                       |
|    1,800m |         (·)Lead /    \                                      |
|           |       /        /      \   (·)Sweep (Δh: 180m)               |
|    1,200m |______/________/________\__________________________         |
|             0 km         5 km         10 km        15 km                |
|    [ GRADE: +9.4% CAUTION ]   [ 64dp ZOOM IN ]   [ 64dp ZOOM OUT ]      |
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
