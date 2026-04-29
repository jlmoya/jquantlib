# Phase 2i.5 Implementation Plan — JQuantLib Migration

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. WI-1 (worktree A) and WI-2 (worktree B) dispatch in parallel after L0; WI-3 (worktree C) dispatches only AFTER WI-1 lands.

**Goal:** Port CORE-MATH correctly-rounded `cos` and `sin` (paired) to `org.jquantlib.math.transcendental.JQuantMath`; rewire NCCS CDF to `JQuantMath.exp` and attempt EXACT-tier; flip GaussLaguerre + GaussLobatto tier annotations to use the new primitives. End state: scanner WIP unchanged at 0; tests `684 → 686` (+2 EXACT tests, one per primitive); tag `jquantlib-phase2i.5-complete`.

**Architecture:** Same as Phase 2i — direct commits to `main`, TDD per primitive, cross-validated against CORE-MATH `cr_cos`/`cr_sin`/`cr_exp` directly (NOT `std::*` per Phase 2i A3 finding) via `migration-harness/` probes, EXACT-tier *bit-pattern* comparison via `MathTestSupport.bitsEqual`. 3 git worktrees per `phase2i.5-design.md` §3 — A=trig port (single commit), B=NCCS rewire (parallel with A), C=tier flips (after A). Pause triggers per design §5: A2 EXACT unreachable, A3 reference-itself-wrong (extremely unlikely), A4 disabled for `org.jquantlib.math.transcendental` (planned helper classes), A6 disabled, A8/A9/A15/A16/A17/A18 carry-forward, A19 expected for NCCS EXACT attempt and possibly Lag/Lob (document and back off one tier).

**Tech Stack:** Java 11 / Maven / JUnit 4; C++17 / CMake / QuantLib v1.42.1 pinned via submodule (commit `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`); CORE-MATH BSD/MIT-licensed `src/binary64/sin/sin.c` and `cos/cos.c` (mirror at `https://raw.githubusercontent.com/mockingbirdnest/core-math/master/...`); Python 3 for scanner tooling; nlohmann/json for probe output; git worktrees for parallel implementer execution.

---

## Overview

| Layer | Description | Worktree | Expected commits |
|-------|-------------|----------|------------------|
| L0 | Pre-flight: confirm baseline, snapshot scanner, set up 3 worktrees, init progress doc | (main) | 1 |
| L1a | WI-1 cos/sin paired port (single commit) | A | 1 |
| L1b | WI-2 NCCS CDF rewire + probe regen + EXACT attempt (parallel with L1a) | B | 1 (+1 doc commit if A19) |
| L2 | WI-3 GaussLag + GaussLob tier flips (after WI-1 lands) | C | 1-2 |
| L3 | Completion doc + tag | (main) | 1 commit + 1 tag |

**Non-goals reminder (design §1):** BroadieKaya retry, `JQuantMath.log/pow`, `tan`/`asin`/`acos`/`atan`/`atan2`/`sinh`/`cosh`/`tanh`/`expm1`/`log1p`/`cbrt`/`hypot`, codebase-wide swap, non-transcendental floor investigations, Gaussian1D family — all deferred.

**Git discipline (inherited):** every commit signed off with `-s`; no `Co-authored-by: Claude` trailer; unsigned (no GPG/SSH); push direct to `origin main`. Commit messages follow `<kind>(<pkg>): <verb> ...` with `(Phase 2i.5 WI-N)` suffix.

**Sequencing:** L0 lands. L1a (WI-1) and L1b (WI-2) dispatch in parallel — they touch disjoint files. After L1a lands on main, dispatch L2 (WI-3) which needs `JQuantMath.cos`/`sin` available. After all WI commits land, L3 (completion + tag).

---

## Layer 0 — Pre-flight + worktree setup

### Task 0.1: Confirm `main` is clean and green

- [ ] **Step 1:** Verify branch and clean working tree.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git status
git branch --show-current
```

Expected: branch `main`, working tree clean (modulo IDE noise + the long-standing untracked `migration-harness/references/pricingengines/capfloor/blackcheck.json` from Phase 2g — leave alone).

- [ ] **Step 2:** Run baseline test suite.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib/jquantlib && mvn test 2>&1 | grep "Tests run:" | tail -3
```

Expected final summary: `Tests run: 684, Failures: 0, Errors: 0, Skipped: 22`.

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

- [ ] **Step 5:** Capture Phase 2i tip + design commit.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git rev-parse main
git tag -l 'jquantlib-phase2i-complete'
git log --oneline -1 docs/migration/phase2i.5-design.md
```

Expected: predecessor tag `jquantlib-phase2i-complete` exists @ `a4a3b77`; design at `8cf4775` or later; current main tip available.

### Task 0.2: Create 3 git worktrees

- [ ] **Step 1:** Create branches and worktrees off main tip.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-2i5-A-trig-port ../jquantlib-2i5-A main
git worktree add -b phase-2i5-B-nccs-rewire ../jquantlib-2i5-B main
git worktree add -b phase-2i5-C-trig-tier-flips ../jquantlib-2i5-C main
git worktree list
```

Expected: 4 worktrees listed (main + 3 new).

- [ ] **Step 2:** Verify each worktree builds clean.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-A/jquantlib && mvn test-compile -q 2>&1 | tail -3
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-B/jquantlib && mvn test-compile -q 2>&1 | tail -3
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-C/jquantlib && mvn test-compile -q 2>&1 | tail -3
```

Expected: each prints `BUILD SUCCESS` (or no error output).

- [ ] **Step 3:** Verify submodules are init'd in each worktree (probes need them).

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-A && git submodule status migration-harness/cpp/quantlib | awk '{print $1}'
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-B && git submodule status migration-harness/cpp/quantlib | awk '{print $1}'
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-C && git submodule status migration-harness/cpp/quantlib | awk '{print $1}'
```

Expected: each prints `099987f0ca2c11c505dc4348cdb9ce01a598e1e5` (no `-` prefix).

If any prints `-` prefix, run:

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-A && git submodule update --init --recursive
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-B && git submodule update --init --recursive
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-C && git submodule update --init --recursive
```

### Task 0.3: Initialize Phase 2i.5 progress doc

**Files:**
- Create: `docs/migration/phase2i.5-progress.md`

- [ ] **Step 1:** Write the initial progress doc.

```markdown
# Phase 2i.5 Progress Log

Living document — updated by the controller after every implementer subagent run.

**Plan:** `docs/migration/phase2i.5-plan.md` (commit TBD-after-plan-lands)
**Design:** `docs/migration/phase2i.5-design.md` (commit `8cf4775`)
**Predecessor:** `jquantlib-phase2i-complete` @ `a4a3b77`
**Phase 2i.5 start tip on main:** `<fill at L0 land>`
**Baseline:** Tests `684/0/0/22`, scanner `0 stubs`

