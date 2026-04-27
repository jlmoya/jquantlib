# Phase 2g Completion Report — JQuantLib Migration

**Date:** 2026-04-26
**Predecessor tag:** `jquantlib-phase2f-complete` @ `debedf9`
**Phase 2g tip on main:** `00ce433`
**Tag:** `jquantlib-phase2g-complete`

## Final state

- **Tests:** `675 / 0 failures / 0 errors / 22 skipped` (was `675/0/0/22` at Phase 2f end). Test count unchanged (no new test methods); **19 tests promoted from LOOSE to TIGHT tier.**
- **Scanner:** `0 stubs` — Phase 1 mandate preserved (WI-2/WI-3 deferred without re-introducing scanner WIPs).
- **Commits:** 7 commits since Phase 2f tip (5 from WI-1 + 1 progress + 1 completion).

## Per-WI summary

### WI-1 — Brent.solveImpl alignment + bundled fixes ✅ complete

Worktree A. 5 commits on main:

- **`148213f`** `align(math.solvers1D): Brent.solveImpl pre-loop init match C++ v1.42.1`. Java's pre-loop init seeded `root=xMax; froot=fxMax; d=0; e=0`; C++ evaluates `f(root)` once before the main loop, branches on `froot * fxMin < 0` to pick the bracket side, and seeds `d = root - xMax; e = d`. Also at the convergence-return inside the main loop, C++ adds `f(root); ++evaluationNumber;` before returning. Mirrored both. BrentTest evaluation count update: 10/13 → 12/12 due to +2 evaluations from pre-loop and final-return.
- **`a221555`** `test(pricingengines.swaption,model.shortrate): tier promotions post-Brent fix`. **5 tests promoted LOOSE → TIGHT:**
  - JamshidianSwaptionEngine swaption fingerprint
  - G2.testSwaptionIntegralFingerprint (closes Phase 2e A11 carve at TIGHT)
  - TreeSwaptionEngine fingerprint
  - HW tree-fingerprint test
  - BK tree-fingerprint test
- **`7965cbd`** `test(processes): NCCV + BroadieKaya variance-leg tier promotion to tight post-Brent fix`. **14 tests promoted LOOSE → TIGHT:**
  - NCCV evolve fingerprints (5 cases) — **Phase 2f A13 retry SUCCEEDED.** The Brent fix eliminated the residual ~1e-9 inverse-CDF noise that had blocked Phase 2f's NCCV tier promotion attempt.
  - BroadieKaya variance leg (9 evolve fingerprint cases across 3 schemes × 3 fixtures).
  - **Asset leg (`evolved[0]`) stays at `Tolerance.within(5e-3)` per Phase 2f A13** — Math.exp/cos/sin/Bessel/GammaFunction compound through the Fourier-CDF at every Lobatto/Laguerre quadrature node; that drift is structural (out of scope without a transcendental library).
- **`f3ef44d`** `align(instruments): VanillaSwap.setupArguments inverted isAssignableFrom + List capacity-vs-size fix`. Phase 2e WI-3 + Phase 2f WI-2 deliberately bypassed both bugs; now fixed. Two changes:
  - `isAssignableFrom` direction inverted → `instanceof`
  - `new ArrayList<>(int)` (capacity, broken `.set(i, ...)`) → `Collections.nCopies(n, null)` for all 9 affected lists. No test changes surfaced (existing fingerprint tests had workarounds populating Swaption.ArgumentsImpl manually for unrelated reasons).
- **`00ce433`** `align(cashflow): FloatingRateCoupon honor fixingDays==0 literally`. Java had `fixingDays_ = fixingDays == 0 ? index.fixingDays() : fixingDays`; C++ honors 0 literally via `Null<Natural>()` sentinel. Java now uses `Constants.NULL_NATURAL` matching C++. CapHelperTest (only consumer of `withFixingDays(0)`) continued to pass at TIGHT both pre- and post-fix — references already encoded C++ 0-literal behavior; pre-fix Java just happened to agree closely on flat-curve fixtures.

