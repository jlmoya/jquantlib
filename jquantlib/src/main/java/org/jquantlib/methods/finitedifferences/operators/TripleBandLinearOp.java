/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008 Ralph Schreyer
 Copyright (C) 2008 Klaus Spanderen
 Copyright (C) 2014 Johannes Goettker-Schnetmann

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

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.SparseMatrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;

/**
 * Banded linear operator with three diagonals (lower, diag, upper) along a
 * single direction of an N-d mesh.
 * <p>
 * Java port of v1.42.1
 * ql/methods/finitedifferences/operators/triplebandlinearop.{hpp,cpp}.
 * <p>
 * Storage uses primitive {@code double[]} for the diagonals and {@code int[]}
 * for the index lookup tables — direct equivalent of the C++
 * {@code std::unique_ptr<Real[]>} / {@code std::unique_ptr<Size[]>} buffers.
 *
 * @author Phase 2h WI-1 port
 */
public class TripleBandLinearOp implements FdmLinearOp {

    protected int direction;
    protected int[] i0;
    protected int[] i2;
    protected int[] reverseIndex;
    protected double[] lower;
    protected double[] diag;
    protected double[] upper;
    protected FdmMesher mesher;

    /** Subclass-only no-arg constructor used by {@link #copyOf}. */
    protected TripleBandLinearOp() {
        // empty
    }

    public TripleBandLinearOp(final int direction, final FdmMesher mesher) {
        final int size = mesher.layout().size();
        this.direction = direction;
        this.mesher = mesher;
        this.i0 = new int[size];
        this.i2 = new int[size];
        this.reverseIndex = new int[size];
        this.lower = new double[size];
        this.diag = new double[size];
        this.upper = new double[size];

        // newDim/newSpacing: swap the iterated direction with axis 0 to
        // build a transposed-iterator-friendly reverse index used by
        // solve_splitting().
        final int[] origDim = mesher.layout().dim();
        final int[] newDim = origDim.clone();
        final int tmpDim = newDim[0];
        newDim[0] = newDim[direction];
        newDim[direction] = tmpDim;
        final int[] newSpacing = new FdmLinearOpLayout(newDim).spacing().clone();
        final int tmpSp = newSpacing[0];
        newSpacing[0] = newSpacing[direction];
        newSpacing[direction] = tmpSp;

        for (final FdmLinearOpIterator iter : mesher.layout()) {
            final int i = iter.index();

            i0[i] = mesher.layout().neighbourhood(iter, direction, -1);
            i2[i] = mesher.layout().neighbourhood(iter, direction, +1);

            final int[] coordinates = iter.coordinates();
            int newIndex = 0;
            for (int k = 0; k < coordinates.length; ++k) {
                newIndex += coordinates[k] * newSpacing[k];
            }
            reverseIndex[newIndex] = i;
        }
    }

    /** Copy constructor — Java port of the C++ deep-copy ctor. */
    public TripleBandLinearOp(final TripleBandLinearOp m) {
        final int len = m.mesher.layout().size();
        this.direction = m.direction;
        this.mesher = m.mesher;
        this.i0 = m.i0.clone();
        this.i2 = m.i2.clone();
        this.reverseIndex = m.reverseIndex.clone();
        this.lower = m.lower.clone();
        this.diag = m.diag.clone();
        this.upper = m.upper.clone();
        if (this.i0.length != len) {
            // sanity — should not happen with a properly constructed source
            throw new IllegalStateException("inconsistent source size");
        }
    }

    /** Java helper — corresponds to C++ {@code TripleBandLinearOp::swap}. */
    public void swap(final TripleBandLinearOp m) {
        final FdmMesher tmpMesher = mesher; mesher = m.mesher; m.mesher = tmpMesher;
        final int tmpDir = direction; direction = m.direction; m.direction = tmpDir;
        final int[] tmpI0 = i0; i0 = m.i0; m.i0 = tmpI0;
        final int[] tmpI2 = i2; i2 = m.i2; m.i2 = tmpI2;
        final int[] tmpRev = reverseIndex; reverseIndex = m.reverseIndex; m.reverseIndex = tmpRev;
        final double[] tmpL = lower; lower = m.lower; m.lower = tmpL;
        final double[] tmpD = diag; diag = m.diag; m.diag = tmpD;
        final double[] tmpU = upper; upper = m.upper; m.upper = tmpU;
    }

