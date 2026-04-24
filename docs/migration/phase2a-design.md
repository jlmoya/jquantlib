# JQuantLib Migration — Phase 2a: Finish the Tail of Phase 1

**Status:** Draft for approval
**Date:** 2026-04-24
**Author:** Jose Moya (with Claude collaboration)
**Branch:** `migration/phase2a-finish-tail` (off `main`)
**C++ reference:** QuantLib **v1.42.1** (pinned, unchanged from Phase 1)
**Predecessor:** `docs/migration/phase1-design.md` — approved 2026-04-22, completed 2026-04-24 (tag `jquantlib-phase1-complete`).

---

## 0. Reading this document

This is the authoritative design for **Phase 2a** — the first sub-phase of Phase 2. Phase 2a's mandate is narrow: finish the five items Phase 1 deferred inside the existing 61 packages. Anything that requires a new top-level package or substantially new infrastructure stays out of scope and falls into Phase 2b.

Phase 1's design doc (`phase1-design.md`) remains binding for everything not revised here. Phase 2a inherits Phase 1's ground-truth principle, tolerance tiers, git discipline, harness architecture, scanner tooling, and quality gates without modification. This document only specifies what's new, what's in scope, and what the exit criteria look like.

The companion plan (`docs/migration/phase2a-plan.md`) will be generated from this design using the `superpowers:writing-plans` skill after this doc is approved.

## 1. Context

### 1.1 State at Phase 1 completion

At the tip of Phase 1 (tag `jquantlib-phase1-complete`, commit `04f8495`), the scanner reports **63 stubs** across three kinds:

| Kind | Count | Disposition |
|---|---|---|
| `work_in_progress` | 6 | 5 in Phase 2a scope, 1 (G2) deferred to 2b. Details below. |
| `not_implemented` | 1 | `TreeLattice2D.grid` — faithful port of C++ `QL_FAIL("not implemented")`; scanner false-positive. |
| `numerical_suspect` | 56 | Pre-existing `TODO: code review :: please verify against QL/C++ code` markers. Never triaged during Phase 1 (out of scope). |

The seven non-suspect stubs map as follows:

| Stub | Phase 2a work item |
|---|---|
| `QL.validateExperimentalMode` (line 391) | WI-5 |
| `LevenbergMarquardt` × 2 (lines 43, 51) | WI-2 |
| `TreeLattice2D.grid` (line 73, `not_implemented`) | WI-1 |
| `CapHelper` (line 84) | **Deferred to Phase 2b** |
| `G2.someMethod` (line 126) | **Deferred to Phase 2b** |
| `HestonProcess` QE branch (line 282) | WI-3 |

### 1.2 Why these five items and not others

Phase 1's design (§2.2) ring-fenced work to the 61 existing packages and avoided introducing new dependencies. Five items fit that fence:

- `TreeLattice2D.grid` — already matches C++; only the scanner needs updating.
- MINPACK / LevenbergMarquardt — the MINPACK port lives at `ql/math/optimization/lmdif.{hpp,cpp}` within the same package as LevenbergMarquardt itself. No new Java package required.
- HestonProcess QE discretization — one algorithmic branch inside an existing class.
- `QL.validateExperimentalMode` — deletion sweep, no new code.
- Auditing the 56 `numerical_suspect` markers — reads and possible alignments within existing packages.

Two items explicitly do **not** fit and are deferred to Phase 2b:

- `CapHelper` — requires `IborLeg` infrastructure that isn't present in JQuantLib yet. Building it would trip design §7.3 pause trigger A4 (new class outside 61 packages).
- `G2` two-factor short-rate model — requires a functional `TreeLattice2D` grid implementation plus two-factor calibration scaffolding, both substantial undertakings on their own.

### 1.3 Ground truth (inherited unchanged)

> **C++ QuantLib v1.42.1 is the source of truth. The existing Java code is our starting material, not a design to preserve. Anywhere Java diverges from v1.42.1 — signatures, implementations, constants, behavior — C++ wins.**

Pinned at commit `099987f0ca2c11c505dc4348cdb9ce01a598e1e5` (tag `v1.42.1`, dated 2026-04-16).

## 2. Goals and Non-goals

### 2.1 Goals

