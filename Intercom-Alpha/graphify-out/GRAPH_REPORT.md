# Graph Report - Intercom-Alpha  (2026-09-26)

## Corpus Check
- 43 files · ~7,433 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 21 file(s) not represented in the graph (top: .xml 8, .properties 3, (none) 1)

## Summary
- 196 nodes · 259 edges · 17 communities (10 shown, 7 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 13 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Platform
- assertequals
- App.kt
- MainActivity.kt
- ContentView
- Application.kt
- experimentalwasmdsl
- gradlew
- desktopApp/build.gradle.kts
- IntercomSignalingClient
- SignalingServerManager.kt
- SignalingMessage
- SignalingServerManager
- README.md

## God Nodes (most connected - your core abstractions)
1. `SignalingMessage` - 22 edges
2. `IntercomSignalingClient` - 19 edges
3. `Platform` - 12 edges
4. `SignalingServerManager` - 11 edges
5. `SignalingConnectionState` - 6 edges
6. `App()` - 6 edges
7. `PeerInfo` - 6 edges
8. `module()` - 6 edges
9. `ContentView` - 5 edges
10. `ContentView_Previews` - 4 edges

## Surprising Connections (you probably didn't know these)
- `module()` --calls--> `sayHello()`  [INFERRED]
  server/src/main/kotlin/org/nowni/intercom_alpha/Application.kt → core/src/commonMain/kotlin/org/nowni/intercom_alpha/GreetingUtil.kt
- `IntercomSignalingClient` --references--> `SignalingMessage`  [EXTRACTED]
  app/sharedLogic/src/commonMain/kotlin/org/nowni/intercom_alpha/signaling/IntercomSignalingClient.kt → core/src/commonMain/kotlin/org/nowni/intercom_alpha/protocol/SignalingProtocol.kt
- `AppAndroidPreview()` --calls--> `App()`  [INFERRED]
  app/androidApp/src/main/kotlin/org/nowni/intercom_alpha/MainActivity.kt → app/sharedUI/src/commonMain/kotlin/org/nowni/intercom_alpha/App.kt
- `main()` --calls--> `App()`  [INFERRED]
  app/desktopApp/src/main/kotlin/org/nowni/intercom_alpha/main.kt → app/sharedUI/src/commonMain/kotlin/org/nowni/intercom_alpha/App.kt
- `.body` --calls--> `ContentView`  [INFERRED]
  app/iosApp/iosApp/iOSApp.swift → app/iosApp/iosApp/ContentView.swift

## Import Cycles
- None detected.

## Communities (17 total, 7 thin omitted)

### Community 0 - "Platform"
Cohesion: 0.15
Nodes (15): AndroidPlatform, getPlatform(), getPlatform(), Platform, getPlatform(), IOSPlatform, getPlatform(), JsPlatform (+7 more)

### Community 1 - "assertequals"
Cohesion: 0.11
Nodes (10): SharedLogicAndroidHostTest, SharedLogicCommonTest, SharedLogicIOSTest, SharedLogicDesktopTest, SharedLogicWebTest, SharedUICommonTest, assertequals, first (+2 more)

### Community 2 - "App.kt"
Cohesion: 0.12
Nodes (15): alignment, animatedvisibility, background, button, column, compose_multiplatform, fillmaxsize, fillmaxwidth (+7 more)

### Community 3 - "MainActivity.kt"
Cohesion: 0.10
Nodes (16): AppAndroidPreview(), MainActivity, main(), Greeting, App(), main(), Bundle, ComponentActivity (+8 more)

### Community 4 - "ContentView"
Cohesion: 0.18
Nodes (12): App, ContentView, .body, ContentView_Previews, .previews, iOSApp, .body, PreviewProvider (+4 more)

### Community 5 - "Application.kt"
Cohesion: 0.15
Nodes (10): consumeeach, PeerInfo, defaultwebsocketsession, polymorphic, seconds, serializable, serialname, module() (+2 more)

### Community 7 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 12 - "IntercomSignalingClient"
Cohesion: 0.13
Nodes (11): Connected, Connecting, Disconnected, Error, IntercomSignalingClient, SignalingConnectionState, IntercomSignalingClientTest, CoroutineScope (+3 more)

### Community 13 - "SignalingServerManager.kt"
Cohesion: 0.15
Nodes (9): asserttrue, concurrenthashmap, SignalingProtocolTest, encodetostring, frame, json, mutex, readtext (+1 more)

### Community 14 - "SignalingMessage"
Cohesion: 0.13
Nodes (15): Answer, AudioPing, AudioPong, ErrorMessage, IceCandidate, JoinRoom, LeaveRoom, Offer (+7 more)

## Knowledge Gaps
- **21 isolated node(s):** `SharedLogic`, `.body`, `Disconnected`, `Connecting`, `Error` (+16 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 87 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **7 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `PeerInfo` connect `Application.kt` to `SignalingServerManager.kt`?**
  _High betweenness centrality (0.260) - this node is a cross-community bridge._
- **Why does `SignalingMessage` connect `SignalingMessage` to `assertequals`, `Application.kt`, `IntercomSignalingClient`, `SignalingServerManager.kt`, `SignalingServerManager`?**
  _High betweenness centrality (0.241) - this node is a cross-community bridge._
- **Why does `App()` connect `MainActivity.kt` to `App.kt`?**
  _High betweenness centrality (0.229) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `IntercomSignalingClient` (e.g. with `.testClientInitialState()` and `.testRoomStateFlow()`) actually correct?**
  _`IntercomSignalingClient` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `SharedLogic`, `.body`, `Disconnected` to the rest of the system?**
  _21 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Platform` be split into smaller, more focused modules?**
  _Cohesion score 0.14761904761904762 - nodes in this community are weakly interconnected._
- **Should `assertequals` be split into smaller, more focused modules?**
  _Cohesion score 0.11067193675889328 - nodes in this community are weakly interconnected._