    /**
     * In-place compound assignment {@code this = a*x + y + b} (per-cell on
     * the three diagonals). Both {@code a} and {@code b} may be empty
     * arrays (size 0), matching C++ {@code Array::empty()} branches.
     */
    public void axpyb(final Array a, final TripleBandLinearOp x,
                      final TripleBandLinearOp y, final Array b) {
        final int size = mesher.layout().size();
        final boolean aEmpty = (a == null) || a.size() == 0;
        final boolean bEmpty = (b == null) || b.size() == 0;
        final int binc = (!bEmpty && b.size() > 1) ? 1 : 0;
        final int ainc = (!aEmpty && a.size() > 1) ? 1 : 0;

        if (aEmpty && bEmpty) {
            for (int i = 0; i < size; ++i) {
                diag[i]  = y.diag[i];
                lower[i] = y.lower[i];
                upper[i] = y.upper[i];
            }
        } else if (aEmpty) {
            for (int i = 0; i < size; ++i) {
                diag[i]  = y.diag[i] + b.get(i * binc);
                lower[i] = y.lower[i];
                upper[i] = y.upper[i];
            }
        } else if (bEmpty) {
            for (int i = 0; i < size; ++i) {
                final double s = a.get(i * ainc);
                diag[i]  = y.diag[i]  + s * x.diag[i];
                lower[i] = y.lower[i] + s * x.lower[i];
                upper[i] = y.upper[i] + s * x.upper[i];
            }
        } else {
            for (int i = 0; i < size; ++i) {
                final double s = a.get(i * ainc);
                diag[i]  = y.diag[i]  + s * x.diag[i] + b.get(i * binc);
                lower[i] = y.lower[i] + s * x.lower[i];
                upper[i] = y.upper[i] + s * x.upper[i];
            }
        }
    }

    /** Operator addition (per-cell sum of diagonals). */
    public TripleBandLinearOp add(final TripleBandLinearOp m) {
        final TripleBandLinearOp ret = new TripleBandLinearOp(direction, mesher);
        final int size = mesher.layout().size();
        for (int i = 0; i < size; ++i) {
            ret.lower[i] = lower[i] + m.lower[i];
            ret.diag[i]  = diag[i]  + m.diag[i];
            ret.upper[i] = upper[i] + m.upper[i];
        }
        return ret;
    }

    /** Add {@code u} as the diagonal contribution of a diagonal matrix. */
    public TripleBandLinearOp add(final Array u) {
        final TripleBandLinearOp ret = new TripleBandLinearOp(direction, mesher);
        final int size = mesher.layout().size();
        for (int i = 0; i < size; ++i) {
            ret.lower[i] = lower[i];
            ret.upper[i] = upper[i];
            ret.diag[i]  = diag[i] + u.get(i);
        }
        return ret;
    }

    /** Multiply on the LHS by the diagonal matrix {@code diag(u)}. */
    public TripleBandLinearOp mult(final Array u) {
        final TripleBandLinearOp ret = new TripleBandLinearOp(direction, mesher);
        final int size = mesher.layout().size();
        for (int i = 0; i < size; ++i) {
            final double s = u.get(i);
            ret.lower[i] = lower[i] * s;
            ret.diag[i]  = diag[i]  * s;
            ret.upper[i] = upper[i] * s;
        }
        return ret;
    }

    /** Multiply on the RHS by the diagonal matrix {@code diag(u)}. */
    public TripleBandLinearOp multR(final Array u) {
        final int size = mesher.layout().size();
        QL.require(u.size() == size, "inconsistent size of rhs");
        final TripleBandLinearOp ret = new TripleBandLinearOp(direction, mesher);

        for (int i = 0; i < size; ++i) {
            final double sm1 = (i > 0) ? u.get(i - 1) : 1.0;
            final double s0  = u.get(i);
            final double sp1 = (i < size - 1) ? u.get(i + 1) : 1.0;
            ret.lower[i] = lower[i] * sm1;
            ret.diag[i]  = diag[i]  * s0;
            ret.upper[i] = upper[i] * sp1;
        }
        return ret;
    }

