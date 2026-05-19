/*
 Copyright (C) 2009 Ralph Schreyer
 Copyright (C) 2009 Klaus Spanderen
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

/*! \file bicgstab.hpp
    \brief Biconjugate gradient stabilized method
    Ported from QuantLib v1.42.1 — Phase 2l Track A.
*/

package org.jquantlib.math.matrixutilities;

import java.util.function.Function;

/**
 * Biconjugate Gradient STABilized (BiCGStab) iterative solver.
 *
 * <p>Solves the linear system A*x = b using the BiCGStab algorithm.
 * Optionally uses a preconditioner M (applied as M^{-1}*x).
 *
 * <p>Ported from QuantLib v1.42.1
 * {@code ql/math/matrixutilities/bicgstab.hpp} and {@code .cpp}.
 *
 * @see <a href="http://www.netlib.org/templates/templates.pdf">
 * Templates for the Solution of Linear Systems</a>
 */
public class BiCGStab {

    private final MatrixMult A_;
    private final MatrixMult M_;

    // -----------------------------------------------------------------------
    private final int maxIter_;
    private final double relTol_;
    /**
     * Constructs a BiCGStab solver.
     *
     * @param A       matrix-vector product operator x -&gt; A*x
     * @param maxIter maximum number of iterations
     * @param relTol  relative tolerance (convergence when ‖r‖/‖b‖ &lt; relTol)
     */
    public BiCGStab(final MatrixMult A, final int maxIter, final double relTol) {
        this(A, maxIter, relTol, null);
    }
    /**
     * Constructs a BiCGStab solver with an optional preconditioner.
     *
     * @param A              matrix-vector product operator x -&gt; A*x
     * @param maxIter        maximum number of iterations
     * @param relTol         relative tolerance
     * @param preConditioner preconditioner operator x -&gt; M^{-1}*x, or {@code null}
     */
    public BiCGStab(final MatrixMult A, final int maxIter, final double relTol, final MatrixMult preConditioner) {
        this.A_ = A;
        this.maxIter_ = maxIter;
        this.relTol_ = relTol;
        this.M_ = preConditioner;
    }

    /** In-place: out[i] += scale * src[i] */
    private static void addScaled(final Array out, final Array src, final double scale) {
        final int n = out.size();
        for ( int i = 0; i < n; i++ ) {
            out.set(i, out.get(i) + scale * src.get(i));
        }
    }

    /** Euclidean norm of an Array: sqrt(sum x_i^2). */
    private static double norm2(final Array x) {
        final int n = x.size();
        double sum = 0.0;
        for ( int i = 0; i < n; i++ ) {
            final double v = x.get(i);
            sum += v * v;
        }
        return Math.sqrt(sum);
    }

    /** Dot product of two Arrays. */
    private static double dotProduct(final Array a, final Array b) {
        return a.dotProduct(b);
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

    // -----------------------------------------------------------------------
    // Arithmetic helpers — avoids creating an extra Array for x += alpha*v

    /**
     * Solve A*x = b, optionally starting from an initial guess x0.
     *
     * @param b  right-hand side vector
     * @param x0 initial guess, or {@code null} (or empty array) for zero start
     * @return solve result
     * @throws IllegalStateException if max iterations exceeded or convergence failed
     */
    public Result solve(final Array b, final Array x0) {
        final double bnorm2 = norm2(b);
        if ( bnorm2 == 0.0 ) {
            return new Result(0, 0.0, b.clone());
        }

        // x = x0 or zero vector
        final Array x = (x0 != null && x0.size() > 0) ? x0.clone() : new Array(b.size());

        // r = b - A*x
        Array r = b.sub(A_.apply(x));

        final Array rTld = r.clone();
        Array p = null;
        Array v = null;
        double omega = 1.0;
        double rhoTld = 1.0;
        double alpha = 0.0;
        double error = norm2(r) / bnorm2;

        int i = 0;
        for ( ; i < maxIter_ && error >= relTol_; ++i ) {
            final double rho = dotProduct(rTld, r);
            if ( rho == 0.0 || omega == 0.0 ) {
                break;
            }

            if ( i != 0 ) {
                final double beta = (rho / rhoTld) * (alpha / omega);
                // p = r + beta * (p - omega * v)
                p = r.add(p.sub(v.mul(omega)).mul(beta));
            } else {
                p = r.clone();
            }

            final Array pTld = (M_ == null) ? p : M_.apply(p);
            v = A_.apply(pTld);

            alpha = rho / dotProduct(rTld, v);
            // s = r - alpha*v
            final Array s = r.sub(v.mul(alpha));

            if ( norm2(s) < relTol_ * bnorm2 ) {
                // x += alpha * pTld
                addScaled(x, pTld, alpha);
                error = norm2(s) / bnorm2;
                break;
            }

            final Array sTld = (M_ == null) ? s : M_.apply(s);
            final Array t = A_.apply(sTld);
            omega = dotProduct(t, s) / dotProduct(t, t);

            // x += alpha*pTld + omega*sTld
            addScaled(x, pTld, alpha);
            addScaled(x, sTld, omega);

            // r = s - omega*t
            r = s.sub(t.mul(omega));
            error = norm2(r) / bnorm2;
            rhoTld = rho;
        }

        if ( i >= maxIter_ ) {
            throw new IllegalStateException("BiCGStab: max number of iterations exceeded");
        }
        if ( error >= relTol_ ) {
            throw new IllegalStateException("BiCGStab: could not converge");
        }

        return new Result(i, error, x);
    }

    /** Operator type: maps an Array to an Array (e.g., x -&gt; A*x). */
    public interface MatrixMult extends Function< Array, Array > {
        // inherits: Array apply(Array x);
    }

    /** Result of a BiCGStab solve. */
    public static final class Result {
        /** Number of iterations performed. */
        public final int iterations;
        /** Final relative residual error ‖r‖/‖b‖. */
        public final double error;
        /** Solution vector. */
        public final Array x;

        public Result(final int iterations, final double error, final Array x) {
            this.iterations = iterations;
            this.error = error;
            this.x = x;
        }
    }
}
