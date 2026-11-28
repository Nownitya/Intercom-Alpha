# Multi-Tier Branching Strategy & Git Workflow — Intercom-Alpha

> **Default branch:** `main`
> **Integration branch:** `develop`

## Branching topology

| Branch | Starts from | Integrates into | Scope |
| --- | --- | --- | --- |
| `feature/ICA-NNNN-slug` | Active sprint | Same sprint | One Ralph story with its tests and review evidence. |
| `sprint/sprint-NN-slug` | `develop` | `develop` | Verified story increments. Close the sprint only after its phase exit conditions pass. |
| `release/vX.Y` | Verified `develop` commit | `main` | Release stabilization, fixes, packaging and documentation. Forward release fixes to `develop`. |
| `hotfix/vX.Y.Z` | Affected release tag or maintenance branch | `main` or the supported release line | Focused repair. Forward the fix to `develop` and any affected active release branch. |
| `docs/slug` | `main` or `develop`, depending on audience | Same branch via PR | Content-only changes. Use `develop` for live engineering plans and `main` for release-facing docs or links to those plans. |

## Integration and release rules

- Use reviewed PRs for `main` and `develop`. Keep force pushes and direct pushes off both branches. Enable GitHub enforcement explicitly and record which rules are active. Repository visibility and enforcement are separate settings.
- `main` holds released app source and approved standalone documentation. A docs-only PR needs link and content review. Workflow files, build configuration, generated runtime code and app sources follow the relevant code gates.
- Tag the exact reviewed app release commit with an annotated version tag. Use prerelease tags for candidates. Keep published tags fixed. Record the commit, artifact checksum, signing and distribution channel.
- Keep one canonical live sprint plan. Prefer linking to it from the default branch when two copies would go out of sync.
- Keep the current Gradle, platform build, Graphify and OpenCodeReview requirements. Report actual results. A successful Linux run does not establish a native iOS app build. Run Xcode and iOS simulator verification on macOS.
- For protocol or membership changes, include compatibility, downgrade, privacy and mixed-device recovery evidence. For store publishing, separate signing and publishing access from PR jobs and obtain owner authorization. Prepare rollout halt or replacement-build instructions.
- Record sprint outcomes, verification, and review under [SPRINT-RULES.md](../SPRINT-RULES.md). See the [branching and council review](reports/2026-11-28-Branching-and-Council-Review.md) for rationale.

## Transition

Adopt new GitHub required checks only after their workflows run reliably on the applicable PR path. Existing hotfixes keep their recorded review and release path. This policy does not alter assigned sprint IDs or release sequencing.
