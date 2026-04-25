# JQuantLib Migration — Phase 2b: Drift Fixes and Incremental Ports

**Status:** Draft for approval
**Date:** 2026-04-25
**Author:** Jose Moya (with Claude collaboration)
**Work target:** direct commits to `main` (no migration branch — same as Phase 2a)
**C++ reference:** QuantLib **v1.42.1** (pinned, unchanged from Phase 1)
**Predecessor:** `docs/migration/phase2a-design.md` — approved 2026-04-24, completed 2026-04-24 (tag `jquantlib-phase2a-complete`, tip `c8235ff`).

---

## 0. Reading this document

This is the authoritative design for **Phase 2b** — drift fixes plus a pair of small incremental ports inside the existing 61 `org.jquantlib.*` packages. Phase 2b's mandate is narrow: clear the three Phase-2a carveouts that revealed real Java/C++ correctness drifts, plus close the C++-default-vs-Java-default gap in `HestonProcess`. CapHelper and G2 (the largest Phase-2a deferrals) stay carved to **Phase 2c**, which will be designed separately.

`phase2a-design.md` and (transitively) `phase1-design.md` remain binding for everything not revised here. Phase 2b inherits Phase 2a's ground-truth principle, tolerance tiers, git discipline, harness architecture, scanner tooling, and quality gates without modification. This document only specifies what's new, what's in scope, and what the exit criteria look like.

The companion plan (`docs/migration/phase2b-plan.md`) will be generated from this design using the `superpowers:writing-plans` skill after this doc is approved.

---

## 1. Context

### 1.1 State at Phase 2a completion

At the tip of Phase 2a (tag `jquantlib-phase2a-complete`, commit `c8235ff`), the scanner reports **2 stubs** — both Phase-2c carveouts (`CapHelper`, `G2`) — and **0** of every other kind. Test suite: **626 / 0 fail / 25 skipped**.

Outside the scanner, three known correctness issues are documented in `phase2a-carveouts.md`:

| Carveout | File(s) | Symptom |
|---|---|---|
| `WI-4-carveout-Vasicek` | `model/shortrate/onefactormodels/Vasicek.java` | Java loses C++'s `Parameter&` reference binding. Same pattern in HullWhite, BlackKarasinski, CoxIngersollRoss. |
| `WI-2-carveout-SABR` | `math/interpolations/SABRInterpolation.java`, two test files | `SABRInterpolation` converges to or starts with a β outside `[0,1]`, throwing from `Sabr.validateSabrParameters`. |
| `WI-2-carveout-simplex` | `math/optimization/Simplex.java`, `OptimizerTest.java` | `Simplex` rejects 1D problems with "Independent variable must be 1 dimensional". |

