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
 Copyright (C) 2003 Ferdinando Ametrano
 Copyright (C) 2003, 2004, 2005, 2007 StatPro Italia srl
*/

package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.instruments.OneAssetOption;
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

/**
 * Pricing engine for vanilla options using Monte Carlo simulation.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/vanilla/mcvanillaengine.hpp} (Phase 5h.5-MC-INFRA
 * WI-7).
 *
 * <p>The C++ template uses multiple inheritance ({@code Inst::engine} +
 * {@code McSimulation<MC,RNG,S>}). Java single-inheritance forces a
 * choice; this port extends {@link OneAssetOption.EngineImpl} (so the
 * Observable / arguments_ / results_ wiring is intact) and embeds a
 * delegate {@link McSimulation McSimulation&lt;Path&gt;} via composition.
 *
 * <p>This class is deliberately specialised to the common case
 * {@code MC = SingleVariate}, {@code RNG = PseudoRandom (MT +
 * InverseCumulativeNormal)}; lifting that restriction is a
 * Phase 5h.5-MC-INFRA-b carry-forward.
 *
 * @author JQuantLib
 */
public abstract class MCVanillaEngine extends OneAssetOption.EngineImpl {

    //
    // protected fields
    //

    protected final GeneralizedBlackScholesProcess process_;
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
    protected McSimulation<Path> simulation_;


    //
    // constructors
    //

    /**
     * Mirrors C++ {@code MCVanillaEngine(process, timeSteps,
     * timeStepsPerYear, brownianBridge, antitheticVariate,
     * controlVariate, requiredSamples, requiredTolerance, maxSamples,
     * seed)}.
     *
     * <p>Pass {@link McSimulation#NULL_SAMPLES} (Integer.MAX_VALUE) /
     * {@link McSimulation#NULL_TOLERANCE} (NaN) for "not specified".
     */
    protected MCVanillaEngine(final GeneralizedBlackScholesProcess process,
                              final int timeSteps,
                              final int timeStepsPerYear,
                              final boolean brownianBridge,
                              final boolean antitheticVariate,
                              final boolean controlVariate,
                              final int requiredSamples,
                              final double requiredTolerance,
                              final int maxSamples,
                              final long seed) {
        super();
        QL.require(timeSteps != McSimulation.NULL_SAMPLES
                || timeStepsPerYear != McSimulation.NULL_SAMPLES,
                "no time steps provided");
        QL.require(timeSteps == McSimulation.NULL_SAMPLES
                || timeStepsPerYear == McSimulation.NULL_SAMPLES,
                "both time steps and time steps per year were provided");
        QL.require(timeSteps != 0,
                "timeSteps must be positive, " + timeSteps + " not allowed");
        QL.require(timeStepsPerYear != 0,
                "timeStepsPerYear must be positive, " + timeStepsPerYear + " not allowed");

        this.process_ = process;
        this.timeSteps_ = timeSteps;
        this.timeStepsPerYear_ = timeStepsPerYear;
        this.brownianBridge_ = brownianBridge;
        this.antitheticVariate_ = antitheticVariate;
        this.controlVariate_ = controlVariate;
        this.requiredSamples_ = requiredSamples;
        this.maxSamples_ = maxSamples;
        this.requiredTolerance_ = requiredTolerance;
        this.seed_ = seed;
        this.process_.addObserver(this);
    }


    //
    // hooks for subclasses
    //

    /**
     * Subclasses must construct a path-pricer using current
     * {@link #arguments_} / {@link #process_} state.
     */
    protected abstract PathPricer<Path> pathPricer();


    //
    // McSimulation-shaped helpers
    //

    /**
     * Mirrors C++ {@code TimeGrid timeGrid()}: returns a uniform time
     * grid whose terminal date matches the option's last exercise
     * date.
     */
    protected TimeGrid timeGrid() {
        final OneAssetOption.ArgumentsImpl a = (OneAssetOption.ArgumentsImpl) arguments_;
        final Date lastExerciseDate = a.exercise.lastDate();
        final double t = process_.time(lastExerciseDate);
        if (timeSteps_ != McSimulation.NULL_SAMPLES) {
            return new TimeGrid(t, timeSteps_);
        } else if (timeStepsPerYear_ != McSimulation.NULL_SAMPLES) {
            final int steps = (int) (timeStepsPerYear_ * t);
            return new TimeGrid(t, Math.max(steps, 1));
        } else {
            throw new RuntimeException("time steps not specified");
        }
    }

    /**
     * Builds a Gaussian-driven {@link PathGenerator} for the
     * underlying {@link GeneralizedBlackScholesProcess}. Mirrors C++
     * {@code MCVanillaEngine::pathGenerator()} specialised to
     * {@code RNG = PseudoRandom}.
     */
    protected MonteCarloModel.PathGeneratorAdapter<Path> pathGenerator() {
        final TimeGrid grid = timeGrid();
        final int dimensions = process_.factors() * (grid.size() - 1);
        final RandomSequenceGenerator<MersenneTwisterUniformRng> uniformRsg =
                new RandomSequenceGenerator<MersenneTwisterUniformRng>(
                        MersenneTwisterUniformRng.class, dimensions, seed_);
        final InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                InverseCumulativeNormal> gsg =
                new InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                        InverseCumulativeNormal>(uniformRsg, new InverseCumulativeNormal());
        final PathGenerator<InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                InverseCumulativeNormal>> gen =
                new PathGenerator<InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                        InverseCumulativeNormal>>(process_, grid, gsg, brownianBridge_);
        return new MonteCarloModel.PathGeneratorAdapterImpl(gen);
    }


    //
    // PricingEngine
    //

    /**
     * Mirrors C++ {@code MCVanillaEngine::calculate()}: drives the
     * embedded {@link McSimulation} with the engine's stored tolerance
     * / sample budget, then writes the mean (and error estimate) to
     * the results.
     *
     * <p>Exercise-type validation is the concrete subclass's
     * responsibility (e.g. {@link MCEuropeanEngine} restricts to
     * European; future MC American restricts to American).
     */
    @Override
    public void calculate() /* @ReadOnly */ {
        final OneAssetOption.ResultsImpl r = (OneAssetOption.ResultsImpl) results_;

        // Build the McSimulation delegate the first time calculate() runs.
        // Re-use across observer-driven recalculations so the accumulator's
        // sample budget is honoured per call.
        this.simulation_ = new McSimulation<Path>(antitheticVariate_, controlVariate_) {
            @Override protected PathPricer<Path> pathPricer() {
                return MCVanillaEngine.this.pathPricer();
            }
            @Override protected MonteCarloModel.PathGeneratorAdapter<Path> pathGenerator() {
                return MCVanillaEngine.this.pathGenerator();
            }
            @Override protected TimeGrid timeGrid() {
                return MCVanillaEngine.this.timeGrid();
            }
        };
        this.simulation_.calculate(requiredTolerance_, requiredSamples_, maxSamples_);
        r.value = this.simulation_.sampleAccumulator().mean();
        r.errorEstimate = this.simulation_.errorEstimate();
    }
}
