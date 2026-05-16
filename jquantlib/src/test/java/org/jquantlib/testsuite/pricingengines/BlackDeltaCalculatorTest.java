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

/*
 Copyright (C) 2009 Dimitri Reiswich

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.pricingengines;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.experimental.fx.DeltaVolQuote;
import org.jquantlib.instruments.EuropeanOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.pricingengines.BlackDeltaCalculator;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1
 * {@code test-suite/blackdeltacalculator.cpp} (Phase 5e.5b-CFC-d-29).
 *
 * <p>Exercises the FX premium-adjusted / unadjusted delta conventions
 * implemented in {@link BlackDeltaCalculator}: {@code testDeltaValues},
 * {@code testDeltaPriceConsistency}, {@code testPutCallParity},
 * {@code testAtmCalcs}.
 *
 * <p>The first test (delta values + strike-from-delta) uses the loose
 * tolerances from the C++ test (1e-3 for deltas, 1e-2 for strikes — the
 * latter being a numerical-procedure outcome). The other two tests use
 * the original C++ tight tolerance ({@code 1e-10}). The ATM test keeps
 * the {@code 1e-2} tolerance the C++ test specifies (strikes from
 * numerical procedures).
 */
public class BlackDeltaCalculatorTest {

    public BlackDeltaCalculatorTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    // C++ utilities.hpp: timeToDays(t, daysPerYear=360) = lround(t*360).
    private static int timeToDays(final double t) {
        return (int) Math.round(t * 360.0);
    }

    private static final class DeltaData {
        final Option.Type ot;
        final DeltaVolQuote.DeltaType dt;
        final double spot;
        final double dDf;
        final double fDf;
        final double stdDev;
        final double strike;
        final double value;

        DeltaData(final Option.Type ot, final DeltaVolQuote.DeltaType dt,
                  final double spot, final double dDf, final double fDf,
                  final double stdDev, final double strike, final double value) {
            this.ot = ot;
            this.dt = dt;
            this.spot = spot;
            this.dDf = dDf;
            this.fDf = fDf;
            this.stdDev = stdDev;
            this.strike = strike;
            this.value = value;
        }
    }

    private static final class EuropeanOptionData {
        final Option.Type type;
        final double strike;
        final double s;   // spot
        final double q;   // dividend
        final double r;   // risk-free rate
        final double t;   // time to maturity
        final double v;   // volatility

        EuropeanOptionData(final Option.Type type, final double strike,
                           final double s, final double q, final double r,
                           final double t, final double v) {
            this.type = type;
            this.strike = strike;
            this.s = s;
            this.q = q;
            this.r = r;
            this.t = t;
            this.v = v;
        }
    }

