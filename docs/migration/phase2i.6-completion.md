# Phase 2i.6 Completion — CORE-MATH `log` Port + NCCS A19 Re-fire (gammaFunction floor)

**Status:** complete 2026-04-30
**Tag:** `jquantlib-phase2i.6-complete` @ `<FILL_AT_TAG>`
**Predecessor:** `jquantlib-phase2i.5-complete` @ `aa5a820`
**Plan:** `docs/migration/phase2i.6-plan.md` (commit `6af2e84`)
**Design:** `docs/migration/phase2i.6-design.md` (commit `7880064`)

## Final state

| Metric | Phase 2i.5 tip | Phase 2i.6 tip | Δ |
|--------|----------------|----------------|----|
| Tests | 687/0/0/22 | 688/0/0/22 | +1 (JQuantMathLogTest) |
| Scanner WIP | 0 | 0 | unchanged |
| `JQuantMath` primitives | exp + cos + sin | exp + cos + sin + **log** | +1 |
| `LogKernel` infrastructure | (none) | full (~1612 LOC; private long[4] dint64-style helpers, 1203 probe cases bit-exact first-shot) | new |
| NCCS CDF tier | TIGHT (A19, "Math.log floor" hypothesis) | TIGHT (A19 re-fired; **gammaFunction_.logValue Lanczos** identified as actual residual) | unchanged tier; structural source now empirically pinned |

Aggregate test-count target was 688 (per design); actual 688. Target hit exactly.

## What landed

### WI-1 — `JQuantMath.log` correctly-rounded port

| Commit | Description |
|--------|-------------|
| `aea7b2a` | Init `phase2i.6-progress.md` |
| `d5553d2` | **WI-1:** CORE-MATH `cr_log` ported (`LogKernel.java` ~1612 LOC; ~640 algorithmic + ~970 static-table initializers extracted via Phase 2i.5 P2I5-12 Python pattern). 1203 probe cases bit-exact **first-shot, no debug iterations**. |

**Probe coverage (1203 cases):**
- IEEE-754 specials: ±0 → -∞, ±∞, NaN, subnormals, min/max normal
- Negative inputs (all → NaN): -1, -π, -0.5, -denorm_min
- Exact-result inputs: 1.0 → +0, e ≈ 2.718281828...
- 15 powers of 2 (k = -1074..1023)
- Dense (0, 10] @ 0.01 (1000 cases)
- Sparse logarithmic (0, 1e308] (61 cases at 10^k spacing)
- Tiny-near-1 inputs (4 cases)
- sqrt(2) boundary triple
- 3 subnormal-path cases
- `ph_worst_125` worst-case Payne-Hanek-equivalent input (verified to exercise `cr_log_accurate` path)
- 100-point near-1 ULP-band sweep

