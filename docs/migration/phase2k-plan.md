# Phase 2k Implementation Plan — JQuantLib Migration

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Compact plan — Phase 2j.5 precedent + design §3 establish the operational pattern. Tracks A/B/C dispatched in parallel after L0.

**Goal:** Close 4 documented stubs from Phase 2j/2j.5 — SabrInterpolatedSmileSection (unblocks MF SabrSmile), BasketGeneratingEngine (unblocks Nonstandard/FloatFloat basket helpers), TqrEigenDecomposition lift (refactor), CustomSmileFactory MF inner class (unblocks MF CustomSmile). Tests `801 → ~810`; scanner WIP=0; tag `jquantlib-phase2k-complete`.

**Architecture:** Same as Phase 2j.5 — direct commits to `main`, TDD per artifact, probe-before-port, JQuantMath from day one. 3 worktrees per design §3.

**Tech Stack:** Java 11 / Maven / JUnit 4; C++17 / CMake / QuantLib v1.42.1 pinned via submodule (`099987f0ca2c11c505dc4348cdb9ce01a598e1e5`); git worktrees.

---

## L0 — Pre-flight + 3 worktrees + progress doc

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
# Confirm 801/0/0/22 + scanner 0 + tag jquantlib-phase2j.5-complete @ 22f65b8
git fetch origin
git worktree add -b phase-2k-A-sabr-smile /Users/josemoya/eclipse-workspace/jquantlib-2k-A main
git worktree add -b phase-2k-B-basket-generating /Users/josemoya/eclipse-workspace/jquantlib-2k-B main
git worktree add -b phase-2k-C-tqr-customsmile /Users/josemoya/eclipse-workspace/jquantlib-2k-C main
for wt in A B C; do
  cd /Users/josemoya/eclipse-workspace/jquantlib-2k-$wt
  git submodule update --init --recursive
done
```

Init `docs/migration/phase2k-progress.md` (Phase 2j.5 shape) + commit.

---

## Track A — SabrInterpolatedSmileSection + MF SabrSmile wiring (worktree A, 1 commit)

- C++: `ql/termstructures/volatility/sabrinterpolatedsmilesection.{hpp,cpp}` (~338 LOC)
- Java target: `org.jquantlib.termstructures.volatilities.SabrInterpolatedSmileSection`
- Probe: `sabr_interpolated_smile_section_probe.cpp` — fit Sabr params to a small set of market quotes, fingerprint volatility(strike) + alpha/beta/nu/rho. ~30-50 cases. TIGHT.
- MF wiring: edit `MarkovFunctional.java` `updateSmiles()` SabrSmile branch to instantiate SabrInterpolatedSmileSection (currently throws). Update `validate()` to remove SabrSmile from the throw list. Add a small MF integration test if practical.
- Commit: `infra(termstructures.volatilities,model.shortrate.onefactormodels.gaussian1d): port SabrInterpolatedSmileSection + wire MF SabrSmile branch (Phase 2k Track A)`

---

## Track B — BasketGeneratingEngine + Nonstandard/FloatFloat basket helpers (worktree B, 1 commit)

- C++: `ql/pricingengines/swaption/basketgeneratingengine.{hpp,cpp}` (~479 LOC)
- Java target: `org.jquantlib.pricingengines.swaption.BasketGeneratingEngine`
- Probe: `basket_generating_engine_probe.cpp` — compute basket for representative Nonstandard/FloatFloat exposures. ~30-50 cases. TIGHT default; LOOSE-with-A19 acceptable for deep numerical paths.
- Engine wiring: edit `Gaussian1dNonstandardSwaptionEngine.java` and `Gaussian1dFloatFloatSwaptionEngine.java` to wire BasketGeneratingEngine basket-helper methods (currently throw UnsupportedOperationException). Update `NonstandardSwaption.calibrationBasket()` and `FloatFloatSwaption.calibrationBasket()` to delegate.
- Commit: `infra(pricingengines.swaption): port BasketGeneratingEngine + wire Nonstandard/FloatFloat basket helpers (Phase 2k Track B)`

---

## Track C — TqrEigenDecomposition lift + CustomSmileFactory + MF CustomSmile wiring (worktree C, 2 commits)

### C.1 — Lift TqrEigenDecomposition to math.matrixutilities

- Source: extract from inside `GaussianQuadrature` (private inner class) to public class `org.jquantlib.math.matrixutilities.TqrEigenDecomposition`
- Existing tests (Phase 2j.5 C.1) continue to validate; no new probe required (refactor is behavior-preserving)
- Update `GaussianQuadrature` to use the public `TqrEigenDecomposition` instead of its private copy
- Optional: add a small unit test for `TqrEigenDecomposition` itself (eigenpair correctness on a known symmetric matrix)
- Commit: `refactor(math.matrixutilities): lift TqrEigenDecomposition from GaussianQuadrature private inner to public class (Phase 2k Track C.1)`

### C.2 — CustomSmileFactory MF inner class + MF CustomSmile wiring

- Source: C++ `markovfunctional.hpp:107-115` defines `CustomSmileFactory` as a public inner abstract class with single virtual `smileSection(...)` method
- Java target: add `CustomSmileFactory` as a public static abstract class inside `MarkovFunctional` (mirror C++ inner class structure)
- Wire `MarkovFunctional.ModelSettings.withCustomSmileFactory(...)` setter
- Edit `MarkovFunctional.updateSmiles()` CustomSmile branch to invoke the factory (currently throws)
- Update `validate()` to remove CustomSmile from the throw list
- Probe: not strictly needed (the C++ test-suite for MF doesn't exercise CustomSmile directly because it's user-supplied). Add a small Java unit test that builds a stub CustomSmileFactory returning a FlatSmileSection and verifies MF accepts it without throwing.
- Commit: `infra(model.shortrate.onefactormodels.gaussian1d): add CustomSmileFactory MF inner class + wire CustomSmile branch (Phase 2k Track C.2)`

---

## L2 — Completion + tag + memory + README + teardown

- Write `docs/migration/phase2k-completion.md` (Phase 2j.5 shape)
- Tag `jquantlib-phase2k-complete`
- Update memory + README per milestone-doc discipline
- Tear down 3 worktrees + delete branches local + remote
