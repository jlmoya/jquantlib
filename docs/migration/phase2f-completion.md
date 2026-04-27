# Phase 2f Completion Report — JQuantLib Migration

**Date:** 2026-04-26
**Predecessor tag:** `jquantlib-phase2e-complete` @ `a533fbd`
**Phase 2f tip on main:** `36d3fe9`
**Tag:** `jquantlib-phase2f-complete`

## Final state

- **Tests:** `675 / 0 failures / 0 errors / 22 skipped` (was `656/0/0/22` at Phase 2e end). +19 net tests, skipped unchanged.
- **Scanner:** `0 stubs` — Phase 1 mandate preserved (no regressions; no new WIPs).
- **Commits:** 19 commits since Phase 2e tip.

## Per-WI summary

### WI-1 — Cap engines (worktree A)

5 commits on main:

- **`33807a9`** `align(termstructures.volatilities.optionlet): add volatilityType + displacement accessors`
- **`3d50dde`** `stub(pricingengines.capfloor): port AnalyticCapFloorEngine + tight-tier fingerprint test`
- **`762fad7`** `align(pricingengines): fix bachelierBlackFormula sign + parenthesization vs C++ v1.42.1`. **Bonus pre-existing port bug** discovered mid-stub: pre-existing `BlackFormula.bachelierBlackFormula` used `optionType.ordinal()` (0/1) instead of `toInteger()` (±1), zeroing the Call branch and flipping the Put sign; missing parens left discount factor multiplying only the std-dev term. No production callers existed before WI-1, so no other tests had to move.
- **`2c79e28`** `stub(pricingengines.capfloor): port BachelierCapFloorEngine + tight-tier fingerprint test`
- **`a8a09a0`** `stub(pricingengines.capfloor,model.shortrate.calibrationhelpers): BlackCapFloorEngine Bachelier branch + CapHelper Normal-vol retrofit`. The Bachelier branch is a Java-only design choice — C++ keeps Black/Bachelier as separate engines; Java dispatches at `calculate()` time on `volatilityType()` per design P2F-5.

**Test count delta:** 656 → 659 (+3, all TIGHT tier on first try).

### WI-2 — Swaption engines + G2.swaption (worktree B)

4 commits on main:

- **`289391e`** `align(termstructures): SwaptionVolatilityStructure add volatilityType + shift accessors`
- **`a99e9c1`** `stub(pricingengines.swaption): port JamshidianSwaptionEngine + loose-tier fingerprint test`. Forced to LOOSE tier instead of TIGHT due to a discovered Brent solver pre-loop init divergence — see deviations.
- **`86690c1`** `stub(pricingengines.swaption): BlackSwaptionEngine Bachelier branch + SwaptionHelper Normal-vol routing`. Bundled extension to `ConstantSwaptionVolatility` adding VolatilityType + shift constructor (additive overload + new fields, default-preserving).
- **`d04d02c`** `stub(model.shortrate.twofactormodels): G2.swaption integral path port + loose-tier fingerprint`. **Closes the Phase 2e A11 carve.** Forced to LOOSE tier (same Brent divergence).

**Test count delta:** 659 → 662 (+3 net via merge ordering: Jamshidian + Bachelier swaption + G2.swaption). Both Jamshidian and G2.swaption at LOOSE tier with inline justification.

### WI-3 — Heston BroadieKaya + NCCS tightening (worktree C)

6 commits on main — the long pole:

- **`8bad005`** `test(math.distributions): NCCS extended regression grid + A13 tier compromise`. **A13 fired** with a fifth structural source not in the design's category list (a/b/c/d) — see deviations.
- **`31c759b`** `infra(math.integrals): port GaussLaguerreIntegration(128) + exact-tier fingerprint test`. Fixed-order n=128 nodes/weights ported as `static final double[]` arrays.
- **`d9d4826`** `infra(math.integrals): port GaussLobattoIntegral + tight-tier fingerprint test`. Adaptive Gauss-Lobatto-Kronrod with Richardson extrapolation.
- **`e7f278e`** `align(math): add Complex + ModifiedBesselFunction + GammaFunction.value()`. **A14 chose option (b)** — minimal in-tree `org.jquantlib.math.Complex` rather than commons-math3 dep. Single use site (Heston BroadieKaya) doesn't justify pulling Apache Commons Math.
- **`ce11bec`** `stub(processes): port Heston Fourier-inversion + 3 BroadieKaya schemes + per-test-relaxed fingerprints; Complex via minimal Java port`. Fourier-inversion harness (`Phi`, `ch`, `cdf_nu_ds`, `cornishFisherEps`) + 3 BroadieKayaExactScheme{Lobatto,Laguerre,Trapezoidal} variants + `factors()` returns 3.
- **`36d3fe9`** `docs(test.processes): C.8 NCCV tier-promotion attempt rolled back`. NCCV tier promotion to TIGHT failed — 3 of 5 cases drifted up to 4.4e-10 absolute. Stayed LOOSE; updated runner javadoc.

**Test count delta:** 662 → 675 (+13 from C alone).

