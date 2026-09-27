# Intercom-Alpha — Master Development Specification

> **Single source of truth** for scope, phases, platform decisions, and delivery plan.
> All questions about "what, why, when, how" answered here.

---

## 🎯 1. PRODUCT VISION & END STATE

### What Is The End Product (v1.0)?
A **production-ready mobile app** (Android + iOS) that lets motorcycle/bike groups talk to each other **completely offline** via Bluetooth mesh.

**User Experience:**
```
Rider opens app → Creates group → Shares QR code → Others scan → Talk via headset
```
- No accounts, no internet, no server, no configuration
- Works in tunnels, forests, mountains, highways
- 4+ hours battery life with screen off
- Android ↔ iOS interoperability (critical)

### Success Metrics (v1.0 Release Criteria)
| Metric | Target | Measurement |
|--------|--------|-------------|
| **Range (open)** | ≥500m | Field test |
| **Range (urban)** | ≥100m | Field test |
| **Latency (1-hop)** | <100ms | Automated test |
| **Latency (3-hop)** | <300ms | Automated test |
| **Battery (4hr ride)** | <30% drain | Real-world test |
| **Crash-free sessions** | >99.5% | Play Console / TestFlight |
| **Join success rate** | >95% | QA test matrix |
| **Audio quality (MOS)** | >3.5 | Subjective + PESQ |

---

## 📦 2. SCOPE — IN vs OUT (v1.0)

### ✅ IN SCOPE (Must Have for v1.0)

| Feature | Description | Priority |
|---------|-------------|----------|
| **BLE Mesh Transport** | Flooding relay, TTL=3, GATT-based | P0 |
| **WiFi Direct Upgrade** | Nearby Connections (Android only) for higher bandwidth | P1 |
| **Opus Audio** | 5 profiles (16-64 kbps), DTX, FEC, PLC | P0 |
| **PTT Mode** | Push-to-talk via screen button + headset button | P0 |
| **VOX Mode** | Voice-activated, adjustable threshold (-20 to -60 dB) | P0 |
| **Bluetooth Headset** | SCO audio routing, button mapping, battery read | P0 |
| **Group Management** | Create/join/leave, QR invite (offline), leadership transfer | P0 |
| **Background Operation** | Foreground Service (Android), Background Modes (iOS) | P0 |
| **Cross-Platform Mesh** | Android ↔ iOS via common BLE GATT protocol | P0 |
| **Encryption** | Noise_XK handshake, per-session keys, forward secrecy | P1 |

### ❌ OUT OF SCOPE (v1.0 — Explicitly Deferred)

| Feature | Reason | Future |
|---------|--------|--------|
| Text messaging | Voice-first MVP | v2.0 |
| Location sharing | Privacy + battery | v2.0 |
| Music streaming | Beyond 64kbps profile | v2.0 |
| Server/cloud components | Offline-first principle | Optional v2.0 |
| WebRTC signaling | Not needed for mesh | Never |
| Advanced mesh routing (OLSR/BATMAN) | TTL=3 flooding sufficient for ≤8 peers | v2.0 |
| Multi-hop > 3 | Diminishing returns, battery cost | v2.0 |
| Video | Bandwidth impossible on BLE | Never |
| Desktop app as primary | Companion only | v2.0 |
| Web app as primary | Companion only | v2.0 |

### 🔮 FUTURE (v2.0+ Backlog)
- Adaptive bitrate by RSSI
- Mesh routing optimization
- Group recording/playback
- Intercom ↔ phone call bridging
- Web dashboard (admin/debug)
- Dedicated hardware firmware

---

## 🏗️ 3. PLATFORM & MODULE BREAKDOWN — WHAT, WHY, NEEDED?

### Core Modules (Required for v1.0)

