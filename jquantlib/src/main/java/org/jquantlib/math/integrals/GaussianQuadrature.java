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

import java.util.Arrays;
import java.util.Comparator;

import org.jquantlib.QL;
import org.jquantlib.math.Ops;

/**
 * 1-dimensional Gauss quadrature derived from the orthogonal polynomial
 * recurrence via the Golub–Welsch algorithm.
 *
 * <p>Phase 2j.5 Track C.1 port of {@code QuantLib::GaussianQuadrature}
 * (v1.42.1 ql/math/integrals/gaussianquadratures.{hpp,cpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The constructor builds the symmetric tridiagonal Jacobi matrix of the
 * polynomial's recurrence coefficients and runs an implicit-shift QR
 * eigendecomposition (a transcription of QuantLib's
 * {@code TqrEigenDecomposition}, embedded here as a private inner class
 * because Phase 2j.5 Track C.1 scope is bounded to
 * {@code org.jquantlib.math.integrals}). Eigenvalues become the abscissae;
 * the first row of the orthogonal eigenvector matrix gives the weights via
 * {@code w_i = mu_0 * v_{0,i}^2 / w(x_i)}.
 *
 * <p>The summation order in {@link #op(Ops.DoubleOp)} matches C++
 * {@code GaussianQuadrature::operator()}: highest-index node first,
 * descending to index 0.
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
        for (int i = 1; i < n; ++i) {
            x_[i] = orthPoly.alpha(i);
            e[i - 1] = Math.sqrt(orthPoly.beta(i));
        }
        x_[0] = orthPoly.alpha(0);

        // Implicit-shift tridiagonal QR with first-row eigenvector only,
        // overrelaxation shift strategy (matches C++).
        final TqrEigen tqr = new TqrEigen(x_, e,
                EigenVectorCalculation.OnlyFirstRowEigenVector,
                ShiftStrategy.Overrelaxation);

        // Eigenvalues become abscissae.
        System.arraycopy(tqr.d, 0, x_, 0, n);

        // Weights from first row of eigenvector matrix:
        //   w_i = mu_0 * ev[0][i]^2 / w(x_i)
        final double mu0 = orthPoly.mu_0();
        for (int i = 0; i < n; ++i) {
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
     * Compute {@code Σᵢ wᵢ · f(xᵢ)} iterating from highest index down to
     * 0, matching C++ {@code GaussianQuadrature::operator()(const F& f)}.
     */
    public double op(final Ops.DoubleOp f) {
        double sum = 0.0;
        for (int i = x_.length - 1; i >= 0; --i) {
            sum += w_[i] * f.op(x_[i]);
        }
        return sum;
    }

    // ===================================================================
    // Embedded tridiagonal QR eigendecomposition (Wilkinson algorithm).
    // Transcription of QuantLib v1.42.1
    // ql/math/matrixutilities/tqreigendecomposition.{hpp,cpp}.
    //
    // Private to this package per Phase 2j.5 Track C.1 scope: the Java port
    // does not yet host TqrEigenDecomposition in math.matrixutilities (only
    // SymmetricSchurDecomposition and EigenvalueDecomposition exist there),
    // and the task scope is bounded to math.integrals. Future phases that
    // need the same algorithm outside Gauss quadratures should lift this to
    // org.jquantlib.math.matrixutilities.
    // ===================================================================

    enum EigenVectorCalculation {
        WithEigenVector,
        WithoutEigenVector,
        OnlyFirstRowEigenVector
    }

    enum ShiftStrategy {
        NoShift,
        Overrelaxation,
        CloseEigenValue
    }

    static final class TqrEigen {
        final double[] d;       // eigenvalues (sorted descending; first ev component non-negative)
        final double[][] ev;    // eigenvector rows; ev.length == 0/1/n depending on calc

        TqrEigen(final double[] diag,
                 final double[] sub,
                 final EigenVectorCalculation calc,
                 final ShiftStrategy strategy) {
            final int n = diag.length;
            QL.require(n == sub.length + 1, "Wrong dimensions");

            this.d = new double[n];
            System.arraycopy(diag, 0, this.d, 0, n);

            final int evRows = (calc == EigenVectorCalculation.WithEigenVector) ? n
                             : (calc == EigenVectorCalculation.WithoutEigenVector) ? 0
                             : 1;
            this.ev = new double[evRows][n];

            // e[0] is unused; the C++ code copies sub into e starting at index 1
            final double[] e = new double[n];
            System.arraycopy(sub, 0, e, 1, n - 1);

            for (int i = 0; i < evRows; ++i) {
                ev[i][i] = 1.0;
            }

            for (int k = n - 1; k >= 1; --k) {
                while (!offDiagIsZero(k, e)) {
                    int l = k;
                    while (--l > 0 && !offDiagIsZero(l, e)) {
                        // walk down to find first zero off-diagonal element
                    }

                    double q = d[l];
                    if (strategy != ShiftStrategy.NoShift) {
                        // eigenvalue of 2x2 sub matrix closer to d[k+1]
                        final double t1 = Math.sqrt(
                                0.25 * (d[k] * d[k] + d[k - 1] * d[k - 1])
                                - 0.5 * d[k - 1] * d[k] + e[k] * e[k]);
                        final double t2 = 0.5 * (d[k] + d[k - 1]);

                        final double lambda =
                                (Math.abs(t2 + t1 - d[k]) < Math.abs(t2 - t1 - d[k]))
                                        ? (t2 + t1) : (t2 - t1);

                        if (strategy == ShiftStrategy.CloseEigenValue) {
                            q -= lambda;
                        } else {
                            q -= ((k == n - 1) ? 1.25 : 1.0) * lambda;
                        }
                    }

                    // QR transformation
                    double sine = 1.0;
                    double cosine = 1.0;
                    double u = 0.0;

                    boolean recoverUnderflow = false;
                    for (int i = l + 1; i <= k && !recoverUnderflow; ++i) {
                        final double h = cosine * e[i];
                        final double p = sine * e[i];

                        e[i - 1] = Math.sqrt(p * p + q * q);
                        if (e[i - 1] != 0.0) {
                            sine = p / e[i - 1];
                            cosine = q / e[i - 1];

                            final double g = d[i - 1] - u;
                            final double t = (d[i] - g) * sine + 2.0 * cosine * h;

                            u = sine * t;
                            d[i - 1] = g + u;
                            q = cosine * t - h;

                            for (int j = 0; j < evRows; ++j) {
                                final double tmp = ev[j][i - 1];
                                ev[j][i - 1] = sine * ev[j][i] + cosine * tmp;
                                ev[j][i] = cosine * ev[j][i] - sine * tmp;
                            }
                        } else {
                            // recover from underflow
                            d[i - 1] -= u;
                            e[l] = 0.0;
                            recoverUnderflow = true;
                        }
                    }

                    if (!recoverUnderflow) {
                        d[k] -= u;
                        e[k] = q;
                        e[l] = 0.0;
                    }
                }
            }

            // Sort (eigenvalues, eigenvectors) descending by eigenvalue.
            // First eigenvector component is forced non-negative (matches C++).
            final Integer[] order = new Integer[n];
            for (int i = 0; i < n; ++i) order[i] = i;
            // Stable sort descending by d[i]; matches std::sort with std::greater<>
            // on pair<Real, vector<Real>> where the vector breaks ties — we use
            // index-stable Arrays.sort then re-permute.
            Arrays.sort(order, new Comparator<Integer>() {
                @Override
                public int compare(final Integer a, final Integer b) {
                    return Double.compare(d[b], d[a]);
                }
            });
            final double[] dCopy = d.clone();
            final double[][] evCopy = new double[evRows][n];
            for (int j = 0; j < evRows; ++j) {
                System.arraycopy(ev[j], 0, evCopy[j], 0, n);
            }
            for (int i = 0; i < n; ++i) {
                final int src = order[i];
                d[i] = dCopy[src];
                double sign = 1.0;
                if (evRows > 0 && evCopy[0][src] < 0.0) {
                    sign = -1.0;
                }
                for (int j = 0; j < evRows; ++j) {
                    ev[j][i] = sign * evCopy[j][src];
                }
            }
        }

        private boolean offDiagIsZero(final int k, final double[] e) {
            // NR-style termination check: |d[k-1]|+|d[k]| == |d[k-1]|+|d[k]|+|e[k]|
            final double a = Math.abs(d[k - 1]) + Math.abs(d[k]);
            return a == a + Math.abs(e[k]);
        }
    }
}
