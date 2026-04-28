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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpLayout;

/**
 * {@link FdmMesher} composed of N {@link Fdm1dMesher} 1D meshes.
 * <p>
 * Java port of v1.42.1
 * ql/methods/finitedifferences/meshers/fdmmeshercomposite.{hpp,cpp}.
 * <p>
 * Each direction's per-cell {@code dplus / dminus / location} is delegated to
 * the corresponding 1D mesh, indexed by the iterator's coordinate along that
 * direction. The {@link FdmLinearOpLayout} is built from the per-direction
 * sizes when not supplied by the caller.
 *
 * @author Phase 2h WI-1 port
 */
public class FdmMesherComposite implements FdmMesher {

    private final FdmLinearOpLayout layout;
    private final List<Fdm1dMesher> meshers;

    /**
     * Build the layout from the per-direction sizes of the supplied
     * {@code meshers}. Mirrors the anonymous-namespace
     * {@code getLayoutFromMeshers} helper in C++.
     */
    private static FdmLinearOpLayout layoutFrom(final List<Fdm1dMesher> meshers) {
        final int[] dim = new int[meshers.size()];
        for (int i = 0; i < dim.length; ++i) {
            dim[i] = meshers.get(i).size();
        }
        return new FdmLinearOpLayout(dim);
    }

    /**
     * Primary constructor — the caller supplies the layout. The 1D meshes'
     * sizes must match {@code layout.dim()}.
     */
    public FdmMesherComposite(final FdmLinearOpLayout layout,
                              final List<Fdm1dMesher> meshers) {
        this.layout = layout;
        this.meshers = Collections.unmodifiableList(Arrays.asList(meshers.toArray(new Fdm1dMesher[0])));
        for (int i = 0; i < meshers.size(); ++i) {
            QL.require(meshers.get(i).size() == layout.dim()[i],
                    "size of 1d mesher " + i + " does not fit to layout");
        }
    }

    /** Convenience constructor — derives layout from the meshers. */
    public FdmMesherComposite(final List<Fdm1dMesher> meshers) {
        this(layoutFrom(meshers), meshers);
    }

    /** Single-mesh convenience constructor. */
    public FdmMesherComposite(final Fdm1dMesher mesher) {
        this(Arrays.asList(mesher));
    }

    /** Two-mesh convenience constructor. */
    public FdmMesherComposite(final Fdm1dMesher m1, final Fdm1dMesher m2) {
        this(Arrays.asList(m1, m2));
    }

    /** Three-mesh convenience constructor. */
    public FdmMesherComposite(final Fdm1dMesher m1, final Fdm1dMesher m2, final Fdm1dMesher m3) {
        this(Arrays.asList(m1, m2, m3));
    }

    /** Four-mesh convenience constructor. */
    public FdmMesherComposite(final Fdm1dMesher m1, final Fdm1dMesher m2,
                              final Fdm1dMesher m3, final Fdm1dMesher m4) {
        this(Arrays.asList(m1, m2, m3, m4));
    }

    @Override
    public final double dplus(final FdmLinearOpIterator iter, final int direction) {
        return meshers.get(direction).dplus(iter.coordinates()[direction]);
    }

    @Override
    public final double dminus(final FdmLinearOpIterator iter, final int direction) {
        return meshers.get(direction).dminus(iter.coordinates()[direction]);
    }

    @Override
    public final double location(final FdmLinearOpIterator iter, final int direction) {
        return meshers.get(direction).location(iter.coordinates()[direction]);
    }

    @Override
    public final Array locations(final int direction) {
        final Array retVal = new Array(layout.size());
        final double[] perDimLocations = meshers.get(direction).locations();
        for (final FdmLinearOpIterator iter : layout) {
            retVal.set(iter.index(), perDimLocations[iter.coordinates()[direction]]);
        }
        return retVal;
    }

    @Override
    public final FdmLinearOpLayout layout() {
        return layout;
    }

    /** Read-only view of the underlying 1D meshes. */
    public final List<Fdm1dMesher> getFdm1dMeshers() {
        return meshers;
    }
}
