# Phase 2i — Tier Audit Report

Inventory of TIGHT and per-test-exception assertions in the JQuantLib test suite,
classified after Phase 2i Option D landed (WI-1.1 CORE-MATH `exp` + WI-2 B-1 FdHullWhite swap).

## Methodology

For each TIGHT or per-test-exception call site, identify the structural source of slack
via inline comments. Categories:

- **(a) flipped this phase** — promoted in Phase 2i.
- **(b) blocked by non-transcendental floor** — cites source other than transcendentals
  (Brent residual, scheme rounding, accumulated polynomial rounding, etc.).
- **(c) candidate for future Phase 2i.5 / 2j** — cites `Math.exp` / `Math.log` /
  `Math.sin` / `Math.cos` / `Math.pow` as the dominant bottleneck; would benefit
  from porting the corresponding CORE-MATH primitive.
- **(d) unclear** — no source annotation.

The audit scanned `jquantlib/src/test/java/` for `Tolerance.tight`, `Tolerance.within`,
`Tolerance.absRel`, `Tolerance.exact`, and `assertEquals(…, [0-9]e-[0-9])` patterns
(87 raw grep hits across 22 test files).  `Tolerance.exact` hits are excluded from the
TIGHT/per-test classification because they represent bit-exact structural contracts
(node/weight tables, FX rate pass-through, long-exact type checks) — not tolerance
compromises requiring future investigation.

---

## Summary

- **Total test files with TIGHT or per-test-exception assertions:** 19
- **(a) flipped this phase:** 1
- **(b) non-transcendental floor:** 8 (from 5 test files / source themes)
- **(c) transcendental candidate for Phase 2i.5 / 2j:** 4 (from 4 test files)
- **(d) unclear / infrastructure tests:** 6 (from 4 test files — all in
  `patterns/`, `util/`, `optimization/` infrastructure, not quant-domain)

---

## (a) Flipped this phase

| Test file | Tier change | Phase 2i commit |
|-----------|-------------|-----------------|
| `pricingengines/swaption/FdHullWhiteSwaptionEngineTest.java:170` | LOOSE 2e−12 → `within(3e−12)` (A19 partial; TIGHT unreachable) | `305ce24` |

Comment: `"FD NPV — Phase 2i WI-2 B-1 attempted a tier flip from LOOSE to TIGHT after the Math.exp -> JQuantMath.exp swap on the FdHullWhite hot path. The swap left the diff essentially unchanged (~1.99e-12), confirming A19: the residual is NOT dominated by Math.exp 1-ULP slack."`

---

## (b) Non-transcendental floors (no Phase 2i.5 benefit)