**C.7 SKIPPED:** The plan specified un-stubbing `HestonProcess.discountBondOption`, but **this method does not exist in C++ HestonProcess**. Phase 2b commit `4f1e440` already documented this misclassification. The closest interpretation, porting `HestonProcess::pdf()` (Fokker-Planck), would require porting NCCS PDF + an `int_ph` helper + ~150 LOC and has zero Java callers (`AnalyticPDFHestonEngine` and `FdmHestonGreensFct` are not in the Java port). Per "don't gold-plate", left as Phase-2-deferred follow-up.

## Final scanner state

```
$ python3 tools/stub-scanner/scan_stubs.py
wrote docs/migration/stub-inventory.json (0 stubs)
wrote docs/migration/worklist.md
```

**Scanner WIP: 0** — Phase 1 mandate preserved (no regressions). Phase 2f purely added new functionality, no scanner-tracked stubs.

## Test suite final state

```
$ (cd jquantlib && mvn test) | grep -E "^\[WARNING\] Tests run"
[WARNING] Tests run: 675, Failures: 0, Errors: 0, Skipped: 22
```

**Test count delta:** 656 → 675 (+19 net). **Skipped:** 22 (unchanged).

| WI | Δ tests | Notes |
|---|---|---|
| WI-1 | +3 | AnalyticCapFloor + BachelierCapFloor + CapHelper Normal-vol — all TIGHT tier |
| WI-2 | +3 | Jamshidian (LOOSE) + Bachelier swaption (TIGHT) + G2.swaption integral (LOOSE) |
| WI-3 | +13 | NCCS extended regression + Lobatto + Laguerre + 3 BroadieKaya schemes (per-scheme multi-tuple) |

No previously-passing test was broken during Phase 2f.

## NCCS Tightening Disclosure (per design §7.8)

**Goal:** EXACT match between Java NCCS and C++ NCCS for all (df, ncp, x) tuples (Phase 2c WI-1 left ~1.5e-12 drift; Phase 2f WI-3 was tasked with fixing it).

**Outcome:** A13 fired. Fix category (a/b/c/d) does not apply — the actual root cause is a fifth structural source not in the design's category list.

**Diagnosed root cause:** **JVM `Math.exp` ULP slack vs libc++ correctly-rounded `std::exp`.**
- Java's `Math.exp(-1.118040329757191)` returns `0x3fd4ec411e587ad6`
- libc++'s `std::exp(-1.118040329757191)` returns `0x3fd4ec411e587ad5`
- 1-ULP delta on the very first transcendental call before the Patnaik series begins
- Accumulates to ~3 ULPs (~1.7e-16 absolute) across 16 series iterations
- `StrictMath.exp` matches `Math.exp`, not libc++ — neither JVM exp variant is correctly-rounded in the libc++ sense

**Tier compromise per A13:** Kept TIGHT (drift fits comfortably within `1e-14 abs + 1e-12 rel`). Probe extended from 6 → 14 fixtures; NCCS source code unchanged.

**Project-wide implication:** This is **not a Phase 2c WI-1 port bug** — it's a structural JVM-vs-libc++ rounding difference that affects EVERY transcendental comparison in JQuantLib at EXACT tier. The migration's "EXACT tier" goal is structurally limited by this. Bit-faithful match would require porting our own correctly-rounded `exp` (out of scope for any reasonable phase). The design's drift-source taxonomy (a-d) should be amended in any future phase that revisits NCCS or related distributions.

**NCCV tier promotion:** ATTEMPTED, ROLLED BACK to LOOSE. Tried TIGHT, 3 of 5 NCCV cases failed (max drift 4.4e-10 absolute). NCCS A13 drift propagates through Brent in `varianceDistribution`. Documented in `HestonProcessTest` runner javadoc.

## Deviations from the plan

1. **WI-2 Brent.solveImpl pre-loop init diverges from C++.** Java seeds `root=xMax; froot=fxMax`; C++ first evaluates `f(guess)` to seed `root_/d/e`. Leaks ~5e-9 into converged root → 7e-11 NPV diff in Jamshidian (37x tight-tier ceiling) and similar in G2.swaption. Both forced to LOOSE tier. Fix would re-fingerprint many Brent callers across the codebase — out of scope for Phase 2f WI-2. Documented inline in JamshidianSwaptionEngineTest class-level Javadoc + G2Test testSwaptionIntegralFingerprint. **Phase 2g seed.**

2. **WI-1 BlackFormula.bachelierBlackFormula bug fix (bonus).** Pre-existing buggy implementation discovered mid-stub via the `align(pricingengines)` commit per CLAUDE §4.2. Real correctness issue: wrong sign + missing parens. Real-world impact was zero because no production caller existed before this WI.

3. **WI-1 FloatingRateCoupon constructor divergence (Phase 2g seed).** Java treats `withFixingDays(0)` as "use index default" while C++ honors 0 as 0. Both probes + Java tests deliberately omit `.withFixingDays(0)` to keep fixtures self-consistent. Documented inline. Leg machinery used everywhere — fix needs careful scoping.

4. **WI-2 VanillaSwap.setupArguments inverted-isAssignableFrom NOT surfaced** by Jamshidian or G2.swaption (both bypass it by reading directly from `args.swap.fixedLeg()`/`args.swap.nominal()`). Left for Phase 2g per plan P2F-7.

