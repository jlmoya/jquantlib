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

import org.jquantlib.model.shortrate.StochasticProcessArray;

/**
 * Least-square Monte Carlo engine for American-style multi-asset path options.
 *
 * <p>Phase 4i scaffold port of C++ QuantLib v1.42.1
 * {@code ql/experimental/mcbasket/mcamericanpathengine.hpp}. Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Per the C++ docstring: "This method is intrinsically weak for
 * out-of-the-money options."
 *
 * <h3>Phase 4i carry-forward (Phase 4i.5)</h3>
 *
 * <p>The constructor wires up all named parameters and the
 * {@link #lsmPathPricer()} factory builds a {@link LongstaffSchwartzMultiPathPricer} with the canonical
 * {@code (polynomialOrder = 2, polynomialType = Monomial)} defaults. The full {@link #calculate()} dispatch is
 * inherited (and currently stubbed) from {@link MCLongstaffSchwartzPathEngine}.
 */
public class MCAmericanPathEngine extends MCLongstaffSchwartzPathEngine {

    private final StochasticProcessArray processArray_;

    public MCAmericanPathEngine(final StochasticProcessArray processes, final int timeSteps, final int timeStepsPerYear,
            final boolean brownianBridge, final boolean antitheticVariate, final boolean controlVariate,
            final int requiredSamples, final double requiredTolerance, final int maxSamples, final long seed,
            final int nCalibrationSamples) {
        super(processes, timeSteps, timeStepsPerYear, brownianBridge, antitheticVariate, controlVariate,
                requiredSamples, requiredTolerance, maxSamples, seed, nCalibrationSamples);
        this.processArray_ = processes;
    }

    @Override
    protected LongstaffSchwartzMultiPathPricer lsmPathPricer() {
        // TODO Phase 4i.5: build the LongstaffSchwartzMultiPathPricer using
        //                  - timeGrid() (depends on McSimulation<MultiVariate>),
        //                  - the GeneralizedBlackScholesProcess underlying
        //                    process(0) of processArray_,
        //                  - the discount factors and ImpliedTermStructure
        //                    handles per fixing date,
        //                  - polynomialOrder = 2,
        //                  - PolynomialType.Monomial.
        // See mcamericanpathengine.hpp lines 114-161.
        throw new UnsupportedOperationException("MCAmericanPathEngine.lsmPathPricer pending Phase 4i.5 "
                + "(timeGrid, ImpliedTermStructure handles per fixing date)");
    }

    public StochasticProcessArray processArray() {
        return processArray_;
    }
}
