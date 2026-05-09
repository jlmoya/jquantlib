/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k Track C C.7 test.

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

package org.jquantlib.testsuite.model.marketmodels.pathwisegreeks;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.driftcomputation.LMMDriftCalculator;
import org.jquantlib.model.marketmodels.pathwisegreeks.RatePseudoRootJacobian;
import org.jquantlib.model.marketmodels.pathwisegreeks.RatePseudoRootJacobianAllElements;
import org.jquantlib.model.marketmodels.pathwisegreeks.RatePseudoRootJacobianNumerical;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the rate-pseudo-root Jacobian family (Phase 3k Track C C.7).
 *
 * <p>Cross-validates analytic (closed-form) vs numerical (FD-bumped) versions
 * of the page-95 Jacobian, and exercises the AllElements variant.
 */
public class RatePseudoRootJacobianTest {

    private static final double TOL = 1e-5;

    @Test
    public void testAnalyticVsNumericalSimple() {
        // 3-rate, 2-factor pseudo-root example
        final int numRates = 3;
        final int factors = 2;
        final double[] taus = {0.5, 0.5, 0.5};
        final double[] displacements = {0.0, 0.0, 0.0};
        final int aliveIndex = 0;
        final int numeraire = 0;

        final Matrix pseudo = new Matrix(numRates, factors);
        // Mild values
        pseudo.set(0, 0, 0.10); pseudo.set(0, 1, 0.05);
        pseudo.set(1, 0, 0.08); pseudo.set(1, 1, 0.06);
        pseudo.set(2, 0, 0.07); pseudo.set(2, 1, 0.07);

        // Bumps: 2 different bumps
        final List<Matrix> bumps = new ArrayList<>();
        final Matrix b1 = new Matrix(numRates, factors);
        b1.set(0, 0, 0.001);
        bumps.add(b1);
        final Matrix b2 = new Matrix(numRates, factors);
        b2.set(2, 1, 0.001);
        bumps.add(b2);

        final double[] oldRates = {0.04, 0.045, 0.05};
        final double[] gaussians = {0.5, -0.3};

        // Compute newRates by running the LMM step ourselves
        final LMMDriftCalculator drifts = new LMMDriftCalculator(
                pseudo, displacements, taus, numeraire, aliveIndex);
        final double[] driftsArr = new double[numRates];
        drifts.compute(oldRates, driftsArr);

        final double[] newRates = new double[numRates];
        for (int j = aliveIndex; j < numRates; ++j) {
            double logRate = Math.log(oldRates[j] + displacements[j]);
            for (int k = 0; k < factors; ++k) {
                logRate += -0.5 * pseudo.get(j, k) * pseudo.get(j, k);
            }
            logRate += driftsArr[j];
            for (int k = 0; k < factors; ++k) {
                logRate += pseudo.get(j, k) * gaussians[k];
            }
            newRates[j] = Math.exp(logRate) - displacements[j];
        }

        // Discount ratios (one-step): P(t,t_{j+1})/P(t,t_j) = 1/(1+r_j*tau_j)
        final double[] discountRatios = new double[numRates + 1];
        discountRatios[0] = 1.0;
        for (int j = 0; j < numRates; ++j) {
            discountRatios[j + 1] = discountRatios[j] / (1.0 + oldRates[j] * taus[j]);
        }
        // The C++ formulation expects discountRatios[j+1] = 1/(1+r_j*tau_j) per-step ratio
        // (not the cumulative product). Re-derive accordingly to match RatePseudoRootJacobian.
        final double[] oneStepDFs = new double[numRates + 1];
        oneStepDFs[0] = 1.0;
        for (int j = 0; j < numRates; ++j) {
            oneStepDFs[j + 1] = 1.0 / (1.0 + oldRates[j] * taus[j]);
        }

        // Analytic Jacobian (use a small bump in pseudoRoot to compare)
        final RatePseudoRootJacobian analytic = new RatePseudoRootJacobian(
                pseudo, aliveIndex, numeraire, taus, bumps, displacements);

        final RatePseudoRootJacobianNumerical numerical = new RatePseudoRootJacobianNumerical(
                pseudo, aliveIndex, numeraire, taus, bumps, displacements);

        final Matrix analyticB = new Matrix(2, numRates);
        final Matrix numericalB = new Matrix(2, numRates);

        analytic.getBumps(oldRates, oneStepDFs, newRates, gaussians, analyticB);
        numerical.getBumps(oldRates, oneStepDFs, newRates, gaussians, numericalB);

        // Compare element by element
        // Note: the analytic version computes derivative w.r.t. infinitesimal
        // bump (linear). For small numeric bump (size 0.001), analytic ≈ numerical
        // up to second-order error.
        for (int i = 0; i < 2; ++i) {
            for (int j = 0; j < numRates; ++j) {
                Assert.assertEquals("[" + i + "][" + j + "]",
                        numericalB.get(i, j), analyticB.get(i, j), TOL);
            }
        }
    }

    @Test
    public void testAllElementsDimensionsAndAliveZeroing() {
        // Verify AllElements variant runs cleanly on a 3x2 setup;
        // structural test only (dim consistency, alive-index zeroing).
        final int numRates = 3;
        final int factors = 2;
        final double[] taus = {0.5, 0.5, 0.5};
        final double[] displacements = {0.0, 0.0, 0.0};
        final int aliveIndex = 1;
        final int numeraire = 1;

        final Matrix pseudo = new Matrix(numRates, factors);
        pseudo.set(0, 0, 0.10); pseudo.set(0, 1, 0.05);
        pseudo.set(1, 0, 0.08); pseudo.set(1, 1, 0.06);
        pseudo.set(2, 0, 0.07); pseudo.set(2, 1, 0.07);

        final RatePseudoRootJacobianAllElements allEl = new RatePseudoRootJacobianAllElements(
                pseudo, aliveIndex, numeraire, taus, displacements);

        final List<Matrix> B = new ArrayList<>();
        for (int j = 0; j < numRates; ++j) {
            B.add(new Matrix(numRates, factors));
        }
        final double[] oldRates = {0.04, 0.045, 0.05};
        final double[] newRates = {0.04, 0.046, 0.051};
        final double[] gaussians = {0.0, 0.0};
        final double[] discountRatios = {1.0, 1.0/(1+0.04*0.5), 1.0/(1+0.045*0.5), 1.0/(1+0.05*0.5)};

        allEl.getBumps(oldRates, discountRatios, newRates, gaussians, B);

        // Rates < aliveIndex should have all-zero matrices
        for (int k = 0; k < numRates; ++k) {
            for (int f = 0; f < factors; ++f) {
                Assert.assertEquals("rate 0 (already reset) zeroed at [" + k + "][" + f + "]",
                        0.0, B.get(0).get(k, f), 1e-15);
            }
        }
    }
}
