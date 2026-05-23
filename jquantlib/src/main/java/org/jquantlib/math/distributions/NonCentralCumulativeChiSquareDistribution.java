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
 * Non-central chi-squared cumulative distribution — C++-named alias.
 *
 * <p>This is a source-compatibility alias for the existing
 * {@link NonCentralCumulativeChiSquaredDistribution}, exposing the exact C++
 * symbol {@code QuantLib::NonCentralCumulativeChiSquareDistribution} (without
 * the trailing "d") so ported code can compile verbatim.
 *
 * <p>Phase 2 L1-D port.
 */
public class NonCentralCumulativeChiSquareDistribution implements Ops.DoubleOp {

    private final NonCentralCumulativeChiSquaredDistribution delegate_;

    public NonCentralCumulativeChiSquareDistribution(final double df, final double ncp) {
        this.delegate_ = new NonCentralCumulativeChiSquaredDistribution(df, ncp);
    }

    @Override
    public double op(final double x) {
        return delegate_.op(x);
    }
}
