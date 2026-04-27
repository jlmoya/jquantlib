# Phase 2g Design — Brent.solveImpl alignment + bundled fixes + FD swaption engines

**Status:** approved 2026-04-26.
**Predecessor:** Phase 2f — `jquantlib-phase2f-complete` @ `debedf9`.
**Inherits unchanged:** Phase 1 design §1-12, Phase 2a §7 (P2A-1..P2A-8), Phase 2b §5 (P2B-1..P2B-7), Phase 2c §5 (P2C-1..P2C-6), Phase 2d §5 (P2D-1..P2D-6), Phase 2e §5 (P2E-1..P2E-7), Phase 2f §6 (P2F-1..P2F-7).
**Source-of-truth pin:** QuantLib v1.42.1 @ `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`.

---

## 1. Goals & Non-Goals

### Goal

Land the Brent.solveImpl pre-loop init alignment (Phase 2f WI-2 priority finding — unlocks Jamshidian + G2.swaption tight tier and may unlock NCCV tier promotion), bundle two small alignment fixes that earlier phases deliberately bypassed (VanillaSwap.setupArguments + FloatingRateCoupon.fixingDays), and complete Java's first FD pricing engines (FdHullWhiteSwaptionEngine + FdG2SwaptionEngine).

### In scope (3 work items)

- **WI-1 — Brent.solveImpl alignment + bundled fixes.** Fix Brent.solveImpl pre-loop init to mirror C++ v1.42.1 (evaluate `f(guess)` once before the main loop to seed `root_/d/e`; Java currently seeds `root=xMax; froot=fxMax`). Re-fingerprint the 11 production Brent callers' probes/tests. Tier promotions: Jamshidian + G2.swaption from LOOSE to TIGHT; attempt NCCV promotion. Bundled alignment fixes:
  - VanillaSwap.setupArguments inverted `isAssignableFrom` + List capacity-vs-size bug
  - FloatingRateCoupon.fixingDays==0 divergence
- **WI-2 — FdHullWhiteSwaptionEngine.** First FD pricing engine in Java. C++ `fdhullwhiteswaptionengine.{hpp,cpp}` (161 LOC). Uses Java's existing FD scaffold in `methods/finitedifferences/`. Probe references generated AFTER WI-1 lands.
- **WI-3 — FdG2SwaptionEngine.** Sibling FD engine for G2 model. C++ `fdg2swaptionengine.{hpp,cpp}` (173 LOC). Uses existing FD scaffold + TreeLattice2D infrastructure. Probe references generated AFTER WI-1 lands.

### Out of scope (explicitly deferred)

- Gaussian1D swaption engine family + Gaussian1D model (10 engines + model — Phase 2h candidate).
- **Transcendental library port (Approach B)** — pure-Java port of libc++'s exp/log/sin/cos/pow algorithms. P2G-6 keeps this as a deliberate Phase 2h decision rather than a 2g side-quest. Would unlock BroadieKaya asset-leg + NCCV tier + transcendental-heavy EXACT tier.
- HestonProcess.pdf() Fokker-Planck (no Java callers).
- BlackSwaptionEngine Cash/ParYieldCurve settlement path (needs CashFlows.bps(InterestRate) + Schedule.tenor()).
- additionalResults in cap/swaption engines (vega, optionletsPrice, optionletsVega).
- SwaptionHelper.addTimesTo / CapHelper.addTimesTo `Time` annotation impedance.
- TreeLattice2D underlying value access API formalization.
- HaltonRsg FMA platform-conditionality documentation.
- Per-test 5e-8 SABR cross-check tolerance investigation.
- BroadieKaya asset-leg per-test 5e-3 tolerance (blocked on Math.exp ULP).
- JVM Math.exp ULP slack vs libc++ (would require correctly-rounded exp port; out of scope here).
- Phase 3+ gap-fill packages (`experimental/`, `models/marketmodels/`, `termstructures/credit/`, `inflation/`).

### Non-goals

- No refactoring of unrelated code.
- No API improvements beyond what v1.42.1 dictates.
- No tier loosening to force green.

---

## 2. Architecture & Components

### WI-1 — Brent.solveImpl alignment + bundled fixes (worktree A)

**Modified:**

