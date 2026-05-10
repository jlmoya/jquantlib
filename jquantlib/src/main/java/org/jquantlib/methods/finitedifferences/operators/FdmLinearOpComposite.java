/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008 Ralph Schreyer
 Copyright (C) 2008, 2012 Klaus Spanderen

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

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.SparseMatrix;

/**
 * Composite linear operator for time-dependent multi-dim pde systems.
 * <p>
 * Java port of v1.42.1
 * ql/methods/finitedifferences/operators/fdmlinearopcomposite.hpp.
 * <p>
 * The composite pattern allows operator-splitting schemes (e.g. Hundsdorfer)
 * to ask each direction sub-operator separately for its action on the state.
 * <p>
 * <b>Phase 5b.5b update:</b> adds {@link #toSparseMatrixDecomp()} +
 * sparse-aware {@link #toSparseMatrix()} default which sums per-direction
 * sparse matrices — mirrors C++ {@code std::accumulate(dcmp.begin()+1, ...)}.
 *
 * @author Phase 2h WI-1 port; Phase 5b.5b sparse extension
 */
public interface FdmLinearOpComposite extends FdmLinearOp {

    /** Number of operator dimensions (1 for HW, 2 for G2). */
    int size();

    /**
     * Update time-dependent coefficients for the rollback step
     * {@code [t1, t2]}. The contract requires {@code t1 <= t2}.
     */
    void setTime(final double t1, final double t2);

    /** Apply the cross-direction (mixed) part of the operator. */
    Array applyMixed(final Array r);

    /** Apply only the {@code direction}-th 1D part of the operator. */
    Array applyDirection(final int direction, final Array r);

    /**
     * Solve the splitting equation
     * {@code (I - s * A_direction) * x = r}
     * for {@code x} along direction {@code direction}.
     */
    Array solveSplitting(final int direction, final Array r, final double s);

    /**
     * Apply the diagonal preconditioner used by the Hundsdorfer / Douglas
     * iterative schemes: {@code (I - dt * A_0) ^ {-1}} along direction 0.
     */
    Array preconditioner(final Array r, final double dt);

    /**
     * Decompose the operator into its per-direction matrices.
     * Default implementation returns a 1-element list with the full
     * {@link #toMatrix()} — port mirrors C++ which throws
     * {@code "ublas representation is not implemented"} when not
     * overridden; Java port instead degrades gracefully so callers can
     * still get a usable matrix.
     */
    List<Matrix> toMatrixDecomp();

    /**
     * Sparse equivalent of {@link #toMatrixDecomp()} (Phase 5b.5b).
     * <p>
     * Default implementation delegates to {@link #toMatrixDecomp()} and
     * converts each dense {@link Matrix} to a {@link SparseMatrix}.
     * Implementations that hold native sparse state (e.g. an
     * {@link NthOrderDerivativeOp}-based operator) should override this for
     * efficiency.
     *
     * @return one sparse matrix per direction
     */
    default List<SparseMatrix> toSparseMatrixDecomp() {
        final List<Matrix> dense = toMatrixDecomp();
        final List<SparseMatrix> out = new ArrayList<>(dense.size());
        for (final Matrix d : dense) {
            final int r = d.rows();
            final int c = d.cols();
            final SparseMatrix sp = new SparseMatrix(r, c);
            for (int i = 0; i < r; ++i) {
                for (int j = 0; j < c; ++j) {
                    final double v = d.get(i, j);
                    if (v != 0.0) {
                        sp.set(i, j, v);
                    }
                }
            }
            out.add(sp);
        }
        return out;
    }

    /**
     * Sparse-aware override of {@link FdmLinearOp#toSparseMatrix()} — sums
     * per-direction sparse matrices to mirror C++ composite
     * {@code toMatrix()} which uses {@code std::accumulate(dcmp.begin()+1, ...)}.
     */
    @Override
    default SparseMatrix toSparseMatrix() {
        final List<SparseMatrix> dcmp = toSparseMatrixDecomp();
        if (dcmp.isEmpty()) {
            // Falls back to the dense-conversion default of FdmLinearOp.
            return FdmLinearOp.super.toSparseMatrix();
        }
        SparseMatrix acc = new SparseMatrix(dcmp.get(0));
        for (int i = 1; i < dcmp.size(); ++i) {
            acc = acc.addAssign(dcmp.get(i));
        }
        return acc;
    }
}
