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
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModel;
import org.jquantlib.model.marketmodels.PiecewiseConstantCorrelation;
import org.jquantlib.model.marketmodels.correlations.ExponentialForwardCorrelation;
import org.jquantlib.model.marketmodels.models.CotSwapToFwdAdapter;
import org.jquantlib.model.marketmodels.models.FlatVol;
import org.jquantlib.model.marketmodels.models.FwdToCotSwapAdapter;
import org.junit.Test;

/**
 * Smoke tests for {@link CotSwapToFwdAdapter} (A.4) and
 * {@link FwdToCotSwapAdapter} (A.5).
 *
 * <p>Round-trip: FlatVol(forward) → FwdToCotSwapAdapter → CotSwapToFwdAdapter
 * should recover a model with the same dimensions and pseudo-roots numerically
 * close to the original (small accumulated Jacobian error).
 */
public class ModelAdaptersTest {

    public ModelAdaptersTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static MarketModel buildFlatVol(final double[] rateTimes,
                                            final double vol,
                                            final int numFactors) {
        final List<Double> rateTimesList = new ArrayList<>();
        for (final double t : rateTimes) {
            rateTimesList.add(t);
        }
        final EvolutionDescription evol = new EvolutionDescription(rateTimes);
        final PiecewiseConstantCorrelation corr =
                new ExponentialForwardCorrelation(rateTimesList, 0.1, 0.1);
        final int n = rateTimes.length - 1;
        final double[] vols = new double[n];
        final double[] initialRates = new double[n];
        final double[] displacements = new double[n];
        for (int i = 0; i < n; ++i) {
            vols[i] = vol;
            initialRates[i] = 0.05;
            displacements[i] = 0.0;
        }
        return new FlatVol(vols, corr, evol, numFactors, initialRates, displacements);
    }

    /** Round-trip: FlatVol → FwdToCotSwap → CotSwapToFwd recovers same dimensions. */
    @Test
    public void testRoundTripDimensions() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        final MarketModel fwd = buildFlatVol(rateTimes, 0.20, 3);

        final FwdToCotSwapAdapter ct = new FwdToCotSwapAdapter(fwd);
        assertEquals(fwd.numberOfRates(), ct.numberOfRates());
        assertEquals(fwd.numberOfFactors(), ct.numberOfFactors());
        assertEquals(fwd.numberOfSteps(), ct.numberOfSteps());

        final CotSwapToFwdAdapter recovered = new CotSwapToFwdAdapter(ct);
        assertEquals(fwd.numberOfRates(), recovered.numberOfRates());
        assertEquals(fwd.numberOfFactors(), recovered.numberOfFactors());
        assertEquals(fwd.numberOfSteps(), recovered.numberOfSteps());
    }

    /**
     * Round-trip should approximately recover original pseudo-root entries.
     * Tolerance: loose 1e-6 (pseudo-root × Jacobian × inverse-Jacobian
     * accumulates float error; perfect identity is not expected from chained
     * matrix multiplications).
     */
    @Test
    public void testRoundTripPseudoRoot() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        final MarketModel fwd = buildFlatVol(rateTimes, 0.20, 3);

        final MarketModel recovered = new CotSwapToFwdAdapter(new FwdToCotSwapAdapter(fwd));

        for (int k = 0; k < fwd.numberOfSteps(); ++k) {
            final Matrix orig = fwd.pseudoRoot(k);
            final Matrix rec = recovered.pseudoRoot(k);
            assertEquals("rows " + k, orig.rows(), rec.rows());
            assertEquals("cols " + k, orig.columns(), rec.columns());
            for (int i = 0; i < orig.rows(); ++i) {
                for (int j = 0; j < orig.columns(); ++j) {
                    assertEquals("step " + k + " [" + i + "][" + j + "]",
                            orig.get(i, j), rec.get(i, j), 1e-10);
                }
            }
        }
    }

    /** FwdToCotSwapAdapter computes valid coterminalSwapRates as initialRates. */
    @Test
    public void testFwdToCotSwapInitialRates() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        final MarketModel fwd = buildFlatVol(rateTimes, 0.20, 3);
        final FwdToCotSwapAdapter ct = new FwdToCotSwapAdapter(fwd);
        final double[] cotSwapRates = ct.initialRates();
        assertEquals(fwd.numberOfRates(), cotSwapRates.length);
        assertNotNull(cotSwapRates);
        // Coterminal swap rates with flat 5% forwards: each ~5%
        for (final double r : cotSwapRates) {
            assertEquals(0.05, r, 1e-10);
        }
    }
}