**Probe regeneration outcome (Task A.2):** Ran `generate-references.sh` end-to-end against all 34 Brent-touching JSON files. **Zero numerical drift** — only timestamp drift on `generated_at`. C++ probes were already correct ground-truth; Brent fix simply brought Java into alignment without disturbing references. No commit needed for probe regen.

**Rolled-back tier-promotion attempts (3, all genuinely unrelated to Brent):**
- G2.testTreeFingerprint TIGHT attempted, rolled back to LOOSE — drift ~5e-12 above tight ceiling, root cause is OU-process discretization round-off in TwoFactorModel.tree(grid), not Brent.
- SphereCylinderOptimizer TIGHT attempted, rolled back to LOOSE — drift ~3e-13 absolute on values ~5.867e-7 (relative ~5e-6 above tight); golden-section minimization noise, unrelated to Brent root-finding.

**Triggers:** No A15/A13/A9 fired. No previously-hidden bugs surfaced. The Brent fix was bit-faithful rather than divergence-amplifying.

### WI-2 — FdHullWhiteSwaptionEngine ⏸ DEFERRED to Phase 2h

Worktree B. **0 commits.** A4/A16 fired before any port work. No edits, no commits, worktree clean throughout.

**Gap:** Java's `methods/finitedifferences/` (35 files) is the **pre-2010 QuantLib FD framework** (`Pde`, `BSMOperator`, `TridiagonalOperator`, `FiniteDifferenceModel`, `MixedScheme`, `CrankNicolson`, `ExplicitEuler`, `StepCondition`, etc.). The modern `Fdm*` framework that C++ v1.42.1's `FdHullWhiteSwaptionEngine.calculate()` depends on is entirely absent — ~3500 LOC across ~30 classes:

| Missing class family | Examples | LOC (.hpp+.cpp) |
|---|---|---|
| Mesher hierarchy | `FdmMesher`, `FdmMesherComposite`, `Fdm1dMesher`, `FdmSimpleProcess1dMesher` | ~415 |
| Operator framework | `FdmHullWhiteOp`, `FdmLinearOp`, `FdmLinearOpComposite`, `FdmLinearOpLayout`, `FdmLinearOpIterator`, `TripleBandLinearOp`, `FirstDerivativeOp`, `SecondDerivativeOp` | ~1,224 |
| Inner value | `FdmInnerValueCalculator`, `FdmAffineModelSwapInnerValue<HullWhite>` | ~411 |
| Step conditions | `FdmStepConditionComposite::vanillaComposite` factory | ~218 |
| Boundaries | `FdmBoundaryConditionSet` | (in `boundarycondition.hpp`) |
| Solvers | `FdmSolverDesc`, `Fdm1dimSolver`, `FdmHullWhiteSolver`, `FdmBackwardSolver` | ~599 |
| Scheme descriptors | `FdmSchemeDesc::Hundsdorfer`/`Douglas`/`CraigSneyd`/`ModifiedCraigSneyd`/`MethodOfLines`/`TrBDF2`/etc. | ~600 |

`CurveDependentStepCondition.java` already documents this gap inline (line 53): *"no direct C++ v1.42.1 counterpart — the newer C++ step-condition framework is Fdm\*-prefixed with a different class layout. This class remains as a Java-only shim."*

**Why deferred (Option A from the brainstorm):** The Fdm framework port is a phase by itself (~3500 LOC). Bundling on top of WI-1's Brent fix would have made Phase 2g unwieldy (~5500 LOC + multi-day execution + significant risk). Cleaner shape: Phase 2g ships WI-1 only as a focused Brent + alignments + tier-promotions phase; Phase 2h gets an explicit "WI-0 Fdm framework port" before FdHullWhite + FdG2 + future Fdm-dependent engines.

### WI-3 — FdG2SwaptionEngine ⏸ DEFERRED to Phase 2h

