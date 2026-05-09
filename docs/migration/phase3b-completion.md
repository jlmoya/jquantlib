# Phase 3b Completion — CreditDefaultSwap + MidPointCdsEngine + CDS Helpers + Test Skeleton

**Status:** complete (autonomous mode — sixteenth autonomous phase)
**Tag:** `jquantlib-phase3b-complete` @ `763fb71`
**Predecessor:** `jquantlib-phase3a-complete` @ `3c72ac6`
**Plan + Design:** `docs/migration/phase3b-{design,plan}.md`

## Final state

| Metric | Phase 3a tip | Phase 3b tip | Δ |
|--------|--------------|--------------|----|
| Tests | 973/0/0/45 | 1004/0/0/51 | +31 (+24 active passing, +6 net @Ignore'd) |
| mvn test wall-clock | 59.3 s | 59.2 s | unchanged ✓ |
| Scanner WIP | 0 | 0 | unchanged |
| Credit subsystem coverage | termstructures only | + CDS instrument + 1 of 3 engines + CDS helpers + test skeleton | substantial |
| New Java packages | — | `org.jquantlib.pricingengines.credit` | +1 |

## What landed (5 commits)

| Commit | Description |
|--------|-------------|
| `6b0b904` | Phase 3b design + plan |
| `2d647b7` | **L0:** CreditDefaultSwap instrument (~690 LOC) + Protection (56) + Claim (117) + FaceValueClaim (49). Smoke test 7 cases. impliedHazardRate/conventionalSpread initially throw. |
| `763fb71` | **L1 Track B:** MidPointCdsEngine (287 LOC) + CdsHelper base (304) + RelativeDateDefaultProbabilityHelper (73) + SpreadCdsHelper (171) + UpfrontCdsHelper (240) = ~1075 LOC main + 427 test + 181 LOC probe. Wired CreditDefaultSwap.impliedHazardRate + conventionalSpread (Midpoint branch). Un-ignored 4 of 7 Phase 3a CDS-deferred tests (testFlatHazardConsistency / testFlatDensityConsistency / testLinearDensityConsistency / testSingleInstrumentBootstrap). |
| `ac5eba8` | **L1 Track C:** CreditDefaultSwapTest skeleton (479 LOC, 10 BOOST_AUTO_TEST_CASE → 10 @Test methods). All 10 @Ignore'd at landing time (Track B hadn't landed yet) with detailed dependency map: 5 need MidPointCdsEngine (now landed; Phase 3c un-ignore opportunity), 1 needs MakeCreditDefaultSwap factory (Phase 3c), 4 need IsdaCdsEngine (Phase 3c). Bodies present for full lineage auditability. |

## A-trigger fire history

| Trigger | Where | Outcome |
|---------|-------|---------|
| **A28 (cross-track parallelism)** | Track B/C | Track C landed before Track B; used Strategy 1 variant (faithful @Test bodies all @Ignore'd with dependency map). When Track B landed (Track C had already pushed), the un-ignore became a Phase 3c opportunity. |
| **A29 (test exercises divergence)** | Track B Phase 3a re-enables | 3 of 7 Phase 3a CDS-deferred tests still @Ignore'd post-Track-B: testLogLinearSurvivalConsistency (needs IterativeBootstrap initial-guess refinement), testUpfrontBootstrap (DateGeneration.CDS), testIterativeBootstrapRetries (DateGeneration.CDS2015 + retries). |

A1-A27, A30-A35 did not fire.

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P3B-1** | MidPointCdsEngine first; Isda + Integral deferred | MidPoint is simplest engine; Isda is sophisticated (488 LOC C++ with calibration nuances); Integral is mid-complexity |
| **P3B-2** | Track C @Ignore'd 10 tests with full bodies + dependency map | Strategy 1 variant — preserves C++ → Java lineage and makes Phase 3c un-ignore work small |
| **P3B-3** | DateGeneration.CDS / CDS2015 / OldCDS deferred to Phase 3c | Schedule rule extensions need to land alongside their use cases (Isda engine + UpfrontCdsHelper Big-Bang) |
| **P3B-4** | Direct-to-main signed `-s` no Co-authored-by | Standing rule |

## Phase 3c+ seed list (substantial)

### Phase 3c — Isda + Integral engines + DateGeneration.CDS + Track C un-ignore (~1,000 LOC C++)

1. **IsdaCdsEngine** — port from `ql/pricingengines/credit/isdacdsengine.{hpp,cpp}` (488 LOC C++, sophisticated)
2. **IntegralCdsEngine** — port from `ql/pricingengines/credit/integralcdsengine.{hpp,cpp}` (250 LOC C++)
3. **MakeCreditDefaultSwap factory** — fluent builder
4. **DateGeneration.CDS / CDS2015 / OldCDS enum values** + Schedule rule support
5. **IterativeBootstrap initial-guess refinement + retries** — needed for testLogLinearSurvivalConsistency + testIterativeBootstrapRetries
6. **Un-ignore Track C's 5 MidPoint-dependent tests** (testCachedValue, testCachedMarketValue, testImpliedHazardRate, testFairSpread, testFairUpfront)
7. **Un-ignore Track C's testAccrualRebateAmounts** (after MakeCreditDefaultSwap)
8. **Un-ignore Track C's 4 Isda tests** (after IsdaCdsEngine)
9. **Un-ignore Phase 3a's 3 still-deferred tests** (after IterativeBootstrap refinement + DateGeneration.CDS)

### Phase 3d+ subsystem ports

10. **`models/marketmodels/`** + tests (~25-30K + tests) — Libor Market Model
11. **`experimental/`** (non-inflation, non-credit) + tests
12. **Remaining C++ test-suite files** (~70+ cpp files in test-suite/, full rigor)

### Phase 2y still pending (small carry-forwards)

13. CPISwapTest.consistency body-fill (~50 LOC)
14. InflationVolatilityTest.testYoYPriceSurfaceToATM PiecewiseYoY bootstrap fix
15. 5 retained Phase 2u Track F @Ignore'd InflationTest body-fills
16. AbstractTermStructure → LazyObject proper cycle prevention
17. IndexManager test isolation lint pass

## Out-of-scope (explicit, deferred)

- All Phase 3c+ items above
- BroadieKaya retry — needs lgamma
- NCCS EXACT — needs lgamma
- JQuantMath.lgamma — still no source