1. **Fill the five deferred items** (WI-1 through WI-5, §3).
2. **Audit every `numerical_suspect` marker** (WI-4) under tiered triage with the hard rule that any divergence, however minor, earns a probe + cross-validated test.
3. **Un-skip every test** that was skipped in Phase 1 due to LevenbergMarquardt unavailability. Each un-skip is a separate commit; failures are treated as Tier-2 audit items.
4. **Preserve Phase 1's cross-validated v1.42.1 fidelity invariant** throughout. No test loosens tolerance without inline justification.

**Definition of "Phase 2a done"** (full detail in §6):

1. Scanner at tip of `migration/phase2a-finish-tail` reports: 2 `work_in_progress` (CapHelper, G2), 0 `not_implemented`, 0 `numerical_suspect`.
2. `docs/migration/stub-allowlist.json` exists and contains the `TreeLattice2D.grid` entry.
3. `docs/migration/phase2a-audit.md` has one line per 56 markers with tier, outcome, and commit reference.
4. `QL.validateExperimentalMode` and all call sites deleted; `grep -r validateExperimentalMode jquantlib/src/main/java` returns empty.
5. All formerly-LM-skipped tests un-skipped and green.
6. `(cd jquantlib && mvn test)` fully green with zero skips attributable to 2a-scope unavailability.
7. `docs/migration/phase2a-completion.md` written.
8. Tag `jquantlib-phase2a-complete` at final commit, pushed.

### 2.2 Non-goals

1. **No new top-level packages.** Same fence as Phase 1 §2.2. Enforced by pause trigger A4.
2. **CapHelper and G2 are out of scope for 2a.** They become the seed items for the Phase 2b scope discussion.
3. **No broadening beyond the 56 `numerical_suspect` markers.** If WI-4 surfaces additional drift (e.g., non-marked methods that look suspect), log them in the audit file for Phase 2b but do not expand 2a's scope.
4. **No parallel-session execution.** The user's current preference is single session for simplicity (discussed during brainstorming). Parallelism, worktrees, and orchestration remain deferred topics.

## 3. The Five Work Items

Each work item lists its scope, deliverables, stopping criterion, and expected commit count. Execution order is **WI-1 → WI-2 → WI-3 → WI-4 → WI-5**, rationale in §5.

### 3.1 WI-1 — Scanner tidy (TreeLattice2D false-positive)

**Scope.** Add an allowlist mechanism to `tools/stub-scanner/scan_stubs.py` and register `pricingengines.lattice.TreeLattice2D#grid`. The Java method already mirrors the C++ `QL_FAIL("not implemented")` contract exactly; it is a faithful port, not a stub. Zero production-code change.

**Deliverables.**

- `docs/migration/stub-allowlist.json` — new committed file. Schema: array of entries with fields `stub_id`, `kind`, `reason`, `cpp_counterpart`. Only entries whose reason is *"Java stub is a faithful port of a C++ `QL_FAIL` / `throw` that will never be implemented"* are allowed. Anything else goes in `carveouts.md` instead.
- `scan_stubs.py` updated to read the allowlist and skip matching entries.
- Regenerated `stub-inventory.json` + `worklist.md`.

**Stopping criterion.** Scanner no longer surfaces TreeLattice2D; `not_implemented` count drops from 1 to 0.

**Expected commits.** 1.

### 3.2 WI-2 — MINPACK `lmdif` port + LevenbergMarquardt unblock

**Scope.** Port the MINPACK namespace from `ql/math/optimization/lmdif.{hpp,cpp}` (~2000 LOC) into Java, then fill the two `LevenbergMarquardt` stubs that depend on it.

**Java package layout** (Option A — mirror C++):

- `org.jquantlib.math.optimization.MINPACK` (new class, package-level, mirrors the C++ `QuantLib::MINPACK` namespace).
  - Public static: `lmdif`, `qrfac`, `qrsolv`.
  - Nested `@FunctionalInterface LmdifCostFunction`.
  - Private static: `lmpar`, `enorm`, `fdjac2` (the helpers that are file-local in C++ `lmdif.cpp`).
- `org.jquantlib.math.optimization.LevenbergMarquardt` (existing class, stubs filled).
  - `minimize(...)` delegates into `MINPACK.lmdif(...)` exactly as C++ does.

Alternatives considered: (B) subpackage with one class per helper — rejected; fragments what C++ keeps coherent and imposes class-per-function Java-ism. (C) nested class inside `LevenbergMarquardt` — rejected; C++ exposes the `MINPACK` namespace publicly and we should mirror that.

