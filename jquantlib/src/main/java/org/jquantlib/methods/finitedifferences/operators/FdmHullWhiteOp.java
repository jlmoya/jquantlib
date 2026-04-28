/*
 Copyright (C) 2011 Klaus Spanderen

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
import java.util.Collections;
import java.util.List;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.model.shortrate.onefactormodels.OneFactorModel;

/**
 * Finite-difference operator for the Hull-White short-rate dynamics
 * <p>
 * {@code dr = (theta(t) - a*r) dt + sigma dW}
 * <p>
 * decomposed on a 1D mesh of the OU state variable {@code x = r - phi(t)}.
 * <p>
 * Java port of v1.42.1
 * ql/methods/finitedifferences/operators/fdmhullwhiteop.{hpp,cpp}.
 *
 * @author Phase 2h WI-1 port
 */
public final class FdmHullWhiteOp implements FdmLinearOpComposite {

    private final int direction;
    private final Array x;
    private final TripleBandLinearOp dzMap;
    private final TripleBandLinearOp mapT;
    private final HullWhite model;

    public FdmHullWhiteOp(final FdmMesher mesher,
                          final HullWhite model,
                          final int direction) {
        this.direction = direction;
        this.x = mesher.locations(direction);
        this.model = model;

        // dzMap = -a * x * d/dx + 0.5 * sigma^2 * d^2/dx^2
        //       = FirstDeriv.mult(-x*a) + SecondDeriv.mult(0.5*sigma^2)
        // (matches v1.42.1 fdmhullwhiteop.cpp lines 38-42).
        final FirstDerivativeOp firstDeriv = new FirstDerivativeOp(direction, mesher);
        final SecondDerivativeOp secondDeriv = new SecondDerivativeOp(direction, mesher);

        final Array minusXTimesA = x.mul(-model.a());
        final double halfSigmaSq = 0.5 * model.sigma() * model.sigma();
        final Array halfSigmaSqArray = new Array(mesher.layout().size()).fill(halfSigmaSq);

        this.dzMap = firstDeriv.mult(minusXTimesA).add(secondDeriv.mult(halfSigmaSqArray));
        this.mapT = new TripleBandLinearOp(direction, mesher);
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public void setTime(final double t1, final double t2) {
        final OneFactorModel.ShortRateDynamics dynamics = model.dynamics();
        final double phi = 0.5 * (dynamics.shortRate(t1, 0.0) + dynamics.shortRate(t2, 0.0));

        // mapT = dzMap + diag(-(x + phi))
        // C++: mapT_.axpyb(Array(), dzMap_, dzMap_, -(x_+phi));
        final Array minusXPlusPhi = x.add(phi).mul(-1.0);
        mapT.axpyb(new Array(0), dzMap, dzMap, minusXPlusPhi);
    }

    @Override
    public Array apply(final Array r) {
        return mapT.apply(r);
    }

    @Override
    public Array applyMixed(final Array r) {
        return new Array(r.size()).fill(0.0);
    }

    @Override
    public Array applyDirection(final int direction, final Array r) {
        if (direction == this.direction) {
            return mapT.apply(r);
        }
        return new Array(r.size()).fill(0.0);
    }

    @Override
    public Array solveSplitting(final int direction, final Array r, final double s) {
        if (direction == this.direction) {
            return mapT.solveSplitting(r, s, 1.0);
        }
        return new Array(r.size()).fill(0.0);
    }

    @Override
    public Array preconditioner(final Array r, final double dt) {
        return solveSplitting(direction, r, dt);
    }

    @Override
    public Matrix toMatrix() {
        return mapT.toMatrix();
    }

    @Override
    public List<Matrix> toMatrixDecomp() {
        final List<Matrix> ret = new ArrayList<Matrix>(1);
        ret.add(mapT.toMatrix());
        return Collections.unmodifiableList(ret);
    }
}
