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
import org.jquantlib.instruments.ForwardVanillaOption;
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
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Monte Carlo forward-starting (strike-resetting) European option engine driven by a
 * {@link GeneralizedBlackScholesProcess}.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/pricingengines/forward/mcforwardeuropeanbsengine.{hpp,cpp}} (Phase 5e.5b-CFC-d-119). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The C++ class is a template
 * {@code MCForwardEuropeanBSEngine<RNG=PseudoRandom, S=Statistics>} inheriting from
 * {@code MCForwardVanillaEngine<SingleVariate,RNG,S>} which itself extends both
 * {@code GenericEngine<ForwardOptionArguments<VanillaOption::arguments>, VanillaOption::results>} and
 * {@code McSimulation<SingleVariate,RNG,S>}. Java single-inheritance forces a choice; this port follows the
 * {@link org.jquantlib.pricingengines.vanilla.MCEuropeanHestonEngine} pattern: extend
 * {@link ForwardVanillaOption.EngineImpl} (so the arguments_ / results_ / Observable wiring stays intact) and embed a
 * delegate {@link McSimulation McSimulation&lt;Path&gt;}.
 *
 * <p>Specialised for {@code RNG = PseudoRandom} (Mersenne-Twister +
 * {@code InverseCumulativeNormal}) — quasi-random / low-discrepancy variants are deferred. Cross-validates against
 * {@link ForwardVanillaEngine}: convergence to the analytic forward- starting BS price as N → ∞.
 *
 * @author JQuantLib
 * @see ForwardVanillaEngine
 * @see ForwardEuropeanBSPathPricer
 */
public class MCForwardEuropeanBSEngine extends ForwardVanillaOption.EngineImpl {

    //
    // protected fields (mirror C++ MCForwardVanillaEngine<SingleVariate,...>
    // that this class would inherit from in the C++ template).
    //

    protected final GeneralizedBlackScholesProcess process_;
    protected final int timeSteps_;
    protected final int timeStepsPerYear_;
    protected final int requiredSamples_;
    protected final int maxSamples_;
    protected final double requiredTolerance_;
    protected final boolean brownianBridge_;
    protected final boolean antitheticVariate_;
    protected final long seed_;

    /** Lazily-built delegate that owns the {@link MonteCarloModel}. */
    protected McSimulation< Path > simulation_;

    //
    // constructors
    //

    /**
     * Mirrors C++
     * {@code MCForwardEuropeanBSEngine(process, timeSteps, timeStepsPerYear, brownianBridge, antitheticVariate,
     * requiredSamples, requiredTolerance, maxSamples, seed)}.
     *
     * <p>Pass {@link McSimulation#NULL_SAMPLES} ({@code Integer.MAX_VALUE})
     * or {@link McSimulation#NULL_TOLERANCE} ({@code NaN}) for "not specified".
     */
    public MCForwardEuropeanBSEngine(final GeneralizedBlackScholesProcess process, final int timeSteps,
            final int timeStepsPerYear, final boolean brownianBridge, final boolean antitheticVariate,
            final int requiredSamples, final double requiredTolerance, final int maxSamples, final long seed) {
        super();
        QL.require(process != null, "null Black-Scholes process");
        QL.require(timeSteps != McSimulation.NULL_SAMPLES || timeStepsPerYear != McSimulation.NULL_SAMPLES,
                "no time steps provided");
        QL.require(timeSteps == McSimulation.NULL_SAMPLES || timeStepsPerYear == McSimulation.NULL_SAMPLES,
                "both time steps and time steps per year were provided");
        QL.require(timeSteps != 0, "timeSteps must be positive, " + timeSteps + " not allowed");
        QL.require(timeStepsPerYear != 0, "timeStepsPerYear must be positive, " + timeStepsPerYear + " not allowed");

        this.process_ = process;
        this.timeSteps_ = timeSteps;
        this.timeStepsPerYear_ = timeStepsPerYear;
        this.brownianBridge_ = brownianBridge;
        this.antitheticVariate_ = antitheticVariate;
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

        final List< Double > mandatory = new ArrayList<>();
        mandatory.add(t1);
        mandatory.add(t2);
        return new TimeGrid(mandatory, totalSteps);
    }

