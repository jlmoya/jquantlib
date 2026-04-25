# Phase 2c Implementation Plan — JQuantLib Migration

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. NEW for Phase 2c: each WI runs in its own git worktree (see L0 setup); independent worktrees A/C/D run concurrently while worktree B serializes WI-2→WI-3 internally.

**Goal:** Land the five Phase 2c work items per `docs/migration/phase2c-design.md`: WI-1 `NonCentralChiSquared*` distribution alignment + `CIR.discountBondOption` unstub (with conditional HestonProcess analogue), WI-2 SABR α-default formula align-fix, WI-3 SABR test hygiene (orphan probe consumer + IsFixed-loop comment), WI-4 three HullWhite latent items, WI-5 BlackKarasinski tree-pricing un-stub (time-boxed; A4 carve fallback). End state: scanner reports 2 `work_in_progress` (CapHelper, G2 — Phase-2d/2e seeds, unchanged), 0 `not_implemented`, 0 `numerical_suspect`; tag `jquantlib-phase2c-complete`.

**Architecture:** Same as Phase 2b — direct commits to `main`, TDD per stub, cross-validated against C++ QuantLib v1.42.1 via `migration-harness/` probes, tolerance tiers (exact/tight/loose). NEW for 2c: parallel git worktrees per `phase2c-design.md` §4.2 — worktree A=WI-1, B=WI-2→WI-3 (internally serialized), C=WI-4, D=WI-5. Each worktree fast-forwards to `main` async as its full-suite passes; controller orchestrates rebases between landings. Pause triggers per design §5: A6 disabled, A4 redirected to WI-5 carve gate, A8 inactive, new A9 worktree-merge-conflict.

**Tech Stack:** Java 11 / Maven / JUnit 4 (existing); C++17 / CMake / QuantLib v1.42.1 pinned via submodule (commit `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`); Python 3 for scanner tooling; nlohmann/json for probe output; git worktrees for parallel implementer execution.

---

## Overview

| Layer | Description | Worktree | Expected commits |
|-------|-------------|----------|------------------|
| L0 | Pre-flight: confirm baseline, snapshot scanner, set up 4 worktrees | (main) | 0 |
| L1 | WI-1 chi-squared port + CIR (+conditional HestonProcess) unstub | A | 3–5 |
| L2 | WI-2 SABR α-default formula | B | 1 |
| L3 | WI-3 SABR test hygiene (orphan probe consumer + IsFixed-loop comment) | B (after L2) | 1–2 |
| L4 | WI-4 HullWhite latent items (3 fixes) | C | 3 |
| L5 | WI-5 BK tree-pricing (time-boxed; fix path or carve to Phase 2d) | D | 0–4 |
| L6 | Completion doc + tag | (main) | 1 commit + tag |

**Non-goals reminder (design §2.2):** no new top-level packages; CapHelper carved to Phase 2d, G2 to Phase 2e; C4 (SABR Halton multi-restart), C5 (XABR plumbing — `shift`/`volatilityType`/`errorAccept`/`useMaxError`/`addParams`), C6 (LM analytic-Jacobian path) stay deferred; cross-cutting one-factor α-default review NOT in scope.

**Git discipline (inherited):** every commit signed off with `-s`; no `Co-authored-by: Claude` trailer; unsigned (no GPG/SSH); push direct to `origin main` after each commit's full suite passes. Commit messages follow `<kind>(<pkg>): <verb> ...` where `<kind>` is `stub`, `align`, `infra`, `chore`, `docs`, or `test`.

**Parallelism (P2C-5):** worktrees A/C/D launch their first implementer subagent in parallel after L0; B starts WI-2 and serializes WI-3 internally. Per-task spec-reviewer + code-quality-reviewer pipeline stays sequential per the skill rule (code quality only after spec compliance ✅). Cross-worktree spec-reviewers may run concurrently with other worktrees' implementers/reviewers.

---

## Layer 0 — Pre-flight + worktree setup (no commits)

### Task 0.1: Confirm `main` is clean and green

- [ ] **Step 1:** Verify branch and clean working tree from the main checkout.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git status
git branch --show-current
```

Expected: branch `main`, working tree clean.

If `jquantlib-parent/.project` and/or `jquantlib/.classpath` show as modified (IDE-generated changes from the user's session), they are pre-existing IDE noise — leave them alone, don't include in any Phase-2c commit. Only flag if any actual source/test/doc file appears unexpected.

- [ ] **Step 2:** Run baseline test suite.

```bash
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: 632, Failures: 0, Errors: 0, Skipped: 22`.

- [ ] **Step 3:** Snapshot scanner state.

```bash
python3 tools/stub-scanner/scan_stubs.py
```

Expected printed tail:
```
  work_in_progress: 2
```

Both entries should be CapHelper and G2:
```bash
grep '"id"' docs/migration/stub-inventory.json
```

Expected: 2 entries — `model.shortrate.calibrationhelpers.CapHelper#Period` and `model.shortrate.twofactormodels.G2#G2`.

- [ ] **Step 4:** Verify the harness is functional and the C++ submodule is pinned.

```bash
./migration-harness/verify-harness.sh 2>&1 | tail -3
(cd migration-harness/cpp/quantlib && git rev-parse HEAD)
```

Expected: harness exits 0; submodule SHA `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`.

### Task 0.2: Set up four git worktrees

The plan uses 4 worktrees named A/B/C/D. Each branches from the current `main` tip. No work happens on the main checkout itself during L1-L5; the main checkout is reserved for controller-orchestrated rebases and the L6 completion-doc commit.

