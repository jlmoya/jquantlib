# JQuantLib Migration — Phase 1: Finish Started Stubs

**Status:** Draft for approval
**Date:** 2026-04-22
**Author:** Jose Moya (with Claude collaboration)
**Branch:** `migration/phase1-finish-stubs` (off `main`)
**C++ reference:** QuantLib **v1.42.1** (pinned)

---

## 0. Reading this document

This is the authoritative design for **Phase 1** of the JQuantLib migration effort. It is not a how-to guide; it is the contract between intent and implementation. The companion document — the implementation plan — will be generated from this design using the `superpowers:writing-plans` skill after this doc is approved, and will live at `docs/migration/phase1-plan.md`.

Phase 2 (filling gaps beyond the current Java tree — `experimental/`, `marketmodels/`, `credit/`, etc.) is explicitly not covered here and will get its own design once Phase 1 is complete.

## 1. Context

### 1.1 The two codebases

- **JQuantLib (Java)** — `/Users/josemoya/eclipse-workspace/jquantlib/jquantlib` — 717 `.java` source files across 61 packages under `org.jquantlib.*`, built with Maven, Java 11 target, JUnit 4 tests, last substantive commit 2021-06-03.
- **QuantLib (C++)** — `/Users/josemoya/Projects/GitProjects/QuantLib` — 2,379 source files (1,422 headers + 957 .cpp), ~433K lines of code across 87 directories, `master` currently at 1.43-dev. This project pins to tag **`v1.42.1`** (commit `41b0e1460f3e4991087917707ca29a9125e200db`, dated 2026-04-13).

### 1.2 The problem

The Java project started as a port of QuantLib from the mid-2000s and stalled at partial coverage. A scan of the current Java tree finds:

- **~179** `throw new UnsupportedOperationException("Work in progress")` in non-test files.
- **~23** `throw new LibraryException("not [yet] implemented")`.
- **~952** `// TODO` and `// FIXME` comments, of which an unknown subset represent real defects rather than notes.
- Uneven coverage: time/calendars ~90%, math ~85%, indexes ~80%, but large gaps in credit, inflation, market models, Monte Carlo variance reduction, and the entire `experimental/` module.

Phase 1's job is to resolve every stub in the existing 61 packages without adding new top-level packages.

### 1.3 Ground truth

> **C++ QuantLib v1.42.1 is the source of truth. The existing Java code is our starting material, not a design to preserve. Anywhere Java diverges from v1.42.1 — signatures, implementations, constants, behavior — C++ wins.**

This principle resolves ambiguity throughout the rest of this document. It also means API compatibility with the pre-Phase-1 Java API is not a goal.

## 2. Goals and Non-Goals

### 2.1 Goal

Bring every already-started Java class in `org.jquantlib.*` to a state where it behaves as its pinned C++ counterpart (QuantLib v1.42.1) does, verified by a Java test whose expected values are cross-validated against a live C++ run of the same inputs.

**Definition of "Phase 1 done":**

1. The stub scanner reports zero stubs across all 61 existing packages.
2. All existing Java tests pass, plus every test added during Phase 1.
3. Every cross-validation reference check passes within its tier tolerance.
4. `docs/migration/stub-inventory.json` is `[]` and `docs/migration/worklist.md` has every checkbox ticked.
5. `migration-harness/verify-harness.sh` passes from a fresh clone + submodule init.
6. A completion report is written to `docs/migration/phase1-completion.md` summarizing work done, carve-outs, and recommended Phase 2 scope.

### 2.2 Non-goals

These are explicitly out of scope for Phase 1. Each has a clear rationale; changing any one requires an explicit revision to this document.

