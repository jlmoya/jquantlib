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

## 3. Approach

Single worktree A. Two sub-commits:
- **A.1** `JQuantMath.pow` port (~600-1000 LOC Java estimated; CORE-MATH pow uses log+exp internally with extended-precision intermediates)
- **A.2** Integration: swap `Math.pow → JQuantMath.pow` at the 3+ identified sites (GsrProcessCore, MethodOfLinesScheme, plus any others surfaced)

## 4. Decisions

- **P2N-1:** CORE-MATH `cr_pow` is source-of-truth. BSD/MIT-licensed. Vendored under `migration-harness/cpp/probes/transcendental/coremath/`.
- **P2N-2:** Probe oracle = `cr_pow` directly via `#include "coremath/pow.c"` (Phase 2i A3 precedent).
- **P2N-3:** EXACT-tier bit-pattern test using `MathTestSupport.bitsEqual`.
- **P2N-4:** Integration tier flips: existing tests using Math.pow may improve from LOOSE to TIGHT — empirically determine per site.
- **P2N-5:** Direct-to-main signed `-s` no Co-authored-by.

## 5. Pause triggers

Carry-forward + new A24 (CORE-MATH pow needs primitive not in current Dint64/LogKernel surface — handle additively).

## Outcome forecast

| Metric | Phase 2m tip | Phase 2n target |
|--------|--------------|-----------------|
| Tests | 816/0/0/22 | ~819-822 (+1 EXACT pow test, possibly tier flips on existing tests) |
| `JQuantMath` primitives | exp/log/sin/cos | exp/log/sin/cos/**pow** |
| Phase 2l C.5 MethodOfLines tier | 1e-7 (A19 — Math.pow ODE-step) | TIGHT possible (with JQuantMath.pow) |