- `org.jquantlib.math.solvers1D.Brent` — port C++ `brent.hpp` solveImpl to seed pre-loop using `f(guess)` rather than the Java-current `root=xMax; froot=fxMax`. Mirror C++ v1.42.1 lines that compute `root_ = guess; d_ = guess; e_ = guess; froot_ = f(guess);` before entering the main loop. The Dekker-Brent algorithm's pivot selection in subsequent iterations depends on this initial state.
- All 11 Brent production callers' probe references regenerate as needed:
  - `pricingengines.swaption.JamshidianSwaptionEngine` — promote test to TIGHT (was LOOSE due to Brent divergence; expected delta ~7e-11 → drops to ~1e-13 territory).
  - `model.shortrate.twofactormodels.G2.swaption(...)` — promote test to TIGHT.
  - `processes.HestonProcess` (varianceDistribution → InverseNCCS via Brent, BroadieKaya cdf_nu_ds via Brent) — re-attempt NCCV tier promotion (was rolled back in Phase 2f due to Brent-amplified Math.exp drift; new Brent init may fix this).
  - `cashflow.CashFlows`, `instruments.Bond`, `instruments.ImpliedVolatilityHelper`, `model.BlackCalibrationHelper` — verify probes still pass; regenerate references where necessary.
  - `model.shortrate.onefactormodels.{BlackKarasinski,OneFactorModel}` — verify tree-fingerprint tests still pass; regenerate references; opportunistically promote tier if they pass TIGHT.
  - `math.distributions.InverseNonCentralCumulativeChiSquaredDistribution` — verify probes (Brent inversion of NCCS).
- `org.jquantlib.instruments.VanillaSwap.setupArguments` — fix inverted `isAssignableFrom` check (currently always-false for `Swap.ArgumentsImpl` from non-VanillaSwap callers); fix List allocation pattern — change `new ArrayList<>(size)` (capacity, not size) followed by `.set(i, ...)` (IndexOutOfBoundsException because capacity ≠ size) to `new ArrayList<>(Collections.nCopies(size, null))` or equivalent so `.set(i, ...)` works.
- `org.jquantlib.cashflow.FloatingRateCoupon` — fix `fixingDays==0` divergence. Java currently has `fixingDays_ = fixingDays == 0 ? index.fixingDays() : fixingDays`; C++ honors 0 as 0. Change Java to honor 0 literally. Probe regeneration needed for any cap/swaption test that relies on this behavior; both Phase 2f WI-1 probes deliberately omitted `withFixingDays(0)` to dodge this — flip them to use 0 explicitly post-fix.

**Approach for re-fingerprinting:** the implementer runs every probe affected by a Brent caller through `generate-references.sh`, captures the new C++ values, then runs Java tests against the new references. Tests that were at LOOSE due to Brent divergence (Jamshidian + G2.swaption) get promoted in the same commit. Tests that drift slightly within their existing tier just get the new reference values landed.

**Each fix is a separate `align(<pkg>)` commit per CLAUDE §4.2:** Brent in its own commit; VanillaSwap.setupArguments in another; FloatingRateCoupon.fixingDays in another. Tier promotions in their own commits. Bundling at worktree level (shared fingerprint capacity) but separate at commit level.

### WI-2 — FdHullWhiteSwaptionEngine (worktree B)

**New classes (in `org.jquantlib.pricingengines.swaption`):**

- `FdHullWhiteSwaptionEngine` — port of v1.42.1 `fdhullwhiteswaptionengine.{hpp,cpp}` (161 LOC total). Constructor takes a `HullWhite` model + grid resolution parameters (xGrid, tGrid, dampingSteps). `calculate()` builds an FD scheme on a (state × time) grid; HW dynamics drive the state; rolls back from exercise dates to `t=0`; reads the swaption value at the initial node. Implements `Swaption.EngineImpl`.

**Java FD scaffold reuse:** Java has 35 files in `methods/finitedifferences/` from earlier phases. Verify the actual subset the FD engine needs and reuse what's there. If a small adapter is missing (e.g. boundary condition class for swaption-specific exercise dates), bundle as `align(methods.finitedifferences)` fix.

**New tests:**

- `FdHullWhiteSwaptionEngineTest` — fingerprint at LOOSE tier (FD discretization noise floor, expected ~1e-6 for typical xGrid=100, tGrid=50 settings). Verify against C++ probe.

### WI-3 — FdG2SwaptionEngine (worktree C)

**New classes (in `org.jquantlib.pricingengines.swaption`):**

- `FdG2SwaptionEngine` — port of v1.42.1 `fdg2swaptionengine.{hpp,cpp}` (173 LOC total). Constructor takes a `G2` model + grid resolution parameters (xGrid, yGrid, tGrid, dampingSteps). `calculate()` builds a 2D FD scheme on (x × y × time); G2's two-factor dynamics drive the (x, y) state. Implements `Swaption.EngineImpl`.

