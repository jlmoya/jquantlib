/*
 Copyright (C) 2020 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

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

package org.jquantlib.math.integrals;

import java.util.ArrayList;
import java.util.List;

/**
 * Common base for Gauss-Laguerre-trigonometric quadrature polynomials.
 *
 * <p>Java port of the C++ template
 * {@code GaussLaguerreTrigonometricBase<mp_real>} from QuantLib v1.42.1
 * {@code ql/math/integrals/gausslaguerrecosinepolynomial.hpp}. Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Holds the second-order auxiliary moment recursion shared by the Cosine
 * and Sine specialisations:
 * <pre>
 *   m_n = (2*n*m_{n-1} - n*(n-1)*m_{n-2}) / (1 + u*u),  n &gt;= 2
 * </pre>
 * Concrete subclasses supply the closed-form initial values {@code m_0} and {@code m_1}, plus the final
 * {@code moment(n)} / {@code w(x)} normalisation required by {@link MomentBasedGaussianPolynomial}.
 *
 * <p>The arbitrary-precision template parameter {@code mp_real} in C++ is not
 * needed in the Java port; {@code double} is used throughout. See {@link MomentBasedGaussianPolynomial} for the
 * precision rationale.
 */
public abstract class GaussLaguerreTrigonometricBase extends MomentBasedGaussianPolynomial {

    protected final double u_;

    // Lazily-grown caches for the auxiliary m_ and factorial f_ tables.
    private final List< Double > m_ = new ArrayList<>();
    private final List< Double > f_ = new ArrayList<>();

    protected GaussLaguerreTrigonometricBase(final double u) {
        this.u_ = u;
    }

    private static void ensureSize(final List< Double > list, final int size) {
        while ( list.size() < size ) {
            list.add(Double.NaN);
        }
    }

    /** Initial moment value m_0 (concrete variant supplies the closed form). */
    protected abstract double m0();

    /** Second initial moment value m_1 (concrete variant supplies the closed form). */
    protected abstract double m1();

    /**
     * Auxiliary moment {@code m_n} via the second-order recursion shared by the Cosine and Sine specialisations.
     * Memoised.
     */
    protected double moment_(final int n) {
        ensureSize(m_, n + 1);
        if ( Double.isNaN(m_.get(n)) ) {
            final double val;
            if ( n == 0 ) {
                val = m0();
            } else if ( n == 1 ) {
                val = m1();
            } else {
                val = (2.0 * n * moment_(n - 1) - n * (n - 1) * moment_(n - 2)) / (1.0 + u_ * u_);
            }
            m_.set(n, val);
        }
        return m_.get(n);
    }

    /**
     * Memoised factorial {@code n!}.
     */
    protected double fact(final int n) {
        ensureSize(f_, n + 1);
        if ( Double.isNaN(f_.get(n)) ) {
            final double val = (n == 0) ? 1.0 : n * fact(n - 1);
            f_.set(n, val);
        }
        return f_.get(n);
    }
}
