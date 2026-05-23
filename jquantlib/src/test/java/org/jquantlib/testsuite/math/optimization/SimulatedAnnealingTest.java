/*
 Copyright (C) 2026 Jose Moya

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license. You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.testsuite.math.optimization;

import static org.junit.Assert.assertEquals;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.CostFunction;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.Problem;
import org.jquantlib.math.optimization.SimulatedAnnealing;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.junit.Test;

/**
 * Tests for {@link SimulatedAnnealing}.
 */
public class SimulatedAnnealingTest {

    /** Quadratic: f(x) = (x0 - 1)^2 + (x1 + 2)^2. Minimum at (1, -2). */
    private static class Quadratic extends CostFunction {
        @Override
        public Array values(final Array x) {
            return new Array(new double[] { x.get(0) - 1.0, x.get(1) + 2.0 });
        }
        @Override
        public double value(final Array x) {
            final double a = x.get(0) - 1.0;
            final double b = x.get(1) + 2.0;
            return a * a + b * b;
        }
    }

    @Test
    public void testConstantFactorSchemeConverges() {
        final Quadratic f = new Quadratic();
        final NoConstraint constraint = new NoConstraint();
        final Array init = new Array(new double[] { 5.0, 5.0 });
        final Problem problem = new Problem(f, constraint, init);
        final EndCriteria end = new EndCriteria(20000, 5000, 1e-4, 1e-8, 1e-8);
        // lambda (step), T0 (initial temperature), epsilon (cooling factor), m (moves per cooling step)
        final SimulatedAnnealing sa = new SimulatedAnnealing(0.5, 1.0, 0.02, 10,
                new MersenneTwisterUniformRng(42L));
        sa.minimize(problem, end);
        final Array sol = problem.currentValue();
        assertEquals("x0 ≈ 1", 1.0, sol.get(0), 0.1);
        assertEquals("x1 ≈ -2", -2.0, sol.get(1), 0.1);
    }

    @Test
    public void testConstantBudgetSchemeConverges() {
        final Quadratic f = new Quadratic();
        final NoConstraint constraint = new NoConstraint();
        final Array init = new Array(new double[] { 5.0, 5.0 });
        final Problem problem = new Problem(f, constraint, init);
        final EndCriteria end = new EndCriteria(20000, 5000, 1e-4, 1e-8, 1e-8);
        // lambda, T0, K (budget), alpha (cooling exponent)
        final SimulatedAnnealing sa = new SimulatedAnnealing(0.5, 1.0, 1000, 2.0,
                new MersenneTwisterUniformRng(42L));
        sa.minimize(problem, end);
        final Array sol = problem.currentValue();
        assertEquals("x0 ≈ 1", 1.0, sol.get(0), 0.1);
        assertEquals("x1 ≈ -2", -2.0, sol.get(1), 0.1);
    }
}
