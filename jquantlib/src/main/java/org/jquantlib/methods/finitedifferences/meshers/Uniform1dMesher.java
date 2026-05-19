/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008 Ralph Schreyer
 Copyright (C) 2008 Klaus Spanderen

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
package org.jquantlib.methods.finitedifferences.meshers;

import org.jquantlib.QL;

/**
 * One-dimensional simple uniform grid mesher.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/meshers/uniform1dmesher.hpp}.
 * <p>
 * Creates a uniformly spaced grid of {@code size} points from {@code start} to {@code end} (inclusive). The step size
 * is {@code (end - start) / (size - 1)}. The boundary cells have {@code dplus = NaN} (last) and {@code dminus = NaN}
 * (first) matching C++ {@code Null<Real>()}.
 *
 * @author Phase 2l Track B port
 */
public class Uniform1dMesher extends Fdm1dMesher {

    /**
     * @param start inclusive lower bound of the grid
     * @param end   inclusive upper bound of the grid (must be &gt; {@code start})
     * @param size  number of grid points (&ge; 2)
     */
    public Uniform1dMesher(final double start, final double end, final int size) {
        super(size);
        QL.require(end > start, "end must be larger than start");

        final double dx = (end - start) / (size - 1);

        for ( int i = 0; i < size - 1; ++i ) {
            locations[i] = start + i * dx;
            dplus[i] = dx;
            dminus[i + 1] = dx;
        }

        locations[size - 1] = end;
        dplus[size - 1] = Double.NaN; // Null<Real>() — no forward step at last node
        dminus[0] = Double.NaN; // Null<Real>() — no backward step at first node
    }
}
