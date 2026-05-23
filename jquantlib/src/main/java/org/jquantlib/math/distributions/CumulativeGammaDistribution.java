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
 * Cumulative gamma distribution {@code P(a, x)} — regularized lower
 * incomplete gamma function.
 *
 * <p>Faithful Java port of {@code QuantLib::CumulativeGammaDistribution}
 * (v1.42.1 {@code ql/math/distributions/gammadistribution.{hpp,cpp}}, pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>The historical JQuantLib class {@link GammaDistribution} also computes
 * the cumulative form — its name is a long-standing misnomer; this class
 * provides the correct C++ name for new callers and delegates to the existing
 * implementation. Constructor signature exactly mirrors C++:
 * {@code CumulativeGammaDistribution(Real a)} with the {@code a > 0} precondition.
 *
 * <p>Phase 2 L1-D port.
 */
public class CumulativeGammaDistribution implements Ops.DoubleOp {

    private final GammaDistribution delegate_;

    public CumulativeGammaDistribution(final double a) {
        this.delegate_ = new GammaDistribution(a);
    }

    @Override
    public double op(final double x) {
        return delegate_.op(x);
    }
}
