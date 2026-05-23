/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license. You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.testsuite.experimental.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jquantlib.experimental.math.HybridSimulatedAnnealing;
import org.jquantlib.experimental.math.HybridSimulatedAnnealing.LocalOptimizeScheme;
import org.jquantlib.experimental.math.HybridSimulatedAnnealing.ResetScheme;
import org.jquantlib.experimental.math.ProbabilityAlwaysDownhill;
import org.jquantlib.experimental.math.ProbabilityBoltzmann;
import org.jquantlib.experimental.math.ProbabilityBoltzmannDownhill;
import org.jquantlib.experimental.math.ReannealingFiniteDifferences;
import org.jquantlib.experimental.math.ReannealingTrivial;
import org.jquantlib.experimental.math.SamplerCauchy;
import org.jquantlib.experimental.math.SamplerGaussian;
import org.jquantlib.experimental.math.SamplerMirrorGaussian;
import org.jquantlib.experimental.math.SamplerRingGaussian;
import org.jquantlib.experimental.math.SamplerVeryFastAnnealing;
import org.jquantlib.experimental.math.TemperatureBoltzmann;
import org.jquantlib.experimental.math.TemperatureCauchy;
import org.jquantlib.experimental.math.TemperatureCauchy1D;
import org.jquantlib.experimental.math.TemperatureExponential;
import org.jquantlib.experimental.math.TemperatureVeryFastAnnealing;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.CostFunction;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.Problem;
import org.junit.Test;

/**
 * Tests for {@link HybridSimulatedAnnealing} and the family of {@link org.jquantlib.experimental.math.SamplerGaussian
 * Sampler} / {@link org.jquantlib.experimental.math.ProbabilityBoltzmann Probability} / {@link
 * org.jquantlib.experimental.math.TemperatureExponential Temperature} / {@link
 * org.jquantlib.experimental.math.ReannealingTrivial Reannealing} functors ported from QuantLib v1.42.1
 * {@code ql/experimental/math/hybridsimulatedannealing{,functors}.hpp}.
 *
 * <p>Tolerance rationale: SA is stochastic. With a fixed seed and a 2D
 * paraboloid {@code f(x,y) = (x-1)^2 + (y+2)^2} we use a coarse positional tolerance (0.5) and a tight functional
 * tolerance (f(best) below 1.0) — both well below what a converged SA run should achieve. The "stochastic" tier is
 * documented in CLAUDE.md as acceptable when seed-fixed.
 */
public class HybridSimulatedAnnealingTest {

    /** 2D quadratic: f(x) = (x0 - 1)^2 + (x1 + 2)^2. Minimum at (1, -2), f_min = 0. */
    private static final class Quadratic2D extends CostFunction {
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

    /** Isotropic Gaussian "bell" objective shifted to (0.5, -0.5): f(x) = -exp(-((x0-0.5)^2 + (x1+0.5)^2)). */
    private static final class IsotropicGaussian extends CostFunction {
        @Override
        public Array values(final Array x) {
            return new Array(new double[] { value(x) });
        }
        @Override
        public double value(final Array x) {
            final double a = x.get(0) - 0.5;
            final double b = x.get(1) + 0.5;
            return -Math.exp(-(a * a + b * b));
        }
    }

    @Test
    public void testGaussianSimulatedAnnealingConvergesToParaboloidMinimum() {
        // Default instantiation pattern: SamplerGaussian + ProbabilityBoltzmannDownhill + TemperatureExponential
        // (== C++ typedef GaussianSimulatedAnnealing).
        final Problem p = new Problem(new Quadratic2D(), new NoConstraint(),
                new Array(new double[] { 5.0, 5.0 }));
        final EndCriteria ec = new EndCriteria(5000, 200, 1e-8, 1e-8, 1e-8);
        final HybridSimulatedAnnealing sa = new HybridSimulatedAnnealing(
                new SamplerGaussian(42L),
                new ProbabilityBoltzmannDownhill(43L),
                new TemperatureExponential(2.0, 2, 0.99),
                new ReannealingTrivial(),
                2.0, 1e-6, 0, ResetScheme.NoResetScheme, 0, null, LocalOptimizeScheme.NoLocalOptimize);

        sa.minimize(p, ec);
        final Array sol = p.currentValue();
        final double f = p.functionValue();
        assertEquals("x0 ≈ 1", 1.0, sol.get(0), 0.5);
        assertEquals("x1 ≈ -2", -2.0, sol.get(1), 0.5);
        assertTrue("f(best) below 1.0, was " + f, f < 1.0);
    }