Worktree C. **0 commits.** Same A4/A16 fire as WI-2 — same Fdm dependency chain plus `FdmG2Op` (TripleBandLinearOp ⊗ TripleBandLinearOp + correlation cross-term) and 2D mesher. Cleanly deferred alongside WI-2.

## Final scanner state

```
$ python3 tools/stub-scanner/scan_stubs.py
wrote docs/migration/stub-inventory.json (0 stubs)
wrote docs/migration/worklist.md
```

**Scanner WIP: 0** — Phase 2e milestone preserved. Deferring WI-2/WI-3 added zero stubs (no port-code committed for the FD engines).

## Test suite final state

```
$ (cd jquantlib && mvn test) | grep -E "^\[WARNING\] Tests run"
[WARNING] Tests run: 675, Failures: 0, Errors: 0, Skipped: 22
```

**Test count delta:** 675 → 675 (unchanged — no new test methods). **Skipped:** 22 (unchanged).

**Tier-promotion summary (the actual Phase 2g value delivery):**

| Cluster | Δ promotions | Notes |
|---|---|---|
| Swaption + tree fingerprints | +5 LOOSE → TIGHT | Jamshidian, G2.swaption, TreeSwaption, HW tree, BK tree |
| Heston NCCV evolve | +5 LOOSE → TIGHT | Phase 2f A13 retry succeeded |
| Heston BroadieKaya variance leg | +9 LOOSE → TIGHT | Asset leg stays 5e-3 (Math.exp ULP — A13 carry) |
| **Total LOOSE → TIGHT** | **+19** | All 19 tests now at production-grade tolerance |

No previously-passing test was broken during Phase 2g.

## Tier-Promotion Disclosure (per design §7.8)

- **Jamshidian + G2.swaption:** TIGHT promotion succeeded (was LOOSE in Phase 2f WI-2 due to Brent.solveImpl divergence; new Brent matches C++ bit-faithfully).
- **NCCV tier promotion:** TIGHT promotion succeeded — **Phase 2f A13 retry** worked. The new Brent init eliminated the residual ~1e-9 inverse-CDF noise that compounded with Math.exp ULP slack to break Phase 2f's NCCV TIGHT attempt.
- **Opportunistic CIR/HW/BK tree promotions:** HW tree + BK tree TIGHT succeeded; G2 tree TIGHT failed (rolled back, ~5e-12 OU discretization round-off — unrelated to Brent).
- **BroadieKaya variance leg (9 cases):** TIGHT succeeded.
- **BroadieKaya asset leg:** stays at `Tolerance.within(5e-3)` (Phase 2f A13 carry — Math.exp ULP slack compounds through Fourier-CDF transcendentals at every quadrature node; structural, out of scope).

## Alignment-Fix Disclosure (per design §7.9)

- **VanillaSwap.setupArguments:** Both bugs fixed. Inverted `isAssignableFrom` → `instanceof`; `new ArrayList<>(int)` capacity → `Collections.nCopies(n, null)` for all 9 affected lists. No test changes surfaced — existing fingerprint tests had unrelated workarounds.
- **FloatingRateCoupon.fixingDays==0:** Fixed via `Constants.NULL_NATURAL` sentinel matching C++ `Null<Natural>()`. CapHelperTest passed pre- and post-fix at TIGHT (references already encoded C++ behavior; pre-fix Java accidentally agreed on flat-curve fixtures).

## Deviations from the plan

