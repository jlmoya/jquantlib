# Phase 2h Design — Fdm framework port + FdHullWhiteSwaptionEngine + FdG2SwaptionEngine

**Status:** approved 2026-04-26.
**Predecessor:** Phase 2g — `jquantlib-phase2g-complete` @ `615806e`.
**Inherits unchanged:** Phase 1 design §1-12, Phase 2a §7 (P2A-1..P2A-8), Phase 2b §5 (P2B-1..P2B-7), Phase 2c §5 (P2C-1..P2C-6), Phase 2d §5 (P2D-1..P2D-6), Phase 2e §5 (P2E-1..P2E-7), Phase 2f §6 (P2F-1..P2F-7), Phase 2g §6 (P2G-1..P2G-7).
**Source-of-truth pin:** QuantLib v1.42.1 @ `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`.

---

## 1. Goals & Non-Goals

### Goal

Port the modern QuantLib `Fdm*` finite-difference framework (~3500 LOC across ~30 classes) and complete the two FD swaption engine ports deferred from Phase 2g (FdHullWhiteSwaptionEngine + FdG2SwaptionEngine). Phase 2h closes the A4/A16 deferral cleanly and unlocks future Fdm-dependent engines (FdHestonHullWhite, FdSabrVanilla, FdBlackScholesVanilla, FdConvertibleBond, FdAndreasenHugeLocalVol, etc.) for later phases.

### In scope (3 work items)

- **WI-1 — Fdm framework port** (largest WI ever, ~3500 LOC C++). Full subset of `ql/methods/finitedifferences/` that FdHullWhite + FdG2 need. Internal port order (5 sub-layer commits per CLAUDE §4.2):
  - **Sub-layer 1.1: Operators core** (~1200 LOC, ~10 classes) — `FdmLinearOp`, `FdmLinearOpComposite`, `FdmLinearOpLayout`, `FdmLinearOpIterator`, `TripleBandLinearOp`, `FirstDerivativeOp`, `SecondDerivativeOp`, `NinePointLinearOp`, `FdmHullWhiteOp`, `FdmG2Op`.
  - **Sub-layer 1.2: Meshers** (~415 LOC, 4 classes) — `FdmMesher`, `Fdm1dMesher`, `FdmSimpleProcess1dMesher`, `FdmMesherComposite`.
  - **Sub-layer 1.3: Inner value + step conditions + boundaries** (~410 LOC) — `FdmInnerValueCalculator`, `FdmAffineModelSwapInnerValue<M extends AffineModel>` (generic Java adaptation of C++ template), `FdmStepConditionComposite` + `vanillaComposite` factory, `FdmBoundaryConditionSet`.
  - **Sub-layer 1.4: Schemes** (~200 LOC for minimum) — `FdmSchemeDesc` POD + `HundsdorferScheme` + `DouglasScheme`. Other schemes deferred unless WI-2/WI-3 need them.
  - **Sub-layer 1.5: Solvers** (~600 LOC) — `FdmSolverDesc`, `FdmBackwardSolver`, `Fdm1dimSolver`, `Fdm2dimSolver`, `FdmHullWhiteSolver`, `FdmG2Solver`.
  - **No probe-driven tests at this layer required.** Optional unit tests (`TripleBandLinearOpTest`, `FdmLinearOpLayoutTest`, `FdmSimpleProcess1dMesherTest`) at TIGHT or EXACT tier — port speculatively only if surfaces during port.
- **WI-2 — FdHullWhiteSwaptionEngine** (~161 LOC C++ engine). Constructor `(HullWhite, xGrid=100, tGrid=50, dampingSteps=2, FdmSchemeDesc=Hundsdorfer)`. Probe + LOOSE-tier test (~1e-6 noise floor). Probe references generated AFTER WI-1 lands.
- **WI-3 — FdG2SwaptionEngine** (~173 LOC C++ engine). Constructor `(G2, xGrid=50, yGrid=50, tGrid=50, dampingSteps=2, FdmSchemeDesc=Hundsdorfer)`. Probe + LOOSE-tier test (~1e-5 noise floor; per-test exception likely for 2D FD convergence). Probe references generated AFTER WI-1 lands.