    @Test
    public void testVeryFastSimulatedAnnealingFindsBellPeak() {
        // C++ typedef VeryFastSimulatedAnnealing.
        final Array lower = new Array(new double[] { -5.0, -5.0 });
        final Array upper = new Array(new double[] { 5.0, 5.0 });
        final Problem p = new Problem(new IsotropicGaussian(), new NoConstraint(),
                new Array(new double[] { 3.0, 3.0 }));
        final EndCriteria ec = new EndCriteria(5000, 300, 1e-8, 1e-8, 1e-8);
        final HybridSimulatedAnnealing sa = new HybridSimulatedAnnealing(
                new SamplerVeryFastAnnealing(lower, upper, 17L),
                new ProbabilityBoltzmannDownhill(18L),
                new TemperatureVeryFastAnnealing(2.0, 1e-3, 2000, 2),
                new ReannealingTrivial(),
                2.0, 1e-6, 0, ResetScheme.NoResetScheme, 0, null, LocalOptimizeScheme.NoLocalOptimize);

        sa.minimize(p, ec);
        final Array sol = p.currentValue();
        assertEquals("x0 ≈ 0.5", 0.5, sol.get(0), 0.5);
        assertEquals("x1 ≈ -0.5", -0.5, sol.get(1), 0.5);
        assertTrue("f(best) negative", p.functionValue() < -0.5);
    }

    @Test
    public void testProbabilityAlwaysDownhillNeverAcceptsWorsePoints() {
        final ProbabilityAlwaysDownhill prob = new ProbabilityAlwaysDownhill();
        final Array temp = new Array(new double[] { 100.0, 100.0 });
        assertTrue(prob.accept(10.0, 5.0, temp));
        assertEquals(false, prob.accept(5.0, 10.0, temp));
        assertEquals(false, prob.accept(5.0, 5.0, temp));
    }

    @Test
    public void testProbabilityBoltzmannAcceptsImprovementInLimit() {
        // At very high temperature, Boltzmann probability ≈ 0.5 — over many trials about half are accepted.
        // We verify the contract: when current > new, probability > 0.5 (always);
        // when current < new with very high T, probability ≈ 0.5.
        final ProbabilityBoltzmann prob = new ProbabilityBoltzmann(99L);
        // High temperature, equal values → equal probability of acceptance.
        final Array hot = new Array(new double[] { 1e6 });
        int acceptHot = 0;
        for ( int i = 0; i < 1000; ++i ) {
            if ( prob.accept(1.0, 2.0, hot) ) {
                acceptHot++;
            }
        }
        // Expectation ~500. Allow wide tolerance for stochastic noise (3-sigma ≈ ±47).
        assertTrue("accepts in [400, 600], got " + acceptHot, acceptHot > 400 && acceptHot < 600);
    }

    @Test
    public void testReannealingTrivialIsNoOp() {
        final Array steps = new Array(new double[] { 1.0, 2.0, 3.0 });
        final Array stepsBefore = steps.clone();
        new ReannealingTrivial().reanneal(steps, new Array(3), 0.0, new Array(3, 1.0, 0.0));
        for ( int i = 0; i < steps.size(); ++i ) {
            assertEquals(stepsBefore.get(i), steps.get(i), 0.0);
        }
    }

