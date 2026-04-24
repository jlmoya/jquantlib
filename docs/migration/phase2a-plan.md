# Phase 2a Implementation Plan — JQuantLib Migration

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the five tail-of-Phase-1 work items inside the existing 61 `org.jquantlib.*` packages: (WI-1) scanner allowlist tidy, (WI-2) MINPACK `lmdif` + LevenbergMarquardt port, (WI-3) HestonProcess `QuadraticExponential` discretization branch, (WI-4) audit 56 `numerical_suspect` markers under tiered triage, (WI-5) delete `QL.validateExperimentalMode`. End state: scanner reports 2 `work_in_progress` (CapHelper, G2 — deferred to Phase 2b), 0 `not_implemented`, 0 `numerical_suspect`; tag `jquantlib-phase2a-complete`.

**Architecture:** Cycle-batched ports, TDD per commit, cross-validated against C++ QuantLib v1.42.1 via `migration-harness/` probes. **Direct commits to `main`, no migration branch** (design P2A-8). Every commit must compile and pass `(cd jquantlib && mvn test)` *before* it lands on `main`. See `docs/migration/phase2a-design.md` for the binding design; inherits `docs/migration/phase1-design.md` for discipline (tolerance tiers, cycle-batch rule, pause triggers A1–A6 plus new A7).

**Tech Stack:** Java 11 / Maven / JUnit 4 (existing); C++17 / CMake / QuantLib v1.42.1 pinned via submodule (harness scaffolded in Phase 1); Python 3 for scanner tooling; nlohmann/json for probe output.

---

## Overview

| Layer | Description | Expected commits |
|-------|-------------|------------------|
| L0 | Pre-flight: confirm baseline, snapshot scanner state | 0 |
| L1 | WI-1 scanner tidy — allowlist for `TreeLattice2D.grid` | 1 |
| L2 | WI-2 MINPACK `lmdif` + LevenbergMarquardt unblock + un-skip sweep | ~10 |
| L3 | WI-3 HestonProcess `QuadraticExponential` branch | 2–3 |
| L4 | WI-4 audit 56 `numerical_suspect` markers (tiered triage) | many (batched by package) |
| L5 | WI-5 `QL.validateExperimentalMode` deletion sweep | 1 |
| L6 | Completion doc + tag | 1 commit + tag |

Each layer ends with pause-trigger A6 — post a short summary, wait for user acknowledgment before starting the next layer.

**Non-goals reminder (design §2.2):** no new top-level packages; CapHelper and G2 deferred to Phase 2b; no broadening beyond the 56 `numerical_suspect` markers; no parallel-session execution.

**Git discipline (inherited, tightened per P2A-8):** every commit is signed off with `-s`; no `Co-authored-by: Claude` trailer; unsigned (no GPG/SSH); push direct to `origin main` after each commit is verified green. Commit messages follow `<kind>(<pkg>): <verb> ...` where `<kind>` is `stub`, `align`, `infra`, `chore`, `docs`, or `test`.

---

## Layer 0 — Pre-flight (no commits)

### Task 0.1: Confirm `main` is clean and green

- [ ] **Step 1:** Verify branch and clean working tree.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git status
git branch --show-current
```

Expected: branch `main`, working tree clean (no uncommitted files).

- [ ] **Step 2:** Run baseline test suite.

```bash
(cd jquantlib && mvn test) 2>&1 | tail -30
```

Expected: `BUILD SUCCESS`. Note the total test count in a scratch note — it is the baseline you will compare against after each commit.

### Task 0.2: Snapshot scanner state

- [ ] **Step 1:** Regenerate inventory.

```bash
python3 tools/stub-scanner/scan_stubs.py
```

Expected printed tail:
```
  not_implemented: 1
  numerical_suspect: 56
  work_in_progress: 6
