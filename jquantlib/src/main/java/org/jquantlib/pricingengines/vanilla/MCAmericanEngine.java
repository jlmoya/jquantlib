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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2006 Klaus Spanderen
 Copyright (C) 2007 StatPro Italia srl
 Copyright (C) 2016 Peter Caspers
 Copyright (C) 2022 Jonghee Lee
*/

package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.exercise.EarlyExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.methods.montecarlo.LongstaffSchwartzPathPricer;
import org.jquantlib.methods.montecarlo.LsmBasisSystem;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.pricingengines.MCLongstaffSchwartzEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * Monte Carlo American option pricing engine.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/vanilla/mcamericanengine.{hpp,cpp}}
 * (Phase 5h.5-MC-AME WI-5). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Combines the {@link AmericanPathPricer} (single-path early-exercise
 * pricer) and the {@link LongstaffSchwartzPathPricer} (LSM regression)
 * inside the {@link MCLongstaffSchwartzEngine} template:
 *
 * <ul>
 *   <li>each calibration path is rolled back step by step; at each
 *       interior step the in-the-money sub-population is regressed
 *       against the polynomial basis (+ payoff) and the coefficients
 *       are stored;</li>
 *   <li>each pricing path is rolled back the same way, taking the
 *       max(continuationValue, exerciseValue) at every step.</li>
 * </ul>
 *
 * <p>Specialised to {@code RNG = PseudoRandom} (Mersenne-Twister); the
 * quasi-random and antithetic-quasi variants are deferred to
 * Phase 5h.5-MC-AME-b.
 *
 * @author JQuantLib
 */
public class MCAmericanEngine extends MCLongstaffSchwartzEngine {

    private final int polynomialOrder_;
    private final LsmBasisSystem.PolynomialType polynomialType_;


    public MCAmericanEngine(
            final GeneralizedBlackScholesProcess process,
            final int timeSteps,
            final int timeStepsPerYear,
            final boolean antitheticVariate,
            final boolean controlVariate,
            final int requiredSamples,
            final double requiredTolerance,
            final int maxSamples,
            final long seed,
            final int polynomialOrder,
            final LsmBasisSystem.PolynomialType polynomialType,
            final int nCalibrationSamples,
            final Boolean antitheticVariateCalibration,
            final long seedCalibration) {
        super(process, timeSteps, timeStepsPerYear,
                /* brownianBridge */ false,
                antitheticVariate, controlVariate,
                requiredSamples, requiredTolerance, maxSamples, seed,
                nCalibrationSamples,
                /* brownianBridgeCalibration */ Boolean.FALSE,
                antitheticVariateCalibration, seedCalibration);
        this.polynomialOrder_ = polynomialOrder;
        this.polynomialType_ = polynomialType;
    }


    /**
     * Mirrors C++ {@code MCAmericanEngine::calculate()}: clamps the
     * control-variate output to be non-negative.
     */
    @Override
    public void calculate() /* @ReadOnly */ {
        super.calculate();
        if (controlVariate_) {
            final OneAssetOption.ResultsImpl r = (OneAssetOption.ResultsImpl) results_;
            // control variate may yield small negative values for deep OTM.
            r.value = Math.max(0.0, r.value);
        }
    }

    @Override
    protected LongstaffSchwartzPathPricer<Path, Double> lsmPathPricer() {
        final OneAssetOption.ArgumentsImpl args = (OneAssetOption.ArgumentsImpl) arguments_;
        final Exercise exercise = args.exercise;
        QL.require(exercise instanceof EarlyExercise, "wrong exercise given");
        QL.require(!((EarlyExercise) exercise).payoffAtExpiry(),
                "payoff at expiry not handled");

        final AmericanPathPricer earlyExercisePathPricer = new AmericanPathPricer(
                args.payoff, polynomialOrder_, polynomialType_);

        return new LongstaffSchwartzPathPricer<Path, Double>(
                this.timeGrid(),
                earlyExercisePathPricer,
                process_.riskFreeRate().currentLink());
    }

    /**
     * Mirrors C++ {@code controlPathPricer()}: returns a European path
     * pricer over the same payoff/strike for variance reduction.
     */
    @Override
    protected PathPricer<Path> controlPathPricer() {
        final OneAssetOption.ArgumentsImpl args = (OneAssetOption.ArgumentsImpl) arguments_;
        final StrikedTypePayoff payoff;
        try {
            payoff = (StrikedTypePayoff) args.payoff;
        } catch (final ClassCastException e) {
            throw new RuntimeException("StrikedTypePayoff needed for control variate");
        }
        QL.require(payoff != null, "StrikedTypePayoff needed for control variate");

        final double discount = process_.riskFreeRate().currentLink()
                .discount(timeGrid().back());
        return new EuropeanPathPricer(payoff.optionType(), payoff.strike(), discount);
    }

    /**
     * Mirrors C++ {@code controlVariateValue()}: calls the analytic
     * European engine over the same payoff/process and returns its NPV.
     *
     * <p>The standalone variance-reduction control-variate value is the
     * European NPV computed by closing-form Black-Scholes (mirrors C++
     * {@code AnalyticEuropeanEngine}). Computed lazily on demand.
     */
    @Override
    protected double controlVariateValue() {
        final OneAssetOption.ArgumentsImpl args = (OneAssetOption.ArgumentsImpl) arguments_;
        final StrikedTypePayoff payoff;
        try {
            payoff = (StrikedTypePayoff) args.payoff;
        } catch (final ClassCastException e) {
            throw new RuntimeException("StrikedTypePayoff needed for control variate");
        }
        if (!(payoff instanceof PlainVanillaPayoff)) {
            // AnalyticEuropeanEngine accepts only PlainVanilla payoffs in this port;
            // fall back to NaN, the McSimulation will raise.
            return Double.NaN;
        }
        // Build a one-shot European option valued via the analytic engine.
        final org.jquantlib.exercise.EuropeanExercise euExercise =
                new org.jquantlib.exercise.EuropeanExercise(args.exercise.lastDate());
        final org.jquantlib.instruments.VanillaOption euOpt =
                new org.jquantlib.instruments.VanillaOption(payoff, euExercise);
        final org.jquantlib.pricingengines.AnalyticEuropeanEngine engine =
                new org.jquantlib.pricingengines.AnalyticEuropeanEngine(process_);
        euOpt.setPricingEngine(engine);
        return euOpt.NPV();
    }
}