### Out of scope (explicitly deferred)

- **Transcendental library port (Approach B)** — Phase 2i candidate. Pure-Java port of libc++'s exp/log/sin/cos/pow algorithms (~5000 LOC). Independent of Fdm; bundling would risk Phase 2c-style overload.
- **Gaussian1D swaption engine family + Gaussian1D model** — 10 engines; deserves its own focused phase (Phase 2j or later).
- **Other Fdm-dependent engines** (FdHestonHullWhite, FdSabrVanilla, FdBlackScholesVanilla, FdConvertibleBond, FdAndreasenHugeLocalVol, etc.) — defer until WI-1's framework lands and stabilizes.
- **Schemes beyond Hundsdorfer + Douglas** (CraigSneyd, ModifiedCraigSneyd, MethodOfLines, TrBDF2, CrankNicolson-Fdm-shape, ImplicitEuler-Fdm-shape, ExplicitEuler-Fdm-shape) — port only if FdHullWhite/FdG2 require; otherwise defer.
- **HestonProcess.pdf() Fokker-Planck** — only if a Java caller emerges.
- **BlackSwaptionEngine Cash/ParYieldCurve settlement** (needs CashFlows.bps(InterestRate) + Schedule.tenor()).
- **additionalResults in cap/swaption engines** (vega, optionletsPrice, optionletsVega).
- **SwaptionHelper.addTimesTo / CapHelper.addTimesTo** `Time` annotation impedance.
- **TreeLattice2D underlying value access API formalization.**
- **HaltonRsg FMA platform-conditionality documentation.**
- **Per-test 5e-8 SABR cross-check tolerance investigation.**
- **G2 tree-fingerprint TIGHT promotion** (~5e-12 OU discretization round-off — Phase 2g rolled-back attempt).
- **SphereCylinderOptimizer TIGHT promotion** (~3e-13 abs golden-section noise).
- **JVM Math.exp ULP slack** (would require correctly-rounded exp port — Phase 2i candidate).
- **Phase 3+ gap-fill packages** (`experimental/`, `models/marketmodels/`, `termstructures/credit/`, `inflation/`).

### Non-goals

- No refactoring of existing pre-2010 FD framework (`Pde`, `BSMOperator`, `FiniteDifferenceModel`, `StandardFiniteDifferenceModel`, etc.) — coexists alongside the new Fdm framework.
- No API improvements beyond what v1.42.1 dictates.
- No tier loosening to force green.

---

## 2. Architecture & Components

### WI-1 — Fdm framework port (worktree A, sequential, longest WI ever)

