# Phase 2j.5 Progress Log

Living document — updated by the controller after every implementer subagent run.

**Plan:** `docs/migration/phase2j.5-plan.md` (commit `efa487b`)
**Design:** `docs/migration/phase2j.5-design.md` (commit `66f3d3c`)
**Predecessor:** `jquantlib-phase2j-complete` @ `8808985`
**Phase 2j.5 start tip on main:** `7828a9d`
**Baseline:** Tests `792/0/0/22`, scanner `0 stubs`
**Operating mode:** Autonomous (per 2026-05-02 user directive — controller decides scope/sequencing without per-phase user gates)

## Worktrees

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2j5-A` | `phase-2j5-A-nonstandard` | NonstandardSwap → NonstandardSwaption → Gaussian1dNonstandardSwaptionEngine |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2j5-B` | `phase-2j5-B-floatfloat` | FloatFloatSwap → FloatFloatSwaption → Gaussian1dFloatFloatSwaptionEngine |
| C | `/Users/josemoya/eclipse-workspace/jquantlib-2j5-C` | `phase-2j5-C-markovfunctional` | GaussHermiteIntegration family → AtmSmileSection → MarkovFunctional |

## Pause-trigger status

- A2 / A3 / A8 / A9 / A15 / A16 / A17 / A18 / A19 / A20 / A21: all not fired (carry-forward from Phase 2j)
- A22 NEW (tertiary missing-dep): not fired
- A6 disabled per memory `feedback_phase2a_no_a6.md`

## Layer / Track progress

### L0 — pre-flight + worktree setup ✅

- L0.1 baseline confirmed (`792/0/0/22`, scanner 0 stubs, tip `7828a9d`, submodule pin `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`)
- L0.2 3 worktrees created off main tip `7828a9d`; submodules init'd in each
- L0.3 design + plan + progress doc + memory + README updated (per milestone-doc discipline)

### Track A — Nonstandard engine (worktree A, 3 sub-commits sequential)

- A.1 NonstandardSwap: pending
- A.2 NonstandardSwaption: pending (after A.1)
- A.3 Gaussian1dNonstandardSwaptionEngine: pending (after A.2)

### Track B — FloatFloat engine (worktree B, 3 sub-commits sequential, parallel with A)

- B.1 FloatFloatSwap: pending
- B.2 FloatFloatSwaption: pending (after B.1)
- B.3 Gaussian1dFloatFloatSwaptionEngine: pending (after B.2)

### Track C — MarkovFunctional (worktree C, 3 sub-commits sequential, parallel with A and B)

- C.1 GaussHermiteIntegration family: pending
- C.2 AtmSmileSection: pending (after C.1)
- C.3 MarkovFunctional: pending (after C.2)

### L2 — completion doc + tag + memory + README + teardown
_(Not yet started)_

## Test count tracking

| Event | Tests | Failures | Errors | Skipped | Notes |
|-------|-------|----------|--------|---------|-------|
| Phase 2j.5 start (`7828a9d`) | 792 | 0 | 0 | 22 | baseline |
