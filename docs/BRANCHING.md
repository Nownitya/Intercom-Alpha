# Branching & Git Workflow — Intercom-Alpha

`main` is the only permanent integration branch and the default destination for all repository changes.

```text
main → short-lived work branch → reviewed pull request → main
```

## Day-to-day work

1. Start from current `origin/main`. Create one short-lived branch per story, fix or documentation change. Use the ICA ID when the work has one.
2. Open the PR directly against `main`. Include the work item and verification evidence. Apply the repository's build, Graphify and OpenCodeReview gates where required.
3. Merge after review and required Android and iOS checks pass. GitHub auto-merge may complete an approved PR once its gates pass.
4. Delete the work branch after confirming it merged and its unique work is accounted for. Retain version tags and the history on `main`.

Sprint → Phase → Story remains the planning and reporting hierarchy. Sprint milestones, ICA IDs, Ralph story status and exit evidence are tracked in [SPRINT-RULES.md](../SPRINT-RULES.md) and [TASKS.md](TASKS.md). A sprint does not require a Git branch.

## Releases and maintenance

- Use the same direct PR path for features, fixes, docs, CI and release preparation.
- Tag the exact verified commit on `main` for an app release. Maintain signing, artifact checksums and owner approval for publication. A routine PR merge does not publish the app.
- Existing develop, sprint, release and archive branches are historical. Inventory unique commits, tags and open PRs before retiring any of them. Bring useful work forward on a fresh branch from `main` with a PR.
- Require PRs and passing CI on `main`. Block force pushes and branch deletion. Record the actual GitHub enforcement separately from this written policy.

For the automated build and release gates, see [CI-CD.md](CI-CD.md).
