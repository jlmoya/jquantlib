# Phase 2i Completion — Transcendental Library Port (Option D pivot)

**Status:** complete 2026-04-28
**Tag:** `jquantlib-phase2i-complete` @ `13a3a60`
**Predecessor:** `jquantlib-phase2h-complete` @ `f0256c8`
**Plan:** `docs/migration/phase2i-plan.md` (commit `4dcbe8f`, with Option D scope reduction in design addendum)
**Design:** `docs/migration/phase2i-design.md` (commit `ad39ee9` + addendum `9739bc7`)
**Audit:** `docs/migration/phase2i-tier-audit.md` (commit `4e9eed7`)

## Final state

| Metric | Phase 2h tip | Phase 2i tip | Δ |
|--------|--------------|--------------|----|
| Tests | 677/0/0/22 | 684/0/0/22 | +7 (+4 helper-class self-tests, +1 EXACT exp test, +2 bitsEqual self-tests) |
| Scanner WIP | 0 | 0 | unchanged |
| Transcendental package | (none) | `org.jquantlib.math.transcendental` with `JQuantMath.exp` | new |
| FdHullWhite swaption tier | LOOSE 2e-12 | `within(3e-12)` (~3000× tighter than LOOSE) | partial; A19 fired |
| Probe oracle for transcendentals | (none — first probe) | CORE-MATH `cr_exp` directly (NOT `std::exp`) | new pattern |

Aggregate test-count target was 682 (per design); ceiling 685; actual 684 — within range. Hit a 1-test overshoot vs the original ceiling because `MathTestSupport` adds 4 helper self-tests (not 1 as the original draft counted) and 2 more came from the `bitsEqual` follow-up.

## What landed

### WI-1 — `JQuantMath.exp` correctly-rounded port (Option D)

7 commits on main, all passing direct-to-main TDD:

| Commit | Description |
|--------|-------------|
| `2ab7ecf` | `MathTestSupport` bit-pattern test helper (assertBitsEqual + parseHexBits + 4 self-tests) |
| `9739bc7` | Design addendum — Option D pivot (msun ≠ libc++; CORE-MATH only) |
| `50c3774` | Progress doc reflecting Option D pivot |
| `d1c3eda` | CORE-MATH `exp.c` ported to Java (`ExpKernel.java` ~430 LOC + `JQuantMath.java` facade + probe + EXACT test); 681 → 682 |
| `807ad6c` | Facade Javadoc fix — attribution to CORE-MATH/Sibidanov/MIT (was stale msun reference) |
| `6a6be46` | Test diagnostics — `MathTestSupport.bitsEqual` (returns boolean) + collect-all-failures pattern in `JQuantMathExpTest`; 682 → 684 |
| `d94aea7` | Hard-cases DB probe coverage — 43/51 entries added (initial workaround for then-unknown Apple-libm bug) |
| `a61b920` | **A3 resolution** — Apple libm shown not-always-correctly-rounded at 8 DB hard cases (verified via 300-bit mpmath); switched probe oracle from `std::exp` to CORE-MATH `cr_exp` directly. All 51/51 DB entries restored. |
| `c7df2e0` | Progress doc — WI-1.1 closed |

**End state:** `JQuantMath.exp` is bit-exactly correctly-rounded across the 508-case probe set. Probe oracle is CORE-MATH `cr_exp` itself (gives provably correctly-rounded reference values). EXACT-tier test (`JQuantMathExpTest`) iterates all 508 cases via collect-all-failures pattern.

### WI-2 B-1 — FdHullWhite tier flip (A19 partial)

| Commit | Description |
|--------|-------------|
| `305ce24` | 4 compounded `Math.exp` call sites swapped to `JQuantMath.exp` on the FdHullWhite hot path; tier LOOSE 2e-12 → `Tolerance.within(3e-12)` |
| `6f728e3` | Progress doc — WI-2 B-1 closed |

Files touched:
- `OneFactorAffineModel.discountBond`: `A(t,T) * exp(-B(t,T)*r)` evaluation
- `Vasicek.B`: `(1.0 - exp(-a*(T-t))) / a` (used by HullWhite via inheritance)
- `HullWhite.A`: overridden, uses exp on the value computation
- `HullWhite.FittingParameter.Impl.value`: `sigma*(1.0 - exp(-a*t))/a`