Java packages mirror C++ subdirectory layout — adds new subpackages alongside the existing flat `org.jquantlib.methods.finitedifferences` (which retains the pre-2010 framework, `BSMOperator`/`Pde`/`StandardFiniteDifferenceModel`/etc., per non-goal #1).

#### Sub-layer 1.1 — Operators core

Package `org.jquantlib.methods.finitedifferences.operators.*`. ~1200 LOC, ~10 classes.

- `FdmLinearOp` (interface) — `Array apply(Array)`, `Matrix toMatrix()`.
- `FdmLinearOpComposite` (interface, extends `FdmLinearOp`) — adds `setTime(t1, t2)`, `int size()`, `Array preconditioner(Array, double dt)`.
- `FdmLinearOpLayout` — N-d index ↔ flat-index mapping; provides dimension iterator + neighborhood lookups.
- `FdmLinearOpIterator` — per-cell iterator for Layout.
- `TripleBandLinearOp` — banded operator with three diagonals (Java `double[][]` shape; foundation for derivative ops).
- `FirstDerivativeOp` (extends TripleBandLinearOp) — central-difference first derivative on a Layout direction.
- `SecondDerivativeOp` (extends TripleBandLinearOp) — central-difference second derivative.
- `NinePointLinearOp` — 2D cross-derivative (needed for FdmG2Op's `rho * σ1 * σ2 * ∂²/∂x∂y` term).
- `FdmHullWhiteOp` (implements FdmLinearOpComposite) — 1D HW dynamics on a mesh; composes FirstDeriv + SecondDeriv.
- `FdmG2Op` (implements FdmLinearOpComposite) — 2D G2 dynamics; composes 2 × FdmHullWhiteOp + NinePointLinearOp cross term.

#### Sub-layer 1.2 — Meshers

Package `org.jquantlib.methods.finitedifferences.meshers.*`. ~415 LOC, 4 classes.

- `FdmMesher` (interface) — `dxArray`, `locations`, `layout`.
- `Fdm1dMesher` — concrete 1D mesh with positions + dx values.
- `FdmSimpleProcess1dMesher` (extends Fdm1dMesher) — OU-process-driven 1D mesh, used by HW + G2.
- `FdmMesherComposite` (implements FdmMesher) — wraps N × Fdm1dMesher into a multi-dim mesh; provides FdmLinearOpLayout.

#### Sub-layer 1.3 — Inner value + step conditions + boundaries

Packages `org.jquantlib.methods.finitedifferences.utilities.*` and `org.jquantlib.methods.finitedifferences.stepconditions.*`. ~410 LOC.

- `FdmInnerValueCalculator` (interface) — `double innerValue(FdmLinearOpIterator, double time)`.
- `FdmAffineModelSwapInnerValue<M extends AffineModel>` (Java generic adaptation of C++ template `FdmAffineModelSwapInnerValue<HullWhite>` / `<G2>`; same precedent as Phase 2d WI-3 XABRSpecs).
- `FdmStepConditionComposite` — composes per-time-slice step conditions + inner value. Static factory `vanillaComposite(...)` mirroring C++.
- `FdmBoundaryConditionSet` — typed `List<BoundaryCondition>` for the modern Fdm shape (distinct from pre-2010 `BoundaryCondition`).

#### Sub-layer 1.4 — Schemes

Package `org.jquantlib.methods.finitedifferences.schemes.*`. ~200 LOC for minimum.

- `FdmSchemeDesc` — POD with static factory methods `Hundsdorfer()`, `Douglas()`, etc.
- `HundsdorferScheme` — Hundsdorfer-Verwer ADI splitting, the C++ default for FdHullWhite + FdG2.
- `DouglasScheme` — Douglas-Rachford ADI splitting.

Defer `CraigSneydScheme`, `ModifiedCraigSneydScheme`, `CrankNicolsonScheme` (Fdm-shape, distinct from pre-2010), `ImplicitEulerScheme`, `ExplicitEulerScheme`, `MethodOfLines`, `TrBDF2` unless WI-2/WI-3 require them.

#### Sub-layer 1.5 — Solvers

Package `org.jquantlib.methods.finitedifferences.solvers.*`. ~600 LOC.

- `FdmSolverDesc` — POD bundling mesher + boundary set + step conditions + inner value + grid resolution.
- `FdmBackwardSolver` — rollback driver; runs scheme back through time grid.
- `Fdm1dimSolver` — 1D wrapper used by FdmHullWhiteSolver.
- `Fdm2dimSolver` — 2D wrapper used by FdmG2Solver.
- `FdmHullWhiteSolver` — HW-specific solver wiring Fdm1dimSolver + FdmHullWhiteOp + FdmSimpleProcess1dMesher.
- `FdmG2Solver` — G2-specific solver wiring Fdm2dimSolver + FdmG2Op + 2 × FdmSimpleProcess1dMesher via FdmMesherComposite.

#### Sub-layer commit ordering (CLAUDE §4.2 — each commit compiles + passes `mvn test`)

1. 1.1 Operators core (foundation).
2. 1.2 Meshers (independent of 1.1 except FdmLinearOpLayout reference).
3. 1.4 Schemes (depends on 1.1).
4. 1.3 Inner value + step conditions + boundaries (depends on 1.1 + 1.2).
5. 1.5 Solvers (depends on 1.1 + 1.2 + 1.3 + 1.4).

5 separate `infra(methods.finitedifferences.<subpkg>): port <piece> (Phase 2h WI-1)` commits.

#### Optional unit tests at sub-layer level

`TripleBandLinearOpTest`, `FdmLinearOpLayoutTest`, `FdmSimpleProcess1dMesherTest` — small probe-driven tests at TIGHT or EXACT tier for foundational classes. WI-2/WI-3 engine probes provide the integration-level coverage. Plan reserves these as **optional** — port speculatively only if surfaces during port.

### WI-2 — FdHullWhiteSwaptionEngine (worktree B)

**New class** in `org.jquantlib.pricingengines.swaption`:

- `FdHullWhiteSwaptionEngine extends Swaption.EngineImpl`. Constructor `(HullWhite model, int xGrid=100, int tGrid=50, int dampingSteps=2, FdmSchemeDesc=Hundsdorfer())`. `calculate()` builds `FdmSimpleProcess1dMesher` for the HW state, wraps in `FdmMesherComposite`, instantiates `FdmHullWhiteOp` on the mesh, builds `FdmAffineModelSwapInnerValue<HullWhite>`, builds `FdmStepConditionComposite.vanillaComposite(...)` from the swaption arguments, runs `FdmHullWhiteSolver`, reads the value at `(t=0, x=0)`.

**New tests:**

- `FdHullWhiteSwaptionEngineTest` — fingerprint at LOOSE tier (1e-6 noise floor for typical 1D FD discretization). Probe references generated AFTER WI-1 lands.

### WI-3 — FdG2SwaptionEngine (worktree C)

**New class** in `org.jquantlib.pricingengines.swaption`:

- `FdG2SwaptionEngine extends Swaption.EngineImpl`. Constructor `(G2 model, int xGrid=50, int yGrid=50, int tGrid=50, int dampingSteps=2, FdmSchemeDesc=Hundsdorfer())`. Same shape as FdHullWhiteSwaptionEngine but builds 2 × `FdmSimpleProcess1dMesher` (one per OU component) wrapped in `FdmMesherComposite`, instantiates `FdmG2Op`, runs `FdmG2Solver` on a 2D mesh.

**New tests:**

- `FdG2SwaptionEngineTest` — fingerprint at LOOSE tier (~1e-5 noise floor for 2D FD; per-test exception likely with inline justification).

### Probe regeneration coordination

Both WI-2 and WI-3 generate new probes that internally call HW.tree() / G2.tree() during FD setup, which goes through Brent (Phase 2g WI-1 fixed). The Brent fix is already on main, so probe regeneration happens on top of post-Phase-2g main — no Phase-2g-style ordering surprises.

---

## 3. Worktree Topology & Layer Ordering

### Worktree topology

3 git worktrees (proven shape from Phase 2c-2g).

```
/Users/josemoya/eclipse-workspace/jquantlib-2h-A/  branch: phase-2h-A-fdm-framework
/Users/josemoya/eclipse-workspace/jquantlib-2h-B/  branch: phase-2h-B-fd-hullwhite
/Users/josemoya/eclipse-workspace/jquantlib-2h-C/  branch: phase-2h-C-fd-g2
```

### Worktree dependency

Same shape as Phase 2g — WI-1 lands first; WI-2 and WI-3 gate on WI-1 landing because their engine code depends on the Fdm framework classes.

```
                       ┌─────────────────────────────────┐
                       │ WI-1 Fdm framework port         │
                       │ (5 sub-layer commits, sequential)│
                       │ ~3500 LOC, ~30 classes          │
                       └────────────┬────────────────────┘
                                    │
                       ┌────────────┴────────────┐
                       ▼                         ▼
        ┌──────────────────────┐    ┌──────────────────────┐
        │ WI-2 FdHullWhite     │    │ WI-3 FdG2            │
        │ (~161 LOC + probe +  │    │ (~173 LOC + probe +  │
        │  test, LOOSE tier)   │    │  test, LOOSE tier)   │
        └──────────────────────┘    └──────────────────────┘
```

WI-2 and WI-3 implementers don't dispatch until WI-1 lands. Single sequential dispatch saves orchestration overhead and avoids stale port-code based on imagined Java APIs.

### Layer ordering

- **L0 — pre-flight + worktree setup.**
  - Confirm baseline `mvn -pl jquantlib test` → `Tests run: 675, Failures: 0, Errors: 0, Skipped: 22`.
  - Confirm scanner: `0 stubs`.
  - Create 3 worktrees off `main` tip `615806e`.
  - Verify each builds clean.
  - Init `phase2h-progress.md`.

- **L1 — WI-1 sequential (the long pole).** Sub-layers commit in dependency-correct order (per P2H-3):
  - L1-step-1: Sub-layer 1.1 Operators core (foundation) — port and commit.
  - L1-step-2: Sub-layer 1.2 Meshers (independent of 1.1 except FdmLinearOpLayout reference) — port and commit.
  - L1-step-3: Sub-layer 1.4 Schemes (depends on 1.1) — port and commit.
  - L1-step-4: Sub-layer 1.3 Inner value + step conditions + boundaries (depends on 1.1 + 1.2) — port and commit.
  - L1-step-5: Sub-layer 1.5 Solvers (depends on 1.1 + 1.2 + 1.3 + 1.4) — port and commit.
  - Optional unit tests at sub-layer level if surfaces during port.
  - Land all sub-layer commits to main fast-forward.

- **L2 — WI-2 + WI-3 parallel (after WI-1 lands).**
  - **B (FdHullWhite):** rebase onto post-WI-1 main; port engine; generate probe; cross-validate Java test at LOOSE tier; land.
  - **C (FdG2):** rebase onto post-WI-1 main; port engine; generate probe; cross-validate Java test at LOOSE tier (likely per-test exception ~1e-5); land.

- **L3 — completion doc + tag.**
  - Once all 3 worktrees have landed, write `docs/migration/phase2h-completion.md`.
  - Tag `jquantlib-phase2h-complete`, push.
  - Clean up worktrees + delete branches local + remote.
  - Update memory.

### Wallclock estimate

WI-1 is the long pole — by far. ~3500 LOC across ~30 classes ported in 5 dependency-ordered commits is multi-hour subagent work; possibly multiple subagent dispatches with watchdog stalls (Phase 2c-2f precedent). WI-2 and WI-3 are roughly equal (~330 LOC each + probe + test).

The scope is large enough that **WI-1 may need to span multiple subagent dispatches**, with the controller committing in-progress state if a subagent stalls.

### Controller orchestration rules (Phase 2c-2g lessons baked in)

- Always run `git -C /Users/josemoya/eclipse-workspace/jquantlib merge --ff-only origin/<branch>` from the **main checkout**.
- WI-2/WI-3 implementers MUST rebase before generating probe references (post-WI-1 main).
- Force-push-with-lease after rebase.
- **Subagent watchdog stalls:** controller commits in-progress state if clean; dispatches focused continuation per sub-layer if WI-1 stalls mid-port.
- **Orphan files** in main checkout from implementers using main worktree's pre-warmed cpp/build (Phase 2d/2e/2f precedent) — diff-then-rm or stash before merge.
- Worktree cleanup: `git worktree remove --force <path>` + `git worktree prune` + `git branch -D` + `git push origin --delete`.

---

## 4. Tolerance, Probes & Test Discipline

### Tolerance tiers (inherits Phase 1 §4.2)

| Tier | Bound | Use |
|---|---|---|
| exact | bit-identical | foundational operator/mesher unit tests where applicable (TripleBandLinearOp.toMatrix, FdmLinearOpLayout.iterate ordering) |
| tight | `abs 1e-14 + rel 1e-12` | analytic operator outputs (FirstDerivativeOp/SecondDerivativeOp on polynomial inputs), mesher coordinate fingerprints if any |
| loose | `abs 1e-8 + rel 1e-8` | FD engine NPV fingerprints (FdHullWhite ~1e-6 noise floor; FdG2 ~1e-5, per-test exception likely) |

Per-test loose-tier exceptions need inline justification per design §4.2.

### Probes

| WI | Probe file | Captures |
|---|---|---|
| 1 | (optional) `triplebandlinearop_probe.cpp` | TripleBandLinearOp.apply(input) for a few well-known kernels (1D Laplacian, advection). Optional. |
| 1 | (optional) `fdmsimpleprocess1dmesher_probe.cpp` | FdmSimpleProcess1dMesher locations + dx values for OU-process inputs. Optional. |
| 1 | (optional) `fdmhullwhiteop_probe.cpp` | FdmHullWhiteOp.apply on constant/linear/quadratic. Optional. |
| 1 | (optional) `fdmg2op_probe.cpp` | FdmG2Op.apply including cross-derivative term. Optional. |
| 2 | `fdhullwhiteswaptionengine_probe.cpp` | NPV for the 5Y×5Y ATM payer swaption fixture under FdHullWhiteSwaptionEngine on HW(0.1, 0.01); xGrid=100, tGrid=50, dampingSteps=2. **Required.** |
| 3 | `fdg2swaptionengine_probe.cpp` | NPV for the same fixture under FdG2SwaptionEngine on G2(0.1, 0.01, 0.1, 0.005, -0.5); xGrid=50, yGrid=50, tGrid=50, dampingSteps=2. **Required.** |

### Probe-driven test discipline

No test passes a value the implementer made up. If a value isn't from a C++ probe or a closed-form derivation, the test doesn't go in.

**WI-1 sub-layer testing strategy:** the framework classes are infrastructure; their correctness is validated end-to-end by WI-2/WI-3 engine probes. Per-sub-layer unit tests are **optional** — port them only if surfaces during port (e.g. an obvious analytic test like `FirstDerivativeOp` on `f(x)=x²` should return `2x`). Don't write speculative tests.

### Loose-tier expectations baked in

- **WI-1 Fdm framework:** any unit tests at TIGHT or EXACT (deterministic operator/mesher arithmetic on rational inputs).
- **WI-2 FdHullWhite:** swaption NPV expected at LOOSE (~1e-6 noise floor for typical 1D xGrid=100, tGrid=50). If 1e-8 fails but 1e-6 passes, document inline as "FD discretization noise floor" and use 1e-6.
- **WI-3 FdG2:** swaption NPV expected at LOOSE with per-test exception (~1e-5 noise floor for 2D FD; 2D convergence is slower than 1D). Inline justification.

### Test count expectation

675 → ~677-679 (+2 to +4):
- WI-1: 0 to +2 optional sub-layer unit tests (no requirement).
- WI-2: +1 (FdHullWhiteSwaptionEngineTest).
- WI-3: +1 (FdG2SwaptionEngineTest).

Skipped: 22 → 22 (no un-skip work).

### Non-loosening rule

No tier loosening to force green. If a tight-expected test fails tight, root-cause first; loosen only with documented justification.

**Special focus for WI-1:** the framework port has 5 dependency-ordered sub-layers landing as separate commits. Each commit must compile + pass `mvn test` (no regressions in existing 675 tests). If a previously-passing test fails after a sub-layer lands, root-cause first — most likely cause would be a name collision with the existing pre-2010 `BoundaryCondition`/`StepCondition` types or an inadvertent shared dependency.

### Phase 2f Math.exp ULP propagation

The Phase 2f A13 finding (JVM Math.exp 1-ULP slack vs libc++) means FD engine probes that compound transcendentals through long backward-rollback time grids may surface tier issues at the LOOSE 1e-8 threshold. If so:
- Don't loosen below 1e-5 without inline justification.
- Document as Phase 2i transcendental-library candidate's blast-radius widening.

---

## 5. Pause Triggers

Inherits Phase 1 §7.3 with Phase 2c-2g deltas. Phase 2h additions / changes:

| Trigger | Status | Condition |
|---|---|---|
| A1 | active | Scanner stub count > 1000 |
| A2 | active | Tolerance looser than `1e-8` ever needed (FD 2D engines may legitimately need this with inline justification — not a blocker) |
| A3 | active | Cross-validation suggests v1.42.1 itself is wrong |
| A4 | **active, sharpened** | New class strictly required outside the 61 existing packages, OR substantively new infrastructure beyond what the design anticipated. For 2h: the Fdm framework port IS the new infrastructure (in scope, planned). But if WI-1 surfaces a deeper dependency than the ~30 classes scoped (e.g. needs Lobatto integrator from Phase 2f, or a math primitive Java doesn't have), pause. |
| A6 | **disabled** | End-of-layer pause — per memory `feedback_phase2a_no_a6.md` |
| A7 | active | Per-WI audit divergence from C++ |
| A8 | inactive | Vasicek-pattern ripple — N/A in 2h |
| A9 | active | Worktree-merge conflict requires manual resolution |
| A10 | inactive | XABR template-to-generics — N/A in 2h |
| A11 | inactive | G2 swaption integrator gap — closed in Phase 2f |
| A12 | inactive | Swaption.NPV() wiring — closed in Phase 2e C.0 |
| A13 | **active (carried from 2f)** | Transcendental-driven drift impossibility (Math.exp ULP slack). For 2h: FD long-rollback paths may surface this; tier compromise acceptable with inline justification, not a hard blocker. |
| A14 | inactive | Complex/external library infrastructure gap — N/A in 2h |
| A15 | **active (carried from 2g)** | Fdm port surfaces a previously-hidden Java port bug in code paths that previously compiled but never executed. If a regenerated probe + Java test fails not because of expected drift but because of a previously-hidden bug, pause. |
| A16 | **active (carried from 2g)** | Fdm port surfaces a missing dependency that's neither in the planned ~30 classes nor a clear sub-task addition (e.g. needs an entire new linear algebra solver beyond TridiagonalOperator). Pause to discuss before improvising. |
| **A17 — new for 2h** | active | WI-1 sub-layer commit count exceeds 5 + 2 (i.e. more than 2 unplanned `align(...)` commits surface during port). If many small port-correctness fixes accumulate, that's a sign the pre-2010 framework has more contamination than expected — pause to assess scope expansion. |

---

## 6. Decision Log (P2H-1 .. P2H-7)

- **P2H-1** Subset choice = A (Fdm framework + 2 deferred engines only). Transcendental library, Gaussian1D family, other Fdm-dependent engines, smaller cleanups all deferred.
- **P2H-2** WI-1 sequential single-worktree (not multi-worktree parallelism). Reasoning: heavy interdependencies in the Fdm operator/scheme/solver spine make true parallelism a fiction. Multi-worktree adds orchestration overhead without saving wallclock.
- **P2H-3** Sub-layer commit ordering inside WI-1: 1.1 Operators → 1.2 Meshers → 1.4 Schemes → 1.3 Inner+Step+Boundary → 1.5 Solvers (5 separate `infra(methods.finitedifferences.<subpkg>)` commits per CLAUDE §4.2). Each commit compiles + passes `mvn test`.
- **P2H-4** Java packages mirror C++ subdirectory layout (`operators`/`meshers`/`schemes`/`solvers`/`stepconditions`/`utilities` subpackages). Pre-2010 framework retained flat in parent package — no refactor.
- **P2H-5** WI-2 + WI-3 dispatched **after** WI-1 lands (not in parallel with WI-1 dispatch). Reasoning: engine code depends on WI-1's ~30 new Java classes existing on main; speculative parallel drafting risks stale port-code based on imagined APIs.
- **P2H-6** `FdmAffineModelSwapInnerValue<M extends AffineModel>` uses Java generics adaptation of C++ template (same precedent as Phase 2d WI-3 XABRSpecs `<S extends XABRSpecs>`).
- **P2H-7** Schemes: port only Hundsdorfer + Douglas (the C++ defaults FdHullWhite/FdG2 use). Other scheme classes deferred. Add only if scope creep surfaces during WI-2/WI-3 (e.g. C++ test reference uses a non-default scheme and Java probe must match).

---

## 7. Exit Criteria

All must hold to tag `jquantlib-phase2h-complete`:

1. `mvn -pl jquantlib test` green: `Failures: 0, Errors: 0`.
2. Test count delta: `675 → ~677-679` (+2 to +4, with full breakdown in completion doc).
3. `Skipped: 22` (unchanged — no un-skip work).
4. Scanner: `work_in_progress: 0` (no regressions; Phase 2e milestone preserved).
5. All 3 worktrees merged fast-forward to `main` and removed; no orphan branches.
6. Every probe in `migration-harness/cpp/probes/` regenerates cleanly via `generate-references.sh`.
7. Per-test loose-tier exceptions all carry inline justification.
8. **WI-1 framework completeness disclosure:** which sub-layers landed; which optional unit tests were added; whether scheme scope crept beyond Hundsdorfer + Douglas; final class count + LOC delta.
9. **WI-2/WI-3 tier disclosure:** what tier each engine test landed at; if per-test exceptions used, what noise floor was observed.
10. Phase 2h completion doc written (`docs/migration/phase2h-completion.md`) covering: per-WI summary with commit hashes, deviations from plan, A4/A13/A15/A16/A17 firings if any, Phase 2i seed list with explicit transcendental library port candidate.
11. Tag `jquantlib-phase2h-complete` pushed; memory `project_jquantlib_migration.md` updated.

---

## 8. Phase 2i Seed List (carry forward)

**Headline candidate:**

- **Transcendental library port (Approach B from Phase 2g brainstorm)** — pure-Java port of libc++'s exp/log/sin/cos/pow algorithms (~5000 LOC). Would unlock BroadieKaya asset-leg from 5e-3 down to LOOSE-or-better; possibly tighten any FD long-rollback drift surfaced in Phase 2h. Recommended as Phase 2i's primary focus if pursued.

**Other carry-forwards:**

- Gaussian1D swaption engine family + Gaussian1D model (10 engines + model — Phase 2j candidate).
- Other Fdm-dependent engines (FdHestonHullWhite, FdSabrVanilla, FdBlackScholesVanilla, FdConvertibleBond, FdAndreasenHugeLocalVol, etc.) — now unblocked by Phase 2h's framework port.
- Schemes beyond Hundsdorfer + Douglas (CraigSneyd, ModifiedCraigSneyd, etc.) — port if/when an engine needs them.
- HestonProcess.pdf() Fokker-Planck (only if a Java caller emerges).
- BlackSwaptionEngine Cash/ParYieldCurve settlement (needs CashFlows.bps(InterestRate) + Schedule.tenor()).
- additionalResults in cap/swaption engines (vega, optionletsPrice, optionletsVega).
- SwaptionHelper.addTimesTo / CapHelper.addTimesTo `Time` annotation impedance.
- TreeLattice2D underlying value access API formalization.
- HaltonRsg FMA platform-conditionality documentation.
- Per-test 5e-8 SABR cross-check tolerance investigation.
- G2 tree-fingerprint TIGHT promotion (~5e-12 OU discretization round-off).
- SphereCylinderOptimizer TIGHT promotion (~3e-13 abs golden-section noise).
- Phase 3+ gap-fill packages (`experimental/`, `models/marketmodels/`, `termstructures/credit/`, `inflation/`).
