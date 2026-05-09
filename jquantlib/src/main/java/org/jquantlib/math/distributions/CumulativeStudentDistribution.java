/*
 Copyright (C) 2008 Roland Lichters
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

package org.jquantlib.math.distributions;

import org.jquantlib.QL;
import org.jquantlib.math.Beta;
import org.jquantlib.math.Ops;

/**
 * Cumulative Student t-distribution.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::CumulativeStudentDistribution}
 * ({@code ql/math/distributions/studenttdistribution.{hpp,cpp}}).
 *
 * <p>Cumulative distribution function for {@code n} degrees of freedom:
 * <pre>
 *   F(x) = 1/2 + 1/2 * sgn(x) * [ I(1, n/2, 1/2) - I(n/(n+y^2), n/2, 1/2) ]
 * </pre>
 * where {@code I(z; a, b)} is the regularised incomplete beta function.
 *
 * <p>The C++ implementation passes the regularisation arg as the third parameter
 * to {@code incompleteBetaFunction(a, b, x)}, matching QuantLib's signature.
 * Java {@link Beta#incompleteBetaFunction(double, double, double, double, Integer)}
 * mirrors that call signature with explicit accuracy and iteration overrides.
 */
public class CumulativeStudentDistribution implements Ops.DoubleOp {

    private static final double ACCURACY = 1.0e-16;
    private static final int MAX_ITER = 100;

    private final int n_;

    public CumulativeStudentDistribution(final int n) {
        QL.require(n > 0, "invalid parameter for t-distribution");
        this.n_ = n;
    }

    @Override
    public double op(final double x) {
        final double xx = 1.0 * n_ / (x * x + n_);
        final double sig = (x > 0 ? 1.0 : -1.0);
        final double full = Beta.incompleteBetaFunction(0.5 * n_, 0.5, 1.0, ACCURACY, MAX_ITER);
        final double partial = Beta.incompleteBetaFunction(0.5 * n_, 0.5, xx, ACCURACY, MAX_ITER);
        return 0.5 + 0.5 * sig * (full - partial);
    }
}
