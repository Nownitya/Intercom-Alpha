# Engineering Rules & Project Governance — Intercom-Alpha

> **Document Version:** 2.12  
> **Status:** Active / Non-Negotiable Governance  
> **Obsidian Reference:** `07_Meta/Sprint-Rules.md` & `SPRINT-RULES.md`

---

## 🏛️ 1. The 9 Non-Negotiable Architectural Laws

### 📱 Rule 1: No Shared UI on iOS (100% Native SwiftUI)
- **Law**: Under no circumstances should Compose Multiplatform be targeted to iOS.
- **Implementation**: The iOS client in `app/iosApp` must remain 100% native SwiftUI.
- **Bridge Contract**: All interaction with shared logic must route through `IntercomBridge.kt` (`app/sharedLogic/src/iosMain`), providing reactive, memory-safe callback observers (`CancellationHandle`) bound to SwiftUI view lifecycles.

### ⚙️ Rule 2: Shared Business & Protocol Logic in KMP
- **Law**: All networking, packet handling, deduplication, group management, audio DSP algorithms, link quality arbitration, and encryption MUST live in `app/sharedLogic/src/commonMain`.
- **Platform Source Sets**: Hardware-specific APIs (`AudioRecord`/`AVAudioEngine`, CoreBluetooth/Android BLE, Bluetooth SCO routing) live strictly in platform source sets (`androidMain`, `iosMain`).

### 🔐 Rule 3: Pure Multiplatform Cryptography
- **Law**: Cryptographic primitives in `commonMain` must have zero Java (`java.security.*`) or C/JNI dependencies.
- **Implementation**: All algorithms (e.g. RFC 8439 ChaCha20-Poly1305 in `PacketCipher.kt`) must be written in 100% pure Kotlin so they execute identically across Android, iOS Native, JVM, and Wasm targets.

### 🌐 Rule 4: Retain `:core` and `:server` (ADR-009)
- **Law**: Do not delete `:core` or `:server`.
- **Rationale**: Ktor WebSocket signaling is required for cloud relay fallback, SFU audio routing, and cross-mesh bridging.

### 🎯 Rule 5: Non-Calendar Milestone-Driven Sprints
- **Law**: A sprint or story is closed ONLY when 100% of Definition of Done (DoD) criteria pass.
- In-progress tasks cannot be rolled over silently without updating the Sprint Document and Obsidian Kanban board.

### 🧪 Rule 6: Mandatory Automated Verification Gates
- **Multiplatform Tests**: All unit tests must pass cleanly:
  ```bash
  ./gradlew test
  ```
- **Platform Builds**: Android debug APK and iOS ARM64 simulator builds must compile without errors:
  ```bash
  ./gradlew :app:androidApp:assembleDebug :app:sharedLogic:compileKotlinIosSimulatorArm64
  ```

### 📊 Rule 7: Mandatory Graphify Knowledge Sync
- **Law**: After modifying code files, always run:
  ```bash
  graphify update .
  ```
- Keeps the AST knowledge graph (`graphify-out/graph.json`) continuously synchronized with zero API cost.

### 🔍 Rule 8: OpenCodeReview Pre-Commit Gate
- **Law**: Before committing any story, run OpenCodeReview delegation mode:
  ```bash
  ocr delegate preview --format json
  ```
- Fix all `critical`, `high`, and `medium` findings before creating the git commit.

### 🌿 Rule 9: Multi-Tier Branching Strategy (docs/BRANCHING.md)
- `main` is protected and strictly reserved for production release tags (`vX.Y.Z`).
- `develop` is the primary integration highway.
- Sprints live on dedicated `sprint/sprint-NN-<name>` branches.
- Individual Ralph stories execute on atomic `feature/<STORY-ID>-<slug>` branches off the active sprint branch, and merge back to the sprint branch upon completion.

---

## 🛡️ 2. Coding Standards & Anti-Overengineering (Ponytail)

1. **Hierarchy of Implementation**: Prioritize existing codebase utilities, Kotlin standard library functions, and native platform capabilities before introducing custom abstractions.
2. **Reject Speculative Generality (YAGNI)**: Never create generic repository wrappers, abstract factories, or multi-layered interfaces for single-implementation classes unless explicitly mandated by project architecture.
3. **Minimal Dependency Footprint**: Prefer pure Kotlin Multiplatform implementations over adding third-party Gradle dependencies when complexity is modest.
4. **Token & Context Conservation**: Prefer targeted graph queries (`graphify query`) over dumping large source files into LLM context.
