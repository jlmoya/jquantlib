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

package org.jquantlib.experimental.barrieroption;

import org.jquantlib.QL;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.methods.montecarlo.MonteCarloModel;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathGenerator;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.TimeGrid;

/**
 * Continuously-monitored double-barrier option pricing engine using Monte
 * Carlo simulation.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/experimental/barrieroption/mcdoublebarrierengine.hpp} (Phase
 * 5e.5b-CFC-d-278). Specialised for {@code MC = SingleVariate, RNG =
 * PseudoRandom} (Mersenne-Twister + InverseCumulativeNormal) and {@code S
 * = Statistics}; lifting that specialisation is a follow-up.
 *
 * <p>Cross-validated against
 * {@link org.jquantlib.pricingengines.barrier.AnalyticDoubleBarrierEngine}
 * (Ikeda/Kunitomo) on the C++ {@code testMonteCarloDoubleBarrierWithAnalytical}
 * suite.
 */
public final class MCDoubleBarrierEngine extends DoubleBarrierOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process_;
    private final int timeSteps_;
    private final int timeStepsPerYear_;
    private final boolean brownianBridge_;
    private final boolean antitheticVariate_;
    private final int requiredSamples_;
    private final int maxSamples_;
    private final double requiredTolerance_;
    private final long seed_;

    public MCDoubleBarrierEngine(
            final GeneralizedBlackScholesProcess process,
            final int timeSteps,
            final int timeStepsPerYear,
            final boolean brownianBridge,
            final boolean antitheticVariate,
            final int requiredSamples,
            final double requiredTolerance,
            final int maxSamples,
            final long seed) {
        super();
        QL.require(timeSteps != McSimulation.NULL_SAMPLES
                || timeStepsPerYear != McSimulation.NULL_SAMPLES,
                "no time steps provided");
        QL.require(timeSteps == McSimulation.NULL_SAMPLES
                || timeStepsPerYear == McSimulation.NULL_SAMPLES,
                "both time steps and time steps per year were provided");
        QL.require(timeSteps != 0,
                "timeSteps must be positive, " + timeSteps + " not allowed");
        QL.require(timeStepsPerYear != 0,
                "timeStepsPerYear must be positive, " + timeStepsPerYear + " not allowed");
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
        final double spot = process_.x0();
        QL.require(spot > 0.0, "negative or null underlying given");
        QL.require(!triggered(spot), "barrier touched");

        final DoubleBarrierOption.ArgumentsImpl a = args();
        QL.require(a.payoff instanceof PlainVanillaPayoff, "non-plain payoff given");
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) a.payoff;

        final TimeGrid grid = timeGrid();
        final double[] discounts = new double[grid.size()];
        for (int i = 0; i < grid.size(); i++) {
            discounts[i] = process_.riskFreeRate().currentLink().discount(grid.get(i));
        }

        final PathPricer<Path> pp = new DoubleBarrierPathPricer(
                a.barrierType,
                a.barrier_lo,
                a.barrier_hi,
                a.rebate,
                payoff.optionType(),
                payoff.strike(),
                discounts);
        final MonteCarloModel.PathGeneratorAdapter<Path> pg = pathGenerator(grid);

        final McSimulation<Path> simulation = new McSimulation<Path>(antitheticVariate_, false) {
            @Override protected PathPricer<Path> pathPricer() { return pp; }
            @Override protected MonteCarloModel.PathGeneratorAdapter<Path> pathGenerator() { return pg; }
            @Override protected TimeGrid timeGrid() { return grid; }
        };
        simulation.calculate(requiredTolerance_, requiredSamples_, maxSamples_);

        final OneAssetOption.ResultsImpl r = (OneAssetOption.ResultsImpl) results_;
        r.value = simulation.sampleAccumulator().mean();
        r.errorEstimate = simulation.errorEstimate();
    }

    private TimeGrid timeGrid() {
        final double residualTime = process_.time(args().exercise.lastDate());
        if (timeSteps_ != McSimulation.NULL_SAMPLES) {
            return new TimeGrid(residualTime, timeSteps_);
        } else if (timeStepsPerYear_ != McSimulation.NULL_SAMPLES) {
            final int steps = (int) (timeStepsPerYear_ * residualTime);
            return new TimeGrid(residualTime, Math.max(steps, 1));
        } else {
            throw new RuntimeException("time steps not specified");
        }
    }

    private MonteCarloModel.PathGeneratorAdapter<Path> pathGenerator(final TimeGrid grid) {
        final int dimensions = process_.factors() * (grid.size() - 1);
        final RandomSequenceGenerator<MersenneTwisterUniformRng> uniformRsg =
                new RandomSequenceGenerator<MersenneTwisterUniformRng>(
                        MersenneTwisterUniformRng.class, dimensions, seed_);
        final InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                InverseCumulativeNormal> gsg =
                new InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                        InverseCumulativeNormal>(uniformRsg, new InverseCumulativeNormal());
        final PathGenerator<InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                InverseCumulativeNormal>> gen =
                new PathGenerator<InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                        InverseCumulativeNormal>>(process_, grid, gsg, brownianBridge_);
        return new MonteCarloModel.PathGeneratorAdapterImpl(gen);
    }
}
