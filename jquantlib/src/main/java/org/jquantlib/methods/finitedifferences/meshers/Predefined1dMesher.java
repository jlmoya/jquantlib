/*
 Copyright (C) 2013 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

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
package org.jquantlib.methods.finitedifferences.meshers;

/**
 * One-dimensional mesher built from a caller-supplied set of points.
 *
 * <p>Java port of v1.42.1
 * {@code ql/methods/finitedifferences/meshers/predefined1dmesher.hpp} (header-only in C++).
 *
 * <p>The locations array is copied verbatim; the {@code dplus} / {@code dminus}
 * arrays are populated as adjacent differences with {@link Double#NaN} sentinels at the ends (mirroring C++
 * {@code Null<Real>()}).
 *
 * @author Phase 5h.5-RND-b port
 */
public class Predefined1dMesher extends Fdm1dMesher {

    /**
     * Build a 1D mesh from the supplied locations. The input array is copied defensively (callers may mutate or reuse
     * it after construction).
     */
    public Predefined1dMesher(final double[] x) {
        super(x.length);

        final int n = x.length;
        // Defensive copy of the caller's array into the protected backing buffer.
        System.arraycopy(x, 0, this.locations, 0, n);

        // dplus/dminus: adjacent differences with NaN sentinels at the ends.
        // Matches C++ predefined1dmesher.hpp:40-43.
        for ( int i = 0; i + 1 < n; ++i ) {
            this.dplus[i] = x[i + 1] - x[i];
            this.dminus[i + 1] = this.dplus[i];
        }
        this.dplus[n - 1] = Double.NaN;
        this.dminus[0] = Double.NaN;
    }
}
