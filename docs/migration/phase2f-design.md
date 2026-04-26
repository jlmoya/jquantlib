# Phase 2f Design — Heston BroadieKaya + NCCS tightening; surgical cap/swaption engine close

**Status:** approved 2026-04-26.
**Predecessor:** Phase 2e — `jquantlib-phase2e-complete` @ `a533fbd`.
**Inherits unchanged:** Phase 1 design §1-12, Phase 2a §7 (P2A-1..P2A-8), Phase 2b §5 (P2B-1..P2B-7), Phase 2c §5 (P2C-1..P2C-6), Phase 2d §5 (P2D-1..P2D-6), Phase 2e §5 (P2E-1..P2E-7).
**Source-of-truth pin:** QuantLib v1.42.1 @ `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`.

---

## 1. Goals & Non-Goals

### Goal

Land the long-deferred Heston BroadieKaya completion (3 schemes + Gauss-Lobatto + Gauss-Laguerre integrators + Fourier-inversion harness) with a tightened NCCS implementation that matches C++ bit-faithfully (un-stubs `HestonProcess.discountBondOption` at TIGHT tier as a bonus); and complete the surgical close of the remaining non-Black-only cap and swaption engine ports (AnalyticCapFloorEngine + BachelierCapFloorEngine + Bachelier path in BlackCapFloorEngine + JamshidianSwaptionEngine + Bachelier path in BlackSwaptionEngine + G2.swaption integral path from the Phase 2e A11 carve).

### In scope (3 work items)

- **WI-1 — Cap engines.** AnalyticCapFloorEngine (175 LOC C++, affine-model closed-form for HW/Vasicek), BachelierCapFloorEngine (207 LOC C++, Normal-vol path), Bachelier path in BlackCapFloorEngine (~80 LOC delta — closes the `VolatilityType.Normal` `UnsupportedOperationException` from Phase 2d WI-1). OVS displacement/volType API alignment if Bachelier surfaces a need.
- **WI-2 — Swaption engines + G2.swaption.** JamshidianSwaptionEngine (201 LOC C++, affine 1-factor closed-form for HW/Vasicek/CIR), Bachelier path in BlackSwaptionEngine (~50 LOC delta), G2.swaption integral path (Phase 2e A11 carve — needs SwaptionPricingFunction Brent operator + SegmentIntegral function-object alignment + Swaption.ArgumentsImpl alignment). VanillaSwap.setupArguments inverted-isAssignableFrom + List capacity-vs-size fix bundled if any new swaption code surfaces it.
- **WI-3 — Heston BroadieKaya + NCCS tightening.** Port Gauss-Lobatto integrator (227 LOC C++) + Gauss-Laguerre integrator (~150 LOC C++) + Fourier-inversion harness (`cdf_nu_ds`, `Phi`, `ch`, `cornishFisherEps` from hestonprocess.cpp lines 230-350). Add 3 BroadieKayaExactScheme{Lobatto,Laguerre,Trapezoidal} variants to `HestonProcess.evolve`. **Tighten NCCS implementation** to match C++ bit-faithfully (diagnose the Phase 2c WI-1 ~1.5e-12 drift). Un-stub `HestonProcess.discountBondOption` at TIGHT tier. Bonus: promote Phase 2d WI-2 NCCV scheme tests from loose to tight tier if NCCS tightening makes it possible.

### Out of scope (explicitly deferred)

- FdHullWhiteSwaptionEngine + FdG2SwaptionEngine — finite-difference swaption engines.
- Gaussian1D swaption engine family — separate model family + multiple engines.
- TreeLattice2D underlying value access API formalization (works fine in Phase 2e G2Test).
- HaltonRsg FMA platform-conditionality documentation (carry-forward, doc-only).
- Per-test 5e-8 SABR cross-check tolerance investigation (carry-forward).
- additionalResults in cap/swaption engines (vega, optionletsPrice) — defer unless a Phase 2f test needs them.
- SwaptionHelper.addTimesTo / CapHelper.addTimesTo `Time` annotation impedance — defer (no current consumer).
- BlackSwaptionEngine Cash/ParYieldCurve settlement path (needs CashFlows.bps(InterestRate) + Schedule.tenor()).
- Phase 3+ gap-fill packages (`experimental/`, `models/marketmodels/`, `termstructures/credit/`, `inflation/`).

