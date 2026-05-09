# Phase 3a Completion — Credit Termstructures + First Test Port

**Status:** complete (autonomous mode — fifteenth autonomous phase; **first Phase-3 subsystem port**)
**Tag:** `jquantlib-phase3a-complete` @ `3c72ac6`
**Predecessor:** `jquantlib-phase2x-complete` @ `a710e9b`
**Plan + Design:** `docs/migration/phase3a-{design,plan}.md`

## Final state

| Metric | Phase 2x tip | Phase 3a tip | Δ |
|--------|--------------|--------------|----|
| Tests | 951/0/0/38 | 973/0/0/45 | +22 (+15 active passing, +7 deferred-Phase-3b @Ignore'd) |
| mvn test wall-clock | 59.5 s | 59.3 s | unchanged ✓ |
| Scanner WIP | 0 | 0 | unchanged |
| Credit subsystem coverage | 0% | termstructures + 1 test file (~70% of credit core; CDS instrument + engines + creditdefaultswap.cpp test = Phase 3b) |
| Java packages added | — | `org.jquantlib.termstructures.credit` | +1 |

## What landed (5 commits)

| Commit | Description |
|--------|-------------|
| `1d19d8e` | Phase 3a design + plan |
| `96575e5` | **L0:** DefaultProbabilityTermStructure base + 3 abstract bases (HazardRateStructure, SurvivalProbabilityStructure, DefaultDensityStructure) + FlatHazardRate (concrete). ~735 LOC main + 115 LOC test. 4 active tests. |
| `a33d95b` | **Align prereq:** `LinearInterpolation.primitive` index off-by-one fix. Pre-existing Java port bug — used `vp.get(i-1)` instead of `vp.get(i)`. Crashed on first interval. Caught by Phase 3a credit-curve tests. Mirrors C++ v1.42.1. |
| `fc193e7` | **L1:** 3 interpolated curves (DefaultDensity / HazardRate / SurvivalProbability) + PiecewiseDefaultCurve + ProbabilityTraits + DefaultProbabilityHelper base (non-CDS). ~1,240 LOC main + 210 LOC test. 9 active tests. |
| `3c72ac6` | **L2:** defaultprobabilitycurves.cpp test port. 200 LOC test. 2 active (interpolated curves can be tested standalone); 7 @Ignore'd as `Phase 3b: needs CreditDefaultSwap` (testFlatHazardConsistency, testFlatDensityConsistency, testLinearDensityConsistency, testLogLinearSurvivalConsistency, testSingleInstrumentBootstrap, testUpfrontBootstrap, testIterativeBootstrapRetries — all need CDS-based helpers). |

## A-trigger fire history

| Trigger | Where | Outcome |
|---------|-------|---------|
| **A26 (cross-cutting align prereq)** | L0/L1 | LinearInterpolation.primitive off-by-one bug caught during testing; bundled as separate align commit per project rule |
| **A29 (test exercises class/method that diverges)** | L2 | 7 tests @Ignore'd with explicit `Phase 3b: needs CreditDefaultSwap` rationale per binding rigor — methods present (not deleted) so C++→Java lineage is auditable |
| **First Phase-3 subsystem** | overall | Greenfield port (no existing Java surface) completed 31min — Phase 2x's mvn-test speedup paying off |

A1-A25, A27, A28, A30-A35 did not fire.

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P3A-1** | Java package `org.jquantlib.termstructures.credit` mirrors C++ ql/termstructures/credit/ | Standard convention |
| **P3A-2** | Phase 3a stops short of CDS-dependent helpers + tests | Bounded scope; CDS subsystem is Phase 3b natural next |
| **P3A-3** | PiecewiseDefaultCurve bootstrap is end-to-end-untested in Phase 3a | Compiles and uses Phase 2v PiecewiseZeroInflationCurve idiom; first end-to-end exercise needs CDS helpers (Phase 3b) |
| **P3A-4** | survivalProbabilityImpl numerical fallback throws on non-overridden path | Every concrete subclass shipped supplies closed-form override; future curve flavor needing fallback can wire in GaussChebyshev integrator |
| **P3A-5** | Direct-to-main signed `-s` no Co-authored-by | Standing rule |

## Phase 3b+ seed list

### Phase 3b — CDS instrument + engines + tests (~3,300 LOC C++)

1. **CreditDefaultSwap instrument** — port from `ql/instruments/creditdefaultswap.{hpp,cpp}` (~700 LOC C++)
2. **MidPointCdsEngine** — port from `ql/pricingengines/credit/midpointcdsengine.{hpp,cpp}`
3. **IsdaCdsEngine** — port from `ql/pricingengines/credit/isdacdsengine.{hpp,cpp}` (substantial — ~700+ LOC)
4. **IntegralCdsEngine** — port from `ql/pricingengines/credit/integralcdsengine.{hpp,cpp}`
5. **CdsHelper / SpreadCdsHelper / UpfrontCdsHelper** — additive to `DefaultProbabilityHelper` (Phase 3a base already in place); ~400 LOC C++
6. **creditdefaultswap.cpp test port** (1,083 LOC C++)
7. **Un-ignore Phase 3a's 7 CDS-dependent tests** + body-fill or verify they pass with the new CDS classes

### Phase 3c+ subsystem ports

8. **`models/marketmodels/`** + tests (~25-30K + tests) — Libor Market Model
9. **`experimental/`** (non-inflation, non-credit) + tests (~40-60K + tests)
10. **Remaining C++ test-suite files** (~70+ cpp files, full rigor)

### Phase 2y still pending (small carry-forwards)

11. CPISwapTest.consistency body-fill (~50 LOC, production fully unblocked)
12. InflationVolatilityTest.testYoYPriceSurfaceToATM PiecewiseYoY bootstrap fix
13. 5 retained Track F @Ignore'd InflationTest body-fills
14. AbstractTermStructure → LazyObject proper cycle prevention (Phase 2x A.4 mitigated; full pattern still desirable)
15. IndexManager test isolation lint pass

## Out-of-scope (explicit, deferred)

- All Phase 3b+ items above
- BroadieKaya retry — needs lgamma
- NCCS EXACT — needs lgamma
- JQuantMath.lgamma — still no source
