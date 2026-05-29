/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008 Ralph Schreyer
 Copyright (C) 2008 Klaus Spanderen
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

package org.jquantlib.methods.finitedifferences.utilities;

import org.jquantlib.QL;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpLayout;

/**
 * Helper class to extract the flat indices on a given boundary of an Fdm layout.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/utilities/fdmindicesonboundary.{hpp,cpp}}.
 *
 * <p>Given an {@link FdmLinearOpLayout}, a {@code direction}, and a
 * {@link BoundaryCondition.Side}, collects the flat indices of all grid cells lying on the lower
 * ({@code coordinate == 0}) or upper ({@code coordinate == dim[direction]-1}) boundary in that direction. Used by
 * {@link FdmDirichletBoundary} (and conceptually by the time-dependent variants).
 *
 * @author JQuantLib gap-fdm port
 */
public class FdmIndicesOnBoundary {

    private final int[] indices_;

    public FdmIndicesOnBoundary(final FdmLinearOpLayout layout, final int direction,
            final BoundaryCondition.Side side) {

        // C++: newDim = layout->dim(); newDim[direction] = 1; hyperSize = product(newDim).
        // This is the number of cells on the chosen boundary hyperplane.
        final int[] newDim = layout.dim().clone();
        newDim[direction] = 1;
        int hyperSize = 1;
        for ( final int d : newDim ) {
            hyperSize *= d;
        }
        this.indices_ = new int[hyperSize];

        final int bdryCoord = (side == BoundaryCondition.Side.Lower)
                ? 0
                : layout.dim()[direction] - 1;

        int i = 0;
        for ( final FdmLinearOpIterator iter : layout ) {
            if ( ((side == BoundaryCondition.Side.Lower) && iter.coordinates()[direction] == 0)
                    || ((side == BoundaryCondition.Side.Upper)
                            && iter.coordinates()[direction] == bdryCoord) ) {
                QL.require(hyperSize > i, "index mismatch");
                indices_[i++] = iter.index();
            }
        }
    }

    /** Flat indices of the cells on the requested boundary (read-only; callers must not mutate). */
    public int[] getIndices() {
        return indices_;
    }
}