### Non-goals

- No refactoring of unrelated code.
- No API improvements beyond what v1.42.1 dictates.
- No tier loosening to force green.

---

## 2. Architecture & Components

### WI-1 — Cap engines (worktree A)

**New classes (in `org.jquantlib.pricingengines.capfloor`):**

- `BachelierCapFloorEngine` — port of v1.42.1 `bacheliercapfloorengine.{hpp,cpp}` (207 LOC). Three constructors mirroring BlackCapFloorEngine. `calculate()` iterates over optionlets using `BlackFormula.bachelierBlackFormula(...)` instead of `blackFormula(...)`. Implements `CapFloor.Engine`.
- `AnalyticCapFloorEngine` — port of v1.42.1 `analyticcapfloorengine.{hpp,cpp}` (175 LOC). Constructor takes a `OneFactorAffineModel` (or general `AffineModel`). `calculate()` iterates over optionlets and uses `model.discountBondOption(Option.Type.Put|Call, strike, exerciseTime, bondMaturity)` for analytic pricing. The HW/Vasicek `discountBondOption` analytic path that Phase 2c WI-1 + Phase 2b ports already implemented is the value generator.

**Modified:**

- `BlackCapFloorEngine` — add Bachelier path. The C++ engine was already templated `BlackStyleCapFloorEngine<Spec>` with `Black76Spec`/`BachelierSpec`; Java's port currently is Black76-only. Add a runtime branch on `volatilityType_` checked at the top of `calculate()`; if `Normal`, delegate to a `bachelierBlackFormula(...)` call; if `ShiftedLognormal`, the existing `blackFormula(...)`. Documented inline (P2F-5).
- `org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure` — add `volatilityType()` and `displacement()` accessors per v1.42.1. Default impls return `ShiftedLognormal` and `0.0` for back-compat with existing constant-vol structures.
- `CapHelper.blackPrice` — uses Phase 2d's `volatilityType_` field (already populated). The existing `case Normal: throw new UnsupportedOperationException(...)` (Phase 2d WI-1) flips to constructing a `BachelierCapFloorEngine` instead.

**New tests:**

- `AnalyticCapFloorEngineTest` — fingerprint at TIGHT tier against C++ probe using HW model.
- `BachelierCapFloorEngineTest` — fingerprint at TIGHT tier (Normal-vol closed form).
- Extend `CapHelperTest` with a `Normal`-vol case using BachelierCapFloorEngine.

### WI-2 — Swaption engines + G2.swaption (worktree B)

**New classes (in `org.jquantlib.pricingengines.swaption`):**

- `JamshidianSwaptionEngine` — port of v1.42.1 `jamshidianswaptionengine.{hpp,cpp}` (201 LOC). Constructor takes a `OneFactorAffineModel`. `calculate()` decomposes the swaption into a series of bond options using Jamshidian's trick (find the critical short-rate `r*` where the swap value equals zero via Brent solver, then sum `discountBondOption(...)` calls per fixed-leg payment date weighted by the coupon). Works only for affine 1-factor models (HW, Vasicek, CIR).

**Modified:**

- `BlackSwaptionEngine` — add Bachelier path. Same runtime-branch pattern as WI-1 BlackCapFloorEngine. The currently-`UnsupportedOperationException`-throwing Cash/ParYieldCurve settlement path stays a Phase 3+ seam.
- `org.jquantlib.termstructures.SwaptionVolatilityStructure` — add `volatilityType()` and `shift()` accessors per v1.42.1. Default impls return `ShiftedLognormal` and `0.0`.
- `SwaptionHelper.blackPrice` — choose Black or Bachelier engine based on `volatilityType_`.
- `G2.swaption(arguments, fixedRate, range, intervals)` — port the C++ implementation (g2.cpp lines 150-200) using `SegmentIntegral` and the inner `SwaptionPricingFunction` (Brent-based). Phase 2e A11 carve resolution. Three sub-tasks (P2F-6): (i) align `SegmentIntegral`'s Java function-object interface with C++'s `Real operator()(Real)` (likely needs a small `Ops.DoubleOp` wrapper); (ii) port `SwaptionPricingFunction` as a private inner class with a Brent solver (Java already has Brent); (iii) align `Swaption.ArgumentsImpl` field access for the integral parameters.
- `VanillaSwap.setupArguments` inverted `isAssignableFrom` + `List` capacity-vs-size fix — bundled into the JamshidianSwaptionEngine commit if Jamshidian's `setupArguments` chain surfaces it (Phase 2e WI-3 deliberately left this intact).

