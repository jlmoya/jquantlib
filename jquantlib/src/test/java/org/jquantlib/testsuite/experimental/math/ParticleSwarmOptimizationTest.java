/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jquantlib.experimental.math.ParticleSwarmOptimization;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.BoundaryConstraint;
import org.jquantlib.math.optimization.CostFunction;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.Problem;
import org.junit.Test;

/**
 * Phase 4k tests for {@link ParticleSwarmOptimization}.
 *
 * <p>Validates that PSO finds the minimum of simple smooth quadratic
 * functions to a few-percent accuracy. Stochastic optimisation is naturally
 * noisy so tolerances are loose.
 */
public class ParticleSwarmOptimizationTest {

    /** Sphere function f(x) = sum_i (x_i - shift)^2, minimum at x = shift. */
    private static class Sphere extends CostFunction {
        private final double shift_;

        Sphere(final double shift) {
            this.shift_ = shift;
        }

        @Override
        public double value(final Array x) {
            double sum = 0.0;
            for (int i = 0; i < x.size(); ++i) {
                final double d = x.get(i) - shift_;
                sum += d * d;
            }
            return sum;
        }

        @Override
        public Array values(final Array x) {
            final Array v = new Array(x.size());
            for (int i = 0; i < x.size(); ++i) {
                final double d = x.get(i) - shift_;
                v.set(i, d * d);
            }
            return v;
        }
    }

    @Test
    public void testFindsMinimumOfSphere() {
        final Sphere cost = new Sphere(0.0);
        final BoundaryConstraint constraint = new BoundaryConstraint(-5.0, 5.0);
        final Array initial = new Array(2);
        initial.set(0, 3.0);
        initial.set(1, -2.0);
        final Problem problem = new Problem(cost, constraint, initial);
        final EndCriteria endCriteria = new EndCriteria(200, 50, 1.0e-8, 1.0e-8, 1.0e-8);

        final ParticleSwarmOptimization.GlobalTopology topology =
                new ParticleSwarmOptimization.GlobalTopology();
        final ParticleSwarmOptimization.TrivialInertia inertia =
                new ParticleSwarmOptimization.TrivialInertia();
        final ParticleSwarmOptimization pso = new ParticleSwarmOptimization(
                30, topology, inertia, 2.05, 2.05, 12345L,
                new double[] { -5.0, -5.0 },
                new double[] { 5.0, 5.0 });

        final EndCriteria.Type ec = pso.minimize(problem, endCriteria);
        assertNotNull(ec);
        // Either MaxIterations or StationaryPoint is acceptable
        assertTrue(ec == EndCriteria.Type.MaxIterations
                || ec == EndCriteria.Type.StationaryPoint);
        // Function value should be small
        assertTrue("function value " + problem.functionValue(),
                problem.functionValue() < 1.0);
        // Each component should be close to 0
        assertEquals("x[0]", 0.0, problem.currentValue().get(0), 0.5);
        assertEquals("x[1]", 0.0, problem.currentValue().get(1), 0.5);
    }

    @Test
    public void testDecreasingInertiaConfiguration() {
        // Smoke test that DecreasingInertia integrates with PSO.
        final Sphere cost = new Sphere(1.0);
        final BoundaryConstraint constraint = new BoundaryConstraint(-3.0, 3.0);
        final Array initial = new Array(1);
        initial.set(0, 0.0);
        final Problem problem = new Problem(cost, constraint, initial);
        final EndCriteria endCriteria = new EndCriteria(100, 25, 1.0e-8, 1.0e-8, 1.0e-8);

        final ParticleSwarmOptimization.GlobalTopology topology =
                new ParticleSwarmOptimization.GlobalTopology();
        final ParticleSwarmOptimization.DecreasingInertia inertia =
                new ParticleSwarmOptimization.DecreasingInertia(0.5);
        final ParticleSwarmOptimization pso = new ParticleSwarmOptimization(
                20, topology, inertia, 0.9, 1.0, 1.0, 1L,
                new double[] { -3.0 }, new double[] { 3.0 });

        final EndCriteria.Type ec = pso.minimize(problem, endCriteria);
        assertNotNull(ec);
        assertTrue("solution near 1.0 (got " + problem.currentValue().get(0) + ")",
                Math.abs(problem.currentValue().get(0) - 1.0) < 1.0);
    }
}
