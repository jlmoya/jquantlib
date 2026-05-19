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
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2003, 2004 Ferdinando Ametrano
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
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.TimeGrid;

/**
 * Monte Carlo pricing engine for discrete arithmetic average-price Asian options.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/asian/mc_discr_arith_av_price.{hpp,cpp}} {@code MCDiscreteArithmeticAPEngine} (Phase
 * 5e.5b-CFC-d-114).
 *
 * @author JQuantLib
 */
public class MCDiscreteArithmeticAPEngine extends MCDiscreteAveragingAsianEngineBase< Path > {

    public MCDiscreteArithmeticAPEngine(final GeneralizedBlackScholesProcess process, final boolean brownianBridge,
            final boolean antitheticVariate, final boolean controlVariate, final int requiredSamples,
            final double requiredTolerance, final int maxSamples, final long seed) {
        super(process, brownianBridge, antitheticVariate, controlVariate, requiredSamples, requiredTolerance,
                maxSamples, seed,
                /* timeSteps */ McSimulation.NULL_SAMPLES,
                /* timeStepsPerYear */ McSimulation.NULL_SAMPLES,
                /* includeExerciseDate */ false);
    }

    @Override
    protected PathPricer< Path > pathPricer() {
        final DiscreteAveragingAsianOption.ArgumentsImpl a = (DiscreteAveragingAsianOption.ArgumentsImpl) arguments_;
        final PlainVanillaPayoff payoff;
        try {
            payoff = (PlainVanillaPayoff) a.payoff;
        } catch ( final ClassCastException e ) {
            throw new RuntimeException("non-plain payoff given");
        }
        QL.require(payoff != null, "non-plain payoff given");
        QL.require(a.exercise instanceof EuropeanExercise, "wrong exercise given");

        final GeneralizedBlackScholesProcess process = (GeneralizedBlackScholesProcess) process_;
        final double discount = process.riskFreeRate().currentLink().discount(a.exercise.lastDate());
        return new ArithmeticAPOPathPricer(payoff.optionType(), payoff.strike(), discount, a.runningAccumulator,
                a.pastFixings);
    }

    @Override
    protected PathPricer< Path > controlPathPricer() {
        final DiscreteAveragingAsianOption.ArgumentsImpl a = (DiscreteAveragingAsianOption.ArgumentsImpl) arguments_;
        final PlainVanillaPayoff payoff;
        try {
            payoff = (PlainVanillaPayoff) a.payoff;
        } catch ( final ClassCastException e ) {
            throw new RuntimeException("non-plain payoff given");
        }
        QL.require(payoff != null, "non-plain payoff given");
        QL.require(a.exercise instanceof EuropeanExercise, "wrong exercise given");

        final GeneralizedBlackScholesProcess process = (GeneralizedBlackScholesProcess) process_;
        return new GeometricAPOPathPricer(payoff.optionType(), payoff.strike(),
                process.riskFreeRate().currentLink().discount(timeGrid().back()));
    }

    @Override
    protected PricingEngine controlPricingEngine() {
        final GeneralizedBlackScholesProcess process = (GeneralizedBlackScholesProcess) process_;
        return new AnalyticDiscreteGeometricAveragePriceAsianEngine(process);
    }

    @Override
    protected MonteCarloModel.PathGeneratorAdapter< Path > pathGenerator() {
        final GeneralizedBlackScholesProcess process = (GeneralizedBlackScholesProcess) process_;
        final TimeGrid grid = timeGrid();
        final int dimensions = process.factors() * (grid.size() - 1);
        final RandomSequenceGenerator< MersenneTwisterUniformRng > uniformRsg = new RandomSequenceGenerator< MersenneTwisterUniformRng >(
                MersenneTwisterUniformRng.class, dimensions, seed_);
        final InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > gsg = new InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal >(
                uniformRsg, new InverseCumulativeNormal());
        final PathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > > gen = new PathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > >(
                process, grid, gsg, brownianBridge_);
        return new MonteCarloModel.PathGeneratorAdapterImpl(gen);
    }
}
