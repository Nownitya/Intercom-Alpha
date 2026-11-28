# Intercom-Alpha · Sprints 1–38 CEO review

**Date:** 2026-11-25  
**Review posture:** Hold scope, strategy and evidence audit.  
**Comparison base:** local `develop`. The checked-out Sprint 37 closeout, `0201dd0`, is 26 commits ahead. The worktree includes uncommitted Sprint 38 planning and prototypes. One older Sprint 31 stash exists. The planned scope and current source were reviewed. No new product scope was approved in this audit.

## Verdict

The product promise is phone-only group intercom. The first release question is whether a rider can join offline, talk and hear normally, mute, and recover when a peer or headset drops. The roadmap and sprint records show substantial work in routing, audio resilience, cockpit UI, and fleet capabilities. Reports and simulations establish evidence for the conditions they describe. Mixed-device ride behavior and production rollout require separate evidence.

Sprints 1–37 are recorded as complete locally in the roadmap. The strength and location of evidence varies. Sprint 38 is **open**. ICA-3801 has design approval and a plan. Fleet monitoring, dispatch/export, and the three-squad verification remain planned.

## Sprint inventory

Task headings identify each sprint. A ref is a local branch name. An ICA commit is a matching commit subject reachable from a local ref, including historical refs. Neither field independently proves all stories were merged or verified. A blank dedicated report field means no sprint-numbered report was found in `docs/reports`. Milestone reports, the journal and Git can also carry evidence. Obsidian status is **unverified for every row**.

