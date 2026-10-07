# Multi-Tier Branching Strategy & Git Workflow — Intercom-Alpha

> **Document Version:** 1.0  
> **Status:** Active / Non-Negotiable  
> **Obsidian Reference:** `02_Architecture/Branching-Strategy.md`  
> **Canonical Default:** `main` (Protected Golden Trunk)

---

## 🏛️ 1. Topology Overview

Intercom-Alpha utilizes an **Enterprise Multi-Tier GitFlow** tailored for Kotlin Multiplatform, native iOS (SwiftUI), Android (Jetpack Compose), and the autonomous Ralph agent cycle.

```text
═══════════════════════════════════════════════════════════════════════════════════════════════
TIER 1: PRODUCTION      main  ───────────────────────────────────────────────────●──────▶ (v1.0.0)
(Protected / Golden)                                                            ▲
                                                                                │ Merge PR
TIER 2: RELEASE PREP    release/v1.0 ──────────────────────────────────●────────┴────────
(QA / TestFlight / APK)                                                ▲
                                                                       │ Release Cut
TIER 3: INTEGRATION     develop ──────────────────────────────●────────┴─────────────────▶
(Nightly builds / CI)                                         ▲
                                                              │ Sprint PR
TIER 3: SPRINT / EPIC   sprint/sprint-08-field-testing ───────┼────────●
                                                              │        ▲
                                                              │        │ Merge
TIER 4: FEATURE/STORY   feature/ICA-801-range-benchmark ──────┴─●──────┘
(Atomic Story Branches) feature/ICA-802-wind-noise-tuning ─────────●───┘
                        feature/ICA-803-battery-profiling ────────────●┘
═══════════════════════════════════════════════════════════════════════════════════════════════
EMERGENCY LANE          hotfix/v1.0.1-ble-reconnect ──── (Off main ➔ Back to main & develop)
═══════════════════════════════════════════════════════════════════════════════════════════════
```

---

## 🌳 2. The 4 Architectural Tiers

### Tier 1: Golden Production (`main`)
* **Role:** Single Source of Truth for production-grade, audited code.
* **Direct Commits:** **STRICTLY PROHIBITED**.
* **Merges:** Only via approved Pull Requests from `release/vX.Y` or `hotfix/vX.Y.Z`.
* **Build Outputs:** Signed Production Android AAB and iOS App Store / TestFlight external production builds.
* **Tagging:** Every commit on `main` MUST have an annotated semantic version tag (e.g. `v0.7.0`, `v1.0.0`).

### Tier 2: Release Candidates (`release/vX.Y.Z`)
* **Role:** Stabilization, version bumps, release candidate builds (`-rc1`), and store metadata preparation.
* **Branched From:** `develop` at milestone freeze.
* **Merge Policy:** Merged into `main` (for release) **AND** back into `develop` (to retain release-hardening fixes).
* **Decommissioning:** Deleted after successful production tag.

### Tier 3: Integration & Sprint Staging (`develop` & `sprint/*`)
1. **`develop` (Trunk Integration):**
   * Long-lived integration branch.
   * All finished sprints converge here.
   * Runs nightly CI, full multiplatform test suites, and internal debug APK builds.
2. **`sprint/sprint-<NN>-<name>` (Sprint Staging):**
   * *Examples:* `sprint/sprint-08-field-testing`, `sprint/sprint-09-ui-polish`
   * Branched from `develop` at the start of a sprint.
   * Collects all individual story features of that sprint.
   * Keeps in-flight code isolated from `develop` until 100% of the sprint Definition of Done (DoD) is satisfied.

### Tier 4: Atomic Feature & Story Branches (`feature/*`)
* **Role:** Working branches where Ralph and engineers implement single user stories (`ICA-xxx`).
* **Naming Standard:** `feature/<STORY-ID>-<short-description>`
  - *Example:* `feature/ICA-801-range-benchmark`
  - *Example:* `feature/ICA-802-wind-noise-tuning`
