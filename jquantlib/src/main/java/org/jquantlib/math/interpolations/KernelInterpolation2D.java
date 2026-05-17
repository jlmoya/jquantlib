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
 * 2-D kernel interpolation between discrete points.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/math/interpolations/kernelinterpolation2d.hpp}.
 *
 * <p>Grid layout (as in C++):
 * <pre>
 *   zData = [ (x1,y1) (x1,y2) ... (x1,yM)
 *             (x2,y1) (x2,y2) ... (x2,yM)
 *             ...
 *             (xN,y1) (xN,y2) ... (xN,yM) ]
 * </pre>
 * The kernel acts on the 2-norm distance between query and grid points;
 * the coefficients are determined by solving an {@code (N*M) x (N*M)}
 * linear system mirroring the 1-D construction.
 *
 * @author Phase 5e.5b-CFC-d-59 port
 */
public class KernelInterpolation2D extends AbstractInterpolation2D {

    public KernelInterpolation2D(final Array x, final Array y, final Matrix zData,
                                 final KernelFunction kernel) {
        this.impl_ = new KernelInterpolation2DImpl(x, y, zData, kernel);
        this.impl_.calculate();
    }

    /**
     * Set the precision required of the residual check that follows the
     * linear solve. Default is {@code 1e-10}.
     */
    public void setInverseResultPrecision(final double invPrec) {
        ((KernelInterpolation2DImpl) impl_).invPrec_ = invPrec;
    }


    //
    // private inner class
    //

    private final class KernelInterpolation2DImpl extends Impl {

        private final KernelFunction kernel_;
        private final int xSize_, ySize_, xySize_;
        private double invPrec_ = 1.0e-10;
        private final Matrix M_;
        private final Array alphaVec_;
        private final Array yVec_;

        KernelInterpolation2DImpl(final Array vx, final Array vy,
                                  final Matrix mz, final KernelFunction kernel) {
            super(vx, vy, mz);
            QL.require(mz.rows()    == vx.size(),
                       "Z value matrix has wrong number of rows");
            QL.require(mz.columns() == vy.size(),
                       "Z value matrix has wrong number of columns");

            this.kernel_   = kernel;
            this.xSize_    = vx.size();
            this.ySize_    = vy.size();
            this.xySize_   = xSize_ * ySize_;
            this.alphaVec_ = new Array(xySize_);
            this.yVec_     = new Array(xySize_);
            this.M_        = new Matrix(xySize_, xySize_);
        }

        @Override
        public void calculate() {
            updateAlphaVec();
        }

        @Override
        public double op(final double x1, final double x2) {
            double res = 0.0;
            int cnt = 0;
            for (int j = 0; j < ySize_; ++j) {
                for (int i = 0; i < xSize_; ++i) {
                    res += alphaVec_.get(cnt) * kernelAbs(x1, x2, vx.get(i), vy.get(j));
                    ++cnt;
                }
            }
            return res / gammaFunc(x1, x2);
        }

        // ---------- helpers ----------

        /** Returns {@code K(||(x1,x2) - (yx,yy)||_2)}. */
        private double kernelAbs(final double x1, final double x2,
                                 final double yx, final double yy) {
            final double dx = x1 - yx;
            final double dy = x2 - yy;
            return kernel_.op(Math.sqrt(dx * dx + dy * dy));
        }

        private double gammaFunc(final double x1, final double x2) {
            double res = 0.0;
            for (int j = 0; j < ySize_; ++j) {
                for (int i = 0; i < xSize_; ++i) {
                    res += kernelAbs(x1, x2, vx.get(i), vy.get(j));
                }
            }
            return res;
        }

        private void updateAlphaVec() {
            int rowCnt = 0;
            for (int j = 0; j < ySize_; ++j) {
                for (int i = 0; i < xSize_; ++i) {

                    yVec_.set(rowCnt, mz.get(i, j));
                    final double xkX = vx.get(i);
                    final double xkY = vy.get(j);
                    final double tmp = 1.0 / gammaFunc(xkX, xkY);

                    int colCnt = 0;
                    for (int jM = 0; jM < ySize_; ++jM) {
                        for (int iM = 0; iM < xSize_; ++iM) {
                            M_.set(rowCnt, colCnt,
                                   kernelAbs(xkX, xkY,
                                             vx.get(iM), vy.get(jM)) * tmp);
                            ++colCnt;
                        }
                    }
                    ++rowCnt;
                }
            }

            // Solve y = M * alpha for alpha. C++ uses qrSolve; LU
            // decomposition produces the same solution for this square
            // system.
            final Matrix b = new Matrix(xySize_, 1);
            for (int i = 0; i < xySize_; ++i) {
                b.set(i, 0, yVec_.get(i));
            }
            final Matrix sol = new LUDecomposition(M_).solve(b);
            for (int i = 0; i < xySize_; ++i) {
                alphaVec_.set(i, sol.get(i, 0));
            }

            // residual check: |M * alpha - y|_inf < invPrec_
            for (int i = 0; i < xySize_; ++i) {
                double row = 0.0;
                for (int j = 0; j < xySize_; ++j) {
                    row += M_.get(i, j) * alphaVec_.get(j);
                }
                final double diff = Math.abs(row - yVec_.get(i));
                QL.require(diff < invPrec_,
                           "inversion failed in 2d kernel interpolation");
            }
        }
    }
}