- [ ] **Step 1:** Create worktrees one level above the repo root (so they don't collide with the IDE's view of the main checkout).

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git worktree add ../jquantlib-2c-A 2c-wi1 -b 2c-wi1
git worktree add ../jquantlib-2c-B 2c-wi23 -b 2c-wi23
git worktree add ../jquantlib-2c-C 2c-wi4 -b 2c-wi4
git worktree add ../jquantlib-2c-D 2c-wi5 -b 2c-wi5
git worktree list
```

Expected: 5 worktrees (main + A/B/C/D), each on its own branch; A/B/C/D all at the same tip as `main`.

- [ ] **Step 2:** Sanity-check each worktree compiles and the test suite runs identically.

```bash
for wt in /Users/josemoya/eclipse-workspace/jquantlib-2c-A \
         /Users/josemoya/eclipse-workspace/jquantlib-2c-B \
         /Users/josemoya/eclipse-workspace/jquantlib-2c-C \
         /Users/josemoya/eclipse-workspace/jquantlib-2c-D; do
  echo "=== $wt ==="
  (cd "$wt/jquantlib" && mvn test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,")
done
```

Expected each: `Tests run: 632, Failures: 0, Errors: 0, Skipped: 22`.

If the C++ submodule was set up via the main checkout's `migration-harness/cpp/build`, each worktree shares it via the symlinked submodule path — no rebuilding required. If not, run `./migration-harness/setup.sh` from each worktree (one time each).

- [ ] **Step 3:** Note the worktree-to-WI mapping for the controller's records:

| Worktree | Path | Branch | WIs |
|---|---|---|---|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2c-A` | `2c-wi1` | WI-1 |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2c-B` | `2c-wi23` | WI-2 → WI-3 |
| C | `/Users/josemoya/eclipse-workspace/jquantlib-2c-C` | `2c-wi4` | WI-4 |
| D | `/Users/josemoya/eclipse-workspace/jquantlib-2c-D` | `2c-wi5` | WI-5 |

The controller passes the worktree path to each implementer subagent's `Work from: <directory>` line.

### Task 0.3: Worktree merge protocol (controller-side)

This is not an implementer task — it's the controller's process for landing each worktree's commits onto `main`.

- [ ] **Step 1:** Whenever a worktree finishes a commit and its full-suite passes (signed off, message correct), push that commit to `origin <branch-name>` first:

```bash
(cd /Users/josemoya/eclipse-workspace/jquantlib-2c-A && git push origin 2c-wi1)
```

This is a backup; the next step fast-forwards to `main`.

- [ ] **Step 2:** From the main checkout, fetch and fast-forward `main` to include the worktree's tip:

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
# If only one worktree has new commits since the last main update:
git merge --ff-only origin/2c-wi1
git push origin main
```

If two worktrees both have new commits and they touch disjoint files (the design's expectation), the merge to `main` is sequential FF-only:

```bash
git merge --ff-only origin/2c-wi1
git merge --ff-only origin/2c-wi4   # second worktree
git push origin main
```

If the second `--ff-only` rejects (merge required because main moved), rebase the second worktree's branch onto the new main FIRST, then re-run:

```bash
(cd /Users/josemoya/eclipse-workspace/jquantlib-2c-C && git fetch origin && git rebase origin/main)
(cd /Users/josemoya/eclipse-workspace/jquantlib-2c-C/jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
# Then back to main:
cd /Users/josemoya/eclipse-workspace/jquantlib
git merge --ff-only origin/2c-wi4
git push origin main
```

If the rebase produces non-trivial conflicts (more than auto-resolvable), **A9 fires** — pause and ask the user how to proceed.

- [ ] **Step 3:** After landing each worktree's WI to `main`, all OTHER active worktrees rebase onto the new main before their next commit:

```bash
for wt in /Users/josemoya/eclipse-workspace/jquantlib-2c-B \
         /Users/josemoya/eclipse-workspace/jquantlib-2c-C \
         /Users/josemoya/eclipse-workspace/jquantlib-2c-D; do
  echo "=== rebasing $wt ==="
  (cd "$wt" && git fetch origin && git rebase origin/main)
done
```

This keeps every worktree current with the latest main, so the next FF merge is always clean.

- [ ] **Step 4:** Update `docs/migration/phase2c-progress.md` after each landing (mirror the `phase2b-progress.md` format).

---

## Layer 1 — WI-1 `NonCentralChiSquared*` distribution + CIR unstub (worktree A)

All work in this layer happens in `/Users/josemoya/eclipse-workspace/jquantlib-2c-A` on branch `2c-wi1`. Other worktrees may run in parallel.

C++ source-of-truth: `migration-harness/cpp/quantlib/ql/math/distributions/chisquaredistribution.{hpp,cpp}` — defines `NonCentralChiSquareDistribution` (PDF), `NonCentralCumulativeChiSquareDistribution` (CDF, Sankaran approximation switching to Patnaik series for medium ncp and Ding series for large ncp), `InverseNonCentralCumulativeChiSquareDistribution` (inverse CDF via secant method on the CDF).

### Task 1.1: Build the chi-squared probe (commit 1, part A)

**Files:**
- Create: `migration-harness/cpp/probes/math/distributions/noncentral_chi_squared_probe.cpp`

- [ ] **Step 1:** Create the directory and probe source.

```bash
mkdir -p migration-harness/cpp/probes/math/distributions
```

```cpp
// migration-harness/cpp/probes/math/distributions/noncentral_chi_squared_probe.cpp
// Reference values for the NonCentralChiSquared* family — CDF, PDF,
// and inverse CDF — at a grid of (degrees, ncp, x) tuples covering
// the Sankaran/Patnaik/Ding branches. Tight tier.

#include <ql/version.hpp>
#include <ql/math/distributions/chisquaredistribution.hpp>
#include "../../common.hpp"

#include <vector>
#include <tuple>

using namespace QuantLib;
using namespace jqml_harness;

namespace {
struct Point {
    Real degrees;
    Real ncp;
    Real x;
};
} // namespace

int main() {
    ReferenceWriter out("math/distributions/noncentral_chi_squared", QL_VERSION,
                        "noncentral_chi_squared_probe");

    // Cover the three Sankaran/Patnaik/Ding regions:
    // - small ncp (< ~20)        → Patnaik / direct series
    // - medium ncp (~20 to ~600) → Sankaran approximation
    // - large ncp (> ~600)        → Ding series
    // Plus a couple of degenerate-ish corners.
    std::vector<Point> points = {
        { 1.0,    0.0,   0.5  },   // central chi-squared boundary
        { 2.5,    1.5,   3.0  },   // small ncp
        { 5.0,   10.0,   8.0  },   // small/medium boundary
        { 8.0,  100.0,  60.0  },   // Sankaran region
        { 4.0,  500.0, 250.0  },   // Sankaran/Ding boundary
        {12.0, 2000.0, 800.0  },   // Ding region
    };

    json arr = json::array();
    for (const auto& p : points) {
        NonCentralCumulativeChiSquareDistribution cdf(p.degrees, p.ncp);
        NonCentralChiSquareDistribution           pdf(p.degrees, p.ncp);
        InverseNonCentralCumulativeChiSquareDistribution icdf(p.degrees, p.ncp);
        const Real cdf_x  = cdf(p.x);
        const Real pdf_x  = pdf(p.x);
        // Round-trip the cdf value through the inverse to avoid the
        // probe asserting on inverse(an arbitrary u) which would
        // require us to publish u; instead we publish cdf(x) and let
        // Java verify inverse(cdf(x)) ≈ x at tight tier on its side.
        arr.push_back({{"degrees", p.degrees},
                       {"ncp",     p.ncp},
                       {"x",       p.x},
                       {"cdf",     cdf_x},
                       {"pdf",     pdf_x},
                       {"inv_cdf_at_cdf_x", icdf(cdf_x)}});
    }

    out.addCase("noncentral_chi_squared_grid",
                json{{"points", points.size()}},
                json{{"samples", arr}});

    out.write();
    return 0;
}
```

- [ ] **Step 2:** Build and run the probe.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2c-A
./migration-harness/generate-references.sh noncentral_chi_squared_probe 2>&1 | tail -5
```

Expected: builds and runs cleanly. Reference written to `migration-harness/references/math/distributions/noncentral_chi_squared.json`.

If the build fails because the C++ class names differ from the include or the constructor signatures are different in v1.42.1 (the names in the probe match the `chisquaredistribution.hpp` API), inspect:

```bash
grep -n "class NonCentralChi\|class InverseNonCentral" migration-harness/cpp/quantlib/ql/math/distributions/chisquaredistribution.hpp
```

Adjust the probe class names to match.

- [ ] **Step 3:** Inspect the generated JSON to confirm the reference values look sane:

```bash
cat migration-harness/references/math/distributions/noncentral_chi_squared.json | head -40
```

CDF values should be between 0 and 1; PDF values non-negative; `inv_cdf_at_cdf_x` should be very close to the input `x`.

### Task 1.2: Port `NonCentralCumulativeChiSquareDistribution` (CDF) (commit 1, part B)

**Files:**
- Create: `jquantlib/src/main/java/org/jquantlib/math/distributions/NonCentralCumulativeChiSquaredDistribution.java`

C++ ref: `chisquaredistribution.{hpp,cpp}` — `class NonCentralCumulativeChiSquareDistribution`. Implements the CDF via Sankaran approximation switching to Patnaik series for medium ncp (the C++ source has the exact branch thresholds — copy them verbatim).

- [ ] **Step 1:** Read the C++ implementation in detail.

```bash
sed -n '1,200p' migration-harness/cpp/quantlib/ql/math/distributions/chisquaredistribution.hpp
sed -n '1,200p' migration-harness/cpp/quantlib/ql/math/distributions/chisquaredistribution.cpp
```

Identify the class `NonCentralCumulativeChiSquareDistribution`, its constructor (takes `Real df, Real ncp`), and its `operator()(Real x)` body (the Sankaran/Patnaik switch).

- [ ] **Step 2:** Write the failing Java test stub first (TDD).

```bash
mkdir -p jquantlib/src/test/java/org/jquantlib/testsuite/math/distributions
```

```java
// jquantlib/src/test/java/org/jquantlib/testsuite/math/distributions/NonCentralChiSquaredDistributionTest.java
/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Tests for the NonCentralChiSquared* family ports. See phase2c-design §3.1.
 */
package org.jquantlib.testsuite.math.distributions;

import org.jquantlib.math.distributions.NonCentralChiSquaredDistribution;
import org.jquantlib.math.distributions.NonCentralCumulativeChiSquaredDistribution;
import org.jquantlib.math.distributions.InverseNonCentralCumulativeChiSquaredDistribution;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.fail;

public class NonCentralChiSquaredDistributionTest {

    @Test
    public void cdfMatchesCpp() {
        runFingerprint("cdf");
    }

    @Test
    public void pdfMatchesCpp() {
        runFingerprint("pdf");
    }

    @Test
    public void inverseCdfRoundTripsAtCdfX() {
        runFingerprint("inv_cdf_at_cdf_x");
    }

    private static void runFingerprint(final String key) {
        final ReferenceReader reader = ReferenceReader.load("math/distributions/noncentral_chi_squared");
        final Case c = reader.getCase("noncentral_chi_squared_grid");
        final JSONObject exp = (JSONObject) c.expectedRaw();
        final JSONArray samples = exp.getJSONArray("samples");
        for (int i = 0; i < samples.length(); i++) {
            final JSONObject s = samples.getJSONObject(i);
            final double degrees = s.getDouble("degrees");
            final double ncp = s.getDouble("ncp");
            final double x = s.getDouble("x");
            final double expected = s.getDouble(key);
            final double got;
            switch (key) {
                case "cdf":
                    got = new NonCentralCumulativeChiSquaredDistribution(degrees, ncp).op(x);
                    break;
                case "pdf":
                    got = new NonCentralChiSquaredDistribution(degrees, ncp).op(x);
                    break;
                case "inv_cdf_at_cdf_x":
                    final double cdfX = new NonCentralCumulativeChiSquaredDistribution(degrees, ncp).op(x);
                    got = new InverseNonCentralCumulativeChiSquaredDistribution(degrees, ncp).op(cdfX);
                    // For the round-trip we expect the original x back, not the published
                    // inv_cdf_at_cdf_x value (which was computed by C++ the same way and
                    // should agree with x at tight tier).
                    break;
                default:
                    throw new IllegalArgumentException(key);
            }
            if (!Tolerance.tight(got, expected)) {
                fail(key + "[" + i + "] (degrees=" + degrees + ", ncp=" + ncp + ", x=" + x
                        + "): expected=" + expected + " got=" + got);
            }
        }
    }
}
```

The test won't compile yet (CDF and inverse CDF classes don't exist). That's the correct red state.

- [ ] **Step 3:** Port the CDF class.

```java
// jquantlib/src/main/java/org/jquantlib/math/distributions/NonCentralCumulativeChiSquaredDistribution.java
/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Port of QuantLib v1.42.1 NonCentralCumulativeChiSquareDistribution
 (ql/math/distributions/chisquaredistribution.{hpp,cpp}).
 */
package org.jquantlib.math.distributions;

import org.jquantlib.math.Ops;

/**
 * Cumulative distribution function of the non-central chi-squared
 * distribution. Implementation mirrors C++ v1.42.1
 * NonCentralCumulativeChiSquareDistribution: Sankaran approximation
 * for medium-to-large ncp; switches to a Patnaik-style series for
 * small ncp where the approximation is less accurate.
 */
public class NonCentralCumulativeChiSquaredDistribution implements Ops.DoubleOp {

    private final double df_;
    private final double ncp_;

    public NonCentralCumulativeChiSquaredDistribution(final double df, final double ncp) {
        this.df_ = df;
        this.ncp_ = ncp;
    }

    @Override
    public double op(final double x) {
        // Port the body from C++ NonCentralCumulativeChiSquareDistribution::operator()(Real x)
        // line-by-line, preserving the branch thresholds and series-truncation rules.
        // See chisquaredistribution.cpp for the exact source.
        // (The implementer should copy the C++ body here verbatim, translating
        // Real → double, std::pow → Math.pow, std::sqrt → Math.sqrt, std::exp → Math.exp,
        // std::log → Math.log, std::tgamma → org.apache.commons.math3.special.Gamma.gamma
        // or the existing Java GammaDistribution helper if available.)
        throw new UnsupportedOperationException("Implementer: port chisquaredistribution.cpp body here");
    }
}
```

The implementer must replace the `throw` with the actual port. The C++ body is ~30-50 LOC depending on Sankaran+Patnaik series structure. Use `org.jquantlib.math` helpers for `Gamma` / `incompleteGamma` if they exist (search `jquantlib/src/main/java/org/jquantlib/math/distributions/` for existing chi-squared / gamma machinery first); otherwise port the C++'s helper inline as a private static method.

- [ ] **Step 4:** Run the CDF test.

```bash
(cd jquantlib && mvn test -Dtest='NonCentralChiSquaredDistributionTest#cdfMatchesCpp') 2>&1 | tail -10
```

Expected: PASS at tight tier on all 6 sample points. If the test fails on one of the Sankaran/Patnaik-region boundary points (small ncp ~20 or large ncp ~600), the branch threshold copied from C++ may have been transcribed wrong — re-check against `chisquaredistribution.cpp`.

If you cannot match tight tier across all 6 points and the diff is in the 1e-12 to 1e-10 range, **STOP and report DONE_WITH_CONCERNS** — do not loosen the tolerance. The Phase-2b WI-3 Task 3.4 implementer documented this exact ~1.5e-12 drift; the whole point of WI-1 is to eliminate it. If it persists, the port is wrong somewhere.

### Task 1.3: Port `NonCentralChiSquaredDistribution` (PDF) and the inverse CDF (commit 1, part C)

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/math/distributions/NonCentralChiSquaredDistribution.java` — replace the existing Java impl with a fresh v1.42.1 port (PDF only — C++ `NonCentralChiSquareDistribution::operator()(Real x)`).
- Create: `jquantlib/src/main/java/org/jquantlib/math/distributions/InverseNonCentralCumulativeChiSquaredDistribution.java` — port C++ `InverseNonCentralCumulativeChiSquareDistribution`.

- [ ] **Step 1:** Inspect callers of the existing `NonCentralChiSquaredDistribution` to confirm the swap won't break them:

```bash
grep -rn "NonCentralChiSquaredDistribution\|NonCentralChiSquareDistribution" jquantlib/src/main/java jquantlib/src/test/java 2>&1 | head -20
```

Note all caller sites. If any rely on a Java-specific constructor signature or method name not present in C++, the port needs to preserve those (for Phase 2c) — flag in the implementer's commit message and consider deprecating in Phase 2d.

- [ ] **Step 2:** Replace the existing PDF class body with a fresh v1.42.1 port. Same translation rules as CDF (Real → double, std::pow → Math.pow, etc.). C++ ref body is the `NonCentralChiSquareDistribution::operator()(Real x)` method.

- [ ] **Step 3:** Create the inverse CDF class.

```java
// jquantlib/src/main/java/org/jquantlib/math/distributions/InverseNonCentralCumulativeChiSquaredDistribution.java
/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Port of QuantLib v1.42.1 InverseNonCentralCumulativeChiSquareDistribution
 (ql/math/distributions/chisquaredistribution.{hpp,cpp}).
 */
package org.jquantlib.math.distributions;

import org.jquantlib.math.Ops;

/**
 * Inverse CDF of the non-central chi-squared distribution. Mirrors
 * C++ v1.42.1 InverseNonCentralCumulativeChiSquareDistribution:
 * Brent / secant root-finder on the CDF.
 */
public class InverseNonCentralCumulativeChiSquaredDistribution implements Ops.DoubleOp {

    private final double df_;
    private final double ncp_;

    public InverseNonCentralCumulativeChiSquaredDistribution(final double df, final double ncp) {
        this.df_ = df;
        this.ncp_ = ncp;
    }

    @Override
    public double op(final double u) {
        // Port the body from C++. Typically a Brent / Newton iteration on
        // NonCentralCumulativeChiSquaredDistribution(df_, ncp_).op(x) - u = 0
        // with sensible initial brackets.
        throw new UnsupportedOperationException("Implementer: port chisquaredistribution.cpp body here");
    }
}
```

Replace the throw with the actual C++ port.

- [ ] **Step 4:** Run all three test methods.

```bash
(cd jquantlib && mvn test -Dtest=NonCentralChiSquaredDistributionTest) 2>&1 | tail -10
```

Expected: 3 tests, all pass at tight tier.

- [ ] **Step 5:** Run the full suite to confirm no callers of the old `NonCentralChiSquaredDistribution` regressed.

```bash
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
```

Expected: `Tests run: 635, Failures: 0, Errors: 0, Skipped: 22` (632 baseline + 3 new tests).

- [ ] **Step 6:** Commit the distribution port.

```bash
git add jquantlib/src/main/java/org/jquantlib/math/distributions/NonCentralChiSquaredDistribution.java \
        jquantlib/src/main/java/org/jquantlib/math/distributions/NonCentralCumulativeChiSquaredDistribution.java \
        jquantlib/src/main/java/org/jquantlib/math/distributions/InverseNonCentralCumulativeChiSquaredDistribution.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/distributions/NonCentralChiSquaredDistributionTest.java \
        migration-harness/cpp/probes/math/distributions/noncentral_chi_squared_probe.cpp \
        migration-harness/references/math/distributions/noncentral_chi_squared.json
git commit -s -m "$(cat <<'EOF'
align(math.distributions): port NonCentralChiSquared* family from v1.42.1

Replaces Java's drifting NonCentralChiSquaredDistribution (the ~1.5e-12
divergence the Phase-2b WI-3 Task 3.4 implementer surfaced) with a
fresh port of the C++ v1.42.1 trio: PDF, CDF (Sankaran/Patnaik switch),
and inverse CDF (Brent on the CDF).

Probe captures CDF + PDF + inverse-CDF round-trip at 6 (degrees, ncp, x)
tuples spanning the small/medium/large ncp regimes (each branch of the
Sankaran/Patnaik switch). Java cross-validation passes at tight tier
across all 6 points × all 3 functions.

Test count: 632 -> 635.
EOF
)"
git push origin 2c-wi1
```

Worktree A then waits for the controller to fast-forward `main` (`merge --ff-only origin/2c-wi1`).

### Task 1.4: Un-stub `CIR.discountBondOption` (commit 2)

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/CoxIngersollRoss.java`
- Modify: `migration-harness/cpp/probes/model/shortrate/coxingersollross_calibration_probe.cpp` (extend existing)
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/CoxIngersollRossCalibrationTest.java` (extend existing)

C++ ref: `migration-harness/cpp/quantlib/ql/models/shortrate/onefactormodels/coxingersollross.cpp::discountBondOption`.

- [ ] **Step 1:** Read the existing CIR `discountBondOption` body and the C++ ground truth.

```bash
sed -n '/discountBondOption/,/^    }/p' jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/CoxIngersollRoss.java
sed -n '/discountBondOption/,/^    }/p' migration-harness/cpp/quantlib/ql/models/shortrate/onefactormodels/coxingersollross.cpp
```

Identify the dead `chis`/`chit`/`z`/`call` locals the Phase-2b L4 code review flagged.

- [ ] **Step 2:** Replace the `return 0.0;` / `return 1.0;` stub returns with the C++ formula. Use `NonCentralCumulativeChiSquaredDistribution` (the new class from Task 1.2) for the chi-squared CDF calls.

The C++ formula (from `coxingersollross.cpp`) is roughly:
```
Real call = bondMaturity*chis(2.0*ncps) - strike*discountT*chit(2.0*ncpt);
return type == Option::Call ? call : call - bondMaturity + strike*discountT;
```
where `chis` and `chit` are `NonCentralCumulativeChiSquareDistribution` evaluations at specific df/ncp parameters. Translate verbatim.

- [ ] **Step 3:** Extend the existing CIR probe with a `discountBondOption` fingerprint case.

Open `migration-harness/cpp/probes/model/shortrate/coxingersollross_calibration_probe.cpp`. After the existing `discountBond` capture, add discountBondOption capture for the same `(strike, maturity, bondMaturity)` tuples:

```cpp
// Existing: const Real discountBond = model.discountBond(t, T, r0);
// Add per-tuple:
const Real callOpt = model.discountBondOption(Option::Call, strike, t, T);
const Real putOpt  = model.discountBondOption(Option::Put,  strike, t, T);
sampleArr.push_back({{"t", t}, {"T", T},
                     {"discountBond", discountBond},
                     {"call",  callOpt},
                     {"put",   putOpt}});
```

(Adjust to whatever the existing probe's tuple shape is — it was either `(strike, mat, bondMat)` or `(t, T)`. Match it.)

- [ ] **Step 4:** Regenerate the CIR reference.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2c-A
./migration-harness/generate-references.sh coxingersollross_calibration_probe 2>&1 | tail -3
```

- [ ] **Step 5:** Extend `CoxIngersollRossCalibrationTest` with assertions on the new `call` / `put` reference values at tight tier (mirror the existing `discountBond` assertion pattern).

- [ ] **Step 6:** Run the CIR test.

```bash
(cd jquantlib && mvn test -Dtest=CoxIngersollRossCalibrationTest) 2>&1 | tail -10
```

Expected: 1 test method, pass at tight tier. The test includes both `discountBond` (existing) and `discountBondOption` (new) assertions.

- [ ] **Step 7:** Run the full suite.

```bash
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
```

Expected: `Tests run: 635, Failures: 0, Errors: 0, Skipped: 22` (no new test method count — the assertion just got more samples; it still counts as 1 test).

- [ ] **Step 8:** Commit.

```bash
git add jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/CoxIngersollRoss.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/CoxIngersollRossCalibrationTest.java \
        migration-harness/cpp/probes/model/shortrate/coxingersollross_calibration_probe.cpp \
        migration-harness/references/model/shortrate/coxingersollross_calibration.json
git commit -s -m "$(cat <<'EOF'
stub(model.shortrate.onefactor): unstub CoxIngersollRoss.discountBondOption

Replaces the hard-coded 0.0/1.0 stub returns with the v1.42.1 formula
using NonCentralCumulativeChiSquaredDistribution (now correctly aligned
to C++ as of the prior commit). Removes the dead chis/chit/z/call
locals that the Phase-2b L4 code review flagged as misleading.

Probe extended with call/put fingerprints at the existing (strike,
maturity, bondMaturity) tuples; CoxIngersollRossCalibrationTest now
asserts both discountBond and discountBondOption at tight tier.

Test count: 635 (no new method; the existing test gained assertion
samples).
EOF
)"
git push origin 2c-wi1
```

### Task 1.5: Verify HestonProcess has no chi-squared-dependent stub (commit 3 conditional)

**Files (conditional):** none, OR if a stub is found:
- Modify: `jquantlib/src/main/java/org/jquantlib/processes/HestonProcess.java`
- Extend: probe + test as appropriate

- [ ] **Step 1:** Check whether `HestonProcess` actually has a `discountBondOption` method or any other chi-squared-distribution-dependent stub.

```bash
grep -n "discountBondOption\|NonCentralChi\|varianceDistribution\|throw new UnsupportedOperationException" jquantlib/src/main/java/org/jquantlib/processes/HestonProcess.java
```

- [ ] **Step 2:** Branch:
  - If no `discountBondOption` method exists AND no chi-squared-dependent stub is found in the in-scope discretizations (PartialTruncation / FullTruncation / Reflection / QuadraticExponential / QuadraticExponentialMartingale), **WI-1 is complete after Task 1.4** — no Task 1.5 commit, just update the docs:

  ```bash
  # Edit phase2b-completion.md to correct the seed-list entry
  ```
  Open `docs/migration/phase2b-completion.md`, find the "HestonProcess.discountBondOption for CIR" entry under the Phase-2c seed list, and rewrite it as:
  ```
  - **HestonProcess.discountBondOption (Phase-2b seed-list misclassification)**:
    HestonProcess does not have a discountBondOption method. The seed-list
    entry was a conflation with CIR.discountBondOption (which IS the stub
    actually depending on the chi-squared distribution). Resolved in Phase
    2c by porting the chi-squared distribution family AND unstubbing CIR;
    HestonProcess required no change. (The chi-squared-dependent
    NonCentralChiSquareVariance and BroadieKaya× HestonProcess
    discretizations remain Phase-2c-out-of-scope per phase2c-design §2.2.)
  ```
  Commit the docs fix:
  ```bash
  git add docs/migration/phase2b-completion.md
  git commit -s -m "docs(migration): correct phase2b-completion seed list — HestonProcess.discountBondOption misclassified"
  git push origin 2c-wi1
  ```
  - If a `discountBondOption` method DOES exist as a stub returning hard-coded values, port it from C++ `hestonprocess.cpp::discountBondOption` (or wherever the C++ HestonProcess defines an option-pricing closed form), extend `hestonprocess_qe_probe.cpp` with a fingerprint case, extend `HestonProcessTest` with the assertion. Commit shape mirrors Task 1.4.

  ```bash
  git commit -s -m "stub(processes): unstub HestonProcess.discountBondOption via NonCentralCumulativeChiSquaredDistribution"
  ```

WI-1 commit count is therefore **3 (CIR-only path) or 4 (CIR + HestonProcess path)**.

After Task 1.5 (or its docs-correction), WI-1 is fully done. Worktree A pushes its branch tip; controller fast-forwards `main` to include all WI-1 commits, then triggers other worktrees' rebases.

---

## Layer 2 — WI-2 SABR α-default formula (worktree B)

All work in this layer happens in `/Users/josemoya/eclipse-workspace/jquantlib-2c-B` on branch `2c-wi23`. Independent of L1 — runs in parallel with WI-1.

### Task 2.1: Replace SABR α-default formula

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/math/interpolations/SABRInterpolation.java`

C++ ref: `migration-harness/cpp/quantlib/ql/termstructures/volatilities/interpolation/xabrinterpolation.hpp::SABRSpecs::defaultValues`.

- [ ] **Step 1:** Locate the α default in Java's SABRCoeffHolder.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2c-B
sed -n '/SABRCoeffHolder/,/validateSabrParameters/p' jquantlib/src/main/java/org/jquantlib/math/interpolations/SABRInterpolation.java
```

Find the `else` branch of the `alpha_ != Constants.NULL_REAL` check (Phase-2b WI-4 fixed the sentinel; the `else` body sets `alpha_ = Math.sqrt(0.2);`).

- [ ] **Step 2:** Replace with the forward-aware formula matching C++.

C++ source (from `xabrinterpolation.hpp` SABR specialization, `defaultValues`):
```cpp
if (params[0] == Null<Real>())
    params[0] = (params[1] < 0.9999)
                ? 0.2 * std::pow(forward, 1.0 - params[1])
                : std::sqrt(0.2);
```

Java rewrite:
```java
} else {
    // C++ xabrinterpolation.hpp SABRSpecs::defaultValues:
    // alpha = (beta < 0.9999) ? 0.2 * pow(forward, 1 - beta) : sqrt(0.2)
    alpha_ = (beta_ < 0.9999)
            ? 0.2 * Math.pow(forward, 1.0 - beta_)
            : Math.sqrt(0.2);
}
```

Note: this requires `beta_` to be set BEFORE `alpha_`'s default branch. Since the SABRCoeffHolder ctor processes the four params in order (alpha, beta, nu, rho), the alpha default would be computed BEFORE beta is set if you naively reorder. Re-check the Phase-2b WI-4 sentinel-fix code: which order are the four `if (X_ != Constants.NULL_REAL)` blocks in?

If the code processes alpha first then beta, you must reorder the defaulting so `beta_` is finalized before `alpha_`'s default branch runs. Either:
- (a) Move the beta defaulting block above the alpha defaulting block (preserving the IsFixed flag handling).
- (b) Compute `alpha_` default at the END after all four IsFixed branches have run (using the now-final beta_ value).

(b) is safer because it doesn't reshuffle the Phase-2b WI-4 fix.

- [ ] **Step 3:** Run the existing SABR tests to confirm no regression.

```bash
(cd jquantlib && mvn test -Dtest='SABRInterpolationTest,InterpolationTest#testSabrInterpolation') 2>&1 | tail -10
```

Expected: both green (the existing tests' calibration paths absorb the α change at 5e-8 tolerance).

- [ ] **Step 4:** Run the full suite.

```bash
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
```

Expected: `Tests run: 632, Failures: 0, Errors: 0, Skipped: 22` (no new test count change — WI-3 adds the consumer that asserts on α).

If any previously-passing test fails, the reorder/restructuring of the defaulting blocks broke something — STOP and report BLOCKED.

- [ ] **Step 5:** Commit.

```bash
git add jquantlib/src/main/java/org/jquantlib/math/interpolations/SABRInterpolation.java
git commit -s -m "$(cat <<'EOF'
align(math.interpolations): SABR alpha-default formula matches v1.42.1

Replaces Java's previous Math.sqrt(0.2) constant alpha-default in
SABRCoeffHolder with C++ xabrinterpolation.hpp SABRSpecs::defaultValues
forward-aware form: alpha = (beta < 0.9999) ? 0.2 * pow(forward, 1 - beta)
: sqrt(0.2).

Recorded as a Phase-2c follow-up in phase2a-carveouts.md::WI-2-carveout-SABR
after the Phase-2b WI-4 sentinel fix unblocked the original SABR tests.
WI-3 (worktree B continuation) wires up the orphan probe consumer that
will assert on alpha post-construction at tight tier.

Test count unchanged at 632.
EOF
)"
git push origin 2c-wi23
```

After Task 2.1 lands and the controller fast-forwards `main`, worktree B continues to L3 (WI-3) without rebasing — Task 2.1 was self-contained and L3 is an additive change in the same worktree.

---

## Layer 3 — WI-3 SABR test hygiene (worktree B, after L2)

Within worktree B, after L2's commit lands on its branch.

### Task 3.1: Add Java consumer for the orphan SABR probe

**Files:**
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationConstructionTest.java`

