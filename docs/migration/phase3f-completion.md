# Phase 3f Completion — Interpolation Copy-vs-Reference Fix (Path A)

**Status:** complete (DONE_WITH_CONCERNS) — autonomous mode — twentieth autonomous phase
**Tag:** `jquantlib-phase3f-complete` @ `521855f`
**Predecessor:** `jquantlib-phase3e-complete` @ `26f7433`
**Plan + Design:** `docs/migration/phase3f-{design,plan}.md`

## Final state

| Metric | Phase 3e tip | Phase 3f tip | Δ |
|--------|--------------|--------------|----|
| Tests | 1024/0/0/41 | 1024/0/0/41 | unchanged |
| mvn test wall-clock | 60.0 s | 61.0 s | unchanged ✓ |
| Scanner WIP | 0 | 0 | unchanged |
| testIsdaEngine bootstrap drift | ~1% | **2e-5 to 1.4e-4 (100× improvement)** | substantial |
| Underlying Array(double[]) semantics | copy via System.arraycopy | reference (matches C++ Array(InputIterator,InputIterator)) | architectural |

## What landed (3 commits)

| Commit | Description |
|--------|-------------|
| `56037e2` | Phase 3f design + plan |
| `7d2acb2` | **A.1 (Path A):** `Array(double[])` constructor wraps source w/o copy. Mirror C++ Array(InputIterator,InputIterator) + data.begin() iterator semantics. 39 LOC fix in Array.java + Cells.java (relaxed length check from `==` to `>=` for partial views; null-addr branch in super ctor). 264 grep callers analyzed; cascade bounded by literal-array pattern dominance (178 test sites use `new double[]{...}` with no other reference path). |
| `521855f` | **A.2:** Refined @Ignore rationale on 3 Markit tests with post-fix diagnostic evidence. testIsdaEngine: ~100× improvement (now within ~2e-5 to 1.4e-4 fraction; budget 1e-3 PERCENT i.e. 1e-5 fraction). testIsdaCalculatorReconcile* (both): unchanged constant -173 absolute diff (IsdaCdsEngine settlement accrual rebate bug, orthogonal to bootstrap fix). |

## A-trigger fire history

| Trigger | Where | Outcome |
|---------|-------|---------|
| **A29 (test exercises divergence)** | A.2 | 3 tests still @Ignore'd post-architectural-fix because residual issues are orthogonal IsdaCdsEngine accrual bugs (short-tenor + settlement rebate). Refined rationale documents post-fix diagnostic evidence. Phase 3g scope. |

A1-A28, A30-A35 did not fire.

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P3F-1** | Path A (Array reference semantics) chosen over Paths B (per-call rebuild) and C (setData/refresh hook) | Matches C++ Array(InputIterator,...) semantics exactly; 39 LOC vs ~80 for Path B/C; cascade bounded by literal-array dominance |
| **P3F-2** | Cells length check relaxed `==` → `>=` | Allows partial views; needed for Path A correctness |
| **P3F-3** | 3 Markit tests stay @Ignore'd with refined rationale | Post-fix diagnostic shows residual is orthogonal IsdaCdsEngine accrual debt, not bootstrap |
| **P3F-4** | Direct-to-main signed `-s` no Co-authored-by | Standing rule |

## Phase 3g+ seed list

### Phase 3g — IsdaCdsEngine accrual debt (~50-100 LOC)

1. **testIsdaEngine residual drift** — drift inversely correlated with maturity (worst at 1-yr, best at 10-yr) → IsdaCdsEngine short-tenor accrual handling
2. **testIsdaCalculatorReconcile* constant -173 diff** — same-rooted IsdaCdsEngine T+3 settlement accrual rebate bug; suspect `accrualRebate()` or `defaultLegNPV()/couponLegNPV()` interaction with negative rates
3. After fixes: un-ignore 3 Markit tests + verify pass

### Phase 3h+ subsystem ports

4. **`models/marketmodels/`** + tests (~25-30K + tests) — Libor Market Model (largest remaining)
5. **`experimental/`** (non-inflation, non-credit) + tests
6. **Remaining C++ test-suite files** (~70+ cpp files, full rigor)

### Phase 2y still pending

7. CPISwapTest.consistency body-fill (~50 LOC)
8. InflationVolatilityTest.testYoYPriceSurfaceToATM PiecewiseYoY bootstrap fix
9. 5 retained Phase 2u Track F @Ignore'd InflationTest body-fills
10. AbstractTermStructure → LazyObject proper cycle prevention
11. IndexManager test isolation lint pass

## Out-of-scope (explicit, deferred)

- All Phase 3g+ items above
- BroadieKaya retry — needs lgamma
- NCCS EXACT — needs lgamma
- JQuantMath.lgamma — still no source
