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
 * Inverse non-central chi-squared CDF — C++-named alias.
 *
 * <p>This is a source-compatibility alias for the existing
 * {@link InverseNonCentralCumulativeChiSquaredDistribution}, exposing the exact
 * C++ symbol {@code QuantLib::InverseNonCentralCumulativeChiSquareDistribution}
 * (without the trailing "d") so ported code can compile verbatim.
 *
 * <p>Phase 2 L1-D port.
 */
public class InverseNonCentralCumulativeChiSquareDistribution implements Ops.DoubleOp {

    private final InverseNonCentralCumulativeChiSquaredDistribution delegate_;

    public InverseNonCentralCumulativeChiSquareDistribution(final double df, final double ncp) {
        this.delegate_ = new InverseNonCentralCumulativeChiSquaredDistribution(df, ncp);
    }

    public InverseNonCentralCumulativeChiSquareDistribution(final double df, final double ncp,
            final int maxEvaluations, final double accuracy) {
        this.delegate_ = new InverseNonCentralCumulativeChiSquaredDistribution(df, ncp, maxEvaluations, accuracy);
    }

    @Override
    public double op(final double x) {
        return delegate_.op(x);
    }
}