**New tests:**

- `JamshidianSwaptionEngineTest` — fingerprint at TIGHT tier against C++ probe using HW model.
- `BachelierBlackSwaptionEngineTest` — Normal-vol fingerprint at TIGHT tier.
- Extend `G2Test` with a `swaption(...)` integral fingerprint at LOOSE tier (Brent + quadrature noise floor).

### WI-3 — Heston BroadieKaya + NCCS tightening (worktree C)

**New classes (in `org.jquantlib.math.integrals`):**

- `GaussLobattoIntegral` — port of v1.42.1 `gausslobattointegral.{hpp,cpp}` (227 LOC). Adaptive Gauss-Lobatto-Kronrod quadrature with Richardson extrapolation. Implements `Integrator`.
- `GaussLaguerreIntegration` — port of v1.42.1 `gausslaguerreintegration.{hpp,cpp}` (~150 LOC). Fixed-order Gauss-Laguerre quadrature using pre-computed nodes/weights for `n=128` (matches C++ default). Implements `Integrator` (or sibling `Integrand` interface).

**New helpers (in `org.jquantlib.processes`):**

- Static helpers in `HestonProcess` package or a sibling `HestonHelpers` class — port of `cdf_nu_ds`, `Phi`, `ch`, `cornishFisherEps` from C++ hestonprocess.cpp lines 230-350. These build the Fourier-inversion machinery for the conditional CDF of integrated variance. Use `Complex` arithmetic — JQuantLib's pom.xml currently has no Complex dependency declared, so the implementer either (a) adds `commons-math3` as a Maven dependency (small pom edit; commons-math3 already cached in `~/.m2`) and uses `org.apache.commons.math3.complex.Complex`, OR (b) ports a minimal `Complex` class (~80 LOC) under `org.jquantlib.math`. Choice deferred to the implementer; if a third unanticipated path emerges, A14 fires.

**Modified:**

- `HestonProcess.Discretization` enum — add `BroadieKayaExactSchemeLobatto`, `BroadieKayaExactSchemeLaguerre`, `BroadieKayaExactSchemeTrapezoidal` (matches C++ enum order at the end).
- `HestonProcess.factors()` — extend to return `3` for the BroadieKaya schemes (vs `2` for the others) per C++ hestonprocess.cpp lines 60-65.
- `HestonProcess.evolve(...)` — add `case BroadieKayaExactSchemeLobatto`, `case BroadieKayaExactSchemeLaguerre`, `case BroadieKayaExactSchemeTrapezoidal` branches sharing one body that calls `cdf_nu_ds` with the appropriate integrator + Brent solver to invert (C++ hestonprocess.cpp lines 517-540).
- `org.jquantlib.math.distributions.NonCentralCumulativeChiSquaredDistribution` — **tighten to match C++ bit-faithfully**. Diagnose the Phase 2c WI-1 ~1.5e-12 drift via probe-driven comparison at multiple (df, ncp) tuples. Expected fix categories per §4 (a-d): Sankaran/Patnaik switching threshold; series convergence criterion; FMA accumulation pattern (Phase 2d HaltonRsg precedent — apply `Math.fma(...)` if needed); Bessel function approximation. Goal: every CDF call exact-tier match to C++.
- `HestonProcess.discountBondOption(...)` — un-stub at TIGHT tier (depends on the tightened NCCS landing first).

**New tests:**

