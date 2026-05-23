# L6 test-suite parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** Faithfully port the 12 genuinely-missing C++ `test-suite/*.cpp` files
to Java JUnit 4 test classes, restoring full test-name parity with v1.42.1.

**Architecture:** Each cluster is a thematic group of related C++ test files.
Tests should faithfully port C++ test bodies, with reference values either
extracted from C++ test-suite literals OR derived via one-off C++ probes.
Java JUnit 4 idiom: `@Test public void testXxx()` mirroring C++
`BOOST_AUTO_TEST_CASE(testXxx)`.

**Tech Stack:** JDK 25, Maven, JUnit 4.13.2.

---

## Scope

**Audit result (181 C++ test files total):**
- 163 likely-present in Java (by name heuristic + cross-check)
- 4 SKIP: build/fixture infrastructure (quantlibbenchmark, preconditions, quantlibglobalfixture, quantlibtestsuite)
- **12 genuinely missing, 5064 C++ LOC total**

| # | C++ file | LOC | Cluster |
|---|---|---|---|
| 1 | `inflationcapflooredcoupon.cpp` | 784 | L6-B |
| 2 | `riskstats.cpp` | 612 | L6-A |
| 3 | `marketmodel_smmcaplethomocalibration.cpp` | 608 | L6-C |
| 4 | `marketmodel_smm.cpp` | 507 | L6-C |
| 5 | `inflationcpiswap.cpp` | 495 | L6-B |
| 6 | `inflationcpicapfloor.cpp` | 434 | L6-B |
| 7 | `stats.cpp` | 382 | L6-A |
| 8 | `marketmodel_smmcapletalphacalibration.cpp` | 346 | L6-C |
| 9 | `marketmodel_smmcapletcalibration.cpp` | 337 | L6-C |
| 10 | `inflationcpibond.cpp` | 296 | L6-B |
| 11 | `commodityunitofmeasure.cpp` | 142 | L6-D |
| 12 | `cdsoption.cpp` | 121 | L6-D |

## Clusters

### L6-A — statistics tests (994 LOC C++)
- `stats.cpp` (382 LOC) — basic statistics framework tests
- `riskstats.cpp` (612 LOC) — risk-statistics extensions (VaR, ES, etc.)

### L6-B — inflation tests (2305 LOC C++)
- `inflationcapflooredcoupon.cpp` (784 LOC)
- `inflationcpiswap.cpp` (495 LOC)
- `inflationcpicapfloor.cpp` (434 LOC)
- `inflationcpibond.cpp` (296 LOC)

### L6-C — marketmodel SMM tests (1798 LOC C++)
- `marketmodel_smmcaplethomocalibration.cpp` (608 LOC)
- `marketmodel_smm.cpp` (507 LOC)
- `marketmodel_smmcapletalphacalibration.cpp` (346 LOC)
- `marketmodel_smmcapletcalibration.cpp` (337 LOC)

### L6-D — small remainders (263 LOC C++)
- `commodityunitofmeasure.cpp` (142 LOC)
- `cdsoption.cpp` (121 LOC)

---

## Per-test-file TDD template

1. Read C++ test file (each `BOOST_AUTO_TEST_CASE` is one Java `@Test`)
2. For each test method:
   a. Mirror BOOST_CHECK / BOOST_CHECK_CLOSE / BOOST_TEST_MESSAGE in JUnit 4 idiom
   b. Reference values come from C++ literals OR existing JSON probe files
3. Existing fixture classes (CommonVars in C++) may need new Java helpers — port them if needed
4. Test ports should COMPILE and PASS (or document SKIP if dependencies missing)
5. Commit per logical batch with `-s` sign-off

---

## Sequencing

L6-A, L6-B, L6-C, L6-D in parallel across 4 worktrees (worktree E free for L6-A backup or unused).

---

## Definition of done

- 12 test files ported to Java
- Each new test runs and passes (or documents @Ignore with rationale)
- Full suite still 3531+/0/0 baseline (no regression)
- Tag `jquantlib-phase2-l6-test-suite-parity-complete`
