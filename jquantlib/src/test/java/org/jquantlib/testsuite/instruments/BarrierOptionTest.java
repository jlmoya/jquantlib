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

package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.experimental.barrieroption.VannaVolgaBarrierEngine;
import org.jquantlib.experimental.fx.DeltaVolQuote;
import org.jquantlib.instruments.BarrierOption;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.barrier.AnalyticBarrierEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Test;

public class BarrierOptionTest {


    public BarrierOptionTest() {
        QL.info("::::: "+this.getClass().getSimpleName()+" :::::");
    }


    @Test
    public void testHaugValues() {

        QL.info("Testing barrier options against Haug's values...");

        final NewBarrierOptionData values[] = {
                //
                // The data below are from "Option pricing formulas", E.G. Haug, McGraw-Hill 1998 pag. 72
                //
                //     barrierType, barrier, rebate,         type, strike,     s,    q,    r,    t,    v,  result, tol
                new NewBarrierOptionData( BarrierType.DownOut,    95.0,    3.0, Option.Type.Call,     90, 100.0, 0.04, 0.08, 0.50, 0.25,  9.0246, 1.0e-4),
                new NewBarrierOptionData( BarrierType.DownOut,    95.0,    3.0, Option.Type.Call,    100, 100.0, 0.04, 0.08, 0.50, 0.25,  6.7924, 1.0e-4),
                new NewBarrierOptionData( BarrierType.DownOut,    95.0,    3.0, Option.Type.Call,    110, 100.0, 0.04, 0.08, 0.50, 0.25,  4.8759, 1.0e-4),
                new NewBarrierOptionData( BarrierType.DownOut,   100.0,    3.0, Option.Type.Call,     90, 100.0, 0.04, 0.08, 0.50, 0.25,  3.0000, 1.0e-4),
                new NewBarrierOptionData( BarrierType.DownOut,   100.0,    3.0, Option.Type.Call,    100, 100.0, 0.04, 0.08, 0.50, 0.25,  3.0000, 1.0e-4),
                new NewBarrierOptionData( BarrierType.DownOut,   100.0,    3.0, Option.Type.Call,    110, 100.0, 0.04, 0.08, 0.50, 0.25,  3.0000, 1.0e-4),
                new NewBarrierOptionData( BarrierType.UpOut,     105.0,    3.0, Option.Type.Call,     90, 100.0, 0.04, 0.08, 0.50, 0.25,  2.6789, 1.0e-4),
                new NewBarrierOptionData( BarrierType.UpOut,     105.0,    3.0, Option.Type.Call,    100, 100.0, 0.04, 0.08, 0.50, 0.25,  2.3580, 1.0e-4),
                new NewBarrierOptionData( BarrierType.UpOut,     105.0,    3.0, Option.Type.Call,    110, 100.0, 0.04, 0.08, 0.50, 0.25,  2.3453, 1.0e-4),

                new NewBarrierOptionData( BarrierType.DownIn,     95.0,    3.0, Option.Type.Call,    90, 100.0, 0.04, 0.08, 0.50, 0.25,  7.7627, 1.0e-4),
                new NewBarrierOptionData( BarrierType.DownIn,     95.0,    3.0, Option.Type.Call,   100, 100.0, 0.04, 0.08, 0.50, 0.25,  4.0109, 1.0e-4),
                new NewBarrierOptionData( BarrierType.DownIn,     95.0,    3.0, Option.Type.Call,   110, 100.0, 0.04, 0.08, 0.50, 0.25,  2.0576, 1.0e-4),
                new NewBarrierOptionData( BarrierType.DownIn,    100.0,    3.0, Option.Type.Call,    90, 100.0, 0.04, 0.08, 0.50, 0.25, 13.8333, 1.0e-4),
                new NewBarrierOptionData( BarrierType.DownIn,    100.0,    3.0, Option.Type.Call,   100, 100.0, 0.04, 0.08, 0.50, 0.25,  7.8494, 1.0e-4),
                new NewBarrierOptionData( BarrierType.DownIn,    100.0,    3.0, Option.Type.Call,   110, 100.0, 0.04, 0.08, 0.50, 0.25,  3.9795, 1.0e-4),
                new NewBarrierOptionData( BarrierType.UpIn,      105.0,    3.0, Option.Type.Call,    90, 100.0, 0.04, 0.08, 0.50, 0.25, 14.1112, 1.0e-4),
                new NewBarrierOptionData( BarrierType.UpIn,      105.0,    3.0, Option.Type.Call,   100, 100.0, 0.04, 0.08, 0.50, 0.25,  8.4482, 1.0e-4),
                new NewBarrierOptionData( BarrierType.UpIn,      105.0,    3.0, Option.Type.Call,   110, 100.0, 0.04, 0.08, 0.50, 0.25,  4.5910, 1.0e-4),

                new NewBarrierOptionData( BarrierType.DownOut,    95.0,    3.0, Option.Type.Call,    90, 100.0, 0.04, 0.08, 0.50, 0.30,  8.8334, 1.0e-4),
                new NewBarrierOptionData( BarrierType.DownOut,    95.0,    3.0, Option.Type.Call,   100, 100.0, 0.04, 0.08, 0.50, 0.30,  7.0285, 1.0e-4),
                new NewBarrierOptionData( BarrierType.DownOut,    95.0,    3.0, Option.Type.Call,   110, 100.0, 0.04, 0.08, 0.50, 0.30,  5.4137, 1.0e-4),
                new NewBarrierOptionData( BarrierType.DownOut,   100.0,    3.0, Option.Type.Call,    90, 100.0, 0.04, 0.08, 0.50, 0.30,  3.0000, 1.0e-4),
                new NewBarrierOptionData( BarrierType.DownOut,   100.0,    3.0, Option.Type.Call,   100, 100.0, 0.04, 0.08, 0.50, 0.30,  3.0000, 1.0e-4),
                new NewBarrierOptionData( BarrierType.DownOut,   100.0,    3.0, Option.Type.Call,   110, 100.0, 0.04, 0.08, 0.50, 0.30,  3.0000, 1.0e-4),
                new NewBarrierOptionData( BarrierType.UpOut,     105.0,    3.0, Option.Type.Call,    90, 100.0, 0.04, 0.08, 0.50, 0.30,  2.6341, 1.0e-4),
                new NewBarrierOptionData( BarrierType.UpOut,     105.0,    3.0, Option.Type.Call,   100, 100.0, 0.04, 0.08, 0.50, 0.30,  2.4389, 1.0e-4),
                new NewBarrierOptionData( BarrierType.UpOut,     105.0,    3.0, Option.Type.Call,   110, 100.0, 0.04, 0.08, 0.50, 0.30,  2.4315, 1.0e-4),

                new NewBarrierOptionData( BarrierType.DownIn,     95.0,    3.0, Option.Type.Call,    90, 100.0, 0.04, 0.08, 0.50, 0.30,  9.0093, 1.0e-4),
                new NewBarrierOptionData( BarrierType.DownIn,     95.0,    3.0, Option.Type.Call,   100, 100.0, 0.04, 0.08, 0.50, 0.30,  5.1370, 1.0e-4),
                new NewBarrierOptionData( BarrierType.DownIn,     95.0,    3.0, Option.Type.Call,   110, 100.0, 0.04, 0.08, 0.50, 0.30,  2.8517, 1.0e-4),
                new NewBarrierOptionData( BarrierType.DownIn,    100.0,    3.0, Option.Type.Call,    90, 100.0, 0.04, 0.08, 0.50, 0.30, 14.8816, 1.0e-4),
                new NewBarrierOptionData( BarrierType.DownIn,    100.0,    3.0, Option.Type.Call,   100, 100.0, 0.04, 0.08, 0.50, 0.30,  9.2045, 1.0e-4),
                new NewBarrierOptionData( BarrierType.DownIn,    100.0,    3.0, Option.Type.Call,   110, 100.0, 0.04, 0.08, 0.50, 0.30,  5.3043, 1.0e-4),
                new NewBarrierOptionData( BarrierType.UpIn,      105.0,    3.0, Option.Type.Call,    90, 100.0, 0.04, 0.08, 0.50, 0.30, 15.2098, 1.0e-4),
                new NewBarrierOptionData( BarrierType.UpIn,      105.0,    3.0, Option.Type.Call,   100, 100.0, 0.04, 0.08, 0.50, 0.30,  9.7278, 1.0e-4),
                new NewBarrierOptionData( BarrierType.UpIn,      105.0,    3.0, Option.Type.Call,   110, 100.0, 0.04, 0.08, 0.50, 0.30,  5.8350, 1.0e-4),

                new NewBarrierOptionData( BarrierType.DownOut,    95.0,    3.0,  Option.Type.Put,    90, 100.0, 0.04, 0.08, 0.50, 0.25,  2.2798, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.DownOut,    95.0,    3.0,  Option.Type.Put,   100, 100.0, 0.04, 0.08, 0.50, 0.25,  2.2947, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.DownOut,    95.0,    3.0,  Option.Type.Put,   110, 100.0, 0.04, 0.08, 0.50, 0.25,  2.6252, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.DownOut,   100.0,    3.0,  Option.Type.Put,    90, 100.0, 0.04, 0.08, 0.50, 0.25,  3.0000, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.DownOut,   100.0,    3.0,  Option.Type.Put,   100, 100.0, 0.04, 0.08, 0.50, 0.25,  3.0000, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.DownOut,   100.0,    3.0,  Option.Type.Put,   110, 100.0, 0.04, 0.08, 0.50, 0.25,  3.0000, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.UpOut,     105.0,    3.0,  Option.Type.Put,    90, 100.0, 0.04, 0.08, 0.50, 0.25,  3.7760, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.UpOut,     105.0,    3.0,  Option.Type.Put,   100, 100.0, 0.04, 0.08, 0.50, 0.25,  5.4932, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.UpOut,     105.0,    3.0,  Option.Type.Put,   110, 100.0, 0.04, 0.08, 0.50, 0.25,  7.5187, 1.0e-4 ),

                new NewBarrierOptionData( BarrierType.DownIn,     95.0,    3.0,  Option.Type.Put,    90, 100.0, 0.04, 0.08, 0.50, 0.25,  2.9586, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.DownIn,     95.0,    3.0,  Option.Type.Put,   100, 100.0, 0.04, 0.08, 0.50, 0.25,  6.5677, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.DownIn,     95.0,    3.0,  Option.Type.Put,   110, 100.0, 0.04, 0.08, 0.50, 0.25, 11.9752, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.DownIn,    100.0,    3.0,  Option.Type.Put,    90, 100.0, 0.04, 0.08, 0.50, 0.25,  2.2845, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.DownIn,    100.0,    3.0,  Option.Type.Put,   100, 100.0, 0.04, 0.08, 0.50, 0.25,  5.9085, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.DownIn,    100.0,    3.0,  Option.Type.Put,   110, 100.0, 0.04, 0.08, 0.50, 0.25, 11.6465, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.UpIn,      105.0,    3.0,  Option.Type.Put,    90, 100.0, 0.04, 0.08, 0.50, 0.25,  1.4653, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.UpIn,      105.0,    3.0,  Option.Type.Put,   100, 100.0, 0.04, 0.08, 0.50, 0.25,  3.3721, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.UpIn,      105.0,    3.0,  Option.Type.Put,   110, 100.0, 0.04, 0.08, 0.50, 0.25,  7.0846, 1.0e-4 ),

                new NewBarrierOptionData( BarrierType.DownOut,    95.0,    3.0,  Option.Type.Put,    90, 100.0, 0.04, 0.08, 0.50, 0.30,  2.4170, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.DownOut,    95.0,    3.0,  Option.Type.Put,   100, 100.0, 0.04, 0.08, 0.50, 0.30,  2.4258, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.DownOut,    95.0,    3.0,  Option.Type.Put,   110, 100.0, 0.04, 0.08, 0.50, 0.30,  2.6246, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.DownOut,   100.0,    3.0,  Option.Type.Put,    90, 100.0, 0.04, 0.08, 0.50, 0.30,  3.0000, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.DownOut,   100.0,    3.0,  Option.Type.Put,   100, 100.0, 0.04, 0.08, 0.50, 0.30,  3.0000, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.DownOut,   100.0,    3.0,  Option.Type.Put,   110, 100.0, 0.04, 0.08, 0.50, 0.30,  3.0000, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.UpOut,     105.0,    3.0,  Option.Type.Put,    90, 100.0, 0.04, 0.08, 0.50, 0.30,  4.2293, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.UpOut,     105.0,    3.0,  Option.Type.Put,   100, 100.0, 0.04, 0.08, 0.50, 0.30,  5.8032, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.UpOut,     105.0,    3.0,  Option.Type.Put,   110, 100.0, 0.04, 0.08, 0.50, 0.30,  7.5649, 1.0e-4 ),

                new NewBarrierOptionData( BarrierType.DownIn,     95.0,    3.0,  Option.Type.Put,    90, 100.0, 0.04, 0.08, 0.50, 0.30,  3.8769, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.DownIn,     95.0,    3.0,  Option.Type.Put,   100, 100.0, 0.04, 0.08, 0.50, 0.30,  7.7989, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.DownIn,     95.0,    3.0,  Option.Type.Put,   110, 100.0, 0.04, 0.08, 0.50, 0.30, 13.3078, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.DownIn,    100.0,    3.0,  Option.Type.Put,    90, 100.0, 0.04, 0.08, 0.50, 0.30,  3.3328, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.DownIn,    100.0,    3.0,  Option.Type.Put,   100, 100.0, 0.04, 0.08, 0.50, 0.30,  7.2636, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.DownIn,    100.0,    3.0,  Option.Type.Put,   110, 100.0, 0.04, 0.08, 0.50, 0.30, 12.9713, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.UpIn,      105.0,    3.0,  Option.Type.Put,    90, 100.0, 0.04, 0.08, 0.50, 0.30,  2.0658, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.UpIn,      105.0,    3.0,  Option.Type.Put,   100, 100.0, 0.04, 0.08, 0.50, 0.30,  4.4226, 1.0e-4 ),
                new NewBarrierOptionData( BarrierType.UpIn,      105.0,    3.0,  Option.Type.Put,   110, 100.0, 0.04, 0.08, 0.50, 0.30,  8.3686, 1.0e-4 ),

                //
                //  Data from "Going to Extreme: Correcting Simulation Bias in Exotic Option Valuation"
                //  D.R. Beaglehole, P.H. Dybvig and G. Zhou
                //  Financial Analysts Journal; Jan / Feb 1997; 53, 1
                //
                //    barrierType, barrier, rebate,         type, strike,     s,    q,    r,    t,    v,  result, tol
                //---- new NewBarrierOptionData( BarrierType.DownOut,    45.0,    0.0,  Option.Type.PUT,     50,  50.0,-0.05, 0.10, 0.25, 0.50,   4.032, 1.0e-3 ),
                //---- new NewBarrierOptionData( BarrierType.DownOut,    45.0,    0.0,  Option.Type.PUT,     50,  50.0,-0.05, 0.10, 1.00, 0.50,   5.477, 1.0e-3 )
        };

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final SimpleQuote           spot  = new SimpleQuote(0.0);
        final SimpleQuote           qRate = new SimpleQuote(0.0);
        final YieldTermStructure    qTS   = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote           rRate = new SimpleQuote(0.0);
        final YieldTermStructure    rTS   = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote           vol   = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

        for (final NewBarrierOptionData value : values) {
            final Date exDate = today.add( timeToDays(value.t) );
            final Exercise exercise = new EuropeanExercise(exDate);

            spot.setValue(value.s);
            qRate.setValue(value.q);
            rRate.setValue(value.r);
            vol.setValue(value.v);

            final StrikedTypePayoff payoff = new PlainVanillaPayoff(value.type, value.strike);

            final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                    new Handle<Quote>(spot),
                    new Handle<YieldTermStructure>(qTS),
                    new Handle<YieldTermStructure>(rTS),
                    new Handle<BlackVolTermStructure>(volTS));
            final PricingEngine engine = new AnalyticBarrierEngine(stochProcess);

            final BarrierOption barrierOption = new BarrierOption(value.barrierType, value.barrier, value.rebate, payoff, exercise);
            barrierOption.setPricingEngine(engine);

            final double calculated = barrierOption.NPV();
            final double expected = value.result;
            final double error = Math.abs(calculated-expected);
            if (error>value.tol) {
                REPORT_FAILURE("value", value.barrierType, value.barrier,
                        value.rebate, payoff, exercise, value.s,
                        value.q, value.r, today, value.v,
                        expected, calculated, error, value.tol);
            }

        }
    }

    @Test
    public void testBabsiriValues() {
        QL.info("Testing barrier options against Babsiri's values...");

        /**
         * Data from
         * "Simulating Path-Dependent Options: A New Approach"
         * - M. El Babsiri and G. Noel
         * Journal of Derivatives; Winter 1998; 6, 2
         */
        final BarrierOptionData values[] = {
                new BarrierOptionData( BarrierType.DownIn,   0.10,   100,  90,   0.07187,  0.0),
                new BarrierOptionData( BarrierType.DownIn,   0.15,   100,  90,   0.60638,  0.0),
                new BarrierOptionData( BarrierType.DownIn,   0.20,   100,  90,   1.64005,  0.0),
                new BarrierOptionData( BarrierType.DownIn,   0.25,   100,  90,   2.98495,  0.0),
                new BarrierOptionData( BarrierType.DownIn,   0.30,   100,  90,   4.50952,  0.0),
                new BarrierOptionData( BarrierType.UpIn,     0.10,   100,  110,  4.79148,  0.0),
                new BarrierOptionData( BarrierType.UpIn,     0.15,   100,  110,   7.08268,  0.0 ),
                new BarrierOptionData( BarrierType.UpIn,     0.20,   100,  110,   9.11008,  0.0 ),
                new BarrierOptionData( BarrierType.UpIn,     0.25,   100,  110,  11.06148,  0.0 ),
                new BarrierOptionData( BarrierType.UpIn,     0.30,   100,  110,  12.98351,  0.0 )
        };

        final double underlyingPrice = 100.0;
        final double rebate = 0.0;
        final double r = 0.05;
        final double q = 0.02;

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final Quote                 underlying = new SimpleQuote(underlyingPrice);
        final Quote                 qH_SME     = new SimpleQuote(q);
        final YieldTermStructure    qTS        = Utilities.flatRate(today, qH_SME, dc);
        final Quote                 rH_SME     = new SimpleQuote(r);
        final YieldTermStructure    rTS        = Utilities.flatRate(today, rH_SME, dc);
        final SimpleQuote           volatility = new SimpleQuote(0.10);
        final BlackVolTermStructure volTS      = Utilities.flatVol(today, volatility, dc);

        final Date exDate = today.add(360);
        final Exercise exercise = new EuropeanExercise(exDate);

        for (final BarrierOptionData value : values) {
            volatility.setValue(value.volatility);
            final StrikedTypePayoff callPayoff = new PlainVanillaPayoff(Option.Type.Call, value.strike);

            final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                    new Handle<Quote>(underlying),
                    new Handle<YieldTermStructure>(qTS),
                    new Handle<YieldTermStructure>(rTS),
                    new Handle<BlackVolTermStructure>(volTS));

            final PricingEngine engine = new AnalyticBarrierEngine(stochProcess);

            final BarrierOption barrierCallOption = new BarrierOption(value.barrierType, value.barrier, rebate, callPayoff, exercise);
            barrierCallOption.setPricingEngine(engine);

            final double calculated = barrierCallOption.NPV();
            final double expected = value.callValue;
            final double error = Math.abs(calculated - expected);
            final double maxErrorAllowed = 1.0e-3;

            if (error > maxErrorAllowed) {
                REPORT_FAILURE("value", value.barrierType, value.barrier, rebate, callPayoff, exercise, underlyingPrice, q,
                        r, today, value.volatility, expected, calculated, error, maxErrorAllowed);
            }
        }
    }

    @Test
    public void testBeagleholeValues() {

        QL.info("Testing barrier options against Beaglehole's values...");

        /**
         * Data from
         * "Going to Extreme: Correcting Simulation Bias in Exotic Option Valuation"
         * - D.R. Beaglehole, P.H. Dybvig and G. Zhou
         * Financial Analysts Journal; Jan / Feb 1997; 53, 1
         */
        final BarrierOptionData values[] = {
                new BarrierOptionData(BarrierType.DownOut, 0.50,   50,      45,  5.477,  0.0)
        };

        final double underlyingPrice = 50.0;
        final double rebate = 0.0;
        final double r = Math.log(1.1);
        final double q = 0.00;

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final Quote                 underlying = new SimpleQuote(underlyingPrice);
        final Quote                 qH_SME     = new SimpleQuote(q);
        final YieldTermStructure    qTS        = Utilities.flatRate(today, qH_SME, dc);
        final Quote                 rH_SME     = new SimpleQuote(r);
        final YieldTermStructure    rTS        = Utilities.flatRate(today, rH_SME, dc);
        final SimpleQuote           volatility = new SimpleQuote(0.10);
        final BlackVolTermStructure volTS      = Utilities.flatVol(today, volatility, dc);

        final Date exDate = today.add(360);

        final Exercise exercise = new EuropeanExercise(exDate);

        for (final BarrierOptionData value : values) {
            volatility.setValue(value.volatility);
            final StrikedTypePayoff callPayoff = new PlainVanillaPayoff(Option.Type.Call, value.strike);

            final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                    new Handle<Quote>(underlying),
                    new Handle<YieldTermStructure>(qTS),
                    new Handle<YieldTermStructure>(rTS),
                    new Handle<BlackVolTermStructure>(volTS));
            final PricingEngine engine = new AnalyticBarrierEngine(stochProcess);


            final BarrierOption barrierCallOption = new BarrierOption(value.barrierType, value.barrier, rebate, callPayoff, exercise);
            barrierCallOption.setPricingEngine(engine);

            final double calculated = barrierCallOption.NPV();
            final double expected = value.callValue;
            final double error = Math.abs(calculated - expected);
            final double maxErrorAllowed = 1.0e-3;

            if (error > maxErrorAllowed) {
                REPORT_FAILURE("value", value.barrierType, value.barrier, rebate, callPayoff, exercise, underlyingPrice, q,
                        r, today, value.volatility, expected, calculated, error, maxErrorAllowed);
            }
        }


        //TODO: MC Barrier engine not implemented yet.
        /*

        final double maxMcRelativeErrorAllowed = 0.01;
        final int timeSteps = 1;
        final boolean brownianBridge = true;
        final boolean antitheticVariate = false;
        final boolean controlVariate = false;
        final int requiredSamples = 131071; //2^17-1
        final double requiredTolerance;
        final int maxSamples = 1048575; // 2^20-1
        final boolean isBiased = false;
        final double seed = 10;

        boost::shared_ptr<PricingEngine> mcEngine(
                new MCBarrierEngine<LowDiscrepancy>(timeSteps, brownianBridge,
                                                antitheticVariate, controlVariate,
                                                requiredSamples, requiredTolerance,
                                                maxSamples, isBiased, seed));

            barrierCallOption.setPricingEngine(mcEngine);
            calculated = barrierCallOption.NPV();
            error = std::fabs(calculated-expected)/expected;
            if (error>maxMcRelativeErrorAllowed) {
                REPORT_FAILURE("value", values[i].type, values[i].barrier,
                               rebate, callPayoff, exercise, underlyingPrice,
                               q, r, today, values[i].volatility,
                               expected, calculated, error,
                               maxMcRelativeErrorAllowed);
            }
         */
    }



