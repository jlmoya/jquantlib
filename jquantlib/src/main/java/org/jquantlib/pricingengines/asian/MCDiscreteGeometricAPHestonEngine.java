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
 Copyright (C) 2020 Jack Gillett
*/

package org.jquantlib.pricingengines.asian;

import org.jquantlib.QL;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.instruments.DiscreteAveragingAsianOption;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.methods.montecarlo.MonteCarloModel;
import org.jquantlib.methods.montecarlo.MultiPath;
import org.jquantlib.methods.montecarlo.MultiPathGenerator;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.time.TimeGrid;

/**
 * Heston Monte Carlo pricing engine for discrete geometric average-price Asian options.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/asian/mc_discr_geom_av_price_heston.{hpp,cpp}} {@code MCDiscreteGeometricAPHestonEngine} (Phase
 * 5e.5b-CFC-d-114).
 *
 * @author JQuantLib
 */
public class MCDiscreteGeometricAPHestonEngine extends MCDiscreteAveragingAsianEngineBase< MultiPath > {

    public MCDiscreteGeometricAPHestonEngine(final HestonProcess process, final boolean antitheticVariate,
            final int requiredSamples, final double requiredTolerance, final int maxSamples, final long seed,
            final int timeSteps, final int timeStepsPerYear) {
        super(process,
                /* brownianBridge */ false, antitheticVariate,
                /* controlVariate */ false, requiredSamples, requiredTolerance, maxSamples, seed, timeSteps,
                timeStepsPerYear,
                /* includeExerciseDate */ false);
        QL.require(timeSteps == McSimulation.NULL_SAMPLES || timeStepsPerYear == McSimulation.NULL_SAMPLES,
                "both time steps and time steps per year were provided");
    }

    @Override
    protected PathPricer< MultiPath > pathPricer() {
        final DiscreteAveragingAsianOption.ArgumentsImpl a = (DiscreteAveragingAsianOption.ArgumentsImpl) arguments_;
        final PlainVanillaPayoff payoff;
        try {
            payoff = (PlainVanillaPayoff) a.payoff;
        } catch ( final ClassCastException e ) {
            throw new RuntimeException("non-plain payoff given");
        }
        QL.require(payoff != null, "non-plain payoff given");
        QL.require(a.exercise instanceof EuropeanExercise, "wrong exercise given");

        final HestonProcess process = (HestonProcess) process_;

        final TimeGrid grid = timeGrid();
        final Array mandatory = grid.mandatoryTimes();
        final int[] fixingIndexes = new int[mandatory.size()];
        for ( int i = 0; i < mandatory.size(); i++ ) {
            fixingIndexes[i] = grid.closestIndex(mandatory.get(i));
        }

        final double discount = process.riskFreeRate().currentLink().discount(a.exercise.lastDate());
        return new GeometricAPOHestonPathPricer(payoff.optionType(), payoff.strike(), discount, fixingIndexes,
                a.runningAccumulator, a.pastFixings);
    }

    @Override
    protected MonteCarloModel.PathGeneratorAdapter< MultiPath > pathGenerator() {
        final TimeGrid grid = timeGrid();
        final int dimensions = process_.factors() * (grid.size() - 1);
        final RandomSequenceGenerator< MersenneTwisterUniformRng > uniformRsg = new RandomSequenceGenerator< MersenneTwisterUniformRng >(
                MersenneTwisterUniformRng.class, dimensions, seed_);
        final InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > gsg = new InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal >(
                uniformRsg, new InverseCumulativeNormal());
        final MultiPathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > > gen = new MultiPathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > >(
                process_, grid, gsg, brownianBridge_);
        return new MonteCarloModel.MultiPathGeneratorAdapterImpl(gen);
    }
}
