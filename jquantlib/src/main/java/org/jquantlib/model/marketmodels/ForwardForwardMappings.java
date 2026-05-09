/*
Copyright (C) 2026 Jose Moya

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
 Copyright (C) 2007 Mark Joshi
*/

package org.jquantlib.model.marketmodels;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;

/**
 * Utility functions for mapping between forward rates of varying tenor.
 *
 * <p>Java port of {@code ql/models/marketmodels/forwardforwardmappings.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * <p>All methods are static (C++ free functions in namespace
 * {@code ForwardForwardMappings}).
 *
 * <p>Phase 3h.5 A.7.
 */
public final class ForwardForwardMappings {

    /** Prevent instantiation — all methods are static. */
    private ForwardForwardMappings() {}

    /**
     * Returns the {@code dg[i]/df[j]} Jacobian between forward rates with tenor
     * {@code multiplier} and forward rates with tenor 1.
     *
     * <p>Precondition: {@code offset < multiplier}.
     * The result is a {@code k x n} matrix where {@code k = (n - offset) / multiplier}.
     *
     * @param cs         curve state providing rates and discount ratios
     * @param multiplier tenor multiplier (period length in number of short tenors)
     * @param offset     starting offset within the period (must be &lt; multiplier)
     * @return k&times;n Jacobian matrix
     */
    public static Matrix forwardForwardJacobian(final CurveState cs,
                                                final int multiplier,
                                                final int offset) {
        final int n = cs.numberOfRates();
        QL.require(offset < multiplier,
                "offset must be less than period in forward forward mappings");

        final int k = (n - offset) / multiplier;
        final double[] tau = cs.rateTaus();

        final Matrix jacobian = new Matrix(k, n);
        // initialise all entries to 0.0
        for (int r = 0; r < k; ++r)
            for (int c = 0; c < n; ++c)
                jacobian.set(r, c, 0.0);

        int m = offset;
        for (int l = 0; l < k; ++l) {
            final double df = cs.discountRatio(m, m + multiplier);
            final double bigTau = cs.rateTimes()[m + multiplier] - cs.rateTimes()[m];

            for (int r = 0; r < multiplier; ++r, ++m) {
                // C++: Real value = df * tau[m]*cs.discountRatio(m+1,m)-1;
                // discountRatio(m+1, m) = D[m+1]/D[m] = 1/(1+f[m]*tau[m])
                double value = df * tau[m] * cs.discountRatio(m + 1, m) - 1.0;
                value /= bigTau;
                jacobian.set(l, m, -value);
            }
        }

        return jacobian;
    }

    /**
     * Returns the Y matrix to switch base from forward rates with tenor 1 to
     * forward rates with tenor {@code multiplier}, incorporating displaced-
     * diffusion adjustments.
     *
     * <p>Precondition: {@code offset < multiplier},
     * {@code shortDisplacements.length == n},
     * {@code longDisplacements.length == k} where {@code k = (n - offset) / multiplier}.
     *
     * @param cs                 curve state
     * @param shortDisplacements displacement for short (unit-tenor) forward rates
     * @param longDisplacements  displacement for long (multiplier-tenor) forward rates
     * @param multiplier         tenor multiplier
     * @param offset             starting offset (must be &lt; multiplier)
     * @return k&times;n Y matrix
     */
    public static Matrix yMatrix(final CurveState cs,
                                 final double[] shortDisplacements,
                                 final double[] longDisplacements,
                                 final int multiplier,
                                 final int offset) {
        final int n = cs.numberOfRates();
        QL.require(offset < multiplier,
                "offset must be less than period in forward forward mappings");

        final int k = (n - offset) / multiplier;

        QL.require(shortDisplacements.length == n,
                "shortDisplacements must be of size equal to number of rates");
        QL.require(longDisplacements.length == k,
                "longDisplacements must be of size equal to (number of rates minus offset) divided by multiplier");

        final Matrix jacobian = forwardForwardJacobian(cs, multiplier, offset);

        for (int i = 0; i < k; ++i) {
            final double tau = cs.rateTimes()[(i + 1) * multiplier + offset]
                    - cs.rateTimes()[i * multiplier + offset];
            final double longForward =
                    (cs.discountRatio((i + 1) * multiplier + offset, i * multiplier + offset) - 1.0)
                            / tau;
            final double longForwardDisplaced = longForward + longDisplacements[i];

            for (int j = 0; j < n; ++j) {
                final double shortForward = cs.forwardRate(j);
                final double shortForwardDisplaced = shortForward + shortDisplacements[j];
                jacobian.set(i, j, jacobian.get(i, j) * shortForwardDisplaced / longForwardDisplaced);
            }
        }

        return jacobian;
    }

    /**
     * Restricts the given curve state to the periodic subset of times defined by
     * {@code multiplier} and {@code offset}, returning a new {@link LMMCurveState}
     * set on those discount ratios.
     *
     * <p>Precondition: {@code offset < multiplier}.
     * The returned state has {@code k = (n - offset) / multiplier} rates.
     *
     * @param cs         source curve state
     * @param multiplier period length
     * @param offset     starting offset (must be &lt; multiplier)
     * @return new LMMCurveState on the restricted time grid
     */
    public static LMMCurveState restrictCurveState(final CurveState cs,
                                                   final int multiplier,
                                                   final int offset) {
        final int n = cs.numberOfRates();
        QL.require(offset < multiplier,
                "offset must be less than period in forward forward mappings");

        final int k = (n - offset) / multiplier;

        final double[] times = new double[k + 1];
        final double[] discRatios = new double[k + 1];

        for (int i = 0; i <= k; ++i) {
            times[i] = cs.rateTimes()[i * multiplier + offset];
            discRatios[i] = cs.discountRatio(i * multiplier + offset, 0);
        }

        final LMMCurveState newState = new LMMCurveState(times);
        newState.setOnDiscountRatios(discRatios);
        return newState;
    }
}