```

Total 63 stubs. If any count differs from these, pause and investigate before any commits — the plan assumes this baseline.

- [ ] **Step 2:** Verify the 7 non-suspect stubs match the design §1.1 disposition table.

```bash
python3 -c "
import json
stubs = json.load(open('docs/migration/stub-inventory.json'))
for s in stubs:
    if s['kind'] != 'numerical_suspect':
        print(f\"{s['kind']:18} {s['id']:70} @ line {s['line']}\")
"
```

Expected exactly 7 entries: `QL.validateExperimentalMode` (line 391), `LevenbergMarquardt` × 2 (lines 43, 51), `TreeLattice2D#grid` (line 73, not_implemented), `CapHelper` (line 84), `G2.<...>` (line 126), `HestonProcess` (line 282).

### Task 0.3: Verify harness works end-to-end

- [ ] **Step 1:** Run the harness smoke test.

```bash
./migration-harness/verify-harness.sh 2>&1 | tail -20
```

Expected: exits 0 with a "harness verified" (or equivalent) message. The C++ build takes ~5–10 min on a cold cache. WI-2 and WI-3 both require this pass; do not proceed if it fails.

- [ ] **Step 2:** Confirm the harness submodule is at v1.42.1.

```bash
cd migration-harness/cpp/quantlib
git rev-parse HEAD
cd ../../..
```

Expected: `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`.

### Task 0.4: Layer checkpoint

- [ ] **Step 1:** Post a short summary to the user: "L0 done — baseline green (N tests), scanner at 63 stubs (6 WIP + 1 NI + 56 suspect), harness verified, submodule at v1.42.1. Starting L1." Wait for acknowledgment.

---

## Layer 1 — WI-1 Scanner tidy (1 commit)

Add an allowlist mechanism to `tools/stub-scanner/scan_stubs.py` so `methods.lattices.TreeLattice2D#grid` — which faithfully mirrors the C++ `QL_FAIL("not implemented")` contract — is no longer surfaced as an open stub.

### Task 1.1: Create the allowlist file

**Files:**
- Create: `docs/migration/stub-allowlist.json`

- [ ] **Step 1:** Write the file.

```bash
cat > docs/migration/stub-allowlist.json <<'EOF'
[
  {
    "stub_id": "methods.lattices.TreeLattice2D#grid",
    "kind": "not_implemented",
    "reason": "Java stub is a faithful port of a C++ QL_FAIL / throw that will never be implemented.",
    "cpp_counterpart": "ql/methods/lattices/lattice2d.hpp TreeLattice2D::grid() throws QL_FAIL(\"not implemented\")"
  }
]
EOF
```

- [ ] **Step 2:** Verify JSON parses.

```bash
python3 -c "import json; json.load(open('docs/migration/stub-allowlist.json'))" && echo OK
```

Expected: `OK`.

### Task 1.2: Teach `scan_stubs.py` to read the allowlist

**Files:**
- Modify: `tools/stub-scanner/scan_stubs.py`

- [ ] **Step 1:** Add allowlist loader. Insert this block in `scan_stubs.py` immediately after the `PACKAGE_DECL = re.compile(...)` line (around line 47):

```python

ALLOWLIST_PATH = REPO_ROOT / "docs" / "migration" / "stub-allowlist.json"
ALLOWLIST_REASON_PREFIX = "Java stub is a faithful port of a C++ QL_FAIL"


def load_allowlist() -> set[str]:
    """Read docs/migration/stub-allowlist.json and return stub_ids to skip.
    Only entries whose reason matches ALLOWLIST_REASON_PREFIX are permitted;
    any other justification belongs in phase2a-carveouts.md. See
    docs/migration/phase2a-design.md §3.1 and §6.5 R7."""
    if not ALLOWLIST_PATH.exists():
        return set()
    entries = json.loads(ALLOWLIST_PATH.read_text(encoding="utf-8"))
    ids: set[str] = set()
    for e in entries:
        if not e.get("reason", "").startswith(ALLOWLIST_REASON_PREFIX):
            raise ValueError(
                f"Allowlist entry {e.get('stub_id')!r} has a rejected reason. "
                "The allowlist is reserved for faithful QL_FAIL ports; put "
                "other justifications in docs/migration/phase2a-carveouts.md."
            )
        ids.add(e["stub_id"])
    return ids
```

- [ ] **Step 2:** Apply the filter in `scan_tree()`. Find:

```python
def scan_tree() -> list[Stub]:
    stubs: list[Stub] = []
    for java in JAVA_ROOT.rglob("*.java"):
        if "/test/" in java.as_posix() or java.stem.endswith("Test"):
            continue  # test files are never stubs
        stubs.extend(scan_file(java))
    stubs.sort(key=lambda s: (s.file, s.line))
    return stubs
```

Replace with:

```python
def scan_tree() -> list[Stub]:
    allowlist = load_allowlist()
    stubs: list[Stub] = []
    for java in JAVA_ROOT.rglob("*.java"):
        if "/test/" in java.as_posix() or java.stem.endswith("Test"):
            continue  # test files are never stubs
        stubs.extend(s for s in scan_file(java) if s.id not in allowlist)
    stubs.sort(key=lambda s: (s.file, s.line))
    return stubs
```

### Task 1.3: Regenerate inventory and verify

- [ ] **Step 1:** Run scanner.

```bash
python3 tools/stub-scanner/scan_stubs.py
```

Expected printed tail:
```
  not_implemented: 0
  numerical_suspect: 56
  work_in_progress: 6
```

- [ ] **Step 2:** Confirm TreeLattice2D no longer in inventory.

```bash
grep -c TreeLattice2D docs/migration/stub-inventory.json
```

Expected: `0`.

- [ ] **Step 3:** Sanity check — allowlist violation path.

```bash
python3 -c "
import json, sys
sys.path.insert(0, 'tools/stub-scanner')
# Mutate the allowlist in memory and verify load_allowlist rejects bad reasons
import importlib.util, tempfile, os
spec = importlib.util.spec_from_file_location('scan_stubs', 'tools/stub-scanner/scan_stubs.py')
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)
orig = mod.ALLOWLIST_PATH
with tempfile.NamedTemporaryFile('w', suffix='.json', delete=False) as f:
    json.dump([{'stub_id':'x','kind':'k','reason':'because I felt like it'}], f)
    tmp = f.name
mod.ALLOWLIST_PATH = type(orig)(tmp)
try:
    mod.load_allowlist()
except ValueError as e:
    print('OK rejected:', str(e)[:60])
os.unlink(tmp)
"
```

Expected: `OK rejected: Allowlist entry 'x' has a rejected reason...`.

### Task 1.4: Commit

- [ ] **Step 1:** Stage and commit.

```bash
git add docs/migration/stub-allowlist.json tools/stub-scanner/scan_stubs.py docs/migration/stub-inventory.json docs/migration/worklist.md
git commit -s -m "$(cat <<'EOF'
infra(scanner): add allowlist; exempt TreeLattice2D.grid

TreeLattice2D.grid throws "not implemented" exactly as its C++ v1.42.1
counterpart (QL_FAIL in lattice2d.hpp) does. It is a faithful port,
not an open stub. Add a narrow allowlist with a strict reason-string
criterion so similar future faithful-port cases have a home without
inviting carve-out abuse (see phase2a-design §3.1, §6.5 R7).

WI-1 of Phase 2a. not_implemented count: 1 -> 0.
EOF
)"
```

- [ ] **Step 2:** Run tests and push.

```bash
(cd jquantlib && mvn test) 2>&1 | tail -5
git push origin main
```

Expected: BUILD SUCCESS; push succeeds.

### Task 1.5: Layer checkpoint

- [ ] **Step 1:** Post summary: "L1 done — scanner allowlist live, TreeLattice2D exempted, 62 stubs remaining (6 WIP + 56 suspect). Starting L2 (MINPACK)."

---

## Layer 2 — WI-2 MINPACK + LevenbergMarquardt (~10 commits)

Port the `QuantLib::MINPACK` namespace (~1700 LOC in `ql/math/optimization/lmdif.cpp`) into a new `org.jquantlib.math.optimization.MINPACK` Java class; fill the two `LevenbergMarquardt` constructor-gate stubs plus the `minimize()` method body; un-skip the three LM-dependent test files.

**Sequence rationale:** port the public helpers (`qrfac`, `qrsolv`) with bit-exact probes FIRST. If those drift, the entire downstream driver work is wasted — catching structural bugs in 100-LOC helpers is much cheaper than in 800-LOC drivers.

**Order:** `enorm` → `qrfac` (+probe) → `qrsolv` (+probe) → `lmpar` → `fdjac2` → `lmdif` (+4 LM probes) → fill `LevenbergMarquardt` → un-skip `SABRInterpolationTest` → un-skip `InterpolationTest` LM tests → un-skip `OptimizerTest` LM tests.

**File layout (design P2A-5):**

- Create: `jquantlib/src/main/java/org/jquantlib/math/optimization/MINPACK.java`
  - Public static: `lmdif`, `qrfac`, `qrsolv`
  - `@FunctionalInterface LmdifCostFunction` (nested)
  - Private static: `lmpar`, `enorm`, `fdjac2`
- Modify: `jquantlib/src/main/java/org/jquantlib/math/optimization/LevenbergMarquardt.java`
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/math/optimization/MINPACKTest.java`
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/math/optimization/LevenbergMarquardtTest.java`
- Create: `migration-harness/cpp/probes/math/optimization/minpack_qrfac_probe.cpp`
- Create: `migration-harness/cpp/probes/math/optimization/minpack_qrsolv_probe.cpp`
- Create: `migration-harness/cpp/probes/math/optimization/levenbergmarquardt_probe.cpp`

**Translation-style invariants (design §3.2):** raw `double[]` arrays — no `Array`/`Matrix` wrappers; `int*`/`double*` out-params → `int[1]`/`double[1]`; pointer-arithmetic sub-array access → base array + offset; C++ goto-style flow → labeled `break`/`continue`, no refactor.

### Task 2.1: Port `enorm` (commit 1)

`enorm` = Euclidean norm with overflow/underflow protection. ~30 LOC in `lmdif.cpp`. Private static. No public probe needed; a direct Java unit test covering the two extreme-magnitude branches (large and small) plus the normal branch is sufficient — it is pure arithmetic with no QuantLib state.

- [ ] **Step 1:** Create `MINPACK.java` skeleton.

```java
// jquantlib/src/main/java/org/jquantlib/math/optimization/MINPACK.java
/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Port of QuantLib v1.42.1 QuantLib::MINPACK namespace (lmdif.{hpp,cpp}).

 This source is released under the BSD License.
 JQuantLib is based on QuantLib. http://quantlib.org/
 */
package org.jquantlib.math.optimization;

/**
 * Port of QuantLib::MINPACK (ql/math/optimization/lmdif.{hpp,cpp}).
 * Algorithm: MINPACK lmdif — Levenberg-Marquardt minimization of the
 * sum-of-squares of m nonlinear functions in n variables.
 * <p>
 * Layout mirrors the C++ namespace: public static {@link #lmdif},
 * {@link #qrfac}, {@link #qrsolv}; private static {@code lmpar},
 * {@code enorm}, {@code fdjac2}. See phase2a-design.md §3.2.
 */
public final class MINPACK {

    private MINPACK() { }  // no instances

    @FunctionalInterface
    public interface LmdifCostFunction {
        void evaluate(int m, int n, double[] x, double[] fvec, int[] iflag);
    }

    // --- Helpers (order of file appearance, same as lmdif.cpp) ---

    // enorm: Euclidean norm of a vector, with overflow/underflow protection.
    // Port of MINPACK enorm (Fortran -> C++ -> Java).
    private static double enorm(int n, double[] x) {
        // TODO fill in Task 2.1.
        return 0.0;
    }
}
```

- [ ] **Step 2:** Write failing test.

```java
// jquantlib/src/test/java/org/jquantlib/testsuite/math/optimization/MINPACKTest.java
package org.jquantlib.testsuite.math.optimization;

import org.jquantlib.math.optimization.MINPACK;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class MINPACKTest {

    @Test
    public void enorm_zeroVector() throws Exception {
        double[] x = { 0.0, 0.0, 0.0 };
        assertEquals(0.0, invokeEnorm(3, x), 0.0);
    }

    @Test
    public void enorm_normalMagnitude() throws Exception {
        double[] x = { 3.0, 4.0 };   // classic 3-4-5
        assertEquals(5.0, invokeEnorm(2, x), 0.0);
    }

    @Test
    public void enorm_overflowSafe() throws Exception {
        // Elements too large to square directly; naive sum-of-squares would overflow
        double[] x = { 1.0e200, 1.0e200 };
        // expected = 1e200 * sqrt(2)
        assertEquals(1.0e200 * Math.sqrt(2.0), invokeEnorm(2, x), 1.0e186);
    }

    @Test
    public void enorm_underflowSafe() throws Exception {
        double[] x = { 1.0e-200, 1.0e-200 };
        assertEquals(1.0e-200 * Math.sqrt(2.0), invokeEnorm(2, x), 1.0e-214);
    }

    // enorm is package-private; access via reflection for tests.
    private static double invokeEnorm(int n, double[] x) throws Exception {
        java.lang.reflect.Method m = MINPACK.class.getDeclaredMethod("enorm", int.class, double[].class);
        m.setAccessible(true);
        return (Double) m.invoke(null, n, x);
    }
}
```

- [ ] **Step 3:** Run test, verify failure.

```bash
(cd jquantlib && mvn test -Dtest=MINPACKTest 2>&1) | tail -20
```

Expected: `enorm_normalMagnitude` etc. fail because `enorm` currently returns `0.0`.

- [ ] **Step 4:** Port `enorm` from `lmdif.cpp`. Open the C++ source and translate the function line-by-line preserving the three-threshold structure (`rdwarf`, `rgiant`, scaled accumulators). Replace the stub body with the port. Apply P2A-5 invariants: raw arrays, C-style indexing, no abstraction.

- [ ] **Step 5:** Run test, verify green.

```bash
(cd jquantlib && mvn test -Dtest=MINPACKTest 2>&1) | tail -10
```

Expected: all four `enorm_*` tests pass.

- [ ] **Step 6:** Run the full test suite.

```bash
(cd jquantlib && mvn test) 2>&1 | tail -5
```

Expected: BUILD SUCCESS, baseline test count unchanged.

- [ ] **Step 7:** Commit.

```bash
git add jquantlib/src/main/java/org/jquantlib/math/optimization/MINPACK.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/optimization/MINPACKTest.java
git commit -s -m "$(cat <<'EOF'
stub(math.optimization): port MINPACK.enorm

First cycle-batch of the MINPACK port (WI-2 of Phase 2a). enorm is the
Euclidean-norm helper with overflow/underflow protection; ~30 LOC,
mirrors lmdif.cpp line-by-line. Private static; reflection-access in
tests. No probe needed: pure arithmetic with no QuantLib state, unit
tests cover normal + extreme-magnitude branches.
EOF
)"
git push origin main
```

### Task 2.2: Port `qrfac` + bit-exact probe (commit 2)

`qrfac` = QR factorization with column pivoting. ~100 LOC. Public (exported in C++ header). Uses `enorm`.

**Probe tolerance:** exact (phase1-design §4.2). QR on fixed-input matrices is deterministic linear algebra; any drift means a structural bug.

- [ ] **Step 1:** Write the C++ probe.

```cpp
// migration-harness/cpp/probes/math/optimization/minpack_qrfac_probe.cpp
// Reference values for MINPACK::qrfac (QR factorization with column pivoting).
// Bit-exact: if Java diverges even in the last bit, something structural is
// wrong. Uses two fixed matrices: 3x3 full-rank and 4x2 tall.

#include <ql/version.hpp>
#include <ql/math/optimization/lmdif.hpp>
#include "../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {
json vec(const double* p, int n) {
    json j = json::array();
    for (int i = 0; i < n; ++i) j.push_back(p[i]);
    return j;
}
json ipvt_vec(const int* p, int n) {
    json j = json::array();
    for (int i = 0; i < n; ++i) j.push_back(p[i]);
    return j;
}
}

int main() {
    ReferenceWriter out("math/optimization/minpack_qrfac", QL_VERSION, "minpack_qrfac_probe");

    // Case A: 3x3 full-rank matrix, column-major (as lmdif.cpp uses).
    {
        int m = 3, n = 3;
        double a[] = { 1.0, 4.0, 7.0,   2.0, 5.0, 8.1,   3.0, 6.2, 9.3 };
        int ipvt[3] = { 0, 0, 0 };
        double rdiag[3] = { 0.0, 0.0, 0.0 };
        double acnorm[3] = { 0.0, 0.0, 0.0 };
        double wa[3] = { 0.0, 0.0, 0.0 };
        MINPACK::qrfac(m, n, a, m, 1, ipvt, n, rdiag, acnorm, wa);
        out.addCase("qrfac_3x3_fullrank",
            json{{"m", m}, {"n", n}, {"a_in", {1.0,4.0,7.0,2.0,5.0,8.1,3.0,6.2,9.3}}},
            json{{"a_out", vec(a, m*n)}, {"ipvt", ipvt_vec(ipvt, n)},
                 {"rdiag", vec(rdiag, n)}, {"acnorm", vec(acnorm, n)}});
    }

    // Case B: 4x2 tall matrix.
    {
        int m = 4, n = 2;
        double a[] = { 1.0, 2.0, 3.0, 4.0,   5.0, 6.0, 7.0, 8.5 };
        int ipvt[2] = { 0, 0 };
        double rdiag[2] = { 0.0, 0.0 };
        double acnorm[2] = { 0.0, 0.0 };
        double wa[2] = { 0.0, 0.0 };
        MINPACK::qrfac(m, n, a, m, 1, ipvt, n, rdiag, acnorm, wa);
        out.addCase("qrfac_4x2_tall",
            json{{"m", m}, {"n", n}, {"a_in", {1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.5}}},
            json{{"a_out", vec(a, m*n)}, {"ipvt", ipvt_vec(ipvt, n)},
                 {"rdiag", vec(rdiag, n)}, {"acnorm", vec(acnorm, n)}});
    }

    out.write();
    return 0;
}
```

- [ ] **Step 2:** Build the probe and capture the reference.

```bash
./migration-harness/verify-harness.sh 2>&1 | tail -10
```

Expected: probe builds; `migration-harness/references/math/optimization/minpack_qrfac/*.json` exists.

- [ ] **Step 3:** Write the failing Java tests. Uses the Phase 1 utilities: `ReferenceReader.load(group)` returns a reader, `reader.getCase(name)` returns a `Case`, `c.inputs()` returns a `JSONObject`, `c.expectedRaw()` returns the raw `Object` (a `JSONObject` for multi-field outputs). Tolerance is scalar-only (`Tolerance.exact(double, double)` → boolean); use small local helpers for arrays. Add to `MINPACKTest.java`:

```java
import org.json.JSONArray;
import org.json.JSONObject;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

// Local helpers — small, scoped to this test class.
private static double[] toDoubleArray(JSONArray a) {
    double[] out = new double[a.length()];
    for (int i = 0; i < out.length; i++) out[i] = a.getDouble(i);
    return out;
}
private static int[] toIntArray(JSONArray a) {
    int[] out = new int[a.length()];
    for (int i = 0; i < out.length; i++) out[i] = a.getInt(i);
    return out;
}
private static void assertDoublesExact(String name, double[] exp, double[] got) {
    if (exp.length != got.length)
        fail(name + ": length mismatch exp=" + exp.length + " got=" + got.length);
    for (int i = 0; i < exp.length; i++) {
        if (!Tolerance.exact(got[i], exp[i]))
            fail(name + "[" + i + "]: exp=" + exp[i] + " got=" + got[i]);
    }
}
private static void assertIntsExact(String name, int[] exp, int[] got) {
    if (exp.length != got.length)
        fail(name + ": length mismatch exp=" + exp.length + " got=" + got.length);
    for (int i = 0; i < exp.length; i++) {
        if (exp[i] != got[i])
            fail(name + "[" + i + "]: exp=" + exp[i] + " got=" + got[i]);
    }
}

@Test
public void qrfac_3x3_fullrank() {
    Case c = ReferenceReader.load("math/optimization/minpack_qrfac").getCase("qrfac_3x3_fullrank");
    JSONObject in = c.inputs();
    int m = in.getInt("m"), n = in.getInt("n");
    double[] a = toDoubleArray(in.getJSONArray("a_in"));
    int[] ipvt = new int[n];
    double[] rdiag = new double[n], acnorm = new double[n], wa = new double[n];
    MINPACK.qrfac(m, n, a, m, 1, ipvt, n, rdiag, acnorm, wa);
    JSONObject exp = (JSONObject) c.expectedRaw();
    assertDoublesExact("a_out", toDoubleArray(exp.getJSONArray("a_out")), a);
    assertIntsExact("ipvt", toIntArray(exp.getJSONArray("ipvt")), ipvt);
    assertDoublesExact("rdiag", toDoubleArray(exp.getJSONArray("rdiag")), rdiag);
    assertDoublesExact("acnorm", toDoubleArray(exp.getJSONArray("acnorm")), acnorm);
}

@Test
public void qrfac_4x2_tall() {
    Case c = ReferenceReader.load("math/optimization/minpack_qrfac").getCase("qrfac_4x2_tall");
    JSONObject in = c.inputs();
    int m = in.getInt("m"), n = in.getInt("n");
    double[] a = toDoubleArray(in.getJSONArray("a_in"));
    int[] ipvt = new int[n];
    double[] rdiag = new double[n], acnorm = new double[n], wa = new double[n];
    MINPACK.qrfac(m, n, a, m, 1, ipvt, n, rdiag, acnorm, wa);
    JSONObject exp = (JSONObject) c.expectedRaw();
    assertDoublesExact("a_out", toDoubleArray(exp.getJSONArray("a_out")), a);
    assertIntsExact("ipvt", toIntArray(exp.getJSONArray("ipvt")), ipvt);
    assertDoublesExact("rdiag", toDoubleArray(exp.getJSONArray("rdiag")), rdiag);
    assertDoublesExact("acnorm", toDoubleArray(exp.getJSONArray("acnorm")), acnorm);
}
```

If the array-assertion helpers end up needed in multiple test files (likely), promote them to `org.jquantlib.testsuite.util.TestArrays` as a sibling of `Tolerance`/`ReferenceReader`. Do that extraction when the third test would copy them, not earlier (YAGNI).

- [ ] **Step 4:** Add the stub `qrfac` method (public static) to `MINPACK.java` returning nothing (arrays left unmodified). Run tests; expect failure.

- [ ] **Step 5:** Port `qrfac` from `lmdif.cpp` line-by-line. Apply P2A-5 invariants.

- [ ] **Step 6:** Run tests. Expect both `qrfac_*` tests green.

- [ ] **Step 7:** Run full suite: `(cd jquantlib && mvn test)` — must be BUILD SUCCESS.

- [ ] **Step 8:** Commit.

```bash
git add jquantlib/src/main/java/org/jquantlib/math/optimization/MINPACK.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/optimization/MINPACKTest.java \
        migration-harness/cpp/probes/math/optimization/minpack_qrfac_probe.cpp \
        migration-harness/references/math/optimization/minpack_qrfac/
# (include any Tolerance/ReferenceReader additions if added)
git commit -s -m "$(cat <<'EOF'
stub(math.optimization): port MINPACK.qrfac with bit-exact probe

QR factorization with column pivoting; ~100 LOC. Public helper in the
C++ MINPACK namespace. Two bit-exact probes: 3x3 full-rank and 4x2
tall. Any drift here is structural and blocks the downstream LM
driver (phase2a-design §3.2).
EOF
)"
git push origin main
```

### Task 2.3: Port `qrsolv` + bit-exact probe (commit 3)

`qrsolv` = least-squares solve on a QR-factored upper-triangular system. ~80 LOC. Public helper. Standalone (no dependencies on other MINPACK helpers).

- [ ] **Step 1:** Write `minpack_qrsolv_probe.cpp` following the `qrfac` probe template. Use two cases: 3x3 upper-triangular system and 4x4 with non-trivial `diag` (damping vector).

- [ ] **Step 2:** Build probe, capture reference.

- [ ] **Step 3:** Write failing Java tests referencing the two cases.

- [ ] **Step 4:** Port `qrsolv` from `lmdif.cpp` line-by-line.

- [ ] **Step 5:** Run tests; expect green. Run full suite; expect BUILD SUCCESS.

- [ ] **Step 6:** Commit.

```bash
git commit -s -m "$(cat <<'EOF'
stub(math.optimization): port MINPACK.qrsolv with bit-exact probe

LM-damped upper-triangular least-squares solve (~80 LOC). Second
public MINPACK helper; together with qrfac it pins the linear-algebra
core of lmdif. Two bit-exact probes cover a 3x3 triangular system and
a 4x4 case with non-trivial diag vector.
EOF
)"
```

### Task 2.4: Port `lmpar` (commit 4)

`lmpar` = compute the Levenberg parameter. ~150 LOC. Private static. Depends on `qrsolv` and `enorm`. No dedicated probe — exercised indirectly by the full LM probes in Task 2.6. A simple sanity test (LM parameter = 0 when system is well-conditioned) suffices.

- [ ] **Step 1:** Write a sanity Java test (reflection access to `lmpar`) that runs a well-conditioned 2x2 case and asserts the output parameter magnitudes are finite and non-negative.

- [ ] **Step 2:** Port `lmpar` from `lmdif.cpp` line-by-line.

- [ ] **Step 3:** Run sanity test and full suite; expect BUILD SUCCESS.

- [ ] **Step 4:** Commit.

```bash
git commit -s -m "stub(math.optimization): port MINPACK.lmpar

Levenberg parameter computation (~150 LOC, private). Exercised via
full LM probes in the next commit; sanity Java test covers the
well-conditioned case to catch egregious transcription errors."
```

### Task 2.5: Port `fdjac2` (commit 5)

`fdjac2` = forward-differences approximation to the Jacobian. ~50 LOC. Private static. Takes a cost-function callback.

- [ ] **Step 1:** Write a Java unit test with a known cost function (`f(x) = [2*x[0], x[0]+x[1]]`, Jacobian known-exact). Use the `LmdifCostFunction` functional interface. Assert the numerical Jacobian matches the analytic Jacobian to within ~1e-6 (forward diffs are first-order; `epsfcn = 1e-8` gives about 1e-4 accuracy in mid-regime — tune the tolerance based on `epsfcn`).

- [ ] **Step 2:** Port `fdjac2`.

- [ ] **Step 3:** Run tests and full suite.

- [ ] **Step 4:** Commit.

```bash
git commit -s -m "stub(math.optimization): port MINPACK.fdjac2

Forward-difference Jacobian (~50 LOC, private). Test uses a linear
cost function where analytic J is known exact; asserts within
first-order diff accuracy."
```

### Task 2.6: Port `lmdif` driver + 4 LM-level probes (commit 6)

`lmdif` = the top-level MINPACK driver. ~700 LOC. Public. Depends on every earlier helper.

**Probes:** 4 cases per design §3.2 table:

| Case | Probe sub-case | Tolerance |
|---|---|---|
| linear-fit | `lm_linear_fit` | tight |
| quadratic-fit | `lm_quadratic_fit` | tight |
| ill-conditioned | `lm_rosenbrock` | tight on `info`/`nfev`, loose on params |
| early-stop | `lm_maxfev_earlystop` | exact on `info`, tight on params |

- [ ] **Step 1:** Write `levenbergmarquardt_probe.cpp` with the four cases. Each calls `MINPACK::lmdif` directly with hand-written cost callbacks, captures `x` (params), `fvec` (residuals), `info`, `nfev`, and `fjac` (Jacobian).

- [ ] **Step 2:** Build probe, capture references.

- [ ] **Step 3:** Write failing Java tests for all four cases.

- [ ] **Step 4:** Port `lmdif` line-by-line. Preserve goto-style flow using labeled `break`/`continue`. Do not refactor — structural parity beats readability here (design §3.2). The driver has ~6 convergence branches; the fact that each one maps to a specific `info` value means cross-validation catches any branch misordering immediately.

- [ ] **Step 5:** Run all four LM probes' Java counterparts; expect green.

- [ ] **Step 6:** Run the full test suite.

- [ ] **Step 7:** Commit.

```bash
git commit -s -m "$(cat <<'EOF'
stub(math.optimization): port MINPACK.lmdif driver + 4 LM probes

Top-level MINPACK driver (~700 LOC). Preserves C++ goto-style control
flow via labeled break/continue (design P2A-5, §3.2 translation
invariants). Four probes: linear fit, quadratic fit, Rosenbrock-style
ill-conditioned, and forced-early-stop via maxfev. MINPACK.lmdif is
now functionally complete.
EOF
)"
```

### Task 2.7: Fill `LevenbergMarquardt` stubs + `minimize()` body (commit 7)

The two scanner-counted stubs are the EXPERIMENTAL-gate throws in both constructors (lines 43, 51). The `minimize()` method body is commented-out C++ at lines 59–129. Task 2.7 fills all three.

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/math/optimization/LevenbergMarquardt.java`

- [ ] **Step 1:** Delete the EXPERIMENTAL-gate block from both constructors. After editing, the two constructors contain just their field assignments.

- [ ] **Step 2:** Rewrite `minimize()` to delegate to `MINPACK.lmdif`. The C++ reference is the commented-out body at lines 87–127 of the current Java file plus the full `minimize` implementation in `levenbergmarquardt.cpp`. Translate the full method, not the current stub. Preserve `ProblemData` (it is the Java port of C++ `ProblemData` singleton).

- [ ] **Step 3:** Delete the now-dead stub `fcn(int x1, int n, double x2, double fvec, int x3)` method at line 131. Port the real C++ `fcn` at lines 132–145 of the current file's commented body (free static with `int`, `int n`, `double* x`, `double* fvec`, `int*` signature translated to Java conventions).

- [ ] **Step 4:** Write `LevenbergMarquardtTest.java` — three tests corresponding to the design probes, but exercising the `LevenbergMarquardt` class's `minimize(Problem, EndCriteria)` API rather than `MINPACK.lmdif` directly. Use the same reference JSON files. This verifies the facade layer works, not just the underlying MINPACK.

- [ ] **Step 5:** Run the new tests and full suite.

- [ ] **Step 6:** Re-run the scanner; confirm `work_in_progress` drops from 6 to 4 (LM×2 gone).

```bash
python3 tools/stub-scanner/scan_stubs.py
grep LevenbergMarquardt docs/migration/stub-inventory.json
```

Expected: scanner tail shows `work_in_progress: 4`; grep returns empty.

- [ ] **Step 7:** Commit.

```bash
git commit -s -m "$(cat <<'EOF'
stub(math.optimization): fill LevenbergMarquardt.minimize via MINPACK

Removes the EXPERIMENTAL gates from both constructors and rewrites
minimize() to delegate into MINPACK.lmdif, matching the C++ facade.
Ports the real fcn static method (Java signature adapted). Stubs:
2 -> 0.
EOF
)"
```

### Task 2.8: Un-skip `SABRInterpolationTest` (commit 8)

**Files:**
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationTest.java`