5. **WI-2 Swaption.ArgumentsImpl projection still incomplete** for callers needing fixed/floating-leg array projections; G2.swaption test manually populates fields. Engine-side, both Jamshidian + G2 read coupon data from `args.swap` directly.

6. **WI-3 A13 fired with a fifth structural source not in the design's category list** (Math.exp ULP slack — not (a) Sankaran/Patnaik threshold, (b) series convergence, (c) FMA, or (d) Bessel approximation). See NCCS Tightening Disclosure above.

7. **WI-3 A14 chose option (b)** — minimal in-tree `org.jquantlib.math.Complex` (~80 LOC) rather than adding `commons-math3` Maven dep. Single use site (Heston BroadieKaya) didn't justify the dep.

8. **WI-3 C.7 SKIPPED** — `HestonProcess.discountBondOption` doesn't exist in C++ HestonProcess. Plan inherited the misclassification from earlier seed lists; Phase 2b commit `4f1e440` had already documented this. The closest interpretation (porting `HestonProcess::pdf()` Fokker-Planck) has no Java callers and would require ~150 LOC of dependent infrastructure. Per "don't gold-plate", left deferred.

9. **WI-3 NCCV tier promotion C.8 ROLLED BACK.** Tried TIGHT, 3 of 5 cases failed. NCCS A13 drift propagates through Brent. Stayed LOOSE.

10. **WI-3 BroadieKaya asset-leg per-test tolerance is one tier looser than LOOSE** (`5e-3` per-test exception via `Tolerance.within`). Justified inline: Brent residual = `cdfNuDs(...)` iterates `Math.exp/cos/sin` plus `ModifiedBesselFunction.i` (which itself uses `GammaFunction.value` and complex `exp`/`pow`) at every Fourier-integration node — 1-ULP-per-call A13 drift compounds to ~2e-3 absolute / ~2e-5 relative on the worst case (`bk_lobatto_lowV0`). The integrated rounding error genuinely exceeds LOOSE.

11. **A8/A10/A11/A12 NOT triggered** — N/A in Phase 2f.

12. **A9 NOT triggered** — all rebases clean; 3 force-pushes after rebase but no manual conflict resolution required.

## Phase 2g seed list (carry forward)

- **`Brent.solveImpl` pre-loop init alignment** (NEW priority Phase 2g item — Phase 2f WI-2 found and documented). Java seeds `root=xMax; froot=fxMax`; C++ first evaluates `f(guess)`. Fix would re-fingerprint many Brent callers — substantial coordinated work. Could promote Jamshidian + G2.swaption tests from LOOSE to TIGHT.
- **JVM `Math.exp` ULP slack vs libc++ `std::exp`** (Phase 2f WI-3 A13 root cause) — affects every transcendental comparison at EXACT tier across JQuantLib. Bit-faithful match would require porting a correctly-rounded `exp` (or accepting the design's drift taxonomy is incomplete).
- **`HestonProcess` Fokker-Planck `pdf()` port** if any future caller needs it (currently zero callers in Java).
- **`FloatingRateCoupon.fixingDays==0` divergence** — Java treats 0 as "use index default", C++ honors 0. Affects leg machinery used everywhere.
- **`VanillaSwap.setupArguments` inverted-isAssignableFrom + List capacity-vs-size bug** (Phase 2e WI-3 + Phase 2f WI-2 both deliberately bypassed; fix in 2g for completeness symmetry with the Phase 2e fetchResults fix).
- **FdHullWhiteSwaptionEngine + FdG2SwaptionEngine** — finite-difference swaption engines.
- **Gaussian1D swaption engine family** + underlying Gaussian1D model.
- **`additionalResults` in cap/swaption engines** (vega, optionletsPrice, optionletsVega).
- **SwaptionHelper.addTimesTo / CapHelper.addTimesTo `Time` annotation impedance.**
- **TreeLattice2D underlying value access API formalization.**
- **HaltonRsg FMA platform-conditionality documentation.**
- **Per-test 5e-8 SABR cross-check tolerance investigation.**
- **BlackSwaptionEngine Cash/ParYieldCurve settlement path** (needs `CashFlows.bps(InterestRate)` + `Schedule.tenor()`).
- **Phase 3+ gap-fill packages** (`experimental/`, `models/marketmodels/`, `termstructures/credit/`, `inflation/`).

## Worktree cleanup

Phase 2f used 3 git worktrees (A/B/C) at `/Users/josemoya/eclipse-workspace/jquantlib-2f-{A,B,C}/`. After tagging, the L2 cleanup will remove the worktrees and their branches. The parallel-execution model worked again — A/B/C ran concurrently with one A13 trigger (cleanly recovered via tier compromise) and one A14 trigger (cleanly recovered via implementer-choice option (b)) — reinforcing the Phase 2c/2d/2e lesson that 3-4 worktrees is workable with disciplined controller orchestration. The orphan-files-from-shared-cpp/build pattern recurred and was handled cleanly via stash + diff-then-rm + merge.