- [ ] **Step 1:** Inspect the existing reference JSON to confirm the post-defaultValues α value matches what WI-2 will produce in Java.

```bash
cat /Users/josemoya/eclipse-workspace/jquantlib-2c-B/migration-harness/references/math/interpolations/sabr_interpolation.json
```

Expected: `"alpha_post_default": 0.0489897...` (= 0.2 * sqrt(0.06)) for the existing case `forward=0.06, beta_default=0.5`. Java's WI-2-aligned formula produces exactly this for the same inputs.

- [ ] **Step 2:** Create the construction test.

```java
// jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationConstructionTest.java
/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated test for SABRCoeffHolder construction defaults.
 See phase2c-design §3.3 WI-3.
 */
package org.jquantlib.testsuite.math.interpolations;

import java.util.Arrays;
import java.util.List;

import org.jquantlib.math.Constants;
import org.jquantlib.math.interpolations.SABRInterpolation;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Cross-validates SABRInterpolation's post-construction parameter
 * values (after defaultValues() fills in NULL_REAL guesses) against
 * the C++ v1.42.1 sabr_interpolation_probe reference. This test
 * lights up after Phase-2c WI-2's alpha-default formula align-fix
 * — before WI-2 alpha would have been Math.sqrt(0.2) and would
 * mismatch C++'s 0.2*pow(forward, 1-beta) at tight tier.
 */
public class SABRInterpolationConstructionTest {

    @Test
    public void allFourParams_postConstruction_matchCppDefaults() {
        final ReferenceReader reader = ReferenceReader.load("math/interpolations/sabr_interpolation");
        final Case c = reader.getCase("nullguess_defaults");
        final JSONObject in = c.inputs();
        final JSONObject exp = (JSONObject) c.expectedRaw();

        final JSONArray strikesJson = in.getJSONArray("strikes");
        final JSONArray volsJson = in.getJSONArray("volatilities");
        final double[] strikes = new double[strikesJson.length()];
        final double[] volatilities = new double[volsJson.length()];
        for (int i = 0; i < strikes.length; i++) {
            strikes[i] = strikesJson.getDouble(i);
            volatilities[i] = volsJson.getDouble(i);
        }
        final double expiry = in.getDouble("expiry");
        final double forward = in.getDouble("forward");

        // Construct with NULL_REAL for all four guesses; defaultValues() fills them in.
        final SABRInterpolation sabr = new SABRInterpolation(
                strikes, volatilities, expiry, forward,
                Constants.NULL_REAL, Constants.NULL_REAL,
                Constants.NULL_REAL, Constants.NULL_REAL,
                false, false, false, false,    // *IsFixed — all free
                false,                          // vegaWeighted
                null, null);                    // EndCriteria, OptimizationMethod

        // Note: we DON'T call sabr.update() — that would trigger calibration
        // and overwrite the defaults. Just assert post-construction state.

        assertParamMatches(exp, "alpha_post_default", sabr.alpha());
        assertParamMatches(exp, "beta_post_default",  sabr.beta());
        assertParamMatches(exp, "nu_post_default",    sabr.nu());
        assertParamMatches(exp, "rho_post_default",   sabr.rho());
    }

    private static void assertParamMatches(final JSONObject exp, final String key, final double got) {
        final double expected = exp.getDouble(key);
        if (!Tolerance.tight(got, expected)) {
            fail(key + ": expected=" + expected + " got=" + got);
        }
    }
}
```

