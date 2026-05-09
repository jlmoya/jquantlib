# Phase 3a Design — Credit Termstructures + Tests

**Status:** approved 2026-05-08 (autonomous mode — fifteenth autonomous phase; **first Phase-3 subsystem port**)
**Predecessor:** `jquantlib-phase2x-complete` (tests `951/0/0/38`, scanner WIP=0, mvn 59.5s)

## 1. Context

Inflation subsystem 100% complete (production + test-suite) as of Phase 2v + 2x. Phase 3a begins the next greenfield subsystem: credit termstructures.

Java currently has NO credit-related classes (verified via grep — no `*Credit*` or `*Cds*` matches). Clean greenfield port.

## 2. Scope (~3,000 LOC C++)

**Production (~2,444 LOC):**
- `termstructures/credit/probabilitytraits.hpp` (~270 LOC) — bootstrap traits for default probability curves
- `termstructures/credit/defaultdensitystructure.{hpp,cpp}` (~170 LOC) — base for default density-based curves
- `termstructures/credit/hazardratestructure.{hpp,cpp}` (~200 LOC) — base for hazard-rate-based curves
- `termstructures/credit/survivalprobabilitystructure.{hpp,cpp}` (~140 LOC) — base for survival-probability-based curves
- `termstructures/credit/flathazardrate.{hpp,cpp}` (~140 LOC) — flat hazard rate curve
- `termstructures/credit/interpolateddefaultdensitycurve.hpp` (~270 LOC)
- `termstructures/credit/interpolatedhazardratecurve.hpp` (~270 LOC)
- `termstructures/credit/interpolatedsurvivalprobabilitycurve.hpp` (~280 LOC)
- `termstructures/credit/piecewisedefaultcurve.hpp` (~290 LOC) — piecewise bootstrap
- `termstructures/credit/defaultprobabilityhelpers.{hpp,cpp}` (~420 LOC) — bootstrap helpers (CDS-based, etc.)

**Test-suite (~533 LOC):**
- `test-suite/defaultprobabilitycurves.cpp` (533 LOC)

**Out of scope (Phase 3b):**
- CreditDefaultSwap instrument port (~700 LOC C++ in `ql/instruments/creditdefaultswap.{hpp,cpp}`)
- IsdaCdsEngine + MidpointCdsEngine + IntegralCdsEngine (~1500 LOC across multiple files)
- creditdefaultswap.cpp test (1,083 LOC)
- DefaultProbabilityHelpers' CDS-based variants if they require CreditDefaultSwap
- Phase 3+ subsystems beyond credit

## 3. Approach

L0 sequential foundation, L1 parallel curves, L2 sequential test port:

- **L0:** probabilitytraits + 3 base term-structure classes (DefaultDensityStructure / HazardRateStructure / SurvivalProbabilityStructure) + FlatHazardRate. Foundation for L1.
- **L1 parallel:** 3 interpolated curve classes (DefaultDensity / HazardRate / SurvivalProbability) + PiecewiseDefaultCurve + DefaultProbabilityHelpers (non-CDS variants).
- **L2:** defaultprobabilitycurves.cpp test port.

## 4. Decisions

- **P3A-1:** Java package: `org.jquantlib.termstructures.credit` (mirror C++ ql/termstructures/credit/)
- **P3A-2:** Mirror existing JQuantLib bootstrap framework patterns (PiecewiseYieldCurve / IterativeBootstrap precedent from Phase 2p inflation)
- **P3A-3:** CDS-based DefaultProbabilityHelpers deferred to Phase 3b (depend on CreditDefaultSwap instrument)
- **P3A-4:** Test port follows binding rigor directive — every C++ BOOST_AUTO_TEST_CASE → faithful Java @Test
- **P3A-5:** Direct-to-main signed `-s` no Co-authored-by

## 5. Pause triggers

Carry-forward A1-A34. New **A35:** credit subsystem requires a test fixture class not yet in Java (mirroring C++ test-suite CommonVars) — bundle as align prereq OR scope the test to use inline fixture.

## Outcome forecast

| Metric | Phase 2x tip | Phase 3a target |
|--------|--------------|-----------------|
| Tests | 951/0/0/38 | ~970-985 |
| Credit subsystem coverage | 0% | termstructures + 1 test file (~70% of credit core) |
| Java packages added | — | `org.jquantlib.termstructures.credit` |
