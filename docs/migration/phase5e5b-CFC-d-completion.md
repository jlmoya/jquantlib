# Phase 5e.5b-CFC-d Completion — OvernightLeg structural body-fills + Schedule characterization + UnitedStates Juneteenth align

**Status:** complete
**Tag:** `jquantlib-phase5e5b-CFC-d-checkpoint` @ `0e2312c6`
**Predecessor:** `jquantlib-phase5e5b-CFC-c-checkpoint` @ `98ac66fd`
**Range:** 6 commits since `phase5e5b-CFC-c-checkpoint`
**Author:** autonomous-mode, 2026-05-14

---

## Final state

| Metric | CFC-c tip | CFC-d tip | Δ |
|--------|----------:|----------:|--:|
| Tests run | 2959 | **2961** | +2 |
| Failures | 0 | 0 | 0 ✓ |
| Errors | 0 | 0 | 0 ✓ |
| Skipped | 598 | **594** | -4 (4 newly green) |
| `mvn test` wall | 161.6s | ~187s | +25s (more tests + JIT) |
| Scanner WIP | 0 | 0 | 0 ✓ |

Net: **6 newly active passing tests** (4 OvernightLeg structural + 2 Schedule characterization).

---

## What landed (6 commits)

| Commit | Description |
|--------|-------------|
| `d5d072ce` | **Test:** un-ignore + body-fill 4 OvernightLeg structural tests (BasicFunctionality, SimpleAveraging, ErrorConditions, GearingsAndSpreads) — added CommonVarsONLeg fixture (1Y quarterly schedule on US-government-bond, eval 2025-06-01, 43 past SOFR fixings) + makeLeg() factory with all overloads |
| `e241eda7` | **Test:** Schedule characterization tests for post-BDC dedup behavior (2 new tests pinning down the `align(time.Schedule)` fix from Phase 5e.5b-CFC-c so it can't silently regress) |
| `d0fdb27f` | **Probe:** add overnight_leg_caps_floors_probe (mirrors C++ test fixture, dumps NPV + per-coupon attrs; cross-validates C++ NPV 34648.32860621049) |
| `9fe71083` | **Probe:** enrich overnight_leg_caps_floors_probe per-coupon dump |
| `ed12169f` | **Align:** port Juneteenth holiday rule to UnitedStates.GovernmentBondImpl + add Market.SOFR enum + SofrImpl. Java's `isJuneteenth(...)` helper existed but wasn't wired in for GovernmentBond — surfaced via the OvernightLeg body-fill effort. SofrImpl is dormant (Sofr index still uses GOVERNMENTBOND, pending GovernmentBond NFP carve-out fix in a follow-up) |
| `0e2312c6` | **Test:** add CommonVarsONLeg.setupForecastCurve helper + refine @Ignore reason on testOvernightLegWithCapsAndFloors with the residual-investigation findings |

---

## Notable findings

### 1. Latent Juneteenth bug in GovernmentBondImpl
The Java `UnitedStates.isJuneteenth(...)` helper has existed since Phase 5g.5d (Federal Reserve calendar work) but was never wired into GovernmentBondImpl, despite C++ v1.42.1 having it at `unitedstates.cpp:301-302`. The bug is silent unless a calendar consumer queries dates in late June 2022 onward. First exposed by the OvernightLeg body-fill effort.

### 2. Java's UnitedStates is missing several v1.42.1 nuances
Audited during the Juneteenth work:
- **MLK Day year guard** — C++ has `&& y >= 1983`; Java treats pre-1983 as holiday too (only matters if a curve has pre-1983 dates).
- **Good Friday NFP carve-out** — C++ has `&& (y < 1996 || d > 7)` for GovernmentBond; Java treats Good Friday as a full closure unconditionally. NOT fixed here — fixing it requires also adding SofrImpl's Good Friday-always-closed override and switching the Sofr index to use Market.SOFR. The Market.SOFR enum + SofrImpl are landed in this phase; activation deferred (would unblock testOvernightLegWithCapsAndFloors but needs more validation across all SOFR-using tests).

### 3. Residual ~2.7e-7 drift in coupon[3] vanilla compound rate
After fixing Juneteenth, Java's coupon[3] (period 2026-04-01 → 2026-07-01) has the correct 62 fixingDates matching C++. Yet vanilla compound rate differs by 2.7e-7 (Java 0.027594750 vs C++ 0.027595019), translating to a 0.067 NPV diff. The drift is localized to the daily-compound forward-rate computation across the Juneteenth gap (likely sub-period dt or curve-discount precision). Cross-validation probe ground-truth confirmed (`overnight_leg_caps_floors.json`). testOvernightLegWithCapsAndFloors stays @Ignore'd with the refined reason; pinpoint deferred to a CFC-d follow-up phase.

---

## Carry-forwards

- **CFC-d follow-up:** pinpoint the 2.7e-7 vanilla-rate drift in coupon[3]. Body-fill is ready (in conversation history), expected NPV is ground-truth-confirmed, calendar fixings match. Just need to find which sub-period dt or discount factor diverges.
- **GovernmentBond NFP carve-out** + activate `Market.SOFR` for the Sofr index (would unblock the WithCapsAndFloors NPV test if the residual drift is also addressed).
- **Phase 5e.5b-CFC follow-ups:** body-fill the remaining REASON_LOOKBACK / REASON_PAYMENT cases in OvernightIndexedCouponTest after Java OvernightIndexedCoupon ctor adds lookback/lockout/observationShift machinery.
- **Phase 5e.5b-CFC-d test deferred:** testOvernightLegWithLookback / WithLockout / WithObservationShift (3 cases) — all need Phase 5d.5 lookback machinery in OvernightIndexedCoupon.
- **OvernightLeg NPV test (testOvernightLegNPV):** uses lockout=3 + telescopic=true, both blocked on Java production gaps. Body-fill ready; needs lockout/telescopic ctor support first.

---

## Out of scope (explicit)

- All Phase 5i+ items
- Per-sub-phase completion docs back-fill for Phase 3i-5e.5b-CFC-c (still in the rolled-up retro)
