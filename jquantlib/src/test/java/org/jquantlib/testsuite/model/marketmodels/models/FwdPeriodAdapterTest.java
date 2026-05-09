/*
 Copyright (C) 2026 Jose Moya

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.
 */

package org.jquantlib.testsuite.model.marketmodels.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModel;
import org.jquantlib.model.marketmodels.PiecewiseConstantCorrelation;
import org.jquantlib.model.marketmodels.correlations.ExponentialForwardCorrelation;
import org.jquantlib.model.marketmodels.models.FlatVol;
import org.jquantlib.model.marketmodels.models.FwdPeriodAdapter;
import org.junit.Test;

/**
 * Smoke tests for {@link FwdPeriodAdapter} — Phase 3j A.3.
 */
public class FwdPeriodAdapterTest {

    public FwdPeriodAdapterTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * 6-rate FlatVol wrapped as period-2 (offset 0) → 3-rate adapter model.
     * Verifies dimensions and that pseudo-root rows are zeroed for "dead" rates.
     */
    @Test
    public void testPeriodTwoOffsetZero() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5};
        final List<Double> rateTimesList = new ArrayList<>();
        for (final double t : rateTimes) {
            rateTimesList.add(t);
        }
        final EvolutionDescription evol = new EvolutionDescription(rateTimes);
        final PiecewiseConstantCorrelation corr =
                new ExponentialForwardCorrelation(rateTimesList, 0.1, 0.1);
        final double[] vols = {0.20, 0.20, 0.20, 0.20, 0.20, 0.20};
        final double[] initialRates = {0.05, 0.05, 0.05, 0.05, 0.05, 0.05};
        final double[] displacements = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0};

        final MarketModel large = new FlatVol(vols, corr, evol, 6,
                initialRates, displacements);

        // Wrap large as period-2, offset-0 → 3-rate adapter
        final FwdPeriodAdapter small = new FwdPeriodAdapter(large, 2, 0,
                new double[] {0.0});

        assertEquals("nRates after period-2", 3, small.numberOfRates());
        assertEquals("nFactors", 6, small.numberOfFactors());
        assertTrue("nSteps > 0", small.numberOfSteps() > 0);

        // displacements broadcast: all zeros
        for (final double d : small.displacements()) {
            assertEquals(0.0, d, 0.0);
        }

        // initialRates: 3 forward rates from the restricted curve state
        assertEquals(3, small.initialRates().length);

        // Each pseudoRoot is 3x6
        for (int k = 0; k < small.numberOfSteps(); ++k) {
            final Matrix pr = small.pseudoRoot(k);
            assertEquals("rows step " + k, 3, pr.rows());
            assertEquals("cols step " + k, 6, pr.columns());
        }
    }

    /**
     * Verifies that period == 0 is rejected.
     */
    @Test(expected = org.jquantlib.lang.exceptions.LibraryException.class)
    public void testZeroPeriodThrows() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        final List<Double> rateTimesList = new ArrayList<>();
        for (final double t : rateTimes) {
            rateTimesList.add(t);
        }
        final EvolutionDescription evol = new EvolutionDescription(rateTimes);
        final PiecewiseConstantCorrelation corr =
                new ExponentialForwardCorrelation(rateTimesList, 0.1, 0.1);
        final MarketModel m = new FlatVol(new double[] {0.2, 0.2, 0.2}, corr, evol, 3,
                new double[] {0.05, 0.05, 0.05}, new double[] {0.0, 0.0, 0.0});
        new FwdPeriodAdapter(m, 0, 0, new double[] {0.0});
    }

    /**
     * Verifies that period &lt;= offset is rejected.
     */
    @Test(expected = org.jquantlib.lang.exceptions.LibraryException.class)
    public void testPeriodLessThanOffsetThrows() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        final List<Double> rateTimesList = new ArrayList<>();
        for (final double t : rateTimes) {
            rateTimesList.add(t);
        }
        final EvolutionDescription evol = new EvolutionDescription(rateTimes);
        final PiecewiseConstantCorrelation corr =
                new ExponentialForwardCorrelation(rateTimesList, 0.1, 0.1);
        final MarketModel m = new FlatVol(new double[] {0.2, 0.2, 0.2}, corr, evol, 3,
                new double[] {0.05, 0.05, 0.05}, new double[] {0.0, 0.0, 0.0});
        new FwdPeriodAdapter(m, 2, 3, new double[] {0.0});
    }
}
