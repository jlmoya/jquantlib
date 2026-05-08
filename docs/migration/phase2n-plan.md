# Phase 2n Implementation Plan

> Single-track focused phase. CORE-MATH `cr_pow` port + integration.

**Goal:** `JQuantMath.pow` correctly-rounded; tests `816 → ~821`; tag `jquantlib-phase2n-complete`.

## L0 — Pre-flight + worktree setup

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-2n-A-pow /Users/josemoya/eclipse-workspace/jquantlib-2n-A main
cd /Users/josemoya/eclipse-workspace/jquantlib-2n-A
git submodule update --init --recursive
```

## A.0 — Qint64 256-bit u128-pair emulation infrastructure (NEW sub-layer)

- **C++ source:** `migration-harness/cpp/probes/transcendental/coremath/qint.h` (already vendored, 1571 lines).
- **Java target:** `org.jquantlib.math.transcendental.Qint64.java` (package-private). Architecture mirrors `Dint64`: signed-magnitude 256-bit mantissa = 4 unsigned longs `(hh, hl, lh, ll)` + signed `int64 ex` exponent + `uint64 sgn`. Operations needed by pow.c chain: add, sub, mul (qint × qint, qint × dint), div, sqr, ldexp, normalize, conversions to/from double, conversions to/from `Dint64`. Inline-justified u128 emulation using existing `Dint64.u128_*` helper patterns.
- **Test:** `Qint64Test.java` — EXACT bit-pattern. Probe: `qint64_probe.cpp` with `#include "coremath/qint.h"` exercising arithmetic on a battery of mantissas + exponents.
- **Commit:** `infra(math.transcendental): port CORE-MATH qint.h → Qint64 (256-bit u128-pair emulation) (Phase 2n A.0)`

## A.1 — JQuantMath.pow port

- **C++ source:** `migration-harness/cpp/probes/transcendental/coremath/{pow.c, pow.h}` (already vendored, 1951 + 875 lines).
- **Probe:** `migration-harness/cpp/probes/transcendental/pow_probe.cpp` — `#include "coremath/pow.c"` and emit `cr_pow(b, e)` raw bits across:
  - IEEE-754 specials per cmath: `pow(±0, ±0)=1`, `pow(1, anything)=1`, `pow(anything, 0)=1`, etc.
  - Integer exponents: `pow(2, k)` for k=-50..50, `pow(0.5, k)` for k=-50..50
  - Dense fractional grid: bases {2, e, π, 0.5, 1.5, 0.1, 1.0001, 0.9999}, exponents in [-10, 10] @ 0.1
  - SABR-shape: bases [0.001, 100], exponents in {0.5, 1-0.5, 2*(1-0.5), 1.5} (covers SABR + AnalyticBarrier + AdaptiveRungeKutta paths)
  - Large stress: `pow(1.0001, 100000)`, `pow(0.9999, -100000)`, `pow(2, 1023)`
- **Java target:** `org.jquantlib.math.transcendental.PowKernel.java` (package-private) + `JQuantMath.pow(double, double)` facade.
- **Tables:** `pow.h` defines `_INVERSE[182]`, `_LOG_INV[182][2]`, plus `T1[][2]` and `T2[][2]` arrays. Extract via Python similar to Phase 2i.5 P2I5-12.
- **Test:** `JQuantMathPowTest.java` — collect-all-failures, EXACT bit-pattern via `MathTestSupport.bitsEqual`.
- **Implementation strategy:** Map CORE-MATH pow.c functions: `q_1/p_1 → log/exp 1st-stage doubles`, `q_2/p_2 → 2nd-stage Dint64`, `q_3/p_3/log_3/exp_3 → 3rd-stage Qint64`, `exact_pow + is_exact → exactness detection`. The public entry point's 3-stage Ziv loop with rounding-boundary test must be preserved bit-exactly.
- **Commit:** `infra(math.transcendental): port CORE-MATH correctly-rounded pow → JQuantMath.pow (Phase 2n A.1)`

## A.2 — Integration: swap Math.pow → JQuantMath.pow at empirical-leverage sites

- Math.pow inventory (50+ sites). High-leverage targets:
  - **Vanilla engines:** AnalyticBarrierEngine, BjerksundStenslandApproximationEngine, JuQuadraticApproximationEngine, BaroneAdesiWhaleyApproximationEngine, AmericanPayoffAtExpiry, AmericanPayoffAtHit
  - **SABR pricing path:** Sabr.java, SABRInterpolation.java, FdmSabrOp.java, CEVRNDCalculator.java
  - **ODE / Fdm:** AdaptiveRungeKutta.java, FdmHestonVarianceMesher.java
  - **Process:** GsrProcessCore.java
  - **Interest rate:** InterestRate.java (compounding)
  - **Math primitives:** GaussHermitePolynomial.java, ModifiedBesselFunction.java, InverseCumulativePoisson.java
  - Skip pure-int powers (`Math.pow(2.0, -52.0)` for eps constants) — those compile to `0x1p-52` anyway, no leverage.
- For each test that previously had LOOSE tier due to Math.pow slack: try TIGHT, accept whichever holds.
- **Commit:** `align(*): swap Math.pow → JQuantMath.pow at empirical-leverage sites (Phase 2n A.2)`

## L2 — Completion + tag + memory + README + teardown

Standard milestone-doc discipline.
