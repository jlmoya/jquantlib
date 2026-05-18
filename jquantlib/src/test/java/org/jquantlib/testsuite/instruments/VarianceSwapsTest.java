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
 Copyright (C) 2006 Warren Chou
 Copyright (C) 2007, 2008 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.Position;
import org.jquantlib.instruments.VarianceSwap;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.forward.MCVarianceSwapEngine;
import org.jquantlib.pricingengines.forward.ReplicatingVarianceSwapEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackVarianceCurve;
import org.jquantlib.termstructures.volatilities.BlackVarianceSurface;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 {@code test-suite/varianceswaps.cpp}
 * (Phase 5e.5b-CFC-d-180).
 *
 * <p>The C++ file has two test cases:
 * <ol>
 *   <li>{@code testReplicatingVarianceSwap} — variance-swap pricing via
 *       the replicating-portfolio engine of Demeterfi, Derman, Kamal &amp;
 *       Zou (1999).</li>
 *   <li>{@code testMCVarianceSwap} — variance-swap pricing via Monte
 *       Carlo on a Black-Scholes-Merton process with a piecewise-flat
 *       vol curve.</li>
 * </ol>
 *
 * <p>Both are body-filled. The replicating test runs at the C++ tight
 * tolerance ({@code 1e-4}); the MC test runs at the LOOSE MC tier
 * ({@code 1e-2}, per Phase 5e.5b-CFC-d-180 task spec).
 */
public class VarianceSwapsTest {

    public VarianceSwapsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /** Convert a year-fraction (Actual/365) into days. */
    private static int timeToDays(final double t) {
        return (int) (t * 365.0 + 0.5);
    }

    /** Datum struct for the replicating-engine option strip. */
    private static final class Datum {
        final Option.Type type;
        final double strike;
        final double v;
        Datum(final Option.Type type, final double strike, final double v) {
            this.type = type;
            this.strike = strike;
            this.v = v;
        }
    }


    /**
     * Port of C++ {@code testReplicatingVarianceSwap}.
     *
     * <p>Data from "A Guide to Volatility and Variance Swaps", Derman,
     * Kamal &amp; Zou (1999) with maturity {@code t} corrected from 0.25
     * to 0.246575 (Jan 1, 1999 → Apr 1, 1999). Expected fair variance:
     * {@code 0.04189}, tolerance {@code 1e-4}.
     */
    @Test
    public void testReplicatingVarianceSwap() {

        final Position type   = Position.Long;
        final double varStrike = 0.04;
        final double nominal   = 50000.0;
        final double s         = 100.0;
        final double q         = 0.00;
        final double r         = 0.05;
        final double t         = 0.246575;
        final double expected  = 0.04189;
        final double tol       = 1.0e-4;

        final Datum[] replicatingOptionData = new Datum[] {
            new Datum(Option.Type.Put,   50,  0.30),
            new Datum(Option.Type.Put,   55,  0.29),
            new Datum(Option.Type.Put,   60,  0.28),
            new Datum(Option.Type.Put,   65,  0.27),
            new Datum(Option.Type.Put,   70,  0.26),
            new Datum(Option.Type.Put,   75,  0.25),
            new Datum(Option.Type.Put,   80,  0.24),
            new Datum(Option.Type.Put,   85,  0.23),
            new Datum(Option.Type.Put,   90,  0.22),
            new Datum(Option.Type.Put,   95,  0.21),
            new Datum(Option.Type.Put,  100,  0.20),
            new Datum(Option.Type.Call, 100,  0.20),
            new Datum(Option.Type.Call, 105,  0.19),
            new Datum(Option.Type.Call, 110,  0.18),
            new Datum(Option.Type.Call, 115,  0.17),
            new Datum(Option.Type.Call, 120,  0.16),
            new Datum(Option.Type.Call, 125,  0.15),
            new Datum(Option.Type.Call, 130,  0.14),
            new Datum(Option.Type.Call, 135,  0.13)
        };

        final DayCounter dc = new Actual365Fixed();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot  = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final YieldTermStructure qTS =
                new FlatForward(today, new Handle<Quote>(qRate), dc);
        final YieldTermStructure rTS =
                new FlatForward(today, new Handle<Quote>(rRate), dc);

        final Date exDate = today.add(timeToDays(t));
        spot.setValue(s);
        qRate.setValue(q);
        rRate.setValue(r);

        // Split into call / put strips.
        final int options = replicatingOptionData.length;
        final java.util.List<Double> callStrikesL = new java.util.ArrayList<Double>();
        final java.util.List<Double> putStrikesL  = new java.util.ArrayList<Double>();
        final java.util.List<Double> callVolsL    = new java.util.ArrayList<Double>();
        final java.util.List<Double> putVolsL     = new java.util.ArrayList<Double>();
        for (int j = 0; j < options; j++) {
            if (replicatingOptionData[j].type == Option.Type.Call) {
                callStrikesL.add(replicatingOptionData[j].strike);
                callVolsL.add(replicatingOptionData[j].v);
            } else {
                putStrikesL.add(replicatingOptionData[j].strike);
                putVolsL.add(replicatingOptionData[j].v);
            }
        }

        // Build the vol surface following the C++ recipe.
        final Date[] dates = new Date[] { exDate };
        final double[] strikes = new double[options - 1];
        final Matrix vols = new Matrix(options - 1, 1);
        for (int j = 0; j < putVolsL.size(); j++) {
            vols.set(j, 0, putVolsL.get(j));
            strikes[j] = putStrikesL.get(j);
        }
        for (int k = 1; k < callVolsL.size(); k++) {
            final int j = putVolsL.size() - 1;
            vols.set(j + k, 0, callVolsL.get(k));
            strikes[j + k] = callStrikesL.get(k);
        }

        final BlackVolTermStructure volTS =
                new BlackVarianceSurface(today, dates, new Array(strikes), vols, dc);

        final GeneralizedBlackScholesProcess stochProcess =
                new BlackScholesMertonProcess(
                        new Handle<SimpleQuote>(spot),
                        new Handle<YieldTermStructure>(qTS),
                        new Handle<YieldTermStructure>(rTS),
                        new Handle<BlackVolTermStructure>(volTS));

        final double[] callStrikes = new double[callStrikesL.size()];
        final double[] putStrikes  = new double[putStrikesL.size()];
        for (int i = 0; i < callStrikes.length; i++) {
            callStrikes[i] = callStrikesL.get(i);
        }
        for (int i = 0; i < putStrikes.length; i++) {
            putStrikes[i] = putStrikesL.get(i);
        }

        final PricingEngine engine = new ReplicatingVarianceSwapEngine(
                stochProcess, 5.0, callStrikes, putStrikes);

        final VarianceSwap varianceSwap =
                new VarianceSwap(type, varStrike, nominal, today, exDate);
        varianceSwap.setPricingEngine(engine);

        final double calculated = varianceSwap.variance();
        final double error = Math.abs(calculated - expected);
        assertTrue("Replicating variance swap mismatch:"
                + " expected=" + expected
                + " calculated=" + calculated
                + " error=" + error
                + " tolerance=" + tol,
                error <= tol);
    }


