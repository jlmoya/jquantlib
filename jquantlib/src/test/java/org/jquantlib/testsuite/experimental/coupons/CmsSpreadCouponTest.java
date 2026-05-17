/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 4d — CmsSpreadCoupon family cross-validation tests.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

/*
 Copyright (C) 2018 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.testsuite.experimental.coupons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.CmsCoupon;
import org.jquantlib.cashflow.CmsCouponPricer;
import org.jquantlib.cashflow.LinearTsrPricer;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.experimental.coupons.CappedFlooredCmsSpreadCoupon;
import org.jquantlib.experimental.coupons.CmsSpreadCoupon;
import org.jquantlib.experimental.coupons.CmsSpreadCouponPricer;
import org.jquantlib.experimental.coupons.LognormalCmsSpreadPricer;
import org.jquantlib.experimental.coupons.SwapSpreadIndex;
import org.jquantlib.indexes.EuriborSwapIsdaFixA;
import org.jquantlib.indexes.IndexManager;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.swaption.ConstantSwaptionVolatility;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Java port of C++ QuantLib v1.42.1
 * {@code QuantLib/test-suite/cmsspread.cpp} (commit 099987f0c).
 *
 * <p>Mirrors the two BOOST_AUTO_TEST_CASE entries:
 * <ul>
 *   <li>{@link #testFixings()} — direct port of {@code testFixings}; verifies
 *       SwapSpreadIndex.fixing() composition, addFixing handling, and the
 *       "enforce today's historic fixings" toggle.</li>
 *   <li>{@link #testCouponPricing()} — port of {@code testCouponPricing};
 *       cross-validates the Java LognormalCmsSpreadPricer (driven by a
 *       LinearTsrPricer underlying CMS coupon pricer) against pinned
 *       reference rates produced by the same C++ classes at v1.42.1
 *       (harness probe
 *       {@code cms_spread_coupon_pricing_probe}).</li>
 * </ul>
 */
public class CmsSpreadCouponTest {

    public CmsSpreadCouponTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    private Date refDate;
    private Handle<YieldTermStructure> yts2;
    private Date savedEvalDate;
    private boolean savedEnforce;

    @Before
    public void setUp() {
        // Mirrors C++ TestData ctor: refDate = 23-Feb-2018; flat forward at 2%.
        refDate = new Date(23, Month.February, 2018);
        savedEvalDate = new Settings().evaluationDate();
        savedEnforce = new Settings().isEnforcesTodaysHistoricFixings();
        new Settings().setEvaluationDate(refDate);

        yts2 = new Handle<YieldTermStructure>(
                new FlatForward(refDate, 0.02, new Actual365Fixed()));

        IndexManager.getInstance().clearHistories();
    }

    @After
    public void tearDown() {
        // Restore globals so other tests are not affected.
        IndexManager.getInstance().clearHistories();
        new Settings().setEnforcesTodaysHistoricFixings(savedEnforce);
        new Settings().setEvaluationDate(savedEvalDate);
    }

