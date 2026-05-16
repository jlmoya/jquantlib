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

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.BlackIborCouponPricer;
import org.jquantlib.cashflow.DigitalCoupon;
import org.jquantlib.cashflow.DigitalReplication;
import org.jquantlib.cashflow.FloatingRateCoupon;
import org.jquantlib.cashflow.IborCoupon;
import org.jquantlib.cashflow.Replication;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.Position;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.optionlet.ConstantOptionletVolatility;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/digitalcoupon.cpp (Phase 5e).
 *
 * <p>8 BOOST_AUTO_TEST_CASE methods exercising
 * {@link org.jquantlib.cashflow.DigitalCoupon} (asset-or-nothing,
 * cash-or-nothing) under the
 * {@link org.jquantlib.cashflow.DigitalReplication} pricing strategy
 * with a Black-style underlying ibor-coupon pricer.
 *
 * <h3>Phase 5e.5b-CFC-d-25 (2026-05-16)</h3>
 *
 * <p>Two structural-identity tests are body-filled and un-ignored:
 * <ul>
 *   <li>{@code testCallPutParity} — verifies that for each (vol, strike,
 *       k) triple, {@code price(longCallDigital) - price(shortPutDigital)}
 *       equals {@code nominal * accrual * discount * cashRate} (cash) and
 *       {@code nominal * accrual * discount * forward} (asset). Pure
 *       parity identity, no probe-generated reference values needed.</li>
 *   <li>{@code testReplicationType} — verifies the monotone ordering
 *       Sub &le; Central &le; Super across replication strategies. Pure
 *       inequality, no probe-generated reference values needed.</li>
 * </ul>
 *
 * <p>Remaining 6 tests stay deferred: they cross-check
 * {@link DigitalCoupon} prices against Cox-Rubinstein N(d1)/N(d2)
 * closed-form formulas via {@code CumulativeNormalDistribution}, the
 * Black {@code AssetOrNothing}/{@code CashOrNothing} payoffs through
 * {@code AnalyticEuropeanEngine}, and {@code BlackScholesMertonProcess}.
 * That path requires verifying the Java analytic-european digital engines
 * are wired in the same way as v1.42.1.
 */
public class DigitalCouponTest {

    public DigitalCouponTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /** Mirror of C++ {@code CommonVars} struct (digitalcoupon.cpp:43-67). */
    private static final class CommonVars {
        final Date today;
        final Date settlement;
        final double nominal;
        final Calendar calendar;
        final IborIndex index;
        final int fixingDays;
        final RelinkableHandle<YieldTermStructure> termStructure;

        CommonVars() {
            this.fixingDays = 2;
            this.nominal = 1000000.0;
            this.termStructure = new RelinkableHandle<YieldTermStructure>();
            this.index = new Euribor6M(termStructure);
            this.calendar = index.fixingCalendar();
            this.today = calendar.adjust(new Settings().evaluationDate());
            new Settings().setEvaluationDate(today);
            this.settlement = calendar.advance(today, fixingDays, TimeUnit.Days);
            termStructure.linkTo(Utilities.flatRate(settlement, 0.05,
                    new Actual365Fixed()));
        }
    }

    @Ignore("Phase 5e.5 WI-5e.5-DC-1: deferred — needs analytic-european digital engine cross-validation.")
    @Test
    public void testAssetOrNothing() {
    }

    @Ignore("Phase 5e.5 WI-5e.5-DC-1: deferred — needs analytic-european digital engine cross-validation.")
    @Test
    public void testAssetOrNothingDeepInTheMoney() {
    }

    @Ignore("Phase 5e.5 WI-5e.5-DC-1: deferred — needs analytic-european digital engine cross-validation.")
    @Test
    public void testAssetOrNothingDeepOutTheMoney() {
    }

    @Ignore("Phase 5e.5 WI-5e.5-DC-1: deferred — needs analytic-european digital engine cross-validation.")
    @Test
    public void testCashOrNothing() {
    }

    @Ignore("Phase 5e.5 WI-5e.5-DC-1: deferred — needs analytic-european digital engine cross-validation.")
    @Test
    public void testCashOrNothingDeepInTheMoney() {
    }

