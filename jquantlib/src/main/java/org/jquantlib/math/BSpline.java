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
 Copyright (C) 2007 Allen Kuo
*/

package org.jquantlib.math;

import org.jquantlib.QL;

/**
 * B-spline basis functions.
 *
 * <p>Java port of {@code ql/math/bspline.{hpp,cpp}} (QuantLib v1.42.1).
 *
 * <p>Follows treatment and notation from:
 * Weisstein, Eric W. "B-Spline." From MathWorld--A Wolfram Web Resource.
 * <a href="http://mathworld.wolfram.com/B-Spline.html">mathworld.wolfram.com/B-Spline.html</a>.
 *
 * <p>{@code (p+1)}-th order B-spline (or p degree polynomial) basis functions
 * {@code N_{i,p}(x), i = 0,1,2 ... n}, with {@code n+1} control points, or
 * equivalently, an associated knot vector of size {@code p+n+2} defined at the
 * increasingly sorted points {@code (x_0, x_1 ... x_{n+p+1})}. A linear
 * B-spline has {@code p=1}, quadratic B-spline has {@code p=2}, a cubic
 * B-spline has {@code p=3}, etc.
 *
 * <p>The B-spline basis functions are defined recursively:
 * <pre>
 *   N_{i,0}(x) = 1   if x_i &lt;= x &lt; x_{i+1}
 *              = 0   otherwise
 *   N_{i,p}(x) = N_{i,p-1}(x) * (x - x_i) / (x_{i+p-1} - x_i)
 *              + N_{i+1,p-1}(x) * (x_{i+p} - x) / (x_{i+p} - x_{i+1})
 * </pre>
 *
 * <p>Phase 5e.5b-CFC-d-91.
 */
public final class BSpline {

    /** {@code p_=2} is a quadratic B-spline, {@code p_=3} is a cubic B-Spline, etc. */
    private final int p_;
    /** {@code n_ + 1} = "control points" = max number of basis functions. */
    private final int n_;
    private final double[] knots_;

    public BSpline(final int p, final int n, final double[] knots) {
        QL.require(p >= 1, "lowest degree B-spline has p = 1");
        QL.require(n >= 1, "number of control points n+1 >= 2");
        QL.require(p <= n, "must have p <= n");
        QL.require(knots.length == p + n + 2, "number of knots must equal p+n+2");

        for (int i = 0; i < knots.length - 1; ++i) {
            QL.require(knots[i] <= knots[i + 1], "knots points must be nondecreasing");
        }

        this.p_ = p;
        this.n_ = n;
        this.knots_ = knots.clone();
    }

    /**
     * Evaluate the {@code i}-th B-spline basis function at {@code x}.
     *
     * @param i basis-function index, must satisfy {@code 0 <= i <= n}
     * @param x evaluation point
     * @return value of {@code N_{i,p}(x)}
     */
    public double valueAt(final int i, final double x) {
        QL.require(i <= n_, "i must not be greater than n");
        return N(i, p_, x);
    }

    /** Recursive definition of N, the B-spline basis function. */
    private double N(final int i, final int p, final double x) {
        if (p == 0) {
            return (knots_[i] <= x && x < knots_[i + 1]) ? 1.0 : 0.0;
        } else {
            return ((x - knots_[i]) / (knots_[i + p] - knots_[i])) * N(i, p - 1, x)
                 + ((knots_[i + p + 1] - x) / (knots_[i + p + 1] - knots_[i + 1])) * N(i + 1, p - 1, x);
        }
    }
}
