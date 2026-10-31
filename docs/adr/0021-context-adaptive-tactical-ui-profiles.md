# ADR-0021: Context-Adaptive Tactical UI Profiles & User-Customizable Component Architecture

* **Status:** Accepted  
* **Date:** 2026-10-31  
* **Deciders:** Intercom-Alpha Engineering & Design Team  
* **Consulted:** GSD Core, Ralph, OpenCodeReview, `ui-ux-pro-max`, `impeccable`, `ios-design`  

---

## Context

Intercom-Alpha is used across vastly differing outdoor and vehicular environments:
1. High-speed, gloved motorcycle riding (extreme vibration, high wind noise, limited hand availability).
2. Mountain hiking and alpine trekking (low speed, walking pace, topo elevation and trail navigation focus).
3. Bicycling / Gravel cycling (handlebar phone mounts, need for minimal distraction).
4. Car / 4x4 convoy driving (in-cabin dashboard view, convoy spacing focus).
5. Casual walkie-talkie / camp use (simple, zero-gimmick communication).

Previously, complex widgets (VU meters, elevation profiles, radar compass, multi-hop route graphs) were placed directly in the main viewports, leading to potential visual clutter and cognitive overload for users who merely wanted a simple, bulletproof intercom.

## Decision

1. **Strict Core Invariants:**
   Every screen and mode guarantees immediate, zero-scroll access to **Core Controls**:
   * Current Channel/Group display & quick selector.
   * Prominent, glove-operable **PTT Actuator** (with hold-to-talk spring physics and optional latched mode).
   * Live speaker indicator ("Who is talking").
   * Vital telemetry (Mesh Node count, link health, battery level).
   * **Guarded SOS Actuator** (swipe-to-confirm with false-positive prevention).

2. **Activity Profiles (`ActivityProfile`):**
   The app provides one-tap operational presets that adjust HUD layout, widget visibility, and sensitivity:
   * **Motorbike (Default Tactical)**: Large 84pt PTT, high-contrast Tactical Amber, slope grade warnings, guarded SOS prominent.
   * **Bicycle / Cycling**: Audio-first, buddy proximity distance, clean minimal telemetry.
   * **Mountain Hiking / Trekking**: Elevation profile, summit delta, barometric pressure, compass bearing.
   * **Overland / Car Driving**: Large channel buttons, convoy leader/sweep spacing, minimal eye-off-road demand.
   * **Minimal**: Pure walkie-talkie: channel, volume, speaker name, large PTT button.

3. **User-Controlled Widget Customization via Settings:**
   * In Settings, users have full toggle control over auxiliary widgets (`showVuMeter`, `showRadar`, `showElevation`, `showConvoyMap`, `showRfDiagnostics`).
   * No unnecessary gimmicks: if a user disables a widget, it completely unmounts, saving CPU/battery cycles and eliminating visual noise.

4. **Multi-Theme Support:**
   The design system provides 5 static, battle-tested color token presets:
   * `Tactical Amber` (OLED Pitch Black + Tactical Amber `#F59E0B`)
   * `Alpine High-Vis` (High-key snow glare contrast)
   * `Forest Emerald` (Tactical green night-vision mode)
   * `Desert Ochre` (High-contrast earth tone)
   * `Monochrome` (Pure grayscale contrast)

5. **Battery-Saver Utilitarian Rendering (`LowPowerUiMode`):**
   * Eliminates continuous 60fps canvas animations, spinning radar sweeps, and live audio waveforms that drain device battery in the field.
   * Event-driven updates only: UI re-renders strictly on discrete state updates (e.g. 5-second GPS delta packet, PTT press, speaker change).
   * Text-first fallback: Compass and elevation can be displayed as clean numeric cards (`[Lead: 140m Ahead ↑] [Sweep: 320m Behind ↓]`, `[Alt: 1,840m | Grade: +4.2%]`) instead of heavy GPU canvas rendering.
   * VU meter uses low-frequency discrete 4-segment LED ticks instead of continuous wave animations.

6. **Platform Concurrency:**
   * iOS (`app/iosApp`): 100% Native SwiftUI views observing `ActivityProfile` and custom widget configuration stored in `UserDefaults` / reactive KMP bridge.
   * Android (`app/sharedUI`): Jetpack Compose observing `StateFlow<ActivityProfileUiState>`.

## Consequences

* **Positive:** Eliminates visual clutter, prevents cognitive overload, accommodates casual users and extreme adventure riders equally.
* **Positive:** Preserves battery and CPU by conditionally unmounting unused auxiliary sensor views.
* **Compliance:** Strictly conforms to Rule 1 (100% native SwiftUI on iOS) and ADR-017 (Zero-scroll tactical cockpit).

