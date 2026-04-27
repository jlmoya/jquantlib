# Phase 2f Progress Log

Living document — updated by the controller after every implementer subagent run.

**Plan:** `docs/migration/phase2f-plan.md` (commit `a788e51`)
**Design:** `docs/migration/phase2f-design.md` (commit `089eddb`)
**Predecessor:** `jquantlib-phase2e-complete` @ `a533fbd`
**Phase 2f start tip on main:** `a788e51`
**Baseline:** Tests `656/0/0/22`, scanner `0 stubs` (Phase 1 mandate met preserved)

## Worktrees

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2f-A` | `phase-2f-A-cap-engines` | WI-1 cap engines: AnalyticCapFloor + BachelierCapFloor + BlackCapFloor Bachelier branch + OVS volType API |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2f-B` | `phase-2f-B-swaption-engines` | WI-2 swaption engines + G2.swaption: Jamshidian + BlackSwaption Bachelier branch + G2.swaption integral |
| C | `/Users/josemoya/eclipse-workspace/jquantlib-2f-C` | `phase-2f-C-heston-bk` | WI-3 Heston BroadieKaya + NCCS tightening: Lobatto + Laguerre + Fourier-inversion + 3 BroadieKaya schemes + NCCS tighten + discountBondOption + NCCV tier promotion |

All 3 worktrees were created off main tip `a788e51` at L0. All independent in the dep graph; launched in parallel.

## Pause-trigger status

- A4 sharpened (Lobatto/Laguerre/Fourier in scope, planned not surprise; commons-math3 OR minimal Complex port for Fourier): not fired
- A6 disabled per memory `feedback_phase2a_no_a6.md`
- A8/A10/A11/A12 inactive per design §5
- A9 worktree-merge-conflict: not fired
- A13 NEW (NCCS structural drift impossibility): not fired
- A14 NEW (Complex arithmetic infrastructure gap): not fired

## Layer / WI progress

### L0 — pre-flight + worktree setup ✅

- L0.1 baseline confirmed (`656/0/0/22`, scanner 0 stubs, tip `a788e51`)
- L0.2 3 worktrees created off main tip `a788e51`

### L1 — parallel WI execution

#### WI-1 (worktree A) — Cap engines
_(Pending — first implementer dispatched)_

#### WI-2 (worktree B) — Swaption engines + G2.swaption
_(Pending — first implementer dispatched)_

#### WI-3 (worktree C) — Heston BroadieKaya + NCCS tightening
_(Pending — first implementer dispatched)_

### L2 — completion doc + tag
_(Not yet started)_

## Test count tracking

| Event | Tests | Failures | Errors | Skipped | Notes |
|-------|-------|----------|--------|---------|-------|
| Phase 2f start (`a788e51`) | 656 | 0 | 0 | 22 | baseline |
