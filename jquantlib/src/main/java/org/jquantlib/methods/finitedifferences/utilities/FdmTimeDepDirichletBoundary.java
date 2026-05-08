/*
 Copyright (C) 2012 Peter Caspers
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOp;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpLayout;

/**
 * Time-dependent Dirichlet boundary condition for the Fdm framework.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/utilities/fdmtimedepdirichletboundary.{hpp,cpp}}.
 *
 * <p>Holds a function {@code t -> value} (or constant scalar) that is
 * evaluated at each time step and applied to all boundary-side cells in the
 * chosen direction via {@code applyAfterApplying} and
 * {@code applyAfterSolving}.
 *
 * @author Phase 2m Track C port
 */
public class FdmTimeDepDirichletBoundary
        implements BoundaryCondition<FdmLinearOp> {

    private final int[] indices_;
    private final DoubleUnaryOperator valueOnBoundary_;
    private double[] values_;

    /**
     * Scalar-valued boundary condition: all boundary cells get the same value
     * at each time step.
     */
    public FdmTimeDepDirichletBoundary(
            final FdmMesher mesher,
            final DoubleUnaryOperator valueOnBoundary,
            final int direction,
            final Side side) {
        this.valueOnBoundary_ = valueOnBoundary;
        this.indices_         = indicesOnBoundary(mesher.layout(), direction, side);
        this.values_          = new double[indices_.length];
    }

    @Override
    public void setTime(final double t) {
        final double v = valueOnBoundary_.applyAsDouble(t);
        for (int i = 0; i < values_.length; ++i) {
            values_[i] = v;
        }
    }

    @Override
    public void applyBeforeApplying(final FdmLinearOp op) {
        // no-op
    }

    @Override
    public void applyBeforeSolving(final FdmLinearOp op, final Array rhs) {
        // no-op
    }

    @Override
    public void applyAfterApplying(final Array a) {
        for (int i = 0; i < indices_.length; ++i) {
            a.set(indices_[i], values_[i]);
        }
    }

    @Override
    public void applyAfterSolving(final Array a) {
        applyAfterApplying(a);
    }

    // --- static helper: compute boundary indices ---

    /**
     * Collect flat indices of all cells on the given boundary
     * ({@code Lower} = coordinate == 0; {@code Upper} = coordinate == dim-1)
     * in the given direction.
     */
    public static int[] indicesOnBoundary(
            final FdmLinearOpLayout layout,
            final int direction,
            final Side side) {

        final int bdryCoord = (side == Side.Lower) ? 0
                : layout.dim()[direction] - 1;

        final List<Integer> idxList = new ArrayList<>();
        for (final FdmLinearOpIterator iter : layout) {
            if (iter.coordinates()[direction] == bdryCoord) {
                idxList.add(iter.index());
            }
        }

        final int[] arr = new int[idxList.size()];
        for (int i = 0; i < arr.length; ++i) {
            arr[i] = idxList.get(i);
        }
        return arr;
    }
}
