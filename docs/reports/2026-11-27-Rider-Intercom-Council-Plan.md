# Rider intercom · Council proposal

**Status:** Proposed · **Council review:** 27 November 2026

## Product milestone

A mixed Android and iPhone group starts one private, hands-free conversation before departure. Riders can keep talking with phones locked and mobile data off when a local route exists. The app takes care of links, forwarding and recovery. Push-to-talk is optional follow-on scope.

The council can rank scope, set thresholds from device measurements, decide routine UX and transport trade-offs within approved scope, defer failed candidates, and withhold a phase exit recommendation when evidence is missing. The owner approves native mockups, roadmap sequencing and release scope. GSD owns planning and verification, Ralph owns atomic stories, and OpenCodeReview owns the pre-commit review.

## Approaches reviewed

| Approach | Trade-off | Council recommendation |
| --- | --- | --- |
| Shared hotspot first | Gives mixed phones a common network and a useful comparison with NearVox. A host going away can interrupt the group. | Evaluate after the baseline. |
| BLE only | Reuses both native transports and isolates mixed-device issues. Capacity and background recovery could limit rider count or spacing. | Use for the first field milestone. |
| Hybrid local topology | Direct links, selected rider relays and validated Wi-Fi segments can coexist in one session. Concurrent links and cross-transport forwarding need independent proof. | Chosen product direction. Add links incrementally. |

## What we have and what belongs in scope

| Area | Existing assets | Must have for the rider experience | Should or could follow |
| --- | --- | --- | --- |
| Entry and native UI | Compose and SwiftUI, QR group setup, tactical cockpit and peer views. | Start or Join up front, consent and an audio check, actual roster and microphone state, clear mute and leave, reachable/reconnecting/split states. Verify TalkBack, VoiceOver, bright-light readability and permission recovery. | Optional audio cues, invite alternatives and a detailed diagnostics panel. |
| Session and audio | Native capture and playback, headset integration, audio profiles, packet-loss components and PTT controls. | Simultaneous audio without a press, locked-screen continuity, headset and interruption recovery, intentional mute preserved, no unintended audio output or transmission after leaving. | Media ducking and optional VOX after wind tests, then optional PTT. |
| Local network | Android/iOS BLE GATT, Apple-only Multipeer, relay/repeater code, deduplication and PHY/congestion work. | Mixed-device direct speech, normal rider relay, alternate-route recovery, partition/rejoin and a reliable 10-rider gate before scaling. | Shared LAN/hotspot and bridge across verified transports. Wi-Fi Aware, Nearby and platform-specific links are candidates. Validate 20 riders before claiming support. |
| Membership and privacy | QR group setup, shared cipher and group components, local leadership changes and election tests. | Authenticate membership and verify encrypted voice end to end. Support multiple admins and agreed join-order succession when the final admin intentionally leaves. Reject stale invites and preserve authority through split/rejoin. | Additional invitation controls after core behavior is proven. |
| Market and release | Competitor research, test harnesses and Sprint 39 release checklist. | India device/headset coverage, original native design, recorded field evidence and aligned MRD, BRD, PRD and DESIGN. | Wider Asia device coverage, extended-range and larger group claims as evidence permits. |
| Operations and other modes | Basecamp plan, maps, elevation, safety and convoy tooling. Core/server fallback components. | Keep the rider session independent of optional infrastructure. | Basecamp and fleet remain their own workstream. Consider cellular fallback, trails and other secondary capabilities on their own evidence. |

Existing modules and simulation results establish starting points. Physical device checks determine which features meet the rider criteria. [Product audit](2026-11-27-Rider-Intercom-Product-Audit.md) · [Transport audit and scenarios](2026-11-27-Intercom-Transport-Comparison.md) · [Market research](2026-11-27-Intercom-Market-Research.md)

## Sprint planning

The tables use the existing Sprint → Phase → Story hierarchy. Candidate numbers start at 40 because 38 and 39 already have ICA phases. Existing IDs, branch plans, GSD references and Ralph records are authoritative. Candidate IDs and GSD references become active after roadmap sequencing is approved.

The council recommends making verified rider behavior a dependency of a rider-focused production release. Two sequencing choices are available. Keep Sprint 39 as the existing release and position the rider milestone for a subsequent release, or formally rebaseline release sequencing so the rider evidence precedes the production sign-off. The council recommends the second choice. The following is a scope and phase proposal, pending that sequencing decision.

### Sprint 40

**Native rider session and mixed-device direct voice**

Target internal device build · GSD reference pending · Proposed

| Phase | Status | Work | Exit condition |
| --- | --- | --- | --- |
| ICA-4001 — Rider journey and native design | Proposed | Define Start/Join, audio check, active group, mute, leave and recovery. Reconcile product and design requirements with the audit. | Owner approves original Android and iOS mockups, including degraded and permission-denied states. |
| ICA-4002 — Shared session and native Leave | Proposed | Define joined/unmuted send state and connect Android UI, notification and iOS Leave to teardown. | Shared and native checks show mute and leaving gate transmission, departure updates the reachable roster, and stale frames do not play. |
| ICA-4003 — Mixed-phone direct voice | Proposed | Verify authenticated group audio through both native BLE adapters with simultaneous speech. | Android–Android, iPhone–iPhone and Android–iPhone pairs pass voice and microphone checks offline. |
| ICA-4004 — Locked session and audio recovery | Proposed | Exercise locked-screen reconnect, headset changes, calls and navigation. | Evidence records recovered audio or explicit required action, preserved mute and expected output routing. |

