# JDK 25 modernization — Design

**Date:** 2026-05-22
**Predecessor:** Phase 1 genuine closure on JDK 25 (`jquantlib-phase1-complete-jdk25` @ `0e72b77a`)
**User mandate:** "All waves 1-6 (full modernization including perf)"
**Scope:** 1775 production Java files + ~580 test files

---

## Goal

Exploit every JDK 25 LTS feature that improves correctness, clarity, or
performance of JQuantLib **without breaking the suite's 3270/0/0/0/24
green baseline**.

---

## Codebase survey (verified 2026-05-22)

| Pattern | Count | Wave |
|---|---|---|
| Production DTO-like static classes (Data/Datum/Result/Case) | 13 | 3 |
| Test DTO-like static classes | 10 | 3 |
| `instanceof` sites | 216 | 2 |
| `switch` statements | 170 | 2 |
| `public enum` + `public abstract class` | 313 | 4 |
| `implements Cloneable` (record-incompatible) | 50+ | excluded |
| Multi-line `String` concat (text-block candidates) | 0 | 1 (no-op) |

---

## Six-wave plan

### Wave 1 — Safe cosmetic (~1-2 sessions)
- `var` for local variables where type is obvious from initializer
- `List.of` / `Map.of` / `Set.of` for immutable literal collections
- Diamond operator gaps (`new HashMap<String, Integer>()` → `new HashMap<>()`)
- Skip text blocks (no candidates surveyed)
- Skip String templates (preview in JDK 25, not stable)

**Risk:** zero (pure syntax sugar). Verification: per-package targeted mvn.

### Wave 2 — Pattern matching + switch expressions (~2-3 sessions)
- 216 `instanceof` chains → pattern matching (`if (x instanceof Foo f)`)
- 170 `switch` statements → switch expressions where applicable (no fallthrough, single-result)
- Switch with patterns (JEP 441) for type-dispatch sites in pricing engines / payoff visitors

**Risk:** low-medium. Patterns reshape control flow — must verify identical behavior.
Verification: per-package targeted mvn + spot checks of dispatch sites.

### Wave 3 — Records for DTOs (~1-2 sessions)
- Convert 23 Cloneable-free DTO static classes to records
- Examples: `BarrierOptionTest.HaugDouble`, `EquityIndexTest.CommonVars`, etc.
- Skip all Cloneable types (Currency, Money, Leg, Matrix, Array, Date, Period, etc.)
- Skip all observer/observable types
- Records auto-generate correct equals/hashCode/toString — replaces 1000s of LOC of boilerplate

**Risk:** low. Records use canonical accessors (`.field()` not `.getField()`) — call sites need updating.
Verification: per-test mvn after each record conversion.

### Wave 4 — Sealed type hierarchies (~2-3 sessions)
- Make abstract class hierarchies explicitly sealed where the subtype list is fixed:
  - `Payoff` and subclasses (PlainVanillaPayoff, CashOrNothingPayoff, etc.)
  - `Exercise` (AmericanExercise, EuropeanExercise, BermudanExercise)
  - `Compounding` (already enum)
  - `Option.Type` (already enum)
  - `Frequency` (already enum)
- Skip hierarchies that legitimately allow user extension (pricing engines, stochastic processes)

**Risk:** medium-high. Sealed hierarchies are exhaustive — any external subtype breaks.
Verification: full `mvn test -pl ../jquantlib clean test` per sealed family added.

### Wave 5 — Virtual threads + structured concurrency (~2-3 sessions)

**Survey 2026-05-22 — material correction:** JQuantLib has **0 existing
Executor / ForkJoin / parallelStream usage** in production code. There is
nothing to "replace" with virtual threads — W5 would be **adding** parallelism
to currently-sequential code, not migrating existing concurrency.

Revised scope:
- Introduce virtual threads to MC simulators (~36 MC* files identified)
- Use `StructuredTaskScope` (JEP 480 in JDK 25) for the new fork-join pattern
- Targets: `MonteCarloModel.calculate()` main loop — parallel path generation
- Deterministic seed splitting via SplitMix64 so MC results stay byte-identical
  across runs regardless of execution order

**Risk:** medium-high. MC convergence + cached test values depend on the
exact sample sequence. Reproducibility requires careful seed splitting
(each virtual thread gets a deterministic sub-seed). If implemented
correctly, results are identical to sequential. If not, the entire MC
test family (~50+ tests) needs re-baselining.

**Recommendation:** Treat W5 as a separate performance project after W1-4
land. Net-new feature, not a migration.

### Wave 6 — Vector API (incubator) + FFM API (~3-5 sessions)

**Survey 2026-05-22:** 219 files use `Math.exp`, 278 use `Math.sqrt`, 68
use `JQuantMath.*` (correctly-rounded). Phase 2n's Math.* → JQuantMath.*
migration covered transcendentals but did not exhaustively sweep all
call sites — many `Math.exp/log/sqrt` remain in batch contexts (Array
arithmetic, MC step loops).

Revised W6 scope:
- Vector API: vectorize `Array.assign(Array)` arithmetic kernels first
  (element-wise add/mul/sub over large doubles arrays — biggest win,
  no transcendental complexity)
- Then MC step loops once W5 introduces parallel paths
- Skip JQuantMath vectorization — correctly-rounded transcendentals
  with Vector API need careful design (NEON doesn't have correctly-rounded
  intrinsics; would have to fall back to scalar fast paths)
- FFM API: JQuantLib is pure-Java; no native interop. SKIP.

**Risk:** highest. Vector API is **incubator on JDK 25** (stable target
JDK 26+). Performance work may not be worth the API churn risk.

**Recommendation:** Reassess after Waves 1-5 complete. May defer to JDK 26
when Vector API exits incubator.

---

## Execution sequencing

Waves 1, 2, 3 are mostly orthogonal — can run in parallel after Wave 1.
Wave 4 must follow Wave 2 (sealed types enable exhaustive switch patterns).
Waves 5, 6 are independent of 1-4.

```
Wave 1 (cosmetic, inline)
   ↓
Waves 2, 3, 4 (parallel agents, sequential within each)
   ↓
Wave 5 (virtual threads)
   ↓
Wave 6 (Vector API — reassess)
```

---

## Memory caveat (from prior session)

Yesterday's full-suite mvn runs OOM'd with parallel JVMs at -Xmx4g.
Current pom sets -Xmx6g + 1g metaspace. Modernization waves DO NOT need
parallel agent dispatch — sequential per-wave verification is fine.
Targeted `-Dtest=<class>` for incremental sanity checks during refactors.

---

## Definition of done

1. All 6 waves landed.
2. Full suite green: 3270/0/0/0/24 baseline maintained (no regression).
3. Per-wave tag at completion (`jquantlib-jdk25-mod-w<N>-complete`).
4. Final tag: `jquantlib-jdk25-modernization-complete`.
5. README + memory updated.

---

## What this DOES NOT include

- JPMS modularization (`module-info.java`) — separate effort
- API redesign / breaking changes
- Reactive/non-blocking I/O adoption — JQuantLib is compute-bound, not I/O-bound
- Migration from JUnit 4 → JUnit 5 — separate effort
