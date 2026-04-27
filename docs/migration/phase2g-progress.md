# Phase 2g Progress Log

Living document — updated by the controller after every implementer subagent run.

**Plan:** `docs/migration/phase2g-plan.md` (commit `1d9bae7`)
**Design:** `docs/migration/phase2g-design.md` (commit `7208b25`)
**Predecessor:** `jquantlib-phase2f-complete` @ `debedf9`
**Phase 2g start tip on main:** `1d9bae7`
**Baseline:** Tests `675/0/0/22`, scanner `0 stubs` (Phase 2e milestone preserved)

## Worktrees

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2g-A` | `phase-2g-A-brent-aligns` | WI-1 Brent.solveImpl alignment + bundled fixes (VanillaSwap.setupArguments + FloatingRateCoupon.fixingDays) + tier promotions |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2g-B` | `phase-2g-B-fd-hullwhite` | WI-2 FdHullWhiteSwaptionEngine — Java's first FD pricing engine |
| C | `/Users/josemoya/eclipse-workspace/jquantlib-2g-C` | `phase-2g-C-fd-g2` | WI-3 FdG2SwaptionEngine — sibling 2D FD swaption engine |

All 3 worktrees were created off main tip `1d9bae7` at L0. WI-1 lands first; WI-2/WI-3 port code can dispatch in parallel with WI-1, but final commits gate on WI-1 land.

## Pause-trigger status

- A4 sharpened (FD scaffold extension if needed): not fired
- A6 disabled per memory `feedback_phase2a_no_a6.md`
- A8/A10/A11/A12/A14 inactive per design §5
- A9 worktree-merge-conflict: not fired
- A13 (carried from 2f, transcendental drift, NCCV tier promotion attempt): not fired
- A15 NEW (Brent fix surfaces previously-hidden Java port bug): not fired
- A16 NEW (FD scaffold gap requires architecture discussion): not fired

## Layer / WI progress

### L0 — pre-flight + worktree setup ✅

- L0.1 baseline confirmed (`675/0/0/22`, scanner 0 stubs, tip `1d9bae7`)
- L0.2 3 worktrees created off main tip `1d9bae7`

### L1a — WI-1 (worktree A)
_(Pending — first implementer dispatched first)_

### L1b — WI-2 + WI-3 parallel (after WI-1 lands)

#### WI-2 (worktree B) — FdHullWhiteSwaptionEngine
_(Pending — port code may dispatch in parallel with WI-1; probe gates on WI-1 land)_

#### WI-3 (worktree C) — FdG2SwaptionEngine
_(Pending — port code may dispatch in parallel with WI-1; probe gates on WI-1 land)_

### L2 — completion doc + tag
_(Not yet started)_

## Test count tracking

| Event | Tests | Failures | Errors | Skipped | Notes |
|-------|-------|----------|--------|---------|-------|
| Phase 2g start (`1d9bae7`) | 675 | 0 | 0 | 22 | baseline |
