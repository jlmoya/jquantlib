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
import org.jquantlib.model.shortrate.StochasticProcessArray;

/**
 * Monte Carlo engine for path-dependent basket options.
 *
 * <p>Phase 4i scaffold port of C++ QuantLib v1.42.1
 * {@code ql/experimental/mcbasket/mcpathbasketengine.{hpp,cpp}}:: {@code MCPathBasketEngine}. Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The C++ engine is templated on
 * {@code <RNG = PseudoRandom, S = Statistics>} and inherits from {@code McSimulation<MultiVariate,RNG,S>}. The Java
 * port stores all the named-parameter knobs but defers the actual MC loop to Phase 4i.5, because it depends on
 * {@code McSimulation<MultiVariate, ...>}, {@code MultiPathGenerator}, and {@code MultiPath} which are not yet
 * available in the Java codebase.
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

    @Override
    public void calculate() /* @ReadOnly */ {
        // TODO Phase 4i.5: invoke McSimulation<MultiVariate>.calculate() on a
        //                  path generator built from process_. The Java
        //                  codebase does not yet ship a multivariate
        //                  McSimulation; mirror lines 62-70 of
        //                  mcpathbasketengine.hpp once it does.
        throw new UnsupportedOperationException("MCPathBasketEngine.calculate pending Phase 4i.5 "
                + "(McSimulation<MultiVariate>, MultiPathGenerator, MultiPath)");
    }
}
