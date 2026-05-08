# Phase 2n Design — JQuantMath.pow Port (CORE-MATH cr_pow)

**Status:** approved 2026-05-08 (autonomous mode — fifth autonomous phase)
**Predecessor:** `jquantlib-phase2m-complete` (tests `816/0/0/22`, scanner WIP=0)

## 1. Context & Motivation

Phase 2j-pre B3 chose to leave `Math.pow` at the GsrProcessCore site (1 use, low leverage). Subsequently:
- Phase 2l C.5 MethodOfLinesScheme — `std::pow` vs `Math.pow` 1-ULP divergence in adaptive ODE step-selection (Phase 2l A19 site)
- Phase 2m FdBlackScholesOp / related — possibly more Math.pow sites (engine integration paths)

Empirical leverage now at 3+ sites. Worth a focused port phase.

## 2. Scope

Single-track phase: port CORE-MATH `cr_pow` to `JQuantMath.pow` + integration at the known sites.

CORE-MATH source: `migration-harness/cpp/probes/transcendental/coremath/` (already vendored — exp, log, sin, cos source files present). Need to fetch `pow.c` from the upstream mirror.

Reuses existing infrastructure:
- `Dint64` u128-emulation (Phase 2i.5 sub-layer 1.0)
- `LogKernel` log-side dint helpers (Phase 2i.6 reusable patterns)
- Probe oracle pattern: `#include "coremath/pow.c"` direct inclusion (Phase 2i a61b920 precedent)
- Python table-extractor (Phase 2i.5 P2I5-12)

## 3. Approach (revised post-inventory)

CORE-MATH `pow.c` (1951 lines) needs **both** `dint.h` (already ported as `Dint64`) AND `qint.h` (1571 lines — 256-bit u128-pair extended-precision, NOT yet ported). Empirical Math.pow inventory: **50+ production sites** including AnalyticBarrierEngine, BjerksundStensland, JuQuadratic, BaroneAdesiWhaley vanilla engines, FdmSabrOp+CEVRNDCalculator+Sabr (SABR pricing), AdaptiveRungeKutta, GsrProcessCore, InterestRate (compounding), GaussHermitePolynomial, ModifiedBesselFunction.

Single worktree A. Three sub-commits, mirroring Phase 2i.5 sub-layer structure:
- **A.0** `Qint64` 256-bit u128-pair emulation port — pure infrastructure, no public API surface beyond package-private. Foundation for PowKernel third Ziv stage. (~1500 LOC Java; mirrors Dint64 architecture.)
- **A.1** `PowKernel` + `JQuantMath.pow` facade — full CORE-MATH `cr_pow` algorithm (3 stages: log_1/p_1, log_2/p_2 via Dint64, log_3/p_3 via Qint64; plus exact_pow rounding-boundary test). (~1800-2200 LOC Java + table extraction.)
- **A.2** Integration: swap top-leverage `Math.pow → JQuantMath.pow` at sites where empirical leverage is documented. Targeted swaps (engines, FdmSabrOp/CEVRNDCalculator, AdaptiveRungeKutta, GsrProcessCore, InterestRate, etc.) — purely mechanical. Existing tests promote from LOOSE to TIGHT where Math.pow was the floor.

## 4. Decisions

- **P2N-1:** CORE-MATH `cr_pow` is source-of-truth. MIT-licensed (CERN+Inria 2022-2025). Vendored at `migration-harness/cpp/probes/transcendental/coremath/{pow.c, pow.h, qint.h}`.
- **P2N-2:** Probe oracle = `cr_pow` directly via `#include "coremath/pow.c"` (Phase 2i A3 precedent).
- **P2N-3:** EXACT-tier bit-pattern test using `MathTestSupport.bitsEqual`.
- **P2N-4:** Integration tier flips: existing tests using Math.pow may improve from LOOSE to TIGHT — empirically determine per site.
- **P2N-5:** Direct-to-main signed `-s` no Co-authored-by.
- **P2N-6:** `Qint64` follows the same package-private architecture as `Dint64`, lifted to `org.jquantlib.math.transcendental.Qint64`. Future `JQuantMath.lgamma` (still blocked) and other higher-precision primitives will reuse it.

## 5. Pause triggers

Carry-forward A1-A23 + new **A24** (CORE-MATH pow needs primitive not in current Dint64/Qint64/LogKernel surface — handle additively, do not pause).

## Outcome forecast

| Metric | Phase 2m tip | Phase 2n target |
|--------|--------------|-----------------|
| Tests | 816/0/0/22 | ~820-825 (+1 EXACT pow test, +1 EXACT Qint64 test, possible tier flips on Math.pow-leveraged tests) |
| `JQuantMath` primitives | exp/log/sin/cos | exp/log/sin/cos/**pow** |
| Math.pow production sites | ~50 | targeted reduction at high-leverage sites; rest tolerated as low-leverage |
| u128 infra | Dint64 (2×64) | Dint64 (2×64) + **Qint64 (4×64)** |
| Phase 2l C.5 MethodOfLines tier | 1e-7 (A19 — Math.pow ODE-step) | TIGHT possible (with JQuantMath.pow) |
