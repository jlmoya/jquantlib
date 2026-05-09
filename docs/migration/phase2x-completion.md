# Phase 2x Completion — Small Infrastructure Aligns + WeakRef Cascade Fix

**Status:** complete (autonomous mode — fourteenth autonomous phase)
**Tag:** `jquantlib-phase2x-complete` @ `a710e9b`
**Predecessor:** `jquantlib-phase2v-complete` @ `d1ad118`
**Plan + Design:** `docs/migration/phase2x-{design,plan}.md`

## Final state

| Metric | Phase 2v tip | Phase 2x tip | Δ |
|--------|--------------|--------------|----|
| Tests | 950/0/0/39 | 951/0/0/38 | +1 active / -1 skipped |
| **mvn test wall-clock** | **30+ min** | **59.5 s** | **>30x speedup** 🚀 |
| Scanner WIP | 0 | 0 | unchanged |

## What landed (5 commits)

| Commit | Description |
|--------|-------------|
| `700eccf` | Phase 2x design + plan |
| `bc2319a` | **A.1:** `InterpolatedZeroCurve` constructor — remove stale `yields[0]==1.0` assertion (was copy-paste from `InterpolatedDiscountCurve`, blocking InterpolatedZeroCurve usage with raw zero rates). Bonus: fixed `Closeness.isClose` polarity bug in same file (line 132 was inverted — required duplicates, now correctly rejects them). |
| `a727c02` | **A.2:** `CPILeg` builder class (~330 LOC, mirrors C++ ql/cashflows/cpicoupon.{hpp,cpp}) + `CashFlows.npv(Leg, ...)` and `CashFlows.accruedAmount(Leg, ...)` static overloads. Un-ignored CPIBondTest.testCPILegWithoutBaseCPI + body-filled with full C++ port (passes within 1e-8 tolerance for clean-price 394.79676680). |
| `2bba795` | **A.3:** `IborCoupon.Settings` nested singleton with `usingAtParCoupons()` static accessor per C++ v1.42.1. Preparatory production align — no test callers yet (CPISwapTest body-fill deferred to Phase 2y). |
| `a710e9b` | **A.4 (the win):** `align(util.WeakReferenceObservable)` — switched 12 production classes from `DefaultObservable` to `WeakReferenceObservable` with batched notification + lazy compact-on-iterate. **mvn test wall-clock 30+ min → 59.5s (>30x)**. DividendOptionTest.testEuropeanGreeks 750+s → 0.916s. AsianOptionTest 7+min → 0.779s. New slowest test: Gaussian1dFloatFloatSwaptionEngineTest 20.5s. |

## A-trigger fire history

| Trigger | Where | Outcome |
|---------|-------|---------|
| **A33 resolved** | A.4 | WeakReferenceObservable cascade slowdown identified in Phase 2v — 32x improvement landed. mvn test now production-quality (<1 min). |
| **A29 (test exercises divergence)** | A.1 retry | InflationVolatilityTest.testYoYPriceSurfaceToATM still @Ignore'd — A.1 unblocked construction but PiecewiseYoYInflationCurve bootstrap throws "date before reference date" inside CashFlows.bps independently. Phase 2y carry-forward. |
| **Bonus bug fix** | A.1 | Closeness.isClose polarity bug fixed alongside the main InterpolatedZeroCurve fix. |

A1-A28, A30-A32, A34 did not fire.

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P2X-1** | A.4 architectural mitigation chosen over deeper rewrite | Switching 12 hot-path Observable delegates to WeakReferenceObservable + lazy compact achieved >30x speedup with minimal risk; full LazyObject pattern deferred to Phase 2y |
| **P2X-2** | A.4 API impact: callers must hold strong ref to registered Observers | Standard observer-pattern contract; verified no anonymous/lambda/register-and-forget observers in production grep; all 951 tests pass |
| **P2X-3** | A.3 IborCoupon.Settings landed without test caller | Production-side preparatory; CPISwapTest.consistency body-fill deferred to Phase 2y as ~50 LOC fixture wiring task |
| **P2X-4** | Direct-to-main signed `-s` no Co-authored-by | Standing rule |

## Phase 2y+ seed list (refined after Phase 2x un-blocks)

### Phase 2y — Body fills + remaining un-ignores (low-medium effort)

1. **CPISwapTest.consistency body-fill** — production-side fully unblocked by Phase 2x A.1+A.3; needs ~50 LOC fixture wiring (CommonVars + 29-pillar nominal curve + UKRPI fixings + DiscountingSwapEngine + 1e-5/3e-5 tolerance branch).
2. **InflationVolatilityTest.testYoYPriceSurfaceToATM** — Phase 2x A.1 unblocked construction but PiecewiseYoYInflationCurve bootstrap fails ("date before reference date" in CashFlows.bps during YearOnYearInflationSwap bootstrap). Investigate + fix.
3. **5 retained Phase 2u Track F @Ignore'd InflationTest tests** — body-fill in light of Phase 2v + 2x infra additions.
4. **InflationVolatilityTest.testYoYPriceSurfaceToVol** — documented C++ `\bug Tests currently fail` per v1.42.1 headers; @Ignore can stay until upstream fixes.
5. **AbstractTermStructure → LazyObject proper cycle prevention** — Phase 2u Track F added single-method updating_ guard; Phase 2x A.4 mitigated wall-clock; proper LazyObject would be most semantically correct but architectural.
6. **IndexManager test isolation lint pass** — mirrors C++ TopLevelFixture::clearHistories(); mechanical sweep.

### Phase 3+ subsystem ports (post-inflation)

7. **`termstructures/credit/`** + tests (~2,444 LOC C++ + tests) — clean greenfield.
8. **`models/marketmodels/`** + tests (~25-30K + tests).
9. **`experimental/`** (non-inflation, non-credit) + tests.
10. **Remaining C++ test-suite files** (~70+ cpp files in test-suite/ beyond inflation, full rigor).

## Out-of-scope (explicit, deferred)

- All Phase 2y+ items above
- BroadieKaya retry — needs lgamma
- NCCS EXACT — needs lgamma
- JQuantMath.lgamma — still no source
