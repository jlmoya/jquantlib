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
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.experimental.coupons.SwapSpreadIndex;
import org.jquantlib.indexes.EuriborSwapIsdaFixA;
import org.jquantlib.indexes.IndexManager;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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
 *   <li>{@link #testCouponPricing()} — port of {@code testCouponPricing} is
 *       deferred; see the @Ignore rationale on that method.</li>
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
     * Port of C++ {@code testCouponPricing}.
     *
     * <p><b>Status: DEFERRED to Phase 4d.5.</b>
     *
     * <p>The C++ test instantiates a {@code LinearTsrPricer} (CMS-coupon
     * convexity-adjustment pricer) under three quoting conventions
     * (lognormal, shifted-lognormal, normal), wraps it with a
     * {@code LognormalCmsSpreadPricer}, and validates plain / capped /
     * floored / collared CmsSpreadCoupon rates against a 1,000,000-sample
     * Sobol Monte-Carlo benchmark over a bivariate-(shifted-)lognormal /
     * normal joint distribution of the two underlying swap rates.
     *
     * <p>Two missing pieces in the Java port make this deferral necessary:
     * <ol>
     *   <li><b>{@code LinearTsrPricer} is not yet ported.</b> Without it,
     *       there is no in-tree CmsCouponPricer to drive
     *       {@code LognormalCmsSpreadPricer.initialize(...)} (which calls
     *       {@code c1.adjustedFixing()} and {@code c2.adjustedFixing()},
     *       both of which ask the CMS pricer for a convexity adjustment).
     *       Porting LinearTsrPricer is a non-trivial multi-class effort
     *       (TsrPricer base + LinearTsrPricer subclass + their underlying
     *       Hagan-style smile-section integration machinery) — it deserves
     *       its own Phase 4d.5 work-item.</li>
     *   <li><b>The MC benchmark needs SobolRsg + InverseCumulativeNormal.</b>
     *       SobolRsg is ported, but the benchmark also relies on
     *       {@code pseudoSqrt} of the 2x2 covariance matrix and on
     *       boost::accumulators (mean over 1M samples) — straightforward
     *       to translate but adds many lines to a single test method, and
     *       only makes sense once the LognormalCmsSpreadPricer can actually
     *       be invoked end-to-end.</li>
     * </ol>
     *
     * <p>This @Ignore is therefore a deliberate Phase 4d.5 deferral, not a
     * silent skip — the LognormalCmsSpreadPricer source code is in tree and
     * compiles, and the integrand math has been mechanically translated
     * from C++ v1.42.1. Phase 4d.5 will (a) port LinearTsrPricer, (b)
     * re-enable this test, and (c) cross-validate the MC reference against
     * a harness probe to avoid stochastic flakiness on the JVM.
     */
    @Test
    @Ignore("Phase 4d.5: requires LinearTsrPricer (not yet ported) + Sobol-MC benchmark; see method javadoc")
    public void testCouponPricing() {
        QL.info("Testing pricing of cms spread coupons... [deferred to Phase 4d.5]");
    }
}
