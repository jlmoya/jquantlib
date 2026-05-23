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
 Copyright (C) 2008 Andrea Odetti

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.mcbasket;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.matrixutilities.Array;
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
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.ImpliedTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Monte Carlo engine for path-dependent basket options.
 *
 * <p>Java port of C++ QuantLib v1.42.1
 * {@code ql/experimental/mcbasket/mcpathbasketengine.{hpp,cpp}}:: {@code MCPathBasketEngine}. Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The C++ engine is templated on
 * {@code <RNG = PseudoRandom, S = Statistics>} and inherits from {@code McSimulation<MultiVariate,RNG,S>}. The Java
 * port collapses the {@code RNG} template parameter (specialised to {@code PseudoRandom} —
 * MersenneTwister + InverseCumulativeNormal) and embeds an {@link McSimulation McSimulation&lt;MultiPath&gt;}
 * delegate via composition, mirroring the precedent set by
 * {@link org.jquantlib.experimental.exoticoptions.MCHimalayaEngine} and
 * {@link org.jquantlib.pricingengines.basket.MCAmericanBasketEngine}.
 */
public class MCPathBasketEngine extends PathMultiAssetOption.EngineImpl {

    protected final StochasticProcessArray process_;
    protected final int timeSteps_;
    protected final int timeStepsPerYear_;
    protected final int requiredSamples_;
    protected final int maxSamples_;
    protected final double requiredTolerance_;
    protected final boolean brownianBridge_;
    protected final boolean antitheticVariate_;
    protected final boolean controlVariate_;
    protected final long seed_;

    /** Lazily-built delegate that owns the {@link MonteCarloModel}. */
    protected McSimulation< MultiPath > simulation_;

    public MCPathBasketEngine(final StochasticProcessArray process, final int timeSteps, final int timeStepsPerYear,
            final boolean brownianBridge, final boolean antitheticVariate, final boolean controlVariate,
            final int requiredSamples, final double requiredTolerance, final int maxSamples, final long seed) {
        super();
        this.process_ = process;
        this.timeSteps_ = timeSteps;
        this.timeStepsPerYear_ = timeStepsPerYear;
        this.requiredSamples_ = requiredSamples;
        this.maxSamples_ = maxSamples;
        this.requiredTolerance_ = requiredTolerance;
        this.brownianBridge_ = brownianBridge;
        this.antitheticVariate_ = antitheticVariate;
        this.controlVariate_ = controlVariate;
        this.seed_ = seed;

        // Mirrors C++ constructor preconditions
        QL.require(timeSteps != Constants.NULL_INTEGER || timeStepsPerYear != Constants.NULL_INTEGER,
                "no time steps provided");
        QL.require(timeSteps == Constants.NULL_INTEGER || timeStepsPerYear == Constants.NULL_INTEGER,
                "both time steps and time steps per year were provided");
        QL.require(timeSteps != 0, "timeSteps must be positive, " + timeSteps + " not allowed");
        QL.require(timeStepsPerYear != 0, "timeStepsPerYear must be positive, " + timeStepsPerYear + " not allowed");

        // C++ registers the engine with the process; the Java Observable
        // mechanism is consistent.
        if ( process_ != null ) {
            process_.addObserver(this);
        }
    }

    //
    // McSimulation-shaped helpers
    //

    /** Mirrors C++ {@code TimeGrid timeGrid()} lines 161-175 of {@code mcpathbasketengine.hpp}. */
    protected TimeGrid timeGrid() {
        final List< Date > fixings = arguments_.fixingDates;
        final int numberOfFixings = fixings.size();
        final List< Double > fixingTimes = new ArrayList<>(numberOfFixings);
        for ( int i = 0; i < numberOfFixings; i++ ) {
            fixingTimes.add(process_.time(fixings.get(i)));
        }
        final int numberOfTimeSteps = (timeSteps_ != Constants.NULL_INTEGER)
                ? timeSteps_
                : (int) (timeStepsPerYear_ * fixingTimes.get(fixingTimes.size() - 1));
        return new TimeGrid(fixingTimes, numberOfTimeSteps);
    }

