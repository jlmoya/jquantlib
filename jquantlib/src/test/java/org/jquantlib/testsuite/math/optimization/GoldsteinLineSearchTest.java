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
import static org.junit.Assert.assertTrue;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.CostFunction;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.GoldsteinLineSearch;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.Problem;
import org.jquantlib.math.optimization.SteepestDescent;
import org.junit.Test;

/**
 * Tests for {@link GoldsteinLineSearch}.
 */
public class GoldsteinLineSearchTest {

    /** Quadratic in 2 dims: f(x) = (x0 - 2)^2 + (x1 - 3)^2. Minimum at (2, 3). */
    private static class Quadratic2D extends CostFunction {
        @Override
        public Array values(final Array x) {
            return new Array(new double[] { x.get(0) - 2.0, x.get(1) - 3.0 });
        }
        @Override
        public double value(final Array x) {
            final double dx = x.get(0) - 2.0;
            final double dy = x.get(1) - 3.0;
            return dx * dx + dy * dy;
        }
        @Override
        public void gradient(final Array grad, final Array x) {
            grad.set(0, 2.0 * (x.get(0) - 2.0));
            grad.set(1, 2.0 * (x.get(1) - 3.0));
        }
    }

    @Test
    public void testSteepestDescentWithGoldsteinConverges() {
        final CostFunction f = new Quadratic2D();
        final NoConstraint constraint = new NoConstraint();
        final Array init = new Array(new double[] { 10.0, -5.0 });
        final Problem problem = new Problem(f, constraint, init);
        final EndCriteria end = new EndCriteria(1000, 100, 1e-12, 1e-12, 1e-12);
        final SteepestDescent sd = new SteepestDescent(new GoldsteinLineSearch());
        sd.minimize(problem, end);
        final Array sol = problem.currentValue();
        assertEquals(2.0, sol.get(0), 1e-4);
        assertEquals(3.0, sol.get(1), 1e-4);
        assertTrue("function value should be ~0 at minimum",
                problem.functionValue() < 1e-6);
    }
}
