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
 */

package org.jquantlib.testsuite.experimental.exoticoptions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.experimental.exoticoptions.EverestOption;
import org.jquantlib.experimental.exoticoptions.HimalayaOption;
import org.jquantlib.experimental.exoticoptions.MCEverestEngine;
import org.jquantlib.experimental.exoticoptions.MCHimalayaEngine;
import org.jquantlib.experimental.exoticoptions.MCPagodaEngine;
import org.jquantlib.experimental.exoticoptions.PagodaOption;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.shortrate.StochasticProcessArray;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

/**
 * Smoke tests for {@link MCHimalayaEngine}, {@link MCEverestEngine}, and
 * {@link MCPagodaEngine} (Phase 4i.5 WI-3..5).
 *
 * <p>These exotic options have no closed-form: tests check that
 * (a) the MC pricer produces a positive, finite NPV with a positive
 * error estimate, and (b) the option's secondary results (Everest yield,
 * Pagoda discounted payoff fraction) satisfy basic invariants.
 *
 * <p>Tier: LOOSE — Monte Carlo convergence at N=4000-10000 with seed=42.
 */
public class MCExoticEnginesTest {

    public MCExoticEnginesTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static GeneralizedBlackScholesProcess makeBsm(
            final Date today, final double S, final double r, final double q,
            final double vol, final DayCounter dc, final Calendar cal) {
        final Handle<? extends Quote> spot = new Handle<Quote>(new SimpleQuote(S));
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, r, dc));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, q, dc));
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, cal, vol, dc));
        return new GeneralizedBlackScholesProcess(spot, qTS, rTS, volTS);
    }

    private static StochasticProcessArray makeArray(final Date today, final int n,
                                                    final double rho,
                                                    final DayCounter dc,
                                                    final Calendar cal) {
        final List<StochasticProcess1D> processes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            processes.add(makeBsm(today, 100.0, 0.05, 0.02, 0.20, dc, cal));
        }
        // n×n constant-correlation matrix, ones on the diagonal
        final double[][] data = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                data[i][j] = (i == j) ? 1.0 : rho;
            }
        }
        return new StochasticProcessArray(processes, new Matrix(data));
    }

    @Test
    public void testHimalayaSmokePositive() {
        final Date today = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final StochasticProcessArray array = makeArray(today, 3, 0.0, dc, cal);

        // 3 fixing dates over 1 year
        final List<Date> fixings = new ArrayList<>();
        fixings.add(today.add(120));
        fixings.add(today.add(240));
        fixings.add(today.add(365));

        final HimalayaOption option = new HimalayaOption(fixings, /* strike */ 95.0);
        final MCHimalayaEngine mc = new MCHimalayaEngine(
                array, /* brownianBridge */ false, /* antithetic */ true,
                /* requiredSamples */ 4000,
                McSimulation.NULL_TOLERANCE, McSimulation.NULL_SAMPLES, /* seed */ 42L);
        option.setPricingEngine(mc);
        final double npv = option.NPV();

        assertTrue("Himalaya NPV must be positive and finite",
                npv > 0.0 && Double.isFinite(npv));
        assertTrue("Himalaya error estimate must be positive",
                option.errorEstimate() > 0.0);
        // Plausibility bound: NPV cannot exceed the sum of all best-asset
        // prices (each ≈ 100 forward), so an upper bound of ~150 is generous.
        assertTrue("Himalaya NPV must be < 150 (plausibility bound)", npv < 150.0);
    }

    @Test
    public void testEverestSmokeYieldFinite() {
        final Date today = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final StochasticProcessArray array = makeArray(today, 3, 0.0, dc, cal);

        final Date exDate = today.add(365);
        final Exercise exercise = new EuropeanExercise(exDate);
        final EverestOption option = new EverestOption(
                /* notional */ 100.0, /* guarantee */ 0.10, exercise);

        final MCEverestEngine mc = new MCEverestEngine(
                array, /* timeSteps */ 1, McSimulation.NULL_SAMPLES,
                /* brownianBridge */ false, /* antithetic */ true,
                /* requiredSamples */ 4000,
                McSimulation.NULL_TOLERANCE, McSimulation.NULL_SAMPLES, /* seed */ 42L);
        option.setPricingEngine(mc);
        final double npv = option.NPV();

        assertTrue("Everest NPV must be positive and finite",
                npv > 0.0 && Double.isFinite(npv));
        assertTrue("Everest error estimate must be positive",
                option.errorEstimate() > 0.0);

        // Yield = NPV/(notional*discount) - 1; should be a small finite number,
        // typically negative with a positive guarantee (since min-yield < 0
        // usually for assets that deflate by μ-σ²/2).
        final double y = option.yield();
        assertTrue("Everest yield must be finite", Double.isFinite(y));
    }

    @Test
    public void testPagodaSmokePositive() {
        final Date today = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final StochasticProcessArray array = makeArray(today, 2, 0.0, dc, cal);

        // 4 fixing dates over 1 year
        final List<Date> fixings = new ArrayList<>();
        fixings.add(today.add(90));
        fixings.add(today.add(180));
        fixings.add(today.add(270));
        fixings.add(today.add(365));

        final PagodaOption option = new PagodaOption(fixings,
                /* roof */ 50.0, /* fraction */ 0.5);

        final MCPagodaEngine mc = new MCPagodaEngine(
                array, /* brownianBridge */ false, /* antithetic */ true,
                /* requiredSamples */ 4000,
                McSimulation.NULL_TOLERANCE, McSimulation.NULL_SAMPLES, /* seed */ 42L);
        option.setPricingEngine(mc);
        final double npv = option.NPV();

        assertTrue("Pagoda NPV must be non-negative and finite",
                npv >= 0.0 && Double.isFinite(npv));
        assertTrue("Pagoda error estimate must be non-negative",
                option.errorEstimate() >= 0.0);
        // Capped by fraction*roof = 0.5*50 = 25
        assertTrue("Pagoda NPV must be <= fraction*roof", npv <= 25.0 + 1e-9);
        // With 100x100 indep assets and modest vol, the average performance
        // should land somewhere positive on average.
        assertEquals("Pagoda NPV in [0, 25]", true, npv >= 0.0 && npv <= 25.0);
    }
}