Verify the SABRInterpolation constructor signature in Java matches what's used here. If parameter order differs (e.g., `(strikes, volatilities, expiry, forward, alpha, beta, nu, rho, ...)` vs the assumed order above), adjust the test call.

- [ ] **Step 3:** Run the construction test.

```bash
(cd jquantlib && mvn test -Dtest=SABRInterpolationConstructionTest) 2>&1 | tail -10
```

Expected: PASS at tight tier on all four params (α, β, ν, ρ). The WI-2 align-fix just above means α matches C++'s 0.0489897... value.

If FAIL on α specifically with a 1.5e-12-class drift, WI-2 didn't apply correctly — go back to L2.

If FAIL on a different parameter (β/ν/ρ), the C++ defaults captured during Phase-2b WI-4 don't actually match Java's defaults — re-investigate the SABRCoeffHolder ctor's `else` branches against C++ `xabrinterpolation.hpp::SABRSpecs::defaultValues`.

### Task 3.2: Add explanatory comments on IsFixed loops

**Files:**
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationTest.java`
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/InterpolationTest.java`

- [ ] **Step 1:** In each file, locate the inner-loop block iterating over `isAlphaFixed[k_a]` × `isBetaFixed[k_b]` × `isNuFixed[k_n]` × `isRhoFixed[k_r]`.

