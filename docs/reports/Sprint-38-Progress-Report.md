# Sprint 38 progress report

**Sprint** · Basecamp dispatch and fleet telemetry  
**GSD reference** · Phase 33  
**Target** · `v3.0.0-rc1`  
**Status on 2026-11-25** · Open, Basecamp design approved

| Phase | Status | Evidence needed for closeout |
|---|---|---|
| ICA-3801 | Design approved | Connect to an Android phone running stationary BLE repeater mode. Inspect forwarding, run a link check and recover from a disconnect. |
| ICA-3802 | Planned | Monitor multiple squads and show unavailable telemetry. |
| ICA-3803 | Planned | Complete emergency all-call and incident export with clear outcomes. |
| ICA-3804 | Planned | Verify three remote squads and one Basecamp node and pass release-candidate gates. |

Basecamp is optional for riders. The first relay target is a phone running Intercom-Alpha. Existing Android mesh and repeater messages give the console a protocol to build on. The desktop/web connection and release checks remain open.

The local integration branch was fast-forwarded to Sprint 37 closeout commit `0201dd0`. Sprint 38 and ICA-3801 have local branches named `sprint/sprint-38-basecamp-dispatch` and `feature/ICA-3801-basecamp-console`.
eature/ICA-3801-basecamp-console.

[Current sprint](../../SPRINT.md) · [Implementation plan](../ICA-3801-PLAN.md) · [Task record](../TASKS.md#sprint-38)
