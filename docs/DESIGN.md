# UI/UX Design Specification & Aesthetic Architecture — Intercom-Alpha

> **Document Version:** 3.0.0  
> **Status:** Active / Permanent Architectural Directive  
> **Phase Policy:** **Execution Gates Post-Development** — Full UI/UX design overhaul initiates immediately once all core backend, mesh protocols, audio DSP, and infrastructure features conclude development.  
> **Craftsmanship Mandate:** **100% Hand-Crafted Human Design** — Zero AI-generated visual boilerplate, templates, or artificial filler. Deliberate, bespoke tokens, tailored layout hierarchy, and precision engineering.  
> **Target Frameworks:** **Jetpack Compose Material 3: Expressive** (Android) & **iOS Latest Design Language** (Native SwiftUI iOS 18+ HIG).  
> **Design Philosophy:** **Pragmatic Tactical Realism** — Simple, sophisticated, high-contrast, glove-friendly outdoor cockpit interface.

---

## 🏛️ 1. Phasing & Execution Order Directive

1. **Backend & Protocol Finality First**: Core mesh transport, audio DSP engines, cryptography, routing governors, and hardware bridge contracts must reach complete architectural maturity and 100% test pass status before beginning the production UI/UX overhaul.
2. **Dedicated UI/UX Milestone**: Once all foundational development concludes, a dedicated, multi-sprint UI/UX overhaul milestone will commence, focusing exclusively on visual excellence, tactile motion physics, sensory haptics, and cockpit ergonomics.
3. **No AI-Generated Slop**: Every design token, curve radius, drop shadow offset, gradient step, and vector glyph must be deliberately hand-crafted and mathematically intentional. Zero AI-generated stock templates or ungrounded aesthetic noise.

---

## 🎨 2. The 5-Pillar Aesthetic Fusion Matrix (Real-Life Balance)

The interface fuses five distinct design paradigms into a cohesive, balanced, and non-overcomplicated design language tailored for high-speed, off-grid adventure sports (motorcycle touring, downhill MTB, alpine snowmobiling, and search-and-rescue convoys):

```
       ┌────────────────────────────────────────────────────────┐
       │              PRAGMATIC TACTICAL REALISM                │
       └──────────────────────────┬─────────────────────────────┘
                                  │
    ┌──────────────┬──────────────┼──────────────┬──────────────┐
    ▼              ▼              ▼              ▼              ▼
Skeuomorphism  Neobrutalism  Claymorphism   Minimalism    Liquid Glass
(Tactile PTT   (High-Sun     (3D Pill       (Glanceable   (Layered Depth
 & Hardware)    Contrast)     Glove Targets) Hierarchy)    Frosted Blur)
```

### 1. 🎛️ Skeuomorphism: Tactical Hardware Realism
* **Purpose**: Evokes the reassurance of mil-spec tactical transceivers and aviation avionics without retro cheese.
* **Implementation**:
  * **Tactile PTT Switch**: Deep mechanical depression states with recessed bezel chassis, brushed dark titanium rims, and concentric machined knurling.
  * **Analog VU Peak Meter**: Dynamic LED bar segments with realistic phosphor decay curves and peak-hold indicators.
  * **Channel Selector Dial**: Rotary rotary detent notches with haptic feedback tick-marks simulating physical heavy-duty radios.
  * **Hardware Rockers**: 2-state mechanical toggles with realistic ambient light highlights and recessed drop-shadow slots.

### 2. ⚡ Neobrutalism: High-Contrast Sunlight Legibility
* **Purpose**: Guarantees instant glanceability under blinding alpine snow glare, harsh direct midday sun, and through tinted helmet visors.
* **Implementation**:
  * **Stark Contrast Borders**: 2–3 dp solid, razor-sharp perimeter outlines (`rgba(255, 255, 255, 0.18)` idle, `#E14A0E` or `#10B981` active).
  * **Hard Status Chips**: High-emphasis rectangular/pill badges with bold 800-weight tabular typography for RF metrics, battery percentages, and hop counts.
  * **Unapologetic Hierarchy**: Primary emergency and PTT alerts punch through with vivid, uncompromising safety colors (International Orange `#E14A0E`, Neon SOS `#EF4444`).

### 3. 🫧 Claymorphism: Glove-Friendly Ergonomic Touch Targets
* **Purpose**: Creates friendly, pillowy, 3D tactile affordance optimized for thick motorcycle leather and winter ski gloves.
* **Implementation**:
  * **Tactile Pillow Cards**: Soft double inner shadows (`inset -2dp -2dp 6dp rgba(0,0,0,0.5)`, `inset 2dp 2dp 4dp rgba(255,255,255,0.08)`) that create a plush, elevated 3D surface.
  * **Generous Touch Targets**: All primary actuators (PTT, Mute, Frequency Scan, SOS, Waypoint Drop) enforce a minimum bounding target of **$84 \times 84$ dp / pt** (well above standard 48dp mobile guidelines).
  * **Smooth Corner Curvatures**: Continuous curvature squircle corners (24–32 dp) that comfortably guide thumb swipes even when vibrations are extreme.

