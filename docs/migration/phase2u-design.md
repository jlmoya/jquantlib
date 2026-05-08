# Phase 2u Design — Inflation Cap/Floor + Swap Test-Suite Ports + Phase 2x Aligns

**Status:** approved 2026-05-08 (autonomous mode — twelfth autonomous phase)
**Predecessor:** `jquantlib-phase2t-complete` (tests `919/0/0/40`, scanner WIP=0)

## 1. Context

Phase 2t set the precedent for test-suite rigor with inflation.cpp port. Phase 2u continues with the next 4 inflation test files (~2,239 LOC C++), and bundles Phase 2x small aligns (~85 LOC) as L0 to unblock 12 of the 18 @Ignore'd tests in the existing InflationTest.

## 2. Scope

**L0 sequential aligns (~85 LOC, unblocks 12 @Ignore'd tests):**
1. UKRPI/EUHICP/YYUKRPI/YYEUHICP availabilityLag → 1 month per C++ (4 lines)
2. ZeroInflationIndex.lastFixingDate() method (~10 lines, mirrors C++ inflationindex.cpp:186-191)
3. YoYInflationIndex(ZeroInflationIndex underlying) ratio constructor (~30 lines)
4. YoYInflationIndex(ZeroInflationIndex underlying, boolean interpolated) deprecated overload (~5 lines)
5. ZeroCouponInflationSwapHelper dual-date + CPI::Linear/Flat overloads (~30 lines)

After L0, run `mvn test -Dtest=InflationTest` and remove @Ignore from the 12 unblocked tests.

**L1 parallel test-suite ports:**
- **Track B:** `inflationcapfloor.cpp` (526 LOC) → `InflationCapFloorTest.java`
- **Track C:** `inflationcpiswap.cpp` (495 LOC) → `CPISwapTest.java` (currently a smoke test from Phase 2r — replace with full port)
- **Track D:** `inflationcpicapfloor.cpp` (434 LOC) → `CPICapFloorTest.java` (currently a smoke test from Phase 2r — replace with full port)
- **Track E:** `inflationcapflooredcoupon.cpp` (784 LOC) → `CapFlooredInflationCouponTest.java` (currently a smoke test from Phase 2q D.1 — replace with full port)

**Out of scope (Phase 2v):**
- `inflationcpibond.cpp` (296 LOC) + CPIBond instrument port (~400 LOC C++)
- `inflationvolatility.cpp` (395 LOC)
- AUCPI/UKHICP/USCPI/EUHICPXT inflation index classes
- GlobalBootstrap template
- PiecewiseZeroInflationCurve lazy-baseDate Supplier ctor

## 3. Approach

L0 sequential, L1 parallel. Same pattern as Phase 2q.

## 4. Decisions

- **P2U-1:** Existing smoke tests (CPISwapTest, CPICapFloorTest, CapFlooredInflationCouponTest from Phase 2q+2r) are **replaced** with full faithful C++ ports. Smoke-only tests don't satisfy rigor directive.
- **P2U-2:** L0 aligns require careful test verification — InflationTest's 12 newly-active tests must pass after each L0 sub-commit; if any fail, fix as part of the same align commit (test-driven align).
- **P2U-3:** Test ports follow Phase 2t conventions: BOOST_AUTO_TEST_CASE → @Test method, no silent skips, @Ignore with documented Phase 2v/2x rationale where required.
- **P2U-4:** Direct-to-main signed `-s` no Co-authored-by per standing rule.

## 5. Pause triggers

Carry-forward A1-A30. New **A31:** L0 align inadvertently breaks an existing-passing test in InflationTest — fix root cause, do not relax. May require iterative debugging.

## Outcome forecast

| Metric | Phase 2t tip | Phase 2u target |
|--------|--------------|-----------------|
| Tests | 919/0/0/40 | ~970-1010 (+30-50 active from L0 unblock + L1 ports) |
| @Ignore count | 40 (22 pre-existing + 18 from Phase 2t) | ~28 (12 unblocked by L0, possible new Phase 2v/2x @Ignore'd in L1 ports) |
| Inflation test-suite coverage | 44% (1/7 files, 2,323/5,253 LOC) | 86% (5/7 files, 4,562/5,253 LOC) |

## Sub-layer order

L0 sequential (5 sub-tasks). L1 parallel B+C+D+E.
