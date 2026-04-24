# Phase 1 Carve-outs

Stubs explicitly excluded from Phase 1 with user sign-off. See design §7.5
for acceptable carve-out conditions.

Each entry: stub id, reason, date, user sign-off (commit sha or message).

---

## 2026-04-24 — org.jquantlib.QL#UnsupportedOperationException (QL.java:391)

**Method:** `QL.validateExperimentalMode()`
**Stub kind:** `work_in_progress` (scanner false positive)

**Reason for carve-out:** This method's entire body is the EXPERIMENTAL-mode
gate — `throw new UnsupportedOperationException("Work in progress")` unless
the system property `EXPERIMENTAL` is set. It has no C++ counterpart; it's
pure JQuantLib-era plumbing used by the (now-removed) gate pattern throughout
the existing Java code. As Phase 1 removes those gates one-by-one, this
method becomes dead code. Slated for deletion once the final gate is gone.

**Fits design §7.5 condition:** "Stubs in transitively unreachable (dead)
Java code" (will be once all callers are de-gated). Also has no C++
counterpart — no ground-truth to migrate to.

**User sign-off:** acknowledged during Phase 3 execution (see conversation
log); will be removed in a final sweep commit once the scanner confirms no
remaining callers.

---

## 2026-04-24 — methods.lattices.TreeLattice2D.grid (line 73)

**Stub kind:** `not_implemented`.
**Method body:** `throw new LibraryException("not implemented")`.

**Reason for carve-out:** Already matches C++ v1.42.1. The C++ source
(`ql/methods/lattices/lattice2d.hpp:52`) literally has
`Array grid(Time) const override { QL_FAIL("not implemented"); }` with a
`// smelly` maintainer comment. Java already throws an equivalent unchecked
exception (`LibraryException` is a `RuntimeException` subclass). This is a
scanner pattern-match false-positive: the phrase "not implemented" matches
the regex but the behavior IS the intended v1.42.1 behavior.

**Fits design §7.5 condition:** Not a carve-out in the usual sense — the
Java code already matches ground truth. Logged here so future scanner runs
don't re-surface it for resolution.

**User sign-off:** acknowledged during Phase 3 execution.

---

## 2026-04-24 — math.optimization.LevenbergMarquardt#UnsupportedOperationException (lines 43, 51)

**Stubs:** `math.optimization.LevenbergMarquardt#<init>` (2 constructor
overloads, both gated) and `LevenbergMarquardt.minimize` (body is a
commented-out C++ block — effectively a stub even though the scanner saw
only the gated constructors).

**Reason for carve-out:** Faithful port requires translating QuantLib C++
v1.42.1's MINPACK `lmdif` implementation (`ql/math/optimization/lmdif.cpp`,
1,672 lines of dense non-linear-least-squares numerics) plus the thin LM
wrapper (`levenbergmarquardt.cpp`, 193 lines). Total ~1,900 lines of port
work across 4 files — an order of magnitude larger than any other Phase 1
stub. This is a self-contained sub-project, not a per-stub commit.

**Downstream dependencies currently safe:** the only current Java callers
are themselves stubs (SABRInterpolation tests @Ignored; other optimizer
tests skipped). Leaving LevenbergMarquardt gated breaks nothing that
currently runs.

**Fits design §7.5 condition:** "Stubs depending on a Phase-2-only class"
— Java needs a new `minpack`/`lmdif` sub-package that doesn't exist yet.
Introducing it inside a single-stub commit violates the per-commit
discipline from design §4.

**Alternative considered and rejected:** use `commons-math3`
`LevenbergMarquardtOptimizer` — rejected because v1.42.1's MINPACK port is
bit-for-bit what C++ users get, and mixing in an external Java numerics
library creates a divergence that cross-validation can't catch cleanly.

**Recommended Phase 2 approach:** port MINPACK `lmdif` as its own
multi-commit sub-task in `org.jquantlib.math.optimization.minpack`
package, with extensive cross-validation (lmdif has many subroutines each
worth its own probe). Or, if time-constrained, adopt commons-math3's
implementation with a documented loose-tolerance adapter layer.

**User sign-off:** acknowledged during Phase 3 execution; deferred to
Phase 2 with explicit note about the MINPACK scope.

---
