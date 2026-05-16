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
import org.jquantlib.indexes.Euribor3M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.ibor.Eonia;
import org.jquantlib.instruments.MakeOIS;
import org.jquantlib.instruments.OvernightIndexedSwap;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Bootstrap;
import org.jquantlib.termstructures.IterativeBootstrap;
import org.jquantlib.termstructures.RateHelper;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.DepositRateHelper;
import org.jquantlib.termstructures.yieldcurves.Discount;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.termstructures.yieldcurves.OISRateHelper;
import org.jquantlib.termstructures.yieldcurves.PiecewiseYieldCurve;
import org.jquantlib.termstructures.yieldcurves.Traits;
import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.math.interpolations.factories.LogLinear;
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
            return makeSwap(length, fixedRate, spread, telescopicValueDates,
                            new Date(), 0, RateAveraging.Type.Compound);
        }

        /**
         * Full-fidelity port of C++ {@code CommonVars::makeSwap}
         * (overnightindexedswap.cpp:150-166) — supports custom payment lag,
         * effective date override, and averaging method for bootstrap-check
         * swaps where the helper used {@code paymentLag = 2}.
         */
        OvernightIndexedSwap makeSwap(final Period length, final double fixedRate,
                                       final double spread,
                                       final boolean telescopicValueDates,
                                       final Date effectiveDate,
                                       final int paymentLag,
                                       final RateAveraging.Type averagingMethod) {
            return new MakeOIS(length, overnightIndex, fixedRate,
                    new Period(0, TimeUnit.Days))
                    .withEffectiveDate(effectiveDate.isNull() ? settlement : effectiveDate)
                    .withOvernightLegSpread(spread)
                    .withNominal(nominal)
                    .withPaymentLag(paymentLag)
                    .withDiscountingTermStructure(termStructure)
                    .withTelescopicValueDates(telescopicValueDates)
                    .withAveragingMethod(averagingMethod)
                    .value();
        }
    }

    /**
     * Deposit data for bootstrap helpers — first two entries (1d, 1d) are
     * used as ON/TN deposits before the OIS helpers take over.
     * <p>Mirrors C++ {@code depositData} (overnightindexedswap.cpp:80-91).
     */
    private static final class Datum {
        final int settlementDays;
        final int n;
        final TimeUnit unit;
        final double rate;
        Datum(final int settlementDays, final int n, final TimeUnit unit, final double rate) {
            this.settlementDays = settlementDays;
            this.n = n;
            this.unit = unit;
            this.rate = rate;
        }
    }

    private static final Datum[] DEPOSIT_DATA = {
            new Datum(0, 1, TimeUnit.Days, 1.10),
            new Datum(1, 1, TimeUnit.Days, 1.10),
    };

    /**
     * Estr (EUR overnight) swap quotes, used by the C++ bootstrap test.
     * <p>Mirrors C++ {@code estrSwapData} (overnightindexedswap.cpp:93-127).
     * Java uses Eonia in place of Estr (both EUR overnight, TARGET / Act360 /
     * 0 settlement days — bootstrap-then-recompute parity is unchanged).
     */
    private static final Datum[] ESTR_SWAP_DATA = {
            new Datum(2,  1, TimeUnit.Weeks, 1.245),
            new Datum(2,  2, TimeUnit.Weeks, 1.269),
            new Datum(2,  3, TimeUnit.Weeks, 1.277),
            new Datum(2,  1, TimeUnit.Months, 1.281),
            new Datum(2,  2, TimeUnit.Months, 1.18),
            new Datum(2,  3, TimeUnit.Months, 1.143),
            new Datum(2,  4, TimeUnit.Months, 1.125),
            new Datum(2,  5, TimeUnit.Months, 1.116),
            new Datum(2,  6, TimeUnit.Months, 1.111),
            new Datum(2,  7, TimeUnit.Months, 1.109),
            new Datum(2,  8, TimeUnit.Months, 1.111),
            new Datum(2,  9, TimeUnit.Months, 1.117),
            new Datum(2, 10, TimeUnit.Months, 1.129),
            new Datum(2, 11, TimeUnit.Months, 1.141),
            new Datum(2, 12, TimeUnit.Months, 1.153),
            new Datum(2, 15, TimeUnit.Months, 1.218),
            new Datum(2, 18, TimeUnit.Months, 1.308),
            new Datum(2, 21, TimeUnit.Months, 1.407),
            new Datum(2,  2, TimeUnit.Years,  1.510),
            new Datum(2,  3, TimeUnit.Years,  1.916),
            new Datum(2,  4, TimeUnit.Years,  2.254),
            new Datum(2,  5, TimeUnit.Years,  2.523),
            new Datum(2,  6, TimeUnit.Years,  2.746),
            new Datum(2,  7, TimeUnit.Years,  2.934),
            new Datum(2,  8, TimeUnit.Years,  3.092),
            new Datum(2,  9, TimeUnit.Years,  3.231),
            new Datum(2, 10, TimeUnit.Years,  3.380),
            new Datum(2, 11, TimeUnit.Years,  3.457),
            new Datum(2, 12, TimeUnit.Years,  3.544),
            new Datum(2, 15, TimeUnit.Years,  3.702),
            new Datum(2, 20, TimeUnit.Years,  3.703),
            new Datum(2, 25, TimeUnit.Years,  3.541),
            new Datum(2, 30, TimeUnit.Years,  3.369),
    };

    /**
     * Build the bootstrap curve from the supplied helpers and verify that
     * recomputing each input swap's fair rate against that curve reproduces
     * the input quote within tolerance.
     *
     * <p>Port of C++ {@code testBootstrap} (overnightindexedswap.cpp:209-281).
     */
    private void runBootstrap(final boolean telescopicValueDates,
                              final RateAveraging.Type averagingMethod,
                              final double tolerance) {
        final CommonVars vars = new CommonVars();

        final int paymentLag = 2;
        final Handle<Quote> spread = new Handle<Quote>(new SimpleQuote(0.0));

        final IborIndex euribor3m = new Euribor3M();
        final Eonia estr = new Eonia();

        final java.util.List<RateHelper> helpers = new java.util.ArrayList<RateHelper>();

        for (final Datum d : DEPOSIT_DATA) {
            final double rate = 0.01 * d.rate;
            final Handle<Quote> quote = new Handle<Quote>(new SimpleQuote(rate));
            final Period term = new Period(d.n, d.unit);
            final RateHelper helper = new DepositRateHelper(
                    quote, term, d.settlementDays,
                    euribor3m.fixingCalendar(),
                    euribor3m.businessDayConvention(),
                    euribor3m.endOfMonth(),
                    euribor3m.dayCounter());
            // C++ only keeps deposits up to 2 days; both DEPOSIT_DATA entries qualify.
            if (term.le(new Period(2, TimeUnit.Days))) {
                helpers.add(helper);
            }
        }

        for (final Datum d : ESTR_SWAP_DATA) {
            final double rate = 0.01 * d.rate;
            final Handle<Quote> quote = new Handle<Quote>(new SimpleQuote(rate));
            final Period term = new Period(d.n, d.unit);
            final RateHelper helper = new OISRateHelper(
                    d.settlementDays,
                    term,
                    quote,
                    estr,
                    new Handle<YieldTermStructure>(),
                    telescopicValueDates,
                    paymentLag,
                    BusinessDayConvention.Following,
                    org.jquantlib.time.Frequency.Annual,
                    null /* paymentCalendar -> default to index fixing calendar */,
                    averagingMethod);
            helpers.add(helper);
        }

        final RateHelper[] helperArray = helpers.toArray(new RateHelper[0]);
        final PiecewiseYieldCurve<Discount, LogLinear, IterativeBootstrap> estrTS =
                new PiecewiseYieldCurve<Discount, LogLinear, IterativeBootstrap>(
                        Discount.class, LogLinear.class, IterativeBootstrap.class,
                        vars.today, helperArray, new Actual365Fixed());

        vars.termStructure.linkTo(estrTS);

        for (final Datum d : ESTR_SWAP_DATA) {
            final double expected = d.rate / 100.0;
            final Period term = new Period(d.n, d.unit);
            // Check swap is built non-telescopic regardless of the bootstrap
            // telescopic flag (C++ overnightindexedswap.cpp:267-269).
            final OvernightIndexedSwap swap = vars.makeSwap(
                    term, 0.0, 0.0, false, new Date(), paymentLag, averagingMethod);
            final double calculated = swap.fairRate();
            final double error = Math.abs(expected - calculated);
            if (error > tolerance) {
                fail("curve inconsistency:"
                        + "\n swap length:     " + term
                        + "\n quoted rate:     " + expected
                        + "\n calculated rate: " + calculated
                        + "\n error:           " + error
                        + "\n tolerance:       " + tolerance);
            }
        }
        assertTrue("OIS bootstrap consistency check passed", true);
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

    /**
     * Port of C++ {@code overnightindexedswap.cpp::testBaseBootstrap}.
     * <p>Verifies that a {@link PiecewiseYieldCurve} bootstrapped from
     * 2 deposit helpers + 33 {@link OISRateHelper}s (paymentLag = 2,
     * compound averaging, non-telescopic value dates) reprices each
     * input swap's fair rate to within 1e-8 of the quoted rate.
     */
    @Test
    public void testBaseBootstrap() {
        runBootstrap(false, RateAveraging.Type.Compound, 1.0e-8);
    }

    @Ignore(REASON_ARITHMETIC) @Test public void testBootstrapWithArithmeticAverage() { fail("not implemented"); }

    /**
     * Port of C++ {@code overnightindexedswap.cpp::testBootstrapWithTelescopicDates}.
     * <p>Same as {@link #testBaseBootstrap} but bootstrap helpers use
     * telescopic value dates; the check-swap remains non-telescopic so
     * the test verifies that telescopic and full-schedule pricing agree.
     */
    @Test
    public void testBootstrapWithTelescopicDates() {
        runBootstrap(true, RateAveraging.Type.Compound, 1.0e-8);
    }

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