## Worktrees

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2i5-A` | `phase-2i5-A-trig-port` | WI-1 cos/sin paired port |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2i5-B` | `phase-2i5-B-nccs-rewire` | WI-2 NCCS CDF rewire + EXACT (parallel with A) |
| C | `/Users/josemoya/eclipse-workspace/jquantlib-2i5-C` | `phase-2i5-C-trig-tier-flips` | WI-3 GaussLag + GaussLob tier flips (after WI-1 lands) |

## Pause-trigger status

- A2 (WI-1 EXACT unreachable): not fired
- A3 (CORE-MATH reference itself wrong): not fired
- A4 (unplanned new class outside `org.jquantlib.math.transcendental`): not fired
- A6 disabled per memory `feedback_phase2a_no_a6.md`
- A8/A10/A11/A12/A14 inactive
- A9 worktree-merge-conflict: not fired
- A13 (non-cos/sin transcendental): not fired
- A15 (previously-hidden bug surface): not fired
- A16 (missing dependency outside planned scope): not fired
- A17 (>2 unplanned align commits during port): not fired
- A18 (NaN payload divergence): not fired
- A19 (tier promotion fails after correct swap-in): not fired

## Layer / WI progress

### L0 — pre-flight + worktree setup
_(Pending)_

### L1a — WI-1 cos/sin paired port (worktree A)
_(Pending)_

### L1b — WI-2 NCCS CDF rewire + EXACT attempt (worktree B, parallel with L1a)
_(Pending)_

### L2 — WI-3 GaussLag + GaussLob tier flips (worktree C, after WI-1 lands)
_(Pending)_

### L3 — completion doc + tag
_(Not yet started)_

## Test count tracking

| Event | Tests | Failures | Errors | Skipped | Notes |
|-------|-------|----------|--------|---------|-------|
| Phase 2i.5 start (`<fill>`) | 684 | 0 | 0 | 22 | baseline |
```

- [ ] **Step 2:** Commit the progress doc on main.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git add docs/migration/phase2i.5-progress.md
git commit -s -m "docs(migration): init phase2i.5-progress log"
git push origin main
```

Update the start-tip line in the progress doc with the resulting commit SHA after push.

---

## Layer 1a — WI-1 cos/sin paired port (worktree A)

**Worktree:** `/Users/josemoya/eclipse-workspace/jquantlib-2i5-A`
**Branch:** `phase-2i5-A-trig-port`
**Dispatches:** in parallel with L1b after L0 lands. Subagent for this work should be opus-class (algorithm transcription with bit-exact requirement).

### Task 1.1: Port CORE-MATH `cos` and `sin` to `JQuantMath`

**Files:**
- Create: `migration-harness/cpp/probes/transcendental/coremath/sin.c` (vendored CORE-MATH source)
- Create: `migration-harness/cpp/probes/transcendental/coremath/cos.c` (vendored CORE-MATH source)
- Create: `migration-harness/cpp/probes/transcendental/coremath/<any-required-helpers>.h` (e.g. shared headers between sin and cos if CORE-MATH uses them)
- Create: `migration-harness/cpp/probes/transcendental/sin_probe.cpp`
- Create: `migration-harness/cpp/probes/transcendental/cos_probe.cpp`
- Create: `migration-harness/references/math/transcendental/sin.json` (probe output)
- Create: `migration-harness/references/math/transcendental/cos.json` (probe output)
- Create: `jquantlib/src/main/java/org/jquantlib/math/transcendental/SinCosKernel.java` (paired implementation)
- Modify: `jquantlib/src/main/java/org/jquantlib/math/transcendental/JQuantMath.java` (add `cos(double)` and `sin(double)` static methods)
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/math/transcendental/JQuantMathSinTest.java`
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/math/transcendental/JQuantMathCosTest.java`

**Probe specifications:**

`cos_probe.cpp` — ~800 inputs:
- IEEE-754 specials: `+0`, `-0`, `+inf` (→ NaN per C++17 semantics; `cr_cos(±inf) = NaN`), `-inf`, `qnan`
- Exact-result inputs: `0.0`, `±π/2` (→ ~0), `±π` (→ -1), `±2π` (→ 1)
- Symmetric inputs: `±π/6`, `±π/4`, `±π/3`, `±π`, `±2π`, `±3π/2`
- Payne-Hanek stress: `±π·2^k` for `k = 10..50` (40 cases per sign = 80 total)
- Dense `[-2π, 2π]` at 0.01 spacing (~1257 cases)
- Tiny inputs near 0: `±2^-54`, `±2^-30`, `±0x1p-52` (where `cos(x) → 1.0 - x²/2`)
- All hard-cases DB entries from CORE-MATH `cos.c` source — transcribe input bit patterns directly from the source's hard-cases table

`sin_probe.cpp` — ~800 inputs (same shape, swap sign-mirror cases):
- IEEE-754 specials
- Exact-result inputs: `0.0`, `±π/2` (→ ±1), `±π` (→ ~0), `±2π` (→ 0)
- Symmetric: `±π/6`, `±π/4`, `±π/3`, `±π`, `±2π`
- Payne-Hanek stress
- Dense `[-2π, 2π]` @ 0.01
- Tiny inputs (where `sin(x) → x`)
- All hard-cases DB entries from CORE-MATH `sin.c` source

- [ ] **Step 0: Rebase worktree A on main**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-A
git fetch origin
git rebase origin/main
git log --oneline -3
```

Expected: clean rebase. Tip at the L0 progress-doc commit.

- [ ] **Step 1: Fetch CORE-MATH sin and cos source files**

Use WebFetch on the GitHub mirror (per Phase 2i finding — `gitlab.inria.fr` is gated):

- `https://raw.githubusercontent.com/mockingbirdnest/core-math/master/src/binary64/sin/sin.c`
- `https://raw.githubusercontent.com/mockingbirdnest/core-math/master/src/binary64/cos/cos.c`

Save them to:
- `migration-harness/cpp/probes/transcendental/coremath/sin.c`
- `migration-harness/cpp/probes/transcendental/coremath/cos.c`

If either file `#include`s helper headers, fetch those too (look at the `#include` directives near the top). Common helpers from CORE-MATH: `dint.h`, `qint.h` for extended-precision arithmetic. If shared between sin and cos, place them in `migration-harness/cpp/probes/transcendental/coremath/`.

Verify license headers preserved verbatim (CORE-MATH is MIT-licensed; the headers carry copyright notice + license text).

- [ ] **Step 2: Write `cos_probe.cpp`**

Create `migration-harness/cpp/probes/transcendental/cos_probe.cpp`:

```cpp
// migration-harness/cpp/probes/transcendental/cos_probe.cpp
// Phase 2i.5 WI-1 — emit bit-exact cr_cos(x) for a curated input set
// covering IEEE-754 special cases, argument-reduction breakpoints,
// dense/sparse coverage, and CORE-MATH's hard-cases DB inputs.
//
// Oracle: CORE-MATH cr_cos (correctly-rounded by design), NOT std::cos.
// Per Phase 2i A3 finding: Apple libm std::cos is not always correctly
// rounded at hard-rounding boundaries; CORE-MATH cr_cos is the only
// reliable EXACT-tier reference.
//
// Output: migration-harness/references/math/transcendental/cos.json

#include <ql/version.hpp>
#include "../common.hpp"

extern "C" {
    #include "coremath/cos.c"
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

void addCosCase(ReferenceWriter& out, const std::string& name, double x) {
    out.addCase(name,
        json{{"x", x}},
        json{{"y_bits", hexBits(cr_cos(x))}});
}

} // namespace

int main() {
    ReferenceWriter out("math/transcendental/cos", QL_VERSION, "cos_probe");

    // IEEE-754 specials
    addCosCase(out, "pos_zero", +0.0);
    addCosCase(out, "neg_zero", -0.0);
    addCosCase(out, "pos_inf", std::numeric_limits<double>::infinity());
    addCosCase(out, "neg_inf", -std::numeric_limits<double>::infinity());
    addCosCase(out, "qnan", std::numeric_limits<double>::quiet_NaN());

    // Exact-result inputs (where mathematical answer is exactly representable)
    const double PI = 3.141592653589793;
    addCosCase(out, "pi_over_2", PI / 2.0);          // → ~0 (close but not bit-exact)
    addCosCase(out, "neg_pi_over_2", -PI / 2.0);
    addCosCase(out, "pi", PI);                        // → -1
    addCosCase(out, "neg_pi", -PI);
    addCosCase(out, "two_pi", 2.0 * PI);              // → ~1
    addCosCase(out, "neg_two_pi", -2.0 * PI);
    addCosCase(out, "three_pi_over_2", 3.0 * PI / 2.0);

    // Symmetric inputs
    addCosCase(out, "pi_over_6", PI / 6.0);
    addCosCase(out, "pi_over_4", PI / 4.0);
    addCosCase(out, "pi_over_3", PI / 3.0);
    addCosCase(out, "neg_pi_over_6", -PI / 6.0);
    addCosCase(out, "neg_pi_over_4", -PI / 4.0);
    addCosCase(out, "neg_pi_over_3", -PI / 3.0);

    // Payne-Hanek stress: ±π · 2^k for k=10..50
    int idx = 0;
    for (int k = 10; k <= 50; ++k) {
        const double x = PI * std::ldexp(1.0, k);
        char nm[32]; std::snprintf(nm, sizeof nm, "ph_pos_%02d", idx);
        addCosCase(out, nm, x);
        std::snprintf(nm, sizeof nm, "ph_neg_%02d", idx);
        addCosCase(out, nm, -x);
        ++idx;
    }

    // Dense [-2π, 2π] @ 0.01
    idx = 0;
    const int n = static_cast<int>(2.0 * PI / 0.01);
    for (int k = -n; k <= n; ++k) {
        const double x = k * 0.01;
        char nm[32]; std::snprintf(nm, sizeof nm, "dense_%04d", idx++);
        addCosCase(out, nm, x);
    }

    // Tiny inputs (where cos(x) ≈ 1.0)
    addCosCase(out, "tiny_pos_2pm54", std::ldexp(1.0, -54));
    addCosCase(out, "tiny_neg_2pm54", -std::ldexp(1.0, -54));
    addCosCase(out, "tiny_pos_2pm30", std::ldexp(1.0, -30));
    addCosCase(out, "tiny_neg_2pm30", -std::ldexp(1.0, -30));
    addCosCase(out, "tiny_pos_2pm52", std::ldexp(1.0, -52));
    addCosCase(out, "tiny_neg_2pm52", -std::ldexp(1.0, -52));

    // Hard-cases DB entries from CORE-MATH cos.c source.
    // The implementer subagent must extract these by reading the
    // CORE-MATH cos.c file's hard-cases table (typically a static
    // const array of (input_bits, sign_bits, low_bit_selectors)) and
    // emit one addCosCase(out, "db_NN", fromBits(0x...)) per entry.
    // PHASE-2I LESSON: do this from day one, not as a retrofit.
    // See coremath/cos.c lines that match the pattern from CORE-MATH's
    // exp.c hard-cases table (look for a similarly-shaped array of
    // bit patterns); transcribe ALL entries here.
    //
    // Example shape (count varies per primitive; CORE-MATH cos has
    // typically 40-60 hard-rounding cases):
    //   addCosCase(out, "db_00", fromBits(0x...UL));
    //   addCosCase(out, "db_01", fromBits(0x...UL));
    //   ...

    out.write();
    return 0;
}
```

Note: the `addCosCase` body uses `cr_cos(x)` from the included CORE-MATH source. The probe includes `coremath/cos.c` directly; cr_cos becomes a TU-local function.

- [ ] **Step 3: Write `sin_probe.cpp`**

Create `migration-harness/cpp/probes/transcendental/sin_probe.cpp` with the same shape as `cos_probe.cpp` but:
- Includes `coremath/sin.c` instead of `coremath/cos.c`
- Calls `cr_sin(x)` instead of `cr_cos(x)`
- Test-group name `math/transcendental/sin`
- Sin-specific exact-result inputs: `±π/2` (→ ±1), `±π` (→ ~0), `±2π` (→ ~0), `0.0` (→ +0)
- Hard-cases DB transcribed from CORE-MATH `sin.c`

(Same probe-source structure with `addSinCase` helper; create by copy-paste-modify of cos_probe.cpp.)

- [ ] **Step 4: Build probes and generate references**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-A
bash migration-harness/setup.sh 2>&1 | tail -5
bash migration-harness/generate-references.sh 2>&1 | tail -10
ls -la migration-harness/references/math/transcendental/sin.json migration-harness/references/math/transcendental/cos.json
python3 -c "import json; d=json.load(open('migration-harness/references/math/transcendental/cos.json')); print('cos cases:', len(d['cases']))"
python3 -c "import json; d=json.load(open('migration-harness/references/math/transcendental/sin.json')); print('sin cases:', len(d['cases']))"
```

Expected: both JSON files exist, ~50KB each, ~1500 cases each (special + Payne-Hanek + dense + tiny + DB).

- [ ] **Step 5: Sanity-check known cases**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-A
python3 -c "
import json
for name in ['pos_zero', 'pi', 'pi_over_2']:
    for fname in ['cos.json', 'sin.json']:
        d = json.load(open(f'migration-harness/references/math/transcendental/{fname}'))
        for c in d['cases']:
            if c['name'] == name:
                print(f'{fname} {name}: {c}')
                break
"
```

