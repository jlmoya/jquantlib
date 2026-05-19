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
*/

package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.exercise.EarlyExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.math.statistics.Statistics;
import org.jquantlib.methods.montecarlo.*;
import org.jquantlib.model.shortrate.StochasticProcessArray;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.time.TimeGrid;

/**
 * Monte-Carlo American max-of-N option pricing engine using Longstaff-Schwartz regression.
 *
 * <p>Java port of the test-suite-internal C++ class
 * {@code MCAmericanMaxEngine<RNG>} from {@code QuantLib v1.42.1 test-suite/mclongstaffschwartzengine.cpp} (Phase
 * MC-extras WI-6). Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The C++ template specialises
 * {@code MCLongstaffSchwartzEngine<VanillaOption::engine, MultiVariate, RNG>} over the {@link AmericanMaxPathPricer}
 * payoff. In Java, the {@link org.jquantlib.pricingengines.MCLongstaffSchwartzEngine} parent is hard-bound to
 * single-asset {@link org.jquantlib.methods.montecarlo.Path}; we therefore implement the multi-asset analog stand-alone
 * here, mirroring the structural pattern of {@link org.jquantlib.pricingengines.basket.MCEuropeanBasketEngine}
 * (composition around {@link McSimulation}{@code <MultiPath>}) plus the calibration-then-pricing two-phase
 * orchestration of {@link org.jquantlib.pricingengines.MCLongstaffSchwartzEngine}.
 *
 * <p>Specialised to {@code RNG = PseudoRandom} (Mersenne-Twister); the
 * quasi-random and antithetic-quasi variants are deferred to Phase MC-extras-b.
 *
 * <p>The option container is {@link org.jquantlib.instruments.VanillaOption}
 * (carries only payoff + exercise); the multi-asset processes live on the engine itself (mirrors C++ where
 * {@code VanillaOption americanMaxOption(payoff, exercise)} is set with an
 * {@code MCAmericanMaxEngine<PseudoRandom>(processes, ...)}).
 *
 * @author JQuantLib
 */
