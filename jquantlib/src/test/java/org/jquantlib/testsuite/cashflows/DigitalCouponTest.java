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
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.AssetOrNothingPayoff;
import org.jquantlib.instruments.CashOrNothingPayoff;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.Position;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.math.Constants;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.optionlet.ConstantOptionletVolatility;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
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
 * <p>Two structural-identity tests were body-filled:
 * {@code testCallPutParity} and {@code testReplicationType}.
 *
 * <h3>Phase 5e.5b-CFC-d-60 (2026-05-16)</h3>
 *
 * <p>The remaining 6 tests are body-filled using the already-ported
 * {@link AnalyticEuropeanEngine} (which delegates to
 * {@code BlackCalculator}, which handles
 * {@link AssetOrNothingPayoff}/{@link CashOrNothingPayoff} payoffs natively)
 * and {@link BlackFormula#blackFormulaCashItmProbability}. The deep ITM/OTM
 * cases are pure deterministic checks against {@code underlying.price()} and
 * 0; the standard {@code testAssetOrNothing}/{@code testCashOrNothing} cases
 * cross-validate against Cox-Rubinstein {@code N(d1)}/{@code N(d2)}
 * closed-form formulas and the {@code AnalyticEuropeanEngine}-backed Vanilla
 * Option NPV with {@link AssetOrNothingPayoff}/{@link CashOrNothingPayoff}.
 *
 * <p>Mirror of {@code test-suite/digitalcoupon.cpp} BOOST_AUTO_TEST_CASE
 * bodies; same tolerances (1e-4 optionTolerance, 1e-10 blackTolerance, ...)
 * as v1.42.1.
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
        final double optionTolerance;
        final double blackTolerance;

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
            this.optionTolerance = 1.0e-04;
            this.blackTolerance = 1.0e-10;
        }
    }

    @Test
    public void testAssetOrNothing() {
        QL.info("Testing European asset-or-nothing digital coupon...");

        /*  Call Payoff = (aL+b)Heaviside(aL+b-X) =  a Max[L-X'] + (b+aX')Heaviside(L-X')
            Value Call = aF N(d1') + bN(d2')
            Put Payoff =  (aL+b)Heaviside(X-aL-b) = -a Max[X-L'] + (b+aX')Heaviside(X'-L)
            Value Put = aF N(-d1') + bN(-d2')
            where:
            d1' = ln(F/X')/stdDev + 0.5*stdDev;
        */

        final CommonVars vars = new CommonVars();

        final double[] vols = { 0.05, 0.15, 0.30 };
        final double[] strikes = { 0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07 };
        final double[] gearings = { 1.0, 2.8 };
        final double[] spreads = { 0.0, 0.005 };

        final double gap = 1.0e-7; /* low, in order to compare digital option value
                                      with black formula result */
        final DigitalReplication replication =
                new DigitalReplication(Replication.Type.Central, gap);

        for (final double capletVol : vols) {
            final RelinkableHandle<OptionletVolatilityStructure> vol =
                    new RelinkableHandle<OptionletVolatilityStructure>();
            vol.linkTo(new ConstantOptionletVolatility(vars.today,
                    vars.calendar, BusinessDayConvention.Following,
                    capletVol, new Actual360()));
            for (final double strike : strikes) {
                for (int k = 9; k < 10; k++) {
                    final Date startDate = vars.calendar.advance(vars.settlement,
                            new Period(k + 1, TimeUnit.Years));
                    final Date endDate = vars.calendar.advance(vars.settlement,
                            new Period(k + 2, TimeUnit.Years));
                    final double nullstrike = Constants.NULL_REAL;
                    final Date paymentDate = endDate;
                    for (int h = 0; h < gearings.length; h++) {
                        final double gearing = gearings[h];
                        final double spread = spreads[h];

                        final FloatingRateCoupon underlying = new IborCoupon(
                                paymentDate, vars.nominal,
                                startDate, endDate,
                                vars.fixingDays, vars.index,
                                gearing, spread);
                        // Floating Rate Coupon - Call Digital option
                        final DigitalCoupon digitalCappedCoupon = new DigitalCoupon(
                                underlying,
                                strike, Position.Short, false, nullstrike,
                                nullstrike, Position.Short, false, nullstrike,
                                replication, false);
                        final BlackIborCouponPricer pricer =
                                new BlackIborCouponPricer(vol);
                        digitalCappedCoupon.setPricer(pricer);

                        // Check digital option price vs N(d1) price
                        final double accrualPeriod = underlying.accrualPeriod();
                        final double discount = vars.termStructure.currentLink()
                                .discount(endDate);
                        final Date exerciseDate = underlying.fixingDate();
                        final double forward = underlying.rate();
                        final double effFwd = (forward - spread) / gearing;
                        final double effStrike = (strike - spread) / gearing;
                        final double stdDev = Math.sqrt(
                                vol.currentLink().blackVariance(exerciseDate, effStrike));
                        final CumulativeNormalDistribution phi =
                                new CumulativeNormalDistribution();
                        final double d1 = Math.log(effFwd / effStrike) / stdDev + 0.5 * stdDev;
                        final double d2 = d1 - stdDev;
                        double nD1 = phi.op(d1);
                        double nD2 = phi.op(d2);
                        double nd1Price = (gearing * effFwd * nD1 + spread * nD2)
                                * vars.nominal * accrualPeriod * discount;
                        double optionPrice = digitalCappedCoupon.callOptionRate()
                                * vars.nominal * accrualPeriod * discount;
                        double error = Math.abs(nd1Price - optionPrice);
                        if (error > vars.optionTolerance) {
                            fail("\nDigital Call Option:"
                                    + "\nVolatility = " + capletVol
                                    + "\nStrike = " + strike
                                    + "\nExercise = " + (k + 1) + " years"
                                    + "\nOption price by replication = " + optionPrice
                                    + "\nOption price by Cox-Rubinstein formula = " + nd1Price
                                    + "\nError " + error);
                        }

                        // Check digital option price vs N(d1) price using Vanilla Option class
                        if (spread == 0.0) {
                            final Exercise exercise = new EuropeanExercise(exerciseDate);
                            final double discountAtFixing = vars.termStructure
                                    .currentLink().discount(exerciseDate);
                            final SimpleQuote fwd = new SimpleQuote(effFwd * discountAtFixing);
                            final SimpleQuote qRate = new SimpleQuote(0.0);
                            final YieldTermStructure qTS = Utilities.flatRate(
                                    vars.today, qRate, new Actual360());
                            final BlackVolTermStructure volTS = Utilities.flatVol(
                                    vars.today, capletVol, new Actual360());
                            final StrikedTypePayoff callPayoff = new AssetOrNothingPayoff(
                                    Option.Type.Call, effStrike);
                            final BlackScholesMertonProcess stochProcess =
                                    new BlackScholesMertonProcess(
                                            new Handle<Quote>(fwd),
                                            new Handle<YieldTermStructure>(qTS),
                                            new Handle<YieldTermStructure>(
                                                    vars.termStructure.currentLink()),
                                            new Handle<BlackVolTermStructure>(volTS));
                            final PricingEngine engine =
                                    new AnalyticEuropeanEngine(stochProcess);
                            final VanillaOption callOpt = new VanillaOption(callPayoff, exercise);
                            callOpt.setPricingEngine(engine);
                            final double callVO = vars.nominal * gearing
                                    * accrualPeriod * callOpt.NPV()
                                    * discount / discountAtFixing
                                    * forward / effFwd;
                            error = Math.abs(nd1Price - callVO);
                            if (error > vars.blackTolerance) {
                                fail("\nDigital Call Option:"
                                        + "\nVolatility = " + capletVol
                                        + "\nStrike = " + strike
                                        + "\nExercise = " + (k + 1) + " years"
                                        + "\nOption price by Black asset-ot-nothing payoff = " + callVO
                                        + "\nOption price by Cox-Rubinstein = " + nd1Price
                                        + "\nError " + error);
                            }
                        }

                        // Floating Rate Coupon + Put Digital option
                        final DigitalCoupon digitalFlooredCoupon = new DigitalCoupon(
                                underlying,
                                nullstrike, Position.Long, false, nullstrike,
                                strike, Position.Long, false, nullstrike,
                                replication, false);
                        digitalFlooredCoupon.setPricer(pricer);

                        // Check digital option price vs N(d1) price
                        nD1 = phi.op(-d1);
                        nD2 = phi.op(-d2);
                        nd1Price = (gearing * effFwd * nD1 + spread * nD2)
                                * vars.nominal * accrualPeriod * discount;
                        optionPrice = digitalFlooredCoupon.putOptionRate()
                                * vars.nominal * accrualPeriod * discount;
                        error = Math.abs(nd1Price - optionPrice);
                        if (error > vars.optionTolerance) {
                            fail("\nDigital Put Option:"
                                    + "\nVolatility = " + capletVol
                                    + "\nStrike = " + strike
                                    + "\nExercise = " + (k + 1) + " years"
                                    + "\nOption price by replication = " + optionPrice
                                    + "\nOption price by Cox-Rubinstein = " + nd1Price
                                    + "\nError " + error);
                        }

                        // Check digital option price vs N(d1) price using Vanilla Option class
                        if (spread == 0.0) {
                            final Exercise exercise = new EuropeanExercise(exerciseDate);
                            final double discountAtFixing = vars.termStructure
                                    .currentLink().discount(exerciseDate);
                            final SimpleQuote fwd = new SimpleQuote(effFwd * discountAtFixing);
                            final SimpleQuote qRate = new SimpleQuote(0.0);
                            final YieldTermStructure qTS = Utilities.flatRate(
                                    vars.today, qRate, new Actual360());
                            final BlackVolTermStructure volTS = Utilities.flatVol(
                                    vars.today, capletVol, new Actual360());
                            final BlackScholesMertonProcess stochProcess =
                                    new BlackScholesMertonProcess(
                                            new Handle<Quote>(fwd),
                                            new Handle<YieldTermStructure>(qTS),
                                            new Handle<YieldTermStructure>(
                                                    vars.termStructure.currentLink()),
                                            new Handle<BlackVolTermStructure>(volTS));
                            final StrikedTypePayoff putPayoff = new AssetOrNothingPayoff(
                                    Option.Type.Put, effStrike);
                            final PricingEngine engine =
                                    new AnalyticEuropeanEngine(stochProcess);
                            final VanillaOption putOpt = new VanillaOption(putPayoff, exercise);
                            putOpt.setPricingEngine(engine);
                            final double putVO = vars.nominal * gearing
                                    * accrualPeriod * putOpt.NPV()
                                    * discount / discountAtFixing
                                    * forward / effFwd;
                            error = Math.abs(nd1Price - putVO);
                            if (error > vars.blackTolerance) {
                                fail("\nDigital Put Option:"
                                        + "\nVolatility = " + capletVol
                                        + "\nStrike = " + strike
                                        + "\nExercise = " + (k + 1) + " years"
                                        + "\nOption price by Black asset-ot-nothing payoff = " + putVO
                                        + "\nOption price by Cox-Rubinstein = " + nd1Price
                                        + "\nError " + error);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testAssetOrNothingDeepInTheMoney() {
        QL.info("Testing European deep in-the-money asset-or-nothing digital coupon...");

        final CommonVars vars = new CommonVars();

        final double gearing = 1.0;
        final double spread = 0.0;

        final double capletVolatility = 0.0001;
        final RelinkableHandle<OptionletVolatilityStructure> volatility =
                new RelinkableHandle<OptionletVolatilityStructure>();
        volatility.linkTo(new ConstantOptionletVolatility(vars.today,
                vars.calendar, BusinessDayConvention.Following,
                capletVolatility, new Actual360()));

        for (int k = 0; k < 10; k++) {   // Loop on start and end dates
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

            // Floating Rate Coupon - Deep-in-the-money Call Digital option
            double strike = 0.001;
            final DigitalCoupon digitalCappedCoupon = new DigitalCoupon(
                    underlying,
                    strike, Position.Short, false, nullstrike,
                    nullstrike, Position.Short, false, nullstrike,
                    null, false);
            final BlackIborCouponPricer pricer = new BlackIborCouponPricer(volatility);
            digitalCappedCoupon.setPricer(pricer);

            // Check price vs its target price
            final double accrualPeriod = underlying.accrualPeriod();
            final double discount = vars.termStructure.currentLink().discount(endDate);

            double targetOptionPrice = underlying.price(vars.termStructure);
            double targetPrice = 0.0;
            double digitalPrice = digitalCappedCoupon.price(vars.termStructure);
            double error = Math.abs(targetPrice - digitalPrice);
            double tolerance = 1.0e-08;
            if (error > tolerance) {
                fail("\nFloating Coupon - Digital Call Option:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nCoupon Price = " + digitalPrice
                        + "\nTarget price = " + targetPrice
                        + "\nError = " + error);
            }

            // Check digital option price
            double replicationOptionPrice = digitalCappedCoupon.callOptionRate()
                    * vars.nominal * accrualPeriod * discount;
            error = Math.abs(targetOptionPrice - replicationOptionPrice);
            double optionTolerance = 1.0e-08;
            if (error > optionTolerance) {
                fail("\nDigital Call Option:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nPrice by replication = " + replicationOptionPrice
                        + "\nTarget price = " + targetOptionPrice
                        + "\nError = " + error);
            }

            // Floating Rate Coupon + Deep-in-the-money Put Digital option
            strike = 0.99;
            final DigitalCoupon digitalFlooredCoupon = new DigitalCoupon(
                    underlying,
                    nullstrike, Position.Long, false, nullstrike,
                    strike, Position.Long, false, nullstrike,
                    null, false);
            digitalFlooredCoupon.setPricer(pricer);

            // Check price vs its target price
            targetOptionPrice = underlying.price(vars.termStructure);
            targetPrice = underlying.price(vars.termStructure) + targetOptionPrice;
            digitalPrice = digitalFlooredCoupon.price(vars.termStructure);
            error = Math.abs(targetPrice - digitalPrice);
            tolerance = 2.5e-06;
            if (error > tolerance) {
                fail("\nFloating Coupon + Digital Put Option:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nDigital coupon price = " + digitalPrice
                        + "\nTarget price = " + targetPrice
                        + "\nError " + error);
            }

            // Check digital option
            replicationOptionPrice = digitalFlooredCoupon.putOptionRate()
                    * vars.nominal * accrualPeriod * discount;
            error = Math.abs(targetOptionPrice - replicationOptionPrice);
            optionTolerance = 2.5e-06;
            if (error > optionTolerance) {
                fail("\nDigital Put Option:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nPrice by replication = " + replicationOptionPrice
                        + "\nTarget price = " + targetOptionPrice
                        + "\nError " + error);
            }
        }
    }

    @Test
    public void testAssetOrNothingDeepOutTheMoney() {
        QL.info("Testing European deep out-the-money asset-or-nothing digital coupon...");

        final CommonVars vars = new CommonVars();

        final double gearing = 1.0;
        final double spread = 0.0;

        final double capletVolatility = 0.0001;
        final RelinkableHandle<OptionletVolatilityStructure> volatility =
                new RelinkableHandle<OptionletVolatilityStructure>();
        volatility.linkTo(new ConstantOptionletVolatility(vars.today,
                vars.calendar, BusinessDayConvention.Following,
                capletVolatility, new Actual360()));

        for (int k = 0; k < 10; k++) { // loop on start and end dates
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

            // Floating Rate Coupon - Deep-out-of-the-money Call Digital option
            double strike = 0.99;
            final DigitalCoupon digitalCappedCoupon = new DigitalCoupon(
                    underlying,
                    strike, Position.Short, false, nullstrike,
                    nullstrike, Position.Long, false, nullstrike,
                    null, false);
            final BlackIborCouponPricer pricer = new BlackIborCouponPricer(volatility);
            digitalCappedCoupon.setPricer(pricer);

            // Check price vs its target
            final double accrualPeriod = underlying.accrualPeriod();
            final double discount = vars.termStructure.currentLink().discount(endDate);

            double targetPrice = underlying.price(vars.termStructure);
            double digitalPrice = digitalCappedCoupon.price(vars.termStructure);
            double error = Math.abs(targetPrice - digitalPrice);
            double tolerance = 1.0e-10;
            if (error > tolerance) {
                fail("\nFloating Coupon - Digital Call Option :"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nCoupon price = " + digitalPrice
                        + "\nTarget price = " + targetPrice
                        + "\nError = " + error);
            }

            // Check digital option price
            double targetOptionPrice = 0.0;
            double replicationOptionPrice = digitalCappedCoupon.callOptionRate()
                    * vars.nominal * accrualPeriod * discount;
            error = Math.abs(targetOptionPrice - replicationOptionPrice);
            double optionTolerance = 1.0e-08;
            if (error > optionTolerance) {
                fail("\nDigital Call Option:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nPrice by replication = " + replicationOptionPrice
                        + "\nTarget price = " + targetOptionPrice
                        + "\nError = " + error);
            }

            // Floating Rate Coupon - Deep-out-of-the-money Put Digital option
            strike = 0.01;
            final DigitalCoupon digitalFlooredCoupon = new DigitalCoupon(
                    underlying,
                    nullstrike, Position.Long, false, nullstrike,
                    strike, Position.Long, false, nullstrike,
                    null, false);
            digitalFlooredCoupon.setPricer(pricer);

            // Check price vs its target
            targetPrice = underlying.price(vars.termStructure);
            digitalPrice = digitalFlooredCoupon.price(vars.termStructure);
            tolerance = 1.0e-08;
            error = Math.abs(targetPrice - digitalPrice);
            if (error > tolerance) {
                fail("\nFloating Coupon + Digital Put Coupon:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nCoupon price = " + digitalPrice
                        + "\nTarget price = " + targetPrice
                        + "\nError = " + error);
            }

            // Check digital option
            targetOptionPrice = 0.0;
            replicationOptionPrice = digitalFlooredCoupon.putOptionRate()
                    * vars.nominal * accrualPeriod * discount;
            error = Math.abs(targetOptionPrice - replicationOptionPrice);
            if (error > optionTolerance) {
                fail("\nDigital Put Coupon:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nPrice by replication = " + replicationOptionPrice
                        + "\nTarget price = " + targetOptionPrice
                        + "\nError = " + error);
            }
        }
    }

    @Test
    public void testCashOrNothing() {
        QL.info("Testing European cash-or-nothing digital coupon...");

        /*  Call Payoff = R Heaviside(aL+b-X)
            Value Call = R N(d2')
            Put Payoff =  R Heaviside(X-aL-b)
            Value Put = R N(-d2')
            where:
            d2' = ln(F/X')/stdDev - 0.5*stdDev;
        */

        final CommonVars vars = new CommonVars();

        final double[] vols = { 0.05, 0.15, 0.30 };
        final double[] strikes = { 0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07 };

        final double gearing = 3.0;
        final double spread = -0.0002;

        final double gap = 1.0e-08; /* very low, in order to compare digital option value
                                       with black formula result */
        final DigitalReplication replication =
                new DigitalReplication(Replication.Type.Central, gap);

        for (final double capletVol : vols) {
            final RelinkableHandle<OptionletVolatilityStructure> vol =
                    new RelinkableHandle<OptionletVolatilityStructure>();
            vol.linkTo(new ConstantOptionletVolatility(vars.today,
                    vars.calendar, BusinessDayConvention.Following,
                    capletVol, new Actual360()));
            for (final double strike : strikes) {
                for (int k = 0; k < 10; k++) {
                    final Date startDate = vars.calendar.advance(vars.settlement,
                            new Period(k + 1, TimeUnit.Years));
                    final Date endDate = vars.calendar.advance(vars.settlement,
                            new Period(k + 2, TimeUnit.Years));
                    final double nullstrike = Constants.NULL_REAL;
                    final double cashRate = 0.01;
                    final Date paymentDate = endDate;

                    final FloatingRateCoupon underlying = new IborCoupon(
                            paymentDate, vars.nominal,
                            startDate, endDate,
                            vars.fixingDays, vars.index,
                            gearing, spread);
                    // Floating Rate Coupon - Call Digital option
                    final DigitalCoupon digitalCappedCoupon = new DigitalCoupon(
                            underlying,
                            strike, Position.Short, false, cashRate,
                            nullstrike, Position.Short, false, nullstrike,
                            replication, false);
                    final BlackIborCouponPricer pricer = new BlackIborCouponPricer(vol);
                    digitalCappedCoupon.setPricer(pricer);

                    // Check digital option price vs N(d2) price
                    final Date exerciseDate = underlying.fixingDate();
                    final double forward = underlying.rate();
                    final double effFwd = (forward - spread) / gearing;
                    final double effStrike = (strike - spread) / gearing;
                    final double accrualPeriod = underlying.accrualPeriod();
                    final double discount = vars.termStructure.currentLink().discount(endDate);
                    final double stdDev = Math.sqrt(
                            vol.currentLink().blackVariance(exerciseDate, effStrike));
                    double itm = BlackFormula.blackFormulaCashItmProbability(
                            Option.Type.Call, effStrike, effFwd, stdDev);
                    double nd2Price = itm * vars.nominal * accrualPeriod * discount * cashRate;
                    double optionPrice = digitalCappedCoupon.callOptionRate()
                            * vars.nominal * accrualPeriod * discount;
                    double error = Math.abs(nd2Price - optionPrice);
                    if (error > vars.optionTolerance) {
                        fail("\nDigital Call Option:"
                                + "\nVolatility = " + capletVol
                                + "\nStrike = " + strike
                                + "\nExercise = " + (k + 1) + " years"
                                + "\nPrice by replication = " + optionPrice
                                + "\nPrice by Reiner-Rubinstein = " + nd2Price
                                + "\nError = " + error);
                    }

                    // Check digital option price vs N(d2) price using Vanilla Option class
                    final Exercise exercise = new EuropeanExercise(exerciseDate);
                    final double discountAtFixing = vars.termStructure
                            .currentLink().discount(exerciseDate);
                    final SimpleQuote fwd = new SimpleQuote(effFwd * discountAtFixing);
                    final SimpleQuote qRate = new SimpleQuote(0.0);
                    final YieldTermStructure qTS = Utilities.flatRate(
                            vars.today, qRate, new Actual360());
                    final BlackVolTermStructure volTS = Utilities.flatVol(
                            vars.today, capletVol, new Actual360());
                    final StrikedTypePayoff callPayoff = new CashOrNothingPayoff(
                            Option.Type.Call, effStrike, cashRate);
                    final BlackScholesMertonProcess stochProcess =
                            new BlackScholesMertonProcess(
                                    new Handle<Quote>(fwd),
                                    new Handle<YieldTermStructure>(qTS),
                                    new Handle<YieldTermStructure>(
                                            vars.termStructure.currentLink()),
                                    new Handle<BlackVolTermStructure>(volTS));
                    final PricingEngine engine = new AnalyticEuropeanEngine(stochProcess);
                    final VanillaOption callOpt = new VanillaOption(callPayoff, exercise);
                    callOpt.setPricingEngine(engine);
                    final double callVO = vars.nominal * accrualPeriod * callOpt.NPV()
                            * discount / discountAtFixing;
                    error = Math.abs(nd2Price - callVO);
                    if (error > vars.blackTolerance) {
                        fail("\nDigital Call Option:"
                                + "\nVolatility = " + capletVol
                                + "\nStrike = " + strike
                                + "\nExercise = " + (k + 1) + " years"
                                + "\nOption price by Black asset-ot-nothing payoff = " + callVO
                                + "\nOption price by Reiner-Rubinstein = " + nd2Price
                                + "\nError " + error);
                    }

                    // Floating Rate Coupon + Put Digital option
                    final DigitalCoupon digitalFlooredCoupon = new DigitalCoupon(
                            underlying,
                            nullstrike, Position.Long, false, nullstrike,
                            strike, Position.Long, false, cashRate,
                            replication, false);
                    digitalFlooredCoupon.setPricer(pricer);

                    // Check digital option price vs N(d2) price
                    itm = BlackFormula.blackFormulaCashItmProbability(
                            Option.Type.Put, effStrike, effFwd, stdDev);
                    nd2Price = itm * vars.nominal * accrualPeriod * discount * cashRate;
                    optionPrice = digitalFlooredCoupon.putOptionRate()
                            * vars.nominal * accrualPeriod * discount;
                    error = Math.abs(nd2Price - optionPrice);
                    if (error > vars.optionTolerance) {
                        fail("\nPut Digital Option:"
                                + "\nVolatility = " + capletVol
                                + "\nStrike = " + strike
                                + "\nExercise = " + (k + 1) + " years"
                                + "\nPrice by replication = " + optionPrice
                                + "\nPrice by Reiner-Rubinstein = " + nd2Price
                                + "\nError = " + error);
                    }

                    // Check digital option price vs N(d2) price using Vanilla Option class
                    final StrikedTypePayoff putPayoff = new CashOrNothingPayoff(
                            Option.Type.Put, effStrike, cashRate);
                    final VanillaOption putOpt = new VanillaOption(putPayoff, exercise);
                    putOpt.setPricingEngine(engine);
                    final double putVO = vars.nominal * accrualPeriod * putOpt.NPV()
                            * discount / discountAtFixing;
                    error = Math.abs(nd2Price - putVO);
                    if (error > vars.blackTolerance) {
                        fail("\nDigital Put Option:"
                                + "\nVolatility = " + capletVol
                                + "\nStrike = " + strike
                                + "\nExercise = " + (k + 1) + " years"
                                + "\nOption price by Black asset-ot-nothing payoff = " + putVO
                                + "\nOption price by Reiner-Rubinstein = " + nd2Price
                                + "\nError " + error);
                    }
                }
            }
        }
    }

    @Test
    public void testCashOrNothingDeepInTheMoney() {
        QL.info("Testing European deep in-the-money cash-or-nothing digital coupon...");

        final CommonVars vars = new CommonVars();

        final double gearing = 1.0;
        final double spread = 0.0;

        final double capletVolatility = 0.0001;
        final RelinkableHandle<OptionletVolatilityStructure> volatility =
                new RelinkableHandle<OptionletVolatilityStructure>();
        volatility.linkTo(new ConstantOptionletVolatility(vars.today,
                vars.calendar, BusinessDayConvention.Following,
                capletVolatility, new Actual360()));

        for (int k = 0; k < 10; k++) {   // Loop on start and end dates
            final Date startDate = vars.calendar.advance(vars.settlement,
                    new Period(k + 1, TimeUnit.Years));
            final Date endDate = vars.calendar.advance(vars.settlement,
                    new Period(k + 2, TimeUnit.Years));
            final double nullstrike = Constants.NULL_REAL;
            final double cashRate = 0.01;
            final Date paymentDate = endDate;

            final FloatingRateCoupon underlying = new IborCoupon(
                    paymentDate, vars.nominal,
                    startDate, endDate,
                    vars.fixingDays, vars.index,
                    gearing, spread);
            // Floating Rate Coupon - Deep-in-the-money Call Digital option
            double strike = 0.001;
            final DigitalCoupon digitalCappedCoupon = new DigitalCoupon(
                    underlying,
                    strike, Position.Short, false, cashRate,
                    nullstrike, Position.Short, false, nullstrike,
                    null, false);
            final BlackIborCouponPricer pricer = new BlackIborCouponPricer(volatility);
            digitalCappedCoupon.setPricer(pricer);

            // Check price vs its target
            final double accrualPeriod = underlying.accrualPeriod();
            final double discount = vars.termStructure.currentLink().discount(endDate);

            final double targetOptionPrice = cashRate * vars.nominal * accrualPeriod * discount;
            double targetPrice = underlying.price(vars.termStructure) - targetOptionPrice;
            double digitalPrice = digitalCappedCoupon.price(vars.termStructure);

            double error = Math.abs(targetPrice - digitalPrice);
            final double tolerance = 1.0e-07;
            if (error > tolerance) {
                fail("\nFloating Coupon - Digital Call Coupon:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nCoupon price = " + digitalPrice
                        + "\nTarget price = " + targetPrice
                        + "\nError " + error);
            }

            // Check digital option price
            double replicationOptionPrice = digitalCappedCoupon.callOptionRate()
                    * vars.nominal * accrualPeriod * discount;
            error = Math.abs(targetOptionPrice - replicationOptionPrice);
            final double optionTolerance = 1.0e-07;
            if (error > optionTolerance) {
                fail("\nDigital Call Option:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nPrice by replication = " + replicationOptionPrice
                        + "\nTarget price = " + targetOptionPrice
                        + "\nError = " + error);
            }

            // Floating Rate Coupon + Deep-in-the-money Put Digital option
            strike = 0.99;
            final DigitalCoupon digitalFlooredCoupon = new DigitalCoupon(
                    underlying,
                    nullstrike, Position.Long, false, nullstrike,
                    strike, Position.Long, false, cashRate,
                    null, false);
            digitalFlooredCoupon.setPricer(pricer);

            // Check price vs its target
            targetPrice = underlying.price(vars.termStructure) + targetOptionPrice;
            digitalPrice = digitalFlooredCoupon.price(vars.termStructure);
            error = Math.abs(targetPrice - digitalPrice);
            if (error > tolerance) {
                fail("\nFloating Coupon + Digital Put Option:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nCoupon price = " + digitalPrice
                        + "\nTarget price  = " + targetPrice
                        + "\nError = " + error);
            }

            // Check digital option
            replicationOptionPrice = digitalFlooredCoupon.putOptionRate()
                    * vars.nominal * accrualPeriod * discount;
            error = Math.abs(targetOptionPrice - replicationOptionPrice);
            if (error > optionTolerance) {
                fail("\nDigital Put Coupon:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nPrice by replication = " + replicationOptionPrice
                        + "\nTarget price = " + targetOptionPrice
                        + "\nError = " + error);
            }
        }
    }

    @Test
    public void testCashOrNothingDeepOutTheMoney() {
        QL.info("Testing European deep out-the-money cash-or-nothing digital coupon...");

        final CommonVars vars = new CommonVars();

        final double gearing = 1.0;
        final double spread = 0.0;

        final double capletVolatility = 0.0001;
        final RelinkableHandle<OptionletVolatilityStructure> volatility =
                new RelinkableHandle<OptionletVolatilityStructure>();
        volatility.linkTo(new ConstantOptionletVolatility(vars.today,
                vars.calendar, BusinessDayConvention.Following,
                capletVolatility, new Actual360()));

        for (int k = 0; k < 10; k++) { // loop on start and end dates
            final Date startDate = vars.calendar.advance(vars.settlement,
                    new Period(k + 1, TimeUnit.Years));
            final Date endDate = vars.calendar.advance(vars.settlement,
                    new Period(k + 2, TimeUnit.Years));
            final double nullstrike = Constants.NULL_REAL;
            final double cashRate = 0.01;
            final Date paymentDate = endDate;

            final FloatingRateCoupon underlying = new IborCoupon(
                    paymentDate, vars.nominal,
                    startDate, endDate,
                    vars.fixingDays, vars.index,
                    gearing, spread);
            // Deep out-of-the-money Capped Digital Coupon
            double strike = 0.99;
            final DigitalCoupon digitalCappedCoupon = new DigitalCoupon(
                    underlying,
                    strike, Position.Short, false, cashRate,
                    nullstrike, Position.Short, false, nullstrike,
                    null, false);
            final BlackIborCouponPricer pricer = new BlackIborCouponPricer(volatility);
            digitalCappedCoupon.setPricer(pricer);

            // Check price vs its target
            final double accrualPeriod = underlying.accrualPeriod();
            final double discount = vars.termStructure.currentLink().discount(endDate);

            double targetPrice = underlying.price(vars.termStructure);
            double digitalPrice = digitalCappedCoupon.price(vars.termStructure);
            double error = Math.abs(targetPrice - digitalPrice);
            double tolerance = 1.0e-10;
            if (error > tolerance) {
                fail("\nFloating Coupon + Digital Call Option:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nCoupon price = " + digitalPrice
                        + "\nTarget price  = " + targetPrice
                        + "\nError = " + error);
            }

            // Check digital option price
            double targetOptionPrice = 0.0;
            double replicationOptionPrice = digitalCappedCoupon.callOptionRate()
                    * vars.nominal * accrualPeriod * discount;
            error = Math.abs(targetOptionPrice - replicationOptionPrice);
            final double optionTolerance = 1.0e-10;
            if (error > optionTolerance) {
                fail("\nDigital Call Option:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nPrice by replication = " + replicationOptionPrice
                        + "\nTarget price = " + targetOptionPrice
                        + "\nError = " + error);
            }

            // Deep out-of-the-money Floored Digital Coupon
            strike = 0.01;
            final DigitalCoupon digitalFlooredCoupon = new DigitalCoupon(
                    underlying,
                    nullstrike, Position.Long, false, nullstrike,
                    strike, Position.Long, false, cashRate,
                    null, false);
            digitalFlooredCoupon.setPricer(pricer);

            // Check price vs its target
            targetPrice = underlying.price(vars.termStructure);
            digitalPrice = digitalFlooredCoupon.price(vars.termStructure);
            tolerance = 1.0e-09;
            error = Math.abs(targetPrice - digitalPrice);
            if (error > tolerance) {
                fail("\nDigital Floored Coupon:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nCoupon price = " + digitalPrice
                        + "\nTarget price  = " + targetPrice
                        + "\nError = " + error);
            }

            // Check digital option
            targetOptionPrice = 0.0;
            replicationOptionPrice = digitalFlooredCoupon.putOptionRate()
                    * vars.nominal * accrualPeriod * discount;
            error = Math.abs(targetOptionPrice - replicationOptionPrice);
            if (error > optionTolerance) {
                fail("\nDigital Put Option:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nPrice by replication " + replicationOptionPrice
                        + "\nTarget price " + targetOptionPrice
                        + "\nError " + error);
            }
        }
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