- [ ] **Step 1:** Remove the `@Ignore` annotation and the `//TODO: uncomment when Minpack.lmdif becomes available` comment (lines 47–48).

- [ ] **Step 2:** Run just this test.

```bash
(cd jquantlib && mvn test -Dtest=SABRInterpolationTest 2>&1) | tail -20
```

Expected: PASS. If it fails, treat as a Tier-2 audit item (design §3.2, §4.3): probe the divergence, align Java, land the align fix first as a separate commit, then land the un-skip.

- [ ] **Step 3:** Run the full suite. Expected BUILD SUCCESS with the test count having increased by the SABR test count.

- [ ] **Step 4:** Commit and push.

```bash
git add jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationTest.java
git commit -s -m "align(interpolations): un-skip SABRInterpolationTest after LM port

LevenbergMarquardt is now functional (WI-2 Tasks 2.1-2.7); the test
no longer needs to be @Ignore'd. No production code change."
git push origin main
```

### Task 2.9: Un-skip `InterpolationTest` LM-dependent tests (commit 9)

- [ ] **Step 1:** Find and inspect the `@Ignore`d tests at lines 423 and 1040 of `InterpolationTest.java`. One uses `LevenbergMarquardt` (line 1111), the other is `testMultiSpline` — verify whether `testMultiSpline` depends on LM. If not, leave its `@Ignore` in place (it is a pre-existing unrelated skip).

