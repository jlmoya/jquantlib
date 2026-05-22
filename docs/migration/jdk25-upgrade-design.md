# JDK 25 Upgrade — Design

**Date:** 2026-05-21
**Predecessor:** Phase 1.3 closure (in flight)
**Successor:** Phase 2 forward closure (deferred until after upgrade)
**User mandate:** "upgrade the solution to JDK 25 and refactor the code to exploit all the benefits"
**Scope chosen:** Pure JDK 25 upgrade only — assess opportunistic modernization separately afterward.

---

## Current state (verified 2026-05-21)

| Setting | Current value | Target |
|---|---|---|
| `<java.version>` (compiler source/target/release) | `11` | **`25`** |
| Runtime (mvn / java -version) | JDK 25.0.3 LTS | unchanged |
| `maven-compiler-plugin` | `3.9.0` | **`3.13.0`** (current LTS) |
| `junit` | `4.12` | keep 4.x for compat → `4.13.2` (small bump, not 5.x) |
| `slf4j-api` | `1.7.30` | **`2.0.16`** (current LTS, backwards-compatible API) |
| `--illegal-access=permit` JVM flag | present (in surefire argLine) | **remove** (no-op since JDK 17) |
| `module-info.java` | none | none (NOT introducing JPMS in this round) |

---

## Why "pure upgrade only" (per user choice)

Two distinct concerns conflated in "modernize":
1. **Make the build target current JDK 25**: mechanical, ~1-2 hours, unlocks all language features without committing to using them. Risk: low — most Java 11 source compiles cleanly under release=25.
2. **Refactor 1775 production files to use records / sealed types / virtual threads / etc.**: weeks-to-months, semantic risk (record equality differs from Cloneable, sealed types are exhaustive, virtual threads change scheduling).

Doing (1) first unblocks future opportunistic modernization without paying its risk now.

---

## Execution plan

### Phase A — pom changes

1. Edit `jquantlib-parent/pom.xml`:
   - `<java.version>11</java.version>` → `<java.version>25</java.version>`
   - `<mavencompiler.version>3.8.1</mavencompiler.version>` → `3.13.0` (typo in current key — also fix `<maven-compiler-plugin.version>3.9.0</maven-compiler-plugin.version>` if duplicated)
   - `<junit.version>4.12</junit.version>` → `4.13.2`
   - `<slf4j.version>1.7.30</slf4j.version>` → `2.0.16`
   - Surefire/Failsafe argLine: remove `--illegal-access=permit`
2. Edit `jquantlib/pom.xml`:
   - Confirm `<release>${java.version}</release>` is the form used (avoid `<source>/<target>` for cleaner JDK 25 compile)

### Phase B — compile-warning sweep

3. `mvn -pl ../jquantlib clean compile` (production code only)
4. Triage warnings. Likely categories:
   - **Removed API**: `Thread.stop()`, `SecurityManager` (deprecated since JDK 17). Grep usages.
   - **`sun.misc.Unsafe` direct usage**: unlikely in JQuantLib; verify.
   - **Reflection on JDK internals**: would have failed with `--illegal-access=permit` warning yesterday. Need to find + replace with public API or VarHandle/MethodHandle.
   - **`Files.readAllBytes` etc.**: API surface stable, no concern.
   - **Boxing/autoboxing warnings**: cosmetic.
5. For genuine warnings, fix in-place (small commits per category).

### Phase C — test compile + targeted run

6. `mvn -pl ../jquantlib clean test-compile` (test code)
7. Targeted `mvn -pl ../jquantlib test -Dtest=` runs on representative classes per package — NOT full suite (memory caveat from yesterday).
8. Fix any test failures introduced by junit 4.13 or slf4j 2.x API shifts.

### Phase D — commit + tag

9. Commit per phase (1 commit for pom, N commits for warning fixes, 1 commit for test ports if any).
10. Tag `jquantlib-jdk25-upgrade` at the final commit.

---

## What this upgrade DOES NOT do

- **No record/sealed/var refactors** — deferred to subsequent opportunistic-modernization pass per user choice.
- **No JPMS modularization** — explicitly out of scope.
- **No virtual-thread adoption** — would change concurrency semantics; needs separate design.
- **No Vector API / FFM API adoption** — JQuantMath could benefit (vector SIMD for transcendentals), but that's a focused future project.

---

## Pre-upgrade scan results (2026-05-21)

Grep-based survey of JDK-friction patterns in `jquantlib/src/main/java/`:

| Pattern | Count | Notes |
|---|---|---|
| `Thread.stop()` / `SecurityManager` / `Thread.suspend()` | 0 | Clean — no removed-API usage |
| `import sun.*` (JDK internal) | 0 | Clean — no JDK-internal direct deps |
| `setAccessible(true)` on `java.*` field | 2 (Settings.java, IborLeg.java) | Both reflect on `org.jquantlib.*` private fields (Date.timeOfDayNanos, Coupon.exCouponDate_) — same-module reflection, no `--add-opens` needed |
| `void finalize()` override | 0 real (1 commented-out, 1 false-positive on `finalizeComposite()`) | Clean — `finalize` removed in JDK 18; no impact |
| `.newInstance()` (deprecated since JDK 9) | ~12 files | Cosmetic deprecation warning. Migrate to `.getDeclaredConstructor().newInstance()` during sweep. Won't block compile. |

**Conclusion:** the upgrade is low-risk. Main work items beyond pom bumps:
- ~12 `.newInstance()` → `.getDeclaredConstructor().newInstance()` rewrites
- slf4j 1.7 → 2.x API shift (mostly transparent; `Logger` interface stable)
- junit 4.12 → 4.13.2 (very minor changes, mostly internal)

---

## Risks + mitigations

| Risk | Mitigation |
|---|---|
| `release=25` exposes API usage banned in newer JDKs | Triage compile warnings; fix one category at a time |
| `junit 4.13.2` breaks test discovery | Targeted runs first; rollback if widespread issues |
| `slf4j 2.x` breaks logging in tests | Most code uses `QL.info/warn/error`; slf4j is indirect dep. Low risk. |
| Removing `--illegal-access=permit` exposes hidden reflection | Already warned at runtime; should be no-op (was being ignored). |
| Mid-Phase-1.3 P1.3 agents' commits don't compile against JDK 25 | Wait for P1.3 closure before starting upgrade — user-approved sequencing |

---

## Definition of done

1. `<java.version>25</java.version>` in both poms
2. All `-Dtest=` per-package smoke runs PASS on JDK 25 source
3. No `--illegal-access=permit` left anywhere
4. `maven-compiler-plugin`, `junit`, `slf4j` all bumped
5. README badge updated to show JDK 25
6. Tag `jquantlib-jdk25-upgrade` at the closing commit

---

## What comes after (deferred, separate effort)

User-approved follow-up scope (separate session, after upgrade is stable):
- Targeted modernization assessment: pick 1-2 high-value features and apply broadly. Candidates:
  - **Records for value types** (Date, Period, Quote impls) — improves hashCode/equals correctness
  - **var for locals** — cosmetic but reduces line noise
  - **Pattern matching for instanceof** — cleanup in dispatch sites
  - **Switch expressions** — replaces switch-statement-with-fallthrough patterns
  - **Text blocks** — for multi-line strings (probe outputs, error messages)
- Then Phase 2 forward closure (currently #564) resumes on the modernized base.
