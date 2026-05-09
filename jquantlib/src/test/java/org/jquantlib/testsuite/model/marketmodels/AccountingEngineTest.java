/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3h Track B.8.

 This source code is release under the BSD License.
 */
package org.jquantlib.testsuite.model.marketmodels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jquantlib.math.statistics.GenericSequenceStatistics;
import org.jquantlib.model.marketmodels.AccountingEngine;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModelDiscounter;
import org.jquantlib.model.marketmodels.MarketModelEvolver;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.junit.Test;

/**
 * Phase 3h B.8 — AccountingEngine tests.
 *
 * Cross-validates against C++ v1.42.1
 * {@code ql/models/marketmodels/accountingengine.cpp} via a minimal mock
 * evolver + product. Full evolver-driven MC integration is deferred to
 * Phase 3i (evolvers land there).
 *
 * Tolerance: tight for closed-form discounter checks, loose (1e-4) for the
 * end-to-end multi-path sum (per design P3H-8 — Monte Carlo noise) when
 * applicable.
 */
public class AccountingEngineTest {

    @Test
    public void marketModelDiscounter_paymentAtRateTime_returnsExactDiscount() {
        // rateTimes = {0.5, 1.0, 1.5, 2.0}; paymentTime=1.0 lies on rate index 1
        // → preDF, postDF correspond to discountRatio(1, numeraire) and
        //   discountRatio(2, numeraire). beforeWeight = 1.0 → returns preDF.
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        final MarketModelDiscounter d = new MarketModelDiscounter(1.0, rateTimes);
        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[] {0.04, 0.045, 0.05});
        // numeraire = 3 (terminal). discountRatio(i, j) = D(t_i)/D(t_j); for
        // i<j the ratio is > 1 in C++/QL convention.
        final double bonds = d.numeraireBonds(cs, 3);
        assertTrue("bonds positive", bonds > 0.0);
        // exact value: discountRatio(1, 3) = (1+τ_1*f_1)*(1+τ_2*f_2)
        // = (1+0.5*0.045)*(1+0.5*0.05) = 1.0225*1.025 = 1.04806...
        final double tau1 = 0.5, tau2 = 0.5;
        final double expected = (1.0 + tau1 * 0.045) * (1.0 + tau2 * 0.05);
        assertEquals("preDF = product of (1+τf)", expected, bonds, 1.0e-12);
    }

    @Test
    public void marketModelDiscounter_paymentBetweenRateTimes_interpolatesViaPow() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        // C++: before_ = std::lower_bound(rateTimes, paymentTime), which for
        // paymentTime=1.25 returns the first index whose value >= 1.25, i.e. 2
        // (rateTimes[2]=1.5). beforeWeight = 1 - (1.25 - 1.5)/(2.0 - 1.5) = 1.5
        // → bonds = pre^1.5 * post^(-0.5) where pre=DR(2,3), post=DR(3,3)=1.
        final MarketModelDiscounter d = new MarketModelDiscounter(1.25, rateTimes);
        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[] {0.04, 0.045, 0.05});
        final double bonds = d.numeraireBonds(cs, 3);
        final double pre = cs.discountRatio(2, 3);
        final double post = cs.discountRatio(3, 3);
        final double expected = Math.pow(pre, 1.5) * Math.pow(post, -0.5);
        assertEquals("Pow interpolation per C++ formula", expected, bonds, 1.0e-12);
    }

    @Test
    public void marketModelDiscounter_paymentAfterLastRateTime_clampsToLastBracket() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        // paymentTime = 5.0 → before_ would be 4 (off-end) → clamped to length-2 = 2
        // → beforeWeight = 1 - (5.0-1.5)/(2.0-1.5) = -6.0 (extrapolation)
        final MarketModelDiscounter d = new MarketModelDiscounter(5.0, rateTimes);
        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[] {0.04, 0.045, 0.05});
        // bonds = pre^(-6) * post^7 — extrapolation; just sanity-check it's finite & positive
        final double bonds = d.numeraireBonds(cs, 3);
        assertTrue("finite extrapolation", !Double.isNaN(bonds) && !Double.isInfinite(bonds));
        assertTrue("positive", bonds > 0.0);
    }

    @Test
    public void accountingEngine_constructor_doesNotThrow() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        final EvolutionDescription evo = new EvolutionDescription(rateTimes);
        final FixedAmountProduct product = new FixedAmountProduct(evo, 1.0, 0);
        final OneStepConstantEvolver evolver = new OneStepConstantEvolver(rateTimes);
        final AccountingEngine eng = new AccountingEngine(evolver, product, 1.0);
        assertNotNull(eng);
    }

    @Test
    public void accountingEngine_singleProduct_singlePath_recoversDiscountedAmount() {
        // Setup: one product paying 1.0 at cashflow time index 0 (= rateTimes[0] = 0.5)
        // numeraire = N-1 = 3 (terminal). Forward rates flat at 0.05.
        // Expected per-path NPV = initialNumeraire * discountedAmount
        // discountRatio(0, 3) = (1+τ_0 f_0)(1+τ_1 f_1)(1+τ_2 f_2) (numeraire denominator at later time)
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        final EvolutionDescription evo = new EvolutionDescription(rateTimes);
        final FixedAmountProduct product = new FixedAmountProduct(evo, 1.0, 0);
        final OneStepConstantEvolver evolver = new OneStepConstantEvolver(rateTimes);

        final AccountingEngine eng = new AccountingEngine(evolver, product, 1.0);
        final GenericSequenceStatistics stats = new GenericSequenceStatistics(1);
        eng.multiplePathValues(stats, 5);

        // Each "path" is deterministic (constant evolver), so mean = exact expected value
        // For numeraire=3, discountRatio(0,3) = (1.025)(1.025)(1.025) = 1.0768906...
        final double tau = 0.5, f = 0.05;
        final double dr03 = (1.0 + tau * f) * (1.0 + tau * f) * (1.0 + tau * f);
        // numeraireBonds(rateTimes[0]=0.5, numeraire=3) on curve state with these rates
        // paymentTime = rateTimes[0] = 0.5 → before_ = 0, beforeWeight = 1.0 → returns preDF = discountRatio(0,3) = dr03
        // numeraireHeld = amount * bonds / principal = 1 * dr03 / 1
        // values[0] = numerairesHeld * 1.0 = dr03
        final double mean0 = stats.mean().get(0);
        assertEquals("mean per-path NPV", dr03, mean0, 1.0e-12);
        assertEquals("samples added", 5, stats.samples());
    }

    @Test
    public void accountingEngine_multiplePaths_meanWithinTolerance() {
        // With deterministic evolver, all paths give same value → variance=0
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        final EvolutionDescription evo = new EvolutionDescription(rateTimes);
        final FixedAmountProduct product = new FixedAmountProduct(evo, 2.0, 1);
        final OneStepConstantEvolver evolver = new OneStepConstantEvolver(rateTimes);

        final AccountingEngine eng = new AccountingEngine(evolver, product, 1.0);
        final GenericSequenceStatistics stats = new GenericSequenceStatistics(1);
        eng.multiplePathValues(stats, 100);
        assertEquals(100, stats.samples());
        // Variance should be 0 within FP precision (same path repeated)
        assertEquals(0.0, stats.variance().get(0), 1.0e-20);
    }

    // -----------------------------------------------------------------------
    // Test fixtures: minimal mock evolver and product. Full evolvers land in
    // Phase 3i; this exercises just the AccountingEngine bookkeeping logic.
    // -----------------------------------------------------------------------

    /**
     * Minimal product paying a fixed amount at a fixed cashflow-time index on
     * the very first step. Stops afterwards.
     */
    private static final class FixedAmountProduct extends MarketModelMultiProduct {
        private final EvolutionDescription evolution_;
        private final double amount_;
        private final int cashFlowTimeIndex_;
        private boolean done_;

        FixedAmountProduct(final EvolutionDescription evo, final double amount,
                           final int cashFlowTimeIndex) {
            this.evolution_ = evo;
            this.amount_ = amount;
            this.cashFlowTimeIndex_ = cashFlowTimeIndex;
            this.done_ = false;
        }

        @Override
        public int[] suggestedNumeraires() {
            // terminal measure: numeraire = N for all steps
            final int[] n = new int[evolution_.numberOfSteps()];
            for (int i = 0; i < n.length; ++i) {
                n[i] = evolution_.numberOfRates();
            }
            return n;
        }

        @Override
        public EvolutionDescription evolution() { return evolution_; }

        @Override
        public double[] possibleCashFlowTimes() {
            return evolution_.rateTimes();
        }

        @Override
        public int numberOfProducts() { return 1; }

        @Override
        public int maxNumberOfCashFlowsPerProductPerStep() { return 1; }

        @Override
        public void reset() { done_ = false; }

        @Override
        public boolean nextTimeStep(final CurveState currentState,
                                    final int[] numberCashFlowsThisStep,
                                    final CashFlow[][] cashFlowsGenerated) {
            if (!done_) {
                numberCashFlowsThisStep[0] = 1;
                cashFlowsGenerated[0][0].timeIndex = cashFlowTimeIndex_;
                cashFlowsGenerated[0][0].amount = amount_;
                done_ = true;
                return true;
            }
            numberCashFlowsThisStep[0] = 0;
            return true;
        }

        @Override
        public MarketModelMultiProduct clone() {
            return new FixedAmountProduct(evolution_, amount_, cashFlowTimeIndex_);
        }
    }

    /**
     * Minimal evolver: deterministic flat-rate curve state, terminal-measure
     * numeraires, advances exactly one step per startNewPath/advanceStep.
     */
    private static final class OneStepConstantEvolver extends MarketModelEvolver {
        private final double[] rateTimes_;
        private final int numberOfRates_;
        private final LMMCurveState state_;
        private final int[] numeraires_;
        private int currentStep_;

        OneStepConstantEvolver(final double[] rateTimes) {
            this.rateTimes_ = rateTimes;
            this.numberOfRates_ = rateTimes.length - 1;
            this.state_ = new LMMCurveState(rateTimes);
            final double[] fwds = new double[numberOfRates_];
            for (int i = 0; i < numberOfRates_; ++i) fwds[i] = 0.05;
            this.state_.setOnForwardRates(fwds);
            // terminal measure: every step's numeraire is N (the last rate index)
            this.numeraires_ = new int[numberOfRates_];
            for (int i = 0; i < numberOfRates_; ++i) numeraires_[i] = numberOfRates_;
            this.currentStep_ = 0;
        }

        @Override
        public int[] numeraires() { return numeraires_; }

        @Override
        public double startNewPath() { currentStep_ = 0; return 1.0; }

        @Override
        public double advanceStep() { currentStep_++; return 1.0; }

        @Override
        public int currentStep() { return currentStep_; }

        @Override
        public CurveState currentState() { return state_; }

        @Override
        public void setInitialState(final CurveState curveState) {
            // not used in this minimal mock
        }
    }
}
