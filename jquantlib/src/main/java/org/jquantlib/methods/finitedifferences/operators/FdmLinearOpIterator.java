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
package org.jquantlib.methods.finitedifferences.operators;

import java.util.Arrays;

/**
 * Per-cell iterator for an N-d {@link FdmLinearOpLayout}.
 * <p>
 * Java port of v1.42.1
 * ql/methods/finitedifferences/operators/fdmlinearopiterator.hpp.
 * <p>
 * Holds the current flat {@link #index()} and the matching N-d
 * {@link #coordinates()}. Walks the layout in column-major (row-fastest)
 * order, which matches C++ where {@code coordinates_[0]} is the inner-most
 * loop index.
 *
 * @author Phase 2h WI-1 port
 */
public final class FdmLinearOpIterator {

    private int index;
    private final int[] dim;
    private final int[] coordinates;

    /**
     * "End" iterator constructor (no dimensions; just a flat index).
     * Used by {@link FdmLinearOpLayout#end()}.
     */
    public FdmLinearOpIterator(final int index) {
        this.index = index;
        this.dim = new int[0];
        this.coordinates = new int[0];
    }

    /**
     * "Begin" iterator constructor — coordinates start at the origin.
     */
    public FdmLinearOpIterator(final int[] dim) {
        this.index = 0;
        this.dim = dim.clone();
        this.coordinates = new int[dim.length];
    }

    /**
     * Full-state constructor — used by
     * {@link FdmLinearOpLayout#iterNeighbourhood} to construct a
     * relocated iterator without re-walking from origin.
     */
    public FdmLinearOpIterator(final int[] dim, final int[] coordinates, final int index) {
        this.index = index;
        this.dim = dim.clone();
        this.coordinates = coordinates.clone();
    }

    /**
     * Advance to the next cell in column-major order.
     * <p>
     * Returns {@code this} so callers can chain.
     * Java port of C++ {@code operator++()}.
     */
    public FdmLinearOpIterator increment() {
        ++index;
        for (int i = 0; i < dim.length; ++i) {
            if (++coordinates[i] == dim[i]) {
                coordinates[i] = 0;
            } else {
                break;
            }
        }
        return this;
    }

    /** Flat-index accessor. */
    public int index() {
        return index;
    }

    /**
     * Read-only view into the current N-d coordinates.
     * <p>
     * Note: C++ returns a {@code const std::vector<Size>&}; Java returns
     * the live backing array (callers must not mutate). This avoids the
     * cost of a defensive clone on every per-cell call from
     * TripleBandLinearOp / NinePointLinearOp / FirstDerivativeOp etc.
     */
    public int[] coordinates() {
        return coordinates;
    }

    @Override
    public boolean equals(final Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof FdmLinearOpIterator)) {
            return false;
        }
        return index == ((FdmLinearOpIterator) other).index;
    }

    @Override
    public int hashCode() {
        return index;
    }

    @Override
    public String toString() {
        return "FdmLinearOpIterator[index=" + index + ", coords=" + Arrays.toString(coordinates) + "]";
    }
}
