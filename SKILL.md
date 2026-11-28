---
name: Intercom-Alpha
description: "Core development, architecture, and UI/UX engineering skill for the Intercom-Alpha off-grid BLE mesh intercom system. Enforces 100% Native SwiftUI on iOS, KMP shared logic, tactical anti-slop UI design, Ralph atomic story loops, and automated verification gates."
---

# Intercom-Alpha Engineering & Design Skill

Authoritative engineering and tactical UI/UX design instructions for **Intercom-Alpha** (Off-Grid BLE Mesh Multiplatform Intercom).

## When to Use

Activate this skill whenever:
* Implementing, modifying, or testing code in `app/iosApp`, `app/sharedLogic`, `app/sharedUI`, `app/androidApp`, `:core`, or `:server`.
* Designing, reviewing, or styling UI/UX screens, components, or user flows.
* Executing Ralph atomic story cycles, planning GSD phases, or running verification gates.

---

## 🏛️ Non-Negotiable Architectural Laws

1. **Rule 1: No Shared UI on iOS (100% Native SwiftUI)**
   - Compose Multiplatform must NEVER be targeted to iOS.
   - The iOS client in `app/iosApp` must remain 100% native SwiftUI.
   - All iOS interaction with shared logic routes through `IntercomBridge.kt` (`iosMain`) with reactive, memory-safe `CancellationHandle` callbacks tied to view lifecycle.
2. **Rule 2: Shared Business & Protocol Logic in KMP**
   - Networking, audio pipeline contracts, packet handling, deduplication, group management, and encryption MUST live in `app/sharedLogic/src/commonMain`.
   - Hardware-specific APIs (`AudioRecord`/`AVAudioEngine`, CoreBluetooth/Android BLE, SCO routing) live strictly in platform source sets (`androidMain`, `iosMain`).
3. **Rule 3: Pure Multiplatform Cryptography**
   - Cryptographic primitives in `commonMain` must have zero Java (`java.security.*`) or C/JNI dependencies.
   - Implementations (e.g. RFC 8439 ChaCha20-Poly1305 in `PacketCipher.kt`) must be 100% pure Kotlin.
4. **Rule 4: Retain `:core` and `:server` (ADR-009)**
   - Do not delete `:core` or `:server`. Ktor WebSocket signaling is required for cloud relay fallback and SFU cross-mesh bridging.
5. **Rule 5: Non-Calendar Milestone-Driven Sprints**
   - A sprint or story is closed ONLY when 100% of Definition of Done (DoD) criteria pass.
6. **Rule 6: Mandatory Automated Verification Gates**
   - Multiplatform tests must pass cleanly: `./gradlew test`.
   - Platform compilation must succeed: `./gradlew :app:androidApp:assembleDebug :app:sharedLogic:compileKotlinIosSimulatorArm64`.
7. **Rule 7: Mandatory Graphify Knowledge Sync**
   - After modifying code files, always run: `graphify update .`.
8. **Rule 8: OpenCodeReview Pre-Commit Gate**
   - Before committing any story, run OpenCodeReview delegation mode (`ocr delegate preview --format json`).
   - Fix all `critical`, `high`, and `medium` findings before creating the git commit.
9. **Rule 9: Multi-Tier Branching Strategy**
   - `main` is protected and reserved for production release tags (`vX.Y.Z`).
   - `develop` is the primary integration highway.
   - Sprints live on dedicated `sprint/sprint-NN-<name>` branches.
   - Stories execute on atomic `feature/<STORY-ID>-<slug>` branches.

---

## 🎨 Tactical UI/UX Design System Laws

1. **Color Palette & Contrast**:
   - **Background**: OLED Pitch Black (`#000000`) for battery longevity during off-grid operations.
   - **Surfaces**: Slate 900 (`#0F172A`), Borders: Slate 800 (`#1E293B`).
   - **Locked Primary Accent**: **Tactical Amber** (`#F59E0B`) — strictly reserved for active interaction (PTT transmitting, active channel, selection).
   - **Status Accents**: Emerald (`#10B981`) for mesh health / relay active; Crimson (`#EF4444`) strictly reserved for Guarded SOS.
2. **Glove & Motorcycle Ergonomics**:
   - Primary PTT Actuator must be at least **84pt / 84dp** with tactile spring animations and continuous haptic feedback.
   - All auxiliary buttons must meet minimum **44pt / 44dp** touch hitboxes.
3. **Typography & Chrome**:
   - Strictly monospaced digits (`.monospacedDigit()` in SwiftUI, `FontFamily.Monospace` in Compose) for numeric telemetry: RF frequencies, dBFS levels, GPS coordinates, ping latencies, and packet drop counts.
   - **Zero Emoji in UI Chrome**: Use 100% vector SF Symbols on iOS, vector Material/Lucide symbols on Android.

---

## 🔄 Ralph Story Execution Protocol

For each story in `tasks/ralph/prd.json`:
1. **Branch**: `git checkout -b feature/<STORY-ID>-<slug> <sprint-branch>`.
2. **Implement**: Surgical, minimal edits conforming to domain models.
3. **Test**: `./gradlew test` and platform builds.
4. **Review**: `ocr delegate rule --rule .ocr/rules.json --format json <files>`.
5. **Remediate**: Resolve all findings.
6. **Commit & Merge**: Clean merge back to sprint branch.
7. **Track & Sync**: Update `tasks/ralph/prd.json`, `tasks/ralph/progress.txt`, and run `graphify update .`.
