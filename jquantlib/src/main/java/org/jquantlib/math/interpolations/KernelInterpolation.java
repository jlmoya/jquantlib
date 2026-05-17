/*
 Copyright (C) 2009 Dimitri Reiswich
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

package org.jquantlib.math.interpolations;

import org.jquantlib.QL;
import org.jquantlib.math.KernelFunction;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.LUDecomposition;
import org.jquantlib.math.matrixutilities.Matrix;

/**
 * Kernel interpolation between discrete points.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/math/interpolations/kernelinterpolation.hpp}.
 *
 * <p>Implementation of the kernel interpolation approach described in
 * "Foreign Exchange Risk" by Hakala &amp; Wystup (page 256). The interpolated
 * value at {@code x} is a Nadaraya-Watson type combination:
 * <pre>
 *   y(x) = ( sum_i alpha_i * K(|x - x_i|) ) / ( sum_i K(|x - x_i|) )
 * </pre>
 * where the coefficients {@code alpha} are obtained by solving the linear
 * system {@code M * alpha = y} with
 * <pre>
 *   M[r][c] = K(|x_r - x_c|) / gamma(x_r),
 *   gamma(x) = sum_i K(|x - x_i|).
 * </pre>
 *
 * <p>The kernel is kept general; {@link org.jquantlib.math.GaussianKernel}
 * is the canonical choice (Hakala &amp; Wystup), but any
 * {@link KernelFunction} may be supplied.
 *
 * <p>A failure is reported if {@code ||M*alpha - y||_inf >= epsilon}
 * (default {@code 1e-7}).
 *
 * @author Phase 5e.5b-CFC-d-59 port
 */
public class KernelInterpolation extends AbstractInterpolation {

    /**
     * Construct a kernel interpolation with the default precision
     * {@code 1e-7}.
     */
    public KernelInterpolation(final Array x, final Array y,
                               final KernelFunction kernel) {
        this(x, y, kernel, 1.0e-7);
    }

    /**
     * Construct a kernel interpolation.
     *
     * @param x      x-nodes (must be sorted)
     * @param y      y-values at the x-nodes
     * @param kernel kernel function K(.)
     * @param epsilon allowed slack in the linear-system residual check
     */
    public KernelInterpolation(final Array x, final Array y,
                               final KernelFunction kernel,
                               final double epsilon) {
        this.impl = new KernelInterpolationImpl(x, y, kernel, epsilon);
        this.impl.update();
    }


    //
    // private inner class
    //

    private final class KernelInterpolationImpl extends Impl {

        private final KernelFunction kernel_;
        private final int xSize_;
        private final double invPrec_;
        private final Matrix M_;
        private final Array alphaVec_;
        private final Array yVec_;

        KernelInterpolationImpl(final Array vx, final Array vy,
                                final KernelFunction kernel,
                                final double epsilon) {
            super(vx, vy);
            this.kernel_   = kernel;
            this.xSize_    = vx.size();
            this.invPrec_  = epsilon;
            this.M_        = new Matrix(xSize_, xSize_);
            this.alphaVec_ = new Array(xSize_);
            this.yVec_     = new Array(xSize_);
        }

        @Override
        public void update() {
            updateAlphaVec();
        }

        @Override
        public double op(final double x) {
            double res = 0.0;
            for (int i = 0; i < xSize_; ++i) {
                res += alphaVec_.get(i) * kernelAbs(x, vx.get(i));
            }
            return res / gammaFunc(x);
        }

        @Override
        public double primitive(final double x) {
            throw new UnsupportedOperationException(
                "Primitive calculation not implemented for kernel interpolation");
        }

        @Override
        public double derivative(final double x) {
            throw new UnsupportedOperationException(
                "First derivative calculation not implemented for kernel interpolation");
        }

        @Override
        public double secondDerivative(final double x) {
            throw new UnsupportedOperationException(
                "Second derivative calculation not implemented for kernel interpolation");
        }

        // ---------- helpers ----------

        private double kernelAbs(final double x1, final double x2) {
            return kernel_.op(Math.abs(x1 - x2));
        }

        private double gammaFunc(final double x) {
            double res = 0.0;
            for (int i = 0; i < xSize_; ++i) {
                res += kernelAbs(x, vx.get(i));
            }
            return res;
        }

        private void updateAlphaVec() {
            // Build matrix M and the right-hand-side y-vector.
            for (int rowIt = 0; rowIt < xSize_; ++rowIt) {
                yVec_.set(rowIt, vy.get(rowIt));
                final double tmp = 1.0 / gammaFunc(vx.get(rowIt));
                for (int colIt = 0; colIt < xSize_; ++colIt) {
                    M_.set(rowIt, colIt,
                           kernelAbs(vx.get(rowIt), vx.get(colIt)) * tmp);
                }
            }

            // Solve y = M * alpha for alpha. C++ uses qrSolve, but for a
            // square system LU decomposition produces the same solution
            // and is the only linear solver currently fully wired up in
            // JQuantLib. The residual check below catches the case where
            // the solve degraded numerically.
            final Matrix b = new Matrix(xSize_, 1);
            for (int i = 0; i < xSize_; ++i) {
                b.set(i, 0, yVec_.get(i));
            }
            final Matrix sol = new LUDecomposition(M_).solve(b);
            for (int i = 0; i < xSize_; ++i) {
                alphaVec_.set(i, sol.get(i, 0));
            }

            // residual: |M * alpha - y|_inf < invPrec_
            for (int i = 0; i < xSize_; ++i) {
                double row = 0.0;
                for (int j = 0; j < xSize_; ++j) {
                    row += M_.get(i, j) * alphaVec_.get(j);
                }
                final double diff = Math.abs(row - yVec_.get(i));
                QL.require(diff < invPrec_,
                           "Inversion failed in 1d kernel interpolation");
            }
        }
    }
}
