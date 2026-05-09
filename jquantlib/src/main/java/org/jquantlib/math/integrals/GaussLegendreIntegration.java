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
package org.jquantlib.math.integrals;

/**
 * Gauss-Legendre quadrature for integrals on {@code [-1, 1]}:
 * <pre>
 *   ∫_{-1}^{1} f(x) dx
 * </pre>
 * with weight function {@code w(x) = 1}.
 *
 * <p>Phase 4a.5 A.5.1 port of {@code QuantLib::GaussLegendreIntegration}
 * (v1.42.1 ql/math/integrals/gaussianquadratures.{hpp,cpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Thin convenience wrapper over {@link GaussianQuadrature} parametrized
 * by {@link GaussJacobiPolynomial} with {@code alpha = beta = 0}, matching
 * the C++ constructor:
 * <pre>
 *   GaussLegendreIntegration(Size n)
 *     : GaussianQuadrature(n, GaussJacobiPolynomial(0.0, 0.0)) {}
 * </pre>
 *
 * <p>The summation order — highest abscissa first — is preserved by
 * inheriting {@link GaussianQuadrature#op(org.jquantlib.math.Ops.DoubleOp)}.
 *
 * <p>Required by Phase 4a A.5.2 ({@code AnalyticHestonEngine} integration
 * scheme {@code gaussLegendre}) and other downstream consumers.
 */
public final class GaussLegendreIntegration extends GaussianQuadrature {

    public GaussLegendreIntegration(final int n) {
        super(n, new GaussJacobiPolynomial(0.0, 0.0));
    }
}
