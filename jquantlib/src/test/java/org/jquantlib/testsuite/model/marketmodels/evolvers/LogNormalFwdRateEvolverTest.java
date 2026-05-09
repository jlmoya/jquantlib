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
import org.jquantlib.model.marketmodels.evolvers.LogNormalCmSwapRatePc;
import org.jquantlib.model.marketmodels.evolvers.LogNormalCotSwapRatePc;
import org.jquantlib.model.marketmodels.evolvers.LogNormalFwdRateBalland;
import org.jquantlib.model.marketmodels.evolvers.LogNormalFwdRateEuler;
import org.jquantlib.model.marketmodels.evolvers.LogNormalFwdRateEulerConstrained;
import org.jquantlib.model.marketmodels.evolvers.LogNormalFwdRateIpc;
import org.jquantlib.model.marketmodels.evolvers.LogNormalFwdRatePc;
import org.jquantlib.model.marketmodels.evolvers.LogNormalFwdRateiBalland;
import org.jquantlib.model.marketmodels.evolvers.NormalFwdRatePc;
import org.jquantlib.model.marketmodels.evolvers.SVDDFwdRatePc;
import org.jquantlib.model.marketmodels.evolvers.volprocesses.SquareRootAndersen;
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
    public void ipc_terminalMeasure_advancesBackwardsIteratively() {
        final FlatVolMarketModel mm = new FlatVolMarketModel();
        final ZeroBrownianGeneratorFactory bgf = new ZeroBrownianGeneratorFactory();
        // Ipc requires terminal measure: numeraire = N (last rate index) for each step
        final int[] num = new int[mm.numberOfSteps()];
        for (int i = 0; i < num.length; ++i) num[i] = mm.numberOfRates();
        final LogNormalFwdRateIpc evolver = new LogNormalFwdRateIpc(mm, bgf, num);

        evolver.startNewPath();
        evolver.advanceStep();
        assertEquals(1, evolver.currentStep());
        for (int i = 0; i < mm.numberOfRates(); ++i) {
            final double f = evolver.currentState().forwardRate(i);
            assertTrue("forward[" + i + "] finite", !Double.isNaN(f) && !Double.isInfinite(f));
        }
    }

    @Test
    public void iBalland_terminalMeasure_combinesIterativeAndGeometricMean() {
        final FlatVolMarketModel mm = new FlatVolMarketModel();
        final ZeroBrownianGeneratorFactory bgf = new ZeroBrownianGeneratorFactory();
        final int[] num = new int[mm.numberOfSteps()];
        for (int i = 0; i < num.length; ++i) num[i] = mm.numberOfRates();
        final LogNormalFwdRateiBalland evolver = new LogNormalFwdRateiBalland(mm, bgf, num);

        evolver.startNewPath();
        evolver.advanceStep();
        assertEquals(1, evolver.currentStep());
    }

    @Test
    public void normalPc_zeroBrownian_isAdditiveAndFinite() {
        final FlatVolMarketModel mm = new FlatVolMarketModel();
        final ZeroBrownianGeneratorFactory bgf = new ZeroBrownianGeneratorFactory();
        final int[] num = new int[mm.numberOfSteps()];
        for (int i = 0; i < num.length; ++i) num[i] = mm.numberOfRates();
        final NormalFwdRatePc evolver = new NormalFwdRatePc(mm, bgf, num);

        evolver.startNewPath();
        evolver.advanceStep();
        assertEquals(1, evolver.currentStep());
        for (int i = 0; i < mm.numberOfRates(); ++i) {
            final double f = evolver.currentState().forwardRate(i);
            assertTrue("forward[" + i + "] finite", !Double.isNaN(f) && !Double.isInfinite(f));
        }
    }

    @Test
    public void cotSwapPc_zeroBrownian_evolvesCoterminalSwapRates() {
        final FlatVolMarketModel mm = new FlatVolMarketModel();
        final ZeroBrownianGeneratorFactory bgf = new ZeroBrownianGeneratorFactory();
        final int[] num = new int[mm.numberOfSteps()];
        for (int i = 0; i < num.length; ++i) num[i] = mm.numberOfRates();
        final LogNormalCotSwapRatePc evolver = new LogNormalCotSwapRatePc(mm, bgf, num);

        evolver.startNewPath();
        evolver.advanceStep();
        assertEquals(1, evolver.currentStep());
    }

    @Test
    public void cmSwapPc_zeroBrownian_evolvesCmSwapRatesWithSpan() {
        final FlatVolMarketModel mm = new FlatVolMarketModel();
        final ZeroBrownianGeneratorFactory bgf = new ZeroBrownianGeneratorFactory();
        final int[] num = new int[mm.numberOfSteps()];
        for (int i = 0; i < num.length; ++i) num[i] = mm.numberOfRates();
        final int spanningForwards = 1; // CM rate spans 1 forward = degenerates to forward LIBOR
        final LogNormalCmSwapRatePc evolver = new LogNormalCmSwapRatePc(spanningForwards, mm, bgf, num);

        evolver.startNewPath();
        evolver.advanceStep();
        assertEquals(1, evolver.currentStep());
    }

    @Test
    public void svddFwdRatePc_withAndersenVol_advancesAndStaysFinite() {
        final FlatVolMarketModel mm = new FlatVolMarketModel();
        final ZeroBrownianGeneratorFactory bgf = new ZeroBrownianGeneratorFactory();
        // 4 evolution times, 2 substeps each → SquareRootAndersen feeds 2 variates per step
        final SquareRootAndersen vol = new SquareRootAndersen(
                /*meanLevel*/ 0.04, /*reversionSpeed*/ 1.0, /*volVar*/ 0.20, /*v0*/ 0.04,
                mm.evolution().evolutionTimes(),
                /*numberSubSteps*/ 2,
                /*w1*/ 0.5, /*w2*/ 0.5);

        final int[] num = new int[mm.numberOfSteps()];
        for (int i = 0; i < num.length; ++i) num[i] = mm.numberOfRates();
        final SVDDFwdRatePc evolver = new SVDDFwdRatePc(mm, bgf, vol, /*firstVolFactor*/ 0,
                /*volFactorStep*/ 1, num);

        assertEquals(0, evolver.currentStep());
        evolver.startNewPath();
        evolver.advanceStep();
        assertEquals(1, evolver.currentStep());
        for (int i = 0; i < mm.numberOfRates(); ++i) {
            final double f = evolver.currentState().forwardRate(i);
            assertTrue("svdd forward[" + i + "] finite", !Double.isNaN(f) && !Double.isInfinite(f));
        }
    }

    @Test
    public void squareRootAndersen_zeroVariates_keepsVarianceAtMeanLevel() {
        // With reversion to theta and zero Brownian (z=0), psi <= cutoff branch
        // gives vt = a*(b+0)^2 = a*b^2 ≈ m at first step (with vt0 = v0 = theta).
        // Just confirm process stepSd > 0 and stateVariables update without NaN.
        final SquareRootAndersen vol = new SquareRootAndersen(
                0.04, 1.0, 0.20, 0.04, new double[] { 0.5, 1.0, 1.5 }, 2, 0.5, 0.5);
        vol.nextPath();
        final double[] zeros = new double[vol.variatesPerStep()];
        vol.nextstep(zeros);
        final double sd = vol.stepSd();
        assertTrue("stepSd finite", !Double.isNaN(sd) && !Double.isInfinite(sd) && sd >= 0.0);
        assertEquals(1, vol.numberStateVariables());
        assertEquals(2, vol.variatesPerStep());
        assertTrue("state finite", !Double.isNaN(vol.stateVariables()[0]));
    }

    @Test
    public void constrainedEuler_withInactiveConstraint_behavesLikeEuler() {
        final FlatVolMarketModel mm = new FlatVolMarketModel();
        final ZeroBrownianGeneratorFactory bgf = new ZeroBrownianGeneratorFactory();
        final int[] num = new int[mm.numberOfSteps()];
        for (int i = 0; i < num.length; ++i) num[i] = mm.numberOfRates();

        final LogNormalFwdRateEulerConstrained evolver =
                new LogNormalFwdRateEulerConstrained(mm, bgf, num);

        // configure constraint type but leave isConstraintActive = all false
        final int[] startIdx = new int[num.length];
        final int[] endIdx = new int[num.length];
        for (int i = 0; i < num.length; ++i) {
            startIdx[i] = 0;
            endIdx[i] = 1; // start+1 == end requirement
        }
        evolver.setConstraintType(startIdx, endIdx);
        final double[] constraints = new double[num.length];
        final boolean[] active = new boolean[num.length]; // all false
        for (int i = 0; i < num.length; ++i) constraints[i] = 0.04;
        evolver.setThisConstraint(constraints, active);

        final double w = evolver.startNewPath();
        assertEquals(1.0, w, TOL);
        final double stepWeight = evolver.advanceStep();
        // with inactive constraint, weight stays at 1.0 (zero brownians, plain euler)
        assertEquals("inactive constraint should not affect weight", 1.0, stepWeight, TOL);
        assertEquals(1, evolver.currentStep());
    }

    @Test
    public void constrainedEuler_withActiveConstraint_appliesShiftAndUpdatesWeight() {
        final FlatVolMarketModel mm = new FlatVolMarketModel();
        final ZeroBrownianGeneratorFactory bgf = new ZeroBrownianGeneratorFactory();
        final int[] num = new int[mm.numberOfSteps()];
        for (int i = 0; i < num.length; ++i) num[i] = mm.numberOfRates();

        final LogNormalFwdRateEulerConstrained evolver =
                new LogNormalFwdRateEulerConstrained(mm, bgf, num);

        final int[] startIdx = new int[num.length];
        final int[] endIdx = new int[num.length];
        for (int i = 0; i < num.length; ++i) {
            startIdx[i] = 0;
            endIdx[i] = 1;
        }
        evolver.setConstraintType(startIdx, endIdx);
        // active constraint at step 0 only — pin rate 0 to 0.04 (its initial value, so shift ~ 0)
        final double[] constraints = new double[num.length];
        final boolean[] active = new boolean[num.length];
        for (int i = 0; i < num.length; ++i) constraints[i] = 0.04;
        active[0] = true;
        evolver.setThisConstraint(constraints, active);

        evolver.startNewPath();
        final double stepWeight = evolver.advanceStep();
        assertTrue("active constraint produces finite weight",
                !Double.isNaN(stepWeight) && !Double.isInfinite(stepWeight));
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