1. **No new top-level packages.** Classes for C++ code with no Java counterpart — `experimental/`, `models/marketmodels/`, `termstructures/credit/`, most of `termstructures/inflation/`, `pricingengines/{credit,cliquet,forward,futures,lookback,quanto,swaption,basket}`, `math/{copulas,ode}`, `methods/finitedifferences/{meshers,operators,schemes,solvers,stepconditions,utilities}` — stay untouched for Phase 2.
2. **No new calendars, day counters, or indexes** beyond what already exists in Java, even where v1.42.1 has more.
3. **API shape follows v1.42.1.** Where the current Java signature differs from v1.42.1, we update Java to match. We do not preserve the existing Java API for its own sake. (This replaces the original "No API redesign" non-goal.)
4. **No performance work.** Correctness only. A slow ported algorithm stays slow; we note it and move on.
5. **No dependency upgrades.** Java 11, SLF4J, JUnit 4 stay. JUnit 5 migration is its own project.
6. **No opportunistic reformatting or refactoring.** We touch only files that contain a stub we're resolving or that need changes to make a test work. When we *do* touch a file, any divergence from v1.42.1 in that file gets reconciled (see §4.6).
7. **No CI.** No `.github/workflows/` added in Phase 1. Every check is local.
8. **No Husky / pre-commit tooling.** Discipline is in the workflow, not the hooks.

### 2.3 Inclusion criteria — what counts as a "stub"

The scanner (§3) classifies the following as stubs:

| Kind | Pattern | Estimated count |
|------|---------|----------|
| `work_in_progress` | `throw new UnsupportedOperationException("Work in progress")` in non-test code | ~179 |
| `not_implemented` | `throw new LibraryException("not [yet] implemented")` | ~23 |
| `todo_stub` | Method body consists of only a `TODO` marker with no real code | TBD by scanner |
| `fixme_defect` | `// FIXME` flagging a real bug (judged individually on encounter) | subset of ~120 |
| `numerical_suspect` | Method compiles/runs but a sibling comment doubts correctness (`TODO: code review :: please verify against QL/C++ code`) | subset of ~650 |

