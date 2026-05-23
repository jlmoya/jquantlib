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
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.model.shortrate.StochasticProcessArray;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.ImpliedTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Least-square Monte Carlo engine for American-style multi-asset path options.
 *
 * <p>Java port of C++ QuantLib v1.42.1
 * {@code ql/experimental/mcbasket/mcamericanpathengine.hpp}. Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Per the C++ docstring: "This method is intrinsically weak for
 * out-of-the-money options."
 *
 * <p>The constructor wires up all named parameters and the
 * {@link #lsmPathPricer()} factory builds a {@link LongstaffSchwartzMultiPathPricer} with the canonical
 * {@code (polynomialOrder = 2, polynomialType = Monomial)} defaults. The full {@link #calculate()} dispatch is
 * inherited from {@link MCLongstaffSchwartzPathEngine}.
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
        // Mirrors C++ {@code MCAmericanPathEngine<RNG>::lsmPathPricer()} lines 114-161 of
        // {@code mcamericanpathengine.hpp}.
        QL.require(processArray_ != null && processArray_.size() > 0, "Stochastic process array required");

        final StochasticProcess1D first = processArray_.process(0);
        if (!(first instanceof GeneralizedBlackScholesProcess process)) {
            throw new RuntimeException("generalized Black-Scholes process required");
        }

        final TimeGrid theTimeGrid = this.timeGrid();
        final Array times = theTimeGrid.mandatoryTimes();
        final int numberOfTimes = times.size();
        final List< Date > fixings = arguments_.fixingDates;
        QL.require(fixings.size() == numberOfTimes, "Invalid dates/times");

        final int[] timePositions = new int[numberOfTimes];
        final double[] discountFactorsArr = new double[numberOfTimes];
        final List< Handle< YieldTermStructure > > forwardTermStructures = new ArrayList<>(numberOfTimes);

        final Handle< YieldTermStructure > riskFreeRate = process.riskFreeRate();
        for ( int i = 0; i < numberOfTimes; i++ ) {
            final double t = times.get(i);
            timePositions[i] = theTimeGrid.index(t);
            discountFactorsArr[i] = riskFreeRate.currentLink().discount(t);
            forwardTermStructures.add(new Handle< YieldTermStructure >(
                    new ImpliedTermStructure< YieldTermStructure >(riskFreeRate, fixings.get(i))));
        }
        final Array discountFactors = new Array(discountFactorsArr);

        final int polynomialOrder = 2;
        final LongstaffSchwartzMultiPathPricer.PolynomialType polynomialType =
                LongstaffSchwartzMultiPathPricer.PolynomialType.Monomial;

        return new LongstaffSchwartzMultiPathPricer(arguments_.payoff, timePositions, forwardTermStructures,
                discountFactors, polynomialOrder, polynomialType);
    }

    public StochasticProcessArray processArray() {
        return processArray_;
    }
}