**Key finding — `Dint64` not reused:** CORE-MATH `log.c` uses its own `log_dint.h` (frozen since 2022; bit-incompatible with `sin/cos`'s `dint.h`). `add_dint`/`mul_dint`/`mul_dint_2` semantics differ between the two headers (e.g. log_dint.h's `64 + a->ex` fallback vs. sin/cos `dint.h`'s different ZERO handling). The Java `LogKernel` reimplements log-side dint helpers privately as `long[4]` quads with private static helpers (`u128Add`, `u128Sub`, `u128ShiftLeft/Right`, `u128GetBit`, `u128AddCarry`, `unsignedMulHigh`) — sister to (NOT extension of) the existing `Dint64`. The existing `Dint64` 9-op surface remains locked.

**Key finding — modern CORE-MATH log.c removed hard-cases.** The 2023-09 CORE-MATH commit `ab6ee9e` removed the 27-entry `T[][]` hard-cases table after Gappa proved the tighter `0x1.b6p-69` error bound made it redundant — the accurate dint64 path subsumes hard-cases. Probe handles this via dense + log10 + ph_worst sweeps that exercise the accurate-path fallback.

**Notes for future `pow` port:**
1. **Version skew matters.** GitHub mirror's `pow.h` (master) split `_INVERSE_2`/`_LOG_INV_2` into `_*_2_1`/`_*_2_2` segments in 2023-09; log.c never followed. Future `pow` port should use master's split tables (matching its master `pow.c`).
2. **`log.c` shadows `log_dint.h`'s `dint_fromd`/`dint_tod`** — the C linker prefers log.c's `static inline` versions at lines 780-818; the header's versions go unused. Java port mirrored log.c's local versions.
3. **Subnormal path** scales by `0x1p52`, re-extracts `e`, runs the normal pipeline with the scaled value; the Java port matches.
4. **Inria GitLab is bot-protected** (Anubis "Access Denied" from WebFetch). GitHub mirror `mockingbirdnest/core-math` serves raw files reliably.

### WI-2 — NCCS rewire (A19 re-fire — gammaFunction_.logValue is the actual floor)

| Commit | Description |
|--------|-------------|
| `e12a7fd` | `Math.log(x2)` → `JQuantMath.log(x2)` at `NonCentralCumulativeChiSquaredDistribution.java:86`. EXACT attempt fired immediately. **Identical 27-ULP residual** at sample (df=10, ncp=50, x=65) — the JQuantMath.log swap closed essentially nothing. With both `JQuantMath.exp` and `JQuantMath.log` now correctly-rounded, the only remaining transcendental in the CDF chain is `gammaFunction_.logValue(f2 + 1.0)` (Lanczos logGamma approximation that uses `Math.log` internally). Tier kept TIGHT (max diff well within `abs 1e-14 + rel 1e-12`). A19 documented. |

**Phase 2j implication:** `JQuantMath.lgamma` port via CORE-MATH is now empirically confirmed as the **actual** path to NCCS EXACT. The Phase 2i.5 hypothesis "Math.log is the NCCS floor" was partially right (Math.log slack was *one* of the slack sources, mediated through `f2 * Math.log(x2)`), but the dominant residual is `gammaFunction_.logValue` Lanczos accumulated rounding. The 27-ULP residual is identical pre- and post-Math.log swap, confirming Math.log was a non-dominant contributor.

## A-trigger fire history

| Trigger | Where | Outcome |
|---------|-------|---------|
| **A19** | WI-2 NCCS EXACT attempt | Fired identically to Phase 2i.5: 27-ULP residual unchanged after Math.log swap. Definitive diagnosis: `gammaFunction_.logValue` Lanczos is the dominant slack source — Phase 2j+ candidate. |

A2/A3/A4/A6/A8/A9/A13/A15/A16/A17/A18 did not fire. Implementer reported no Dint64 extension was needed (P2I6-6 path A: `log.c` uses its own incompatible dint helpers, not new operations on the existing class).

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P2I6-11** | LogKernel uses private long[4] dint64-style helpers, NOT the existing `Dint64` class | CORE-MATH `log_dint.h` semantics are bit-incompatible with sin/cos `dint.h`. Reusing `Dint64` would silently produce wrong results. Sister class is the correct approach. |
| **P2I6-12** | Reusable u128 helpers (`u128Add`, `u128Sub`, `u128ShiftLeft/Right`, `u128GetBit`, `u128AddCarry`, `unsignedMulHigh`) kept private to LogKernel for now | Phase 2j extraction candidate to a shared `U128.java` utility — primitives are dint-layout-agnostic, would serve both `LogKernel` and `Dint64` without forcing them onto a shared dint type. |
| **P2I6-13** | NCCS EXACT abandoned for Phase 2i.6; A19 documented with `gammaFunction_.logValue` as the empirically-pinned floor | The 27-ULP residual is identical pre- and post-Math.log swap, definitively ruling out Math.log as the dominant slack. Phase 2j candidate: `JQuantMath.lgamma` port. |
| **P2I6-14** | Source-of-truth fetch fallback = GitHub `mockingbirdnest/core-math` mirror when `gitlab.inria.fr` is bot-blocked | Inria's Anubis bot protection rejects WebFetch's UA. Mirror is canonically synced and serves raw files reliably. |

## JVM-vs-libc++ ULP-slack outcome (refresh)

Phase 2i.6 extends Phase 2i / 2i.5 correctly-rounded coverage:

**Now correctly-rounded against CORE-MATH `cr_*`:**
- `JQuantMath.exp` (Phase 2i)
- `JQuantMath.cos` (Phase 2i.5)
- `JQuantMath.sin` (Phase 2i.5)
- `JQuantMath.log` (Phase 2i.6) ← new

**Still platform-dependent (`Math.*` with potential 1-ULP slack):**
- `Math.pow`, `Math.tan`, `Math.asin`, `Math.acos`, `Math.atan`, `Math.atan2`, `Math.sinh`, `Math.cosh`, `Math.tanh`, `Math.expm1`, `Math.log1p`, `Math.cbrt`, `Math.hypot`

**Highest-leverage Phase 2j candidate:** `JQuantMath.lgamma` (or equivalent gamma function port). Empirically confirmed via WI-2 A19 as the NCCS dominant residual. Once available, NCCS flips to EXACT trivially.

## Phase 2j seed list (refresh)

### Transcendental ports (high → low priority by empirical leverage)

1. **CORE-MATH `lgamma` port** — empirically confirmed as NCCS dominant floor (Phase 2i.6 WI-2 A19). Once available, NCCS flips to EXACT. No prior CORE-MATH lgamma port in JQuantLib; investigate CORE-MATH availability.
2. **CORE-MATH `pow` port** — depends on log + exp (now both available). Structural completion of base-arithmetic primitive set. Audit cited 0× but BroadieKaya retry depends on it.
3. **`U128.java` shared utility extraction** — extract reusable u128 helpers (currently duplicated between `Dint64` and `LogKernel`) to a shared util class. Phase 2j refactor candidate.

### Non-transcendental floor investigations (carry-forward)

4. **Douglas ADI scheme rounding** — FdHullWhite remaining floor (Phase 2i WI-2 B-1 A19).
5. **FdmAffineModelTermStructure discount projection** — co-suspect for FdHullWhite floor.

### Carry-forward from Phase 2h (Fdm framework completeness)

6. Bermudan/American/dividend in Fdm vanillaComposite + step-condition classes
7. BiCGStab/GMRES iterative solvers
8. Schemes beyond Hundsdorfer/Douglas/ImplicitEuler (CraigSneyd, ModifiedCraigSneyd, etc.)
9. Fdm2DimSolver derivative accessors
10. BicubicSplineInterpolation Address-mapping audit (broader)

### Carry-forward (other engines)

11. **Gaussian1D family** (10 engines + model) — Phase 2j primary scope candidate; transcendentals (exp, cos, sin, log) all ready.
12. Other Fdm-dependent engines (FdHestonHullWhite, FdSabrVanilla, FdBlackScholesVanilla, FdConvertibleBond, FdAndreasenHugeLocalVol)
13. HestonProcess.pdf() Fokker-Planck (only if Java caller emerges)
14. BlackSwaptionEngine Cash/ParYieldCurve settlement
15. additionalResults in cap/swaption engines

## Out-of-scope (explicit, deferred)

- BroadieKaya asset-leg retry — deferred until `JQuantMath.pow` lands.
- `JQuantMath.pow`, `tan`, `asin`, `acos`, `atan`, `atan2`, `sinh`, `cosh`, `tanh`, `expm1`, `log1p`, `cbrt`, `hypot` — Phase 2j+ candidates.
- `JQuantMath.lgamma` — Phase 2j primary candidate (per WI-2 A19).
- Math.log audit re-sweep — explicitly skipped per tight scope.
- Codebase-wide `Math.log → JQuantMath.log` swap — only NCCS line 86 was rewired.
- `Math.sqrt`, `Math.fma`, basic arithmetic — JVM matches CORE-MATH.
