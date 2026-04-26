# Phase 2e Design — G2, BlackCapFloorEngine retrofit, Swaption infrastructure + SwaptionHelper

**Status:** approved 2026-04-26.
**Predecessor:** Phase 2d — `jquantlib-phase2d-complete` @ `06450e6`.
**Inherits unchanged:** Phase 1 design §1-12, Phase 2a §7 (P2A-1..P2A-8), Phase 2b §5 (P2B-1..P2B-7), Phase 2c §5 (P2C-1..P2C-6), Phase 2d §5 (P2D-1..P2D-6).
**Source-of-truth pin:** QuantLib v1.42.1 @ `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`.

---

## 1. Goals & Non-Goals

### Goal

Close the last `work_in_progress` scanner item (G2; scanner 1 → 0 — symbolic completion of Phase 1's "finish all stubs" mandate), retroactively complete Phase 2d WI-1's CapHelper vision (port `BlackCapFloorEngine` + wire `CapFloor.NPV()` so `modelValue`/`blackPrice` produce real values), and complete Phase 2d WI-1's symmetric SwaptionHelper deferral (port `BlackSwaptionEngine` + `TreeSwaptionEngine` + `DiscretizedSwaption` + `SwaptionHelper` full body).

### In scope (3 work items)

- **WI-1 — G2 model body port.** Port C++ `g2.cpp` (248 LOC) using existing Java `TreeLattice2D` and `TwoFactorModel` infrastructure (both already present, 0 stubs each). Add tree-fingerprint test (loose tier).
- **WI-2 — BlackCapFloorEngine + CapFloor.NPV() wiring + CapHelper retrofit.** Port C++ `blackcapfloorengine.{hpp,cpp}` (Black76 closed-form per optionlet, mechanical given Java's `BlackFormula`); wire `CapFloor.NPV()` to dispatch through `engine_.calculate()`. Retrofit Phase 2d's `CapHelperTest` to assert `modelValue()` and `blackPrice(0.20)` at tight tier against the existing C++ probe (which already captures these values).
- **WI-3 — Swaption pricing infrastructure + SwaptionHelper full body.** Port `BlackSwaptionEngine` (412 LOC, Black76 swaption closed-form), `TreeSwaptionEngine` (170 LOC, generic lattice-based), `DiscretizedSwaption` (183 LOC, helper required by TreeSwaptionEngine), and `SwaptionHelper` full body (replaces Phase 2d compile-only stub). Both `modelValue()` and `blackPrice(volatility)` work end-to-end.

### Out of scope (explicitly deferred)

- BroadieKaya×3 Heston schemes (Lobatto + Laguerre integrators + Fourier-inversion harness) — Phase 2f.
- AnalyticCapFloorEngine — second cap engine; redundant with Black engine for the WI-1 retrofit goal.
- JamshidianSwaptionEngine — affine 1-factor closed-form (TreeSwaptionEngine covers HW + G2 + BK in one engine).
- FdHullWhiteSwaptionEngine, FdG2SwaptionEngine, Gaussian1D swaption variants.
- HestonProcess `discountBondOption` — still needs the Phase 2c WI-1 chi-squared drift acceptability investigation first.
- BachelierCapFloorEngine — currently throws `UnsupportedOperationException` from Phase 2d WI-1 CapHelper.blackPrice when `VolatilityType.Normal` is used.
- Phase 3+ gap-fill packages (`experimental/`, `models/marketmodels/`, `termstructures/credit/`, `inflation/`).

### Non-goals

- No refactoring of unrelated code.
- No API improvements beyond what v1.42.1 dictates.
- No tier loosening to force green.

---

## 2. Architecture & Components

### WI-1 — G2 model body port

**Modified:**

- `org.jquantlib.model.shortrate.twofactormodels.G2` — replace stub body with port of v1.42.1 `g2.cpp` lines 33-248. Components:
  - Constructor with `(termStructure, a, sigma, b, eta, rho)` parameters; registers with termStructure; stores `Parameter` indirection for each (a, σ, b, η, ρ) — same pattern as Phase 2b's one-factor models.
  - Inner `Dynamics extends TwoFactorModel.ShortRateDynamics` class with `Process` instances for x and y processes (`OrnsteinUhlenbeckProcess` × 2; correlation captured separately).
  - `discount(t)` — affine bond price `A(t) * exp(-B(a, t) * x - B(b, t) * y)` per g2.hpp formula.
  - `discountBond(now, maturity, factors)` — same with caller-provided x, y.
  - `swaption(arguments, range, intervals)` — quadrature-based swaption pricing per g2.cpp lines 130-200. Uses `SegmentIntegral` (or `KronrodIntegral`) — both exist in Java.
  - Tree generation overrides: `tree(grid)` returns a `TreeLattice2D` built from two `TrinomialTree` instances (one per factor) plus the (a, σ, b, η, ρ) correlation. Same shape as Phase 2c WI-4/WI-5's `tree(grid)` overrides for HW/BK.
  - `generateArguments()` standard pattern.

**New tests:**

- `org.jquantlib.testsuite.model.shortrate.twofactormodels.G2Test` — analytic discount fingerprint at tight tier + tree-fingerprint at loose tier (Brent-noise floor precedent from Phase 2c WI-5 BK tree). Optional `swaption(...)` quadrature fingerprint test if the integrator behavior cleanly matches C++.

**No new infrastructure** — `TreeLattice2D` and `TwoFactorModel` already exist in Java with 0 stubs; `TrinomialTree`, `SegmentIntegral`, `KronrodIntegral`, `OrnsteinUhlenbeckProcess` all present.

### WI-2 — BlackCapFloorEngine + CapFloor.NPV() wiring + CapHelper retrofit

**Modified:**

- `org.jquantlib.pricingengines.capfloor.BlackCapFloorEngine` — uncomment + port from C++ `blackcapfloorengine.{hpp,cpp}`. Three constructors as in C++ (`(yts, vol, dc)`, `(yts, vol-handle, dc)`, `(yts, optionletVolStruct)`). `calculate()` body iterates over the cap's optionlets, computes a Black76 price per optionlet using `BlackFormula.blackFormula(...)`, sums into `value`, accumulates `vega`. Implements `CapFloor.Engine` interface.
- `org.jquantlib.instruments.CapFloor` — wire `NPV()` to dispatch through `engine_.calculate()` and read `results_.value`. Standard Instrument calculate-and-pull pattern.
- `org.jquantlib.testsuite.model.shortrate.calibrationhelpers.CapHelperTest` — extend to also assert `modelValue()` and `blackPrice(0.20)` at tight tier against the existing `caphelper_probe.json` (Phase 2d already captured these values; the test currently asserts only `fairRate`).

**Tests:** existing CapHelperTest extended with 2 new assertions (modelValue + blackPrice at tight tier).

### WI-3 — Swaption pricing infrastructure + SwaptionHelper full body

**New classes (in `org.jquantlib.pricingengines.swaption`, new package directory):**

- `BlackSwaptionEngine` — port of v1.42.1 `blackswaptionengine.{hpp,cpp}` (412 LOC). Black76 closed-form swaption pricing using `BlackFormula.blackFormula(...)` on the swaption's underlying par swap rate and forward annuity. Three constructor variants (volatility, vol-handle, vol-structure).
- `DiscretizedSwaption` — port of v1.42.1 `discretizedswaption.{hpp,cpp}` (183 LOC). Helper for tree-based swaption valuation; extends `DiscretizedOption` (Java already has `DiscretizedOption`, `DiscretizedAsset`, `DiscretizedDiscountBond` in `org.jquantlib.instruments`). Place in `org.jquantlib.pricingengines.swaption` alongside `TreeSwaptionEngine` (matches C++ layout).
- `TreeSwaptionEngine` — port of v1.42.1 `treeswaptionengine.{hpp,cpp}` (170 LOC). Generic lattice-based swaption pricing: takes a `ShortRateModel` + timeGrid, builds the model's tree, evaluates the discretized swaption on the lattice. Works with any tree-based short-rate model — HW, BK, G2 (via `TreeLattice2D`).

**Modified:**

- `org.jquantlib.model.shortrate.calibrationhelpers.SwaptionHelper` — full body port from C++ `swaptionhelper.cpp`. Replaces the Phase 2d compile-only stub:
  - Constructor: ports both v1.42.1 ctor variants (period-based + start-date-based). Builds the underlying `VanillaSwap` and `Swaption` instruments.
  - `performCalculations()` — sets the underlying VanillaSwap on a `DiscountingSwapEngine`, computes ATM `fairRate`, builds the `Swaption` from `VanillaSwap` + `Exercise`.
  - `addTimesTo(times)` — collect mandatory times from the `DiscretizedSwaption` arguments.
  - `modelValue()` — sets `engine_` on `swaption_`, returns `swaption_.NPV()`.
  - `blackPrice(volatility)` — temporary `BlackSwaptionEngine` with the supplied volatility, returns `swaption_.NPV()`.
- `org.jquantlib.instruments.Swaption` — likely needs the `NPV()` wiring too (same pattern as `CapFloor` in WI-2). Check actual state; minimal touch.

**New tests:**

- `BlackSwaptionEngineTest` — Black76 swaption pricing fingerprint at tight tier.
- `TreeSwaptionEngineTest` — tree-pricing fingerprint at loose tier (Brent noise floor, BK tree precedent).
- `SwaptionHelperTest` — `modelValue` + `blackPrice` cross-validated against a C++ probe at tight tier (modelValue may go loose if model is tree-based).

---

## 3. Worktree Topology & Layer Ordering

### Worktree topology

3 git worktrees (same shape as Phase 2d — proven). All independent in the dep graph: G2 lives in `model.shortrate.twofactormodels`, BlackCapFloorEngine + CapFloor in `pricingengines.capfloor` + `instruments`, swaption infra in a new `pricingengines.swaption` package + `model.shortrate.calibrationhelpers`. No file overlap.

```
/Users/josemoya/eclipse-workspace/jquantlib-2e-A/  branch: phase-2e-A-g2
/Users/josemoya/eclipse-workspace/jquantlib-2e-B/  branch: phase-2e-B-cap-engine
/Users/josemoya/eclipse-workspace/jquantlib-2e-C/  branch: phase-2e-C-swaption
```

### Layer ordering

- **L0 — pre-flight + worktree setup.**
  - Confirm baseline `mvn -pl jquantlib test` → `Tests run: 649, Failures: 0, Errors: 0, Skipped: 22`.
  - Confirm scanner: `work_in_progress: 1` (G2).
  - Create 3 worktrees off `main` tip `06450e6`.
  - Verify each builds clean (`mvn -pl jquantlib test-compile`).

- **L1 — parallel launch of all 3 worktrees.** Each implementer subagent runs independently:
  - **A (G2):**
    - L1a: port G2 ctor + Parameter indirection + Dynamics inner class.
    - L1b: port `discount(t)` + `discountBond(...)` + `tree(grid)`.
    - L1c: probe + tree fingerprint test.
    - L1d: optionally `swaption(...)` quadrature path.
    - Land fast-forward to `main`.
  - **B (Cap engine):**
    - L1a: port `BlackCapFloorEngine` body.
    - L1b: wire `CapFloor.NPV()`.
    - L1c: extend `CapHelperTest` with `modelValue` + `blackPrice` assertions at tight tier (probe already exists from Phase 2d).
    - Land fast-forward to `main`.
  - **C (Swaption):**
    - L1a: port `BlackSwaptionEngine` + `BlackSwaptionEngineTest`.
    - L1b: port `DiscretizedSwaption` helper.
    - L1c: port `TreeSwaptionEngine` + `TreeSwaptionEngineTest`.
    - L1d: port `SwaptionHelper` full body + `swaptionhelper_probe` + `SwaptionHelperTest`.
    - Land fast-forward to `main`.

- **L2 — completion doc + tag.**
  - Once all 3 worktrees have landed, write `docs/migration/phase2e-completion.md`.
  - Tag `jquantlib-phase2e-complete`, push.
  - Clean up worktrees (`git worktree remove --force` + `git worktree prune` + delete branches local + remote).

### Wallclock estimate

C is the long pole (~800 LOC C++ to port: 412 BlackSwaption + 170 TreeSwaption + 183 DiscretizedSwaption + SwaptionHelper body). A is medium (G2 body ~250 LOC + tree tests). B is smallest (BlackCapFloorEngine body is mostly mechanical; CapFloor.NPV() wiring is small; CapHelper test extension is one method).

### Controller orchestration rules (Phase 2c/2d lessons baked in)

- Always run `git merge --ff-only origin/<branch>` from the **main checkout**, never from inside a worktree's cwd. Verify with `git -C /Users/josemoya/eclipse-workspace/jquantlib log --oneline -3` after each merge.
- Between landings: each unmerged worktree rebases onto the new `main` before its next implementer dispatch. Force-push-with-lease after rebase.
- **Subagent watchdog stalls (Phase 2d C precedent):** if an agent stalls mid-flight, controller manually commits the in-progress state if it's clean and dispatches a focused continuation. Do NOT restart from scratch unless the stall left an inconsistent worktree.
- Worktree cleanup uses `git worktree remove --force <path>` followed by `rm -rf` if needed, then `git worktree prune`.

---

## 4. Tolerance, Probes & Test Discipline

### Tolerance tiers (inherits Phase 1 §4.2)

| Tier | Bound | Use |
|---|---|---|
| exact | bit-identical | enum ordinals, integer-quantized outputs |
| tight | `abs 1e-14 + rel 1e-12` | analytic outputs (G2 affine `discount(t)`, BlackFormula-based BlackCapFloorEngine prices, BlackSwaptionEngine prices) |
| loose | `abs 1e-8 + rel 1e-8` | tree-fingerprint values (G2 tree, TreeSwaptionEngine pricing) — Brent solver convergence noise floor; same precedent as Phase 2c WI-5 BK tree |

Per-test loose-tier exceptions must carry inline justification per design §4.2 (precedent: Phase 2c WI-1 CIR / WI-4 HW / WI-5 BK; Phase 2d WI-2 NCCV / WI-3 SABR cross-check).

### Probes

| WI | Probe file | Captures |
|---|---|---|
| A | `g2_probe.cpp` | G2 analytic `discount(t)` at multiple maturities + tree-fingerprint cells (i,j,k) on a fixed grid for `(a, σ, b, η, ρ) = (0.1, 0.01, 0.1, 0.005, -0.5)`, evalDate=2026-01-15, flat 5% curve. Optionally `swaption(args, range, intervals)` quadrature output for one well-posed swaption fixture. |
| B | (reuse Phase 2d `caphelper_probe.json` — already captures `modelValue` and `blackPrice(0.20)`; no new probe needed) | — |
| C | `blackswaptionengine_probe.cpp` | Swaption `NPV()` via `BlackSwaptionEngine` for a 5Y×5Y ATM payer swaption at vol=0.20, fixed flat-curve fixture |
| C | `treeswaptionengine_probe.cpp` | Swaption `NPV()` via `TreeSwaptionEngine` on HullWhite (a=0.1, σ=0.01) for the same fixture; tree-fingerprint at loose tier |
| C | `swaptionhelper_probe.cpp` | SwaptionHelper.modelValue + blackPrice for a HW-calibrated swaption fixture |

### Probe-driven test discipline

No test passes a value the implementer made up. If a value isn't from a C++ probe or a closed-form derivation, the test doesn't go in.

### Loose-tier expectations baked in

- **WI-1 G2:** analytic `discount(t)` likely tight (closed-form affine). Tree-fingerprint expected loose (Brent solver in `TermStructureFittingParameter`).
- **WI-2 BlackCapFloorEngine:** `modelValue()` and `blackPrice(0.20)` expected tight (closed-form Black76 sum over optionlets, no Brent / no Halton).
- **WI-3 BlackSwaptionEngine:** swaption price expected tight. **TreeSwaptionEngine:** tree-pricing expected loose (Brent solver in tree calibration). **SwaptionHelper:** `blackPrice` tight; `modelValue` loose if model is tree-based, tight if affine/closed-form.

### Test count expectation

649 → ~660 (+1 G2 discount fingerprint, +1 G2 tree fingerprint, +2 CapHelperTest extensions for modelValue/blackPrice, +1 BlackSwaptionEngine fingerprint, +1 TreeSwaptionEngine fingerprint, +1 or +2 SwaptionHelper modelValue/blackPrice, possibly +2 G2 swaption integral). Skipped: 22 → 22 (no un-skip work in 2e).

### Non-loosening rule

No tier loosening to force green. If a tight-expected test fails tight, root-cause first; loosen only with documented justification, never silently.

---

## 5. Pause Triggers

Inherits Phase 1 §7.3 with Phase 2c/2d deltas. Phase 2e additions / changes:

| Trigger | Status | Condition |
|---|---|---|
| A1 | active | Scanner stub count > 1000 |
| A2 | active | Tolerance looser than `1e-8` ever needed |
| A3 | active | Cross-validation suggests v1.42.1 itself is wrong |
| A4 | **active, sharpened** | New class strictly required outside the 61 existing packages, OR a port surfaces a need for substantively new infrastructure. For 2e: the new `pricingengines.swaption` directory is in scope (planned, not a surprise); but if WI-3 surfaces e.g. a need for `CashFlows.bps` variants Java doesn't have, A4 fires. |
| A6 | **disabled** | End-of-layer pause — per memory `feedback_phase2a_no_a6.md` |
| A7 | active | Per-WI audit divergence from C++ |
| A8 | inactive | Vasicek-pattern ripple — N/A in 2e (G2 is two-factor, not part of one-factor family fan-out) |
| A9 | active | Worktree-merge conflict requires manual resolution |
| A10 | inactive | XABR template-to-generics translation snag — N/A in 2e (no XABR work) |
| **A11 — new for 2e** | active | Inside WI-1 (G2), if the swaption integral path needs a non-trivial integrator wrapper (e.g. a 2D quadrature beyond what `KronrodIntegral`/`SegmentIntegral` provide), pause to discuss before improvising. The G2 swaption(...) C++ uses `SegmentIntegral` so this should not fire — but flag it. |
| **A12 — new for 2e** | active | Inside WI-3, if `Swaption.NPV()` wiring requires a deeper Instrument/Engine-arguments dispatch refactor than the simple `engine_.calculate()` pattern used in CapFloor, pause to discuss. |

---

## 6. Decision Log (P2E-1 .. P2E-7)

- **P2E-1** Subset choice = B (G2 + BlackCapFloorEngine + SwaptionHelper). BroadieKaya×3, AnalyticCapFloorEngine, Heston.discountBondOption deferred.
- **P2E-2** SwaptionHelper full-bundle (γ): port BlackSwaptionEngine + TreeSwaptionEngine + DiscretizedSwaption + SwaptionHelper full body. Both `modelValue()` and `blackPrice(volatility)` work end-to-end.
- **P2E-3** TreeSwaptionEngine chosen over JamshidianSwaptionEngine for the modelValue path. Reasoning: TreeSwaptionEngine works for both G2 (via `TreeLattice2D`) and any future tree-based model (HW, BK); JamshidianSwaptionEngine only works for affine 1-factor (HW, Vasicek, CIR). Jamshidian deferred.
- **P2E-4** Worktree topology = 3 worktrees, parallel launch from L0. Same pattern as Phase 2d.
- **P2E-5** WI-2 reuses Phase 2d's `caphelper_probe.json` — no new probe needed; the `modelValue` and `blackPrice` fields are already captured. The Phase 2d test asserted only `fairRate`; WI-2 retroactively asserts the other two fields.
- **P2E-6** AnalyticCapFloorEngine NOT in scope. Reasoning: BlackCapFloorEngine alone unblocks Phase 2d WI-1 retrofit goal; AnalyticCapFloorEngine is a redundant second engine at this stage.
- **P2E-7** Phase 2d's `Swap.legBPS(int)` and `Swap.legNPV(int)` accessors are reused in WI-3's SwaptionHelper.performCalculations (same as CapHelper used them).

---

## 7. Exit Criteria

All must hold to tag `jquantlib-phase2e-complete`:

1. `mvn -pl jquantlib test` green: `Failures: 0, Errors: 0`.
2. Test count delta: `649 → ~660` (± a small adjustment), with full breakdown in completion doc.
3. `Skipped: 22` (unchanged — no un-skip work in 2e).
4. **Scanner: `work_in_progress: 0`** (G2 closed; the symbolic milestone — Phase 1's "finish all stubs" mandate fully met).
5. All 3 worktrees merged fast-forward to `main` and removed; no orphan branches.
6. Every probe in `migration-harness/cpp/probes/` regenerates cleanly via `generate-references.sh`.
7. Per-test loose-tier exceptions all carry inline justification.
8. Phase 2e completion doc written (`docs/migration/phase2e-completion.md`) covering: per-WI summary with commit hashes, probe inventory, deviations from plan, Phase 2f seed list (BroadieKaya×3 + Lobatto/Laguerre integrators + AnalyticCapFloorEngine + JamshidianSwaptionEngine + Heston.discountBondOption + remaining swaption engines + BachelierCapFloorEngine).
9. Tag `jquantlib-phase2e-complete` pushed; memory `project_jquantlib_migration.md` updated.

---

## 8. Phase 2f Seed List (carry forward)

- BroadieKaya×3 Heston schemes + Gauss-Lobatto + Gauss-Laguerre integrator ports + Fourier-inversion harness (the original Phase 2d carve, still pending).
- AnalyticCapFloorEngine — second cap pricing engine.
- JamshidianSwaptionEngine — affine 1-factor closed-form swaption pricing.
- FdHullWhiteSwaptionEngine, FdG2SwaptionEngine — finite-difference swaption engines.
- Gaussian1D swaption engine family.
- HestonProcess `discountBondOption` — needs Phase 2c WI-1 chi-squared drift acceptability investigation first.
- BachelierCapFloorEngine — Normal-vol cap pricing (currently throws `UnsupportedOperationException` from Phase 2d WI-1 CapHelper.blackPrice when `VolatilityType.Normal` is used).
- Phase 3+ gap-fill packages (`experimental/`, `models/marketmodels/`, `termstructures/credit/`, `inflation/`).
