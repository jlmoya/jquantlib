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
 Copyright (C) 2007 StatPro Italia srl
 Copyright (C) 2015, 2016 Peter Caspers
 Copyright (C) 2015 Thema Consulting SA
*/

package org.jquantlib.pricingengines;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.math.statistics.Statistics;
import org.jquantlib.methods.montecarlo.LongstaffSchwartzPathPricer;
import org.jquantlib.methods.montecarlo.MonteCarloModel;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathGenerator;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.pricingengines.vanilla.MCVanillaEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Longstaff-Schwartz Monte Carlo engine for early-exercise options.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/mclongstaffschwartzengine.hpp} (Phase 5h.5-MC-AME WI-4).
 * Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>References: Francis Longstaff, Eduardo Schwartz, 2001.
 * <i>Valuing American Options by Simulation: A Simple Least-Squares
 * Approach</i>, Review of Financial Studies 14(1), 113-147.
 *
 * <p>The C++ template uses multiple inheritance ({@code GenericEngine} +
 * {@code McSimulation<MC,RNG,S>}). This Java port extends
 * {@link MCVanillaEngine} (so the OneAssetOption / arguments_ / results_
 * wiring is intact) and adds a calibration phase ahead of the standard
 * {@link McSimulation#calculate} pricing loop.
 *
 * <p>Specialised to single-variate {@code (MC = SingleVariate)} and
 * {@code RNG = PseudoRandom} (Mersenne-Twister + InverseCumulativeNormal);
 * lifting that restriction is a Phase 5h.5-MC-AME-b carry-forward.
 *
 * @author JQuantLib
 */
public abstract class MCLongstaffSchwartzEngine extends MCVanillaEngine {

    //
    // protected fields (mirror C++ exactly)
    //

    protected final int nCalibrationSamples_;
    protected final boolean brownianBridgeCalibration_;
    protected final boolean antitheticVariateCalibration_;
    protected final long seedCalibration_;

    /** Lazy-built shared LSM path pricer (built once per calculate() call). */
    protected LongstaffSchwartzPathPricer<Path, Double> pathPricer_;
    /** Calibration-phase MC model. */
    protected MonteCarloModel<Path> mcModelCalibration_;


    //
    // constructor
    //

    /**
     * Mirrors C++ {@code MCLongstaffSchwartzEngine(process, timeSteps,
     * timeStepsPerYear, brownianBridge, antitheticVariate, controlVariate,
     * requiredSamples, requiredTolerance, maxSamples, seed,
     * nCalibrationSamples, brownianBridgeCalibration,
     * antitheticVariateCalibration, seedCalibration)}.
     *
     * <p>{@code nCalibrationSamples == NULL_SAMPLES} → use the C++ default 2048.
     * {@code seedCalibration == NULL_SAMPLES} → derive from {@code seed}
     * (0 if {@code seed == 0} else {@code seed + 1768237423L} per C++).
     */
    protected MCLongstaffSchwartzEngine(
            final GeneralizedBlackScholesProcess process,
            final int timeSteps,
            final int timeStepsPerYear,
            final boolean brownianBridge,
            final boolean antitheticVariate,
            final boolean controlVariate,
            final int requiredSamples,
            final double requiredTolerance,
            final int maxSamples,
            final long seed,
            final int nCalibrationSamples,
            final Boolean brownianBridgeCalibration,
            final Boolean antitheticVariateCalibration,
            final long seedCalibration) {
        super(process, timeSteps, timeStepsPerYear,
                brownianBridge, antitheticVariate, controlVariate,
                requiredSamples, requiredTolerance, maxSamples, seed);

        this.nCalibrationSamples_ = (nCalibrationSamples == McSimulation.NULL_SAMPLES)
                ? 2048
                : nCalibrationSamples;
        this.brownianBridgeCalibration_ = (brownianBridgeCalibration != null)
                ? brownianBridgeCalibration.booleanValue()
                : brownianBridge;
        this.antitheticVariateCalibration_ = (antitheticVariateCalibration != null)
                ? antitheticVariateCalibration.booleanValue()
                : antitheticVariate;
        this.seedCalibration_ = (seedCalibration != McSimulation.NULL_SAMPLES)
                ? seedCalibration
                : (seed == 0 ? 0 : seed + 1768237423L);
    }


    //
    // hooks for subclasses
    //

    /**
     * Subclasses build the LSM path pricer (with its calibration
     * book-keeping). Mirrors C++
     * {@code virtual ext::shared_ptr<LongstaffSchwartzPathPricer<path_type>>
     *        lsmPathPricer() const = 0}.
     */
    protected abstract LongstaffSchwartzPathPricer<Path, Double> lsmPathPricer();


    //
    // McSimulation-shaped helpers
    //

    /**
     * Mirrors C++ {@code TimeGrid timeGrid()}: insert the option exercise
     * dates into the time grid (American → just the last date; Bermudan →
     * each interior date).
     */
    @Override
    protected TimeGrid timeGrid() {
        final OneAssetOption.ArgumentsImpl a =
                (OneAssetOption.ArgumentsImpl) arguments_;
        final Exercise exercise = a.exercise;
        final List<Double> requiredTimes = new ArrayList<Double>();
        if (exercise.type() == Exercise.Type.American) {
            final Date last = exercise.lastDate();
            requiredTimes.add(process_.time(last));
        } else {
            for (int i = 0; i < exercise.dates().size(); ++i) {
                final double t = process_.time(exercise.date(i));
                if (t > 0.0) {
                    requiredTimes.add(t);
                }
            }
        }
        if (timeSteps_ != McSimulation.NULL_SAMPLES) {
            return buildTimeGrid(requiredTimes, timeSteps_);
        } else if (timeStepsPerYear_ != McSimulation.NULL_SAMPLES) {
            final double back = requiredTimes.get(requiredTimes.size() - 1);
            final int steps = (int) (timeStepsPerYear_ * back);
            return buildTimeGrid(requiredTimes, Math.max(steps, 1));
        } else {
            throw new RuntimeException("time steps not specified");
        }
    }

    /**
     * Builds a TimeGrid from required-times + steps. The TimeGrid in the
     * code-base supports two patterns: a single end-time/steps and a
     * mandatory-times-list constructor. Use the second form to honour
     * Bermudan exercise dates.
     */
    private TimeGrid buildTimeGrid(final List<Double> requiredTimes, final int steps) {
        if (requiredTimes.size() == 1) {
            return new TimeGrid(requiredTimes.get(0), steps);
        }
        return new TimeGrid(requiredTimes, steps);
    }

    /**
     * Build a Gaussian-driven {@link PathGenerator} for the calibration
     * stage. Mirrors C++ {@code pathGeneratorCalibration} construction in
     * {@link #calculate()}.
     */
    protected MonteCarloModel.PathGeneratorAdapter<Path> pathGeneratorCalibration() {
        final TimeGrid grid = timeGrid();
        final int dimensions = process_.factors() * (grid.size() - 1);
        final RandomSequenceGenerator<MersenneTwisterUniformRng> uniformRsg =
                new RandomSequenceGenerator<MersenneTwisterUniformRng>(
                        MersenneTwisterUniformRng.class, dimensions, seedCalibration_);
        final InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                InverseCumulativeNormal> gsg =
                new InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                        InverseCumulativeNormal>(uniformRsg, new InverseCumulativeNormal());
        final PathGenerator<InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                InverseCumulativeNormal>> gen =
                new PathGenerator<InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                        InverseCumulativeNormal>>(process_, grid, gsg, brownianBridgeCalibration_);
        return new MonteCarloModel.PathGeneratorAdapterImpl(gen);
    }

    /**
     * The shared LSM path pricer is built once per calculate() call (in
     * {@link #calculate}) and re-used for both calibration and pricing
     * phases.
     */
    @Override
    protected PathPricer<Path> pathPricer() {
        QL.require(pathPricer_ != null, "path pricer unknown");
        return pathPricer_;
    }


    //
    // PricingEngine
    //

    /**
     * Mirrors C++ {@code MCLongstaffSchwartzEngine::calculate()}. Three
     * phases:
     * <ol>
     *   <li>build the LSM path pricer (in calibration phase);</li>
     *   <li>run the calibration MC model with N samples; call
     *       {@code pathPricer_.calibrate()} which solves the regression;</li>
     *   <li>delegate to the standard {@link McSimulation#calculate} for
     *       the pricing phase.</li>
     * </ol>
     */
    @Override
    public void calculate() /* @ReadOnly */ {
        final OneAssetOption.ResultsImpl r = (OneAssetOption.ResultsImpl) results_;

        // 1) calibration: build pathPricer_ in calibration-phase, drive it
        //    with N samples via a dedicated MC model.
        this.pathPricer_ = lsmPathPricer();

        final MonteCarloModel.PathGeneratorAdapter<Path> calibGen = pathGeneratorCalibration();
        this.mcModelCalibration_ = new MonteCarloModel<Path>(
                calibGen, this.pathPricer_, new Statistics(),
                antitheticVariateCalibration_);
        this.mcModelCalibration_.addSamples(nCalibrationSamples_);
        this.pathPricer_.calibrate();

        // 2) pricing: build the pricing simulation. pathPricer() now
        //    returns the calibrated LSM pricer (pricing phase).
        this.simulation_ = new McSimulation<Path>(antitheticVariate_, controlVariate_) {
            @Override protected PathPricer<Path> pathPricer() {
                return MCLongstaffSchwartzEngine.this.pathPricer();
            }
            @Override protected MonteCarloModel.PathGeneratorAdapter<Path> pathGenerator() {
                return MCLongstaffSchwartzEngine.this.pathGenerator();
            }
            @Override protected TimeGrid timeGrid() {
                return MCLongstaffSchwartzEngine.this.timeGrid();
            }
            @Override protected PathPricer<Path> controlPathPricer() {
                return MCLongstaffSchwartzEngine.this.controlPathPricer();
            }
            @Override protected double controlVariateValue() {
                return MCLongstaffSchwartzEngine.this.controlVariateValue();
            }
        };
        this.simulation_.calculate(requiredTolerance_, requiredSamples_, maxSamples_);
        r.value = this.simulation_.sampleAccumulator().mean();
        r.errorEstimate = this.simulation_.errorEstimate();

        // exerciseProbability is captured in the LSM pricer
        // (additionalResults map in C++; kept for diagnostics here).
    }

    /** Override in subclasses that supply a control-variate pricer. */
    protected PathPricer<Path> controlPathPricer() {
        return null;
    }

    /** Override in subclasses that supply a control-variate value. */
    protected double controlVariateValue() {
        return Double.NaN;
    }
}
