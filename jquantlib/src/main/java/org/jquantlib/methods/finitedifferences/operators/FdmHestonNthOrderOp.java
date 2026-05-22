/*
 Copyright (C) 2018 Klaus Spanderen (C++ source-of-truth, test-suite/nthorderderivativeop.cpp)
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.methods.finitedifferences.operators;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.SparseMatrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.processes.HestonProcess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * High-order finite-difference operator for the Heston PDE assembled from {@link NthOrderDerivativeOp} stencils
 * (Fornberg coefficients).
 *
 * <p>Java port of v1.42.1
 * {@code test-suite/nthorderderivativeop.cpp} inline class {@code FdmHestonNthOrderOp} (lines 395-477).
 *
 * <p>The PDE is the standard Heston log-spot/variance pricing PDE
 * <pre>
 *   dV/dt = -rV * dV/dx + v * d^2V/dx^2
 *           + rho * sigma * v * d^2V/dx dv
 *           + 0.5 * sigma^2 * v * d^2V/dv^2
 *           + kappa * (theta - v) * dV/dv
 * </pre>
 * but with all spatial derivatives discretised via the {@code nPoints}-point Fornberg stencil instead of the usual
 * 3-point central scheme.  The mixed second-derivative is built as the symmetric product {@code (dx*dv + dv*dx)/2}.
 *
 * <p>Implements {@link FdmLinearOpComposite} so it plugs into
 * {@link org.jquantlib.methods.finitedifferences.solvers.FdmBackwardSolver}. Operator splitting is not supported (the
 * C++ class fails with the same message in {@code apply_mixed}, {@code apply_direction}, {@code solve_splitting} — only
 * {@code CrankNicolson} / explicit schemes that never call those paths can drive this operator).
 *
 * <p>Used by {@code testHigherOrderHestonOptionPricing} and
 * {@code testHigherOrderAndRichardsonExtrapolation}.
 *
 * <p>Phase 5e.5b-CFC-d-277.
 */
public class FdmHestonNthOrderOp implements FdmLinearOpComposite {

    private final SparseMatrix map;
    private final TripleBandLinearOp preconditioner;

    /**
     * Build the operator.
     *
     * @param nPoints       Fornberg stencil width passed to {@link NthOrderDerivativeOp}
     * @param hestonProcess Heston process providing v0/kappa/theta/sigma/rho
     * @param mesher        2-D mesher (dim 0 = log-spot, dim 1 = variance)
     */
    public FdmHestonNthOrderOp(final int nPoints, final HestonProcess hestonProcess, final FdmMesher mesher) {
        this(nPoints, hestonProcess, mesher, 0);
    }