    @Ignore("Phase 5e.5 WI-5e.5-DC-1: deferred — needs analytic-european digital engine cross-validation.")
    @Test
    public void testCashOrNothingDeepOutTheMoney() {
    }

    @Test
    public void testCallPutParity() {
        QL.info("Testing call/put parity for European digital coupon...");

        final CommonVars vars = new CommonVars();

        final double[] vols = { 0.05, 0.15, 0.30 };
        final double[] strikes = { 0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07 };

        final double gearing = 1.0;
        final double spread = 0.0;

        for (final double capletVolatility : vols) {
            final RelinkableHandle<OptionletVolatilityStructure> volatility =
                    new RelinkableHandle<OptionletVolatilityStructure>();
            volatility.linkTo(new ConstantOptionletVolatility(vars.today,
                    vars.calendar, BusinessDayConvention.Following,
                    capletVolatility, new Actual360()));
            for (final double strike : strikes) {
                for (int k = 0; k < 10; k++) {
                    final Date startDate = vars.calendar.advance(vars.settlement,
                            new Period(k + 1, TimeUnit.Years));
                    final Date endDate = vars.calendar.advance(vars.settlement,
                            new Period(k + 2, TimeUnit.Years));
                    final double nullstrike = Constants.NULL_REAL;
                    final Date paymentDate = endDate;

                    final FloatingRateCoupon underlying = new IborCoupon(
                            paymentDate, vars.nominal,
                            startDate, endDate,
                            vars.fixingDays, vars.index,
                            gearing, spread);

                    final double cashRate = 0.01;
                    final DigitalCoupon cashDigitalCallCoupon = new DigitalCoupon(
                            underlying,
                            strike, Position.Long, false, cashRate,
                            nullstrike, Position.Long, false, nullstrike,
                            null, false);
                    final BlackIborCouponPricer pricer =
                            new BlackIborCouponPricer(volatility);
                    cashDigitalCallCoupon.setPricer(pricer);
                    final DigitalCoupon cashDigitalPutCoupon = new DigitalCoupon(
                            underlying,
                            nullstrike, Position.Long, false, nullstrike,
                            strike, Position.Short, false, cashRate,
                            null, false);
                    cashDigitalPutCoupon.setPricer(pricer);
                    double digitalPrice = cashDigitalCallCoupon.price(vars.termStructure)
                                          - cashDigitalPutCoupon.price(vars.termStructure);
                    final double accrualPeriod = underlying.accrualPeriod();
                    final double discount = vars.termStructure.currentLink()
                            .discount(endDate);
                    double targetPrice = vars.nominal * accrualPeriod * discount * cashRate;

                    double error = Math.abs(targetPrice - digitalPrice);
                    final double tolerance1 = 1.0e-08;
                    if (error > tolerance1) {
                        fail("\nCash-or-nothing:"
                                + "\nVolatility = " + capletVolatility
                                + "\nStrike = " + strike
                                + "\nExercise = " + (k + 1) + " years"
                                + "\nPrice = " + digitalPrice
                                + "\nTarget Price = " + targetPrice
                                + "\nError = " + error);
                    }

                    final DigitalCoupon assetDigitalCallCoupon = new DigitalCoupon(
                            underlying,
                            strike, Position.Long, false, nullstrike,
                            nullstrike, Position.Long, false, nullstrike,
                            null, false);
                    assetDigitalCallCoupon.setPricer(pricer);
                    final DigitalCoupon assetDigitalPutCoupon = new DigitalCoupon(
                            underlying,
                            nullstrike, Position.Long, false, nullstrike,
                            strike, Position.Short, false, nullstrike,
                            null, false);
                    assetDigitalPutCoupon.setPricer(pricer);
                    digitalPrice = assetDigitalCallCoupon.price(vars.termStructure)
                                   - assetDigitalPutCoupon.price(vars.termStructure);
                    targetPrice = vars.nominal * accrualPeriod * discount
                                  * underlying.rate();
                    error = Math.abs(targetPrice - digitalPrice);
                    final double tolerance2 = 1.0e-07;
                    if (error > tolerance2) {
                        fail("\nAsset-or-nothing:"
                                + "\nVolatility = " + capletVolatility
                                + "\nStrike = " + strike
                                + "\nExercise = " + (k + 1) + " years"
                                + "\nPrice = " + digitalPrice
                                + "\nTarget Price = " + targetPrice
                                + "\nError = " + error);
                    }
                }
            }
        }
    }