- `GaussLobattoIntegralTest` — exact tier fingerprint of integral values against C++ probe for several test functions.
- `GaussLaguerreIntegrationTest` — same.
- `HestonProcessTest` extension: `testBroadieKayaLobattoEvolve`, `testBroadieKayaLaguerreEvolve`, `testBroadieKayaTrapezoidalEvolve` at LOOSE tier (Brent + quadrature noise floor). 5 tuples each (or 3 well-chosen tuples to keep test count manageable).
- `NonCentralCumulativeChiSquaredDistributionTest` retighten — promote any existing NCCS tests from loose to exact tier; add multi-(df, ncp) regression coverage.
- Extend `HestonProcessTest.testNCCV*` (Phase 2d) — promote loose tier `1e-8` to tight tier `1e-12` if NCCS tightening makes it possible.
- `HestonProcessDiscountBondOptionTest` — un-skip / add fingerprint at TIGHT tier.

---

## 3. Worktree Topology & Layer Ordering

### Worktree topology

3 git worktrees (proven shape from Phase 2c/2d/2e). All independent in the dep graph.

```
/Users/josemoya/eclipse-workspace/jquantlib-2f-A/  branch: phase-2f-A-cap-engines
/Users/josemoya/eclipse-workspace/jquantlib-2f-B/  branch: phase-2f-B-swaption-engines
/Users/josemoya/eclipse-workspace/jquantlib-2f-C/  branch: phase-2f-C-heston-bk
```

### Layer ordering

- **L0 — pre-flight + worktree setup.**
  - Confirm baseline `mvn -pl jquantlib test` → `Tests run: 656, Failures: 0, Errors: 0, Skipped: 22`.
  - Confirm scanner: `work_in_progress: 0` (no stub regressions).
  - Create 3 worktrees off `main` tip `a533fbd`.
  - Verify each builds clean.
  - Init `phase2f-progress.md`.

- **L1 — parallel launch of all 3 worktrees.**
  - **A (Cap engines):**
    - L1a: OVS displacement/volType API alignment if needed.
    - L1b: AnalyticCapFloorEngine + tests.
    - L1c: BachelierCapFloorEngine + tests.
    - L1d: BlackCapFloorEngine Bachelier branch + CapHelperTest Normal-vol case.
    - Land fast-forward to `main`.
  - **B (Swaption engines + G2.swaption):**
    - L1a: SwaptionVolatilityStructure displacement/shift API alignment if needed.
    - L1b: JamshidianSwaptionEngine + tests; bundle VanillaSwap.setupArguments fix if surfaced.
    - L1c: BlackSwaptionEngine Bachelier branch + tests.
    - L1d: G2.swaption integral path (SegmentIntegral function-object alignment + SwaptionPricingFunction Brent + Swaption.ArgumentsImpl alignment) + G2 swaption integral test at loose tier.
    - Land fast-forward to `main`.
  - **C (Heston BroadieKaya + NCCS tightening):**
    - L1a: NCCS tightening — diagnose ~1.5e-12 drift, fix Java NCCS to bit-faithfully match C++.
    - L1b: GaussLaguerreIntegration port + test.
    - L1c: GaussLobattoIntegral port + test.
    - L1d: Heston Fourier-inversion helpers (cdf_nu_ds + Phi + ch + cornishFisherEps) port.
    - L1e: 3 BroadieKaya enum values + evolve branches + factors() update + tests at loose tier.
    - L1f: HestonProcess.discountBondOption un-stub at tight tier (depends on NCCS tightening from L1a).
    - L1g: Promote NCCV tests from loose to tight tier (depends on NCCS tightening).
    - Land fast-forward to `main`.

- **L2 — completion doc + tag.**
  - Once all 3 worktrees have landed, write `docs/migration/phase2f-completion.md`.
  - Tag `jquantlib-phase2f-complete`, push.
  - Clean up worktrees + delete branches local + remote.
  - Update memory.

### Wallclock estimate

C is the long pole (~1500 LOC C++ to port across 6 sub-tasks). A and B are roughly comparable (~600-800 LOC each). Same orchestration pattern as Phase 2c/2d/2e.

### Controller orchestration rules (Phase 2c/2d/2e lessons baked in)