- [ ] **Step 2:** Un-skip only the LM-dependent tests. If `testMultiSpline` is unrelated, leave it.

- [ ] **Step 3:** Run `(cd jquantlib && mvn test -Dtest=InterpolationTest)` — expect green. If anything fails, treat as Tier-2 audit item.

- [ ] **Step 4:** Commit.

```bash
git commit -s -m "align(interpolations): un-skip LM-dependent InterpolationTest cases"
```

### Task 2.10: Un-skip `OptimizerTest` (commit 10)

- [ ] **Step 1:** Remove the `@Ignore` at line 47 of `OptimizerTest.java`.

- [ ] **Step 2:** Run the test; expect green. Handle any failure as Tier-2.

- [ ] **Step 3:** Commit.

```bash
git commit -s -m "align(math.optimization): un-skip OptimizerTest after LM port"
```

### Task 2.11: Layer checkpoint

- [ ] **Step 1:** Post a summary to the user: "L2 done in N commits — MINPACK ported (~1700 LOC, 6 probes), LM unblocked, three test files un-skipped. Scanner: 4 WIP + 56 suspect = 60 stubs. mvn test count increased from X to Y. Starting L3 (HestonProcess QE)." Wait for acknowledgment.

---

## Layer 3 — WI-3 HestonProcess QuadraticExponential branch (2–3 commits)

