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

## A.1 — JQuantMath.pow port

- **C++ source:** Fetch CORE-MATH `pow.c` from `https://raw.githubusercontent.com/mockingbirdnest/core-math/master/src/binary64/pow/pow.c`. Save to `migration-harness/cpp/probes/transcendental/coremath/pow.c`. Verify license header.
- **Probe:** `migration-harness/cpp/probes/transcendental/pow_probe.cpp` — `#include "coremath/pow.c"` and emit `cr_pow(b, e)` raw bits across:
  - IEEE-754 specials per cmath: `pow(±0, ±0)=1`, `pow(1, anything)=1`, `pow(anything, 0)=1`, etc.
  - Integer exponents: `pow(2, k)` for k=-50..50
  - Dense fractional grid: bases {2, e, π, 0.5, 1.5}, exponents in [-10, 10] @ 0.1
  - Hard-cases DB entries from CORE-MATH pow.c source
  - Large stress: `pow(1.0001, 100000)`, `pow(0.9999, -100000)`
- **Java target:** `org.jquantlib.math.transcendental.PowKernel.java` (package-private) + `JQuantMath.pow(double, double)` facade addition
- **Test:** `JQuantMathPowTest.java` — collect-all-failures, EXACT bit-pattern via `MathTestSupport.bitsEqual`
- **Implementation strategy:** mirror Phase 2i.6 LogKernel pattern. Use `Dint64` for accurate-path arithmetic. Python table-extractor for static tables. Reuse LogKernel's u128 helpers if applicable (or use the lifted public TqrEigenDecomposition's siblings).
- **Commit:** `infra(math.transcendental): port CORE-MATH correctly-rounded pow → JQuantMath.pow (Phase 2n A.1)`

## A.2 — Integration: swap Math.pow → JQuantMath.pow at empirical-leverage sites

- Find Math.pow sites:
  ```bash
  grep -rn "Math\.pow" /Users/josemoya/eclipse-workspace/jquantlib-2n-A/jquantlib/src/main/java/org/jquantlib | head -20
  ```
- Targeted swaps (per design):
  - `GsrProcessCore` — Phase 2j-pre B3 site
  - `MethodOfLinesScheme` (or AdaptiveRungeKutta) — Phase 2l C.5 A19 site
  - Other empirical-leverage sites surfaced
- For each test that previously had LOOSE tier due to Math.pow slack: try TIGHT, accept whichever holds.
- **Commit:** `align(*): swap Math.pow → JQuantMath.pow at empirical-leverage sites (Phase 2n A.2)`

## L2 — Completion + tag + memory + README + teardown

Standard milestone-doc discipline.
