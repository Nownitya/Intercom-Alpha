# Design System & UI/UX Specification — Intercom-Alpha

> **Document Version:** 3.1.0  
> **Status:** Active Design Contract (ADR-017 & ADR-021)  
> **Theme:** Context-Adaptive Tactical Cockpit & Utilitarian Anti-Slop Design  
> **Target Frameworks:** 100% Native SwiftUI (iOS) & Jetpack Compose Multiplatform (Android/Desktop)  

---

## 1. Design Philosophy: Simplicity, Utility & Anti-Slop

The Intercom-Alpha interface is designed around one core axiom: **Mission-critical clarity with zero cognitive distraction and zero unnecessary gimmicks**.

1. **Unshakeable Core Invariants:**
   Every profile and mode guarantees permanent, zero-scroll access to the 5 essentials:
   * **Active Channel / Group Dial**: Clear frequency/channel ID and tactile switch.
   * **Live Speaker Status**: Instant visual feedback of who is transmitting.
   * **84pt PTT Actuator**: Ergonomic, glove-friendly thumb shelf with Hold-to-Talk spring physics.
   * **Vital Status**: Mesh connectivity (`24 Nodes`, `Q_link %`) and battery level.
   * **Guarded SOS**: 3-second swipe-to-confirm emergency slider.

2. **Context-Adaptive Activity Profiles (`ActivityProfile`):**
   The app provides 5 distinct profiles that automatically configure the HUD, visible tabs, and sensor alerts:
   * **Motorbike (Default Tactical)**: Large 84pt PTT, guarded SOS prominent, slope % warning banners, Tactical Amber theme. 3 Tabs: `[Cockpit, Radar, Convoy]`.
   * **Car Driving / 4x4 Overland**: High-legibility dashboard layout with giant channel buttons, speaker name, and convoy inter-vehicle distance. No complex radar circles or topo graphs.
   * **Bicycle / Cycling**: Audio-first interface. Compact PTT, buddy proximity tag, and hands-free VOX toggle.
   * **Mountain Trails (Walking / Alpine Hiking)**: Topo trail cross-section, compass bearing, barometric altitude, and low-power battery saver mode.
   * **Minimal (Casual / Everyday)**: Clean single-screen interface. Only the channel dial, speaker indicator, and large PTT button. Zero extra tabs.

3. **User-Controlled Modular Widgets (Settings):**
   Users have granular toggle control over all auxiliary widgets:
   * `showVuMeter` (Dual VU audio meters)
   * `showRadar` (360° proximity radar compass)
   * `showElevation` (Alpine trail elevation profile & slope %)
   * `showConvoyMap` (Convoy fleet route map)
   * `showRfDiagnostics` (Advanced PHY, SNR, PER telemetry)
   * *When turned OFF, widgets completely unmount to preserve CPU and battery cycles.*

4. **Typography & Strict Chrome Rules:**
   * **Tabular Figures**: All numeric telemetry (frequencies, dBFS, dBm, % packet loss, GPS coordinates, elevation, speed) strictly use `.monospacedDigit()` in SwiftUI and `FontFamily.Monospace` in Compose.
   * **Zero Emoji in Chrome**: 100% vector SF Symbols on iOS, vector Material/Lucide symbols on Android.

5. **Battery-Saver Utilitarian Rendering (`LowPowerUiMode`):**
   * **Zero Continuous GPU Loops**: Eliminates 60fps spinning radar graphics, continuous contour redraws, and live waveform visualizers that drain battery outdoors.
   * **Event-Driven Repaints**: Screen only repaints when discrete state changes occur (PTT press, speaker change, 5s GPS delta beacon).
   * **Text-First Visual Representation**: Radar compass and elevation can be displayed as clean, static numeric cards (`[Lead: 140m Ahead ↑] [Sweep: 320m Behind ↓]`, `[Alt: 1,840m | Grade: +4.2%]`) with minimal vector overhead.
   * **Discrete 4-Segment LED Indicator**: 4-segment discrete LED level indicator updated at low frequency (100ms) instead of continuous high-rate visualizers.

---

## 2. Multi-Theme Color Palettes (Static & High-Contrast)

All themes maintain a minimum **7:1 contrast ratio** under direct sunlight.

| Theme Name | Background | Surface Card | Border | Primary Active Accent | Semantic Status Accents |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`Tactical Amber`** *(Default)* | `#000000` (OLED) | `#0F172A` (Slate 900) | `#1E293B` (Slate 800) | `#F59E0B` (Tactical Amber) | Emerald `#10B981` (Link OK), Crimson `#EF4444` (SOS) |
| **`Alpine High-Vis`** | `#0B132B` (Navy) | `#1C2541` (Deep Slate) | `#3A506B` (Steel) | `#00E5FF` (Cyber Cyan) | Lime `#A3E635` (Link OK), Crimson `#EF4444` (SOS) |
| **`Forest Emerald`** | `#0A130C` (Dark Moss) | `#112214` (Woodland) | `#1F3824` (Pine) | `#10B981` (Trail Green) | Amber `#F59E0B` (Warning), Crimson `#EF4444` (SOS) |
| **`Desert Ochre`** | `#120E0B` (Dark Earth) | `#1E1713` (Umber) | `#332720` (Sandstone) | `#FBBF24` (Mojave Gold) | Teal `#14B8A6` (Link OK), Crimson `#EF4444` (SOS) |
| **`Monochrome Minimal`** | `#000000` (Pitch) | `#121212` (Neutral 900) | `#262626` (Neutral 800) | `#FFFFFF` (Pure White) | Slate `#94A3B8` (Muted), Crimson `#EF4444` (SOS) |

---

## 3. Screen Topology & Platform Implementations

### iOS (100% Native SwiftUI — Rule 1)
* **Root Navigation**: Native SwiftUI `TabView` dynamically filtering tabs based on `activityProfile`.
* **State Bridge**: `IntercomBridge.kt` emitting `@Published` properties with `CancellationHandle` lifecycles.
* **Haptics**: `TacticalHaptics.swift` (`UIImpactFeedbackGenerator`, `UINotificationFeedbackGenerator`).

### Android & Desktop (Compose Multiplatform — Rule 2)
* **Root Navigation**: Navigation 3 backstack (`AppNavGraph.kt`) observing `StateFlow<HomeUiState>`.
* **Theme System**: `TacticalTheme.kt` providing `MaterialTheme` color scheme and typography bindings.
