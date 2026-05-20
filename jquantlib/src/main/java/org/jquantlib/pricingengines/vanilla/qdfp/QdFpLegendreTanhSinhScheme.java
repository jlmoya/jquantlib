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
package org.jquantlib.pricingengines.vanilla.qdfp;

import org.jquantlib.math.integrals.Integrator;
import org.jquantlib.math.integrals.TanhSinhIntegral;

/**
 * Hybrid Legendre / tanh-sinh {@code (l,m,n)-eps} scheme.
 *
 * <p>Phase 1 closure A1 port of {@code QuantLib::QdFpLegendreTanhSinhScheme}
 * (v1.42.1 ql/pricingengines/vanilla/qdfpamericanengine.{hpp,cpp}; pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>Inherits the {@link QdFpLegendreScheme} fixed-point integrator
 * (Gauss-Legendre of order {@code l}) but overrides the final boundary-to-
 * price integrator with a {@link TanhSinhIntegral} of relative tolerance
 * {@code eps}. The {@code p} parameter of the parent is fixed to {@code 1}
 * because it is unused — the override returns a fresh integrator on every
 * call (matching the C++ behaviour, where each call constructs a new
 * {@code TanhSinhIntegral}).
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code l} — order of Gauss-Legendre integration inside every
 *       fixed-point iteration step.</li>
 *   <li>{@code m} — total fixed-point iteration steps.</li>
 *   <li>{@code n} — number of Chebyshev nodes for the exercise boundary.</li>
 *   <li>{@code eps} — tanh-sinh relative tolerance for the final price
 *       conversion.</li>
 * </ul>
 */
public class QdFpLegendreTanhSinhScheme extends QdFpLegendreScheme {

    private final double eps_;

    public QdFpLegendreTanhSinhScheme(final int l, final int m, final int n, final double eps) {
        super(l, m, n, 1);
        this.eps_ = eps;
    }

    @Override
    public Integrator getExerciseBoundaryToPriceIntegrator() {
        // C++ constructs a fresh TanhSinhIntegral on every call (the Boost
        // tanh_sinh state is per-instance). We mirror that for behaviour
        // equivalence: callers that mutate Integrator state (e.g. error,
        // evaluation count) get a fresh object per call.
        return new TanhSinhIntegral(eps_);
    }
}
