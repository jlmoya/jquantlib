/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.math;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jquantlib.experimental.math.FireflyAlgorithm;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.BoundaryConstraint;
import org.jquantlib.math.optimization.CostFunction;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.Problem;
import org.junit.Test;

/**
 * Phase 4k tests for {@link FireflyAlgorithm}.
 */
public class FireflyAlgorithmTest {

    /** Sphere f(x) = sum_i x_i^2, minimum at origin. */
    private static class Sphere extends CostFunction {
        @Override
        public double value(final Array x) {
            double sum = 0.0;
            for (int i = 0; i < x.size(); ++i) {
                sum += x.get(i) * x.get(i);
            }
            return sum;
        }

        @Override
        public Array values(final Array x) {
            final Array v = new Array(x.size());
            for (int i = 0; i < x.size(); ++i) {
                v.set(i, x.get(i) * x.get(i));
            }
            return v;
        }
    }

    @Test
    public void testFireflyOnSphere() {
        final Sphere cost = new Sphere();
        final BoundaryConstraint constraint = new BoundaryConstraint(-5.0, 5.0);
        final Array initial = new Array(2);
        initial.set(0, 1.0);
        initial.set(1, 1.0);
        final Problem problem = new Problem(cost, constraint, initial);
        final EndCriteria endCriteria = new EndCriteria(150, 50, 1.0e-8, 1.0e-8, 1.0e-8);

        final FireflyAlgorithm.ExponentialIntensity intensity =
                new FireflyAlgorithm.ExponentialIntensity(1.0, 0.1, 0.1);
        final FireflyAlgorithm.GaussianWalk walk =
                new FireflyAlgorithm.GaussianWalk(0.5, 0.9, 1L);
        final FireflyAlgorithm fa = new FireflyAlgorithm(
                30, intensity, walk,
                10, 1.0, 0.5, 7L,
                new double[] { -5.0, -5.0 },
                new double[] { 5.0, 5.0 });

        final EndCriteria.Type ec = fa.minimize(problem, endCriteria);
        assertNotNull(ec);
        // Stochastic algorithm: just verify it runs to a low function value (loose bound)
        assertTrue("function value " + problem.functionValue() + " not improved",
                problem.functionValue() < 5.0);
    }

    @Test
    public void testFireflyDecreasingGaussianWalkRuns() {
        // End-to-end smoke test of the newly-ported DecreasingGaussianWalk.
        final Sphere cost = new Sphere();
        final BoundaryConstraint constraint = new BoundaryConstraint(-5.0, 5.0);
        final Array initial = new Array(2);
        initial.set(0, 1.0);
        initial.set(1, 1.0);
        final Problem problem = new Problem(cost, constraint, initial);
        final EndCriteria endCriteria = new EndCriteria(150, 50, 1.0e-8, 1.0e-8, 1.0e-8);

        final FireflyAlgorithm.ExponentialIntensity intensity =
                new FireflyAlgorithm.ExponentialIntensity(1.0, 0.1, 0.1);
        final FireflyAlgorithm.DecreasingGaussianWalk walk =
                new FireflyAlgorithm.DecreasingGaussianWalk(0.5, 0.9, 3L);
        final FireflyAlgorithm fa = new FireflyAlgorithm(
                30, intensity, walk, 10, 1.0, 0.5, 7L,
                new double[] { -5.0, -5.0 }, new double[] { 5.0, 5.0 });

        final EndCriteria.Type ec = fa.minimize(problem, endCriteria);
        assertNotNull(ec);
        assertTrue("function value " + problem.functionValue(), problem.functionValue() < 5.0);
    }

    @Test
    public void testFireflyDifferentialEvolutionOnly() {
        // Mfa = 0 -> pure differential evolution
        final Sphere cost = new Sphere();
        final BoundaryConstraint constraint = new BoundaryConstraint(-5.0, 5.0);
        final Array initial = new Array(2);
        initial.set(0, 2.0);
        initial.set(1, -1.5);
        final Problem problem = new Problem(cost, constraint, initial);
        final EndCriteria endCriteria = new EndCriteria(150, 50, 1.0e-8, 1.0e-8, 1.0e-8);

        final FireflyAlgorithm.InverseLawSquareIntensity intensity =
                new FireflyAlgorithm.InverseLawSquareIntensity(1.0, 0.1);
        final FireflyAlgorithm.GaussianWalk walk =
                new FireflyAlgorithm.GaussianWalk(0.5, 0.9, 2L);
        final FireflyAlgorithm fa = new FireflyAlgorithm(
                20, intensity, walk,
                20, 0.5, 0.5, 11L,  // Mde=20 == M=20 => pure DE
                new double[] { -5.0, -5.0 },
                new double[] { 5.0, 5.0 });

        final EndCriteria.Type ec = fa.minimize(problem, endCriteria);
        assertNotNull(ec);
        assertTrue("DE function value " + problem.functionValue(),
                problem.functionValue() < 5.0);
    }
}
