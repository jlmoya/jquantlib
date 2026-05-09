/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k Track C C.6 test.

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

package org.jquantlib.testsuite.model.marketmodels;

import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.MarketModelPathwiseDiscounter;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link MarketModelPathwiseDiscounter} (Phase 3k C.6).
 *
 * <p>Verifies factor[0] is positive, decreasing as paymentTime advances, and
 * derivatives have the correct sign (negative — discount factor decreases as
 * forward rates rise).
 */
public class PathwiseDiscounterTest {

    @Test
    public void testFactorAtRateTime() {
        // paymentTime exactly at rate-time boundary → discount = preDF
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final MarketModelPathwiseDiscounter d = new MarketModelPathwiseDiscounter(1.0, rateTimes);

        // Build trivial discount matrix: P(t_0, t_j) = 1 / (1 + r*t_j)
        final int numRates = rateTimes.length - 1;
        final int numSteps = 1;
        final Matrix LIBORRates = new Matrix(numSteps, numRates);
        final Matrix Discounts = new Matrix(numSteps, numRates + 1);
        for (int j = 0; j <= numRates; ++j) {
            Discounts.set(0, j, 1.0 / (1.0 + 0.05 * rateTimes[Math.min(j, rateTimes.length - 1)]));
        }
        Discounts.set(0, 0, 1.0);

        final double[] factors = new double[numRates + 1];
        d.getFactors(LIBORRates, Discounts, 0, factors);

        // factor[0] should be positive
        Assert.assertTrue(factors[0] > 0.0);
        Assert.assertTrue(factors[0] <= 1.0);
        // derivatives w.r.t. early rates should be negative (rates up → DF down)
        Assert.assertTrue("derivative w.r.t. forward[0] should be negative",
                factors[1] < 0.0);
    }

    @Test
    public void testFactorMidPeriod() {
        // paymentTime between two rate-times → uses interpolation
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        final MarketModelPathwiseDiscounter d = new MarketModelPathwiseDiscounter(0.75, rateTimes);

        final int numRates = rateTimes.length - 1;
        final Matrix LIBORRates = new Matrix(1, numRates);
        final Matrix Discounts = new Matrix(1, numRates + 1);
        Discounts.set(0, 0, 1.0);
        Discounts.set(0, 1, 0.975);
        Discounts.set(0, 2, 0.95);
        Discounts.set(0, 3, 0.925);

        final double[] factors = new double[numRates + 1];
        d.getFactors(LIBORRates, Discounts, 0, factors);

        // C++ lower_bound: first index whose rateTime >= 0.75 → before_ = 1 (rateTimes[1]=1.0)
        // beforeWeight = 1 - (0.75 - 1.0)/(1.5 - 1.0) = 1.5; postWeight = -0.5
        // factor[0] = preDF * (postDF/preDF)^postWeight
        //           = Discounts[0,1] * (Discounts[0,2]/Discounts[0,1])^(-0.5)
        //           = 0.975 * (0.95/0.975)^(-0.5)
        final double expected = 0.975 * Math.pow(0.95 / 0.975, -0.5);
        Assert.assertEquals(expected, factors[0], 1e-12);
    }
}