// http://bugs.jquantlib.org/view.php?id=459
//
//    void BarrierOptionTest::testPerturbative() {
//        BOOST_MESSAGE("Testing perturbative engine for barrier options...");
//
//        Real S = 100.0;
//        Real rebate = 0.0;
//        Rate r = 0.03;
//        Rate q = 0.02;
//
//        DayCounter dc = Actual360();
//        Date today = Date::todaysDate();
//
                // ---- This is future Java code, to be inserted when this code were translated
                //FIXME: http://bugs.jquantlib.org/view.php?id=460
                //new Settings().setEvaluationDate(today);
//
//        boost::shared_ptr<SimpleQuote> underlying(new SimpleQuote(S));
//        boost::shared_ptr<YieldTermStructure> qTS = flatRate(today, q, dc);
//        boost::shared_ptr<YieldTermStructure> rTS = flatRate(today, r, dc);
//
//        std::vector<Date> dates(2);
//        std::vector<Volatility> vols(2);
//
//        dates[0] = today + 90;  vols[0] = 0.105;
//        dates[1] = today + 180; vols[1] = 0.11;
//
//        boost::shared_ptr<BlackVolTermStructure> volTS(
//                                  new BlackVarianceCurve(today, dates, vols, dc));
//
//        boost::shared_ptr<BlackScholesMertonProcess> stochProcess(
//            new BlackScholesMertonProcess(Handle<Quote>(underlying),
//                                          Handle<YieldTermStructure>(qTS),
//                                          Handle<YieldTermStructure>(rTS),
//                                          Handle<BlackVolTermStructure>(volTS)));
//
//        Real strike = 101.0;
//        Real barrier = 101.0;
//        Date exDate = today+180;
//
//        boost::shared_ptr<Exercise> exercise(new EuropeanExercise(exDate));
//        boost::shared_ptr<StrikedTypePayoff> payoff(
//                                     new PlainVanillaPayoff(Option::Put, strike));
//
//        BarrierOption option(Barrier::UpOut, barrier, rebate, payoff, exercise);
//
//        Natural order = 0;
//        bool zeroGamma = false;
//        boost::shared_ptr<PricingEngine> engine(
//             new PerturbativeBarrierOptionEngine(stochProcess, order, zeroGamma));
//
//        option.setPricingEngine(engine);
//
//        Real calculated = option.NPV();
//        Real expected = 0.897365;
//        Real tolerance = 1.0e-6;
//        if (std::fabs(calculated-expected) > tolerance) {
//            BOOST_ERROR("Failed to reproduce expected value"
//                        << "\n  calculated: " << std::setprecision(5) << calculated
//                        << "\n  expected:   " << std::setprecision(5) << expected);
//        }
//
//        order = 1;
//        engine = boost::shared_ptr<PricingEngine>(
//             new PerturbativeBarrierOptionEngine(stochProcess, order, zeroGamma));
//
//        option.setPricingEngine(engine);
//
//        calculated = option.NPV();
//        expected = 0.894374;
//        if (std::fabs(calculated-expected) > tolerance) {
//            BOOST_ERROR("Failed to reproduce expected value"
//                        << "\n  calculated: " << std::setprecision(5) << calculated
//                        << "\n  expected:   " << std::setprecision(5) << expected);
//        }
//
//        order = 2;
//        engine = boost::shared_ptr<PricingEngine>(
//             new PerturbativeBarrierOptionEngine(stochProcess, order, zeroGamma));
//
//        option.setPricingEngine(engine);
//
//        calculated = option.NPV();
//        expected = 0.894375;
//        if (std::fabs(calculated-expected) > tolerance) {
//            BOOST_ERROR("Failed to reproduce expected value"
//                        << "\n  calculated: " << std::setprecision(5) << calculated
//                        << "\n  expected:   " << std::setprecision(5) << expected);
//        }
//    }


    /**
     * Faithful port of {@code test-suite/barrieroption.cpp:1374}
     * {@code BOOST_AUTO_TEST_CASE(testVannaVolgaSimpleBarrierValues)}.
     *
     * <p>Validates {@link VannaVolgaBarrierEngine} (single barrier) against the
     * reference FX-option values tabulated in v1.42.1. The fixture comprises
     * 150+ rows covering UpOut/UpIn/DownOut/DownIn at strikes spanning the
     * smile and two maturities (1Y, 2Y). C++ tolerance is {@code 1e-4} for every row.
     */
    @Test
    public void testVannaVolgaSimpleBarrierValues() {
        QL.info("Testing barrier FX options against Vanna/Volga values...");

        final BarrierFxOptionData values[] = new BarrierFxOptionData[] {
                // barrierType, barrier, rebate, type, strike, s, q, r, t, vol25Put, volAtm, vol25Call, vol, result, tol
                new BarrierFxOptionData(BarrierType.UpOut, 1.5, 0, Option.Type.Call, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.148127, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpOut, 1.5, 0, Option.Type.Call, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.075943, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpOut, 1.5, 0, Option.Type.Call, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0.0274771, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpOut, 1.5, 0, Option.Type.Call, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.00573, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpOut, 1.5, 0, Option.Type.Call, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.00012, 1.0e-4),

                new BarrierFxOptionData(BarrierType.UpOut, 1.5, 0, Option.Type.Put, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.00697606, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpOut, 1.5, 0, Option.Type.Put, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.020078, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpOut, 1.5, 0, Option.Type.Put, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0.0489395, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpOut, 1.5, 0, Option.Type.Put, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.0969877, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpOut, 1.5, 0, Option.Type.Put, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.157, 1.0e-4),

                new BarrierFxOptionData(BarrierType.UpIn, 1.5, 0, Option.Type.Call, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.0322202, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpIn, 1.5, 0, Option.Type.Call, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.0241491, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpIn, 1.5, 0, Option.Type.Call, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0.0164275, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpIn, 1.5, 0, Option.Type.Call, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.01, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpIn, 1.5, 0, Option.Type.Call, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.00489, 1.0e-4),

                new BarrierFxOptionData(BarrierType.UpIn, 1.5, 0, Option.Type.Put, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.000560713, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpIn, 1.5, 0, Option.Type.Put, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.000546804, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpIn, 1.5, 0, Option.Type.Put, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0.000130649, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpIn, 1.5, 0, Option.Type.Put, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.000300828, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpIn, 1.5, 0, Option.Type.Put, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.00135, 1.0e-4),

                new BarrierFxOptionData(BarrierType.DownOut, 1.1, 0, Option.Type.Call, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.17746, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.1, 0, Option.Type.Call, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.0994142, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.1, 0, Option.Type.Call, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0.0439, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.1, 0, Option.Type.Call, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.01574, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.1, 0, Option.Type.Call, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.00501, 1.0e-4),

                new BarrierFxOptionData(BarrierType.DownOut, 1.3, 0, Option.Type.Call, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.00612, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.3, 0, Option.Type.Call, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.00426, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.3, 0, Option.Type.Call, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0.00257, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.3, 0, Option.Type.Call, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.00122, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.3, 0, Option.Type.Call, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.00045, 1.0e-4),

                new BarrierFxOptionData(BarrierType.DownOut, 1.1, 0, Option.Type.Put, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.00022, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.1, 0, Option.Type.Put, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.00284, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.1, 0, Option.Type.Put, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0.02032, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.1, 0, Option.Type.Put, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.058235, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.1, 0, Option.Type.Put, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.109432, 1.0e-4),

                new BarrierFxOptionData(BarrierType.DownOut, 1.3, 0, Option.Type.Put, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.3, 0, Option.Type.Put, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.3, 0, Option.Type.Put, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.3, 0, Option.Type.Put, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.00017, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.3, 0, Option.Type.Put, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.00083, 1.0e-4),

                new BarrierFxOptionData(BarrierType.DownIn, 1.1, 0, Option.Type.Call, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.00289, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.1, 0, Option.Type.Call, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.00067784, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.1, 0, Option.Type.Call, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.1, 0, Option.Type.Call, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.1, 0, Option.Type.Call, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0, 1.0e-4),

                new BarrierFxOptionData(BarrierType.DownIn, 1.3, 0, Option.Type.Call, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.17423, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.3, 0, Option.Type.Call, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.09584, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.3, 0, Option.Type.Call, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0.04133, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.3, 0, Option.Type.Call, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.01452, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.3, 0, Option.Type.Call, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.00456, 1.0e-4),

                new BarrierFxOptionData(BarrierType.DownIn, 1.1, 0, Option.Type.Put, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.00732, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.1, 0, Option.Type.Put, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.01778, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.1, 0, Option.Type.Put, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0.02875, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.1, 0, Option.Type.Put, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.0390535, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.1, 0, Option.Type.Put, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.0489236, 1.0e-4),

                new BarrierFxOptionData(BarrierType.DownIn, 1.3, 0, Option.Type.Put, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.00753, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.3, 0, Option.Type.Put, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.02062, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.3, 0, Option.Type.Put, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0.04907, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.3, 0, Option.Type.Put, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.09711, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.3, 0, Option.Type.Put, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.15752, 1.0e-4),

                new BarrierFxOptionData(BarrierType.UpOut, 1.6, 0, Option.Type.Call, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.20493, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpOut, 1.6, 0, Option.Type.Call, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.105577, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpOut, 1.6, 0, Option.Type.Call, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.0358872, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpOut, 1.6, 0, Option.Type.Call, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.00634958, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpOut, 1.6, 0, Option.Type.Call, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0, 1.0e-4),

                new BarrierFxOptionData(BarrierType.UpOut, 1.6, 0, Option.Type.Put, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.0108218, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpOut, 1.6, 0, Option.Type.Put, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.0313339, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpOut, 1.6, 0, Option.Type.Put, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.0751237, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpOut, 1.6, 0, Option.Type.Put, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.153407, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpOut, 1.6, 0, Option.Type.Put, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.253767, 1.0e-4),

                new BarrierFxOptionData(BarrierType.UpIn, 1.6, 0, Option.Type.Call, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.05402, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpIn, 1.6, 0, Option.Type.Call, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.0410069, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpIn, 1.6, 0, Option.Type.Call, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.0279562, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpIn, 1.6, 0, Option.Type.Call, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.0173055, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpIn, 1.6, 0, Option.Type.Call, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.00764, 1.0e-4),

                new BarrierFxOptionData(BarrierType.UpIn, 1.6, 0, Option.Type.Put, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.000962737, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpIn, 1.6, 0, Option.Type.Put, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.00102637, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpIn, 1.6, 0, Option.Type.Put, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.000419834, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpIn, 1.6, 0, Option.Type.Put, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.00159277, 1.0e-4),
                new BarrierFxOptionData(BarrierType.UpIn, 1.6, 0, Option.Type.Put, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.00473629, 1.0e-4),

                new BarrierFxOptionData(BarrierType.DownOut, 1, 0, Option.Type.Call, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.255098, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1, 0, Option.Type.Call, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.145701, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1, 0, Option.Type.Call, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.06384, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1, 0, Option.Type.Call, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.02366, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1, 0, Option.Type.Call, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.00764, 1.0e-4),

                new BarrierFxOptionData(BarrierType.DownOut, 1.3, 0, Option.Type.Call, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.00592, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.3, 0, Option.Type.Call, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.00421, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.3, 0, Option.Type.Call, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.00256, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.3, 0, Option.Type.Call, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.0012, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.3, 0, Option.Type.Call, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.0004, 1.0e-4),

                new BarrierFxOptionData(BarrierType.DownOut, 1, 0, Option.Type.Put, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1, 0, Option.Type.Put, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.00280549, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1, 0, Option.Type.Put, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.0279945, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1, 0, Option.Type.Put, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.0896352, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1, 0, Option.Type.Put, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.175182, 1.0e-4),

                new BarrierFxOptionData(BarrierType.DownOut, 1.3, 0, Option.Type.Put, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.00000, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.3, 0, Option.Type.Put, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.00000, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.3, 0, Option.Type.Put, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.00000, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.3, 0, Option.Type.Put, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.0002, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownOut, 1.3, 0, Option.Type.Put, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.00096, 1.0e-4),

                new BarrierFxOptionData(BarrierType.DownIn, 1, 0, Option.Type.Call, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.00384783, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1, 0, Option.Type.Call, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.000883232, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1, 0, Option.Type.Call, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1, 0, Option.Type.Call, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.00000, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1, 0, Option.Type.Call, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.00000, 1.0e-4),

                new BarrierFxOptionData(BarrierType.DownIn, 1.3, 0, Option.Type.Call, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.25302, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.3, 0, Option.Type.Call, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.14238, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.3, 0, Option.Type.Call, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.06128, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.3, 0, Option.Type.Call, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.02245, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.3, 0, Option.Type.Call, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.00725, 1.0e-4),

                new BarrierFxOptionData(BarrierType.DownIn, 1, 0, Option.Type.Put, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.01178, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1, 0, Option.Type.Put, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.0295548, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1, 0, Option.Type.Put, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.047549, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1, 0, Option.Type.Put, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.0653642, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1, 0, Option.Type.Put, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.0833221, 1.0e-4),

                new BarrierFxOptionData(BarrierType.DownIn, 1.3, 0, Option.Type.Put, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.01178, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.3, 0, Option.Type.Put, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.03236, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.3, 0, Option.Type.Put, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.07554, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.3, 0, Option.Type.Put, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.15479, 1.0e-4),
                new BarrierFxOptionData(BarrierType.DownIn, 1.3, 0, Option.Type.Put, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.25754, 1.0e-4),
        };

        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(5, Month.March, 2013);
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote vol25Put = new SimpleQuote(0.0);
        final SimpleQuote volAtm = new SimpleQuote(0.0);
        final SimpleQuote vol25Call = new SimpleQuote(0.0);

        for (final BarrierFxOptionData value : values) {
            spot.setValue(value.s);
            qRate.setValue(value.q);
            rRate.setValue(value.r);
            vol25Put.setValue(value.vol25Put);
            volAtm.setValue(value.volAtm);
            vol25Call.setValue(value.vol25Call);

            final StrikedTypePayoff payoff = new PlainVanillaPayoff(value.type, value.strike);

            final Date exDate = today.add(timeToDaysAct365(value.t));
            final Exercise exercise = new EuropeanExercise(exDate);

            final Handle<DeltaVolQuote> volAtmQuote = new Handle<DeltaVolQuote>(
                    new DeltaVolQuote(new Handle<Quote>(volAtm),
                            DeltaVolQuote.DeltaType.Fwd, value.t,
                            DeltaVolQuote.AtmType.AtmDeltaNeutral));
            final Handle<DeltaVolQuote> vol25PutQuote = new Handle<DeltaVolQuote>(
                    new DeltaVolQuote(-0.25, new Handle<Quote>(vol25Put), value.t,
                            DeltaVolQuote.DeltaType.Fwd));
            final Handle<DeltaVolQuote> vol25CallQuote = new Handle<DeltaVolQuote>(
                    new DeltaVolQuote(0.25, new Handle<Quote>(vol25Call), value.t,
                            DeltaVolQuote.DeltaType.Fwd));

            final BarrierOption barrierOption = new BarrierOption(
                    value.barrierType, value.barrier, value.rebate, payoff, exercise);

            final double bsVanillaPrice = BlackFormula.blackFormula(value.type, value.strike,
                    spot.value() * qTS.discount(value.t) / rTS.discount(value.t),
                    value.v * Math.sqrt(value.t), rTS.discount(value.t));

            final PricingEngine vannaVolgaEngine = new VannaVolgaBarrierEngine(
                    volAtmQuote, vol25PutQuote, vol25CallQuote,
                    new Handle<Quote>(spot),
                    new Handle<YieldTermStructure>(rTS),
                    new Handle<YieldTermStructure>(qTS),
                    true, bsVanillaPrice);
            barrierOption.setPricingEngine(vannaVolgaEngine);

            final double calculated = barrierOption.NPV();
            final double expected = value.result;
            final double error = Math.abs(calculated - expected);
            assertTrue(String.format(
                    "VannaVolga(simple) mismatch: barrierType=%s barrier=%.2f strike=%.5f s=%.5f type=%s "
                            + "t=%.2f vol25Put=%.5f volAtm=%.5f vol25Call=%.5f v=%.5f -> "
                            + "expected=%.6f calculated=%.6f error=%.4g (tol=%.4g)",
                    value.barrierType, value.barrier, value.strike, value.s, value.type, value.t,
                    value.vol25Put, value.volAtm, value.vol25Call, value.v,
                    expected, calculated, error, value.tol),
                    error <= value.tol);
        }
    }


    private int timeToDays(/*@Time*/ final double t) {
        return (int) (t*360+0.5);
    }

    private int timeToDaysAct365(/*@Time*/ final double t) {
        return (int) (t*365+0.5);
    }


    private void REPORT_FAILURE(final String greekName, final BarrierType barrierType,
            final double barrier, final double rebate, final StrikedTypePayoff payoff,
            final Exercise exercise, final double s, final double q, final double r, final Date today,
            final double v, final double expected, final double calculated,
            final double error, final double tolerance) {
        fail("\n" + barrierType + " " + exercise
                + payoff.optionType() + " option with "
                + payoff.getClass().getSimpleName() + " payoff:\n"
                + "    underlying value: " +  s + "\n"
                + "    strike:           " + payoff.strike() + "\n"
                + "    barrier:          " + barrier + "\n"
                + "    rebate:           " + rebate + "\n"
                + "    dividend yield:   " + q + "\n"
                + "    risk-free rate:   " + r + "\n"
                + "    reference date:   " + today + "\n"
                + "    maturity:         " + exercise.lastDate() + "\n"
                + "    volatility:       " + v  + "\n\n"
                + "    expected   " + greekName + ": " + expected + "\n"
                + "    calculated " + greekName + ": " + calculated + "\n"
                + "    error:            " + error + "\n"
                + "    tolerance:        " + tolerance);
    }


    private static class NewBarrierOptionData {

        private final BarrierType barrierType;
        private final double barrier;
        private final double rebate;
        private final Option.Type type;
        private final double strike;
        private final double s;        // spot
        private final double q;        // dividend
        private final double r;        // risk-free rate
        private final double t;        // time to maturity
        private final double v;  // volatility
        private final double result;   // result
        private final double tol;      // tolerance

        public NewBarrierOptionData(
                final BarrierType barrierType,
                final double barrier,
                final double rebate,
                final Option.Type type,
                final double strike,
                final double s,        // spot
                final double q,        // dividend
                final double r,        // risk-free rate
                final double t,        // time to maturity
                final double v,  // volatility
                final double result,   // result
                final double tol      // tolerance
        ) {
            this.barrierType = barrierType;
            this.barrier = barrier;
            this.rebate = rebate;
            this.type = type;
            this.strike = strike;
            this.s = s;
            this.q = q;
            this.r = r;
            this.t = t;
            this.v = v;
            this.result = result;
            this.tol = tol;
        }
    }


    private static class BarrierOptionData {

        private final BarrierType barrierType;
        private final double volatility;
        private final double strike;
        private final double barrier;
        private final double callValue;
        private final double putValue;

        public BarrierOptionData(
                final BarrierType barrierType,
                final double volatility,
                final double strike,
                final double barrier,
                final double callValue,
                final double putValue) {
            this.barrierType = barrierType;
            this.volatility = volatility;
            this.strike = strike;
            this.barrier = barrier;
            this.callValue = callValue;
            this.putValue = putValue;
        }

    }


    /** Row of FX-option Vanna/Volga reference values from v1.42.1 test-suite/barrieroption.cpp:130-146. */
    private static final class BarrierFxOptionData {
        final BarrierType barrierType;
        final double barrier;
        final double rebate;
        final Option.Type type;
        final double strike;
        final double s;          // spot
        final double q;          // dividend
        final double r;          // risk-free rate
        final double t;          // time to maturity
        final double vol25Put;   // 25 delta put vol
        final double volAtm;     // atm vol
        final double vol25Call;  // 25 delta call vol
        final double v;          // volatility at strike
        final double result;     // result
        final double tol;        // tolerance

        BarrierFxOptionData(final BarrierType barrierType, final double barrier, final double rebate,
                            final Option.Type type, final double strike, final double s,
                            final double q, final double r, final double t,
                            final double vol25Put, final double volAtm, final double vol25Call,
                            final double v, final double result, final double tol) {
            this.barrierType = barrierType;
            this.barrier = barrier;
            this.rebate = rebate;
            this.type = type;
            this.strike = strike;
            this.s = s;
            this.q = q;
            this.r = r;
            this.t = t;
            this.vol25Put = vol25Put;
            this.volAtm = volAtm;
            this.vol25Call = vol25Call;
            this.v = v;
            this.result = result;
            this.tol = tol;
        }
    }

    /**
     * Java port of v1.42.1 {@code test-suite/barrieroption.cpp:testLowVolatility} (line 1185).
     * <p>
     * Validates that the analytic barrier engine returns the deterministic payoff (within 0.5)
     * when vol is collapsed to 1e-7 (i.e. the lognormal degenerates to its forward). At very low
     * vol the helper {@code powHS = pow(HS, 2*mu)} can become {@code +inf} while N(.) can become
     * {@code 0}, producing {@code 0*inf = NaN} in C++. v1.42.1 added explicit
     * {@code N==0 ? 0 : powHS*N} guards in {@code C/D/E/F} (mirrored in Java since
     * Phase1-closure-A2-C-555-escrow-naninf).
     */
    @Test
    public void testLowVolatility() {
        QL.info("Testing barrier options with low volatility value...");

        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(11, Month.February, 2018);
        new Settings().setEvaluationDate(today);

        final Date maturity = today.add(365);  // ~1 year, same as today + Period(1, Years)

        final double spot = 100.0;
        final double vol = 1e-7;

        final SimpleQuote q = new SimpleQuote(0.0);
        final SimpleQuote r = new SimpleQuote(0.0);
        final Handle< Quote > s0 = new Handle< Quote >(new SimpleQuote(spot));
        final Handle< YieldTermStructure > qTS = new Handle< YieldTermStructure >(Utilities.flatRate(today, q, dc));
        final Handle< YieldTermStructure > rTS = new Handle< YieldTermStructure >(Utilities.flatRate(today, r, dc));
        final Handle< BlackVolTermStructure > volTS = new Handle< BlackVolTermStructure >(
                Utilities.flatVol(today, vol, dc));

        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(s0, qTS, rTS, volTS);
        final PricingEngine engine = new AnalyticBarrierEngine(process);

        // {strike, optionType, barrier, barrierType, rebate, r, q, expected}
        final double[][] cases = {
                // C++ comments retained as references
                { 105.0, 0, 107.0, 0, 4.0, 0.03, 0.01, 3.0 },  // put UpOut k<H
                { 109.0, 0, 107.0, 0, 4.0, 0.03, 0.01, 7.0 },  // put UpOut k>H
                { 100.0, 0, 107.0, 0, 4.0, 0.03, 0.01, 0.0 },  // put UpOut OTM
                { 99.0,  0, 101.0, 0, 4.0, 0.03, 0.01, 4.0 },  // put knocked out k<H
                { 105.0, 0, 101.0, 0, 4.0, 0.03, 0.01, 4.0 },  // put knocked out k>H
                { 105.0, 1, 107.0, 0, 4.0, 0.03, 0.01, 0.0 },  // call UpOut OTM k<H
                { 109.0, 1, 107.0, 0, 4.0, 0.03, 0.01, 0.0 },  // call UpOut OTM k>H
                { 100.0, 1, 107.0, 0, 4.0, 0.03, 0.01, 2.0 },  // call UpOut ITM
                { 105.0, 1, 101.0, 0, 4.0, 0.03, 0.01, 4.0 },  // call knocked out k<H
        };

        for ( final double[] c : cases ) {
            r.setValue(c[5]);
            q.setValue(c[6]);
            final double strike = c[0];
            final Option.Type optionType = (c[1] == 1.0) ? Option.Type.Call : Option.Type.Put;
            final double barrier = c[2];
            final BarrierType barrierType = BarrierType.UpOut;  // all C++ rows are UpOut
            final double rebate = c[4];
            final double expected = c[7];

            final StrikedTypePayoff payoff = new PlainVanillaPayoff(optionType, strike);
            final Exercise exercise = new EuropeanExercise(maturity);
            final BarrierOption option = new BarrierOption(barrierType, barrier, rebate, payoff, exercise);
            option.setPricingEngine(engine);

            final double value = option.NPV();
            final double diff = Math.abs(value - expected);
            assertTrue("strike=" + strike + " type=" + optionType + " barrier=" + barrier + " r=" + c[5] + " q=" + c[6]
                    + " expected=" + expected + " calculated=" + value, !Double.isNaN(value) && diff <= 0.5);
        }
    }

    // ------------------------------------------------------------------
    // BLOCKED / EXISTING_EQUIVALENT ports from test-suite/barrieroption.cpp (Phase1-D5-B-R3)
    // ------------------------------------------------------------------
    // testLocalVolAndHestonComparison (cpp:781)
    //   BLOCKED. Exercises FdBlackScholesBarrierEngine + FdHestonBarrierEngine
    //   on a bicubic-interpolated BlackVarianceSurface.
    //   FdBlackScholesBarrierEngine is not yet ported to Java (~500 LOC;
    //   corresponds to C++ ql/pricingengines/barrier/fdblackscholesbarrierengine.{hpp,cpp}).
    //   FdHestonBarrierEngine *is* ported but cannot be exercised without
    //   the BS counterpart for the side-by-side test.
    //
    // testDividendBarrierOption (cpp:916), testDividendBarrierOptionWithDividendsPastMaturity (cpp:1022)
    //   BLOCKED. Both rely on FdBlackScholesBarrierEngine.withCashDividends
    //   — the discrete-dividend ctor on the missing FD-BS-Barrier engine.
    //   Same blocker as above.
    //
    // testPerturbative (cpp:1281)
    //   EXISTING_EQUIVALENT: the test is already ported as
    //   {@link org.jquantlib.testsuite.experimental.barrieroption.DoubleBarrierOptionTest#testPerturbativeValues}
    //   (Phase 4e.5 split: PerturbativeBarrierOptionEngine lives in
    //   ql/experimental/, so its Java test lives in the experimental test
    //   package alongside the other experimental barrier engines).

}