Sanity expectations:
- `cos pos_zero` y_bits = `0x3ff0000000000000` (1.0)
- `cos pi` y_bits = `0xbff0000000000000` (-1.0)
- `sin pos_zero` y_bits = `0x0000000000000000` (+0.0)
- `sin pi` y_bits ≈ `0x3ca1a62633145c07` (~1.22e-16, the rounding error from PI not being exactly π)

- [ ] **Step 6: Add `cos` and `sin` to `JQuantMath` facade**

Edit `jquantlib/src/main/java/org/jquantlib/math/transcendental/JQuantMath.java`. Add:

```java
    /** Bit-exact correctly-rounded {@code cos(x)} matching CORE-MATH cr_cos. */
    public static double cos(double x) {
        return SinCosKernel.cos(x);
    }

    /** Bit-exact correctly-rounded {@code sin(x)} matching CORE-MATH cr_sin. */
    public static double sin(double x) {
        return SinCosKernel.sin(x);
    }
```

Place them after the existing `exp` method, in alphabetical order if the implementer prefers (cos before exp, sin after exp), or grouped after exp if they prefer source-order-of-port.

- [ ] **Step 7: Implement `SinCosKernel`**

Create `jquantlib/src/main/java/org/jquantlib/math/transcendental/SinCosKernel.java`. The implementer transcribes:
1. Shared Payne-Hanek argument reduction from CORE-MATH (typically ~600 LOC; CORE-MATH may inline it directly in sin.c/cos.c rather than as a separate helper — adapt to whatever structure CORE-MATH uses).
2. The `cr_sin` algorithm body.
3. The `cr_cos` algorithm body.
4. The hard-cases DB constants for both primitives.

Java transcription notes from Phase 2i WI-1.1:
- C `union { double f; uint64_t i; }` → `Double.doubleToRawLongBits` / `Double.longBitsToDouble`
- C `__builtin_fma` → `Math.fma` (Java 11+, IEEE-754 conformant)
- C `__builtin_roundeven` → `Math.rint`
- C `__builtin_expect` → drop (Java JIT does branch profiling)
- C `volatile` → drop
- Tables of double-double pairs encoded as bit patterns:
  ```java
  static final double[] T_HI = new double[N];
  static final double[] T_LO = new double[N];
  static {
      T_HI[0] = Double.longBitsToDouble(0x...L);
      T_LO[0] = Double.longBitsToDouble(0x...L);
      // ... rest of entries from CORE-MATH source ...
  }
  ```
- DB constants (sign-bit and any low-bit selectors) preserved as raw `long` constants (e.g. `S_SIGN`, `S2_0`, `S2_1` in Phase 2i exp port).

Length target: ~1500 LOC total in `SinCosKernel.java`.

- [ ] **Step 8: Write the failing tests**

