# Contributing to Intercom-Alpha

Thank you for your interest in contributing to **Intercom-Alpha**!

Intercom-Alpha is an off-grid, low-latency, peer-to-peer Bluetooth Low Energy (BLE) mesh voice intercom system built with Kotlin Multiplatform (KMP) and 100% native SwiftUI on iOS.

---

## 🏛️ Engineering Philosophy & Workflow

We employ a **Hybrid GSD Core + Ralph + OpenCodeReview Architecture**:

* **GSD Core**: Owns the roadmap, requirements (`docs/PRD.md`), architecture (`docs/ARCHITECTURE.md`), and phase verification.
* **Ralph**: Owns atomic implementation, testing, diff inspection, finding resolution, and git commits (one story at a time).
* **OpenCodeReview**: Owns pre-commit review. Strictly read-only; never mutates code directly.

---

## ⚖️ Non-Negotiable Architectural Laws

Before writing code or opening a pull request, review [`AGENTS.md`](./AGENTS.md) and [`docs/RULES.md`](./docs/RULES.md):

1. **Rule 1: No Shared UI on iOS (100% Native SwiftUI)**
   - Compose Multiplatform must NEVER be targeted to iOS.
   - The iOS client in `app/iosApp` must remain 100% native SwiftUI.
   - All iOS interaction with shared logic routes through `IntercomBridge.kt` (`iosMain`) with reactive, memory-safe `CancellationHandle` callbacks tied to view lifecycle.
2. **Rule 2: Shared Business & Protocol Logic in KMP**
   - Networking, audio pipeline contracts, packet handling, deduplication, group management, and encryption MUST live in `app/sharedLogic/src/commonMain`.
   - Platform-specific code (`androidMain`, `iosMain`) is reserved strictly for hardware adapters (`AudioRecord`/`AVAudioEngine`, CoreBluetooth/Android BLE, SCO routing).
3. **Rule 3: Pure Multiplatform Cryptography**
   - Cryptographic primitives in `commonMain` must have zero Java (`java.security.*`) or C/JNI dependencies.
   - Implementations (e.g. RFC 8439 ChaCha20-Poly1305 in `PacketCipher.kt`) must be 100% pure Kotlin.
4. **Rule 4: Retain `:core` and `:server` (ADR-009)**
   - Do not delete `:core` or `:server`. Ktor WebSocket signaling is required for cloud relay fallback and SFU cross-mesh bridging.
5. **Rule 5: Non-Calendar Milestone-Driven Sprints**
   - A sprint or story is closed ONLY when 100% of Definition of Done (DoD) criteria pass.

---

## 🧪 Local Build & Verification

Before submitting any Pull Request, ensure that all automated verification gates pass:

```bash
# 1. Run all multiplatform unit tests across all subprojects
./gradlew test

# 2. Assemble Android Debug APK
./gradlew :app:androidApp:assembleDebug

# 3. Compile iOS KMP Multiplatform Framework
./gradlew :app:sharedLogic:compileKotlinIosSimulatorArm64

# 4. Run OpenCodeReview pre-commit check
ocr delegate preview --format json
```

---

## 🔀 Branching & PR Guidelines

* Branch from `main`: `git checkout -b feature/your-feature-name`
* Keep pull requests focused on a single atomic capability or bug fix.
* Fill out the checklist in `.github/pull_request_template.md`.
* Ensure commits follow conventional commit format: `feat: [Story ID] - [Story Title]` or `fix: [Story ID] - [Description]`.
