# Phase 2e Progress Log

Living document — updated by the controller after every implementer subagent run.

**Plan:** `docs/migration/phase2e-plan.md` (commit `855f45d`)
**Design:** `docs/migration/phase2e-design.md` (commit `c4447ea`)
**Predecessor:** `jquantlib-phase2d-complete` @ `06450e6`
**Phase 2e start tip on main:** `855f45d`
**Baseline:** Tests `649/0/0/22`, scanner `work_in_progress: 1` (G2 only)

## Worktrees

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2e-A` | `phase-2e-A-g2` | WI-1 G2 model body port (closes last scanner WIP) |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2e-B` | `phase-2e-B-cap-engine` | WI-2 BlackCapFloorEngine + CapFloor.NPV() wiring + CapHelper retrofit |
| C | `/Users/josemoya/eclipse-workspace/jquantlib-2e-C` | `phase-2e-C-swaption` | WI-3 Swaption infrastructure (BlackSwaption + DiscretizedSwaption + TreeSwaption) + SwaptionHelper full body |

All 3 worktrees were created off main tip `855f45d` at L0. All independent in the dep graph; launched in parallel after L0.

## Pause-trigger status

- A4 sharpened (new `pricingengines.swaption` directory in scope, planned not surprise; includes ConstantOptionletVolatility / ConstantSwaptionVolatility small mechanical helpers if needed): not fired
- A6 disabled per memory `feedback_phase2a_no_a6.md`
- A8 inactive (G2 is two-factor, not one-factor family fan-out)
- A9 worktree-merge-conflict: not fired
- A10 inactive (no XABR work in 2e)
- A11 NEW (G2 swaption integral path needing non-trivial integrator): not fired
- A12 NEW (Swaption.NPV() wiring needing deeper engine-arguments dispatch refactor): not fired

## Layer / WI progress

### L0 — pre-flight + worktree setup ✅

- L0.1 baseline confirmed (`649/0/0/22`, scanner WIP=1, tip `855f45d`)
- L0.2 3 worktrees created off main tip `855f45d`

### L1 — parallel WI execution

#### WI-1 (worktree A) — G2 model body port
_(Pending — first implementer dispatched)_

#### WI-2 (worktree B) — BlackCapFloorEngine + CapFloor.NPV() + CapHelper retrofit
_(Pending — first implementer dispatched)_

#### WI-3 (worktree C) — Swaption infrastructure + SwaptionHelper full body
_(Pending — first implementer dispatched)_

### L2 — completion doc + tag
_(Not yet started)_

## Test count tracking

| Event | Tests | Failures | Errors | Skipped | Notes |
|-------|-------|----------|--------|---------|-------|
| Phase 2e start (`855f45d`) | 649 | 0 | 0 | 22 | baseline |
