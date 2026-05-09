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
import org.jquantlib.math.Ops;

/**
 * Student t-distribution density.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::StudentDistribution}
 * ({@code ql/math/distributions/studenttdistribution.{hpp,cpp}}).
 *
 * <p>Probability density function for {@code n} degrees of freedom:
 * <pre>
 *   f(x) = Gamma((n+1)/2) / ( sqrt(n*pi) * Gamma(n/2) )
 *          * 1 / (1 + x^2/n)^((n+1)/2)
 * </pre>
 *
 * <p>Phase 4m.6 prereq for {@link org.jquantlib.experimental.math.TCopulaPolicy}
 * and {@link org.jquantlib.experimental.credit.OneFactorStudentCopula} family.
 */
public class StudentDistribution implements Ops.DoubleOp {

    private static final GammaFunction G = new GammaFunction();

    private final int n_;

    public StudentDistribution(final int n) {
        QL.require(n > 0, "invalid parameter for t-distribution");
        this.n_ = n;
    }

    @Override
    public double op(final double x) {
        final double g1 = Math.exp(G.logValue(0.5 * (n_ + 1)));
        final double g2 = Math.exp(G.logValue(0.5 * n_));
        final double power = Math.pow(1.0 + x * x / n_, 0.5 * (n_ + 1));
        return g1 / (g2 * power * Math.sqrt(Math.PI * n_));
    }
}
