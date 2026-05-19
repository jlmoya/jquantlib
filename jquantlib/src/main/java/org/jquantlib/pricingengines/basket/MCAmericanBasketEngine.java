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
 Copyright (C) 2006 Klaus Spanderen
 Copyright (C) 2007, 2008 StatPro Italia srl
*/

package org.jquantlib.pricingengines.basket;

import org.jquantlib.QL;
import org.jquantlib.exercise.EarlyExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.BasketOption;
import org.jquantlib.instruments.MultiAssetOption;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.math.statistics.Statistics;
import org.jquantlib.methods.montecarlo.*;
import org.jquantlib.model.shortrate.StochasticProcessArray;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.time.TimeGrid;

/**
 * Monte-Carlo American basket-option pricing engine using Longstaff-Schwartz regression.
 *
 * <p>Java port of C++ template
 * {@code MCAmericanBasketEngine<RNG = PseudoRandom>} from
 * {@code QuantLib v1.42.1 ql/pricingengines/basket/mcamericanbasketengine.{hpp,cpp}} (Phase 4i.5b WI-2). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The C++ template extends
 * {@code MCLongstaffSchwartzEngine<BasketOption::engine, MultiVariate, RNG>}. The Java
 * {@link org.jquantlib.pricingengines.MCLongstaffSchwartzEngine} parent is hard-bound to single-asset
 * {@link org.jquantlib.methods.montecarlo.Path}; this port therefore implements the multi-asset analog stand-alone
 * here, mirroring the structural pattern of {@link MCEuropeanBasketEngine} (composition around
 * {@link McSimulation}{@code <MultiPath>}) plus the calibration-then-pricing two-phase orchestration of
 * {@link org.jquantlib.pricingengines.vanilla.MCAmericanMaxEngine}.
 *
 * <p>Specialised to {@code RNG = PseudoRandom} (Mersenne-Twister +
 * InverseCumulativeNormal); quasi-random / antithetic-quasi variants are deferred (consistent with
 * {@link MCEuropeanBasketEngine}'s scope).
 *
 * <p>Per-path pricer is {@link AmericanBasketPathPricer} — uses
 * {@link LsmBasisSystem#multiPathBasisSystem} (dim=numAssets) plus the scaled basket payoff functional as the
 * regression basis.
 *
 * <p>{@code nCalibrationSamples == NULL_SAMPLES} → C++ default 2048 (matches
 * {@link org.jquantlib.pricingengines.vanilla.MCAmericanMaxEngine}).
 *
 * @author JQuantLib
 */
public class MCAmericanBasketEngine extends BasketOption.Engine {

    //
    // protected fields
    //

    protected final StochasticProcessArray processes_;
    protected final int timeSteps_;
    protected final int timeStepsPerYear_;
    protected final boolean brownianBridge_;
    protected final boolean antitheticVariate_;
    protected final int requiredSamples_;
    protected final double requiredTolerance_;
    protected final int maxSamples_;
    protected final long seed_;
    protected final int nCalibrationSamples_;
    protected final int polynomialOrder_;
    protected final LsmBasisSystem.PolynomialType polynomialType_;

    protected LongstaffSchwartzMultiPathPricer pathPricer_;
    protected McSimulation< MultiPath > simulation_;

    //
    // constructors
    //

    /**
     * Default-arity constructor matching C++ default arguments ({@code polynomialOrder = 2},
     * {@code polynomialType = Monomial}, {@code nCalibrationSamples = NULL_SAMPLES → 2048}).
     */
    public MCAmericanBasketEngine(final StochasticProcessArray processes, final int timeSteps,
            final int timeStepsPerYear, final boolean brownianBridge, final boolean antitheticVariate,
            final int requiredSamples, final double requiredTolerance, final int maxSamples, final long seed) {
        this(processes, timeSteps, timeStepsPerYear, brownianBridge, antitheticVariate, requiredSamples,
                requiredTolerance, maxSamples, seed, McSimulation.NULL_SAMPLES, 2,
                LsmBasisSystem.PolynomialType.Monomial);
    }

