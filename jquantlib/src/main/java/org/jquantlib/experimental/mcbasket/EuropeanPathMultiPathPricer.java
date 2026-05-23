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
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.montecarlo.MultiPath;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * European-style path pricer for multi-asset baskets.
 *
 * <p>Phase 4i port of C++ QuantLib v1.42.1
 * {@code ql/experimental/mcbasket/mcpathbasketengine.{hpp,cpp}}:: {@code EuropeanPathMultiPathPricer}. Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Computes {@code DotProduct(payments, discounts)} for the path,
 * ignoring early exercise.
 *
 * <h3>Phase 4i.5 implementation (P3-B)</h3>
 *
 * <p>{@link #op(MultiPath)} now mirrors C++
 * {@code EuropeanPathMultiPathPricer::operator()} lines 33-65 of
 * {@code mcpathbasketengine.cpp}: build the (numAssets, numTimes) path matrix from the {@link MultiPath}, call
 * {@link PathPayoff#value(Matrix, List, Array, Array, List)}, then dot-product the values with {@code discounts_}.
 */
public class EuropeanPathMultiPathPricer extends PathPricer< MultiPath > {

    private final PathPayoff payoff_;
    private final int[] timePositions_;
    private final List< Handle< YieldTermStructure > > forwardTermStructures_;
    private final Array discounts_;

    public EuropeanPathMultiPathPricer(final PathPayoff payoff, final int[] timePositions,
            final List< Handle< YieldTermStructure > > forwardTermStructures, final Array discounts) {
        this.payoff_ = payoff;
        this.timePositions_ = Arrays.copyOf(timePositions, timePositions.length);
        this.forwardTermStructures_ = forwardTermStructures;
        this.discounts_ = discounts;
    }

    @Override
    public Double op(final MultiPath multiPath) {
        final int n = multiPath.pathSize();
        QL.require(n > 0, "the path cannot be empty");

        final int numberOfAssets = multiPath.assetNumber();
        QL.require(numberOfAssets > 0, "there must be some paths");

        final int numberOfTimes = timePositions_.length;

        // Mirrors C++ Matrix path(numberOfAssets, numberOfTimes, Null<Real>())
        // — sentinel-initialised; cells are immediately overwritten below.
        final Matrix path = new Matrix(numberOfAssets, numberOfTimes);

        for ( int i = 0; i < numberOfTimes; i++ ) {
            final int pos = timePositions_[i];
            for ( int j = 0; j < numberOfAssets; j++ ) {
                path.set(j, i, multiPath.get(j).get(pos));
            }
        }

        final Array values = new Array(numberOfTimes, 0.0, 0.0);

        // C++ passes default-constructed Array exercises and empty std::vector<Array> states;
        // PathPayoff::value treats them as "ignored" outputs.
        final Array exercises = new Array(0);
        final List< Array > states = new ArrayList<>();

        payoff_.value(path, forwardTermStructures_, values, exercises, states);

        // in this engine we ignore early exercise
        return values.dotProduct(discounts_);
    }

    public PathPayoff payoff() {
        return payoff_;
    }

    public int[] timePositions() {
        return Arrays.copyOf(timePositions_, timePositions_.length);
    }

    public Array discounts() {
        return discounts_;
    }

    public List< Handle< YieldTermStructure > > forwardTermStructures() {
        return forwardTermStructures_;
    }
}
