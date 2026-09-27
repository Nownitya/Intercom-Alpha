---
name: open-code-review
description: Performs AI-powered code review on Git changes using Alibaba OpenCodeReview (ocr) in Delegation Mode. Use to review Ralph's uncommitted/staged working copy changes against architectural laws, correctness, concurrency, security, and KMP standards before committing.
license: Apache-2.0
metadata:
  version: "1.12.9"
  author: alibaba / Intercom-Alpha
---

# OpenCodeReview — Delegation Mode for Intercom-Alpha

A specialized code review skill leveraging Alibaba's `open-code-review` (`ocr`) CLI for deterministic file filtering, git diff scoping, and rule resolution, executed by the host AI agent to enforce zero-external-transmission code review quality gates.

---

## 🎯 Role in the Hybrid Pipeline

```
GSD (Plan / Architect) 
  ➔ Ralph (Select Story ➔ Implement ➔ ./gradlew test)
      ➔ OpenCodeReview Gate (PRE-COMMIT)
          ➔ Clean? ➔ Commit & Update prd.json ➔ Next Story
          ➔ Findings? ➔ Ralph Fixes ➔ Re-test ➔ Re-review
```

* **Read-Only**: OpenCodeReview NEVER mutates source code directly.
* **Pre-Commit**: Always reviews uncommitted working tree changes (`git diff HEAD`) before Ralph creates a commit.
* **Deterministic Scoping**: Uses `ocr delegate preview --format json` to ensure exact changed file boundaries.

---

## 🛠️ Review Execution Protocol

### Step 1: Scoping via OCR Preview
Run the deterministic scoping command:
```bash
ocr delegate preview --format json
```
Extract:
- `reviewable_files`: List of changed files to inspect.
- `mode`: Typically `workspace` (staged, unstaged, untracked).

### Step 2: Resolve File Rules
Run rule resolution against the reviewable files:
```bash
ocr delegate rule --format json <reviewable_files...>
```
Also load project-specific architectural rules from `.ocr/rules.json`.

### Step 3: Inspect Diff & Surrounding Context
For each reviewable file:
```bash
git diff HEAD -- <path>
```
If untracked, read the entire new file.

### Step 4: Line-Level Inspection Criteria
Evaluate against 5 core engineering pillars:
1. **Platform Isolation (Rule 1)**:
   - NO Compose Multiplatform imports in `app/iosApp`. iOS must remain 100% native SwiftUI.
   - All shared interactions in iOS must route via `IntercomBridge.kt` with cancellation handles.
2. **KMP & Pure Crypto (Rules 2 & 3)**:
   - Zero platform-specific C/JNI or `java.security.*` imports in `app/sharedLogic/src/commonMain`.
   - Cryptographic primitives must remain pure Kotlin.
3. **Concurrency & Thread Safety**:
   - Proper use of structured concurrency (`CoroutineScope`, supervisor jobs).
   - Protection of mutable state via `Mutex` or atomic state flows. No unbounded channel buffering.
4. **Correctness & Resource Lifecycle**:
   - Proper handling of nullable types (no unchecked `!!`).
   - Cleanup of audio taps, Bluetooth GATT connections, and OS wake locks on service stop / view deinit.
5. **Deduplication & Packet Handling**:
   - Ensure mesh forwarding always checks `PacketDeduplicator.shouldProcess(packet)` to prevent broadcast storms.
   - Ensure `classDiscriminator = "#type"` on polymorphic packet serializers.

### Step 5: Output Format & Gate Evaluation
Write findings to `tasks/ralph/review-findings.json`:
```json
{
  "timestamp": "2026-09-27T17:30:00Z",
  "story_id": "ICA-601",
  "status": "passed" | "actionable_findings",
  "findings": [
    {
      "path": "app/sharedLogic/src/commonMain/.../File.kt",
      "start_line": 42,
      "end_line": 48,
      "category": "bug" | "security" | "performance" | "maintainability" | "architecture",
      "severity": "critical" | "high" | "medium" | "low",
      "content": "Specific explanation of defect and recommended fix"
    }
  ]
}
```

* **Gate Rule**: If ANY finding has `severity` $\in \{\text{critical}, \text{high}, \text{medium}\}$, the review FAILS.
  - Ralph must fix all non-low findings.
  - Re-run Gradle tests & builds.
  - Re-run OpenCodeReview.
* If all findings are `low` or zero findings exist, the review PASSES. Ralph proceeds to git commit.