**Java FD scaffold reuse:** verify the 2D FD machinery (`PdeSecondOrderParabolic`, possibly `DynamicPdeSecondOrderParabolic`) handles the G2 case; if a 2D-specific extension is needed, bundle as `align(methods.finitedifferences)` fix.

**New tests:**

- `FdG2SwaptionEngineTest` — fingerprint at LOOSE tier. 2D FD has lower convergence rate than 1D — expect noise floor ~1e-5. Per-test exception likely needed; document inline.

### Probe regeneration coordination

Both WI-2 and WI-3 generate new probes (`fdhullwhiteswaptionengine_probe.cpp` and `fdg2swaptionengine_probe.cpp`). Both probes call HW.tree() / G2.tree() internally for the FD discretization grid times, which goes through Brent. **WI-2 and WI-3 implementers must rebase onto WI-1's Brent fix before generating probe references** — otherwise their reference JSONs would capture pre-fix Brent values that won't match Java post-fix.

---

## 3. Worktree Topology & Layer Ordering

### Worktree topology

3 git worktrees (proven shape from Phase 2c/2d/2e/2f).

```
/Users/josemoya/eclipse-workspace/jquantlib-2g-A/  branch: phase-2g-A-brent-aligns
/Users/josemoya/eclipse-workspace/jquantlib-2g-B/  branch: phase-2g-B-fd-hullwhite
/Users/josemoya/eclipse-workspace/jquantlib-2g-C/  branch: phase-2g-C-fd-g2
```

### Worktree dependency

WI-1 lands first; WI-2 and WI-3 rebase before probe-reference generation.

```
                       ┌─────────────────────────────────┐
                       │ WI-1 Brent + alignments + tier  │
                       │ promotions (lands first)        │
                       └────────────┬────────────────────┘
                                    │
                       ┌────────────┴────────────┐
                       ▼                         ▼
        ┌──────────────────────┐    ┌──────────────────────┐
        │ WI-2 FdHullWhite     │    │ WI-3 FdG2            │
        │ (rebase, regen,      │    │ (rebase, regen,      │
        │  land)               │    │  land)               │
        └──────────────────────┘    └──────────────────────┘
```

WI-2 and WI-3 can dispatch their **port code work** (engine class + test scaffolding) in parallel with WI-1 — that work is independent of Brent's exact behavior. But probe **reference generation** has to happen post-WI-1 merge.

### Layer ordering

- **L0 — pre-flight + worktree setup.**
  - Confirm baseline `mvn -pl jquantlib test` → `Tests run: 675, Failures: 0, Errors: 0, Skipped: 22`.
  - Confirm scanner: `0 stubs`.
  - Create 3 worktrees off `main` tip `debedf9`.
  - Verify each builds clean.
  - Init `phase2g-progress.md`.

- **L1a — WI-1 first (priority + blocking dependency).**
  - L1a-1: Brent.solveImpl alignment — port C++ pre-loop init.
  - L1a-2: Run all tests; identify drift; regenerate affected probes via `generate-references.sh`; update Java reference loads.
  - L1a-3: Promote Jamshidian + G2.swaption tests from LOOSE to TIGHT (new commit); attempt NCCV tier promotion (separate commit; commit only if it passes).
  - L1a-4: VanillaSwap.setupArguments fix (separate `align(instruments)` commit per CLAUDE §4.2).
  - L1a-5: FloatingRateCoupon.fixingDays==0 fix (separate `align(cashflow)` commit per CLAUDE §4.2). Probe regeneration where needed.
  - Land fast-forward to `main`.

- **L1b — WI-2 + WI-3 parallel (after WI-1 lands).**
  - **B (FdHullWhite):**
    - L1b-1: Port FdHullWhiteSwaptionEngine using existing FD scaffold; bundle scaffold extensions as `align(methods.finitedifferences)` if needed.
    - L1b-2: Rebase onto post-WI-1 main; generate probe; cross-validate Java test at LOOSE tier.
    - Land fast-forward to `main`.
  - **C (FdG2):**
    - L1b-1: Port FdG2SwaptionEngine using existing FD scaffold + TreeLattice2D; bundle 2D scaffold extensions as `align(methods.finitedifferences)` if needed.
    - L1b-2: Rebase onto post-WI-1 main; generate probe; cross-validate Java test at LOOSE tier.
    - Land fast-forward to `main`.

