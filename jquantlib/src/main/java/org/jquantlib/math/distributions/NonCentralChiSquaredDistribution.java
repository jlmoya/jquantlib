/*
 Copyright (C) 2008 Richard Gomes
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

package org.jquantlib.math.distributions;

import org.jquantlib.math.Ops;

/**
 * Non-central chi-squared cumulative distribution (legacy alias).
 * <p>
 * Despite the name (which omits "Cumulative"), this class has always been a
 * cumulative distribution function — JQuantLib historically dropped the
 * "Cumulative" qualifier from QuantLib's
 * {@code NonCentralCumulativeChiSquareDistribution}. Note that v1.42.1 does
 * <strong>not</strong> define a non-central chi-squared probability density
 * (PDF) class; the public surface is the cumulative distribution, the
 * Sankaran approximation, and the inverse cumulative distribution.
 * <p>
 * This class is retained for source compatibility with existing callers
 * (e.g. {@link org.jquantlib.model.shortrate.onefactormodels.CoxIngersollRoss},
 * {@link org.jquantlib.model.shortrate.onefactormodels.ExtendedCoxIngersollRoss})
 * and now delegates to {@link NonCentralCumulativeChiSquaredDistribution},
 * which is the canonical fresh port from v1.42.1.
 *
 * @author Richard Gomes
 */
public class NonCentralChiSquaredDistribution implements Ops.DoubleOp {

    private final NonCentralCumulativeChiSquaredDistribution delegate_;

    public NonCentralChiSquaredDistribution(final double df, final double ncp) {
        this.delegate_ = new NonCentralCumulativeChiSquaredDistribution(df, ncp);
    }

    @Override
    public double op(final double x) /* @Read-only */ {
        return delegate_.op(x);
    }
}