    /**
     * Build the operator.
     *
     * @param nPoints       Fornberg stencil width passed to {@link NthOrderDerivativeOp}
     * @param hestonProcess Heston process providing v0/kappa/theta/sigma/rho
     * @param mesher        2-D mesher (dim 0 = log-spot, dim 1 = variance)
     * @param direction     direction used to build the {@link SecondDerivativeOp}-based preconditioner (C++ default
     *                      {@code 0})
     */
    public FdmHestonNthOrderOp(final int nPoints, final HestonProcess hestonProcess, final FdmMesher mesher,
            final int direction) {

        final int n = mesher.layout().size();

        final double kappa = hestonProcess.kappa().currentLink().value();
        final double theta = hestonProcess.theta().currentLink().value();
        final double sigma = hestonProcess.sigma().currentLink().value();
        final double rho = hestonProcess.rho().currentLink().value();

        // vol2_ = 0.5 * theta
        final double vol2 = 0.5 * theta;

        // preconditioner_(SecondDerivativeOp(direction, mesher).mult(Array(n, vol2_)))
        final SecondDerivativeOp d2 = new SecondDerivativeOp(direction, mesher);
        final Array vol2Vec = new Array(n).fill(vol2);
        this.preconditioner = d2.mult(vol2Vec);

        // varianceValues = 0.5 * mesher->locations(1)
        final Array vv = mesher.locations(1);
        final Array varianceValues = vv.mul(0.5);

        // Zero out boundary in direction 0 (log-spot).
        final int[] dim = mesher.layout().dim();
        for ( final FdmLinearOpIterator iter : mesher.layout() ) {
            final int c0 = iter.coordinates()[0];
            if ( c0 == 0 || c0 == dim[0] - 1 ) {
                varianceValues.set(iter.index(), 0.0);
            }
        }

        // Build n-point Fornberg stencils for both axes.
        final SparseMatrix dx = new NthOrderDerivativeOp(0, 1, nPoints, mesher).toSparseMatrix();
        final SparseMatrix dxx = new NthOrderDerivativeOp(0, 2, nPoints, mesher).toSparseMatrix();
        final SparseMatrix dv = new NthOrderDerivativeOp(1, 1, nPoints, mesher).toSparseMatrix();
        final SparseMatrix dvv = new NthOrderDerivativeOp(1, 2, nPoints, mesher).toSparseMatrix();

        // Diagonal arrays for the per-row scaling matrices.
        // C++ builds banded_matrix v, u, rV as full diagonal matrices; we
        // implement the products as per-row scaling (sparseProdLeftDiag).
        //   v(i,i)  = varianceValues[i]
        //   u(i,i)  = vv[i]
        //   rV(i,i) = varianceValues[i] - 0.5*theta  =  varianceValues[i] - vol2
        final double[] vDiag = new double[n];
        final double[] uDiag = new double[n];
        final double[] rVDiag = new double[n];
        for ( int i = 0; i < n; ++i ) {
            vDiag[i] = varianceValues.get(i);
            uDiag[i] = vv.get(i);
            rVDiag[i] = varianceValues.get(i) - vol2;
        }

        // dxDv = dx * dv,  dvDx = dv * dx
        final SparseMatrix dxDv = sparseProd(dx, dv);
        final SparseMatrix dvDx = sparseProd(dv, dx);
        // dxDvPlusDvDx = dxDv + dvDx
        final SparseMatrix dxDvPlusDvDx = new SparseMatrix(dxDv).addAssign(dvDx);

        // theta*I - u  ⇒ a diagonal array (theta - u[i])
        final double[] thetaMinusUDiag = new double[n];
        for ( int i = 0; i < n; ++i ) {
            thetaMinusUDiag[i] = theta - uDiag[i];
        }

        // map = -rV*dx + v*dxx
        //       + 0.5*rho*sigma * u * (dxDvPlusDvDx)
        //       + 0.5*sigma^2  * u * dvv
        //       + kappa * (theta*I - u) * dv
        SparseMatrix term1 = scaleRows(dx, negate(rVDiag));        // -rV * dx
        SparseMatrix term2 = scaleRows(dxx, vDiag);                //  v  * dxx
        SparseMatrix term3 = scaleRows(dxDvPlusDvDx, uDiag);
        scaleInPlace(term3, 0.5 * rho * sigma);                    // 0.5*rho*sigma * u * (...)
        SparseMatrix term4 = scaleRows(dvv, uDiag);
        scaleInPlace(term4, 0.5 * sigma * sigma);                  // 0.5*sigma^2 * u * dvv
        SparseMatrix term5 = scaleRows(dv, thetaMinusUDiag);
        scaleInPlace(term5, kappa);                                // kappa * (theta-u) * dv

        SparseMatrix acc = new SparseMatrix(term1);
        acc.addAssign(term2);
        acc.addAssign(term3);
        acc.addAssign(term4);
        acc.addAssign(term5);
        this.map = acc;
    }

    // ------------------------------------------------------------------
    // FdmLinearOpComposite interface