**Translation style — C-array parity.** MINPACK is deliberately C-style: raw `Real*` params, implicit lengths, workspace arrays (`wa1`..`wa4`), Fortran-heritage index arithmetic, goto-style control flow. The Java port keeps all of that — `double[]` arrays, no `Array`/`Matrix` wrappers, no abstraction, line-by-line mapping. Concessions:

- `std::function<...>` → `@FunctionalInterface LmdifCostFunction` (single-method).
- `int* info`, `int* nfev` out-params → `int[]` one-element arrays (Phase 1 convention).
- Pointer arithmetic on sub-arrays (`x + k`) → pass-through of base array + offset parameter.
- C++ goto-style control flow → labeled `break` / `continue` / flagged early returns, preserving exact loop structure. No refactoring into "nicer Java".

**Probes** (`migration-harness/cpp/probes/math/optimization/`):

| # | Probe | Tolerance | Purpose |
|---|---|---|---|
| 1 | `minpack_qrfac_*` | exact | 3×3 and 4×2 fixed matrices; deterministic linear algebra, bit-exact. |
| 2 | `minpack_qrsolv_*` | exact | Upper-triangular solve on fixed inputs, bit-exact. |
| 3 | `lm_linear_fit` | tight `1e-12` | `y = a·x + b` on perfect 5-point data; checks params + `info` + `nfev`. |
| 4 | `lm_quadratic_fit` | tight | `y = a·x²` on perfect data; single-param convergence. |
| 5 | `lm_rosenbrock` | tight on `info`/`nfev`, loose `1e-8` on params | Ill-conditioned 2-param problem. |
| 6 | `lm_maxfev_earlystop` | exact on `info`, tight on params | `maxfev=10` forced early-stop; verifies returned params are the 10th iterate. |

Probes 1–2 exercise the publicly-exported MINPACK helpers in isolation — if those drift, the fault is structural and blocks everything downstream. Write and cross-validate them **first**, before drivers.

**Un-skip strategy.** After the port lands, each formerly-skipped LM-dependent test is un-skipped in its own `align(<pkg>): un-skip <TestName> after LevenbergMarquardt port` commit. Un-skipping is a separate logical change from porting and must not be bundled. If an un-skipped test fails, treat as a Tier-2 audit item (WI-4 methodology §4): probe the divergence, align Java, land the fix, then un-skip.

**Stopping criterion.** All 6 probes green; both LM stubs filled; every formerly-LM-skipped test un-skipped and green.

**Expected commits.** 4–8, cycle-batched. If a MINPACK helper is <400 LOC and independently compilable, it gets its own commit. Do not port 2000 LOC in one commit.

### 3.3 WI-3 — HestonProcess QUADRATIC_EXPONENTIAL branch

**Scope.** Port the QE discretization branch of `HestonProcess::evolve()` from v1.42.1. Approximately 100-line algorithmic block that Phase 1 stubbed.

**Probe.** `migration-harness/cpp/probes/processes/hestonprocess_qe_probe.cpp` — fixed-seed evolution traces at representative parameter regimes: high vol-of-vol, low correlation, high correlation. Each trace covers ≥20 time steps so any drift accumulates.

**Stopping criterion.** QE-branch test green at tight `1e-12` tolerance; other HestonProcess tests unaffected. If FP ordering forces `1e-8` fallback, inline justification per phase1-design §4.2.

**Expected commits.** 2–3 (probe, port, any alignments surfaced).

### 3.4 WI-4 — Audit 56 `numerical_suspect` markers

Full methodology in §4. Summary:

**Tier-1** (visual diff, minutes): nine-point checklist applied to each method beside its C++ counterpart. Clean only if every point passes with zero doubt.

**Tier-2** (full probe + cross-validated test): triggered by *any* Tier-1 doubt. **Hard rule — wherever a divergence is found, it gets the full probe + test treatment, no exceptions.**

**Outcomes per marker.** Clean (remove stale TODO), aligned (`align(<pkg>): …` commit with probe + test), or carved (new entry in `phase2a-carveouts.md` with pointer to 2b). Carving is only permitted if completing the work would trip A4 (new class outside 61 packages). No "too tricky" carves.

**Log.** `docs/migration/phase2a-audit.md` — one line per marker in scanner order.

