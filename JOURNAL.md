# Engineering Journal & Changelog

> **Obsidian Source:** `D:\ObsidianVault\Vault1\ObsidianVault\Android\Projects\Intercom-Alpha\10-Daily-Notes\`  
> **MemPalace Integration:** Wing `intercom_alpha` (2,426 drawers)

## 📅 2026-09-27: Sprint 05 — Multi-Hop Flooding Relay, Sliding-Window Deduplication & ChaCha20-Poly1305 Encryption

### Summary
1. **Sliding-Window Packet Deduplication (`PacketDeduplicator.kt`)**:
   - Implemented thread-safe sliding-window packet deduplication cache in `commonMain` with mutex protection, 5000ms expiration TTL, and 1000 max capacity.
   - Built composite deduplication keys: `audio:senderId:seq`, `ctrl:senderId:type:hash`, and `relay:origSender:innerKey`.
   - Prevents broadcast flooding storms across cyclic multi-peer BLE topologies.
2. **Multi-Hop TTL Flooding Relay & Inner Packet Delivery (`MeshTransport.android.kt` & `MeshTransport.ios.kt`)**:
   - Integrated `PacketDeduplicator` into both Android and iOS GATT mesh engines.
   - Unpacked incoming `MeshPacket.Relay` payloads, immediately delivering inner `Audio` and `Control` packets to local channels.
   - Decremented `ttl` from 3 down to 1 and re-broadcasted forwarded packets to all other connected peers without manual intervention.
3. **Pure Kotlin RFC 8439 ChaCha20-Poly1305 AEAD (`PacketCipher.kt`)**:
   - Implemented 100% pure Kotlin RFC 8439 ChaCha20 stream cipher, Poly1305 26-bit limb one-time authenticator, and FIPS 180-4 SHA-256 digest.
   - Zero native or platform-specific C/JNI dependencies: runs identically across Android, iOS Native, JVM, and Wasm/JS.
   - Verified byte-for-byte against the official RFC 8439 Section 2.8.2 test vector.
4. **Multi-Hop Simulation & Relay Unit Tests (`CrossPlatformGattMeshTest.kt` & `PacketCipherTest.kt`)**:
   - Added `SimulatedMeshNode` verifying 3-node topology (A ➔ B ➔ C) with TTL decrement (3 ➔ 2), local delivery at B, packet forwarding to C, broadcast loop suppression, and end-to-end encrypted PCM voice payload delivery.
5. **Verification**:
   - `./gradlew test`: 100% passing across all targets.
   - `./gradlew :app:androidApp:assembleDebug`: BUILD SUCCESSFUL in 8s.
   - `./gradlew :app:sharedLogic:compileKotlinIosSimulatorArm64`: BUILD SUCCESSFUL in 12s.
   - `graphify update .`: Knowledge graph updated to 1,179 nodes, 1,994 edges, 94 communities.

---

## 📅 2026-09-27: Standard Application Flow, Multiplatform Koin DI & Jetpack Navigation Compose

### Summary
1. **Multiplatform Dependency Injection (Koin 4.0.2)**:
   - Added `koin-core`, `koin-android`, `koin-compose`, and `koin-compose-viewmodel` across `sharedLogic`, `sharedUI`, and `androidApp`.
   - Verified seamless compatibility across Android, JVM, iOS Simulator, and native builds.
   - Created `sharedUiModule` in `app/sharedUI/src/commonMain/kotlin/org/nowni/intercom_alpha/di/AppModule.kt` declaring multiplatform ViewModels (`viewModelOf(::HomeViewModel)`).
2. **Android Application & Clean Activity Flow**:
   - Created `IntercomAlphaApplication : Application()` in `app/androidApp/src/main/kotlin/org/nowni/intercom_alpha/IntercomAlphaApplication.kt`.
   - Registered `android:name=".IntercomAlphaApplication"` in `AndroidManifest.xml`.
   - Refactored `MainActivity.kt` to the standard boilerplate calling `IntercomAlphaApp()`.
3. **Type-Safe Compose Multiplatform Navigation & Navigation 3**:
   - Upgraded to modern Navigation 3: added `org.jetbrains.androidx.navigation3:navigation3-ui:1.1.2` (`navigation3-ui`) and `org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10` with Kotlin Serialization.
   - Built `AppNavGraph.kt` with `@Serializable object HomeRoute` and Koin-injected ViewModels via `koinViewModel()`.
   - Verified metadata compilation and compatibility across Android, JVM, and Wasm/JS.
   - Wrapped `IntercomAlphaApp()` in `KoinContext` and `AppNavGraph()`.
4. **Verification**:
   - `./gradlew :app:sharedUI:compileCommonMainKotlinMetadata`: BUILD SUCCESSFUL.
   - `./gradlew :app:androidApp:assembleDebug`: BUILD SUCCESSFUL.
   - `./gradlew :app:sharedLogic:compileKotlinIosSimulatorArm64`: BUILD SUCCESSFUL.
   - `./gradlew test`: 100% passing across all multiplatform modules.
   - `graphify update .`: Knowledge graph refreshed to 1,124 nodes, 1,873 edges, 81 communities.

---

## 📅 2026-09-26: Sprint 04 — Swift ↔ KMP Bridge, QR Scanner & Vault Restructuring

### Summary
1. **Swift ↔ KMP Bridge (`IntercomBridge.kt`)**:
   - Implemented `IntercomBridge` in `app/sharedLogic/src/iosMain` providing clean, non-blocking Swift callback observers (`CancellationHandle`) for input/output audio levels, recording state, peers, connection state, headset route, and active group.
   - Piped incoming Mesh audio directly to `audioEngine.playAudio(shorts)` and recorded microphone audio to `meshTransport.sendAudio(...)`.
   - Wired `HeadsetManager` physical button events (PTT_PRESS / PTT_RELEASE) directly to transmission.
2. **SwiftUI ViewModel & Camera QR Scanner**:
   - Created `@MainActor class IntercomViewModel: ObservableObject` in `app/iosApp/iosApp/IntercomViewModel.swift`.
   - Implemented native `AVFoundation` camera-based QR code scanner (`QrScannerView.swift`) and dynamic QR share sheet (`QrShareView`) in `ContentView.swift`.
   - Connected PTT touch drag gestures, audio level meters, dynamic group status, and peer rows directly to live Kotlin/Native shared logic.
3. **Obsidian Vault 8-Pillar Restructuring**:
   - Reorganized `D:\ObsidianVault\Vault1\ObsidianVault\Android\Projects\Intercom-Alpha` from 21 cluttered folders into a clean, sequential `00_Inbox` to `08_Archive` structure.
   - Decoupled all 9 ADRs into MADR markdown files with standardized YAML frontmatter.
   - Created dynamic Dataview dashboards in `INDEX.md` and `Architecture-MOC.md`.
4. **Verification**:
   - `compileKotlinIosArm64` & `compileKotlinIosSimulatorArm64`: ALL PASSED.
   - `assembleDebug` Android APK: BUILD SUCCESSFUL in 8s.
   - `./gradlew test`: 100% passing across all modules.
   - `graphify update .`: Rebuilt with 1,075 nodes, 1,812 edges, 82 communities.

---

## 📅 2026-09-26: Phase 2 & Phase 3 Implementation (Android + iOS + Cross-Platform GATT)

### Summary
Completed the platform implementations for both Android and iOS in `app/sharedLogic`, established a unified single-source GATT mesh protocol, updated native SwiftUI UI in `app/iosApp`, and verified all unit tests across the monorepo.

### Key Milestones
1. **iOS Audio Engine (`AudioEngine.ios.kt`)**:
   - `AVAudioEngine` input tap with RMS volume metering and VOX energy thresholding.
   - `AVAudioPlayerNode` with dynamic buffer scheduling.
   - `AVAudioSession` Voice Chat category with Bluetooth SCO routing.
2. **iOS Transport (`MeshTransport.ios.kt`)**:
   - CoreBluetooth GATT Server (`CBPeripheralManager`) & Client (`CBCentralManager`).
   - `MultipeerConnectivity` (`MCSession`) peer-to-peer Wi-Fi/Bluetooth bridge.
3. **iOS Headset Manager (`HeadsetManager.ios.kt`)**:
   - `AVAudioSession` route changes and `MPRemoteCommandCenter` media button listener.
4. **Native SwiftUI UI (`ContentView.swift`)**:
   - Complied with "Do not share UI - Use SwiftUI" directive.
   - Built interactive PTT button, live audio meters, peer list, and route status.
5. **Cross-Platform GATT Mesh Test Suite (`CrossPlatformGattMeshTest.kt`)**:
   - Validated JSON serialization symmetry for `Audio`, `Control`, `Discovery`, and `Relay` packets.
   - Verified QR invite code generator/parser.
6. **Build & Knowledge Verification**:
   - `compileIosMainKotlinMetadata`, `compileKotlinIosArm64`, `compileKotlinIosSimulatorArm64`: ALL PASSED.
   - `assembleDebug` Android APK: BUILD SUCCESSFUL.
   - Unit tests: 100% passed across all targets.
   - Knowledge Graph: Rebuilt with 967 nodes, 1,606 edges, and exported to Obsidian canvas.
   - MemPalace: Mined 1,391 new drawers into `intercom_alpha` wing (total: 2,426 drawers).

---

## 📅 2026-09-26: Phase 1 Core Networking & Signaling
- Built polymorphic protocol in `:core` (`SignalingProtocol.kt`).
- Built Ktor WebSocket Server in `:server` (`/ws/intercom`, `/api/status`).
- Built KMP Signaling Client in `app/sharedLogic` (`IntercomSignalingClient.kt`).
- Retained `:core` and `:server` per ADR-009 for cloud audio relay & signaling fallback.