**A19 fired (partial):** swap closed essentially zero of the residual gap (pre: ~2.0e-12; post: 1.9935e-12). The Phase 2h thesis that `Math.exp` 1-ULP slack dominated the FdHullWhite floor was *wrong*. The structural source is non-transcendental (Douglas ADI scheme rounding or `FdmAffineModelTermStructure` discount projection rounding chain). Tier improvement is real (~3000× tighter than LOOSE) but not full TIGHT (~1.96e-12).

### WI-2 B-2 / B-3 — DEFERRED (Option D pivot)

Out of scope per design addendum. B-2 (BroadieKaya) and B-3 (NCCS) both need `log`/`sin`/`cos` paths beyond `exp` alone, so they cannot be addressed by exp-only port. Carried forward to Phase 2j seed list.

### WI-3 — Tier outcome audit

| Commit | Description |
|--------|-------------|
| `4e9eed7` | `docs/migration/phase2i-tier-audit.md` — classifies all TIGHT and per-test-exception pins by source-of-slack |

Key findings from the audit:
- **1 test flipped this phase** (FdHullWhite, partial)
- **8 non-transcendental floors** identified across 5 themes (FdHullWhite scheme rounding, NCCS chain drift, SABR optimizer accumulation, 4 Phase-2g Brent-fix tights — already optimal)
- **4 transcendental candidates for Phase 2i.5/2j**:
  - NCCS CDF (`Math.exp` cited; **High priority** — `JQuantMath.exp` already available, just needs wiring)
  - GaussLaguerre cos-integrand (`Math.cos`)
  - GaussLobatto transcendental integrands (`Math.exp` + `Math.sin` + `Math.cos`)
  - BroadieKaya asset leg (`Math.exp` + `Math.cos` + `Math.sin` compounded, Low — 5e-3 floor likely persists)
- **Top primitives by citation frequency:** `Math.exp` 3×, `Math.cos` 2×, `Math.sin` 2×, `Math.log` 0×, `Math.pow` 0×
- **6 unclear/unannotated** (all infrastructure tests, no quant-domain relevance)

## A-trigger fire history

| Trigger | Where | Outcome |
|---------|-------|---------|
| **Pre-pivot BLOCKED** | First WI-1.1 dispatch | msun ≠ libc++ surfaced. User chose Option D pivot. |
| **A3** | WI-1.1 DB-coverage testing | Apple libm not-always-correctly-rounded at 8/51 hard cases. Resolved by switching probe oracle to CORE-MATH `cr_exp`. |
| **A19** | WI-2 B-1 | `Math.exp → JQuantMath.exp` swap closed essentially nothing of FdHullWhite residual. Confirmed transcendentals are NOT the dominant floor for that test. |

A2/A4/A6/A8/A9/A13/A15/A16/A17/A18 did not fire.

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P2I-13** | Pivot to Option D (correctly-rounded `exp` only via CORE-MATH) | msun has same 1-ULP slack as JVM Math.exp; original "port msun" thesis was equivalent to "do nothing" against the WI-2 promotion goal. |
| **P2I-14** | Algorithm source = CORE-MATH `src/binary64/exp/exp.c` (BSD/MIT, correctly-rounded by design) | Modern, specifically targets bit-exact correctness across all rounding modes. |
| **P2I-15** | Drop WI-2 B-2 (BroadieKaya) and B-3 (NCCS) from this phase scope | Both rely on log/sin/cos paths beyond `exp`. Not addressable with exp-only port. |
| **P2I-16** | Keep WI-1.1 WIP artifacts (probe + reference + facade + test); only `ExpKernel.java` discarded as wrong-algorithm | Probe and test scaffold are correct; only the Java algorithm needs replacement. |
| **P2I-17** | Probe oracle for transcendentals = CORE-MATH `cr_*` directly, NOT platform `std::*` | A3 finding: Apple libm is *almost* correctly-rounded but not provably so. Future log/sin/cos/pow ports must use CORE-MATH `cr_log`/`cr_sin`/etc. as oracles. |
| **P2I-18** | FdHullWhite TIGHT promotion abandoned; tier set to `within(3e-12)` | A19 finding: real FdHullWhite floor is non-transcendental. Pursuing TIGHT requires fixing Douglas ADI / FdmAffineModelTermStructure — Phase 2j scope. |

## JVM-vs-libc++ ULP-slack outcome

The Phase 2f A13 finding ("JVM Math.exp 1-ULP slack vs libc++ std::exp") was real but its *implications* were not what we thought:

**True facts revealed during Phase 2i:**
- JVM `Math.exp` has up to 1 ULP slack vs the correctly-rounded result (confirmed)
- Apple libm `std::exp` is *almost* correctly-rounded but not provably (~99.something% correctly rounded; ~50/2^64 hard cases still 1-ULP off) — surfaced by A3
- FreeBSD msun `e_exp.c` has the same 1-ULP slack as JVM Math.exp (NOT libc++) — surfaced by pre-pivot BLOCKED
- CORE-MATH `cr_exp` is correctly-rounded by design — the only oracle robust enough for EXACT-tier transcendental claims

**Impact on QuantLib port:**
- For tests pinned at TIGHT due to genuine `Math.exp` slack: `JQuantMath.exp` swap will help, IF the `Math.exp` slack is the dominant residual (B-1 showed it isn't always — A19)
- For tests where transcendental slack is dominated by accumulated polynomial rounding, scheme rounding, or compounded interpolation: the swap helps marginally if at all
- The thesis "transcendentals are the dominant floor across the suite" was incorrect for FdHullWhite. Audit suggests it's *probably* still correct for NCCS CDF and the GaussLaguerre/Lobatto integrands (high `Math.exp`/`Math.cos`/`Math.sin` citation frequency) — but those weren't tested experimentally this phase.

**Practical takeaway:** Future tier promotions should be experimentally validated, not assumed from inline source-of-slack annotations. Comments rot; A19 demonstrated.

## Phase 2j seed list (carry-forward + this phase's discoveries)

### Transcendental ports (high → low priority by audit)

1. **NCCS CDF wiring** — `JQuantMath.exp` available; just rewire NCCS code (no new port). Highest-leverage transcendental work.
2. **CORE-MATH `cos`/`sin` port** — cited 2× each in audit. Would unlock GaussLaguerre / GaussLobatto / BroadieKaya potential promotions. ~600 LOC each (Payne-Hanek argument reduction shared).
3. **CORE-MATH `log` port** — not cited in audit but needed by future BroadieKaya / Heston paths. ~400 LOC.
4. **CORE-MATH `pow` port** — not cited; lowest priority. Decompose `x^y = exp(y*log(x))` once `exp` and `log` are available.

### Non-transcendental floor investigations (Phase 2j)

5. **Douglas ADI scheme rounding** — FdHullWhite remaining floor (per WI-2 B-1 A19). Investigate `HundsdorferScheme` / `DouglasScheme` numerical stability.
6. **FdmAffineModelTermStructure discount projection** — co-suspect for FdHullWhite floor.
7. **NCCS chain accumulated drift** — distinct from CDF transcendental concern.
8. **SABR Halton+LM/Simplex optimizer accumulation** — 2 audit sites.

### Carry-forward from Phase 2h (Fdm framework completeness)

9. Bermudan/American/dividend in Fdm vanillaComposite + step-condition classes
10. BiCGStab/GMRES iterative solvers
11. Schemes beyond Hundsdorfer/Douglas/ImplicitEuler (CraigSneyd, ModifiedCraigSneyd, etc.)
12. Fdm2DimSolver derivative accessors
13. BicubicSplineInterpolation Address-mapping audit (broader)

### Carry-forward (other engines)

14. Gaussian1D family (10 engines + model)
15. Other Fdm-dependent engines (FdHestonHullWhite, FdSabrVanilla, FdBlackScholesVanilla, FdConvertibleBond, FdAndreasenHugeLocalVol)
16. HestonProcess.pdf() Fokker-Planck (only if Java caller emerges)
17. BlackSwaptionEngine Cash/ParYieldCurve settlement
18. additionalResults in cap/swaption engines
19. Various test-tolerance investigation items

## Out-of-scope (explicit, deferred)

- `tan`, `asin`, `acos`, `atan`, `atan2`, `sinh`, `cosh`, `tanh`, `expm1`, `log1p`, `cbrt`, `hypot` — none cited in audit; not needed for any current TIGHT/per-test pin.
- Performance benchmarking of `JQuantMath.exp` vs `Math.exp` — noted but not measured (correctness-first).
- Codebase-wide `Math.exp → JQuantMath.exp` swap — only the 4 named FdHullWhite hot-path sites were swapped per surgical scope.
- `Math.sqrt`, `Math.fma`, basic arithmetic — JVM matches CORE-MATH/correctly-rounded here, no port needed.
