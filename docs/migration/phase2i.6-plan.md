# Phase 2i.6 Implementation Plan — JQuantLib Migration

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. WI-1 (worktree A) lands first; WI-2 (worktree B) dispatches only AFTER WI-1 lands.

**Goal:** Port CORE-MATH correctly-rounded `log` to `org.jquantlib.math.transcendental.JQuantMath.log`; rewire `NonCentralCumulativeChiSquaredDistribution.java:86` (`Math.log(x2)` → `JQuantMath.log(x2)`); flip NCCS CDF tier annotation TIGHT → EXACT (the Phase 2i.5 WI-2 A19 discharge). End state: scanner WIP unchanged at 0; tests `687 → 688` (+1 EXACT log test); tag `jquantlib-phase2i.6-complete`.

**Architecture:** Same as Phase 2i.5 — direct commits to `main`, TDD per primitive, cross-validated against CORE-MATH `cr_log` directly (NOT `std::log` per Phase 2i A3 finding) via `migration-harness/` probes, EXACT-tier *bit-pattern* comparison via `MathTestSupport.bitsEqual`. 2 git worktrees per `phase2i.6-design.md` §3 — A=log port (single commit), B=NCCS EXACT flip (after A). Pause triggers per design §5: A2 EXACT unreachable, A3 reference itself wrong (extremely unlikely), A4 disabled for `org.jquantlib.math.transcendental` (planned: `LogKernel.java`); A19 expected possibility for NCCS EXACT (gammaFunction_.logValue Lanczos as documented secondary slack — back off to TIGHT-with-doc).

**Tech Stack:** Java 11 / Maven / JUnit 4; C++17 / CMake / QuantLib v1.42.1 pinned via submodule (commit `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`); CORE-MATH BSD/MIT-licensed `src/binary64/log/log.c` (canonical Inria via `https://gitlab.inria.fr/core-math/core-math`); existing `Dint64` u128-emulation infrastructure (Phase 2i.5 commit `73b0a23` + followup `042468c`); Python 3 for table-extractor pattern; nlohmann/json for probe output; git worktrees for sequential implementer execution.

---

## Overview

| Layer | Description | Worktree | Expected commits |
|-------|-------------|----------|------------------|
| L0 | Pre-flight: confirm baseline, snapshot scanner, set up 2 worktrees, init progress doc | (main) | 1 |
| L1 | WI-1 CORE-MATH log port (single commit; possibly +1 if Dint64 needs new ops per P2I6-6) | A | 1 (or 2) |
| L2 | WI-2 NCCS rewire + EXACT flip (after WI-1 lands) | B | 1 |
| L3 | Completion doc + tag | (main) | 1 commit + 1 tag |

**Non-goals reminder (design §1):** BroadieKaya retry, `JQuantMath.pow/tan/asin/acos/atan/atan2/sinh/cosh/tanh/expm1/log1p/cbrt/hypot`, `JQuantMath.lgamma`, codebase-wide swap, Math.log audit re-sweep, Gaussian1D family — all deferred.

**Git discipline (inherited):** every commit signed off with `-s`; no `Co-authored-by: Claude` trailer; unsigned (no GPG/SSH); push direct to `origin main`. Commit messages follow `<kind>(<pkg>): <verb> ...` with `(Phase 2i.6 WI-N)` suffix.

**Sequencing:** L0 lands. L1 (WI-1) dispatches in worktree A. After L1 lands on main, dispatch L2 (WI-2) which needs `JQuantMath.log` available. After both WI commits land, L3 (completion + tag).

---

## Layer 0 — Pre-flight + worktree setup

### Task 0.1: Confirm `main` is clean and green

- [ ] **Step 1:** Verify branch and clean working tree.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git status
git branch --show-current
```

Expected: branch `main`, working tree clean (modulo IDE noise).

- [ ] **Step 2:** Run baseline test suite.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib/jquantlib && mvn test 2>&1 | grep "Tests run:" | tail -3
```

Expected final line: `Tests run: 687, Failures: 0, Errors: 0, Skipped: 22`.

- [ ] **Step 3:** Snapshot scanner state.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib && python3 tools/stub-scanner/scan_stubs.py 2>&1 | tail -3
```

Expected: `0 stubs`.

- [ ] **Step 4:** Verify the harness submodule pin.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib/migration-harness/cpp/quantlib && git rev-parse HEAD
```

Expected: `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`.

- [ ] **Step 5:** Capture predecessor tag + design commit.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git rev-parse main
git tag -l 'jquantlib-phase2i.5-complete'
git log --oneline -1 docs/migration/phase2i.6-design.md
```

Expected: predecessor tag `jquantlib-phase2i.5-complete` exists @ `aa5a820`; design at `7880064` or later.

### Task 0.2: Create 2 git worktrees

- [ ] **Step 1:** Create branches and worktrees off main tip.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-2i6-A-log-port ../jquantlib-2i6-A main
git worktree add -b phase-2i6-B-nccs-exact-flip ../jquantlib-2i6-B main
git worktree list
```

Expected: 3 worktrees (main + 2 new).

- [ ] **Step 2:** Verify each worktree builds clean.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-A/jquantlib && mvn test-compile -q 2>&1 | tail -3
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-B/jquantlib && mvn test-compile -q 2>&1 | tail -3
```

Expected: each prints no error output.

- [ ] **Step 3:** Verify submodules are init'd in each worktree (probes need them).

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-A && git submodule status migration-harness/cpp/quantlib | awk '{print $1}'
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-B && git submodule status migration-harness/cpp/quantlib | awk '{print $1}'
```

Expected: each prints `099987f0ca2c11c505dc4348cdb9ce01a598e1e5` (no `-` prefix).

If any prints `-` prefix:

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-A && git submodule update --init --recursive
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-B && git submodule update --init --recursive
```

### Task 0.3: Initialize Phase 2i.6 progress doc

**Files:**
- Create: `docs/migration/phase2i.6-progress.md`

- [ ] **Step 1:** Write the initial progress doc.

```markdown
# Phase 2i.6 Progress Log

