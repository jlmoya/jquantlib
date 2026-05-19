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
import org.jquantlib.cashflow.CappedFlooredCmsCoupon;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.CmsCoupon;
import org.jquantlib.cashflow.CmsCouponPricer;
import org.jquantlib.cashflow.GFunctionFactory;
import org.jquantlib.cashflow.NumericHaganPricer;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.instruments.MakeCms;
import org.jquantlib.instruments.Swap;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.swaption.InterpolatedSwaptionVolatilityCube;
import org.jquantlib.termstructures.volatilities.swaption.SabrSwaptionVolatilityCube;
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
 * <p>Phase 5e.5b-CFC-d-275: testCmsSwap + testParity body-filled using
 * existing {@link MakeCms} (already used by {@link CmsTest#testCmsSwap}) and
 * the just-landed {@link SabrSwaptionVolatilityCube} +
 * {@link InterpolatedSwaptionVolatilityCube} (SABR Normal-vol kernel from
 * CFC-d-263). testParity iterates {atmVol, SabrVolCube1, SabrVolCube2} per
 * C++ cms_normal.cpp:403-404.
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
     *
     * <p>Phase 5e.5b-CFC-d-275: SABR + Interpolated vol cubes built and
     * exposed via {@link #sabrVolCube1} / {@link #sabrVolCube2}, mirroring
     * C++ cms_normal.cpp:104-250.
     */
    private static final class CommonVars {

        final RelinkableHandle<YieldTermStructure> termStructure = new RelinkableHandle<YieldTermStructure>();
        IborIndex iborIndex;
        Handle<SwaptionVolatilityStructure> atmVol;
        Handle<SwaptionVolatilityStructure> sabrVolCube1;
        Handle<SwaptionVolatilityStructure> sabrVolCube2;

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

            // Vol cubes — C++ cms_normal.cpp:104-250. Optional smile +
            // strike-spread grid feeding either the SABR-calibrated or
            // bilinear-interpolated cube.
            buildVolCubes();
        }

        /**
         * Build the SABR + Interpolated vol cubes (mirror C++
         * cms_normal.cpp:104-250). The cubes wrap {@link #atmVol} with a
         * strike-spread grid keyed by option/swap tenor; SABR variant
         * solves per-row for (alpha, beta, nu, rho), bilinear variant
         * interpolates the spread grid directly.
         */
        private void buildVolCubes() {
            final List<Period> optionTenors = Arrays.asList(
                    new Period(1, TimeUnit.Years),
                    new Period(10, TimeUnit.Years),
                    new Period(30, TimeUnit.Years));
            final List<Period> swapTenors = Arrays.asList(
                    new Period(2, TimeUnit.Years),
                    new Period(10, TimeUnit.Years),
                    new Period(30, TimeUnit.Years));
            final List<Double> strikeSpreads = Arrays.asList(-0.020, -0.005, 0.000, 0.005, 0.020);

            final int nRows = optionTenors.size() * swapTenors.size();
            final int nCols = strikeSpreads.size();
            final Matrix volSpreadsMatrix = new Matrix(nRows, nCols);

            volSpreadsMatrix.set(0, 0, -0.0016);
            volSpreadsMatrix.set(0, 1, -0.0008);
            volSpreadsMatrix.set(0, 2,  0.0000);
            volSpreadsMatrix.set(0, 3,  0.0009);
            volSpreadsMatrix.set(0, 4,  0.0038);

            volSpreadsMatrix.set(1, 0,  0.0009);
            volSpreadsMatrix.set(1, 1, -0.0003);
            volSpreadsMatrix.set(1, 2,  0.0000);
            volSpreadsMatrix.set(1, 3,  0.0007);
            volSpreadsMatrix.set(1, 4,  0.0035);

            volSpreadsMatrix.set(2, 0,  0.0025);
            volSpreadsMatrix.set(2, 1,  0.0002);
            volSpreadsMatrix.set(2, 2,  0.0000);
            volSpreadsMatrix.set(2, 3,  0.0002);
            volSpreadsMatrix.set(2, 4,  0.0024);

            volSpreadsMatrix.set(3, 0, -0.0009);
            volSpreadsMatrix.set(3, 1, -0.0003);
            volSpreadsMatrix.set(3, 2,  0.0000);
            volSpreadsMatrix.set(3, 3,  0.0003);
            volSpreadsMatrix.set(3, 4,  0.0013);

            volSpreadsMatrix.set(4, 0, -0.0001);
            volSpreadsMatrix.set(4, 1, -0.0001);
            volSpreadsMatrix.set(4, 2,  0.0000);
            volSpreadsMatrix.set(4, 3,  0.0001);
            volSpreadsMatrix.set(4, 4,  0.0007);

            volSpreadsMatrix.set(5, 0,  0.0003);
            volSpreadsMatrix.set(5, 1,  0.0000);
            volSpreadsMatrix.set(5, 2,  0.0000);
            volSpreadsMatrix.set(5, 3,  0.0001);
            volSpreadsMatrix.set(5, 4,  0.0005);

            volSpreadsMatrix.set(6, 0, -0.0004);
            volSpreadsMatrix.set(6, 1, -0.0001);
            volSpreadsMatrix.set(6, 2,  0.0000);
            volSpreadsMatrix.set(6, 3,  0.0001);
            volSpreadsMatrix.set(6, 4,  0.0006);

            volSpreadsMatrix.set(7, 0, -0.0001);
            volSpreadsMatrix.set(7, 1,  0.0000);
            volSpreadsMatrix.set(7, 2,  0.0000);
            volSpreadsMatrix.set(7, 3,  0.0000);
            volSpreadsMatrix.set(7, 4,  0.0002);

            volSpreadsMatrix.set(8, 0, -0.0002);
            volSpreadsMatrix.set(8, 1, -0.0001);
            volSpreadsMatrix.set(8, 2,  0.0000);
            volSpreadsMatrix.set(8, 3,  0.0001);
            volSpreadsMatrix.set(8, 4,  0.0002);

            final List<List<Handle<Quote>>> volSpreads = new ArrayList<List<Handle<Quote>>>(nRows);
            for (int i = 0; i < nRows; ++i) {
                final List<Handle<Quote>> row = new ArrayList<Handle<Quote>>(nCols);
                for (int j = 0; j < nCols; ++j) {
                    row.add(new Handle<Quote>(new SimpleQuote(volSpreadsMatrix.get(i, j))));
                }
                volSpreads.add(row);
            }

            // C++ cms_normal.cpp:186-205 — two SwapIndex bases (2Y + 1Y)
            // built bare-bones (no EuriborSwapIsdaFixA convenience).
            final SwapIndex swapIndexBase = new SwapIndex("swapIndexBase",
                    new Period(2, TimeUnit.Years),
                    iborIndex.fixingDays(),
                    iborIndex.currency(),
                    iborIndex.fixingCalendar(),
                    new Period(1, TimeUnit.Years),
                    BusinessDayConvention.ModifiedFollowing,
                    new Thirty360(Thirty360.Convention.EurobondBasis),
                    iborIndex);
            final SwapIndex shortSwapIndexBase = new SwapIndex("shortSwapIndexBase",
                    new Period(1, TimeUnit.Years),
                    iborIndex.fixingDays(),
                    iborIndex.currency(),
                    iborIndex.fixingCalendar(),
                    new Period(1, TimeUnit.Years),
                    BusinessDayConvention.ModifiedFollowing,
                    new Thirty360(Thirty360.Convention.EurobondBasis),
                    iborIndex);

            final boolean vegaWeightedSmileFit = false;

            // SabrVolCube2 — InterpolatedSwaptionVolatilityCube (bilinear).
            sabrVolCube2 = new Handle<SwaptionVolatilityStructure>(
                    new InterpolatedSwaptionVolatilityCube(atmVol,
                            optionTenors, swapTenors, strikeSpreads,
                            volSpreads, swapIndexBase, shortSwapIndexBase,
                            vegaWeightedSmileFit));
            sabrVolCube2.currentLink().enableExtrapolation();

            // SabrVolCube1 — SABR-calibrated cube. Seed guesses mirror
            // C++ cms_normal.cpp:220-231 (alpha=0.01, beta=0.0 fixed,
            // nu=0.3, rho=0.5).
            final List<List<Handle<Quote>>> guess = new ArrayList<List<Handle<Quote>>>(nRows);
            for (int i = 0; i < nRows; ++i) {
                final List<Handle<Quote>> row = new ArrayList<Handle<Quote>>(4);
                row.add(new Handle<Quote>(new SimpleQuote(0.01)));
                row.add(new Handle<Quote>(new SimpleQuote(0.0)));
                row.add(new Handle<Quote>(new SimpleQuote(0.3)));
                row.add(new Handle<Quote>(new SimpleQuote(0.5)));
                guess.add(row);
            }
            final boolean[] isParameterFixed = { false, true, false, false };

            final boolean isAtmCalibrated = false;

            sabrVolCube1 = new Handle<SwaptionVolatilityStructure>(
                    new SabrSwaptionVolatilityCube(atmVol,
                            optionTenors, swapTenors, strikeSpreads,
                            volSpreads, swapIndexBase, shortSwapIndexBase,
                            vegaWeightedSmileFit, guess, isParameterFixed,
                            isAtmCalibrated));
            sabrVolCube1.currentLink().enableExtrapolation();
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

    /**
     * Hagan-pricer flat-vol equivalence for swaps (normal case). Mirrors
     * C++ {@code cms_normal.cpp::testCmsSwap} — builds CMS swaps of various
     * lengths via {@link MakeCms} and asserts numeric/analytic NPV
     * agreement to {@code 2e-4}.
     *
     * <p>The Java {@link MakeCms} ctor used by {@link CmsTest#testCmsSwap}
     * is already present and exercises the same code path; this test only
     * differs in the swap-index family name and ATM vol type (Normal vs
     * Lognormal).
     */
    @Test
    public void testCmsSwap() {
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

        final double spread = 0.0;
        final int[] swapLengths = { 1, 5, 6, 10 };
        final int n = swapLengths.length;
        final Swap[] cms = new Swap[n];
        for (int i = 0; i < n; ++i) {
            // no cap, floor; no gearing
            cms[i] = new MakeCms(new Period(swapLengths[i], TimeUnit.Years),
                    swapIndex,
                    vars.iborIndex, spread,
                    new Period(10, TimeUnit.Days)).value();
        }

        for (int j = 0; j < vars.yieldCurveModels.size(); ++j) {
            vars.numericalPricers.get(j).setSwaptionVolatility(vars.atmVol);
            vars.analyticPricers.get(j).setSwaptionVolatility(vars.atmVol);
            for (int sl = 0; sl < n; ++sl) {
                CashFlows.setCouponPricer(cms[sl].leg(0), vars.numericalPricers.get(j));
                final double priceNum = cms[sl].NPV();
                CashFlows.setCouponPricer(cms[sl].leg(0), vars.analyticPricers.get(j));
                final double priceAn = cms[sl].NPV();

                final double difference = Math.abs(priceNum - priceAn);
                final double tol = 2.0e-4;
                if (difference > tol) {
                    fail("\nLength in Years:  " + swapLengths[sl]
                            + "\nswap index:       " + swapIndex.name()
                            + "\nibor index:       " + vars.iborIndex.name()
                            + "\nspread:           " + spread
                            + "\nYieldCurve Model: " + vars.yieldCurveModels.get(j)
                            + "\nNumerical Pricer: " + priceNum
                            + "\nAnalytic Pricer:  " + priceAn
                            + "\ndifference:       " + difference
                            + "\ntolerance:        " + tol);
                }
            }
        }
    }

    /**
     * Put-call parity for capped-floored CMS coupons under a normal-vol
     * surface. Mirrors C++ {@code cms_normal.cpp::testParity} — for each
     * strike in {-0.005, 0.005, 0.015, 0.025} (C++ loops
     * {@code strike = -0.005; strike <= 0.035; strike += 0.01}), builds an
     * uncapped/unfloored swaplet, a caplet ({@code cap = strike}), and a
     * floorlet ({@code floor = strike}) sharing the same CMS swap index,
     * then verifies the put-call identity
     * <pre>
     *   caplet + floorlet == swaplet + N * accrual * strike * discount
     * </pre>
     * to a {@code 4e-5} tolerance (mirrors C++ cms_normal.cpp:472).
     *
     * <p>Iterates the assertion over three vol surfaces
     * {@code {atmVol, SabrVolCube1, SabrVolCube2}}. The SABR cube uses
     * {@link SabrSwaptionVolatilityCube} (CFC-d-263 SABR Normal-vol
     * kernel) and the interpolated cube uses
     * {@link InterpolatedSwaptionVolatilityCube} (bilinear over the
     * strike-spread grid).
     */
    @Test
    public void testParity() {
        final CommonVars vars = new CommonVars();

        // C++ cms_normal.cpp:403-404 — iterate over ATM matrix + SABR cube +
        // bilinear cube.
        final List<Handle<SwaptionVolatilityStructure>> swaptionVols =
                Arrays.asList(vars.atmVol, vars.sabrVolCube1, vars.sabrVolCube2);

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
        // C++ uses Null<Real>(); Java NULL_RATE sentinel (Constants) elides
        // the cap/floor branch in the CappedFlooredCoupon delegation.
        final double infiniteCap = Constants.NULL_RATE;
        final double infiniteFloor = Constants.NULL_RATE;
        final double gearing = 1.0;
        final double spread = 0.0;
        final double discount = vars.termStructure.currentLink().discount(paymentDate);

        final CappedFlooredCmsCoupon cpnPlain = new CappedFlooredCmsCoupon(
                paymentDate, nominal,
                startDate, endDate,
                swapIndex.fixingDays(), swapIndex,
                gearing, spread,
                infiniteCap, infiniteFloor,
                startDate, endDate,
                vars.iborIndex.dayCounter());

        // C++ loop: for (Rate strike = -0.005; strike <= 0.035; strike+=0.01)
        // → {-0.005, 0.005, 0.015, 0.025, 0.035} (5 strikes).
        for (double strike = -0.005; strike <= 0.0355; strike += 0.01) {
            final CappedFlooredCmsCoupon cpnCapped = new CappedFlooredCmsCoupon(
                    paymentDate, nominal,
                    startDate, endDate,
                    swapIndex.fixingDays(), swapIndex,
                    gearing, spread,
                    strike, infiniteFloor,
                    startDate, endDate,
                    vars.iborIndex.dayCounter());
            final CappedFlooredCmsCoupon cpnFloored = new CappedFlooredCmsCoupon(
                    paymentDate, nominal,
                    startDate, endDate,
                    swapIndex.fixingDays(), swapIndex,
                    gearing, spread,
                    infiniteCap, strike,
                    startDate, endDate,
                    vars.iborIndex.dayCounter());

            for (final Handle<SwaptionVolatilityStructure> swaptionVol : swaptionVols) {
                for (int j = 0; j < vars.yieldCurveModels.size(); ++j) {
                    vars.numericalPricers.get(j).setSwaptionVolatility(swaptionVol);
                    vars.analyticPricers.get(j).setSwaptionVolatility(swaptionVol);
                    final List<CmsCouponPricer> pricers = Arrays.asList(
                            vars.numericalPricers.get(j), vars.analyticPricers.get(j));
                    for (int k = 0; k < pricers.size(); ++k) {
                        cpnPlain.setPricer(pricers.get(k));
                        cpnCapped.setPricer(pricers.get(k));
                        cpnFloored.setPricer(pricers.get(k));
                        final double cpnPlainPrice = cpnPlain.price(vars.termStructure);
                        final double cpnCappedPrice = cpnCapped.price(vars.termStructure);
                        final double cpnFlooredPrice = cpnFloored.price(vars.termStructure);
                        final double difference = Math.abs(cpnCappedPrice + cpnFlooredPrice - cpnPlainPrice
                                - nominal * strike * cpnPlain.accrualPeriod() * discount);
                        // C++ cms_normal.cpp:472 — tol = 4e-5.
                        final double tol = 4.0e-5;

                        if (difference > tol) {
                            fail("\nDiscount Factor:     " + discount
                                    + "\nCoupon payment date: " + paymentDate
                                    + "\nCoupon start date:   " + startDate
                                    + "\nCoupon gearing:      " + gearing
                                    + "\nCoupon swap index:   " + swapIndex.name()
                                    + "\nCoupon spread:       " + spread
                                    + "\nstrike:              " + strike
                                    + "\nCoupon DayCounter:   " + vars.iborIndex.dayCounter()
                                    + "\nYieldCurve Model:    " + vars.yieldCurveModels.get(j)
                                    + "\nPricerType:          " + (k == 0 ? "Numerical Pricer" : "Analytic Pricer")
                                    + "\nPlain Coupon with rate=strike:       "
                                    + (nominal * strike * cpnPlain.accrualPeriod() * discount)
                                    + "\nPlain Coupon price:       " + cpnPlainPrice
                                    + "\nCapped Coupon price:        " + cpnCappedPrice
                                    + "\nFloored Coupon price:      " + cpnFlooredPrice
                                    + "\ndifference:          " + difference
                                    + "\ntolerance:           " + tol);
                        }
                    }
                }
            }
        }
    }
}