    /**
     * Direct port of C++ {@code testFixings}.
     *
     * <ol>
     *   <li>fixing(refDate - 1) on a SwapSpreadIndex must throw (no fixing
     *       and pre-evaluation date).</li>
     *   <li>fixing(refDate) succeeds and equals
     *       {@code cms10y.fixing(refDate) - cms2y.fixing(refDate)} both with
     *       and without past component fixings injected.</li>
     *   <li>Same identity holds for a future fixing date.</li>
     *   <li>With {@code enforcesTodaysHistoricFixings = true}, fixing(refDate)
     *       throws unless BOTH component fixings are present in history.</li>
     * </ol>
     *
     * <p>Tolerance: exact equality (BOOST_CHECK_EQUAL in the C++ original) —
     * the spread is just a linear combination of the two component fixings
     * via floating-point arithmetic that should round identically.
     */
    @Test
    public void testFixings() {
        QL.info("Testing fixings of cms spread indices...");

        final SwapIndex cms10y = new EuriborSwapIsdaFixA(
                new Period(10, TimeUnit.Years), yts2);
        final SwapIndex cms2y = new EuriborSwapIsdaFixA(
                new Period(2, TimeUnit.Years), yts2);
        final SwapSpreadIndex cms10y2y = new SwapSpreadIndex(
                "cms10y2y", cms10y, cms2y);

        new Settings().setEnforcesTodaysHistoricFixings(false);

        // 1. Pre-evaluation-date fixing must fail.
        try {
            cms10y2y.fixing(refDate.sub(1));
            fail("expected exception when fixing before refDate without history");
        } catch (final Exception e) {
            // expected
        }

        // 2. fixing(refDate) before any history is added — both components forecast.
        final double atRef = cms10y2y.fixing(refDate);
        assertEquals(cms10y.fixing(refDate) - cms2y.fixing(refDate), atRef, 0.0);

        // 3. With cms10y history present.
        cms10y.addFixing(refDate, 0.05);
        assertEquals(cms10y.fixing(refDate) - cms2y.fixing(refDate),
                cms10y2y.fixing(refDate), 0.0);

        // 4. With BOTH histories present.
        cms2y.addFixing(refDate, 0.04);
        assertEquals(cms10y.fixing(refDate) - cms2y.fixing(refDate),
                cms10y2y.fixing(refDate), 0.0);

        // 5. Future fixing always derived from forecast.
        final Date futureFixingDate = new Target().adjust(
                refDate.add(new Period(1, TimeUnit.Years)));
        assertEquals(cms10y.fixing(futureFixingDate) - cms2y.fixing(futureFixingDate),
                cms10y2y.fixing(futureFixingDate), 0.0);

        IndexManager.getInstance().clearHistories();
        // Re-prime empty histories for the two component indices: the
        // InterestRateIndex ctor would have done this on first construction
        // (see IndexManager.notifier(name)), but clearHistories() above just
        // dropped them. addFixing(...) below requires a non-null history map.
        IndexManager.getInstance().notifier(cms10y.name());
        IndexManager.getInstance().notifier(cms2y.name());

        // 6. enforcesTodaysHistoricFixings = true: refDate fixing requires history.
        new Settings().setEnforcesTodaysHistoricFixings(true);
        try {
            cms10y2y.fixing(refDate);
            fail("expected exception when enforcing historic fixings with no history");
        } catch (final Exception e) {
            // expected — neither component has a fixing
        }

        cms10y.addFixing(refDate, 0.05);
        try {
            cms10y2y.fixing(refDate);
            fail("expected exception when only one component has a historic fixing");
        } catch (final Exception e) {
            // expected — cms2y still missing
        }

        cms2y.addFixing(refDate, 0.04);
        assertEquals(cms10y.fixing(refDate) - cms2y.fixing(refDate),
                cms10y2y.fixing(refDate), 0.0);
    }

