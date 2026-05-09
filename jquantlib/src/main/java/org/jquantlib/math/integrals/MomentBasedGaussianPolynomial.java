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

import org.jquantlib.QL;
import org.jquantlib.math.Closeness;

import java.util.ArrayList;
import java.util.List;

/**
 * Gaussian quadrature polynomial defined by the moments of the distribution.
 *
 * <p>Java port of the {@code double}-specialization of the C++ template
 * {@code MomentBasedGaussianPolynomial<Real>} from QuantLib v1.42.1
 * {@code ql/math/integrals/momentbasedgaussianpolynomial.hpp}.
 * The arbitrary-precision template parameter {@code mp_real} is not needed
 * in the Java port; {@code double} is used throughout.
 *
 * <p>Implements the Golub-Welsch / Chebyshev algorithm: the three-term
 * recurrence coefficients {@code alpha(i)} and {@code beta(i)} are derived
 * from the moments of the weight function via a modified Chebyshev recursion
 * on the matrix {@code z[k][i]}.
 *
 * <p>References:
 * <ul>
 *   <li>G.H. Golub and J.H. Welsch, Math. Comput. 23 (1986), 221–230.</li>
 *   <li>M. Morandi Cecchi and M. Redivo Zaglia, Numerical integration by
 *       moments and modified moments.</li>
 * </ul>
 *
 * @author Phase 4j port
 */
public abstract class MomentBasedGaussianPolynomial extends GaussianOrthogonalPolynomial {

    // z matrix: z_[k][i], lazily populated
    private final List<List<Double>> z_ = new ArrayList<>();
    {
        z_.add(new ArrayList<>());  // z_[0] is initially empty
    }
    private final List<Double> b_ = new ArrayList<>();   // alpha cache
    private final List<Double> c_ = new ArrayList<>();   // beta cache

    /**
     * Returns the {@code i}-th moment of the weight function:
     * <pre>  mu_i = ∫ x^i w(x) dx</pre>
     * Subclasses must implement this.
     */
    public abstract double moment(int i);

    // --- GaussianOrthogonalPolynomial interface ---

    @Override
    public double mu_0() {
        final double m0 = moment(0);
        QL.require(Closeness.isClose(m0, 1.0),
                "MomentBasedGaussianPolynomial: zero moment must be one");
        return m0;
    }

    @Override
    public double alpha(final int u) {
        return alpha_(u);
    }

    @Override
    public double beta(final int u) {
        return beta_(u);
    }

    // --- internal helpers ---

    private double alpha_(final int u) {
        ensureSize(b_, u + 1);
        if (Double.isNaN(b_.get(u))) {
            final double val;
            if (u == 0) {
                val = moment(1);
            } else {
                // b_[u] = -z(u-1, u)/z(u-1, u-1) + z(u, u+1)/z(u, u)
                val = -z(u - 1, u) / z(u - 1, u - 1) + z(u, u + 1) / z(u, u);
            }
            b_.set(u, val);
        }
        return b_.get(u);
    }

    private double beta_(final int u) {
        if (u == 0) return 1.0;
        ensureSize(c_, u + 1);
        if (Double.isNaN(c_.get(u))) {
            // c_[u] = z(u, u) / z(u-1, u-1)
            final double val = z(u, u) / z(u - 1, u - 1);
            c_.set(u, val);
        }
        return c_.get(u);
    }

    private double z(final int k, final int i) {
        if (k == -1) return 0.0;

        // Ensure z_ has at least k+1 rows and each row has at least i+1 cols
        while (z_.size() <= k) {
            z_.add(new ArrayList<>());
        }
        final List<Double> row = z_.get(k);
        while (row.size() <= i) {
            row.add(Double.NaN);
        }
        // Also ensure all earlier rows have same column width
        for (int l = 0; l < z_.size(); ++l) {
            while (z_.get(l).size() <= i) {
                z_.get(l).add(Double.NaN);
            }
        }

        if (Double.isNaN(row.get(i))) {
            final double val;
            if (k == 0) {
                val = moment(i);
            } else {
                // z_[k][i] = z_[k-1][i+1] - alpha_(k-1)*z_[k-1][i] - beta_(k-1)*z_[k-2][i]
                val = z(k - 1, i + 1) - alpha_(k - 1) * z(k - 1, i) - beta_(k - 1) * z(k - 2, i);
            }
            row.set(i, val);
        }
        return row.get(i);
    }

    private static void ensureSize(final List<Double> list, final int size) {
        while (list.size() < size) {
            list.add(Double.NaN);
        }
    }
}
