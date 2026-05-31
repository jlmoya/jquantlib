# Stash archive — 2026-05-31

A **complete, lossless archive of 43 `git stash` entries** that existed on `main` as of
2026-05-31, captured immediately **before** `git stash clear`. Preserved two ways:

- **`*.patch`** — one full patch per stash (tracked **and** untracked content; verified to
  contain **zero binary** diffs, so the text is a complete record). `git apply`-able.
- **`all-stashes.bundle`** — a git bundle holding the **exact** stash commit objects
  (working-tree + index + any untracked parents), for bit-exact recovery.
- **`MANIFEST.txt`** — `index | date | commit SHA | original label`.

## What these were
Transient working-tree snapshots from the multi-agent **Phase 5e.5b-CFC-d** burst
(**2026-05-15 → 2026-05-19**): parked baselines, killed-agent leftovers, other-agents'-WIP
snapshots, and a few mid-task `WIP on main` index states.

## Why they were cleared
Every snapshot **predates** both `jquantlib-truly-complete` (`54ca6d72`, 2026-05-23) and the
full-surface functional-coverage milestone (`0646ee75`, 2026-05-29). The codebase advanced to
all-green / 0-unflagged-coverage / 3678 tests well past every one of them, so their WIP is
superseded or abandoned — pure clutter in `git stash list`. Archived here first so nothing is lost.

## How to recover

**Inspect / apply a single stash (human-readable):**
```bash
git apply --stat docs/migration/stash-archive-2026-05-31/NN-<label>.patch   # preview
git apply        docs/migration/stash-archive-2026-05-31/NN-<label>.patch   # apply to working tree
git apply --3way docs/migration/stash-archive-2026-05-31/NN-<label>.patch   # context-tolerant
```

**Bit-exact recovery of the original stash commits (from the bundle):**
```bash
git fetch docs/migration/stash-archive-2026-05-31/all-stashes.bundle \
  'refs/tags/stasharc/*:refs/tags/recovered-stash/*'
git for-each-ref refs/tags/recovered-stash/        # list what came back
git stash apply <recovered-sha>                    # or: git show <recovered-sha>
```
The bundle excludes history already present in `main`, so recovery must run from inside this
repository (where `main` provides the shared base objects).
