# JQuantLib Migration — Phase 2c: Distribution Alignment + Surgical Tail

**Status:** Draft for approval
**Date:** 2026-04-25
**Author:** Jose Moya (with Claude collaboration)
**Work target:** direct commits to `main` via parallel git worktrees (new for 2c — see §4)
**C++ reference:** QuantLib **v1.42.1** (pinned, unchanged from Phase 1)
**Predecessor:** `docs/migration/phase2b-design.md` — approved 2026-04-25, completed 2026-04-25 (tag `jquantlib-phase2b-complete`, tip `e52f274`).

---

## 0. Reading this document

This is the authoritative design for **Phase 2c** — the surgical-tail phase that aligns Java's `NonCentralChiSquared*` distribution family to v1.42.1, un-stubs the two `discountBondOption` methods that have been waiting on it, fixes a 4-item subset of the smaller drift items left over from earlier phases, and time-boxes one bigger item (BlackKarasinski tree-pricing) with a clean carve fallback. CapHelper and G2 — the two largest Phase-2b carveouts — remain deferred to dedicated follow-on phases (2d, 2e) which each warrant their own focused design.

`phase2b-design.md` and (transitively) `phase2a-design.md` / `phase1-design.md` remain binding for everything not revised here. Phase 2c inherits Phase 2b's ground-truth principle, tolerance tiers, git discipline, harness architecture, scanner tooling, and quality gates without modification. This document only specifies what's new, what's in scope, and what the exit criteria look like.

The companion plan (`docs/migration/phase2c-plan.md`) will be generated from this design using the `superpowers:writing-plans` skill after this doc is approved.

---

## 1. Context

### 1.1 State at Phase 2b completion

At the tip of Phase 2b (tag `jquantlib-phase2b-complete`, commit `e52f274`), the scanner reports **2 stubs** — both Phase-2c carveouts (`CapHelper`, `G2`) — and **0** of every other kind. Test suite: **632 / 0 fail / 22 skipped**.

The Phase-2b completion doc enumerates ~12 Phase-2c seed items, which split naturally into three buckets:

| Bucket | Items | Phase-2c disposition |
|---|---|---|
| **A — heavy infrastructure** | CapHelper (needs IborLeg), G2 (needs TreeLattice2D grid + 2-factor calibration) | Carved to dedicated phases (2d, 2e). Each is comparable to a small Phase 1 in scope. |
| **B — distribution alignment** | `NonCentralChiSquaredDistribution` family aligned to v1.42.1; `CIR.discountBondOption` un-stubbed (and HestonProcess analogue if a re-check confirms its stub exists — see §3.1) | **In scope.** High-leverage: one alignment unblocks at least one and possibly two stubs, and proves out the distribution-port pattern for any later use. |
| **C — surgical tail (4-item subset)** | C1 BK tree-pricing stub (time-boxed); C2 HullWhite latent items (3 fixes); C3 SABR α-default formula; "extra" SABR test hygiene from Phase-2b reviewer findings | **In scope.** Drops the speculative items (C4 Halton multi-restart, C5 XABR plumbing, C6 LM analytic-Jacobian) — no current Java caller asks for them. |

### 1.2 Why these five items and not others