```bash
grep -n "isAlphaFixed\|isBetaFixed\|isNuFixed\|isRhoFixed" \
    jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationTest.java \
    jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/InterpolationTest.java
```

- [ ] **Step 2:** Add an explanatory comment IMMEDIATELY ABOVE the outermost of the four nested for-loops in each file:

```java
// NOTE: this 16-IsFixed combination loop is currently a stability check, NOT a
// constraint-topology check. With NULL_REAL guesses (the test's setup at lines
// XYZ above), the SABRCoeffHolder ctor falls into all four `else` branches and
// every *IsFixed_ flag stays at its default `false` regardless of the loop
// variables. The 16 iterations exercise the same all-free calibration 16 times.
// Strengthening this to actually exercise distinct constraint topologies would
// require seeded guesses for the IsFixed=true iterations — Phase 2c WI-3
// captured the option but deferred the rework as out-of-scope.
// See phase2c-design §3.3 + phase2b-completion.md "Code-review followups for Phase 2c".
for (int j=0; j<methods_.size(); ++j) {
    ...
```

Use the actual line number for the test's NULL_REAL setup in place of `XYZ`.

- [ ] **Step 3:** Run both tests to confirm no regression (the change is comment-only).

```bash
(cd jquantlib && mvn test -Dtest='SABRInterpolationTest,InterpolationTest#testSabrInterpolation,SABRInterpolationConstructionTest') 2>&1 | tail -10
```