- **L2 — completion doc + tag.**
  - Once all 3 worktrees have landed, write `docs/migration/phase2g-completion.md`.
  - Tag `jquantlib-phase2g-complete`, push.
  - Clean up worktrees + delete branches local + remote.
  - Update memory.

### Wallclock estimate

WI-1 is the long pole (Brent fix + 11-caller probe regeneration + tier promotions + 2 alignment fixes — estimated 4-6 hours of subagent work). WI-2 and WI-3 are roughly equal (~330 LOC C++ each + probe + test). WI-2/WI-3 port work can start in parallel with WI-1, but final commits land after WI-1 plus a rebase.

### Controller orchestration rules (Phase 2c-2f lessons baked in)

- Always run `git -C /Users/josemoya/eclipse-workspace/jquantlib merge --ff-only origin/<branch>` from the **main checkout**.
- WI-2/WI-3 implementers MUST rebase before generating probe references.
- Force-push-with-lease after rebase.
- **Subagent watchdog stalls:** controller commits in-progress state if clean and dispatches focused continuation.
- **Orphan files** in main checkout from implementers using main worktree's pre-warmed cpp/build (Phase 2d/2e/2f precedent) — diff-then-rm before merge.
- Worktree cleanup: `git worktree remove --force <path>` + `git worktree prune` + `git branch -D` + `git push origin --delete`.

---

## 4. Tolerance, Probes & Test Discipline

### Tolerance tiers (inherits Phase 1 §4.2)

| Tier | Bound | Use |
|---|---|---|
| exact | bit-identical | enum ordinals (no exact-tier work in 2g) |
| tight | `abs 1e-14 + rel 1e-12` | post-Brent-fix Jamshidian + G2.swaption (promoted from LOOSE), NCCV (re-attempted from Phase 2f rollback) |
| loose | `abs 1e-8 + rel 1e-8` | FD discretization noise floor (FdHullWhite + FdG2 swaption fingerprints) |

Per-test loose-tier exceptions need inline justification per design §4.2.

### Probes

| WI | Probe file | Captures |
|---|---|---|
| 1 | (regenerate Phase 2c/2d/2e/2f probes) | All 11 Brent-caller probe references regenerated against the post-fix Brent. Specifically: `jamshidianswaptionengine.json`, `g2.json` (swaption_integral field), `heston_nccv.json` (NCCV regen if tier promotion succeeds), `heston_broadiekaya.json` (asset-leg may tighten), CIR/HW/BK tree fingerprints, NCCS-related, ImpliedVolatilityHelper-related, Bond yield-solver, etc. |
| 2 | `fdhullwhiteswaptionengine_probe.cpp` | NPV for the 5Y×5Y ATM payer swaption fixture (mirror Phase 2e BlackSwaption probe) under FdHullWhiteSwaptionEngine on HW(0.1, 0.01); xGrid=100, tGrid=50, dampingSteps=2 (matches C++ defaults) |
| 3 | `fdg2swaptionengine_probe.cpp` | NPV for the same fixture under FdG2SwaptionEngine on G2(0.1, 0.01, 0.1, 0.005, -0.5); xGrid=50, yGrid=50, tGrid=50, dampingSteps=2 |

### Probe-driven test discipline

No test passes a value the implementer made up. If a value isn't from a C++ probe or a closed-form derivation, the test doesn't go in.

### Loose-tier expectations baked in

- **WI-1 Brent:** Jamshidian + G2.swaption expected to drop from LOOSE (`1e-8`) to TIGHT (`1e-12`) post-fix. NCCV tier promotion: target TIGHT but the Phase 2f Math.exp ULP slack may still propagate; if TIGHT fails, leave at LOOSE with updated inline justification noting the new Brent init narrowed the gap. CIR/HW/BK tree fingerprints currently at LOOSE may also tighten — opportunistically promote where they pass TIGHT.
- **WI-2 FdHullWhite:** swaption NPV expected at LOOSE tier. FD discretization noise floor is ~1e-6 for typical xGrid=100, tGrid=50 settings; need to verify the Java port's discretization scheme matches C++ exactly (operator splitting, boundary conditions, damping steps). If noise floor is even worse than LOOSE (`1e-8`), per-test loose-tier exception with inline justification.
- **WI-3 FdG2:** swaption NPV expected at LOOSE tier. 2D FD has lower convergence rate than 1D — expect noise floor ~1e-5. Per-test exception likely needed; document inline.

### Test count expectation

