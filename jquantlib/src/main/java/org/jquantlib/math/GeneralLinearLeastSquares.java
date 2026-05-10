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

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2009 Dirk Eddelbuettel
 Copyright (C) 2006, 2009, 2010 Klaus Spanderen
 Copyright (C) 2010 Kakhkhor Abdijalilov
 Copyright (C) 2010 Slava Mazur
*/

package org.jquantlib.math;

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.SVD;

/**
 * General linear least-squares regression solved via SVD.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/math/generallinearleastsquares.hpp} (Phase 5h.5-MC-AME WI-1).
 * Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The C++ template version is parameterised over container/iterator
 * types and basis-function callables. Java-side this port specialises
 * to the only call sites the LSM machinery uses:
 *
 * <ul>
 *   <li>{@link #regress(double[], double[], List)} — single-variate
 *       regression where every basis function takes a {@code double}
 *       state.</li>
 * </ul>
 *
 * <p>References:
 * "Numerical Recipes in C", 2nd edition (Press et al.).
 *
 * @author JQuantLib
 */
public final class GeneralLinearLeastSquares {

    private final Array a_;
    private final Array err_;
    private final Array residuals_;
    private final Array standardErrors_;

    /**
     * Single-variate constructor: regress {@code y} on the basis
     * {@code v} evaluated at the points {@code x}.
     *
     * @param x  sample state values, size {@code n}
     * @param y  observed values, size {@code n}
     * @param v  basis system, size {@code m} (m &lt;= n)
     */
    public GeneralLinearLeastSquares(final double[] x,
                                     final double[] y,
                                     final List<? extends Ops.DoubleOp> v) {
        QL.require(x != null && y != null && v != null,
                "x, y and v must be non-null");
        final int n = y.length;
        final int m = v.size();
        QL.require(x.length == n,
                "sample set need to be of the same size");
        QL.require(n >= m, "sample set is too small");

        this.a_ = new Array(m);
        this.err_ = new Array(m);
        this.residuals_ = new Array(n);
        this.standardErrors_ = new Array(m);

        // Build design matrix A_{i,j} = v_j(x_i). Note: the SVD constructor
        // destroys its input matrix in place (mirrors the JAMA algorithm),
        // so we keep an unmodified copy of A for the residual calculation.
        final Matrix A = new Matrix(n, m);
        for (int j = 0; j < m; ++j) {
            final Ops.DoubleOp f = v.get(j);
            for (int i = 0; i < n; ++i) {
                A.set(i, j, f.op(x[i]));
            }
        }
        final Matrix Aorig = A.clone();

        // SVD of A (consumes A in place).
        final SVD svd = new SVD(A);
        final Matrix V = svd.V();
        final Matrix U = svd.U();
        final Array w = svd.singularValues();
        final double threshold = n * Constants.QL_EPSILON * w.get(0);

        // Pseudo-inverse via SVD: a_j += sum_i V_{ji} * (U_i^T y) / w_i for w_i > threshold.
        for (int i = 0; i < m; ++i) {
            if (w.get(i) > threshold) {
                // u = inner_product(U.col(i), y) / w_i
                double u = 0.0;
                for (int k = 0; k < n; ++k) {
                    u += U.get(k, i) * y[k];
                }
                u /= w.get(i);
                for (int j = 0; j < m; ++j) {
                    final double vji = V.get(j, i);
                    a_.set(j, a_.get(j) + u * vji);
                    err_.set(j, err_.get(j) + (vji * vji) / (w.get(i) * w.get(i)));
                }
            }
        }
        // err_ = sqrt(err_)
        for (int j = 0; j < m; ++j) {
            err_.set(j, Math.sqrt(err_.get(j)));
        }

        // residuals = A * a - y (use original A; the in-place SVD trashed
        // the working copy)
        final Array tmp = new Array(n);
        for (int i = 0; i < n; ++i) {
            double s = 0.0;
            for (int j = 0; j < m; ++j) {
                s += Aorig.get(i, j) * a_.get(j);
            }
            tmp.set(i, s);
        }
        for (int i = 0; i < n; ++i) {
            residuals_.set(i, tmp.get(i) - y[i]);
        }

        // standardErrors = err * sqrt(chiSq / (n-2))
        double chiSq = 0.0;
        for (int i = 0; i < n; ++i) {
            chiSq += residuals_.get(i) * residuals_.get(i);
        }
        final double multiplier = Math.sqrt(chiSq / (n - 2));
        for (int j = 0; j < m; ++j) {
            standardErrors_.set(j, err_.get(j) * multiplier);
        }
    }

    /** Regression coefficients. */
    public Array coefficients() {
        return a_;
    }

    /** Residuals (length n). */
    public Array residuals() {
        return residuals_;
    }

    /** Standard parameter errors (Excel/R convention). */
    public Array standardErrors() {
        return standardErrors_;
    }

    /** Modeling uncertainty as defined in Numerical Recipes. */
    public Array error() {
        return err_;
    }

    public int size() {
        return residuals_.size();
    }

    public int dim() {
        return a_.size();
    }
}