| Sprint | Repository task record | Local sprint ref | ICA commit example | Dedicated report |
|---|---|---|---|---|
| 01 | Core Networking & Signaling Protocol ([TASKS.md:12](TASKS.md)) | — | — | — |
| 02 | Android Platform Engine & Compose UI ([TASKS.md:20](TASKS.md)) | — | — | — |
| 03 | iOS Native Audio, CoreBluetooth Mesh & Cross-Platform GATT ([TASKS.md:28](TASKS.md)) | — | — | — |
| 04 | Swift ↔ KMP Bridge & QR Code Onboarding ([TASKS.md:37](TASKS.md)) | — | — | — |
| 05 | Mesh Flooding Relay, Deduplication & Encryption ([TASKS.md:45](TASKS.md)) | — | — | — |
| 06 | Background Services & OS Power Management ([TASKS.md:59](TASKS.md)) | — | `8ec25a6` | [Sprint-06-Outcome-Report.md](reports/Sprint-06-Outcome-Report.md) |
| 07 | Audio Codec Optimization, Jitter Buffering & Packet Loss Concealment (PLC) ([TASKS.md:70](TASKS.md)) | — | `a71b2e0` | [Sprint-07-Outcome-Report.md](reports/Sprint-07-Outcome-Report.md) |
| 08 | Real-World Field Testing & Hardening ([TASKS.md:83](TASKS.md)) | `sprint/sprint-08-field-testing` | `0eacda7` | [Sprint-08-Outcome-Report.md](reports/Sprint-08-Outcome-Report.md) |
| 09 | Cellular Fallback & Cloud Relay Bridge ([TASKS.md:96](TASKS.md)) | `sprint/sprint-09-cellular-cloud-relay` | `c9f0c92` | — |
| 10 | Multi-Channel Intercom, Priority Emergency & Audio Spatialization ([TASKS.md:109](TASKS.md)) | `sprint/sprint-10-multi-channel-spatial-audio` | `1149768` | — |
| 11 | Convoy Situational Awareness, GPS Mesh Telemetry & Blackbox Voice Cache ([TASKS.md:123](TASKS.md)) | `sprint/sprint-11-convoy-gps-blackbox` | `9ab33d8` | — |
| 12 | Multiplatform Cockpit UI & Bridge Integration ([TASKS.md:137](TASKS.md)) | `sprint/sprint-12-cockpit-ui-integration` | `dd5521e` | — |
| 13 | v2.0 Production Release & Hardening ([TASKS.md:150](TASKS.md)) | `sprint/sprint-13-v2-production-release` | `7ba3ce5` | — |
| 14 | Advanced Convoy Mesh, Forward-Secret Key Ratchet & Live Activity ([TASKS.md:164](TASKS.md)) | — | `faa8d31` | — |
| 15 | Zero-Scroll Tactical Cockpit HUD & Hardware Telemetry Integration ([TASKS.md:178](TASKS.md)) | `sprint/sprint-15-cockpit-hud-integration` | — | — |
| 16 | Mesh Repeater, Audio DSP & Convoy Simulation ([TASKS.md:192](TASKS.md)) | `sprint/sprint-16-repeater-dsp-simulation` | `90d8bea` | [Sprint-16-Outcome-Report.md](reports/Sprint-16-Outcome-Report.md) |
| 17 | Basecamp Stationary Repeater UI & Low-Power Cockpit Mode ([TASKS.md:206](TASKS.md)) | `sprint/sprint-17-basecamp-repeater-ui` | `b3ba3e2` | — |
| 18 | Offline Vector Terrain & Trail Map Overlay ([TASKS.md:220](TASKS.md)) | `sprint/sprint-18-offline-vector-terrain-map` | `8aa2dec` | — |
| 19 | Over-The-Air BLE Mesh GPX/GeoJSON Trail Sharing & Storage Manager ([TASKS.md:234](TASKS.md)) | `sprint/sprint-19-mesh-trail-sharing` | `f491c7c` | — |
| 20 | Tactical Convoy Geofencing, Autonomous Power Governor & Multi-Node Stress Benchmark Suite ([TASKS.md:248](TASKS.md)) | `sprint/sprint-20-geofence-power-stress` | `8165391` | — |
| 21 | Autonomous Crash Impact Detection, eCall SOS & Offline Voice Command HUD ([TASKS.md:262](TASKS.md)) | `sprint/sprint-21-crash-detection-voice-commands` | `a8eadaa` | — |
| 22 | Dynamic Mesh Directed Routing, AODV-Lite Route Optimization & RF Collision Reduction ([TASKS.md:276](TASKS.md)) | `sprint/sprint-22-mesh-directed-routing` | `949c555` | [Sprint-22-Outcome-Report.md](reports/Sprint-22-Outcome-Report.md) |
| 23 | Multi-Group Inter-Convoy Gateway & Cross-Mesh Federation ([TASKS.md:291](TASKS.md)) | `sprint/sprint-23-multi-group-gateway` | `d4e8e2d` | — |
| 24 | Mesh Airtime QoS Traffic Shaping & Airtime Contention Mitigator ([TASKS.md:306](TASKS.md)) | — | `d7242b0` | [Sprint-24-Outcome-Report.md](reports/Sprint-24-Outcome-Report.md) |
| 25 | Automated Multi-Path Mesh Failover & Hybrid Cellular/SFU Relay Handover ([TASKS.md:321](TASKS.md)) | `sprint/sprint-25-multipath-mesh-failover` | `b2328ac` | [Sprint-25-Outcome-Report.md](reports/Sprint-25-Outcome-Report.md) |
| 26 | Long-Haul Fleet Hardening & Low-Bandwidth Visual Telemetry ([TASKS.md:336](TASKS.md)) | `sprint/sprint-26-fleet-hardening` | `f7b0e13` | — |
| 27 | Alpine Extreme Reach & Satellite Gateway Integration ([TASKS.md:357](TASKS.md)) | `sprint/sprint-27-satellite-alpine-gateway` | `d882d8b` | — |
| 28 | Edge Acoustic Intelligence & Mesh Airtime Congestion Governance ([TASKS.md:378](TASKS.md)) | `sprint/sprint-28-edge-acoustic-congestion` | `60a6578` | — |
| 29 | Unified Hardware Bridge Integration & System Certification ([TASKS.md:399](TASKS.md)) | `sprint/sprint-29-unified-bridge-certification` | `8dd86b8` | — |
| 30 | Dedicated UI/UX Design System Foundations ([TASKS.md:420](TASKS.md)) | `sprint/sprint-30-design-system-foundations` | `116aa89` | — |
| 31 | Zero-Scroll Tactical Cockpit Redesign ([TASKS.md:441](TASKS.md)) | `sprint/sprint-31-tactical-cockpit-redesign` | `4b19344` | — |
| 32 | Tactile Sub-Screens & Secondary Overlays Polish ([TASKS.md:462](TASKS.md)) | `sprint/sprint-32-tactile-subscreens` | `9bb5a4c` | — |
| 33 | Dynamic Adaptive Mesh Bitrate Tuning ([TASKS.md:483](TASKS.md)) | `sprint/sprint-33-adaptive-mesh-bitrate` | `d036847` | — |
| 34 | Forward Error Correction (FEC) & Packet Loss Concealment (PLC) Hardening ([TASKS.md:504](TASKS.md)) | `sprint/sprint-34-fec-and-plc` | `e2d0cf1` | [2026-10-18-Sprint-34-Closeout-Report.md](reports/2026-10-18-Sprint-34-Closeout-Report.md) |
| 35 | Ultra-Low Latency BLE MTU Frame Packing & Interleaving ([TASKS.md:524](TASKS.md)) | `sprint/sprint-35-ble-mtu-packing` | `3475b12` | [2026-10-18-Sprint-35-Closeout-Report.md](reports/2026-10-18-Sprint-35-Closeout-Report.md) |
| 36 | Offline Vector Topographic & Trail Elevation Profile HUD ([TASKS.md:545](TASKS.md)) | `sprint/sprint-36-vector-trail-elevation-hud` | `2f970f3` | [2026-10-18-Sprint-36-Closeout-Report.md](reports/2026-10-18-Sprint-36-Closeout-Report.md) |
| 37 | Fleet congestion control and adaptive PHY ([TASKS.md:570](TASKS.md)) | `sprint/sprint-37-fleet-congestion-control` | `0201dd0` | — |
| 38 | Basecamp dispatch and fleet telemetry ([TASKS.md:583](TASKS.md)) | — | — | — |

