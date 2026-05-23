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
 Copyright (C) 2016 Klaus Spanderen
 */
package org.jquantlib.methods.finitedifferences.operators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.SparseMatrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.processes.OrnsteinUhlenbeckProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Frequency;

/**
 * Finite-difference operator for the (standard) Ornstein-Uhlenbeck process
 * {@code dx = a*(b - x) dt + sigma dW} on a 1D mesh along {@code direction}.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/methods/finitedifferences/operators/fdmornsteinuhlenbeckop.{hpp,cpp}}.</p>
 *
 * <p>Both the drift {@code a*(b - x)} and the diffusion {@code 0.5*sigma^2 d2/dx2}
 * are time-independent for the standard OU process, so they are built once in
 * the constructor; only the discount-rate term {@code -r(t)} is refreshed
 * inside {@link #setTime(double, double)}.</p>
 *
 * @author Phase 2 L5-B port
 */
public class FdmOrnsteinUhlenbeckOp implements FdmLinearOpComposite {

    private final FdmMesher mesher_;
    private final OrnsteinUhlenbeckProcess process_;
    private final YieldTermStructure rTS_;
    private final int direction_;

    private final TripleBandLinearOp m_;
    private final TripleBandLinearOp mapX_;

    public FdmOrnsteinUhlenbeckOp(final FdmMesher mesher,
                                  final OrnsteinUhlenbeckProcess process,
                                  final YieldTermStructure rTS) {
        this(mesher, process, rTS, 0);
    }

    public FdmOrnsteinUhlenbeckOp(final FdmMesher mesher,
                                  final OrnsteinUhlenbeckProcess process,
                                  final YieldTermStructure rTS,
                                  final int direction) {
        this.mesher_ = mesher;
        this.process_ = process;
        this.rTS_ = rTS;
        this.direction_ = direction;
        this.m_ = new TripleBandLinearOp(direction, mesher);
        this.mapX_ = new TripleBandLinearOp(direction, mesher);

        final int n = mesher.layout().size();
        final Array x = mesher.locations(direction);
        final Array drift = new Array(n);
        for (final FdmLinearOpIterator iter : mesher.layout()) {
            final int i = iter.index();
            drift.set(i, process.drift(0.0, x.get(i)));
        }

        // m_ = drift * d/dx + 0.5*sigma^2 * d^2/dx^2
        final double halfSigmaSq = 0.5 * process.volatility() * process.volatility();
        final Array halfSigmaSqArr = new Array(n).fill(halfSigmaSq);
        final TripleBandLinearOp dxxScaled =
            new SecondDerivativeOp(direction, mesher).mult(halfSigmaSqArr);
        m_.axpyb(drift, new FirstDerivativeOp(direction, mesher), dxxScaled, new Array(0));
    }

    @Override
    public int size() {
        return mesher_.layout().dim().length;
    }

    @Override
    public void setTime(final double t1, final double t2) {
        final double r = rTS_.forwardRate(t1, t2, Compounding.Continuous,
                                          Frequency.NoFrequency, true).rate();
        // mapX_ = m_ - r*I  (encoded via axpyb b = -r vector of length 1)
        mapX_.axpyb(new Array(0), m_, m_, new Array(1).fill(-r));
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
}