Living document — updated by the controller after every implementer subagent run.

**Plan:** `docs/migration/phase2i.6-plan.md` (commit TBD-after-plan-lands)
**Design:** `docs/migration/phase2i.6-design.md` (commit `7880064`)
**Predecessor:** `jquantlib-phase2i.5-complete` @ `aa5a820`
**Phase 2i.6 start tip on main:** `<fill at L0 land>`
**Baseline:** Tests `687/0/0/22`, scanner `0 stubs`

## Worktrees

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2i6-A` | `phase-2i6-A-log-port` | WI-1 CORE-MATH log port |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2i6-B` | `phase-2i6-B-nccs-exact-flip` | WI-2 NCCS rewire + EXACT flip (after WI-1 lands) |

## Pause-trigger status

- A2 (WI-1 EXACT unreachable): not fired
- A3 (CORE-MATH reference itself wrong): not fired
- A4 (unplanned new class outside `org.jquantlib.math.transcendental`): not fired
- A6 disabled per memory `feedback_phase2a_no_a6.md`
- A8/A10/A11/A12/A14 inactive
- A9 worktree-merge-conflict: not fired
- A13 (non-log transcendental): not fired
- A15 (previously-hidden bug surface): not fired
- A16 (Dint64 needs new ops): not fired
- A17 (>2 unplanned align commits during port): not fired
- A18 (NaN payload divergence): not fired
- A19 (NCCS EXACT fails after JQuantMath.log swap-in): not fired

## Layer / WI progress

### L0 — pre-flight + worktree setup
_(Pending)_

### L1 — WI-1 CORE-MATH log port (worktree A)
_(Pending)_

### L2 — WI-2 NCCS rewire + EXACT flip (worktree B, after WI-1 lands)
_(Pending)_

### L3 — completion doc + tag
_(Not yet started)_

## Test count tracking

| Event | Tests | Failures | Errors | Skipped | Notes |
|-------|-------|----------|--------|---------|-------|
| Phase 2i.6 start (`<fill>`) | 687 | 0 | 0 | 22 | baseline |
```

- [ ] **Step 2:** Commit the progress doc on main.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git add docs/migration/phase2i.6-progress.md
git commit -s -m "docs(migration): init phase2i.6-progress log"
git push origin main
```

Update the start-tip line in the progress doc with the resulting commit SHA after push.

---

## Layer 1 — WI-1 CORE-MATH log port (worktree A)

**Worktree:** `/Users/josemoya/eclipse-workspace/jquantlib-2i6-A`
**Branch:** `phase-2i6-A-log-port`
**Subagent:** opus-class (algorithm transcription with bit-exact requirement; ~600-1000 LOC).

### Task 1.1: Port CORE-MATH `cr_log` to `JQuantMath.log`

**Files:**
- Create: `migration-harness/cpp/probes/transcendental/coremath/log.c` (vendored CORE-MATH source)
- Create: `migration-harness/cpp/probes/transcendental/coremath/<helpers>.h` (only if log.c uses headers beyond existing `dint.h`)
- Create: `migration-harness/cpp/probes/transcendental/log_probe.cpp`
- Create: `migration-harness/references/math/transcendental/log.json` (probe output)
- Create: `jquantlib/src/main/java/org/jquantlib/math/transcendental/LogKernel.java`
- Modify: `jquantlib/src/main/java/org/jquantlib/math/transcendental/JQuantMath.java` (add `log(double)` static method)
- Modify (only if needed per P2I6-6): `jquantlib/src/main/java/org/jquantlib/math/transcendental/Dint64.java` (additional dint operations)
- Modify (only if needed): `migration-harness/cpp/probes/transcendental/dint64_probe.cpp` + `migration-harness/references/math/transcendental/dint64.json`
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/math/transcendental/JQuantMathLogTest.java`

- [ ] **Step 0: Rebase worktree A on main**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-A
git fetch origin
git rebase origin/main
git log --oneline -3
```

Expected: clean rebase. Tip should include the L0 progress-doc commit.

- [ ] **Step 1: Inspect existing infrastructure**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-A
cat jquantlib/src/main/java/org/jquantlib/math/transcendental/Dint64.java | head -80
cat jquantlib/src/main/java/org/jquantlib/math/transcendental/JQuantMath.java
ls migration-harness/cpp/probes/transcendental/coremath/
```

Familiarize yourself with the Dint64 9-op surface (`fromDouble`, `toDouble`, `copyFrom`, `addAssign`, `mulAssign`, `mul21Assign`, `subnormalize`, `cmpAbs`, `isZero`) and the existing JQuantMath methods (`exp`, `cos`, `sin`).

- [ ] **Step 2: Fetch CORE-MATH log.c from canonical Inria source**

Use WebFetch on:
- `https://gitlab.inria.fr/core-math/core-math/-/raw/master/src/binary64/log/log.c`

Save to `migration-harness/cpp/probes/transcendental/coremath/log.c`. Preserve the MIT license header verbatim.

If `log.c` `#include`s helper headers not already present in `migration-harness/cpp/probes/transcendental/coremath/` (note `dint.h` is already there from Phase 2i.5), fetch those too. Common candidates: tables, additional support headers.

```bash
ls migration-harness/cpp/probes/transcendental/coremath/
grep "^#include" migration-harness/cpp/probes/transcendental/coremath/log.c
```

- [ ] **Step 3: Inventory log.c structure**

Read `coremath/log.c`. Note for the implementer's report:
- Algorithm sketch (fast path vs accurate path; argument reduction approach)
- Static lookup tables present (count and sizes)
- Hard-cases / exception table presence and entry count
- All `dint`-prefixed functions called — compare against Dint64's 9-op surface
- LOC count of log.c