## What already exists

- **1–8:** Signaling, Android and iOS foundations, QR onboarding, encrypted BLE mesh, background operation, audio resilience and initial field work.
- **9–13:** Cellular relay, channel and priority controls, GPS convoy features, cockpit integration, release hardening.
- **14–25:** Stationary repeater, terrain and trail tools, safety flows, dynamic routing, federation, airtime QoS and failover.
- **26–29:** Fleet telemetry, alternate radios and satellite work, acoustic tools and bridge simulation.
- **30–37:** Cockpit redesign, bitrate and loss recovery, BLE packet packing, elevation, congestion and adaptive PHY. Sprint 37 has four passing Ralph entries at the local closeout.
- **38:** Approved Basecamp direction and plan. Existing on-phone repeater code has telemetry and ping/pong. Desktop and web entry points open the shared application. The operator-to-phone connection remains to be demonstrated.

Sources: [Roadmap](ROADMAP.md), [task inventory](TASKS.md), [current sprint](../SPRINT.md), [architecture](ARCHITECTURE.md), [story tracker](../tasks/ralph/prd.json), [project journal](../JOURNAL.md).

## Eleven review lenses

| Lens | Finding and release evidence needed |
|---|---|
| Architecture | Reuse shared mesh and repeater logic. Keep Android Compose and iOS SwiftUI native. Treat operator connection, rider membership and mic state as separate. |
| Errors and recovery | The first release needs explicit behavior for Bluetooth disabled, missing permission, no peers, and interrupted audio. See the failure map below. |
| Security and permissions | Verify QR group admission, microphone and location consent, and incident export access. Keep keys and private incident data out of vault evidence. |
| Data and interaction | Show last-seen time when telemetry is stale. Repeated Join, Link Check or Retry actions should never duplicate requests or misreport success. Test navigation during a reconnect. |
| Code quality | The existing repeater ViewModel defaults to a no-op bridge and operates a local node. A console needs real connection events before showing a live remote connection. |
| Testing | Use simulation to exercise traffic and loss. Record mixed Android/iOS device evidence of simultaneous speech, mute, background/lock-screen recovery and headset routing. For Basecamp, use an Android phone in stationary relay mode. |
| Performance | Separate graph and simulator results from observed radio range, audible latency, battery drain, thermal behavior, and iOS background limits. |
| Observability | Show connection and audio route on the phone, and last-seen, forwarding and diagnostic outcome in Basecamp. Keep radio detail in diagnostics. |
| Rollout | Reconcile release version and integration branch state. The roadmap header says beta1, while its Sprint 37 entry targets beta2. Closeout commit, integration, tag and shipped package each need independent evidence. |
| Long-term trajectory | Preserve a phone-only default. A stationary phone and Basecamp console are optional. Keep fleet dispatch and export behind their existing acceptance criteria. |
| Design and UX | The design contract still puts PTT first, while the user has specified an ongoing group intercom. Resolve that in native requirements before sign-off. Use Material bottom navigation on Android and SwiftUI tabs on iOS. Browser previews are design evidence. |

## Error and rescue registry

