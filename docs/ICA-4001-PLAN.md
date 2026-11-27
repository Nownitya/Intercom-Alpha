# Sprint 40 → ICA-4001 — Rider journey and native design

**Status:** Proposed · **GSD reference:** Pending roadmap sequencing

The exit condition is owner approval of original Android and iOS mockups for starting, joining, talking and recovering in a private ride group. These requirements guide design and review of those mockups.

## Product constraints

Use Jetpack Compose for Android and native SwiftUI for iOS. Both clients share the same session behavior through existing KMP contracts. The default ride experience is hands-free conversation. One group identity survives link and transport changes. A rider can stay in the current group when the creator leaves, provided a usable route remains.

## 1. Design the rider journey

| Screen or state | What the rider sees | Primary action | What to verify |
| --- | --- | --- | --- |
| Ready to ride | Start group and Join group. A short explanation of nearby voice where needed. | Start or Join. | Both actions are discoverable from first launch with no account or cloud dependency. |
| Invite or scan | Group name, QR invite, and who has joined, or a camera view with a manual invite alternative. | Share or scan invite. | Permission denial, unavailable camera and expired invite have a clear recovery step. |
| Audio check | Selected input/output and whether the mic is muted. A way to confirm the rider can hear and be heard. | Join conversation or change audio route. | The rider explicitly chooses to join voice. The app explains a missing mic permission. |
| Active group | Group name, reachable riders, speaking indicators based on real audio state, and persistent mic status. | Mute or unmute. Leave stays visible. | Two remote speakers can be indicated together. No PTT action is needed to speak. |
| Recovering | Concise connection state and which riders remain reachable. A brief audible cue where available. | Keep listening, or view the action required. | Cue once per meaningful state change. Recovery never clears deliberate mute. |
| Part of group unreachable | Reachable members remain present and can talk. Unreachable members are identified. | View members or continue. | Show the group split separately from a headset or permission issue. |
| Headset disconnected | Private audio paused and a clear route choice while stopped. | Reconnect or choose another route. | Voice does not unexpectedly switch to speaker. |
| Group controls | Member list, current admins, authorized promotion and invite access. | Approve invite or promote an eligible member. | Existing co-admins keep authority. Last-admin departure promotes the earliest eligible member only after confirmation. |
| Leaving or left | Local mic closes on confirmation, followed by a clear outside-session state. | Return to Start or Join. | Temporary drop stays recoverable. Deliberate departure requires authorized rejoin. |

## 2. Review native layouts and wording

- Use platform-native controls and accessibility semantics. Keep the same concepts and state names across platforms. Use original layout, typography and icon choices.
- Review TalkBack and VoiceOver reading order, large text, daylight contrast and touch targets. Show connection, mute and role using text and symbols in addition to color.
- Before departure, a rider should find Start or Join and confirm the headset without opening Convoy, Radar or Settings. During the ride, a headset cue should distinguish lost voice from restored voice without requiring screen interaction.
- Keep transport and diagnostic details a level deeper. The active group should communicate whether people can talk and who can hear them.
- Walk through a creator leaving, a sole relay dropping, a stale invitation and a headset interruption. Use the real-ride scenarios to check state and copy.

## 3. Produce review evidence

- Create Android and iOS mockups covering the primary flow and at least the locked-session recovery, group split, headset loss and last-admin departure states.
- Validate the state walkthrough using the same two-rider and mixed-convoy scenarios on both platforms. Record confusing actions, status or wording and revise the designs.
- Record owner approval and council findings in the applicable sprint report. Link the approved flow to the PRD and design spec when roadmap sequencing is active.
- Hand the approved state contract to ICA-4002 for native Leave and shared microphone lifecycle work.

[Council sprint plan](reports/2026-11-27-Rider-Intercom-Council-Plan.md) · [Product audit](reports/2026-11-27-Rider-Intercom-Product-Audit.md) · [Transport and field scenarios](reports/2026-11-27-Intercom-Transport-Comparison.md)
