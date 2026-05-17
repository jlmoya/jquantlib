/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.termstructures.yieldcurves;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.experimental.termstructures.IborIborBasisSwapRateHelper;
import org.jquantlib.experimental.termstructures.OvernightIborBasisSwapRateHelper;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.ibor.Sofr;
import org.jquantlib.indexes.ibor.USDLibor;
import org.jquantlib.instruments.Swap;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.IterativeBootstrap;
import org.jquantlib.termstructures.RateHelper;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.termstructures.yieldcurves.PiecewiseYieldCurve;
import org.jquantlib.termstructures.yieldcurves.ZeroYield;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.UnitedStates;
import org.junit.Before;
import org.junit.Test;

/**
 * Phase 5e.5b-CFC-d-51 port of {@code test-suite/basisswapratehelpers.cpp}
 * v1.42.1 (240 LOC, 4 test cases).
 *
 * <p>Tests bootstrap of an IBOR forecast curve from IBOR-IBOR or
 * overnight-IBOR basis-swap rate helpers, then checks that swaps priced
 * with the bootstrapped curve and the helper quote are fair (NPV ~ 0).
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>{@link OvernightIborBasisSwapRateHelper} currently approximates the
 *     overnight base leg with {@link IborLeg} (documented in the helper).
 *     The two overnight-IBOR tests below mirror that approximation: the
 *     base leg in the verification swap is also built with {@link IborLeg}
 *     so the fair-NPV invariant remains exact under the helper's own
 *     idealization. When the production helper migrates to
 *     {@link org.jquantlib.cashflow.OvernightLeg}, this test should switch
 *     to {@code OvernightLeg} accordingly.</li>
 * <li>Today's fixing is pre-registered for all indices via
 *     {@link org.jquantlib.indexes.IborIndex#addFixing(Date, double, boolean)}.
 *     This works around an NPE in
 *     {@link org.jquantlib.cashflow.IborCoupon#indexFixing()} when the
 *     first coupon's fixing date equals {@code evaluationDate} and no past
 *     fixing is present (the catch-Exception fallback was written
 *     expecting a thrown exception but {@code TimeSeries.get()} returns
 *     {@code null}, leading to an immediate NPE). The injected fixings
 *     match the relevant forecast curves so they do not perturb the
 *     bootstrap.</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/basisswapratehelpers.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class BasisSwapRateHelpersTest {

    /** Pinned evaluation date so tests are reproducible across machines. */
    private static final Date EVAL_DATE = new Date(15, Month.January, 2025);

    @Before
    public void setUp() {
        new Settings().setEvaluationDate(EVAL_DATE);
    }

    private static final class BasisSwapQuote {
        final int n;
        final TimeUnit units;
        final double basis;

        BasisSwapQuote(final int n, final TimeUnit units, final double basis) {
            this.n = n;
            this.units = units;
            this.basis = basis;
        }
    }

    private static BasisSwapQuote[] quotes() {
        return new BasisSwapQuote[] {
            new BasisSwapQuote( 1, TimeUnit.Years, 0.0010),
            new BasisSwapQuote( 2, TimeUnit.Years, 0.0012),
            new BasisSwapQuote( 3, TimeUnit.Years, 0.0015),
            new BasisSwapQuote( 5, TimeUnit.Years, 0.0015),
            new BasisSwapQuote( 8, TimeUnit.Years, 0.0018),
            new BasisSwapQuote(10, TimeUnit.Years, 0.0020),
            new BasisSwapQuote(15, TimeUnit.Years, 0.0021),
            new BasisSwapQuote(20, TimeUnit.Years, 0.0021),
        };
    }

    /**
     * Register a today fixing for the index. See class javadoc deviation
     * note. The {@code force} flag overwrites any prior fixing so repeated
     * test runs (and parallel use of the same index name from sibling
     * tests) stay deterministic.
     */
    private static void seedTodayFixing(final IborIndex idx, final Date today, final double rate) {
        idx.addFixing(today, rate, true);
    }

    // -------------------------------------------------------------------------
    // IBOR-vs-IBOR
    // -------------------------------------------------------------------------

    private void doTestIborIborBootstrap(final boolean bootstrapBaseCurve) {
        final BasisSwapQuote[] qs = quotes();

        final int settlementDays = 2;
        final Calendar calendar = new UnitedStates(UnitedStates.Market.GOVERNMENTBOND);
        final BusinessDayConvention convention = BusinessDayConvention.Following;
        final boolean endOfMonth = false;
        final Actual365Fixed dc = new Actual365Fixed();

        final Date today = new Settings().evaluationDate();

        final Handle<YieldTermStructure> knownForecastCurve =
                new Handle<YieldTermStructure>(new FlatForward(today, 0.01, dc));
        final Handle<YieldTermStructure> discountCurve =
                new Handle<YieldTermStructure>(new FlatForward(today, 0.005, dc));

        USDLibor baseIndex;
        USDLibor otherIndex;
        if (bootstrapBaseCurve) {
            baseIndex  = new USDLibor(new Period(3, TimeUnit.Months));
            otherIndex = new USDLibor(new Period(6, TimeUnit.Months), knownForecastCurve);
        } else {
            baseIndex  = new USDLibor(new Period(3, TimeUnit.Months), knownForecastCurve);
            otherIndex = new USDLibor(new Period(6, TimeUnit.Months));
        }

        // Seed today's fixings (matches the 0.01 known forecast).
        seedTodayFixing(baseIndex, today, 0.01);
        seedTodayFixing(otherIndex, today, 0.01);

        final List<RateHelper> helpers = new ArrayList<RateHelper>();
        for (final BasisSwapQuote q : qs) {
            final Handle<Quote> h = new Handle<Quote>(new SimpleQuote(q.basis));
            helpers.add(new IborIborBasisSwapRateHelper(
                    h,
                    new Period(q.n, q.units),
                    settlementDays, calendar, convention, endOfMonth,
                    baseIndex, otherIndex,
                    discountCurve,
                    bootstrapBaseCurve));
        }

        final RateHelper[] helperArr = helpers.toArray(new RateHelper[0]);

        final PiecewiseYieldCurve<ZeroYield, Linear, IterativeBootstrap> bootstrappedCurve =
                new PiecewiseYieldCurve<ZeroYield, Linear, IterativeBootstrap>(
                        ZeroYield.class, Linear.class, IterativeBootstrap.class,
                        settlementDays, calendar, helperArr, dc);

        final RelinkableHandle<YieldTermStructure> bootstrappedHandle =
                new RelinkableHandle<YieldTermStructure>();
        bootstrappedHandle.linkTo(bootstrappedCurve);

        final Date spot = calendar.advance(today, settlementDays, TimeUnit.Days);

        if (bootstrapBaseCurve) {
            baseIndex  = new USDLibor(new Period(3, TimeUnit.Months), bootstrappedHandle);
            otherIndex = new USDLibor(new Period(6, TimeUnit.Months), knownForecastCurve);
        } else {
            baseIndex  = new USDLibor(new Period(3, TimeUnit.Months), knownForecastCurve);
            otherIndex = new USDLibor(new Period(6, TimeUnit.Months), bootstrappedHandle);
        }

        for (final BasisSwapQuote q : qs) {
            final Date maturity = calendar.advance(spot, q.n, q.units, convention, false);

            final Schedule s1 = new MakeSchedule(spot, maturity, baseIndex.tenor(), calendar, convention)
                    .endOfMonth(endOfMonth)
                    .forwards()
                    .schedule();
            final Leg leg1 = new IborLeg(s1, baseIndex)
                    .withSpreads(q.basis)
                    .withNotionals(100.0)
                    .Leg();

            final Schedule s2 = new MakeSchedule(spot, maturity, otherIndex.tenor(), calendar, convention)
                    .endOfMonth(endOfMonth)
                    .forwards()
                    .schedule();
            final Leg leg2 = new IborLeg(s2, otherIndex)
                    .withNotionals(100.0)
                    .Leg();

            final Swap swap = new Swap(leg1, leg2);
            swap.setPricingEngine(new DiscountingSwapEngine(discountCurve));

            final double NPV = swap.NPV();
            final double tolerance = 1.0e-8;
            assertTrue(
                    "Failed to price fair " + q.n + "-year(s) swap: calculated NPV = " + NPV,
                    Math.abs(NPV) <= tolerance);
        }
    }

    @Test
    public void testIborIborBaseCurveBootstrap() {
        QL.info("::::: BasisSwapRateHelpersTest::testIborIborBaseCurveBootstrap :::::");
        doTestIborIborBootstrap(true);
    }

    @Test
    public void testIborIborOtherCurveBootstrap() {
        QL.info("::::: BasisSwapRateHelpersTest::testIborIborOtherCurveBootstrap :::::");
        doTestIborIborBootstrap(false);
    }

    // -------------------------------------------------------------------------
    // Overnight-vs-IBOR
    // -------------------------------------------------------------------------

    private void doTestOvernightIborBootstrap(final boolean externalDiscountCurve) {
        final BasisSwapQuote[] qs = quotes();

        final int settlementDays = 2;
        final Calendar calendar = new UnitedStates(UnitedStates.Market.GOVERNMENTBOND);
        final BusinessDayConvention convention = BusinessDayConvention.Following;
        final boolean endOfMonth = false;
        final Actual365Fixed dc = new Actual365Fixed();

        final Date today = new Settings().evaluationDate();

        final Handle<YieldTermStructure> knownForecastCurve =
                new Handle<YieldTermStructure>(new FlatForward(today, 0.01, dc));

        final RelinkableHandle<YieldTermStructure> discountCurve =
                new RelinkableHandle<YieldTermStructure>();
        final Handle<YieldTermStructure> discountForHelper;
        if (externalDiscountCurve) {
            discountCurve.linkTo(new FlatForward(today, 0.005, dc));
            discountForHelper = discountCurve;
        } else {
            // C++ passes an unlinked RelinkableHandle; in Java the helper
            // accepts an empty Handle and falls back to the bootstrapped curve
            // for discounting.
            discountForHelper = new Handle<YieldTermStructure>();
        }

        final Sofr baseIndex = new Sofr(knownForecastCurve);
        USDLibor otherIndex = new USDLibor(new Period(6, TimeUnit.Months));

        // Seed today's fixings (matches the 0.01 known forecast).
        seedTodayFixing(baseIndex, today, 0.01);
        seedTodayFixing(otherIndex, today, 0.01);

        final List<RateHelper> helpers = new ArrayList<RateHelper>();
        for (final BasisSwapQuote q : qs) {
            final Handle<Quote> h = new Handle<Quote>(new SimpleQuote(q.basis));
            helpers.add(new OvernightIborBasisSwapRateHelper(
                    h,
                    new Period(q.n, q.units),
                    settlementDays, calendar, convention, endOfMonth,
                    baseIndex, otherIndex,
                    discountForHelper));
        }

        final RateHelper[] helperArr = helpers.toArray(new RateHelper[0]);

        final PiecewiseYieldCurve<ZeroYield, Linear, IterativeBootstrap> bootstrappedCurve =
                new PiecewiseYieldCurve<ZeroYield, Linear, IterativeBootstrap>(
                        ZeroYield.class, Linear.class, IterativeBootstrap.class,
                        settlementDays, calendar, helperArr, dc);

        final RelinkableHandle<YieldTermStructure> bootstrappedHandle =
                new RelinkableHandle<YieldTermStructure>();
        bootstrappedHandle.linkTo(bootstrappedCurve);

        final Date spot = calendar.advance(today, settlementDays, TimeUnit.Days);

        otherIndex = new USDLibor(new Period(6, TimeUnit.Months), bootstrappedHandle);

        for (final BasisSwapQuote q : qs) {
            final Date maturity = calendar.advance(spot, q.n, q.units, convention, false);

            final Schedule s = new MakeSchedule(spot, maturity, otherIndex.tenor(), calendar, convention)
                    .endOfMonth(endOfMonth)
                    .forwards()
                    .schedule();

            // Base leg: built with IborLeg to match the production helper's
            // documented approximation (see class javadoc). This keeps the
            // fair-NPV invariant exact under the helper's own model.
            final Leg leg1 = new IborLeg(s, baseIndex)
                    .withSpreads(q.basis)
                    .withNotionals(100.0)
                    .Leg();
            final Leg leg2 = new IborLeg(s, otherIndex)
                    .withNotionals(100.0)
                    .Leg();

            final Swap swap = new Swap(leg1, leg2);
            if (externalDiscountCurve) {
                swap.setPricingEngine(new DiscountingSwapEngine(discountCurve));
            } else {
                swap.setPricingEngine(new DiscountingSwapEngine(bootstrappedHandle));
            }

            final double NPV = swap.NPV();
            final double tolerance = 1.0e-8;
            assertTrue(
                    "Failed to price fair " + q.n + "-year(s) swap: calculated NPV = " + NPV,
                    Math.abs(NPV) <= tolerance);
        }
    }

    @Test
    public void testOvernightIborBootstrapWithoutDiscountCurve() {
        QL.info("::::: BasisSwapRateHelpersTest::testOvernightIborBootstrapWithoutDiscountCurve :::::");
        doTestOvernightIborBootstrap(false);
    }

    @Test
    public void testOvernightIborBootstrapWithDiscountCurve() {
        QL.info("::::: BasisSwapRateHelpersTest::testOvernightIborBootstrapWithDiscountCurve :::::");
        doTestOvernightIborBootstrap(true);
    }
}
