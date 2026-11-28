# Sprint 37 closeout report

**Sprint** · Fleet mesh congestion control and adaptive PHY  
**GSD reference** · Phase 32  
**Target** · `v3.0.0-beta2`  
**Status** · Closed locally on 2026-11-23 at `0201dd0`

## Outcomes

| Phase / story | Recorded result |
|---|---|
| ICA-3701 | Rate-limited BLE advertising and reduced redundant relay traffic under congestion. The congestion policy bypasses damping for emergency traffic. |
| ICA-3702 | Implemented switching policy for LE 1M, LE 2M and LE Coded S8 with hysteresis. |
| ICA-3703 | Connected PHY and congestion snapshots to the platform bridges and cockpit. |
| ICA-3704 | Verified the policy under deterministic 24-node mixed traffic and tested airtime recovery and neighbor expiry. Feature commit `5a92b0e` merged in `cc7cf74`. |

## Verification

The closeout records passing Gradle tests, 478 shared JVM tests, Android debug assembly, and shared Kotlin iOS simulator compilation. It also records a Graphify refresh and offline OpenCodeReview delegation with no actionable findings on the changed test.

Physical BLE range and radio-applied PHY remain field checks. The closeout establishes local sprint completion. Integration and publication have separate evidence.

[Task record](../TASKS.md#sprint-37) · [Ralph tracker](../../tasks/ralph/prd.json) · [Ralph verification log](../../tasks/ralph/progress.txt)