public class MCAmericanMaxEngine extends GenericEngine< OneAssetOption.Arguments, OneAssetOption.Results >
        implements OneAssetOption.Engine {

    //
    // protected fields
    //

    protected final StochasticProcessArray processes_;
    protected final int timeSteps_;
    protected final int timeStepsPerYear_;
    protected final boolean brownianBridge_;
    protected final boolean antitheticVariate_;
    protected final boolean controlVariate_;
    protected final int requiredSamples_;
    protected final double requiredTolerance_;
    protected final int maxSamples_;
    protected final long seed_;
    protected final int nCalibrationSamples_;

    protected LongstaffSchwartzMultiPathPricer pathPricer_;
    protected McSimulation< MultiPath > simulation_;

    //
    // constructor
    //

    /**
     * Mirrors C++
     * {@code MCAmericanMaxEngine(processes, timeSteps, timeStepsPerYear, brownianBridge, antitheticVariate,
     * controlVariate, requiredSamples, requiredTolerance, maxSamples, seed, nCalibrationSamples = Null<Size>())}.
     *
     * <p>{@code nCalibrationSamples == NULL_SAMPLES} → C++ default 2048.
     */
    public MCAmericanMaxEngine(final StochasticProcessArray processes, final int timeSteps, final int timeStepsPerYear,
            final boolean brownianBridge, final boolean antitheticVariate, final boolean controlVariate,
            final int requiredSamples, final double requiredTolerance, final int maxSamples, final long seed,
            final int nCalibrationSamples) {
        super(new OneAssetOption.ArgumentsImpl(), new OneAssetOption.ResultsImpl());
        QL.require(timeSteps != McSimulation.NULL_SAMPLES || timeStepsPerYear != McSimulation.NULL_SAMPLES,
                "no time steps provided");
        QL.require(timeSteps == McSimulation.NULL_SAMPLES || timeStepsPerYear == McSimulation.NULL_SAMPLES,
                "both time steps and time steps per year were provided");

        this.processes_ = processes;
        this.timeSteps_ = timeSteps;
        this.timeStepsPerYear_ = timeStepsPerYear;
        this.brownianBridge_ = brownianBridge;
        this.antitheticVariate_ = antitheticVariate;
        this.controlVariate_ = controlVariate;
        this.requiredSamples_ = requiredSamples;
        this.requiredTolerance_ = requiredTolerance;
        this.maxSamples_ = maxSamples;
        this.seed_ = seed;
        this.nCalibrationSamples_ = (nCalibrationSamples == McSimulation.NULL_SAMPLES) ? 2048 : nCalibrationSamples;
        this.processes_.addObserver(this);
    }

    //
    // helpers
    //

    /** Mirrors C++ {@code TimeGrid timeGrid()} for an American option. */
    protected TimeGrid timeGrid() {
        final OneAssetOption.ArgumentsImpl a = (OneAssetOption.ArgumentsImpl) arguments_;
        final Exercise exercise = a.exercise;
        QL.require(exercise instanceof EarlyExercise, "wrong exercise given");
        final double residualTime = processes_.time(exercise.lastDate());
        if ( timeSteps_ != McSimulation.NULL_SAMPLES ) {
            return new TimeGrid(residualTime, timeSteps_);
        } else if ( timeStepsPerYear_ != McSimulation.NULL_SAMPLES ) {
            final int steps = (int) (timeStepsPerYear_ * residualTime);
            return new TimeGrid(residualTime, Math.max(steps, 1));
        } else {
            throw new RuntimeException("time steps not specified");
        }
    }

    /** Build a Gaussian-driven {@link MultiPathGenerator} for the underlying processes. */
    protected MonteCarloModel.PathGeneratorAdapter< MultiPath > pathGenerator(final long seed) {
        final TimeGrid grid = timeGrid();
        final int dimensions = processes_.factors() * (grid.size() - 1);
        final RandomSequenceGenerator< MersenneTwisterUniformRng > uniformRsg = new RandomSequenceGenerator< MersenneTwisterUniformRng >(
                MersenneTwisterUniformRng.class, dimensions, seed);
        final InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > gsg = new InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal >(
                uniformRsg, new InverseCumulativeNormal());
        final MultiPathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > > gen = new MultiPathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > >(
                processes_, grid, gsg, brownianBridge_);
        return new MonteCarloModel.MultiPathGeneratorAdapterImpl(gen);
    }

    /** Build the LSM multi-path pricer (calibration phase). */
    protected LongstaffSchwartzMultiPathPricer lsmPathPricer() {
        final OneAssetOption.ArgumentsImpl a = (OneAssetOption.ArgumentsImpl) arguments_;
        // Resolve the risk-free curve from the first underlying process.
        final StochasticProcess1D first = processes_.process(0);
        QL.require(first instanceof GeneralizedBlackScholesProcess, "generalized Black-Scholes process required");
        final GeneralizedBlackScholesProcess process = (GeneralizedBlackScholesProcess) first;

        final AmericanMaxPathPricer earlyPricer = new AmericanMaxPathPricer(a.payoff);
        return new LongstaffSchwartzMultiPathPricer(this.timeGrid(), earlyPricer, process.riskFreeRate().currentLink());
    }

    //
    // PricingEngine
    //

    /**
     * Mirrors C++ {@code MCLongstaffSchwartzEngine::calculate()}: build the pricer, run the calibration MC, calibrate,
     * then run the pricing MC.
     */
    @Override
    public void calculate() /* @ReadOnly */ {
        final OneAssetOption.ResultsImpl r = (OneAssetOption.ResultsImpl) results_;

        // 1) calibration phase: build a pricer in calibration mode and
        //    drive it with N samples via a dedicated MC model.
        this.pathPricer_ = lsmPathPricer();
        final long seedCal = (seed_ == 0) ? 0 : seed_ + 1768237423L;
        final MonteCarloModel< MultiPath > mcModelCalibration = new MonteCarloModel< MultiPath >(pathGenerator(seedCal),
                this.pathPricer_, new Statistics(), antitheticVariate_);
        mcModelCalibration.addSamples(nCalibrationSamples_);
        this.pathPricer_.calibrate();

        // 2) pricing phase: standard McSimulation drives the now-calibrated
        //    pricer to produce mean + error.
        final MonteCarloModel.PathGeneratorAdapter< MultiPath > pricingGen = pathGenerator(seed_);
        final LongstaffSchwartzMultiPathPricer pricer = this.pathPricer_;
        this.simulation_ = new McSimulation< MultiPath >(antitheticVariate_, controlVariate_) {
            @Override
            protected PathPricer< MultiPath > pathPricer() {
                return pricer;
            }

            @Override
            protected MonteCarloModel.PathGeneratorAdapter< MultiPath > pathGenerator() {
                return pricingGen;
            }

            @Override
            protected TimeGrid timeGrid() {
                return MCAmericanMaxEngine.this.timeGrid();
            }
        };
        this.simulation_.calculate(requiredTolerance_, requiredSamples_, maxSamples_);
        r.value = this.simulation_.sampleAccumulator().mean();
        r.errorEstimate = this.simulation_.errorEstimate();
        // Suppress 'unused' warnings on Array import without changing
        // public API or intent.
        @SuppressWarnings( "unused" )
        final Class< ? > arrayClass = Array.class;
    }
}
