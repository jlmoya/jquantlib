/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.ibor.Eonia;
import org.jquantlib.instruments.MakeOIS;
import org.jquantlib.instruments.OvernightIndexedSwap;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase Body-Fill-5 partial port of {@code test-suite/overnightindexedswap.cpp}
 * v1.42.1 (1,098 LOC, 21 cases).
 *
 * <p>Exercises the {@code OvernightIndexedSwap} (OIS) instrument and the
 * {@code MakeOIS} factory.
 *
 * <p><strong>Body-fills (Phase Body-Fill-5):</strong>
 * <ul>
 *   <li>{@link #testFairRate()} — fair fixed-rate parity (telescopic vs
 *       non-telescopic) + zero-NPV recalculation.
 *   <li>{@link #testFairSpread()} — fair floating-spread parity + zero-NPV
 *       recalculation.
 * </ul>
 *
 * <p><strong>Carry-forward to Phase 5d.5</strong>: 19 remaining cases need
 * either an Estr index port (we substitute Eonia for the body-filled cases),
 * lookback / lockout / observation-shift / arithmetic-average MakeOIS
 * variants, OIS bootstrap rate-helper coverage, or Phase 5d.5 testCachedValue
 * (which depends on a regenerated cached NPV from C++ v1.42.1 via a probe).
 *
 * <p>Source: {@code test-suite/overnightindexedswap.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class OvernightIndexedSwapTest {

    public OvernightIndexedSwapTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    private static final String REASON_PRICING =
            "Phase 5d.5 — additional pricing cases need probe-cached "
          + "expected NPV against C++ Estr (Java has Eonia, sub-millisecond "
          + "calendar diff vs Estr means cachedValue must be regenerated)";

    private static final String REASON_BOOTSTRAP =
            "Phase 5d.5 — requires OISRateHelper bootstrap test framework "
          + "(present in production but no test-suite port yet)";

    private static final String REASON_MAKE_OIS =
            "Phase 5d.5 — requires MakeOIS additional default-settlement-days "
          + "+ end-of-month regression #2453 + settlement/effective conflict "
          + "guard-rail wiring";

    private static final String REASON_NOTIFY =
            "Phase 5d.5 — requires OIS observable wiring + cached-value "
          + "regression coverage";

    private static final String REASON_LOOKBACK =
            "Phase 5d.5 — MakeOIS withLookbackDays / withLockoutDays / "
          + "withObservationShift not yet implemented in Java MakeOIS MVP";

    private static final String REASON_ARITHMETIC =
            "Phase 5d.5 — arithmetic-average bootstrap requires specialized "
          + "OvernightIndexedCouponPricer adaption";

    /**
     * Fixture mirroring C++ {@code CommonVars}.
     *
     * <p>Substitutes {@code Eonia} for the (yet-unported) {@code Estr} —
     * both are EUR-area overnight indices using TARGET calendar / Actual360
     * daycount / 0 settlement days, so the parity invariants tested here
     * (fairRate / fairSpread / NPV-zero-on-implied) are unchanged.
     */
    private static final class CommonVars {
        final Date today;
        final Date settlement;
        final double nominal;
        final Calendar calendar;
        final int settlementDays;
        final Eonia overnightIndex;
        final RelinkableHandle<YieldTermStructure> termStructure;

        CommonVars() {
            settlementDays = 2;
            nominal = 100.0;
            termStructure = new RelinkableHandle<YieldTermStructure>();
            overnightIndex = new Eonia(termStructure);
            calendar = overnightIndex.fixingCalendar();
            today = new Date(5, Month.February, 2009);
            new Settings().setEvaluationDate(today);
            settlement = calendar.advance(today,
                    new Period(settlementDays, TimeUnit.Days),
                    BusinessDayConvention.Following);
            termStructure.linkTo(new FlatForward(today, 0.05, new Actual365Fixed()));
        }

        OvernightIndexedSwap makeSwap(final Period length, final double fixedRate,
                                       final double spread,
                                       final boolean telescopicValueDates) {
            return new MakeOIS(length, overnightIndex, fixedRate,
                    new Period(0, TimeUnit.Days))
                    .withEffectiveDate(settlement)
                    .withOvernightLegSpread(spread)
                    .withNominal(nominal)
                    .withPaymentLag(0)
                    .withDiscountingTermStructure(termStructure)
                    .withTelescopicValueDates(telescopicValueDates)
                    .value();
        }
    }

    /**
     * Port of C++ {@code overnightindexedswap.cpp::testFairRate}.
     *
     * <p>Verifies that:
     * <ul>
     *   <li>The fair fixed rate computed under telescopic and
     *       non-telescopic value-date conventions agree to 1e-10;
     *   <li>An OIS rebuilt at its implied fair rate has NPV ~= 0
     *       (1e-10 tolerance) under both conventions.
     * </ul>
     */
    @Test
    public void testFairRate() {
        final CommonVars vars = new CommonVars();

        final Period[] lengths = {
                new Period(1, TimeUnit.Years), new Period(2, TimeUnit.Years),
                new Period(5, TimeUnit.Years), new Period(10, TimeUnit.Years),
                new Period(20, TimeUnit.Years)
        };
        final double[] spreads = { -0.001, -0.01, 0.0, 0.01, 0.001 };

        for (final Period length : lengths) {
            for (final double spread : spreads) {
                OvernightIndexedSwap swap  = vars.makeSwap(length, 0.0, spread, false);
                final OvernightIndexedSwap swap2 = vars.makeSwap(length, 0.0, spread, true);
                final double fairRate1 = swap.fairRate();
                final double fairRate2 = swap2.fairRate();
                if (Math.abs(fairRate1 - fairRate2) > 1.0e-10) {
                    fail("fair rates are different:"
                            + "\n    length: " + length
                            + "\n    floating spread: " + spread
                            + "\n    fair rate (non telescopic value dates): " + fairRate1
                            + "\n    fair rate (telescopic value dates)    : " + fairRate2);
                }

                swap = vars.makeSwap(length, fairRate1, spread, false);
                if (Math.abs(swap.NPV()) > 1.0e-10) {
                    fail("recalculating with implied rate (non telescopic value dates):"
                            + "\n    length: " + length
                            + "\n    floating spread: " + spread
                            + "\n    swap value: " + swap.NPV());
                }

                swap = vars.makeSwap(length, fairRate1, spread, true);
                if (Math.abs(swap.NPV()) > 1.0e-10) {
                    fail("recalculating with implied rate (telescopic value dates):"
                            + "\n    length: " + length
                            + "\n    floating spread: " + spread
                            + "\n    swap value: " + swap.NPV());
                }
            }
        }
        assertTrue("OIS fairRate parity test passed", true);
    }

    /**
     * Port of C++ {@code overnightindexedswap.cpp::testFairSpread}.
     *
     * <p>Verifies that:
     * <ul>
     *   <li>The fair floating spread computed under telescopic and
     *       non-telescopic value-date conventions agree to 1e-10;
     *   <li>An OIS rebuilt at its implied fair spread has NPV ~= 0
     *       (1e-10 tolerance) under both conventions.
     * </ul>
     */
    @Test
    public void testFairSpread() {
        final CommonVars vars = new CommonVars();

        final Period[] lengths = {
                new Period(1, TimeUnit.Years), new Period(2, TimeUnit.Years),
                new Period(5, TimeUnit.Years), new Period(10, TimeUnit.Years),
                new Period(20, TimeUnit.Years)
        };
        final double[] rates = { 0.04, 0.05, 0.06, 0.07 };

        for (final Period length : lengths) {
            for (final double j : rates) {
                OvernightIndexedSwap swap  = vars.makeSwap(length, j, 0.0, false);
                final OvernightIndexedSwap swap2 = vars.makeSwap(length, j, 0.0, true);
                final double fairSpread1 = swap.fairSpread();
                final double fairSpread2 = swap2.fairSpread();
                if (Math.abs(fairSpread1 - fairSpread2) > 1.0e-10) {
                    fail("fair spreads are different:"
                            + "\n    length: " + length
                            + "\n    fixed rate: " + j
                            + "\n    fair spread (non telescopic value dates): " + fairSpread1
                            + "\n    fair spread (telescopic value dates)    : " + fairSpread2);
                }

                swap = vars.makeSwap(length, j, fairSpread1, false);
                if (Math.abs(swap.NPV()) > 1.0e-10) {
                    fail("recalculating with implied spread (non telescopic value dates):"
                            + "\n    length: " + length
                            + "\n    fixed rate: " + j
                            + "\n    fair spread: " + fairSpread1
                            + "\n    swap value: " + swap.NPV());
                }

                swap = vars.makeSwap(length, j, fairSpread1, true);
                if (Math.abs(swap.NPV()) > 1.0e-10) {
                    fail("recalculating with implied spread (telescopic value dates):"
                            + "\n    length: " + length
                            + "\n    fixed rate: " + j
                            + "\n    fair spread: " + fairSpread1
                            + "\n    swap value: " + swap.NPV());
                }
            }
        }
        assertTrue("OIS fairSpread parity test passed", true);
    }

    @Ignore(REASON_PRICING) @Test public void testCachedValue() { fail("not implemented"); }
    @Ignore(REASON_BOOTSTRAP) @Test public void testBaseBootstrap() { fail("not implemented"); }
    @Ignore(REASON_ARITHMETIC) @Test public void testBootstrapWithArithmeticAverage() { fail("not implemented"); }
    @Ignore(REASON_BOOTSTRAP) @Test public void testBootstrapWithTelescopicDates() { fail("not implemented"); }
    @Ignore(REASON_ARITHMETIC) @Test public void testBootstrapWithTelescopicDatesAndArithmeticAverage() { fail("not implemented"); }
    @Ignore(REASON_BOOTSTRAP) @Test public void testBootstrapWithCustomPricer() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testBootstrapWithLookbackDays() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testBootstrapWithLookbackDaysAndShift() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testBootstrapWithLockoutDays() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testBootstrapWithLockoutDaysAndShift() { fail("not implemented"); }
    @Ignore(REASON_PRICING) @Test public void testSeasonedSwaps() { fail("not implemented"); }
    @Ignore(REASON_BOOTSTRAP) @Test public void testBootstrapRegression() { fail("not implemented"); }
    @Ignore(REASON_BOOTSTRAP) @Test public void test131BootstrapRegression() { fail("not implemented"); }
    @Ignore(REASON_BOOTSTRAP) @Test public void testBootstrapWithDifferentCalendars() { fail("not implemented"); }
    @Ignore(REASON_PRICING) @Test public void testConstructorsAndNominals() { fail("not implemented"); }
    @Ignore(REASON_NOTIFY) @Test public void testNotifications() { fail("not implemented"); }
    @Ignore(REASON_MAKE_OIS) @Test public void testMakeOISDefaultSettlementDays() { fail("not implemented"); }
    @Ignore(REASON_MAKE_OIS) @Test public void testMakeOisEndOfMonthRegression2453() { fail("not implemented"); }
    @Ignore(REASON_MAKE_OIS) @Test public void testSettlementDaysEffectiveDateConflict() { fail("not implemented"); }

    // unused-suppress to keep references to constants (used as labels)
    private static final String UNUSED1 = REASON_PRICING + REASON_BOOTSTRAP + REASON_MAKE_OIS;
    private static final String UNUSED2 = REASON_NOTIFY + REASON_LOOKBACK + REASON_ARITHMETIC;
    private static final String[] UNUSED = { UNUSED1, UNUSED2 };

    static {
        // satisfy unused-array warnings — the constant fields are visible
        // through @Ignore-annotated methods, but javac may still warn.
        if (UNUSED.length == 0) {
            throw new AssertionError();
        }
    }
}
