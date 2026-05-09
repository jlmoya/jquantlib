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
 Copyright (C) 2012 Peter Caspers
 */
package org.jquantlib.experimental.finitedifferences;

import org.jquantlib.QL;
import org.jquantlib.math.Closeness;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;

/**
 * One-dimensional grid mesher combining two existing ones.
 * <p>
 * Java port of v1.42.1
 * {@code ql/experimental/finitedifferences/glued1dmesher.{hpp,cpp}}.
 * <p>
 * If the rightmost point of {@code leftMesher} equals the leftmost point of
 * {@code rightMesher}, that point is included only once (the two meshes share
 * a boundary node). Otherwise all points from both meshes are concatenated.
 *
 * @author Phase 4n WI port
 */
public class Glued1dMesher extends Fdm1dMesher {

    private final boolean commonPoint_;

    public Glued1dMesher(final Fdm1dMesher leftMesher, final Fdm1dMesher rightMesher) {
        super(leftMesher.locations().length + rightMesher.locations().length
              - (Closeness.isClose(
                      leftMesher.locations()[leftMesher.locations().length - 1],
                      rightMesher.locations()[0]) ? 1 : 0));
        this.commonPoint_ = Closeness.isClose(
                leftMesher.locations()[leftMesher.locations().length - 1],
                rightMesher.locations()[0]);

        QL.require(
                leftMesher.locations()[leftMesher.locations().length - 1]
                        <= rightMesher.locations()[0],
                "left mesher's rightmost point may not be greater than"
                        + " right mesher's leftmost point");

        final double[] left = leftMesher.locations();
        final double[] right = rightMesher.locations();
        // Copy left mesher locations
        System.arraycopy(left, 0, locations, 0, left.length);
        // Copy right mesher locations starting from position
        // (left.length) — skip the first element of right when there's a
        // common point.
        final int rightStart = commonPoint_ ? 1 : 0;
        System.arraycopy(right, rightStart, locations, left.length,
                right.length - rightStart);

        // Recompute dplus / dminus from the merged locations.
        for (int i = 0; i < locations.length - 1; ++i) {
            final double diff = locations[i + 1] - locations[i];
            dplus[i] = diff;
            dminus[i + 1] = diff;
        }
        // Sentinel for the past-the-edge cells (matches C++ Null<Real>()).
        dplus[locations.length - 1] = Double.NaN;
        dminus[0] = Double.NaN;
    }
}
