# Phase 2m Progress Log

**Plan + Design:** `docs/migration/phase2m-{design,plan}.md` (commit `943d904`)
**Predecessor:** `jquantlib-phase2l-complete` @ `9dab878`
**Phase 2m start tip on main:** `943d904`
**Baseline:** Tests `812/0/0/22`, scanner `0 stubs`
**Operating mode:** Autonomous

## Worktrees

| WT | Branch | Scope |
|----|--------|-------|
| A | `phase-2m-A-fd-black-scholes` | FdBlackScholesVanillaEngine (~447 LOC C++) |
| B | `phase-2m-B-fd-heston-hullwhite` | FdHestonHullWhiteVanillaEngine (~341 LOC C++; possible HestonHullWhiteProcess prereq) |
| C | `phase-2m-C-fd-sabr` | FdSabrVanillaEngine (~207 LOC C++) |
| D | `phase-2m-D-andreasenhuge` | AndreasenHuge LocalVol family (3 sub-commits sequential, ~1031 LOC C++) |

## Pause-trigger status

All carry-forward triggers + new A23 (engine port reveals deeper-than-expected dep) — none fired.

## L0 ✅

3 worktrees + submodules init'd.

## Track progress

- Track A FdBlackScholesVanilla — pending
- Track B FdHestonHullWhite — pending
- Track C FdSabrVanilla — pending
- Track D AndreasenHuge family (D.1 Interpl → D.2 VolAdapter → D.3 LocalVolAdapter) — pending

## Test count tracking

| Event | Tests | Failures | Errors | Skipped |
|-------|-------|----------|--------|---------|
| Phase 2m start (`943d904`) | 812 | 0 | 0 | 22 |
