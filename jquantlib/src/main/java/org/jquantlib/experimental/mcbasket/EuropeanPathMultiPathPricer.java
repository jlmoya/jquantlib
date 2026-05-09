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

import java.util.Arrays;
import java.util.List;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * European-style path pricer for multi-asset baskets.
 *
 * <p>Phase 4i scaffold port of C++ QuantLib v1.42.1
 * {@code ql/experimental/mcbasket/mcpathbasketengine.{hpp,cpp}}::
 * {@code EuropeanPathMultiPathPricer}.
 * Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Computes {@code DotProduct(payments, discounts)} for the path,
 * ignoring early exercise.
 *
 * <h3>Phase 4i carry-forward (Phase 4i.5)</h3>
 *
 * <p>Like {@link LongstaffSchwartzMultiPathPricer}, this is a partial port:
 * the {@link #op(Object)} body needs the still-missing {@code MultiPath}
 * type. The class is fully wired so that, once {@code MultiPath} lands in
 * {@code org.jquantlib.methods.montecarlo}, the body can be filled by
 * mirroring lines 36-65 of the referenced C++ source (build a path
 * matrix, call {@link PathPayoff#value(org.jquantlib.math.matrixutilities.Matrix,
 * java.util.List, Array, Array, java.util.List)}, then dot-product with
 * the discounts).
 */
public class EuropeanPathMultiPathPricer extends PathPricer<Object> {

    private final PathPayoff payoff_;
    private final int[] timePositions_;
    private final List<Handle<YieldTermStructure>> forwardTermStructures_;
    private final Array discounts_;

    public EuropeanPathMultiPathPricer(final PathPayoff payoff,
            final int[] timePositions,
            final List<Handle<YieldTermStructure>> forwardTermStructures,
            final Array discounts) {
        this.payoff_ = payoff;
        this.timePositions_ = Arrays.copyOf(timePositions, timePositions.length);
        this.forwardTermStructures_ = forwardTermStructures;
        this.discounts_ = discounts;
    }

    @Override
    public Double op(final Object multiPath) {
        // TODO Phase 4i.5: build the (numAssets, numTimes) path matrix from
        //                  the MultiPath, call payoff_.value(...), then
        //                  return DotProduct(values, discounts_).
        throw new UnsupportedOperationException(
                "EuropeanPathMultiPathPricer.op pending Phase 4i.5 (MultiPath)");
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

    public List<Handle<YieldTermStructure>> forwardTermStructures() {
        return forwardTermStructures_;
    }
}
