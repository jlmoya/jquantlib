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
 Copyright (C) 2014 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.math.interpolations;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;

/**
 * Backward-flat (in first component) and linear (in second component) 2-D interpolation.
 * <p>
 * Mirrors C++ {@code BackwardflatLinearInterpolation} in
 * {@code ql/math/interpolations/backwardflatlinearinterpolation.hpp} (v1.42.1).
 * <p>
 * The value at {@code (x, y)} is computed as:
 * <ul>
 *   <li>find the cell {@code (i, j)} containing {@code (x, y)};</li>
 *   <li>pick {@code z1 = z[j][i+1]} and {@code z2 = z[j+1][i+1]} (or {@code z[j][0]}, {@code z[j+1][0]}
 *       if {@code x <= xBegin[0]}), backward-flat in {@code x};</li>
 *   <li>linearly interpolate in {@code y} between {@code z1} and {@code z2}.</li>
 * </ul>
 *
 * @author JQuantLib migration contributors
 */
public class BackwardflatLinearInterpolation extends AbstractInterpolation2D {

    public BackwardflatLinearInterpolation(final Array vx, final Array vy, final Matrix mz) {
        super.impl_ = new BackwardflatLinearInterpolationImpl(vx, vy, mz);
    }

    private class BackwardflatLinearInterpolationImpl extends AbstractInterpolation2D.Impl {

        public BackwardflatLinearInterpolationImpl(final Array vx, final Array vy, final Matrix mz) {
            super(vx, vy, mz);
            calculate();
        }

        @Override
        public void calculate() {
            // nothing
        }

        @Override
        public double op(final double x, final double y) /* @ReadOnly */ {
            final int j = locateY(y);
            final double z1;
            final double z2;
            if (x <= vx.get(0)) {
                z1 = mz.get(j, 0);
                z2 = mz.get(j + 1, 0);
            } else {
                final int i = locateX(x);
                if (x == vx.get(i)) {
                    z1 = mz.get(j, i);
                    z2 = mz.get(j + 1, i);
                } else {
                    z1 = mz.get(j, i + 1);
                    z2 = mz.get(j + 1, i + 1);
                }
            }
            final double u = (y - vy.get(j)) / (vy.get(j + 1) - vy.get(j));
            return (1.0 - u) * z1 + u * z2;
        }
    }
}
