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
 Copyright (C) 2009 Andrea Odetti

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.mcbasket;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.math.statistics.Statistics;
import org.jquantlib.methods.montecarlo.MonteCarloModel;
import org.jquantlib.methods.montecarlo.MultiPath;
import org.jquantlib.methods.montecarlo.MultiPathGenerator;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.processes.StochasticProcess;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Longstaff-Schwartz Monte Carlo engine base for early-exercise basket options.
 *
 * <p>Java port of C++ QuantLib v1.42.1
 * {@code ql/experimental/mcbasket/mclongstaffschwartzpathengine.hpp}. Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The C++ class is templated on
 * {@code <GenericEngine, MC, RNG, S = Statistics>} and inherits from {@code McSimulation<MC,RNG,S>}. The Java port
 * collapses the {@code MC} template parameter (only {@code MultiVariate} is used in v1.42.1), specialises {@code RNG}
 * to {@code PseudoRandom} (Mersenne-Twister + InverseCumulativeNormal), and embeds an
 * {@link McSimulation McSimulation&lt;MultiPath&gt;} delegate via composition (mirroring the pattern set by
 * {@link org.jquantlib.pricingengines.basket.MCAmericanBasketEngine}).
 *
 * <p>Subclasses supply {@link #lsmPathPricer()} which is invoked during the
 * calibration phase; {@link #calculate()} then runs the standard pricing MC loop after the pricer has been calibrated.
 */
public abstract class MCLongstaffSchwartzPathEngine extends PathMultiAssetOption.EngineImpl {

    private static final int DEFAULT_CALIBRATION_SAMPLES = 2048;

    protected final StochasticProcess process_;
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

    protected MCLongstaffSchwartzPathEngine(final StochasticProcess process, final int timeSteps,
            final int timeStepsPerYear, final boolean brownianBridge, final boolean antitheticVariate,
            final boolean controlVariate, final int requiredSamples, final double requiredTolerance,
            final int maxSamples, final long seed, final int nCalibrationSamples) {
        super();
        this.process_ = process;
        this.timeSteps_ = timeSteps;
        this.timeStepsPerYear_ = timeStepsPerYear;
        this.brownianBridge_ = brownianBridge;
        this.antitheticVariate_ = antitheticVariate;
        this.controlVariate_ = controlVariate;
        this.requiredSamples_ = requiredSamples;
        this.requiredTolerance_ = requiredTolerance;
        this.maxSamples_ = maxSamples;
        this.seed_ = seed;
        this.nCalibrationSamples_ = (nCalibrationSamples == Constants.NULL_INTEGER)
                ? DEFAULT_CALIBRATION_SAMPLES
                : nCalibrationSamples;

        QL.require(timeSteps != Constants.NULL_INTEGER || timeStepsPerYear != Constants.NULL_INTEGER,
                "no time steps provided");
        QL.require(timeSteps == Constants.NULL_INTEGER || timeStepsPerYear == Constants.NULL_INTEGER,
                "both time steps and time steps per year were provided");
        QL.require(timeSteps != 0, "timeSteps must be positive, " + timeSteps + " not allowed");
        QL.require(timeStepsPerYear != 0, "timeStepsPerYear must be positive, " + timeStepsPerYear + " not allowed");

        if ( process_ != null ) {
            process_.addObserver(this);
        }
    }

    /**
     * Subclass-supplied pricer factory. Mirrors C++ pure virtual {@code lsmPathPricer()}.
     */
    protected abstract LongstaffSchwartzMultiPathPricer lsmPathPricer();

    //
    // read-only accessors for the configured Monte-Carlo parameters
    // (used by builder cross-validation; no behavioural effect)
    //

    public int timeSteps() {
        return timeSteps_;
    }

    public int timeStepsPerYear() {
        return timeStepsPerYear_;
    }

    public boolean brownianBridge() {
        return brownianBridge_;
    }

    public boolean antitheticVariate() {
        return antitheticVariate_;
    }

    public boolean controlVariate() {
        return controlVariate_;
    }

    public int requiredSamples() {
        return requiredSamples_;
    }

    public double requiredTolerance() {
        return requiredTolerance_;
    }

    public int maxSamples() {
        return maxSamples_;
    }

    public long seed() {
        return seed_;
    }

    public int calibrationSamples() {
        return nCalibrationSamples_;
    }

    //
    // McSimulation-shaped helpers
    //

    /** Mirrors C++ {@code TimeGrid timeGrid()} lines 159-173 of {@code mclongstaffschwartzpathengine.hpp}. */
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

    /** Build a Gaussian-driven {@link MultiPathGenerator}; mirrors C++ {@code pathGenerator()} lines 175-190. */
    protected MonteCarloModel.PathGeneratorAdapter< MultiPath > pathGenerator() {
        final int dimensions = process_.factors();
        final TimeGrid grid = timeGrid();
        final RandomSequenceGenerator< MersenneTwisterUniformRng > uniformRsg =
                new RandomSequenceGenerator< MersenneTwisterUniformRng >(MersenneTwisterUniformRng.class,
                        dimensions * (grid.size() - 1), seed_);
        final InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > gsg =
                new InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal >(
                        uniformRsg, new InverseCumulativeNormal());
        final MultiPathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > > gen =
                new MultiPathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > >(
                        process_, grid, gsg, brownianBridge_);
        return new MonteCarloModel.MultiPathGeneratorAdapterImpl(gen);
    }

    //
    // PricingEngine
    //

    /**
     * Mirrors C++ {@code MCLongstaffSchwartzPathEngine::calculate()} lines 132-154 of
     * {@code mclongstaffschwartzpathengine.hpp}: build the pricer in calibration mode, drive it through one MC pass,
     * call {@link LongstaffSchwartzMultiPathPricer#calibrate()}, then run the standard pricing MC and write the mean
     * / error to results.
     */
    @Override
    public void calculate() /* @ReadOnly */ {
        // 1) Calibration phase: build a pricer in calibration mode and drive
        //    it with nCalibrationSamples samples.
        this.pathPricer_ = lsmPathPricer();
        final MonteCarloModel< MultiPath > mcModelCalibration = new MonteCarloModel< MultiPath >(pathGenerator(),
                this.pathPricer_, new Statistics(), antitheticVariate_);
        mcModelCalibration.addSamples(nCalibrationSamples_);
        this.pathPricer_.calibrate();

        // 2) Pricing phase: standard McSimulation drives the now-calibrated
        //    pricer to produce mean + error.
        final LongstaffSchwartzMultiPathPricer pricer = this.pathPricer_;
        this.simulation_ = new McSimulation< MultiPath >(antitheticVariate_, controlVariate_) {
            @Override
            protected PathPricer< MultiPath > pathPricer() {
                return pricer;
            }

            @Override
            protected MonteCarloModel.PathGeneratorAdapter< MultiPath > pathGenerator() {
                return MCLongstaffSchwartzPathEngine.this.pathGenerator();
            }

            @Override
            protected TimeGrid timeGrid() {
                return MCLongstaffSchwartzPathEngine.this.timeGrid();
            }
        };
        this.simulation_.calculate(requiredTolerance_, requiredSamples_, maxSamples_);
        results_.value = this.simulation_.sampleAccumulator().mean();
        results_.errorEstimate = this.simulation_.errorEstimate();
    }
}