Expected: all green.

- [ ] **Step 4:** Run the full suite.

```bash
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
```

Expected: `Tests run: 633, Failures: 0, Errors: 0, Skipped: 22` (632 + 1 new construction test).

- [ ] **Step 5:** Commit.

```bash
git add jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationConstructionTest.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationTest.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/InterpolationTest.java
git commit -s -m "$(cat <<'EOF'
test(math.interpolations): consume orphan SABR probe + comment IsFixed loops

Adds SABRInterpolationConstructionTest asserting all four post-
construction SABR params (alpha, beta, nu, rho) match the C++ v1.42.1
defaults from sabr_interpolation_probe at tight tier. Lights up after
Phase-2c WI-2's alpha-default formula align-fix; previously orphan
reference (Phase-2b L4 code-review finding).

Adds explanatory comments above the 16-IsFixed combination loops in
SABRInterpolationTest and InterpolationTest.testSabrInterpolation
clarifying the loop is a stability check, not a constraint-topology
check, when guesses are NULL_REAL. Strengthening to seeded guesses
deferred to a future SABR-improvement pass.

Test count: 632 -> 633.
EOF
)"
git push origin 2c-wi23
```

WI-3 commit count is 1–2 (combine into a single commit as above, or split if either change drags more than expected).

---

## Layer 4 — WI-4 HullWhite latent items (worktree C)

All work in this layer happens in `/Users/josemoya/eclipse-workspace/jquantlib-2c-C` on branch `2c-wi4`. Independent of L1/L2/L3 — runs in parallel.

C++ ref: `migration-harness/cpp/quantlib/ql/models/shortrate/onefactormodels/hullwhite.{hpp,cpp}`.

### Task 4.1: Port the missing 5-arg `discountBondOption` overload

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/HullWhite.java`
- Extend: `migration-harness/cpp/probes/model/shortrate/hullwhite_calibration_probe.cpp` + reference JSON
- Extend: `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/HullWhiteCalibrationTest.java`

- [ ] **Step 1:** Read C++'s 5-arg `discountBondOption` to identify its body.

```bash
grep -n "discountBondOption" migration-harness/cpp/quantlib/ql/models/shortrate/onefactormodels/hullwhite.{hpp,cpp}
```

The 5-arg form has signature `discountBondOption(Option::Type, Real strike, Time maturity, Time bondStart, Time bondMaturity)`. Note this differs from the 4-arg form by including `bondStart` (the start time of the bond, not just its maturity).

- [ ] **Step 2:** Add the overload to Java's `HullWhite.java`. The C++ body uses `B(bondStart, bondMaturity)` and a vol parameter that depends on `(bondStart - maturity)` rather than `maturity` alone.

- [ ] **Step 3:** Extend the existing `hullwhite_calibration_probe.cpp` with a new sub-array of `(strike, maturity, bondStart, bondMaturity, call, put)` tuples for the 5-arg form (3 representative cases).

- [ ] **Step 4:** Regenerate the reference.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2c-C
./migration-harness/generate-references.sh hullwhite_calibration_probe 2>&1 | tail -3
```

- [ ] **Step 5:** Add a test method to `HullWhiteCalibrationTest` that asserts the 5-arg outputs at tight tier.

- [ ] **Step 6:** Run targeted + full suite.

```bash
(cd jquantlib && mvn test -Dtest=HullWhiteCalibrationTest) 2>&1 | tail -5
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
```

Expected: HullWhiteCalibrationTest gains a method (now 2 test methods); full suite +1 test.

- [ ] **Step 7:** Commit.