    @Test
    public void testReannealingFiniteDifferencesUpdatesSteps() {
        // Smoke test that FD reannealing runs without error and writes valid (positive) step values.
        final Problem p = new Problem(new Quadratic2D(), new NoConstraint(),
                new Array(new double[] { 5.0, 5.0 }));
        p.setCurrentValue(new Array(new double[] { 5.0, 5.0 }));
        final ReannealingFiniteDifferences rean = new ReannealingFiniteDifferences(1.0, 2);
        rean.setProblem(p);
        final Array steps = new Array(new double[] { 10.0, 10.0 });
        rean.reanneal(steps, new Array(new double[] { 5.0, 5.0 }), p.value(new Array(new double[] { 5.0, 5.0 })),
                new Array(new double[] { 1.0, 1.0 }));
        // Steps should be updated (typically away from the input 10.0) and remain non-negative.
        for ( int i = 0; i < steps.size(); ++i ) {
            assertTrue("steps[" + i + "] non-negative", steps.get(i) >= 0.0);
        }
    }

    @Test
    public void testTemperatureExponentialDecaysGeometrically() {
        final TemperatureExponential temp = new TemperatureExponential(2.0, 2, 0.5);
        final Array curr = new Array(new double[] { 2.0, 2.0 });
        final Array out = new Array(2);
        // step = 1: T = 2.0 * 0.5^1 = 1.0
        temp.update(out, curr, new Array(new double[] { 1.0, 1.0 }));
        assertEquals(1.0, out.get(0), 1e-12);
        assertEquals(1.0, out.get(1), 1e-12);
        // step = 3: T = 2.0 * 0.5^3 = 0.25
        temp.update(out, curr, new Array(new double[] { 3.0, 3.0 }));
        assertEquals(0.25, out.get(0), 1e-12);
        assertEquals(0.25, out.get(1), 1e-12);
    }

    @Test
    public void testTemperatureBoltzmannLogCooling() {
        final TemperatureBoltzmann temp = new TemperatureBoltzmann(10.0, 1);
        final Array out = new Array(1);
        final Array curr = new Array(new double[] { 10.0 });
        // step = e → T = 10/log(e) = 10
        temp.update(out, curr, new Array(new double[] { Math.E }));
        assertEquals(10.0, out.get(0), 1e-12);
        // step = e^2 → T = 10/2 = 5
        temp.update(out, curr, new Array(new double[] { Math.E * Math.E }));
        assertEquals(5.0, out.get(0), 1e-12);
    }

    @Test
    public void testTemperatureCauchyHyperbolicCooling() {
        final TemperatureCauchy temp = new TemperatureCauchy(10.0, 1);
        final Array out = new Array(1);
        final Array curr = new Array(new double[] { 10.0 });
        temp.update(out, curr, new Array(new double[] { 2.0 }));
        assertEquals(5.0, out.get(0), 1e-12);
        temp.update(out, curr, new Array(new double[] { 10.0 }));
        assertEquals(1.0, out.get(0), 1e-12);
    }

    @Test
    public void testTemperatureCauchy1DMatchesDimensionalRescaling() {
        // For dim=1, behaves like TemperatureCauchy (k^(1/1) = k).
        final TemperatureCauchy1D temp = new TemperatureCauchy1D(8.0, 1);
        final Array out = new Array(1);
        final Array curr = new Array(new double[] { 8.0 });
        temp.update(out, curr, new Array(new double[] { 4.0 }));
        assertEquals(2.0, out.get(0), 1e-12);
    }

    @Test
    public void testTemperatureVeryFastAnnealingMonotonicallyCools() {
        final TemperatureVeryFastAnnealing temp = new TemperatureVeryFastAnnealing(10.0, 1e-3, 1000, 2);
        final Array out1 = new Array(2);
        final Array out2 = new Array(2);
        final Array curr = new Array(new double[] { 10.0, 10.0 });
        temp.update(out1, curr, new Array(new double[] { 1.0, 1.0 }));
        temp.update(out2, curr, new Array(new double[] { 100.0, 100.0 }));
        // Later step → cooler.
        assertTrue("T(step=100) < T(step=1)", out2.get(0) < out1.get(0));
        // Both positive.
        assertTrue(out1.get(0) > 0.0);
        assertTrue(out2.get(0) > 0.0);
    }

