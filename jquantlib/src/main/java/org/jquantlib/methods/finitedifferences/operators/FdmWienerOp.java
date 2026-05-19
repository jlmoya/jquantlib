/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2024 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.methods.finitedifferences.operators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.SparseMatrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * N-dimensional Wiener / Black-Scholes after-PCA operator:
 * <p>
 * {@code L = sum_i 0.5 * lambda_i * d^2 / dx_i^2 - r * I}
 * <p>
 * Used by {@code FdndimBlackScholesVanillaEngine} to roll back the PCA-
 * transformed log-price grid. The diagonal {@code -r*I} term is only
 * applied when a (non-null) risk-free curve is supplied; European
 * pricing zeroes the {@code rTS} and applies the discount once at the
 * end via {@code VectorBsmProcessExtractor.getInterestRateDf}.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/operators/fdmwienerop.{hpp,cpp}}.
 *
 * @author Phase 5e.5b-CFC-d-280 port
 */
public final class FdmWienerOp implements FdmLinearOpComposite {

    private final YieldTermStructure rTS;
    private final List<TripleBandLinearOp> ops;
    private double r;

    public FdmWienerOp(final FdmMesher mesher,
                       final YieldTermStructure rTS,
                       final Array lambdas) {
        QL.require(mesher.layout().dim().length == lambdas.size(),
                "mesher and lambdas need to be of the same dimension");
        this.rTS = rTS;
        this.r = 0.0;
        this.ops = new ArrayList<>(lambdas.size());

        final int size = mesher.layout().size();
        for (int i = 0; i < lambdas.size(); ++i) {
            final SecondDerivativeOp sec = new SecondDerivativeOp(i, mesher);
            final Array scale = new Array(size).fill(0.5 * lambdas.get(i));
            ops.add(sec.mult(scale));
        }
    }

    @Override
    public int size() {
        return ops.size();
    }

    @Override
    public void setTime(final double t1, final double t2) {
        if (rTS != null) {
            r = rTS.forwardRate(t1, t2, Compounding.Continuous).rate();
        }
    }

    @Override
    public Array apply(final Array x) {
        final Array y = x.mul(-r);
        for (final TripleBandLinearOp op : ops) {
            y.addAssign(op.apply(x));
        }
        return y;
    }

    @Override
    public Array applyMixed(final Array x) {
        return new Array(x.size()).fill(0.0);
    }

    @Override
    public Array applyDirection(final int direction, final Array x) {
        return ops.get(direction).apply(x);
    }

    @Override
    public Array solveSplitting(final int direction, final Array x, final double s) {
        return ops.get(direction).solveSplitting(x, s, 1.0);
    }

    @Override
    public Array preconditioner(final Array r, final double dt) {
        return solveSplitting(0, r, dt);
    }

    @Override
    public Matrix toMatrix() {
        Matrix acc = ops.get(0).toMatrix();
        for (int i = 1; i < ops.size(); ++i) {
            acc = acc.add(ops.get(i).toMatrix());
        }
        return acc;
    }

    @Override
    public List<Matrix> toMatrixDecomp() {
        final List<Matrix> out = new ArrayList<>(ops.size());
        for (final TripleBandLinearOp op : ops) {
            out.add(op.toMatrix());
        }
        return Collections.unmodifiableList(out);
    }

    @Override
    public List<SparseMatrix> toSparseMatrixDecomp() {
        final List<SparseMatrix> out = new ArrayList<>(ops.size());
        for (final TripleBandLinearOp op : ops) {
            out.add(op.toSparseMatrix());
        }
        return Collections.unmodifiableList(out);
    }
}