**Important discovery:** v1.42.1 C++ `HestonProcess::Discretization` has **9 values** (`PartialTruncation`, `FullTruncation`, `Reflection`, `NonCentralChiSquareVariance`, `QuadraticExponential`, `QuadraticExponentialMartingale`, `BroadieKayaExactSchemeLobatto`, `BroadieKayaExactSchemeLaguerre`, `BroadieKayaExactSchemeTrapezoidal`). Current Java has 4 (`PartialTruncation`, `FullTruncation`, `Reflection`, `ExactVariance`). The Java `ExactVariance` entry is a stale ~2007-vintage name with no v1.42.1 counterpart; the partially-implemented body at lines 260–288 appears to be an old Broadie-Kaya variant.

**Scope decision for this plan:** align the Java enum by **renaming `ExactVariance` → `QuadraticExponential` and porting the QE algorithm**. Five additional v1.42.1 discretizations (`NonCentralChiSquareVariance`, `QuadraticExponentialMartingale`, three `BroadieKaya*`) are **NOT** in 2a scope (design §2.2 "no broadening beyond the 56 `numerical_suspect` markers"); they remain as a Phase 2b backlog item — add a note to `phase2a-completion.md` carry-over section at the end of L6.

If implementation reveals a call site in the existing Java that references one of the missing discretizations, that is an A4 trigger and must be surfaced to the user before proceeding.

