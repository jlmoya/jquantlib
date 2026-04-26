# Phase 2d Progress Log

Living document — updated by the controller after every implementer subagent run.

**Plan:** `docs/migration/phase2d-plan.md` (commit `42c833d`)
**Design:** `docs/migration/phase2d-design.md` (commit `82eb740`)
**Predecessor:** `jquantlib-phase2c-complete` @ `4cbabec`
**Phase 2d start tip on main:** `42c833d`
**Baseline:** Tests `640/0/0/24`, scanner `work_in_progress: 2` (CapHelper, G2)

## Worktrees

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2d-A` | `phase-2d-A-caphelper` | WI-1 CapHelper unstub via BlackCalibrationHelper port |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2d-B` | `phase-2d-B-nccv` | WI-2 Heston `NonCentralChiSquareVariance` scheme |
| C | `/Users/josemoya/eclipse-workspace/jquantlib-2d-C` | `phase-2d-C-sabr-xabr` | WI-3 SABR Halton via XABR scaffold + un-skip 2 calibration tests |

All 3 worktrees were created off main tip `42c833d` at L0. All independent; launched in parallel after L0.

## Pause-trigger status

- A4 sharpened (BroadieKaya carve gate inside WI-2): not fired (NCCV is closed-form, no quadrature needed)
- A6 disabled per memory `feedback_phase2a_no_a6.md`
- A8 inactive (no one-factor model fan-out in 2d)
- A9 worktree-merge-conflict: not fired
- A10 NEW (XABR template-to-generics translation snag): not fired

## Layer / WI progress

### L0 — pre-flight + worktree setup ✅

- L0.1 baseline confirmed (`640/0/0/24`, scanner WIP=2, harness OK, submodule pin `099987f0`)
- L0.2 3 worktrees created off main tip `42c833d`, each compiles clean

### L1 — parallel WI execution

#### WI-1 (worktree A) — CapHelper unstub
_(Pending — first implementer dispatched: A.1+A.2+A.3+A.4 bundle)_

#### WI-2 (worktree B) — Heston NCCV
_(Pending — first implementer dispatched: B.1+B.2 bundle)_

#### WI-3 (worktree C) — SABR/XABR/Halton
_(Pending — first implementer dispatched: C.1+C.2+C.3 bundle)_

### L2 — completion doc + tag
_(Not yet started)_

## Test count tracking

| Event | Tests | Failures | Errors | Skipped | Notes |
|-------|-------|----------|--------|---------|-------|
| Phase 2d start (`42c833d`) | 640 | 0 | 0 | 24 | baseline |