```bash
git add jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/HullWhite.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/HullWhiteCalibrationTest.java \
        migration-harness/cpp/probes/model/shortrate/hullwhite_calibration_probe.cpp \
        migration-harness/references/model/shortrate/hullwhite_calibration.json
git commit -s -m "stub(model.shortrate.onefactor): port HullWhite 5-arg discountBondOption overload from v1.42.1"
git push origin 2c-wi4
```

### Task 4.2: Fix `convexityBias` formula

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/HullWhite.java`
- Extend: probe + test for a `convexityBias` fingerprint

- [ ] **Step 1:** Diff Java vs C++ `convexityBias`. Per the Phase-2b L3 code review, Java uses `(1.0 - exp(-z)) * (futureRate + 1.0/(T-t))` while C++ uses `(1.0 - exp(-z*tempDeltaT)) * (futureRate + 1.0/deltaT)` with a `deltaT < QL_EPSILON ? z : ...` branch and a small-`a` Taylor fallback.

```bash
grep -n "convexityBias" jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/HullWhite.java migration-harness/cpp/quantlib/ql/models/shortrate/onefactormodels/hullwhite.cpp
```

Read both bodies side by side.

- [ ] **Step 2:** Replace Java's body with the C++ form line-by-line.

- [ ] **Step 3:** Extend `hullwhite_calibration_probe.cpp` with `convexityBias` fingerprint (3 cases at varying `a` to cover the small-`a` fallback branch).

- [ ] **Step 4:** Regenerate reference, add Java test method, run suite. Expected delta: +1 test.

- [ ] **Step 5:** Commit.

```bash
git commit -s -m "align(model.shortrate.onefactor): HullWhite.convexityBias matches v1.42.1"
git push origin 2c-wi4
```

### Task 4.3: Fix `tree(grid)` `impl.set` key (time, not grid index)

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/HullWhite.java`
- Extend: a probe + test that exercises the tree-pricing path at a small grid

- [ ] **Step 1:** Locate the `tree(grid)` body and the `// ?????` self-flagged line.

```bash
grep -n "tree(grid\|impl.set\|// ?????" jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/HullWhite.java
```

- [ ] **Step 2:** Change `impl.set(grid.index(i), value)` to `impl.set(grid.get(i), value)` (or whatever the Java idiom for "get the time value at index i" is for the `TimeGrid` type — read the C++ `grid[i]` semantics and find the Java analogue).

- [ ] **Step 3:** Remove the `// ?????` comment. Replace with a brief note like `// Aligned to C++ hullwhite.cpp tree(grid) — key is time value, not grid index.`

- [ ] **Step 4:** Add a fingerprint test that builds the tree at a small grid (e.g., 4 time steps over 1 year), reads `tree.discount(i, j)` at fixed `(i, j)`, asserts against C++ probe values at tight tier.

- [ ] **Step 5:** Run targeted + full suite. Expected delta: +1 test.

- [ ] **Step 6:** Commit.

```bash
git commit -s -m "align(model.shortrate.onefactor): HullWhite tree(grid) key is time value not grid index"
git push origin 2c-wi4
```

After Task 4.3, WI-4 is fully done (3 commits in worktree C). Controller fast-forwards `main`.

---

## Layer 5 — WI-5 BlackKarasinski tree-pricing (worktree D, time-boxed)

All work in this layer happens in `/Users/josemoya/eclipse-workspace/jquantlib-2c-D` on branch `2c-wi5`. Independent of L1/L2/L3/L4 — runs in parallel.

This layer has a fix-or-carve outcome. Per the design's WI-5 time-box: investigate whether `BlackKarasinski.tree(grid)` can be un-stubbed without new infrastructure outside the 61 packages. If yes (likely — the Phase-2c plan-writer confirmed `OneFactorModel.ShortRateTree` is already ported in Java with a 2-arg ctor that does Brent-solver calibration), proceed to fix. If A4 fires, carve.

### Task 5.1: Investigate `BlackKarasinski.tree(grid)`

**Files:** read-only.

- [ ] **Step 1:** Read BK's `tree(grid)` and `dynamics()` bodies.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2c-D
sed -n '/tree(.*grid/,/^    }/p' jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/BlackKarasinski.java
sed -n '/dynamics()/,/^    }/p' jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/BlackKarasinski.java
```

Identify the `numericTree = null` placeholder and what's needed to construct a real `ShortRateTree`.

- [ ] **Step 2:** Read C++ BK's tree path.

```bash
sed -n '/tree(/,/^    }/p' migration-harness/cpp/quantlib/ql/models/shortrate/onefactormodels/blackkarasinski.cpp
```

C++ `BlackKarasinski::dynamics()` builds a tree via `OneFactorModel::ShortRateTree` (the same nested class Java already has at `OneFactorModel.java:143`) using `TermStructureFittingParameter` and Brent calibration per grid step.

- [ ] **Step 3:** Verify Java's `OneFactorModel.ShortRateTree` 2-arg ctor handles the BK path:

```bash
grep -n "TermStructureFittingParameter\|class ShortRateTree" jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/OneFactorModel.java
grep -n "TermStructureFittingParameter" jquantlib/src/main/java/org/jquantlib/model/shortrate/
```

If `TermStructureFittingParameter` exists in Java with a `NumericalImpl` inner class, **diagnosis (a)** holds — fix path is feasible. Proceed to Task 5.2.

If `TermStructureFittingParameter` is missing or its `NumericalImpl` inner class is absent/stubbed, **diagnosis (b)** — assess whether porting it stays inside the 61 packages. If yes, port-and-proceed. If it pulls in further unported lattice/calibration infrastructure, **A4 fires → diagnosis (c)** → skip to Task 5.3 (carve).

### Task 5.2: Un-stub BK tree-pricing (commit 1, fix path only)

**Files (fix path):**
- Modify: `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/BlackKarasinski.java`
- (Possibly) modify or create: `jquantlib/src/main/java/org/jquantlib/model/TermStructureFittingParameter.java` if missing pieces need filling
- Create: `migration-harness/cpp/probes/model/shortrate/blackkarasinski_tree_probe.cpp`
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/BlackKarasinskiCalibrationTest.java` — upgrade from reflection-only to fingerprint

Only execute this task if Task 5.1 → diagnosis (a) or (b)-with-existing-package.

- [ ] **Step 1:** Replace `final ShortRateTree numericTree = null;` in BK's `tree(grid)` body with a real `ShortRateTree` construction mirroring C++:

```java
final TermStructureFittingParameter phi = new TermStructureFittingParameter(termStructure());
final OneFactorModel.ShortRateTree numericTree = new OneFactorModel.ShortRateTree(
        new TrinomialTree(process, grid),
        new Dynamics(phi, a(), sigma()),
        (TermStructureFittingParameter.NumericalImpl) phi.implementation(),
        grid);
return numericTree;
```

Adjust types/visibilities to match what's actually accessible in Java (the Phase-2b WI-3 code review noted BK's `Dynamics` constructor signature; align accordingly).

- [ ] **Step 2:** Build the fingerprint probe.

```cpp
// migration-harness/cpp/probes/model/shortrate/blackkarasinski_tree_probe.cpp
#include <ql/version.hpp>
#include <ql/models/shortrate/onefactormodels/blackkarasinski.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/timegrid.hpp>
#include "../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("model/shortrate/blackkarasinski_tree", QL_VERSION,
                        "blackkarasinski_tree_probe");

    Settings::instance().evaluationDate() = Date(22, April, 2026);
    Handle<YieldTermStructure> ts(
        ext::make_shared<FlatForward>(Date(22, April, 2026), 0.04, Actual365Fixed()));

    BlackKarasinski model(ts, 0.1, 0.01);
    TimeGrid grid(/*end*/1.0, /*steps*/4);
    auto tree = model.tree(grid);

    json sampleArr = json::array();
    for (Size i = 0; i < grid.size(); ++i) {
        for (Size j = 0; j < tree->size(i); ++j) {
            sampleArr.push_back({{"i", i}, {"j", j},
                                 {"discount",   tree->discount(i, j)},
                                 {"underlying", tree->underlying(i, j)}});
        }
    }

    out.addCase("bk_tree_grid_4steps",
        json{{"r_curve", 0.04}, {"a", 0.1}, {"sigma", 0.01},
             {"grid_end", 1.0}, {"grid_steps", 4}},
        json{{"samples", sampleArr}});

    out.write();
    return 0;
}
```