- Always run `git merge --ff-only origin/<branch>` from the **main checkout**, never inside a worktree's cwd.
- Between landings: each unmerged worktree rebases onto the new `main` before its next implementer dispatch. Force-push-with-lease after rebase.
- **Subagent watchdog stalls:** controller manually commits the in-progress state if it's clean and dispatches a focused continuation. Don't restart from scratch unless the worktree is inconsistent.
- **Orphan files** in main checkout from implementers using the main worktree's pre-warmed cpp/build (Phase 2d/2e precedent) — diff-then-rm before merge.
- Worktree cleanup: `git worktree remove --force <path>` + `git worktree prune` + `git branch -D` + `git push origin --delete`.

---

## 4. Tolerance, Probes & Test Discipline

### Tolerance tiers (inherits Phase 1 §4.2)

| Tier | Bound | Use |
|---|---|---|
| exact | bit-identical | Lobatto/Laguerre integrator nodes/weights, NCCS values (post-tightening), enum ordinals |
| tight | `abs 1e-14 + rel 1e-12` | analytic outputs (AnalyticCapFloor via discountBondOption, BachelierCapFloor closed-form, JamshidianSwaption via Brent + bond options, BlackSwaption Bachelier path, Heston discountBondOption post-NCCS-tightening, NCCV tests promoted from loose) |
| loose | `abs 1e-8 + rel 1e-8` | tree-fingerprint values, Brent + quadrature noise floor (G2.swaption integral, BroadieKaya schemes via cdf_nu_ds + Brent inversion) |

Per-test loose-tier exceptions must carry inline justification per design §4.2.

### Probes

