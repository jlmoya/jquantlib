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

package org.jquantlib.testsuite.math.optimization;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.CostFunction;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.LevenbergMarquardt;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.Problem;
import org.junit.Test;

/**
 * Java port of test-suite/optimizers.cpp::nestedOptimizationTest (Phase 5b).
 *
 * <p>Mirrors the C++ {@code OptimizationBasedCostFunction} pattern: a cost
 * function whose {@code values()} routine itself runs a nested LM optimisation.
 * Verifies that nesting LevenbergMarquardt within an outer LM does not crash
 * and converges (smoke-test only — the inner problem is a 1D quadratic with
 * known minimum at -0.5; the outer optimiser is essentially free since the
 * outer cost {@code value()} returns the constant 1.0).
 */
public class NestedOptimizationTest {

    public NestedOptimizationTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * 1-D polynomial of degree N, mirroring C++
     * {@code OneDimensionalPolynomialDegreeN}.
     */
    private static final class OneDimensionalPolynomialDegreeN extends CostFunction {
        private final Array coefficients;
        private final int polynomialDegree;

        OneDimensionalPolynomialDegreeN(final Array coefficients) {
            this.coefficients = coefficients;
            this.polynomialDegree = coefficients.size() - 1;
        }

        @Override
        public double value(final Array x) {
            QL.require(x.size() == 1, "independent variable must be 1 dimensional");
            double y = 0.0;
            for (int i = 0; i <= polynomialDegree; ++i) {
                y += coefficients.get(i) * Math.pow(x.get(0), i);
            }
            return y;
        }

        @Override
        public Array values(final Array x) {
            QL.require(x.size() == 1, "independent variable must be 1 dimensional");
            return new Array(new double[] { value(x) });
        }
    }

    /**
     * Outer cost function whose values() runs a nested LM optimisation,
     * mirroring C++ {@code OptimizationBasedCostFunction}.
     */
    private static final class OptimizationBasedCostFunction extends CostFunction {
        @Override
        public double value(final Array x) {
            return 1.0;
        }

        @Override
        public Array values(final Array x) {
            // Nested 1D LM optimisation with known minimum at -0.5
            final Array coeffs = new Array(new double[] { 1.0, 1.0, 1.0 });
            final OneDimensionalPolynomialDegreeN inner =
                    new OneDimensionalPolynomialDegreeN(coeffs);
            final NoConstraint constraint = new NoConstraint();
            final Array initialValues = new Array(new double[] { 100.0 });
            final Problem problem = new Problem(inner, constraint, initialValues);
            final LevenbergMarquardt optimizer = new LevenbergMarquardt();
            final EndCriteria endCriteria = new EndCriteria(1000, 100, 1e-5, 1e-5, 1e-5);
            optimizer.minimize(problem, endCriteria);
            return new Array(new double[] { 0.0 });
        }
    }

    @Test
    public void testNestedOptimization() {
        QL.info("Testing nested optimizations...");

        final OptimizationBasedCostFunction outerCost = new OptimizationBasedCostFunction();
        final NoConstraint constraint = new NoConstraint();
        final Array initialValues = new Array(new double[] { 0.0 });
        final Problem problem = new Problem(outerCost, constraint, initialValues);
        final LevenbergMarquardt optimizer = new LevenbergMarquardt();
        final EndCriteria endCriteria = new EndCriteria(1000, 100, 1e-5, 1e-5, 1e-5);
        // Smoke-test: just verify it runs without throwing
        optimizer.minimize(problem, endCriteria);
    }
}
