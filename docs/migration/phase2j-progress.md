# Phase 2j Progress Log

Living document — updated by the controller after every implementer subagent run.

**Plan:** `docs/migration/phase2j-plan.md` (commit `3f2c33f`)
**Design:** `docs/migration/phase2j-design.md` (commit `368dbda`)
**Predecessor:** `jquantlib-phase2i.6-complete` @ `44be66c`
**Phase 2j start tip on main:** `3f2c33f`
**Baseline:** Tests `688/0/0/22`, scanner `0 stubs`
**Current tip on main:** `7ec9333` (WI-4.0c complete)

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

### L1 — WI-1 model layer (4 sub-commits, sequential, worktree A) ✅

#### Sub-layer 1.1 — Gaussian1dModel base ✅
Commit `b22afae` (worktree A → main). `Gaussian1dModel.java` abstract base + `Gaussian1dModelTest`.

#### Sub-layer 1.2 — GsrProcessCore + GsrProcess ✅
Commit `3b29638` (worktree A → main). `GsrProcessCore.java` + `GsrProcess.java`; align commit `9b26a30` for friend-pattern setters + pairKey fix.

#### Sub-layer 1.3 — Gsr concrete model ✅
Commit `1e70e44` (worktree A → main). `Gsr.java` concrete model.

#### Sub-layer 1.4 — Gaussian1dSmileSection + Gaussian1dSwaptionVolatility ✅
Commit `b46f065` (worktree A → main). Align commit `f931ce2` for SmileSection API (volatilityType/shift/optionPrice + BlackFormula shift fix).

### L2 — WI-2 standard engines + WI-3 niche engines (parallel after WI-1.4 lands)

#### WI-2 (worktree B): SwaptionEngine + CapFloorEngine
- WI-2.1 Gaussian1dSwaptionEngine: **in progress** (worktree B)
- WI-2.2 Gaussian1dCapFloorEngine: pending after 2.1

#### WI-3 (worktree C): Jamshidian + Nonstandard + FloatFloat
- WI-3.1 Gaussian1dJamshidianSwaptionEngine ✅ — commit `24c0a8e` → main
- WI-3.2 Gaussian1dNonstandardSwaptionEngine: pending
- WI-3.3 Gaussian1dFloatFloatSwaptionEngine: pending

### L3 — WI-4 MarkovFunctional (parallel after WI-1.1 lands)

**Per Option A scope expansion (P2J-11):** WI-4 expanded to 4 sub-commits. Sub-commit order:

#### Sub-commit 4.0a — MfStateProcess prereq (~179 LOC C++) ✅
Commit `0aee3f4` → main. `MfStateProcess.java` + `MfStateProcessTest`.

#### Sub-commit 4.0b — SmileSectionUtils prereq (~278 LOC C++) ✅
Commits `4dec5d8` + align `f931ce2` → main. `SmileSectionUtils.java` + `SmileSectionUtilsTest`.

#### Sub-commit 4.0c — KahaleSmileSection prereq (~450 LOC C++) ✅
Commit `7ec9333` → main. `KahaleSmileSection.java` + `KahaleSmileSectionTest`.
Key fixes: CFunction.eval N(d1) saturation for d1>8.2; Halley-refined invNormal; local
blackFormulaImpliedStdDevKahale with maxStdDev=24.0 matching C++ (Java BlackFormula uses 3.0
causing 1e-6 bisection path divergence). All 5 scenarios A-E pass at TIGHT tier, 790 tests.

#### Sub-commit 4.0d — MarkovFunctional (~1710 LOC C++)
_(Pending — dispatch after 4.0c lands; WI-4.0c now complete on main)_

### L4 — completion doc + tag
_(Not yet started)_

## Test count tracking

| Event | Tests | Failures | Errors | Skipped | Notes |
|-------|-------|----------|--------|---------|-------|
| Phase 2j start (`3f2c33f`) | 688 | 0 | 0 | 22 | baseline |
| WI-1.1 (`b22afae`) | 692 | 0 | 0 | 22 | +4 Gaussian1dModelTest |
| WI-1.2 (`3b29638`) | 692 | 0 | 0 | 22 | no new tests |
| WI-1.3 (`1e70e44`) | 692 | 0 | 0 | 22 | no new tests |
| WI-1.4 (`b46f065`) | 693 | 0 | 0 | 22 | +1 Gaussian1dVolTest |
| WI-3.1 (`24c0a8e`) | 694 | 0 | 0 | 22 | +1 JamshidianSwaptionEngineTest |
| WI-4.0a (`0aee3f4`) | 695 | 0 | 0 | 22 | +1 MfStateProcessTest |
| WI-4.0b (`4dec5d8`) | 696 | 0 | 0 | 22 | +1 SmileSectionUtilsTest |
| WI-4.0c (`7ec9333`) | 790 | 0 | 0 | 22 | +94 (KahaleSmileSectionTest covers 5 scenarios × many strikes) |
