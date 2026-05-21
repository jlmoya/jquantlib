# Phase 1 — Path A Closure Metrics

**Tip:** `4202288c` (current main as of 2026-05-20)
**Baseline:** `b66b8ef4` (pre-Path-A audit start, D5 RED finding)

## Headline numbers

| Metric | Pre-Path-A | Post-Path-A | Δ |
|---|---|---|---|
| Java `@Test` methods | 3010 | 3344+ | **+334+** |
| Java test files | ~330 | ~340+ | +10 |
| `@Ignore` count | 0 (claimed) / ~14 (actual) | **5** | −9 |
| Full-suite passing | 3010 / 0 / 0 / 1 | **3221+ / 0 / 0 / 20** (in-flight verified) | +211 passing, +19 slow-gated skip mirrors |
| D5 missing-by-name | 280 | **~38** | **−242 (87% closure)** |
| Production code Path A | — | **~17,500 LOC** | new |
| Latent production bugs fixed | — | **17 documented** | new |
| TODOs closed | 0/18 | **17/18** (only #562 partial Phase 1.1 carve-out) | — |

## Latent production bugs surfaced and fixed via Path A

Faithful porting of C++ tests against v1.42.1 baseline forced these bugs out
(each had been silently wrong in the JQuantLib port — most pre-dated this
migration effort):

| # | Class | Bug | Commit |
|---|---|---|---|
| 1 | `AndreasenHugeVolatilityInterpl` | Put/call payoff swap at initial NPV boundary (latent since 2007) | `958afc29` |
| 2 | `TanhSinhIntegral` | Missing level-0 integer-multiple nodes | `844d75ed` |
| 3 | `MethodOfLinesScheme` | Missing `bcSet.setTime()` call (other schemes had it) | `214f7b8a` |
| 4 | `PathwiseVegasOuterAccountingEngine` | All-elements Jacobian transpose | `462b8456` |
| 5 | `QdPlusAmericanEngine` | xMax NaN dispatch at spot=0 | `b8f4d88a` |
| 6 | `Money` | Infinite recursion in `greater`/`greaterEqual` (Phase 1 inline) | (early Path A) |
| 7 | `Period` | Once vs NoFrequency sentinel collision (0 Days vs 0 Years) | `452cc883` |
| 8 | `FuturesRateHelper.impliedQuote` | Misplaced parenthesis broke bootstrap convergence | `e0efe5c9` |
| 9 | `BjerksundStenslandApproximationEngine` | Deep-tail erfc precision (hybrid w/ continued-fraction \|x\|≥4) | `e19325c6` |
| 10 | `GlobalBootstrap` | Defensive guards not in v1.42.1, broke additional-helpers/over-determined paths | `7c5815c3` |
| 11 | `InterpolatedForwardCurve` | Stale `forwards[0]==1.0` discount guard + inverted `Closeness.isClose` (copy-paste residue) | `147c8eb8` |
| 12 | `RelativeDateRateHelper` | DateProxy aliasing in `update()` guard, missed re-init under setEvaluationDate | `722e1d7a` |
| 13 | `ZeroInflationIndex.fixing` | Unsafe `TimeSeries.get` unbox + raw-fixingDate key (should key by period-start) | `0bcc9a15` |
| 14 | `AbstractYieldTermStructure.zeroRate(Date)` | Wrong branch condition; should be `timeFromReference(d)==0` not `d==referenceDate()` (Thirty360 BondBasis Aug 30/31 case) | `82a09e53` |
| 15 | `ArmijoLineSearch` | Reference-aliasing of `xtd_` mutated problem's stored x across iterations | `ea7e6ca0` |
| 16 | `CostFunction.value(x)` | Default RMS impl missing (was abstract); broke BFGS evaluation | `ea7e6ca0` |
| 17 | `AndreasenHugeVolatilityInterpl{AH,Combined}CostFunction` | Sum-of-squares override should not exist (C++ inherits RMS default) | `ea7e6ca0` |

Plus smaller align fixes that didn't qualify as standalone production bugs:
ExponentialIntegral `sign(-0.0)`, Schedule × 3 (CFC-c dedup, etc.), BAW
negative-rates, Germany 31-Dec, Denmark holidays, AnalyticEuropeanEngine
discount-curve ctor, AnalyticDividendEuropeanEngine, GeneralizedBlackScholesProcess
5-arg explicit-local-vol ctor.

## Per-round contribution

| Round | Worktrees | Headline landings |
|---|---|---|
| A1 | A,B,C,D | Array.resize, AbcdCalibration, Daycounter+Date infra, QdPlus/QdFp engines |
| A2 | A,B,C,D | Calendar gaps × 7, BjerksundStensland rewrite, MultiComposite + escrow dividend, FdBSBarrier |
| A3 | A,B,C,D | AndreasenHuge A3 fix + 1 test, HestonRNDCalculator R3, Tracing infra + ConstantLossModel, TanhSinh align |
| A4 | A,B,C,D,E | TanhSinh ports, ExpSinh/Filon/TwoDimIntegral, audit catalogue, QdPlus xMax NaN, AndreasenHuge re-tests, MOL dividend+Dirichlet |
| A5 | A,B,C,D,E | GlobalBootstrap, MarketModel testPathwiseVegas, daycounters schedule-aware, PathwiseVegasOuter Jacobian |
| A6 | A,B,C,D | A6-A piecewiseyieldcurve audit, A6-B GlobalBootstrap, A6-D docs |
| A7 | A,B,C,D,E | BFGS+ZeroCurve, ConvexMonotone+2 interp factories, RelativeDateRateHelper align, 4 ctor overloads, deep-tail erfc, MarkovFunctional×3, dividend×5, EuropeanFD nonconst params, Abcd degenerate cases |
| A8 | A,B,C,E | Inflation NPE (#563), BFGS+ArmijoLineSearch+CostFunction trio, NullTimeToReference + zeroRate align, GaussianQuadrature ports, EXISTING_EQUIVALENT alias batch, docs |

## Residual carve-outs

Tracked in `docs/migration/phase1-closure-remaining.md`:
- **#562 (Phase 1.1)**: ~3 MultiCurve tests need ~1000+ LOC of MultiCurve + MultiCurveBootstrap + InterpolatedSpreadDiscountCurve + observer/shared-ownership semantics infrastructure
- **#562 (Phase 1.1)**: 4 GlobalBootstrap test bodies (~1500-2000 LOC market-data setup)
- **A3 numerical**: AndreasenHuge LM convergence (~2e-9 vs C++ 1e-10), 1 daycounters intraday (Date.java day-resolution), Gaussian1dModel→CalibratedModel refactor pending for testCalibrationTwoInstrumentSets

## D5 dimension status revision

**Audit's original recommendation**: NO-GO pending D5 remediation.

**Post-Path-A reality**: 87% closure on the 280 missing-by-name gap; the
residual ~38 are documented Phase 1.1 / Phase 2 carve-outs with concrete
estimates and rationale. D5 status should be revised from **RED** to **GREEN**
(with documented small Phase 1.1 backlog).

The "leave nothing for future me" mandate is met to the extent achievable
without expanding Phase 1 scope.
