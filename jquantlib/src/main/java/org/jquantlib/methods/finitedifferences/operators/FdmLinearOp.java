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

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.SparseMatrix;

/**
 * Linear operator for a multi-dimensional pde system on an N-d mesh.
 * <p>
 * Java port of v1.42.1 ql/methods/finitedifferences/operators/fdmlinearop.hpp.
 * <p>
 * <b>Phase 5b.5b update:</b> The C++ class returns
 * {@code SparseMatrix} from {@code toMatrix()}.  The Java port keeps
 * {@link #toMatrix()} returning dense {@link Matrix} for backward compatibility
 * with the existing Hull-White / G2 / Bates call-sites and tests; it adds the
 * companion {@link #toSparseMatrix()} method (default: convert dense to sparse)
 * so high-order operators ({@link NthOrderDerivativeOp}) can publish a native
 * CSR view without losing sparsity structure.  Both methods are equivalent in
 * value; they differ only in storage.
 *
 * @author Phase 2h WI-1 port; Phase 5b.5b sparse extension
 */
public interface FdmLinearOp {

    /**
     * Apply the linear operator to vector {@code r}.
     * @param r input array (length must equal the layout size)
     * @return new array with operator-applied values
     */
    Array apply(final Array r);

    /**
     * Materialize this operator as a dense matrix.
     * <p>
     * Note: C++ returns a {@code boost::numeric::ublas::compressed_matrix}
     * (sparse).  The Java port keeps the dense {@link Matrix} return type so
     * that existing Hull-White / G2 / Bates implementations need no changes;
     * callers that need a sparse view should call {@link #toSparseMatrix()}.
     */
    Matrix toMatrix();

    /**
     * Materialize this operator as a {@link SparseMatrix}.  C++ counterpart of
     * the {@code SparseMatrix toMatrix()} return type.
     * <p>
     * The default implementation converts {@link #toMatrix()} entry-by-entry,
     * preserving every non-zero.  Implementations whose internal state is
     * already sparse (notably {@link NthOrderDerivativeOp}) should override
     * this method to expose the underlying CSR storage directly.
     *
     * @return CSR view of the operator
     */
    default SparseMatrix toSparseMatrix() {
        final Matrix dense = toMatrix();
        final int rows = dense.rows();
        final int cols = dense.cols();
        final SparseMatrix out = new SparseMatrix(rows, cols);
        for (int i = 0; i < rows; ++i) {
            for (int j = 0; j < cols; ++j) {
                final double v = dense.get(i, j);
                if (v != 0.0) {
                    out.set(i, j, v);
                }
            }
        }
        return out;
    }
}