If `log.c` calls dint operations beyond Dint64's current surface (e.g. `inv_dint`, `sqr_dint`), report — these need a Dint64 extension prep commit (per P2I6-6) before LogKernel can be implemented.

- [ ] **Step 4: (CONDITIONAL — only if Step 3 surfaced new dint ops needed) Extend Dint64 in a prep commit**

If Step 3 reports new dint operations needed:

1. Add the new methods to `Dint64.java` (transcribe verbatim from CORE-MATH `dint.h` or wherever they're defined).
2. Add corresponding probe cases to `dint64_probe.cpp` (≥5 cases per new operation, mirroring existing coverage style).
3. Regenerate `dint64.json`:
   ```bash
   cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-A
   bash migration-harness/generate-references.sh 2>&1 | tail -5
   ```
4. Update `Dint64Test.java` if needed (it iterates all probe cases via reflection — usually no test code change required).
5. Run tests: `mvn test -Dtest='Dint64Test' -q`. Expected: all dint cases (existing 100 + new) pass bit-exact.
6. Discard timestamp-only ref regenerations:
   ```bash
   git status --short | head -30
   git checkout -- migration-harness/references/_smoke_test.json \
                   migration-harness/references/currencies/ \
                   migration-harness/references/math/distributions/ \
                   migration-harness/references/math/integrals/ \
                   migration-harness/references/math/interpolations/ \
                   migration-harness/references/math/optimization/ \
                   migration-harness/references/math/randomnumbers/ \
                   migration-harness/references/model/ \
                   migration-harness/references/patterns/ \
                   migration-harness/references/pricingengines/ \
                   migration-harness/references/processes/ 2>&1 | head -3 || true
   ```
   `migration-harness/references/math/transcendental/{exp,sin,cos}.json` should NOT be in the diff (they're stable from prior phases).
7. Commit and push as a prep commit:
   ```bash
   git add jquantlib/src/main/java/org/jquantlib/math/transcendental/Dint64.java \
           migration-harness/cpp/probes/transcendental/dint64_probe.cpp \
           migration-harness/references/math/transcendental/dint64.json
   git commit -s -m "infra(math.transcendental): extend Dint64 with <op-names> for log port (Phase 2i.6 WI-1 prep)"
   git push origin phase-2i6-A-log-port
   ```
8. Fast-forward to main:
   ```bash
   cd /Users/josemoya/eclipse-workspace/jquantlib
   git fetch origin
   git merge --ff-only origin/phase-2i6-A-log-port
   git push origin main
   cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-A
   git fetch origin && git rebase origin/main
   ```

If Step 3 found no new dint ops needed, skip Step 4 entirely.

- [ ] **Step 5: Write `log_probe.cpp`**

Create `migration-harness/cpp/probes/transcendental/log_probe.cpp`:

```cpp
// migration-harness/cpp/probes/transcendental/log_probe.cpp
// Phase 2i.6 WI-1 — emit bit-exact cr_log(x) for a curated input set.
//
// Oracle: CORE-MATH cr_log (correctly-rounded by design), NOT std::log.
// Per Phase 2i A3 (commit a61b920): Apple libm std::log is not always
// correctly-rounded at hard-rounding boundaries; CORE-MATH cr_log is
// the only reliable EXACT-tier reference.

#include <ql/version.hpp>
#include "../common.hpp"

extern "C" {
    #include "coremath/log.c"
}

#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <limits>

using namespace jqml_harness;

namespace {

std::string hexBits(double y) {
    std::uint64_t bits;
    std::memcpy(&bits, &y, sizeof bits);
    char buf[32];
    std::snprintf(buf, sizeof buf, "0x%016llx", (unsigned long long) bits);
    return std::string(buf);
}

double fromBits(std::uint64_t bits) {
    double d;
    std::memcpy(&d, &bits, sizeof d);
    return d;
}

void addLogCase(ReferenceWriter& out, const std::string& name, double x) {
    out.addCase(name,
        json{{"x", x}},
        json{{"y_bits", hexBits(cr_log(x))}});
}

} // namespace

int main() {
    ReferenceWriter out("math/transcendental/log", QL_VERSION, "log_probe");

    // IEEE-754 specials
    addLogCase(out, "pos_zero", +0.0);                                    // → -inf
    addLogCase(out, "neg_zero", -0.0);                                    // → -inf
    addLogCase(out, "pos_inf", std::numeric_limits<double>::infinity());  // → +inf
    addLogCase(out, "neg_inf", -std::numeric_limits<double>::infinity()); // → NaN
    addLogCase(out, "qnan", std::numeric_limits<double>::quiet_NaN());

    addLogCase(out, "min_subnormal", std::numeric_limits<double>::denorm_min());
    addLogCase(out, "min_normal", std::numeric_limits<double>::min());
    addLogCase(out, "max_normal", std::numeric_limits<double>::max());

    // Negative inputs (all → NaN)
    addLogCase(out, "neg_one", -1.0);
    addLogCase(out, "neg_pi", -3.141592653589793);
    addLogCase(out, "neg_half", -0.5);
    addLogCase(out, "neg_min_subnormal", -std::numeric_limits<double>::denorm_min());

    // Exact-or-near-exact result inputs
    addLogCase(out, "one", 1.0);                                  // → +0.0 (exact)
    addLogCase(out, "e", 2.718281828459045);                      // → ~1.0

    // Powers of 2 (sample subset — full -1074..1023 is too many; pick representative breakpoints)
    int idx = 0;
    for (int k : {-1074, -1000, -100, -50, -10, -2, -1, 0, 1, 2, 10, 50, 100, 1000, 1023}) {
        const double x = std::ldexp(1.0, k);
        char nm[32]; std::snprintf(nm, sizeof nm, "pow2_%05d", idx++);
        addLogCase(out, nm, x);
    }

    // Dense (0, 10] @ 0.01
    idx = 0;
    for (int k = 1; k <= 1000; ++k) {
        const double x = k * 0.01;
        char nm[32]; std::snprintf(nm, sizeof nm, "dense_%04d", idx++);
        addLogCase(out, nm, x);
    }

    // Sparse logarithmic (0, 1e308] — 10^k for k=-300..300 step 10
    idx = 0;
    for (int k = -300; k <= 300; k += 10) {
        const double x = std::pow(10.0, (double) k);
        char nm[32]; std::snprintf(nm, sizeof nm, "log10_%05d", idx++);
        addLogCase(out, nm, x);
    }

    // Tiny-near-1 inputs (where log(x) ≈ x - 1; argument-reduction stress)
    addLogCase(out, "near1_pos_2pm52", 1.0 + std::ldexp(1.0, -52));
    addLogCase(out, "near1_neg_2pm52", 1.0 - std::ldexp(1.0, -52));
    addLogCase(out, "near1_pos_2pm30", 1.0 + std::ldexp(1.0, -30));
    addLogCase(out, "near1_neg_2pm30", 1.0 - std::ldexp(1.0, -30));

    // Hard-cases entries from coremath/log.c source.
    // Read the source's hard-cases / exception table and add ALL entries:
    //   addLogCase(out, "db_NN", fromBits(0x...UL));
    // Phase 2i.5 lesson: do this from day one, not as a retrofit.

    out.write();
    return 0;
}
```

After fetching `coremath/log.c`, **read it carefully** and add probe cases for every hard-case / exception-table entry. Implementer reports the count.

- [ ] **Step 6: Build probe and generate references**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-A
bash migration-harness/setup.sh 2>&1 | tail -5
bash migration-harness/generate-references.sh 2>&1 | tail -10
ls -la migration-harness/references/math/transcendental/log.json
python3 -c "import json; d=json.load(open('migration-harness/references/math/transcendental/log.json')); print('cases:', len(d['cases']))"
```

Expected: `log.json` exists, ~600+ cases (specials + powers of 2 + dense + sparse + tiny-near-1 + hard-cases).

- [ ] **Step 7: Sanity-check known cases**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-A
python3 -c "
import json
d = json.load(open('migration-harness/references/math/transcendental/log.json'))
for name in ['one', 'pos_zero', 'pos_inf', 'qnan']:
    for c in d['cases']:
        if c['name'] == name:
            print(f'{name}: {c}')
            break
"
```

Sanity expectations:
- `one` y_bits = `0x0000000000000000` (+0.0)
- `pos_zero` y_bits = `0xfff0000000000000` (-inf)
- `pos_inf` y_bits = `0x7ff0000000000000` (+inf)
- `qnan` y_bits = some NaN bit pattern (canonicalized by MathTestSupport)

- [ ] **Step 8: Add `log` to `JQuantMath` facade**

Edit `jquantlib/src/main/java/org/jquantlib/math/transcendental/JQuantMath.java`. After the existing `sin` method:

```java
    /**
     * Bit-exact correctly-rounded {@code log(x)} matching CORE-MATH {@code cr_log}
     * across all IEEE-754 binary64 inputs in round-to-nearest-even mode.
     */
    public static double log(double x) {
        return LogKernel.log(x);
    }
```

- [ ] **Step 9: Implement `LogKernel`**

Create `jquantlib/src/main/java/org/jquantlib/math/transcendental/LogKernel.java`. The class is package-private; only `JQuantMath.log` invokes it.

Class structure (target ~600-1000 LOC including static-table initializers):

```java
package org.jquantlib.math.transcendental;

/**
 * Pure-Java port of CORE-MATH's correctly-rounded {@code cr_log}.
 *
 * <p>Source: CORE-MATH {@code src/binary64/log/log.c} (Sibidanov et al.,
 * Inria; MIT-licensed; canonical Inria source).
 * Transcribed faithfully — every intermediate produces the same bit-exact
 * value as the C reference.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Special-case dispatch: x ≤ 0 → NaN/-inf; +inf → +inf; NaN → NaN.</li>
 *   <li>Exponent extraction: x = 2^k · m with m ∈ [1, 2).</li>
 *   <li>Fast path: polynomial approximation in (m-1) using a precomputed
 *       lookup table. Returns result with a small error bound.</li>
 *   <li>Accurate path (cold): full reconstruction via {@link Dint64} 128-bit
 *       arithmetic + accurate polynomial in dint64_t precision.</li>
 *   <li>Hard-cases table: lookup of inputs whose correctly-rounded result
 *       requires a specific nudge to land on the IEEE-754 boundary.</li>
 * </ol>
 *
 * <p>Reuses the {@link Dint64} infrastructure landed in Phase 2i.5
 * sub-layer 1.0 (commit {@code 73b0a23}).
 */
final class LogKernel {

    private LogKernel() {}

    static double log(double x) {
        // Special-case dispatch (NaN, ±inf, x ≤ 0, x = 1.0)
        // Then call logFast(x); check eps tolerance to decide if accurate path needed.
        // Final lookup in hard-cases table if eps band straddles a rounding boundary.
        // ...
        throw new UnsupportedOperationException("LogKernel.log not yet implemented");
    }

    // --- Static tables ---
    // T_HI[]/T_LO[] — fast-path lookup of log(1+u) at table breakpoints
    // Pfast[] — fast-path polynomial coefficients
    // P[] — accurate-path polynomial coefficients
    // <other tables specific to CORE-MATH log.c>
    //
    // Use Phase 2i.5 Python table-extractor pattern: mechanically convert
    // CORE-MATH hex floats to Double.longBitsToDouble in static {} initializer.

    // --- Fast path ---
    private static double logFast(double x) { /* ... */ }

    // --- Accurate path ---
    private static double logAccurate(/* args */) { /* ... */ }

    // --- Hard-cases handling ---
    private static double logExceptionTable(double x, double fastResult) { /* ... */ }
}
```

The implementer transcribes the actual CORE-MATH source faithfully. Java translation notes inherited from Phase 2i.5:

- C `__builtin_fma(a, b, c)` → `Math.fma(a, b, c)` (Java 11+, IEEE-754 FMA)
- C `__builtin_roundeven(x)` → `Math.rint(x)`
- C `__builtin_expect(cond, val)` → drop
- C `volatile` → drop
- C `union { double f; uint64_t i; }` → `Double.doubleToRawLongBits` / `Double.longBitsToDouble`
- Tables: encode as `double[]` arrays with `static {}` initializer using bit patterns:
  ```java
  static final double[] T_HI = new double[64];
  static final double[] T_LO = new double[64];
  static {
      T_HI[0] = Double.longBitsToDouble(0x...L);
      T_LO[0] = Double.longBitsToDouble(0x...L);
      // ... rest of entries from coremath/log.c source ...
  }
  ```
  Use a Python extractor (Phase 2i.5 P2I5-12 pattern) to convert all CORE-MATH hex floats mechanically.
- Dint64 usage: instantiate fresh `Dint64` instances per intermediate (no aliasing — see Dint64 Javadoc constraint on `addAssign` / `mulAssign`).

- [ ] **Step 10: Write the failing test**

Create `jquantlib/src/test/java/org/jquantlib/testsuite/math/transcendental/JQuantMathLogTest.java`:

```java
package org.jquantlib.testsuite.math.transcendental;

import org.json.JSONObject;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.testsuite.util.MathTestSupport;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 2i.6 WI-1 — bit-exact validation of {@link JQuantMath#log(double)}
 * against CORE-MATH cr_log via the probe at
 * {@code migration-harness/references/math/transcendental/log.json}.
 *
 * <p>EXACT tier: comparison is on raw {@code long} bit patterns
 * (NaN-payload-canonicalised). Per Phase 2i A3: probe oracle is
 * CORE-MATH cr_log directly, NOT Apple libm std::log.
 *
 * <p>Collect-all-failures pattern (per Phase 2i WI-1.1 review): iterate
 * every probe case before reporting; on failure show count + first 5.
 */
public class JQuantMathLogTest {

    @Test
    public void log_bitExactAgainstCoreMathProbe() {
        final ReferenceReader ref = ReferenceReader.load("math/transcendental/log");
        final List<String> mismatches = new ArrayList<>();
        for (String name : ref.caseNames()) {
            final ReferenceReader.Case c = ref.getCase(name);
            final double x = c.inputs().getDouble("x");
            final long expectedBits = MathTestSupport.parseHexBits(
                ((JSONObject) c.expected()).getString("y_bits"));
            final double actual = JQuantMath.log(x);
            if (!MathTestSupport.bitsEqual(expectedBits, actual)) {
                mismatches.add(String.format(
                    "case=%s x=%s expected=0x%016x actual=0x%016x (=%s)",
                    name, x, expectedBits, Double.doubleToRawLongBits(actual), actual));
            }
        }
        if (!mismatches.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            sb.append(mismatches.size()).append(" of ").append(ref.caseNames().size())
              .append(" cases mismatched. First 5:\n");
            for (int i = 0; i < Math.min(5, mismatches.size()); ++i) {
                sb.append("  ").append(mismatches.get(i)).append('\n');
            }
            throw new AssertionError(sb.toString());
        }
    }
}
```

- [ ] **Step 11: Run failing test (compile-fail or stub-throw expected)**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-A/jquantlib
mvn test -Dtest='JQuantMathLogTest' 2>&1 | tail -15
```

Expected: 1 test fails — either compile error (if facade not yet added) or stub `UnsupportedOperationException` from `LogKernel.log`.

- [ ] **Step 12: Run test at EXACT tier**

After implementing `LogKernel`:

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-A/jquantlib
mvn test -Dtest='JQuantMathLogTest' 2>&1 | tail -15
```

Expected: 1 test passes (Tests run: 1, Failures: 0).

If mismatches:
- Collect-all-failures pattern shows count + first 5 — diagnose.
- DO NOT loosen the tier — fix the algorithm.
- Likely root causes: table entry off-by-one, polynomial coefficient transcription error, accurate-path fallback condition wrong, special-case ordering different from CORE-MATH, Dint64 op aliasing.

- [ ] **Step 13: Run full suite**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-A/jquantlib && mvn test 2>&1 | grep "Tests run:" | tail -3
```

Expected: `Tests run: 688, Failures: 0, Errors: 0, Skipped: 22` (687 + 1 new EXACT log test).

- [ ] **Step 14: Scanner check**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-A && python3 tools/stub-scanner/scan_stubs.py 2>&1 | tail -3
```

Expected: `0 stubs`.

- [ ] **Step 15: Discard timestamp-only ref regenerations**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-A
git status --short | head -30
git checkout -- migration-harness/references/_smoke_test.json \
                migration-harness/references/currencies/ \
                migration-harness/references/math/distributions/ \
                migration-harness/references/math/integrals/ \
                migration-harness/references/math/interpolations/ \
                migration-harness/references/math/optimization/ \
                migration-harness/references/math/randomnumbers/ \
                migration-harness/references/model/ \
                migration-harness/references/patterns/ \
                migration-harness/references/pricingengines/ \
                migration-harness/references/processes/ 2>&1 | head -3 || true
git status --short
```

If `migration-harness/references/math/transcendental/{exp,sin,cos,dint64}.json` were touched only by timestamps, discard them too — they are stable from prior phases:

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-A
# Only keep the new log.json; if exp/sin/cos/dint64.json show diff, check if it's only timestamp:
git diff migration-harness/references/math/transcendental/exp.json | head -5
# If only generated_at changes, discard:
git checkout -- migration-harness/references/math/transcendental/exp.json \
                migration-harness/references/math/transcendental/sin.json \
                migration-harness/references/math/transcendental/cos.json \
                migration-harness/references/math/transcendental/dint64.json 2>&1 | head -3 || true
```

Final expected staged set:
- New: `migration-harness/cpp/probes/transcendental/coremath/log.c`
- New: `migration-harness/cpp/probes/transcendental/coremath/<helpers>.h` (only if log.c needed them)
- New: `migration-harness/cpp/probes/transcendental/log_probe.cpp`
- New: `migration-harness/references/math/transcendental/log.json`
- New: `jquantlib/src/main/java/org/jquantlib/math/transcendental/LogKernel.java`
- Modified: `jquantlib/src/main/java/org/jquantlib/math/transcendental/JQuantMath.java`
- New: `jquantlib/src/test/java/org/jquantlib/testsuite/math/transcendental/JQuantMathLogTest.java`

- [ ] **Step 16: Commit and push**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-A
git add migration-harness/cpp/probes/transcendental/coremath/log.c \
        migration-harness/cpp/probes/transcendental/log_probe.cpp \
        migration-harness/references/math/transcendental/log.json \
        jquantlib/src/main/java/org/jquantlib/math/transcendental/LogKernel.java \
        jquantlib/src/main/java/org/jquantlib/math/transcendental/JQuantMath.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/transcendental/JQuantMathLogTest.java
# If you added helper headers under coremath/, add them too:
git add migration-harness/cpp/probes/transcendental/coremath/<helpers>.h 2>/dev/null || true
git commit -s -m "infra(math.transcendental): port CORE-MATH correctly-rounded log → JQuantMath.log via Dint64 (Phase 2i.6 WI-1)"
git push origin phase-2i6-A-log-port
```

- [ ] **Step 17: Fast-forward to main**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git merge --ff-only origin/phase-2i6-A-log-port
git push origin main
```

- [ ] **Step 18: Update `phase2i.6-progress.md` — mark WI-1 ✅** and record commit SHA + new test count `688/0/0/22`. Then dispatch L2 (WI-2).

---

## Layer 2 — WI-2 NCCS rewire + EXACT flip (worktree B, after WI-1 lands)

**Worktree:** `/Users/josemoya/eclipse-workspace/jquantlib-2i6-B`
**Branch:** `phase-2i6-B-nccs-exact-flip`
**Pre-requisite:** WI-1 must be on main.
**Subagent:** sonnet-class (small mechanical edits + tier flip + EXACT/A19 outcome diagnosis).

### Task 2.1: Swap `Math.log(x2) → JQuantMath.log(x2)` and flip NCCS CDF tier TIGHT → EXACT

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/math/distributions/NonCentralCumulativeChiSquaredDistribution.java` (line 86: `Math.log(x2)` → `JQuantMath.log(x2)`)
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/math/distributions/NonCentralCumulativeChiSquaredDistributionTest.java` (`cdfMatchesCpp` tier: TIGHT → EXACT; update inline comment)

- [ ] **Step 0: Rebase worktree B on main**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-B
git fetch origin
git rebase origin/main
git log --oneline -5
```

Expected: clean rebase. Tip should include the WI-1 commit (`infra(math.transcendental): port CORE-MATH correctly-rounded log ...`).

Verify `JQuantMath.log` exists:

```bash
grep -n "public static double log" jquantlib/src/main/java/org/jquantlib/math/transcendental/JQuantMath.java
```

Expected: `log(double x)` method present.

- [ ] **Step 1: Confirm NCCS production state (only Math.log left)**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-B
grep -n "Math\." jquantlib/src/main/java/org/jquantlib/math/distributions/NonCentralCumulativeChiSquaredDistribution.java
```

Expected (carry-over from Phase 2i.5):
- `JQuantMath.exp(...)` at lines 66, 83, 86 (already swapped)
- `Math.log(x2)` at line 86 (single remaining swap target)
- `Math.abs`, `Math.sqrt`, `Math.PI` — UNCHANGED out of scope

- [ ] **Step 2: Swap `Math.log(x2)` → `JQuantMath.log(x2)`**

Edit `jquantlib/src/main/java/org/jquantlib/math/distributions/NonCentralCumulativeChiSquaredDistribution.java`.

Find line 86:
```java
            t = JQuantMath.exp(f2 * Math.log(x2) - x2 - gammaFunction_.logValue(f2 + 1.0));
```

Change to:
```java
            t = JQuantMath.exp(f2 * JQuantMath.log(x2) - x2 - gammaFunction_.logValue(f2 + 1.0));
```

(The `JQuantMath` import is already present from Phase 2i.5.)

Note: `gammaFunction_.logValue(...)` is NOT swapped — it's a helper class with its own internal Math.log usage (Lanczos approximation). Per design §4 risk analysis, this is the documented secondary slack candidate if A19 fires.

- [ ] **Step 3: Run NCCS tests at TIGHT (sanity baseline)**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-B/jquantlib
mvn test -Dtest='NonCentralCumulativeChiSquaredDistributionTest' 2>&1 | tail -10
```

Expected: passes at TIGHT. The swap should not regress correctness; `JQuantMath.log` is correctly-rounded and at most as good as `Math.log` was (almost always strictly better).

- [ ] **Step 4: Flip the `cdfMatchesCpp` test tier TIGHT → EXACT**

Edit `jquantlib/src/test/java/org/jquantlib/testsuite/math/distributions/NonCentralCumulativeChiSquaredDistributionTest.java`.

Add import (alphabetical with existing imports — between `org.jquantlib.testsuite.util.ReferenceReader` and `org.jquantlib.testsuite.util.Tolerance`):

```java
import org.jquantlib.testsuite.util.MathTestSupport;
```

Replace lines 32-44 (the `cdfMatchesCpp` test method's body comment + call):

```java
    @Test
    public void cdfMatchesCpp() {
        // Phase 2i.6 WI-2: Math.log(x2) at production line 86 swapped to
        // JQuantMath.log (CORE-MATH correctly-rounded). Phase 2i.5 WI-2 had
        // already swapped 3 Math.exp call sites; with both Math.exp and
        // Math.log now correctly-rounded, the residual (was 27-ULP, A19) should
        // collapse to bit-exact across the probe. Tier: TIGHT → EXACT.
        // (If A19 re-fires, secondary slack is most likely
        // gammaFunction_.logValue Lanczos accumulated rounding — Phase 2j+
        // candidate. Back off to TIGHT-with-doc and document.)
        runFingerprint("cdf");
    }
```

Update the helper `runFingerprint` to use EXACT comparison for the `cdf` key only (NOT for `inv_cdf_at_cdf_x` which has its own structural Brent-accuracy floor at 1e-13 and stays TIGHT). Replace lines 80-86:

```java
            if (!"cdf".equals(key)) {
                // inv_cdf_at_cdf_x stays at TIGHT — its floor is the Brent
                // solver convergence accuracy (1e-13), not transcendental
                // slack. EXACT not achievable here even with correctly-
                // rounded primitives.
                if (!Tolerance.tight(got, expected)) {
                    fail(key + "[" + i + "] (degrees=" + degrees + ", ncp=" + ncp + ", x=" + x
                            + "): expected=" + expected + " got=" + got
                            + " diff=" + Math.abs(got - expected)
                            + " ulps=" + Math.abs(Double.doubleToRawLongBits(got) - Double.doubleToRawLongBits(expected)));
                }
                continue;
            }
            // cdf: EXACT-tier bit-pattern equality.
            if (!MathTestSupport.bitsEqual(Double.doubleToRawLongBits(expected), got)) {
                fail(key + "[" + i + "] (degrees=" + degrees + ", ncp=" + ncp + ", x=" + x
                        + "): expected=" + expected + " (0x" + Long.toHexString(Double.doubleToRawLongBits(expected)) + ")"
                        + " got=" + got + " (0x" + Long.toHexString(Double.doubleToRawLongBits(got)) + ")"
                        + " diff=" + Math.abs(got - expected)
                        + " ulps=" + Math.abs(Double.doubleToRawLongBits(got) - Double.doubleToRawLongBits(expected)));
            }
```

- [ ] **Step 5: Run NCCS test at EXACT**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-B/jquantlib
mvn test -Dtest='NonCentralCumulativeChiSquaredDistributionTest' 2>&1 | tail -20
```

**Two outcomes:**

### Outcome A: EXACT passes ✅

Continue to Step 6. Commit message reflects "TIGHT → EXACT" tier promotion.

### Outcome B: EXACT fails — A19 fires

The NCCS `cdfMatchesCpp` test has a secondary slack source beyond Math.log. Diagnosis:

1. The failure message will identify which inputs differ and by how many ULPs.
2. Likely structural sources, in decreasing likelihood:
   - `gammaFunction_.logValue(f2 + 1.0)` — Lanczos approximation in `org.jquantlib.math.distributions.GammaFunction`; uses `Math.log` internally
   - Sankaran polynomial coefficient table precision
3. Revert the Step 4 EXACT flip — restore the simpler TIGHT version. Update the comment:
   ```java
       @Test
       public void cdfMatchesCpp() {
           // Phase 2i.6 WI-2 attempted EXACT after JQuantMath.log swap.
           // A19 fired: <X>-ULP residual survives — structural source is
           // gammaFunction_.logValue Lanczos approximation (uses Math.log
           // internally) / <other identified source>. Phase 2j+ candidate:
           // JQuantMath.lgamma port. Staying TIGHT.
           runFingerprint("cdf");
       }
   ```
   Restore the simpler `runFingerprint` body to the version using `Tolerance.tight(got, expected)` for both keys.

- [ ] **Step 6: Run full suite**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-B/jquantlib && mvn test 2>&1 | grep "Tests run:" | tail -3
```

Expected: `Tests run: 688, Failures: 0, Errors: 0, Skipped: 22` (carry-forward from WI-1; tier flip is not a new test).

If unrelated test broke: A8 — investigate before continuing.

- [ ] **Step 7: Scanner check**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-B && python3 tools/stub-scanner/scan_stubs.py 2>&1 | tail -3
```

Expected: `0 stubs`.

- [ ] **Step 8: Commit and push**

If EXACT succeeded:
```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-B
git add jquantlib/src/main/java/org/jquantlib/math/distributions/NonCentralCumulativeChiSquaredDistribution.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/distributions/NonCentralCumulativeChiSquaredDistributionTest.java
git commit -s -m "align(math.distributions): swap Math.log → JQuantMath.log in NCCS CDF chain; tier TIGHT → EXACT (Phase 2i.6 WI-2)"
git push origin phase-2i6-B-nccs-exact-flip
```

If EXACT failed (A19):
```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i6-B
git add jquantlib/src/main/java/org/jquantlib/math/distributions/NonCentralCumulativeChiSquaredDistribution.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/distributions/NonCentralCumulativeChiSquaredDistributionTest.java
git commit -s -m "align(math.distributions): swap Math.log → JQuantMath.log in NCCS CDF chain; tier unchanged TIGHT (Phase 2i.6 WI-2, A19 — gammaFunction_.logValue floor)"
git push origin phase-2i6-B-nccs-exact-flip
```

- [ ] **Step 9: Fast-forward to main**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git merge --ff-only origin/phase-2i6-B-nccs-exact-flip
git push origin main
```

- [ ] **Step 10: Update `phase2i.6-progress.md` — mark WI-2 ✅** with tier outcome (EXACT or TIGHT-with-A19).

---

## Layer 3 — Completion doc + tag

### Task 3.1: Write the completion doc

**Files:**
- Create: `docs/migration/phase2i.6-completion.md`

- [ ] **Step 1:** Write the completion doc following Phase 2i.5 shape.

Sections:
1. Header (status, predecessor tag, this phase's tip SHA, plan + design + progress doc commits).
2. Final state table (test count `687 → 688`, scanner WIP=0, JQuantMath primitives now: exp + cos + sin + log, NCCS CDF tier outcome).
3. Per-WI summary (commit SHAs, tier outcomes, A19 fires).
4. A-trigger fire history (which fired, where, mitigation).
5. Decision log additions (any P2I6-11+ surfaced during execution).
6. JVM-vs-libc++ ULP-slack outcome refresh (now correctly-rounded: exp, cos, sin, log).
7. Phase 2j seed list refresh — what's still on the list:
   - **CORE-MATH `pow` port** — depends on log + exp (now both available); structural completion of base-arithmetic primitive set
   - **`JQuantMath.lgamma`** (only if WI-2 A19 fired) — would close the residual NCCS gap
   - **BroadieKaya retry** — still deferred (also needs pow)
   - **Gaussian1D family** — Phase 2j primary scope, ready
   - **Douglas ADI / FdmAffineModelTermStructure** — FdHullWhite real floor (Phase 2i WI-2 B-1 A19)
   - Carry-forward Fdm completeness items from Phase 2h

- [ ] **Step 2:** Commit and push.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git add docs/migration/phase2i.6-completion.md
git commit -s -m "docs(migration): Phase 2i.6 completion — CORE-MATH log via Dint64 + NCCS <EXACT/TIGHT-A19> flip"
git push origin main
```

### Task 3.2: Tag the phase

- [ ] **Step 1: Create and push the tag**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git tag -a jquantlib-phase2i.6-complete -m "Phase 2i.6 complete — JQuantMath.log via CORE-MATH (correctly-rounded); NCCS CDF <EXACT/TIGHT-A19>. Test count 688/0/0/22; scanner WIP=0."
git push origin jquantlib-phase2i.6-complete
```

- [ ] **Step 2: Verify tag is on the expected tip**

```bash
git show jquantlib-phase2i.6-complete --stat | head -10
```

### Task 3.3: Update memory

**Files:**
- Modify: `/Users/josemoya/.claude/projects/-Users-josemoya-eclipse-workspace-jquantlib/memory/MEMORY.md`
- Modify: `/Users/josemoya/.claude/projects/-Users-josemoya-eclipse-workspace-jquantlib/memory/project_jquantlib_migration.md`

- [ ] **Step 1:** Update `project_jquantlib_migration.md`:
  - Add a Phase 2i.6 paragraph under the Phase 2i.5 one (with WI-1/WI-2 outcomes, A-trigger fires, completion details).
  - Add 2026-04-30 date entry for the Phase 2i.6 completion.
  - Update the description-line frontmatter with new tag/tip and refreshed Phase 2j candidate list.

- [ ] **Step 2:** Update `MEMORY.md`:
  - Update the JQuantLib migration line — new tip SHA, test count `688/0/0/22`, JQuantMath now has {exp, cos, sin, log}, refreshed Phase 2j candidates.

(Memory updates are not committed — they live outside the repo.)

### Task 3.4: Tear down worktrees

- [ ] **Step 1: Remove the 2 phase-2i.6 worktrees**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git worktree remove --force ../jquantlib-2i6-A
git worktree remove --force ../jquantlib-2i6-B
git worktree list
```

(Phase 2i and 2i.5 required `--force` due to submodule subtree; Phase 2i.6 likely the same.)

Expected: only main remains.

- [ ] **Step 2: Delete merged branches locally and on origin**

```bash
git branch -D phase-2i6-A-log-port phase-2i6-B-nccs-exact-flip
git push origin --delete phase-2i6-A-log-port phase-2i6-B-nccs-exact-flip
```

---

## Self-Review

(Run by writer before handoff — for controller / executor reference.)

**Spec coverage:**
- §1 goals WI-1 (log port) and WI-2 (NCCS rewire + EXACT flip) → covered by L1 and L2 respectively.
- §2 chosen approach (standalone log port via Dint64) → encoded in Task 1.1 Steps 1-9 (Dint64 conditional extension at Step 4 per P2I6-6).
- §3 worktree topology (A=WI-1, B=WI-2 sequential) → enforced in L0 setup + dispatch ordering (B rebases on main post-WI-1).
- §4 tolerance tiers (EXACT bit-pattern via MathTestSupport.bitsEqual) → encoded in JQuantMathLogTest source (Step 10) and NCCS test rewrite (L2 Step 4).
- §4 probe (1 new probe + cr_log oracle inclusion + hard-cases coverage from day one) → covered by Task 1.1 Steps 5-7.
- §4 test discipline (probe-before-port, no backfilling green, collect-all-failures) → embedded in test source templates.
- §4 test count target `687 → 688` → tracked in Step 13 expectation.
- §5 pause triggers (A2, A3, A19, etc.) → invocation conditions described in Step 12 (A2), L2 Step 5 (A19).
- §5 exit criteria → covered by L3 (completion doc + tag + memory update + worktree teardown).

**Placeholder scan:**
- `<helpers>.h` is a deliberate variable — CORE-MATH source structure determines what helpers are needed; the implementer fetches and includes whatever `log.c` actually uses.
- `<op-names>` in the conditional Dint64-extension prep commit message (Step 4) is intentional — the implementer fills in actual operation names if they extend Dint64.
- `<EXACT/TIGHT-A19>` in commit messages and tag descriptions reflects the binary outcome of WI-2 — implementer picks the actual outcome at commit time.
- `0x...UL` placeholders inside hard-cases probe code are explicitly labeled as "the implementer extracts entries from coremath/log.c source." Directed task, not forgotten placeholder.
- "TBD-after-plan-lands" appears once intentionally in the progress-doc template (filled after L0 lands).

**Type consistency:**
- `JQuantMath.log` static signature (`public static double log(double)`) matches across L1 definition (Step 8) and L2 call site (Step 2).
- `MathTestSupport.bitsEqual` signature matches Phase 2i prior usage.
- Test-group string `"math/transcendental/log"` matches probe `ReferenceWriter` ctor (Step 5) and Java `ReferenceReader.load(...)` (Step 10).
- `LogKernel` package-private status mirrors Phase 2i `ExpKernel` and Phase 2i.5 `SinCosKernel` patterns.