| Module | Path | Platform | Purpose | Required? |
|--------|------|----------|---------|-----------|
| **sharedLogic** | `app/sharedLogic/` | KMP (Android, iOS, JVM, JS, Wasm) | **All business logic**: MeshTransport, AudioEngine, HeadsetManager, GroupManager — expect/actual pattern | **YES — Core** |
| **sharedUI** | `app/sharedUI/` | Compose Multiplatform (Android, Desktop, Web) | Shared Compose UI components: PTT button, peer list, profile selector, level meter | **YES — Android UI** |
| **androidApp** | `app/androidApp/` | Android | App entry point, Manifest, Foreground Service, MainActivity hosting Compose | **YES — Android** |
| **iosApp** | `app/iosApp/` | iOS (SwiftUI) | App entry point, SwiftUI views, ViewModels bridging to SharedLogic framework | **YES — iOS** |

### Companion Modules (Optional — Not Required for v1.0)

| Module | Path | Platform | Purpose | Keep? |
|--------|------|----------|---------|-------|
| **desktopApp** | `app/desktopApp/` | JVM (Desktop) | Debug companion, testing, admin dashboard | **KEEP — Low maintenance, useful for dev** |
| **webApp** | `app/webApp/` | Wasm/JS (Browser) | Remote monitoring, signaling server UI (future) | **KEEP — Low maintenance, future signaling** |
| **core** | `core/` | Pure Kotlin (all) | Shared pure-Kotlin code (no platform deps) | **REMOVE — Redundant with sharedLogic** |
| **server** | `server/` | JVM (Ktor) | Signaling/relay server (WebRTC fallback) | **REMOVE for v1.0 — Offline-first, not needed** |

### Decision: **REMOVE `core` and `server` modules for v1.0**

**Why?**
- `core` duplicates `sharedLogic/commonMain` — no value add
- `server` violates offline-first principle; WebRTC not in scope
- Reduces build complexity, CI time, maintenance burden
- Can be re-added later if signaling needed (v2.0+)

**Action:** Delete `core/` and `server/` directories, remove from `settings.gradle.kts`

---

## 📱 4. PLATFORM STRATEGY — ANDROID vs iOS vs DESKTOP vs WEB

### Primary Targets (v1.0 Release)

| Platform | UI Framework | Transport | Audio | Status |
|----------|--------------|-----------|-------|--------|
| **Android** | Compose Multiplatform (sharedUI) | BLE GATT + Nearby Connections | AudioRecord/Track + opus-android | **Primary** |
| **iOS** | SwiftUI (native) | MultipeerConnectivity + CoreBluetooth | AVAudioEngine + libopus | **Primary** |

### Secondary Targets (Companion — Not Release Blockers)

| Platform | UI Framework | Use Case | Priority |
|----------|--------------|----------|----------|
| **Desktop (JVM)** | Compose Multiplatform | Dev debugging, multi-device test on one machine | Nice-to-have |
| **Web (Wasm)** | Compose for Web | Remote monitoring, future signaling dashboard | Nice-to-have |

### Platform-Specific Implementation Notes

#### Android (Primary)
- **Transport**: BLE GATT (primary) + Nearby Connections (upgrade)
- **Audio**: `AudioRecord` (capture) + `AudioTrack` (playback) + `opus-android` JNI
- **Headset**: `BluetoothHeadset` + `BluetoothAdapter` + SCO routing via `AudioManager`
- **Background**: Foreground Service with `FOREGROUND_SERVICE_MICROPHONE` (API 34+)
- **Permissions**: BLUETOOTH_CONNECT/SCAN/ADVERTISE, NEARBY_WIFI_DEVICES, RECORD_AUDIO, ACCESS_FINE_LOCATION (legacy)

#### iOS (Primary)
- **Transport**: MultipeerConnectivity (primary, high-level) + CoreBluetooth (BLE Mesh fallback/upgrade)
- **Audio**: `AVAudioEngine` (capture/playback) + `libopus` via CocoaPods
- **Headset**: `AVAudioSession` category `.voiceChat` + `MPRemoteCommandCenter` for buttons
- **Background**: `UIBackgroundModes`: audio, bluetooth-central, bluetooth-peripheral
- **Critical**: **Test on physical devices only** — MultipeerConnectivity doesn't work in Simulator

