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

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.SparseMatrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;

/**
 * Nine-point cross-derivative linear operator on an N-d mesh.
 * <p>
 * Java port of v1.42.1
 * ql/methods/finitedifferences/operators/ninepointlinearop.{hpp,cpp}.
 * <p>
 * Two directions {@code d0_, d1_} are explicitly named; for each cell, we
 * pre-compute eight reflected-neighbor indices around the central cell
 * (forming a 3x3 stencil) and the corresponding nine coefficients
 * {@code a00..a22}. {@link SecondOrderMixedDerivativeOp} fills in the
 * coefficient values; this base class handles index pre-computation,
 * application, and matrix materialization.
 *
 * @author Phase 2h WI-1 port
 */
public class NinePointLinearOp implements FdmLinearOp {

    protected int d0;
    protected int d1;
    protected int[] i00, i10, i20;
    protected int[] i01, i21;
    protected int[] i02, i12, i22;
    protected double[] a00, a10, a20;
    protected double[] a01, a11, a21;
    protected double[] a02, a12, a22;
    protected FdmMesher mesher;

    /** Subclass-only no-arg constructor used by deep-copy paths. */
    protected NinePointLinearOp() {
        // empty
    }

    public NinePointLinearOp(final int d0, final int d1, final FdmMesher mesher) {
        final int size = mesher.layout().size();
        this.d0 = d0;
        this.d1 = d1;
        this.mesher = mesher;
        this.i00 = new int[size];
        this.i10 = new int[size];
        this.i20 = new int[size];
        this.i01 = new int[size];
        this.i21 = new int[size];
        this.i02 = new int[size];
        this.i12 = new int[size];
        this.i22 = new int[size];
        this.a00 = new double[size];
        this.a10 = new double[size];
        this.a20 = new double[size];
        this.a01 = new double[size];
        this.a11 = new double[size];
        this.a21 = new double[size];
        this.a02 = new double[size];
        this.a12 = new double[size];
        this.a22 = new double[size];

        QL.require(d0 != d1
                && d0 < mesher.layout().dim().length
                && d1 < mesher.layout().dim().length,
                "inconsistent derivative directions");

        for (final FdmLinearOpIterator iter : mesher.layout()) {
            final int i = iter.index();
            i10[i] = mesher.layout().neighbourhood(iter, d1, -1);
            i01[i] = mesher.layout().neighbourhood(iter, d0, -1);
            i21[i] = mesher.layout().neighbourhood(iter, d0, +1);
            i12[i] = mesher.layout().neighbourhood(iter, d1, +1);
            i00[i] = mesher.layout().neighbourhood(iter, d0, -1, d1, -1);
            i20[i] = mesher.layout().neighbourhood(iter, d0, +1, d1, -1);
            i02[i] = mesher.layout().neighbourhood(iter, d0, -1, d1, +1);
            i22[i] = mesher.layout().neighbourhood(iter, d0, +1, d1, +1);
        }
    }

    /** Copy constructor — Java port of the C++ deep-copy ctor. */
    public NinePointLinearOp(final NinePointLinearOp m) {
        this.d0 = m.d0;
        this.d1 = m.d1;
        this.mesher = m.mesher;
        this.i00 = m.i00.clone();
        this.i10 = m.i10.clone();
        this.i20 = m.i20.clone();
        this.i01 = m.i01.clone();
        this.i21 = m.i21.clone();
        this.i02 = m.i02.clone();
        this.i12 = m.i12.clone();
        this.i22 = m.i22.clone();
        this.a00 = m.a00.clone();
        this.a10 = m.a10.clone();
        this.a20 = m.a20.clone();
        this.a01 = m.a01.clone();
        this.a11 = m.a11.clone();
        this.a21 = m.a21.clone();
        this.a02 = m.a02.clone();
        this.a12 = m.a12.clone();
        this.a22 = m.a22.clone();
    }

