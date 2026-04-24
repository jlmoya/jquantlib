/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Tests for the LevenbergMarquardt facade. See phase2a-plan §Task 2.7.
 */
package org.jquantlib.testsuite.math.optimization;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.CostFunction;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.LevenbergMarquardt;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.Problem;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Facade tests for {@link LevenbergMarquardt}. Uses the three LM-probe
 * problems (linear fit, quadratic fit, Rosenbrock) but exercises the
 * {@code minimize(Problem, EndCriteria)} public API rather than the
 * underlying {@link org.jquantlib.math.optimization.Minpack#lmdif} —
 * MinpackTest covers the latter. Tolerances here are loose because the
 * point is contract-correctness, not numeric parity.
 */
public class LevenbergMarquardtTest {

    // y ≈ 2*x - 1 with small noise. Same dataset as the lm_linear_fit probe.
    private static final double[] LIN_XS = { 0.0, 1.0, 2.0, 3.0, 4.0 };
    private static final double[] LIN_YS = { -1.1, 0.9, 3.2, 4.8, 7.1 };

    // y ≈ x^2 - 2x + 3 exact. Same dataset as lm_quadratic_fit probe.
    private static final double[] QUAD_XS = { -2.0, -1.0, 0.0, 1.0, 2.0 };
    private static final double[] QUAD_YS = { 11.0, 6.0, 3.0, 2.0, 3.0 };

    @Test
    public void minimize_linearFit_convergesNearSlopeInterceptTruth() {
        final CostFunction cf = new LinearFitCostFunction();
        final Array x0 = new Array(new double[] { 0.0, 0.0 });
        final Problem problem = new Problem(cf, new NoConstraint(), x0);
        final EndCriteria ec = new EndCriteria(200, 100, 0.0, 1.0e-10, 0.0);

        final LevenbergMarquardt lm = new LevenbergMarquardt(1.0e-8, 1.0e-10, 0.0);
        final EndCriteria.Type endType = lm.minimize(problem, ec);

        // info = 1/2/3/4 all map to StationaryFunctionValue.
        assertEquals(EndCriteria.Type.StationaryFunctionValue, endType);
        final Array xFinal = problem.currentValue();
        assertEquals("a", 2.0, xFinal.get(0), 0.1);
        assertEquals("b", -1.0, xFinal.get(1), 0.25);
    }

    @Test
    public void minimize_quadraticFit_recoversExactCoefficients() {
        final CostFunction cf = new QuadraticFitCostFunction();
        final Array x0 = new Array(new double[] { 0.0, 0.0, 0.0 });
        final Problem problem = new Problem(cf, new NoConstraint(), x0);
        final EndCriteria ec = new EndCriteria(200, 100, 0.0, 1.0e-10, 0.0);

        final LevenbergMarquardt lm = new LevenbergMarquardt(1.0e-8, 1.0e-10, 0.0);
        final EndCriteria.Type endType = lm.minimize(problem, ec);

        assertEquals(EndCriteria.Type.StationaryFunctionValue, endType);
        final Array xFinal = problem.currentValue();
        assertEquals("a", 1.0, xFinal.get(0), 1.0e-6);
        assertEquals("b", -2.0, xFinal.get(1), 1.0e-6);
        assertEquals("c", 3.0, xFinal.get(2), 1.0e-6);
    }

    @Test
    public void minimize_rosenbrock_reachesGlobalMinimum() {
        final CostFunction cf = new RosenbrockCostFunction();
        final Array x0 = new Array(new double[] { -1.2, 1.0 });
        final Problem problem = new Problem(cf, new NoConstraint(), x0);
        final EndCriteria ec = new EndCriteria(500, 250, 0.0, 1.0e-10, 0.0);

        final LevenbergMarquardt lm = new LevenbergMarquardt(1.0e-8, 1.0e-10, 0.0);
        final EndCriteria.Type endType = lm.minimize(problem, ec);

        assertEquals(EndCriteria.Type.StationaryFunctionValue, endType);
        final Array xFinal = problem.currentValue();
        assertEquals("x[0]", 1.0, xFinal.get(0), 1.0e-4);
        assertEquals("x[1]", 1.0, xFinal.get(1), 1.0e-4);
    }

    @Test
    public void getInfo_reportsMinpackTerminationCode() {
        final CostFunction cf = new LinearFitCostFunction();
        final Problem problem = new Problem(cf, new NoConstraint(),
                new Array(new double[] { 0.0, 0.0 }));
        final EndCriteria ec = new EndCriteria(200, 100, 0.0, 1.0e-10, 0.0);
        final LevenbergMarquardt lm = new LevenbergMarquardt(1.0e-8, 1.0e-10, 0.0);

        lm.minimize(problem, ec);
        final int info = lm.getInfo();
        // Converged ⇒ info ∈ {1, 2, 3, 4}. Nothing else is legal here.
        assertTrue("info=" + info, info >= 1 && info <= 4);
    }

    // --- Cost functions ----------------------------------------------------

    private static final class LinearFitCostFunction extends CostFunction {
        @Override
        public Array values(final Array x) {
            final Array r = new Array(LIN_XS.length);
            for (int i = 0; i < LIN_XS.length; i++) {
                r.set(i, LIN_YS[i] - (x.get(0) * LIN_XS[i] + x.get(1)));
            }
            return r;
        }

        @Override
        public double value(final Array x) {
            final Array r = values(x);
            double s = 0.0;
            for (int i = 0; i < r.size(); i++) {
                s += r.get(i) * r.get(i);
            }
            return 0.5 * s;
        }
    }

    private static final class QuadraticFitCostFunction extends CostFunction {
        @Override
        public Array values(final Array x) {
            final Array r = new Array(QUAD_XS.length);
            for (int i = 0; i < QUAD_XS.length; i++) {
                final double xi = QUAD_XS[i];
                r.set(i, QUAD_YS[i] - (x.get(0) * xi * xi + x.get(1) * xi + x.get(2)));
            }
            return r;
        }

        @Override
        public double value(final Array x) {
            final Array r = values(x);
            double s = 0.0;
            for (int i = 0; i < r.size(); i++) {
                s += r.get(i) * r.get(i);
            }
            return 0.5 * s;
        }
    }

    private static final class RosenbrockCostFunction extends CostFunction {
        @Override
        public Array values(final Array x) {
            final Array r = new Array(2);
            r.set(0, 10.0 * (x.get(1) - x.get(0) * x.get(0)));
            r.set(1, 1.0 - x.get(0));
            return r;
        }

        @Override
        public double value(final Array x) {
            final Array r = values(x);
            return 0.5 * (r.get(0) * r.get(0) + r.get(1) * r.get(1));
        }
    }
}
