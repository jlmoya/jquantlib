# Phase 3d Implementation Plan

> Single worktree A. L0 small foundations + L1 IsdaCdsEngine + final un-ignore.

**Goal:** Credit subsystem 100% v1.42.1 coverage. Tag `jquantlib-phase3d-complete`.

## L0 — Pre-flight + worktree setup

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-3d-A /Users/josemoya/eclipse-workspace/jquantlib-3d-A main
cd /Users/josemoya/eclipse-workspace/jquantlib-3d-A
git submodule update --init --recursive
```

## L0 A.1 — IterativeBootstrap configuration object

- C++ source: `migration-harness/cpp/quantlib/ql/termstructures/iterativebootstrap.hpp` — find IterativeBootstrap configuration ctor params (maxAttempts, minFactor, maxFactor, dontThrow, dontThrowFallback)
- Java target: extend existing inline IterativeBootstrap loop in `PiecewiseDefaultCurve` to accept a config object; add factory ctor on PiecewiseDefaultCurve
- Verify: un-ignore `testIterativeBootstrapRetries` (Phase 3a)
- Commit: `align(termstructures.credit): PiecewiseDefaultCurve IterativeBootstrap config object + dontThrow fallback (Phase 3d L0 A.1)`

## L0 A.2 — Actual360(true) variant + FixedRateLeg.withLastPeriodDayCounter

- C++ source: `ql/time/daycounters/actual360.hpp` (Actual360(bool includeLastDay)) + `ql/cashflows/fixedratecoupon.hpp` (withLastPeriodDayCounter)
- Java targets: extend `Actual360` constructor; wire `FixedRateLeg.withLastPeriodDayCounter` to actually use the parameter (currently ignored per Phase 3b note)
- Verify: applicable to MakeCreditDefaultSwap; smoke test
- Commit: `align(time,cashflow): Actual360(true) variant + FixedRateLeg.withLastPeriodDayCounter wiring (Phase 3d L0 A.2)`

## L1 — IsdaCdsEngine

- C++ source: `migration-harness/cpp/quantlib/ql/pricingengines/credit/isdacdsengine.{hpp,cpp}` (488 LOC)
- Java target: `org.jquantlib.pricingengines.credit.IsdaCdsEngine`
- Sophisticated: ISDA standard CDS pricing methodology (calibration to running spread / upfront, ISDA accrual conventions, etc.)
- Probe + tests
- **Un-ignore Track C 4 Isda tests** (testIsdaEngine, testIsdaCalculatorReconcileSingleQuote, testIsdaCalculatorReconcileSingleWithIssueDateInThePast, testDefaultConventions) — verify pass
- **Un-ignore Phase 3a's testUpfrontBootstrap** — verify pass with IsdaCdsEngine + IterativeBootstrap config (from L0 A.1)
- Commit: `infra(pricingengines.credit): port IsdaCdsEngine + un-ignore Phase 3a/Track C Isda tests (Phase 3d L1)`

## L2 — Completion + tag + memory + README + teardown

Standard milestone-doc discipline.
