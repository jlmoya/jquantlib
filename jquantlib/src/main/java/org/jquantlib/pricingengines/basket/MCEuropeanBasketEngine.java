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
 Copyright (C) 2004 Neil Firth
 Copyright (C) 2007, 2008 StatPro Italia srl
*/

package org.jquantlib.pricingengines.basket;

import org.jquantlib.QL;
import org.jquantlib.instruments.BasketOption;
import org.jquantlib.instruments.BasketPayoff;
import org.jquantlib.instruments.MultiAssetOption;
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
 * Pricing engine for European basket options using Monte Carlo simulation.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/basket/mceuropeanbasketengine.{hpp,cpp}} (Phase 4i.5 WI-1). Specialised for
 * {@code RNG = PseudoRandom} (Mersenne-Twister + InverseCumulativeNormal) — quasi-random / low-discrepancy variants are
 * deferred to Phase 4i.5b.
 *
 * <p>The C++ template uses multiple inheritance ({@code BasketOption::engine} +
 * {@code McSimulation<MultiVariate,RNG,S>}). Java single-inheritance forces a choice; this port extends
 * {@link BasketOption.Engine} (so the GenericEngine arguments_/results_/Observable wiring is intact) and embeds a
 * delegate {@link McSimulation McSimulation&lt;MultiPath&gt;} via composition — same pattern as
 * {@link org.jquantlib.pricingengines.vanilla.MCVanillaEngine}.
 *
 * <p>Cross-validates against analytic engines (e.g. Stulz) and convergence
 * to known closed-form max/min basket prices as N→∞.
 *
 * @author JQuantLib
 */
public class MCEuropeanBasketEngine extends BasketOption.Engine {

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

    /**
     * Mirrors C++
     * {@code MCEuropeanBasketEngine(processes, timeSteps, timeStepsPerYear, brownianBridge, antitheticVariate,
     * requiredSamples, requiredTolerance, maxSamples, seed)}.
     *
     * <p>Pass {@link McSimulation#NULL_SAMPLES} (Integer.MAX_VALUE) /
     * {@link McSimulation#NULL_TOLERANCE} (NaN) for "not specified".
     */
    public MCEuropeanBasketEngine(final StochasticProcessArray processes, final int timeSteps,
            final int timeStepsPerYear, final boolean brownianBridge, final boolean antitheticVariate,
            final int requiredSamples, final double requiredTolerance, final int maxSamples, final long seed) {
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
     * Mirrors C++ {@code TimeGrid timeGrid()}: returns a uniform time grid whose terminal date matches the option's
     * last exercise date.
     */
    protected TimeGrid timeGrid() {
        final MultiAssetOption.ArgumentsImpl a = arguments_;
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

    /**
     * Builds a Gaussian-driven {@link MultiPathGenerator} for the underlying {@link StochasticProcessArray}. Mirrors
     * C++ {@code MCEuropeanBasketEngine::pathGenerator()} specialised to {@code RNG = PseudoRandom}.
     */
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

    /**
     * Builds a {@link EuropeanMultiPathPricer} from the basket payoff and risk-free discount at the exercise date.
     * Mirrors C++ {@code MCEuropeanBasketEngine::pathPricer()}.
     */
    protected PathPricer< MultiPath > pathPricer() {
        final MultiAssetOption.ArgumentsImpl a = arguments_;
        final BasketPayoff payoff;
        try {
            payoff = (BasketPayoff) a.payoff;
        } catch ( final ClassCastException e ) {
            throw new RuntimeException("non-basket payoff given");
        }
        QL.require(payoff != null, "non-basket payoff given");

        final StochasticProcess1D first = processes_.process(0);
        if ( !(first instanceof GeneralizedBlackScholesProcess) ) {
            throw new RuntimeException("Black-Scholes process required");
        }
        final GeneralizedBlackScholesProcess process = (GeneralizedBlackScholesProcess) first;
        final double discount = process.riskFreeRate().currentLink().discount(a.exercise.lastDate());
        return new EuropeanMultiPathPricer(payoff, discount);
    }

    //
    // PricingEngine
    //

    /**
     * Mirrors C++ {@code MCEuropeanBasketEngine::calculate()}: drives the embedded {@link McSimulation} with the
     * engine's stored tolerance / sample budget, then writes the mean (and error estimate) to the results.
     */
    @Override
    public void calculate() /* @ReadOnly */ {
        final MultiAssetOption.ResultsImpl r = results_;

        this.simulation_ = new McSimulation< MultiPath >(antitheticVariate_, /* controlVariate */ false) {
            @Override
            protected PathPricer< MultiPath > pathPricer() {
                return MCEuropeanBasketEngine.this.pathPricer();
            }

            @Override
            protected MonteCarloModel.PathGeneratorAdapter< MultiPath > pathGenerator() {
                return MCEuropeanBasketEngine.this.pathGenerator();
            }

            @Override
            protected TimeGrid timeGrid() {
                return MCEuropeanBasketEngine.this.timeGrid();
            }
        };
        this.simulation_.calculate(requiredTolerance_, requiredSamples_, maxSamples_);
        r.value = this.simulation_.sampleAccumulator().mean();
        r.errorEstimate = this.simulation_.errorEstimate();
    }
}
