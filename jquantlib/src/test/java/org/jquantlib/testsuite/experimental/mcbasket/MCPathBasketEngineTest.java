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
 */
package org.jquantlib.testsuite.experimental.mcbasket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.mcbasket.MCPathBasketEngine;
import org.jquantlib.experimental.mcbasket.PathMultiAssetOption;
import org.jquantlib.experimental.mcbasket.PathPayoff;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.shortrate.StochasticProcessArray;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.util.PolymorphicVisitor;
import org.junit.Test;

/**
 * Cross-validation test for {@link MCPathBasketEngine} (Phase 4i.5 / P3-B).
 *
 * <p>Exercises {@code MCPathBasketEngine.calculate()} and the underlying
 * {@code EuropeanPathMultiPathPricer.op()} end-to-end. A single-asset basket
 * whose {@link PathPayoff} is the vanilla European call payoff at expiry must
 * reproduce the Black-Scholes analytic call value (within MC noise) and
 * exercise both wired code paths (multi-path generation, dot-product against
 * the discount factor vector).
 *
 * <p>Tier: LOOSE — Monte Carlo convergence is O(1/√N). With N=20000 samples
 * and antithetic on, the standard error is ~1% of the price; we use a 5%
 * relative tolerance.
 */
public class MCPathBasketEngineTest {

    public MCPathBasketEngineTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Simple test-only {@link PathPayoff} that returns a vanilla call payoff
     * on the first asset at the LAST fixing date, and zero elsewhere.
     * <ul>
     *   <li>{@code payments[i]} = 0 for i &lt; last, max(S0(last) - K, 0)
     *       at last.</li>
     *   <li>{@code exercises[i]} = 0 (no early exercise).</li>
     *   <li>{@code states[i]} = empty array (no exercise opportunity).</li>
     * </ul>
     * Reproduces a European vanilla call exactly when N=1 asset.
     */
    private static class VanillaAtExpiryPathPayoff extends PathPayoff {

        private final double strike_;

        VanillaAtExpiryPathPayoff(final double strike) {
            this.strike_ = strike;
        }

        @Override
        public String name() {
            return "VanillaAtExpiry";
        }

        @Override
        public String description() {
            return "Vanilla European call on first asset at last fixing date";
        }

        @Override
        public int basisSystemDimension() {
            // dim is only consulted by the LSM pricer for early-exercise; we
            // return 1 to satisfy LsmBasisSystem.multiPathBasisSystem's
            // dim > 0 requirement should anyone construct a regression basis
            // against this payoff.
            return 1;
        }

        @Override
        public void value(final Matrix path,
                final List<Handle<YieldTermStructure>> forwardTermStructures, final Array payments,
                final Array exercises, final List<Array> states) {
            final int numberOfTimes = path.columns();
            // payments[i] = 0 for i < last; payments[last] = max(S0(last) - K, 0)
            for (int i = 0; i < numberOfTimes - 1; i++) {
                payments.set(i, 0.0);
            }
            payments.set(numberOfTimes - 1, Math.max(path.get(0, numberOfTimes - 1) - strike_, 0.0));
        }

        @Override
        public void accept(final PolymorphicVisitor pv) {
            // unused in this test — no visitor dispatch
        }
    }

    /** Simple test-only {@link PathMultiAssetOption} backed by a single fixing date and a vanilla payoff. */
    private static class TestPathOption extends PathMultiAssetOption {

        private final PathPayoff payoff_;
        private final List<Date> fixingDates_;

        TestPathOption(final PathPayoff payoff, final List<Date> fixingDates) {
            super();
            this.payoff_ = payoff;
            this.fixingDates_ = fixingDates;
        }

        @Override
        public PathPayoff pathPayoff() {
            return payoff_;
        }

        @Override
        public List<Date> fixingDates() {
            return fixingDates_;
        }
    }

    private static GeneralizedBlackScholesProcess makeBsm(final Date today, final double S, final double r,
            final double q, final double vol, final DayCounter dc, final Calendar cal) {
        final Handle<? extends Quote> spot = new Handle<Quote>(new SimpleQuote(S));
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(new FlatForward(today, r, dc));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(new FlatForward(today, q, dc));
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, cal, vol, dc));
        return new GeneralizedBlackScholesProcess(spot, qTS, rTS, volTS);
    }

    /**
     * Single-asset basket via {@link MCPathBasketEngine}: payoff is a vanilla
     * European call at the last (and only) fixing date. Cross-checked against
     * the Black-Scholes analytic value.
     *
     * <p>S=100, K=100, r=5%, q=2%, σ=20%, T=1y → analytic call ≈ 9.227.
     */
    @Test
    public void testSingleAssetVanillaReproducesBlackScholes() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final GeneralizedBlackScholesProcess process = makeBsm(today, 100.0, 0.05, 0.02, 0.20, dc, cal);

        // single-asset "array" with 1x1 correlation = [[1]]
        final List<StochasticProcess1D> processes = new ArrayList<>();
        processes.add(process);
        final Matrix corr = new Matrix(new double[][] { { 1.0 } });
        final StochasticProcessArray array = new StochasticProcessArray(processes, corr);

        // single fixing date at T=1y
        final Date exDate = today.add(365);
        final List<Date> fixingDates = new ArrayList<>();
        fixingDates.add(exDate);

        final PathPayoff payoff = new VanillaAtExpiryPathPayoff(100.0);
        final TestPathOption option = new TestPathOption(payoff, fixingDates);

        final MCPathBasketEngine engine = new MCPathBasketEngine(array,
                /* timeSteps */ 1, /* timeStepsPerYear */ Constants.NULL_INTEGER,
                /* brownianBridge */ false, /* antithetic */ true, /* controlVariate */ false,
                /* requiredSamples */ 20000, McSimulation.NULL_TOLERANCE,
                /* maxSamples */ McSimulation.NULL_SAMPLES, /* seed */ 42L);
        option.setPricingEngine(engine);

        final double npv = option.NPV();

        // ATM vanilla call ~9.227; LOOSE tier (5% relative) absorbs MC noise.
        assertEquals("single-asset basket vs Black-Scholes call", 9.227, npv, 0.05 * 9.227);
        assertTrue("error estimate must be positive", option.errorEstimate() > 0.0);
    }
}