```bash
mkdir -p migration-harness/cpp/probes/model/shortrate
./migration-harness/generate-references.sh blackkarasinski_tree_probe 2>&1 | tail -3
```

- [ ] **Step 3:** Upgrade `BlackKarasinskiCalibrationTest` from reflection-only to a `discountBond`-style fingerprint. Add a new test method `treeFingerprint_matchesCpp` that builds BK with the same params, builds the tree at the same grid, asserts `tree.discount(i, j)` and `tree.underlying(i, j)` at tight tier.

- [ ] **Step 4:** Run targeted + full suite.

```bash
(cd jquantlib && mvn test -Dtest=BlackKarasinskiCalibrationTest) 2>&1 | tail -5
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
```

Expected: BK test gains 1 method (now 2 methods); full suite +1 test.

If the fingerprint asserts fail at tight tier (small numeric difference from the Brent-solver convergence threshold), STOP and report DONE_WITH_CONCERNS — do not loosen.

- [ ] **Step 5:** Commit.

```bash
git add jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/BlackKarasinski.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/BlackKarasinskiCalibrationTest.java \
        migration-harness/cpp/probes/model/shortrate/blackkarasinski_tree_probe.cpp \
        migration-harness/references/model/shortrate/blackkarasinski_tree.json
git commit -s -m "$(cat <<'EOF'
stub(model.shortrate.onefactor): unstub BlackKarasinski tree-pricing

Replaces the `final ShortRateTree numericTree = null;` placeholder
in BK.tree(grid) with a real ShortRateTree built via the inherited
OneFactorModel.ShortRateTree 2-arg ctor (Brent-solver per grid step
against the term structure). Mirrors C++ blackkarasinski.cpp's tree
construction; uses the existing Java TermStructureFittingParameter.

Probe: blackkarasinski_tree_probe captures discount and underlying
fingerprints at all (i, j) cells of a 4-step grid for a flat-rate
4% term structure with a=0.1, sigma=0.01. BlackKarasinskiCalibrationTest
upgraded from reflection-only (Phase-2b WI-3 fallback) to fingerprint
at tight tier.

Test count: <baseline> -> <baseline + 1>.
EOF
)"
git push origin 2c-wi5
```

### Task 5.3: Carve to Phase 2d (carve path only)

Only execute this task if Task 5.1 → diagnosis (c) (A4 fires).

**Files:**
- Create: `docs/migration/phase2c-carveouts.md`

- [ ] **Step 1:** Create the carveout doc.

```markdown
# Phase 2c — Carveouts

**Last update:** 2026-04-25

Items originally scoped into Phase 2c that revealed out-of-scope
infrastructure dependencies on un-stubbing and have been deferred.

---

## WI-5-BK-tree — BlackKarasinski tree-pricing un-stub

**File:** `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/BlackKarasinski.java`

**Observed:**
WI-5 Task 5.1 investigation found that un-stubbing
`BlackKarasinski.tree(grid)` requires <specific infrastructure> beyond
the existing 61 packages. <Specific failure mode discovered.>

**Root cause:**
<One paragraph diagnosis: which class/method needs porting first;
which packages they live in; why they trip A4.>

**Disposition:**
A4 trigger fired automatically per the Phase-2c WI-5 time-box gate.
Carved to Phase 2d (folded into the CapHelper/IborLeg work or split
out as its own future phase, depending on Phase-2d design).
`BlackKarasinskiCalibrationTest` continues to use the Phase-2b
reflection-based test (commit 072d25d).
```

- [ ] **Step 2:** Commit + push.

```bash
git add docs/migration/phase2c-carveouts.md
git commit -s -m "$(cat <<'EOF'
docs(migration): re-carve BK tree-pricing from Phase 2c WI-5 to Phase 2d

The WI-5 time-boxed investigation localized BlackKarasinski.tree(grid)
un-stubbing to <one-sentence summary>. The fix requires <infrastructure
description>, which is outside the 61-package fence and trips A4.
Per design §3.5, A4 firing inside WI-5 is the carve gate: no further
deliberation, ship Phase 2c without the BK tree-pricing fix.

BlackKarasinskiCalibrationTest stays as the reflection-based test
landed in Phase-2b WI-3 (commit 072d25d). Test count and skip count
unchanged.
EOF
)"
git push origin 2c-wi5
```

WI-5 commit count is 0 (carve, doc-only), 1 (carve, with the docs commit above), or 1 (fix). After this task, worktree D's contribution to L5 is done.

---

## Layer 6 — Completion doc + tag (main checkout)

After all four worktrees (A/B/C/D) have landed their commits to `main` and the controller has confirmed full-suite green at the consolidated tip, the L6 completion happens on the main checkout (not in a worktree).

### Task 6.1: Final consolidation check

- [ ] **Step 1:** From the main checkout, fetch and verify all worktree branches are merged:

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git log --oneline e52f274..HEAD | cat
```

Expected: 6–14 commits (depending on WI-5 outcome and whether HestonProcess required a Task 1.5 commit), spanning:
- WI-1: 3–4 commits (chi-squared port + CIR unstub + optional HestonProcess unstub or docs correction)
- WI-2: 1 commit (SABR α-default)
- WI-3: 1 commit (SABR test hygiene)
- WI-4: 3 commits (HullWhite latent items)
- WI-5: 0–1 commit (fix or carve)

- [ ] **Step 2:** Run the full suite from main.

```bash
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
```

Expected fix path: `Tests run: 640–644, Failures: 0, Errors: 0, Skipped: 22`.
Expected carve path: `Tests run: 637–640, Failures: 0, Errors: 0, Skipped: 22`.

- [ ] **Step 3:** Run the scanner — expect `work_in_progress: 2` unchanged.

```bash
python3 tools/stub-scanner/scan_stubs.py | tail -3
grep '"id"' docs/migration/stub-inventory.json
```

Expected: 2 stubs (CapHelper + G2).

### Task 6.2: Write `phase2c-completion.md`

**Files:**
- Create: `docs/migration/phase2c-completion.md`

- [ ] **Step 1:** Use `phase2b-completion.md` as the template; adapt sections:
  - Header: date, tip commit, predecessor tag, target tag.
  - Exit criteria table mapping to design §6.
  - Per-WI summary with commit hashes.
  - Final scanner state.
  - Test suite final state.
  - Deviations from plan.
  - Phase 2d/2e seed list (carry forward Phase-2c remainders + Phase-2c carveouts; refresh based on actual outcomes).

- [ ] **Step 2:** Commit + push.

```bash
git add docs/migration/phase2c-completion.md
git commit -s -m "docs(migration): Phase 2c completion report"
git push origin main
```

### Task 6.3: Tag and clean up worktrees

- [ ] **Step 1:** Tag.

```bash
git tag -a jquantlib-phase2c-complete -m "Phase 2c complete. See docs/migration/phase2c-completion.md."
git push origin jquantlib-phase2c-complete
```

- [ ] **Step 2:** Verify tag at HEAD.

```bash
git tag --points-at HEAD
git log --oneline -3
```

- [ ] **Step 3:** Clean up worktrees and their branches.

```bash
git worktree remove /Users/josemoya/eclipse-workspace/jquantlib-2c-A
git worktree remove /Users/josemoya/eclipse-workspace/jquantlib-2c-B
git worktree remove /Users/josemoya/eclipse-workspace/jquantlib-2c-C
git worktree remove /Users/josemoya/eclipse-workspace/jquantlib-2c-D
git branch -D 2c-wi1 2c-wi23 2c-wi4 2c-wi5
git push origin --delete 2c-wi1 2c-wi23 2c-wi4 2c-wi5
git worktree list
```

Expected: only the main checkout remains.

Phase 2c complete.
