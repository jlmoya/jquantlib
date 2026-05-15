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
 Copyright (C) 2002, 2003 Sadruddin Rejeb
 Copyright (C) 2007 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.math.distributions;

import org.jquantlib.math.Ops;

/**
 * Sankaran approximation for the non-central chi-squared cumulative
 * distribution function.
 * <p>
 * Java port of {@code QuantLib::NonCentralCumulativeChiSquareSankaranApprox}
 * (v1.42.1, {@code ql/math/distributions/chisquaredistribution.{hpp,cpp}}).
 * Provides a closed-form normal-CDF based approximation to the non-central
 * chi-squared CDF, accurate to roughly 1e-2 for typical parameter ranges
 * (cf. C++ test {@code testSankaranApproximation} which targets a 0.01
 * tolerance vs. the exact AS-275 series).
 * <p>
 * The class name uses the Java convention "ChiSquared" (rather than C++'s
 * "ChiSquare") to align with {@link NonCentralChiSquaredDistribution} and
 * {@link NonCentralCumulativeChiSquaredDistribution}.
 */
public class NonCentralCumulativeChiSquaredSankaranApprox implements Ops.DoubleOp {

    private final double df_;
    private final double ncp_;

    public NonCentralCumulativeChiSquaredSankaranApprox(final double df, final double ncp) {
        this.df_ = df;
        this.ncp_ = ncp;
    }

    @Override
    public double op(final double x) {
        // Direct port of the body of
        //   QuantLib::NonCentralCumulativeChiSquareSankaranApprox::operator()(Real x)
        // in v1.42.1 chisquaredistribution.cpp:93-103.
        final double dfPlusNcp = df_ + ncp_;
        final double dfPlus2Ncp = df_ + 2.0 * ncp_;
        final double dfPlus3Ncp = df_ + 3.0 * ncp_;
        final double h = 1.0 - 2.0 * dfPlusNcp * dfPlus3Ncp / (3.0 * dfPlus2Ncp * dfPlus2Ncp);
        final double p = dfPlus2Ncp / (dfPlusNcp * dfPlusNcp);
        final double m = (h - 1.0) * (1.0 - 3.0 * h);

        final double u = (Math.pow(x / dfPlusNcp, h)
                - (1.0 + h * p * (h - 1.0 - 0.5 * (2.0 - h) * m * p)))
                / (h * Math.sqrt(2.0 * p) * (1.0 + 0.5 * m * p));

        return new CumulativeNormalDistribution().op(u);
    }
}
