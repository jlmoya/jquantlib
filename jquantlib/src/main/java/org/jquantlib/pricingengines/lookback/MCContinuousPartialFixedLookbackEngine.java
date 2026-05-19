/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2020 Lew Wei Hao

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.pricingengines.lookback;

import org.jquantlib.QL;
import org.jquantlib.instruments.ContinuousPartialFixedLookbackOption;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.methods.montecarlo.MonteCarloModel;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.TimeGrid;

/**
 * Continuous partial-time fixed-strike lookback option pricing engine using Monte Carlo simulation.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/lookback/mclookbackengine.hpp} instantiated with {@code I = ContinuousPartialFixedLookbackOption}
 * (Phase 5e.5b-CFC-d-183). Specialised for {@code RNG = PseudoRandom}.
 *
 * <p>Cross-validated against
 * {@link AnalyticContinuousPartialFixedLookbackEngine}.
 */
public final class MCContinuousPartialFixedLookbackEngine extends ContinuousPartialFixedLookbackOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process_;
    private final int timeSteps_;
    private final int timeStepsPerYear_;
    private final boolean brownianBridge_;
    private final boolean antitheticVariate_;
    private final int requiredSamples_;
    private final int maxSamples_;
    private final double requiredTolerance_;
    private final long seed_;

    public MCContinuousPartialFixedLookbackEngine(final GeneralizedBlackScholesProcess process, final int timeSteps,
            final int timeStepsPerYear, final boolean brownianBridge, final boolean antitheticVariate,
            final int requiredSamples, final double requiredTolerance, final int maxSamples, final long seed) {
        super();
        MCLookbackHelper.validateTimeStepArgs(timeSteps, timeStepsPerYear);
        this.process_ = process;
        this.timeSteps_ = timeSteps;
        this.timeStepsPerYear_ = timeStepsPerYear;
        this.brownianBridge_ = brownianBridge;
        this.antitheticVariate_ = antitheticVariate;
        this.requiredSamples_ = requiredSamples;
        this.maxSamples_ = maxSamples;
        this.requiredTolerance_ = requiredTolerance;
        this.seed_ = seed;
        this.process_.addObserver(this);
    }

    @Override
    public void calculate() {
        QL.require(process_.x0() > 0.0, "negative or null underlying given");
        final ContinuousPartialFixedLookbackOption.ArgumentsImpl a = arguments_;
        QL.require(a.payoff instanceof PlainVanillaPayoff, "non-plain payoff given");
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) a.payoff;
        final TimeGrid grid = MCLookbackHelper.timeGrid(process_, a.exercise, timeSteps_, timeStepsPerYear_);
        final double discount = process_.riskFreeRate().currentLink().discount(grid.back());
        final double lookbackStart = process_.time(a.lookbackPeriodStart);

        final PathPricer< Path > pp = new LookbackPathPricers.PartialFixed(lookbackStart, payoff.optionType(),
                payoff.strike(), discount);
        final MonteCarloModel.PathGeneratorAdapter< Path > pg = MCLookbackHelper.pathGenerator(process_, grid,
                brownianBridge_, seed_);

        final McSimulation< Path > simulation = new McSimulation< Path >(antitheticVariate_, false) {
            @Override
            protected PathPricer< Path > pathPricer() {
                return pp;
            }

            @Override
            protected MonteCarloModel.PathGeneratorAdapter< Path > pathGenerator() {
                return pg;
            }

            @Override
            protected TimeGrid timeGrid() {
                return grid;
            }
        };
        simulation.calculate(requiredTolerance_, requiredSamples_, maxSamples_);
        results_.value = simulation.sampleAccumulator().mean();
        results_.errorEstimate = simulation.errorEstimate();
    }
}
