/*
 Copyright (C) 2026 JQuantLib migration contributors.

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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.AnalyticHaganPricer;
import org.jquantlib.cashflow.CmsCoupon;
import org.jquantlib.cashflow.CmsCouponPricer;
import org.jquantlib.cashflow.GFunctionFactory;
import org.jquantlib.cashflow.NumericHaganPricer;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.swaption.SwaptionVolatilityMatrix;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/cms_normal.cpp (Phase 5e.5b-CFC-d-246).
 *
 * <p>3 BOOST_AUTO_TEST_CASE methods exercising
 * {@link org.jquantlib.cashflow.CmsCoupon} pricing under a normal
 * (Bachelier) {@link SwaptionVolatilityMatrix}, mirroring the trio in
 * {@link CmsTest} (which uses lognormal Black vols). The Normal-vol kernel
 * branches in {@link org.jquantlib.cashflow.HaganPricer},
 * {@link AnalyticHaganPricer}, {@link NumericHaganPricer} and
 * {@link org.jquantlib.cashflow.MarketQuotedOptionPricer} are exercised here.
 *
 * <p>testCmsSwap + testParity remain {@code @Ignore}'d for the same reasons
 * as the lognormal companion in {@link CmsTest} (MakeCms swap-leg wiring +
 * SABR vol cubes not yet ported).
 */
public class CmsNormalTest {

    public CmsNormalTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Mirror of C++ {@code cms_normal.cpp::CommonVars} struct. ATM normal-vol
     * surface, ibor index, yield-curve-model variants and numeric/analytic
     * pricer pairs are shared across tests. Unlike {@link CmsTest}, no
     * LinearTsrPricer slot — C++ cms_normal.cpp only iterates the 4
     * {@link GFunctionFactory.YieldCurveModel} variants.
     */
    private static final class CommonVars {

        final RelinkableHandle<YieldTermStructure> termStructure = new RelinkableHandle<YieldTermStructure>();
        IborIndex iborIndex;
        Handle<SwaptionVolatilityStructure> atmVol;

        List<GFunctionFactory.YieldCurveModel> yieldCurveModels;
        List<CmsCouponPricer> numericalPricers;
        List<CmsCouponPricer> analyticPricers;

        CommonVars() {
            final Calendar calendar = new Target();

            final Date referenceDate = calendar.adjust(Date.todaysDate());
            new Settings().setEvaluationDate(referenceDate);

            // Flat 2% Actual/365F discount/forwarding curve (cf C++
            // cms_normal.cpp:73 — uses 0.02 not 0.05 like cms.cpp).
            termStructure.linkTo(new FlatForward(referenceDate, 0.02, new Actual365Fixed()));

            // ATM swaption-vol matrix in Bachelier (normal) units, mirrors
            // C++ cms_normal.cpp:77-89. Values are in absolute-rate-vol
            // (e.g. 0.0085 = 85bp).
            final List<Period> atmOptionTenors = Arrays.asList(
                    new Period(1, TimeUnit.Months),
                    new Period(6, TimeUnit.Months),
                    new Period(1, TimeUnit.Years),
                    new Period(5, TimeUnit.Years),
                    new Period(10, TimeUnit.Years),
                    new Period(30, TimeUnit.Years));
            final List<Period> atmSwapTenors = Arrays.asList(
                    new Period(1, TimeUnit.Years),
                    new Period(5, TimeUnit.Years),
                    new Period(10, TimeUnit.Years),
                    new Period(30, TimeUnit.Years));

            final Matrix m = new Matrix(atmOptionTenors.size(), atmSwapTenors.size());
            m.set(0, 0, 0.0085); m.set(0, 1, 0.0120); m.set(0, 2, 0.0102); m.set(0, 3, 0.0095);
            m.set(1, 0, 0.0106); m.set(1, 1, 0.0104); m.set(1, 2, 0.0095); m.set(1, 3, 0.0092);
            m.set(2, 0, 0.0104); m.set(2, 1, 0.0099); m.set(2, 2, 0.0092); m.set(2, 3, 0.0088);
            m.set(3, 0, 0.0091); m.set(3, 1, 0.0086); m.set(3, 2, 0.0080); m.set(3, 3, 0.0070);
            m.set(4, 0, 0.0077); m.set(4, 1, 0.0073); m.set(4, 2, 0.0068); m.set(4, 3, 0.0060);
            m.set(5, 0, 0.0057); m.set(5, 1, 0.0055); m.set(5, 2, 0.0050); m.set(5, 3, 0.0039);

            atmVol = new Handle<SwaptionVolatilityStructure>(
                    new SwaptionVolatilityMatrix(calendar,
                            BusinessDayConvention.Following,
                            atmOptionTenors, atmSwapTenors,
                            m, new Actual365Fixed(),
                            false, VolatilityType.Normal,
                            /*shifts*/ (Matrix) null));

            iborIndex = new Euribor6M(termStructure);

            // C++ cms_normal.cpp:253-256 — 4 yield-curve-model slots, NO
            // LinearTsrPricer (the cms.cpp companion adds a 5th LinearTsr
            // slot; cms_normal.cpp does not).
            yieldCurveModels = Arrays.asList(
                    GFunctionFactory.YieldCurveModel.Standard,
                    GFunctionFactory.YieldCurveModel.ExactYield,
                    GFunctionFactory.YieldCurveModel.ParallelShifts,
                    GFunctionFactory.YieldCurveModel.NonParallelShifts);

            final Handle<Quote> zeroMeanRev = new Handle<Quote>(new SimpleQuote(0.0));

            numericalPricers = new ArrayList<CmsCouponPricer>();
            analyticPricers = new ArrayList<CmsCouponPricer>();
            for (int j = 0; j < yieldCurveModels.size(); ++j) {
                numericalPricers.add(new NumericHaganPricer(atmVol, yieldCurveModels.get(j), zeroMeanRev));
                analyticPricers.add(new AnalyticHaganPricer(atmVol, yieldCurveModels.get(j), zeroMeanRev));
            }
        }
    }

