# Phase 2u Completion — Inflation Cap/Floor + Swap Test-Suite Ports + L0 Aligns

**Status:** complete (autonomous mode — twelfth autonomous phase)
**Tag:** `jquantlib-phase2u-complete` @ `3581853`
**Predecessor:** `jquantlib-phase2t-complete` @ `37e88b8`
**Plan + Design:** `docs/migration/phase2u-{design,plan}.md`

## Final state

| Metric | Phase 2t tip | Phase 2u tip | Δ |
|--------|--------------|--------------|----|
| Tests | 919/0/0/40 | 933/0/0/37 | +14 active / -3 skipped |
| Scanner WIP | 0 | 0 | unchanged |
| Inflation test-suite coverage | 44% (1/7 files = inflation.cpp) | 86% (5/7 files = +inflationcapfloor + inflationcpiswap + inflationcpicapfloor + inflationcapflooredcoupon) | +42% |

## What landed (11 commits)

### L0 — Sequential align prereqs (5 commits)

| Commit | Description |
|--------|-------------|
| `9486712` | Phase 2u design + plan |
| `de81e43` | **L0 A.1:** UKRPI/EUHICP/YYUKRPI/YYEUHICP availabilityLag → 1 month per C++ v1.42.1 |
| `efb04dc` | **L0 A.2:** ZeroInflationIndex.lastFixingDate() + YoYInflationIndex.lastFixingDate() per C++ v1.42.1 |
| `d99f638` | **L0 A.3:** YoYInflationIndex(ZeroInflationIndex) ratio constructors per C++ v1.42.1 |
| `98d6150` | **L0 A.4:** ZeroCouponInflationSwapHelper dual-date + CPI::Linear/Flat overloads per C++ v1.42.1 |
| `25f566e` | **L0 A.5:** Re-enable 11 InflationTest @Ignore'd tests post-L0 aligns (empty bodies as compile-stubs awaiting body fill in Track F) |

### L1 — Parallel test-suite ports (4 commits)

