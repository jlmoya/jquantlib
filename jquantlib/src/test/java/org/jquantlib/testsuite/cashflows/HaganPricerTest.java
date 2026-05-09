/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Smoke + self-validating tests for HaganPricer concrete subclasses
 AnalyticHaganPricer and NumericHaganPricer (Phase 5e.6 commit 2).
*/
package org.jquantlib.testsuite.cashflows;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.AnalyticHaganPricer;
import org.jquantlib.cashflow.CmsCoupon;
import org.jquantlib.cashflow.GFunctionFactory;
import org.jquantlib.cashflow.HaganPricer;
import org.jquantlib.cashflow.NumericHaganPricer;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.EuriborSwapIsdaFixA;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.swaption.ConstantSwaptionVolatility;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Smoke + self-validation tests for AnalyticHaganPricer and
 * NumericHaganPricer.
 *
 * <p>Phase 5e.6 ports the pricer machinery. Probe-driven cross-validation
 * against the C++ swapletPrice/capletPrice/floorletPrice values is
 * deferred to Phase 5e.6b once a stable EuriborSwapIsdaFixA / CMS-rig is
 * shared between probe and Java test. In the meantime these tests
 * exercise:
 *
 * <ol>
 *   <li>Instantiation + initialize() against a CMS coupon under a
 *       constant-vol swaption surface (smoke -- proves the
 *       SwapIndex.swapIndex_ wiring + initialize() pipeline works).</li>
 *   <li>swapletPrice/swapletRate finite + non-NaN (smoke).</li>
 *   <li>Analytic vs Numeric ATM swaplet-rate agreement at LOOSE
 *       (1e-6) -- the tightest self-test we can construct without a
 *       reference. The two pricers solve the same Hagan integral; one
 *       closed-form, the other Gauss-Kronrod -- they should match to
 *       integration precision.</li>
 *   <li>capletPrice / floorletPrice non-negative.</li>
 * </ol>
 */
public class HaganPricerTest {

    public HaganPricerTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private CmsCoupon coupon;
    private Handle<SwaptionVolatilityStructure> swaptionVol;
    private Handle<Quote> meanReversion;

    private void setup() {
        final Date evalDate = new Date(28, Month.January, 2010);
        new Settings().setEvaluationDate(evalDate);

        final Calendar calendar = new Target();
        final DayCounter dc = new Actual360();

        final Handle<YieldTermStructure> rateCurve =
                new Handle<YieldTermStructure>(
                        new FlatForward(evalDate, 0.04, dc, Compounding.Continuous));

        final SwapIndex swapIndex =
                new EuriborSwapIsdaFixA(new Period(10, TimeUnit.Years), rateCurve);

        final Date startDate = calendar.advance(evalDate, new Period(2, TimeUnit.Years),
                BusinessDayConvention.Following);
        final Date endDate = calendar.advance(startDate, new Period(6, TimeUnit.Months),
                BusinessDayConvention.Following);
        final Date paymentDate = endDate;

        coupon = new CmsCoupon(paymentDate, 100000.0,
                startDate, endDate,
                2,                       // fixingDays
                swapIndex,
                1.0,                     // gearing
                0.0,                     // spread
                new Date(), new Date(),
                dc,
                false                    // isInArrears
        );

        // Constant-vol shifted-lognormal swaption surface at 20%.
        swaptionVol = new Handle<SwaptionVolatilityStructure>(
                new ConstantSwaptionVolatility(
                        evalDate, calendar, BusinessDayConvention.ModifiedFollowing,
                        0.20, dc));
        meanReversion = new Handle<Quote>(new SimpleQuote(0.01));
    }

