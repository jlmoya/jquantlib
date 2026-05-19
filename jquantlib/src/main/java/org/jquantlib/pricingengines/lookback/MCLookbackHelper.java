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
import org.jquantlib.exercise.Exercise;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.methods.montecarlo.MonteCarloModel;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathGenerator;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.TimeGrid;

/**
 * Shared Monte-Carlo plumbing used by the four {@code MCLookback*Engine} variants. Mirrors the bits of C++
 * {@code MCLookbackEngine} that are independent of the instrument-specific arguments / path-pricer choice.
 *
 * <p>Specialised to {@code MC = SingleVariate, RNG = PseudoRandom}
 * (Mersenne-Twister + InverseCumulativeNormal). Lifting that restriction is a follow-up.
 */
final class MCLookbackHelper {

    private MCLookbackHelper() { /* static-only */ }

    /**
     * Mirrors C++ {@code MCLookbackEngine::timeGrid()}: uniform grid from {@code 0} to the option's residual time, with
     * either {@code timeSteps} steps or {@code timeStepsPerYear * residualTime} steps.
     */
    static TimeGrid timeGrid(final GeneralizedBlackScholesProcess process, final Exercise exercise, final int timeSteps,
            final int timeStepsPerYear) {
        final double residualTime = process.time(exercise.lastDate());
        if ( timeSteps != McSimulation.NULL_SAMPLES ) {
            return new TimeGrid(residualTime, timeSteps);
        } else if ( timeStepsPerYear != McSimulation.NULL_SAMPLES ) {
            final int steps = (int) (timeStepsPerYear * residualTime);
            return new TimeGrid(residualTime, Math.max(steps, 1));
        } else {
            throw new RuntimeException("time steps not specified");
        }
    }

    /**
     * Builds the PseudoRandom path generator adapter for the given grid. Mirrors C++
     * {@code MCLookbackEngine::pathGenerator()} specialised to {@code RNG = PseudoRandom}.
     */
    static MonteCarloModel.PathGeneratorAdapter< Path > pathGenerator(final GeneralizedBlackScholesProcess process,
            final TimeGrid grid, final boolean brownianBridge, final long seed) {
        final int dimensions = process.factors() * (grid.size() - 1);
        final RandomSequenceGenerator< MersenneTwisterUniformRng > uniformRsg = new RandomSequenceGenerator< MersenneTwisterUniformRng >(
                MersenneTwisterUniformRng.class, dimensions, seed);
        final InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > gsg = new InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal >(
                uniformRsg, new InverseCumulativeNormal());
        final PathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > > gen = new PathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > >(
                process, grid, gsg, brownianBridge);
        return new MonteCarloModel.PathGeneratorAdapterImpl(gen);
    }

    /**
     * Validates the (timeSteps | timeStepsPerYear) bookkeeping. Mirrors C++ {@code MCLookbackEngine} constructor
     * preconditions.
     */
    static void validateTimeStepArgs(final int timeSteps, final int timeStepsPerYear) {
        QL.require(timeSteps != McSimulation.NULL_SAMPLES || timeStepsPerYear != McSimulation.NULL_SAMPLES,
                "no time steps provided");
        QL.require(timeSteps == McSimulation.NULL_SAMPLES || timeStepsPerYear == McSimulation.NULL_SAMPLES,
                "both time steps and time steps per year were provided");
        QL.require(timeSteps != 0, "timeSteps must be positive, " + timeSteps + " not allowed");
        QL.require(timeStepsPerYear != 0, "timeStepsPerYear must be positive, " + timeStepsPerYear + " not allowed");
    }
}