#### Desktop (Companion)
- **Transport**: WebRTC data channels (for future signaling) or none
- **Audio**: JavaSound or platform-specific
- **Use**: Run multiple instances locally for mesh testing

#### Web (Companion)
- **Transport**: WebRTC / WebSockets (signaling)
- **Audio**: WebAudio API + opus-wasm
- **Use**: Admin dashboard, remote monitoring

---

## 🗓️ 5. DEVELOPMENT PHASES & SPRINTS

### Phase Overview (24 weeks → v1.0)

| Phase | Weeks | Focus | Deliverable |
|-------|-------|-------|-------------|
| **Phase 1: Foundation** | 1-4 | Scaffold, contracts, project setup | ✅ DONE |
| **Phase 2: Core Audio & Transport** | 5-8 | Android Opus + BLE, iOS Opus + Multipeer | Working audio on each platform |
| **Phase 3: Cross-Platform Mesh** | 9-12 | Android ↔ iOS BLE connect, relay | Cross-platform voice |
| **Phase 4: Headset & UX** | 13-16 | Headset integration, UI polish | Production UX |
| **Phase 5: Group & Security** | 17-20 | QR invites, encryption, group mgmt | Secure, easy joining |
| **Phase 6: Polish & Release** | 21-24 | Battery, adaptive bitrate, beta, release | v1.0 production |

### Detailed Sprint Plan (2-week sprints)

| Sprint | Dates | Focus | Key Deliverables | Definition of Done |
|--------|-------|-------|------------------|---------------------|
| **Sprint 1** | 9/20-10/3 | **Scaffold Complete** | ✅ KMP structure, contracts, Gradle, permissions | Build passes |
| **Sprint 2** | 10/4-10/17 | **Android Audio + BLE** | `AudioEngine.android.kt` (Opus), `MeshTransport.android.kt` (GATT), Foreground Service | Android↔Android audio |
| **Sprint 3** | 10/18-10/31 | **iOS Audio + Multipeer** | `AudioEngine.ios.kt` (libopus), `MeshTransport.ios.kt` (MC+CB), Background Modes | iOS↔iOS audio |
| **Sprint 4** | 11/1-11/14 | **Cross-Platform BLE** | Common packet format, Android↔iOS GATT connect, profile negotiation | Android↔iOS mesh |
| **Sprint 5** | 11/15-11/28 | **Relay + Reliability** | TTL=3 flooding, dedupe, peer cleanup, reconnection | 3+ device mesh |
| **Sprint 6** | 11/29-12/12 | **Range + Stress Test** | Field tests (open/urban/forest), 8-device stress, battery profiling | Verified specs |
| **Sprint 7** | 12/13-12/26 | **Headset Integration** | SCO routing, button mapping (PTT/Vol/Call), battery, auto-reconnect | Full headset UX |
| **Sprint 8** | 12/27-1/9 | **UI Polish** | Compose + SwiftUI parity, animations, accessibility, dark mode | Production UI |
| **Sprint 9** | 1/10-1/23 | **Groups + QR Invites** | Create/join/leave, QR gen/scan, invite expiry, leadership transfer | Easy joining |
| **Sprint 10** | 1/24-2/6 | **Encryption** | Noise_XK handshake, per-session keys, key rotation, replay protection | Secure voice |
| **Sprint 11** | 2/7-2/20 | **Battery + Adaptive** | 4hr background test, DTX tuning, adaptive bitrate (RSSI), sleep modes | 4hr battery |
| **Sprint 12** | 2/21-3/6 | **Release Prep** | TestFlight beta, Play Console Internal, Crashlytics, release notes | **v1.0 Release** |

---

## 🔧 6. HOW TO TACKLE — EXECUTION STRATEGY

### Priority Order (Critical Path)

