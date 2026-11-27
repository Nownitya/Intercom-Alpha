# Intercom-Alpha

[Rider plan](docs/reports/2026-11-27-Rider-Intercom-Council-Plan.md) and [native design brief](docs/ICA-4001-PLAN.md)

Intercom-Alpha is an offline-first voice intercom for riders using Android and iPhone. The goal is to start or join one private group before departure and talk naturally over phone-compatible headsets, with screens locked and mobile data off whenever a local route is available.

[GitHub releases](https://github.com/Nownitya/Intercom-Alpha/releases) · [Architecture](docs/ARCHITECTURE.md) · [Contributing](CONTRIBUTING.md) · [Apache 2.0](LICENSE)

## What the app does

- **Group voice.** The current mobile clients offer QR group setup and push-to-talk controls. Continuous hands-free conversation is the rider-first direction. PTT is a secondary mode.
- **Local connectivity.** Both mobile platforms have BLE GATT transport. iOS also has an Apple-only Multipeer Connectivity path. The planned hybrid topology combines direct links, rider-to-rider relaying and compatible local Wi-Fi where device tests support it.
- **Ride tools.** The codebase includes convoy telemetry, offline trail tools, a stationary repeater and guarded SOS. Availability and device verification vary by platform.

## Project status

Android uses Compose and iOS uses native SwiftUI. Kotlin Multiplatform shares session, packet and audio logic. The repository also includes desktop and web clients and Ktor relay components.

The first rider milestone is two-way hands-free Android–iPhone audio, with both phones locked and mobile data disabled. The proposed progression covers three-phone relaying, alternate-route recovery, and mixed-device field trials with 10 and 20 riders. Automatic admin succession, mixed-platform Wi-Fi voice and live multi-hop at those group sizes still need implementation or field verification. Basecamp work is tracked separately.

See the [sprint tracker](SPRINT.md) for current branch work and [GitHub releases](https://github.com/Nownitya/Intercom-Alpha/releases) for published builds.

## Build from source

Use **JDK 21** and the repository's Gradle wrapper. For Android, install Android Studio and the SDK configured in [the version catalog](gradle/libs.versions.toml). For iOS, use macOS with Xcode and an iOS simulator that meets the deployment target in the Xcode project.

### Android

Open the repository root in Android Studio and run the `androidApp` configuration on a phone or emulator. To build a debug APK from the terminal:

```sh
./gradlew :app:androidApp:assembleDebug
```

On Windows PowerShell, use `.\gradlew.bat :app:androidApp:assembleDebug`. Use physical phones to evaluate BLE voice, range, and headset behavior.

### iOS

Open [the iOS Xcode project](app/iosApp/iosApp.xcodeproj/project.pbxproj), select the `iosApp` scheme, and run it on a simulator or a configured device. Xcode's build phase invokes Gradle to build and embed the shared Kotlin framework.

### Tests

```sh
./gradlew test
```

On a Mac, run the shared iOS compilation check separately:

```sh
./gradlew :app:sharedLogic:compileKotlinIosSimulatorArm64
```

See [the contribution guide](CONTRIBUTING.md) for branch rules and review gates.

## Explore the project

| If you want to… | Start here |
| --- | --- |
| Understand the system boundaries and mobile bridges | [System architecture](docs/ARCHITECTURE.md) |
| See the app journeys, engineering flows, and planned improvements | [Visual project atlas](docs/diagrams/README.md) |
| Understand the product goals and capabilities | [Product requirements](docs/PRD.md) and [feature catalog](docs/FEATURES.md) |
| Follow current work | [Sprint tracker](SPRINT.md) and [Ralph story tracker](tasks/ralph/prd.json) |

## Support and contributions

Open a [GitHub issue](https://github.com/Nownitya/Intercom-Alpha/issues) for bugs and feature requests. For a mobile bug, include the app version, phone models, OS versions, and whether mobile data was enabled, the transport in use, and whether the screens were locked.

Read [CONTRIBUTING.md](CONTRIBUTING.md) before proposing changes. Report vulnerabilities using [SECURITY.md](SECURITY.md). The project is licensed under [Apache 2.0](LICENSE).
