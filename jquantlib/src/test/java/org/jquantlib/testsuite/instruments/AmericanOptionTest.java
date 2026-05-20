/*
 Copyright (C) 2008 Richard Gomes

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

/*
 Copyright (C) 2003, 2004 Ferdinando Ametrano
 Copyright (C) 2005 StatPro Italia srl
 Copyright (C) 2005 Joseph Wang

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */

/**
 *
 * Ported from
 * <ul>
 * <li>test-suite/americanoption.cpp</li>
 * </ul>
 *
 * @author <Richard Gomes>
 *
 */

package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.AmericanExercise;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.instruments.Option.Type;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.vanilla.BaroneAdesiWhaleyApproximationEngine;
import org.jquantlib.pricingengines.vanilla.BjerksundStenslandApproximationEngine;
import org.jquantlib.pricingengines.vanilla.FdBlackScholesVanillaEngine;
import org.jquantlib.pricingengines.vanilla.JuQuadraticApproximationEngine;
import org.jquantlib.pricingengines.vanilla.finitedifferences.FDAmericanEngine;
import org.jquantlib.pricingengines.vanilla.finitedifferences.FDShoutEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

public class AmericanOptionTest {

    public AmericanOptionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testBjerksundStenslandValues() {

        QL.info("Testing Bjerksund and Stensland approximation for American options...");

        // type, strike, spot, q, r, t, vol, value, tol
        final AmericanOptionData values[] = {
                // From "Option pricing formulas", Haug, McGraw-Hill 1998, pag 27
                new AmericanOptionData(Option.Type.Call, 40.00, 42.00, 0.08, 0.04, 0.75, 0.35, 5.2704),
                // From "Option pricing formulas", Haug, McGraw-Hill 1998, VBA
                new AmericanOptionData(Option.Type.Put, 40.00, 36.00, 0.00, 0.06, 1.00, 0.20, 4.4531) };

        final Date today = new Settings().evaluationDate();

        final DayCounter dc = new Actual360();

        final SimpleQuote           spot  = new SimpleQuote(0.0);
        final SimpleQuote           qRate = new SimpleQuote(0.0);
        final YieldTermStructure    qTS   = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote           rRate = new SimpleQuote(0.0);
        final YieldTermStructure    rTS   = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote           vol   = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

        final double /* @Real */tolerance = 1.0e-4;

        for (final AmericanOptionData value : values) {

            final StrikedTypePayoff payoff = new PlainVanillaPayoff(value.type, value.strike);

            final int daysToExpiry = (int) (value.t * 360 + 0.5);
            final Date exDate = today.clone().addAssign(daysToExpiry);
            final Exercise exercise = new AmericanExercise(today, exDate);

            spot.setValue(value.s);
            qRate.setValue(value.q);
            rRate.setValue(value.r);
            vol.setValue(value.v);

            final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                    new Handle<Quote>(spot),
                    new Handle<YieldTermStructure>(qTS),
                    new Handle<YieldTermStructure>(rTS),
                    new Handle<BlackVolTermStructure>(volTS));
            final PricingEngine engine = new BjerksundStenslandApproximationEngine(stochProcess);

            final VanillaOption option = new VanillaOption(payoff, exercise);
            option.setPricingEngine(engine);

            final double /* @Real */calculated = option.NPV();
            final double /* @Real */error = Math.abs(calculated - value.result);

            if (error > tolerance) {
                reportFailure(
                        "value", payoff, exercise,
                        value.s, value.q, value.r, today, value.v,
                        value.result, calculated, error, tolerance);
            }
        }

    }

    @Test
    public void testBaroneAdesiWhaley() {
        QL.info("Testing Barone-Adesi and Whaley approximation for American options...");

        /**
         * The data below are from "Option pricing formulas", E.G. Haug, McGraw-Hill 1998 pag 24
         * <p>
         * The following values were replicated only up to the second digit by the VB code provided by Haug, which was used as base
         * for the C++ implementation
         */
        final AmericanOptionData values[] = {
                // type, strike, spot, q, r, t, vol, value
                new AmericanOptionData(Option.Type.Call, 100.00, 90.00, 0.10, 0.10, 0.10, 0.15, 0.0206),
                new AmericanOptionData(Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.10, 0.15, 1.8771),
                new AmericanOptionData(Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.10, 0.15, 10.0089),
                new AmericanOptionData(Option.Type.Call, 100.00, 90.00, 0.10, 0.10, 0.10, 0.25, 0.3159),
                new AmericanOptionData(Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.10, 0.25, 3.1280),
                new AmericanOptionData(Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.10, 0.25, 10.3919),
                new AmericanOptionData(Option.Type.Call, 100.00, 90.00, 0.10, 0.10, 0.10, 0.35, 0.9495),
                new AmericanOptionData(Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.10, 0.35, 4.3777),
                new AmericanOptionData(Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.10, 0.35, 11.1679),
                new AmericanOptionData(Option.Type.Call, 100.00, 90.00, 0.10, 0.10, 0.50, 0.15, 0.8208),
                new AmericanOptionData(Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.50, 0.15, 4.0842),
                new AmericanOptionData(Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.50, 0.15, 10.8087),
                new AmericanOptionData(Option.Type.Call, 100.00, 90.00, 0.10, 0.10, 0.50, 0.25, 2.7437),
                new AmericanOptionData(Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.50, 0.25, 6.8015),
                new AmericanOptionData(Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.50, 0.25, 13.0170),
                new AmericanOptionData(Option.Type.Call, 100.00, 90.00, 0.10, 0.10, 0.50, 0.35, 5.0063),
                new AmericanOptionData(Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.50, 0.35, 9.5106),
                new AmericanOptionData(Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.50, 0.35, 15.5689),
                new AmericanOptionData(Option.Type.Put, 100.00, 90.00, 0.10, 0.10, 0.10, 0.15, 10.0000),
                new AmericanOptionData(Option.Type.Put, 100.00, 100.00, 0.10, 0.10, 0.10, 0.15, 1.8770),
                new AmericanOptionData(Option.Type.Put, 100.00, 110.00, 0.10, 0.10, 0.10, 0.15, 0.0410),
                new AmericanOptionData(Option.Type.Put, 100.00, 90.00, 0.10, 0.10, 0.10, 0.25, 10.2533),
                new AmericanOptionData(Option.Type.Put, 100.00, 100.00, 0.10, 0.10, 0.10, 0.25, 3.1277),
                new AmericanOptionData(Option.Type.Put, 100.00, 110.00, 0.10, 0.10, 0.10, 0.25, 0.4562),
                new AmericanOptionData(Option.Type.Put, 100.00, 90.00, 0.10, 0.10, 0.10, 0.35, 10.8787),
                new AmericanOptionData(Option.Type.Put, 100.00, 100.00, 0.10, 0.10, 0.10, 0.35, 4.3777),
                new AmericanOptionData(Option.Type.Put, 100.00, 110.00, 0.10, 0.10, 0.10, 0.35, 1.2402),
                new AmericanOptionData(Option.Type.Put, 100.00, 90.00, 0.10, 0.10, 0.50, 0.15, 10.5595),
                new AmericanOptionData(Option.Type.Put, 100.00, 100.00, 0.10, 0.10, 0.50, 0.15, 4.0842),
                new AmericanOptionData(Option.Type.Put, 100.00, 110.00, 0.10, 0.10, 0.50, 0.15, 1.0822),
                new AmericanOptionData(Option.Type.Put, 100.00, 90.00, 0.10, 0.10, 0.50, 0.25, 12.4419),
                new AmericanOptionData(Option.Type.Put, 100.00, 100.00, 0.10, 0.10, 0.50, 0.25, 6.8014),
                new AmericanOptionData(Option.Type.Put, 100.00, 110.00, 0.10, 0.10, 0.50, 0.25, 3.3226),
                new AmericanOptionData(Option.Type.Put, 100.00, 90.00, 0.10, 0.10, 0.50, 0.35, 14.6945),
                new AmericanOptionData(Option.Type.Put, 100.00, 100.00, 0.10, 0.10, 0.50, 0.35, 9.5104),
                new AmericanOptionData(Option.Type.Put, 100.00, 110.00, 0.10, 0.10, 0.50, 0.35, 5.8823) };

        final Date today = new Settings().evaluationDate();

        final DayCounter dc = new Actual360();

        final SimpleQuote           spot  = new SimpleQuote(0.0);
        final SimpleQuote           qRate = new SimpleQuote(0.0);
        final YieldTermStructure    qTS   = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote           rRate = new SimpleQuote(0.0);
        final YieldTermStructure    rTS   = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote           vol   = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

        final double /* @Real */tolerance = 3.0e-3;

        for (final AmericanOptionData value : values) {
            final StrikedTypePayoff payoff = new PlainVanillaPayoff(value.type, value.strike);
            final Date exDate = today.add(timeToDays(value.t));
            final Exercise exercise = new AmericanExercise(today, exDate);

            spot.setValue(value.s);
            qRate.setValue(value.q);
            rRate.setValue(value.r);
            vol.setValue(value.v);

            final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                    new Handle<Quote>(spot),
                    new Handle<YieldTermStructure>(qTS),
                    new Handle<YieldTermStructure>(rTS),
                    new Handle<BlackVolTermStructure>(volTS));

            final PricingEngine engine = new BaroneAdesiWhaleyApproximationEngine(stochProcess);

            final VanillaOption option = new VanillaOption(payoff, exercise);
            option.setPricingEngine(engine);

            final double /* @Real */calculated = option.NPV();
            final double /* @Real */error = Math.abs(calculated - value.result);

            if (error > tolerance) {
                reportFailure(
                        "value", payoff, exercise,
                        value.s, value.q, value.r, today, value.v,
                        value.result, calculated, error, tolerance);
            }
        }
    }

    @Test
    public void testJu() {

        /*
         * The data below are from An Approximate Formula for Pricing American Options Journal of Derivatives Winter 1999 Ju, N.
         */
        final AmericanOptionData juValues[] = {
                // type, strike, spot, q, r, t, vol, value, tol
                // These values are from Exhibit 3 - Short dated Put Options
                new AmericanOptionData(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.0833, 0.2, 0.006),
                new AmericanOptionData(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.3333, 0.2, 0.201),
                new AmericanOptionData(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.5833, 0.2, 0.433),

                new AmericanOptionData(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.0833, 0.2, 0.851),
                new AmericanOptionData(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.3333, 0.2, 1.576),
                new AmericanOptionData(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.5833, 0.2, 1.984),

                new AmericanOptionData(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.0833, 0.2, 5.000),
                new AmericanOptionData(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.3333, 0.2, 5.084),
                new AmericanOptionData(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.5833, 0.2, 5.260),

                new AmericanOptionData(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.0833, 0.3, 0.078),
                new AmericanOptionData(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.3333, 0.3, 0.697),
                new AmericanOptionData(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.5833, 0.3, 1.218),

                new AmericanOptionData(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.0833, 0.3, 1.309),
                new AmericanOptionData(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.3333, 0.3, 2.477),
                new AmericanOptionData(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.5833, 0.3, 3.161),

                new AmericanOptionData(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.0833, 0.3, 5.059),
                new AmericanOptionData(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.3333, 0.3, 5.699),
                new AmericanOptionData(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.5833, 0.3, 6.231),

                new AmericanOptionData(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.0833, 0.4, 0.247),
                new AmericanOptionData(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.3333, 0.4, 1.344),
                new AmericanOptionData(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.5833, 0.4, 2.150),

                new AmericanOptionData(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.0833, 0.4, 1.767),
                new AmericanOptionData(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.3333, 0.4, 3.381),
                new AmericanOptionData(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.5833, 0.4, 4.342),

                new AmericanOptionData(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.0833, 0.4, 5.288),
                new AmericanOptionData(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.3333, 0.4, 6.501),
                new AmericanOptionData(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.5833, 0.4, 7.367),

                // Type in Exhibits 4 and 5 if you have some spare time ;-)

                // type, strike, spot, q, r, t, vol, value, tol
                // These values are from Exhibit 6 - Long dated Call Options
                // with dividends
                new AmericanOptionData(Option.Type.Call, 100.00, 80.00, 0.07, 0.03, 3.0, 0.2, 2.605),
                new AmericanOptionData(Option.Type.Call, 100.00, 90.00, 0.07, 0.03, 3.0, 0.2, 5.182),
                new AmericanOptionData(Option.Type.Call, 100.00, 100.00, 0.07, 0.03, 3.0, 0.2, 9.065),
                new AmericanOptionData(Option.Type.Call, 100.00, 110.00, 0.07, 0.03, 3.0, 0.2, 14.430),
                new AmericanOptionData(Option.Type.Call, 100.00, 120.00, 0.07, 0.03, 3.0, 0.2, 21.398),

                new AmericanOptionData(Option.Type.Call, 100.00, 80.00, 0.07, 0.03, 3.0, 0.4, 11.336),
                new AmericanOptionData(Option.Type.Call, 100.00, 90.00, 0.07, 0.03, 3.0, 0.4, 15.711),
                new AmericanOptionData(Option.Type.Call, 100.00, 100.00, 0.07, 0.03, 3.0, 0.4, 20.760),
                new AmericanOptionData(Option.Type.Call, 100.00, 110.00, 0.07, 0.03, 3.0, 0.4, 26.440),
                new AmericanOptionData(Option.Type.Call, 100.00, 120.00, 0.07, 0.03, 3.0, 0.4, 32.709),

                // FIXME case of zero interest rates not handled
                // new AmericanOptionData(Option.Type.CALL, 100.00, 80.00, 0.07, 0.0, 3.0, 0.3, 5.552 ),
                // new AmericanOptionData(Option.Type.CALL, 100.00, 90.00, 0.07, 0.0, 3.0, 0.3, 8.868 ),
                // new AmericanOptionData(Option.Type.CALL, 100.00, 100.00, 0.07, 0.0, 3.0, 0.3, 13.158 ),
                // new AmericanOptionData(Option.Type.CALL, 100.00, 110.00, 0.07, 0.0, 3.0, 0.3, 18.458 ),
                // new AmericanOptionData(Option.Type.CALL, 100.00, 120.00, 0.07, 0.0, 3.0, 0.3, 24.786 ),

                new AmericanOptionData(Option.Type.Call, 100.00, 80.00, 0.03, 0.07, 3.0, 0.3, 12.177),
                new AmericanOptionData(Option.Type.Call, 100.00, 90.00, 0.03, 0.07, 3.0, 0.3, 17.411),
                new AmericanOptionData(Option.Type.Call, 100.00, 100.00, 0.03, 0.07, 3.0, 0.3, 23.402),
                new AmericanOptionData(Option.Type.Call, 100.00, 110.00, 0.03, 0.07, 3.0, 0.3, 30.028),
                new AmericanOptionData(Option.Type.Call, 100.00, 120.00, 0.03, 0.07, 3.0, 0.3, 37.177) };

        QL.info("Testing Ju approximation for American options...");

        final Date today = new Settings().evaluationDate();

        final DayCounter dc = new Actual360();

        final SimpleQuote           spot  = new SimpleQuote(0.0);
        final SimpleQuote           qRate = new SimpleQuote(0.0);
        final YieldTermStructure    qTS   = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote           rRate = new SimpleQuote(0.0);
        final YieldTermStructure    rTS   = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote           vol   = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

        final double tolerance = 1.0e-3;

        for (final AmericanOptionData juValue : juValues) {

            final StrikedTypePayoff payoff = new PlainVanillaPayoff(juValue.type, juValue.strike);
            final Date exDate = today.add(timeToDays(juValue.t));
            final Exercise exercise = new AmericanExercise(today, exDate);

            spot.setValue(juValue.s);
            qRate.setValue(juValue.q);
            rRate.setValue(juValue.r);
            vol.setValue(juValue.v);

            final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                    new Handle<Quote>(spot),
                    new Handle<YieldTermStructure>(qTS),
                    new Handle<YieldTermStructure>(rTS),
                    new Handle<BlackVolTermStructure>(volTS));

            final PricingEngine engine = new JuQuadraticApproximationEngine(stochProcess);

            final VanillaOption option = new VanillaOption(payoff, exercise);
            option.setPricingEngine(engine);

            final double calculated = option.NPV();
            final double error = Math.abs(calculated - juValue.result);

            if (error > tolerance) {
                reportFailure(
                        "value", payoff, exercise,
                        juValue.s, juValue.q, juValue.r, today, juValue.v,
                        juValue.result, calculated, error, tolerance);
            }
        }
    }

    @Test
    public void testFdValues() {
        QL.info("Testing finite-difference engine for American options...");

        /**
         * The data below are from An Approximate Formula for Pricing American Options Journal of Derivatives Winter 1999 Ju, N.
         */
        final AmericanOptionData juValues[] = {
                // type, strike, spot, q, r, t, vol, value, tol
                // These values are from Exhibit 3 - Short dated Put Options
                new AmericanOptionData(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.0833, 0.2, 0.006),
                new AmericanOptionData(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.3333, 0.2, 0.201),
                new AmericanOptionData(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.5833, 0.2, 0.433),

                new AmericanOptionData(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.0833, 0.2, 0.851),
                new AmericanOptionData(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.3333, 0.2, 1.576),
                new AmericanOptionData(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.5833, 0.2, 1.984),

                new AmericanOptionData(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.0833, 0.2, 5.000),
                new AmericanOptionData(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.3333, 0.2, 5.084),
                new AmericanOptionData(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.5833, 0.2, 5.260),

                new AmericanOptionData(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.0833, 0.3, 0.078),
                new AmericanOptionData(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.3333, 0.3, 0.697),
                new AmericanOptionData(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.5833, 0.3, 1.218),

                new AmericanOptionData(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.0833, 0.3, 1.309),
                new AmericanOptionData(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.3333, 0.3, 2.477),
                new AmericanOptionData(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.5833, 0.3, 3.161),

                new AmericanOptionData(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.0833, 0.3, 5.059),
                new AmericanOptionData(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.3333, 0.3, 5.699),
                new AmericanOptionData(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.5833, 0.3, 6.231),

                new AmericanOptionData(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.0833, 0.4, 0.247),
                new AmericanOptionData(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.3333, 0.4, 1.344),
                new AmericanOptionData(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.5833, 0.4, 2.150),

                new AmericanOptionData(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.0833, 0.4, 1.767),
                new AmericanOptionData(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.3333, 0.4, 3.381),
                new AmericanOptionData(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.5833, 0.4, 4.342),

                new AmericanOptionData(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.0833, 0.4, 5.288),
                new AmericanOptionData(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.3333, 0.4, 6.501),
                new AmericanOptionData(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.5833, 0.4, 7.367),

                // Type in Exhibits 4 and 5 if you have some spare time ;-)

                // type, strike, spot, q, r, t, vol, value, tol
                // These values are from Exhibit 6 - Long dated Call Options
                // with dividends
                new AmericanOptionData(Option.Type.Call, 100.00, 80.00, 0.07, 0.03, 3.0, 0.2, 2.605),
                new AmericanOptionData(Option.Type.Call, 100.00, 90.00, 0.07, 0.03, 3.0, 0.2, 5.182),
                new AmericanOptionData(Option.Type.Call, 100.00, 100.00, 0.07, 0.03, 3.0, 0.2, 9.065),
                new AmericanOptionData(Option.Type.Call, 100.00, 110.00, 0.07, 0.03, 3.0, 0.2, 14.430),
                new AmericanOptionData(Option.Type.Call, 100.00, 120.00, 0.07, 0.03, 3.0, 0.2, 21.398),

                new AmericanOptionData(Option.Type.Call, 100.00, 80.00, 0.07, 0.03, 3.0, 0.4, 11.336),
                new AmericanOptionData(Option.Type.Call, 100.00, 90.00, 0.07, 0.03, 3.0, 0.4, 15.711),
                new AmericanOptionData(Option.Type.Call, 100.00, 100.00, 0.07, 0.03, 3.0, 0.4, 20.760),
                new AmericanOptionData(Option.Type.Call, 100.00, 110.00, 0.07, 0.03, 3.0, 0.4, 26.440),
                new AmericanOptionData(Option.Type.Call, 100.00, 120.00, 0.07, 0.03, 3.0, 0.4, 32.709),

                // FIXME case of zero interest rates not handled
                // new AmericanOptionData(Option.Type.CALL, 100.00, 80.00, 0.07, 0.0, 3.0, 0.3, 5.552 ),
                // new AmericanOptionData(Option.Type.CALL, 100.00, 90.00, 0.07, 0.0, 3.0, 0.3, 8.868 ),
                // new AmericanOptionData(Option.Type.CALL, 100.00, 100.00, 0.07, 0.0, 3.0, 0.3, 13.158 ),
                // new AmericanOptionData(Option.Type.CALL, 100.00, 110.00, 0.07, 0.0, 3.0, 0.3, 18.458 ),
                // new AmericanOptionData(Option.Type.CALL, 100.00, 120.00, 0.07, 0.0, 3.0, 0.3, 24.786 ),

                new AmericanOptionData(Option.Type.Call, 100.00, 80.00, 0.03, 0.07, 3.0, 0.3, 12.177),
                new AmericanOptionData(Option.Type.Call, 100.00, 90.00, 0.03, 0.07, 3.0, 0.3, 17.411),
                new AmericanOptionData(Option.Type.Call, 100.00, 100.00, 0.03, 0.07, 3.0, 0.3, 23.402),
                new AmericanOptionData(Option.Type.Call, 100.00, 110.00, 0.03, 0.07, 3.0, 0.3, 30.028),
                new AmericanOptionData(Option.Type.Call, 100.00, 120.00, 0.03, 0.07, 3.0, 0.3, 37.177) };

        final Date today = new Settings().evaluationDate();
        final double tolerance = 8.0e-2;

        for (final AmericanOptionData juValue : juValues) {

            final DayCounter dc = new Actual360();

            final SimpleQuote           spot  = new SimpleQuote(0.0);
            final SimpleQuote           qRate = new SimpleQuote(0.0);
            final YieldTermStructure    qTS   = Utilities.flatRate(today, qRate, dc);
            final SimpleQuote           rRate = new SimpleQuote(0.0);
            final YieldTermStructure    rTS   = Utilities.flatRate(today, rRate, dc);
            final SimpleQuote           vol   = new SimpleQuote(0.0);
            final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

            final StrikedTypePayoff payoff = new PlainVanillaPayoff(juValue.type, juValue.strike);
            final Date exDate = today.add(timeToDays(juValue.t));
            final Exercise exercise = new AmericanExercise(today, exDate);

            spot.setValue(juValue.s);
            qRate.setValue(juValue.q);
            rRate.setValue(juValue.r);
            vol.setValue(juValue.v);

            final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                    new Handle<Quote>(spot),
                    new Handle<YieldTermStructure>(qTS),
                    new Handle<YieldTermStructure>(rTS),
                    new Handle<BlackVolTermStructure>(volTS));

            final PricingEngine engine = new FDAmericanEngine(stochProcess, 100, 100);
            final VanillaOption option = new VanillaOption(payoff, exercise);
            option.setPricingEngine(engine);

            final double calculated = option.NPV();
            final double error = Math.abs(calculated - juValue.result);

            if (error > tolerance) {
                reportFailure(
                        "value", payoff, exercise,
                        juValue.s, juValue.q, juValue.r, today, juValue.v,
                        juValue.result, calculated, error, tolerance);
            }

        }
    }

    @Test
    public void testFdAmericanGreeks() {
        QL.info("Testing Greeks (delta, gamma, theta for American options using FDAmericanEngine");
        testFdGreeks(FDAmericanEngine.class);
    }

    @Test
    public void testFdShoutGreeks() {
        QL.info("Testing Greeks (delta, gamma, theta for American options using FDShoutEngine");
        testFdGreeks(FDShoutEngine.class);
    }


    private void testFdGreeks(final Class<? extends PricingEngine> klass) {
        final Map<String, Double> calculated = new HashMap<String, Double>();
        final Map<String, Double> expected = new HashMap<String, Double>();
        final Map<String, Double> tolerance = new HashMap<String, Double>();
        tolerance.put("delta", 7.0e-4);
        tolerance.put("gamma", 2.0e-4);
        tolerance.put("theta", 1.0e-4);

        final Option.Type types[] = { Option.Type.Call, Option.Type.Put };
        final double strikes[] = { 50.0, 99.5, 100.0, 100.5, 150.0 };
        final double underlyings[] = { 100.0 };
        final double qRates[] = { 0.04, 0.05, 0.06 };
        final double rRates[] = { 0.01, 0.05, 0.15 };
        final int years[] = { 1, 2 };
        final double vols[] = { 0.11, 0.50, 1.20 };

        final DayCounter dc = new Actual360();
        final Date today = new Settings().evaluationDate();

        final SimpleQuote           spot  = new SimpleQuote(0.0);
        final SimpleQuote           qRate = new SimpleQuote(0.0);
        final YieldTermStructure    qTS   = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote           rRate = new SimpleQuote(0.0);
        final YieldTermStructure    rTS   = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote           vol   = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

        for (final Type type : types) {
            for (final double strike : strikes) {
                for (final int year : years) {

                    final Date exDate = today.add(new Period(year, TimeUnit.Years));
                    final Exercise exercise = new AmericanExercise(today, exDate);
                    final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike);

                    final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                            new Handle<Quote>(spot),
                            new Handle<YieldTermStructure>(qTS),
                            new Handle<YieldTermStructure>(rTS),
                            new Handle<BlackVolTermStructure>(volTS));

                    PricingEngine engine = null;
                    try {
                        final Constructor<? extends PricingEngine> c = klass.getConstructor(GeneralizedBlackScholesProcess.class);
                        engine = c.newInstance(stochProcess);
                    } catch (final Exception e) {
                        e.printStackTrace();
                        fail("failed to create pricing engine");
                    }

                    final VanillaOption option = new VanillaOption(payoff, exercise);
                    option.setPricingEngine(engine);

                    for (final double u : underlyings) {
                        for (final double q : qRates) {
                            for (final double r : rRates) {
                                for (final double v : vols) {
                                    spot.setValue(u);
                                    qRate.setValue(q);
                                    rRate.setValue(r);
                                    vol.setValue(v);
                                    // FLOATING_POINT_EXCEPTION
                                    final double value = option.NPV();
                                    final double delta = option.delta();
                                    final double gamma = option.gamma();
                                    //final double theta = option.theta();

                                    calculated.put("delta", delta);
                                    calculated.put("gamma", gamma);
                                    //calculated.put("theta", theta);

                                    if (value > spot.value() * 1.0e-5) {
                                        // perturb spot and get delta and gamma
                                        final double du = u * 1.0e-4;
                                        spot.setValue(u + du);
                                        final double value_p = option.NPV();
                                        final double delta_p = option.delta();
                                        spot.setValue(u - du);
                                        final double value_m = option.NPV();
                                        final double delta_m = option.delta();
                                        spot.setValue(u);
                                        expected.put("delta", (value_p - value_m) / (2 * du));
                                        expected.put("gamma", (delta_p - delta_m) / (2 * du));

                                        /*
                                        // perturb date and get theta
                                        final Date yesterday = today.sub(1);
                                        final Date tomorrow = today.add(1);
                                        final double dT = dc.yearFraction(yesterday, tomorrow);
                                        new Settings().setEvaluationDate(yesterday);
                                        value_m = option.getNPV();
                                        new Settings().setEvaluationDate(tomorrow);
                                        value_p = option.getNPV();
                                        expected.put("theta", (value_p - value_m) / dT);
                                         */

                                        // compare
                                        for (final Entry<String, Double> greek : calculated.entrySet()) {
                                            final double expct = expected.get(greek.getKey());
                                            final double calcl = calculated.get(greek.getKey());
                                            final double tol = tolerance.get(greek.getKey());
                                            final double error = Utilities.relativeError(expct, calcl, u);
                                            if (error > tol) {
                                                reportFailure(
                                                        greek.getKey(), payoff, exercise,
                                                        u, q, r, today, v,
                                                        expct, calcl, error, tol);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void reportFailure(
            final String greekName,
            final StrikedTypePayoff payoff,
            final Exercise exercise,
            final double s,
            final double q,
            final double r,
            final Date today,
            final double v,
            final double expected,
            final double calculated,
            final double error,
            final double tolerance) {

        final StringBuilder sb = new StringBuilder();
        sb.append(exercise.type()).append(' ');
        sb.append(payoff.optionType()).append(" option with ").append(payoff.getClass().getSimpleName()).append(" payoff:\n");
        sb.append("    spot value:     ").append(s).append('\n');
        sb.append("    strike:         ").append(payoff.strike()).append('\n');
        sb.append("    dividend yield: ").append(q).append('\n');
        sb.append("    risk-free rate: ").append(r).append('\n');
        sb.append("    reference date: ").append(today).append('\n');
        sb.append("    maturity:       ").append(exercise.lastDate()).append('\n');
        sb.append("    volatility:     ").append(v).append('\n');
        sb.append("    expected ").append(greekName).append(":    ").append(expected).append('\n');
        sb.append("    calculated ").append(greekName).append(":  ").append(calculated).append('\n');
        sb.append("    error:     ").append(error).append('\n');
        sb.append("    tolerance: ").append(tolerance).append('\n');
        fail(sb.toString());
    }

    private int timeToDays(/* @Time */final double t) {
        return (int) (t * 360 + 0.5);
    }


    /**
     * Faithful port of {@code test-suite/americanoption.cpp:2174}
     * {@code BOOST_AUTO_TEST_CASE(testFdEarliestExerciseDate)}.
     *
     * <p>Verifies that the FD engine honours the earliest-exercise date on an {@code AmericanExercise}:
     * a deep ITM American put with a restricted exercise window must be cheaper than full American but
     * still richer than the European benchmark, and price must rise monotonically with window width.
     */
    @Test
    public void testFdEarliestExerciseDate() {
        QL.info("Testing that the FD engine respects the earliest date for American exercise...");

        final Date today = new Date(15, Month.January, 2025);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();

        final double S0 = 80.0;
        final double K = 100.0;
        final double sigma = 0.25;
        final double r = 0.05;
        final double q = 0.0;

        final SimpleQuote spotQuote = new SimpleQuote(S0);
        final SimpleQuote qQuote = new SimpleQuote(q);
        final SimpleQuote rQuote = new SimpleQuote(r);
        final SimpleQuote volQuote = new SimpleQuote(sigma);
        final Handle<Quote> spot = new Handle<Quote>(spotQuote);
        final YieldTermStructure qTS = Utilities.flatRate(today, qQuote, dc);
        final YieldTermStructure rTS = Utilities.flatRate(today, rQuote, dc);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, volQuote, dc);

        final BlackScholesMertonProcess bsmProcess = new BlackScholesMertonProcess(
                spot,
                new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(volTS));

        final Date maturity = today.add(new Period(1, TimeUnit.Years));
        final StrikedTypePayoff payoff = new PlainVanillaPayoff(Option.Type.Put, K);

        // Full American exercise
        final Exercise fullExercise = new AmericanExercise(today, maturity);
        final VanillaOption fullOption = new VanillaOption(payoff, fullExercise);
        final PricingEngine fdEngine = new FdBlackScholesVanillaEngine(
                bsmProcess, 200, 200, 0, FdmSchemeDesc.Douglas());
        fullOption.setPricingEngine(fdEngine);
        final double fullPrice = fullOption.NPV();

        // European benchmark
        final Exercise euroExercise = new EuropeanExercise(maturity);
        final VanillaOption euroOption = new VanillaOption(payoff, euroExercise);
        final PricingEngine euroEngine = new AnalyticEuropeanEngine(bsmProcess);
        euroOption.setPricingEngine(euroEngine);
        final double euroPrice = euroOption.NPV();

        final double earlyExPremium = fullPrice - euroPrice;

        // Sanity: the early exercise premium should be significant for this deep ITM put with 5% rates
        assertTrue("early-exercise premium should be > 1.0, got " + earlyExPremium, earlyExPremium > 1.0);

        // Restricted exercise: only last 3 months
        final Date lateStart = maturity.sub(new Period(3, TimeUnit.Months));
        final Exercise lateExercise = new AmericanExercise(lateStart, maturity);
        final VanillaOption lateOption = new VanillaOption(payoff, lateExercise);
        lateOption.setPricingEngine(fdEngine);
        final double latePrice = lateOption.NPV();

        // The restricted option should be worth less than full American
        assertTrue("Restricting exercise window should reduce price: full=" + fullPrice + " late=" + latePrice,
                fullPrice - latePrice > 0.01);

        // The restricted option should be worth more than European (some early-exercise value remains)
        assertTrue("Restricted American should exceed European: late=" + latePrice + " euro=" + euroPrice,
                latePrice > euroPrice + 0.01);

        // Monotonicity: longer exercise window -> higher price
        final Date midStart = maturity.sub(new Period(6, TimeUnit.Months));
        final Exercise midExercise = new AmericanExercise(midStart, maturity);
        final VanillaOption midOption = new VanillaOption(payoff, midExercise);
        midOption.setPricingEngine(fdEngine);
        final double midPrice = midOption.NPV();

        assertTrue("Wider window should give higher price: 6M=" + midPrice + " 3M=" + latePrice,
                midPrice >= latePrice - 1e-8);
        assertTrue("Full window should give highest price: full=" + fullPrice + " 6M=" + midPrice,
                fullPrice >= midPrice - 1e-8);
    }

    /**
     * Faithful port of {@code test-suite/americanoption.cpp:2257}
     * {@code BOOST_AUTO_TEST_CASE(testBaroneAdesiWhaleyNegativeRates)}.
     *
     * <p>Issue #1291: BAW crashes with a cryptic error when rates are negative. The v1.42.1 engine
     * throws a clear {@code QL_REQUIRE} message instead. This test asserts both put-with-negative-rate
     * and call-with-positive-dividend (rates still negative) raise an exception mentioning
     * "negative interest rates".
     */
    @Test
    public void testBaroneAdesiWhaleyNegativeRates() {
        QL.info("Testing Barone-Adesi-Whaley engine with negative rates...");

        final Date today = new Settings().evaluationDate();
        final DayCounter dc = new Actual360();

        final SimpleQuote spot = new SimpleQuote(36.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final SimpleQuote rRate = new SimpleQuote(-0.012);
        final SimpleQuote vol = new SimpleQuote(0.20);

        final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(Utilities.flatRate(today, qRate, dc)),
                new Handle<YieldTermStructure>(Utilities.flatRate(today, rRate, dc)),
                new Handle<BlackVolTermStructure>(Utilities.flatVol(today, vol, dc)));

        final StrikedTypePayoff putPayoff = new PlainVanillaPayoff(Option.Type.Put, 40.0);
        final Date exDate = today.add(new Period(1, TimeUnit.Years));
        final Exercise exercise = new AmericanExercise(today, exDate);

        final VanillaOption putOption = new VanillaOption(putPayoff, exercise);
        putOption.setPricingEngine(new BaroneAdesiWhaleyApproximationEngine(stochProcess));

        try {
            putOption.NPV();
            fail("expected QL_REQUIRE failure for negative interest rates on put");
        } catch (final RuntimeException e) {
            assertTrue("error message should mention 'negative interest rates'; got: " + e.getMessage(),
                    e.getMessage() != null && e.getMessage().contains("negative interest rates"));
        }

        // also verify with a call and positive dividends
        final StrikedTypePayoff callPayoff = new PlainVanillaPayoff(Option.Type.Call, 40.0);
        qRate.setValue(0.06);
        final VanillaOption callOption = new VanillaOption(callPayoff, exercise);
        callOption.setPricingEngine(new BaroneAdesiWhaleyApproximationEngine(stochProcess));

        try {
            callOption.NPV();
            fail("expected QL_REQUIRE failure for negative interest rates on call");
        } catch (final RuntimeException e) {
            assertTrue("error message should mention 'negative interest rates'; got: " + e.getMessage(),
                    e.getMessage() != null && e.getMessage().contains("negative interest rates"));
        }
    }

    //
    // private inner classes
    //

    private class AmericanOptionData {

        private final Option.Type type;
        private final double /* @Real */strike;
        private final double /* @Real */s; // spot
        private final double /* @Rate */q; // dividend
        private final double /* @Rate */r; // risk-free rate
        private final double /* @Time */t; // time to maturity
        private final double /* @Volatility */v; // volatility
        private final double /* @Real */result; // expected result

        public AmericanOptionData(
                final Option.Type type,
                final double strike,
                final double s,
                final double q,
                final double r,
                final double t,
                final double v,
                final double result) {
            this.type = type;
            this.strike = strike;
            this.s = s;
            this.q = q;
            this.r = r;
            this.t = t;
            this.v = v;
            this.result = result;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("Type: " + type);
            builder.append(" Strike: " + strike);
            builder.append(" Spot: " + s);
            builder.append(" DividendYield: " + q);
            builder.append(" Riskfree: " + r);
            builder.append(" TTm: " + t);
            builder.append(" Vol: " + v);
            builder.append(" result: " + result);

            return builder.toString();
        }
    }

}