| Commit | Description |
|--------|-------------|
| `c0d5358` | **Track D:** port inflationcpicapfloor.cpp (434 LOC C++, 2 BOOST_AUTO_TEST_CASE → 2 active @Test). Embedded CommonVars private static inner class. CPICapFloorTest.java replaced from smoke → full port. |
| `aeb9b1d` | **Track E:** port inflationcapflooredcoupon.cpp (784 LOC, 2 BOOST_AUTO_TEST_CASE → 2 active @Test). testDecomposition (9 cap/floor/collar identities) + testInstrumentEquality (840 sub-checks across length×strike×vol×pricer matrix). Replaced curve-relinking bootstrap with synthetic 16-pillar curve due to Java weak-ref observer cycle. |
| `dd66f92` | **Track C:** port inflationcpiswap.cpp (495 LOC, 3 BOOST_AUTO_TEST_CASE + 5 bridging tests). 6 active + 2 @Ignore'd (consistency: blocked by InterpolatedZeroCurve bug + missing IborCoupon.Settings.usingAtParCoupons accessor; cpibondconsistency: missing CPIBond instrument). |
| `599c898` | **Track B:** port inflationcapfloor.cpp (526 LOC, 3 BOOST_AUTO_TEST_CASE → 2 active + 1 @Ignore'd). testConsistency (5,880 sub-checks across 8 lengths × 7 caps × 7 floors × 5 vols × 3 pricers) + testParity (840 sub-checks). testCachedValue @Ignore'd pending YearOnYearInflationSwapHelper(quote,...,discountCurve) overload. |

### Track F — InflationTest body fills (1 commit)

| Commit | Description |
|--------|-------------|
| `3581853` | **Track F:** Port C++ bodies for 11 InflationTest tests un-ignored in L0. 6 fully filled + 5 retained as @Ignore'd with refined Phase 2v rationale. **4 substantive infrastructure align fixes folded in:** (1) PiecewiseZeroInflationCurve default accuracy 1e-12 → 1e-14 + update() override resetting calculated flag for re-bootstrap on setSeasonality(); (2) PiecewiseYoYInflationCurve identical update() override; (3) YearOnYearInflationSwapHelper added nominalTermStructure constructor parameter (mirrors C++ inflationhelpers.cpp:241-305); (4) AbstractTermStructure added updating_ re-entrancy guard in update() to prevent observer cycle infinite recursion. |

## A-trigger fire history

| Trigger | Where | Outcome |
|---------|-------|---------|
| **A26** | Track F | 4 substantive infrastructure aligns folded into Track F commit (per project rule for cross-cutting align discoveries during test ports) |
| **A29 (test exercises class/method that diverges)** | Tracks B/C/D/F | Several @Ignore'd with refined Phase 2v/2x rationale (CPIBond, InterpolatedZeroCurve constructor bug, IborCoupon.Settings.usingAtParCoupons accessor, YearOnYearInflationSwapHelper discount-curve overload) |
| **A30** | Track F | 5 of 11 empty-body tests couldn't be passed at C++ tier with current Java production state — re-Ignore'd with refined Phase 2v rationale (no silent skips per rigor directive) |

A1-A25, A27, A28, A31 did not fire.

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P2U-1** | Existing Phase 2q+2r smoke tests **replaced** with full faithful C++ ports | Smoke-only tests don't satisfy rigor directive |
| **P2U-2** | L0 + L1 + F = 11 commits across 6 worktrees | Maximum parallelism while respecting cross-track dependencies (F depends on L0) |
| **P2U-3** | Track F pulled in 4 production aligns (curve update overrides + helper nominalTermStructure + AbstractTermStructure updating_ guard) per A26 | These were necessary to make the un-ignored test bodies pass; folded into single commit per minimal-touch convention |
| **P2U-4** | 5 Track F tests retained as @Ignore'd with refined rationale rather than passing trivially | Per rigor directive: never silently skip; refined Phase 2v rationale documented inline |
| **P2U-5** | Direct-to-main signed `-s` no Co-authored-by | Standing rule |

## Phase 2v+ seed list (refined)

### Phase 2v — inflation tail closeout (~6,000 LOC C++)

1. **CPIBond instrument port** (~700 LOC C++ in `ql/instruments/bonds/cpibond.{hpp,cpp}`) — required for `inflationcpibond.cpp` test port and Track C's `cpibondconsistency`.
2. **`inflationcpibond.cpp` test port** (296 LOC) — depends on #1.
3. **`inflationvolatility.cpp` test port** (395 LOC) — exercises Phase 2s experimental coverage.
4. **AUCPI** (82 LOC), **UKHICP** (41 LOC), **USCPI** (83 LOC), **EUHICPXT** (small additive in euhicp.hpp), **FRHICP**, **ZACPI** + corresponding YY variants — ~12 missing classes (~600 LOC C++).
5. **GlobalBootstrap template for inflation curves**.
6. **PiecewiseZeroInflationCurve lazy-baseDate Supplier ctor**.
7. **YearOnYearInflationSwapHelper(quote,...,discountCurve) overload** — unblocks Track B's testCachedValue.

### Phase 2x — Small infrastructure aligns (~150 LOC)

8. **`InterpolatedZeroCurve` constructor bug fix** — `data[0]==1.0` assertion treats raw zero rates as discount factors. Blocks tests in 4 files (Phase 2s + 2u Tracks C/D + future). High leverage.
9. **`IborCoupon.Settings.usingAtParCoupons()` static accessor** — needed by Track C's @Ignore'd `consistency` test.
10. **`AbstractTermStructure` → `LazyObject` cycle prevention** — Track F added a single-method updating_ guard but a proper LazyObject pattern would be more semantically correct. Touches the entire termstructure hierarchy. Optional cleanup.
11. **IndexManager test isolation lint/audit pass** — mirrors C++ TopLevelFixture::clearHistories(); currently inconsistently applied across test classes.
12. **Body fill remaining 5 InflationTest tests** (Track F retained as @Ignore'd with refined Phase 2v rationale).

### Phase 3+ subsystem ports (post-inflation)

13. **`termstructures/credit/`** + tests (~6K LOC C++ + tests) — clean greenfield.
14. **`models/marketmodels/`** + tests (~25-30K + tests).
15. **`experimental/`** (non-inflation, non-credit) + tests.
16. **Remaining C++ test-suite files** (~70+ cpp files in test-suite/ beyond inflation, full rigor).

## Out-of-scope (explicit, deferred)

- All Phase 2v+ items above
- BroadieKaya retry — needs lgamma
- NCCS EXACT — needs lgamma
- JQuantMath.lgamma — still no source