675 → ~677-678 (+2 to +3):
- WI-1: 0 new tests (probe regeneration + tier promotions only)
- WI-2: +1 (FdHullWhiteSwaptionEngineTest)
- WI-3: +1 (FdG2SwaptionEngineTest)
- Possibly +1 if NCCV tier promotion succeeds — but it's a tier change, not a new test method, so test count unchanged.

Skipped: 22 → 22 (no un-skip work).

### Non-loosening rule

No tier loosening to force green. If a tight-expected test fails tight, root-cause first; loosen only with documented justification.

**Special focus for WI-1:** the Brent fix is structural and may surface unexpected drift in tests we don't currently track at TIGHT. The implementer's job is to **regenerate references first**, then verify Java matches the new C++ reference at the existing tier — NOT to find post-hoc tier compromises. If a previously-TIGHT test fails TIGHT after Brent regeneration, the C++ reference itself shifted (which is fine — the test gets the new reference). If Java diverges from the new C++ reference, that's a real bug to investigate.

### Phase 2f Math.exp ULP propagation

The Phase 2f A13 finding (JVM Math.exp 1-ULP slack vs libc++) means certain Brent-driven paths cannot reach EXACT tier even with the Brent fix. WI-1 should NOT attempt EXACT-tier promotions on transcendental-heavy paths. TIGHT is the realistic ceiling.

---

## 5. Pause Triggers

Inherits Phase 1 §7.3 with Phase 2c-2f deltas. Phase 2g additions / changes:

| Trigger | Status | Condition |
|---|---|---|
| A1 | active | Scanner stub count > 1000 |
| A2 | active | Tolerance looser than `1e-8` ever needed |
| A3 | active | Cross-validation suggests v1.42.1 itself is wrong |
| A4 | **active, sharpened** | New class strictly required outside the 61 existing packages, OR substantively new infrastructure beyond what the design anticipated. For 2g: FD scaffold extensions if Java's existing 35 FD files don't cover what FdHullWhite/FdG2 need (e.g. swaption-specific boundary condition class). |
| A6 | **disabled** | End-of-layer pause — per memory `feedback_phase2a_no_a6.md` |
| A7 | active | Per-WI audit divergence from C++ |
| A8 | inactive | Vasicek-pattern ripple — N/A in 2g |
| A9 | active | Worktree-merge conflict requires manual resolution |
| A10 | inactive | XABR template-to-generics — N/A in 2g |
| A11 | inactive | G2 swaption integrator gap — closed in Phase 2f |
| A12 | inactive | Swaption.NPV() wiring — closed in Phase 2e C.0 |
| A13 | **active (carried from 2f)** | Transcendental-driven drift impossibility (Math.exp ULP slack). For 2g: WI-1's NCCV tier promotion attempt may still fail — that's expected; document and stay LOOSE. Don't escalate unless a new transcendental drift class surfaces. |
| A14 | inactive | Complex/external library infrastructure gap — N/A in 2g |
| **A15 — new for 2g** | active | WI-1 Brent fix surfaces a Java caller whose existing test was passing for the wrong reason (Brent's old behavior masked a real port bug elsewhere). If a regenerated probe + Java test fails not because of expected drift but because of a previously-hidden bug, pause to discuss before improvising. |
| **A16 — new for 2g** | active | WI-2 or WI-3 FD engine port surfaces a missing FD scaffold piece that Java doesn't have a clear place for (e.g. a damping-step coordinator that's neither a `StepCondition` nor a `FiniteDifferenceModel` extension). Pause to discuss before improvising the architecture. |

---

## 6. Decision Log (P2G-1 .. P2G-7)

