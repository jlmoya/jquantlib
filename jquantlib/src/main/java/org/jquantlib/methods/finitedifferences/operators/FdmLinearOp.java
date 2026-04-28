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

/**
 * Linear operator for a multi-dimensional pde system on an N-d mesh.
 * <p>
 * Java port of v1.42.1 ql/methods/finitedifferences/operators/fdmlinearop.hpp.
 * Java uses {@link Matrix} for the dense {@code toMatrix()} representation in
 * place of the C++ {@code SparseMatrix} (boost ublas) — JQuantLib does not yet
 * have a sparse-matrix type; the dense form is sufficient for the current
 * Hull-White / G2 use cases ({@link FdmHullWhiteOp}, {@link FdmG2Op}) which
 * never decompose the system matrix on the Java side.
 *
 * @author Phase 2h WI-1 port
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
     * (sparse). The Java port returns a dense {@link Matrix} since
     * JQuantLib does not yet expose a sparse-matrix type. Callers that
     * just multiply by the matrix are unaffected; callers that probe
     * sparsity structure must compose against the underlying op directly.
     */
    Matrix toMatrix();
}