    /**
     * Port of C++ {@code testMCVarianceSwap}.
     *
     * <p>Exercises the MC engine on a piecewise-flat vol curve. The
     * result should be {@code v*v} for arbitrary intermediate
     * {@code (t1, v1)} (with {@code 0<=t1<t} and {@code 0<=v1<v}). For
     * {@code v=0.20} the expected fair variance is {@code 0.04}.
     *
     * <p>Tolerance: LOOSE 1e-2 (MC tier, Phase 5e.5b-CFC-d-180 spec).
     */
    @Test
    public void testMCVarianceSwap() {

        final Position type   = Position.Long;
        final double varStrike = 0.04;
        final double nominal   = 50000.0;
        final double s         = 100.0;
        final double q         = 0.00;
        final double r         = 0.05;
        final double t1        = 0.1;
        final double t         = 0.246575;
        final double v1        = 0.10;
        final double v         = 0.20;
        final double expected  = 0.04;
        final double tol       = 1.0e-2;  // LOOSE MC tier per task spec

        final DayCounter dc = new Actual365Fixed();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot  = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final YieldTermStructure qTS =
                new FlatForward(today, new Handle<Quote>(qRate), dc);
        final YieldTermStructure rTS =
                new FlatForward(today, new Handle<Quote>(rRate), dc);

        final Date exDate     = today.add(timeToDays(t));
        final Date intermDate = today.add(timeToDays(t1));
        spot.setValue(s);
        qRate.setValue(q);
        rRate.setValue(r);

        final Date[] dates = new Date[] { intermDate, exDate };
        final double[] vols = new double[] { v1, v };
        final BlackVarianceCurve volCurve =
                new BlackVarianceCurve(today, dates, vols, dc, true);
        volCurve.setInterpolation();
        final BlackVolTermStructure volTS = volCurve;

        final GeneralizedBlackScholesProcess stochProcess =
                new BlackScholesMertonProcess(
                        new Handle<SimpleQuote>(spot),
                        new Handle<YieldTermStructure>(qTS),
                        new Handle<YieldTermStructure>(rTS),
                        new Handle<BlackVolTermStructure>(volTS));

        final PricingEngine engine = new MCVarianceSwapEngine
                .MakeMCVarianceSwapEngine(stochProcess)
                .withStepsPerYear(250)
                .withSamples(1023)
                .withSeed(42L)
                .value();

        final VarianceSwap varianceSwap =
                new VarianceSwap(type, varStrike, nominal, today, exDate);
        varianceSwap.setPricingEngine(engine);

        final double calculated = varianceSwap.variance();
        final double error = Math.abs(calculated - expected);
        assertTrue("MC variance swap mismatch:"
                + " expected=" + expected
                + " calculated=" + calculated
                + " error=" + error
                + " tolerance=" + tol,
                error <= tol);
    }
}
