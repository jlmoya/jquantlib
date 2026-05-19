/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is released under the BSD License.

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
 Copyright (C) 2003 Ferdinando Ametrano
 Copyright (C) 2004 StatPro Italia srl
 Copyright (C) 2009 Bernd Engelmann

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.experimental.inflation;

import org.jquantlib.QL;
import org.jquantlib.math.interpolations.AbstractInterpolation2D;
import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.interpolations.CubicInterpolation.BoundaryCondition;
import org.jquantlib.math.interpolations.CubicInterpolation.DerivativeApprox;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.Interpolation2D;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;

/**
 * Polynomial-in-y, cubic-spline-in-x 2D interpolation between discrete points.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::Polynomial2DSpline}
 * ({@code ql/experimental/inflation/polynomial2Dspline.hpp}).
 *
 * <p>Algorithm:
 * <ol>
 *   <li>For each x-column {@code i}, fit a Parabolic (local cubic with parabolic
 *       first-derivative approximation) interpolation in the y direction using
 *       the z-values along that column.</li>
 *   <li>To evaluate at {@code (x, y)}, first build a cross-section by evaluating
 *       each column polynomial at {@code y}, then fit a natural cubic spline over
 *       x on that cross-section and evaluate at {@code x}.</li>
 * </ol>
 *
 * <p><b>Matrix convention:</b> {@code mz[row][col]} = z at (x[col], y[row]),
 * i.e., rows are indexed by y, columns by x. This matches the Java
 * {@link AbstractInterpolation2D.Impl} convention used throughout this codebase.
 *
 * <p><b>Prerequisites:</b> x and y values must be sorted in ascending order.
 *
 * @author JQuantLib migration contributors (Phase 2s L0)
 * @see org.jquantlib.experimental.inflation.Polynomial2DSpline.Polynomial interpolator factory
 */
public class Polynomial2DSpline extends AbstractInterpolation2D {

    /**
     * Constructs a 2D polynomial/spline interpolation from the given axes and data matrix.
     *
     * @param vx x-axis values (size N, sorted ascending)
     * @param vy y-axis values (size M, sorted ascending)
     * @param mz z data matrix (M rows x N columns), where mz[y_index][x_index] = z(x,y)
     */
    public Polynomial2DSpline(final Array vx, final Array vy, final Matrix mz) {
        super.impl_ = new Polynomial2DSplineImpl(vx, vy, mz);
    }

    //
    // private inner classes
    //

    /**
     * Factory for creating {@link Polynomial2DSpline} instances.
     *
     * <p>Mirrors C++ {@code QuantLib::Polynomial} factory class.
     */
    public static class Polynomial implements Interpolation2D.Interpolator2D {

        @Override
        public Interpolation2D interpolate(final Array vx, final Array vy, final Matrix mz) {
            return new Polynomial2DSpline(vx, vy, mz);
        }

    }

    //
    // Interpolator2D factory
    //

    /**
     * Implementation of Polynomial2DSpline.
     *
     * <p>Mirrors C++ {@code detail::Polynomial2DSplineImpl}.
     */
    private class Polynomial2DSplineImpl extends AbstractInterpolation2D.Impl {

        /** One parabolic interpolation per x-column; interpolates in y. */
        private Interpolation[] polynomials_;

        public Polynomial2DSplineImpl(final Array vx, final Array vy, final Matrix mz) {
            super(vx, vy, mz);
            calculate();
        }

        @Override
        public void calculate() {
            // C++ checks: zData_.rows() == yEnd_ - yBegin_
            QL.require(mz.rows() == vy.size(),
                    "size mismatch of the interpolation data: mz.rows()=" + mz.rows() + " vy.size()=" + vy.size());

            // One parabolic polynomial per x-column; each interpolates along y.
            // C++: polynomials_.reserve(zData_.columns())
            //      polynomials_.push_back(Parabolic(yBegin_, yEnd_, zData_.column_begin(i)))
            polynomials_ = new Interpolation[mz.cols()];
            for ( int i = 0; i < mz.cols(); ++i ) {
                // Extract column i: z-values at x[i] for all y points.
                // mz.rangeCol(i) is a view; materialise to a dense array to
                // ensure CubicInterpolation's raw-array access works correctly
                // (same fix as BicubicSplineInterpolation for row views).
                final Array colView = mz.rangeCol(i);
                final double[] colData = new double[colView.size()];
                for ( int k = 0; k < colView.size(); ++k ) {
                    colData[k] = colView.get(k);
                }
                // Parabolic = CubicInterpolation(Parabolic, false, SecondDerivative, ...)
                // C++ Parabolic constructor: CubicInterpolation(Parabolic, false,
                //   SecondDerivative, 0.0, SecondDerivative, 0.0) — but the
                // boundary conditions are irrelevant for local (Parabolic) scheme.
                // Use NotAKnot for non-Spline da to avoid invalid unused path.
                // C++ Parabolic class sets: da=Parabolic, monotone=false,
                //   leftCondition=SecondDerivative/0, rightCondition=SecondDerivative/0
                polynomials_[i] = new CubicInterpolation(vy, new Array(colData), DerivativeApprox.Parabolic, false,
                        BoundaryCondition.SecondDerivative, 0.0, BoundaryCondition.SecondDerivative, 0.0);
            }
        }

        @Override
        public double op(final double x, final double y) {
            // C++: for each column polynomial, evaluate at y (with extrapolation).
            // section[i] = z at (x[i], y).
            final double[] section = new double[polynomials_.length];
            for ( int i = 0; i < polynomials_.length; ++i ) {
                section[i] = polynomials_[i].op(y, true);
            }

            // C++ requires section.size() == xEnd_ - xBegin_
            QL.require(section.length == vx.size(),
                    "size mismatch of the interpolation data: section.length=" + section.length + " vx.size()="
                            + vx.size());

            // Fit a natural cubic spline over x on the cross-section and evaluate.
            // C++: CubicInterpolation(xBegin_, xEnd_, section.begin(),
            //       Spline, true, SecondDerivative, 0.0, SecondDerivative, 0.0)
            final CubicInterpolation spline = new CubicInterpolation(vx, new Array(section), DerivativeApprox.Spline,
                    true, BoundaryCondition.SecondDerivative, 0.0, BoundaryCondition.SecondDerivative, 0.0);
            return spline.op(x, true);
        }

    }

}
