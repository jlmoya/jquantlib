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
- Replace `Executors.newFixedThreadPool(...)` in MC simulators with `Executors.newVirtualThreadPerTaskExecutor()`
- Adopt `StructuredTaskScope` (JEP 462 → 480 in 25) for new fork-join sites
- Targets: `MonteCarloModel`, `MCEuropeanEngine`, `MCAmerican*`, `MCHeston*`, `MCBates*`, `LiborMarketModelTest.testCalibration`

**Risk:** medium. Virtual threads have different scheduling — MC convergence is sample-order
dependent in some tests. Need careful regression on MC-heavy tests.

### Wave 6 — Vector API (incubator) + FFM API (~3-5 sessions)
- Vector API: batch math in JQuantMath (already correctly-rounded — may not win much)
  AND in MC path generation (high-volume independent SIMD ops)
- FFM API: any native interop? JQuantLib is pure-Java per project docs, so likely N/A
- Vectorize: `AdaptiveRungeKutta` array ops, `Array.assign(Array)` arithmetic, MC step loops

**Risk:** highest. Vector API is incubator on JDK 25 (stable target is JDK 26+). Performance
work may not be worth the API churn risk. Reassess after Waves 1-5 complete.

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
