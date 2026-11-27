# Sprint 38 · Basecamp dispatch and fleet telemetry

[Proposed rider workstream](docs/reports/2026-11-27-Rider-Intercom-Council-Plan.md)

**Current phase:** `ICA-3801` — Basecamp Console · Design approved

**Target:** `v3.0.0-rc1` · **GSD reference:** Phase 33

| Phase | Status | Work | Exit condition |
| --- | --- | --- | --- |
| `ICA-3801` — Basecamp Console and repeater connection | Design approved | Build the desktop/web console and connect to a phone running Intercom-Alpha in stationary BLE repeater mode. | An operator can connect, inspect forwarding, run a link check and recover from disconnection. |
| `ICA-3802` — Fleet monitoring | Planned | Add multi-squad tracking, battery health and telemetry. | An operator can monitor multiple squads and identify unavailable telemetry. |
| `ICA-3803` — Dispatch and incident export | Planned | Add emergency all-call and incident audio export. | An operator can complete dispatch and export with clear outcome feedback. |
| `ICA-3804` — Verification and closeout | Planned | Simulate three remote squads and one basecamp node. | The deployment simulation and release-candidate gates pass. |

The project owner approved the Basecamp direction on 2026-11-25. Implementation and the branch-specific Ralph queue are pending. See the [ICA-3801 implementation plan](docs/ICA-3801-PLAN.md).

[Sprint 38 progress report](docs/reports/Sprint-38-Progress-Report.md) · [All sprint reports](docs/reports/README.md)

## Sprint 37 closeout

[Sprint 37 is complete](docs/reports/2026-11-23-Sprint-37-Closeout-Report.md) at local closeout commit `0201dd0`. Ralph records `ICA-3701` through `ICA-3704` as passed. Simulation and platform checks passed. Physical BLE range and applied radio PHY remain device-verification items.

[Task breakdown](docs/TASKS.md#sprint-38) · [Roadmap](docs/ROADMAP.md) · [Ralph tracker for the checked-out Sprint 37 branch](tasks/ralph/prd.json) · [Governance](SPRINT-RULES.md#sprint-planning-hierarchy)

## 📜 Historical Sprint Archive
- ✅ **Sprint 36: Offline Vector Topographic & Trail Elevation Profile HUD**: Completed 2026-10-18 (100% DoD, v3.0.0-beta1).
- ✅ **Sprint 35: Ultra-Low Latency BLE MTU Frame Packing & Interleaved Diversity**: Completed 2026-10-18 (100% DoD, v3.0.0-alpha3).
- ✅ **Sprint 34: Forward Error Correction & Packet Loss Concealment**: Completed 2026-10-18 (100% DoD, v3.0.0-alpha2).
- ✅ **Sprint 33: Dynamic Adaptive Mesh Bitrate Tuning**: Completed 2026-10-18 (100% DoD, v3.0.0-alpha2).
- ✅ **Sprint 32: Tactile Sub-Screens & Secondary Overlays Polish**: Completed 2026-10-18 (100% DoD, v3.0.0-alpha1).
- ✅ **Sprint 31: Zero-Scroll Tactical Cockpit Redesign**: Completed 2026-10-18 (100% DoD, v3.0.0-alpha1).
- ✅ **Sprint 30: Dedicated UI/UX Design System Foundations**: Completed 2026-10-17 (100% DoD, v3.0.0-alpha1).
