# Phase 2l Progress Log

**Plan + Design:** `docs/migration/phase2l-{design,plan}.md` (commit `91ef33c`)
**Predecessor:** `jquantlib-phase2k-complete` @ `70e5007`
**Phase 2l start tip on main:** `91ef33c`
**Baseline:** Tests `809/0/0/22`, scanner `0 stubs`
**Operating mode:** Autonomous

## Worktrees

| WT | Branch | Scope |
|----|--------|-------|
| A | `phase-2l-A-iterative-solvers` | BiCGStab + GMRES |
| B | `phase-2l-B-step-conditions` | FdmAmericanStepCondition + FdmBermudanStepCondition + FdmDividendHandler + vanillaComposite wiring |
| C | `phase-2l-C-schemes` | 6 schemes sequential: ExplicitEuler → CrankNicolson → CraigSneyd → ModifiedCraigSneyd → MethodOfLines → TrBDF2 |

## Pause-trigger status

All carry-forward triggers not fired.

## Layer / Track progress

### L0 ✅
3 worktrees + submodules init'd.

### Track A — BiCGStab + GMRES — pending
### Track B — Step conditions + FdmDividendHandler + vanillaComposite wiring — pending
### Track C — 6 schemes (sequential within track) — pending
### L2 — completion + tag + memory + README + teardown — pending

## Test count tracking

| Event | Tests | Failures | Errors | Skipped |
|-------|-------|----------|--------|---------|
| Phase 2l start (`91ef33c`) | 809 | 0 | 0 | 22 |