**Stopping criterion.** Scanner reports 0 `numerical_suspect`.

**Expected commits.** Many, checkpoint every ~10 markers. Mix depends on Tier distribution; Tier-1 clean resolutions can be batched by package (e.g., one commit drops stale markers from `math/interpolations/`).

### 3.5 WI-5 — `QL.validateExperimentalMode` deletion sweep

**Scope.** The EXPERIMENTAL-mode gate method is a JQuantLib-ism with no C++ counterpart. Phase 1 deleted call sites opportunistically (many came out during stub fills); 2a finishes the job.

**Deliverables.** Every remaining call to `QL.validateExperimentalMode()` removed; the method deleted from `org.jquantlib.QL`. One commit, `chore(ql): delete validateExperimentalMode and all call sites`.

**Stopping criterion.** `grep -r validateExperimentalMode jquantlib/src/main/java` returns empty; `(cd jquantlib && mvn test)` green.

**Expected commits.** 1.

## 4. Numerical-Suspect Audit Methodology

### 4.1 Tier-1 visual-diff checklist

For each `TODO: code review :: please verify against QL/C++ code` marker, open the Java method beside its v1.42.1 C++ counterpart and confirm all nine points. The method is Tier-1 clean only if every point passes with **zero doubt**:

1. **Constants.** Every magic number identical: π, `sqrt(2π)`, convergence ε, series-truncation bounds, QL-internal defaults.
2. **Loop bounds.** `<` vs `<=`, inclusive/exclusive endpoints, ascending vs descending direction, same starting index.
3. **Branch structure.** Same if/else chains, same early-return conditions, same guard ordering.
4. **Arithmetic expression shape.** Same parenthesisation and associativity. `(a*b)+c` vs `a*(b+c)` changes FP rounding; we require exact structural parity.
5. **Helper calls.** Java invokes the same algorithm / same sub-function as C++. No silent substitution of a different `std::` equivalent.
6. **Pass-by-reference semantics.** C++ `Type&` / `Size&` out-params mirrored via Java `Type[]` / `int[]` one-element arrays (Phase 1 convention). No value-copy where C++ expects mutation.
7. **Preconditions.** Every `QL_REQUIRE` ported verbatim (same message, same condition direction). Missing `QL_REQUIRE` on the Java side counts as a divergence.
8. **Default arguments.** C++ default params explicitly represented in Java overloads with identical defaults.
9. **Algorithm identity.** When C++ cites a reference ("NR §10.3", "Press et al."), confirm the Java matches the same recipe; differing citations or none where C++ has one is a Tier-2 trigger.

### 4.2 Tier-2 triggers

Any of the following moves the marker out of Tier-1:

- A constant that isn't obviously correct from first principles.
- A reordered FP expression, however semantically equivalent.
- A branch or precondition difference, however minor.
- A helper substitution.
- Gut feeling: "looks close but I'm not 100% sure" → Tier-2. The "no hurry, bulletproof" rule dominates.

### 4.3 Tier-2 procedure

Identical to Phase 1's per-stub workflow (phase1-design §4.1):

1. Write a focused C++ probe covering the suspect branch/expression. Probes are scoped to the concern, not the whole method.
2. Capture reference JSON via `migration-harness/verify-harness.sh`.
3. Write a Java test that loads the reference and asserts under the appropriate tolerance tier.
4. Outcome:
   - Test passes → marker cleared and removed. Commit: `align(<pkg>): drop stale numerical-suspect marker on <method> — probe confirms match`.
   - Test fails → fix the Java to match C++. Commit: `align(<pkg>): match v1.42.1 <method>` with probe + test in the same commit.

### 4.4 Audit-log schema

`docs/migration/phase2a-audit.md` — append-only, one line per stub in scanner order:

```
- [x] <stub-id> · T1 · clean — TODO removed in <commit-sha>
- [x] <stub-id> · T2 · aligned — probe <probe-path>; fix in <commit-sha>
- [x] <stub-id> · T2 · clean — probe <probe-path>; TODO removed in <commit-sha>
- [ ] <stub-id> · T2 · carved — see phase2a-carveouts.md#<anchor>
```

The audit doc is the single source of truth for what's been looked at. When every line is checked, WI-4 is done.

### 4.5 Carving rules during WI-4