    /**
     * Hagan-pricer flat-vol equivalence for coupons (normal-vol surface).
     * Mirrors C++ {@code cms_normal.cpp::testFairRate} — replicates a
     * 20-year-forward 1-year CMS coupon and checks
     * {@link NumericHaganPricer} vs {@link AnalyticHaganPricer} rate
     * agreement to {@code 2e-4} (C++ comment: "2bp... seems very low for a
     * coupon with pmt in 20 Years").
     *
     * <p>C++ wraps the coupon in {@code CappedFlooredCmsCoupon} with
     * {@code Null<Real>()} cap/floor — semantically equivalent to a plain
     * {@link CmsCoupon} (no cap, no floor). We construct {@link CmsCoupon}
     * directly, matching the {@link CmsTest#testFairRate} pattern.
     *
     * <p>The C++ SwapIndex ctor used here (cms_normal.cpp:280-289) differs
     * from cms.cpp's:
     * <ul>
     *   <li>{@code "CMS10Y"} family (cms.cpp uses {@code "EuriborSwapIsdaFixA"})</li>
     *   <li>{@code ModifiedFollowing} BDC (cms.cpp uses {@code Unadjusted})</li>
     *   <li>{@code Thirty360(EurobondBasis)} day counter (cms.cpp uses
     *       {@code iborIndex.dayCounter()} = Actual/360)</li>
     * </ul>
     */
    @Test
    public void testFairRate() {
        final CommonVars vars = new CommonVars();

        final SwapIndex swapIndex = new SwapIndex("CMS10Y",
                new Period(10, TimeUnit.Years),
                vars.iborIndex.fixingDays(),
                vars.iborIndex.currency(),
                vars.iborIndex.fixingCalendar(),
                new Period(1, TimeUnit.Years),
                BusinessDayConvention.ModifiedFollowing,
                new Thirty360(Thirty360.Convention.EurobondBasis),
                vars.iborIndex);

        final Date startDate = vars.termStructure.currentLink().referenceDate().add(new Period(20, TimeUnit.Years));
        final Date paymentDate = startDate.add(new Period(1, TimeUnit.Years));
        final Date endDate = paymentDate;
        final double nominal = 1.0;
        final double gearing = 1.0;
        final double spread = 0.0;

        // CappedFlooredCmsCoupon with Null<Real>() caps == plain CmsCoupon.
        final CmsCoupon coupon = new CmsCoupon(paymentDate, nominal,
                startDate, endDate,
                swapIndex.fixingDays(), swapIndex,
                gearing, spread,
                startDate, endDate,
                vars.iborIndex.dayCounter());

        for (int j = 0; j < vars.yieldCurveModels.size(); ++j) {
            vars.numericalPricers.get(j).setSwaptionVolatility(vars.atmVol);
            coupon.setPricer(vars.numericalPricers.get(j));
            final double rate0 = coupon.rate();

            vars.analyticPricers.get(j).setSwaptionVolatility(vars.atmVol);
            coupon.setPricer(vars.analyticPricers.get(j));
            final double rate1 = coupon.rate();

            final double difference = Math.abs(rate1 - rate0);
            // C++ uses tol = 2e-4 with std::round(10*(diff-tol))/10 > 0
            // (effectively allows ~5e-5 slack); we use a straight 2e-4
            // tolerance matching cms.cpp::testFairRate.
            final double tol = 2.0e-4;

            if (difference > tol) {
                fail("\nCoupon payment date: " + paymentDate
                        + "\nCoupon start date:   " + startDate
                        + "\nCoupon gearing:      " + gearing
                        + "\nCoupon swap index:   " + swapIndex.name()
                        + "\nCoupon spread:       " + spread
                        + "\nCoupon DayCounter:   " + vars.iborIndex.dayCounter()
                        + "\nYieldCurve Model:    " + vars.yieldCurveModels.get(j)
                        + "\nNumerical Pricer:    " + rate0
                        + "\nAnalytic Pricer:     " + rate1
                        + "\ndifference:          " + difference
                        + "\ntolerance:           " + tol);
            }
        }
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-CMSN-1 + WI-5e.5-CMS-3 — same as CmsTest plus normal kernel.")
    @Test
    public void testCmsSwap() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-CMSN-2 + WI-5e.5-CMS-3 — same as CmsTest plus normal kernel.")
    @Test
    public void testParity() {
    }
}
