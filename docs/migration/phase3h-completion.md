# Phase 3h Completion — LMM Marketmodels Foundation (First Slice)

**Status:** complete — autonomous mode — twenty-second autonomous phase
**Tag:** `jquantlib-phase3h-complete` @ `80ff375`
**Predecessor:** `jquantlib-phase3g-complete` @ `a57af3f`
**Plan + Design:** `docs/migration/phase3h-{design,plan}.md`

## Final state

| Metric | Phase 3g tip | Phase 3h tip | Δ |
|--------|--------------|--------------|----|
| Tests | 1062/0/0/38 (post 3g landings) | **1087/0/0/38** | +25 active |
| mvn test wall-clock | 62.0 s | 62.0 s | unchanged ✓ |
| Scanner WIP | 0 | 0 | unchanged |
| Marketmodels subsystem | 0% | foundation slice (~3,600 LOC C++ ported) | first Phase-3 LMM slice |
| New Java packages | — | `org.jquantlib.model.marketmodels` + `.curvestates` + `.driftcomputation` + `.correlations` + `.browniangenerators` | +5 |

## What landed (7 commits across 2 parallel worktrees)

### Track A — Foundation + Curvestates + Drift (3 commits)

| Commit | Description |
|--------|-------------|
| `fbf3e2f` | **A.1-A.3:** Utilities + EvolutionDescription + CurveState abstract base. ~440 LOC main + 18 tests. |
| `df4e5ee` | **A.4-A.6:** LMMCurveState + CoterminalSwapCurveState + CMSwapCurveState. ~510 LOC main + 11 tests. |
| `750b481` | **A.10-A.13:** LMM + LMMNormal + SMM + CMSMM drift calculators. ~520 LOC main + 12 tests. |

### Track B — Correlations + Brownian + AccountingEngine (4 commits)

| Commit | Description |
|--------|-------------|
| `d9bd6e1` | **B.1-B.3:** PiecewiseConstantCorrelation + ExpForward + TimeHomogeneous. 660 LOC + 9 tests. |
| `93bf56f` | **B.7-align (NEW finding):** `align(math.randomnumbers.MersenneTwisterUniformRng.nextInt32)` — pre-existing latent bug: returned signed long via auto-widening of `int next(32)` producing negative "uniforms" when MSB=1. Fixed by masking with `0xFFFFFFFFL` per C++ unsigned-long semantics. Compensating logic in MersenneTwisterTest.testMakotoNishimura also corrected. |
| `a45c60b` | **B.7:** MTBrownianGenerator + Factory + BrownianGenerator align (signature `nextStep(double[])` matching C++ out-param). 358 LOC + 7 tests. |
| `80ff375` | **B.5/B.6/B.8:** AccountingEngine + MarketModelDiscounter + MarketModelMultiProduct + MarketModelEvolver align. 651 LOC + 6 tests. |

## A-trigger fire history

| Trigger | Where | Outcome |
|---------|-------|---------|
| **A29 (test exercises divergence)** | Track B B.7-align | MersenneTwister latent bug surfaced when Brownian generator first exercised it. Pre-existing JQuantLib bug never triggered until now. Fixed inline. |
| **A28 (cross-track parallelism)** | Track B B.3 (correlations) | Used Strategy 1 — inlined `checkIncreasingTimes` package-private pending Track A.1 landing. Refactor to `Utilities.checkIncreasingTimes` deferred (works correctly as-is). |
| **Scope deferral (per design)** | Track A A.7-A.9 | forwardforwardmappings, swapforwardmappings, marketmodeldifferences deferred — depend on Track B's MarketModel + Phase 3j PiecewiseConstantVariance. Phase 3h.5 follow-up candidate. |

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P3H-A.7-A.9 deferred** | A.7-A.9 mappings + differences deferred | Cross-phase dependencies (B.5 MarketModel landed late in Track B; A.9 needs Phase 3j PiecewiseConstantVariance) |
| **P3H-MT-fix** | MersenneTwister unsigned-long mask | C++ `unsigned long`/`uint32_t` semantics; Java auto-widening was sign-extending |
| **P3H-Evolver-public** | MarketModelEvolver methods widened to public | Required for cross-package test fixture access |
| **Direct-to-main** | Standing rule | `git push origin phase-3h-{A,B}:main` |

## Phase 3i+ seed list

### Phase 3h.5 follow-up (small)
1. A.7 forwardforwardmappings.cpp — straightforward, depends only on already-landed CurveState
2. A.8 (partial) swapforwardmappings.cpp — minus swaptionImpliedVolatility (needs Phase 3j)

### Phase 3i — Evolvers (~4,500 LOC C++)
3. 9 evolvers (lognormal/normal × Pc/Euler/Balland) + volprocesses + ConstrainedEvolver
4. Sobol BrownianGenerator
5. CovarianceDecomposition (needed by CotSwapFromFwdCorrelation, deferred from Track B B.4)

### Phase 3j+ — Concrete models, products, callability, pathwise
- Per Phase 3h research doc decomposition

### Other carry-forwards
- testIsdaEngine USD bootstrap precision (Phase 3g A.2 carry-forward)
- Track B `checkIncreasingTimes` inline → Utilities.checkIncreasingTimes refactor (cosmetic)
- Phase 2y carry-forwards (independently in flight)

## Out-of-scope (explicit, deferred)

- All Phase 3i+ items above
- BroadieKaya retry — needs lgamma
- NCCS EXACT — needs lgamma
- JQuantMath.lgamma — still no source