### Task 3.1: Rename enum value and port QE branch

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/processes/HestonProcess.java`
- Create: `migration-harness/cpp/probes/processes/hestonprocess_qe_probe.cpp`
- Modify (if needed): `jquantlib/src/test/java/org/jquantlib/testsuite/processes/` tests

- [ ] **Step 1:** Check for call sites referencing `HestonProcess.Discretization.ExactVariance`.

```bash
grep -rn 'Discretization\.ExactVariance\|HestonProcess.Discretization' jquantlib/src --include='*.java' | grep -v HestonProcess.java
```

If any real call site exists, examine whether it is correct given v1.42.1's enum and either update it to `QuadraticExponential` or flag to the user. Expected case: no call sites (the stubbed branch isn't used anywhere).

- [ ] **Step 2:** Write the C++ probe.

```cpp
// migration-harness/cpp/probes/processes/hestonprocess_qe_probe.cpp
// Reference values for HestonProcess QuadraticExponential (QE) evolve().
// Three parameter regimes at 20 time steps each, with fixed dw values so
// any drift in the branch accumulates visibly.

#include <ql/version.hpp>
#include <ql/processes/hestonprocess.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include "../../common.hpp"

// ... [build three HestonProcess instances at representative regimes:
//      regime A: high vol-of-vol (sigma=0.8), low correlation (rho=-0.1)
//      regime B: low vol-of-vol (sigma=0.2), high correlation (rho=-0.9)
//      regime C: balanced (sigma=0.4, rho=-0.5)
//    For each: 20 evolve() steps with dt=0.01 and hand-picked dw values,
//    capture every (t, x0, x1) tuple.  Tolerance: tight 1e-12.]
```

Fill out the probe completely — no TBDs. See `spherecylinder_probe.cpp` and `leastsquare_probe.cpp` in the same harness directory for the reference style.

- [ ] **Step 3:** Build probe, capture reference.

```bash
./migration-harness/verify-harness.sh 2>&1 | tail -10
```

- [ ] **Step 4:** Rename the Java enum value. In `HestonProcess.java` line 46–48, change `ExactVariance` to `QuadraticExponential`. Update all internal references (the `case ExactVariance:` in `evolve()` becomes `case QuadraticExponential:`).

- [ ] **Step 5:** Delete the partially-implemented `case ExactVariance:` body (lines 260–288) — it mixes old Broadie-Kaya variance-sampling code with a `throw new UnsupportedOperationException` gate, and is not the QE algorithm.

- [ ] **Step 6:** Write the failing Java test referencing the new probe. Separate package: `jquantlib/src/test/java/org/jquantlib/testsuite/processes/HestonProcessQETest.java` if not already present (follow Phase 1 test-file conventions for the `processes` package).

- [ ] **Step 7:** Port the QE branch from C++ v1.42.1 `ql/processes/hestonprocess.cpp` line-for-line into the Java `case QuadraticExponential:` branch. The QE algorithm is Andersen (2008) — it uses a switching variance approximation driven by the ratio `psi = variance / mean²` with a critical threshold at ~1.5. Preserve constants and branch structure exactly.

- [ ] **Step 8:** Run the new test and the full suite.

```bash
(cd jquantlib && mvn test -Dtest=HestonProcessQETest) 2>&1 | tail -10
(cd jquantlib && mvn test) 2>&1 | tail -5
```

Expected: both green. If `HestonProcessQETest` fails at tight `1e-12`, fall back to loose `1e-8` is **not automatic** — it requires an inline justification comment in the test citing phase1-design §4.2 (design §3.3 and R4 in risk log).

- [ ] **Step 9:** Re-run scanner; WIP count 4 → 3.

```bash
python3 tools/stub-scanner/scan_stubs.py
grep HestonProcess.*UnsupportedOperation docs/migration/stub-inventory.json
```

Expected: no `UnsupportedOperationException`-type HestonProcess entries.

- [ ] **Step 10:** Commit.

```bash
git add jquantlib/src/main/java/org/jquantlib/processes/HestonProcess.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/processes/HestonProcessQETest.java \
        migration-harness/cpp/probes/processes/hestonprocess_qe_probe.cpp \
        migration-harness/references/processes/hestonprocess_qe/
git commit -s -m "$(cat <<'EOF'
stub(processes): port HestonProcess QuadraticExponential branch

Renames stale ExactVariance enum value to v1.42.1's QuadraticExponential
and ports the Andersen (2008) QE algorithm. Probe captures 20-step
evolve() traces at three parameter regimes (high/low vol-of-vol,
varying correlation). Tight 1e-12 tolerance.

Five additional v1.42.1 discretizations (NonCentralChiSquareVariance,
QuadraticExponentialMartingale, three BroadieKaya* variants) deferred
to Phase 2b — out of 2a scope per design §2.2. WIP: 4 -> 3.
EOF
)"
git push origin main
```

### Task 3.2: Layer checkpoint

- [ ] **Step 1:** Post summary: "L3 done — HestonProcess QE branch live, Java enum aligned (one value; five others carry to 2b). Scanner: 3 WIP + 56 suspect. Starting L4 (56-marker audit)."

---

## Layer 4 — WI-4 Audit 56 numerical_suspect markers (many commits)

This layer applies the **tiered triage methodology** from design §4 to every `TODO: code review :: please verify against QL/C++ code` marker in `main`. The outcome distribution is unknown a priori; the work is bounded by scanner output, not by a predetermined task list.

**Methodology recap (design §4):**

- **Tier-1:** nine-point visual-diff checklist. Clean only if every point passes with zero doubt.
- **Tier-2:** triggered by any Tier-1 doubt. Full probe + cross-validated test.
- **Outcome per marker:** clean (remove TODO), aligned (`align(<pkg>)` commit with probe+test), or carved (only if filling trips A4; no "too complex" carves).
- **Hard rule:** wherever a divergence is found, full probe+test treatment. No exceptions.
- **Pause trigger A7:** if Tier-2 divergences exceed 20 out of 56, pause for scope reassessment.

### Task 4.0: Setup — generate audit worklist and log skeleton

**Files:**
- Create: `docs/migration/phase2a-audit.md`

- [ ] **Step 1:** Extract the 56 markers, grouped by package, into a checkable audit log.

```bash
python3 -c "
import json
from collections import defaultdict
stubs = json.load(open('docs/migration/stub-inventory.json'))
suspect = [s for s in stubs if s['kind'] == 'numerical_suspect']
suspect.sort(key=lambda s: (s['file'], s['line']))
by_pkg = defaultdict(list)
for s in suspect:
    pkg = s['id'].rsplit('.', 1)[0]
    by_pkg[pkg].append(s)
