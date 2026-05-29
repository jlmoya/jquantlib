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

package org.jquantlib.methods.finitedifferences.meshers;

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpLayout;
import org.jquantlib.util.Pair;

/**
 * Concrete multi-dimensional {@link FdmMesher} over a uniform grid.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/meshers/uniformgridmesher.{hpp,cpp}}.
 *
 * <p>Each direction {@code i} is meshed uniformly between {@code boundaries[i].first} and {@code boundaries[i].second}
 * with {@code layout.dim()[i]} points, so the constant per-direction spacing is
 * {@code dx[i] = (second - first) / (dim[i] - 1)} and {@code locations[i][j] = first + j*dx[i]}.
 *
 * @author JQuantLib gap-fdm port
 */
public class UniformGridMesher implements FdmMesher {

    private final FdmLinearOpLayout layout_;
    private final double[] dx_;
    private final double[][] locations_;

    /**
     * Primary constructor mirroring C++ ({@code std::vector<std::pair<Real,Real>>} boundaries).
     */
    public UniformGridMesher(final FdmLinearOpLayout layout, final List< Pair< Double, Double > > boundaries) {
        this.layout_ = layout;
        final int[] dim = layout.dim();
        QL.require(boundaries.size() == dim.length, "inconsistent boundaries given");

        this.dx_ = new double[dim.length];
        this.locations_ = new double[dim.length][];
        for ( int i = 0; i < dim.length; ++i ) {
            final double lo = boundaries.get(i).first();
            final double hi = boundaries.get(i).second();
            dx_[i] = (hi - lo) / (dim[i] - 1);
            locations_[i] = new double[dim[i]];
            for ( int j = 0; j < dim[i]; ++j ) {
                locations_[i][j] = lo + j * dx_[i];
            }
        }
    }

    /**
     * Convenience constructor: {@code boundaries[i] = {lo, hi}}. Equivalent to the {@link Pair}-based constructor.
     */
    public UniformGridMesher(final FdmLinearOpLayout layout, final double[][] boundaries) {
        this.layout_ = layout;
        final int[] dim = layout.dim();
        QL.require(boundaries.length == dim.length, "inconsistent boundaries given");

        this.dx_ = new double[dim.length];
        this.locations_ = new double[dim.length][];
        for ( int i = 0; i < dim.length; ++i ) {
            final double lo = boundaries[i][0];
            final double hi = boundaries[i][1];
            dx_[i] = (hi - lo) / (dim[i] - 1);
            locations_[i] = new double[dim[i]];
            for ( int j = 0; j < dim[i]; ++j ) {
                locations_[i][j] = lo + j * dx_[i];
            }
        }
    }

    @Override
    public double dplus(final FdmLinearOpIterator iter, final int direction) {
        return dx_[direction];
    }

    @Override
    public double dminus(final FdmLinearOpIterator iter, final int direction) {
        return dx_[direction];
    }

    @Override
    public double location(final FdmLinearOpIterator iter, final int direction) {
        return locations_[direction][iter.coordinates()[direction]];
    }

    @Override
    public Array locations(final int direction) {
        final Array retVal = new Array(layout_.size());
        for ( final FdmLinearOpIterator iter : layout_ ) {
            retVal.set(iter.index(), locations_[direction][iter.coordinates()[direction]]);
        }
        return retVal;
    }

    @Override
    public FdmLinearOpLayout layout() {
        return layout_;
    }
}