`numerical_suspect` is recorded but **not** actively fixed in Phase 1 unless cross-validation exposes a bug (per the user's decision during design review). Plain `// TODO: <note>` comments next to working code are not stubs.

## 3. The stub inventory and worklist

### 3.1 Artifacts

Two tracked documents, both regenerated by the scanner tool:

- **`docs/migration/stub-inventory.json`** — one JSON object per open stub, schema below. Regenerating it should be deterministic given a fixed Java tree.
- **`docs/migration/worklist.md`** — human-readable checklist ordered by dependency layer (§3.3). Checked off as stubs close.

### 3.2 Inventory schema

```json
{
  "id": "methods.montecarlo.PathGenerator#next",
  "file": "jquantlib/src/main/java/org/jquantlib/methods/montecarlo/PathGenerator.java",
  "line": 142,
  "kind": "work_in_progress",
  "method_signature": "Sample<Path> next()",
  "cpp_counterpart": "ql/methods/montecarlo/pathgenerator.hpp:next()",
  "depends_on": [
    "math.randomnumbers.RandomNumberGenerator#nextSequence"
  ],
  "existing_test": null,
  "cpp_tests": ["test-suite/pathgenerator.cpp::testPathGeneratorBrownian"],
  "notes": "..."
}
```

`kind` is one of the five values in §2.3. `depends_on` is populated in the first pass by scanning Java imports (see §3.3); it may be amended manually when the static pass is ambiguous.

### 3.3 Dependency ordering — two-pass

**Pass 1 — automatic.** For each stub's enclosing class, list every Java class in the tree that imports it; reverse the edges to derive a "depends on" graph; topologically sort.

**Pass 2 — manual tiebreak.** Where Pass 1 produces ambiguous order (multiple stubs at identical graph depth), consult v1.42.1's `#include` list as tiebreak.

Cycles — which exist (Observer/Observable) and will not be broken — get merged into a single **cycle-batch** processed atomically (§4.5).

### 3.4 The worklist layers

`worklist.md` groups stubs into **layers** derived from topological depth:

```markdown
## Layer 0 — leaves (no unresolved Java dependencies)
- [ ] math.distributions.BinomialDistribution#binomialCoefficientLn
- ...

## Layer 1 — depends only on Layer 0
- ...
```

Layer 0 begins with the **Observable / Handle audit** (§8.R3), which is infrastructure work predating stub resolution.

### 3.5 Size estimate and scope-adjustment trigger

Best current estimate: **300–600 atomic stubs** once TODOs-that-are-notes are filtered out. Actual count arrives with the scanner's first run. If actual > 1000 we invoke trigger A1 and re-scope (§7.3).

## 4. The per-stub workflow

This is the core operational loop, executed for every stub or cycle-batch in worklist order. The `superpowers:test-driven-development` skill is binding.

### 4.1 The nine steps

1. **Read the C++ counterpart** at v1.42.1 — `.hpp` and `.cpp` both. Note boost-isms, numerical choices (addition ordering, branch cuts, edge cases).
2. **Identify or author the reference test.** Three cases:
    - **a.** C++ test exists → port verbatim, keep the test name.
    - **b.** No C++ test but method is pure/deterministic → author 2–3 input/output pairs taken from running C++.
    - **c.** Stateful cycle → author an integration test exercising the whole cycle against C++-produced reference values.
   Tests live at `src/test/java/org/jquantlib/testsuite/<package>/<ClassName>Test.java`.
3. **Run the test. Confirm RED.** If it passes unexpectedly, stop — either the stub wasn't a stub or the test is tautological.
4. **Port the implementation** idiomatically in Java, following existing JQuantLib conventions. Preserve the C++ method's comment as a `//--` block above the Java method for traceability. Do not add features C++ doesn't have. Do not simplify unless the simplification is provably equivalent.
5. **Run the test. Confirm GREEN.** If it still fails, stop and inspect — do not loosen tolerance to force a pass.
6. **Cross-validate against live C++** via the harness (§5). Compare Java output to C++ output to the same tier tolerance. On mismatch, C++ is ground truth; investigate Java.
7. **Run the full Java test suite** (`mvn -pl jquantlib test`). Any regression in the pre-existing ~100 tests blocks the commit.
8. **Commit** per §6, one commit per stub (or cycle-batch).
9. **Re-run the scanner.** The stub should no longer appear. New stubs surfaced by the port (if any) get added to the worklist at appropriate layers.

### 4.2 Tolerance tiers

Tolerance is set at test-authoring time, written into the test file, reviewed in the commit.

| Tier | Applies to | Tolerance |
|------|------------|-----------|
| **Exact** | Integer/date arithmetic, calendar logic, day-count integer fractions, enum dispatch | bit-exact (`==`) |
| **Tight** | Closed-form formulas (Black-Scholes, bond yields, discount factors, interpolations) | `1e-12` relative; `1e-14` absolute when the reference value is below `1e-2` |
| **Loose** | Monte Carlo, numerical integration, root-finding, PDE solvers, anything with a convergence parameter | `1e-8` relative, or the algorithm's stated convergence × 10, whichever is larger |

A helper, `org.jquantlib.testsuite.util.Tolerance`, encapsulates the policy so tests say `Tolerance.tight(java, cpp)` rather than open-coding thresholds.

**Tolerance exceptions** (tiers loosened for a specific test) require inline justification. Blanket loosening across tests is prohibited and triggers quality gate G1 (§7.2).

### 4.3 Done-criteria per stub

A stub is done when **all** hold:

1. No `UnsupportedOperationException` / "not implemented" remain in the resolved method.
2. At least one new test exercised the path and failed before the fix.
3. That test now passes.
4. Cross-validation agrees within the tier tolerance.
5. The full pre-existing test suite still passes.
6. The scanner no longer lists the stub.
7. The commit is on `migration/phase1-finish-stubs` and references the worklist entry.

Any of 1–7 not satisfied means "not done."

### 4.4 Explicitly forbidden per-stub actions

- No opportunistic refactoring of nearby code (unrelated to the stub). Track follow-ups in `docs/migration/followups.md`.
- No renaming, no reformatting of code we're not touching.
- No adding `@Override`, no style-only edits outside the stub's file.
- No skipping cross-validation (step 6). If the harness is broken, we fix it before committing — we never commit without it.

### 4.5 Cycle-batches

When stubs form a dependency cycle that can't be broken:

1. Port all tests for the cycle (steps 1–2 for each).
2. Confirm all RED.
3. Port all implementations.
4. Confirm all GREEN.
5. Cross-validate each.
6. **One commit** for the whole batch. Commit body names every cycle member.

Cycle-batches larger than ~5 classes are flagged to the user before commit.

### 4.6 Divergence discovered mid-stub (the `align(...)` commit)

When cross-validation (step 6) fails and the cause is a bug in **already-existing non-stub Java code** transitively called by the stub under work, the fix is **a separate commit preceding the stub commit**, labeled `align(<package>): reconcile <ClassName> with v1.42.1`. This keeps:

- The stub commit's diff focused on the stub fix.
- The alignment commit individually reviewable.
- `git bisect` clean.

Alignment commits follow the same per-commit discipline as stub commits (their own regression test, cross-validation, passing full suite).

## 5. C++ reference harness

Step 6 of the per-stub workflow requires a reproducible way to run v1.42.1 and capture output as expected test values. This section specifies that infrastructure.

### 5.1 Layout

A new top-level directory, **not** part of the Maven build:

```
migration-harness/
├── README.md
├── setup.sh                      # One-time: submodule init, build QL, build probes
├── generate-references.sh        # Build + run + emit reference JSON
├── verify-harness.sh             # Regenerate a known probe, diff against committed
├── cpp/
│   ├── CMakeLists.txt
│   ├── quantlib/                 # git submodule → lballabio/QuantLib pinned to v1.42.1
│   ├── probes/
│   │   ├── common.hpp            # ReferenceWriter and helpers
│   │   ├── math/
│   │   │   └── bisection_probe.cpp
│   │   └── ...                   # one probe per Java test needing cross-validation
│   └── third_party/
│       └── nlohmann/json.hpp
└── references/                   # Generated JSON, committed
    ├── math/bisection.json
    └── ...
```

### 5.2 The QuantLib submodule

`cpp/quantlib/` is a git submodule tracking `https://github.com/lballabio/QuantLib.git` with HEAD pinned to **`v1.42.1`** (commit SHA recorded in `.gitmodules`). Fresh clones get exactly that tree via `git submodule update --init --recursive`. Pin moves are explicit, reviewable diffs to `.gitmodules`.

### 5.3 Probes

A probe is a small C++17 program exercising one or a few closely related QuantLib functions. It emits inputs and outputs as JSON to `migration-harness/references/<test-group>.json`.

Example:

```cpp
// cpp/probes/math/bisection_probe.cpp
#include <ql/math/solvers1d/bisection.hpp>
#include "../common.hpp"
using namespace QuantLib;

int main() {
    Bisection solver;
    auto f = [](Real x) { return x*x - 4.0; };

    ReferenceWriter out("math/bisection", "v1.42.1", "bisection_probe");
    out.addCase("testBisectionPositiveRoot",
                {{"a", 0.0}, {"b", 10.0}, {"guess", 5.0}, {"accuracy", 1e-12}},
                solver.solve(f, 1e-12, 5.0, 0.0, 10.0));
    out.write();
    return 0;
}
```

Probes are hand-authored, not generated. Each probe is reviewed in the same commit as its consuming Java test.

### 5.4 Reference JSON format

```json
{
  "test_group": "math/bisection",
  "cpp_version": "v1.42.1",
  "cpp_commit": "41b0e1460f3e4991087917707ca29a9125e200db",
  "generated_at": "2026-04-22T14:30:00Z",
  "generated_by": "bisection_probe",
  "cases": [
    {
      "name": "testBisectionPositiveRoot",
      "inputs": {"a": 0.0, "b": 10.0, "guess": 5.0, "accuracy": 1e-12},
      "expected": 2.0000000000000004
    }
  ]
}
```

A Java helper, `org.jquantlib.testsuite.util.ReferenceReader`, reads these files; a schema check ensures Java expects the same case count the JSON provides. Drift (missing cases, extra cases) fails tests hard.

### 5.5 Dependencies and build

- **Boost** (QuantLib's required dep): `brew install boost` (macOS, installed). `apt install libboost-all-dev` (Linux CI, future).
- **CMake 3.18+** and a C++17 compiler. Verified in pre-flight checks (§9.P2).
- First-time QL build: 5–10 min. Subsequent probes: seconds.

### 5.6 Regeneration policy

References are regenerated when:

1. A new Java test is added that requires cross-validation — its probe is authored and run in the same commit.
2. A probe bug is fixed — the regeneration diff is reviewed.
3. The pinned QuantLib version moves (a Phase-2 event; explicitly high-scrutiny).

References are **never** regenerated silently during normal dev. A reference file change is a committed, reviewable diff.

### 5.7 Failure modes

- **Probe doesn't compile against v1.42.1** — probe uses stale API; fix the probe, not the pin.
- **Java and C++ disagree** — C++ wins. Investigate Java first (§4.6 alignment path).
- **Probe produces nondeterministic output** — stop adding stubs until fixed; `verify-harness.sh` exists to catch this.
- **C++ itself appears wrong** — investigate hard; trigger A3 escalates to the user.

### 5.8 Out of scope

- No per-test probe generation automation. Volume (~500 probes total) doesn't justify a code generator, and a generator would hide per-test input choices.
- No CI wiring. References are committed so CI (if ever added) reads them without needing the C++ toolchain.

## 6. Git, commit, and branch discipline

### 6.1 Branch topology

```
main
  └── migration/phase1-finish-stubs          (long-lived, ← all Phase 1 work)
```

- `main` — production branch; Phase 1 layers merge here via fast-forward only.
- `migration/phase1-finish-stubs` — created 2026-04-22 off `main`, where every commit lands.
- No sub-branches for individual stubs. Exploratory throwaway branches may be cut off `migration/phase1-finish-stubs` for genuinely uncertain investigations, then rebased and squashed back.

### 6.2 Commit atoms

One stub (or one cycle-batch) = one commit. Each stub commit contains:

- The Java source change.
- The new or updated Java test.
- The new C++ probe under `migration-harness/cpp/probes/`.
- The generated JSON reference under `migration-harness/references/`.
- The updated `docs/migration/stub-inventory.json`.
- The updated `docs/migration/worklist.md` checkbox.

Infrastructure commits are separate and labeled `infra(...)`. Divergence-reconciliation commits are separate and labeled `align(...)` (§4.6).

### 6.3 Commit message format

```
<type>(<package-short>): <verb> <ClassName>.<method>

Worklist: <id from stub-inventory.json>
C++ ref: <relative path>:<line>
Test: <added test class + method>
Cross-validation: <probe path>, <reference file path>
Tolerance tier: exact | tight | loose           (note any non-default)

<optional notes — cycle-batch members, tolerance exception justification, etc.>
```

`<type>` is one of `stub`, `align`, `infra`, `fix`, `docs`.

Example:

```
stub(math.solvers1d): implement Bisection.solveImpl

Worklist: math.solvers1d.Bisection#solveImpl
C++ ref: ql/math/solvers1d/bisection.hpp:45
Test: BisectionTest.testQuadraticRoot
Cross-validation: migration-harness/cpp/probes/math/bisection_probe.cpp,
                  migration-harness/references/math/bisection.json
Tolerance tier: tight
```

### 6.4 Pre-commit checklist (manual)

Before every commit:

1. `mvn -pl jquantlib test` passes.
2. Scanner re-run produces an inventory without the resolved stub.
3. `git status` shows exactly the expected files, nothing stray.
4. Cross-validation test passed — not just the unit test.
5. Commit message follows the format and names a real worklist ID.

No Husky hook enforces this. Discipline is in the workflow.

### 6.5 Merging to `main` — per layer, fast-forward

After every layer of the worklist is fully green:

```bash
git checkout main && git pull origin main
git checkout migration/phase1-finish-stubs
git rebase main
git checkout main
git merge --ff-only migration/phase1-finish-stubs
git push origin main
git checkout migration/phase1-finish-stubs   # continue on branch for next layer
```

Rules:

- **No squashing.** Every stub commit individually preserved on `main` (bisect must work).
- **No merge commits.** Fast-forward only; linear history.
- **No PRs.** This is a solo, single-owner repo — PRs add no value.
- Rebase `migration/phase1-finish-stubs` on `main` before merging up.

### 6.6 Bisectability — every commit green

Every individual commit must compile, pass the full Java test suite, and leave inventory/worklist/references in a consistent state. A broken intermediate commit poisons bisect for the whole subsequent range. If a change turns out bigger than one commit, we finish it or abandon the work — we never push a broken state.

### 6.7 Regression handling — fix-forward

Default: a new `fix(<package>): ...` commit with a test exposing the regression. Revert only when the fix is complex and non-local. Never amend or force-push commits already merged to `main`.

### 6.8 Commit attribution and signing

- **Author:** whatever the user's `git config` resolves (currently `Jose Moya <jlmoya@gmail.com>`).
- **Co-authored-by trailer:** none. No Claude attribution.
- **Signed-off-by trailer:** yes (`git commit -s`).
- **GPG/SSH signing:** disabled. Unsigned commits are the default.

### 6.9 Phase 1 opening commits

The first seven commits on `migration/phase1-finish-stubs`, in order:

1. `docs(migration): add Phase 1 design document`
2. `infra(harness): add migration-harness scaffold with QuantLib submodule at v1.42.1`
3. `infra(harness): add CMake build for probes; add nlohmann/json third-party`
4. `infra(harness): add common.hpp ReferenceWriter and reference JSON schema`
5. `infra(scanner): add stub inventory scanner tool`
6. `infra(inventory): generate initial stub-inventory.json and worklist.md`
7. `infra(testutil): add org.jquantlib.testsuite.util.Tolerance and ReferenceReader`

Stub commits begin after these.

## 7. Quality gates and check-in cadence

### 7.1 Three levels of done

| Level | Unit | Gate |
|-------|------|------|
| L1 | One stub / cycle-batch | §4.3 criteria (1–7) all hold |
| L2 | One layer | L1 for every stub in layer + `mvn clean test` from fresh checkout + `verify-harness.sh` passes + merged to `main` + layer summary reported to user |
| L3 | Phase 1 complete | §2.1 criteria (1–6) all hold |

### 7.2 Continuous quality gates

These apply throughout, not per-stub:

- **G1 — No tolerance erosion.** Loosening a tolerance on a previously passing test requires explicit user sign-off, recorded in the commit message.
- **G2 — No skipped tests.** `@Ignore`, `@Disabled`, `Assume.assumeTrue(false)` never enter `main` without an open followup issue and user sign-off.
- **G3 — No swallowed exceptions.** `catch (... e) { /* ignore */ }` must be inline-justified.
- **G4 — No TODO churn.** New `// TODO` comments discouraged; follow-ups go to `docs/migration/followups.md` with date and context.
- **G5 — No unsourced magic numbers.** Hardcoded numerical values in new tests must cite their probe and reference file.

### 7.3 When the user is pulled in

Default: autonomous work. The user is asked before proceeding only for:

| Trigger | Condition |
|---------|-----------|
| A1 | Stub count from first scanner run is > 1000 (vs 300–600 estimate) |
| A2 | Tolerance exception needed looser than `1e-8` |
| A3 | Cross-validation mismatch investigated and C++ v1.42.1 appears to be the bug |
| A4 | A stub resolution strictly requires a new class outside the 61 existing packages |
| A5 | (Not used.) API shape changes are automatic under §1.3; no per-change approval needed |
| A6 | End of every layer — summary report, user acknowledges before next layer starts |

Otherwise the user works without interruption and receives a layer-end heads-up.

### 7.4 Early-abort triggers

Conditions that pause Phase 1 for redesign:

- Harness regeneration reveals non-determinism in probes.
- More than three tolerance exceptions in a single layer (pattern suggests wrong tier or wrong port).
- Pre-existing Java test asserts a wrong value — we pause, fix the test, continue.
- Cumulative carve-outs (§7.5) exceed ~5% of stubs.

(A velocity-based trigger was considered and deferred; we will evaluate it empirically after the first layer completes.)

### 7.5 Acceptable carve-outs

Some stubs may end Phase 1 explicitly unfixed, with user sign-off, documented in `docs/migration/phase1-carveouts.md`:

- Stubs whose C++ counterpart uses a deprecated/removed feature in v1.42.1.
- Stubs in transitively unreachable (dead) Java code.
- Stubs depending on a Phase-2-only class.

Target: zero carve-outs. Acceptable if rare.

### 7.6 Out of scope for quality gates

- Performance benchmarks.
- API backward compatibility with the pre-Phase-1 Java API.
- Formal verification or proof checking.

## 8. Risks

**R1. API drift between 2007-era Java and v1.42.1.** Several QuantLib classes have been renamed/split/reshaped. Resolution: per §1.3, update Java to match v1.42.1 automatically. No per-case approval.

**R2. Non-stub code may be silently wrong.** The scanner finds stubs, not hidden defects in passing-but-untested code. Mitigation: cross-validation naturally traverses call graphs; every divergence discovered becomes an `align(...)` commit (§4.6). Systematic audit of untouched non-stub code is Phase 2.

**R3. Observable/Handle infrastructure may be subtly wrong.** These are load-bearing across every pricing engine. Mitigation: **Layer 0 begins with a targeted audit** of `org.jquantlib.util.Observable*`, `org.jquantlib.util.WeakReferenceObservable`, `org.jquantlib.quotes.Handle` — port the C++ observable test-suite, verify semantics, reconcile. This precedes all stub work.

**R4. Probes can be wrong.** Cross-validation only works if the probe computes the intended thing. Mitigation: probes are small and committed alongside their consuming test; review is always joint.

**R5. Stub count surprise.** Real count may exceed estimate. Trigger A1 handles this.

**R6. Floating-point platform drift.** Last-bit differences between macOS and Linux for `exp`, `log`, `erf` may exceed `1e-12`. Resolution: per-test tolerance loosening with justification; no blanket change.

**R7. Large cycle-batches.** `Observable`/`Handle` may pull in 10+ interconnected classes. Mitigation: flag any cycle-batch >5 classes to user before commit; split if reasonable.

**R8. Boost-idiom translation subtleties.** `boost::shared_ptr` lifetime, `boost::optional` null semantics, lambda captures. Mitigation: follow existing JQuantLib conventions; where convention is itself wrong, `align(...)` to v1.42.1.

**R9. `maven-central` branch.** Present on origin but unrelated to Phase 1; we do not touch it. If POM changes ever conflict with its expectations, address at that time.

**R10. No CI.** Regressions surface only in local `mvn test`. Acceptable given per-commit discipline; reconsider in Phase 2.

## 9. Pre-implementation checks

Before writing the implementation plan or any code, verify:

- **P1.** `brew list boost` shows Boost installed; note version.
- **P2.** `cmake --version` ≥ 3.18.
- **P3.** `mvn -pl jquantlib test` passes on current `migration/phase1-finish-stubs` — known-green baseline.
- **P4.** Checkout QuantLib at v1.42.1 in a throwaway location, build it, link a trivial probe. De-risks the harness.
- **P5.** A rough grep-based stub count, pre-scanner, to sanity-check the 300–600 estimate.

If any fail, address before proceeding. Total: ~15–20 minutes.

## 10. Transition to implementation

Once this document is approved:

1. The `superpowers:writing-plans` skill produces `docs/migration/phase1-plan.md` — the ordered, concrete implementation plan grounded in this design.
2. That plan is reviewed and approved by the user.
3. Pre-flight checks §9 run.
4. The seven opening commits (§6.9) land in order.
5. Layer 0 begins with the Observable/Handle audit (§8.R3), then leaf stubs in topological order.
6. Work proceeds per §4 (workflow), §6 (git), §7 (quality gates) until §2.1 (Phase 1 done).

## 11. Glossary

- **Alignment commit (`align(...)`):** A commit that reconciles pre-existing non-stub Java code with v1.42.1 when cross-validation reveals divergence; precedes the stub commit that uncovered it.
- **Cross-validation:** Confirming a ported Java implementation matches v1.42.1's output on identical inputs, within tier tolerance, using the harness (§5).
- **Cycle-batch:** A set of stubs with mutual dependencies, migrated and committed atomically.
- **Ground truth:** C++ QuantLib v1.42.1. When Java and C++ disagree, C++ is correct by definition.
- **Harness:** `migration-harness/` — the reproducible C++ reference infrastructure (§5).
- **Layer:** A topological level in the stub dependency graph; unit of `main`-merge.
- **Probe:** A small C++17 program under `migration-harness/cpp/probes/` that captures v1.42.1 outputs as JSON references.
- **Stub:** A method classified by the scanner as needing implementation (five kinds in §2.3).
- **Worklist:** `docs/migration/worklist.md` — the ordered, checkable list of stubs, grouped by layer.

## 12. Decision log

The following decisions were made during the 2026-04-22 design session and are binding on Phase 1:

1. **Scope:** Option B — finish what's started within the existing 61 packages.
2. **Test strategy:** A+ — TDD + cross-validation against live C++ runs.
3. **C++ pin:** v1.42.1.
4. **Sequencing:** Approach #1 — dependency-first (bottom-up).
5. **Inventory format:** JSON.
6. **`numerical_suspect` handling:** Leave alone unless tests expose a bug.
7. **Tolerance:** exact / tight `1e-12` / loose `1e-8`; per-test exceptions with justification.
8. **Cycle commits:** Batched.
9. **Merge strategy:** Per-layer, fast-forward, no squashing.
10. **No Co-authored-by trailer.** No PR ceremony. Push direct to `main`.
11. **Commit signing:** disabled (unsigned).
12. **Design doc location:** `docs/migration/phase1-design.md`.
13. **Observable/Handle audit:** first task of Layer 0.
14. **Ground truth:** v1.42.1. Java diverges → Java updates. (§1.3)
15. **Divergence found mid-stub:** separate `align(...)` commit preceding the stub.

## Appendix A — C++-to-Java convention reference

Conventions inherited from the existing Java code base, used throughout Phase 1:

| C++ | Java |
|-----|------|
| `namespace QuantLib::X::Y` | package `org.jquantlib.x.y` |
| `QL_REQUIRE(cond, msg)` | `QL.require(cond, msg)` |
| `QL_ENSURE(cond, msg)` | `QL.ensure(cond, msg)` |
| `typedef double Real` | `@Real double` (JSR-308 annotation) |
| `typedef Size Natural` | `@Natural int` |
| `ext::shared_ptr<T>` | plain `T` reference (GC-managed) |
| `Handle<T>` | `org.jquantlib.quotes.Handle<T extends Observable>` |
| `Observable` / `Observer` | `org.jquantlib.util.Observable` / `Observer` (delegation pattern for multi-inheritance) |
| `operator+=(Period)` | `addAssign(Period)` |
| `operator==` / `operator<` | `.eq(...)` / `.lt(...)` |
| Polymorphic dispatch | `PolymorphicVisitor` pattern |
| C++ method signature preserved | `//--` comment above Java method |

Where an existing Java convention is itself wrong relative to v1.42.1, reconciliation is an `align(...)` commit (§4.6) not a convention change.

## Appendix B — Reference links

- Upstream QuantLib: https://github.com/lballabio/QuantLib
- Pinned tag: `v1.42.1` @ `41b0e1460f3e4991087917707ca29a9125e200db`
- Local C++ clone: `/Users/josemoya/Projects/GitProjects/QuantLib`
- Original JQuantLib site: http://www.jquantlib.org/
- Superpowers skills used: `brainstorming`, `writing-plans` (next), `test-driven-development` (implementation), `verification-before-completion`, `executing-plans`.
