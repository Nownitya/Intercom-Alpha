# Antigravity & Ralph Agent Engineering Contract — Intercom-Alpha

> **Project:** Intercom-Alpha (Off-Grid BLE Mesh Multiplatform Intercom)  
> **Workflow:** Hybrid GSD Core + Ralph + OpenCodeReview Architecture  
> **Authority:** Non-Negotiable Instructions for all Autonomous & Subagent Iterations  

---

## 🏛️ 1. System Topology & Role Separation

```
Obsidian + MemPalace + Graphify
               │
               ▼
           GSD Core          ───► THINK / PLAN / ARCHITECT / VERIFY
               │
               ▼
             Ralph           ───► IMPLEMENT / TEST / COMMIT / TRACK (1 story per turn)
               │
               ▼
        OpenCodeReview       ───► REVIEW (Pre-commit diff & correctness gate)
               │
               ▼
          Ralph Fixes        ───► Re-test ➔ Re-review ➔ Clean commit
```

* **GSD Core**: Owns the roadmap, requirements (`docs/PRD.md`), architecture (`docs/ARCHITECTURE.md`), and phase verification. Does NOT implement individual stories.
* **Ralph**: Owns atomic implementation, testing, diff inspection, finding resolution, and git commits. Runs one story at a time in a fresh context.
* **OpenCodeReview**: Owns pre-commit review. Strictly read-only; never mutates code directly.
* **Prohibition**: GSD and Ralph must NEVER independently compete over implementation of the same task.

---

## ⚖️ 2. Non-Negotiable Architectural Laws

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
9. **Rule 9: Multi-Tier Branching Strategy (docs/BRANCHING.md)**
   - `main` is protected and strictly reserved for production release tags (`vX.Y.Z`).
   - `develop` is the primary integration highway.
   - Sprints live on dedicated `sprint/sprint-NN-<name>` branches.
   - Individual Ralph stories execute on atomic `feature/<STORY-ID>-<slug>` branches off the active sprint branch, and merge back to the sprint branch upon completion.

---

## 🔄 3. Ralph Atomic Story Execution Cycle

For each story in `tasks/ralph/prd.json`:

1. **Select & Branch**:
   - Pick the highest-priority story where `passes: false`.
   - Branch off the active sprint branch: `git checkout -b feature/<STORY-ID>-<slug> <sprint-branch>`.
2. **Orient**: Read the active GSD Phase Plan, `AGENTS.md`, `docs/RULES.md`, and `docs/MEMORY.md`.
3. **Implement**: Implement strictly the files required for that single story.
4. **Test & Build**: Run `./gradlew test` and platform builds. Do NOT call review on broken code.
5. **Review Gate (OpenCodeReview)**:
   - Run `ocr delegate preview --format json` on working copy changes.
   - Run `ocr delegate rule --rule .ocr/rules.json --format json <files>`.
   - Inspect diff against project rules and emit `tasks/ralph/review-findings.json`.
6. **Remediate**: If actionable findings exist, fix them, re-test, and re-review until clean.
7. **Commit & Merge**:
   - `git add <files>`
   - `git commit -m "feat: [Story ID] - [Story Title]"`
   - `git checkout <sprint-branch>`
   - `git merge --no-ff feature/<STORY-ID>-<slug>`
   - `git branch -d feature/<STORY-ID>-<slug>`
8. **Track**:
   - Set `passes: true` in `tasks/ralph/prd.json`.
   - Append execution summary, patterns, and gotchas to `tasks/ralph/progress.txt` and `docs/MEMORY.md`.

---

## 🧩 4. Critical Technical Gotchas

* **Navigation 3 Polymorphic Backstack**: In `AppNavGraph.kt`, always supply `SavedStateConfiguration` with `SerializersModule` registering routes under `polymorphic(NavKey::class)`.
* **Json Class Discriminator Collision**: Always use `Json { classDiscriminator = "#type" }` to prevent collision with `MeshPacket.Control.type`.
* **Packet Flooding Loops**: Always check `PacketDeduplicator.shouldProcess(packet)` before forwarding incoming mesh packets.
* **Poly1305 on KMP**: Represent 130-bit integers as five 26-bit limbs over 64-bit Longs to avoid Java `BigInteger` allocations.
