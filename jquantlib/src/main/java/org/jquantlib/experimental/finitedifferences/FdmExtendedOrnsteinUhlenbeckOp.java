/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

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
/*
 Copyright (C) 2011 Klaus Spanderen
 */
package org.jquantlib.experimental.finitedifferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jquantlib.experimental.processes.ExtendedOrnsteinUhlenbeckProcess;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.SparseMatrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpComposite;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.operators.FirstDerivativeOp;
import org.jquantlib.methods.finitedifferences.operators.SecondDerivativeOp;
import org.jquantlib.methods.finitedifferences.operators.TripleBandLinearOp;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Frequency;

/**
 * Finite-difference operator for the (extended) Ornstein–Uhlenbeck process
 * <p>
 * {@code dx = a*(b(t) - x) dt + sigma dW}
 * <p>
 * on a 1D mesh along {@code direction}.
 * <p>
 * Java port of v1.42.1
 * {@code ql/experimental/finitedifferences/fdmextendedornsteinuhlenbeckop.{hpp,cpp}}.
 *
 * <p>This operator combines the (time-frozen) constant diffusion term
 * {@code 0.5*sigma^2 d²/dx²} with the time-varying mean-reverting drift
 * {@code mu(t,x) = a*(b(t) - x)} that is refreshed in {@link #setTime(double, double)}.
 * A discount-rate term {@code -r(t)} is added to the diagonal, so the
 * composite operator advances {@code v} backward in time according to the
 * pricing PDE for an asset paying no dividends.</p>
 *
 * @author Phase 5e.5b-CFC-d-171 port
 */
public class FdmExtendedOrnsteinUhlenbeckOp implements FdmLinearOpComposite {

    private final FdmMesher mesher_;
    private final ExtendedOrnsteinUhlenbeckProcess process_;
    private final YieldTermStructure rTS_;
    @SuppressWarnings("unused")
    private final FdmBoundaryConditionSet bcSet_;
    private final int direction_;

    private final Array x_;
    private final FirstDerivativeOp dxMap_;
    private final TripleBandLinearOp dxxMap_;
    private final TripleBandLinearOp mapX_;

    public FdmExtendedOrnsteinUhlenbeckOp(final FdmMesher mesher,
                                          final ExtendedOrnsteinUhlenbeckProcess process,
                                          final YieldTermStructure rTS,
                                          final FdmBoundaryConditionSet bcSet) {
        this(mesher, process, rTS, bcSet, 0);
    }

    public FdmExtendedOrnsteinUhlenbeckOp(final FdmMesher mesher,
                                          final ExtendedOrnsteinUhlenbeckProcess process,
                                          final YieldTermStructure rTS,
                                          final FdmBoundaryConditionSet bcSet,
                                          final int direction) {
        this.mesher_ = mesher;
        this.process_ = process;
        this.rTS_ = rTS;
        this.bcSet_ = bcSet;
        this.direction_ = direction;
        this.x_ = mesher.locations(direction);
        this.dxMap_ = new FirstDerivativeOp(direction, mesher);

        // dxxMap = SecondDerivativeOp.mult(0.5 * sigma^2 * Array(n,1))
        final double halfSigmaSq = 0.5 * process.volatility() * process.volatility();
        final Array halfSigmaSqArr = new Array(mesher.layout().size()).fill(halfSigmaSq);
        this.dxxMap_ = new SecondDerivativeOp(direction, mesher).mult(halfSigmaSqArr);

        this.mapX_ = new TripleBandLinearOp(direction, mesher);
    }

    @Override
    public int size() {
        return mesher_.layout().dim().length;
    }

    @Override
    public void setTime(final double t1, final double t2) {
        final double r = rTS_.forwardRate(t1, t2, Compounding.Continuous,
                                          Frequency.NoFrequency, true).rate();

        final int n = mesher_.layout().size();
        final Array drift = new Array(n);
        final double tMid = 0.5 * (t1 + t2);
        for (final FdmLinearOpIterator iter : mesher_.layout()) {
            final int i = iter.index();
            drift.set(i, process_.drift(tMid, x_.get(i)));
        }
        final Array minusR = new Array(1).fill(-r);
        mapX_.axpyb(drift, dxMap_, dxxMap_, minusR);
    }

    @Override
    public Array apply(final Array r) {
        return mapX_.apply(r);
    }

    @Override
    public Array applyMixed(final Array r) {
        return new Array(r.size()).fill(0.0);
    }

    @Override
    public Array applyDirection(final int direction, final Array r) {
        if (direction == direction_) {
            return mapX_.apply(r);
        }
        return new Array(r.size()).fill(0.0);
    }

    @Override
    public Array solveSplitting(final int direction, final Array r, final double s) {
        if (direction == direction_) {
            return mapX_.solveSplitting(r, s, 1.0);
        }
        return r.clone();
    }

    @Override
    public Array preconditioner(final Array r, final double dt) {
        return solveSplitting(direction_, r, dt);
    }

    @Override
    public Matrix toMatrix() {
        return mapX_.toMatrix();
    }

    @Override
    public List<Matrix> toMatrixDecomp() {
        final List<Matrix> ret = new ArrayList<>(1);
        ret.add(mapX_.toMatrix());
        return Collections.unmodifiableList(ret);
    }

    /**
     * Native sparse decomposition: avoids materialising the dense
     * {@code n*n} matrix produced by {@link #toMatrixDecomp()}.
     * Delegates to {@link TripleBandLinearOp#toSparseMatrix()} which
     * exposes a native CSR view (3-band).
     *
     * <p>Added in Phase 5e.5b-CFC-d-285 so {@link FdmKlugeExtOUOp} can
     * service the 50x20x20 = 20000-cell {@code testKlugeExtOUMatrixDecomposition}
     * test without the {@code O(n^2)} dense intermediate.
     */
    @Override
    public List<SparseMatrix> toSparseMatrixDecomp() {
        final List<SparseMatrix> ret = new ArrayList<>(1);
        ret.add(mapX_.toSparseMatrix());
        return Collections.unmodifiableList(ret);
    }
}
