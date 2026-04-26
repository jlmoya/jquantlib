# Phase 2d Design — CapHelper, Heston NCCV, SABR Halton via XABR scaffold

**Status:** approved 2026-04-26.
**Predecessor:** Phase 2c — `jquantlib-phase2c-complete` @ `4cbabec`.
**Inherits unchanged:** Phase 1 design §1-12, Phase 2a §7 (P2A-1..P2A-8), Phase 2b §5 (P2B-1..P2B-7), Phase 2c §5 (P2C-1..P2C-6).
**Source-of-truth pin:** QuantLib v1.42.1 @ `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`.

---

## 1. Goals & Non-Goals

### Goal

Knock out one of the two remaining `work_in_progress` scanner items (CapHelper), un-skip the 2 `@Ignore`'d SABR calibration tests, and add the `NonCentralChiSquareVariance` Heston discretization scheme using Phase 2c WI-1's chi-squared CDF.

### In scope (3 work items)

- **WI-1 — CapHelper unstub.** Port `BlackCalibrationHelper` (intermediate base, with `VolatilityType` enum and `CalibrationErrorType`) and `VolatilityType` enum (`ShiftedLognormal`, `Normal`); refactor `CapHelper` to extend it; port `CapHelper.performCalculations` body from C++ v1.42.1.
- **WI-2 — Heston `NonCentralChiSquareVariance` scheme.** Add the enum value; add the `evolve` branch using Phase 2c WI-1's `NonCentralCumulativeChiSquaredDistribution` + `InverseNonCentralCumulativeChiSquaredDistribution` per C++ hestonprocess.cpp lines 290-330.
- **WI-3 — SABR Halton multi-restart via XABR scaffold.** Port `HaltonRsg`; port `XABRInterpolation` + `XABRInterpolationImpl<Model>` + `XABRCoeffHolder<Model>`; refactor `SABRInterpolation` to extend; fix the inverted null-checks at lines 248-255; un-skip both `@Ignore`'d SABR calibration tests with C++ probe references.

### Out of scope (explicitly deferred)

- BroadieKaya×3 Heston schemes (Lobatto + Laguerre integrator ports + Fourier-inversion harness) — Phase 2e or later.
- SwaptionHelper unstub — its own port body; defer.
- G2 / TreeLattice2D — Phase 2e.
- HestonProcess `discountBondOption` (still chi-squared drift question from Phase 2c WI-1).
- Phase 3+ gap-fill packages (`experimental/`, `models/marketmodels/`, `termstructures/credit/`, `inflation/`).

### Non-goals

- No refactoring of unrelated code.
- No API improvements beyond what v1.42.1 dictates.
- No tier loosening to force green.

---

## 2. Architecture & Components

### WI-1 — CapHelper

**New classes:**

- `org.jquantlib.model.VolatilityType` (enum) — `ShiftedLognormal`, `Normal`. Mirrors `ql/termstructures/volatility/volatilitytype.hpp`.
- `org.jquantlib.model.BlackCalibrationHelper` — abstract. Extends `LazyObject` (Java already has it) and the existing `CalibrationHelper` interface contract. Holds `volatility_`, `volatilityType_`, `shift_`, `calibrationErrorType_`, `engine_`, `marketValue_`. Provides:
  - `enum CalibrationErrorType { RelativePriceError, PriceError, ImpliedVolError }`
  - `calibrationError()` — concrete
  - `impliedVolatility(targetValue, accuracy, maxEvaluations, minVol, maxVol)` — concrete
  - `marketValue()` — concrete (calls `calculate()`)
  - `setPricingEngine(engine)` — concrete
  - abstract `modelValue()`, `blackPrice(volatility)`, `addTimesTo(times)`
  - `performCalculations()` override — stores `marketValue_ = blackPrice(volatility_->value())`

**Modified:**

- `org.jquantlib.model.shortrate.calibrationhelpers.CapHelper` — `extends BlackCalibrationHelper`. New ctor signature mirrors v1.42.1 caphelper.hpp lines 33-45 (with `errorType=RelativePriceError`, `type=ShiftedLognormal`, `shift=0.0` defaults via overloaded ctors). `performCalculations()` body ports caphelper.cpp lines 91-144 using existing Java `IborLeg`, `FixedRateLeg`, `Cap` (in `instruments.CapFloor`), `Swap`, `BlackCapFloorEngine`, `DiscountingSwapEngine`. `addTimesTo`, `blackPrice`, `modelValue` ported matching caphelper.cpp lines 51-89.