```
1. Android AudioEngine (Opus) → 2. Android MeshTransport (GATT) → 3. Android Foreground Service
                         ↓
4. iOS AudioEngine (libopus) → 5. iOS MeshTransport (MC+CB) → 6. iOS Background Modes
                         ↓
7. Cross-Platform BLE Protocol (common packet format, GATT UUIDs)
                         ↓
8. Android ↔ iOS Mesh Connection Test
                         ↓
9. Relay (TTL=3) + 3+ Device Test
                         ↓
10. Headset Integration (both platforms)
                         ↓
11. UI Polish (Compose + SwiftUI)
                         ↓
12. QR Invites + Encryption
                         ↓
13. Battery Optimization + Release
```

### Key Technical Decisions (Already Made — ADRs)

| ADR | Decision | Rationale |
|-----|----------|-----------|
| **ADR-001** | BLE Mesh primary transport | Lower power, penetrates helmets, works in tunnels |
| **ADR-002** | Opus codec (5 profiles) | Royalty-free, DTX/FEC built-in, excellent at low bitrates |
| **ADR-003** | Leaderless mesh, TTL=3 flooding | No single point of failure, simple, works for ≤8 peers |
| **ADR-004** | KMP + Compose (Android) + SwiftUI (iOS) | Max logic sharing, native UI on each platform |
| **ADR-005** | PTT + VOX both | User preference: PTT for noise, VOX for hands-free |
| **ADR-006** | Foreground Service (Android) / Background Modes (iOS) | Only way to keep mic alive background |
| **ADR-007** | QR code invites (offline) | No server, easy sharing, expiry + signature |
| **ADR-008** | Noise_XK encryption | Forward secrecy, no PKI, low overhead |

### Risk Mitigation Actions

| Risk | Action | Owner | Timeline |
|------|--------|-------|----------|
| Android ↔ iOS BLE incompat | Implement common GATT protocol in Sprint 2-3; test weekly | Android/iOS leads | Sprint 2-4 |
| Opus native lib issues | Pin versions (opus-android 1.3.1, libopus 1.3); test all ABIs | Audio engineer | Sprint 2-3 |
| Background kill (Android) | Foreground Service + PARTIAL_WAKE_LOCK; 4hr test Sprint 11 | Android lead | Sprint 2, 11 |
| iOS BT background limits | MultipeerConnectivity primary; BLE peripheral only foreground | iOS lead | Sprint 3 |
| Battery drain | Profile weekly; DTX on, adaptive bitrate, 20ms frames | All | Sprint 6, 11 |

---

## ❓ 7. ANSWERS TO YOUR SPECIFIC QUESTIONS

### Q: Do we have sprints and phases?
**YES** — 12 sprints (2 weeks each) across 6 phases, documented above. Sprint 1 complete.

### Q: Do we have complete scope, scenarios, expectations?
**YES** — Project Charter defines scope (in/out/future), success criteria, risks, timeline. This SPEC adds platform breakdown and sprint detail.

### Q: What multiplatform things do we need vs not need?

| Component | Need? | Why? |
|-----------|-------|------|
| **sharedLogic (KMP)** | **YES** | Single source of truth for mesh/audio/headset/group logic |
| **sharedUI (Compose)** | **YES** | Android UI + Desktop/Web companion from single codebase |
| **androidApp** | **YES** | Android entry point, Foreground Service, permissions |
| **iosApp (SwiftUI)** | **YES** | iOS native UI (Compose not ready for iOS production) |
| **desktopApp** | **KEEP** | Low cost, useful for dev testing multiple peers |
| **webApp** | **KEEP** | Low cost, future signaling dashboard |
| **core** | **REMOVE** | Redundant with sharedLogic/commonMain |
| **server (Ktor)** | **REMOVE v1.0** | Offline-first; no signaling needed for mesh |

### Q: Server — do we need it?
**NO for v1.0.** The app is fully peer-to-peer. No signaling server needed because:
- BLE Mesh uses local advertising/scanning
- MultipeerConnectivity uses local Bonjour/Bluetooth
- QR invites work completely offline
- WiFi Direct uses Nearby Connections (local)

**Only add server if:** WebRTC fallback for internet relay (v2.0+), or admin dashboard backend.

