# Phase 3b Implementation Plan

> Two-layer phase. L0 CreditDefaultSwap instrument (sequential), L1 parallel B (engine+helpers) + C (test port).

**Goal:** CDS instrument + simplest engine + CDS helpers + test. Tag `jquantlib-phase3b-complete`.

## L0 — Pre-flight + worktree setup

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-3b-A /Users/josemoya/eclipse-workspace/jquantlib-3b-A main
cd /Users/josemoya/eclipse-workspace/jquantlib-3b-A
git submodule update --init --recursive
```

After L0 lands:
```bash
git worktree add -b phase-3b-B /Users/josemoya/eclipse-workspace/jquantlib-3b-B main
git worktree add -b phase-3b-C /Users/josemoya/eclipse-workspace/jquantlib-3b-C main
```

## L0 — CreditDefaultSwap instrument

- C++ source: `migration-harness/cpp/quantlib/ql/instruments/creditdefaultswap.{hpp,cpp}` (874 LOC C++)
- Java target: `org.jquantlib.instruments.CreditDefaultSwap`
- Mirror C++ structure: extends Instrument, takes Side (Buyer/Seller) + nominalNotional + spread + premiumSchedule + paymentConvention + dayCounter + protectionStart + protectionPaymentTime
- Inner classes: `CreditDefaultSwap.Arguments`, `Results`, `Engine` (PricingEngine subtype)
- Smoke test verifying construction + setters + arguments validation
- Commit: `infra(instruments): port CreditDefaultSwap (Phase 3b L0)`

## L1 Track B — MidPointCdsEngine + CDS helpers

- Worktree: `/Users/josemoya/eclipse-workspace/jquantlib-3b-B`
- C++ source:
  - `pricingengines/credit/midpointcdsengine.{hpp,cpp}` (239 LOC)
  - `termstructures/credit/defaultprobabilityhelpers.{hpp,cpp}` CDS-based variants (additive to Phase 3a; ~400 LOC C++)
- Java targets:
  - `org.jquantlib.pricingengines.credit.MidPointCdsEngine`
  - Additive to existing `org.jquantlib.termstructures.credit.DefaultProbabilityHelper`: `CdsHelper`, `SpreadCdsHelper`, `UpfrontCdsHelper`
- Probes + tier-stratified tests
- Commit: `infra(pricingengines.credit,termstructures.credit): MidPointCdsEngine + CDS-based helpers (Phase 3b B)`

## L1 Track C — creditdefaultswap.cpp test port

- Worktree: `/Users/josemoya/eclipse-workspace/jquantlib-3b-C`
- C++ source: `migration-harness/cpp/quantlib/test-suite/creditdefaultswap.cpp` (1,083 LOC C++)
- Java target: `jquantlib/src/test/java/org/jquantlib/testsuite/instruments/CreditDefaultSwapTest.java`
- Per binding rigor directive: every BOOST_AUTO_TEST_CASE → faithful @Test
- @Ignore Isda-specific tests with `Phase 3c: needs IsdaCdsEngine` rationale
- Pull main with `git pull --ff-only` to absorb Track B's engine before pushing
- Commit: `infra(testsuite.instruments): port creditdefaultswap.cpp test cases (Phase 3b C)`

Plus: un-ignore Phase 3a's 7 CDS-deferred tests in `DefaultProbabilityCurvesTest.java` and verify they pass with the new helpers.

## L2 — Completion + tag + memory + README + teardown

Standard milestone-doc discipline.
