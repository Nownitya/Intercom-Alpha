# Engineering Rules & Project Governance — Intercom-Alpha

> **Document Version:** 1.0  
> **Status:** Active / Non-Negotiable  
> **Obsidian Reference:** `07_Meta/Sprint-Rules.md` & `SPRINT-RULES.md`

---

## ⚖️ 1. Core Architectural Laws (Non-Negotiable)

### 🔴 Rule 1: No Shared UI on iOS (100% Native SwiftUI)
- **Law**: Under no circumstances should Compose Multiplatform be targeted to iOS.
- **Implementation**: The iOS client in `app/iosApp` must remain 100% native SwiftUI.
- **Bridge Contract**: All interaction with shared logic must route through `IntercomBridge.kt` (`app/sharedLogic/src/iosMain`), providing reactive, memory-safe callback observers (`CancellationHandle`).

### 🟢 Rule 2: Shared Business & Protocol Logic in KMP
- **Law**: All networking, packet handling, audio DSP algorithms, group state, and encryption MUST live in `app/sharedLogic/src/commonMain`.
- Platform-specific code (`androidMain`, `iosMain`, `jvmMain`) is reserved strictly for hardware adapters (`AudioRecord`/`AVAudioEngine`, CoreBluetooth/Android BLE, and SCO routing).

### 🛡️ Rule 3: Pure Multiplatform Cryptography
- **Law**: Cryptographic primitives in `commonMain` must have zero C/JNI or Java-only dependencies (`java.security.*`).
- **Implementation**: All encryption algorithms (e.g., RFC 8439 ChaCha20-Poly1305 in `PacketCipher.kt`) must be written in pure Kotlin so they execute identically across Android, iOS Native, JVM, and Wasm/JS.

### 🌐 Rule 4: Retain `:core` and `:server` (ADR-009)
- **Law**: Do not delete `:core` or `:server`.
- While peer-to-peer BLE mesh is the primary transport, Ktor WebSocket signaling in `:server` and shared contracts in `:core` are required for cloud relay fallback, SFU audio routing, and cross-mesh bridging.

---

## 🏃 2. Sprint & Development Governance

### 📅 Rule 5: Non-Calendar Milestone-Driven Sprints
- Sprints are **outcome-driven**, not bound to arbitrary calendar weeks.
- A sprint is marked "Complete" **if and only if 100% of Acceptance Criteria (DoD)** are verified and tested.
- In-progress tasks cannot be rolled over silently without updating the Sprint Document and Obsidian Kanban.

### 🧪 Rule 6: 100% Multiplatform Test Coverage
- Before closing any task or sprint, the full test suite must pass cleanly:
  ```bash
  ./gradlew test
  ```
- Android Debug APK and iOS Simulator frameworks must compile without errors:
  ```bash
  ./gradlew :app:androidApp:assembleDebug :app:sharedLogic:compileKotlinIosSimulatorArm64
  ```

---

## 🧠 3. AI & Knowledge Graph Maintenance

### 🕸️ Rule 7: Mandatory Graphify Knowledge Graph Sync
- **Rule**: After modifying any code files in a session, run:
  ```bash
  graphify update .
  ```
- This maintains AST indexing (nodes, edges, communities) at zero API cost.
- Always check `graphify query "<question>"` or `graphify-out/` before researching unfamiliar architectural components.

### 📓 Rule 8: Obsidian Vault 8-Pillar Alignment
- Keep all documentation in the Obsidian Vault (`D:\ObsidianVault\Vault1\ObsidianVault\Android\Projects\Intercom-Alpha`) strictly categorized within the sequential pillar structure:
  - `00_Inbox`: Unprocessed incoming notes
  - `01_Management`: Active/Archive sprints, roadmap, daily logs, kanban
  - `02_Architecture`: ADRs, blueprints, Archify diagrams
  - `03_Specifications`: Protocols, testing checklists
  - `04_Platform-Guides`: Android, iOS, Server, KMP setup guides
  - `05_Knowledge-Base`: Audio DSP, BLE Mesh deep dives
  - `06_AI-Sync`: Graphify manifests, MemPalace walkthroughs
  - `07_Meta`: Project charter, sprint rules, templates
  - `08_Archive`: Deprecated artifacts

### 🔍 Rule 9: OpenCodeReview Pre-Commit Gate
- Before committing any atomic implementation story in the Ralph loop, run:
  ```bash
  ocr delegate preview --format json
  ocr delegate rule --rule .ocr/rules.json --format json <files...>
  ```
- All findings of severity `critical`, `high`, and `medium` MUST be resolved by Ralph before the git commit is created.
- Review happens strictly against uncommitted working copy changes (`git diff HEAD`).
- OpenCodeReview is strictly read-only and never modifies code automatically.

---

## ⚠️ 4. Technical Gotchas & Critical Patterns

### 🧩 Gotcha 1: Navigation 3 Polymorphic Backstack
- In `AppNavGraph.kt`, when using `rememberNavBackStack(config, HomeRoute)`, `NavKey` is an open interface.
- You **must** provide `SavedStateConfiguration` with a `SerializersModule` registering each route under `polymorphic(NavKey::class)`. Omitting this causes runtime crashes on Android back gestures.

### ⚡ Gotcha 2: Json Class Discriminator Collision
- `MeshPacket` has a subclass `Control(val type: ControlType)`.
- The field name `type` collides with default `kotlinx.serialization` discriminator `"type"`.
- Always configure `Json { classDiscriminator = "#type" }` to prevent `JsonEncodingException`.

### 🔄 Gotcha 3: Packet Flooding Loops
- Never broadcast a received packet without checking `PacketDeduplicator.shouldProcess(packet)`.
- Cyclic BLE connections across $>2$ devices will cause exponential broadcast storms and freeze GATT servers without sliding-window deduplication.

### 📝 Gotcha 4: Obsidian Bases Schema
- In `.base` files, `groupBy` **must** specify both `property` and `direction: ASC` (or `DESC`).
- Omitting `direction` breaks the Obsidian Bases AST parser with `"groupBy must be an object"`.