Create `jquantlib/src/test/java/org/jquantlib/testsuite/math/transcendental/JQuantMathCosTest.java`:

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
 * Phase 2i.5 WI-1 — bit-exact validation of {@link JQuantMath#cos(double)}
 * against CORE-MATH cr_cos via the probe at
 * {@code migration-harness/references/math/transcendental/cos.json}.
 *
 * <p>EXACT tier: comparison is on raw {@code long} bit patterns
 * (NaN-payload-canonicalised). Per Phase 2i A3: probe oracle is
 * CORE-MATH cr_cos directly, NOT Apple libm std::cos.
 *
 * <p>Collect-all-failures pattern (per Phase 2i WI-1.1 review): iterate
 * every probe case before reporting; on failure show count + first 5.
 */
public class JQuantMathCosTest {

    @Test
    public void cos_bitExactAgainstCoreMathProbe() {
        final ReferenceReader ref = ReferenceReader.load("math/transcendental/cos");
        final List<String> mismatches = new ArrayList<>();
        for (String name : ref.caseNames()) {
            final ReferenceReader.Case c = ref.getCase(name);
            final double x = c.inputs().getDouble("x");
            final long expectedBits = MathTestSupport.parseHexBits(
                ((JSONObject) c.expected()).getString("y_bits"));
            final double actual = JQuantMath.cos(x);
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

Create `jquantlib/src/test/java/org/jquantlib/testsuite/math/transcendental/JQuantMathSinTest.java` with the same shape, swap `cos` for `sin` (test-group `math/transcendental/sin`, method `JQuantMath.sin`, class name `JQuantMathSinTest`).

- [ ] **Step 9: Run failing tests (compile-fail expected)**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-A/jquantlib
mvn test -Dtest='JQuantMathCosTest,JQuantMathSinTest' 2>&1 | tail -15
```

Expected: compile error — `SinCosKernel.cos` / `SinCosKernel.sin` body throws `UnsupportedOperationException`, OR test compiles and tests fail with the not-implemented error.

- [ ] **Step 10: Run tests at EXACT tier**

After implementing `SinCosKernel`:

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-A/jquantlib
mvn test -Dtest='JQuantMathCosTest,JQuantMathSinTest' 2>&1 | tail -15
```

Expected: 2 tests pass.

If any input mismatches:
- The collect-all-failures pattern shows count + first 5 — use that to diagnose.
- DO NOT loosen the tier — fix the algorithm.
- If a DB hard-case input mismatches and the implementer's algorithm is faithful to CORE-MATH (verified by tracing in C), the issue is the probe oracle — but since the probe oracle IS CORE-MATH (`cr_cos` from included source), the implementer's transcription has a bug. Trace it.
- If genuinely impossible to reach EXACT for some inputs after multiple investigation rounds (unlikely; CORE-MATH is correctly-rounded by design), that's an A2 trigger — pause and report.

- [ ] **Step 11: Run full suite**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-A/jquantlib && mvn test 2>&1 | grep "Tests run:" | tail -3
```

Expected: `Tests run: 686, Failures: 0, Errors: 0, Skipped: 22` (684 + 2 new EXACT tests).

- [ ] **Step 12: Scanner check**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-A && python3 tools/stub-scanner/scan_stubs.py 2>&1 | tail -3
```

Expected: `0 stubs`.

- [ ] **Step 13: Discard timestamp-only ref regenerations**

`generate-references.sh` re-runs all probes. Discard non-Phase-2i.5 timestamp-only updates:

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-A
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

Note: `migration-harness/references/math/transcendental/exp.json` may or may not be touched by the regen (it's based on a stable C++ probe, no source change here, so should be byte-identical post-regen — but if the timestamp updated, discard the change).

Should now show only:
- New: `migration-harness/cpp/probes/transcendental/coremath/sin.c`
- New: `migration-harness/cpp/probes/transcendental/coremath/cos.c`
- New: `migration-harness/cpp/probes/transcendental/coremath/<helpers>.h` (if any)
- New: `migration-harness/cpp/probes/transcendental/sin_probe.cpp`
- New: `migration-harness/cpp/probes/transcendental/cos_probe.cpp`
- New: `migration-harness/references/math/transcendental/sin.json`
- New: `migration-harness/references/math/transcendental/cos.json`
- New: `jquantlib/src/main/java/org/jquantlib/math/transcendental/SinCosKernel.java`
- Modified: `jquantlib/src/main/java/org/jquantlib/math/transcendental/JQuantMath.java`
- New: `jquantlib/src/test/java/org/jquantlib/testsuite/math/transcendental/JQuantMathSinTest.java`
- New: `jquantlib/src/test/java/org/jquantlib/testsuite/math/transcendental/JQuantMathCosTest.java`

- [ ] **Step 14: Commit and push**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-A
git add migration-harness/cpp/probes/transcendental/coremath/ \
        migration-harness/cpp/probes/transcendental/sin_probe.cpp \
        migration-harness/cpp/probes/transcendental/cos_probe.cpp \
        migration-harness/references/math/transcendental/sin.json \
        migration-harness/references/math/transcendental/cos.json \
        jquantlib/src/main/java/org/jquantlib/math/transcendental/SinCosKernel.java \
        jquantlib/src/main/java/org/jquantlib/math/transcendental/JQuantMath.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/transcendental/JQuantMathSinTest.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/transcendental/JQuantMathCosTest.java
git commit -s -m "infra(math.transcendental): port CORE-MATH correctly-rounded cos+sin → JQuantMath.cos/sin (Phase 2i.5 WI-1)"
git push origin phase-2i5-A-trig-port
```

- [ ] **Step 15: Fast-forward to main**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git merge --ff-only origin/phase-2i5-A-trig-port
git push origin main
```

- [ ] **Step 16: Update `phase2i.5-progress.md` — mark WI-1 ✅** and record commit SHA + new test count `686/0/0/22`. After this lands, dispatch L2 (WI-3 — depends on WI-1 being on main).

---

## Layer 1b — WI-2 NCCS CDF rewire + EXACT attempt (worktree B, parallel with L1a)

**Worktree:** `/Users/josemoya/eclipse-workspace/jquantlib-2i5-B`
**Branch:** `phase-2i5-B-nccs-rewire`
**Dispatches:** in parallel with L1a after L0 lands. Touches disjoint files from WI-1; no merge conflict expected. Subagent should be opus-class for the EXACT attempt + A19 diagnosis.

### Task 2.1: Rewire NCCS to `JQuantMath.exp` and switch probe oracle to `cr_exp`; attempt EXACT-tier

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/math/distributions/NonCentralCumulativeChiSquaredDistribution.java` (3 `Math.exp` call sites at lines 65, 82, 85 per pre-flight grep)
- Modify: `migration-harness/cpp/probes/math/distributions/noncentral_chi_squared_probe.cpp` (switch oracle from `std::exp` to `cr_exp` via `#include "../../transcendental/coremath/exp.c"`)
- Modify: `migration-harness/references/math/distributions/noncentral_chi_squared.json` (regenerated reference)
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/math/distributions/NonCentralChiSquaredDistributionTest.java` AND/OR `NonCentralCumulativeChiSquaredDistributionTest.java` (tier flip)

- [ ] **Step 0: Rebase worktree B on main**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-B
git fetch origin
git rebase origin/main
git log --oneline -3
```

Expected: clean. Tip at L0 progress-doc commit.

- [ ] **Step 1: Identify NCCS production-code Math.exp call sites**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-B
grep -n "Math\." jquantlib/src/main/java/org/jquantlib/math/distributions/NonCentralCumulativeChiSquaredDistribution.java
```

Confirm the 3 `Math.exp` call sites at approximately lines 65, 82, 85. Also note any `Math.log` calls (line 85: `Math.log(x2)`) — `Math.log` is OUT of Phase 2i.5 scope. Leave Math.log calls untouched.

- [ ] **Step 2: Swap `Math.exp → JQuantMath.exp` in NCCS production code**

Edit `jquantlib/src/main/java/org/jquantlib/math/distributions/NonCentralCumulativeChiSquaredDistribution.java`:

Add import:
```java
import org.jquantlib.math.transcendental.JQuantMath;
```

Replace the 3 `Math.exp(...)` call sites with `JQuantMath.exp(...)`. Leave `Math.log`, `Math.abs`, `Math.sqrt`, `Math.PI` UNCHANGED — only `exp` is in scope.

Example:
```java
// Before:  double u = Math.exp(-lam);
// After:   double u = JQuantMath.exp(-lam);
```

- [ ] **Step 3: Switch NCCS probe oracle from `std::exp` to `cr_exp`**

Edit `migration-harness/cpp/probes/math/distributions/noncentral_chi_squared_probe.cpp`. The prerequisite — `migration-harness/cpp/probes/transcendental/coremath/exp.c` already exists from Phase 2i WI-1.1 (commit `a61b920`). Add includes:

```cpp
extern "C" {
    #include "../../transcendental/coremath/exp.c"
}
```

Replace any `std::exp(...)` calls in the probe with `cr_exp(...)`. NCCS probe likely calls `std::exp` indirectly through QuantLib's NCCS C++ implementation — that's harder to redirect. Strategy: only replace `std::exp` calls that the probe code itself makes directly (if any). For `std::exp` calls inside the QuantLib NCCS code (linked from the harness QL submodule), those CAN'T easily be redirected without modifying QL itself — and we don't.

Inspection step: read the probe file first.

```bash
cat migration-harness/cpp/probes/math/distributions/noncentral_chi_squared_probe.cpp
```

If the probe is just calling QuantLib's `NonCentralChiSquareDistribution::operator()(x)` which internally uses `std::exp`, then this scenario is more complex than just adding a CORE-MATH include. Alternative: regenerate the probe references with the existing implementation (no probe change) and accept that the test will show whether CDF tier-EXACT is achievable against the *Apple libm-derived* reference. If A3 fires (some inputs disagree by 1 ULP), document and back off to TIGHT.

A pragmatic shortcut: skip Step 3 if the probe doesn't call `std::exp` directly. Just regenerate to refresh the timestamp-stable JSON, run the test, see whether the swap-only-on-Java-side achieves EXACT against the existing oracle. Document if A19 fires.

- [ ] **Step 4: Regenerate NCCS probe references**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-B
bash migration-harness/generate-references.sh 2>&1 | tail -5
git diff migration-harness/references/math/distributions/noncentral_chi_squared.json | head -20
```

Expected: minimal or no diff (the C++ probe runs against the same QL submodule; output should be byte-stable except for `generated_at` timestamp).

- [ ] **Step 5: Run the NCCS test (still TIGHT, sanity check)**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-B/jquantlib
mvn test -Dtest='NonCentralChiSquaredDistributionTest,NonCentralCumulativeChiSquaredDistributionTest' 2>&1 | tail -10
```

Expected: passes at TIGHT (the swap should not regress correctness — `JQuantMath.exp` is correctly-rounded, and the NCCS algorithm tolerates exp slack).

- [ ] **Step 6: Identify the tier annotation in the test file**

```bash
grep -n "Tolerance\.\(loose\|tight\|exact\|absRel\)\|assertEquals.*[0-9]e-[0-9]" \
  jquantlib/src/test/java/org/jquantlib/testsuite/math/distributions/NonCentralChiSquaredDistributionTest.java \
  jquantlib/src/test/java/org/jquantlib/testsuite/math/distributions/NonCentralCumulativeChiSquaredDistributionTest.java
```

Identify each TIGHT assertion. Look for inline comments citing Phase 2f A13 / Math.exp slack.

- [ ] **Step 7: Attempt EXACT-tier flip**

For each TIGHT assertion that cited `Math.exp` as the bottleneck, replace it with `MathTestSupport.assertBitsEqual(cppValue, javaValue)`:

```java
// Before:
// Phase 2f A13: TIGHT — JVM Math.exp ULP slack via Sankaran approximation.
assertTrue(Tolerance.tight(java, cpp));

// After (EXACT attempt):
MathTestSupport.assertBitsEqual(cpp, java);
```

(Note: `assertBitsEqual` is `(double expected, double actual)` — the C++ probe value is "expected".)

If the test imports are missing, add:
```java
import org.jquantlib.testsuite.util.MathTestSupport;
```

- [ ] **Step 8: Run the test at EXACT tier**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-B/jquantlib
mvn test -Dtest='NonCentralChiSquaredDistributionTest,NonCentralCumulativeChiSquaredDistributionTest' 2>&1 | tail -20
```

**Two scenarios:**

### Scenario A: EXACT passes ✅

Continue to Step 9. Final commit message reflects "TIGHT → EXACT" tier promotion.

### Scenario B: EXACT fails — A19 fires

The NCCS algorithm's Sankaran polynomial has accumulated rounding beyond the `Math.exp` slack. Diagnosis:

1. The test failure message will show which inputs differ. If only a few cases differ by exactly 1 ULP, that's A19's classic signature.
2. Revert the EXACT flip — restore TIGHT tier with updated comment:
   ```java
   // Phase 2i.5 WI-2 attempted EXACT after JQuantMath.exp swap. Residual
   // ~Xe-Y absolute survives — structural source other than Math.exp slack
   // (likely Sankaran polynomial accumulated rounding, or Math.log slack
   // from line 85 which is out of Phase 2i.5 scope). A19 fired; staying
   // TIGHT. Math.log port (Phase 2i.5+ scope) may close this.
   assertTrue(Tolerance.tight(java, cpp));
   ```
3. Adjust the commit message to reflect "tier unchanged TIGHT (A19 fired)".

- [ ] **Step 9: Run full suite**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-B/jquantlib && mvn test 2>&1 | grep "Tests run:" | tail -3
```

Expected: `Tests run: 686/0/0/22` (was 684 baseline, +2 from L1a if WI-1 already merged in B's view via fetch — but B is independent of A. Most likely B sees `Tests run: 684/0/0/22` since B doesn't have A's commits unless explicitly rebased on top. Actually if B was created off the L0 progress-doc commit, B's test count is `684`. After landing both A and B, main shows `686`).

If suite is red unrelated to the NCCS test: A8 — investigate before continuing.

- [ ] **Step 10: Scanner check**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-B && python3 tools/stub-scanner/scan_stubs.py 2>&1 | tail -3
```

Expected: `0 stubs`.

- [ ] **Step 11: Discard timestamp-only ref regenerations** (same pattern as Task 1.1 Step 13).

- [ ] **Step 12: Commit and push**

If EXACT succeeded:
```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-B
git add jquantlib/src/main/java/org/jquantlib/math/distributions/NonCentralCumulativeChiSquaredDistribution.java \
        migration-harness/cpp/probes/math/distributions/noncentral_chi_squared_probe.cpp \
        migration-harness/references/math/distributions/noncentral_chi_squared.json \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/distributions/NonCentralChiSquaredDistributionTest.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/distributions/NonCentralCumulativeChiSquaredDistributionTest.java
git commit -s -m "align(math.distributions): rewire NCCS to JQuantMath.exp; tier TIGHT → EXACT (Phase 2i.5 WI-2)"
git push origin phase-2i5-B-nccs-rewire
```

If EXACT failed (A19):
```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-B
git add jquantlib/src/main/java/org/jquantlib/math/distributions/NonCentralCumulativeChiSquaredDistribution.java \
        migration-harness/cpp/probes/math/distributions/noncentral_chi_squared_probe.cpp \
        migration-harness/references/math/distributions/noncentral_chi_squared.json \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/distributions/NonCentralChiSquaredDistributionTest.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/distributions/NonCentralCumulativeChiSquaredDistributionTest.java
git commit -s -m "align(math.distributions): rewire NCCS to JQuantMath.exp; tier unchanged (Phase 2i.5 WI-2, A19 — Sankaran polynomial / Math.log floor)"
git push origin phase-2i5-B-nccs-rewire
```

- [ ] **Step 13: Fast-forward to main**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git merge --ff-only origin/phase-2i5-B-nccs-rewire
git push origin main
```

If A's commit already landed on main, B's merge is a fast-forward of B's branch into main — should be conflict-free since A and B touched disjoint files. If conflict: A9 — controller resolves.

- [ ] **Step 14: Update `phase2i.5-progress.md` — mark WI-2 ✅** with tier outcome (EXACT or TIGHT-with-A19).

---

## Layer 2 — WI-3 GaussLag + GaussLob tier flips (worktree C, after WI-1 lands)

**Worktree:** `/Users/josemoya/eclipse-workspace/jquantlib-2i5-C`
**Branch:** `phase-2i5-C-trig-tier-flips`
**Pre-requisite:** L1a (WI-1 cos/sin port) MUST be on main. Worktree C rebases on main tip post-WI-1.

### Task 3.1: Swap `Math.cos`/`Math.sin → JQuantMath.cos`/`sin` in GaussLag + GaussLob tests; flip tier annotations

**Files:**
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/math/integrals/GaussLaguerreIntegrationTest.java` (line 87 per pre-flight grep — `Math.cos` integrand lambda; plus tier annotations)
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/math/integrals/GaussLobattoIntegralTest.java` (line 87 per pre-flight grep — `Math.sin` integrand lambda; plus tier annotations)

- [ ] **Step 0: Rebase worktree C on main (after WI-1 lands)**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-C
git fetch origin
git rebase origin/main
git log --oneline -5
```

Expected: clean rebase. Tip should include the WI-1 commit (`infra(math.transcendental): port CORE-MATH correctly-rounded cos+sin ...`).

Verify `JQuantMath.cos` and `JQuantMath.sin` exist:

```bash
grep -n "public static double cos\|public static double sin" jquantlib/src/main/java/org/jquantlib/math/transcendental/JQuantMath.java
```

Expected: both methods present.

- [ ] **Step 1: Identify `Math.cos`/`sin` integrand call sites**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-C
grep -n "Math\.\(cos\|sin\)" \
  jquantlib/src/test/java/org/jquantlib/testsuite/math/integrals/GaussLaguerreIntegrationTest.java \
  jquantlib/src/test/java/org/jquantlib/testsuite/math/integrals/GaussLobattoIntegralTest.java
```

Confirm:
- `GaussLaguerreIntegrationTest.java:87` — cos integrand
- `GaussLobattoIntegralTest.java:87` — sin integrand

(Other Math.exp calls in those files exist too — check via separate grep — but `exp` was already integrated in Phase 2i WI-2 B-1 if cited in audit; for Phase 2i.5 scope, swap ONLY `cos`/`sin`.)

- [ ] **Step 2: Read the per-test exception comments**

```bash
grep -B3 -A1 "Math\.\(cos\|sin\)" \
  jquantlib/src/test/java/org/jquantlib/testsuite/math/integrals/GaussLaguerreIntegrationTest.java \
  jquantlib/src/test/java/org/jquantlib/testsuite/math/integrals/GaussLobattoIntegralTest.java
```

Read the surrounding comment in `GaussLaguerreIntegrationTest.java:53` ("a few ULPs because Math.cos vs std::cos differ by 1 ULP per ..."). Confirm the comment cites Math.cos slack as the source. Same for GaussLob.

- [ ] **Step 3: Swap to JQuantMath.cos/sin in GaussLaguerreIntegrationTest**

Edit `jquantlib/src/test/java/org/jquantlib/testsuite/math/integrals/GaussLaguerreIntegrationTest.java`:

Add import (if not already present):
```java
import org.jquantlib.math.transcendental.JQuantMath;
```

Replace line 87 (or wherever the cos integrand is defined):
```java
// Before:
private static Ops.DoubleOp cos()      { return new Ops.DoubleOp() { public double op(double x){ return Math.cos(x); } }; }

// After:
private static Ops.DoubleOp cos()      { return new Ops.DoubleOp() { public double op(double x){ return JQuantMath.cos(x); } }; }
```

Update the inline comment near line 53 — replace the "Math.cos vs std::cos 1-ULP" justification with a Phase 2i.5 note:
```java
// Phase 2i.5 WI-3: swapped Math.cos → JQuantMath.cos (correctly-rounded
// against CORE-MATH cr_cos). Tier promotable from per-test ULP-slack to
// TIGHT (verify on next test run; back off if A19 fires).
```

Tighten any per-test tolerance assertion. If currently looking like:
```java
final double tolerance = 5e-15;  // a few ULPs because Math.cos slack
assertTrue("|java - cpp| > " + tolerance, Math.abs(java - cpp) <= tolerance);
```

Replace with:
```java
assertTrue(Tolerance.tight(java, cpp));
```

- [ ] **Step 4: Swap in GaussLobattoIntegralTest**

Edit `jquantlib/src/test/java/org/jquantlib/testsuite/math/integrals/GaussLobattoIntegralTest.java`:

Add import:
```java
import org.jquantlib.math.transcendental.JQuantMath;
```

Replace line 87:
```java
// Before:
private static Ops.DoubleOp sin()       { return new Ops.DoubleOp() { public double op(double x){ return Math.sin(x); } }; }

// After:
private static Ops.DoubleOp sin()       { return new Ops.DoubleOp() { public double op(double x){ return JQuantMath.sin(x); } }; }
```

Update inline comments + tier annotation similarly.

- [ ] **Step 5: Run the integral tests**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-C/jquantlib
mvn test -Dtest='GaussLaguerreIntegrationTest,GaussLobattoIntegralTest' 2>&1 | tail -15
```

**Two scenarios:**

### Scenario A: TIGHT passes ✅ for both

Continue to Step 6.

### Scenario B: A19 fires for one or both tests

The integral's `cos`/`sin` slack wasn't the dominant residual; some other source (Gauss quadrature node accumulation, integrand re-evaluation rounding) survives. Document inline:

```java
// Phase 2i.5 WI-3 attempted TIGHT after JQuantMath.cos swap. Residual
// ~Xe-Y survives — structural source other than Math.cos slack
// (likely Gauss quadrature node accumulation or integrand re-evaluation).
// A19 fired; staying at <chosen tier>.
```

Find the tightest-passing tier (TIGHT or LOOSE or per-test) and pin it.

- [ ] **Step 6: Run full suite**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-C/jquantlib && mvn test 2>&1 | grep "Tests run:" | tail -3
```

Expected: `Tests run: 686, Failures: 0, Errors: 0, Skipped: 22` (since C is rebased on main post-WI-1 and post-WI-2, baseline is now 686).

- [ ] **Step 7: Scanner check**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-C && python3 tools/stub-scanner/scan_stubs.py 2>&1 | tail -3
```

Expected: `0 stubs`.

- [ ] **Step 8: Commit and push**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2i5-C
git add jquantlib/src/test/java/org/jquantlib/testsuite/math/integrals/GaussLaguerreIntegrationTest.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/integrals/GaussLobattoIntegralTest.java
git commit -s -m "align(math.integrals): swap Math.cos/sin → JQuantMath.cos/sin in GaussLag + GaussLob test integrands; tier promoted to TIGHT (Phase 2i.5 WI-3)"
git push origin phase-2i5-C-trig-tier-flips
```

Adjust commit message subject if A19 fires — e.g. `... in GaussLag + GaussLob test integrands; tier <ACTUAL> (Phase 2i.5 WI-3, A19 partial)`.

- [ ] **Step 9: Fast-forward to main**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git merge --ff-only origin/phase-2i5-C-trig-tier-flips
git push origin main
```

- [ ] **Step 10: Update `phase2i.5-progress.md` — mark WI-3 ✅** with tier outcomes for both tests.

---

## Layer 3 — Completion doc + tag

### Task 4.1: Write the completion doc

**Files:**
- Create: `docs/migration/phase2i.5-completion.md`

- [ ] **Step 1:** Write the completion doc following Phase 2i shape.

Sections:
1. Header (status, predecessor tag, this phase's tip SHA, plan + design + progress doc commits)
2. Final state table (test count, scanner WIP, JQuantMath primitives now: exp + cos + sin)
3. Per-WI summary (commit SHAs, tier outcomes, A19 fires)
4. A-trigger fire history (which fired, where, mitigation)
5. Decision log additions (any P2I5-10+ surfaced during execution)
6. Phase 2j seed list refresh — what's still on the list:
   - **NCCS Math.log port** (if WI-2 A19 fired with log-floor diagnosis): high priority for Phase 2j
   - **CORE-MATH log port** (general): cited for NCCS, future BroadieKaya
   - **CORE-MATH pow port**: not currently cited but structural completeness
   - **BroadieKaya retry**: still deferred (audit predicted A19; may be addressable post-log)
   - **Douglas ADI / FdmAffineModelTermStructure**: Phase 2i WI-2 B-1 carry-forward (FdHullWhite real floor)
   - **Gaussian1D family**: Phase 2j primary scope
   - **Fdm framework completeness** (Bermudan/American/dividend, BiCGStab/GMRES, scheme expansion): Phase 2h carry-forward
   - **Other Fdm-dependent engines** (FdHestonHullWhite, etc.): unblocked by Phase 2h
   - **CubicInterpolation Address-mapping audit**: Phase 2h carry-forward

- [ ] **Step 2: Commit and push**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git add docs/migration/phase2i.5-completion.md
git commit -s -m "docs(migration): Phase 2i.5 completion — CORE-MATH cos/sin + NCCS rewire + audit tier discharge"
git push origin main
```

### Task 4.2: Tag the phase

- [ ] **Step 1: Create and push the tag**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git tag -a jquantlib-phase2i.5-complete -m "Phase 2i.5 complete — JQuantMath.cos/sin via CORE-MATH; NCCS CDF rewire to JQuantMath.exp + EXACT/TIGHT outcome; GaussLag + GaussLob tier promotions. Test count 686/0/0/22; scanner WIP=0."
git push origin jquantlib-phase2i.5-complete
```

- [ ] **Step 2: Verify tag is on the expected tip**

```bash
git show jquantlib-phase2i.5-complete --stat | head -10
```

### Task 4.3: Update memory

**Files:**
- Modify: `/Users/josemoya/.claude/projects/-Users-josemoya-eclipse-workspace-jquantlib/memory/MEMORY.md`
- Modify: `/Users/josemoya/.claude/projects/-Users-josemoya-eclipse-workspace-jquantlib/memory/project_jquantlib_migration.md`

- [ ] **Step 1:** Update `project_jquantlib_migration.md`:
  - Add a Phase 2i.5 paragraph under the Phase 2i one (with WI-1/WI-2/WI-3 outcomes, A-trigger fires, completion details)
  - Add 2026-04-28 date entry for the Phase 2i.5 completion
  - Update the description-line frontmatter with new tag/tip and refreshed Phase 2j candidate list

- [ ] **Step 2:** Update `MEMORY.md`:
  - Update the JQuantLib migration line — new tip, test count `686/0/0/22`, Phase 2j focus

(Memory updates are not committed — they live outside the repo.)

### Task 4.4: Tear down worktrees

- [ ] **Step 1: Remove the 3 phase-2i.5 worktrees**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git worktree remove --force ../jquantlib-2i5-A
git worktree remove --force ../jquantlib-2i5-B
git worktree remove --force ../jquantlib-2i5-C
git worktree list
```

(Phase 2i required `--force` due to submodule subtree; Phase 2i.5 likely the same.)

Expected: only main remains.

- [ ] **Step 2: Delete merged branches locally and on origin**

```bash
git branch -D phase-2i5-A-trig-port phase-2i5-B-nccs-rewire phase-2i5-C-trig-tier-flips
git push origin --delete phase-2i5-A-trig-port phase-2i5-B-nccs-rewire phase-2i5-C-trig-tier-flips
```

---

## Self-Review

(Run by writer before handoff — this section is for the controller / executor reference.)

**Spec coverage check:** every requirement in `phase2i.5-design.md`:
- §1 goals WI-1/WI-2/WI-3 → covered by L1a, L1b, L2 respectively.
- §2 chosen approach (paired CORE-MATH cos+sin via SinCosKernel) → encoded in Task 1.1 Steps 1-7.
- §3 worktree topology (A=WI-1, B=WI-2 parallel, C=WI-3 dependent) → enforced in L0 setup + dispatch ordering.
- §4 tolerance tiers (EXACT bit-pattern via MathTestSupport.bitsEqual) → encoded in test source code in Step 8 + assertion forms in WI-2 Step 7 / WI-3 Step 5.
- §4 probes (2 new probe sources + cr_*_oracle inclusion + DB coverage from day one) → covered by Task 1.1 Steps 1-3 (with explicit mention of DB coverage in cos_probe.cpp template comment).
- §4 NCCS probe regen → WI-2 Step 3.
- §4 test discipline (probe-before-port, no backfilling green, collect-all-failures) → embedded in test source templates.
- §4 test count target `684 → 686` → tracked in Step 11/9/6 expectations across WIs.
- §5 pause triggers (A2/A3/A19/etc.) → invocation conditions described in Step 10 (A2), Step 8 (A19) for both WI-2 and WI-3.
- §5 exit criteria → covered by L3 (completion doc + tag + memory update + worktree teardown).

**Placeholder scan:**
- "TBD-after-plan-lands" appears once intentionally in the progress-doc template (filled after L0 lands).
- `<helpers>.h` is a deliberate variable — CORE-MATH source structure determines what helpers are needed; the implementer fetches and includes whatever CORE-MATH actually uses.
- `0x...UL` placeholders appear inside example DB-entry transcription comments — these are explicitly labeled as "the implementer must extract by reading CORE-MATH source." This is a directed task, not a forgotten placeholder.

**Type consistency:**
- `JQuantMath.cos` / `JQuantMath.sin` static signatures (`public static double` of one `double` param) match across L1a definition and L2 call-site usage.
- `MathTestSupport.assertBitsEqual` and `bitsEqual` signatures match Phase 2i prior usage.
- Test-group strings in probes (`"math/transcendental/cos"`, `"math/transcendental/sin"`) match the `ReferenceReader.load(...)` calls in the test classes.
- `SinCosKernel` package-private status mirrors Phase 2i `ExpKernel` pattern.