print('# Phase 2a Audit Log — numerical_suspect markers')
print()
print('Generated from stub-inventory.json at L4 start.')
print(f'Total markers: {len(suspect)}.')
print()
print('Status legend: T1 = Tier-1 visual diff; T2 = Tier-2 full probe+test.')
print('Outcomes: clean (TODO removed) · aligned (Java fixed) · carved (see phase2a-carveouts.md).')
print()
for pkg in sorted(by_pkg):
    ms = by_pkg[pkg]
    print(f'## {pkg} ({len(ms)})')
    print()
    for s in ms:
        print(f\"- [ ] \`{s['id']}\` @ \`{s['file']}:{s['line']}\` — TBD\")
    print()
" > docs/migration/phase2a-audit.md
```

- [ ] **Step 2:** Commit the skeleton (no production change; a scaffolding commit is acceptable here because it enables the per-marker commits below to reference concrete line numbers).

```bash
git add docs/migration/phase2a-audit.md
git commit -s -m "docs(migration): add Phase 2a audit log skeleton

Enumerates the 56 numerical_suspect markers by package. Each line is
updated in-place as markers are triaged. See phase2a-design §4 for
methodology; §4.4 for log schema."
git push origin main
```

### Task 4.1: Per-package triage loop

This task repeats per package. A package's markers are either:

- **All Tier-1 clean:** produce a single `align(<pkg>): drop N stale numerical-suspect markers after T1 review` commit that removes the `TODO:` comments from the Java source, updates the audit log entries to `T1 · clean`, and runs `mvn test`.
- **One or more Tier-2 divergences:** each Tier-2 case gets its own commit following the Tier-2 procedure.

**Per-package procedure:**

- [ ] **Step 1:** Read the Java method beside its v1.42.1 C++ counterpart. Apply the nine-point Tier-1 checklist from design §4.1:

  1. Constants: magic numbers identical?
  2. Loop bounds: `<` vs `<=`, endpoints, direction, starting index identical?
  3. Branch structure: same if/else, early-returns, guards in same order?
  4. Arithmetic expression shape: same parenthesisation and associativity?
  5. Helper calls: same sub-function / same algorithm? No silent `std::` substitution?
  6. Pass-by-reference semantics: C++ `Type&` out-params mirrored as Java `Type[]`/`int[]`?
  7. Preconditions: every `QL_REQUIRE` ported verbatim with the same condition direction?
  8. Default arguments: C++ defaults represented in Java overloads with identical defaults?
  9. Algorithm identity: citations match (e.g., "NR §10.3")?

- [ ] **Step 2:** If all nine points pass with zero doubt for every marker in the package, remove the `TODO: code review :: please verify against QL/C++ code` lines, update audit-log entries to `T1 · clean — TODO removed in <commit>`, commit:

```bash
git commit -s -m "align(<pkg>): drop N stale numerical-suspect markers after T1 review

Each marker reviewed against v1.42.1 counterpart using the 9-point
tier-1 checklist (phase2a-design §4.1). All pass with zero doubt;
TODO comments removed. No production code change.

Markers: <list of stub IDs>."
```

- [ ] **Step 3:** If any marker raises doubt, **do not batch the whole package**. Per design §4.3:

  1. Write a focused C++ probe scoped to the suspect branch/expression.
  2. Capture reference JSON via `verify-harness.sh`.
  3. Write a Java test at the appropriate tolerance tier.
  4. Either:
     - Test passes → marker cleared. Commit: `align(<pkg>): drop stale numerical-suspect marker on <method> — probe confirms match`.
     - Test fails → fix Java to match C++. Commit: `align(<pkg>): match v1.42.1 <method>` with probe + test in the same commit.
  5. Update the audit-log entry accordingly.

- [ ] **Step 4:** Carving. A marker is carved **only if** aligning it requires a new class outside the 61 packages (A4 trigger). If carved: add an entry to `docs/migration/phase2a-carveouts.md` (create the file on first carve) with:
  - stub-id
  - reason (what new class/package is needed)
  - expected Phase 2b disposition
  Update audit log to `T2 · carved — see phase2a-carveouts.md#<anchor>`.

- [ ] **Step 5:** Checkpoint every ~10 markers. Re-run scanner and count remaining `numerical_suspect`. If Tier-2 divergences are running ≥ 20 across the markers audited so far, **trigger pause A7**: post a summary to the user with drift-rate statistics and wait for guidance before continuing.

### Task 4.2: Package-level order

The 56 markers group (from Task 4.0 output, approximate counts per package):

- `processes` (~5, including 4 in `HestonProcess` already being seen during L3)
- `cashflow` (~4)
- `termstructures.yieldcurves` (~3)
- `model.shortrate.*` (~4)
- `math.matrixutilities` (~2)
- `math.*` (~3)
- `instruments`, `pricingengines`, `termstructures.*`, `time` etc. (~35 scattered markers)

Process packages in roughly-ascending-dependency order so any alignments flow downstream: `math.*` → `time.*` → `termstructures.*` → `cashflow`/`instruments` → `pricingengines.*` → `model.*` → `processes.*`. This ordering is a suggestion, not a hard requirement — the critical property is that any Tier-2 alignment is landed before packages that might import the aligned code.

### Task 4.3: WI-4 stopping check

- [ ] **Step 1:** Re-run scanner.

```bash
python3 tools/stub-scanner/scan_stubs.py
```

Expected tail: `numerical_suspect: 0`.

- [ ] **Step 2:** Verify audit log completeness.

```bash
grep -c '^- \[ \]' docs/migration/phase2a-audit.md
```

Expected: `0` (all entries checked off).

- [ ] **Step 3:** Layer checkpoint. Summary to user: "L4 done — N markers audited: X T1-clean, Y T2-aligned, Z carved. Scanner: 3 WIP, 0 NI, 0 suspect. Starting L5 (QL cleanup)."

---

## Layer 5 — WI-5 `QL.validateExperimentalMode` deletion sweep (1 commit)

Delete the method and its ~70 remaining call sites. Single commit per design §3.5.

### Task 5.1: Enumerate call sites

- [ ] **Step 1:** Refresh call-site list.

```bash
grep -rn '\bvalidateExperimentalMode\b' jquantlib/src/main/java | tee /tmp/phase2a-ql-callsites.txt | wc -l
```

Note the count. At L5 start (per a snapshot taken during plan authoring) the count was 77 lines across ~30 files; the exact number may have shifted because WI-2 Task 2.7 removed two call sites.

- [ ] **Step 2:** Inspect a few representative call sites (e.g., `Money.java`, `ForwardRateStructure.java`) to confirm the pattern is uniform: each call is a single line `QL.validateExperimentalMode();` near the top of a constructor or method body. One known outlier is `RelativeDateRateHelper.java:92` which is commented-out (`//        QL.validateExperimentalMode();`); handle by full-line deletion.

### Task 5.2: Bulk remove call sites

- [ ] **Step 1:** Write a Python sweep script. Keep it narrowly-targeted: match only the exact `QL.validateExperimentalMode();` statement (with or without leading whitespace and with or without a `//` comment prefix) as a full line; preserve line endings exactly. Do not use regex that could catch partial matches.

```bash
cat > /tmp/phase2a-ql-sweep.py <<'EOF'
#!/usr/bin/env python3
"""Remove all full-line occurrences of `QL.validateExperimentalMode();`
(and the commented form `// QL.validateExperimentalMode();`) from the
Java main-source tree. Preserves line endings. Narrow match on purpose."""
import re
from pathlib import Path

ROOT = Path('jquantlib/src/main/java')
PAT = re.compile(rb'^[ \t]*(//\s*)?QL\.validateExperimentalMode\(\)\s*;[ \t]*(\r\n|\r|\n)', re.MULTILINE)

changed = 0
for p in ROOT.rglob('*.java'):
    b = p.read_bytes()
    nb = PAT.sub(b'', b)
    if nb != b:
        p.write_bytes(nb)
        changed += 1
print(f'modified {changed} files')
EOF
python3 /tmp/phase2a-ql-sweep.py
```

Expected: `modified ~30 files`.

- [ ] **Step 2:** Verify no call sites remain.

```bash
grep -rn '\bvalidateExperimentalMode\b' jquantlib/src/main/java
```

Expected output: **only** the method definition itself in `QL.java:389`. No other lines.

### Task 5.3: Delete the method

- [ ] **Step 1:** In `jquantlib/src/main/java/org/jquantlib/QL.java`, delete lines 385–394 (the Javadoc comment block and the `validateExperimentalMode()` method).

- [ ] **Step 2:** Verify nothing imports or references the method.

```bash
grep -rn '\bvalidateExperimentalMode\b' jquantlib/src
```

Expected: empty output.

### Task 5.4: Run tests

- [ ] **Step 1:** Full test suite.

```bash
(cd jquantlib && mvn test) 2>&1 | tail -10
```

Expected: BUILD SUCCESS. If any test fails, the removal unmasked a real test dependency on the gate — investigate before committing (likely indicates a test was silently relying on an `EXPERIMENTAL`-gate-thrown-exception path, which is a pre-existing bug).

### Task 5.5: Re-run scanner and confirm WIP count

- [ ] **Step 1:**

```bash
python3 tools/stub-scanner/scan_stubs.py
```

Expected tail:
```
  numerical_suspect: 0
  work_in_progress: 2
```

(CapHelper and G2 are the only remaining WIP entries.)

### Task 5.6: Commit

- [ ] **Step 1:**

```bash
git add jquantlib/src/main/java
git commit -s -m "$(cat <<'EOF'
chore(ql): delete validateExperimentalMode and all call sites

QL.validateExperimentalMode is a JQuantLib-ism with no C++ counterpart;
Phase 1 removed call sites opportunistically during stub fills, WI-5
of Phase 2a finishes the job. Method deleted from org.jquantlib.QL;
all remaining call sites (~70 lines across ~30 files) removed via
narrow regex sweep that matches only full-line QL.validateExperimentalMode()
statements with preserved line endings.

WIP: 3 -> 2 (CapHelper and G2 remain; both deferred to Phase 2b).
EOF
)"
git push origin main
```

### Task 5.7: Layer checkpoint

- [ ] **Step 1:** Summary to user: "L5 done — QL.validateExperimentalMode gone, all call sites purged. Scanner: 2 WIP (CapHelper, G2), 0 NI, 0 suspect. Starting L6 (completion doc + tag)."

---

## Layer 6 — Completion doc + tag (1 commit + tag)

### Task 6.1: Verify all Phase 2a done criteria

Walk through `phase2a-design.md §6.1` item by item:

- [ ] Scanner: 2 WIP, 0 NI, 0 suspect — verify.
- [ ] `docs/migration/stub-allowlist.json` with TreeLattice2D entry — verify.
- [ ] `docs/migration/phase2a-audit.md` complete — verify no unchecked boxes.
- [ ] `grep -r validateExperimentalMode jquantlib/src/main/java` empty — verify.
- [ ] All formerly-LM-skipped tests un-skipped and green — verify (check the three files for any remaining `@Ignore` that should be gone).
- [ ] `(cd jquantlib && mvn test)` fully green with no 2a-attributable skips — verify.
- [ ] `phase2a-completion.md` — next task.
- [ ] Tag — next task.

If any fails: fix the gap as a final stub commit before writing the completion doc.

### Task 6.2: Write the completion report

**Files:**
- Create: `docs/migration/phase2a-completion.md`

- [ ] **Step 1:** Draft following the `phase1-completion.md` template. Include:

```markdown
# Phase 2a Completion Report

**Completed:** <DATE>
**Branch:** `main` (no migration branch — per design P2A-8)
**Commit range:** `<first>..<last>` (N commits on main since Phase 1 completion tag)
**Phase 2a design:** `docs/migration/phase2a-design.md`
**Phase 2a plan:** `docs/migration/phase2a-plan.md`
**Predecessor tag:** `jquantlib-phase1-complete` (`54e89b0`)

---

## Summary

Phase 2a finished the five tail items of Phase 1 within the 61 existing `org.jquantlib.*` packages. Starting from 63 stubs (6 WIP + 1 NI + 56 suspect) at tip of Phase 1, all actionable items are resolved:

- WI-1: TreeLattice2D allowlist — <commit>
- WI-2: MINPACK/LM port — <N> commits (<first-sha>..<last-sha>)
- WI-3: HestonProcess QE branch — <commits>
- WI-4: 56 numerical_suspect audit — <X> T1-clean + <Y> T2-aligned + <Z> carved
- WI-5: QL.validateExperimentalMode deletion — <commit>

End state: 2 WIP (CapHelper, G2), 0 NI, 0 suspect.

## Numeric outcome

| Metric | Start | End | Delta |
|---|---|---|---|
| work_in_progress | 6 | 2 | -4 |
| not_implemented | 1 | 0 | -1 |
| numerical_suspect | 56 | 0 | -56 |
| mvn test count | <start> | <end> | +<delta> |
| commits on main | - | N | - |

## Probes added

- `minpack_qrfac_probe` (exact, 2 cases)
- `minpack_qrsolv_probe` (exact, 2 cases)
- `levenbergmarquardt_probe` (4 cases: linear, quadratic, Rosenbrock, maxfev early-stop)
- `hestonprocess_qe_probe` (tight 1e-12, 3 regimes × 20 steps)
- <any Tier-2 probes added during WI-4, list them>

## Carry-over to Phase 2b

- `CapHelper` (line 84) — needs `IborLeg` infrastructure.
- `G2` two-factor short-rate model (line 126) — needs functional `TreeLattice2D` + two-factor calibration.
- HestonProcess discretizations missing: `NonCentralChiSquareVariance`, `QuadraticExponentialMartingale`, `BroadieKayaExactSchemeLobatto`, `BroadieKayaExactSchemeLaguerre`, `BroadieKayaExactSchemeTrapezoidal` — five discretizations present in v1.42.1 but not in Java. Out of 2a scope; no call sites in JQuantLib depend on them (verified in L3 Task 3.1).
- <any Tier-2 carves from WI-4>

Open question for 2b brainstorming: stay inside 61 packages or broaden?

## Pause triggers hit

<list or "none" — A7 specifically if it fired during WI-4>

## Notes / lessons learned

<short notes on anything surprising, anything the next phase should know>
```

- [ ] **Step 2:** Fill in every `<placeholder>` with actual values from commit log, scanner output, and audit log.

### Task 6.3: Commit completion doc

- [ ] **Step 1:**

```bash
git add docs/migration/phase2a-completion.md
git commit -s -m "docs(migration): Phase 2a completion report"
git push origin main
```

### Task 6.4: Create and push tag

- [ ] **Step 1:** Tag the completion commit.

```bash
git tag -a jquantlib-phase2a-complete -m "Phase 2a complete: MINPACK + LM, HestonProcess QE, 56-marker audit, QL cleanup"
git push origin jquantlib-phase2a-complete
```

- [ ] **Step 2:** Verify.

```bash
git describe --tags --abbrev=0
git ls-remote --tags origin | grep phase2a
```

Expected: `jquantlib-phase2a-complete` on both local and origin.

### Task 6.5: Final checkpoint

- [ ] **Step 1:** Summary to user: "Phase 2a complete. Tag `jquantlib-phase2a-complete` pushed. Scanner: 2 WIP (CapHelper, G2 — deferred to 2b), 0 NI, 0 suspect. mvn test: N tests green. Ready for Phase 2b brainstorming."

---

## Appendix A — Commit-message reference

Commit kinds used in Phase 2a (inherited from Phase 1 §6):

| Kind | Use |
|---|---|
| `stub(<pkg>): …` | Fill a stub — first commit to implement a previously-empty method. Both constructor gates in LM and the QE branch are `stub(…)` commits. |
| `align(<pkg>): …` | Java implementation adjusted to match v1.42.1; also used for un-skipping tests and removing stale numerical_suspect markers. |
| `infra(<area>): …` | Tooling or harness change; used for WI-1. |
| `chore(<pkg>): …` | Pure housekeeping with no behavior change; WI-5 (QL deletion). |
| `docs(migration): …` | Plan/design/completion documents. |
| `test(<pkg>): …` | Test-only additions or fixes (rarely standalone; usually paired with `stub` or `align`). |

Bodies are 1–3 paragraphs. End with `-s Signed-off-by`. No `Co-authored-by` trailer.

## Appendix B — Pause trigger quick reference (design §6.3 + §6.4)

- **A1:** Scanner stub count > 1000 after a regeneration. (Not expected in 2a.)
- **A2:** Tolerance looser than `1e-8` needed for a test. Pause + inline justification.
- **A3:** Cross-validation suggests v1.42.1 itself is wrong.
- **A4:** Stub strictly needs a new class outside the 61 existing packages.
- **A6:** End of every layer — post summary, wait for user acknowledgment.
- **A7 (new, 2a-only):** WI-4 Tier-2 divergences ≥ 20 of 56. Pause for scope reassessment.

A5 is deliberately unused (API changes to match v1.42.1 are automatic per design §7.3).

---

**End of plan.**
