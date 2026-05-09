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
import org.jquantlib.model.marketmodels.PiecewiseConstantCorrelation;
import org.jquantlib.model.marketmodels.correlations.ExponentialForwardCorrelation;
import org.jquantlib.model.marketmodels.models.FlatVol;
import org.junit.Test;

/**
 * Smoke tests for {@link FlatVol} — Phase 3j A.1.
 *
 * <p>For a flat-vol model with rate times {@code [t_0=0,...,t_n]}, evolution
 * times equal to {@code [t_0,...,t_{n-1}]} (default) and constant per-rate
 * volatility {@code v[i]}, the per-step covariance for forwards i,j (with
 * i,j alive at step k) collapses to:
 *
 * <pre>{@code Cov_k[i][j] = (t_k - t_{k-1}) * v[i] * v[j] * corr[i][j]}</pre>
 *
 * <p>FlatVol decomposes Cov_k via PseudoSqrt rank-reduced sqrt; we recover Cov
 * by computing pseudoRoot * pseudoRoot^T and check entries.
 */
public class FlatVolTest {

    public FlatVolTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * 3-rate grid (rateTimes = {0.5, 1.0, 1.5, 2.0}), flat vol = 0.20,
     * exponential forward correlation (longTermCorr = 0.1, beta = 0.1).
     * Verifies dimensions and reconstructed covariance.
     */
    @Test
    public void testThreeRatePseudoRootShape() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        final List<Double> rateTimesList = new ArrayList<>();
        for (final double t : rateTimes) {
            rateTimesList.add(t);
        }
        final EvolutionDescription evol = new EvolutionDescription(rateTimes);

        final double vol = 0.20;
        final double[] vols = {vol, vol, vol};
        final double[] initialRates = {0.05, 0.05, 0.05};
        final double[] displacements = {0.0, 0.0, 0.0};

        final PiecewiseConstantCorrelation corr =
                new ExponentialForwardCorrelation(rateTimesList, 0.1, 0.1);

        // Full rank: numFactors == numRates so the pseudo-root reconstructs
        // the covariance exactly (no rank-reduction loss).
        final int numFactors = 3;
        final FlatVol model = new FlatVol(vols, corr, evol,
                numFactors, initialRates, displacements);

        // basic shape
        assertEquals("nRates", 3, model.numberOfRates());
        assertEquals("nFactors", 3, model.numberOfFactors());
        assertEquals("nSteps", 3, model.numberOfSteps());

        for (int k = 0; k < model.numberOfSteps(); ++k) {
            final Matrix pr = model.pseudoRoot(k);
            assertEquals("pr rows step " + k, 3, pr.rows());
            assertEquals("pr cols step " + k, 3, pr.columns());
        }

        // covariance reconstruction Cov = pr * pr^T
        final Matrix corr0 = corr.correlation(0);
        for (int k = 0; k < model.numberOfSteps(); ++k) {
            final Matrix pr = model.pseudoRoot(k);
            final Matrix cov = pr.mul(pr.transpose());
            final double dt = (k == 0) ? rateTimes[0] : rateTimes[k] - rateTimes[k - 1];
            // rate i is alive at step k iff rateTimes[i] > evolutionTimes[k].
            // evolutionTimes default = rateTimes[0..n-1]; so step k integrates on
            // (t_{k-1}, t_k] and only the rates with index >= k survive. Lower-indexed
            // rates contribute zero variance at later steps due to truncation in
            // flatVolCovariance: t1 >= cutOff(=rateTimes[i]).
            // We verify entries for surviving rates only.
            for (int i = k; i < 3; ++i) {
                for (int j = i; j < 3; ++j) {
                    final double expected = dt * vol * vol * corr0.get(i, j);
                    assertEquals("step " + k + " cov[" + i + "][" + j + "]",
                            expected, cov.get(i, j), 1e-12);
                }
            }
        }
    }

    /**
     * Verifies validation: number of factors must be {@code <= numberOfRates}.
     */
    @Test(expected = org.jquantlib.lang.exceptions.LibraryException.class)
    public void testTooManyFactorsThrows() {
        final double[] rateTimes = {0.5, 1.0, 1.5};
        final List<Double> rateTimesList = new ArrayList<>();
        for (final double t : rateTimes) {
            rateTimesList.add(t);
        }
        final EvolutionDescription evol = new EvolutionDescription(rateTimes);
        final PiecewiseConstantCorrelation corr =
                new ExponentialForwardCorrelation(rateTimesList, 0.1, 0.1);
        new FlatVol(new double[] {0.2, 0.2}, corr, evol,
                /* numberOfFactors */ 3,
                new double[] {0.05, 0.05},
                new double[] {0.0, 0.0});
    }

    /**
     * Verifies one-factor reduction: pseudo-root has 1 column.
     */
    @Test
    public void testOneFactor() {
        final double[] rateTimes = {0.5, 1.0, 1.5};
        final List<Double> rateTimesList = new ArrayList<>();
        for (final double t : rateTimes) {
            rateTimesList.add(t);
        }
        final EvolutionDescription evol = new EvolutionDescription(rateTimes);
        final PiecewiseConstantCorrelation corr =
                new ExponentialForwardCorrelation(rateTimesList, 0.1, 0.1);
        final FlatVol m = new FlatVol(new double[] {0.2, 0.2}, corr, evol, 1,
                new double[] {0.05, 0.05}, new double[] {0.0, 0.0});

        for (int k = 0; k < m.numberOfSteps(); ++k) {
            assertEquals(1, m.pseudoRoot(k).columns());
        }
        // diagonal should be positive for first alive rate
        assertTrue(m.pseudoRoot(0).get(0, 0) > 0.0);
    }
}