| WI | Probe file | Captures |
|---|---|---|
| A | `analyticcapfloorengine_probe.cpp` | AnalyticCapFloorEngine.NPV() for a 5Y cap on HW(a=0.1, σ=0.01), eval=2026-01-15, FlatForward 5%, vol=0.20 |
| A | `bacheliercapfloorengine_probe.cpp` | BachelierCapFloorEngine.NPV() for the same fixture but normal-vol=0.01 (~1% absolute) |
| A | (extend `caphelper_probe.json` Phase 2d) | Add Normal-vol case via BachelierCapFloorEngine path |
| B | `jamshidianswaptionengine_probe.cpp` | JamshidianSwaptionEngine.NPV() for the 5Y×5Y ATM payer swaption fixture (mirror BlackSwaptionEngine probe) on HW(a=0.1, σ=0.01) |
| B | `bachelierswaptionengine_probe.cpp` (or extend `blackswaptionengine.json`) | Bachelier-path swaption pricing for the same fixture, normal-vol case |
| B | (extend `g2.json`) | G2.swaption(...) integral output for one well-posed fixture (5Y×5Y ATM payer on G2(0.1, 0.01, 0.1, 0.005, -0.5)) |
| C | `gauss_lobatto_integral_probe.cpp` | GaussLobattoIntegral output for several test functions (polynomials, transcendentals, oscillating) |
| C | `gauss_laguerre_integration_probe.cpp` | Same shape for GaussLaguerreIntegration |
| C | `nccs_distribution_probe.cpp` (extend Phase 2c WI-1's probe if it exists) | NCCS values at multiple (df, ncp, x) tuples spanning Sankaran/Patnaik regimes — exact-tier targets |
| C | `heston_broadiekaya_probe.cpp` | HestonProcess.evolve under each of the 3 BroadieKaya schemes for several fixtures |
| C | `heston_discountbondoption_probe.cpp` | HestonProcess.discountBondOption for several (strike, maturity, bondMaturity) tuples — tight-tier target |

### Probe-driven test discipline

No test passes a value the implementer made up. If a value isn't from a C++ probe or a closed-form derivation, the test doesn't go in.

### Loose-tier expectations baked in

- **WI-1 cap engines:** AnalyticCapFloorEngine and BachelierCapFloorEngine expected TIGHT (closed-form). BlackCapFloorEngine Bachelier branch TIGHT.
- **WI-2 swaption engines:** JamshidianSwaptionEngine TIGHT (Brent on a single critical-rate; Brent's tolerance 1e-7 → expected ~1e-12 in price). BlackSwaptionEngine Bachelier TIGHT. G2.swaption(...) integral LOOSE (Brent + SegmentIntegral noise floor).
- **WI-3 Heston:** GaussLobatto and GaussLaguerre integrator unit tests EXACT (deterministic node/weight arithmetic). NCCS tightened to EXACT (post-fix). HestonProcess.discountBondOption TIGHT (analytic via tightened NCCS). BroadieKaya scheme `evolve` LOOSE (Brent + adaptive quadrature noise floor). NCCV tier-promotion: target TIGHT but accept LOOSE if NCCS tightening doesn't fully eliminate the floor.

### Test count expectation

656 → ~672 (+16 net):
- WI-1: +3 (AnalyticCapFloor + BachelierCapFloor + CapHelper Normal-vol case)
- WI-2: +3 (JamshidianSwaption + BachelierSwaption + G2.swaption)
- WI-3: +10 (Lobatto + Laguerre + 3 BroadieKaya schemes (each with multi-tuple loops, ~5 tests collectively) + discountBondOption + NCCS regression coverage + NCCV tier-promotion confirmation)

Skipped: 22 → 22 (no un-skip work; if HestonProcess.discountBondOption was previously skipped via an `@Ignore`, un-skip count drops accordingly).

### Non-loosening rule

No tier loosening to force green. If a tight-expected test fails tight, root-cause first; loosen only with documented justification.

**Special focus for WI-3:** the NCCS tightening goal is EXACT match to C++. The expected diagnostic outcomes:
- (a) **Sankaran/Patnaik switching threshold differs** — Java picks the other branch in some regime. Fix: align threshold.
- (b) **Series convergence criterion differs** — Java terminates the Patnaik infinite series at a different `eps`. Fix: align stopping condition.
- (c) **FMA accumulation pattern** — Phase 2d HaltonRsg precedent. Java's `+=` produces 1-ULP drift vs C++'s hardware FMA. Fix: `Math.fma(...)`.
- (d) **Bessel function approximation differs** — Java's Bessel uses a different polynomial expansion than C++'s. Fix: align coefficients.

Report which class of fix was needed in the completion doc.

---

## 5. Pause Triggers

Inherits Phase 1 §7.3 with Phase 2c/2d/2e deltas. Phase 2f additions / changes:

| Trigger | Status | Condition |
|---|---|---|
| A1 | active | Scanner stub count > 1000 |
| A2 | active | Tolerance looser than `1e-8` ever needed |
| A3 | active | Cross-validation suggests v1.42.1 itself is wrong |
| A4 | **active, sharpened** | New class strictly required outside the 61 existing packages, OR substantively new infrastructure beyond what the design anticipated. For 2f: GaussLobatto + GaussLaguerre integrators (in scope, planned), Fourier-inversion harness (in scope, planned), Complex arithmetic (verify Java has `org.apache.commons.math3.complex.Complex`; if not, A14 fires). |
| A6 | **disabled** | End-of-layer pause — per memory `feedback_phase2a_no_a6.md` |
| A7 | active | Per-WI audit divergence from C++ |
| A8 | inactive | Vasicek-pattern ripple — N/A in 2f |
| A9 | active | Worktree-merge conflict requires manual resolution |
| A10 | inactive | XABR template-to-generics translation snag — N/A in 2f |
| A11 | inactive | G2 swaption integrator gap — being closed in WI-2 (was the trigger; now the fix) |
| A12 | inactive | Swaption.NPV() wiring — already resolved in Phase 2e C.0 |
| **A13 — new for 2f** | active | NCCS tightening discovers the drift root cause is structural (e.g. C++ uses Boost-specific algorithms not portable to Java) and exact-tier match is genuinely impossible. Pause to discuss tier compromise (e.g. `1e-15` vs strict bit-identical) before settling. |
| **A14 — new for 2f** | active | BroadieKaya `cdf_nu_ds` Fourier-inversion needs `Complex` arithmetic. Verified: JQuantLib's pom.xml has no Complex dependency declared. Implementer choice = (a) add `commons-math3` Maven dep (jar already in `~/.m2`) and use `org.apache.commons.math3.complex.Complex`, OR (b) port a minimal Complex class under `org.jquantlib.math`. Either path is acceptable; A14 fires only if a third unanticipated infrastructure gap surfaces. |

---

## 6. Decision Log (P2F-1 .. P2F-7)

- **P2F-1** Subset choice = B (surgical engines + BroadieKaya). FdHullWhite/FdG2 swaption engines, Gaussian1D family, additionalResults, addTimesTo Time-impedance, TreeLattice2D API formalization deferred to Phase 2g.
- **P2F-2** HestonProcess.discountBondOption included in WI-3 with goal: **tighten Java NCCS to bit-faithfully match C++** (user's explicit choice over the "accept loose tier" alternative). Bonus: promote Phase 2d WI-2 NCCV tests from loose to tight tier if NCCS tightening makes it possible.
- **P2F-3** TreeSwaptionEngine vs JamshidianSwaptionEngine in WI-2: BOTH already exist (TreeSwaption from Phase 2e WI-3); JamshidianSwaption added here. They serve different model classes (Jamshidian = affine 1-factor closed-form; Tree = generic lattice).
- **P2F-4** Worktree topology = 3 worktrees, parallel launch from L0. Same pattern as Phase 2c/2d/2e. Cleanup items absorbed into engine WIs naturally rather than a dedicated 4th worktree.
- **P2F-5** WI-1 Bachelier path in BlackCapFloorEngine via runtime branch on `volatilityType_` (option (a) from §2) — easier Java route than templated structure split.
- **P2F-6** WI-2 G2.swaption integral path = the Phase 2e A11 carve resolution. Three sub-tasks: SegmentIntegral function-object alignment, SwaptionPricingFunction Brent inner class, Swaption.ArgumentsImpl alignment.
- **P2F-7** Cleanup items (VanillaSwap.setupArguments fix, OVS/SwVS displacement+volType API, addTimesTo Time-impedance, additionalResults) bundled into the engine WIs ONLY if surfaced naturally by the WI's port work. Items not surfaced carry to Phase 2g.

---

## 7. Exit Criteria

All must hold to tag `jquantlib-phase2f-complete`:

1. `mvn -pl jquantlib test` green: `Failures: 0, Errors: 0`.
2. Test count delta: `656 → ~672` (± a small adjustment), with full breakdown in completion doc.
3. `Skipped: 22` (unchanged; if HestonProcess.discountBondOption was previously skipped, un-skip count drops accordingly).
4. Scanner: `work_in_progress: 0` (no regressions; Phase 2e milestone preserved).
5. All 3 worktrees merged fast-forward to `main` and removed; no orphan branches.
6. Every probe in `migration-harness/cpp/probes/` regenerates cleanly via `generate-references.sh`.
7. Per-test loose-tier exceptions all carry inline justification.
8. **NCCS tightening completion-doc disclosure:** which of fix categories (a)/(b)/(c)/(d) from §4 was the root cause; whether NCCV tier-promotion succeeded.
9. Phase 2f completion doc written (`docs/migration/phase2f-completion.md`) covering: per-WI summary with commit hashes, probe inventory, deviations from plan, A13/A14 firings if any, Phase 2g seed list.
10. Tag `jquantlib-phase2f-complete` pushed; memory `project_jquantlib_migration.md` updated.

---

## 8. Phase 2g Seed List (carry forward)

- FdHullWhiteSwaptionEngine + FdG2SwaptionEngine — finite-difference swaption engines.
- Gaussian1D swaption engine family + underlying Gaussian1D model.
- additionalResults in cap/swaption engines (vega, optionletsPrice, optionletsVega).
- SwaptionHelper.addTimesTo / CapHelper.addTimesTo `Time` annotation impedance.
- TreeLattice2D underlying value access API formalization.
- HaltonRsg FMA platform-conditionality documentation.
- Per-test 5e-8 SABR cross-check tolerance investigation.
- BlackSwaptionEngine Cash/ParYieldCurve settlement path (needs CashFlows.bps(InterestRate) + Schedule.tenor()).
- Any cleanup items not surfaced by Phase 2f WIs (P2F-7).
- Phase 3+ gap-fill packages (`experimental/`, `models/marketmodels/`, `termstructures/credit/`, `inflation/`).