- **P2G-1** Subset choice = A (Brent.solveImpl alignment + bundled small alignment fixes + FdHullWhiteSwaptionEngine + FdG2SwaptionEngine). Gaussian1D family deferred to Phase 2h. Cleanup cluster (additionalResults, addTimesTo Time-impedance, TreeLattice2D API formalization, HaltonRsg FMA docs, per-test 5e-8 SABR investigation, BroadieKaya asset-leg tolerance) deferred.
- **P2G-2** WI-1 bundles three logically-distinct fixes (Brent + VanillaSwap.setupArguments + FloatingRateCoupon.fixingDays) into one worktree's commits, but each is a separate `align(<pkg>)` commit per CLAUDE §4.2. Reasoning: shared fingerprint capacity — all three may regenerate probes; bundling avoids triple-rebase overhead.
- **P2G-3** WI-2 + WI-3 dispatch port code in parallel with WI-1, but probe reference generation happens AFTER WI-1 lands and rebase. No race with Brent fix.
- **P2G-4** Worktree topology = 3 worktrees, parallel launch from L0 (with WI-2/WI-3 final commits gated on WI-1 land). Same pattern as Phase 2c-2f.
- **P2G-5** Tier promotion strategy: WI-1 promotes Jamshidian + G2.swaption from LOOSE to TIGHT atomically with the Brent fix. NCCV tier promotion is conditional — separate commit landed only if the new Brent init makes TIGHT achievable. Other Brent-touching tests (CIR/HW/BK trees) opportunistically promoted where they pass TIGHT.
- **P2G-6** Phase 2h candidate (deferred): **transcendental library port** — pure-Java port of libc++'s exp/log/sin/cos/pow algorithms (Approach B from the brainstorming discussion — ~500-1000 LOC per primitive, ~5000 LOC total). Would unlock BroadieKaya asset-leg LOOSE-or-better, NCCV tier promotion at TIGHT, and EXACT-tier on transcendental-heavy distribution tests. Kept as a deliberate Phase 2h decision rather than a Phase 2g side-quest.
- **P2G-7** FD engine probes use grid resolution matching C++ defaults (xGrid=100/tGrid=50 for 1D HW; xGrid=50/yGrid=50/tGrid=50 for 2D G2; dampingSteps=2). If LOOSE tier `1e-8` is unreachable due to FD discretization noise floor, per-test exception with inline justification (not a hard exit-criterion blocker).

---

## 7. Exit Criteria

All must hold to tag `jquantlib-phase2g-complete`:

1. `mvn -pl jquantlib test` green: `Failures: 0, Errors: 0`.
2. Test count delta: `675 → ~677-678` (+2 to +3, with full breakdown in completion doc).
3. `Skipped: 22` (unchanged — no un-skip work in 2g).
4. Scanner: `work_in_progress: 0` (no regressions; Phase 2e milestone preserved).
5. All 3 worktrees merged fast-forward to `main` and removed; no orphan branches.
6. Every probe in `migration-harness/cpp/probes/` regenerates cleanly via `generate-references.sh` (especially the regenerated Brent-touching probes).
7. Per-test loose-tier exceptions all carry inline justification.
8. **WI-1 tier-promotion disclosure:** which tests were promoted from LOOSE to TIGHT (Jamshidian + G2.swaption expected); whether NCCV tier promotion succeeded; any opportunistic CIR/HW/BK tree promotions.
9. **WI-1 alignment-fix disclosure:** what changes the FloatingRateCoupon.fixingDays fix surfaced in existing leg-related tests (probe regeneration count, tier shifts).
10. Phase 2g completion doc written (`docs/migration/phase2g-completion.md`) covering: per-WI summary with commit hashes, probe inventory + regeneration count, deviations from plan, A13/A15/A16 firings if any, Phase 2h seed list with explicit transcendental library port option.
11. Tag `jquantlib-phase2g-complete` pushed; memory `project_jquantlib_migration.md` updated.

---

## 8. Phase 2h Seed List (carry forward)

- **Transcendental library port (Approach B)** — pure-Java port of libc++'s exp/log/sin/cos/pow algorithms (~5000 LOC). Recommended as Phase 2h's primary focus if pursued. Unlocks BroadieKaya asset-leg + NCCV tier promotion + transcendental-heavy distribution EXACT tier.
- **Gaussian1D swaption engine family** + Gaussian1D model (10 engines + model — Phase 2h alternative focus if transcendental port deferred further).
- **HestonProcess.pdf() Fokker-Planck** — only if a Java caller emerges.
- **BlackSwaptionEngine Cash/ParYieldCurve settlement** (needs CashFlows.bps(InterestRate) + Schedule.tenor()).
- **additionalResults in cap/swaption engines** (vega, optionletsPrice, optionletsVega).
- **SwaptionHelper.addTimesTo / CapHelper.addTimesTo** `Time` annotation impedance.
- **TreeLattice2D underlying value access API formalization.**
- **HaltonRsg FMA platform-conditionality documentation.**
- **Per-test 5e-8 SABR cross-check tolerance investigation.**
- **BroadieKaya asset-leg per-test 5e-3 tolerance** — blocked on Math.exp ULP (or unblocked if Phase 2h transcendental port lands).
- **Phase 3+ gap-fill packages** (`experimental/`, `models/marketmodels/`, `termstructures/credit/`, `inflation/`).