    /**
     * Builds a Gaussian-driven {@link PathGenerator} for the underlying {@link GeneralizedBlackScholesProcess}. Mirrors
     * C++ {@code MCForwardVanillaEngine::pathGenerator()} specialised to
     * {@code MC = SingleVariate, RNG = PseudoRandom}.
     */
    protected MonteCarloModel.PathGeneratorAdapter< Path > pathGenerator() {
        final TimeGrid grid = timeGrid();
        final int dimensions = process_.factors() * (grid.size() - 1);
        final RandomSequenceGenerator< MersenneTwisterUniformRng > uniformRsg = new RandomSequenceGenerator< MersenneTwisterUniformRng >(
                MersenneTwisterUniformRng.class, dimensions, seed_);
        final InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > gsg = new InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal >(
                uniformRsg, new InverseCumulativeNormal());
        final PathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > > gen = new PathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > >(
                process_, grid, gsg, brownianBridge_);
        return new MonteCarloModel.PathGeneratorAdapterImpl(gen);
    }

    /**
     * Mirrors C++ {@code MCForwardEuropeanBSEngine::pathPricer()}: locate the reset-time grid index, then build a
     * {@link ForwardEuropeanBSPathPricer} that strikes off the (moneyness * S_reset) sample on each path.
     */
    protected PathPricer< Path > pathPricer() {
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
        return new ForwardEuropeanBSPathPricer(payoff.optionType(), a.moneyness, resetIndex, discount);
    }

    //
    // PricingEngine
    //

    /**
     * Mirrors C++ {@code MCForwardVanillaEngine::calculate()}: drives the embedded {@link McSimulation} with the
     * engine's stored tolerance / sample budget, then writes the mean (and error estimate) to the results.
     */
    @Override
    public void calculate() /* @ReadOnly */ {
        final ForwardVanillaOption.ResultsImpl r = (ForwardVanillaOption.ResultsImpl) results_;

        this.simulation_ = new McSimulation< Path >(antitheticVariate_, /* controlVariate */ false) {
            @Override
            protected PathPricer< Path > pathPricer() {
                return MCForwardEuropeanBSEngine.this.pathPricer();
            }

            @Override
            protected MonteCarloModel.PathGeneratorAdapter< Path > pathGenerator() {
                return MCForwardEuropeanBSEngine.this.pathGenerator();
            }

            @Override
            protected TimeGrid timeGrid() {
                return MCForwardEuropeanBSEngine.this.timeGrid();
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
     * Java port of C++ {@code ForwardEuropeanBSPathPricer}: evaluates the plain-vanilla payoff at the terminal asset
     * price with the forward-set strike {@code S(resetIndex) * moneyness}, discounted by the constant pre-computed
     * factor.
     */
    public static final class ForwardEuropeanBSPathPricer extends PathPricer< Path > {

        private final org.jquantlib.instruments.Option.Type type_;
        private final double moneyness_;
        private final int resetIndex_;
        private final double discount_;

        public ForwardEuropeanBSPathPricer(final org.jquantlib.instruments.Option.Type type, final double moneyness,
                final int resetIndex, final double discount) {
            QL.require(moneyness >= 0.0, "moneyness less than zero not allowed");
            this.type_ = type;
            this.moneyness_ = moneyness;
            this.resetIndex_ = resetIndex;
            this.discount_ = discount;
        }

        @Override
        public Double op(final Path path) {
            final int n = path.length() - 1;
            QL.require(n > 0, "the path cannot be empty");

            final double resetLevel = path.get(resetIndex_);
            final double strike = resetLevel * moneyness_;
            final PlainVanillaPayoff payoff = new PlainVanillaPayoff(type_, strike);
            return payoff.get(path.back()) * discount_;
        }
    }
}
