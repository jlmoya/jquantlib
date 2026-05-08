# Phase 2m Implementation Plan

> REQUIRED SUB-SKILL: superpowers:subagent-driven-development. 4 parallel tracks dispatched after L0.

**Goal:** Phase 2h carry-forward Fdm-dependent engines now unblocked + AndreasenHuge LocalVol surfaces. Tests `812 → ~825`; tag `jquantlib-phase2m-complete`.

## L0 — Pre-flight + 4 worktrees + progress doc

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-2m-A-fd-black-scholes /Users/josemoya/eclipse-workspace/jquantlib-2m-A main
git worktree add -b phase-2m-B-fd-heston-hullwhite /Users/josemoya/eclipse-workspace/jquantlib-2m-B main
git worktree add -b phase-2m-C-fd-sabr /Users/josemoya/eclipse-workspace/jquantlib-2m-C main
git worktree add -b phase-2m-D-andreasenhuge /Users/josemoya/eclipse-workspace/jquantlib-2m-D main
for wt in A B C D; do
  cd /Users/josemoya/eclipse-workspace/jquantlib-2m-$wt
  git submodule update --init --recursive
done
```

Init `phase2m-progress.md` + commit.

## Track A — FdBlackScholesVanillaEngine (worktree A, 1 commit)

- C++: `ql/pricingengines/vanilla/fdblackscholesvanillaengine.{hpp,cpp}` (151+296=447 LOC)
- Java target: `org.jquantlib.pricingengines.vanilla.FdBlackScholesVanillaEngine`
- Probe: vanilla European/American option NPVs across (strike, expiry, vol, type) grid + DiscreteDividendOption variants. ~30-50 cases. TIGHT default; LOOSE/A19 if numerical depth.
- Test: probe-driven, ONE @Test, collect-all-failures
- Commit: `infra(pricingengines.vanilla): port FdBlackScholesVanillaEngine (Phase 2m Track A)`

## Track B — FdHestonHullWhiteVanillaEngine (worktree B, 1 commit + possibly 1 prereq)

- C++: `ql/pricingengines/vanilla/fdhestonhullwhitevanillaengine.{hpp,cpp}` (96+245=341 LOC)
- Java target: `org.jquantlib.pricingengines.vanilla.FdHestonHullWhiteVanillaEngine`
- Likely prereq: `HestonHullWhiteProcess` (cross-product process) — check if Java has it; if not, port as a sub-task
- Probe: vanilla NPV under Heston-HW model. ~20-40 cases. TIGHT/LOOSE.
- Commit: `infra(pricingengines.vanilla): port FdHestonHullWhiteVanillaEngine (Phase 2m Track B)`

## Track C — FdSabrVanillaEngine (worktree C, 1 commit)

- C++: `ql/pricingengines/vanilla/fdsabrvanillaengine.{hpp,cpp}` (61+146=207 LOC)
- Java target: `org.jquantlib.pricingengines.vanilla.FdSabrVanillaEngine`
- Probe: SABR vanilla NPV. ~20-40 cases. TIGHT/LOOSE.
- Commit: `infra(pricingengines.vanilla): port FdSabrVanillaEngine (Phase 2m Track C)`

## Track D — AndreasenHuge LocalVol family (worktree D, 3 sub-commits sequential)

- C++ files (sequential port order — interpolator first, then 2 adapters):
  - **D.1** `andreasenhugevolatilityinterpl.{hpp,cpp}` (152+633=785 LOC) — the core interpolator (largest)
  - **D.2** `andreasenhugevolatilityadapter.{hpp,cpp}` (57+69=126 LOC) — vol surface adapter
  - **D.3** `andreasenhugelocalvoladapter.{hpp,cpp}` (57+63=120 LOC) — local-vol adapter
- Java target: `org.jquantlib.termstructures.volatilities.equityfx.{AndreasenHugeVolatilityInterpolation,AndreasenHugeVolatilityAdapter,AndreasenHugeLocalVolAdapter}`
- Probes per file: surface eval + interpolation correctness against C++ reference. TIGHT.
- Commits: `infra(termstructures.volatilities.equityfx): port <className> (Phase 2m Track D.<n>)`

## L2 — Completion + tag + memory + README + teardown

Standard milestone-doc discipline: `phase2m-completion.md`, tag, MEMORY.md + project_jquantlib_migration.md + README, worktree teardown, branch cleanup.
