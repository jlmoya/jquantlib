# Phase 3f Implementation Plan

> Single worktree A. Investigation-first architectural fix.

**Goal:** Close 3 Markit-reconciliation tests via Interpolation copy-vs-reference fix. Tag `jquantlib-phase3f-complete`.

## L0 — Pre-flight + worktree setup

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-3f-A /Users/josemoya/eclipse-workspace/jquantlib-3f-A main
cd /Users/josemoya/eclipse-workspace/jquantlib-3f-A
git submodule update --init --recursive
```

## A.1 — Investigation + minimum-surface fix

**Investigation tasks:**
1. Read `org.jquantlib.math.matrixutilities.Array.java:126-142` — current Array(double[]) constructor
2. Read `org.jquantlib.math.interpolations.AbstractInterpolation.java:293-332` — LogInterpolationImpl with vy/logY_ decoupled from source
3. Read `org.jquantlib.termstructures.BootstrapError.java:71-77` — op() callback
4. Grep for `new Array(double[])` callers — assess copy-semantics dependency
5. Decide: (a) Array reference semantics, (b) rebuild interpolation per call, (c) setData/refresh hook

**Land minimum-surface fix targeting <200 LOC.** Document choice + cascade impact in commit message.

**Verify focused:** `mvn test -Dtest=ArrayTest,InterpolationTest,IterativeBootstrapTest` (existing tests).

**Verify broad:** full `mvn test` ~60s; no regressions.

**Commit:** `align(math.{matrixutilities,interpolations}): Interpolation copy-vs-reference fix per choice [a/b/c] (Phase 3f A.1)`

## A.2 — Un-ignore + verify 3 Markit tests

**File:** `jquantlib/src/test/java/org/jquantlib/testsuite/instruments/CreditDefaultSwapTest.java`

For each test (testIsdaEngine, testIsdaCalculatorReconcileSingleQuote, testIsdaCalculatorReconcileSingleWithIssueDateInThePast):
1. Remove @Ignore
2. If BOOST_CHECK_CLOSE percent semantics need assertion adjustment, fix
3. Run focused test
4. Verify pass at C++ tolerance

If any test still fails: refine @Ignore rationale (no silent skip). Document remaining gap.

**Commit:** `align(testsuite.instruments): un-ignore 3 CreditDefaultSwapTest Markit reconciliation tests post-Phase-3f fix (Phase 3f A.2)`

## L2 — Completion + tag + memory + README + teardown

Standard milestone-doc discipline.
