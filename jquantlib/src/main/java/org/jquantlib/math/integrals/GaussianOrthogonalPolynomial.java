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
 * Orthogonal polynomial for Gaussian quadratures (3-term recurrence).
 *
 * <p>Phase 2j.5 Track C.1 port of {@code QuantLib::GaussianOrthogonalPolynomial}
 * (v1.42.1 ql/math/integrals/gaussianorthogonalpolynomial.{hpp,cpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Polynomials defined by the three-term recurrence
 * <pre>
 *   P_{k+1}(x) = (x - alpha_k) P_k(x) - beta_k P_{k-1}(x)
 * </pre>
 * with weight function {@code w(x)} and zeroth moment {@code mu_0 = ∫ w(x) dx}. The {@code value(n,x)} default uses the
 * recurrence; {@code weightedValue(n,x) = sqrt(w(x)) * value(n,x)}.
 *
 * <p>References:
 * <ul>
 *   <li>G.H. Golub and J.H. Welsch, "Calculation of Gauss quadrature rule",
 *       Math. Comput. 23 (1986), 221–230.</li>
 *   <li>Press et al., "Numerical Recipes in C", 2nd ed., §4.5.</li>
 * </ul>
 */
public abstract class GaussianOrthogonalPolynomial {

    public abstract double mu_0();

    public abstract double alpha(int i);

    public abstract double beta(int i);

    public abstract double w(double x);

    /**
     * Recursive evaluation of the orthogonal polynomial of degree {@code n} at {@code x}, via the 3-term recurrence.
     * Mirrors C++ {@code GaussianOrthogonalPolynomial::value(Size,Real)}.
     */
    public double value(final int n, final double x) {
        if ( n > 1 ) {
            return (x - alpha(n - 1)) * value(n - 1, x) - beta(n - 1) * value(n - 2, x);
        } else if ( n == 1 ) {
            return x - alpha(0);
        }
        return 1.0;
    }

    /**
     * Weighted value {@code sqrt(w(x)) * P_n(x)}. Mirrors C++
     * {@code GaussianOrthogonalPolynomial::weightedValue(Size,Real)}.
     */
    public double weightedValue(final int n, final double x) {
        return Math.sqrt(w(x)) * value(n, x);
    }
}
