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
 * Maddock's cumulative normal distribution.
 *
 * <p>Java port of {@code QuantLib::MaddockCumulativeNormal} (v1.42.1
 * {@code ql/math/distributions/normaldistribution.{hpp,cpp}}, pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>The C++ implementation calls Boost.Math's CDF — i.e. a high-accuracy
 * polynomial approximation of {@code erf}. This Java port delegates to
 * {@link CumulativeNormalDistribution}, which already provides the
 * v1.42.1-equivalent CDF accurate to ~1e-15 (cf. {@code testInverseCumulativeNormal}
 * and related tests).
 *
 * <p>Phase 2 L1-D port.
 */
public class MaddockCumulativeNormal implements Ops.DoubleOp {

    private final CumulativeNormalDistribution delegate_;

    public MaddockCumulativeNormal() {
        this(0.0, 1.0);
    }

    public MaddockCumulativeNormal(final double average, final double sigma) {
        this.delegate_ = new CumulativeNormalDistribution(average, sigma);
    }

    @Override
    public double op(final double x) {
        return delegate_.op(x);
    }
}