| Test file:line | Tier | Cited source | Notes |
|----------------|------|--------------|-------|
| `pricingengines/swaption/FdHullWhiteSwaptionEngineTest.java:170` | `within(3e−12)` | Douglas ADI scheme rounding / `FdmAffineModelTermStructure` discount projection | A19 fired; structural floor is NOT Math.exp. Remains 3000× tighter than LOOSE; TIGHT requires Douglas scheme or discount-projection investigation. |
| `model/shortrate/HullWhiteCalibrationTest.java:~90` | TIGHT inline note (1e−13 abs) | IEEE-754 accumulated rounding through chi-squared CDF chain (`rho, psi, b, z` intermediates ~3.5e−14) | Comment: `"chained intermediates accumulate ~3.5e-14 of IEEE 754 rounding drift … fixed 1e-13 absolute floor — still two orders tighter than the loose tier"`. Non-transcendental: pure polynomial / rational arithmetic chain. |
| `model/shortrate/HullWhiteCalibrationTest.java:163,167` | TIGHT | Phase 2g WI-1 Brent-fix promotion — was LOOSE due to Brent pre-loop init divergence; now bit-faithful | `"Phase 2g WI-1: tight tier promotion … per-step phi calibration now matches C++ bit-faithfully"`. Already at TIGHT; no further action needed. |
| `model/shortrate/BlackKarasinskiCalibrationTest.java:133,137` | TIGHT | Same Phase 2g WI-1 Brent-fix; BK tree exp(−φ·dt) discount values now match at TIGHT | `"Phase 2g WI-1 aligned Java Brent with C++ brent.hpp, eliminating the divergence"`. Already at TIGHT. |
| `model/shortrate/twofactormodels/G2Test.java:225` | TIGHT | Phase 2g WI-1 Brent-fix — `SegmentIntegral`-over-Brent composition now bit-faithful | `"post-Brent-fix tier promotion … SegmentIntegral-over-Brent composition tightens to the same level"`. Already at TIGHT. |
| `pricingengines/swaption/JamshidianSwaptionEngineTest.java:145` | TIGHT | Phase 2g WI-1 Brent-fix — Jamshidian NPV previously LOOSE due to Brent pre-loop divergence | `"post-Brent-fix tier promotion … new Brent matches C++ bit-faithfully"`. Already at TIGHT. |
| `math/optimization/SABRInterpolationTest.java:268` / `math/interpolations/InterpolationTest.java:1219` | `within(5e−8)` | Halton+LM/Simplex floating-point accumulation between Java port and C++ Boost SABR optimizer | Comment: `"Halton+LM/Simplex fp accumulation between Java port and C++ Boost"`. Not a transcendental bottleneck — it is accumulated optimizer iterate rounding across ~64 SABR calibration combos. |
| `processes/HestonProcessTest.java:184` | TIGHT | Phase 2g WI-1 Brent-fix — variance leg `evolved[1]` now TIGHT | `"The variance leg evolved[1] now sits at TIGHT post Phase 2g WI-1 Brent.solveImpl alignment"`. Already at TIGHT; no further action. |

---

## (c) Transcendental candidates for Phase 2i.5 / 2j

| Test file:line | Tier | Cited primitive(s) | Phase 2i.5 priority |
|----------------|------|---------------------|---------------------|
| `math/distributions/NonCentralCumulativeChiSquaredDistributionTest.java:89` | TIGHT (held; EXACT blocked) | `Math.exp` — `"the very first Math.exp(...) call before the Patnaik series even begins differs from C++ libc++ std::exp by 1 ULP"`. 1–3 ULP seed drift propagates to ~1.7e−16 absolute on CDF sum (comfortably inside TIGHT; EXACT unreachable without JQuantMath.exp on hot path). | **High** — only `Math.exp` stands between TIGHT and EXACT. WI-1.1 delivered `JQuantMath.exp`; wiring it into `NonCentralCumulativeChiSquaredDistribution.op()` is the sole gating task. |
| `math/integrals/GaussLaguerreIntegrationTest.java:77` | TIGHT | `Math.cos` — `"for transcendental integrands (cos) the result drifts by a few ULPs because Math.cos vs std::cos differ by 1 ULP per call (same A13 phenomenon as NCCS)"`. Polynomial integrands already bit-exact in practice. | **Medium** — a correctly-rounded `cos` port would promote `cos` integrand to EXACT. Lower priority than NCCS because the polynomial integrands already pass; only the cosine integrand case would benefit. |
| `math/integrals/GaussLobattoIntegralTest.java:63` | TIGHT | `Math.exp` / `Math.sin` / `Math.cos` — `"transcendental integrands drift a few ULPs through Math.{exp,sin,cos} (A13 phenomenon)"`. Smooth analytic integrands pass bit-exact; the drift is only observable on explicitly transcendental test integrands. | **Medium** — blocked by `exp`+`sin`+`cos`; full EXACT promotion requires all three. `exp` addressed by WI-1.1; `sin`/`cos` remain. |
| `processes/HestonProcessTest.java:189` | `within(5e−3)` | `Math.exp` / `Math.cos` / `Math.sin` compounded via Brent root-find over Fourier-inverted CDF — BroadieKaya asset leg | **Low** — `"The asset leg evolved[0] still depends on a Brent root-find against a Fourier-inverted CDF that iterates Math.exp / Math.cos / Math.sin / GammaFunction / ModifiedBesselFunction — every Math.* call accumulates the A13 1-ULP-per-call drift"`. Empirical floor ~2e−3 absolute. Even with all three correctly-rounded primitives the compound Fourier-CDF Brent accumulation would likely still require a per-test tier above TIGHT. |