**Untouched:** `SwaptionHelper` stays empty-stub (out of scope per P2D-3).

**Tests:**

- New `org.jquantlib.testsuite.model.shortrate.calibrationhelpers.CapHelperTest` exercising the C++-equivalent calibration path.

### WI-2 — Heston NonCentralChiSquareVariance

**Modified:**

- `org.jquantlib.processes.HestonProcess.Discretization` enum — add `NonCentralChiSquareVariance` between `Reflection` and `QuadraticExponential` (matches C++ enum order in hestonprocess.hpp lines 48-56).
- `HestonProcess.evolve(...)` — add `case NonCentralChiSquareVariance` branch following hestonprocess.cpp lines 290-330. Closed form:
  - `df = 4.0 * kappa * theta / (sigma*sigma)`
  - `ncp = 4.0 * kappa * exp(-kappa*dt) / (sigma*sigma * (1.0 - exp(-kappa*dt))) * v0`
  - `inv = InverseNonCentralCumulativeChiSquaredDistribution(df, ncp).evaluate(Φ(dw[1]))`
  - `v_new = sigma*sigma * (1.0 - exp(-kappa*dt)) / (4.0 * kappa) * inv`
  - Asset-leg evolution per C++ lines 320-330.
- `HestonProcess.apply(...)` — extend the switch if needed for the new scheme.

**Tests:**

- `HestonProcessTest` — extend with one-step trajectory probe at the new scheme. Loose tier expected (inverse-CDF Brent noise floor, same precedent as Phase 2c WI-1 CIR).

### WI-3 — SABR Halton via XABR scaffold

**New classes (in `org.jquantlib.math.randomnumbers`):**

- `HaltonRsg` — direct port of `haltonrsg.hpp/cpp` v1.42.1. Reverse-radix base-prime sequences, dimensional shuffling. Implements `RandomSequenceGeneratorIntf` (Java already has the interface).

**New classes (in `org.jquantlib.math.interpolations`):**

- `XABRSpecs` (interface) — Java representative of C++'s `template<class Model>` parameter. Methods: `int dimension()` (parameter count), `Array defaultValues(double forward, double t)` (initial guess), `void guess(Array values, BooleanArray isFixed, HaltonRsg.Sample sample, double forward, double t)` (per-restart Halton-driven guess synthesis), `Array inverse(Array y, BooleanArray isFixed, Array params, double forward)` and `Array direct(Array x, BooleanArray isFixed, Array params, double forward)` (parameter transformation), `double volatility(double strike, double forward, Array params)`, `Array equalConstraints(double forward)` (constraint bounds).
- `XABRCoeffHolder<S extends XABRSpecs>` — abstract params holder mirroring C++'s `XABRCoeffHolder<Model>` template. Holds `t_`, `forward_`, `params_`, `paramIsFixed_`, `weights_`, `error_`, `maxError_`, `XABREndCriteria_`, plus the `S specs_` instance.
- `XABRInterpolationImpl<S extends XABRSpecs>` — generic impl with `calculate()` containing the Halton restart loop (`maxGuesses_`, `errorAccept_`, `useMaxError_`, `addParams_` semantics from xabrinterpolation.hpp lines 122, 184, 218, 227-228, 315-317).
- `XABRInterpolation` — outer wrapper holding the impl pointer.

**Modified:**

- `org.jquantlib.math.interpolations.SABRInterpolation` — add new `SABRSpecs` (implements `XABRSpecs`) inner type defining `dimension() = 4`, `defaultValues(...)` returning Phase 2c WI-2's α formula plus β/ν/ρ defaults, transformation via existing `SabrParametersTransformation`, `volatility(strike, forward, params)` via existing `SabrFormula`. Refactor inner `SABRInterpolationImpl` to extend `XABRInterpolationImpl<SABRSpecs>`. Per-restart guess logic moves into `SABRSpecs.guess(...)`.
- Fix inverted null-checks at SABRInterpolation lines 248-255 (Phase 2c WI-3 seed): change
  ```java
  if (optMethod_ != null) { optMethod_ = new Simplex(0.01); }
  if (endCriteria_ != null) { endCriteria_ = new EndCriteria(...); }
  ```
  to the C++ semantic — assign default only when caller-supplied is `null`.