### 4. 🎯 Minimalism: Zero-Clutter Cockpit Ergonomics
* **Purpose**: Prevents cognitive overload when navigating treacherous terrain at $100\text{ km/h}$.
* **Implementation**:
  * **Sub-Second Glanceability ($< 0.5$s)**: Key vitals (Who is speaking, Convoy RF Link Quality, Lead Distance, Battery) identifiable via peripheral vision alone.
  * **Ruthless Elimination of Noise**: No decorative icons, unnecessary gradients, or non-functional visual filler.
  * **Progressive Disclosure**: Detailed mesh topology graphs, packet collision diagnostics, and routing tables remain tucked into gesture-driven bottom sheets, leaving the primary cockpit clean and focused.

### 5. 💧 Liquid Glass: Frosted Modern Depth & Specular Luminance
* **Purpose**: Provides sophisticated, modern depth layering that floats over real-time tactical vector trail maps and convoy radar without obscuring spatial context.
* **Implementation**:
  * **Ultra-Thin Frosted Materials**: Real-time multi-pass backdrop blur (`.ultraThinMaterial` in SwiftUI, custom RenderEffect / blurred surface in Compose) with subtle noise grain simulating hardened polycarbonate.
  * **Luminous Specular Highlights**: Micro-fine $1\text{ dp}$ top border sheen (`rgba(255, 255, 255, 0.22)`) indicating light reflection on glass cockpit instruments.
  * **Fluid Sheet Transitions**: Smooth spring physics sheets (damping ratio 0.82, stiffness 380) that glide organically without rigid clipping.

---

## 🛠️ 3. Native Platform Technology Stack

### 3.1 Android Cockpit: Jetpack Compose Material 3 Expressive
* **Core Paradigm**: Material 3 Expressive (M3-X) design system in `:app:sharedUI`.
* **Key Mechanisms**:
  * **Expressive Typography**: Bold, punchy variable font weights (Google Sans Flex / Roboto Flex) with tabular lining figures for real-time telemetry meters.
  * **Container Transforms**: Fluid shared-axis transitions between compact Avionics Bar and expanded Convoy Radar.
  * **Expressive Shapes**: Asymmetric and squircle pill shapes responding dynamically to touch states.
  * **Spring Physics**: `spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)` for natural, weighted button releases.
  * **Haptic Actuation**: Custom `HapticFeedback` patterns distinguishing PTT engage, VOX trigger, and geofence perimeter warnings.

### 3.2 iOS Cockpit: 100% Native SwiftUI (iOS 18+ HIG)
* **Core Paradigm**: Pure Native SwiftUI in `:app:iosApp` adhering strictly to **Rule 1** (Zero Compose on iOS).
* **Key Mechanisms**:
  * **Native Materials**: Extensive use of `.ultraThinMaterial`, `.thinMaterial`, and custom vibrant visual effect views.
  * **SF Symbols 6**: Dynamic multi-color rendering, variable value progression (e.g. `wifi`, `antenna.radiowaves.left.and.right`, `battery.100percent`), and discrete symbol animations (`.symbolEffect(.bounce)`).
  * **Sensory Feedback**: `UIImpactFeedbackGenerator(style: .rigid)` on PTT click, `.heavy` on SOS toggle, and `.soft` on rotary dials.
  * **Reactive Bridge Routing**: Exclusively consumes state through `IntercomBridge.kt` with lifecycle-managed `CancellationHandle` closures.

---

## 📐 4. The Real-Life Balance: How They Work Together

| Component | Skeuomorphism | Neobrutalism | Claymorphism | Minimalism | Liquid Glass |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Main PTT Button** | Mechanical knurled rim & recessed well | Solid 3px high-contrast orange halo | Pillowy 3D depressed volume | Single centered icon & label | Subtle specular glow highlight |
| **Convoy Radar HUD** | Analog vector compass grid lines | Bold black-and-white distance chips | Soft floating blip badges | Only active & out-of-bounds nodes shown | Translucent glass backdrop over map |
| **Avionics Status Bar** | Segmented LED battery / RF bars | Stark black badge container with 2px border | Rounded pill container for easy tapping | 3 essential numbers ($Q_{link}$, Mode, PTT) | Frosted blurred underlay |
| **Crash Alert Modal** | Heavy warning hazard stripes | Saturated high-visibility crimson border | Large 96dp "I AM OK" abort pillow | Single countdown timer & cancel button | Dark liquid glass backdrop dimming cockpit |
| **Route Sharing Sheet** | Physical SD card / flash drive badge | Bold difficulty color chips (Easy..Expert) | Soft pill progress track | Clean transfer percentage and button | Full frosted blur sheet overlay |

---

## 🔒 5. Quality & Governance Requirements

1. **Accessibility & Contrast**: All text and status indicators must achieve a minimum WCAG AAA contrast ratio ($\ge 7:1$) against dark cockpit surfaces for outdoor visibility.
2. **Night Mode Safety**: Pure OLED black (`#0A0D10`) as base substrate to avoid pupil dilation shock during night rides.
3. **No Decorative Friction**: Every visual effect must serve tactile, communicative, or ergonomic utility. If an effect distracts from riding safety, it is eliminated.
4. **Execution Gate**: This specification is the authoritative binding contract for the upcoming UI/UX milestone once core development concludes.
