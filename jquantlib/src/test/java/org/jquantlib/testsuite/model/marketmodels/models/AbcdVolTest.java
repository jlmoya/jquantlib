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

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.PiecewiseConstantCorrelation;
import org.jquantlib.model.marketmodels.correlations.ExponentialForwardCorrelation;
import org.jquantlib.model.marketmodels.models.AbcdVol;
import org.jquantlib.termstructures.volatility.AbcdFunction;
import org.junit.Test;

/**
 * Smoke tests for {@link AbcdVol} — Phase 3j A.2.
 *
 * <p>The per-step covariance is
 * {@code Cov_k[i][j] = k_i*k_j * AbcdFunction.covariance(t_{k-1},t_k,T_i,T_j) * corr[i][j]}.
 * With full rank (numFactors == numRates), the pseudo-root reconstructs Cov_k
 * exactly via PR * PR^T.
 */
public class AbcdVolTest {

    public AbcdVolTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /** 3-rate full-rank reconstruction test. */
    @Test
    public void testFullRankPseudoRootReconstructsCovariance() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        final List<Double> rateTimesList = new ArrayList<>();
        for (final double t : rateTimes) {
            rateTimesList.add(t);
        }
        final EvolutionDescription evol = new EvolutionDescription(rateTimes);

        final double a = 0.1, b = 0.2, c = 1.5, d = 0.1;
        final double[] ks = {1.0, 1.0, 1.0};
        final double[] initialRates = {0.05, 0.05, 0.05};
        final double[] displacements = {0.0, 0.0, 0.0};

        final PiecewiseConstantCorrelation corr =
                new ExponentialForwardCorrelation(rateTimesList, 0.1, 0.1);

        final int numFactors = 3;
        final AbcdVol model = new AbcdVol(a, b, c, d, ks, corr, evol,
                numFactors, initialRates, displacements);

        assertEquals("nRates", 3, model.numberOfRates());
        assertEquals("nFactors", 3, model.numberOfFactors());
        assertEquals("nSteps", 3, model.numberOfSteps());

        final AbcdFunction abcd = new AbcdFunction(a, b, c, d);
        final Matrix corr0 = corr.correlation(0);

        for (int k = 0; k < model.numberOfSteps(); ++k) {
            final Matrix pr = model.pseudoRoot(k);
            assertEquals("rows", 3, pr.rows());
            assertEquals("cols", 3, pr.columns());
            final Matrix cov = pr.mul(pr.transpose());
            final double t1 = (k == 0) ? 0.0 : rateTimes[k - 1];
            final double t2 = rateTimes[k];
            // Surviving rates only (alive = rates with rateTimes[i] > evolutionTimes[k]),
            // so for evolution time = rateTimes[k], rates with i >= k+1 are alive at end-of-step;
            // however the integration window [t_{k-1}, t_k] only contributes positive covariance
            // when the rate fixing time T_i > t_{k-1}, so for surviving i,j check covariance.
            for (int i = 0; i < 3; ++i) {
                for (int j = i; j < 3; ++j) {
                    final double expected = abcd.covariance(t1, t2, rateTimes[i], rateTimes[j])
                            * corr0.get(i, j);
                    assertEquals("step " + k + " cov[" + i + "][" + j + "]",
                            expected, cov.get(i, j), 1e-12);
                }
            }
        }
    }

    /**
     * With a=0, b=0, c &gt; 0, d=v=const, AbcdFunction(t) ≡ d, so AbcdVol matches
     * a flat-vol model with vol=d. This sanity-checks the limit case.
     */
    @Test
    public void testDegenerateConstantVol() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        final List<Double> rateTimesList = new ArrayList<>();
        for (final double t : rateTimes) {
            rateTimesList.add(t);
        }
        final EvolutionDescription evol = new EvolutionDescription(rateTimes);

        final double v = 0.2;
        final double a = 0.0, b = 0.0, c = 1.0, d = v;
        final double[] ks = {1.0, 1.0, 1.0};
        final double[] initialRates = {0.05, 0.05, 0.05};
        final double[] displacements = {0.0, 0.0, 0.0};
        final PiecewiseConstantCorrelation corr =
                new ExponentialForwardCorrelation(rateTimesList, 0.1, 0.1);

        final AbcdVol model = new AbcdVol(a, b, c, d, ks, corr, evol,
                3, initialRates, displacements);

        // Reconstruct covariance and compare to flat-vol formula:
        // cov_k[i][j] = (cutOff - t_{k-1}) * v * v * corr[i][j], where
        // cutOff = min(rateTimes[i], rateTimes[j], rateTimes[k]).
        final Matrix corr0 = corr.correlation(0);
        for (int k = 0; k < model.numberOfSteps(); ++k) {
            final Matrix pr = model.pseudoRoot(k);
            final Matrix cov = pr.mul(pr.transpose());
            final double t1 = (k == 0) ? 0.0 : rateTimes[k - 1];
            final double t2 = rateTimes[k];
            for (int i = 0; i < 3; ++i) {
                for (int j = i; j < 3; ++j) {
                    final double cutOff = Math.min(t2, Math.min(rateTimes[i], rateTimes[j]));
                    final double expected = (cutOff > t1)
                            ? (cutOff - t1) * v * v * corr0.get(i, j)
                            : 0.0;
                    assertEquals("step " + k + " cov[" + i + "][" + j + "]",
                            expected, cov.get(i, j), 1e-10);
                }
            }
        }
    }
}
