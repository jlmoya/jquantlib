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
import org.jquantlib.model.shortrate.twofactormodels.G2;
import org.jquantlib.model.shortrate.twofactormodels.TwoFactorModel;

/**
 * Finite-difference operator for the G2++ two-factor short-rate dynamics
 * <p>
 * {@code dx = -a*x*dt + sigma dW1, dy = -b*y*dt + eta dW2, dW1 dW2 = rho dt}.
 * <p>
 * Java port of v1.42.1
 * ql/methods/finitedifferences/operators/fdmg2op.{hpp,cpp}.
 *
 * @author Phase 2h WI-1 port
 */
public final class FdmG2Op implements FdmLinearOpComposite {

    private final int direction1;
    private final int direction2;
    private final Array x;
    private final Array y;
    private final TripleBandLinearOp dxMap;
    private final TripleBandLinearOp dyMap;
    private final NinePointLinearOp corrMap;
    private final TripleBandLinearOp mapX;
    private final TripleBandLinearOp mapY;
    private final G2 model;

    public FdmG2Op(final FdmMesher mesher,
                   final G2 model,
                   final int direction1, final int direction2) {
        this.direction1 = direction1;
        this.direction2 = direction2;
        this.x = mesher.locations(direction1);
        this.y = mesher.locations(direction2);
        this.model = model;

        final int size = mesher.layout().size();

        // dxMap = -a*x * d/dx + 0.5 * sigma^2 * d^2/dx^2
        final FirstDerivativeOp firstDerivX = new FirstDerivativeOp(direction1, mesher);
        final SecondDerivativeOp secondDerivX = new SecondDerivativeOp(direction1, mesher);
        final Array minusXTimesA = x.mul(-model.a());
        final double halfSigmaSq = 0.5 * model.sigma() * model.sigma();
        this.dxMap = firstDerivX.mult(minusXTimesA).add(
                secondDerivX.mult(new Array(size).fill(halfSigmaSq)));

        // dyMap = -b*y * d/dy + 0.5 * eta^2 * d^2/dy^2
        final FirstDerivativeOp firstDerivY = new FirstDerivativeOp(direction2, mesher);
        final SecondDerivativeOp secondDerivY = new SecondDerivativeOp(direction2, mesher);
        final Array minusYTimesB = y.mul(-model.b());
        final double halfEtaSq = 0.5 * model.eta() * model.eta();
        this.dyMap = firstDerivY.mult(minusYTimesB).add(
                secondDerivY.mult(new Array(size).fill(halfEtaSq)));

        // corrMap = rho * sigma * eta * ∂²/∂x∂y
        // Java port mirrors C++ field type: NinePointLinearOp (not the
        // SecondOrderMixedDerivativeOp subclass). `mult` constructs a fresh
        // NinePointLinearOp with the same index layout but scaled coefficients,
        // matching the C++ value-semantics return of `mult` on the base type.
        final double corrCoeff = model.rho() * model.sigma() * model.eta();
        this.corrMap = new SecondOrderMixedDerivativeOp(direction1, direction2, mesher)
                .mult(new Array(size).fill(corrCoeff));

        this.mapX = new TripleBandLinearOp(direction1, mesher);
        this.mapY = new TripleBandLinearOp(direction2, mesher);
    }

    @Override
    public int size() {
        return 2;
    }

    @Override
    public void setTime(final double t1, final double t2) {
        final TwoFactorModel.ShortRateDynamics dynamics = model.dynamics();
        final double phi = 0.5 * (dynamics.shortRate(t1, 0.0, 0.0) + dynamics.shortRate(t2, 0.0, 0.0));

        // hr = -0.5 * (x + y + phi)
        final Array hr = x.add(y).add(phi).mul(-0.5);
        // mapX = dxMap + diag(hr); mapY = dyMap + diag(hr)
        mapX.axpyb(new Array(0), dxMap, dxMap, hr);
        mapY.axpyb(new Array(0), dyMap, dyMap, hr);
    }

    @Override
    public Array apply(final Array r) {
        return mapX.apply(r).add(mapY.apply(r)).add(applyMixed(r));
    }

    @Override
    public Array applyMixed(final Array r) {
        return corrMap.apply(r);
    }

    @Override
    public Array applyDirection(final int direction, final Array r) {
        if (direction == direction1) {
            return mapX.apply(r);
        }
        if (direction == direction2) {
            return mapY.apply(r);
        }
        return new Array(r.size()).fill(0.0);
    }

    @Override
    public Array solveSplitting(final int direction, final Array r, final double s) {
        if (direction == direction1) {
            return mapX.solveSplitting(r, s, 1.0);
        }
        if (direction == direction2) {
            return mapY.solveSplitting(r, s, 1.0);
        }
        return new Array(r.size()).fill(0.0);
    }

    @Override
    public Array preconditioner(final Array r, final double dt) {
        return solveSplitting(direction1, r, dt);
    }

    @Override
    public Matrix toMatrix() {
        // Sum of mapX + mapY + corrMap matrices.
        return mapX.toMatrix().add(mapY.toMatrix()).add(corrMap.toMatrix());
    }

    @Override
    public List<Matrix> toMatrixDecomp() {
        final List<Matrix> ret = new ArrayList<Matrix>(3);
        ret.add(mapX.toMatrix());
        ret.add(mapY.toMatrix());
        ret.add(corrMap.toMatrix());
        return Collections.unmodifiableList(ret);
    }
}