---

## (d) Unclear / unannotated

| Test file:line | Tier | Notes |
|----------------|------|-------|
| `patterns/HandleBehaviorTest.java:33,47,48` | TIGHT | Infrastructure test exercising `SimpleQuote.value()` round-trip. Source is pure arithmetic (no transcendentals). TIGHT is structurally correct; no slack concern. |
| `util/ToleranceTest.java:11–41` | TIGHT / `within` | Self-test of the `Tolerance` utility class. Exercises the class's own boundary conditions; not a quant-domain test. |
| `math/optimization/MinpackTest.java:148` | TIGHT | MINPACK qrsolv/qrfac outputs; comment says `"enorm / qrfac / qrsolv: tight tier"`. Source is pure linear algebra — no transcendentals. |
| `math/optimization/LeastSquareTest.java:64,73,81,107` | TIGHT | LeastSquareProblem `value()` / `minimise()` outputs; pure polynomial cost function. No transcendental cited. |
| `math/optimization/SphereCylinderOptimizerTest.java:30` | TIGHT | SphereCylinder optimizer outputs; no annotation on source. |
| `math/optimization/ProjectionTest.java:46,112` | TIGHT | Projection utility outputs; pure arithmetic. |

All category-(d) entries are optimization-framework infrastructure tests with purely arithmetic
or linear-algebra-backed assertions. None show evidence of transcendental involvement; they
would not benefit from CORE-MATH primitive ports.

---

## Phase 2j seed candidates from this audit

**Phase 2i.5 candidate primitives (priority order):**

1. **`Math.exp`** (cited 3 times across NCCS, GaussLobatto, BroadieKaya) — `JQuantMath.exp`
   is already complete (WI-1.1). The remaining work is wiring it into
   `NonCentralCumulativeChiSquaredDistribution.op()` hot path. Expected tier outcome: TIGHT →
   EXACT on NCCS CDF; partial tightening on GaussLobatto transcendental integrands.
2. **`Math.cos`** (cited 2 times — GaussLaguerre cosine integrand, BroadieKaya Fourier-CDF) —
   a correctly-rounded CORE-MATH `cos` port would complete the GaussLaguerre promotio to EXACT
   and partially reduce BroadieKaya compound drift.
3. **`Math.sin`** (cited 2 times — GaussLobatto, BroadieKaya) — paired with `cos` for Fourier
   path coverage.

**Phase 2j non-transcendental candidates (from (b)):**

- **FdHullWhite Douglas ADI scheme rounding** — the remaining 3e−12 floor on
  `FdHullWhiteSwaptionEngineTest` (A19 partial) requires investigation of the
  `FdmHullWhiteOp` Douglas ADI rollback or `FdmAffineModelTermStructure::discount()`
  projection; not a transcendental bottleneck.
- **SABR optimizer accumulation** — the 5e−8 per-test tier on SABR calibration across
  64 Halton+LM/Simplex combos reflects accumulated iterate rounding between Java and C++
  Boost; would require Boost-compatible optimizer alignment, not primitive porting.
- **BroadieKaya compound Fourier-CDF** — even with correctly-rounded `exp`/`sin`/`cos`,
  the 5e−3 BroadieKaya asset-leg floor likely persists due to Brent-over-Fourier-CDF
  iteration count sensitivity. Consider tightening only after all three primitives land.

---

## Methodology caveats

- This audit relies on inline source-of-slack annotations. Category (d) entries cannot be
  classified as transcendental vs. non-transcendental without experimental swap.
- "Cited primitive" means the comment named that primitive; doesn't prove it is actually the
  dominant floor. WI-2 B-1 demonstrated that a comment can be correct in naming a primitive
  but wrong about it being the *dominant* floor (FdHullWhite exp swap had no effect).
- Phase 2i.5 priority ordering is heuristic — based on citation frequency and expected tier
  delta, not measured magnitude. Empirical verification recommended before committing to a
  priority order.
- Tests already promoted to TIGHT by Phase 2g WI-1 Brent-fix (categories b rows 3–6) appear
  here because they use `Tolerance.tight` — they are already at the ceiling and are included
  only for completeness. They require no further work.
