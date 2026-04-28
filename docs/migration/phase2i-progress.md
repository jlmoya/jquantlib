# Phase 2i Progress Log

Living document — updated by the controller after every implementer subagent run.

**Plan:** `docs/migration/phase2i-plan.md` (commit `4dcbe8f`)
**Design:** `docs/migration/phase2i-design.md` (commit `ad39ee9`)
**Predecessor:** `jquantlib-phase2h-complete` @ `f0256c8`
**Phase 2i start tip on main:** `14fbd49`
**Baseline:** Tests `677/0/0/22`, scanner `0 stubs`

## Worktrees

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2i-A` | `phase-2i-A-transcendental-lib` | WI-1 transcendental port (4 sub-layers, sequential) |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2i-B` | `phase-2i-B-call-site-integration` | WI-2 — dispatches AFTER WI-1 lands |
| C | `/Users/josemoya/eclipse-workspace/jquantlib-2i-C` | `phase-2i-C-tier-promotion-sweep` | WI-3 — dispatches AFTER WI-2 lands |

## Pause-trigger status

- A2 (WI-1 EXACT unreachable): not fired
- A4 (unplanned new class outside `org.jquantlib.math.transcendental`): not fired
- A6 disabled per memory `feedback_phase2a_no_a6.md`
- A8/A10/A11/A12/A14 inactive
- A9 worktree-merge-conflict: not fired
- A13 carried (re-arms for non-transcendental ULP source): not fired
- A15 (previously-hidden bug surface): not fired
- A16 (missing dependency outside planned scope, e.g. cosh/sinh): not fired
- A17 (>2 unplanned align commits during port): not fired
- A18 NEW (NaN payload divergence): not fired
- A19 NEW (WI-2 promotion fails after correct swap-in): not fired

## Layer / WI progress

### L0 — pre-flight + worktree setup ✅

- L0.1 baseline confirmed (`677/0/0/22`, scanner 0 stubs, tip `4dcbe8f`, submodule pin `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`)
- L0.2 3 worktrees created off main tip `4dcbe8f`; submodules init'd in each

### L1 — WI-1 sequential (4 sub-layer commits + 1 prep commit on main)

#### Task 1.0 — MathTestSupport bit-pattern helper (lands on main first) ✅

- Commit `2ab7ecf` on main — adds `MathTestSupport.java` (assertBitsEqual w/ NaN-payload canonicalisation, parseHexBits) and `MathTestSupportTest.java` (4 self-tests). Tests `677 → 681`. Spec-compliant; code-review APPROVED (0 findings).

#### Sub-layer 1.1 — exp
_(Pending — dispatch after Task 1.0 lands)_

#### Sub-layer 1.2 — log
_(Pending — dispatch after 1.1 lands)_

#### Sub-layer 1.3 — sin + cos
_(Pending — dispatch after 1.2 lands)_

#### Sub-layer 1.4 — pow
_(Pending — dispatch after 1.3 lands)_

### L2 — WI-2 sequential (3 sub-tasks)

#### B-1 FdHullWhiteSwaptionEngine LOOSE → TIGHT
_(Pending — dispatches after WI-1 lands)_

#### B-2 Heston BroadieKaya 5e-3 → LOOSE
_(Pending — dispatches after B-1 lands)_

#### B-3 NCCS TIGHT → EXACT attempt
_(Pending — dispatches after B-2 lands)_

### L3 — WI-3 audit + tier-flip sweep
_(Pending — dispatches after WI-2 lands)_

### L4 — completion doc + tag
_(Not yet started)_

## Test count tracking

| Event | Tests | Failures | Errors | Skipped | Notes |
|-------|-------|----------|--------|---------|-------|
| Phase 2i start (`14fbd49`) | 677 | 0 | 0 | 22 | baseline (post-progress-doc) |
| Task 1.0 land (`2ab7ecf`) | 681 | 0 | 0 | 22 | +4 MathTestSupportTest |