    /** Build a Gaussian-driven {@link MultiPathGenerator} for the underlying processes. */
    protected MonteCarloModel.PathGeneratorAdapter< MultiPath > pathGenerator() {
        final PathPayoff payoff = arguments_.payoff;
        QL.require(payoff != null, "non-basket payoff given");

        final int numAssets = process_.size();
        final TimeGrid grid = timeGrid();
        final int dimensions = numAssets * (grid.size() - 1);
        final RandomSequenceGenerator< MersenneTwisterUniformRng > uniformRsg =
                new RandomSequenceGenerator< MersenneTwisterUniformRng >(MersenneTwisterUniformRng.class, dimensions,
                        seed_);
        final InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > gsg =
                new InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal >(
                        uniformRsg, new InverseCumulativeNormal());
        final MultiPathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > > gen =
                new MultiPathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > >(
                        process_, grid, gsg, brownianBridge_);
        return new MonteCarloModel.MultiPathGeneratorAdapterImpl(gen);
    }

    /** Mirrors C++ {@code pathPricer()} lines 177-218 of {@code mcpathbasketengine.hpp}. */
    protected PathPricer< MultiPath > pathPricer() {
        final PathPayoff payoff = arguments_.payoff;
        QL.require(payoff != null, "non-basket payoff given");

        final StochasticProcess1D first = process_.process(0);
        if (!(first instanceof GeneralizedBlackScholesProcess process)) {
            throw new RuntimeException("Black-Scholes process required");
        }

        final TimeGrid theTimeGrid = timeGrid();
        final Array times = theTimeGrid.mandatoryTimes();
        final int numberOfTimes = times.size();
        final List< Date > fixings = arguments_.fixingDates;
        QL.require(fixings.size() == numberOfTimes, "Invalid dates/times");

        final int[] timePositions = new int[numberOfTimes];
        final double[] discountFactorsArr = new double[numberOfTimes];
        final List< Handle< YieldTermStructure > > forwardTermStructures = new ArrayList<>(numberOfTimes);

        final Handle< YieldTermStructure > riskFreeRate = process.riskFreeRate();
        for ( int i = 0; i < numberOfTimes; i++ ) {
            final double t = times.get(i);
            timePositions[i] = theTimeGrid.index(t);
            discountFactorsArr[i] = riskFreeRate.currentLink().discount(t);
            forwardTermStructures.add(new Handle< YieldTermStructure >(
                    new ImpliedTermStructure< YieldTermStructure >(riskFreeRate, fixings.get(i))));
        }
        final Array discountFactors = new Array(discountFactorsArr);
        return new EuropeanPathMultiPathPricer(payoff, timePositions, forwardTermStructures, discountFactors);
    }

    //
    // PricingEngine
    //

    @Override
    public void calculate() /* @ReadOnly */ {
        // Mirrors C++ {@code calculate()} lines 62-70 of mcpathbasketengine.hpp:
        //   McSimulation<MultiVariate,RNG,S>::calculate(requiredTolerance_,
        //                                               requiredSamples_,
        //                                               maxSamples_);
        //   results_.value = mcModel_->sampleAccumulator().mean();
        //   if constexpr (RNG::allowsErrorEstimate)
        //       results_.errorEstimate = mcModel_->sampleAccumulator().errorEstimate();
        this.simulation_ = new McSimulation< MultiPath >(antitheticVariate_, controlVariate_) {
            @Override
            protected PathPricer< MultiPath > pathPricer() {
                return MCPathBasketEngine.this.pathPricer();
            }

            @Override
            protected MonteCarloModel.PathGeneratorAdapter< MultiPath > pathGenerator() {
                return MCPathBasketEngine.this.pathGenerator();
            }

            @Override
            protected TimeGrid timeGrid() {
                return MCPathBasketEngine.this.timeGrid();
            }
        };
        this.simulation_.calculate(requiredTolerance_, requiredSamples_, maxSamples_);
        results_.value = this.simulation_.sampleAccumulator().mean();
        results_.errorEstimate = this.simulation_.errorEstimate();
    }
}