A Tier-2 divergence gets carved to Phase 2b **only if** filling it requires a new class outside the 61 existing packages (A4 trigger). Otherwise it is in-scope for 2a and gets aligned. No "too complex" or "too slow" carves — bulletproof means we fix it.

## 5. Layer Structure and Execution Order

### 5.1 Layers

Branch: `migration/phase2a-finish-tail` off `main`. Each layer ends with pause-trigger A6 (layer-end summary; wait for user acknowledgment before next layer).

| Layer | Work item | Expected commits |
|---|---|---|
| L0 | Pre-flight: branch off `main`, confirm baseline green, snapshot current scanner state | 0 |
| L1 | WI-1 scanner tidy | 1 |
| L2 | WI-2 MINPACK + LM port + un-skip sweep | 4–8 (cycle-batched) |
| L3 | WI-3 HestonProcess QE | 2–3 |
| L4 | WI-4 audit sweep (56 markers) | Many; checkpoint every ~10 |
| L5 | WI-5 `QL.validateExperimentalMode` deletion | 1 |
| L6 | Completion doc + tag | 1 commit (completion doc) + tag |

### 5.2 Ordering rationale

Execution order L1 → L2 → L3 → L4 → L5 was chosen for three reasons:

1. **WI-1 first (cheap warmup).** Single-file infrastructure change, confirms branch and tooling are working before any porting starts.
2. **WI-2 before WI-4 (regression coverage).** Unblocking LM un-skips tests across three files — `SABRInterpolationTest`, `InterpolationTest`, `OptimizerTest` — that currently pass trivially by being skipped. Doing this before the audit sweep means any regression WI-4 might introduce is caught immediately by the un-skipped suite.
3. **WI-2 before WI-3 (cognitive load).** MINPACK is the largest port of 2a (~2000 LOC) and benefits most from fresh focus. WI-3 is a small algorithmic branch that can run on lower energy.
4. **WI-5 last (final housekeeping).** Deleting `validateExperimentalMode` must not break anything else; doing it after all functional work is green makes root-cause analysis trivial if something does break.

## 6. Done Criteria, Handoff to Phase 2b, and Risks

### 6.1 Phase 2a done criteria

Exit state required before tagging `jquantlib-phase2a-complete`:

1. Scanner at tip of `migration/phase2a-finish-tail` reports:
   - `work_in_progress`: 2 (CapHelper, G2 — deferred to 2b).
   - `not_implemented`: 0.
   - `numerical_suspect`: 0.
2. `docs/migration/stub-allowlist.json` committed with the TreeLattice2D entry.
3. `docs/migration/phase2a-audit.md` complete: one line per 56 markers with tier + outcome + commit reference.
4. `QL.validateExperimentalMode` and every call site gone; `grep -r validateExperimentalMode jquantlib/src/main/java` returns empty.
5. All formerly-LM-skipped tests un-skipped and green.
6. `(cd jquantlib && mvn test)` fully green with zero skips attributable to 2a-scope unavailability.
7. `docs/migration/phase2a-completion.md` written (stub counts, probe counts, commits summary, carry-over to 2b).
8. Tag `jquantlib-phase2a-complete` at final commit, pushed to origin.

### 6.2 Handoff to Phase 2b

Phase 2b starts when `jquantlib-phase2a-complete` is pushed. Known 2b content at that point:

- `CapHelper` — requires `IborLeg` infrastructure and its transitive dependencies.
- `G2` two-factor short-rate model — requires functional `TreeLattice2D` grid and two-factor calibration scaffolding.
- Any marker carved during WI-4 (expected count: 0, by design — only A4-triggered carves are permitted).
- **Open question for 2b brainstorming:** does 2b stay inside the 61 packages or broaden? The Phase 1 fence was there to make Phase 1 tractable; 2b can and probably should reassess scope.

2b design starts from `docs/migration/phase2a-carveouts.md` (if any) + the scanner state at tip of 2a + a fresh reread of `phase1-design.md` §12.

### 6.3 Inherited discipline (from phase1-design)

The following remain binding unchanged through Phase 2a:

- Ground truth: C++ QuantLib v1.42.1 @ `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`.
- TDD + cross-validation via `migration-harness/` probes.
- Tolerance tiers: exact / tight `1e-12` rel `1e-14` abs / loose `1e-8` rel (phase1-design §4.2). Per-test exceptions require inline justification.
- One stub (or cycle-batch) = one commit. Every commit compiles and passes `(cd jquantlib && mvn test)`.
- Direct push to `main`, fast-forward per layer, no PRs. No `Co-authored-by: Claude` trailer. `-s` Signed-off-by. Unsigned commits (no GPG/SSH).
- Divergence found mid-stub → separate preceding `align(<pkg>): ...` commit, not folded into the stub commit.
- Pause triggers A1–A6 from phase1-design §7.3 remain active.

### 6.4 New pause trigger for Phase 2a

- **A7** — WI-4 Tier-2 divergences ≥ 20 of 56. Indicates systemic JQuantLib drift requiring scope reassessment; pause and reconfer.

### 6.5 Risk log

| # | Risk | Probability | Impact | Mitigation |
|---|---|---|---|---|
| R1 | MINPACK port surfaces a bug in v1.42.1 itself (A3 trigger) | Low | High | Pause. Verify against upstream MINPACK (public domain, ~40-year pedigree, exhaustively cross-validated). Extremely unlikely. |
| R2 | 2000 LOC MINPACK port → transcription error from fatigue | Med-High | High | Cycle-batch (≤400 LOC per commit). Helper-level probes (qrfac, qrsolv) written and cross-validated **first**, catching structural drift before drivers are built. |
| R3 | Tier-1 visual diff misses a subtle divergence (false negative) | Med | Med | Nine-point checklist + "any doubt → T2" rule. Residual risk accepted; Phase 2b can re-audit in field. |
| R4 | HestonProcess QE probe requires loose `1e-8` tolerance due to FP ordering | Med | Low | Inline justification per phase1-design §4.2. Documented in audit log. |
| R5 | WI-4 reveals >20 divergences — deeper drift than expected | Low | High | New pause trigger A7. Pause for scope reassessment. |
| R6 | Un-skipped LM-dependent test fails for reasons unrelated to LM | Med | Med | Each failure is its own Tier-2 item; fix one at a time; don't re-skip. |
| R7 | Scanner allowlist mechanism becomes a carve-out dumping ground | Low | Med | Allowlist entries must match the exact criterion "faithful port of C++ `QL_FAIL` / `throw`". Any other justification goes in `carveouts.md`. |

## 7. Decision Log

Phase 2a-specific decisions made during brainstorming. Phase 1 decisions in `phase1-design.md` §12 remain binding.

| # | Decision | Rationale |
|---|---|---|
| P2A-1 | Phase 2a scope = tail-of-Phase-1 (A of scope options) | Keeps 2a bounded and finishable; decouples finishing Phase 1's deferred work from the larger question of broadening scope. |
| P2A-2 | Carveouts = MINPACK/LM + Heston QE in scope; CapHelper + G2 deferred to 2b (B of carve options) | CapHelper needs IborLeg, G2 needs TreeLattice2D + two-factor calibration — both too large to include without tripping A4. MINPACK, QE, TreeLattice2D, and QL cleanup all fit within the 61-package fence. |
| P2A-3 | Audit approach = tiered triage with hard "any divergence → full probe+test" rule (B of audit options) | Balances throughput (most markers likely to be Tier-1 clean) with bulletproof guarantee (any actual drift gets the full Phase 1 treatment). |
| P2A-4 | Execution order = WI-1 → WI-2 → WI-3 → WI-4 → WI-5 (A of ordering options) | Cheap warmup first; then MINPACK while focus is fresh; then QE; then the audit sweep under regression coverage from un-skipped LM tests; then final housekeeping. |
| P2A-5 | MINPACK Java package layout = new `MINPACK` class in same package as `LevenbergMarquardt` (not subpackage, not nested) | Java idiom for mirroring a C++ namespace of free functions; keeps `MINPACK` lexically adjacent to its sole caller the way C++ does; avoids class-per-function Java-ism. |
| P2A-6 | Single-session execution, no parallelism | User preference: keep things simple for now; parallelism/orchestration deferred. |
| P2A-7 | New pause trigger A7 (Phase 2a-only) | WI-4 is the first time we're auditing pre-existing Java for drift at scale; need a circuit-breaker if the suspected drift rate is wildly wrong. |

---

**End of design.**

Implementation plan (`docs/migration/phase2a-plan.md`) to be generated next via the `superpowers:writing-plans` skill after this design is approved.