    /**
     * Mirrors C++
     * {@code MCAmericanBasketEngine(processes, timeSteps, timeStepsPerYear, brownianBridge, antitheticVariate,
     * requiredSamples, requiredTolerance, maxSamples, seed, nCalibrationSamples = Null<Size>(), polynomialOrder = 2,
     * polynomialType = LsmBasisSystem::Monomial)}.
     *
     * <p>Pass {@link McSimulation#NULL_SAMPLES} (Integer.MAX_VALUE) /
     * {@link McSimulation#NULL_TOLERANCE} (NaN) for "not specified".
     *
     * <p>{@code nCalibrationSamples == NULL_SAMPLES} → C++ default 2048.
     */
    public MCAmericanBasketEngine(final StochasticProcessArray processes, final int timeSteps,
            final int timeStepsPerYear, final boolean brownianBridge, final boolean antitheticVariate,
            final int requiredSamples, final double requiredTolerance, final int maxSamples, final long seed,
            final int nCalibrationSamples, final int polynomialOrder,
            final LsmBasisSystem.PolynomialType polynomialType) {
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
        this.requiredTolerance_ = requiredTolerance;
        this.maxSamples_ = maxSamples;
        this.seed_ = seed;
        this.nCalibrationSamples_ = (nCalibrationSamples == McSimulation.NULL_SAMPLES) ? 2048 : nCalibrationSamples;
        this.polynomialOrder_ = polynomialOrder;
        this.polynomialType_ = polynomialType;
        this.processes_.addObserver(this);
    }

    //
    // McSimulation-shaped helpers
    //

    /**
     * Mirrors C++ {@code TimeGrid timeGrid()} for an American basket option.
     */
    protected TimeGrid timeGrid() {
        final MultiAssetOption.ArgumentsImpl a = arguments_;
        final Exercise exercise = a.exercise;
        QL.require(exercise instanceof EarlyExercise, "wrong exercise given");
        QL.require(!((EarlyExercise) exercise).payoffAtExpiry(), "payoff at expiry not handled");
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

    /** Build the LSM multi-path pricer (calibration phase). Mirrors C++ {@code lsmPathPricer()}. */
    protected LongstaffSchwartzMultiPathPricer lsmPathPricer() {
        QL.require(processes_ != null && processes_.size() > 0, "Stochastic process array required");

        // Resolve the risk-free curve from the first underlying process.
        // Mirrors C++ {@code dynamic_pointer_cast<GeneralizedBlackScholesProcess>}
        // on processArray->process(0).
        final StochasticProcess1D first = processes_.process(0);
        if ( !(first instanceof GeneralizedBlackScholesProcess) ) {
            throw new RuntimeException("generalized Black-Scholes process required");
        }
        final GeneralizedBlackScholesProcess process = (GeneralizedBlackScholesProcess) first;

        final MultiAssetOption.ArgumentsImpl a = arguments_;
        final AmericanBasketPathPricer earlyPricer = new AmericanBasketPathPricer(processes_.size(), a.payoff,
                polynomialOrder_, polynomialType_);

        return new LongstaffSchwartzMultiPathPricer(this.timeGrid(), earlyPricer, process.riskFreeRate().currentLink());
    }

    //
    // PricingEngine
    //

    /**
     * Mirrors C++ {@code MCLongstaffSchwartzEngine::calculate()}: build the pricer in calibration mode, run the
     * calibration MC, calibrate, then run the pricing MC and write the mean / error to results.
     */
    @Override
    public void calculate() /* @ReadOnly */ {
        final MultiAssetOption.ResultsImpl r = results_;

        // 1) calibration phase: build a pricer in calibration mode and
        //    drive it with N samples via a dedicated MC model.
        this.pathPricer_ = lsmPathPricer();
        // C++ derives the calibration seed by adding an offset to the seed
        // (see McLongstaffSchwartzEngine.hpp); MCAmericanMaxEngine uses the
        // same offset 1768237423L. Reproduce here for parity.
        final long seedCal = (seed_ == 0) ? 0 : seed_ + 1768237423L;
        final MonteCarloModel< MultiPath > mcModelCalibration = new MonteCarloModel< MultiPath >(pathGenerator(seedCal),
                this.pathPricer_, new Statistics(), antitheticVariate_);
        mcModelCalibration.addSamples(nCalibrationSamples_);
        this.pathPricer_.calibrate();

        // 2) pricing phase: standard McSimulation drives the now-calibrated
        //    pricer to produce mean + error.
        final MonteCarloModel.PathGeneratorAdapter< MultiPath > pricingGen = pathGenerator(seed_);
        final LongstaffSchwartzMultiPathPricer pricer = this.pathPricer_;
        this.simulation_ = new McSimulation< MultiPath >(antitheticVariate_, /* controlVariate */ false) {
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
                return MCAmericanBasketEngine.this.timeGrid();
            }
        };
        this.simulation_.calculate(requiredTolerance_, requiredSamples_, maxSamples_);
        r.value = this.simulation_.sampleAccumulator().mean();
        r.errorEstimate = this.simulation_.errorEstimate();
    }
}
