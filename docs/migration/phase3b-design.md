# Phase 3b Design — CreditDefaultSwap + MidPointCdsEngine + CDS Helpers + Test Port

**Status:** approved 2026-05-08 (autonomous mode — sixteenth autonomous phase)
**Predecessor:** `jquantlib-phase3a-complete` (tests `973/0/0/45`, scanner WIP=0, mvn 59.3s)

## 1. Context

Phase 3a closed credit termstructures (production + 1 test file) but left CDS instrument + engines + creditdefaultswap.cpp test deferred. Phase 3b ports the CreditDefaultSwap instrument + MidPointCdsEngine (simplest) + CDS-based bootstrap helpers + creditdefaultswap.cpp test. IsdaCdsEngine + IntegralCdsEngine (sophisticated engines) deferred to Phase 3c.

## 2. Scope (~2,300 LOC C++)

**Production:**
- `instruments/creditdefaultswap.{hpp,cpp}` (874 LOC)
- `pricingengines/credit/midpointcdsengine.{hpp,cpp}` (239 LOC)
- CDS-based bootstrap helpers added to existing `org.jquantlib.termstructures.credit.DefaultProbabilityHelper` (~400 LOC C++ in `defaultprobabilityhelpers.{hpp,cpp}`)

**Test-suite:**
- `test-suite/creditdefaultswap.cpp` (1,083 LOC)

**Out of scope (Phase 3c):**
- IsdaCdsEngine (488 LOC C++, sophisticated)
- IntegralCdsEngine (250 LOC C++)
- isdacdshelper.{hpp,cpp} if separate

## 3. Approach

Two-layer:

**L0:** CreditDefaultSwap instrument (sequential foundation)
**L1 parallel:**
- Track B: MidPointCdsEngine + CdsHelper / SpreadCdsHelper / UpfrontCdsHelper additive
- Track C: creditdefaultswap.cpp test port (uses Track B's engine + helpers)

Track C may need to wait for Track B if test cases need engine. Coordinate via `git pull --ff-only` and Strategy 1 forward-declarations as before.

## 4. Decisions

- **P3B-1:** MidPointCdsEngine first (simplest); Isda + Integral deferred to Phase 3c
- **P3B-2:** CDS helpers added to existing Phase 3a DefaultProbabilityHelper hierarchy (additive, no refactor)
- **P3B-3:** Test port follows binding rigor: every BOOST_AUTO_TEST_CASE → faithful @Test; @Ignore CDS-Isda-dependent tests with `Phase 3c: needs IsdaCdsEngine` rationale
- **P3B-4:** Direct-to-main signed `-s` no Co-authored-by

## 5. Pause triggers

Carry-forward A1-A35.

## Outcome forecast

| Metric | Phase 3a tip | Phase 3b target |
|--------|--------------|-----------------|
| Tests | 973/0/0/45 | ~995-1015 (most CDS tests should pass with MidPoint engine; Isda-specific @Ignore) |
| Credit subsystem coverage | termstructures only | termstructures + CDS instrument + 1 of 3 engines + most tests |
| Phase 3a CDS-deferred @Ignore'd | 7 | unblock most (those that don't need Isda specifically) |
