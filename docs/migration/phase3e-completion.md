# Phase 3e Completion — Markit Fixture Investigation + 4 PiecewiseYieldCurve Align Fixes

**Status:** complete (DONE_WITH_CONCERNS) — autonomous mode — nineteenth autonomous phase
**Tag:** `jquantlib-phase3e-complete` @ `26f7433`
**Predecessor:** `jquantlib-phase3d-complete` @ `d34750a`
**Plan + Design:** `docs/migration/phase3e-{design,plan}.md`

## Final state

| Metric | Phase 3d tip | Phase 3e tip | Δ |
|--------|--------------|--------------|----|
| Tests | 1024/0/0/41 | 1024/0/0/41 | unchanged |
| mvn test wall-clock | 60.0 s | 60.0 s | unchanged |
| Scanner WIP | 0 | 0 | unchanged |
| Pre-existing bugs fixed | — | 4 PiecewiseYieldCurve infrastructure bugs | substantial |
| Markit reconciliation tests | 3 @Ignore'd skeletons | 3 @Ignore'd full bodies (Phase 3f architectural unblocker pending) | bodies-ready |

## What landed (3 commits)

| Commit | Description |
|--------|-------------|
| `c5af0ac` | Phase 3e design + plan |
| `5819acd` | **A.1:** 4 align fixes for PiecewiseYieldCurve infrastructure surfaced when first exercising end-to-end bootstrap (PiecewiseYieldCurveTest is fully @Ignore'd in repo): (1) InterpolatedDiscount/Zero/ForwardCurve `(int settlementDays, Calendar, ...)` constructors were ignoring supplied calendar (NPE on referenceDate query); (2) Discount.updateGuess used Arrays.fill instead of data[i]=value (clobbered earlier nodes); (3) PiecewiseYieldCurve.discount(t/Date) bypassed calculate() (bootstrap never ran); (4) IterativeBootstrap.calculate Array size mismatch (full size vs partial). |
| `26f7433` | **A.2:** Full body ports (461 LOC) for 3 Markit-reconciliation tests (testIsdaEngine, testIsdaCalculatorReconcileSingleQuote, testIsdaCalculatorReconcileSingleWithIssueDateInThePast). Bodies fully port C++ logic but tests stay @Ignore'd with refined Phase 3f rationale: deeper Interpolation copy-vs-reference architectural issue (Array(double[]) System.arraycopy + AbstractInterpolation reads from copy, IterativeBootstrap.BootstrapError.op writes to source). Current run NPV -15897.6 vs Markit -16070.7 (1.07% off; budget 1e-3 PERCENT = 1e-5 fraction). |

## A-trigger fire history

| Trigger | Where | Outcome |
|---------|-------|---------|
| **A26 (cross-cutting align prereq)** | A.1 | 4 PiecewiseYieldCurve infrastructure bugs surfaced and bundled as separate align commit per project rule |
| **A29 (test exercises divergence)** | A.2 | 3 tests bodied + @Ignore'd with refined architectural rationale (Phase 3f Interpolation copy-vs-reference fix). No silent skip; bodies present for one-line un-ignore once architectural fix lands. |

A1-A25, A27, A28, A30-A35 did not fire.

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P3E-1** | 4 align fixes in PiecewiseYieldCurve are minimal additive — bigger architectural fix deferred | A.1 lands the surface bugs; A.2 documents the deeper one for Phase 3f |
| **P3E-2** | Test bodies committed even though @Ignore'd | Per binding rigor — bodies present + dependency map visible; one-line un-ignore in Phase 3f |
| **P3E-3** | Direct-to-main signed `-s` no Co-authored-by | Standing rule |

## Phase 3f+ seed list

### Phase 3f — Interpolation copy-vs-reference architectural fix (~50-200 LOC, careful)

1. **`Array(double[])` ctor copy semantics** — currently System.arraycopy creates copy; Interpolation reads from copy; bootstrap writes to source — stale values
2. **Possible fix paths (per Phase 3e investigation):**
   - Make Array(double[]) store reference (no arraycopy); risk: many tests assume copy semantics
   - Rebuild interpolation inside IterativeBootstrap.BootstrapError.op per call instead of update()
   - Thread explicit setData/refresh hook through Interpolation so update() re-reads from stored source
3. **After fix lands**: un-ignore 3 Markit tests; verify pass at 1e-3 PERCENT (BOOST_CHECK_CLOSE convention)
4. **Test assertion semantics**: BOOST_CHECK_CLOSE tolerance is in PERCENT, not fraction; test bodies use Math.abs(expected) * tolerance — confirm this matches once values are within budget

### Phase 3g+ subsystem ports

5. **`models/marketmodels/`** + tests (~25-30K + tests) — Libor Market Model (largest remaining)
6. **`experimental/`** (non-inflation, non-credit) + tests
7. **Remaining C++ test-suite files** (~70+ cpp files, full rigor)

### Phase 2y still pending (small carry-forwards)

8. CPISwapTest.consistency body-fill (~50 LOC)
9. InflationVolatilityTest.testYoYPriceSurfaceToATM PiecewiseYoY bootstrap fix
10. 5 retained Phase 2u Track F @Ignore'd InflationTest body-fills
11. AbstractTermStructure → LazyObject proper cycle prevention
12. IndexManager test isolation lint pass

## Out-of-scope (explicit, deferred)

- All Phase 3f+ items above
- BroadieKaya retry — needs lgamma
- NCCS EXACT — needs lgamma
- JQuantMath.lgamma — still no source