### Q: Web app — do we need it?
**Not for v1.0 release.** Keep the module (low maintenance) for:
- Future: Remote monitoring dashboard
- Future: Signaling server UI (if WebRTC added)
- Current: Compose for Web practice

**Not a release blocker.**

### Q: Desktop app — do we need it?
**Not for v1.0 release.** Keep the module (low maintenance) for:
- Running multiple peers on one machine for testing
- Developer debugging companion
- Potential future: Desktop intercom for base station

**Not a release blocker.**

### Q: Core module — do we need it?
**NO. DELETE IT.** It duplicates `sharedLogic/commonMain` with no platform-specific value.

### Q: How to tackle the Android ↔ iOS gap?
**This is the #1 technical risk.** Strategy:
1. **Define common BLE GATT protocol** (service UUID, characteristic UUID, packet format) — Sprint 2
2. **Implement on Android first** (GATT server + client) — Sprint 2
3. **Implement on iOS** (CoreBluetooth peripheral + central) — Sprint 3
4. **Weekly cross-platform test** — Sprint 4+
5. **Fallback**: If BLE GATT fails, use MultipeerConnectivity on iOS + custom Android implementation (more work)

---

## 📋 8. DEFINITION OF DONE — PER FEATURE

| Checklist Item | Required? |
|----------------|-----------|
| Code complete + peer reviewed | ✅ |
| Unit tests >80% coverage (sharedLogic) | ✅ |
| Integration test on physical device (both platforms) | ✅ |
| Cross-platform verified (if applicable) | ✅ |
| Documentation updated (Obsidian + code comments) | ✅ |
| No critical/high bugs open | ✅ |
| Performance profiled (CPU, memory, battery) | ✅ |
| Battery impact measured (4hr test for background features) | ✅ |
| Accessibility (TalkBack/VoiceOver) | ✅ |
| Localization (EN at minimum) | ✅ |

---

## 🔗 9. QUICK REFERENCE — WHERE TO FIND WHAT

| Need | Location |
|------|----------|
| **Project vision & scope** | `PROJECT-CHARTER.md` (this vault) |
| **Sprint plan & milestones** | This document (`PROJECT-SPEC.md`) |
| **Architecture decisions (ADRs)** | `06-Decisions/README.md` |
| **Build & test commands** | `01-Projects/Build-Test.md` |
| **Testing checklist** | `01-Projects/Testing-Checklist.md` |
| **Android BLE guide** | `03-Resources/Android-BLE.md` |
| **iOS Multipeer guide** | `03-Resources/iOS-Multipeer.md` |
| **Opus integration** | `03-Resources/Opus-Codec.md` |
| **KMP setup** | `03-Resources/KMP-Setup.md` |
| **Codebase walkthrough** | `09-MemPalace/Codebase-Walkthrough.md` |
| **Architecture diagrams** | `09-MemPalace/Architecture-Viz.md` |
| **Daily progress** | `10-Daily-Notes/` |
| **Android app overview** | `app/androidApp/PROJECT-OVERVIEW.md` |

---

## ✅ 10. IMMEDIATE ACTION ITEMS

### This Week (Sprint 2 Start)
- [ ] **Delete `core/` and `server/` modules** from project
- [ ] **Implement `AudioEngine.android.kt`** with opus-android
- [ ] **Complete `MeshTransport.android.kt`** GATT server/client
- [ ] **Add Foreground Service** with microphone type
- [ ] **Update AndroidManifest** with all required permissions
- [ ] **Build & test Android APK** — verify BLE advertise/scan

### Next Week
- [ ] **iOS: Add libopus to Podfile**, run `pod install`
- [ ] **Implement `AudioEngine.ios.kt`** with AVAudioEngine + libopus
- [ ] **Implement `MeshTransport.ios.kt`** MultipeerConnectivity + CoreBluetooth
- [ ] **Update Info.plist** with background modes + usage descriptions
- [ ] **Build & test iOS on device** — verify Multipeer browse/connect

---

*Master Spec v1.0 — 2026-09-21*
*Source of truth for all development decisions*