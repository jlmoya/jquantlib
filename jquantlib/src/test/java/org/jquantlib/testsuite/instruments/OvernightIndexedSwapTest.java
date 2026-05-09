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
 * Phase 5d skeleton port of {@code test-suite/overnightindexedswap.cpp}
 * v1.42.1 (1,098 LOC, 21 cases).
 *
 * <p>Exercises the {@code OvernightIndexedSwap} (OIS) instrument and the
 * {@code MakeOIS} factory. Covers fair-rate / fair-spread / cached-value
 * pricing, full bootstrap (base, with arithmetic average, with telescopic
 * dates, with custom pricer, with lookback / observation shift / lockout
 * variants), seasoned swaps, {@code MakeOIS} default settlement days,
 * end-of-month regression (#2453), settlement-days/effective-date conflict
 * detection, constructors / nominals, and the curve-bootstrap regressions.
 *
 * <p><strong>All 21 cases deferred to Phase 5d.5</strong> — Java has no
 * OIS family:
 * <ul>
 *   <li>No {@code OvernightIndex} hierarchy / index classes
 *       (Eonia/Sonia/SOFR/etc.);
 *   <li>No {@code OvernightIndexedSwap} instrument
 *       (C++ {@code ql/instruments/overnightindexedswap.hpp});
 *   <li>No {@code MakeOIS} convenience factory;
 *   <li>No {@code OISRateHelper} for curve bootstrapping;
 *   <li>No {@code DiscountingSwapEngine} branch wired for OIS conventions;
 *   <li>No {@code OvernightIndexedCoupon} / {@code OvernightLeg}
 *       (see {@link
 *       org.jquantlib.testsuite.cashflows.OvernightIndexedCouponTest}).
 * </ul>
 *
 * <p>Phase 5d.5 carry-forward: the OIS subsystem is the largest single
 * production-code gap remaining in the bond-instrument area. Required
 * for SOFR-discounting curves and post-Libor reform pricing — see
 * also {@link org.jquantlib.testsuite.indexes.SofrFuturesTest} which
 * shares the {@code OvernightIndex} prerequisite.
 *
 * <p>Source: {@code test-suite/overnightindexedswap.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class OvernightIndexedSwapTest {

    private static final String REASON_PRICING =
            "Phase 5d.5 — requires OvernightIndexedSwap + OvernightIndex + "
          + "OIS pricing engine (no Java equivalent yet)";

    private static final String REASON_BOOTSTRAP =
            "Phase 5d.5 — requires OISRateHelper + bootstrap wiring "
          + "(no Java equivalent yet)";

    private static final String REASON_MAKE_OIS =
            "Phase 5d.5 — requires MakeOIS factory (no Java equivalent yet)";

    private static final String REASON_NOTIFY =
            "Phase 5d.5 — requires OIS observable wiring + cached-value "
          + "regression coverage (no Java equivalent yet)";

    @Ignore(REASON_PRICING) @Test public void testFairRate() { fail("not implemented"); }
    @Ignore(REASON_PRICING) @Test public void testFairSpread() { fail("not implemented"); }
    @Ignore(REASON_PRICING) @Test public void testCachedValue() { fail("not implemented"); }
    @Ignore(REASON_BOOTSTRAP) @Test public void testBaseBootstrap() { fail("not implemented"); }
    @Ignore(REASON_BOOTSTRAP) @Test public void testBootstrapWithArithmeticAverage() { fail("not implemented"); }
    @Ignore(REASON_BOOTSTRAP) @Test public void testBootstrapWithTelescopicDates() { fail("not implemented"); }
    @Ignore(REASON_BOOTSTRAP) @Test public void testBootstrapWithTelescopicDatesAndArithmeticAverage() { fail("not implemented"); }
    @Ignore(REASON_BOOTSTRAP) @Test public void testBootstrapWithCustomPricer() { fail("not implemented"); }
    @Ignore(REASON_BOOTSTRAP) @Test public void testBootstrapWithLookbackDays() { fail("not implemented"); }
    @Ignore(REASON_BOOTSTRAP) @Test public void testBootstrapWithLookbackDaysAndShift() { fail("not implemented"); }
    @Ignore(REASON_BOOTSTRAP) @Test public void testBootstrapWithLockoutDays() { fail("not implemented"); }
    @Ignore(REASON_BOOTSTRAP) @Test public void testBootstrapWithLockoutDaysAndShift() { fail("not implemented"); }
    @Ignore(REASON_PRICING) @Test public void testSeasonedSwaps() { fail("not implemented"); }
    @Ignore(REASON_BOOTSTRAP) @Test public void testBootstrapRegression() { fail("not implemented"); }
    @Ignore(REASON_BOOTSTRAP) @Test public void test131BootstrapRegression() { fail("not implemented"); }
    @Ignore(REASON_BOOTSTRAP) @Test public void testBootstrapWithDifferentCalendars() { fail("not implemented"); }
    @Ignore(REASON_PRICING) @Test public void testConstructorsAndNominals() { fail("not implemented"); }
    @Ignore(REASON_NOTIFY) @Test public void testNotifications() { fail("not implemented"); }
    @Ignore(REASON_MAKE_OIS) @Test public void testMakeOISDefaultSettlementDays() { fail("not implemented"); }
    @Ignore(REASON_MAKE_OIS) @Test public void testMakeOisEndOfMonthRegression2453() { fail("not implemented"); }
    @Ignore(REASON_MAKE_OIS) @Test public void testSettlementDaysEffectiveDateConflict() { fail("not implemented"); }
}
