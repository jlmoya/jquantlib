/*
 Copyright (C) 2018 Klaus Spanderen
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
package org.jquantlib.methods.finitedifferences.operators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Frequency;

/**
 * 1-D finite-difference operator for the constant elasticity of variance
 * (CEV) process with absorbing boundary at {@code f = 0}:
 *
 * <pre>  df_t = alpha * f_t^beta * dW_t</pre>
 *
 * <p>The operator is the discretisation of
 * <pre>  0.5 * alpha^2 * f^{2*beta} * d^2/df^2  -  r * I</pre>
 * along direction {@code direction}.
 *
 * <p>Java port of v1.42.1
 * {@code ql/methods/finitedifferences/operators/fdmcevop.{hpp,cpp}}.
 *
 * @author Phase 5e.5b-CFC-d-112 port
 */
public class FdmCEVOp implements FdmLinearOpComposite {

    private final YieldTermStructure rTS_;
    private final int direction_;
    private final TripleBandLinearOp dxxMap_;
    private final TripleBandLinearOp mapT_;

    public FdmCEVOp(final FdmMesher mesher,
                    final YieldTermStructure rTS,
                    final double f0,
                    final double alpha,
                    final double beta,
                    final int direction) {
        this.rTS_       = rTS;
        this.direction_ = direction;

        // C++: dxxMap_ = SecondDerivativeOp(0, mesher)
        //                .mult(0.5 * alpha^2 * Pow(mesher->locations(direction), 2*beta))
        //
        // Note: SecondDerivativeOp is constructed on axis 0 in C++ even
        // when direction != 0 (matches the operator's intended 1-D usage
        // — the engine always calls with direction = 0). We mirror this
        // exactly.
        final Array locs = mesher.locations(direction);
        final int n = mesher.layout().size();
        final Array coeff = new Array(n);
        final double half_a2 = 0.5 * alpha * alpha;
        for (int i = 0; i < n; ++i) {
            coeff.set(i, half_a2 * JQuantMath.pow(locs.get(i), 2.0 * beta));
        }
        this.dxxMap_ = new SecondDerivativeOp(0, mesher).mult(coeff);
        this.mapT_   = new TripleBandLinearOp(direction, mesher);

        // f0 is part of the C++ signature for API symmetry but is not used
        // by the operator itself (the engine uses it to build the mesher).
        @SuppressWarnings("unused")
        final double unused = f0;
    }

    @Override
    public int size() { return 1; }

    @Override
    public void setTime(final double t1, final double t2) {
        // C++: r = rTS_->forwardRate(t1, t2, Continuous).rate();
        //      mapT_.axpyb(Array(), dxxMap_, dxxMap_, Array(1, -r));
        final double r = rTS_.forwardRate(
                t1, t2, Compounding.Continuous, Frequency.NoFrequency, true).rate();
        mapT_.axpyb(new Array(0), dxxMap_, dxxMap_, new Array(1).fill(-r));
    }

    @Override
    public Array apply(final Array r) {
        return mapT_.apply(r);
    }

    @Override
    public Array applyMixed(final Array r) {
        return new Array(r.size()).fill(0.0);
    }

    @Override
    public Array applyDirection(final int direction, final Array r) {
        if (direction == direction_) {
            return mapT_.apply(r);
        }
        return new Array(r.size()).fill(0.0);
    }

    @Override
    public Array solveSplitting(final int direction, final Array r, final double a) {
        if (direction == direction_) {
            return mapT_.solveSplitting(r, a, 1.0);
        }
        return new Array(r.size()).fill(0.0);
    }

    @Override
    public Array preconditioner(final Array r, final double dt) {
        return solveSplitting(direction_, r, dt);
    }

    @Override
    public Matrix toMatrix() {
        return mapT_.toMatrix();
    }

    @Override
    public List<Matrix> toMatrixDecomp() {
        final List<Matrix> ret = new ArrayList<>(1);
        ret.add(mapT_.toMatrix());
        return Collections.unmodifiableList(ret);
    }
}