* **Lifecycle:**
  1. Branch off `sprint/sprint-NN-<name>`.
  2. Implement code and unit/integration tests.
  3. Run verification gates: `./gradlew test` + platform builds.
  4. Run OpenCodeReview pre-commit checks (`ocr delegate rule`).
  5. Commit with standard Ralph message: `feat: [ICA-xxx] - Title`.
  6. Merge back into `sprint/sprint-NN-<name>`.
  7. Delete local and remote feature branch.

---

## 🚨 3. Emergency Lane (`hotfix/*`)

For critical production defects (e.g., Bluetooth permission crash on iOS 18 or Android 15 audio focus failure):
1. **Branch directly from `main`:**
   ```bash
   git checkout -b hotfix/v1.0.1-ble-crash main
   ```
2. **Apply fix, run verification & OpenCodeReview gates.**
3. **Merge into `main`:**
   ```bash
   git checkout main
   git merge --no-ff hotfix/v1.0.1-ble-crash
   git tag -a v1.0.1 -m "Hotfix: v1.0.1 - Fix BLE connection crash"
   git push origin main --tags
   ```
4. **Merge back into `develop`:**
   ```bash
   git checkout develop
   git merge --no-ff hotfix/v1.0.1-ble-crash
   git push origin develop
   ```
5. **Delete hotfix branch:**
   ```bash
   git branch -d hotfix/v1.0.1-ble-crash
   git push origin --delete hotfix/v1.0.1-ble-crash
   ```

---

## 🚦 4. Gate Enforcement Matrix

| From Branch | Target Branch | Verification Gates Required |
|---|---|---|
| `feature/ICA-xxx` | `sprint/sprint-XX` | `./gradlew test` + OpenCodeReview (0 critical/high/med findings) |
| `sprint/sprint-XX` | `develop` | Complete Sprint DoD + `./gradlew assembleDebug` + iOS compile |
| `develop` | `release/vX.Y` | Full test suite clean + Milestone Outcome Report signed |
| `release/vX.Y` | `main` | Physical device field sign-off + Tag created + Store package generated |

---

## 🏷️ 5. Milestone Tag History

| Tag | Commit | Milestone Description |
|---|---|---|
| `v0.5.0` | `41f4d92` | Phase 3: Core BLE Mesh Engine & Multi-Hop Relay |
| `v0.6.0` | `8c4e70e` | Phase 4: Dual-Radio Cloud Relay & Background Service |
| `v0.7.0` | `1cdb52e` | Phase 5: Pure KMP Audio Codec, Jitter Buffer & PLC |
| `v1.0.0` | `7be2904` | Sprint 08: Production Release v1.0.0 — Off-Grid BLE Mesh Multiplatform Intercom |
| `v2.0.0` | `9785675` | Sprint 13: Production Release v2.0.0 — Android Platform Hardware, R8 Minification & Profiling |
| `v2.1.0` | `8c3be9d` | Sprint 14: Production Release v2.1.0 — Advanced Convoy Mesh, Forward-Secret Key Ratchet & Live Activity |
| `v2.2.0` | `a906cf2` | Sprint 15: Production Release v2.2.0 — Zero-Scroll Tactical Cockpit HUD & Telemetry Integration |
| `v2.4.0` | `e2a7b3c` | Sprint 17: Production Release v2.4.0 — Basecamp Stationary Repeater UI & Dedicated Low-Power Cockpit Mode |
| `v2.5.0` | `5c84d72` | Sprint 18: Production Release v2.5.0 — Offline Vector Terrain & Trail Map Overlay |
| `v2.6.0` | `6c121e4` | Sprint 19: Production Release v2.6.0 — Over-The-Air BLE Mesh GPX/GeoJSON Trail Sharing & Storage Manager |
| `v2.7.0` | *(Current)* | Sprint 20: Production Release v2.7.0 — Tactical Convoy Geofencing, Autonomous Power Governor & Multi-Node Stress Benchmark Suite |
