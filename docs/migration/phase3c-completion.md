# Phase 3c Completion — IntegralCdsEngine + MakeCDS + DateGeneration.CDS + Un-ignore Sweep

**Status:** complete (autonomous mode — seventeenth autonomous phase)
**Tag:** `jquantlib-phase3c-complete` @ `4fa8c9c`
**Predecessor:** `jquantlib-phase3b-complete` @ `763fb71`
**Plan + Design:** `docs/migration/phase3c-{design,plan}.md`

## Final state

| Metric | Phase 3b tip | Phase 3c tip | Δ |
|--------|--------------|--------------|----|
| Tests | 1004/0/0/51 | 1018/0/0/44 | +14 active / -7 skipped |
| mvn test wall-clock | 59.2 s | 59.4 s | unchanged ✓ |
| Scanner WIP | 0 | 0 | unchanged |
| Credit subsystem coverage | + MidPoint engine + helpers | + Integral engine + MakeCDS + DateGeneration.CDS + 7 of 10 Track C tests un-ignored | substantial |

## What landed (5 commits)

| Commit | Description |
|--------|-------------|
| `bae72e0` | Phase 3c design + plan |
| `d7e58ae` | **L0 A.1:** DateGeneration.CDS/CDS2015/OldCDS enum values + Schedule rule support per C++ v1.42.1. ~250 LOC main + 140 test + 130 LOC C++ probe. New WeekendsOnly calendar. CdsScheduleTest 6 cases. |
| `0d512d2` | **L0 A.2:** PiecewiseDefaultCurve IterativeBootstrap initial-guess refinement. ~140 LOC refactor. Un-ignored Phase 3a's testLogLinearSurvivalConsistency (LogLinear bootstrap now converges). |
| `5ffd64a` | **L0 A.3:** MakeCreditDefaultSwap factory (~290 LOC main + 50 test) + cdsMaturity helper. Un-ignored Track C's testAccrualRebateAmounts (10 ISDA cases match within 0.01). |
| `41f3ada` | **L1 B:** IntegralCdsEngine port (~290 LOC main + 190 test + 190 LOC C++ probe). 8 IntegralCdsEngineTest cases all green. |
| `4fa8c9c` | **L1 C:** un-ignore + body-fill 5 CreditDefaultSwapTest MidPoint-dependent tests post-Phase-3b. testCachedValue (NPV 295.0153, fair-rate 0.0075175), testCachedMarketValue (NPV -1.364048777 at 1e-9), testImpliedHazardRate (5 maturities monotonic), testFairSpread (re-priced NPV ≈ 0), testFairUpfront (both upfront variants ≈ 0). |

## Drive-by fixes folded into commits

- **Settings.TODAYS_PAYMENTS default mismatch** — Java initial value `true` vs C++ `Settings::includeReferenceDateEvents` defaults to `false`. C++ test cached values rely on `false`. Track C tests now toggle locally (folded into 4fa8c9c).
- **InterpolatedDiscountCurve.initialize 2 pre-existing bugs:**
  - `QL.require(Closeness.isClose(times[i], times[i-1]), ...)` was inverted — flipped to `!isClose`
  - `QL.require(data[0] > 0, ...)` always checked index 0 — corrected to `data[i] > 0`
- **CreditDefaultSwap.init** now reads schedule's rule and treats CDS/CDS2015 schedules as post-Big-Bang (allows protectionStart > schedule[0])
- **Schedule.previousTwentieth/nextTwentieth** elevated to public static helpers

## A-trigger fire history

| Trigger | Where | Outcome |
|---------|-------|---------|
| **A26 (cross-cutting align prereqs)** | L0 A.1, A.2, A.3, L1 C | 4 substantive align findings (Settings.TODAYS_PAYMENTS, InterpolatedDiscountCurve 2x bugs, CreditDefaultSwap init) folded into respective commits per minimal-touch rule |
| **A29 (test exercises divergence)** | L1 C un-ignore retry | testIterativeBootstrapRetries re-ignored with refined Phase 3d rationale: needs IterativeBootstrap configuration object + dontThrow fallback mode |

A1-A25, A27, A28, A30-A35 did not fire.

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P3C-1** | DateGeneration.CDS / CDS2015 / OldCDS implemented at enum level + Schedule integration | C++ Big-Bang rules need first-class enum support |
| **P3C-2** | New WeekendsOnly calendar | C++ DateGeneration.CDS default; previously absent in Java |
| **P3C-3** | testIterativeBootstrapRetries re-ignored | IterativeBootstrap config object architectural — Phase 3d natural scope |
| **P3C-4** | Direct-to-main signed `-s` no Co-authored-by | Standing rule |

## Phase 3d+ seed list

### Phase 3d — IsdaCdsEngine + final un-ignore (~600 LOC C++)

1. **IsdaCdsEngine** — port from `ql/pricingengines/credit/isdacdsengine.{hpp,cpp}` (488 LOC C++, sophisticated)
2. **IsdaCdsEngine helper functions** — typically extra ISDA-specific utility code
3. **IterativeBootstrap configuration object** + dontThrow / dontThrowFallback mode (~30 LOC)
4. **Un-ignore Track C's 4 Isda tests** (testIsdaEngine, testIsdaCalculatorReconcileSingleQuote, testIsdaCalculatorReconcileSingleWithIssueDateInThePast, testDefaultConventions) — verify pass with IsdaCdsEngine
5. **Un-ignore Phase 3a's testIterativeBootstrapRetries** + testUpfrontBootstrap (after IterativeBootstrap config + DateGeneration.CDS already landed in 3c)
6. **Actual360(true) DayCounter variant** + FixedRateLeg.withLastPeriodDayCounter — minor MakeCDS gap

### Phase 3e+ subsystem ports

7. **`models/marketmodels/`** + tests (~25-30K + tests) — Libor Market Model
8. **`experimental/`** (non-inflation, non-credit) + tests (~40-60K + tests)
9. **Remaining C++ test-suite files** (~70+ cpp files in test-suite/, full rigor)

### Phase 2y still pending

10. CPISwapTest.consistency body-fill (~50 LOC)
11. InflationVolatilityTest.testYoYPriceSurfaceToATM PiecewiseYoY bootstrap fix
12. 5 retained Phase 2u Track F @Ignore'd InflationTest body-fills
13. AbstractTermStructure → LazyObject proper cycle prevention (Phase 2x A.4 mitigated)
14. IndexManager test isolation lint pass

## Out-of-scope (explicit, deferred)

- All Phase 3d+ items above
- BroadieKaya retry — needs lgamma
- NCCS EXACT — needs lgamma
- JQuantMath.lgamma — still no source
