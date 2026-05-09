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
import org.jquantlib.processes.StochasticProcess;

/**
 * Longstaff-Schwartz Monte Carlo engine base for early-exercise basket
 * options.
 *
 * <p>Phase 4i scaffold port of C++ QuantLib v1.42.1
 * {@code ql/experimental/mcbasket/mclongstaffschwartzpathengine.hpp}.
 * Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The C++ class is templated on
 * {@code <GenericEngine, MC, RNG, S = Statistics>} and inherits from
 * {@code McSimulation<MC,RNG,S>}. The Java port collapses the {@code MC}
 * template parameter (only {@code MultiVariate} is used in v1.42.1) and
 * keeps the engine concrete on {@link PathMultiAssetOption.EngineImpl}.
 *
 * <h3>Phase 4i carry-forward (Phase 4i.5)</h3>
 *
 * <p>Subclasses must implement {@link #lsmPathPricer()}; the {@link #calculate()}
 * loop is a stub awaiting the multivariate {@code McSimulation},
 * {@code MultiPathGenerator}, and {@code MonteCarloModel<MultiVariate>}
 * dependencies.
 */
public abstract class MCLongstaffSchwartzPathEngine
        extends PathMultiAssetOption.EngineImpl {

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

    protected MCLongstaffSchwartzPathEngine(final StochasticProcess process,
            final int timeSteps, final int timeStepsPerYear,
            final boolean brownianBridge,
            final boolean antitheticVariate, final boolean controlVariate,
            final int requiredSamples, final double requiredTolerance,
            final int maxSamples, final long seed,
            final int nCalibrationSamples) {
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
                ? DEFAULT_CALIBRATION_SAMPLES : nCalibrationSamples;

        QL.require(timeSteps != Constants.NULL_INTEGER || timeStepsPerYear != Constants.NULL_INTEGER,
                "no time steps provided");
        QL.require(timeSteps == Constants.NULL_INTEGER || timeStepsPerYear == Constants.NULL_INTEGER,
                "both time steps and time steps per year were provided");
        QL.require(timeSteps != 0,
                "timeSteps must be positive, " + timeSteps + " not allowed");
        QL.require(timeStepsPerYear != 0,
                "timeStepsPerYear must be positive, "
                        + timeStepsPerYear + " not allowed");

        if (process_ != null) {
            process_.addObserver(this);
        }
    }

    /**
     * Subclass-supplied pricer factory. Mirrors C++ pure virtual
     * {@code lsmPathPricer()}.
     */
    protected abstract LongstaffSchwartzMultiPathPricer lsmPathPricer();

    @Override
    public void calculate() /* @ReadOnly */ {
        // TODO Phase 4i.5: replicate the C++ two-phase calibration/pricing
        //                  loop once the multivariate Monte-Carlo
        //                  infrastructure is available. Roughly:
        //
        //   pathPricer_ = lsmPathPricer();
        //   model = MonteCarloModel<MC,RNG,S>(pathGenerator, pathPricer_, ...);
        //   model.addSamples(nCalibrationSamples_);
        //   pathPricer_.calibrate();
        //   McSimulation::calculate(requiredTolerance_, requiredSamples_, maxSamples_);
        //   results_.value = model.sampleAccumulator().mean();
        //
        // See mclongstaffschwartzpathengine.hpp lines 132-154.
        throw new UnsupportedOperationException(
                "MCLongstaffSchwartzPathEngine.calculate pending Phase 4i.5 "
              + "(McSimulation<MultiVariate>, MonteCarloModel<MultiVariate>, "
              + "MultiPathGenerator)");
    }
}