    @Test
    public void testReplicationType() {
        QL.info("Testing replication type for European digital coupon...");

        final CommonVars vars = new CommonVars();

        final double[] vols = { 0.05, 0.15, 0.30 };
        final double[] strikes = { 0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07 };

        final double gearing = 1.0;
        final double spread = 0.0;

        final double gap = 1.0e-04;
        final DigitalReplication subReplication =
                new DigitalReplication(Replication.Type.Sub, gap);
        final DigitalReplication centralReplication =
                new DigitalReplication(Replication.Type.Central, gap);
        final DigitalReplication superReplication =
                new DigitalReplication(Replication.Type.Super, gap);

        for (final double capletVolatility : vols) {
            final RelinkableHandle<OptionletVolatilityStructure> volatility =
                    new RelinkableHandle<OptionletVolatilityStructure>();
            volatility.linkTo(new ConstantOptionletVolatility(vars.today,
                    vars.calendar, BusinessDayConvention.Following,
                    capletVolatility, new Actual360()));
            for (final double strike : strikes) {
                for (int k = 0; k < 10; k++) {
                    final Date startDate = vars.calendar.advance(vars.settlement,
                            new Period(k + 1, TimeUnit.Years));
                    final Date endDate = vars.calendar.advance(vars.settlement,
                            new Period(k + 2, TimeUnit.Years));
                    final double nullstrike = Constants.NULL_REAL;
                    final Date paymentDate = endDate;

                    final FloatingRateCoupon underlying = new IborCoupon(
                            paymentDate, vars.nominal,
                            startDate, endDate,
                            vars.fixingDays, vars.index,
                            gearing, spread);
                    final double cashRate = 0.005;
                    final DigitalCoupon subCashLongDigitalCallCoupon = new DigitalCoupon(
                            underlying,
                            strike, Position.Long, false, cashRate,
                            nullstrike, Position.Long, false, nullstrike,
                            subReplication, false);
                    final DigitalCoupon centralCashLongDigitalCallCoupon = new DigitalCoupon(
                            underlying,
                            strike, Position.Long, false, cashRate,
                            nullstrike, Position.Long, false, nullstrike,
                            centralReplication, false);
                    final DigitalCoupon superCashLongDigitalCallCoupon = new DigitalCoupon(
                            underlying,
                            strike, Position.Long, false, cashRate,
                            nullstrike, Position.Long, false, nullstrike,
                            superReplication, false);
                    final BlackIborCouponPricer pricer =
                            new BlackIborCouponPricer(volatility);
                    subCashLongDigitalCallCoupon.setPricer(pricer);
                    centralCashLongDigitalCallCoupon.setPricer(pricer);
                    superCashLongDigitalCallCoupon.setPricer(pricer);
                    final double subDigitalPrice =
                            subCashLongDigitalCallCoupon.price(vars.termStructure);
                    final double centralDigitalPrice =
                            centralCashLongDigitalCallCoupon.price(vars.termStructure);
                    final double superDigitalPrice =
                            superCashLongDigitalCallCoupon.price(vars.termStructure);
                    final double tolerance = 1.0e-09;
                    if ((subDigitalPrice > centralDigitalPrice
                            && Math.abs(centralDigitalPrice - subDigitalPrice) > tolerance)
                            || (centralDigitalPrice > superDigitalPrice
                            && Math.abs(centralDigitalPrice - superDigitalPrice) > tolerance)) {
                        fail("\nCash-or-nothing: Floating Rate Coupon + Call Digital option"
                                + "\nVolatility = " + capletVolatility
                                + "\nStrike = " + strike
                                + "\nExercise = " + (k + 1) + " years"
                                + "\nSub-Replication Price = " + subDigitalPrice
                                + "\nCentral-Replication Price = " + centralDigitalPrice
                                + "\nOver-Replication Price = " + superDigitalPrice);
                    }
                }
            }
        }
    }
}
