# Sprint 38 · Rider intercom product audit

27 November 2026 · Findings and proposed priorities

## Product direction

Intercom-Alpha lets a small group of riders start a private conversation before setting off and keep speaking naturally through supported phone audio devices when mobile data disappears. An Android rider and an iPhone rider should be able to join the same group with their existing gear. Push-to-talk belongs to a later optional mode. VOX may save airtime if testing shows it preserves natural turn-taking and the start of each phrase.

The founder independently conceived this use case, then found NearVox while looking for an app accessible in India or Asia. NearVox subsequently informed the product. India is the proposed first validation market. Country-by-country availability, device support and market demand need separate evidence. [Original planning chat](https://chatgpt.com/c/6aaf6f6e-226c-83e8-8112-b9db6fcd27ea)

## What NearVox teaches

NearVox describes a hosted local Wi-Fi network with a QR invitation and simultaneous group voice across Android and iPhone. The host's hotspot provides the local network and devices exchange voice directly over it. Bluetooth audio accessories are a separate connection between each rider and their own phone. NearVox advertises eight participants, automatic reconnect, spoken loss and battery cues, media ducking, and Pro host handover. A local Wi-Fi network with all participants connected still differs from multi-hop phone-to-phone forwarding. The public description establishes the intended experience. Recovery timing, range, audio quality and platform behavior require hands-on measurements. [NearVox product demo and FAQ](https://nearvox.de/en/)

Sena WAVE shows how to distinguish private friends groups from nearby discovery and how compatible hardware can provide a fallback. Its cellular voice path and hardware mesh backup belong to a different deployment model. The design lesson for Intercom-Alpha is to make group membership and degraded connectivity easy to understand. [Sena WAVE](https://www.sena.com/wave-intercom/)

[Companion market research](2026-11-27-Intercom-Market-Research.md) records other products, public demo and store evidence, and what remains unverified.

## Where the project drifted

| Sprint sequence or evidence | What changed | Product implication |
|---|---|---|
| 01–08 · Foundations and initial field work | Native clients, shared audio, QR, mesh and background operation established the core ingredients. | Preserve this work and retest the current build through the rider journey. |
| 09–17 · Relay, channels, convoy and cockpit | Cloud fallback, channel controls, telemetry and repeaters broadened the experience. The tactical cockpit became the main interaction model. | Supplemental features grew while the open group conversation was still missing from the primary experience. |
| 18–29 · Terrain, safety and fleet | Mapping, trail exchange, federation, satellite and fleet tooling added additional audiences and workflows. | Keep the engineering investment, rank it against the first rider release and prevent it from consuming the release gate. |
| 30–37 · Cockpit and network hardening | The UI redesign reinforced the PTT actuator, while bitrate, FEC, packing and congestion changes improved transport components. | Transport and simulation results need a real-device rider acceptance record. |
| 38 · Basecamp | The approved work is an operator console, monitoring, dispatch and fleet verification. | Keep the approved plan and story IDs visible. The council's recommendation is to prioritize a rider validation milestone before widening fleet scope. Roadmap sequencing remains an owner decision. |

[Sprint evidence inventory](2026-11-25-Sprints-01-38-CEO-Review.md) · [Sprint 38](../../SPRINT.md) · [Basecamp plan](../ICA-3801-PLAN.md)

### Native UX and behavior

- Both cockpits make the PTT shelf the primary action. Android's voice tab also foregrounds SOS, channels and hazard shortcuts. On iOS the QR host and join callbacks are wired to the conditional Convoy tab. The minimal profile's cockpit has no reachable QR flow through those callbacks. Put Start group and Join group on the primary entry screen and keep the active session one tap away.
- Android's cockpit derives a displayed remote speaker from the first peer and feeds the microphone input level into the remote meter when it is idle locally. It also clamps peer count to at least one. A session screen should use actual peer, speaking and route state so riders can distinguish waiting, active, muted and recovering.
- The reviewed Android foreground recording loop sends when transmitting is enabled by the PTT path. The iOS bridge checks isPttActive before encoding and sending microphone audio. A full-duplex audio abstraction therefore does not establish an open-mic group session. Define joined-and-unmuted transmission in the session contract and verify it on both platforms.
- Source review establishes these wiring and hierarchy issues. The audit did not run an installed build or visually verify screen layouts on a phone.

[Android cockpit](../../app/sharedUI/src/commonMain/kotlin/org/nowni/intercom_alpha/feature/home/MainCockpitScreen.kt) · [Android audio service](../../app/androidApp/src/main/kotlin/org/nowni/intercom_alpha/service/IntercomForegroundService.kt) · [iOS entry](../../app/iosApp/iosApp/ContentView.swift) · [iOS cockpit](../../app/iosApp/iosApp/CockpitView.swift) · [iOS bridge](../../app/sharedLogic/src/iosMain/kotlin/org/nowni/intercom_alpha/bridge/IntercomBridge.kt)

### Documentation repair map

| Source | Current conflict | Proposed responsibility |
|---|---|---|
| [MRD](../MRD.md) | Market framing mixes hardware, PTT, satellite and fleet. | Define the small-group rider segment, geography hypotheses, alternatives and evidence dates. |
| [BRD](../BRD.md) | The platform serves consumer and enterprise audiences at once. Targets such as zero dropped words are asserted without a field protocol in the BRD. | Own the rider business outcome, regional launch assumptions, success metrics and the secondary fleet opportunity. |
| [PRD](../PRD.md) | PTT is P0 and the vision mixes core voice with terrain and cellular fallback. | Make offline group conversation P0. Specify join, audio consent, mute, recovery, exit and the release evidence for each. |
| [Design spec](../DESIGN.md) | The common design is organized around an 84pt PTT actuator. | Own user flows, platform-native states, copy, accessibility and rider usability criteria. |
| [Architecture](../ARCHITECTURE.md), [roadmap](../ROADMAP.md) and [sprint tracker](../../SPRINT.md) | Engineering capability and release readiness are easy to conflate. | Architecture owns transport tradeoffs. The roadmap owns sequence. Sprint reports link each passed release criterion to device evidence and its limits. |

## Proposed rider experience

1. Before riding, choose Start group or Join group. The host shares an invite and sees who joined. Give a direct recovery action when a permission, camera or connection fails.
2. Show the selected microphone and listening route and provide a brief pre-ride audio check. Each rider explicitly joins the conversation with clear microphone state.
3. In the active group, show connection and microphone state first, then who is present and speaking. Keep mute and leave obvious, with text and accessibility labels. Use quiet, distinct headset cues for loss and recovery so the screen can stay locked.
4. Rejoin the same group after a temporary connection loss when possible. Preserve intentional mute across recovery. Surface when automatic recovery needs help.
5. Keep member details and diagnostics one level down. Use Android Jetpack Compose and native SwiftUI for iOS. Design original screen compositions, copy, icons, and visual identity from these tasks.

## Release evidence

Start with one Android and one iPhone, then a four-rider mixed group. Use ordinary Bluetooth audio gear from the target market. Record exact devices, OS, app build, host direction, route, distance and radio conditions. Use mobile data off with the necessary local radios enabled. Reproduce the matrix in a controlled stationary setup before a ride assessment with riders who do not operate screens while moving.

| Gate | Record |
|---|---|
| Create and join | Time from Start or Join to first intelligible two-way voice, join completion and failure cause. Exercise each platform as creator and joiner. |
| Natural conversation | Overlapping speech without a talk button, first-syllable intelligibility, latency and background noise. Log device and audio route. |
| Ride continuity | Lock both phones, interrupt navigation/audio, reconnect a headset and restore a lost peer. Measure recovery and identify any phone interaction required. |
| Trust and usability | Verify mute survives reconnect, leaving stops transmission, degraded state is understandable, and core flows work with screen readers and in bright light before departure. |
| Endurance and reach | Record battery drain and practical range across representative phones and environments. Publish observed conditions alongside every claim. |

The initial run establishes baselines. The council sets release thresholds from those measurements and records who approved them. Compare BLE mesh with a supported local Wi-Fi candidate only if the existing transport misses the rider criteria. Measure the same conditions in both cases. Retain KMP shared logic, native iOS UI and the existing core/server fallback architecture.

## Product and design council

This is a repository review workflow activated by this document. Roles are assigned per review. The project owner holds decision authority. GSD owns planning and verification, Ralph owns an approved implementation story, and OpenCodeReview retains its pre-commit role.

| Seat | Review responsibility | Evidence the seat brings |
|---|---|---|
| Rider and India market | Confirm the job, vocabulary, gear and regional access. | Rider observations and country-specific storefront/device checks. |
| Product | Keep the rider journey P0 and order the roadmap. | Requirement IDs, dependencies and the effect of any scope change. |
| Native UX | Own the primary journey, platform conventions, accessibility and originality. | Original Android and iOS flows, recovery-state walkthrough and usability outcomes. |
| Audio and connectivity | Review session and transport options. | Device traces for simultaneous voice, routing, reconnect, battery and practical range. |
| Field QA and release | Decide which product claims the evidence supports. | Reproducible device matrix, failed cases and release readiness record. |

At each milestone review, bring one decision, its evidence, and viable alternatives. For every competitor idea, record the rider problem it addresses, the general principle, the original Intercom-Alpha design response and the test of improvement. Keep competitor assets, wording and exact screen compositions outside the implementation. Compare join effort, intelligibility, recovery and compatibility using equivalent conditions. Log the decision, owner, unresolved evidence and affected requirement in the sprint record.

### First council review

| Decision | Disposition |
|---|---|
| Primary experience | Owner direction recorded. Offline, continuous group intercom for riders. Hold-to-talk is a later option. |
| First implementation milestone | Proposed. Validate the start/join → speak → mute → recover journey on mixed devices, then use the findings to revise the MRD, BRD, PRD and design spec. |
| Sprint 38 scope | Existing Basecamp approval recorded. Reordering the roadmap remains an owner decision. |
| Market and technical claims | Open. Confirm India storefront installation, record transport performance on physical devices, and establish measurable head-to-head criteria before claiming superiority. |

[Follow-up transport audit and complete app comparison](2026-11-27-Intercom-Transport-Comparison.md)
