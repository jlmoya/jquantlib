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
 Copyright (C) 2005 Klaus Spanderen
 Copyright (C) 2007 StatPro Italia srl
*/
package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.experimental.processes.HestonStochasticLocalVolProcess;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.methods.montecarlo.*;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.processes.StochasticProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeGrid;

/**
 * Monte Carlo Heston-model engine for European options.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/pricingengines/vanilla/mceuropeanhestonengine.{hpp,cpp}} (Phase 5h.5-Bates-b; process-generalised in Phase
 * 5e.5b-CFC-d-235).
 *
 * <p>The C++ class is a template
 * {@code MCEuropeanHestonEngine<RNG=PseudoRandom, S=Statistics, P=HestonProcess>} inheriting from
 * {@code MCVanillaEngine<MultiVariate, RNG, S>}. Java's existing {@link MCVanillaEngine} is hard-wired for
 * single-variate {@code GeneralizedBlackScholesProcess}; rather than retro-fit a second template axis, this port
 * follows the {@link BatesEngine} pattern and stands the engine up directly on {@link OneAssetOption.EngineImpl} + an
 * embedded {@link McSimulation McSimulation&lt;MultiPath&gt;} delegate.
 *
 * <p>Specialised for {@code RNG = PseudoRandom} (Mersenne-Twister +
 * {@code InverseCumulativeNormal}) — quasi-random / low-discrepancy variants are deferred. Cross-validates against
 * {@link AnalyticHestonEngine}: convergence to the closed-form Heston price as N → ∞.
 *
 * <p><strong>Process generalisation (Phase 5e.5b-CFC-d-235):</strong> the
 * C++ template parameter {@code P} defaults to {@code HestonProcess} but the SLV test-suite
 * (test-suite/hestonslvmodel.cpp::testMonteCarloVsFdmPricing, testMonteCarloCalibration) exercises it with
 * {@code HestonSLVProcess} (Java {@link HestonStochasticLocalVolProcess}). The Java port now accepts either via
 * overloaded constructors — internally the engine stores the process as {@link StochasticProcess} (the common base) and
 * reaches the {@code riskFreeRate()} discount through an instance-typed dispatch helper. Two-factor variance dynamics
 * (Heston / SLV) and any subclass of {@code HestonProcess} (e.g. {@code BatesProcess}) work transparently — the path
 * pricer only inspects sub-path 0 (the spot trajectory), so the body is identical across process flavours.
 *
 * @author JQuantLib
 * @see EuropeanHestonPathPricer
 * @see MultiPathGenerator
 * @see HestonProcess
 * @see HestonStochasticLocalVolProcess
 */
public class MCEuropeanHestonEngine extends OneAssetOption.EngineImpl {

    //
    // protected fields (mirror C++ MCVanillaEngine<MultiVariate,...>
    // that this class would inherit from in the C++ template).
    //

    /**
     * Underlying process — either a {@link HestonProcess} (or subclass such as {@code BatesProcess}) or a
     * {@link HestonStochasticLocalVolProcess}. Stored as the common {@link StochasticProcess} base;
     * {@link #riskFreeRate()} dispatches to the right accessor by instance type.
     */
    protected final StochasticProcess process_;
    protected final int timeSteps_;
    protected final int timeStepsPerYear_;
    protected final int requiredSamples_;
    protected final int maxSamples_;
    protected final double requiredTolerance_;
    protected final boolean antitheticVariate_;
    protected final long seed_;

    /** Lazily-built delegate that owns the {@link MonteCarloModel}. */
    protected McSimulation< MultiPath > simulation_;

    //
    // constructors
    //

