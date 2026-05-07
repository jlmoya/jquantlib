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
 * Generalized Gauss-Hermite integration:
 * <pre>
 *   ∫_{-∞}^{∞} f(x) dx
 * </pre>
 * with weight function {@code w(x;mu) = |x|^{2 mu} exp(-x^2)}, {@code mu > -0.5}.
 *
 * <p>Phase 2j.5 Track C.1 port of {@code QuantLib::GaussHermiteIntegration}
 * (v1.42.1 ql/math/integrals/gaussianquadratures.{hpp,cpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Thin convenience wrapper over {@link GaussianQuadrature} parametrized
 * by {@link GaussHermitePolynomial}. The C++ summation order — highest
 * abscissa first — is preserved by inheriting {@link GaussianQuadrature#op}.
 */
public final class GaussHermiteIntegration extends GaussianQuadrature {

    public GaussHermiteIntegration(final int n) {
        this(n, 0.0);
    }

    public GaussHermiteIntegration(final int n, final double mu) {
        super(n, new GaussHermitePolynomial(mu));
    }
}
