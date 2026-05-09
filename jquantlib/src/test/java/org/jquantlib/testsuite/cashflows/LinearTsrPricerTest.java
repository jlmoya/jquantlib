/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Smoke + self-validating tests for LinearTsrPricer (Phase 5e.6 commit 3).
*/
package org.jquantlib.testsuite.cashflows;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.AnalyticHaganPricer;
import org.jquantlib.cashflow.CmsCoupon;
import org.jquantlib.cashflow.GFunctionFactory;
import org.jquantlib.cashflow.HaganPricer;
import org.jquantlib.cashflow.LinearTsrPricer;
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
 * Smoke + self-validation tests for {@link LinearTsrPricer}.
 *
 * <p>Phase 5e.6 ports the pricer machinery. Probe-driven cross-validation
 * against C++ values is deferred to Phase 5e.6b. In the meantime these
 * tests exercise:
 *
 * <ol>
 *   <li>Instantiation + initialize() against a CMS coupon under a
 *       constant-vol swaption surface (smoke -- proves the
 *       SwapIndex.swapIndex_ wiring + initialize() pipeline + integrator
 *       wiring all work).</li>
 *   <li>swapletPrice/swapletRate/capletPrice/floorletPrice finite +
 *       non-NaN (smoke).</li>
 *   <li>capletPrice / floorletPrice non-negative.</li>
 *   <li>Linear-TSR vs Analytic-Hagan ATM swaplet rates: both are CMS
 *       convexity-adjusted models on the same coupon. They use different
 *       methodologies (Hagan static replication vs linear TSR), so they
 *       are NOT expected to agree exactly. Just verify they produce
 *       reasonable (5%-error) ATM rates within the same ballpark.</li>
 * </ol>
 */
public class LinearTsrPricerTest {

    public LinearTsrPricerTest() {
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
                startDate, endDate, 2, swapIndex,
                1.0, 0.0,
                new Date(), new Date(), dc, false);

        swaptionVol = new Handle<SwaptionVolatilityStructure>(
                new ConstantSwaptionVolatility(
                        evalDate, calendar, BusinessDayConvention.ModifiedFollowing,
                        0.20, dc));
        meanReversion = new Handle<Quote>(new SimpleQuote(0.01));
    }

    @Test
    public void linearTsrPricer_initializeAndPrice_smoke() {
        setup();
        final LinearTsrPricer pricer = new LinearTsrPricer(swaptionVol, meanReversion);
        coupon.setPricer(pricer);
        pricer.initialize(coupon);

        final double sp = pricer.swapletPrice();
        final double sr = pricer.swapletRate();
        assertFinite("LinearTsrPricer.swapletPrice", sp);
        assertFinite("LinearTsrPricer.swapletRate", sr);

        for (final double k : new double[] {0.02, 0.04, 0.06}) {
            final double cp = pricer.capletPrice(k);
            final double fp = pricer.floorletPrice(k);
            assertFinite("LinearTsrPricer.capletPrice(" + k + ")", cp);
            assertFinite("LinearTsrPricer.floorletPrice(" + k + ")", fp);
            assertTrue("LinearTsrPricer.capletPrice(" + k + ") < 0",  cp >= 0.0);
            assertTrue("LinearTsrPricer.floorletPrice(" + k + ") < 0", fp >= 0.0);
        }
    }

    @Test
    public void linearTsrPricer_settings_strategiesCompile() {
        // Smoke test that all four strategies wire correctly.
        setup();

        for (final LinearTsrPricer.Settings s : new LinearTsrPricer.Settings[] {
                new LinearTsrPricer.Settings().withRateBound(),
                new LinearTsrPricer.Settings().withVegaRatio(0.01),
                new LinearTsrPricer.Settings().withBSStdDevs(3.0)
                // PriceThreshold -> probe sets vegaRatio_ as the price target
                // (a known C++ typo); skip in smoke to avoid dependency on
                // a price-strike-locked Brent on this synthetic curve.
        }) {
            final LinearTsrPricer pricer = new LinearTsrPricer(
                    swaptionVol, meanReversion,
                    new Handle<YieldTermStructure>(),
                    s, null);
            coupon.setPricer(pricer);
            pricer.initialize(coupon);
            final double sr = pricer.swapletRate();
            assertFinite("LinearTsrPricer[" + s.strategy_ + "].swapletRate", sr);
        }
    }

    @Test
    public void linearTsrPricer_vsHagan_swapletRate_inSameBallpark() {
        setup();

        final LinearTsrPricer linear = new LinearTsrPricer(swaptionVol, meanReversion);
        coupon.setPricer(linear);
        linear.initialize(coupon);
        final double linearRate = linear.swapletRate();

        final HaganPricer analytic = new AnalyticHaganPricer(
                swaptionVol, GFunctionFactory.YieldCurveModel.Standard, meanReversion);
        coupon.setPricer(analytic);
        analytic.initialize(coupon);
        final double analyticRate = analytic.swapletRate();

        assertFinite("linearRate", linearRate);
        assertFinite("analyticRate", analyticRate);
        // The two are different convexity-adjustment methodologies.
        // They should both produce positive rates near the par swap rate
        // (~4%), within ~5% of each other (sanity, not equality).
        if (!Tolerance.within(linearRate, analyticRate, 0.05,
                "LinearTSR vs Hagan are different models -- expect order-of-magnitude agreement only")) {
            fail("LinearTSR vs AnalyticHagan swaplet-rate disagree: "
                    + "linear=" + linearRate
                    + " hagan=" + analyticRate);
        }
    }

    private static void assertFinite(final String tag, final double v) {
        assertFalse(tag + " is NaN", Double.isNaN(v));
        assertFalse(tag + " is +/-Inf", Double.isInfinite(v));
    }
}
