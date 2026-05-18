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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2008 Master IMAFA - Polytech'Nice Sophia - Universite de Nice Sophia Antipolis
*/

package org.jquantlib.pricingengines.asian;

import org.jquantlib.QL;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.instruments.DiscreteAveragingAsianOption;
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
 * Monte Carlo pricing engine for discrete arithmetic average-strike Asian options.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/asian/mc_discr_arith_av_strike.{hpp,cpp}}
 * {@code MCDiscreteArithmeticASEngine} (Phase 5e.5b-CFC-d-243).
 *
 * <p>Mirrors C++ behavior: {@code includeExerciseDate=true} so that an
 * exercise date past the last fixing extends the time grid by one point
 * (the exercise date), without that extra point participating in the
 * arithmetic average (Issue #646).
 *
 * @author JQuantLib
 */
public class MCDiscreteArithmeticASEngine extends MCDiscreteAveragingAsianEngineBase<Path> {

    public MCDiscreteArithmeticASEngine(final GeneralizedBlackScholesProcess process,
                                        final boolean brownianBridge,
                                        final boolean antitheticVariate,
                                        final int requiredSamples,
                                        final double requiredTolerance,
                                        final int maxSamples,
                                        final long seed) {
        super(process,
                brownianBridge,
                antitheticVariate,
                /* controlVariate */ false,
                requiredSamples,
                requiredTolerance,
                maxSamples,
                seed,
                /* timeSteps */ McSimulation.NULL_SAMPLES,
                /* timeStepsPerYear */ McSimulation.NULL_SAMPLES,
                /* includeExerciseDate */ true);
    }

    @Override
    protected PathPricer<Path> pathPricer() {
        final DiscreteAveragingAsianOption.ArgumentsImpl a =
                (DiscreteAveragingAsianOption.ArgumentsImpl) arguments_;
        final PlainVanillaPayoff payoff;
        try {
            payoff = (PlainVanillaPayoff) a.payoff;
        } catch (final ClassCastException e) {
            throw new RuntimeException("non-plain payoff given");
        }
        QL.require(payoff != null, "non-plain payoff given");
        QL.require(a.exercise instanceof EuropeanExercise, "wrong exercise given");

        final GeneralizedBlackScholesProcess process = (GeneralizedBlackScholesProcess) process_;

        // When the exercise date was added to the time grid (i.e., it
        // falls after the last fixing), tell the path pricer how many
        // grid points are fixings so it can exclude the exercise point
        // from the average.
        int fixingCount = ArithmeticASOPathPricer.NULL_FIXING_COUNT;
        if (includeExerciseDate_) {
            QL.require(timeSteps_ == McSimulation.NULL_SAMPLES
                    && timeStepsPerYear_ == McSimulation.NULL_SAMPLES,
                    "extra time steps are not supported when "
                            + "includeExerciseDate is enabled");
            final TimeGrid grid = timeGrid();
            final double lastFixing = process.time(
                    a.fixingDates.get(a.fixingDates.size() - 1));
            final double exerciseTime = process.time(a.exercise.lastDate());
            if (exerciseTime > lastFixing) {
                // exercise date was added to the grid; path has one
                // extra point at the end that is NOT a fixing
                fixingCount = grid.size() - 1;
            }
        }

        final double discount = process.riskFreeRate().currentLink()
                .discount(a.exercise.lastDate());
        return new ArithmeticASOPathPricer(
                payoff.optionType(),
                discount,
                a.runningAccumulator,
                a.pastFixings,
                fixingCount);
    }

    @Override
    protected MonteCarloModel.PathGeneratorAdapter<Path> pathGenerator() {
        final GeneralizedBlackScholesProcess process = (GeneralizedBlackScholesProcess) process_;
        final TimeGrid grid = timeGrid();
        final int dimensions = process.factors() * (grid.size() - 1);
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
                        InverseCumulativeNormal>>(process, grid, gsg, brownianBridge_);
        return new MonteCarloModel.PathGeneratorAdapterImpl(gen);
    }
}