- Un-`@Ignore` the 2 SABR calibration tests (`SABRInterpolationTest::testSabrInterpolation` and `InterpolationTest::testSabrInterpolation`).

**Tests:**

- `HaltonRsgTest` — first-N values cross-validated against C++ probe, exact tier.
- `XABRInterpolationImplTest` — unit-test the restart loop with deterministic single-iter and multi-iter convergence cases, both probe-driven.
- `SABRInterpolationCalibrationTest` (new) — probe C++ `SABRInterpolation::update()` end-to-end on a fresh fixture; cross-validate Java at tight tier on calibrated params, loose tier on fitted vols (LM + Halton restart noise expected).
- The 2 un-skipped tests get probe references too (replace any hardcoded original-Java values).

---

## 3. Worktree Topology & Layer Ordering

### Worktree topology

3 git worktrees, one per WI. All independent in the dep graph (no shared files, no shared upstream stubs). Same orchestration model as Phase 2c with main-checkout-merge orchestration baked in.

```
/Users/josemoya/eclipse-workspace/jquantlib-2d-A/  branch: phase-2d-A-caphelper
/Users/josemoya/eclipse-workspace/jquantlib-2d-B/  branch: phase-2d-B-nccv
/Users/josemoya/eclipse-workspace/jquantlib-2d-C/  branch: phase-2d-C-sabr-xabr
```

### Layer ordering

- **L0 — pre-flight + worktree setup.**
  - Confirm baseline `mvn -pl jquantlib test` → `Tests run: 640, Failures: 0, Errors: 0, Skipped: 24`.
  - Confirm scanner: `work_in_progress: 2` (CapHelper, G2).
  - Create 3 worktrees off `main` tip `4cbabec`.
  - Verify each builds clean (`mvn -pl jquantlib test-compile`).

- **L1 — parallel launch of all 3 worktrees.** Each implementer subagent runs independently on its worktree:
  - **A (CapHelper):**
    - L1a: port `VolatilityType` enum.
    - L1b: port `BlackCalibrationHelper` + tests.
    - L1c: refactor `CapHelper` to extend `BlackCalibrationHelper`; port `performCalculations` body.
    - L1d: probe + `CapHelperTest`.
    - Land fast-forward to `main`.
  - **B (NCCV):**
    - L1a: add `NonCentralChiSquareVariance` enum value.
    - L1b: add `evolve` branch + `apply` extension.
    - L1c: probe + `HestonProcessTest` extension.
    - Land fast-forward to `main`.
  - **C (SABR/XABR):**
    - L1a: port `HaltonRsg` + `HaltonRsgTest`.
    - L1b: port `XABRCoeffHolder` + `XABRInterpolationImpl` + `XABRInterpolation` + `XABRInterpolationImplTest`.
    - L1c: refactor `SABRInterpolation` inner impl to extend `XABRInterpolationImpl<SABRSpecs>`.
    - L1d: fix inverted null-checks at lines 248-255.
    - L1e: un-skip the 2 SABR calibration tests with probe refs; add `SABRInterpolationCalibrationTest`.
    - Land fast-forward to `main`.

- **L2 — completion doc + tag.**
  - Once all 3 worktrees have landed, write `docs/migration/phase2d-completion.md`.
  - Tag `jquantlib-phase2d-complete`, push.
  - Clean up worktrees (`git worktree remove --force` + `git worktree prune` + delete branches local + remote).

### Controller orchestration rules (Phase 2c lessons baked in)

- Always run `git merge --ff-only origin/<branch>` from the **main checkout**, never from inside a worktree's cwd. Verify with `git -C /Users/josemoya/eclipse-workspace/jquantlib log --oneline -3` after each merge.
- Between landings: each unmerged worktree rebases onto the new `main` before its next implementer dispatch.
- After a worktree lands, the next implementer in *another* worktree gets told to re-pull `main` and rebase.
- If a rebase conflicts, A9 fires (the only deliberate ask in normal operation).
- Worktree cleanup uses `git worktree remove --force <path>` followed by `rm -rf` if needed, then `git worktree prune`.