    /**
     * Mirrors C++
     * {@code MCEuropeanHestonEngine(process, timeSteps, timeStepsPerYear, antitheticVariate, requiredSamples,
     * requiredTolerance, maxSamples, seed)} for {@code P = HestonProcess}.
     *
     * <p>Pass {@link McSimulation#NULL_SAMPLES} ({@code Integer.MAX_VALUE})
     * or {@link McSimulation#NULL_TOLERANCE} ({@code NaN}) for "not specified".
     */
    public MCEuropeanHestonEngine(final HestonProcess process, final int timeSteps, final int timeStepsPerYear,
            final boolean antitheticVariate, final int requiredSamples, final double requiredTolerance,
            final int maxSamples, final long seed) {
        this((StochasticProcess) process, timeSteps, timeStepsPerYear, antitheticVariate, requiredSamples,
                requiredTolerance, maxSamples, seed);
    }

    /**
     * Mirrors C++ {@code MCEuropeanHestonEngine<...,P = HestonSLVProcess>} — the SLV specialisation exercised by
     * {@code test-suite/hestonslvmodel.cpp::testMonteCarloVsFdmPricing} and {@code testMonteCarloCalibration}.
     *
     * <p>The generated multi-path's first sub-path is the (log-leverage-
     * adjusted) spot trajectory; the second is the variance trajectory. The European path pricer
     * ({@link EuropeanHestonPathPricer}) reads only the spot sub-path's terminal value, so the body is identical to the
     * pure-Heston case once the process can produce paths.
     */
    public MCEuropeanHestonEngine(final HestonStochasticLocalVolProcess process, final int timeSteps,
            final int timeStepsPerYear, final boolean antitheticVariate, final int requiredSamples,
            final double requiredTolerance, final int maxSamples, final long seed) {
        this((StochasticProcess) process, timeSteps, timeStepsPerYear, antitheticVariate, requiredSamples,
                requiredTolerance, maxSamples, seed);
    }

    /**
     * Common ctor body shared by both public overloads. Package-private so the {@link MakeMCEuropeanHestonEngine}
     * builder can also reach this generically.
     */
    MCEuropeanHestonEngine(final StochasticProcess process, final int timeSteps, final int timeStepsPerYear,
            final boolean antitheticVariate, final int requiredSamples, final double requiredTolerance,
            final int maxSamples, final long seed) {
        super();
        QL.require(process != null, "null Heston-like process");
        QL.require(process instanceof HestonProcess || process instanceof HestonStochasticLocalVolProcess,
                "process must be a HestonProcess (or subclass) or a " + "HestonStochasticLocalVolProcess");
        QL.require(timeSteps != McSimulation.NULL_SAMPLES || timeStepsPerYear != McSimulation.NULL_SAMPLES,
                "no time steps provided");
        QL.require(timeSteps == McSimulation.NULL_SAMPLES || timeStepsPerYear == McSimulation.NULL_SAMPLES,
                "both time steps and time steps per year were provided");
        QL.require(timeSteps != 0, "timeSteps must be positive, " + timeSteps + " not allowed");
        QL.require(timeStepsPerYear != 0, "timeStepsPerYear must be positive, " + timeStepsPerYear + " not allowed");

        this.process_ = process;
        this.timeSteps_ = timeSteps;
        this.timeStepsPerYear_ = timeStepsPerYear;
        this.antitheticVariate_ = antitheticVariate;
        this.requiredSamples_ = requiredSamples;
        this.maxSamples_ = maxSamples;
        this.requiredTolerance_ = requiredTolerance;
        this.seed_ = seed;
        this.process_.addObserver(this);
    }

    /**
     * Risk-free curve accessor — dispatches by instance type. C++ uses a single template type {@code P} for which
     * {@code riskFreeRate()} is resolved at compile-time; Java's lack of a shared {@code riskFreeRate()} on
     * {@link StochasticProcess} forces an {@code instanceof} switch here.
     */
    protected Handle< YieldTermStructure > riskFreeRate() {
        if ( process_ instanceof HestonProcess ) {
            return ((HestonProcess) process_).riskFreeRate();
        }
        if ( process_ instanceof HestonStochasticLocalVolProcess ) {
            return ((HestonStochasticLocalVolProcess) process_).riskFreeRate();
        }
        throw new IllegalStateException("unsupported process type: " + process_.getClass().getName());
    }

