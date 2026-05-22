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
import org.jquantlib.cashflow.LinearTsrPricer;
import org.jquantlib.cashflow.NumericHaganPricer;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.instruments.MakeCms;
import org.jquantlib.instruments.Swap;
import org.jquantlib.math.matrixutilities.Matrix;
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
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Java port of QuantLib v1.42.1 test-suite/cms.cpp (Phase 5e.5b-CFC-d-76).
 *
 * <p>3 BOOST_AUTO_TEST_CASE methods exercising
 * {@link org.jquantlib.cashflow.CmsCoupon} pricing under a lognormal
 * {@link SwaptionVolatilityMatrix}. The companion file {@code cms_normal.cpp}
 * repeats the trio against a normal vol surface.
 *
 * <p>Three C++ {@code GFunctionFactory::YieldCurveModel} variants drive the
 * pricer pair (NumericHaganPricer + AnalyticHaganPricer), with the fifth
 * model slot occupied by a {@link LinearTsrPricer}. The two Hagan pricers
 * must agree to {@code 2e-4} on swaplet rate / swap NPV — the analytic
 * variant evaluates Hagan's closed-form (eq. 3.5b/3.5c) while the numeric
 * variant Gauss-Kronrod integrates the same kernel.
 */
public class CmsTest {

    public CmsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Mirror of C++ {@code CommonVars} struct. ATM vol surface, ibor index,
     * yield-curve-model variants and numeric/analytic pricer pairs are
     * shared across tests. Vol cubes (SabrVolCube1/2) are deferred until
     * {@link org.jquantlib.termstructures.volatilities.swaption.SwaptionVolatilityMatrix}
     * has SABR/Interpolated cube companions.
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

            // Flat 5% Actual/365F discount/forwarding curve.
            termStructure.linkTo(new FlatForward(referenceDate, 0.05, new Actual365Fixed()));

            // ATM swaption-vol matrix (mirrors C++ cms.cpp:76-96).
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
            m.set(0, 0, 0.1300); m.set(0, 1, 0.1560); m.set(0, 2, 0.1390); m.set(0, 3, 0.1220);
            m.set(1, 0, 0.1440); m.set(1, 1, 0.1580); m.set(1, 2, 0.1460); m.set(1, 3, 0.1260);
            m.set(2, 0, 0.1600); m.set(2, 1, 0.1590); m.set(2, 2, 0.1470); m.set(2, 3, 0.1290);
            m.set(3, 0, 0.1640); m.set(3, 1, 0.1470); m.set(3, 2, 0.1370); m.set(3, 3, 0.1220);
            m.set(4, 0, 0.1400); m.set(4, 1, 0.1300); m.set(4, 2, 0.1250); m.set(4, 3, 0.1100);
            m.set(5, 0, 0.1130); m.set(5, 1, 0.1090); m.set(5, 2, 0.1070); m.set(5, 3, 0.0930);

            atmVol = new Handle<SwaptionVolatilityStructure>(
                    new SwaptionVolatilityMatrix(calendar,
                            BusinessDayConvention.Following,
                            atmOptionTenors, atmSwapTenors,
                            m, new Actual365Fixed(),
                            false, /*VolatilityType.ShiftedLognormal*/
                            org.jquantlib.model.VolatilityType.ShiftedLognormal,
                            /*shifts*/ (Matrix) null));

            iborIndex = new Euribor6M(termStructure);

            // Five yield-curve-model slots (last one == LinearTsr; mirrors
            // C++ cms.cpp:220-224).
            yieldCurveModels = Arrays.asList(
                    GFunctionFactory.YieldCurveModel.Standard,
                    GFunctionFactory.YieldCurveModel.ExactYield,
                    GFunctionFactory.YieldCurveModel.ParallelShifts,
                    GFunctionFactory.YieldCurveModel.NonParallelShifts,
                    GFunctionFactory.YieldCurveModel.NonParallelShifts);

            final Handle<Quote> zeroMeanRev = new Handle<Quote>(new SimpleQuote(0.0));