    @Test
    public void analyticHaganPricer_initializeAndPrice_smoke() {
        setup();
        final HaganPricer pricer = new AnalyticHaganPricer(
                swaptionVol, GFunctionFactory.YieldCurveModel.Standard, meanReversion);
        coupon.setPricer(pricer);
        pricer.initialize(coupon);

        final double sp = pricer.swapletPrice();
        final double sr = pricer.swapletRate();
        assertFinite("AnalyticHaganPricer.swapletPrice", sp);
        assertFinite("AnalyticHaganPricer.swapletRate", sr);

        // capletPrice + floorletPrice non-negative for in-range strikes.
        for (final double k : new double[] {0.02, 0.04, 0.06}) {
            final double cp = pricer.capletPrice(k);
            final double fp = pricer.floorletPrice(k);
            assertFinite("AnalyticHaganPricer.capletPrice(" + k + ")", cp);
            assertFinite("AnalyticHaganPricer.floorletPrice(" + k + ")", fp);
            assertTrue("AnalyticHaganPricer.capletPrice(" + k + ") < 0",  cp >= 0.0);
            assertTrue("AnalyticHaganPricer.floorletPrice(" + k + ") < 0", fp >= 0.0);
        }
    }

    @Test
    public void numericHaganPricer_initializeAndPrice_smoke() {
        setup();
        final HaganPricer pricer = new NumericHaganPricer(
                swaptionVol, GFunctionFactory.YieldCurveModel.Standard, meanReversion);
        coupon.setPricer(pricer);
        pricer.initialize(coupon);

        final double sp = pricer.swapletPrice();
        final double sr = pricer.swapletRate();
        assertFinite("NumericHaganPricer.swapletPrice", sp);
        assertFinite("NumericHaganPricer.swapletRate", sr);

        for (final double k : new double[] {0.02, 0.04, 0.06}) {
            final double cp = pricer.capletPrice(k);
            final double fp = pricer.floorletPrice(k);
            assertFinite("NumericHaganPricer.capletPrice(" + k + ")", cp);
            assertFinite("NumericHaganPricer.floorletPrice(" + k + ")", fp);
            assertTrue("NumericHaganPricer.capletPrice(" + k + ") < 0",  cp >= 0.0);
            assertTrue("NumericHaganPricer.floorletPrice(" + k + ") < 0", fp >= 0.0);
        }
    }

    /**
     * Self-validation: AnalyticHaganPricer.swapletRate vs
     * NumericHaganPricer.swapletRate should agree at LOOSE
     * (Gauss-Kronrod precision_ = 1e-6). Both implement Hagan's static
     * replication of the same CMS coupon; analytic uses the closed form,
     * numeric integrates. They must converge.
     */
    @Test
    public void analyticVsNumericHaganPricer_swapletRate_atmAgrees() {
        setup();

        final HaganPricer analytic = new AnalyticHaganPricer(
                swaptionVol, GFunctionFactory.YieldCurveModel.Standard, meanReversion);
        coupon.setPricer(analytic);
        analytic.initialize(coupon);
        final double analyticRate = analytic.swapletRate();

        // Re-set the pricer to the numeric variant.
        final HaganPricer numeric = new NumericHaganPricer(
                swaptionVol, GFunctionFactory.YieldCurveModel.Standard, meanReversion);
        coupon.setPricer(numeric);
        numeric.initialize(coupon);
        final double numericRate = numeric.swapletRate();

        // The two pricers solve the same Hagan integral. NumericHaganPricer
        // uses GaussKronrodNonAdaptive with precision_ = 1e-6, so disagreement
        // up to ~1e-5 (a few times the integrator absolute tol) is expected.
        // Tighter cross-validation against C++ values lives in Phase 5e.6b.
        assertFinite("analyticRate", analyticRate);
        assertFinite("numericRate", numericRate);
        if (!Tolerance.within(numericRate, analyticRate, 1.0e-5,
                "GaussKronrodNonAdaptive precision_ = 1e-6 -- analytic vs numeric integration error")) {
            fail("AnalyticHagan vs NumericHagan swaplet-rate disagree: "
                    + "analytic=" + analyticRate
                    + " numeric=" + numericRate
                    + " diff=" + Math.abs(analyticRate - numericRate));
        }
    }

    private static void assertFinite(final String tag, final double v) {
        assertFalse(tag + " is NaN", Double.isNaN(v));
        assertFalse(tag + " is +/-Inf", Double.isInfinite(v));
    }
}
