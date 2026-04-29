# Phase 2i.5 Completion — CORE-MATH `cos`/`sin` Port + NCCS Rewire + Audit Tier Discharge

**Status:** complete 2026-04-28
**Tag:** `jquantlib-phase2i.5-complete` @ `3c4b913`
**Predecessor:** `jquantlib-phase2i-complete` @ `a4a3b77`
**Plan:** `docs/migration/phase2i.5-plan.md` (commit `a4dcbf0`)
**Design:** `docs/migration/phase2i.5-design.md` (commit `8cf4775`, with Option 1 scope correction during execution)

## Final state

| Metric | Phase 2i tip | Phase 2i.5 tip | Δ |
|--------|--------------|----------------|----|
| Tests | 684/0/0/22 | 687/0/0/22 | +3 (Dint64 + cos + sin EXACT-tier tests) |
| Scanner WIP | 0 | 0 | unchanged |
| `JQuantMath` primitives | exp | exp + cos + sin | +2 |
| `Dint64` u128-emulation infrastructure | (none) | full (553 LOC, 9 ops, 100 probe cases) | new — reusable for future log/pow ports |
| NCCS CDF tier | TIGHT (vs `std::exp` oracle, A13 slack) | TIGHT (vs `std::exp` oracle, A19 — Math.log floor identified) | unchanged tier, source-of-slack now empirically identified |
| GaussLaguerre cos-integrand tier | TIGHT (with "Math.cos slack" justification) | TIGHT (correct-by-construction via `JQuantMath.cos`) | qualitatively improved |
| GaussLobatto trig-integrand tier | TIGHT (with "Math.sin slack" justification) | TIGHT (correct-by-construction via `JQuantMath.sin`) | qualitatively improved |

Aggregate test-count target was 686 (per design); actual 687. The +1 vs target comes from sub-layer 1.0's `Dint64Test` which wasn't in the original design (the dint64_t infrastructure was discovered during the WI-1 BLOCKED dispatch).

## What landed

### WI-1 — `JQuantMath.cos`/`sin` port via Dint64 (sub-layered after Option 1 scope correction)

| Commit | Description |
|--------|-------------|
| `1f2ee97` | Init `phase2i.5-progress.md` |
| `8f30182` | (WI-2 — see below) |
| `2e01804` | Progress: WI-1 scope correction note + WI-2 A19 |
| `73b0a23` | **WI-1.0:** `Dint64` u128-emulation infrastructure (553 LOC; 9 ops: fromDouble, toDouble, copyFrom, addAssign, mulAssign, mul21Assign, subnormalize, cmpAbs, isZero); 90 probe cases all bit-exact first-shot. Aliasing constraint locked in API: `addAssign(a, b)` requires `this != a && this != b`. |
| `042468c` | **WI-1.0 followup:** expand probe to 100 cases (mul21 parity + add 128-bit overflow corner); tighten addAssign aliasing Javadoc per code review. |
| `07337e8` | **WI-1.1:** `SinCosKernel` (1672 LOC; ~820 algorithm + ~840 static-table initializers). First-compile bit-exact on 2,757 probe cases (1,381 cos + 1,376 sin) including IEEE-754 specials, exact-result inputs, Payne-Hanek stress through 2^50·π, dense [-2π, 2π] @ 0.01, the `sin_accurate` huge-x worst case `0x1.6ac5b262ca1ffp+849`, and 7 hard-rounding exceptions (sin 2 + cos 5 — CORE-MATH uses small exception tables triggered when accurate-path 41-ulp confidence band straddles a rounding boundary; not a "hard-cases DB" like exp). |
| `35278d5` | Progress: WI-1 complete |

**Insight for future ports:** Python table-extractor (~150 LOC) converted all C tables (T[20], S[256], C[256], PSfast[5], PCfast[5], PS[6], PC[6], SC[256][3]) into Java `static {}` initializers via `Double.longBitsToDouble` — zero hand-transcription error opportunity. Reusable pattern for log/pow.

### WI-2 — NCCS CDF rewire (A19 partial — Math.log floor surfaced)

