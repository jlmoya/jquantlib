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

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Memory layout of an N-d Fdm linear operator.
 * <p>
 * Java port of v1.42.1
 * ql/methods/finitedifferences/operators/fdmlinearoplayout.{hpp,cpp}.
 * <p>
 * Stores the per-direction extents in {@link #dim()} and the column-major
 * stride in {@link #spacing()}. Provides a flat-index iterator and
 * boundary-reflecting neighborhood lookups used by the difference operators.
 *
 * @author Phase 2h WI-1 port
 */
public final class FdmLinearOpLayout implements Iterable<FdmLinearOpIterator> {

    private final int size;
    private final int[] dim;
    private final int[] spacing;

    public FdmLinearOpLayout(final int[] dim) {
        this.dim = dim.clone();
        this.spacing = new int[dim.length];
        // partial_sum with multiplication: spacing[0]=1; spacing[k]=prod(dim[0..k-1]).
        spacing[0] = 1;
        for (int i = 1; i < dim.length; ++i) {
            spacing[i] = spacing[i - 1] * dim[i - 1];
        }
        this.size = spacing[dim.length - 1] * dim[dim.length - 1];
    }

    /** Total number of grid cells (= product of all dimensions). */
    public int size() {
        return size;
    }

    /** Per-direction grid extents (read-only view). */
    public int[] dim() {
        return dim;
    }

    /** Per-direction column-major strides (read-only view). */
    public int[] spacing() {
        return spacing;
    }

    /**
     * Map an N-d coordinate to its flat index.
     * Java port of {@code FdmLinearOpLayout::index} —
     * {@code inner_product(coords, spacing, 0)}.
     */
    public int index(final int[] coordinates) {
        int sum = 0;
        for (int i = 0; i < coordinates.length; ++i) {
            sum += coordinates[i] * spacing[i];
        }
        return sum;
    }

    /**
     * Map a flat index to its N-d coordinate.
     * Provided for the Java side only — C++ does not expose this directly,
     * but it is convenient for tests and for boundary-condition wiring.
     */
    public int[] coordinates(final int flat) {
        final int[] coords = new int[dim.length];
        int rem = flat;
        for (int i = dim.length - 1; i >= 0; --i) {
            coords[i] = rem / spacing[i];
            rem -= coords[i] * spacing[i];
        }
        return coords;
    }

    /**
     * Reflecting-boundary neighbour at {@code coords[i] + offset} along
     * direction {@code i}. Java port of single-axis
     * {@code FdmLinearOpLayout::neighbourhood}.
     */
    public int neighbourhood(final FdmLinearOpIterator iter, final int direction, final int offset) {
        final int[] coords = iter.coordinates();
        final int myIndex = iter.index() - coords[direction] * spacing[direction];

        int coorOffset = coords[direction] + offset;
        if (coorOffset < 0) {
            coorOffset = -coorOffset;
        } else if (coorOffset >= dim[direction]) {
            coorOffset = 2 * (dim[direction] - 1) - coorOffset;
        }
        return myIndex + coorOffset * spacing[direction];
    }

    /**
     * Reflecting-boundary neighbour at {@code (coords[i1]+off1, coords[i2]+off2)}.
     * Java port of two-axis {@code FdmLinearOpLayout::neighbourhood}.
     */
    public int neighbourhood(final FdmLinearOpIterator iter,
                             final int i1, final int offset1,
                             final int i2, final int offset2) {
        final int[] coords = iter.coordinates();
        final int myIndex = iter.index()
                - coords[i1] * spacing[i1]
                - coords[i2] * spacing[i2];

        int coorOffset1 = coords[i1] + offset1;
        if (coorOffset1 < 0) {
            coorOffset1 = -coorOffset1;
        } else if (coorOffset1 >= dim[i1]) {
            coorOffset1 = 2 * (dim[i1] - 1) - coorOffset1;
        }
        int coorOffset2 = coords[i2] + offset2;
        if (coorOffset2 < 0) {
            coorOffset2 = -coorOffset2;
        } else if (coorOffset2 >= dim[i2]) {
            coorOffset2 = 2 * (dim[i2] - 1) - coorOffset2;
        }
        return myIndex
                + coorOffset1 * spacing[i1]
                + coorOffset2 * spacing[i2];
    }

    /**
     * Construct an iterator at the reflected neighbour of {@code iter}
     * along direction {@code i}. Java port of
     * {@code FdmLinearOpLayout::iter_neighbourhood}.
     */
    public FdmLinearOpIterator iterNeighbourhood(final FdmLinearOpIterator iter,
                                                 final int direction, final int offset) {
        final int[] coords = iter.coordinates().clone();
        int coorOffset = coords[direction] + offset;
        if (coorOffset < 0) {
            coorOffset = -coorOffset;
        } else if (coorOffset >= dim[direction]) {
            coorOffset = 2 * (dim[direction] - 1) - coorOffset;
        }
        coords[direction] = coorOffset;
        return new FdmLinearOpIterator(dim, coords, index(coords));
    }

    /** Begin iterator (matches C++ {@code FdmLinearOpLayout::begin()}). */
    public FdmLinearOpIterator begin() {
        return new FdmLinearOpIterator(dim);
    }

    /** End iterator (matches C++ {@code FdmLinearOpLayout::end()}). */
    public FdmLinearOpIterator end() {
        return new FdmLinearOpIterator(size);
    }

    /**
     * For-each support — yields snapshots of an internal walking iterator.
     * The returned iterators are the same {@link FdmLinearOpIterator} object
     * (recycled across iterations) to mirror C++ range-for semantics where
     * {@code *iter} aliases the iterator itself.
     */
    @Override
    public Iterator<FdmLinearOpIterator> iterator() {
        return new Iterator<FdmLinearOpIterator>() {
            private final FdmLinearOpIterator cur = begin();

            @Override
            public boolean hasNext() {
                return cur.index() < size;
            }

            @Override
            public FdmLinearOpIterator next() {
                if (cur.index() >= size) {
                    throw new NoSuchElementException();
                }
                final FdmLinearOpIterator snapshot =
                        new FdmLinearOpIterator(dim, cur.coordinates(), cur.index());
                cur.increment();
                return snapshot;
            }
        };
    }
}
