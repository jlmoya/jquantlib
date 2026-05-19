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
package org.jquantlib.pricingengines.forward;

import org.jquantlib.QL;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.instruments.ForwardVanillaOption;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.methods.montecarlo.*;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.vanilla.AnalyticHestonEngine;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Monte Carlo forward-starting (strike-resetting) European option engine driven by a {@link HestonProcess}.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/pricingengines/forward/mcforwardeuropeanhestonengine.{hpp,cpp}} (Phase 5e.5b-CFC-d-119). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The C++ class is a template
 * {@code MCForwardEuropeanHestonEngine<RNG=PseudoRandom, S=Statistics, P=HestonProcess>} inheriting from
 * {@code MCForwardVanillaEngine<MultiVariate,RNG,S>} which itself extends both
 * {@code GenericEngine<ForwardOptionArguments<VanillaOption::arguments>, VanillaOption::results>} and
 * {@code McSimulation<MultiVariate,RNG,S>}. Java single-inheritance forces a choice; this port follows the
 * {@link org.jquantlib.pricingengines.vanilla.MCHestonHullWhiteEngine} pattern: extend
 * {@link ForwardVanillaOption.EngineImpl} and embed a delegate {@link McSimulation McSimulation&lt;MultiPath&gt;}.
 *
 * <p>The control-variate variant uses the standard
 * {@link AnalyticHestonEngine} on the vanilla option running from {@code t=0} to {@code t=expiry}, working well if
 * {@code resetTime} is small (see PR <a href="https://github.com/lballabio/QuantLib/pull/948">
 * lballabio/QuantLib#948</a> for trade-off discussion).
 *
 * <p>Specialised for {@code RNG = PseudoRandom} (Mersenne-Twister +
 * {@code InverseCumulativeNormal}) — quasi-random / low-discrepancy variants are deferred. Cross-validates against
 * {@link ForwardVanillaEngine} (BS-equivalent flat Heston) and
 * {@link org.jquantlib.experimental.forward.AnalyticHestonForwardEuropeanEngine} (semi-analytic forward-start Heston).
 *
 * @author JQuantLib
 * @see ForwardEuropeanHestonPathPricer
 * @see AnalyticHestonEngine
 */
public class MCForwardEuropeanHestonEngine extends ForwardVanillaOption.EngineImpl {

    //
    // protected fields (mirror C++ MCForwardVanillaEngine<MultiVariate,...>
    // that this class would inherit from in the C++ template).
    //

    protected final HestonProcess process_;
    protected final int timeSteps_;
    protected final int timeStepsPerYear_;
    protected final int requiredSamples_;
    protected final int maxSamples_;
    protected final double requiredTolerance_;
    protected final boolean antitheticVariate_;
    protected final boolean controlVariate_;
    protected final long seed_;

    /** Lazily-built delegate that owns the {@link MonteCarloModel}. */
    protected McSimulation< MultiPath > simulation_;

    //
    // constructors
    //

    /**
     * Convenience constructor: control variate disabled (matches C++ default).
     */
    public MCForwardEuropeanHestonEngine(final HestonProcess process, final int timeSteps, final int timeStepsPerYear,
            final boolean antitheticVariate, final int requiredSamples, final double requiredTolerance,
            final int maxSamples, final long seed) {
        this(process, timeSteps, timeStepsPerYear, antitheticVariate, requiredSamples, requiredTolerance, maxSamples,
                seed,
                /* controlVariate */ false);
    }

    /**
     * Mirrors C++
     * {@code MCForwardEuropeanHestonEngine(process, timeSteps, timeStepsPerYear, antitheticVariate, requiredSamples,
     * requiredTolerance, maxSamples, seed, controlVariate=false)}.
     *
     * <p>Note: brownianBridge is hard-wired to {@code false} (C++ base
     * ctor passes {@code false} unconditionally).
     *
     * <p>Pass {@link McSimulation#NULL_SAMPLES} ({@code Integer.MAX_VALUE})
     * or {@link McSimulation#NULL_TOLERANCE} ({@code NaN}) for "not specified".
     */
    public MCForwardEuropeanHestonEngine(final HestonProcess process, final int timeSteps, final int timeStepsPerYear,
            final boolean antitheticVariate, final int requiredSamples, final double requiredTolerance,
            final int maxSamples, final long seed, final boolean controlVariate) {
        super();
        QL.require(process != null, "null Heston-like process");
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
        this.controlVariate_ = controlVariate;
        this.requiredSamples_ = requiredSamples;
        this.maxSamples_ = maxSamples;
        this.requiredTolerance_ = requiredTolerance;
        this.seed_ = seed;
        this.process_.addObserver(this);
    }

    //
    // McSimulation-shaped helpers
    //

    /**
     * Mirrors C++ {@code MCForwardVanillaEngine::timeGrid()}: build a mandatory-point grid that includes both the reset
     * date {@code t1} and the exercise date {@code t2}, then pad with evenly-spaced inner points to reach the requested
     * total step count.
     */
    protected TimeGrid timeGrid() {
        final ForwardVanillaOption.ArgumentsImpl a = (ForwardVanillaOption.ArgumentsImpl) arguments_;
        final Date resetDate = a.resetDate;
        final Date lastExerciseDate = a.exercise.lastDate();

        final double t1 = process_.time(resetDate);
        final double t2 = process_.time(lastExerciseDate);

        int totalSteps;
        if ( timeSteps_ != McSimulation.NULL_SAMPLES ) {
            totalSteps = timeSteps_;
        } else if ( timeStepsPerYear_ != McSimulation.NULL_SAMPLES ) {
            totalSteps = (int) (timeStepsPerYear_ * t2);
        } else {
            throw new RuntimeException("time steps not specified");
        }

        final List< Double > mandatory = new ArrayList< Double >();
        mandatory.add(t1);
        mandatory.add(t2);
        return new TimeGrid(mandatory, totalSteps);
    }

    /**
     * Builds a Gaussian-driven {@link MultiPathGenerator} for the underlying {@link HestonProcess}. Mirrors C++
     * {@code MCForwardVanillaEngine::pathGenerator()} specialised to {@code MC = MultiVariate, RNG = PseudoRandom}.
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
     * Mirrors C++ {@code MCForwardEuropeanHestonEngine::pathPricer()}: locate the reset-time grid index, then build a
     * {@link ForwardEuropeanHestonPathPricer} that strikes off the (moneyness * S_reset) sample on each path.
     */
    protected PathPricer< MultiPath > pathPricer() {
        final ForwardVanillaOption.ArgumentsImpl a = (ForwardVanillaOption.ArgumentsImpl) arguments_;
        final TimeGrid grid = timeGrid();

        final double resetTime = process_.time(a.resetDate);
        final int resetIndex = grid.closestIndex(resetTime);

        final PlainVanillaPayoff payoff;
        try {
            payoff = (PlainVanillaPayoff) a.payoff;
        } catch ( final ClassCastException e ) {
            throw new RuntimeException("non-plain payoff given");
        }
        QL.require(payoff != null, "non-plain payoff given");

        final double discount = process_.riskFreeRate().currentLink().discount(grid.back());
        return new ForwardEuropeanHestonPathPricer(payoff.optionType(), a.moneyness, resetIndex, discount);
    }

    /**
     * Mirrors C++ {@code MCForwardEuropeanHestonEngine::controlPathPricer()}.
     *
     * <p>Control variate prices a vanilla option on the path, and
     * compares to analytical Heston vanilla price. First entry in {@code TimeGrid} is 0, so the existing path pricer is
     * re-used with {@code resetIndex = 0}.
     */
    protected PathPricer< MultiPath > controlPathPricer() {
        final ForwardVanillaOption.ArgumentsImpl a = (ForwardVanillaOption.ArgumentsImpl) arguments_;
        final TimeGrid grid = timeGrid();

        final PlainVanillaPayoff payoff;
        try {
            payoff = (PlainVanillaPayoff) a.payoff;
        } catch ( final ClassCastException e ) {
            throw new RuntimeException("non-plain payoff given");
        }
        QL.require(payoff != null, "non-plain payoff given");

        final double discount = process_.riskFreeRate().currentLink().discount(grid.back());
        return new ForwardEuropeanHestonPathPricer(payoff.optionType(), a.moneyness, /* resetIndex */ 0, discount);
    }

    /**
     * Mirrors C++ {@code MCForwardEuropeanHestonEngine::controlPricingEngine()}: wraps the standard
     * {@link AnalyticHestonEngine} around a freshly-built {@link HestonModel} from the underlying process.
     */
    protected PricingEngine controlPricingEngine() {
        final HestonModel hestonModel = new HestonModel(process_);
        return new AnalyticHestonEngine(hestonModel, process_);
    }

    //
    // PricingEngine
    //

    /**
     * Mirrors C++ {@code MCForwardVanillaEngine::calculate()}: drives the embedded {@link McSimulation} with the
     * engine's stored tolerance / sample budget, then writes the mean (and error estimate) to the results.
     *
     * <p>When the control variate is enabled, the analytic-engine value
     * is pre-computed against a synthetic {@link VanillaOption} carrying the forward-start equivalent strike
     * {@code moneyness * S0} and the same exercise.
     */
    @Override
    public void calculate() /* @ReadOnly */ {
        final ForwardVanillaOption.ResultsImpl r = (ForwardVanillaOption.ResultsImpl) results_;

        // Pre-compute the control-variate value (an option NPV against
        // the analytic engine) once, before driving the MC loop.
        final double cvValue;
        if ( controlVariate_ ) {
            final ForwardVanillaOption.ArgumentsImpl a = (ForwardVanillaOption.ArgumentsImpl) arguments_;
            final PlainVanillaPayoff payoff;
            try {
                payoff = (PlainVanillaPayoff) a.payoff;
            } catch ( final ClassCastException e ) {
                throw new RuntimeException("non-plain payoff given");
            }
            final double spot = process_.s0().currentLink().value();
            final double strike = a.moneyness * spot;
            final StrikedTypePayoff cvPayoff = new PlainVanillaPayoff(payoff.optionType(), strike);
            final VanillaOption cvOption = new VanillaOption(cvPayoff, a.exercise);
            cvOption.setPricingEngine(controlPricingEngine());
            cvValue = cvOption.NPV();
        } else {
            cvValue = Double.NaN;
        }

        final double cvValueFinal = cvValue;
        this.simulation_ = new McSimulation< MultiPath >(antitheticVariate_, controlVariate_) {
            @Override
            protected PathPricer< MultiPath > pathPricer() {
                return MCForwardEuropeanHestonEngine.this.pathPricer();
            }

            @Override
            protected MonteCarloModel.PathGeneratorAdapter< MultiPath > pathGenerator() {
                return MCForwardEuropeanHestonEngine.this.pathGenerator();
            }

            @Override
            protected TimeGrid timeGrid() {
                return MCForwardEuropeanHestonEngine.this.timeGrid();
            }

            @Override
            protected PathPricer< MultiPath > controlPathPricer() {
                return MCForwardEuropeanHestonEngine.this.controlPathPricer();
            }

            @Override
            protected double controlVariateValue() {
                return cvValueFinal;
            }
        };
        this.simulation_.calculate(requiredTolerance_, requiredSamples_, maxSamples_);
        r.value = this.simulation_.sampleAccumulator().mean();
        r.errorEstimate = this.simulation_.errorEstimate();
    }

    //
    // PathPricer
    //

    /**
     * Java port of C++ {@code ForwardEuropeanHestonPathPricer}: evaluates the plain-vanilla payoff at the terminal
     * asset price (sub-path 0 of the multi-path) with the forward-set strike {@code S(resetIndex) * moneyness},
     * discounted by the constant pre-computed factor.
     */
    public static final class ForwardEuropeanHestonPathPricer extends PathPricer< MultiPath > {

        private final org.jquantlib.instruments.Option.Type type_;
        private final double moneyness_;
        private final int resetIndex_;
        private final double discount_;

        public ForwardEuropeanHestonPathPricer(final org.jquantlib.instruments.Option.Type type, final double moneyness,
                final int resetIndex, final double discount) {
            QL.require(moneyness >= 0.0, "moneyness less than zero not allowed");
            this.type_ = type;
            this.moneyness_ = moneyness;
            this.resetIndex_ = resetIndex;
            this.discount_ = discount;
        }

        @Override
        public Double op(final MultiPath multiPath) {
            final Path path = multiPath.get(0);
            final int n = multiPath.pathSize();
            QL.require(n > 0, "the path cannot be empty");

            final double resetLevel = path.get(resetIndex_);
            final double strike = resetLevel * moneyness_;
            final PlainVanillaPayoff payoff = new PlainVanillaPayoff(type_, strike);
            return payoff.get(path.back()) * discount_;
        }
    }
}