    @Test
    public void testDeltaValues() {

        QL.info("Testing delta calculator values...");

        // Values taken from parallel implementation in R
        final DeltaData[] values = new DeltaData[] {
            new DeltaData(Option.Type.Call, DeltaVolQuote.DeltaType.Spot,    1.421,   0.997306, 0.992266,  0.1180654,  1.608080,  0.15),
            new DeltaData(Option.Type.Call, DeltaVolQuote.DeltaType.PaSpot,  1.421,   0.997306, 0.992266,  0.1180654,  1.600545,  0.15),
            new DeltaData(Option.Type.Call, DeltaVolQuote.DeltaType.Fwd,     1.421,   0.997306, 0.992266,  0.1180654,  1.609029,  0.15),
            new DeltaData(Option.Type.Call, DeltaVolQuote.DeltaType.PaFwd,   1.421,   0.997306, 0.992266,  0.1180654,  1.601550,  0.15),
            new DeltaData(Option.Type.Call, DeltaVolQuote.DeltaType.Spot,    122.121, 0.9695434,0.9872347, 0.0887676,  119.8031,  0.67),
            new DeltaData(Option.Type.Call, DeltaVolQuote.DeltaType.PaSpot,  122.121, 0.9695434,0.9872347, 0.0887676,  117.7096,  0.67),
            new DeltaData(Option.Type.Call, DeltaVolQuote.DeltaType.Fwd,     122.121, 0.9695434,0.9872347, 0.0887676,  120.0592,  0.67),
            new DeltaData(Option.Type.Call, DeltaVolQuote.DeltaType.PaFwd,   122.121, 0.9695434,0.9872347, 0.0887676,  118.0532,  0.67),
            new DeltaData(Option.Type.Put,  DeltaVolQuote.DeltaType.Spot,    3.4582,  0.99979,  0.9250616, 0.3199034,  4.964924, -0.821),
            new DeltaData(Option.Type.Put,  DeltaVolQuote.DeltaType.PaSpot,  3.4582,  0.99979,  0.9250616, 0.3199034,  3.778327, -0.821),
            new DeltaData(Option.Type.Put,  DeltaVolQuote.DeltaType.Fwd,     3.4582,  0.99979,  0.9250616, 0.3199034,  4.51896,  -0.821),
            new DeltaData(Option.Type.Put,  DeltaVolQuote.DeltaType.PaFwd,   3.4582,  0.99979,  0.9250616, 0.3199034,  3.65728,  -0.821),
            // JPYUSD Data taken from Castagnas "FX Options and Smile Risk" (Wiley 2009)
            new DeltaData(Option.Type.Put,  DeltaVolQuote.DeltaType.Spot,    103.00,  0.99482,  0.98508,   0.07247845, 97.47,    -0.25),
            new DeltaData(Option.Type.Put,  DeltaVolQuote.DeltaType.PaSpot,  103.00,  0.99482,  0.98508,   0.07247845, 97.22,    -0.25),
        };

        for (int i = 0; i < values.length; ++i) {
            final DeltaData v = values[i];

            final BlackDeltaCalculator myCalc = new BlackDeltaCalculator(
                    v.ot, v.dt, v.spot, v.dDf, v.fDf, v.stdDev);

            double tolerance = 1.0e-3;

            double expected = v.value;
            double calculated = myCalc.deltaFromStrike(v.strike);
            double error = Math.abs(calculated - expected);

            if (error > tolerance) {
                fail("\n Delta-from-strike calculation failed for delta. \n"
                        + "Iteration: " + i + "\n"
                        + "Calculated Delta: " + calculated + "\n"
                        + "Expected   Delta: " + expected + "\n"
                        + "Error: " + error);
            }

            tolerance = 1.0e-2;
            // tolerance not that small, but sufficient for strikes in
            // particular since they might be results of a numerical procedure

            expected = v.strike;
            calculated = myCalc.strikeFromDelta(v.value);
            error = Math.abs(calculated - expected);

            if (error > tolerance) {
                fail("\n Strike-from-delta calculation failed for delta. \n"
                        + "Iteration: " + i + "\n"
                        + "Calculated Strike: " + calculated + "\n"
                        + "Expected   Strike: " + expected + "\n"
                        + "Error: " + error);
            }
        }
    }

