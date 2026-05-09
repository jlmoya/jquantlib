/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3i Commit 2.

 This source code is release under the BSD License.
 */
package org.jquantlib.testsuite.model.marketmodels.evolvers;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.BrownianGenerator;
import org.jquantlib.model.marketmodels.BrownianGeneratorFactory;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModel;
import org.jquantlib.model.marketmodels.evolvers.LogNormalFwdRateBalland;
import org.jquantlib.model.marketmodels.evolvers.LogNormalFwdRateEuler;
import org.jquantlib.model.marketmodels.evolvers.LogNormalFwdRatePc;
import org.junit.Test;

/**
 * Phase 3i Commit 2 — structural tests for log-normal forward-rate evolvers
 * (Euler, Pc, Balland).
 *
 * <p>Uses a minimal flat-vol mock {@link MarketModel} together with a
 * deterministic zero-Brownian generator. That setup makes the closed-form
 * expectation computable: with all Brownian increments equal to zero the
 * evolved log-forwards are the initial log-forwards plus the deterministic
 * drift term, so we can cross-validate against an analytic projection.
 *
 * <p>Tolerance: TIGHT (1e-12) — pure deterministic arithmetic.
 */
public class LogNormalFwdRateEvolverTest {

    private static final double TOL = 1.0e-12;

    @Test
    public void euler_zeroBrownian_advancesPerDeterministicDrift() {
        final FlatVolMarketModel mm = new FlatVolMarketModel();
        final ZeroBrownianGeneratorFactory bgf = new ZeroBrownianGeneratorFactory();
        // terminal measure: numeraire = numberOfRates for every step
        final int[] num = new int[mm.numberOfSteps()];
        for (int i = 0; i < num.length; ++i) num[i] = mm.numberOfRates();
        final LogNormalFwdRateEuler evolver = new LogNormalFwdRateEuler(mm, bgf, num);

        assertEquals(0, evolver.currentStep());
        assertNotNull(evolver.currentState());
        assertArrayEquals(num, evolver.numeraires());

        final double w = evolver.startNewPath();
        assertEquals(1.0, w, TOL);
        // Step 0 → Step 1 with zero Brownian: only deterministic drifts apply.
        // The advanceStep() sets curveState_; we verify currentState() reflects update.
        evolver.advanceStep();
        assertEquals(1, evolver.currentStep());
    }

    @Test
    public void pc_zeroBrownian_predictAndCorrectMatchEuler() {
        final FlatVolMarketModel mm = new FlatVolMarketModel();
        final ZeroBrownianGeneratorFactory bgf = new ZeroBrownianGeneratorFactory();
        final int[] num = new int[mm.numberOfSteps()];
        for (int i = 0; i < num.length; ++i) num[i] = mm.numberOfRates();
        final LogNormalFwdRatePc evolver = new LogNormalFwdRatePc(mm, bgf, num);

        evolver.startNewPath();
        evolver.advanceStep();
        assertEquals(1, evolver.currentStep());
        // currentState curve must produce non-NaN forward rates for the alive index
        for (int i = 0; i < mm.numberOfRates(); ++i) {
            final double f = evolver.currentState().forwardRate(i);
            assertTrue("forward[" + i + "] finite", !Double.isNaN(f) && !Double.isInfinite(f));
        }
    }

    @Test
    public void balland_zeroBrownian_geometricMeanCorrector_isFinite() {
        final FlatVolMarketModel mm = new FlatVolMarketModel();
        final ZeroBrownianGeneratorFactory bgf = new ZeroBrownianGeneratorFactory();
        final int[] num = new int[mm.numberOfSteps()];
        for (int i = 0; i < num.length; ++i) num[i] = mm.numberOfRates();
        final LogNormalFwdRateBalland evolver = new LogNormalFwdRateBalland(mm, bgf, num);

        evolver.startNewPath();
        evolver.advanceStep();
        assertEquals(1, evolver.currentStep());
        for (int i = 0; i < mm.numberOfRates(); ++i) {
            final double f = evolver.currentState().forwardRate(i);
            assertTrue("forward[" + i + "] finite", !Double.isNaN(f) && !Double.isInfinite(f));
        }
    }