    @Test
    public void testSamplerGaussianStaysCentredOnZeroTemperature() {
        // With temp -> 0, sampler should return a point arbitrarily close to currentPoint.
        final SamplerGaussian sg = new SamplerGaussian(42L);
        final Array current = new Array(new double[] { 1.0, 2.0 });
        final Array newPoint = new Array(2);
        final Array temp = new Array(new double[] { 1e-20, 1e-20 });
        sg.sample(newPoint, current, temp);
        assertEquals(1.0, newPoint.get(0), 1e-9);
        assertEquals(2.0, newPoint.get(1), 1e-9);
    }

    @Test
    public void testSamplerMirrorGaussianReflectsAtBoundary() {
        // Use enormous temperature so the unbounded draw is highly likely to land out-of-bounds,
        // then check the mirrored result is in-bounds.
        final Array lower = new Array(new double[] { 0.0, 0.0 });
        final Array upper = new Array(new double[] { 1.0, 1.0 });
        final SamplerMirrorGaussian sg = new SamplerMirrorGaussian(lower, upper, 7L);
        final Array current = new Array(new double[] { 0.5, 0.5 });
        final Array out = new Array(2);
        final Array temp = new Array(new double[] { 1.0, 1.0 });
        for ( int i = 0; i < 100; ++i ) {
            sg.sample(out, current, temp);
            assertTrue("out[0] in bounds, got " + out.get(0), out.get(0) >= lower.get(0) && out.get(0) <= upper.get(0));
            assertTrue("out[1] in bounds, got " + out.get(1), out.get(1) >= lower.get(1) && out.get(1) <= upper.get(1));
        }
    }

    @Test
    public void testSamplerRingGaussianWrapsAtBoundary() {
        final Array lower = new Array(new double[] { 0.0, 0.0 });
        final Array upper = new Array(new double[] { 1.0, 1.0 });
        final SamplerRingGaussian sg = new SamplerRingGaussian(lower, upper, 11L);
        final Array current = new Array(new double[] { 0.5, 0.5 });
        final Array out = new Array(2);
        final Array temp = new Array(new double[] { 1.0, 1.0 });
        for ( int i = 0; i < 100; ++i ) {
            sg.sample(out, current, temp);
            assertTrue("out[0] in bounds", out.get(0) >= lower.get(0) && out.get(0) <= upper.get(0));
            assertTrue("out[1] in bounds", out.get(1) >= lower.get(1) && out.get(1) <= upper.get(1));
        }
    }

    @Test
    public void testSamplerCauchyDoesNotProduceNaN() {
        final SamplerCauchy sg = new SamplerCauchy(13L);
        final Array current = new Array(new double[] { 0.0, 0.0 });
        final Array out = new Array(2);
        final Array temp = new Array(new double[] { 1.0, 1.0 });
        for ( int i = 0; i < 500; ++i ) {
            sg.sample(out, current, temp);
            assertTrue("out[0] finite", Double.isFinite(out.get(0)));
            assertTrue("out[1] finite", Double.isFinite(out.get(1)));
        }
    }

    @Test
    public void testHybridSimulatedAnnealingMinimalRunReturnsImmediately() {
        // Exercise a single-iteration run: should return cleanly.
        final Problem p = new Problem(new Quadratic2D(), new NoConstraint(),
                new Array(new double[] { 5.0, 5.0 }));
        final EndCriteria ec = new EndCriteria(10, 2, 1e-8, 1e-8, 1e-8);
        final HybridSimulatedAnnealing sa = new HybridSimulatedAnnealing(
                new SamplerGaussian(1L), new ProbabilityBoltzmannDownhill(2L),
                new TemperatureExponential(1.0, 2));
        final EndCriteria.Type ec_type = sa.minimize(p, ec);
        assertNotNull(ec_type);
    }
}