| Commit | Description |
|--------|-------------|
| `8f30182` | 3 `Math.exp` call sites in `NonCentralCumulativeChiSquaredDistribution.java` swapped to `JQuantMath.exp`. Probe Scenario B (QL NCCS delegates `std::exp` internally; probe has no direct std::exp calls; reference unchanged). EXACT attempt fired immediately: 27-ULP residual (far beyond 1-ULP Math.exp slack). **A19 fired** with diagnosis: `Math.log(x2)` at line 86 feeds into `JQuantMath.exp(f2 * Math.log(x2) - x2 - ...)` — Math.log slack dominates. Tier kept TIGHT (max diff 2.99e-15, well within `abs 1e-14 + rel 1e-12`). |

**Phase 2j implication:** Math.log is empirically confirmed as the actual NCCS floor. Once `JQuantMath.log` lands, NCCS flips to EXACT with a one-line test change.

### WI-3 — GaussLaguerre + GaussLobatto tier annotations updated

| Commit | Description |
|--------|-------------|
| `17bcef5` | `Math.cos` → `JQuantMath.cos` in GaussLaguerreIntegrationTest (line 90); `Math.sin` → `JQuantMath.sin` in GaussLobattoIntegralTest (line 91). Tier already at TIGHT — but the inline comment justification changed from "Math.cos/sin slack workaround" to "correct-by-construction via correctly-rounded primitive." Qualitative improvement: tier is now structurally justified rather than a workaround. |

## A-trigger fire history

| Trigger | Where | Outcome |
|---------|-------|---------|
| **Pre-port BLOCKED** | First WI-1 dispatch | Discovered: design's "~1500 LOC paired" estimate based on Phase 2i exp's mockingbirdnest C++/MSVC mirror. Canonical CORE-MATH (Inria) `sin.c`+`cos.c` is **~4159 LOC combined** with `unsigned __int128` extended-precision type and ~2000 longs of table data per primitive. User chose Option 1: full port at corrected scope, sub-layered (1.0 dint64_t infrastructure + 1.1 SinCosKernel). |
| **A19** | WI-2 NCCS EXACT attempt | Fired: 27-ULP residual after Math.exp swap proves Math.log is the actual NCCS floor. Documented as Phase 2j seed candidate. |

A2/A3/A4/A6/A8/A9/A13/A15/A16/A17/A18 did not fire.

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P2I5-10** | Sub-layer WI-1 into 1.0 (Dint64) + 1.1 (SinCosKernel) after scope correction | ~5000 LOC port too large for single commit; Dint64 is reusable foundation that benefits from independent test coverage. |
| **P2I5-11** | Use canonical Inria source (`gitlab.inria.fr/core-math/core-math`) for sin/cos, not the mockingbirdnest GitHub mirror | Mirror is C++/MSVC-adapted with `absl::uint128`; canonical Inria source is plain C with `__int128` and is what the Java port needs to match. (gitlab.inria.fr serves the source reliably; no fallback needed.) |
| **P2I5-12** | Python table-extractor for static initializers | Mechanical extraction (CORE-MATH hex floats → `Double.longBitsToDouble`) eliminates hand-transcription errors. Pattern reusable for future log/pow ports. |
| **P2I5-13** | Dint64 mutability + non-aliasing contract | CORE-MATH C uses out-parameters (`add_dint(*r, *a, *b)`); Java port replicates with `addAssign(a, b)` writing to receiver. Aliasing (`r.addAssign(r, x)`) explicitly forbidden in Javadoc to match CORE-MATH's actual call-site usage and avoid subtle bugs. |
| **P2I5-14** | Probe oracle for cos/sin = CORE-MATH `cr_cos`/`cr_sin` directly via `#include "coremath/{sin,cos}.c"` | Phase 2i A3 lesson: Apple libm not always correctly-rounded. Probes use the canonical correctly-rounded reference. |
| **P2I5-15** | NCCS A19 path forward = wait for `JQuantMath.log` (Phase 2j) | EXACT promotion blocked by Math.log slack, not Math.exp. Cleanly attributable; defer with one-line test change ready. |