Plus one missing Heston discretization: `HestonProcess.Discretization.QuadraticExponentialMartingale` (C++'s default) is silently absent in Java's enum.

### 1.2 Why these four items and not others

The Phase-2a completion doc lists seven Phase-2b candidates. The design decisions in §7 below scoped Phase 2b to four:

- The three carveouts above must land here — they're known wrong, the diagnosis is in hand, and leaving them lurking grows their blast radius as new callers appear.
- Heston QEM is essentially free given the QE branch already ported in Phase 2a (commit `6eb170f`); not landing it leaves Java silently using a different default discretization than C++.

The remaining three Phase-2c candidates are explicitly out of scope (§2.2):

- `CapHelper` — needs `IborLeg` infrastructure outside the 61 packages (A4 territory).
- `G2` — needs `TreeLattice2D.grid` + two-factor calibration scaffolding.
- LM analytic-Jacobian path — no current Java caller; speculative without one.
- Remaining Heston schemes (NonCentralChiSquareVariance, BroadieKaya×3) — needs new math/integration infrastructure (A4).

### 1.3 Ground truth (inherited unchanged)

> **C++ QuantLib v1.42.1 is the source of truth. The existing Java code is our starting material, not a design to preserve. Anywhere Java diverges from v1.42.1 — signatures, implementations, constants, behavior — C++ wins.**

Pinned at commit `099987f0ca2c11c505dc4348cdb9ce01a598e1e5` (tag `v1.42.1`, dated 2026-04-16).

---

## 2. Goals and Non-goals

### 2.1 Goals

1. **Land the four work items** (WI-1 through WI-4, §3) with full v1.42.1 cross-validation.
2. **Re-enable the three tests carved in Phase 2a** — or carve SABR forward to Phase 2c with a documented diagnosis if depth proves too large.
3. **Preserve the cross-validated v1.42.1 fidelity invariant** from Phase 1. No test loosens tolerance without inline justification.

**Definition of "Phase 2b done"** (full detail in §6):

1. Scanner on `main` reports: 2 `work_in_progress` (CapHelper, G2 — unchanged from Phase 2a tip), 0 `not_implemented`, 0 `numerical_suspect`.
2. WI-1 — QEM enum value present; probe extended; tests green.
3. WI-2 — `OptimizerTest` un-skipped with both Simplex and LM in the active matrix; carveout entry updated.
4. WI-3 — Four calibration round-trip tests added (Vasicek, HullWhite, BlackKarasinski, CoxIngersollRoss); Vasicek carveout entry updated.
5. WI-4 — Either both SABR tests un-skipped and green, or `phase2c-carveouts.md` carries a `WI-2-SABR` entry with the discovered failure mode.
6. `(cd jquantlib && mvn test)` green; ~633 tests for the fix path or ~630 for the carve path; no regression.
7. `docs/migration/phase2b-completion.md` written.
8. Tag `jquantlib-phase2b-complete` at the final commit, pushed.

### 2.2 Non-goals

1. **No new top-level packages.** Same fence as Phase 1/2a. Enforced by pause trigger A4 (now redirected to the SABR carve gate, §5).
2. **CapHelper and G2 stay carved to Phase 2c.** They are the Phase-2c seed agenda by themselves; bundling them dilutes both phases.
3. **No remaining Heston schemes.** `NonCentralChiSquareVariance` and the three `BroadieKaya*` schemes stay deferred — they need new `Distribution`/`Integration` infrastructure that trips A4.
4. **No LM analytic-Jacobian path.** No current caller. The C++ ctor flag `useCostFunctionsJacobian` stays absent from the Java ctor.
5. **No SABR scope creep.** If WI-4's investigation reveals the fix needs new infrastructure or substantial algorithm rework, carve to Phase 2c per A4 and ship Phase 2b without it.
6. **No parallel-session execution.** Same as Phase 2a.

---

## 3. The Four Work Items

Order is **WI-1 → WI-2 → WI-3 → WI-4** (Approach 3 from the brainstorm: ascending complexity with the SABR investigation deliberately last so it can be carved without blocking earlier wins).

### 3.1 WI-1 — HestonProcess `QuadraticExponentialMartingale`

**Scope.** Add a `QuadraticExponentialMartingale` value to `HestonProcess.Discretization` and the martingale-correction `k0` recomputation inside the existing `QuadraticExponential` branch of `evolve()`. C++ ref: `ql/processes/hestonprocess.cpp` lines 488–506. Both sub-branches (`psi < 1.5` and `psi >= 1.5`) need:

- The QEM-specific `k0` override (different formula in each sub-branch).
- The `QL_REQUIRE` validity check (`A < 1/(2*a)` for `psi<1.5`; `A < beta` for `psi>=1.5`).

**Files.**
- Modify: `jquantlib/src/main/java/org/jquantlib/processes/HestonProcess.java`.
- Modify: `migration-harness/cpp/probes/processes/hestonprocess_qe_probe.cpp` (add 2 cases, don't fork the file).
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/processes/HestonProcessTest.java` (add 2 tests).

**Probe cases.** One per sub-branch:
- `qem_psiLow_centralVol` — same params as `qe_psiLow_centralVol` but Discretization = QEM.
- `qem_psiHigh_lowInitV` — same params as `qe_psiHigh_lowInitV` but Discretization = QEM.

The deltas in `retVal[0]` between QE and QEM at fixed inputs verify the `k0` override took effect; sub-branch coverage is preserved.

**Stopping criterion.** New enum value present; QEM tests green at tight tier (1e-12); existing QE tests untouched.

**Expected commits.** 1.

### 3.2 WI-2 — Simplex 1D-dimension fix + un-skip OptimizerTest

**Scope.** Diagnose and fix the "Independent variable must be 1 dimensional" failure in `Simplex.minimize` when fed a 1D problem. Per `phase2a-carveouts.md::WI-2-carveout-simplex`, the failure point is in how Simplex constructs its internal `Array` from a scalar problem; the offending line in the test path is `OptimizerTest.java:116`, but the exception is thrown inside `Simplex`.

**Investigation.** Read `jquantlib/src/main/java/org/jquantlib/math/optimization/Simplex.java` against `migration-harness/cpp/quantlib/ql/math/optimization/simplex.cpp`. Compare the vertex-array construction in `restart`/`minimize` with C++'s `extractCostFunction` + `vertices_` initialization. Most likely fix: 1D Array initialization with `Array(0)` followed by `add()` is constructing zero-dimensional Array instead of size-1, and Simplex's vertex builder rejects it. Possible alternate fix: Simplex itself needs a 1D special case (less likely — C++ handles 1D uniformly).

**Files.**
- Modify: `jquantlib/src/main/java/org/jquantlib/math/optimization/Simplex.java` (likely small, single-method fix).
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/math/optimization/OptimizerTest.java` — remove `@Ignore`, replace the `// TODO` carveout pointer with a fix-commit reference, uncomment the `LevenbergMarquardt` entry on line 104 so the cross-method matrix runs.
- Modify: `docs/migration/phase2a-carveouts.md` — update `WI-2-carveout-simplex` to "fixed in Phase 2b WI-2" with the fix-commit hash.

**Cross-validation.** OptimizerTest's existing 1D parabolic problem becomes the cross-validation. The expected `xMin = -b/(2a)` and `yMin = -(b² - 4ac)/(4a)` are analytic; Java must converge to these at tight tier under both Simplex and LM.

**Stopping criterion.** OptimizerTest un-skipped, both Simplex and LM in the active method matrix, green; carveout entry updated.

**Expected commits.** 1–2 (one fix + un-skip; an additional `align(math.optimization): ...` commit if Simplex's vertex algorithm structurally differs from C++).

### 3.3 WI-3 — Vasicek family `Parameter`-ref sweep

**Scope.** Apply a consistent indirection pattern across `Vasicek`, `HullWhite`, `BlackKarasinski`, `CoxIngersollRoss` so member parameter accessors read through `arguments_.get(i)` rather than holding a copy of the slot. C++ uses `Parameter&` reference binding in the constructor init list (e.g., `a_(arguments_[0])`), so subsequent assignments through `a_` update `arguments_[i]`; Java has no field-level reference semantics, so the copy diverges from the calibratable vector after construction.

**Design.** For each model:

- The protected `Parameter` fields (`a_`, `b_`, `sigma_`, `lambda_`, etc.) become small package-private getters that return `arguments_.get(i)` for the correct slot.
- The constructor sets `arguments_.set(i, new ConstantParameter(...))` rather than assigning to a member.
- Internal usages of `a_.get(0.0)`, etc., become `a().get(0.0)`.

C++'s `a_(arguments_[0])` reference-bind is replicated by routing all reads through the same single source of truth.

**No abstract extract.** The indirection-into-base-class form (Approach C in the brainstorm) is rejected: C++'s `Parameter&` binding is a C++-language feature, not a design pattern, and pushing it into `OneFactorModel`/`OneFactorAffineModel` would force every subclass to change its storage anyway. Each of the four models gets the same pattern applied locally; the diff stays surgical.

**Files (per model).**
- Modify: `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/Vasicek.java`
- Modify: `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/HullWhite.java`
- Modify: `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/BlackKarasinski.java`
- Modify: `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/CoxIngersollRoss.java`

**Probe + test (per model).** One calibration round-trip:

1. Build the model with known parameters (`a=0.1`, `b=0.05`, `sigma=0.01`, etc.).
2. Generate synthetic discount-bond prices on a maturity grid.
3. Build `OneFactorAffineModel.calibrate` (or the model-specific calibration) against those prices.
4. Assert recovered parameters match the originals.

C++ probe (`migration-harness/cpp/probes/model/shortrate/<model>_calibration_probe.cpp`) runs the same calibration via `QuantLib::CalibrationHelper`/`<Model>::calibrate` and captures the recovered vector. Tight tier on the recovered parameters; loose tier on the bond prices (since calibration involves LM, the same cumulative-FMA loosening as the linear-fit case in Phase 2a applies).

**Files (probes + tests).**
- Create: `migration-harness/cpp/probes/model/shortrate/{vasicek,hullwhite,blackkarasinski,coxingersollross}_calibration_probe.cpp`
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/{Vasicek,HullWhite,BlackKarasinski,CoxIngersollRoss}CalibrationTest.java`

**Stopping criterion.** Four calibration round-trip tests added and green; no existing test regresses; Vasicek carveout entry updated.

**Expected commits.** 4–6: one shared `align(model.shortrate.onefactor): introduce Parameter-arguments indirection` setting up the pattern, then one `stub(model.shortrate.onefactor): wire <Model> through indirection + calibration probe` per model. The pattern commit lands first so the per-model commits are uniform.

### 3.4 WI-4 — SABR transformation fix-or-carve (time-boxed)

**Scope (investigation half).** Read `SABRInterpolation.java` lines 399–500-ish (the `SabrParametersTransformation` and the surrounding init/calibration glue) against C++ `ql/termstructures/volatilities/interpolation/sabrinterpolation.hpp` (the `SABRWrapper` end-criteria logic and `SABRSpecs::guess`). Identify whether the β-out-of-`[0,1]` failure is:

- (a) **Transcription bug in `SabrParametersTransformation`.** The line-412 commented hint (`y_(1) = std::atan(dilationFactor_*x(1))/M_PI + 0.5`) suggests the β cube transformation may be partially commented out or applied to the wrong parameter index. Likely a 5–20 LOC fix.
- (b) **Missing initial-guess strategy.** C++'s `SABRSpecs::guess` returns parameters known to lie inside the legal cube; Java may be feeding an illegal β to `validateSabrParameters` before optimization even starts. Likely a constructor-path fix.
- (c) **Deeper algorithmic divergence.** Rare but possible — e.g., Java's transformation handles `isBetaFixed` paths differently, or the LM-side transform-pre-eval flow differs from C++. Would require substantial port work.

**Time-box.** ~1 working session of investigation + fix attempt. If the fix is (a) or (b), land it as `align(math.interpolations): ...` commits with a probe (exact `SabrParametersTransformation` outputs at fixed inputs, plus a calibration round-trip on representative volatility surface data). Then un-skip both SABR tests. If the diagnosis is (c) or requires new infrastructure (e.g., new LM termination-criteria machinery, new constraint-projection helpers outside the existing 61 packages), write a Phase-2c carveout entry with the diagnosis and skip un-skipping.

**Files (fix path).**
- Modify: `jquantlib/src/main/java/org/jquantlib/math/interpolations/SABRInterpolation.java` (likely the transformation block).
- Create: `migration-harness/cpp/probes/math/interpolations/sabr_interpolation_probe.cpp` (fixed-input transform tests + 1 calibration round-trip).
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRTransformationTest.java` (exact-input transform tests; complements the un-skipped facade tests).
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationTest.java` — remove `@Ignore`, replace pointer comment with fix-commit reference.
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/InterpolationTest.java` — same for `testSabrInterpolation`.
- Modify: `docs/migration/phase2a-carveouts.md` — update `WI-2-carveout-SABR` to "fixed in Phase 2b WI-4" with commit hashes.

**Files (carve path).**
- Create: `docs/migration/phase2c-carveouts.md` (new file) with a `WI-2-SABR` entry citing the specific failure mode.
- Modify: both SABR test files — keep `@Ignore`, update pointer comment from `phase2a-carveouts.md` to `phase2c-carveouts.md`.

**Stopping criterion.** Either (fix path) SABR tests un-skipped and green at tight (transformation) / loose (calibration) tier; or (carve path) `phase2c-carveouts.md::WI-2-SABR` exists with a specific diagnosis and the two SABR tests stay `@Ignore`d with their carveout pointers updated.

**Expected commits.** 0–4 depending on outcome (0 if pure carve with no aligned probe; 4 if fix path with separate align/probe/test/un-skip commits).

---

## 4. Workflow and quality gates (inherited from Phase 2a)

### 4.1 Workflow

- **Direct commits to `main`**, fast-forward, no PRs, no migration branch.
- **One stub (or cycle-batch) = one commit**, signed off with `-s`, no `Co-authored-by` trailer, unsigned (no GPG/SSH).
- **Every commit compiles and passes `(cd jquantlib && mvn test)` *before* it lands.**
- **Commit message form:** `<kind>(<pkg>): <verb> ...` with `kind ∈ {stub, align, infra, chore, docs, test}`.
- **Divergences found mid-stub** → separate preceding `align(<pkg>): ...` commit, not folded into the stub commit.
- **API changes** to match v1.42.1 are automatic; no per-change approval needed.

### 4.2 Quality gates

- **TDD per stub.** Probe before port for any cross-validated change. Java test that fails first; port; green; commit.
- **Tolerance tiers** (inherited from Phase-1 design §4.2):
  - **Exact** — bit-identical doubles, integer/date/enum equality.
  - **Tight** — `|a − b| < 1e-14 + 1e-12·|cpp|`. Default for closed-form formulas and bit-stable kernels.
  - **Loose** — `|a − b| < 1e-8 + 1e-8·|cpp|`. For Monte Carlo, root-finding, PDE solvers, iterative LM with cumulative FMA drift.
- **Per-test loosening** allowed only with inline justification citing the reason (e.g., `// Loose on params: 10 LM iterations cumulate ~1-ulp FMA drift to ~3e-9 relative — see phase2a-progress.md.`).
- **v1.42.1 is ground truth.** C++ wins where Java diverges.

---

## 5. Pause triggers

Phase 2b inherits A1–A7 from Phase 2a `phase2a-design.md` §7.3 (which itself inherits A1–A6 from `phase1-design.md` §7.3). Phase 2b makes three changes:

| ID | Status in Phase 2b | Note |
|---|---|---|
| A1 | active, unchanged | Scanner stub count >1000 → pause. Will not fire (we're at 2). |
| A2 | active, unchanged | Tolerance looser than `1e-8` needed → pause and ask. |
| A3 | active, unchanged | Cross-validation suggests v1.42.1 itself is wrong → pause. |
| A4 | **redirected — SABR carve gate** | If WI-4's investigation needs new classes outside the 61 packages, A4 fires immediately → carve to Phase 2c without further deliberation. No mid-WI ask; the time-box decides. |
| A5 | not used | Phase-1-design §7.3 reserves A5 for "API shape changes" — automatic per Phase-1 §1.3, no trigger needed. Numbering kept for continuity. |
| A6 | **disabled** | Per `feedback_phase2a_no_a6.md`. No end-of-layer ack pause. Phase 2b runs all WIs end-to-end. |
| A7 | inactive — N/A to scope | Phase-2a addition: WI-4 audit-divergence circuit-breaker (≥20 of 56 markers as Tier-2). Phase 2b has no large audit pass; trigger retained for numbering continuity but cannot fire. |
| **A8** | **new for Phase 2b** | **Vasicek-pattern alignment risk.** If the WI-3 indirection design breaks a passing test in any of the four one-factor models, pause and ask the user whether to (i) revise the indirection, (ii) carve the affected model and ship the others, or (iii) carve the entire Vasicek family forward. This is the only "deliberate ask" in Phase 2b — the family sweep has the most blast radius. |

---

## 6. Exit criteria

| # | Criterion |
|---|---|
| 1 | Scanner on `main` reports `work_in_progress: 2` (CapHelper, G2 — Phase-2c seeds, unchanged from Phase 2a tip), `not_implemented: 0`, `numerical_suspect: 0`. |
| 2 | **WI-1.** `HestonProcess.Discretization.QuadraticExponentialMartingale` enum value present; `hestonprocess_qe` probe extended with QEM cases; `HestonProcessTest` gains 2 QEM tests, all tight-tier green. |
| 3 | **WI-2.** `OptimizerTest#testOptimizers` un-skipped, both Simplex and LM active in its method-types matrix, green. `phase2a-carveouts.md::WI-2-carveout-simplex` updated to "fixed in Phase 2b WI-2" with fix-commit reference. |
| 4 | **WI-3.** Four calibration round-trip tests added (`Vasicek`, `HullWhite`, `BlackKarasinski`, `CoxIngersollRoss`), all tight-tier green against v1.42.1 probes. `phase2a-carveouts.md::WI-4-carveout-Vasicek` updated to "fixed in Phase 2b WI-3" with fix-commit reference. |
| 5 | **WI-4.** Either (a) `SABRInterpolationTest#testSABRInterpolationTest` and `InterpolationTest#testSabrInterpolation` un-skipped and green, with `phase2a-carveouts.md::WI-2-carveout-SABR` updated to "fixed in Phase 2b WI-4"; or (b) `phase2c-carveouts.md` exists with a `WI-2-SABR` entry citing the specific failure mode, and the two tests stay `@Ignore`d with their carveout pointers updated to the 2c file. |
| 6 | `(cd jquantlib && mvn test)` green. Test count ≥ 626 + ~7 (≥ 633), with the exact delta depending on WI-4's outcome. Skipped count drops by at least 1 (OptimizerTest), possibly 3 (if SABR fixes). |
| 7 | `docs/migration/phase2b-completion.md` written, summarizing all four WIs with their commit references. |
| 8 | Tag `jquantlib-phase2b-complete` at the final commit, pushed to `origin`. |

### Test count expectations

| WI | Δ tests | Notes |
|---|---|---|
| WI-1 | +2 | QEM cross-validated cases (one per QE sub-branch) |
| WI-2 | +0 net | OptimizerTest re-counts; `Skipped` drops by 1 |
| WI-3 | +4 | One calibration round-trip per one-factor model |
| WI-4 (fix) | +1 to +3 | Depends on whether both SABR tests un-skip and any new probe-driven tests are added |
| WI-4 (carve) | +0 | Tests stay skipped; carveout doc only |

Estimated final state: **633–635 tests** for the fix path, **630 tests** for the carve path. Skipped: **22–24** (down from 25).

### Definition of "Phase 2b done"

All eight criteria above met simultaneously. Carveouts are valid outcomes for **WI-4 only** — the other three WIs must land their fixes (no carve-on-difficulty for WI-1/2/3, since the scope is well-bounded).

---

## 7. Decision log

Phase-2b-specific decisions (P2B-N) recorded as the brainstorming session reached them, so a future reader can reconstruct intent.

### P2B-1 — Phase-shape: drift-first 2b, infra in 2c

**Decision.** Phase 2b covers drift fixes and incremental ports only (Vasicek family, SABR, Simplex, Heston QEM). CapHelper and G2 stay carved to a separate Phase 2c.

**Reason.** The two infrastructure items (`CapHelper`/`IborLeg`, `G2`/`TreeLattice2D`) are each large enough to warrant their own focused phase; bundling them with surgical drift fixes dilutes both.

**Alternative considered.** "Bundle all seven seeds" would have made Phase 2b the size of Phase 1 (~3-5× Phase 2a) and required retiring A4 as a pause trigger. "Infra-first" would have left the three known-broken tests red while attention went to CapHelper/G2.

### P2B-2 — Vasicek scope: family sweep

**Decision.** Fix Vasicek + HullWhite + BlackKarasinski + CoxIngersollRoss in one shared design pass with a calibration round-trip per model.

**Reason.** The Java/C++ reference-semantics bug is a copy-paste of the same C++ pattern across all four models; fixing only Vasicek leaves the same bug in three more, and the next CapHelper-class consumer (which uses HullWhite) would trip it.

**Alternative considered.** Single-class fix (leaves the bug latent in three more places); abstract extract into `OneFactorAffineModel` (rejected — C++'s `Parameter&` reference binding is a C++-language feature, not a design pattern, and forcing it into a base class would still ripple into all subclasses).

### P2B-3 — Heston scope: QEM only

**Decision.** Add `QuadraticExponentialMartingale` only. `NonCentralChiSquareVariance` and the three `BroadieKaya*` schemes stay deferred.

**Reason.** QEM is C++'s default discretization and sits inside the QE branch already ported in Phase 2a (~30 LOC). The other four schemes need new infrastructure (`InverseCumulativeNonCentralChiSquare`, Fourier-inversion + quadrature) that trips A4. No current Java caller selects them.

**Alternative considered.** "QEM + NCCS" (would have required a new `Distribution` class — borderline A4); "all five" (definite A4 trip).

### P2B-4 — LM analytic-Jacobian: defer

**Decision.** No `useCostFunctionsJacobian` ctor flag added in Phase 2b.

**Reason.** No current Java caller asks for analytic Jacobians; without one, there's no signal on the right contract (Java's `CostFunction.jacobian()` defaults to central differences while MINPACK assumes forward; aligning the default ripples through every existing CostFunction subclass). Easier to revisit when a real caller appears.

**Alternative considered.** "Add the flag, throw if true" (reverses Phase-2a WI-5 cleanup spirit); "full port with caller-overrides-jacobian semantics" (~30 LOC + 1 probe — fine, but speculative without a calling test).

### P2B-5 — Ordering: ascending complexity, SABR last with time-box

**Decision.** WI-1 (Heston QEM) → WI-2 (Simplex) → WI-3 (Vasicek family) → WI-4 (SABR fix-or-carve, time-boxed).

**Reason.** Builds momentum on small wins, leaves the highest-uncertainty item (SABR depth unknown) for the end where carving is a clean exit rather than a mid-phase scope shock.

**Alternative considered.** "Risk-first" (SABR at L1) — sapped momentum if the answer is "carve"; "Phase-2a verbatim ordering" (no time-box) — implicitly assumes SABR will fit, which we can't yet verify.

### P2B-6 — A4 redirected to SABR carve gate

**Decision.** A4 (new class outside the 61 packages) becomes the SABR carve gate in Phase 2b — no mid-WI ask, the time-box decides.

**Reason.** WI-4 is the only Phase-2b WI with depth uncertainty; the other three are well-bounded. Letting A4 fire automatically inside WI-4 lets us treat the carve as a normal terminal state of the WI rather than an interruption.

### P2B-7 — A8 added for Vasicek-pattern alignment risk

**Decision.** New A8 trigger: if the WI-3 indirection design breaks a passing test in any of the four one-factor models, pause and ask the user.

**Reason.** WI-3 has the largest blast radius in Phase 2b — touching four classes that are direct dependencies of CapHelper/G2 (Phase-2c targets). A bad indirection pattern shipped quietly would contaminate Phase 2c work.

**Alternative considered.** Fold into A2 (tolerance) or A4 (new class) — neither matches the failure mode. A regression in a passing test isn't a tolerance issue and doesn't involve new packages; it's a "did we get the indirection shape right" question that genuinely needs human input.