    /**
     * Port of C++ {@code testCouponPricing} (Phase 5e.5b-CFC-d-88).
     *
     * <p>Cross-validates the Java {@link LognormalCmsSpreadPricer} (driven by a
     * {@link LinearTsrPricer} underlying CMS coupon pricer) against pinned
     * reference rates produced by the same C++ classes at v1.42.1 commit
     * {@code 099987f0c}. Reference values come from harness probe
     * {@code migration-harness/cpp/probes/experimental/cms_spread_coupon_pricing_probe.cpp}
     * (JSON: {@code migration-harness/references/experimental/cms_spread_coupon_pricing.json}).
     *
     * <p><b>Java vs C++ test deviation:</b> the C++ test compares the analytical
     * rates against a 1,000,000-sample Sobol Monte-Carlo reference at {@code 1e-6}
     * absolute tolerance. We pin Java directly against C++ analytical rates at
     * TIGHT tolerance ({@code 1e-12} relative) instead — both implementations
     * are deterministic Gauss-Hermite-32 quadratures over the same
     * Brigo-Mercurio closed-form integrand, so they agree at near-machine
     * precision (avoiding stochastic flakiness from re-implementing the
     * Sobol MC chain on the JVM).
     *
     * <p>Identity checks (first half of the C++ test) mirror exactly:
     * the spread-coupon rate must equal the difference of the two component
     * coupon rates at {@code 100*QL_EPSILON} (the C++ {@code eqTol}).
     */
    @Test
    public void testCouponPricing() {
        QL.info("Testing pricing of cms spread coupons...");

        // Tolerances. C++ test uses tol = 1e-6 absolute against MC reference;
        // we pin against analytical reference so we can tighten substantially.
        // TIGHT tier: 1e-12 rel, 1e-14 abs near zero.
        final double tightRel = 1.0e-12;
        final double tightAbsNearZero = 1.0e-14;
        // Identity rate-vs-component-diff: C++ uses 100*QL_EPSILON ≈ 2.22e-14.
        final double identityTol = 100.0 * Constants.QL_EPSILON;

        // ------------------------------------------------------------------
        // TestData fixture (mirrors C++ cmsspread.cpp:50-94 TestData struct).
        // ------------------------------------------------------------------
        final Handle<SwaptionVolatilityStructure> swLn =
                new Handle<SwaptionVolatilityStructure>(
                        new ConstantSwaptionVolatility(refDate, new Target(),
                                BusinessDayConvention.Following,
                                new Handle<Quote>(new SimpleQuote(0.20)),
                                new Actual365Fixed(),
                                VolatilityType.ShiftedLognormal, 0.0));
        final Handle<SwaptionVolatilityStructure> swSln =
                new Handle<SwaptionVolatilityStructure>(
                        new ConstantSwaptionVolatility(refDate, new Target(),
                                BusinessDayConvention.Following,
                                new Handle<Quote>(new SimpleQuote(0.10)),
                                new Actual365Fixed(),
                                VolatilityType.ShiftedLognormal, 0.01));
        final Handle<SwaptionVolatilityStructure> swN =
                new Handle<SwaptionVolatilityStructure>(
                        new ConstantSwaptionVolatility(refDate, new Target(),
                                BusinessDayConvention.Following,
                                new Handle<Quote>(new SimpleQuote(0.0075)),
                                new Actual365Fixed(),
                                VolatilityType.Normal, 0.01));

        final Handle<Quote> reversion = new Handle<Quote>(new SimpleQuote(0.01));
        final CmsCouponPricer cmsPricerLn  =
                new LinearTsrPricer(swLn,  reversion, yts2, new LinearTsrPricer.Settings(), null);
        final CmsCouponPricer cmsPricerSln =
                new LinearTsrPricer(swSln, reversion, yts2, new LinearTsrPricer.Settings(), null);
        final CmsCouponPricer cmsPricerN   =
                new LinearTsrPricer(swN,   reversion, yts2, new LinearTsrPricer.Settings(), null);

        final Handle<Quote> correlation = new Handle<Quote>(new SimpleQuote(0.6));
        final CmsSpreadCouponPricer cmsspPricerLn  =
                new LognormalCmsSpreadPricer(cmsPricerLn,  correlation, yts2, 32);
        final CmsSpreadCouponPricer cmsspPricerSln =
                new LognormalCmsSpreadPricer(cmsPricerSln, correlation, yts2, 32);
        final CmsSpreadCouponPricer cmsspPricerN   =
                new LognormalCmsSpreadPricer(cmsPricerN,   correlation, yts2, 32);

        // ------------------------------------------------------------------
        // Section A: spread = cmp1 - cmp2 identity under different fixings.
        // (C++ cmsspread.cpp:196-231).
        // ------------------------------------------------------------------
        final SwapIndex cms10y = new EuriborSwapIsdaFixA(new Period(10, TimeUnit.Years), yts2);
        final SwapIndex cms2y  = new EuriborSwapIsdaFixA(new Period( 2, TimeUnit.Years), yts2);
        final SwapSpreadIndex cms10y2y =
                new SwapSpreadIndex("cms10y2y", cms10y, cms2y);

        final Date valueDate = cms10y2y.valueDate(refDate);
        final Date payDate   = valueDate.add(new Period(1, TimeUnit.Years));

        final CmsCoupon cpn1a = new CmsCoupon(payDate, 10000.0, valueDate, payDate,
                cms10y.fixingDays(), cms10y, 1.0, 0.0,
                new Date(), new Date(), new Actual360(), false);
        final CmsCoupon cpn1b = new CmsCoupon(payDate, 10000.0, valueDate, payDate,
                cms2y.fixingDays(),  cms2y,  1.0, 0.0,
                new Date(), new Date(), new Actual360(), false);
        final CmsSpreadCoupon cpn1 = new CmsSpreadCoupon(payDate, 10000.0, valueDate, payDate,
                cms10y2y.fixingDays(), cms10y2y, 1.0, 0.0,
                new Date(), new Date(), new Actual360(), false);
        // The fixingDate of the spread coupon must equal refDate (cms10y2y has
        // 2 fixing days, so backed off from valueDate it lands back on refDate).
        assertEquals("cpn1 fixingDate must be refDate", refDate, cpn1.fixingDate());

        cpn1a.setPricer(cmsPricerLn);
        cpn1b.setPricer(cmsPricerLn);
        cpn1.setPricer(cmsspPricerLn);

        // C++ reference values for the Ln-pricer identity (probe output).
        // Format: { state, spread_rate, component_diff, cpn1a_rate, cpn1b_rate }
        assertCloseRel("identity_no_fixings: spread_rate",
                1.1262962354690459e-05, cpn1.rate(), tightRel, tightAbsNearZero);
        assertCloseRel("identity_no_fixings: cpn1a_rate",
                0.020212602989110397,   cpn1a.rate(), tightRel, tightAbsNearZero);
        assertCloseRel("identity_no_fixings: cpn1b_rate",
                0.020201340026755707,   cpn1b.rate(), tightRel, tightAbsNearZero);
        // Inline identity check at C++ tolerance (mirrors QL_CHECK_CLOSE).
        assertCloseRel("identity_no_fixings: cpn1.rate == cpn1a.rate - cpn1b.rate",
                cpn1a.rate() - cpn1b.rate(), cpn1.rate(), identityTol, tightAbsNearZero);

        cms10y.addFixing(refDate, 0.05);
        assertCloseRel("identity_one_fixing: spread_rate",
                0.029798659973244296, cpn1.rate(), tightRel, tightAbsNearZero);
        assertCloseRel("identity_one_fixing: cpn1.rate == cpn1a.rate - cpn1b.rate",
                cpn1a.rate() - cpn1b.rate(), cpn1.rate(), identityTol, tightAbsNearZero);

        cms2y.addFixing(refDate, 0.03);
        assertCloseRel("identity_both_fixings: spread_rate",
                0.020000000000000004, cpn1.rate(), tightRel, tightAbsNearZero);
        assertCloseRel("identity_both_fixings: cpn1.rate == cpn1a.rate - cpn1b.rate",
                cpn1a.rate() - cpn1b.rate(), cpn1.rate(), identityTol, tightAbsNearZero);

        IndexManager.getInstance().clearHistories();
        // Re-prime empty histories for the two component indices (see testFixings).
        IndexManager.getInstance().notifier(cms10y.name());
        IndexManager.getInstance().notifier(cms2y.name());

        // ------------------------------------------------------------------
        // Section B: plain / capped / floored / collared rates across 3 vol
        // regimes. C++ cmsspread.cpp:233-346.
        // ------------------------------------------------------------------
        final Date pay29 = new Date(23, Month.February, 2029);
        final Date acc28 = new Date(23, Month.February, 2028);

        final CmsCoupon cpn2a = new CmsCoupon(pay29, 10000.0, acc28, pay29, 2,
                cms10y, 1.0, 0.0, new Date(), new Date(), new Actual360(), false);
        final CmsCoupon cpn2b = new CmsCoupon(pay29, 10000.0, acc28, pay29, 2,
                cms2y,  1.0, 0.0, new Date(), new Date(), new Actual360(), false);

        final CappedFlooredCmsSpreadCoupon plainCpn =
                new CappedFlooredCmsSpreadCoupon(pay29, 10000.0, acc28, pay29, 2,
                        cms10y2y, 1.0, 0.0,
                        Constants.NULL_REAL, Constants.NULL_REAL,
                        new Date(), new Date(), new Actual360(), false);
        final CappedFlooredCmsSpreadCoupon cappedCpn =
                new CappedFlooredCmsSpreadCoupon(pay29, 10000.0, acc28, pay29, 2,
                        cms10y2y, 1.0, 0.0,
                        0.03, Constants.NULL_REAL,
                        new Date(), new Date(), new Actual360(), false);
        final CappedFlooredCmsSpreadCoupon flooredCpn =
                new CappedFlooredCmsSpreadCoupon(pay29, 10000.0, acc28, pay29, 2,
                        cms10y2y, 1.0, 0.0,
                        Constants.NULL_REAL, 0.01,
                        new Date(), new Date(), new Actual360(), false);
        final CappedFlooredCmsSpreadCoupon collaredCpn =
                new CappedFlooredCmsSpreadCoupon(pay29, 10000.0, acc28, pay29, 2,
                        cms10y2y, 1.0, 0.0,
                        0.03, 0.01,
                        new Date(), new Date(), new Actual360(), false);

        // -- Ln pricer --
        cpn2a.setPricer(cmsPricerLn);
        cpn2b.setPricer(cmsPricerLn);
        plainCpn.setPricer(cmsspPricerLn);
        cappedCpn.setPricer(cmsspPricerLn);
        flooredCpn.setPricer(cmsspPricerLn);
        collaredCpn.setPricer(cmsspPricerLn);
        assertCloseRel("plain_ln",    0.000750878709963862,  plainCpn.rate(),    tightRel, tightAbsNearZero);
        assertCloseRel("capped_ln",   0.0004274870535555494, cappedCpn.rate(),   tightRel, tightAbsNearZero);
        assertCloseRel("floored_ln",  0.01177566933969961,   flooredCpn.rate(),  tightRel, tightAbsNearZero);
        assertCloseRel("collared_ln", 0.011452277683291297,  collaredCpn.rate(), tightRel, tightAbsNearZero);

        // -- Sln pricer --
        cpn2a.setPricer(cmsPricerSln);
        cpn2b.setPricer(cmsPricerSln);
        plainCpn.setPricer(cmsspPricerSln);
        cappedCpn.setPricer(cmsspPricerSln);
        flooredCpn.setPricer(cmsspPricerSln);
        collaredCpn.setPricer(cmsspPricerSln);
        assertCloseRel("plain_sln",    0.0003529500436387292,  plainCpn.rate(),    tightRel, tightAbsNearZero);
        assertCloseRel("capped_sln",   0.00033960963736606807, cappedCpn.rate(),   tightRel, tightAbsNearZero);
        assertCloseRel("floored_sln",  0.010662991642952318,   flooredCpn.rate(),  tightRel, tightAbsNearZero);
        assertCloseRel("collared_sln", 0.010649651236679656,   collaredCpn.rate(), tightRel, tightAbsNearZero);

        // -- Normal pricer --
        cpn2a.setPricer(cmsPricerN);
        cpn2b.setPricer(cmsPricerN);
        plainCpn.setPricer(cmsspPricerN);
        cappedCpn.setPricer(cmsspPricerN);
        flooredCpn.setPricer(cmsspPricerN);
        collaredCpn.setPricer(cmsspPricerN);
        assertCloseRel("plain_n",    0.0021209155485561668, plainCpn.rate(),    tightRel, tightAbsNearZero);
        assertCloseRel("capped_n",   0.0011839816985091513, cappedCpn.rate(),   tightRel, tightAbsNearZero);
        assertCloseRel("floored_n",  0.015100429935176664,  flooredCpn.rate(),  tightRel, tightAbsNearZero);
        assertCloseRel("collared_n", 0.014163496085129648,  collaredCpn.rate(), tightRel, tightAbsNearZero);

        // Silence unused-variable warnings for assertTrue-helper if compiler complains.
        assertTrue("rate() must be finite", !Double.isNaN(plainCpn.rate()));
    }

    /**
     * Relative-tolerance assertion that falls back to absolute tolerance for
     * values near zero (mirrors C++ {@code QL_CHECK_CLOSE} semantics).
     */
    private static void assertCloseRel(final String tag, final double expected,
                                       final double actual, final double relTol,
                                       final double absNearZeroTol) {
        final double diff = Math.abs(expected - actual);
        if (Math.abs(expected) < absNearZeroTol) {
            // Use absolute tolerance near zero.
            assertTrue(tag + ": expected=" + expected + " actual=" + actual
                    + " absDiff=" + diff + " > " + absNearZeroTol,
                    diff <= absNearZeroTol);
            return;
        }
        final double relErr = diff / Math.abs(expected);
        if (relErr > relTol) {
            fail(tag + ": expected=" + expected + " actual=" + actual
                    + " relErr=" + relErr + " > tol=" + relTol);
        }
    }
}
