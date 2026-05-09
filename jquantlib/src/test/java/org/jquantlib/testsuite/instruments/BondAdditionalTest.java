/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5d additional skeleton port of {@code test-suite/bonds.cpp} v1.42.1
 * (1,896 LOC, 18 cases) — gap-fill for cases not in
 * {@link BondTest}.
 *
 * <p>{@link BondTest} already covers 7 of the 18 cases:
 * {@code testYield}, {@code testTheoretical}, {@code testCached},
 * {@code testCachedZero}, {@code testCachedFixed}, {@code testCachedFloating},
 * {@code testBrazilianCached}.
 *
 * <p>This companion file adds the 11 missing cases:
 * <ul>
 *   <li>{@code testAtmRate} — ATM par-yield consistency;
 *   <li>{@code testZspread} — Z-spread / OAS computation;
 *   <li>{@code testExCouponGilt} / {@code testExCouponAustralianBond} —
 *       UK gilt and Australian-style ex-coupon date conventions;
 *   <li>{@code testBondFromScheduleWithDateVector} —
 *       constructor-from-date-vector schedule path;
 *   <li>{@code testFixedBondWithGivenDates} /
 *       {@code testRiskyBondWithGivenDates} —
 *       date-vector-given variants of {@code FixedRateBond};
 *   <li>{@code testFixedRateBondWithArbitrarySchedule} —
 *       arbitrary (non-regular) schedule support;
 *   <li>{@code testThirty360BondWithSettlementOn31st} —
 *       Thirty-360 day-count regression for settlement on the 31st;
 *   <li>{@code testBasisPointValue} — DV01 / BPV;
 *   <li>{@code testFixingConvention} — fixing-day convention path.
 * </ul>
 *
 * <p><strong>All 11 cases deferred to Phase 5d.5</strong> — these tests
 * exercise existing Java {@code FixedRateBond} / {@code FloatingRateBond}
 * machinery, but require:
 * <ul>
 *   <li>Cross-validated probe reference values (most assertions are
 *       numeric NPV / yield / spread / DV01);
 *   <li>{@code testExCoupon*} — the {@code BondHelper} +
 *       {@code DiscountingBondEngine} ex-coupon date branch must be
 *       audited against C++ for the gilt / Australian conventions;
 *   <li>{@code testZspread} — needs the
 *       {@code BondFunctions::zSpread} static helper exposed in Java;
 *   <li>{@code testFixedRateBondWithArbitrarySchedule} — currently
 *       {@code FixedRateBond} requires a regular {@code Schedule}; the
 *       arbitrary-schedule constructor is not yet ported.
 * </ul>
 *
 * <p>Phase 5d.5 carry-forward: body these 11 cases against
 * {@code migration-harness/cpp/probes/instruments/bonds/} probes once
 * authored.
 *
 * <p>Source: {@code test-suite/bonds.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class BondAdditionalTest {

    private static final String REASON_NUMERIC =
            "Phase 5d.5 — requires C++ probe reference values for ATM "
          + "rate / Z-spread / DV01 / theoretical-yield assertions";

    private static final String REASON_EX_COUPON =
            "Phase 5d.5 — requires audit of ex-coupon date branch in "
          + "DiscountingBondEngine against gilt / Australian conventions";

    private static final String REASON_ARBITRARY =
            "Phase 5d.5 — requires FixedRateBond arbitrary-schedule "
          + "constructor (not yet ported)";

    private static final String REASON_GIVEN_DATES =
            "Phase 5d.5 — requires FixedRateBond date-vector constructor "
          + "branch + reference-value cross-validation";

    private static final String REASON_THIRTY_360 =
            "Phase 5d.5 — requires Thirty360 day-count regression coverage "
          + "for settlement on the 31st (audit + probe values needed)";

    private static final String REASON_FIXING =
            "Phase 5d.5 — requires audit of FloatingRateBond fixing-day "
          + "convention path + reference values";

    @Ignore(REASON_NUMERIC) @Test public void testAtmRate() { fail("not implemented"); }
    @Ignore(REASON_NUMERIC) @Test public void testZspread() { fail("not implemented"); }
    @Ignore(REASON_EX_COUPON) @Test public void testExCouponGilt() { fail("not implemented"); }
    @Ignore(REASON_EX_COUPON) @Test public void testExCouponAustralianBond() { fail("not implemented"); }
    @Ignore(REASON_GIVEN_DATES) @Test public void testBondFromScheduleWithDateVector() { fail("not implemented"); }
    @Ignore(REASON_GIVEN_DATES) @Test public void testFixedBondWithGivenDates() { fail("not implemented"); }
    @Ignore(REASON_GIVEN_DATES) @Test public void testRiskyBondWithGivenDates() { fail("not implemented"); }
    @Ignore(REASON_ARBITRARY) @Test public void testFixedRateBondWithArbitrarySchedule() { fail("not implemented"); }
    @Ignore(REASON_THIRTY_360) @Test public void testThirty360BondWithSettlementOn31st() { fail("not implemented"); }
    @Ignore(REASON_NUMERIC) @Test public void testBasisPointValue() { fail("not implemented"); }
    @Ignore(REASON_FIXING) @Test public void testFixingConvention() { fail("not implemented"); }
}