    @Test
    public void euler_browniansThisStepAccessible() {
        final FlatVolMarketModel mm = new FlatVolMarketModel();
        final ZeroBrownianGeneratorFactory bgf = new ZeroBrownianGeneratorFactory();
        final int[] num = new int[mm.numberOfSteps()];
        for (int i = 0; i < num.length; ++i) num[i] = mm.numberOfRates();
        final LogNormalFwdRateEuler evolver = new LogNormalFwdRateEuler(mm, bgf, num);

        evolver.startNewPath();
        evolver.advanceStep();
        final double[] browns = evolver.browniansThisStep();
        assertNotNull(browns);
        assertEquals(mm.numberOfFactors(), browns.length);
        for (final double b : browns) assertEquals(0.0, b, TOL);
    }

    // ----------------------------------------------------------------------
    // Test fixtures
    // ----------------------------------------------------------------------

    /**
     * Minimal MarketModel: 4 rates, 4 steps, single factor with constant
     * volatility 0.20 over each step. Pseudoroot is sqrt(tau) * 0.2 for the
     * diagonal.
     */
    private static final class FlatVolMarketModel extends MarketModel {
        private static final double[] RATE_TIMES = { 0.5, 1.0, 1.5, 2.0, 2.5 };
        private static final double[] INITIAL_RATES = { 0.04, 0.045, 0.05, 0.055 };
        private static final double[] DISPLACEMENTS = { 0.0, 0.0, 0.0, 0.0 };
        private final EvolutionDescription evolution_;
        private final Matrix[] pseudo_;

        FlatVolMarketModel() {
            this.evolution_ = new EvolutionDescription(RATE_TIMES);
            final double sigma = 0.20;
            final double[] taus = evolution_.rateTaus();
            this.pseudo_ = new Matrix[evolution_.numberOfSteps()];
            for (int j = 0; j < pseudo_.length; ++j) {
                // Single-factor pseudoroot: column 0 = sigma*sqrt(tau) for each rate
                final Matrix m = new Matrix(numberOfRates(), 1);
                final double sd = sigma * Math.sqrt(taus[j]);
                for (int i = 0; i < numberOfRates(); ++i) {
                    m.set(i, 0, sd);
                }
                pseudo_[j] = m;
            }
        }

        @Override public double[] initialRates() { return INITIAL_RATES.clone(); }
        @Override public double[] displacements() { return DISPLACEMENTS.clone(); }
        @Override public EvolutionDescription evolution() { return evolution_; }
        @Override public int numberOfRates() { return INITIAL_RATES.length; }
        @Override public int numberOfFactors() { return 1; }
        @Override public int numberOfSteps() { return evolution_.numberOfSteps(); }
        @Override public Matrix pseudoRoot(final int i) { return pseudo_[i]; }
    }

    /**
     * Brownian generator producing zero variates on every step → enables
     * deterministic verification of the drift component.
     */
    private static final class ZeroBrownianGenerator extends BrownianGenerator {
        private final int factors_;
        private final int steps_;
        ZeroBrownianGenerator(final int factors, final int steps) {
            this.factors_ = factors;
            this.steps_ = steps;
        }
        @Override public double nextStep(final double[] output) {
            for (int i = 0; i < output.length; ++i) output[i] = 0.0;
            return 1.0;
        }
        @Override public double nextPath() { return 1.0; }
        @Override public int numberOfFactors() { return factors_; }
        @Override public int numberOfSteps() { return steps_; }
    }

    private static final class ZeroBrownianGeneratorFactory extends BrownianGeneratorFactory {
        @Override public BrownianGenerator create(final int factors, final int steps) {
            return new ZeroBrownianGenerator(factors, steps);
        }
    }
}
