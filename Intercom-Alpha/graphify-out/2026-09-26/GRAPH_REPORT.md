# Graph Report - Intercom-Alpha  (2026-09-25)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 116 nodes · 136 edges · 12 communities (7 shown, 5 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 10 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Platform
- assertequals
- App.kt
- MainActivity.kt
- ContentView
- Greeting
- experimentalwasmdsl
- gradlew
- desktopApp/build.gradle.kts

## God Nodes (most connected - your core abstractions)
1. `Platform` - 12 edges
2. `App()` - 6 edges
3. `ContentView` - 5 edges
4. `ContentView_Previews` - 4 edges
5. `iOSApp` - 4 edges
6. `Greeting` - 4 edges
7. `AndroidPlatform` - 3 edges
8. `IOSPlatform` - 3 edges
9. `JsPlatform` - 3 edges
10. `JVMPlatform` - 3 edges

## Surprising Connections (you probably didn't know these)
- `main()` --calls--> `App()`  [INFERRED]
  app/desktopApp/src/main/kotlin/org/nowni/intercom_alpha/main.kt → app/sharedUI/src/commonMain/kotlin/org/nowni/intercom_alpha/App.kt
- `module()` --calls--> `sayHello()`  [INFERRED]
  server/src/main/kotlin/org/nowni/intercom_alpha/Application.kt → core/src/commonMain/kotlin/org/nowni/intercom_alpha/GreetingUtil.kt
- `.body` --calls--> `ContentView`  [INFERRED]
  app/iosApp/iosApp/iOSApp.swift → app/iosApp/iosApp/ContentView.swift
- `Greeting` --calls--> `getPlatform()`  [INFERRED]
  app/sharedLogic/src/commonMain/kotlin/org/nowni/intercom_alpha/Greeting.kt → app/sharedLogic/src/commonMain/kotlin/org/nowni/intercom_alpha/Platform.kt
- `App()` --calls--> `Greeting`  [INFERRED]
  app/sharedUI/src/commonMain/kotlin/org/nowni/intercom_alpha/App.kt → app/sharedLogic/src/commonMain/kotlin/org/nowni/intercom_alpha/Greeting.kt

## Import Cycles
- None detected.

## Communities (12 total, 5 thin omitted)

### Community 0 - "Platform"
Cohesion: 0.15
Nodes (15): AndroidPlatform, getPlatform(), getPlatform(), Platform, getPlatform(), IOSPlatform, getPlatform(), JsPlatform (+7 more)

### Community 1 - "assertequals"
Cohesion: 0.13
Nodes (8): SharedLogicAndroidHostTest, SharedLogicCommonTest, SharedLogicIOSTest, SharedLogicDesktopTest, SharedLogicWebTest, SharedUICommonTest, assertequals, test

### Community 2 - "App.kt"
Cohesion: 0.12
Nodes (15): alignment, animatedvisibility, background, button, column, compose_multiplatform, fillmaxsize, fillmaxwidth (+7 more)

### Community 3 - "MainActivity.kt"
Cohesion: 0.16
Nodes (12): AppAndroidPreview(), MainActivity, App(), main(), Bundle, ComponentActivity, composable, composeviewport (+4 more)

### Community 4 - "ContentView"
Cohesion: 0.18
Nodes (12): App, ContentView, .body, ContentView_Previews, .previews, iOSApp, .body, PreviewProvider (+4 more)

### Community 5 - "Greeting"
Cohesion: 0.14
Nodes (6): main(), Greeting, sayHello(), module(), ApplicationTest, window

### Community 7 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **2 isolated node(s):** `.body`, `SharedLogic`
  These have ≤1 connection - possible missing edges. (Counts symbols only; 46 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **5 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `App()` connect `MainActivity.kt` to `App.kt`, `Greeting`?**
  _High betweenness centrality (0.198) - this node is a cross-community bridge._
- **Why does `Greeting` connect `Greeting` to `Platform`, `MainActivity.kt`?**
  _High betweenness centrality (0.172) - this node is a cross-community bridge._
- **Are the 5 inferred relationships involving `App()` (e.g. with `AppAndroidPreview()` and `.onCreate()`) actually correct?**
  _`App()` has 5 INFERRED edges - model-reasoned connections that need verification._
- **What connects `.body`, `SharedLogic` to the rest of the system?**
  _2 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Platform` be split into smaller, more focused modules?**
  _Cohesion score 0.14761904761904762 - nodes in this community are weakly interconnected._
- **Should `assertequals` be split into smaller, more focused modules?**
  _Cohesion score 0.12631578947368421 - nodes in this community are weakly interconnected._
- **Should `App.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.125 - nodes in this community are weakly interconnected._