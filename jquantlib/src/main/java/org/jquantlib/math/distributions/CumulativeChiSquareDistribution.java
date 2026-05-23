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
package org.jquantlib.math.distributions;

import org.jquantlib.math.Ops;

/**
 * Central chi-squared cumulative distribution function.
 *
 * <p>Faithful Java port of {@code QuantLib::CumulativeChiSquareDistribution}
 * (v1.42.1 {@code ql/math/distributions/chisquaredistribution.{hpp,cpp}}, pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>Implementation matches the C++ source one-liner
 * {@code return CumulativeGammaDistribution(0.5*df_)(0.5*x);}, i.e. the central
 * chi-squared CDF equals the regularized lower incomplete gamma {@code P(k/2, x/2)}.
 *
 * <p>This class uses C++'s naming convention ("ChiSquare", without trailing
 * "d") to mirror the upstream API; {@link CumulativeGammaDistribution} performs
 * the underlying gamma computation.
 *
 * <p>Phase 2 L1-D port.
 */
public class CumulativeChiSquareDistribution implements Ops.DoubleOp {

    private final double df_;

    public CumulativeChiSquareDistribution(final double df) {
        this.df_ = df;
    }

    @Override
    public double op(final double x) {
        return new CumulativeGammaDistribution(0.5 * df_).op(0.5 * x);
    }
}
