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

import org.jquantlib.QL;
import org.jquantlib.math.Ops;
import org.jquantlib.math.matrixutilities.TqrEigenDecomposition;
import org.jquantlib.math.matrixutilities.TqrEigenDecomposition.EigenVectorCalculation;
import org.jquantlib.math.matrixutilities.TqrEigenDecomposition.ShiftStrategy;

/**
 * 1-dimensional Gauss quadrature derived from the orthogonal polynomial recurrence via the Golub–Welsch algorithm.
 *
 * <p>Phase 2j.5 Track C.1 port of {@code QuantLib::GaussianQuadrature}
 * (v1.42.1 ql/math/integrals/gaussianquadratures.{hpp,cpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The constructor builds the symmetric tridiagonal Jacobi matrix of the
 * polynomial's recurrence coefficients and runs an implicit-shift QR eigendecomposition (a transcription of QuantLib's
 * {@link org.jquantlib.math.matrixutilities.TqrEigenDecomposition}). Eigenvalues become the abscissae; the first row of
 * the orthogonal eigenvector matrix gives the weights via {@code w_i = mu_0 * v_{0,i}^2 / w(x_i)}.
 *
 * <p>The summation order in {@link #op(Ops.DoubleOp)} matches C++
 * {@code GaussianQuadrature::operator()}: highest-index node first, descending to index 0.
 *
 * <p>References:
 * <ul>
 *   <li>Golub & Welsch, "Calculation of Gauss quadrature rule",
 *       Math. Comput. 23 (1986), 221–230.</li>
 *   <li>Wilkinson & Reinsch, "Linear Algebra", Handbook for Automatic
 *       Computation, vol. II (Springer, 1971).</li>
 * </ul>
 */
public class GaussianQuadrature {

    protected final double[] x_;
    protected final double[] w_;

    public GaussianQuadrature(final int n, final GaussianOrthogonalPolynomial orthPoly) {
        QL.require(n > 0, "GaussianQuadrature: order must be positive");
        this.x_ = new double[n];
        this.w_ = new double[n];

        // Symmetric tridiagonal Jacobi matrix:
        //   diag[i] = alpha(i)
        //   subdiag[i-1] = sqrt(beta(i))
        final double[] e = new double[n - 1];
        for ( int i = 1; i < n; ++i ) {
            x_[i] = orthPoly.alpha(i);
            e[i - 1] = Math.sqrt(orthPoly.beta(i));
        }
        x_[0] = orthPoly.alpha(0);

        // Implicit-shift tridiagonal QR with first-row eigenvector only,
        // overrelaxation shift strategy (matches C++).
        final TqrEigenDecomposition tqr = new TqrEigenDecomposition(x_, e,
                EigenVectorCalculation.OnlyFirstRowEigenVector, ShiftStrategy.Overrelaxation);

        // Eigenvalues become abscissae.
        System.arraycopy(tqr.d, 0, x_, 0, n);

        // Weights from first row of eigenvector matrix:
        //   w_i = mu_0 * ev[0][i]^2 / w(x_i)
        final double mu0 = orthPoly.mu_0();
        for ( int i = 0; i < n; ++i ) {
            final double v = tqr.ev[0][i];
            w_[i] = mu0 * v * v / orthPoly.w(x_[i]);
        }
    }

    /** Number of quadrature points. */
    public int order() {
        return x_.length;
    }

    /** Read-only access to the abscissa table. */
    public double x(final int i) {
        return x_[i];
    }

    /** Read-only access to the weight table. */
    public double weight(final int i) {
        return w_[i];
    }

    /**
     * Compute {@code Σᵢ wᵢ · f(xᵢ)} iterating from highest index down to 0, matching C++
     * {@code GaussianQuadrature::operator()(const F& f)}.
     */
    public double op(final Ops.DoubleOp f) {
        double sum = 0.0;
        for ( int i = x_.length - 1; i >= 0; --i ) {
            sum += w_[i] * f.op(x_[i]);
        }
        return sum;
    }

}
