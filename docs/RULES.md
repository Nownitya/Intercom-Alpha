# Engineering Rules & Project Governance — Intercom-Alpha

> **Document Version:** 3.0.0  
> **Status:** Active / Non-Negotiable Governance  
> **Repository:** `Intercom-Alpha`  

---

## The 10 Non-Negotiable Architectural Laws

### Rule 1: No Shared UI on iOS (100% Native SwiftUI)
* **Law**: Under no circumstances should Compose Multiplatform be targeted to iOS.
* **Implementation**: The iOS client in `app/iosApp` must remain 100% native SwiftUI.
* **Bridge Contract**: All interaction with shared logic must route through `IntercomBridge.kt` (`app/sharedLogic/src/iosMain`), providing reactive, memory-safe callback observers (`CancellationHandle`) bound to SwiftUI view lifecycles.

### Rule 2: Shared Business & Protocol Logic in KMP
* **Law**: All networking, packet handling, deduplication, group management, audio DSP algorithms, link quality arbitration, and encryption MUST live in `app/sharedLogic/src/commonMain`.
* **Platform Source Sets**: Hardware-specific APIs (`AudioRecord`/`AVAudioEngine`, CoreBluetooth/Android BLE, Bluetooth SCO routing) live strictly in platform source sets (`androidMain`, `iosMain`).

### Rule 3: Pure Multiplatform Cryptography
* **Law**: Cryptographic primitives in `commonMain` must have zero Java (`java.security.*`) or C/JNI dependencies.
* **Implementation**: All algorithms (e.g. RFC 8439 ChaCha20-Poly1305 in `PacketCipher.kt`) must be written in 100% pure Kotlin so they execute identically across Android, iOS Native, JVM, and Wasm targets.

### Rule 4: Retain `:core` and `:server` (ADR-009)
* **Law**: Do not delete `:core` or `:server`.
* **Rationale**: Ktor WebSocket signaling is required for cloud relay fallback, SFU audio routing, and cross-mesh bridging.

### Rule 5: Non-Calendar Milestone-Driven Sprints
* **Law**: A sprint or story is closed ONLY when 100% of Definition of Done (DoD) criteria pass.
* In-progress tasks cannot be rolled over silently without updating the Sprint Document and Obsidian Kanban board.

### Rule 6: Mandatory Automated Verification Gates
* **Multiplatform Tests**: All unit tests must pass cleanly:
  ```bash
  ./gradlew test
  ```
* **Platform Builds**: Android debug APK and iOS ARM64 simulator builds must compile without errors:
  ```bash
  ./gradlew :app:androidApp:assembleDebug :app:sharedLogic:compileKotlinIosSimulatorArm64
  ```

### Rule 7: Zero-Allocation Real-Time Audio DSP
* **Law**: Real-time audio ingestion and playout loops must not allocate heap objects per frame.
* **Implementation**: Circular ring buffers and static tables must be used exclusively to prevent GC audio stutter.

### Rule 8: Mandatory Memory Zeroization on Session Teardown
* **Law**: All cryptographic keys, intermediate hashes, and audio ring buffers must be explicitly zeroed in memory upon session close.

### Rule 9: Dynamic Link-Aware Transmission
* **Law**: Transmission parameters (bitrate, FEC ratio, transport path) must adapt dynamically to link quality ($Q_{link}$). Static bitrates under lossy RF conditions are strictly prohibited.

### Rule 10: Mandatory Graphify & Documentation Sync
* **Law**: After completing sprint stories, always synchronize AST knowledge graphs and update repository & Obsidian documentation before marking the sprint closed.