The brainstorming session (decisions P2C-1..P2C-4 in §7) settled on this scope after weighing four candidate phase shapes. Bucket A's items are too large to bundle with surgical work without diluting both — each warrants its own focused design. Bucket B is small but high-leverage: the distribution port is a single coherent v1.42.1 unit and unblocks two long-standing `discountBondOption` stubs. Bucket C's 4-item subset closes real broken behavior (BK can't price; HullWhite has known bugs flagged with `// ?????`); the C4/C5/C6 items would land speculative code paths nothing currently exercises.

### 1.3 Ground truth (inherited unchanged)

> **C++ QuantLib v1.42.1 is the source of truth. The existing Java code is our starting material, not a design to preserve. Anywhere Java diverges from v1.42.1 — signatures, implementations, constants, behavior — C++ wins.**

Pinned at commit `099987f0ca2c11c505dc4348cdb9ce01a598e1e5` (tag `v1.42.1`, dated 2026-04-16).

---

## 2. Goals and Non-goals

### 2.1 Goals

1. **Land WI-1 through WI-5** (§3) with full v1.42.1 cross-validation.
2. **Unblock `CIR.discountBondOption`** (returns hard-coded 0.0/1.0 today, waiting on the chi-squared distribution alignment since Phase 2b L3 Task 3.4). Also handle the analogous HestonProcess stub if a re-check during implementation confirms it exists — see §3.1's conditional HestonProcess scope.
3. **Either land WI-5 (BK tree-pricing) or carve it forward to Phase 2d** with a documented diagnosis if the `ShortRateTree` port trips A4 (new infrastructure outside the 61 packages).
4. **Preserve Phase 1's cross-validated v1.42.1 fidelity invariant.** No test loosens tolerance without inline justification.

**Definition of "Phase 2c done"** (full detail in §6):

1. Scanner: 2 WIP (CapHelper, G2 — Phase-2d/2e seeds, unchanged), 0 NI, 0 numerical_suspect.
2. WI-1 — distribution family aligned; both `discountBondOption` un-stubs land tight-tier green.
3. WI-2 — SABR α-default formula matches C++.
4. WI-3 — orphan SABR probe gains a Java consumer; 16-IsFixed loop hardened.
5. WI-4 — three HullWhite latent items resolved.
6. WI-5 — either BK tree-pricing un-stubbed and `BlackKarasinskiCalibrationTest` upgraded, or `phase2c-carveouts.md::WI-5-BK-tree` carries the diagnosis.
7. `(cd jquantlib && mvn test)` green; ~641-644 tests for fix path or ~638-639 for carve path; Skipped unchanged at 22.
8. `docs/migration/phase2c-completion.md` written.
9. Tag `jquantlib-phase2c-complete` pushed.

### 2.2 Non-goals

1. **No new top-level packages.** Same fence as Phases 1/2a/2b. WI-5 is the only WI with A4-risk; if it fires, the WI carves rather than the fence relaxes.
2. **CapHelper stays carved to Phase 2d.** Needs IborLeg scaffolding; its own focused phase.
3. **G2 stays carved to Phase 2e.** Needs TreeLattice2D grid + two-factor calibration.
4. **C4 (SABR Halton multi-restart), C5 (SABR XABR plumbing — `shift`, `volatilityType`, `errorAccept`, `useMaxError`, `addParams`), C6 (LM analytic-Jacobian path) stay deferred.** No current Java caller asks for them.
5. **Cross-cutting one-factor α-default review is NOT in Phase 2c.** It's a maintenance sweep without a known broken consumer; revisit later.
6. **No parallel-session execution.** Within-session parallelism via git worktrees (§4) is the new addition; cross-session orchestration remains deferred.

---

## 3. The Five Work Items

Order is per Approach 3 from the brainstorm (P2C-4): bucket B first for high leverage; bucket C tail in ascending complexity; WI-5 (BK tree-pricing) time-boxed at the end so an A4 carve doesn't block earlier wins.

### 3.1 WI-1 — `NonCentralChiSquared*` distribution alignment + CIR `discountBondOption` unstub

**Scope.** Port the three C++ chi-squared classes as a coherent v1.42.1 unit, then un-stub `CIR.discountBondOption` which depends on them.

**C++ reference.** `migration-harness/cpp/quantlib/ql/math/distributions/chisquaredistribution.{hpp,cpp}` — defines `NonCentralChiSquareDistribution` (PDF), `NonCentralCumulativeChiSquareDistribution` (CDF, Sankaran approximation switching to Patnaik/Ding series), and `InverseNonCentralCumulativeChiSquareDistribution` (inverse CDF).

**Files (port).**
- Modify or replace: `jquantlib/src/main/java/org/jquantlib/math/distributions/NonCentralChiSquaredDistribution.java` — fresh port from C++ replacing the current Java impl. Verify all internal/external callers still compile.
- Create: `jquantlib/src/main/java/org/jquantlib/math/distributions/NonCentralCumulativeChiSquaredDistribution.java`.
- Create: `jquantlib/src/main/java/org/jquantlib/math/distributions/InverseNonCentralCumulativeChiSquaredDistribution.java`.

**Files (unstub).**
- Modify: `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/CoxIngersollRoss.java` — replace `discountBondOption`'s hard-coded 0.0/1.0 returns with the C++ formula (uses `NonCentralCumulativeChiSquared`). Removes the dead `chis`/`chit`/`z`/`call` locals that the Phase-2b L3 Task 3.4 code review flagged as misleading.

**HestonProcess unstub (conditional).** The Phase-2b completion doc lists "HestonProcess.discountBondOption" as a Phase-2c follow-up, but a quick re-check during implementation should verify whether this is (a) an actual public method on `HestonProcess` with a hard-coded stub body, (b) the `varianceDistribution(...)` call inside the `NonCentralChiSquareVariance` discretization (out of scope per §2.2 — that's a Phase-2c-or-later scheme port), or (c) inherited dead code. If (a), include the unstub in this WI's scope (one extra commit); otherwise scope WI-1 to CIR alone and update `phase2b-completion.md`'s Phase-2c seed list to reflect the corrected diagnosis.

**Probes & tests.**
- Create: `migration-harness/cpp/probes/math/distributions/noncentral_chi_squared_probe.cpp` — capture CDF, PDF, and inverse CDF at ~6 fixed `(degrees, ncp, x)` tuples spanning small/medium/large ncp regimes (each branch of the Sankaran/Patnaik switch). Tight tier.
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/math/distributions/NonCentralChiSquaredDistributionTest.java` — cross-validate against the probe at tight tier.
- Extend: `migration-harness/cpp/probes/model/shortrate/coxingersollross_calibration_probe.cpp` with a `discountBondOption` fingerprint case at the same `(r0, theta, k, sigma)` parameters as the existing case; extend `CoxIngersollRossCalibrationTest` with the assertion.
- If the HestonProcess unstub branch (a) holds, extend `migration-harness/cpp/probes/processes/hestonprocess_qe_probe.cpp` (or a new probe) with that unstubbed-path fingerprint; extend `HestonProcessTest` likewise.

**Stopping criterion.** All three distributions match C++ at tight tier across the probe cases; `CIR.discountBondOption` unstub lands and passes tight cross-validation; if the HestonProcess unstub is in scope, it lands too; no previously-passing test regresses.

**Expected commits.** 3–4 (CIR-only path) or 4–5 (with HestonProcess unstub): one per distribution port + the unstub(s) (each with its own probe-case extension) + a consolidation/doc commit if needed.

### 3.2 WI-2 — SABR α-default formula

**Scope.** Replace Java's `Math.sqrt(0.2)` constant α-default in `SABRInterpolation.java::SABRCoeffHolder` with C++'s forward-aware `0.2 * Math.pow(forward, 1.0 - beta_)` for `β < 0.9999` (and `Math.sqrt(0.2)` only as the β ≥ 0.9999 fallback, matching `xabrinterpolation.hpp::SABRSpecs::defaultValues` exactly).

**Files.**
- Modify: `jquantlib/src/main/java/org/jquantlib/math/interpolations/SABRInterpolation.java` — ~5 LOC inside the SABRCoeffHolder ctor's α default branch (the `else` of the `α_ != Constants.NULL_REAL` check that Phase-2b WI-4 fixed).

**Probe.** No new probe needed. The existing `sabr_interpolation_probe.json` already records the C++ α value (`0.0489897... = 0.2·√0.06` for the existing `forward=0.06, β=0.5` case — captured during Phase-2b WI-4). The Java post-construction α will now match it.

**Tests.** No new test needed; WI-3 below adds the Java consumer that now passes for all four params.

**Stopping criterion.** `sabr_interpolation_probe.json` α value matches Java's post-construction α at tight tier (verified via WI-3's construction test, landed concurrently in worktree B).

**Expected commits.** 1.

### 3.3 WI-3 — SABR probe consumer + 16-IsFixed loop hardening

**Scope.** Two test-hygiene items from the Phase-2b WI-4 code review:

- **Orphan probe consumer.** `migration-harness/references/math/interpolations/sabr_interpolation.json` is currently an orphan reference file — generated, never asserted against. Add a Java consumer that asserts all four post-construction params (α, β, ν, ρ) match the JSON at tight tier. With WI-2 fixing α, the assertion can be tight on all four.
- **16-IsFixed loop hardening.** `SABRInterpolationTest`'s 16-combination IsFixed loop is currently a silent no-op (with `Constants.NULL_REAL` guesses, every `*IsFixed_` flag falls into `else` and stays `false` regardless of the loop variable). Either augment the test with seeded guesses for the IsFixed=true iterations, or add an explanatory comment. Cheaper choice: comment-only.

**Files.**
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationConstructionTest.java` — single test loading the probe JSON, constructing `SABRInterpolation` with `Constants.NULL_REAL` guesses, asserting `sabr.alpha()/.beta()/.nu()/.rho()` at tight tier.
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationTest.java` — add the explanatory comment in the IsFixed loop.
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/InterpolationTest.java` — same comment for `testSabrInterpolation`'s IsFixed loop.

**Stopping criterion.** New construction test green at tight tier (depends on WI-2's α-default fix landing first within worktree B); both IsFixed-loop call sites carry the explanatory comment.

**Expected commits.** 1–2.

### 3.4 WI-4 — HullWhite latent items

**Scope.** Three pre-existing HullWhite drift items flagged by the Phase-2b WI-3 code review:

- **5-arg `discountBondOption` overload missing.** C++ v1.42.1 has `discountBondOption(type, strike, maturity, bondStart, bondMaturity)`; Java only has the 4-arg form. Port the missing overload.
- **`convexityBias` formula divergence.** C++ uses `(1.0 - exp(-z*tempDeltaT)) * (futureRate + 1.0/deltaT)` with a `deltaT < QL_EPSILON ? z : ...` branch and a small-`a` Taylor fallback. Java uses `(1.0 - exp(-z)) * (futureRate + 1.0/(T-t))` and lacks the small-`a` fallback. Port the C++ form.
- **`tree(grid)` index-vs-time key.** Java uses `impl.set(grid.index(i), value)` whereas C++ uses `impl->set(grid[i], value)` — Java passes the grid index as the key while C++ passes the time value. Java source already self-flags this with a `// ?????` comment.

**C++ reference.** `migration-harness/cpp/quantlib/ql/models/shortrate/onefactormodels/hullwhite.{hpp,cpp}`.

**Files.**
- Modify: `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/HullWhite.java` — three independent fixes.
- Extend: `migration-harness/cpp/probes/model/shortrate/hullwhite_calibration_probe.cpp` with new cases for the 5-arg overload and `convexityBias` fingerprints.
- Extend: `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/HullWhiteCalibrationTest.java` — one new test method per fix.

**Stopping criterion.** Three drift items resolved; new probe cases tight-tier green; pre-existing `// ?????` comment removed from `tree(grid)`; no previously-passing test regresses.

**Expected commits.** 3 (one per item, since they're independent).

### 3.5 WI-5 — BlackKarasinski tree-pricing (time-boxed, A4 carve gate)

**Scope (investigation half).** Read `BlackKarasinski.java::tree(grid)` (currently `final ShortRateTree numericTree = null;` at the source) and identify what's needed to un-stub:

- (a) `ShortRateTree` class exists in Java but has a stubbed body → A4 NOT triggered, port the body from C++ inside the existing class.
- (b) `ShortRateTree` is missing entirely but lives in a Java package already mirrored from C++ (likely `methods.lattices`) → A4 NOT triggered, port the class.
- (c) Even with `ShortRateTree`, `Brent`-based calibration needs `Helper` infrastructure that's also stubbed and that recursively pulls in further unported lattice classes → recursive carve risk; A4 fires.

**Time-box.** ~1 session of investigation + port attempt. If (a) or (b), land it as `stub(model.shortrate.onefactor): port BlackKarasinski tree-pricing`. If (c) or A4 trigger, write `phase2c-carveouts.md::WI-5-BK-tree` with the diagnosis and ship Phase 2c without it.

**C++ reference.** `migration-harness/cpp/quantlib/ql/models/shortrate/onefactormodels/blackkarasinski.cpp` (the `dynamics()` method that constructs the tree via `TermStructureFittingParameter` and `Brent` root-finder per grid step).

**Files (fix path).**
- Modify: `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/BlackKarasinski.java` — un-stub `tree(grid)`, remove the `numericTree = null` placeholder.
- Modify or create: `jquantlib/src/main/java/org/jquantlib/methods/lattices/ShortRateTree.java` (or wherever the Java port lives).
- Create: `migration-harness/cpp/probes/model/shortrate/blackkarasinski_tree_probe.cpp` — capture a `discountBond` fingerprint at the same parameters as the existing `BlackKarasinskiCalibrationTest`'s reflection test, but now exercising the tree pricing path.
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/BlackKarasinskiCalibrationTest.java` — upgrade from reflection-only to `discountBond` fingerprint at tight tier.

**Files (carve path).**
- Create: `docs/migration/phase2c-carveouts.md` (new file) with `## WI-5-BK-tree` section: failure mode discovered, infrastructure that would unblock Phase-2d port, hypothesis on whether it fits inside the 61-package fence.
- Update: `phase2b-completion.md::Phase 2c seed list` BK tree-pricing entry with re-carve note pointing forward.

**Stopping criterion.** Either (a) BK tree-pricing un-stubbed and `BlackKarasinskiCalibrationTest` upgraded to fingerprint at tight tier; or (b) `phase2c-carveouts.md::WI-5-BK-tree` exists with specific diagnosis and the reflection test stays as-is.

**Expected commits.** 0–4 depending on outcome.

---

## 4. Workflow and parallelism

### 4.1 Workflow (inherited from Phase 2b unchanged, except for parallelism)

- **Direct commits to `main`**, fast-forward, no PRs, no migration branch.
- **One stub (or cycle-batch) = one commit**, signed off with `-s`, no `Co-authored-by: Claude` trailer, unsigned.
- **Every commit compiles and passes `(cd jquantlib && mvn test)` *before* it lands.**
- **Commit message form:** `<kind>(<pkg>): <verb> ...` with `kind ∈ {stub, align, infra, chore, docs, test}`.
- **Divergences found mid-stub** → separate preceding `align(<pkg>): ...` commit.

### 4.2 Parallelism via git worktrees (P2C-5 — new for Phase 2c)

Phase 2c switches from Phase 2b's strictly-sequential subagent pipeline to **git-worktree-based parallel execution** for independent WIs. This is the natural application of Jose's standing parallelism preference (`feedback_parallelism.md` plus the 2026-04-25 reminder mid-2c-brainstorm) to multi-WI phases.

**Worktree topology:**

| Worktree | WIs | Touches | A→D parallelism |
|---|---|---|---|
| A | WI-1 | `math/distributions/NonCentralChiSquared*`, `CIR.discountBondOption`, `HestonProcess` unstub, related probes + tests | Independent of B, C, D |
| B | WI-2 → WI-3 | `SABRInterpolation.java::SABRCoeffHolder` α-default; then new `SABRInterpolationConstructionTest` + comment on existing SABR tests | Internally serialized (WI-3 depends on WI-2's α fix); independent of A, C, D |
| C | WI-4 | `HullWhite.java` (3 fixes), HullWhite probe + test extensions | Independent of A, B, D |
| D | WI-5 | `BlackKarasinski.java` + `ShortRateTree` (or carve) | Independent of A, B, C |

These touch disjoint files. File-topology check at design time: WI-1 touches CIR + HestonProcess; WI-2/3 touches SABR; WI-4 touches HullWhite; WI-5 touches BlackKarasinski + ShortRateTree. No file in any WI's modify set appears in another WI's modify set.

**Execution model.**

1. After L0 pre-flight, set up worktrees A/B/C/D via `superpowers:using-git-worktrees`. Each worktree branches from the current `main` tip.
2. Dispatch 4 implementer subagents in parallel — one per worktree, each owning its WI's full task list.
3. Per-task spec-reviewer + code-quality-reviewer remain sequential (skill rule: code quality only after spec compliance ✅), but spec-reviewer for one worktree's commit can run concurrently with implementer/reviewer for another worktree.
4. Each worktree fast-forwards its commits to `main` as soon as its full-suite passes; merge cadence is async.
5. Controller (the Claude session) coordinates: rebases worktrees onto latest `main` between landings to keep them up-to-date, watches for any cross-worktree file conflict, updates `phase2c-progress.md` between landings.
6. Worktree D (WI-5, BK tree-pricing) carries the time-box decision; if it carves, the carve doc lands cleanly without blocking the other three.

**Failure mode.** If two worktrees independently produce commits that conflict on a shared file (unexpected — file topology was checked at design time), the later-landing worktree rebases against the latest `main` and re-runs its full suite. If the rebase resolution requires more than a mechanical merge (e.g., new shared probe schema, refactored test class), that worktree's WI pauses for human review — A9 trigger fires (§5).

### 4.3 Quality gates (inherited from Phase 2b unchanged)

- **TDD per stub.** Probe before port for any cross-validated change. Java test that fails first; port; green; commit.
- **Tolerance tiers** (inherited from Phase-1 design §4.2):
  - **Exact** — bit-identical doubles, integer/date/enum equality.
  - **Tight** — `|a − b| < 1e-14 + 1e-12·|cpp|`. Default for closed-form formulas and bit-stable kernels.
  - **Loose** — `|a − b| < 1e-8 + 1e-8·|cpp|`. For Monte Carlo, root-finding, PDE solvers, iterative LM with cumulative FMA drift.
- **Per-test loosening** allowed only with inline justification.
- **v1.42.1 is ground truth.**

---

## 5. Pause triggers

Phase 2c inherits A1–A8 from Phase 2b §5. Three changes:

| ID | Status in Phase 2c | Note |
|---|---|---|
| A1 | active, unchanged | Will not fire (scanner at 2). |
| A2 | active, unchanged | Tolerance > `1e-8` → pause. |
| A3 | active, unchanged | C++ v1.42.1 itself appears wrong → pause. |
| A4 | **redirected — WI-5 (BK tree-pricing) carve gate** | If `ShortRateTree` port needs new infrastructure outside the 61 packages, A4 fires inside WI-5 → carve to Phase 2d alongside CapHelper. No mid-WI ask; the time-box decides. |
| A5 | not used (Phase-1 reservation) | — |
| A6 | **disabled** | No end-of-layer ack pause. Phase 2c runs end-to-end across all worktrees. |
| A7 | inactive — N/A to scope | Phase-2a-only audit-divergence trigger. |
| A8 | inactive — N/A to scope | Phase-2b-only Vasicek-pattern alignment risk. The one-factor family sweep is closed. |
| **A9** | **new for Phase 2c (P2C-6)** | **Worktree-merge conflict.** If two worktrees produce non-trivially-conflicting changes to a shared file (rebase requires more than mechanical merge), the later-landing one pauses for human review rather than the controller resolving the merge. Expected to never fire (file topology checked at design time) but documented for completeness. |

---

## 6. Exit criteria

| # | Criterion |
|---|---|
| 1 | Scanner on `main` reports `work_in_progress: 2` (CapHelper, G2 — Phase-2d/2e seeds, unchanged from 2b tip), `not_implemented: 0`, `numerical_suspect: 0`. |
| 2 | **WI-1.** `NonCentralCumulativeChiSquaredDistribution`, `NonCentralChiSquaredDistribution`, `InverseNonCentralCumulativeChiSquaredDistribution` aligned to v1.42.1; `noncentral_chi_squared_probe` passes tight-tier; `CIR.discountBondOption` un-stubbed and tight-tier green; HestonProcess analogue un-stubbed if §3.1's re-check confirms its stub exists; `phase2b-completion.md` Phase-2c seed list entries marked resolved (with corrected diagnosis if HestonProcess turned out not to need an unstub). |
| 3 | **WI-2.** `SABRInterpolation::SABRCoeffHolder` α-default formula uses `0.2 * Math.pow(forward, 1.0 - beta_)` for β < 0.9999, matching C++ `xabrinterpolation.hpp::SABRSpecs::defaultValues`; `sabr_interpolation.json` α reference matches Java post-construction at tight tier (verified by WI-3's construction test). |
| 4 | **WI-3.** `SABRInterpolationConstructionTest` exists and asserts all four post-construction params (α, β, ν, ρ) at tight tier against the probe JSON. 16-IsFixed loop in `SABRInterpolationTest` (and `InterpolationTest::testSabrInterpolation`) carries an explanatory comment OR is augmented with seeded guesses. |
| 5 | **WI-4.** All three HullWhite latent items resolved: 5-arg `discountBondOption` overload ported, `convexityBias` formula matches C++, `tree(grid)` `impl.set` key uses time value not grid index. New probe cases tight-tier green; pre-existing `// ?????` comment removed. |
| 6 | **WI-5.** Either (a) BlackKarasinski tree-pricing un-stubbed and `BlackKarasinskiCalibrationTest` upgraded from reflection-only to a `discountBond`-style fingerprint at tight tier; or (b) `phase2c-carveouts.md` exists with a `WI-5-BK-tree` entry citing the specific `ShortRateTree`-related blocker, and the reflection test stays as-is. |
| 7 | `(cd jquantlib && mvn test)` green. Test count ≥ 632 + ~6 (≥ 638), with the exact delta depending on WI-5's outcome. Skipped count unchanged at 22 (WI-1 unstubs replace dead code, not `@Ignore`s; WI-5 carve doesn't re-skip anything). |
| 8 | `docs/migration/phase2c-completion.md` written. |
| 9 | Tag `jquantlib-phase2c-complete` at the final commit, pushed to `origin`. |

### Test count expectations

| WI | Δ tests | Notes |
|---|---|---|
| WI-1 | +3 to +5 | 3 distribution-port tests (CDF + PDF + inverse CDF) + 1 CIR unstub fingerprint + maybe 1 HestonProcess unstub fingerprint (conditional on §3.1 re-check) |
| WI-2 | 0 | Reuses existing SABR probe; no new test (Java consumer is WI-3) |
| WI-3 | +1 | Construction test asserting all four α/β/ν/ρ |
| WI-4 | +3 | One per fix |
| WI-5 (fix) | +1 to +2 | Upgraded `BlackKarasinskiCalibrationTest` (reflection → fingerprint); maybe 1 additional structural test |
| WI-5 (carve) | 0 | Reflection test stays; carveout doc only |

Estimated final state: **640–644 tests** for fix path, **637–640 tests** for carve path (BK tree-pricing carve and/or HestonProcess unstub turning out to be N/A reduce the WI-1 / WI-5 contributions by 1 test each). Skipped: **22** (unchanged).

### Definition of "Phase 2c done"

All nine criteria above met simultaneously. Carve outcomes are valid for **WI-5 only** — the other four WIs must land their fixes (no carve-on-difficulty, since the scope is well-bounded).

---

## 7. Decision log

### P2C-1 — Phase shape: surgical only (B + C subset); CapHelper and G2 split into 2d/2e

**Decision.** Phase 2c covers Bucket B (distribution alignment + 2 unstubs) and a 4-item subset of Bucket C. Bucket A (CapHelper, G2) is split into dedicated phases (2d, 2e).

**Reason.** CapHelper needs IborLeg and G2 needs TreeLattice2D + 2-factor calibration — each is comparable to a small Phase 1 in scope. Bundling them with surgical drift work would dilute both. The Phase-2b cadence (single-session, ~5-8 commits, predictable exit) was valuable; preserving it for 2c keeps the rhythm.

**Alternative considered.** "Bundle A+B+C" (~3-5× Phase 2b scope; would need to retire A4 trigger). "Infra-first" (A in 2c, B+C in 2d) — defers the unblocking effects of B and the user-visible drift fixes of C. Most-granular split (B, C, A1, A2 each its own phase) — too much overhead.

### P2C-2 — Bucket C scope: 4-item subset

**Decision.** Phase 2c includes C1 (BK tree-pricing, time-boxed), C2 (HullWhite latent items), C3 (SABR α-default), and the "extra" SABR test hygiene from Phase-2b reviewer findings. Drops C4 (Halton multi-restart), C5 (XABR plumbing — `shift`/`volatilityType`/`errorAccept`/`useMaxError`/`addParams`), C6 (LM analytic-Jacobian flag).

**Reason.** C1 and C2 unblock real broken behavior (BK can't price; HullWhite has known `// ?????` bugs). C3 is 5 LOC — trivially worth doing. The "extra" closes the orphan-probe finding from Phase-2b WI-4 review at low cost. C4/C5/C6 are speculative — no current Java caller asks for them (P2B-4 already deferred LM analytic-Jac on the same reasoning).

**Alternative considered.** "All 7+" (drags speculative code paths nothing exercises); "C1+C2 only" (defers cheap polish unnecessarily).

### P2C-3 — Bucket B scope: full distribution port (CDF + PDF + inverse CDF)

**Decision.** Port all three C++ chi-squared distributions as a coherent v1.42.1 unit, then un-stub both `CIR.discountBondOption` and `HestonProcess.discountBondOption`.

**Reason.** C++ defines the three together; the math is shared. Porting only the CDF would leave a stale Java implementation lurking that someone trips over later. Unstubbing both `discountBondOption` methods is the high-leverage payoff that justifies the whole bucket.

**Alternative considered.** "CDF only" (surgical but leaves PDF + inverse CDF Java impls drifting); "Parallel sibling class" (additive but leaves the original drift; future maintainers see two classes).

### P2C-4 — Ordering: B first, C tail in ascending complexity, WI-5 time-boxed

**Decision.** L0 pre-flight → L1 WI-1 (B) → L2 WI-2 (SABR α-default warm-up) → L3 WI-3 (test hygiene) → L4 WI-4 (HullWhite latent) → L5 WI-5 (BK tree-pricing, time-boxed) → L6 completion + tag. WI-5 has explicit fix-or-carve outcome; A4 fires automatically inside it if `ShortRateTree` port needs new infrastructure outside the 61 packages.

**Reason.** B first builds momentum on high-leverage well-bounded work. C tail in ascending complexity follows the Phase-2b shape that worked. Time-boxing WI-5 with a clean carve fallback prevents a single risky item from blocking the rest of the phase (mirrors Phase-2b's WI-4 SABR fix-or-carve handling).

**Alternative considered.** "Risk-first" (WI-5 at L1) — saps momentum if the answer is "carve". "Strict ascending" without time-box — implicitly assumes WI-5 fits.

### P2C-5 — Parallelism: git-worktree-based execution

**Decision.** Phase 2c uses 4 git worktrees (A=WI-1, B=WI-2→WI-3, C=WI-4, D=WI-5) for parallel implementer execution. Per-task reviewer pipeline stays sequential (skill rule); cross-worktree work is async.

**Reason.** Standing parallelism preference (`feedback_parallelism.md`) reiterated mid-Phase-2c brainstorm: "remember to parallelize everywhere we can to speed up the process". Phase 2b's WIs serialized on the Vasicek-pattern alignment risk; Phase 2c's WIs touch disjoint files (verified at design time) and are good candidates for worktree-based parallelism. Expected speedup: 3-4× implementer wall time vs Phase-2b's strict serialization.

**Alternative considered.** Strict serialization (Phase-2b verbatim) — wastes the parallelism opportunity. Multi-session orchestration (multiple Claude sessions in tabs) — deferred per `feedback_parallelism.md` standing preference.

### P2C-6 — New A9 trigger: worktree-merge conflict

**Decision.** Add A9 trigger: if two worktrees produce non-trivially-conflicting changes to a shared file (rebase requires more than mechanical merge), the later-landing worktree pauses for human review.

**Reason.** File topology was checked at design time and no shared file is in scope across worktrees, so A9 should never fire in practice. Documenting the trigger covers the "unknown unknowns" — e.g., if a refactor mid-WI surfaces a shared probe-schema change that wasn't anticipated, A9 catches it cleanly rather than silent merge weirdness.

**Alternative considered.** Fold into A2 (tolerance) or A4 (new class) — neither matches the failure mode. Skip the trigger entirely — leaves the worktree topology assumption implicit; A9 makes the assumption explicit and audit-able.
