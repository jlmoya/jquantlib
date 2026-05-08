/*
 Copyright (C) 2017 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*! \file gmres.hpp
    \brief Generalized minimal residual method
    Ported from QuantLib v1.42.1 — Phase 2l Track A.

    References:
    Saad, Yousef. 1996, Iterative methods for sparse linear systems,
    http://www-users.cs.umn.edu/~saad/books.html

    Dongarra et al. 1994, Templates for the Solution of Linear Systems:
    Building Blocks for Iterative Methods, 2nd Edition, SIAM, Philadelphia
    http://www.netlib.org/templates/templates.pdf
*/

package org.jquantlib.math.matrixutilities;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Generalized Minimal RESidual (GMRES) iterative solver.
 *
 * <p>Solves the linear system A*x = b using the GMRES(m) algorithm with
 * optional left preconditioning. Supports restart via
 * {@link #solveWithRestart(int, Array, Array)}.
 *
 * <p>Ported from QuantLib v1.42.1
 * {@code ql/math/matrixutilities/gmres.hpp} and {@code .cpp}.
 */
public class GMRES {

    /** Machine epsilon: std::numeric_limits&lt;double&gt;::epsilon() equivalent. */
    private static final double QL_EPSILON = Math.ulp(1.0);

    /** Operator type: maps an Array to an Array (e.g., x -&gt; A*x). */
    public interface MatrixMult extends Function<Array, Array> {
        // inherits: Array apply(Array x);
    }

    /** Result of a GMRES solve. */
    public static final class Result {
        /**
         * List of relative residual errors ‖r‖/‖b‖ collected at each iteration.
         * The first element is the initial residual; subsequent elements are
         * after each Arnoldi step.
         */
        public final List<Double> errors;
        /** Solution vector. */
        public final Array x;

        public Result(final List<Double> errors, final Array x) {
            this.errors = errors;
            this.x = x;
        }
    }

    // -----------------------------------------------------------------------

    private final MatrixMult A_;
    private final MatrixMult M_;
    private final int maxIter_;
    private final double relTol_;

    /**
     * Constructs a GMRES solver without preconditioner.
     *
     * @param A       matrix-vector product operator x -&gt; A*x
     * @param maxIter maximum Krylov space dimension (number of Arnoldi steps)
     * @param relTol  relative tolerance (convergence when ‖r‖/‖b‖ &lt; relTol)
     */
    public GMRES(final MatrixMult A, final int maxIter, final double relTol) {
        this(A, maxIter, relTol, null);
    }

    /**
     * Constructs a GMRES solver with an optional preconditioner.
     *
     * @param A              matrix-vector product x -&gt; A*x
     * @param maxIter        maximum Krylov space dimension
     * @param relTol         relative tolerance
     * @param preConditioner preconditioner x -&gt; M^{-1}*x, or {@code null}
     */
    public GMRES(final MatrixMult A, final int maxIter, final double relTol,
                 final MatrixMult preConditioner) {
        if (maxIter <= 0) {
            throw new IllegalArgumentException("GMRES: maxIter must be greater than zero");
        }
        this.A_       = A;
        this.maxIter_ = maxIter;
        this.relTol_  = relTol;
        this.M_       = preConditioner;
    }

    /**
     * Solve A*x = b starting from the zero vector.
     *
     * @param b right-hand side vector
     * @return solve result
     */
    public Result solve(final Array b) {
        return solve(b, null);
    }

    /**
     * Solve A*x = b, optionally starting from an initial guess x0.
     *
     * @param b  right-hand side vector
     * @param x0 initial guess, or {@code null} (or empty) for zero start
     * @return solve result
     * @throws IllegalStateException if convergence failed
     */
    public Result solve(final Array b, final Array x0) {
        final Result result = solveImpl(b, x0);
        if (result.errors.get(result.errors.size() - 1) >= relTol_) {
            throw new IllegalStateException("GMRES: could not converge");
        }
        return result;
    }

    /**
     * Solve A*x = b with up to {@code restart} restarts.
     *
     * @param restart number of restart cycles (total iterations = restart * maxIter)
     * @param b       right-hand side vector
     * @return solve result (errors list includes all iterations across restarts)
     * @throws IllegalStateException if convergence failed after all restarts
     */
    public Result solveWithRestart(final int restart, final Array b) {
        return solveWithRestart(restart, b, null);
    }

    /**
     * Solve A*x = b with restarts, starting from x0.
     *
     * @param restart number of restart cycles
     * @param b       right-hand side vector
     * @param x0      initial guess or {@code null}
     * @return solve result
     * @throws IllegalStateException if convergence failed
     */
    public Result solveWithRestart(final int restart, final Array b, final Array x0) {
        Result result = solveImpl(b, x0);
        final List<Double> errors = new ArrayList<>(result.errors);

        for (int i = 0; i < restart - 1 && result.errors.get(result.errors.size() - 1) >= relTol_; ++i) {
            result = solveImpl(b, result.x);
            errors.addAll(result.errors);
        }

        if (errors.get(errors.size() - 1) >= relTol_) {
            throw new IllegalStateException("GMRES: could not converge");
        }

        return new Result(errors, result.x);
    }

    // -----------------------------------------------------------------------

    /**
     * Core GMRES implementation (one cycle, no restart, no convergence check).
     * Mirrors {@code GMRES::solveImpl} in QuantLib v1.42.1.
     */
    protected Result solveImpl(final Array b, final Array x0) {
        final double bn = norm2(b);
        if (bn == 0.0) {
            final List<Double> errs = new ArrayList<>();
            errs.add(0.0);
            return new Result(errs, b.clone());
        }

        final Array x = (x0 != null && x0.size() > 0)
                ? x0.clone()
                : new Array(b.size());

        // r = b - A*x
        final Array r = b.sub(A_.apply(x));

        final double g = norm2(r);
        if (g / bn < relTol_) {
            final List<Double> errs = new ArrayList<>();
            errs.add(g / bn);
            return new Result(errs, x);
        }

        // Krylov basis vectors: v[0] = r/g
        final List<double[]> v = new ArrayList<>();
        v.add(scaled(r, 1.0 / g));

        // Upper Hessenberg matrix h, stored as list of double[] columns
        // h[i][j]: element at row i, column j — but stored as h.get(i)[j]
        // Initial: h[0] is a row of length maxIter_
        final List<double[]> h = new ArrayList<>();
        h.add(new double[maxIter_]);   // h[0] — length maxIter_

        final double[] c = new double[maxIter_ + 1];
        final double[] s = new double[maxIter_ + 1];
        final double[] z = new double[maxIter_ + 1];

        z[0] = g;

        final List<Double> errors = new ArrayList<>();
        errors.add(g / bn);

        for (int j = 0; j < maxIter_ && errors.get(errors.size() - 1) >= relTol_; ++j) {
            // Add new column to h
            h.add(new double[maxIter_]);  // h[j+1]

            // w = A * (M == null ? v[j] : M(v[j]))
            final double[] vj = v.get(j);
            final Array vArr = arrayFrom(vj);
            final Array wArr = A_.apply(M_ == null ? vArr : M_.apply(vArr));
            final double[] w = doubleArray(wArr);

            // Modified Gram-Schmidt orthogonalization
            for (int ii = 0; ii <= j; ++ii) {
                final double[] vi = v.get(ii);
                h.get(ii)[j] = dotProductRaw(w, vi);
                // w -= h[i][j] * v[i]
                for (int k = 0; k < w.length; k++) {
                    w[k] -= h.get(ii)[j] * vi[k];
                }
            }
            h.get(j + 1)[j] = norm2Raw(w);

            // If w is (near) zero, Krylov space is exhausted — stop
            final double eps2 = QL_EPSILON * QL_EPSILON;
            if (h.get(j + 1)[j] < eps2) {
                break;
            }

            // v[j+1] = w / h[j+1][j]
            final double scale = 1.0 / h.get(j + 1)[j];
            v.add(scaled(w, scale));

            // Apply previous Givens rotations to new column
            for (int ii = 0; ii < j; ++ii) {
                final double h0 =  c[ii] * h.get(ii)[j] + s[ii] * h.get(ii + 1)[j];
                final double h1 = -s[ii] * h.get(ii)[j] + c[ii] * h.get(ii + 1)[j];
                h.get(ii)[j]     = h0;
                h.get(ii + 1)[j] = h1;
            }

            // Compute new Givens rotation for (h[j][j], h[j+1][j])
            final double nu = Math.sqrt(h.get(j)[j] * h.get(j)[j]
                                      + h.get(j + 1)[j] * h.get(j + 1)[j]);
            c[j] = h.get(j)[j] / nu;
            s[j] = h.get(j + 1)[j] / nu;

            h.get(j)[j]     = nu;
            h.get(j + 1)[j] = 0.0;

            z[j + 1] = -s[j] * z[j];
            z[j]     =  c[j] * z[j];

            errors.add(Math.abs(z[j + 1]) / bn);
        }

        // Back-substitution: solve upper triangular system H*y = z
        final int k = v.size() - 1;   // number of Krylov vectors used
        final double[] y = new double[k];
        if (k > 0) {
            y[k - 1] = z[k - 1] / h.get(k - 1)[k - 1];
            for (int ii = k - 2; ii >= 0; --ii) {
                double sum = 0.0;
                for (int jj = ii + 1; jj < k; jj++) {
                    sum += h.get(ii)[jj] * y[jj];
                }
                y[ii] = (z[ii] - sum) / h.get(ii)[ii];
            }
        }

        // xm = x + sum_{i=0}^{k-1} y[i] * (M == null ? v[i] : M(v[i]))
        final int n = x.size();
        final double[] xm = new double[n];
        for (int ii = 0; ii < k; ii++) {
            final double yi = y[ii];
            if (M_ == null) {
                final double[] vi = v.get(ii);
                for (int d = 0; d < n; d++) {
                    xm[d] += yi * vi[d];
                }
            } else {
                final Array mvi = M_.apply(arrayFrom(v.get(ii)));
                for (int d = 0; d < n; d++) {
                    xm[d] += yi * mvi.get(d);
                }
            }
        }
        // xm = x + xm
        for (int d = 0; d < n; d++) {
            xm[d] += x.get(d);
        }

        return new Result(errors, arrayFrom(xm));
    }

    // -----------------------------------------------------------------------
    // Low-level helpers using raw double[] to match C++ Array performance.

    /** Create a new double[] scaled by {@code scale}. */
    private static double[] scaled(final Array arr, final double scale) {
        final int n = arr.size();
        final double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = arr.get(i) * scale;
        }
        return out;
    }

    /** Create a new double[] scaled by {@code scale}. */
    private static double[] scaled(final double[] arr, final double scale) {
        final int n = arr.length;
        final double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = arr[i] * scale;
        }
        return out;
    }

    /** Euclidean norm of a raw double[] array. */
    private static double norm2Raw(final double[] x) {
        double sum = 0.0;
        for (final double v : x) sum += v * v;
        return Math.sqrt(sum);
    }

    /** Dot product of two raw double[] arrays. */
    private static double dotProductRaw(final double[] a, final double[] b) {
        double sum = 0.0;
        final int n = a.length;
        for (int i = 0; i < n; i++) sum += a[i] * b[i];
        return sum;
    }

    /** Euclidean norm of an Array. */
    private static double norm2(final Array x) {
        final int n = x.size();
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            final double v = x.get(i);
            sum += v * v;
        }
        return Math.sqrt(sum);
    }

    /** Wrap a raw double[] into an Array (zero-copy where possible). */
    private static Array arrayFrom(final double[] data) {
        return new Array(data, data.length);
    }

    /** Extract an Array's contents into a raw double[]. */
    private static double[] doubleArray(final Array arr) {
        final int n = arr.size();
        final double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = arr.get(i);
        return out;
    }
}
