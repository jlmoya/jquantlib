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
 Copyright (C) 2011, 2012 Klaus Spanderen
 */
package org.jquantlib.experimental.finitedifferences;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;

/**
 * VPP step condition with optional total-starts limit.
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * {@code ql/experimental/finitedifferences/fdmvppstartlimitstepcondition.{hpp,cpp}}.</p>
 *
 * <p>The state-axis cardinality is {@code (2*tMinUp + tMinDown) * (1
 * if nStarts == NULL else nStarts+1)}. The dynamic-programming sweep at
 * each call to {@link #changeState(double, Array, double)} picks the
 * better of "continue current trajectory" vs "start a new cycle"
 * (subject to the start-count budget if applicable).</p>
 *
 * @author Phase 5e.5b-CFC-d-287 port
 */
public class FdmVPPStartLimitStepCondition extends FdmVPPStepCondition {

    /** Sentinel for "unlimited starts" — matches {@link VanillaVPPOption#NULL_INT}. */
    public static final int NULL_INT = VanillaVPPOption.NULL_INT;

    private final int nStarts_;

    public FdmVPPStartLimitStepCondition(final Params params,
                                         final int nStarts,
                                         final Mesher mesh,
                                         final FdmInnerValueCalculator gasPrice,
                                         final FdmInnerValueCalculator sparkSpreadPrice) {
        super(params,
              nStates(params.tMinUp, params.tMinDown, nStarts),
              mesh, gasPrice, sparkSpreadPrice);
        this.nStarts_ = nStarts;
        QL.require(tMinUp_ > 0,   "minimum up time must be greater than one");
        QL.require(tMinDown_ > 0, "minimum down time must be greater than one");
    }

    /**
     * Total number of states for given up/down/start counts. Mirrors C++
     * {@code FdmVPPStartLimitStepCondition::nStates(tMinUp, tMinDown,
     * nStarts)}.
     */
    public static int nStates(final int tMinUp, final int tMinDown,
                              final int nStarts) {
        return (2 * tMinUp + tMinDown)
                * ((nStarts == NULL_INT) ? 1 : nStarts + 1);
    }

    @Override
    public double maxValue(final Array states) {
        double m = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < states.size(); ++i) {
            if (states.get(i) > m) {
                m = states.get(i);
            }
        }
        return m;
    }

    @Override
    protected Array changeState(final double gasPrice,
                                final Array state, final double t) {
        final double startUpCost
                = startUpFixCost_ + (gasPrice + fuelCostAddon_) * startUpFuel_;

        final Array retVal = new Array(state.size());
        final int sss = 2 * tMinUp_ + tMinDown_;

        for (int i = 0; i < nStates_; ++i) {
            final int j = i % sss;

            if (j < tMinUp_ - 1) {
                retVal.set(i, Math.max(state.get(i + 1),
                                        state.get(tMinUp_ + i + 1)));
            } else if (j == tMinUp_ - 1) {
                final double a = state.get(i + tMinUp_ + 1);
                final double b = state.get(i);
                final double c = state.get(i + tMinUp_);
                retVal.set(i, Math.max(a, Math.max(b, c)));
            } else if (j < 2 * tMinUp_) {
                retVal.set(i, retVal.get(i - tMinUp_));
            } else if (j < 2 * tMinUp_ + tMinDown_ - 1) {
                retVal.set(i, state.get(i + 1));
            } else if (nStarts_ == NULL_INT) {
                retVal.set(i, Math.max(state.get(i),
                        Math.max(state.get(0),
                                  state.get(tMinUp_)) - startUpCost));
            } else if (i >= sss) {
                final double bestFromPrevCycle = Math.max(
                        state.get(i + 1 - 2 * sss),
                        state.get(i + 1 - 2 * sss + tMinUp_));
                retVal.set(i,
                        Math.max(state.get(i), bestFromPrevCycle - startUpCost));
            } else {
                retVal.set(i, state.get(i));
            }
        }
        return retVal;
    }
}