### Wallclock estimate

B is smallest and likely lands first. A and C are roughly comparable in size. The long pole is C (most files touched, most tests added). Subagent-driven flow per Phase 2c precedent: implementer → spec reviewer → code-quality reviewer per task.

---

## 4. Tolerance, Probes & Test Discipline

### Tolerance tiers (inherits Phase 1 §4.2)

| Tier | Bound | Use |
|---|---|---|
| exact | bit-identical | Halton sequence values, enum ordinals, integer-quantized outputs |
| tight | `abs 1e-14 + rel 1e-12` | analytic outputs (CapHelper Black price, calibrated SABR params, NCCV variance for low-σ regime) |
| loose | `abs 1e-8 + rel 1e-8` | inverse-CDF Brent noise, multi-iteration LM convergence noise, Halton-driven multi-restart calibration error |

Per-test loose-tier exceptions must carry inline justification per design §4.2 (precedent: Phase 2c WI-1 CIR / WI-4 HW / WI-5 BK).

### Probe contract

One C++ probe per cross-validated stub, in `migration-harness/cpp/probes/`. Each writes a JSON reference into `migration-harness/data/`. Java tests load via `ReferenceReader.load(path)`. Probes regenerate-able via `migration-harness/scripts/generate-references.sh`.

### Probes for Phase 2d

| WI | Probe file | Captures |
|---|---|---|
| A | `caphelper_probe.cpp` | `CapHelper.modelValue()` and `blackPrice(0.20)` for a fixed flat-curve setup (Actual365Fixed, dummy 3M Ibor index, 5Y cap, vol=0.20, errorType=RelativePriceError) |
| B | `heston_nccv_probe.cpp` | `HestonProcess.evolve(t=0, x0=[s0,v0], dt, dw)` with Discretization=NCCV, for 5 (v0, dw[1]) tuples — 4 spanning ncp regimes (low/mid/high) plus 1 high-ncp `(12.0, 2000.0, 800.0)`-style tuple closing the Phase 2c WI-1 coverage gap (P2D-6) |
| C | `halton_rsg_probe.cpp` | First 100 vectors of `HaltonRsg(dim=4, seed=42)` |
| C | `sabr_calibration_probe.cpp` | C++ `SABRInterpolation::update()` end-to-end on the 2 currently-`@Ignore`'d test fixtures: calibrated (α, β, ν, ρ), final error, end criteria, fitted vols at all strikes |
| C | `xabr_restart_loop_probe.cpp` | A constructed deterministic single-iter case + a multi-iter convergence case for the restart loop's invariants (best-error tracking, return-on-accept) |

### Probe-driven test discipline

No test passes a value the implementer made up. If a value isn't from a C++ probe or a closed-form derivation, the test doesn't go in.

### Loose-tier expectations baked in

- A's `CapHelper.modelValue()` likely tight; `blackPrice` likely tight (closed-form Black-76 cap).
- B's NCCV one-step `evolve` is **expected loose** at the `1e-8` floor — same Brent-driven inverse-CDF noise pattern as Phase 2c WI-1 CIR option.
- C's HaltonRsg and XABR restart-loop unit cases are **expected exact / tight**.
- C's 2 un-skipped SABR calibration tests are **expected loose** on fitted vols (LM + Halton restart convergence sensitivity to JVM-vs-C++ FP rounding); calibrated params should hit tight.

### Test count expectation

640 → ~648 (+1 CapHelper + ~2 NCCV + ~3 Halton/XABR/SABRCalibration + 2 un-skipped). Skipped: 24 → 22 (the 2 SABR re-enabled).

### Non-loosening rule

No tier loosening to force green. If a tight-expected test fails tight, root-cause first; loosen only with documented justification, never silently.

---

## 5. Pause Triggers

Inherits Phase 1 §7.3 with Phase 2c §5 deltas. Phase 2d additions / changes:

