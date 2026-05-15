# Phase 5e.5b-CFC-d-final Completion

**Status:** complete (autonomous mode, max-parallelism)
**Tag:** `jquantlib-phase5e5b-CFC-d-final` @ `651b1058`
**Predecessor:** `jquantlib-phase5e5b-CFC-d-checkpoint` @ `0e2312c6`
**Range:** ~14 commits since CFC-d-checkpoint (mix of foreground + 4 background agents)
**Author:** controller + multi-agent dispatch, 2026-05-14

---

## Final state

| Metric | CFC-d tip | CFC-d-final tip | Δ |
|--------|----------:|----------------:|--:|
| Tests run | 2961 | **2961** | 0 |
| Failures | 0 | 0 | 0 ✓ |
| Errors | 0 | 0 | 0 ✓ (heap bump eliminated flakies) |
| Skipped | 594 | **586** | -8 (8 newly active) |
| Active passing | 2367 | **2375** | +8 |

Net: **8 newly-active passing tests**, 0 regression, 1 production-bug fix that closed the residual 2.7e-7 NPV drift surfaced in CFC-c.

---

## Major landings (highlighted)

### Production-code alignments
- `0c193757` — `align(time.calendars)`: wire `isJuneteenth` into UnitedStates SettlementImpl + NyseImpl. Audit found same latent-wiring pattern as CFC-d's GovernmentBondImpl fix.
- `638bd307` — `align(instruments.FixedRateBond + cashflow.FixedRateLeg)`: handle arbitrary-schedule (`fullInterface=false`) construction. Adds `Schedule.fullInterface()` / `hasTenor()` / `hasIsRegular()` accessors.
- `fd445543` — `align(termstructures.InterpolatedZeroCurve)`: port flat-fwd extrapolation past last pillar. **Closed the residual 2.7e-7 vanilla-rate drift** in coupon[3] of testOvernightLegWithCapsAndFloors via a 6-LOC fix.
- `3f39f024` — `infra(pricingengines.BlackFormula)`: port `blackFormulaForwardDerivative`, `bachelierBlackFormulaForwardDerivative`, `blackFormulaStdDevSecondDerivative`, RS / Chambers approximations.

### Test body-fills
- `3f39f024` — 5 BlackFormula tests body-filled (forward-derivative + bachelier + zero-strike + zero-vol). Mean-Value-Theorem self-validation.
- `d8319775` — `test(instruments.AmortizingBondTest)`: un-ignore + body-fill testAmortizingFixedRateBond against Excel-PMT references (13 rates × 30y monthly amortization).
- `638bd307` — `BondAdditionalTest.testFixedRateBondWithArbitrarySchedule` body-filled + un-ignored.
- `fd445543` — `OvernightIndexedCouponTest.testOvernightLegWithCapsAndFloors` un-ignored — the test that surfaced the entire CFC-d work, now PASSING at 1e-8 tolerance.

### Probes
- `9fe71083` — enrich overnight_leg_caps_floors_probe with per-coupon dump.
- `6f007774` — add cubic_extrapolation_tail_probe for the Cubic-tail diagnostic.

### Build/infra
- `651b1058` — `infra(build)`: bump Surefire JVM heap to 2g — eliminated the intermittent NoClassDefFoundError flakiness (50-250 random errors) that had been masking real test results.

---

## Notable findings

### 1. Latent Juneteenth wiring across all 3 main UnitedStates calendars
The `isJuneteenth(...)` helper had been ported in Phase 5g.5d (for FederalReserve) but **never wired into GovernmentBondImpl, SettlementImpl, or NyseImpl** despite C++ wiring it in all three (`unitedstates.cpp:152, 200, 302`). Same shape as the CFC-c Schedule.dedup bug — helper present but call site missing.

**Audit pattern documented for future passes:** grep for private static helpers referenced from one calendar variant but missing from siblings.

### 2. Cubic-tail extrapolation: `InterpolatedZeroCurve`, not `CubicInterpolation`
Agent A's investigation showed the residual ~2.7e-7 drift was NOT in `CubicInterpolation` (Java + C++ produce identical cubic-extrapolation values) but in **`InterpolatedZeroCurve.zeroYieldImpl(t)`**:
- C++ (`zerocurve.hpp:159-169`): flat-forward extrapolation past `times_.back()`
- Java (pre-fix): cubic-extrapolated the zero-rate polynomial of the last segment

The Cubic itself was bit-exact; the bug was in the WRAPPER's extrapolation strategy.

### 3. Surefire heap was undersized for the test suite
Default 256m couldn't hold the 575-class suite — caused random NoClassDefFoundError on FDShoutEngine, AssetSwap, StulzEngine. 2g eliminates this. Should have been done long ago.

---

## Still-open carry-forwards

- **BlackFormula RS / Chambers**: ports landed but 3 tests stay @Ignore'd. RS returns wrong stdDev for ITM cases. Chambers has too-strict parameter check. Both need line-by-line audit.
- **Calendar gaps catalogued by agent**: UK Bank Holiday consolidator, Japan Naruhito (2020+), China year-data 2010-2026, India May Day, Germany spurious Dec-31 New Year's Eve, etc.
- **Bond infrastructure**: rich Schedule-from-dates ctor, FixedRateBond exCouponPeriod, BondFunctions statics, RiskyBondEngine.
- **OvernightIndexedSwapTest** (19 @Ignore'd): OISRateHelper bootstrap, MakeOIS lookback, etc.
- **Most experimental/ tests**: missing engine classes.

---

## Out of scope (explicit)

- All Phase 5i+ items
- Per-sub-phase completion docs for Phase 3i-5e.5b-CFC-c (in rolled-up retro)