**Ralph story slices:** shared send/leave state; Android action wiring; SwiftUI bridge wiring; native BLE audio/authentication; audio interruption handling per platform. Each slice gets its own verification and review.

### Sprint 41

**Rider relay, alternate routes and admin continuity**

Target mixed-device mesh build · GSD reference pending · Proposed

| Phase | Status | Work | Exit condition |
| --- | --- | --- | --- |
| ICA-4101 — Ordinary rider forwarding | Proposed | Apply authenticated forwarding, expiry, bounded hops and duplicate suppression to ordinary group voice. | Both three-phone mixed-platform chains carry two-way speech with endpoints out of direct reach. |
| ICA-4102 — Route loss and rejoin | Proposed | Recover across a viable second path and report a split when none remains. | Four-phone test proves route recovery, split/merge and no duplicate playback. |
| ICA-4103 — Multiple admins and succession | Proposed | Use committed admission order and idempotent membership events. Preserve co-admins and pause disputed authority on a split. | Last-admin leave promotes the earliest eligible member once. A timeout grants no authority. Replay, stale invites and divergent updates pass checks. |
| ICA-4104 — Real ride roles and security | Proposed | Test creator leaving as ordinary rider, hotspot host or sole relay. Verify intentional leave versus temporary disconnect and credential rotation. | Remaining authorized riders retain voice wherever a path exists. Revoked membership cannot resume or use an obsolete invite. |

**Ralph story slices:** relay envelope and receiver deduplication; route recovery; native recovery state; admission order and promotion; revocation and split reconciliation.

### Sprint 42

**Ten-rider field milestone and hybrid transport evaluation**

Target rider field beta · GSD reference pending · Proposed

| Phase | Status | Work | Exit condition |
| --- | --- | --- | --- |
| ICA-4201 — Ten-rider BLE baseline | Proposed | Test 4 iPhones + 6 Androids interleaved, grouped and stretched. Rotate the only mixed-device bridge. | Field report records first voice, latency, intelligibility, recovery, battery and manual actions. |
| ICA-4202 — Shared Wi-Fi comparison | Proposed | Compare local LAN or hotspot with BLE under matching conditions. Exercise both host roles and upstream internet disabled. | Results substantiate whether Wi-Fi improves capacity or recovery on selected phones. |
| ICA-4203 — Concurrent dual-transport trial | Proposed | Test a phone carrying BLE and Wi-Fi simultaneously. Verify forwarding, path change and packet identity. | A bridge passes two-way live voice and no duplicate audio, or the candidate is explicitly deferred with evidence. |
| ICA-4204 — India field acceptance | Proposed | Validate installation with India accounts or signed beta builds, representative devices and headsets, wind, obstructions, phone calls, lock-screen behavior and recovery. | Council reviews native usability and field evidence against agreed thresholds and records the supported scope. |

**Ralph story slices:** field instrumentation; local Wi-Fi prototype; conditional bridge adapter; rider cues and usability fixes supported by field findings.

### Sprint 43

**Twenty-rider validation and rider release evidence**

Target release recommendation · GSD reference pending · Proposed

| Phase | Status | Work | Exit condition |
| --- | --- | --- | --- |
| ICA-4301 — Twenty-rider controlled and route runs | Proposed | Run a representative mixed convoy and stretched chain with overlapping speech, wind, group split and join/leave. | Publish observed capacity, usable hop count, intelligibility, battery and recovery bounds. |
| ICA-4302 — Release UX and documentation | Proposed | Resolve field usability gaps and reconcile MRD, BRD, PRD, DESIGN, architecture and public support guidance. | Owner signs off native journey and published claims map to evidence. |
| ICA-4303 — Rider release verification | Proposed | Link signed release builds and field results to the applicable release gates. | Council records a go/no-go for the measured limits and release artifacts are verified under the approved roadmap. |

**Ralph story slices:** performance fixes justified by measurements; accessibility and recovery fixes; release artifacts and evidence documentation.

## Verification and coordination

Before field acceptance, set thresholds from the baseline for first intelligible speech, p50/p95 delay and recovery, intelligibility, battery drain and manual interventions. Record devices, OS, headset, build, route, transport, group mix and hop count. Measure outages that require user action separately. Repeat the mixed locked-screen gate on the proposed support matrix.

For code stories use the branch and review workflow in AGENTS.md, required Gradle and platform checks, Graphify and OpenCodeReview. Record device checks separately from simulations and compile evidence. At phase exit update the existing sprint report, Ralph and Obsidian workflow under SPRINT-RULES.md. No rider-count, range or release claim is established by this proposal.
