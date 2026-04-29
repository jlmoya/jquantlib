# Phase 2i.5 Progress Log

Living document — updated by the controller after every implementer subagent run.

**Plan:** `docs/migration/phase2i.5-plan.md` (commit `a4dcbf0`)
**Design:** `docs/migration/phase2i.5-design.md` (commit `8cf4775`)
**Predecessor:** `jquantlib-phase2i-complete` @ `a4a3b77`
**Phase 2i.5 start tip on main:** `<fill at L0 land>`
**Baseline:** Tests `684/0/0/22`, scanner `0 stubs`

## Worktrees

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2i5-A` | `phase-2i5-A-trig-port` | WI-1 cos/sin paired port |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2i5-B` | `phase-2i5-B-nccs-rewire` | WI-2 NCCS CDF rewire + EXACT (parallel with A) |
| C | `/Users/josemoya/eclipse-workspace/jquantlib-2i5-C` | `phase-2i5-C-trig-tier-flips` | WI-3 GaussLag + GaussLob tier flips (after WI-1 lands) |

## Pause-trigger status

- A2 (WI-1 EXACT unreachable): not fired
- A3 (CORE-MATH reference itself wrong): not fired
- A4 (unplanned new class outside `org.jquantlib.math.transcendental`): not fired
- A6 disabled per memory `feedback_phase2a_no_a6.md`
- A8/A10/A11/A12/A14 inactive
- A9 worktree-merge-conflict: not fired
- A13 (non-cos/sin transcendental): not fired
- A15 (previously-hidden bug surface): not fired
- A16 (missing dependency outside planned scope): not fired
- A17 (>2 unplanned align commits during port): not fired
- A18 (NaN payload divergence): not fired
- A19 (tier promotion fails after correct swap-in): not fired

## Layer / WI progress

### L0 — pre-flight + worktree setup ✅

- L0.1 baseline confirmed (`684/0/0/22`, scanner 0 stubs, tip `a4dcbf0`, submodule pin `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`)
- L0.2 3 worktrees created off main tip `a4dcbf0`; submodules init'd in each

### L1a — WI-1 cos/sin paired port (worktree A)
_(Pending — implementer dispatched)_

### L1b — WI-2 NCCS CDF rewire + EXACT attempt (worktree B, parallel with L1a)
_(Pending — implementer dispatched in parallel with L1a)_

### L2 — WI-3 GaussLag + GaussLob tier flips (worktree C, after WI-1 lands)
_(Pending — dispatches after WI-1 lands)_

### L3 — completion doc + tag
_(Not yet started)_

## Test count tracking

| Event | Tests | Failures | Errors | Skipped | Notes |
|-------|-------|----------|--------|---------|-------|
| Phase 2i.5 start (`a4dcbf0`) | 684 | 0 | 0 | 22 | baseline |
