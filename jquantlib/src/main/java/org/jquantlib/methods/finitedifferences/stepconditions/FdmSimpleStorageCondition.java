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
 Copyright (C) 2010 Klaus Spanderen
 */
package org.jquantlib.methods.finitedifferences.stepconditions;

import org.jquantlib.QL;
import org.jquantlib.math.interpolations.BicubicSplineInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.StepCondition;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;

import java.util.List;

/**
 * Bermudan step condition for a simple working-gas storage option.
 *
 * <p>Java port of v1.42.1
 * {@code ql/methods/finitedifferences/stepconditions/fdmsimplestoragecondition.{hpp,cpp}}.</p>
 *
 * <p>At each exercise date the holder can inject (buy) or withdraw (sell)
 * gas, bounded by the {@code changeRate} per-step cap and the {@code [y_min, y_max]} storage-level grid. The condition
 * evaluates the bang-bang choice (buy max / sell max / wait) plus every intermediate storage-grid point in the feasible
 * window using a bilinear interpolant of the rolled-back continuation value over the
 * {@code (x = log-spot, y = storage-level)} mesh.</p>
 *
 * @author Phase 5e.5b-CFC-d-215 port
 */
public class FdmSimpleStorageCondition implements StepCondition< Array > {

    private final List< Double > exerciseTimes_;
    private final FdmMesher mesher_;
    private final FdmInnerValueCalculator calculator_;
    private final double changeRate_;

    private final double[] x_;
    private final double[] y_;

    public FdmSimpleStorageCondition(final List< Double > exerciseTimes, final FdmMesher mesher,
            final FdmInnerValueCalculator calculator, final double changeRate) {
        this.exerciseTimes_ = exerciseTimes;
        this.mesher_ = mesher;
        this.calculator_ = calculator;
        this.changeRate_ = changeRate;

        final int[] dim = mesher_.layout().dim();
        this.x_ = new double[dim[0]];
        this.y_ = new double[dim[1]];

        // Mirrors C++ ctor: walk the layout once and collect the unique x and
        // y axis locations from the cells with coordinates[1]==0 / [0]==0.
        for ( final FdmLinearOpIterator iter : mesher_.layout() ) {
            final int[] coor = iter.coordinates();
            if ( coor[1] == 0 ) {
                x_[coor[0]] = mesher_.location(iter, 0);
            }
            if ( coor[0] == 0 ) {
                y_[coor[1]] = mesher_.location(iter, 1);
            }
        }
    }

    /** Returns first index i with {@code arr[i] > key} (C++ std::upper_bound). */
    private static int upperBound(final double[] arr, final double key) {
        int lo = 0, hi = arr.length;
        while ( lo < hi ) {
            final int mid = (lo + hi) >>> 1;
            if ( arr[mid] <= key ) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    @Override
    public void applyTo(final Array a, final double t) {
        // Trigger only on exact exercise-time matches (C++ std::find).
        boolean isExercise = false;
        for ( final double et : exerciseTimes_ ) {
            if ( et == t ) {
                isExercise = true;
                break;
            }
        }
        if ( !isExercise ) {
            return;
        }

        final int nx = x_.length;
        final int ny = y_.length;
        QL.require(mesher_.layout().size() == a.size(), "inconsistent array dimensions");

        // Reshape a into Matrix(ny, nx). The C++ layout is column-major in
        // x (x_inner-most), so flat index k = j*nx + i  → m(j, i) (with
        // y = row j, x = col i).
        final Matrix m = new Matrix(ny, nx);
        for ( int j = 0; j < ny; ++j ) {
            for ( int i = 0; i < nx; ++i ) {
                m.set(j, i, a.get(j * nx + i));
            }
        }
        final BicubicSplineInterpolation interpl = new BicubicSplineInterpolation(new Array(x_), new Array(y_), m);

        final Array retVal = new Array(a.size());
        final double yFront = y_[0];
        final double yBack = y_[ny - 1];

        for ( final FdmLinearOpIterator iter : mesher_.layout() ) {
            final int[] coor = iter.coordinates();
            final double x = x_[coor[0]];
            final double y = y_[coor[1]];

            final double price = calculator_.innerValue(iter, t);

            final double maxWithDraw = Math.min(y - yFront, changeRate_);
            final double sellPrice = interpl.op(x, y - maxWithDraw);

            final double maxInject = Math.min(yBack - y, changeRate_);
            final double buyPrice = interpl.op(x, y + maxInject);

            // bang-bang-wait strategy: wait, buy max, or sell max.
            double currentValue = a.get(iter.index());
            currentValue = Math.max(currentValue, buyPrice - price * maxInject);
            currentValue = Math.max(currentValue, sellPrice + price * maxWithDraw);

            // Check intermediate storage-grid points inside [y - maxWithDraw,
            // y + maxInject]. Mirrors C++ std::upper_bound walk.
            int yIdx = upperBound(y_, y - maxWithDraw);
            while ( yIdx < ny && y_[yIdx] < y + maxInject ) {
                if ( y_[yIdx] != y ) {
                    final double change = y_[yIdx] - y;
                    final double storagePrice = interpl.op(x, y_[yIdx]);
                    currentValue = Math.max(currentValue, storagePrice - change * price);
                }
                ++yIdx;
            }

            retVal.set(iter.index(), currentValue);
        }

        // In-place copy back into a (C++ `a = retVal`).
        for ( int i = 0; i < a.size(); ++i ) {
            a.set(i, retVal.get(i));
        }
    }
}
