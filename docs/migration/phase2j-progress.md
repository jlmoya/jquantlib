# Phase 2j Progress Log

Living document — updated by the controller after every implementer subagent run.

**Plan:** `docs/migration/phase2j-plan.md` (commit `3f2c33f`)
**Design:** `docs/migration/phase2j-design.md` (commit `368dbda`)
**Predecessor:** `jquantlib-phase2i.6-complete` @ `44be66c`
**Phase 2j start tip on main:** `<fill at L0 land>`
**Baseline:** Tests `688/0/0/22`, scanner `0 stubs`

## Worktrees

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2j-A` | `phase-2j-A-gaussian1d-model` | WI-1 model layer (4 sub-commits, sequential) |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2j-B` | `phase-2j-B-standard-engines` | WI-2 standard engines — dispatches AFTER WI-1.4 |
| C | `/Users/josemoya/eclipse-workspace/jquantlib-2j-C` | `phase-2j-C-niche-swaption-engines` | WI-3 niche engines — dispatches AFTER WI-1.4 |
| D | `/Users/josemoya/eclipse-workspace/jquantlib-2j-D` | `phase-2j-D-markov-functional` | WI-4 MarkovFunctional — dispatches AFTER WI-1.1 |

## Pause-trigger status

- A2 (tolerance looser than 1e-8): not fired
- A3 (cross-validation reveals reference wrong): not fired
- A4 (unplanned new packages outside the 5 planned): not fired
- A6 disabled per memory `feedback_phase2a_no_a6.md`
- A8 (test suite red unrelated): not fired
- A9 worktree-merge-conflict: not fired
- A13 re-armed for Math.pow: not fired
- A15 (previously-hidden bug surface): not fired
- A16 (missing dependency outside planned scope): not fired
- A17 (>2 unplanned align commits during port): not fired
- A18 (NaN payload divergence): not fired
- A19 (Math.pow at GsrProcessCore floors a tier): not fired
- A20 NEW (MarkovFunctional calibration non-determinism): not fired
- A21 NEW (wall-time projection > 3 sessions): not fired

## Layer / WI progress

### L0 — pre-flight + worktree setup ✅

- L0.1 baseline confirmed (`688/0/0/22`, scanner 0 stubs, tip `3f2c33f`, submodule pin `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`)
- L0.2 4 worktrees created off main tip `3f2c33f`; submodules init'd in each

### L1 — WI-1 model layer (4 sub-commits, sequential, worktree A)

#### Sub-layer 1.1 — Gaussian1dModel base
_(Pending — first implementer dispatched after L0)_

#### Sub-layer 1.2 — GsrProcessCore + GsrProcess
_(Pending — dispatch after 1.1 lands)_

#### Sub-layer 1.3 — Gsr concrete model
_(Pending — dispatch after 1.2 lands)_

#### Sub-layer 1.4 — Gaussian1dSmileSection + Gaussian1dSwaptionVolatility
_(Pending — dispatch after 1.3 lands)_

### L2 — WI-2 standard engines + WI-3 niche engines (parallel after WI-1.4 lands)

#### WI-2 (worktree B): SwaptionEngine + CapFloorEngine
_(Pending — dispatches after WI-1.4 lands)_

#### WI-3 (worktree C): Jamshidian + Nonstandard + FloatFloat
_(Pending — dispatches after WI-1.4 lands; parallel with WI-2)_

### L3 — WI-4 MarkovFunctional (parallel after WI-1.1 lands)
_(Pending — dispatches after WI-1.1 lands; runs concurrent with WI-1.2/1.3/1.4 + WI-2/WI-3)_

### L4 — completion doc + tag
_(Not yet started)_

## Test count tracking

| Event | Tests | Failures | Errors | Skipped | Notes |
|-------|-------|----------|--------|---------|-------|
| Phase 2j start (`3f2c33f`) | 688 | 0 | 0 | 22 | baseline |
