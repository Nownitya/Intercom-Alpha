# Branching and council governance review

**Date:** 28 November 2026 · **Status:** Proposed

## Findings

- CONTRIBUTING directs feature branches from main. AGENTS and BRANCHING require story branches from the active sprint, with sprints based on develop. Make BRANCHING the canonical branch map and align the other documents.
- BRANCHING describes main as protected. GitHub reported main as unprotected during the repository audit and returned a plan limitation for rules on the private repository. Distinguish team policy from GitHub enforcement.
- Main serves both as the release branch and the default documentation landing page. Requiring a version tag for every main commit turns planning documentation updates into release hotfixes.
- Fixed passing-test counts and blanket story coverage percentages age poorly. Keep the project verification gates, require relevant regression evidence, and report actual test counts.
- The council has a scope mandate but needs a decision record, tiered review and boundaries around release authority. Keep GSD, Ralph and OpenCodeReview responsibilities.

## Proposed branch map

| Branch or ref | Base and destination | Use |
| --- | --- | --- |
| feature/ICA-NNNN-slug | Active sprint → same sprint | One Ralph story with evidence and OpenCodeReview before commitment and integration. |
| sprint/sprint-NN-slug | develop → develop | Aggregate verified story increments. Close the sprint only when all phase exit criteria pass. Use integration PRs for verified increments so changes do not pile up until closeout. |
| develop | Receives sprint and maintenance PRs | Integration and current engineering documentation. Sprint branches pull in verified integration changes as needed. |
| release/vX.Y | Verified develop commit → main | Temporary stabilization for one supported release line. Accept release fixes, packaging and docs. Keep unreleased features outside the release branch. Forward release fixes into develop. |
| hotfix/vX.Y.Z | Affected release tag or corresponding maintenance branch → main or the supported release line | Repair a shipped version. Forward the fix to develop and any active release branch that needs it. Keep rollback instructions and compatibility evidence with the PR. |
| docs/slug | main → main for approved standalone documentation | Optional lane for content-only changes such as a task index or user-facing docs. Portable links or references to pinned sources must work on main. Require link and content review. Backport the docs commit to develop if applicable. |
| vX.Y.Z or vX.Y.Z-rc.N | Exact reviewed release commit | Tag releasable commits and identify artifacts by commit and checksum. Do not move published version tags. |

**Decision needed:** Permit approved docs-only updates to main without app version tags. This changes the current every-main-commit tag rule. Keep app releases explicitly tagged. Until approved, follow the current hotfix or release process. Maintain one source for the live sprint plan, and link to it from main when publishing a second copy would create two active trackers.

## Gates and environments

| Change | Evidence and approval |
| --- | --- |
| Documentation only | Confirm changed files are content-only, validate local and pinned links, and review published claims. Workflow, signing, dependency and build changes use the relevant code or release gate. |
| Story and sprint integration | Keep the repository Gradle and OpenCodeReview requirements. Use Linux for shared and Android checks and macOS for the actual Xcode build and iOS simulator checks. Run Graphify after code changes. Keep distinct required status names and record skipped jobs honestly. |
| Networking, membership or protocol changes | Review compatibility across app versions and optional server usage, downgrade behavior, privacy and recovery. Run mixed-device field cases for affected rider claims. |
| Release and hotfix | Verify exact commit, source tag, checksums, signing, installable artifact, release notes, store channel, recovery plan and relevant field evidence. Builds can go to GitHub drafts, Play internal or TestFlight before production approval. |
| CI credentials and deployment | Keep PR jobs read-only by default. Isolate signing and publishing permissions. Use an owner-controlled dispatch or external approval mechanism if private-repository environment reviewers are unavailable. Use rule enforcement on main and develop when plan access permits. |

Capture pass/fail evidence in the existing Sprint → Phase → Story reports. Retain mobile signing and store constraints, including monotonically increasing build numbers. Recovery may require a replacement build or a staged rollout halt. Keep last-known-good artifacts and server compatibility instructions available.

## Council authority

| Role | Authority and limits |
| --- | --- |
| Project owner | Approves changes to roadmap sequencing, release scope, native design and public commitments. Authorizes production publishing and emergency exceptions. |
| Council | Ranks must-have and optional capabilities, chooses routine UX and transport trade-offs inside approved scope, defines field evidence and withholds its recommendation when evidence is missing. |
| GSD | Owns approved requirements, milestones, phase exit checks and the decision log. |
| Ralph | Implements and verifies one atomic story, remediates findings and records evidence. |
| OpenCodeReview and applicable specialist | Independently review the diff and record findings. For protocol, security, signing or store changes, include the relevant specialist in the review. |
| Release maintainer | Prepares the release candidate, verifies the tagged commit and artifact evidence, and promotes it after authorization. |

A small change needs a relevant reviewer and concise evidence. A cross-platform, privacy, transport or release decision needs input from affected seats and one recorded disposition. The proposer should get independent review for high-impact changes. When only one human maintainer is available, record the automated and independent agent evidence and keep production authorization with the owner. Record decision, scope, evidence, dissent or unresolved risk, owner, and the milestone that will revisit it.

## Adoption

1. Decide whether to allow the docs-only lane. Align BRANCHING, CONTRIBUTING and AGENTS in one governance PR. Preserve sprint numbering, assigned stories and existing release tags.
2. Split Android and macOS/iOS CI. Keep existing checks passing, then enforce verified check names on GitHub if the private plan permits.
3. Add tagged draft-release automation and artifact manifests. Roll out signed internal store delivery after the owner supplies signing and store access.
4. Use the council decision record at the next rider phase review and review the policy again after the first real release and hotfix.

[Current branching policy](../BRANCHING.md) · [Contributor guide](../../CONTRIBUTING.md) · [Sprint rules](../../SPRINT-RULES.md) · [Rider council plan](2026-11-27-Rider-Intercom-Council-Plan.md) · [GitHub branch protection](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches) · [GitHub deployment approvals](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/review-deployments)
