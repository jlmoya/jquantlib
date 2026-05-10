/*
 Copyright (C) 2018 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

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

import java.util.TreeSet;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.SparseMatrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;

/**
 * Arbitrary {@code n}-th order finite-difference linear operator on a 1D
 * direction of an N-d {@link FdmMesher}.
 *
 * <p>Java port of v1.42.1
 * {@code ql/methods/finitedifferences/operators/nthorderderivativeop.{hpp,cpp}}.
 *
 * <p>Builds a {@link SparseMatrix} of stencil weights computed by
 * {@link NumericalDifferentiation} (Fornberg, 1998) for the {@code order}-th
 * derivative on {@code nPoints} stencil points. Boundaries are handled by
 * sliding the central stencil so that all {@code nPoints} points stay within
 * the grid.
 *
 * <p>Used by Heston-Richardson high-order pricing
 * (testHigherOrderHestonOptionPricing in C++ test-suite).
 *
 * <p>Phase 5b.5b — JQuantLib migration.
 */
public class NthOrderDerivativeOp implements FdmLinearOp {

    private final SparseMatrix m;

    /**
     * Build the operator.
     *
     * @param direction direction along which to differentiate
     * @param order     order {@code k} of the derivative ({@code >= 1})
     * @param nPoints   stencil width ({@code > 1}, {@code <= grid extent in {@code direction}})
     * @param mesher    enclosing mesher
     */
    public NthOrderDerivativeOp(final int direction, final int order,
                                 final int nPoints, final FdmMesher mesher) {
        this.m = new SparseMatrix(mesher.layout().size(), mesher.layout().size());

        final int hPoints = nPoints / 2;
        final boolean isEven = (nPoints == 2 * hPoints);

        // Unique sorted x-values along the direction.
        final Array meshLocations = mesher.locations(direction);
        final TreeSet<Double> tmp = new TreeSet<>();
        for (int i = 0; i < meshLocations.size(); ++i) {
            tmp.add(meshLocations.get(i));
        }
        final double[] xValues = new double[tmp.size()];
        int xi = 0;
        for (final Double v : tmp) {
            xValues[xi++] = v;
        }

        final int nx = mesher.layout().dim()[direction];
        QL.require(xValues.length == nx,
                "inconsistent set of grid values in direction " + direction);
        QL.require(nPoints > 1 && nPoints <= nx,
                "inconsistent number of points");

        final double[] xOffsetsBuf = new double[nPoints];

        for (final FdmLinearOpIterator iter : mesher.layout()) {
            final int ix = iter.coordinates()[direction];

            // Slide stencil so that [ilx, ilx+nPoints) stays inside [0, nx).
            final int offset = Math.max(0, hPoints - ix)
                - Math.max(0, hPoints - (nx - (isEven ? 0 : 1) - ix));
            final int ilx = ix - hPoints + offset;

            for (int j = 0; j < nPoints; ++j) {
                final int idx = ilx + j;
                xOffsetsBuf[j] = xValues[idx] - xValues[ix];
            }
            final Array xOffsets = new Array(xOffsetsBuf.clone());

            final Array weights =
                new NumericalDifferentiation(null, order, xOffsets).weights();

            final int i = iter.index();
            for (int j = 0; j < nPoints; ++j) {
                final int k = mesher.layout().neighbourhood(iter, direction, ilx - ix + j);
                m.set(i, k, weights.get(j));
            }
        }
    }

    @Override
    public Array apply(final Array r) {
        return SparseMatrix.prod(m, r);
    }

    /**
     * Dense materialization of the underlying sparse matrix.
     *
     * <p>Mirrors C++ {@code SparseMatrix toMatrix()} — the Java
     * {@link FdmLinearOp} interface returns a dense {@link Matrix} for
     * back-compat; the sparse view is available via {@link #toSparseMatrix()}.
     */
    @Override
    public Matrix toMatrix() {
        final int n = m.rows();
        final Matrix out = new Matrix(n, n);
        for (int row = 0; row < n; ++row) {
            for (int col = 0; col < m.columns(); ++col) {
                final double v = m.get(row, col);
                if (v != 0.0) {
                    out.set(row, col, v);
                }
            }
        }
        return out;
    }

    /**
     * Native sparse view of the operator.
     *
     * <p>This is the C++ counterpart of {@code toMatrix()} (which returns
     * a {@code SparseMatrix} in C++); preserved as a separate method so that
     * the Java {@link FdmLinearOp#toMatrix()} contract (returning dense
     * {@link Matrix}) is unchanged for the existing Hull-White / G2 / Bates
     * call-sites.
     *
     * @return reference to the underlying CSR matrix
     */
    public SparseMatrix toSparseMatrix() {
        return m;
    }
}
