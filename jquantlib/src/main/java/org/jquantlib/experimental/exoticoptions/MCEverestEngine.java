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

package org.jquantlib.experimental.exoticoptions;

import org.jquantlib.QL;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.methods.montecarlo.MonteCarloModel;
import org.jquantlib.methods.montecarlo.MultiPath;
import org.jquantlib.methods.montecarlo.MultiPathGenerator;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.model.shortrate.StochasticProcessArray;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.time.TimeGrid;

/**
 * Monte Carlo engine for Everest options.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/experimental/exoticoptions/mceverestengine.{hpp,cpp}} (Phase 4i.5 WI-4).
 *
 * <p>Specialised for {@code RNG = PseudoRandom}; quasi-random variants are
 * deferred to Phase 4i.5b. Pattern follows {@code MCEuropeanBasketEngine}.
 *
 * <p>Beyond the standard MC mean/error, the engine writes a {@code yield}
 * field to the results: {@code yield = NPV / (notional * endDiscount) - 1}.
 *
 * @author JQuantLib
 */
public class MCEverestEngine extends EverestOption.EngineImpl {

    //
    // protected fields
    //

    protected final StochasticProcessArray processes_;
    protected final int timeSteps_;
    protected final int timeStepsPerYear_;
    protected final int requiredSamples_;
    protected final int maxSamples_;
    protected final double requiredTolerance_;
    protected final boolean brownianBridge_;
    protected final boolean antitheticVariate_;
    protected final long seed_;

    /** Lazily-built delegate that owns the {@link MonteCarloModel}. */
    protected McSimulation< MultiPath > simulation_;

    //
    // constructors
    //

    public MCEverestEngine(final StochasticProcessArray processes, final int timeSteps, final int timeStepsPerYear,
            final boolean brownianBridge, final boolean antitheticVariate, final int requiredSamples,
            final double requiredTolerance, final int maxSamples, final long seed) {
        super();
        QL.require(timeSteps != McSimulation.NULL_SAMPLES || timeStepsPerYear != McSimulation.NULL_SAMPLES,
                "no time steps provided");
        QL.require(timeSteps == McSimulation.NULL_SAMPLES || timeStepsPerYear == McSimulation.NULL_SAMPLES,
                "both time steps and time steps per year were provided");
        QL.require(timeSteps != 0, "timeSteps must be positive, " + timeSteps + " not allowed");
        QL.require(timeStepsPerYear != 0, "timeStepsPerYear must be positive, " + timeStepsPerYear + " not allowed");

        this.processes_ = processes;
        this.timeSteps_ = timeSteps;
        this.timeStepsPerYear_ = timeStepsPerYear;
        this.brownianBridge_ = brownianBridge;
        this.antitheticVariate_ = antitheticVariate;
        this.requiredSamples_ = requiredSamples;
        this.maxSamples_ = maxSamples;
        this.requiredTolerance_ = requiredTolerance;
        this.seed_ = seed;
        this.processes_.addObserver(this);
    }

    //
    // McSimulation-shaped helpers
    //

    /**
     * Mirrors C++ {@code endDiscount()}: discount factor at the option's exercise date, taken from the first asset's
     * risk-free yield curve.
     */
    protected double endDiscount() {
        final EverestOption.ArgumentsImpl a = arguments_;
        final StochasticProcess1D first = processes_.process(0);
        if (!(first instanceof GeneralizedBlackScholesProcess process)) {
            throw new RuntimeException("Black-Scholes process required");
        }
        return process.riskFreeRate().currentLink().discount(a.exercise.lastDate());
    }

    protected TimeGrid timeGrid() {
        final EverestOption.ArgumentsImpl a = arguments_;
        final double residualTime = processes_.time(a.exercise.lastDate());
        if ( timeSteps_ != McSimulation.NULL_SAMPLES ) {
            return new TimeGrid(residualTime, timeSteps_);
        } else if ( timeStepsPerYear_ != McSimulation.NULL_SAMPLES ) {
            final int steps = (int) (timeStepsPerYear_ * residualTime);
            return new TimeGrid(residualTime, Math.max(steps, 1));
        } else {
            throw new RuntimeException("time steps not specified");
        }
    }

    protected MonteCarloModel.PathGeneratorAdapter< MultiPath > pathGenerator() {
        final int numAssets = processes_.size();
        final TimeGrid grid = timeGrid();
        final int dimensions = numAssets * (grid.size() - 1);
        final RandomSequenceGenerator< MersenneTwisterUniformRng > uniformRsg = new RandomSequenceGenerator< MersenneTwisterUniformRng >(
                MersenneTwisterUniformRng.class, dimensions, seed_);
        final InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > gsg = new InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal >(
                uniformRsg, new InverseCumulativeNormal());
        final MultiPathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > > gen = new MultiPathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > >(
                processes_, grid, gsg, brownianBridge_);
        return new MonteCarloModel.MultiPathGeneratorAdapterImpl(gen);
    }

    protected PathPricer< MultiPath > pathPricer() {
        final EverestOption.ArgumentsImpl a = arguments_;
        return new EverestMultiPathPricer(a.notional, a.guarantee, endDiscount());
    }

    //
    // PricingEngine
    //

    @Override
    public void calculate() /* @ReadOnly */ {
        final EverestOption.ResultsImpl r = results_;
        final EverestOption.ArgumentsImpl a = arguments_;

        this.simulation_ = new McSimulation< MultiPath >(antitheticVariate_, /* controlVariate */ false) {
            @Override
            protected PathPricer< MultiPath > pathPricer() {
                return MCEverestEngine.this.pathPricer();
            }

            @Override
            protected MonteCarloModel.PathGeneratorAdapter< MultiPath > pathGenerator() {
                return MCEverestEngine.this.pathGenerator();
            }

            @Override
            protected TimeGrid timeGrid() {
                return MCEverestEngine.this.timeGrid();
            }
        };
        this.simulation_.calculate(requiredTolerance_, requiredSamples_, maxSamples_);
        r.value = this.simulation_.sampleAccumulator().mean();
        r.errorEstimate = this.simulation_.errorEstimate();

        final double notional = a.notional;
        final double discount = endDiscount();
        r.yield = r.value / (notional * discount) - 1.0;
    }
}
