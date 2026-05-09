/*
 Copyright (C) 2015 Peter Caspers
 Copyright (C) 2026 JQuantLib migration contributors.

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

package org.jquantlib.experimental.math;

/**
 * Piecewise constant function helper.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/math/piecewisefunction.hpp}.
 *
 * <p>Defines a piecewise constant, RCLL function which takes the values
 * {@code Y[0], Y[1], ... Y[n]} on the intervals
 * {@code (-inf, X[0]), [X[1], X[2]), ..., [X[n-1], inf)}.
 *
 * <p>Normally {@code Y.length} should be {@code X.length + 1}. If more values
 * for {@code Y} are given, they are ignored. If fewer values are given, the
 * last given value is reused for the remaining intervals.
 *
 * <p>If {@code X.length == 0} a constant function returning {@code Y[0]} is
 * evaluated.
 *
 * <p>The C++ source ships this as a {@code QL_PIECEWISE_FUNCTION} macro;
 * this Java translation exposes it as a static method.
 */
public final class PiecewiseFunction {

    private PiecewiseFunction() {
    }

    /**
     * Evaluate the piecewise function at {@code x}.
     *
     * @param X strictly increasing vector of breakpoints (may be empty)
     * @param Y values vector ({@code Y.length &gt;= 1})
     * @param x argument
     */
    public static double eval(final double[] X, final double[] Y, final double x) {
        // upper_bound returns first element greater than x
        final int idx = upperBound(X, x);
        return Y[Math.min(idx, Y.length - 1)];
    }

    /** Returns the first index in {@code X} whose value is &gt; {@code x}. */
    private static int upperBound(final double[] X, final double x) {
        int lo = 0;
        int hi = X.length;
        while (lo < hi) {
            final int mid = (lo + hi) >>> 1;
            if (X[mid] <= x) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
