# Phase 2v Design — Inflation Tail Closeout (CPIBond + indexes + remaining test ports)

**Status:** approved 2026-05-08 (autonomous mode — thirteenth autonomous phase)
**Predecessor:** `jquantlib-phase2u-complete` (tests `933/0/0/37`, scanner WIP=0)

## 1. Context

Phase 2u closed 4 of 7 inflation test-suite files (86% coverage). Phase 2v closes the remaining 2 files plus the missing production classes they depend on, bringing inflation to 100% (production + test-suite).

## 2. Scope (~6,000 LOC C++)

**Production classes (new):**
- `org.jquantlib.instruments.bonds.CPIBond` (~700 LOC C++ in `ql/instruments/bonds/cpibond.{hpp,cpp}`)
- 6 missing CPI base index classes:
  - AUCPI (~82 LOC), UKHICP (~41 LOC), USCPI (~83 LOC), FRHICP, ZACPI (~50 LOC each)
  - EUHICPXT (additive in existing euhicp.hpp)
- 6 corresponding YY variants (YYAUCPI, YYUKHICP, etc.) — small additive (~30 LOC each)
- `org.jquantlib.termstructures.bootstraptraits.GlobalBootstrap` template (or generic class) — needed by `testEuHicpFlatBootstrapAtMonthStart`
- `PiecewiseZeroInflationCurve` lazy-baseDate Supplier ctor — needed by `testZeroTermStructureLazyBaseDate`
- `YearOnYearInflationSwapHelper(quote, ..., discountCurve)` overload — needed by Phase 2u Track B's testCachedValue

**Test-suite ports (~691 LOC C++):**
- `inflationcpibond.cpp` (296 LOC) → `CPIBondTest.java`
- `inflationvolatility.cpp` (395 LOC) → `InflationVolatilityTest.java`

**Out of scope (Phase 2x):**
- InterpolatedZeroCurve constructor bug fix
- IborCoupon.Settings.usingAtParCoupons accessor
- AbstractTermStructure → LazyObject proper cycle prevention
- IndexManager test isolation audit
- Body-fill remaining 5 Track F @Ignore'd tests

## 3. Approach

Three-layer:

**L0 sequential (small additive prereqs):**
- Track A.1: 6 missing CPI base index classes + YY variants (~700 LOC)
- Track A.2: GlobalBootstrap template (~150 LOC)
- Track A.3: PiecewiseZeroInflationCurve lazy-baseDate Supplier ctor (~30 LOC)
- Track A.4: YearOnYearInflationSwapHelper(quote, ..., discountCurve) overload (~50 LOC)

**L1 parallel:**
- Track B: CPIBond instrument port (~700 LOC C++) → CPIBondTest.java structural smoke
- Track C: inflationvolatility.cpp test port (395 LOC C++) — independent of CPIBond

**L2 sequential after Track B:**
- Track D: inflationcpibond.cpp test port (296 LOC C++) — depends on Track B's CPIBond

## 4. Decisions

- **P2V-1:** L0 sequential aligns (A.1-A.4) all before L1 to give Tracks B+C clean foundation
- **P2V-2:** Track D serializes after Track B (CPIBond dependency)
- **P2V-3:** Phase 2x small aligns (InterpolatedZeroCurve, etc.) NOT in Phase 2v — separate phase to keep scope tractable
- **P2V-4:** Direct-to-main signed `-s` no Co-authored-by

## 5. Pause triggers

Carry-forward A1-A31. New **A32:** missing index classes need test-suite-level inflation test data tables (e.g., AUCPI fix data) which may not exist in C++ source — workaround: synthesize from the test fixture or skip data-dependent tests.

## Outcome forecast

| Metric | Phase 2u tip | Phase 2v target |
|--------|--------------|-----------------|
| Tests | 933/0/0/37 | ~970-990 |
| Inflation test-suite coverage | 86% (5/7 files) | 100% (7/7 files) |
| Inflation production coverage | 100% surface | 100% (no change — closing test gaps and small aligns) |
| Missing CPI index classes | 6 | 0 |
