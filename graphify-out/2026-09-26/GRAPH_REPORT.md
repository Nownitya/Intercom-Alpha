# Graph Report - Intercom-Alpha  (2026-09-26)

## Corpus Check
- 123 files · ~33,510 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 43 file(s) not represented in the graph (top: .xml 17, .properties 6, (none) 2)

## Summary
- 1075 nodes · 1812 edges · 82 communities (57 shown, 25 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 67 edges (avg confidence: 0.87)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- app/sharedUI/src/commonMain/kotlin/org/nowni/intercom_alpha/App.kt
- assertequals
- Intercom-Alpha — Master Development Specification
- ScannerViewController
- View
- module
- experimentalwasmdsl
- gradlew
- targetformat
- IntercomSignalingClient
- SignalingMessage
- Intercom-Alpha/app/sharedUI/src/commonMain/kotlin/org/nowni/intercom_alpha/App.kt
- AudioProfile
- README.md
- Intercom-Alpha — Project Overview
- IntercomBridge.kt
- Intercom-Alpha — Project Overview
- HeadsetManagerImpl
- IntercomSignalingClient
- rules/graphify.md
- workflows/graphify.md
- MeshTransportImpl
- MeshTransport.android.kt
- SignalingServerManager
- app/sharedLogic/src/androidMain/kotlin/org/nowni/intercom_alpha/Platform.android.kt
- app/sharedLogic/src/iosMain/kotlin/org/nowni/intercom_alpha/Platform.ios.kt
- Intercom-Alpha/app/sharedLogic/src/jsMain/kotlin/org/nowni/intercom_alpha/Platform.js.kt
- AudioEngine.android.kt
- app/sharedLogic/src/jsMain/kotlin/org/nowni/intercom_alpha/Platform.js.kt
- getPlatform
- getPlatform
- Intercom-Alpha/gradlew
- Intercom-Alpha/README.md
- GroupManager
- app/androidApp/src/main/kotlin/org/nowni/intercom_alpha/MainActivity.kt
- GroupManagerImpl
- FakeMeshTransport
- HeadsetManager.android.kt
- advertisecallback
- bluetoothgattcallback
- decodefrombytearray
- encodetobytearray
- receivechannel
- runblocking
- scancallback
- PeerList.kt
- HomeScreen.kt
- SignalingServerManager
- ControlType
- CrossPlatformGattMeshTest
- App
- IntercomForegroundService.kt
- MeshTransport.ios.kt
- Technical Notes & Architectural Gotchas
- Sprint Management Framework & Rules for Intercom-Alpha
- Current Sprint: Sprint 04 — Swift ↔ KMP Bridge & QR Code Onboarding
- IntercomBridge
- IntercomViewModel
- encodetostring
- SignalingServerManager
- .body
- HeadsetManagerImpl
- HeadsetManager
- Intercom-Alpha/server/src/main/kotlin/org/nowni/intercom_alpha/Application.kt
- AudioMeterView
- HeadsetButtonEvent
- iOSApp
- app/iosApp/iosApp/ContentView.swift
- app/webApp/src/webMain/kotlin/org/nowni/intercom_alpha/main.kt
- module
- module
- 🔑 Core Features
- HeadsetButtonListener
- peerinfo
- .start

## God Nodes (most connected - your core abstractions)
1. `MeshTransportImpl` - 38 edges
2. `MeshTransportImpl` - 36 edges
3. `AudioProfile` - 28 edges
4. `IntercomBridge` - 27 edges
5. `IntercomSignalingClient` - 20 edges
6. `IntercomSignalingClient` - 19 edges
7. `HeadsetManagerImpl` - 19 edges
8. `IntercomViewModel` - 18 edges
9. `AudioEngine` - 18 edges
10. `MeshTransport` - 18 edges

## Surprising Connections (you probably didn't know these)
- `🎯 Sprint 04 Goal` --references--> `IntercomViewModel`  [INFERRED]
  SPRINT.md → app/iosApp/iosApp/IntercomViewModel.swift
- `Key Milestones` --references--> `Audio`  [INFERRED]
  JOURNAL.md → app/sharedLogic/src/commonMain/kotlin/org/nowni/intercom_alpha/mesh/MeshTransport.kt
- `Key Milestones` --references--> `Control`  [INFERRED]
  JOURNAL.md → app/sharedLogic/src/commonMain/kotlin/org/nowni/intercom_alpha/mesh/MeshTransport.kt
- `Key Milestones` --references--> `Discovery`  [INFERRED]
  JOURNAL.md → app/sharedLogic/src/commonMain/kotlin/org/nowni/intercom_alpha/mesh/MeshTransport.kt
- `Key Milestones` --references--> `Relay`  [INFERRED]
  JOURNAL.md → app/sharedLogic/src/commonMain/kotlin/org/nowni/intercom_alpha/mesh/MeshTransport.kt

## Import Cycles
- None detected.

## Communities (82 total, 25 thin omitted)

### Community 0 - "app/sharedUI/src/commonMain/kotlin/org/nowni/intercom_alpha/App.kt"
Cohesion: 0.13
Nodes (14): HomeUiAction, HomeUiEvent, HomeUiState, LeaveGroup, NavigateToGroupCreate, SelectAudioProfile, ShowToast, StartTransmitting (+6 more)

### Community 1 - "assertequals"
Cohesion: 0.05
Nodes (20): SharedLogicAndroidHostTest, SharedLogicCommonTest, SharedLogicIOSTest, SharedLogicDesktopTest, SharedLogicWebTest, SharedUICommonTest, assertequals, asserttrue (+12 more)

### Community 2 - "Intercom-Alpha — Master Development Specification"
Cohesion: 0.05
Nodes (41): ✅ 10. IMMEDIATE ACTION ITEMS, 🎯 1. PRODUCT VISION & END STATE, 📦 2. SCOPE — IN vs OUT (v1.0), 🏗️ 3. PLATFORM & MODULE BREAKDOWN — WHAT, WHY, NEEDED?, 📱 4. PLATFORM STRATEGY — ANDROID vs iOS vs DESKTOP vs WEB, 🗓️ 5. DEVELOPMENT PHASES & SPRINTS, 🔧 6. HOW TO TACKLE — EXECUTION STRATEGY, ❓ 7. ANSWERS TO YOUR SPECIFIC QUESTIONS (+33 more)

### Community 3 - "ScannerViewController"
Cohesion: 0.09
Nodes (20): AnyObject, Coordinator, QrScannerView, ScannerViewController, ScannerViewControllerDelegate, Bool, Context, String (+12 more)

### Community 4 - "View"
Cohesion: 0.31
Nodes (9): ContentView, ContentView_Previews, .previews, ContentView, .body, ContentView_Previews, .previews, PreviewProvider (+1 more)

### Community 5 - "module"
Cohesion: 0.16
Nodes (7): Greeting, getPlatform(), Platform, sayHello(), module(), PeerSession, ApplicationTest

### Community 7 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 12 - "IntercomSignalingClient"
Cohesion: 0.12
Nodes (13): Connected, Connecting, Disconnected, Error, IntercomSignalingClient, CoroutineScope, DefaultClientWebSocketSession, SharedFlow (+5 more)

### Community 13 - "SignalingMessage"
Cohesion: 0.05
Nodes (37): abs, AudioConfig, Answer, AudioPing, AudioPong, ErrorMessage, IceCandidate, JoinRoom (+29 more)

### Community 14 - "Intercom-Alpha/app/sharedUI/src/commonMain/kotlin/org/nowni/intercom_alpha/App.kt"
Cohesion: 0.20
Nodes (9): alignment, animatedvisibility, button, column, compose_multiplatform, image, modifier, painterresource (+1 more)

### Community 15 - "AudioProfile"
Cohesion: 0.05
Nodes (32): AudioEngine, AudioEngineConfig, AudioPlayer, AudioRecorder, ByteArray, ReceiveChannel, SendChannel, ShortArray (+24 more)

### Community 17 - "Intercom-Alpha — Project Overview"
Cohesion: 0.11
Nodes (18): Android, 🏗️ Architecture at a Glance, 🎯 Current Status (Alpha Scaffold), Desktop (JVM), 📝 For New Contributors, Intercom-Alpha — Project Overview, iOS, 📚 Key Documentation (Obsidian Vault) (+10 more)

### Community 18 - "IntercomBridge.kt"
Cohesion: 0.12
Nodes (13): ConnectionState, CONNECTED, CONNECTING, DISCONNECTED, RECONNECTING, ByteArray, ReceiveChannel, MeshConfig (+5 more)

### Community 19 - "Intercom-Alpha — Project Overview"
Cohesion: 0.08
Nodes (23): 1. **Mesh Transport** (`MeshTransport`), 2. **Audio Engine** (`AudioEngine`), 3. **Headset Manager** (`HeadsetManager`), 4. **Group Manager** (`GroupManager`), Android, 🏗️ Architecture at a Glance, 🔑 Core Features, 🎯 Current Status (Alpha Scaffold) (+15 more)

### Community 21 - "IntercomSignalingClient"
Cohesion: 0.12
Nodes (12): Connected, Connecting, Disconnected, Error, IntercomSignalingClient, CoroutineScope, DefaultClientWebSocketSession, SharedFlow (+4 more)

### Community 24 - "MeshTransportImpl"
Cohesion: 0.07
Nodes (36): ByteArray, NSObject, MeshTransportImpl, NSObject, NSObject, NSObject, NSObject, NSObject (+28 more)

### Community 25 - "MeshTransport.android.kt"
Cohesion: 0.05
Nodes (40): advertisedata, AdvertiseSettings, BluetoothAdapter, ByteArray, ReceiveChannel, MeshTransportImpl, BluetoothGattCallback, AdvertiseCallback (+32 more)

### Community 26 - "SignalingServerManager"
Cohesion: 0.36
Nodes (3): PeerSession, SignalingMessage, SignalingServerManager

### Community 27 - "app/sharedLogic/src/androidMain/kotlin/org/nowni/intercom_alpha/Platform.android.kt"
Cohesion: 0.15
Nodes (15): AndroidPlatform, currentTimeMillis(), getPlatform(), Platform, randomUUID(), currentTimeMillis(), getPlatform(), JvmPlatform (+7 more)

### Community 28 - "app/sharedLogic/src/iosMain/kotlin/org/nowni/intercom_alpha/Platform.ios.kt"
Cohesion: 0.23
Nodes (10): getPlatform(), IOSPlatform, Platform, getPlatform(), IOSPlatform, Platform, nsdate, nsuuid (+2 more)

### Community 29 - "Intercom-Alpha/app/sharedLogic/src/jsMain/kotlin/org/nowni/intercom_alpha/Platform.js.kt"
Cohesion: 0.60
Nodes (4): getPlatform(), JsPlatform, Platform, navigator

### Community 30 - "AudioEngine.android.kt"
Cohesion: 0.11
Nodes (15): AudioEngineImpl, AudioPlayerAndroid, AudioRecorderAndroid, ByteArray, Job, ReceiveChannel, SendChannel, ShortArray (+7 more)

### Community 31 - "app/sharedLogic/src/jsMain/kotlin/org/nowni/intercom_alpha/Platform.js.kt"
Cohesion: 0.19
Nodes (8): getPlatform(), JsPlatform, Platform, getPlatform(), Platform, WasmPlatform, date, random

### Community 32 - "getPlatform"
Cohesion: 0.83
Nodes (3): getPlatform(), JVMPlatform, Platform

### Community 33 - "getPlatform"
Cohesion: 0.83
Nodes (3): getPlatform(), Platform, WasmPlatform

### Community 34 - "Intercom-Alpha/gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 40 - "GroupManager"
Cohesion: 0.22
Nodes (4): Group, GroupManager, InviteInfo, ReceiveChannel

### Community 41 - "app/androidApp/src/main/kotlin/org/nowni/intercom_alpha/MainActivity.kt"
Cohesion: 0.14
Nodes (16): activityresultcontracts, Bundle, ComponentActivity, MainActivity, composable, contextcompat, enableedgetoedge, AppAndroidPreview() (+8 more)

### Community 42 - "GroupManagerImpl"
Cohesion: 0.17
Nodes (6): Greeting, GroupManagerImpl, currentTimeMillis(), getPlatform(), Platform, randomUUID()

### Community 43 - "FakeMeshTransport"
Cohesion: 0.29
Nodes (3): FakeMeshTransport, ByteArray, ReceiveChannel

### Community 44 - "HeadsetManager.android.kt"
Cohesion: 0.11
Nodes (18): NSObject, ReceiveChannel, audiomanager, BluetoothHeadset, bluetoothmanager, bluetoothprofile, broadcastreceiver, context (+10 more)

### Community 52 - "PeerList.kt"
Cohesion: 0.25
Nodes (10): Peer, Modifier, PeerItem(), PeerList(), card, carddefaults, items, lazycolumn (+2 more)

### Community 53 - "HomeScreen.kt"
Cohesion: 0.10
Nodes (27): HomeScreen(), Modifier, AudioLevelMeter(), Modifier, Modifier, PttButton(), arrangement, background (+19 more)

### Community 54 - "SignalingServerManager"
Cohesion: 0.36
Nodes (3): PeerSession, SignalingMessage, SignalingServerManager

### Community 55 - "ControlType"
Cohesion: 0.22
Nodes (9): ControlType, JOIN_ACCEPT, JOIN_REJECT, JOIN_REQUEST, LEAVE, PING, PONG, SYNC_REQUEST (+1 more)

### Community 57 - "App"
Cohesion: 0.27
Nodes (7): AppAndroidPreview(), main(), main(), App(), application, main(), window

### Community 58 - "IntercomForegroundService.kt"
Cohesion: 0.14
Nodes (13): IntercomForegroundService, Context, IBinder, Intent, log, mainactivity, Notification, notificationchannel (+5 more)

### Community 59 - "MeshTransport.ios.kt"
Cohesion: 0.13
Nodes (14): addressof, ReceiveChannel, ReceiveChannel, assertnotnull, base64, channel, currenttimemillis, delay (+6 more)

### Community 60 - "Technical Notes & Architectural Gotchas"
Cohesion: 0.33
Nodes (5): 🎙️ Audio Pipeline & Codec, 📡 Cross-Platform GATT Protocol, 🎧 Headset Audio Routing, 🧠 Memory & Knowledge System, Technical Notes & Architectural Gotchas

### Community 61 - "Sprint Management Framework & Rules for Intercom-Alpha"
Cohesion: 0.20
Nodes (9): 🎯 1. Core Philosophy: Why We Do NOT Use Calendar Sprints, 🏗️ 2. Architectural Hierarchy, 📁 3. Multiple Sprint File Structure & Locations, 🟢 4. Sprint Creation Criteria (When to Start a New Sprint), 🔴 5. Definition of Done (DoD — When to Close a Sprint), 📊 6. Obsidian Kanban Plugin Integration, 🧠 7. MemPalace & Graphify Harmony, Sprint Document Template (+1 more)

### Community 62 - "Current Sprint: Sprint 04 — Swift ↔ KMP Bridge & QR Code Onboarding"
Cohesion: 0.40
Nodes (4): Current Sprint: Sprint 04 — Swift ↔ KMP Bridge & QR Code Onboarding, 🎯 Sprint 04 Goal, 📦 Sprint History & Archive, 📋 Task Matrix

### Community 63 - "IntercomBridge"
Cohesion: 0.24
Nodes (3): CancellationHandle, IntercomBridge, Job

### Community 64 - "IntercomViewModel"
Cohesion: 0.23
Nodes (5): IntercomViewModel, Bool, Float, String, ObservableObject

### Community 65 - "encodetostring"
Cohesion: 0.38
Nodes (7): concurrenthashmap, encodetostring, frame, json, mutex, readtext, withlock

### Community 66 - "SignalingServerManager"
Cohesion: 0.36
Nodes (3): PeerSession, SignalingMessage, SignalingServerManager

### Community 67 - ".body"
Cohesion: 0.20
Nodes (7): .body, PeerRowView, .body, PttButtonView, .body, Bool, Void

### Community 68 - "HeadsetManagerImpl"
Cohesion: 0.29
Nodes (3): HeadsetManagerImpl, BluetoothAdapter, ReceiveChannel

### Community 69 - "HeadsetManager"
Cohesion: 0.24
Nodes (3): HeadsetInfo, HeadsetManager, ReceiveChannel

### Community 70 - "Intercom-Alpha/server/src/main/kotlin/org/nowni/intercom_alpha/Application.kt"
Cohesion: 0.33
Nodes (4): consumeeach, peersession, seconds, signalingservermanager

### Community 71 - "AudioMeterView"
Cohesion: 0.28
Nodes (8): AudioMeterView, .body, QrShareView, .body, Float, String, Color, UIImage

### Community 72 - "HeadsetButtonEvent"
Cohesion: 0.22
Nodes (8): HeadsetButtonEvent, ANSWER_CALL, END_CALL, PTT_PRESS, PTT_RELEASE, VOICE_ASSISTANT, VOLUME_DOWN, VOLUME_UP

### Community 73 - "iOSApp"
Cohesion: 0.25
Nodes (7): App, iOSApp, .body, Scene, iOSApp, .body, Scene

### Community 74 - "app/iosApp/iosApp/ContentView.swift"
Cohesion: 0.32
Nodes (4): CoreImage.CIFilterBuiltins, Foundation, SharedLogic, SwiftUI

### Community 75 - "app/webApp/src/webMain/kotlin/org/nowni/intercom_alpha/main.kt"
Cohesion: 0.40
Nodes (4): main(), composeviewport, experimentalcomposeuiapi, main()

### Community 76 - "module"
Cohesion: 0.40
Nodes (3): module(), PeerSession, ApplicationTest

### Community 77 - "module"
Cohesion: 0.40
Nodes (3): module(), PeerSession, ApplicationTest

### Community 78 - "🔑 Core Features"
Cohesion: 0.40
Nodes (5): 1. **Mesh Transport** (`MeshTransport`), 2. **Audio Engine** (`AudioEngine`), 3. **Headset Manager** (`HeadsetManager`), 4. **Group Manager** (`GroupManager`), 🔑 Core Features

## Knowledge Gaps
- **155 isolated node(s):** `.body`, `Disconnected`, `Connecting`, `Error`, `Register` (+150 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 372 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **25 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Peer` connect `PeerList.kt` to `app/sharedUI/src/commonMain/kotlin/org/nowni/intercom_alpha/App.kt`, `GroupManager`, `GroupManagerImpl`, `FakeMeshTransport`, `IntercomBridge.kt`, `MeshTransportImpl`, `MeshTransport.android.kt`, `MeshTransport.ios.kt`, `App`?**
  _High betweenness centrality (0.212) - this node is a cross-community bridge._
- **Why does `AudioMeterView` connect `AudioMeterView` to `app/iosApp/iosApp/ContentView.swift`, `.body`, `View`?**
  _High betweenness centrality (0.147) - this node is a cross-community bridge._
- **Why does `AudioProfile` connect `AudioProfile` to `app/sharedUI/src/commonMain/kotlin/org/nowni/intercom_alpha/App.kt`, `FakeMeshTransport`, `SignalingMessage`, `IntercomBridge.kt`, `MeshTransportImpl`, `MeshTransport.android.kt`, `MeshTransport.ios.kt`, `AudioEngine.android.kt`, `IntercomBridge`?**
  _High betweenness centrality (0.138) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `MeshTransportImpl` (e.g. with `IntercomBridge` and `randomUUID()`) actually correct?**
  _`MeshTransportImpl` has 2 INFERRED edges - model-reasoned connections that need verification._
- **Are the 3 inferred relationships involving `IntercomBridge` (e.g. with `AudioEngineImpl` and `HeadsetManagerImpl`) actually correct?**
  _`IntercomBridge` has 3 INFERRED edges - model-reasoned connections that need verification._
- **Are the 2 inferred relationships involving `IntercomSignalingClient` (e.g. with `.testClientInitialState()` and `.testRoomStateFlow()`) actually correct?**
  _`IntercomSignalingClient` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `.body`, `Disconnected`, `Connecting` to the rest of the system?**
  _155 weakly-connected nodes found - possible documentation gaps or missing edges._