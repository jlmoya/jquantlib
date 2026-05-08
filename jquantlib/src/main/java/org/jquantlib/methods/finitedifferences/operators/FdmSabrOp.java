/*
 Copyright (C) 2018 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

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
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Frequency;

/**
 * Finite-difference operator for the SABR model.
 * <p>
 * Process:
 * <pre>
 *   df_t = alpha_t * f_t^beta * dW_t
 *   d(alpha_t) = nu * alpha_t * dZ_t
 *   corr(dW_t, dZ_t) = rho * dt
 * </pre>
 *
 * <p>The grid uses direction 0 for the forward {@code f} and direction 1 for
 * {@code x = log(alpha)}. The operator decomposes as:
 * <ul>
 *   <li>{@code mapF_}: CEV drift in direction 0 (second-derivative term
 *       {@code 0.5 * exp(2x) * f^{2beta} * d^2/df^2} plus discount)</li>
 *   <li>{@code mapA_}: vol-of-vol dynamics in direction 1
 *       ({@code -0.5*nu^2 * d/dx + 0.5*nu^2 * d^2/dx^2} plus discount)</li>
 *   <li>{@code correlationMap_}: cross-derivative
 *       {@code rho * nu * exp(x) * f^beta * d^2/df dx}</li>
 * </ul>
 *
 * <p>Java port of v1.42.1
 * {@code ql/methods/finitedifferences/operators/fdmsabrop.{hpp,cpp}}.
 *
 * @author Phase 2m Track C port
 */
public final class FdmSabrOp implements FdmLinearOpComposite {

    private final YieldTermStructure rTS_;

    // Constant coefficient maps (built once in ctor)
    private final TripleBandLinearOp dffMap_;    // 0.5 * e^{2x} * f^{2beta} * d^2/df^2
    private final TripleBandLinearOp dxMap_;     // -0.5 * nu^2 * d/dx
    private final TripleBandLinearOp dxxMap_;    // +0.5 * nu^2 * d^2/dx^2
    private final NinePointLinearOp correlationMap_; // rho * nu * e^x * f^beta * d^2/df dx

    // Time-variant direction maps (updated in setTime)
    private final TripleBandLinearOp mapF_;
    private final TripleBandLinearOp mapA_;

    public FdmSabrOp(
            final FdmMesher mesher,
            final YieldTermStructure rTS,
            final double f0,
            final double alpha,
            final double beta,
            final double nu,
            final double rho) {

        this.rTS_ = rTS;

        final int n = mesher.layout().size();

        // Precompute: locations along direction 0 (forward f) and 1 (x = log alpha)
        final Array fLocs  = mesher.locations(0);  // f mesh node values
        final Array xLocs  = mesher.locations(1);  // x = log(alpha) values

        // --- dffMap_: 0.5 * exp(2*x) * f^{2*beta} * d^2/df^2 ---
        final Array dffCoeff = new Array(n);
        for (int i = 0; i < n; ++i) {
            final double f = fLocs.get(i);
            final double x = xLocs.get(i);
            dffCoeff.set(i, 0.5 * Math.exp(2.0 * x) * JQuantMath.pow(f, 2.0 * beta));
        }
        dffMap_ = new SecondDerivativeOp(0, mesher).mult(dffCoeff);

        // --- dxMap_: -0.5 * nu^2 * d/dx (constant coefficient) ---
        final Array dxCoeff = new Array(n).fill(-0.5 * nu * nu);
        dxMap_  = new FirstDerivativeOp(1, mesher).mult(dxCoeff);

        // --- dxxMap_: +0.5 * nu^2 * d^2/dx^2 (constant coefficient) ---
        final Array dxxCoeff = new Array(n).fill(0.5 * nu * nu);
        dxxMap_ = new SecondDerivativeOp(1, mesher).mult(dxxCoeff);

        // --- correlationMap_: rho * nu * exp(x) * f^beta * d^2/(df dx) ---
        final Array corrCoeff = new Array(n);
        for (int i = 0; i < n; ++i) {
            final double f = fLocs.get(i);
            final double x = xLocs.get(i);
            corrCoeff.set(i, rho * nu * Math.exp(x) * JQuantMath.pow(f, beta));
        }
        correlationMap_ = new SecondOrderMixedDerivativeOp(0, 1, mesher)
                .mult(corrCoeff);

        // mutable direction maps — filled in setTime
        mapF_ = new TripleBandLinearOp(0, mesher);
        mapA_ = new TripleBandLinearOp(1, mesher);
    }

    @Override
    public int size() {
        return 2;
    }

    /**
     * Update time-varying part: add discount rate as diagonal scalar.
     * <p>
     * Mirrors C++:
     * <pre>
     *   mapF_.axpyb(Array(), dffMap_, dffMap_, Array(1, -0.5*r));
     *   mapA_.axpyb(Array(1, 1.0), dxMap_, dxxMap_, Array(1, -0.5*r));
     * </pre>
     */
    @Override
    public void setTime(final double t1, final double t2) {
        final double r = rTS_.forwardRate(
                t1, t2, Compounding.Continuous, Frequency.NoFrequency, true).rate();
        final double halfR = -0.5 * r;

        // mapF_ = dffMap_ + diag(-0.5*r)
        // axpyb(Array(), dffMap_, dffMap_, Array(1, -0.5*r)):
        //   x is empty (no left map), a = dffMap_, b = dffMap_, c = scalar
        //   => mapF_ = 0*mapF_ + 1*dffMap_ + diag(c)
        mapF_.axpyb(new Array(0), dffMap_, dffMap_,
                new Array(1).fill(halfR));

        // mapA_ = dxMap_ + dxxMap_ + diag(-0.5*r)
        // axpyb(Array(1,1), dxMap_, dxxMap_, Array(1, -0.5*r)):
        //   x = [1], a = dxMap_, b = dxxMap_, c = -0.5*r
        //   => mapA_ = 1*dxMap_ + dxxMap_ + diag(c)
        mapA_.axpyb(new Array(1).fill(1.0), dxMap_, dxxMap_,
                new Array(1).fill(halfR));
    }

    @Override
    public Array apply(final Array r) {
        return mapF_.apply(r).add(mapA_.apply(r)).add(correlationMap_.apply(r));
    }

    @Override
    public Array applyMixed(final Array r) {
        return correlationMap_.apply(r);
    }

    @Override
    public Array applyDirection(final int direction, final Array r) {
        if (direction == 0) return mapF_.apply(r);
        if (direction == 1) return mapA_.apply(r);
        throw new IllegalArgumentException("direction too large: " + direction);
    }

    @Override
    public Array solveSplitting(final int direction, final Array r, final double s) {
        if (direction == 0) return mapF_.solveSplitting(r, s, 1.0);
        if (direction == 1) return mapA_.solveSplitting(r, s, 1.0);
        throw new IllegalArgumentException("direction too large: " + direction);
    }

    @Override
    public Array preconditioner(final Array r, final double dt) {
        return solveSplitting(1, solveSplitting(0, r, dt), dt);
    }

    @Override
    public Matrix toMatrix() {
        return mapF_.toMatrix().add(mapA_.toMatrix()).add(correlationMap_.toMatrix());
    }

    @Override
    public List<Matrix> toMatrixDecomp() {
        final List<Matrix> ret = new ArrayList<>(3);
        ret.add(mapA_.toMatrix());
        ret.add(mapF_.toMatrix());
        ret.add(correlationMap_.toMatrix());
        return Collections.unmodifiableList(ret);
    }
}