    @Override
    public Array apply(final Array u) {
        QL.require(u.size() == mesher.layout().size(),
                "inconsistent length of r");
        final int size = u.size();
        final Array ret = new Array(size);
        for (int i = 0; i < size; ++i) {
            ret.set(i,
                      a00[i] * u.get(i00[i])
                    + a01[i] * u.get(i01[i])
                    + a02[i] * u.get(i02[i])
                    + a10[i] * u.get(i10[i])
                    + a11[i] * u.get(i)
                    + a12[i] * u.get(i12[i])
                    + a20[i] * u.get(i20[i])
                    + a21[i] * u.get(i21[i])
                    + a22[i] * u.get(i22[i]));
        }
        return ret;
    }

    @Override
    public Matrix toMatrix() {
        final int n = mesher.layout().size();
        final Matrix ret = new Matrix(n, n);
        for (int i = 0; i < n; ++i) {
            ret.set(i, i00[i], ret.get(i, i00[i]) + a00[i]);
            ret.set(i, i01[i], ret.get(i, i01[i]) + a01[i]);
            ret.set(i, i02[i], ret.get(i, i02[i]) + a02[i]);
            ret.set(i, i10[i], ret.get(i, i10[i]) + a10[i]);
            ret.set(i, i,      ret.get(i, i)      + a11[i]);
            ret.set(i, i12[i], ret.get(i, i12[i]) + a12[i]);
            ret.set(i, i20[i], ret.get(i, i20[i]) + a20[i]);
            ret.set(i, i21[i], ret.get(i, i21[i]) + a21[i]);
            ret.set(i, i22[i], ret.get(i, i22[i]) + a22[i]);
        }
        return ret;
    }

    /**
     * Native sparse view of the nine-point cross-derivative operator: at most
     * 9 entries per row (the {@code 3x3} stencil around each cell). Overrides
     * {@link FdmLinearOp#toSparseMatrix()} so callers do not materialize a
     * dense {@code n*n} matrix first — important for large 3-D layouts
     * (e.g. 50x20x20 = 20000 rows ⇒ dense ~4e8 cells / ~3.2 GB).
     *
     * <p>Boundary nodes can have stencil indices collapse onto the same
     * column; we accumulate via {@link SparseMatrix#addAt} so the nine
     * writes combine where needed, matching the {@link #toMatrix()} {@code +=}
     * semantics verbatim.
     *
     * @author Phase 5e.5b-CFC-d-285 — sparse path for FdmKlugeExtOUOp
     */
    @Override
    public SparseMatrix toSparseMatrix() {
        final int n = mesher.layout().size();
        final SparseMatrix out = new SparseMatrix(n, n);
        for (int i = 0; i < n; ++i) {
            out.addAt(i, i00[i], a00[i]);
            out.addAt(i, i01[i], a01[i]);
            out.addAt(i, i02[i], a02[i]);
            out.addAt(i, i10[i], a10[i]);
            out.addAt(i, i,      a11[i]);
            out.addAt(i, i12[i], a12[i]);
            out.addAt(i, i20[i], a20[i]);
            out.addAt(i, i21[i], a21[i]);
            out.addAt(i, i22[i], a22[i]);
        }
        return out;
    }

    /** Multiply on the LHS by the diagonal matrix {@code diag(u)}. */
    public NinePointLinearOp mult(final Array u) {
        final NinePointLinearOp ret = new NinePointLinearOp(d0, d1, mesher);
        final int size = mesher.layout().size();
        for (int i = 0; i < size; ++i) {
            final double s = u.get(i);
            ret.a11[i] = a11[i] * s; ret.a00[i] = a00[i] * s;
            ret.a01[i] = a01[i] * s; ret.a02[i] = a02[i] * s;
            ret.a10[i] = a10[i] * s; ret.a20[i] = a20[i] * s;
            ret.a21[i] = a21[i] * s; ret.a12[i] = a12[i] * s;
            ret.a22[i] = a22[i] * s;
        }
        return ret;
    }
}