    //
    // McSimulation-shaped helpers
    //

    /** Mirrors C++ {@code MCVanillaEngine::timeGrid()}. */
    protected TimeGrid timeGrid() {
        final OneAssetOption.ArgumentsImpl a = (OneAssetOption.ArgumentsImpl) arguments_;
        final Date lastExerciseDate = a.exercise.lastDate();
        final double t = process_.time(lastExerciseDate);
        if ( timeSteps_ != McSimulation.NULL_SAMPLES ) {
            return new TimeGrid(t, timeSteps_);
        } else if ( timeStepsPerYear_ != McSimulation.NULL_SAMPLES ) {
            final int steps = (int) (timeStepsPerYear_ * t);
            return new TimeGrid(t, Math.max(steps, 1));
        } else {
            throw new RuntimeException("time steps not specified");
        }
    }

    /**
     * Builds a Gaussian-driven {@link MultiPathGenerator} for the underlying Heston-like process. Mirrors C++
     * {@code MCVanillaEngine::pathGenerator()} specialised to {@code MC = MultiVariate, RNG = PseudoRandom}.
     */
    protected MonteCarloModel.PathGeneratorAdapter< MultiPath > pathGenerator() {
        final TimeGrid grid = timeGrid();
        final int dimensions = process_.factors() * (grid.size() - 1);
        final RandomSequenceGenerator< MersenneTwisterUniformRng > uniformRsg = new RandomSequenceGenerator< MersenneTwisterUniformRng >(
                MersenneTwisterUniformRng.class, dimensions, seed_);
        final InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > gsg = new InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal >(
                uniformRsg, new InverseCumulativeNormal());
        final MultiPathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > > gen = new MultiPathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > >(
                process_, grid, gsg, /* brownianBridge */ false);
        return new MonteCarloModel.MultiPathGeneratorAdapterImpl(gen);
    }

    /**
     * Mirrors C++ {@code MCEuropeanHestonEngine::pathPricer()} — European plain-vanilla payoff applied to the asset
     * trajectory (sub-path 0 of the multi-path).
     */
    protected PathPricer< MultiPath > pathPricer() {
        final OneAssetOption.ArgumentsImpl a = (OneAssetOption.ArgumentsImpl) arguments_;
        final PlainVanillaPayoff payoff;
        try {
            payoff = (PlainVanillaPayoff) a.payoff;
        } catch ( final ClassCastException e ) {
            throw new RuntimeException("non-plain payoff given");
        }
        QL.require(payoff != null, "non-plain payoff given");

        final double discount = riskFreeRate().currentLink().discount(timeGrid().back());
        return new EuropeanHestonPathPricer(payoff.optionType(), payoff.strike(), discount);
    }

    //
    // PricingEngine
    //

    /**
     * Mirrors C++ {@code MCVanillaEngine::calculate()}: drives the embedded {@link McSimulation} with the engine's
     * stored tolerance / sample budget, then writes the mean (and error estimate) to the results.
     */
    @Override
    public void calculate() /* @ReadOnly */ {
        final OneAssetOption.ResultsImpl r = (OneAssetOption.ResultsImpl) results_;

        this.simulation_ = new McSimulation< MultiPath >(antitheticVariate_, /* controlVariate */ false) {
            @Override
            protected PathPricer< MultiPath > pathPricer() {
                return MCEuropeanHestonEngine.this.pathPricer();
            }

            @Override
            protected MonteCarloModel.PathGeneratorAdapter< MultiPath > pathGenerator() {
                return MCEuropeanHestonEngine.this.pathGenerator();
            }

            @Override
            protected TimeGrid timeGrid() {
                return MCEuropeanHestonEngine.this.timeGrid();
            }
        };
        this.simulation_.calculate(requiredTolerance_, requiredSamples_, maxSamples_);
        r.value = this.simulation_.sampleAccumulator().mean();
        r.errorEstimate = this.simulation_.errorEstimate();
    }
}