| Trigger | Status | Condition |
|---|---|---|
| A1 | active | Scanner stub count > 1000 |
| A2 | active | Tolerance looser than `1e-8` ever needed |
| A3 | active | Cross-validation suggests v1.42.1 itself is wrong |
| A4 | **active, sharpened** | New class strictly required outside the 61 existing packages, OR a port surfaces a need for substantively new infrastructure (e.g. a quadrature class beyond the `TrapezoidIntegral` family). For Phase 2d this is the BroadieKaya carve gate — if WI-2 NCCV implementation discovers it actually needs Lobatto/Laguerre helpers, it pauses and the work item carves to Phase 2e. |
| A6 | **disabled** | End-of-layer pause — per memory `feedback_phase2a_no_a6.md`, run end-to-end without ack |
| A7 | active | Per-WI audit divergence from C++ |
| A8 | inactive | Vasicek-pattern ripple — N/A in 2d (no one-factor model fan-out) |
| A9 | active | Worktree-merge conflict requires manual resolution |
| **A10 — new for 2d** | active | Inside WI-3, if the XABR-template-to-Java-generics translation surfaces a non-mechanical type-system mismatch (e.g. C++ partial specialization without an obvious Java equivalent), pause to discuss before improvising |

---

## 6. Decision Log (P2D-1 .. P2D-6)

- **P2D-1** Subset choice = B (CapHelper + NCCV + SABR Halton). BroadieKaya×3 deferred to a later phase.
- **P2D-2** SABR shape = α (faithful XABR port — `XABRInterpolation` + `XABRInterpolationImpl<Model>` + `XABRCoeffHolder<Model>`, refactor `SABRInterpolation` to extend).
- **P2D-3** SwaptionHelper untouched. Stays empty-stub; not on Phase 2d agenda. Phase 2e or later candidate.
- **P2D-4** Worktree topology = 3 worktrees, parallel launch from L0. Same model as Phase 2c with main-checkout-merge orchestration rule.
- **P2D-5** XABR generic-on-Model — Java represents C++'s `template<class Model>` via a `Model` interface (parameter count, default values, constraints, transformation, `volatility(strike)`). Avoids Java's lack of template specialization; matches the "Specs" type pattern C++ uses inside the template.
- **P2D-6** Phase 2c WI-1 chi-squared probe coverage gap (the missing `(12.0, 2000.0, 800.0)` tuple) gets folded into WI-2's NCCV probe — adding the high-ncp tuple costs nothing extra and de-risks NCCV for Ding-region inputs.

---

## 7. Exit Criteria

All must hold to tag `jquantlib-phase2d-complete`:

1. `mvn -pl jquantlib test` green: `Failures: 0, Errors: 0`.
2. Test count delta: `640 → ~648` (± a small adjustment), with full breakdown in the completion doc.
3. `Skipped: 22` (the 2 SABR calibration tests un-skipped; WI-1 chi-squared tuple gap closed).
4. Scanner: `work_in_progress: 1` (only G2 remaining; CapHelper closed).
5. All 3 worktrees merged fast-forward to `main` and removed; no orphan branches.
6. Every probe in `migration-harness/cpp/probes/` regenerates cleanly via `generate-references.sh`.
7. Per-test loose-tier exceptions all carry inline justification.
8. Phase 2d completion doc written (`docs/migration/phase2d-completion.md`) covering: per-WI summary with commit hashes, probe inventory, deviations from plan, Phase 2e seed list (G2/TreeLattice2D + carved BroadieKaya×3 + carved SwaptionHelper).
9. Tag `jquantlib-phase2d-complete` pushed; memory `project_jquantlib_migration.md` updated.

---

## 8. Phase 2e Seed List (carry forward)

- G2 / TreeLattice2D (already planned).
- BroadieKaya×3 + Gauss-Lobatto + Gauss-Laguerre integrators (carved here per P2D-1).
- SwaptionHelper unstub (carved here per P2D-3).
- HestonProcess `discountBondOption` if the chi-squared drift remaining from Phase 2c WI-1 is acceptable for the option-formula accuracy budget.
- HullWhite/BK Brent tolerance tightening (Phase 2c WI-5 seed, low-priority).
- `CIR.discountBondOption` per-test 1e-13 tolerance — could be tightened back to `Tolerance.tight` if the chained arithmetic could be reformulated to avoid 3.5e-14 ULP drift (Phase 2c WI-1 seed, low-priority).
