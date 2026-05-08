# Phase 2n Completion — JQuantMath.pow correctly-rounded + Qint64 infra

**Status:** complete (autonomous mode — fifth autonomous phase)
**Tag:** `jquantlib-phase2n-complete` @ `e4b1230`
**Predecessor:** `jquantlib-phase2m-complete` @ `9e5b59a`
**Plan + Design:** `docs/migration/phase2n-{design,plan}.md`

## Final state

| Metric | Phase 2m tip | Phase 2n tip | Δ |
|--------|--------------|--------------|----|
| Tests | 816/0/0/22 | 818/0/0/22 | +2 (Qint64Test + JQuantMathPowTest) |
| Scanner WIP | 0 | 0 | unchanged |
| `JQuantMath` primitives | exp/log/sin/cos | exp/log/sin/cos/**pow** | +1 |
| u128 emulation infra | Dint64 (2×64) | Dint64 (2×64) + **Qint64 (4×64)** | +1 |
| Math.pow sites in production | ~50 | ~5 (constant epsilon, internal fallback) | -45 swapped |
| Bit-exact pow oracle cases | 0 | 2,763 (100%) | +2,763 |
| New transcendental class LOC | — | Qint64 870 + PowKernel 2285 = 3155 | |

## What landed (8 commits)

### Sub-layer A.0 — Qint64 (256-bit u128-pair emulation)

| Commit | Description |
|--------|-------------|
| `92c2fef` | Phase 2n design + plan revised: A.0 Qint64 + A.1 PowKernel + A.2 integration |
| `71d441e` | Vendor CORE-MATH pow.c + pow.h + qint.h sources |
| `f157abf` | **A.0:** Qint64.java (870 LOC) + Qint64Test.java (308 LOC) + qint64_probe.cpp (478 LOC) + qint64_shim.{c,h} (305 LOC). 322 probe cases / 16 ops EXACT. JDK 11 target → ported inline u128 mul-hi from Dint64. C/C++ shim resolves dint.h/qint.h C++-overload conflicts and pre-C99 string-concat in `print_qint`. Mod-64 shift semantics intentionally preserved (Java JLS §15.19 matches arm64/x86 platform behaviour). |
| `2992fd5` | Phase 2n A.0 progress doc |

### Sub-layer A.1 — PowKernel + JQuantMath.pow

| Commit | Description |
|--------|-------------|
| `6ebf44d` | **A.1.a:** pow_probe.cpp (2,763 reference cases / 745KB pow.json). Categories: specials, integer-y, dense fractional grid, SABR pricing, vanilla engines, AdaptiveRungeKutta PSHRINK/PGROW, InterestRate compounding, large stress, subnormal/boundary, hard-rounding, small-int. C-language shim resolves the same C/C++-incompatibilities encountered in A.0. |
| `db75406` | **A.1.b:** PowKernel.java specials path + JQuantMath.pow facade. 38 specials cases bit-exact. Non-special paths delegate to Math.pow temporarily. |
| `6496f6b` | **A.1.b/1:** PowKernel stage-1 fast path + tables (`_INVERSE[182]`, `_LOG_INV[182][2]`, `T1[64][2]`, `T2[64][2]`, `P_1[6]`, `Q_1[5]`). 738 LOC. Python table-extractor (`migration-harness/tools/extract-pow-tables.py` 456 LOC). 2,761/2,763 = 99.93% bit-exact. 2 hard-rounding cases fall through to Math.pow (1 ULP off). |
| `e831fd7` | **A.1.c:** PowKernel stage-2 Dint64 Ziv chain. 738 → 2,285 LOC (+1,547). Inlined Dint64 ops (mul_dint_11, mul_dint_int64, mul_dint_21, add_dint_11, dint_toi, etc.) avoiding cross-package pollution. 8 stage-2 tables (`_INVERSE_2_1[92]`, `_INVERSE_2_2[129]`, `_LOG_INV_2_1[92]`, `_LOG_INV_2_2[129]`, `T1_2[64]`, `T2_2[64]`, `P_2[9]`, `Q_2[8]`). **2,763/2,763 = 100% bit-exact** vs CORE-MATH cr_pow. Three porting bugs found+fixed via side-by-side C/Java tracing (mul_dint_int64 128-bit double-shift; mul_dint u128 carry-propagation; add_dint_11 lo-preservation). |

### Sub-layer A.2 — Integration

| Commit | Description |
|--------|-------------|
| `e4b1230` | **A.2:** `align(jquantlib): swap Math.pow → JQuantMath.pow at empirical-leverage sites`. 29 files / 57 sites swapped. AmericanPayoffAtExpiry/AtHit, AnalyticBarrier, BjerksundStensland, JuQuadratic, BaroneAdesiWhaley, FdmSabrOp, FdmHestonVarianceMesher, CEVRNDCalculator, GsrProcessCore, AdaptiveRungeKutta, GaussHermitePolynomial, GaussKronrodPatterson/NonAdaptive, InverseCumulativePoisson, ModifiedBesselFunction, Rounding, DiscrepancyStatistics, InterestRate, Sabr, SABRInterpolation, ZeroInflationIndex, lattices Joshi4/LeisenReimer/Tian + Extended* variants. **Skipped:** EigenvalueDecomposition/SVD `Math.pow(2.0, -52.0)` (compile-time constant `0x1p-52`, no leverage). PowKernel internal fallback (it IS the JQuantMath implementation). |

## A-trigger fire history

| Trigger | Where | Outcome |
|---------|-------|---------|
| A19 (tier discrepancy) | A.1.b/1 | 99.93% bit-exact stage-1 → reframed; closed by stage-2 Ziv at A.1.c (100% bit-exact). |
| A24 (new — needed primitive not in current Dint64/Qint64 surface) | A.1.c | Inlined the additional Dint64 ops directly into PowKernel rather than expanding Dint64.java's surface. Avoids cross-pollution between sin/cos's canonical dint.h variant and pow's pow_dint.h variant (different exponent conventions). |
| **Refined finding** | A.2 MOL_TOL | **Phase 2l A19 reframe:** MethodOfLinesScheme 1e-7 was *not* attributable to `Math.pow` slack. After AdaptiveRungeKutta swap, the divergence persists at ~5e-8 relative due to accumulated platform FP ordering through the ODE state, not the pow call. MOL_TOL stays 1e-7; comment refreshed in `FdmSchemesTest.java` to reflect actual cause. |

A1/A2/A3/A4/A6/A8/A9/A13/A15/A16/A17/A18/A20/A21/A22/A23 did not fire.

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P2N-1** | CORE-MATH cr_pow is source-of-truth for JQuantMath.pow | MIT-licensed (CERN+Inria 2022-2025), correctly-rounded IEEE-754 binary64 pow. |
| **P2N-2** | Probe oracle = `cr_pow` directly via `#include "coremath/pow.c"` | Phase 2i A3 precedent; avoids platform libm slack. |
| **P2N-3** | EXACT-tier bit-pattern test using `MathTestSupport.bitsEqual` | Probe-before-port discipline; 2,763 cases. |
| **P2N-4** | Integration tier flips empirically determined per site | Most tests unchanged (Math.pow vs JQuantMath.pow agree on regular inputs); stage-1 fall-through cases are rare in realistic finance code. |
| **P2N-5** | Direct-to-main signed `-s` no Co-authored-by | Standing project rule. |
| **P2N-6** | Qint64 follows package-private architecture mirroring Dint64 | Reusable for future JQuantMath.lgamma + other higher-precision primitives. |
| **P2N-7** | Stage-2 Dint64 ops inlined into PowKernel rather than extending Dint64.java | pow_dint.h has a different exponent convention than sin/cos's canonical dint.h (`r->ex = a->ex + b->ex + ex` vs `… + ex - 1`). Cross-pollution would corrupt sin/cos. PowKernel-internal helpers stay scoped. |
| **P2N-8** | Stage 3 (Qint64 chain + exact_pow + is_exact) deferred to Phase 2o-future | Stage 2 alone closed all 2,763 reference cases; stage 3 is reserved for genuinely hard-rounding cases that require >256-bit precision. None surfaced in our oracle. Defer until empirical demand. |
| **P2N-9** | Phase 2l A19 finding refined: MethodOfLinesScheme 1e-7 floor is platform FP ordering, NOT Math.pow | Empirical: even after Math.pow → JQuantMath.pow swap, ~5e-8 relative divergence persists. Tier kept at 1e-7; comment updated. |

## Phase 2o+ seed list

### High-leverage carry-forwards

1. **PowKernel stage 3 (Qint64 chain + exact_pow + is_exact)** — only needed if a future test surfaces a hard-rounding case that stage-2 fails. Currently theoretical; `Math.pow` fallback covers stage-2 failures (bit-imperfect but functional).
2. **JQuantMath.lgamma** — still no path; remains blocked. CORE-MATH does provide `cr_lgamma` (search `gitlab.inria.fr/core-math` for binary64/lgamma). Reuses Qint64 infra.
3. **Phase 2l A19 reframe → ODE state ordering** — if a future phase wants to align `AdaptiveRungeKutta` deeper to C++ for tighter MOL_TOL, the divergence source is FP ordering through the K1/K2/K3/K4 state accumulation, not transcendental primitives.
4. **HestonModel rho constraint align** (carry-forward from Phase 2m) — `PositiveConstraint` → `BoundaryConstraint(-1,1)` to match C++.
5. **AndreasenHuge calibration** (carry-forward from Phase 2m) — surface ports done, calibration loop deferred.
6. **C++ test-suite Java equivalents** — substantial scope, every C++ engine test deserves a Java equivalent.

### Other carry-forwards

7. **SABRInterpolation shifted-strike support** (Phase 2k Track A Scenario C unblocker)
8. **U128.java shared util extraction** — refactor candidate
9. **Douglas ADI / FdmAffineModelTermStructure** (FdHullWhite real floor, Phase 2i carry-forward)

### Phase 3+ subsystem ports

10. **`experimental/`** — large surface
11. **`models/marketmodels/`** — Libor Market Model family
12. **`termstructures/credit/`** — credit term structures + CDS + CDX
13. **`inflation/`** — inflation indexes + curves + linkers + caps/floors

## Out-of-scope (explicit, deferred)

- All Phase 2o+ items above
- BroadieKaya retry — needs lgamma
- NCCS EXACT — needs lgamma
- FdConvertibleBond — does not exist in v1.42.1
