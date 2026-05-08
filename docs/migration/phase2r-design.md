# Phase 2r Design — Inflation Subsystem Closeout (Caps/Floors + Engines + Vol)

**Status:** approved 2026-05-08 (autonomous mode — ninth autonomous phase)
**Predecessor:** `jquantlib-phase2q-complete` (tests `832/0/0/22`, scanner WIP=0)

## 1. Context & Motivation

Phase 2q closed inflation YoY+CPI+Seasonality (60% → 85% surface coverage). Phase 2r closes the remaining 15% — caps/floors instruments, pricing engines, vol structures, and YoY optionlet pricers — bringing inflation to 100% v1.42.1 surface coverage.

Plus two carry-forward aligns from Phase 2q:
- `FiniteDifferenceNewtonSafe` solver port + adoption in PiecewiseZero/YoY bootstrap (tightens LOOSE 1e-5 → TIGHT)
- `YoYInflationIndex.fixing` past-path align (`ratio_=false` returns stored YoY rate directly per C++)

## 2. Scope

**In scope (~2,264 LOC C++):**
- **Vol structures (~628 LOC):**
  - `termstructures/volatility/inflation/yoyinflationoptionletvolatilitystructure.{hpp,cpp}`
  - `experimental/inflation/yoyinflationoptionletvolatilitystructure2.hpp`
- **Instruments (~1,336 LOC):**
  - `instruments/inflationcapfloor.{hpp,cpp}`
  - `instruments/cpicapfloor.{hpp,cpp}`
  - `instruments/cpiswap.{hpp,cpp}`
  - `instruments/makeyoyinflationcapfloor.{hpp,cpp}`
- **Engines + pricers (~300+ LOC):**
  - `pricingengines/inflation/inflationcapfloorengines.{hpp,cpp}` (Bachelier branch + Black-DD + Unit-Displaced)
  - YoY optionlet pricers in `cashflows/inflationcouponpricer.{hpp,cpp}` — Bachelier/Black/UnitDisplaced YoYInflationCouponPricer subclasses
- **L0 align prereqs (~200 LOC):**
  - `FiniteDifferenceNewtonSafe` solver port (mirror C++ `ql/math/solvers1d/finitedifferencenewtonsafe.hpp`)
  - `YoYInflationIndex.fixing` past-path align

**Out of scope (Phase 2s+):**
- yoyInflationLeg / cmsInflationLeg builder helpers (small, can fold into Phase 2r if time permits)
- Inflation calibration / market-data setup helpers
- Phase 3 subsystems beyond inflation

## 3. Approach

Two-layer:

**L0 (sequential align):**
- A.1 `FiniteDifferenceNewtonSafe` port — small standalone solver class, mirror C++ exactly
- A.2 Adopt FDNewtonSafe in `PiecewiseZeroInflationCurve` + `PiecewiseYoYInflationCurve` bootstrap loops; tier-promote LOOSE → TIGHT where tightening holds
- A.3 `YoYInflationIndex.fixing` past-path align (small)

**L1 (parallel):**
- **Track B (vol structures, ~628 LOC):**
  - YoYInflationOptionletVolatilityStructure
  - ConstantYoYOptionletVolatility (likely a subclass)
  - Experimental InterpolatedYoYInflationOptionletVolatilityCurve (yoyinflationoptionletvolatilitystructure2)
  - YoYOptionletStripper if discovered (Phase 2r seed → fold)
- **Track C (instruments + engines + pricers, ~1,636 LOC):**
  - InflationCapFloor (instrument)
  - CPICapFloor (instrument)
  - CPISwap (instrument)
  - MakeYoYInflationCapFloor (factory)
  - InflationCapFloorEngines (engines)
  - YoY optionlet pricers (Bachelier/Black/UnitDisplaced)

Track C is large but cohesive. May internal sub-track if implementer reports BLOCKED.

## 4. Decisions

- **P2R-1:** L0 FDNewtonSafe is mechanical solver port; tier promotion is empirical per test
- **P2R-2:** Phase 2q PiecewiseYoY 1e-5 inline-justified loosening should tighten to TIGHT after FDNewtonSafe adoption (P2Q A19 source identified)
- **P2R-3:** YoYInflationIndex.fixing past-path align unblocks past-period YoY testing (Phase 2q D.1 worked around with future-only)
- **P2R-4:** Track B + C parallel even though C depends on B for cap/floor pricers (vol surface needed for cap/floor NPV); pricer can use mock vol surface in tests until Track B integration
- **P2R-5:** Direct-to-main signed `-s` no Co-authored-by per standing rule

## 5. Pause triggers

Carry-forward A1-A27 + new **A28**: Track C cap/floor pricer test requires Track B's vol surface but Track B hasn't landed yet — bundle a temporary mock vol surface OR serialize C-after-B.

## Outcome forecast

| Metric | Phase 2q tip | Phase 2r target |
|--------|--------------|-----------------|
| Tests | 832/0/0/22 | ~845-855 (+13-23 across vol+caps/floors+swaps+engines+pricers) |
| Scanner WIP | 0 | 0 |
| Inflation surface coverage | 85% | 100% |
| Phase 2q Piecewise tier | LOOSE 1e-5 (4 cases) | TIGHT (after FDNewtonSafe) |
| Past-period YoY testing | blocked (A.D.1 workaround) | unblocked |