1. **WI-2 + WI-3 DEFERRED to Phase 2h.** A4 + A16 fired on both worktrees during port-code dispatch. Java's modern `Fdm*` framework (~3500 LOC) entirely absent — not a "small bundled fix" the design's A4 anticipated. Per Option A from the brainstorm decision, Phase 2g shipped WI-1 only.
2. **Probe regeneration was a no-op.** Plan task A.2 anticipated regenerating Brent-touching probe references; in practice C++ ground-truth was already correct (the divergence was Java's pre-fix Brent diverging FROM C++, not vice versa). Java ran `generate-references.sh` end-to-end and got zero numerical drift across 34 JSONs. No probe-regen commit needed.
3. **NCCV tier promotion succeeded** — design treated this as conditional/optional ("attempt NCCV promotion"). The Brent fix narrowed the gap enough that TIGHT held cleanly across all 5 NCCV cases.
4. **BroadieKaya variance leg promotion was a bonus** — not explicitly listed in the plan's tier promotion checklist but was the natural consequence of NCCV tightening (BroadieKaya schemes invert NCCV via Brent; same noise floor). 9 cases promoted.
5. **3 rolled-back promotion attempts** — G2 tree fingerprint, SphereCylinderOptimizer, and one CapHelper assertion. All rolled back with inline justification noting the residual drift is unrelated to Brent (OU discretization, golden-section minimization, FD discretization noise floor).
6. **A15 NOT triggered** — no previously-hidden bug surfaced. The Brent fix was bit-faithful rather than divergence-amplifying.
7. **A9 NOT triggered** — single-WI worktree topology (B and C deferred); no cross-worktree merge contention.

## Phase 2h seed list

**Headline:**

- **Fdm framework port + FdHullWhiteSwaptionEngine + FdG2SwaptionEngine** — the deferred Phase 2g WI-2/WI-3. Sub-phases:
  - **WI-0: Fdm framework port** (~3500 LOC across ~30 classes). Mesher hierarchy + operator framework + inner value + step conditions + boundary conditions + solvers + scheme descriptors. Preserves Java's existing pre-2010 FD scaffold (still used by `BSMOperator`-based engines) — adds the modern `Fdm*` framework alongside.
  - **WI-1: FdHullWhiteSwaptionEngine** + probe + LOOSE-tier test (FD discretization noise floor ~1e-6).
  - **WI-2: FdG2SwaptionEngine** + probe + LOOSE-tier test (2D FD ~1e-5; per-test exception likely).

**Other carry-forwards:**

- **Transcendental library port (Approach B from Phase 2g brainstorm)** — pure-Java port of libc++'s exp/log/sin/cos/pow algorithms (~5000 LOC). Would unlock BroadieKaya asset-leg from 5e-3 down to LOOSE-or-better. Phase 2h alternative if Fdm port deferred further; or a parallel Phase 2i.
- **Gaussian1D swaption engine family** + Gaussian1D model (10 engines + model — Phase 2h or 2i candidate).
- **HestonProcess.pdf() Fokker-Planck** — only if a Java caller emerges.
- **BlackSwaptionEngine Cash/ParYieldCurve settlement** (needs `CashFlows.bps(InterestRate)` + `Schedule.tenor()`).
- **additionalResults in cap/swaption engines** (vega, optionletsPrice, optionletsVega).
- **SwaptionHelper.addTimesTo / CapHelper.addTimesTo** `Time` annotation impedance.
- **TreeLattice2D underlying value access API formalization.**
- **HaltonRsg FMA platform-conditionality documentation.**
- **Per-test 5e-8 SABR cross-check tolerance investigation.**
- **G2 tree-fingerprint TIGHT promotion** (~5e-12 OU discretization round-off — investigate G2.tree(grid) discretization scheme).
- **SphereCylinderOptimizer TIGHT promotion** (~3e-13 abs golden-section noise — investigate convergence threshold).
- **Phase 3+ gap-fill packages** (`experimental/`, `models/marketmodels/`, `termstructures/credit/`, `inflation/`).

## Worktree cleanup

Phase 2g used 3 git worktrees (A/B/C) at `/Users/josemoya/eclipse-workspace/jquantlib-2g-{A,B,C}/`. WI-1 (worktree A) committed and pushed; WI-2 + WI-3 (worktrees B/C) had no commits and no edits (BLOCKED on A4/A16 before any port work). All 3 worktrees + branches removed cleanly post-tag.

The parallel-execution model worked again — A/B/C dispatched simultaneously; B/C cleanly stopped at A4/A16 without any wasted code; A landed independently. Reinforces the Phase 2c-2f lesson that A4/A16/A13/A15 pause triggers are the design's safety net, not failure modes.
