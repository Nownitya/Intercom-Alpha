# Multi-Tier Branching Strategy & Git Workflow — Intercom-Alpha

> **Document Version:** 3.0.0  
> **Status:** Active / Non-Negotiable Governance  
> **Default Branch:** `main` (Protected Golden Trunk)  
> **Integration Branch:** `develop` (Nightly CI Trunk)  

---

## 1. Branching Topology

Intercom-Alpha utilizes an **Enterprise 4-Tier GitFlow** tailored for Kotlin Multiplatform, native iOS (SwiftUI), Android (Jetpack Compose), and the autonomous Ralph agent cycle.

```text
TIER 1: PRODUCTION      main  ------------------------------------------------------------- (v3.0.0-alpha2)
(Protected / Golden)                                                            ^
                                                                                | Merge PR
TIER 2: RELEASE PREP    release/v3.0 -------------------------------------------+
(QA / TestFlight / APK)                                                ^
                                                                       | Release Cut
TIER 3: INTEGRATION     develop ---------------------------------------+
(Nightly builds / CI)                                         ^
                                                              | Sprint PR
TIER 3: SPRINT / EPIC   sprint/sprint-34-fec-and-plc ---------+
                                                              ^
                                                              | Merge
TIER 4: FEATURE/STORY   feature/ICA-3401-fec-encoder ---------+
(Atomic Story Branches) feature/ICA-3402-audio-plc -----------+
                        feature/ICA-3403-fec-plc-wiring ------+
                        feature/ICA-3404-fec-simulation ------+

EMERGENCY LANE          hotfix/v3.0.1-ble-patch ---- (Off main -> Back to main & develop)
```

---

## 2. The 4 Architectural Tiers

### Tier 1: Golden Production (`main`)
* **Role:** Single Source of Truth for production-grade, audited code.
* **Direct Commits:** **STRICTLY PROHIBITED**.
* **Merges:** Only via approved Pull Requests from `release/vX.Y` or `hotfix/vX.Y.Z`.
* **Tagging:** Every commit on `main` MUST have an annotated semantic version tag (e.g. `v3.0.0-alpha1`, `v3.0.0-alpha2`).

### Tier 2: Release Preparation (`release/vX.Y`)
* **Role:** Staging release candidates for TestFlight and Android Play Store internal testing.
* **Permitted Changes:** Bug fixes, documentation updates, version bumps. Zero new feature code.

### Tier 3: Integration Trunk (`develop`)
* **Role:** Integration branch for all completed and verified sprint epics.
* **Quality Gate:** Nightly CI builds and full unit test execution (475+ tests passing).

### Tier 4: Sprint Epics & Feature Branches
* **Sprint Epic (`sprint/sprint-XX-<name>`):** Created from `develop`. Aggregates all stories for an active sprint.
* **Feature Story (`feature/ICA-XXXX-<name>`):** Created from the sprint branch. Focuses strictly on a single user story with 100% test coverage.
