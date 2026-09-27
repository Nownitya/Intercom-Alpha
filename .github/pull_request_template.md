## 📋 Pull Request Summary

* **Story / Task ID**: `ICA-`
* **Phase / Sprint**: Sprint 
* **Type of Change**: `feat` | `fix` | `refactor` | `docs` | `chore`

---

## 🔍 Detailed Description

<!-- Briefly describe the architectural decisions, code changes, and implementation rationale -->

---

## 🧪 Definition of Done (DoD) & Verification Checklist

- [ ] **Automated Tests**: `./gradlew test` passes 100% across all subprojects.
- [ ] **Android Compilation**: `./gradlew :app:androidApp:assembleDebug` builds cleanly with valid APK.
- [ ] **iOS KMP Target**: `./gradlew :app:sharedLogic:compileKotlinIosSimulatorArm64` compiles with 0 errors.
- [ ] **OpenCodeReview Gate**: `ocr delegate preview` passed with 0 critical, high, or medium findings.
- [ ] **Platform Isolation (Rule 1)**: `app/iosApp` is 100% native SwiftUI. Zero Compose Multiplatform imports.
- [ ] **Pure Multiplatform Crypto (Rule 3)**: Zero Java (`java.security.*`) or C/JNI dependencies in `commonMain`.
- [ ] **Broadcast Storm Suppression**: `PacketDeduplicator.shouldProcess(packet)` is checked before packet relay.
- [ ] **Serialization Safety**: `Json { classDiscriminator = "#type" }` is used on `MeshPacket` serializers.

---

## 📸 Screenshots / Verification Artifacts

<!-- Attach screenshots, VU meter demos, or test logs if applicable -->