    @Test
    public void testDeltaPriceConsistency() {

        QL.info("Testing premium-adjusted delta price consistency...");

        // This function tests for price consistencies with the standard
        // Black Scholes calculator, since premium adjusted deltas can be
        // calculated from spot deltas by adding/subtracting the premium.

        final EuropeanOptionData[] values = new EuropeanOptionData[] {
            //                      type,         strike, spot,    rd,     rf,     t,    vol
            new EuropeanOptionData(Option.Type.Call,  0.9123, 1.2212, 0.0231, 0.0000, 0.25, 0.301),
            new EuropeanOptionData(Option.Type.Call,  0.9234, 1.2212, 0.0231, 0.0000, 0.35, 0.111),
            new EuropeanOptionData(Option.Type.Call,  0.9783, 1.2212, 0.0231, 0.0000, 0.45, 0.071),
            new EuropeanOptionData(Option.Type.Call,  1.0000, 1.2212, 0.0231, 0.0000, 0.55, 0.082),
            new EuropeanOptionData(Option.Type.Call,  1.1230, 1.2212, 0.0231, 0.0000, 0.65, 0.012),
            new EuropeanOptionData(Option.Type.Call,  1.2212, 1.2212, 0.0231, 0.0000, 0.75, 0.129),
            new EuropeanOptionData(Option.Type.Call,  1.3212, 1.2212, 0.0231, 0.0000, 0.85, 0.034),
            new EuropeanOptionData(Option.Type.Call,  1.3923, 1.2212, 0.0131, 0.2344, 0.95, 0.001),
            new EuropeanOptionData(Option.Type.Call,  1.3455, 1.2212, 0.0000, 0.0000, 1.00, 0.127),
            new EuropeanOptionData(Option.Type.Put,   0.9123, 1.2212, 0.0231, 0.0000, 0.25, 0.301),
            new EuropeanOptionData(Option.Type.Put,   0.9234, 1.2212, 0.0231, 0.0000, 0.35, 0.111),
            new EuropeanOptionData(Option.Type.Put,   0.9783, 1.2212, 0.0231, 0.0000, 0.45, 0.071),
            new EuropeanOptionData(Option.Type.Put,   1.0000, 1.2212, 0.0231, 0.0000, 0.55, 0.082),
            new EuropeanOptionData(Option.Type.Put,   1.1230, 1.2212, 0.0231, 0.0000, 0.65, 0.012),
            new EuropeanOptionData(Option.Type.Put,   1.2212, 1.2212, 0.0231, 0.0000, 0.75, 0.129),
            new EuropeanOptionData(Option.Type.Put,   1.3212, 1.2212, 0.0231, 0.0000, 0.85, 0.034),
            new EuropeanOptionData(Option.Type.Put,   1.3923, 1.2212, 0.0131, 0.2344, 0.95, 0.001),
            new EuropeanOptionData(Option.Type.Put,   1.3455, 1.2212, 0.0000, 0.0000, 1.00, 0.127),
            // extreme case: zero vol
            new EuropeanOptionData(Option.Type.Put,   1.3455, 1.2212, 0.0000, 0.0000, 0.50, 0.000),
            // extreme case: zero strike
            new EuropeanOptionData(Option.Type.Put,   0.0000, 1.2212, 0.0000, 0.0000, 1.50, 0.133),
            // extreme case: zero strike + zero vol
            new EuropeanOptionData(Option.Type.Put,   0.0000, 1.2212, 0.0000, 0.0000, 1.00, 0.133),
        };

        final DayCounter dc = new Actual360();
        final Calendar calendar = new Target();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        // Start setup of market data
        final SimpleQuote spotQuote = new SimpleQuote(0.0);
        final Handle<Quote> spotHandle = new Handle<Quote>(spotQuote);

        final SimpleQuote qQuote = new SimpleQuote(0.0);
        final Handle<Quote> qHandle = new Handle<Quote>(qQuote);
        final YieldTermStructure qTS = new FlatForward(today, qHandle, dc);

        final SimpleQuote rQuote = new SimpleQuote(0.0);
        // C++ test passes qQuote here too (likely typo in upstream — reproduce
        // verbatim so domestic and foreign discount factors share the same
        // quote, mirroring the C++ semantics).
        final Handle<Quote> rHandle = new Handle<Quote>(qQuote);
        final YieldTermStructure rTS = new FlatForward(today, rHandle, dc);

        final SimpleQuote volQuote = new SimpleQuote(0.0);
        final Handle<Quote> volHandle = new Handle<Quote>(volQuote);
        final BlackVolTermStructure volTS = new BlackConstantVol(today, calendar, volHandle, dc);

        final double tolerance = 1.0e-10;

        for (int i = 0; i < values.length; ++i) {
            final EuropeanOptionData value = values[i];

            final StrikedTypePayoff payoff = new PlainVanillaPayoff(value.type, value.strike);
            final Date exDate = today.add(timeToDays(value.t));
            final Exercise exercise = new EuropeanExercise(exDate);

            spotQuote.setValue(value.s);
            volQuote.setValue(value.v);
            rQuote.setValue(value.r);
            qQuote.setValue(value.q);

            final double discDom = rTS.discount(exDate);
            final double discFor = qTS.discount(exDate);
            final double implVol = Math.sqrt(volTS.blackVariance(exDate, 0.0));

            final BlackDeltaCalculator myCalc = new BlackDeltaCalculator(
                    value.type, DeltaVolQuote.DeltaType.PaSpot,
                    spotQuote.value(), discDom, discFor, implVol);

            final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                    spotHandle,
                    new Handle<YieldTermStructure>(qTS),
                    new Handle<YieldTermStructure>(rTS),
                    new Handle<BlackVolTermStructure>(volTS));

            final PricingEngine engine = new AnalyticEuropeanEngine(stochProcess);

            final EuropeanOption option = new EuropeanOption(payoff, exercise);
            option.setPricingEngine(engine);

            double calculatedVal = myCalc.deltaFromStrike(value.strike);

            double delta = 0.0;
            if (implVol > 0.0) {
                delta = option.delta();
            } else {
                final double fwd = spotQuote.value() * discFor / discDom;
                if (payoff.optionType() == Option.Type.Call && fwd > payoff.strike()) {
                    delta = 1.0;
                } else if (payoff.optionType() == Option.Type.Put && fwd < payoff.strike()) {
                    delta = -1.0;
                }
            }

            double expectedVal = delta - option.NPV() / spotQuote.value();
            double error = Math.abs(expectedVal - calculatedVal);

            if (error > tolerance) {
                fail("\n Premium-adjusted spot delta test failed. \n"
                        + "Iteration: " + i + "\n"
                        + "Calculated Delta: " + calculatedVal + "\n"
                        + "Expected Value:   " + expectedVal + "\n"
                        + "Error: " + error);
            }

            myCalc.setDeltaType(DeltaVolQuote.DeltaType.PaFwd);

            calculatedVal = myCalc.deltaFromStrike(value.strike);
            // Premium adjusted Fwd Delta is PA spot without discount
            expectedVal = expectedVal / discFor;
            error = Math.abs(expectedVal - calculatedVal);

            if (error > tolerance) {
                fail("\n Premium-adjusted forward delta test failed. \n"
                        + "Iteration: " + i + "\n"
                        + "Calculated Delta: " + calculatedVal + "\n"
                        + "Expected Value:   " + expectedVal + "\n"
                        + "Error: " + error);
            }

            // Test consistency with BlackScholes Calculator for Spot Delta
            myCalc.setDeltaType(DeltaVolQuote.DeltaType.Spot);

            calculatedVal = myCalc.deltaFromStrike(value.strike);
            expectedVal = delta;
            error = Math.abs(calculatedVal - expectedVal);

            if (error > tolerance) {
                fail("\n spot delta in BlackDeltaCalculator differs from delta in BlackScholesCalculator. \n"
                        + "Iteration: " + i + "\n"
                        + "Calculated Value: " + calculatedVal + "\n"
                        + "Expected Value:   " + expectedVal + "\n"
                        + "Error: " + error);
            }
        }
    }

    @Test
    public void testPutCallParity() {

        QL.info("Testing put-call parity for deltas...");

        // Test for put call parity between put and call deltas.
        // Data below are from "Option pricing formulas", E.G. Haug,
        // McGraw-Hill 1998, pag 11-16.
        final EuropeanOptionData[] values = new EuropeanOptionData[] {
            // pag 2-8
            new EuropeanOptionData(Option.Type.Call,  65.00,  60.00, 0.00, 0.08, 0.25, 0.30),
            new EuropeanOptionData(Option.Type.Put,   95.00, 100.00, 0.05, 0.10, 0.50, 0.20),
            new EuropeanOptionData(Option.Type.Put,   19.00,  19.00, 0.10, 0.10, 0.75, 0.28),
            new EuropeanOptionData(Option.Type.Call,  19.00,  19.00, 0.10, 0.10, 0.75, 0.28),
            new EuropeanOptionData(Option.Type.Call,   1.60,   1.56, 0.08, 0.06, 0.50, 0.12),
            new EuropeanOptionData(Option.Type.Put,   70.00,  75.00, 0.05, 0.10, 0.50, 0.35),
            // pag 24
            new EuropeanOptionData(Option.Type.Call, 100.00,  90.00, 0.10, 0.10, 0.10, 0.15),
            new EuropeanOptionData(Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.10, 0.15),
            new EuropeanOptionData(Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.10, 0.15),
            new EuropeanOptionData(Option.Type.Call, 100.00,  90.00, 0.10, 0.10, 0.10, 0.25),
            new EuropeanOptionData(Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.10, 0.25),
            new EuropeanOptionData(Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.10, 0.25),
            new EuropeanOptionData(Option.Type.Call, 100.00,  90.00, 0.10, 0.10, 0.10, 0.35),
            new EuropeanOptionData(Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.10, 0.35),
            new EuropeanOptionData(Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.10, 0.35),
            new EuropeanOptionData(Option.Type.Call, 100.00,  90.00, 0.10, 0.10, 0.50, 0.15),
            new EuropeanOptionData(Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.50, 0.15),
            new EuropeanOptionData(Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.50, 0.15),
            new EuropeanOptionData(Option.Type.Call, 100.00,  90.00, 0.10, 0.10, 0.50, 0.25),
            new EuropeanOptionData(Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.50, 0.25),
            new EuropeanOptionData(Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.50, 0.25),
            new EuropeanOptionData(Option.Type.Call, 100.00,  90.00, 0.10, 0.10, 0.50, 0.35),
            new EuropeanOptionData(Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.50, 0.35),
            new EuropeanOptionData(Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.50, 0.35),
            new EuropeanOptionData(Option.Type.Put,  100.00,  90.00, 0.10, 0.10, 0.10, 0.15),
            new EuropeanOptionData(Option.Type.Put,  100.00, 100.00, 0.10, 0.10, 0.10, 0.15),
            new EuropeanOptionData(Option.Type.Put,  100.00, 110.00, 0.10, 0.10, 0.10, 0.15),
            new EuropeanOptionData(Option.Type.Put,  100.00,  90.00, 0.10, 0.10, 0.10, 0.25),
            new EuropeanOptionData(Option.Type.Put,  100.00, 100.00, 0.10, 0.10, 0.10, 0.25),
            new EuropeanOptionData(Option.Type.Put,  100.00, 110.00, 0.10, 0.10, 0.10, 0.25),
            new EuropeanOptionData(Option.Type.Put,  100.00,  90.00, 0.10, 0.10, 0.10, 0.35),
            new EuropeanOptionData(Option.Type.Put,  100.00, 100.00, 0.10, 0.10, 0.10, 0.35),
            new EuropeanOptionData(Option.Type.Put,  100.00, 110.00, 0.10, 0.10, 0.10, 0.35),
            new EuropeanOptionData(Option.Type.Put,  100.00,  90.00, 0.10, 0.10, 0.50, 0.15),
            new EuropeanOptionData(Option.Type.Put,  100.00, 100.00, 0.10, 0.10, 0.50, 0.15),
            new EuropeanOptionData(Option.Type.Put,  100.00, 110.00, 0.10, 0.10, 0.50, 0.15),
            new EuropeanOptionData(Option.Type.Put,  100.00,  90.00, 0.10, 0.10, 0.50, 0.25),
            new EuropeanOptionData(Option.Type.Put,  100.00, 100.00, 0.10, 0.10, 0.50, 0.25),
            new EuropeanOptionData(Option.Type.Put,  100.00, 110.00, 0.10, 0.10, 0.50, 0.25),
            new EuropeanOptionData(Option.Type.Put,  100.00,  90.00, 0.10, 0.10, 0.50, 0.35),
            new EuropeanOptionData(Option.Type.Put,  100.00, 100.00, 0.10, 0.10, 0.50, 0.35),
            new EuropeanOptionData(Option.Type.Put,  100.00, 110.00, 0.10, 0.10, 0.50, 0.35),
            // pag 27
            new EuropeanOptionData(Option.Type.Call,  40.00,  42.00, 0.08, 0.04, 0.75, 0.35),
        };

        final DayCounter dc = new Actual360();
        final Calendar calendar = new Target();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final SimpleQuote spotQuote = new SimpleQuote(0.0);

        final SimpleQuote qQuote = new SimpleQuote(0.0);
        final Handle<Quote> qHandle = new Handle<Quote>(qQuote);
        final YieldTermStructure qTS = new FlatForward(today, qHandle, dc);

        final SimpleQuote rQuote = new SimpleQuote(0.0);
        // Verbatim C++: rHandle is built from qQuote (likely upstream typo,
        // but kept identical so semantics match).
        final Handle<Quote> rHandle = new Handle<Quote>(qQuote);
        final YieldTermStructure rTS = new FlatForward(today, rHandle, dc);

        final SimpleQuote volQuote = new SimpleQuote(0.0);
        final Handle<Quote> volHandle = new Handle<Quote>(volQuote);
        final BlackVolTermStructure volTS = new BlackConstantVol(today, calendar, volHandle, dc);

        final double tolerance = 1.0e-10;

        for (int i = 0; i < values.length; ++i) {
            final EuropeanOptionData value = values[i];

            final Date exDate = today.add(timeToDays(value.t));

            spotQuote.setValue(value.s);
            volQuote.setValue(value.v);
            rQuote.setValue(value.r);
            qQuote.setValue(value.q);

            final double discDom = rTS.discount(exDate);
            final double discFor = qTS.discount(exDate);
            final double implVol = Math.sqrt(volTS.blackVariance(exDate, 0.0));
            final double forward = spotQuote.value() * discFor / discDom;

            final BlackDeltaCalculator myCalc = new BlackDeltaCalculator(
                    Option.Type.Call, DeltaVolQuote.DeltaType.Spot,
                    spotQuote.value(), discDom, discFor, implVol);

            double deltaCall = myCalc.deltaFromStrike(value.strike);
            myCalc.setOptionType(Option.Type.Put);
            double deltaPut = myCalc.deltaFromStrike(value.strike);
            myCalc.setOptionType(Option.Type.Call);

            double expectedDiff = discFor;
            double calculatedDiff = deltaCall - deltaPut;
            double error = Math.abs(expectedDiff - calculatedDiff);

            if (error > tolerance) {
                fail("\n Put-call parity failed for spot delta. \n"
                        + "Iteration: " + i + "\n"
                        + "Calculated Call Delta: " + deltaCall + "\n"
                        + "Calculated Put Delta:  " + deltaPut + "\n"
                        + "Expected Difference:   " + expectedDiff + "\n"
                        + "Calculated Difference: " + calculatedDiff);
            }

            myCalc.setDeltaType(DeltaVolQuote.DeltaType.Fwd);

            deltaCall = myCalc.deltaFromStrike(value.strike);
            myCalc.setOptionType(Option.Type.Put);
            deltaPut = myCalc.deltaFromStrike(value.strike);
            myCalc.setOptionType(Option.Type.Call);

            expectedDiff = 1.0;
            calculatedDiff = deltaCall - deltaPut;
            error = Math.abs(expectedDiff - calculatedDiff);

            if (error > tolerance) {
                fail("\n Put-call parity failed for forward delta. \n"
                        + "Iteration: " + i + "\n"
                        + "Calculated Call Delta: " + deltaCall + "\n"
                        + "Calculated Put Delta:  " + deltaPut + "\n"
                        + "Expected Difference:   " + expectedDiff + "\n"
                        + "Calculated Difference: " + calculatedDiff);
            }

            myCalc.setDeltaType(DeltaVolQuote.DeltaType.PaSpot);

            deltaCall = myCalc.deltaFromStrike(value.strike);
            myCalc.setOptionType(Option.Type.Put);
            deltaPut = myCalc.deltaFromStrike(value.strike);
            myCalc.setOptionType(Option.Type.Call);

            expectedDiff = discFor * value.strike / forward;
            calculatedDiff = deltaCall - deltaPut;
            error = Math.abs(expectedDiff - calculatedDiff);

            if (error > tolerance) {
                fail("\n Put-call parity failed for premium-adjusted spot delta. \n"
                        + "Iteration: " + i + "\n"
                        + "Calculated Call Delta: " + deltaCall + "\n"
                        + "Calculated Put Delta:  " + deltaPut + "\n"
                        + "Expected Difference:   " + expectedDiff + "\n"
                        + "Calculated Difference: " + calculatedDiff);
            }

            myCalc.setDeltaType(DeltaVolQuote.DeltaType.PaFwd);

            deltaCall = myCalc.deltaFromStrike(value.strike);
            myCalc.setOptionType(Option.Type.Put);
            deltaPut = myCalc.deltaFromStrike(value.strike);
            myCalc.setOptionType(Option.Type.Call);

            expectedDiff = value.strike / forward;
            calculatedDiff = deltaCall - deltaPut;
            error = Math.abs(expectedDiff - calculatedDiff);

            if (error > tolerance) {
                fail("\n Put-call parity failed for premium-adjusted forward delta. \n"
                        + "Iteration: " + i + "\n"
                        + "Calculated Call Delta: " + deltaCall + "\n"
                        + "Calculated Put Delta:  " + deltaPut + "\n"
                        + "Expected Difference:   " + expectedDiff + "\n"
                        + "Calculated Difference: " + calculatedDiff);
            }
        }
    }

    @Test
    public void testAtmCalcs() {

        QL.info("Testing delta-neutral ATM quotations...");

        final DeltaData[] values = new DeltaData[] {
            new DeltaData(Option.Type.Call, DeltaVolQuote.DeltaType.Spot,    1.421,   0.997306, 0.992266,  0.1180654,  1.608080,  0.15),
            new DeltaData(Option.Type.Call, DeltaVolQuote.DeltaType.PaSpot,  1.421,   0.997306, 0.992266,  0.1180654,  1.600545,  0.15),
            new DeltaData(Option.Type.Call, DeltaVolQuote.DeltaType.Fwd,     1.421,   0.997306, 0.992266,  0.1180654,  1.609029,  0.15),
            new DeltaData(Option.Type.Call, DeltaVolQuote.DeltaType.PaFwd,   1.421,   0.997306, 0.992266,  0.1180654,  1.601550,  0.15),
            new DeltaData(Option.Type.Call, DeltaVolQuote.DeltaType.Spot,    122.121, 0.9695434,0.9872347, 0.0887676,  119.8031,  0.67),
            new DeltaData(Option.Type.Call, DeltaVolQuote.DeltaType.PaSpot,  122.121, 0.9695434,0.9872347, 0.0887676,  117.7096,  0.67),
            new DeltaData(Option.Type.Call, DeltaVolQuote.DeltaType.Fwd,     122.121, 0.9695434,0.9872347, 0.0887676,  120.0592,  0.67),
            new DeltaData(Option.Type.Call, DeltaVolQuote.DeltaType.PaFwd,   122.121, 0.9695434,0.9872347, 0.0887676,  118.0532,  0.67),
            new DeltaData(Option.Type.Put,  DeltaVolQuote.DeltaType.Spot,    3.4582,  0.99979,  0.9250616, 0.3199034,  4.964924, -0.821),
            new DeltaData(Option.Type.Put,  DeltaVolQuote.DeltaType.PaSpot,  3.4582,  0.99979,  0.9250616, 0.3199034,  3.778327, -0.821),
            new DeltaData(Option.Type.Put,  DeltaVolQuote.DeltaType.Fwd,     3.4582,  0.99979,  0.9250616, 0.3199034,  4.51896,  -0.821),
            new DeltaData(Option.Type.Put,  DeltaVolQuote.DeltaType.PaFwd,   3.4582,  0.99979,  0.9250616, 0.3199034,  3.65728,  -0.821),
            // Data taken from Castagnas "FX Options and Smile Risk" (Wiley 2009)
            new DeltaData(Option.Type.Put,  DeltaVolQuote.DeltaType.Spot,    103.00,  0.99482,  0.98508,   0.07247845, 97.47,    -0.25),
            new DeltaData(Option.Type.Put,  DeltaVolQuote.DeltaType.PaSpot,  103.00,  0.99482,  0.98508,   0.07247845, 97.22,    -0.25),
            // Extreme case: zero vol, ATM Fwd strike
            new DeltaData(Option.Type.Call, DeltaVolQuote.DeltaType.Fwd,     103.00,  0.99482,  0.98508,   0.0,        101.0013,  0.5),
            new DeltaData(Option.Type.Call, DeltaVolQuote.DeltaType.Spot,    103.00,  0.99482,  0.98508,   0.0,        101.0013,  0.99482 * 0.5),
        };

        // tolerance not that small, but sufficient for strikes (numerical procedures)
        final double tolerance = 1.0e-2;

        for (int i = 0; i < values.length; ++i) {
            final DeltaData v = values[i];

            final double currFwd = v.spot * v.fDf / v.dDf;

            final BlackDeltaCalculator myCalc = new BlackDeltaCalculator(
                    Option.Type.Call, v.dt, v.spot, v.dDf, v.fDf, v.stdDev);

            double currAtmStrike = myCalc.atmStrike(DeltaVolQuote.AtmType.AtmDeltaNeutral);
            double currCallDelta = myCalc.deltaFromStrike(currAtmStrike);
            myCalc.setOptionType(Option.Type.Put);
            double currPutDelta = myCalc.deltaFromStrike(currAtmStrike);
            myCalc.setOptionType(Option.Type.Call);

            double expected = 0.0;
            double calculated = currCallDelta + currPutDelta;
            double error = Math.abs(calculated - expected);

            if (error > tolerance) {
                fail("\n Delta neutrality failed for spot delta in Delta Calculator. \n"
                        + "Iteration: " + i + "\n"
                        + "Calculated Delta Sum: " + calculated + "\n"
                        + "Expected Delta Sum:   " + expected + "\n"
                        + "Error: " + error);
            }

            myCalc.setDeltaType(DeltaVolQuote.DeltaType.Fwd);
            currAtmStrike = myCalc.atmStrike(DeltaVolQuote.AtmType.AtmDeltaNeutral);
            currCallDelta = myCalc.deltaFromStrike(currAtmStrike);
            myCalc.setOptionType(Option.Type.Put);
            currPutDelta = myCalc.deltaFromStrike(currAtmStrike);
            myCalc.setOptionType(Option.Type.Call);

            calculated = currCallDelta + currPutDelta;
            error = Math.abs(calculated - expected);

            if (error > tolerance) {
                fail("\n Delta neutrality failed for forward delta in Delta Calculator. \n"
                        + "Iteration: " + i + "\n"
                        + "Calculated Delta Sum: " + calculated + "\n"
                        + "Expected Delta Sum:   " + expected + "\n"
                        + "Error: " + error);
            }

            myCalc.setDeltaType(DeltaVolQuote.DeltaType.PaSpot);
            currAtmStrike = myCalc.atmStrike(DeltaVolQuote.AtmType.AtmDeltaNeutral);
            currCallDelta = myCalc.deltaFromStrike(currAtmStrike);
            myCalc.setOptionType(Option.Type.Put);
            currPutDelta = myCalc.deltaFromStrike(currAtmStrike);
            myCalc.setOptionType(Option.Type.Call);

            calculated = currCallDelta + currPutDelta;
            error = Math.abs(calculated - expected);

            if (error > tolerance) {
                fail("\n Delta neutrality failed for premium-adjusted spot delta in Delta Calculator. \n"
                        + "Iteration: " + i + "\n"
                        + "Calculated Delta Sum: " + calculated + "\n"
                        + "Expected Delta Sum:   " + expected + "\n"
                        + "Error: " + error);
            }

            myCalc.setDeltaType(DeltaVolQuote.DeltaType.PaFwd);
            currAtmStrike = myCalc.atmStrike(DeltaVolQuote.AtmType.AtmDeltaNeutral);
            currCallDelta = myCalc.deltaFromStrike(currAtmStrike);
            myCalc.setOptionType(Option.Type.Put);
            currPutDelta = myCalc.deltaFromStrike(currAtmStrike);
            myCalc.setOptionType(Option.Type.Call);

            calculated = currCallDelta + currPutDelta;
            error = Math.abs(calculated - expected);

            if (error > tolerance) {
                fail("\n Delta neutrality failed for premium-adjusted forward delta in Delta Calculator. \n"
                        + "Iteration: " + i + "\n"
                        + "Calculated Delta Sum: " + calculated + "\n"
                        + "Expected Delta Sum:   " + expected + "\n"
                        + "Error: " + error);
            }

            // Test ATM forward Calculations
            calculated = myCalc.atmStrike(DeltaVolQuote.AtmType.AtmFwd);
            expected = currFwd;
            error = Math.abs(expected - calculated);

            if (error > tolerance) {
                fail("\n Atm forward test failed. \n"
                        + "Iteration: " + i + "\n"
                        + "Calculated Value: " + calculated + "\n"
                        + "Expected   Value: " + expected + "\n"
                        + "Error: " + error);
            }

            // Test ATM 0.50 delta calculations
            myCalc.setDeltaType(DeltaVolQuote.DeltaType.Fwd);
            final double atmFiftyStrike = myCalc.atmStrike(DeltaVolQuote.AtmType.AtmPutCall50);
            calculated = Math.abs(myCalc.deltaFromStrike(atmFiftyStrike));
            expected = 0.50;
            error = Math.abs(expected - calculated);

            if (error > tolerance) {
                fail("\n Atm 0.50 delta strike test failed. \n"
                        + "Iteration: " + i + "\n"
                        + "Calculated Value: " + calculated + "\n"
                        + "Expected   Value: " + expected + "\n"
                        + "Error: " + error);
            }
        }
    }
}