| Trigger | Rider or operator outcome | Verification |
|---|---|---|
| Bluetooth or mic permission denied | Explain the missing permission and provide a recovery route. | Deny, reopen and join after granting. |
| Peer or headset lost mid-ride | Show reconnecting or unavailable. Indicate the actual audio route and preserve mute through recovery. | Drop Bluetooth or headset mid-talk and verify state on both platforms. |
| Phone relay or diagnostic reply disappears | Show stale metrics and a timed-out link check with retry. | Disconnect during a ping, reconnect, then confirm a fresh reply and no stale success. |
| Case has no groups, peers or telemetry | Show an empty state with a meaningful next action. | Open fresh and offline, then import or join and confirm live data replaces the empty state. |
| Dispatch or incident export fails | Give an explicit outcome and allow a safe retry without duplicate dispatch. | Verify timeout, partial failure, permissions and repeat action before ICA-3803 closes. |

## Product and verification flow

```text
Rider headset <-> rider phone <-> nearby peer phones <-> group
                         optional BLE hop -> stationary phone -> Basecamp view

Repo task -> implementation ref -> build and simulation evidence
                                -> device evidence -> release claim
                                -> Obsidian sprint or milestone link
```

**Dream state:** Today the repo contains many advanced capabilities and mixed-strength release evidence. Sprint 38 adds an operator console over the existing phone repeater. The longer-term target is a rider who can join offline with a phone, speak normally, and understand degraded conditions while an optional operator can monitor and recover the same network.

## Obsidian audit

The exact Intercom-Alpha folder recorded by [NOTES.md](../NOTES.md) and [JOURNAL.md](../JOURNAL.md) could not be opened in this session. No Intercom-Alpha folder was found at the exact project subpaths checked against the local Obsidian registry. Repository references to Roadmap, Kanban and MEMORY establish the intended destinations. They do not verify vault contents. No vault changes were made.

| What should be present or linked | What to verify |
|---|---|
| Project hub and Roadmap | All 38 sprint IDs, milestone and phase links, current status and version provenance. |
| Kanban and sprint history | Sprint status, story IDs, acceptance criteria, owner and next action. Leave Sprint 38 open. |
| Plans and design decisions | Native UI separation, ongoing intercom vs optional PTT/VOX, phone-only product constraint, and optional Basecamp console. Link authoritative repo decisions and record superseded guidance. |
| Closeouts and verification | Sprint outcomes, commit or branch references, Gradle/review evidence, and device vs simulation distinction. Link reports rather than duplicating generated artifacts. |
| Incident and field evidence | Redacted test summary, devices, OS/build, environment and open field checks. Keep sensitive raw material under its established access controls. |
| Knowledge base and indexes | MEMORY patterns, project glossary and durable links. Ensure hub links resolve, Sprint 38 points to its current plan, and historical summaries remain retrievable. |

All 38 vault rows remain **unverified**, with zero classified as present or missing. Once the current Intercom-Alpha folder is supplied, compare the inventory by sprint ID across pages and milestone indexes. Mark each artifact present, linked, stale, missing, or not applicable. A standalone note per sprint is unnecessary when its canonical evidence is linked and discoverable.

## Decision ledger

| Decision | Status | Basis |
|---|---|---|
| Phone-only rider experience and optional Basecamp | Approved | User's product clarification and [ICA-3801 plan](ICA-3801-PLAN.md). |
| Open intercom direction | Approved for mockup | User correction to the PTT-first mockup. Native design contract and field proof remain follow-up work. |
| Obsidian completeness | Unresolved | Current Intercom-Alpha vault folder is unavailable. |
| New product scope | No decision requested | This is a review of existing work. |

## Out of scope

Code implementation, vault edits, branch changes, tagging, and release claims based on this review alone.

## GSTACK REVIEW REPORT

| Runs | Status | Findings |
|---|---|---|
| Git and repository audit | Completed | Sprint records found for 1–38. Sprint 38 remains open. |
| Product and evidence review | Completed | Native open-intercom behavior, field testing, and release version provenance need follow-through. |
| Obsidian cross-check | Blocked | The recorded project path is unavailable. |

**Verdict:** Repository review recorded. Vault completeness and production readiness remain unverified. This is a retrospective report. No implementation tasks or releases were approved by it.

**UNRESOLVED DECISIONS:**

- Locate the current Intercom-Alpha Obsidian project folder for the sprint-by-sprint comparison.
