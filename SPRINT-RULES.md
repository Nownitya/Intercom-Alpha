# Sprint Management Framework & Rules for Intercom-Alpha

> **Project Model:** Capability & Milestone-Driven Engineering  
> **Linked Systems:** Obsidian Vault, MemPalace v3.10.0, Graphify Knowledge Graph  
> **Obsidian Mirror:** `05-Meta/Sprint-Management-Rules.md`  

---

## 🎯 1. Core Philosophy: Why We Do NOT Use Calendar Sprints

In typical corporate SaaS projects, sprints are time-boxed to calendar weeks (e.g. *Monday 9 AM to Friday 5 PM*). 

In **Intercom-Alpha**—a low-level systems engineering project involving multiplatform Bluetooth LE GATT state machines, audio DSP buffering, and native Android/iOS hardware APIs—calendar-based slicing causes fragmented, half-tested code.

Instead, we use a **Capability & Milestone-Driven Sprint Model**:
* Sprints represent **coherent, testable units of system capability** (e.g. *Sprint 03: iOS Native Audio, CoreBluetooth & Cross-Platform GATT*).
* A sprint closes **only when its Definition of Done (DoD) is 100% verified** by automated unit tests and multiplatform compilation.
* Sprints do not force artificial deadlines; they enforce strict architectural boundaries.

---

## 🏗️ 2. Architectural Hierarchy

```
Phase (Macro Horizon)
  └── Epic (Subsystem Domain)
        └── Sprint (Atomic Execution Unit)
              └── Task (Concrete Code & Test Files)
```

1. **Phase (Macro Horizon)**: High-level architectural milestones (e.g., Phase 1: Core Signaling, Phase 2: Android Platform, Phase 3: iOS Platform, Phase 4: Integration & Hardware Polish, Phase 5: Production Release).
2. **Epic (Subsystem Domain)**: Cross-cutting architectural areas spanning multiple platforms (`EPIC-AUDIO`, `EPIC-MESH`, `EPIC-HEADSET`, `EPIC-ONBOARDING`, `EPIC-SECURITY`).
3. **Sprint (Actionable Unit)**: A dedicated, isolated unit with a strict scope (typically 3–8 cohesive tasks).
4. **Task (Atomic Work)**: Specific file modifications, tests, and configurations.

---

## 📁 3. Multiple Sprint File Structure & Locations

To avoid merge conflicts, context bloat, and session amnesia, each sprint lives in its own dedicated document:

```
Obsidian Vault: D:\ObsidianVault\Vault1\ObsidianVault\Android\Projects\Intercom-Alpha/
├── 01-Projects/
│   ├── Active/
│   │   ├── Kanban-Board.md                <-- Interactive Obsidian Kanban
│   │   └── Sprints/
│   │       ├── Sprint-03-iOS-Mesh-And-Audio.md   (Recently Completed)
│   │       └── Sprint-04-Swift-KMP-Bridge.md     (Active / Next)
│   └── Archive/
│       └── Sprints/                        <-- Completed Historical Sprints
│           ├── Sprint-01-Core-Networking-And-Signaling.md
│           └── Sprint-02-Android-Platform-Engine.md
│
Workspace Root: D:\Codes\Antigravity\Project4\Intercom-Alpha/
├── SPRINT.md                               <-- Pointer / Mirror of ACTIVE Sprint
├── SPRINT-RULES.md                         <-- This Governance Specification
├── JOURNAL.md                              <-- Daily Engineering Changelog
└── NOTES.md                                <-- Technical Cheat-Sheet & UUIDs
```

---

## 🟢 4. Sprint Creation Criteria (When to Start a New Sprint)

A new sprint is created **only** when all three conditions are satisfied:
1. **Preceding Sprint Closed**: All Definition of Done (DoD) criteria of the previous sprint are verified and checked off.
2. **Prerequisites Fulfilled**: Upstream dependencies exist (e.g. cannot start iOS GATT transport without Core GATT UUID contracts in place).
3. **Atomic Scope**: The sprint has a clear title, 3–6 prioritized tasks, specific target files, and an unambiguous verification plan.

### Sprint Document Template
Every sprint file must contain:
* **Header**: ID, Title, Status, Phase, Epics, Vault Area links.
* **Sprint Goal**: 1–2 sentence statement of the capability being delivered.
* **Task Matrix**: Table with `Task ID`, `Component / Item`, `Owner`, `Target Files`, and `Status`.
* **Definition of Done (DoD)**: Explicit list of verification commands and acceptance criteria.
* **Next Sprint Preview**: Immediate handoff target.

---

## 🔴 5. Definition of Done (DoD — When to Close a Sprint)

A sprint **CANNOT** be closed until every item below passes:
1. **Compilation Check**:
   * Android: `./gradlew :app:androidApp:assembleDebug` succeeds with valid APK generated.
   * iOS Native: `./gradlew :app:sharedLogic:compileIosMainKotlinMetadata`, `compileKotlinIosSimulatorArm64`, and `compileKotlinIosArm64` succeed with 0 errors.
2. **Automated Unit Tests**:
   * `./gradlew test` passes 100% across `:core`, `:server`, `:app:sharedLogic`, and `:app:sharedUI`.
3. **Cross-Platform Symmetry**:
   * Shared serialization, UUID constants, and network packet schemas verified across platforms.
4. **Knowledge & Memory Sync**:
   * Knowledge graph updated: `graphify update .`
   * MemPalace memory updated: `python -m mempalace mine . --wing intercom_alpha` and `python -m mempalace mine <obsidian-vault> --wing intercom_alpha`.
5. **Handoff & Archive**:
   * Daily note entry added to `10-Daily-Notes/` in Obsidian.
   * Completed sprint moved to `01-Projects/Archive/Sprints/` (or marked Completed in Kanban).
   * Active pointer in `SPRINT.md` switched to the next sprint.

---

## 📊 6. Obsidian Kanban Plugin Integration

The Obsidian Kanban plugin interprets standard Markdown list syntax with `##` column headers:
* `## 📋 Product Backlog`: Long-term features and deferred epics.
* `## 🎯 Active Sprint [XX]`: Current sprint task cards.
* `## 🚧 In Progress`: Tasks actively being edited or designed.
* `## 🧪 Verification & QA`: Gradle tasks, simulator test runs, APK verification.
* `## ✅ Done`: Completed tasks with links to target files.
* `## 📦 Archived Sprints`: Links to past completed sprint documents.

---

## 🧠 7. MemPalace & Graphify Harmony

* **MemPalace** (`intercom_alpha` wing): Stores verbatim sprint goals, task matrices, daily logs, and decisions in persistent memory drawers. Survives session resets and context window compaction.
* **Graphify**: Indexes the code symbols and Markdown notes into an AST-based knowledge graph. `graphify export obsidian` generates visual canvases (`graph.canvas`) right inside Obsidian to navigate code hubs and sprint files simultaneously.