## JVM-vs-libc++ ULP-slack outcome (refresh)

Phase 2i.5 extends Phase 2i's correctly-rounded coverage:

**Now correctly-rounded against CORE-MATH `cr_*`:**
- `JQuantMath.exp` (Phase 2i)
- `JQuantMath.cos` (Phase 2i.5)
- `JQuantMath.sin` (Phase 2i.5)

**Still platform-dependent (`Math.*` with potential 1-ULP slack):**
- `Math.log`, `Math.pow`, `Math.tan`, `Math.asin`, `Math.acos`, `Math.atan`, `Math.atan2`, `Math.sinh`, `Math.cosh`, `Math.tanh`, `Math.expm1`, `Math.log1p`, `Math.cbrt`, `Math.hypot`

**Highest-leverage Phase 2j candidate:** `JQuantMath.log` — empirically confirmed as the NCCS floor via WI-2 A19. Once available, NCCS flips to EXACT trivially. Other beneficiaries: any test currently citing `Math.log` slack (audit didn't surface any; would need re-audit with log-aware lens).

## Phase 2j seed list (refresh from Phase 2i seed + this phase's discoveries)

### Transcendental ports (high → low priority)

1. **CORE-MATH `log` port** — empirically confirmed as NCCS floor. Likely smaller LOC than cos/sin (no Payne-Hanek; algorithm closer to exp). Reuses `Dint64` infrastructure — saves ~500 LOC of foundation work. Highest-leverage transcendental port remaining.
2. **CORE-MATH `pow` port** — depends on log + exp. Not currently cited but structurally completes the trig + base-arithmetic primitive set. May reuse Dint64 + SinCosKernel patterns.
3. **NCCS CDF EXACT-tier flip** (after `JQuantMath.log` lands) — one-line test change.

### Non-transcendental floor investigations (carry-forward + new)

4. **Douglas ADI scheme rounding** — FdHullWhite remaining floor (Phase 2i WI-2 B-1 A19).
5. **FdmAffineModelTermStructure discount projection** — co-suspect for FdHullWhite floor.
6. **NCCS chain accumulated drift** — distinct from CDF transcendental concern.
7. **SABR Halton+LM/Simplex optimizer accumulation** — 2 audit sites.

### Carry-forward from Phase 2h (Fdm framework completeness)

8. Bermudan/American/dividend in Fdm vanillaComposite + step-condition classes
9. BiCGStab/GMRES iterative solvers
10. Schemes beyond Hundsdorfer/Douglas/ImplicitEuler (CraigSneyd, ModifiedCraigSneyd, etc.)
11. Fdm2DimSolver derivative accessors
12. BicubicSplineInterpolation Address-mapping audit (broader)

### Carry-forward (other engines)

13. **Gaussian1D family** (10 engines + model) — Phase 2j primary scope candidate (originally planned; still on the list).
14. Other Fdm-dependent engines (FdHestonHullWhite, FdSabrVanilla, FdBlackScholesVanilla, FdConvertibleBond, FdAndreasenHugeLocalVol)
15. HestonProcess.pdf() Fokker-Planck (only if Java caller emerges)
16. BlackSwaptionEngine Cash/ParYieldCurve settlement
17. additionalResults in cap/swaption engines

## Out-of-scope (explicit, deferred)

- BroadieKaya asset-leg retry (audit predicted ~A19; deferred until cos/sin/log/pow all available).
- `JQuantMath.tan`, `asin`, `acos`, `atan`, `atan2`, `sinh`, `cosh`, `tanh`, `expm1`, `log1p`, `cbrt`, `hypot` — none cited in audit; not needed for any current TIGHT/per-test pin.
- Performance benchmarking of `JQuantMath.cos`/`.sin` vs `Math.cos`/`.sin` — noted but not measured.
- Codebase-wide `Math.* → JQuantMath.*` swap — only the 5 specified WI-2/WI-3 sites were swapped (3 in NCCS + 1 each in GaussLag/Lob).
- `Math.sqrt`, `Math.fma`, basic arithmetic — JVM matches CORE-MATH, no port needed.