    /** Static sparse-matrix product {@code A * B} via row iteration. */
    private static SparseMatrix sparseProd(final SparseMatrix a, final SparseMatrix b) {
        if ( a.columns() != b.rows() ) {
            throw new IllegalArgumentException("sparseProd: A cols=" + a.columns() + " != B rows=" + b.rows());
        }
        final int n = a.rows();
        final int p = b.columns();
        final SparseMatrix out = new SparseMatrix(n, p);
        // Row-by-row: for each non-zero (i,k) in A, scan row k of B.
        final int[] aRowPtr = a.index1Data();
        final int[] aColIdx = a.index2Data();
        final double[] aVal = a.valueData();
        final int[] bRowPtr = b.index1Data();
        final int[] bColIdx = b.index2Data();
        final double[] bVal = b.valueData();
        for ( int i = 0; i < n; ++i ) {
            for ( int ak = aRowPtr[i]; ak < aRowPtr[i + 1]; ++ak ) {
                final int k = aColIdx[ak];
                final double vAik = aVal[ak];
                for ( int bk = bRowPtr[k]; bk < bRowPtr[k + 1]; ++bk ) {
                    out.addAt(i, bColIdx[bk], vAik * bVal[bk]);
                }
            }
        }
        return out;
    }

    /** Returns a new sparse matrix equal to {@code diag(d) * a} (row scaling). */
    private static SparseMatrix scaleRows(final SparseMatrix a, final double[] d) {
        final int n = a.rows();
        if ( d.length != n ) {
            throw new IllegalArgumentException("scaleRows: diag length " + d.length + " != rows " + n);
        }
        final SparseMatrix out = new SparseMatrix(a);
        final int[] rowPtr = out.index1Data();
        final double[] vals = out.valueData();
        for ( int i = 0; i < n; ++i ) {
            final double s = d[i];
            for ( int k = rowPtr[i]; k < rowPtr[i + 1]; ++k ) {
                vals[k] *= s;
            }
        }
        return out;
    }

    /** In-place scalar multiply of all stored values. */
    private static void scaleInPlace(final SparseMatrix a, final double s) {
        final double[] vals = a.valueData();
        final int nnz = a.nrElements();
        for ( int k = 0; k < nnz; ++k ) {
            vals[k] *= s;
        }
    }

    /** Returns a new array with each element negated. */
    private static double[] negate(final double[] d) {
        final double[] out = new double[d.length];
        for ( int i = 0; i < d.length; ++i ) {
            out[i] = -d[i];
        }
        return out;
    }

    @Override
    public int size() {
        return 2;
    }

    @Override
    public void setTime(final double t1, final double t2) {
        // Operator is time-independent (Heston PDE coefficients pull
        // r, q from the discount factor outside the rollback in the C++
        // test; FdmHestonNthOrderOp itself has no setTime work).
    }

    @Override
    public Array apply(final Array r) {
        return SparseMatrix.prod(map, r);
    }

    @Override
    public Array applyMixed(final Array r) {
        throw new UnsupportedOperationException("FdmHestonNthOrderOp: operator splitting is not supported");
    }

    @Override
    public Array applyDirection(final int direction, final Array r) {
        throw new UnsupportedOperationException("FdmHestonNthOrderOp: operator splitting is not supported");
    }

    @Override
    public Array solveSplitting(final int direction, final Array r, final double dt) {
        throw new UnsupportedOperationException("FdmHestonNthOrderOp: operator splitting is not supported");
    }

    // ------------------------------------------------------------------
    // Internal sparse helpers

    @Override
    public Array preconditioner(final Array r, final double dt) {
        return preconditioner.solveSplitting(r, dt, 1.0);
    }

    @Override
    public Matrix toMatrix() {
        final int n = map.rows();
        final Matrix out = new Matrix(n, n);
        for ( int i = 0; i < n; ++i ) {
            for ( int j = 0; j < map.columns(); ++j ) {
                final double v = map.get(i, j);
                if ( v != 0.0 ) {
                    out.set(i, j, v);
                }
            }
        }
        return out;
    }

    @Override
    public List< Matrix > toMatrixDecomp() {
        // Composite is non-splitting — return as a single-element decomp.
        return Collections.unmodifiableList(new ArrayList<>(Collections.singletonList(toMatrix())));
    }

    @Override
    public SparseMatrix toSparseMatrix() {
        return new SparseMatrix(map);
    }
}