    @Override
    public Array apply(final Array r) {
        QL.require(r.size() == mesher.layout().size(), "inconsistent length of r");
        final int size = mesher.layout().size();
        final Array ret = new Array(size);
        for (int i = 0; i < size; ++i) {
            ret.set(i,
                    r.get(i0[i]) * lower[i]
                    + r.get(i)   * diag[i]
                    + r.get(i2[i]) * upper[i]);
        }
        return ret;
    }

    @Override
    public Matrix toMatrix() {
        final int n = mesher.layout().size();
        final Matrix ret = new Matrix(n, n);
        for (int i = 0; i < n; ++i) {
            ret.set(i, i0[i], ret.get(i, i0[i]) + lower[i]);
            ret.set(i, i,     ret.get(i, i)     + diag[i]);
            ret.set(i, i2[i], ret.get(i, i2[i]) + upper[i]);
        }
        return ret;
    }

    /**
     * Native sparse view of the triple-band operator: at most 3 entries per
     * row ({@code i0[i]}, {@code i}, {@code i2[i]}). Overrides
     * {@link FdmLinearOp#toSparseMatrix()} so callers do not materialize a
     * dense {@code n*n} matrix first — important for large 3-D layouts
     * (e.g. 50x25x31 = 38750 rows ⇒ dense ~1.5e9 cells / ~12 GB).
     *
     * <p>Boundary nodes can have {@code i0[i] == i} or {@code i2[i] == i};
     * we accumulate via {@link SparseMatrix#addAt} so that the three writes
     * collapse onto the same column when needed (matches the
     * {@link #toMatrix()} += semantics verbatim).
     */
    @Override
    public SparseMatrix toSparseMatrix() {
        final int n = mesher.layout().size();
        final SparseMatrix out = new SparseMatrix(n, n);
        for (int i = 0; i < n; ++i) {
            out.addAt(i, i0[i], lower[i]);
            out.addAt(i, i,     diag[i]);
            out.addAt(i, i2[i], upper[i]);
        }
        return out;
    }

    /**
     * Solve the splitting system {@code (a * this + b * I) x = r} with the
     * Thomas algorithm walked in transposed (reverse-index) order.
     * Java port of v1.42.1 {@code TripleBandLinearOp::solve_splitting}.
     */
    public Array solveSplitting(final Array r, final double a, final double b) {
        final int size = mesher.layout().size();
        QL.require(r.size() == size, "inconsistent size of rhs");

        final Array ret = new Array(size);
        final Array tmp = new Array(size);

        // Thomas algorithm walked along reverseIndex.
        int rim1 = reverseIndex[0];
        double bet = 1.0 / (a * diag[rim1] + b);
        QL.require(bet != 0.0, "division by zero");
        ret.set(reverseIndex[0], r.get(rim1) * bet);

        for (int j = 1; j <= size - 1; ++j) {
            final int ri = reverseIndex[j];
            tmp.set(j, a * upper[rim1] * bet);

            bet = b + a * (diag[ri] - tmp.get(j) * lower[ri]);
            QL.ensure(bet != 0.0, "division by zero");
            bet = 1.0 / bet;

            ret.set(ri, (r.get(ri) - a * lower[ri] * ret.get(rim1)) * bet);
            rim1 = ri;
        }
        for (int j = size - 2; j > 0; --j) {
            ret.set(reverseIndex[j],
                    ret.get(reverseIndex[j]) - tmp.get(j + 1) * ret.get(reverseIndex[j + 1]));
        }
        if (size >= 2) {
            ret.set(reverseIndex[0],
                    ret.get(reverseIndex[0]) - tmp.get(1) * ret.get(reverseIndex[1]));
        }
        return ret;
    }

    /** Single-arg overload: {@code b = 1.0}. */
    public Array solveSplitting(final Array r, final double a) {
        return solveSplitting(r, a, 1.0);
    }

    /**
     * Java helper: deep copy. Not used internally; provided for callers
     * that need an independent op (mirrors C++ copy ctor + assignment).
     */
    public TripleBandLinearOp copyOf() {
        return new TripleBandLinearOp(this);
    }
}