            numericalPricers = new ArrayList<>();
            analyticPricers = new ArrayList<>();
            for (int j = 0; j < yieldCurveModels.size(); ++j) {
                if (j < yieldCurveModels.size() - 1) {
                    numericalPricers.add(new NumericHaganPricer(atmVol, yieldCurveModels.get(j), zeroMeanRev));
                } else {
                    numericalPricers.add(new LinearTsrPricer(atmVol, zeroMeanRev));
                }
                analyticPricers.add(new AnalyticHaganPricer(atmVol, yieldCurveModels.get(j), zeroMeanRev));
            }
        }
    }

    /**
     * Hagan-pricer flat-vol equivalence for coupons. Mirrors C++
     * {@code cms.cpp::testFairRate} — replicates a 20-year-forward 1-year
     * CMS coupon and checks numeric vs analytic rate agreement to {@code 2e-4}.
     *
     * <p>The C++ test wraps the coupon in {@code CappedFlooredCmsCoupon}
     * with {@code Null<Real>()} cap/floor (i.e. effectively no cap/floor).
     * Java's {@code CappedFlooredCmsCoupon} ctor still throws "work in
     * progress" (Phase 5e.5b-CFC-d-76 leaves the
     * {@code CappedFlooredCoupon} delegation gap untouched), so we
     * construct {@link CmsCoupon} directly — semantically equivalent when
     * both cap and floor are absent.
     */
    @Test
    public void testFairRate() {
        final CommonVars vars = new CommonVars();

        // C++ inlines a SwapIndex ctor mirroring EuriborSwapIsdaFixA(10Y);
        // we use the same convenience subclass for parity.
        final SwapIndex swapIndex = new SwapIndex("EuriborSwapIsdaFixA",
                new Period(10, TimeUnit.Years),
                vars.iborIndex.fixingDays(),
                vars.iborIndex.currency(),
                vars.iborIndex.fixingCalendar(),
                new Period(1, TimeUnit.Years),
                BusinessDayConvention.Unadjusted,
                vars.iborIndex.dayCounter(),
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
            final double tol = 2.0e-4;
            final boolean linearTsr = j == vars.yieldCurveModels.size() - 1;

            if (difference > tol) {
                fail("\nCoupon payment date: " + paymentDate
                        + "\nCoupon start date:   " + startDate
                        + "\nCoupon gearing:      " + gearing
                        + "\nCoupon swap index:   " + swapIndex.name()
                        + "\nCoupon spread:       " + spread
                        + "\nCoupon DayCounter:   " + vars.iborIndex.dayCounter()
                        + "\nYieldCurve Model:    " + vars.yieldCurveModels.get(j)
                        + "\nNumerical Pricer:    " + rate0
                        + (linearTsr ? " (Linear TSR Model)" : "")
                        + "\nAnalytic Pricer:     " + rate1
                        + "\ndifference:          " + difference
                        + "\ntolerance:           " + tol);
            }
        }
    }

    /**
     * Hagan-pricer flat-vol equivalence for swaps. Mirrors C++
     * {@code cms.cpp::testCmsSwap} — builds CMS swaps of various lengths
     * via {@link MakeCms} and asserts numeric/analytic NPV agreement to
     * {@code 2e-4}.
     */
    @Test
    public void testCmsSwap() {
        final CommonVars vars = new CommonVars();

        final SwapIndex swapIndex = new SwapIndex("EuriborSwapIsdaFixA",
                new Period(10, TimeUnit.Years),
                vars.iborIndex.fixingDays(),
                vars.iborIndex.currency(),
                vars.iborIndex.fixingCalendar(),
                new Period(1, TimeUnit.Years),
                BusinessDayConvention.Unadjusted,
                vars.iborIndex.dayCounter(),
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
                final boolean linearTsr = j == vars.yieldCurveModels.size() - 1;
                if (difference > tol) {
                    fail("\nLength in Years:  " + swapLengths[sl]
                            + "\nswap index:       " + swapIndex.name()
                            + "\nibor index:       " + vars.iborIndex.name()
                            + "\nspread:           " + spread
                            + "\nYieldCurve Model: " + vars.yieldCurveModels.get(j)
                            + "\nNumerical Pricer: " + priceNum
                            + (linearTsr ? " (Linear TSR Model)" : "")
                            + "\nAnalytic Pricer:  " + priceAn
                            + "\ndifference:       " + difference
                            + "\ntolerance:        " + tol);
                }
            }
        }
    }

    /**
     * Put-call parity for capped-floored CMS coupons. Mirrors C++
     * {@code cms.cpp::testParity} — for each strike in {2%, 7%}, builds
     * an uncapped/unfloored swaplet, a caplet ({@code cap = strike}), and
     * a floorlet ({@code floor = strike}) sharing the same CMS swap
     * index, then verifies the put-call identity
     * <pre>
     *   caplet + floorlet == swaplet + N * accrual * strike * discount
     * </pre>
     * to a {@code 2e-5} tolerance ({@code 1e-7} for the LinearTsr pricer).
     *
     * <p>C++ iterates the assertion over three vol surfaces
     * {@code {atmVol, SabrVolCube1, SabrVolCube2}}. Java only ports the
     * ATM matrix today (SabrSwaptionVolatilityCube /
     * InterpolatedSwaptionVolatilityCube belong to a future work-item),
     * so we exercise the parity over {@code atmVol} alone — the SABR-cube
     * iteration is purely additional vol surfaces feeding the same
     * arithmetic identity, not an independent assertion.
     */
    @Test
    public void testParity() {
        final CommonVars vars = new CommonVars();

        // C++ cms.cpp iterates {atmVol, SabrVolCube1, SabrVolCube2}. Java
        // only ports the ATM matrix; SABR cubes are deferred.
        final List<Handle<SwaptionVolatilityStructure>> swaptionVols =
                Arrays.asList(vars.atmVol);

        final SwapIndex swapIndex = new SwapIndex("EuriborSwapIsdaFixA",
                new Period(10, TimeUnit.Years),
                vars.iborIndex.fixingDays(),
                vars.iborIndex.currency(),
                vars.iborIndex.fixingCalendar(),
                new Period(1, TimeUnit.Years),
                BusinessDayConvention.Unadjusted,
                vars.iborIndex.dayCounter(),
                vars.iborIndex);

        final Date startDate = vars.termStructure.currentLink().referenceDate().add(new Period(20, TimeUnit.Years));
        final Date paymentDate = startDate.add(new Period(1, TimeUnit.Years));
        final Date endDate = paymentDate;
        final double nominal = 1.0;
        final double infiniteCap = Double.NaN;
        final double infiniteFloor = Double.NaN;
        final double gearing = 1.0;
        final double spread = 0.0;
        final double discount = vars.termStructure.currentLink().discount(paymentDate);

        final CappedFlooredCmsCoupon swaplet = new CappedFlooredCmsCoupon(
                paymentDate, nominal,
                startDate, endDate,
                swapIndex.fixingDays(), swapIndex,
                gearing, spread,
                infiniteCap, infiniteFloor,
                startDate, endDate,
                vars.iborIndex.dayCounter());

        for (double strike = 0.02; strike < 0.12; strike += 0.05) {
            final CappedFlooredCmsCoupon caplet = new CappedFlooredCmsCoupon(
                    paymentDate, nominal,
                    startDate, endDate,
                    swapIndex.fixingDays(), swapIndex,
                    gearing, spread,
                    strike, infiniteFloor,
                    startDate, endDate,
                    vars.iborIndex.dayCounter());
            final CappedFlooredCmsCoupon floorlet = new CappedFlooredCmsCoupon(
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
                        swaplet.setPricer(pricers.get(k));
                        caplet.setPricer(pricers.get(k));
                        floorlet.setPricer(pricers.get(k));
                        final double swapletPrice = swaplet.price(vars.termStructure)
                                + nominal * swaplet.accrualPeriod() * strike * discount;
                        final double capletPrice = caplet.price(vars.termStructure);
                        final double floorletPrice = floorlet.price(vars.termStructure);
                        final double difference = Math.abs(capletPrice + floorletPrice - swapletPrice);
                        final boolean linearTsr = k == 0 && j == vars.yieldCurveModels.size() - 1;
                        final double tol = linearTsr ? 1.0e-7 : 2.0e-5;
                        if (difference > tol) {
                            fail("\nCoupon payment date: " + paymentDate
                                    + "\nCoupon start date:   " + startDate
                                    + "\nCoupon gearing:      " + gearing
                                    + "\nCoupon swap index:   " + swapIndex.name()
                                    + "\nCoupon spread:       " + spread
                                    + "\nstrike:              " + strike
                                    + "\nCoupon DayCounter:   " + vars.iborIndex.dayCounter()
                                    + "\nYieldCurve Model:    " + vars.yieldCurveModels.get(j)
                                    + (k == 0 ? "\nNumerical Pricer" : "\nAnalytic Pricer")
                                    + (linearTsr ? " (Linear TSR Model)" : "")
                                    + "\nSwaplet price:       " + swapletPrice
                                    + "\nCaplet price:        " + capletPrice
                                    + "\nFloorlet price:      " + floorletPrice
                                    + "\ndifference:          " + difference
                                    + "\ntolerance:           " + tol);
                        }
                    }
                }
            }
        }
    }
}
