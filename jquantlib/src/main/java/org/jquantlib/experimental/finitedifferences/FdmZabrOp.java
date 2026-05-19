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

 JQuantLib is based on QuantLib. http://quantlib.org/
*/

/*
 Copyright (C) 2014 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.finitedifferences;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Two-factor finite-difference operator for the ZABR model.
 * <p>
 * Java port of v1.42.1 {@code ql/experimental/finitedifferences/fdmzabrop.{hpp,cpp}}.
 *
 * <p>The 2-D ZABR PDE under the {@code (f, alpha)} state has the form
 * <pre>
 *   dV/dt = 0.5 * alpha^2 * f^{2*beta} * d^2V/df^2
 *         + 0.5 * nu^2 * alpha^{2*gamma} * d^2V/dalpha^2
 *         + nu * rho * |alpha|^{gamma+1} * f^beta * d^2V/(df dalpha)
 * </pre>
 *
 * <p>The grid uses direction 0 for the forward {@code f} and direction 1 for
 * {@code alpha} (the stochastic volatility level). All three coefficient maps are time-independent (no discount applied
 * — pricing is done in forward measure / undiscounted call payoff), so {@link #setTime} is a no-op.
 *
 * <p>The operator decomposes as:
 * <ul>
 *   <li>{@code dxMap_} ({@link FdmZabrUnderlyingPart}): direction 0 — second
 *       derivative in {@code f}, scaled by {@code 0.5 * vol^2 * f^{2*beta}}.</li>
 *   <li>{@code dyMap_} ({@link FdmZabrVolatilityPart}): direction 1 — second
 *       derivative in {@code alpha}, scaled by
 *       {@code 0.5 * nu^2 * |vol|^{2*gamma}}.</li>
 *   <li>{@code dxyMap_}: cross derivative (9-point), scaled by
 *       {@code nu * rho * |vol|^{gamma+1} * f^beta}.</li>
 * </ul>
 *
 * @author Phase 4f.5c WI port
 */
public class FdmZabrOp implements FdmLinearOpComposite {

    private final FdmZabrUnderlyingPart dxMap_;
    private final FdmZabrVolatilityPart dyMap_;
    private final NinePointLinearOp dxyMap_;

    public FdmZabrOp(final FdmMesher mesher, final double beta, final double nu, final double rho, final double gamma) {

        // Locations along directions 0 (forward) and 1 (vol).
        final Array forwardValues = mesher.locations(0);
        final Array volatilityValues = mesher.locations(1);

        // Cross-derivative coefficient: nu * rho * |vol|^{gamma+1} * f^beta.
        final int n = mesher.layout().size();
        final Array corrCoeff = new Array(n);
        for ( int i = 0; i < n; ++i ) {
            final double vol = volatilityValues.get(i);
            final double f = forwardValues.get(i);
            corrCoeff.set(i, nu * rho * Math.pow(Math.abs(vol), gamma + 1.0) * Math.pow(f, beta));
        }
        this.dxyMap_ = new SecondOrderMixedDerivativeOp(0, 1, mesher).mult(corrCoeff);

        this.dxMap_ = new FdmZabrUnderlyingPart(mesher, beta, nu, rho, gamma);
        this.dyMap_ = new FdmZabrVolatilityPart(mesher, beta, nu, rho, gamma);
    }

    /** Mirrors C++ {@code FdmZabrOp::size()} — 2 directions. */
    @Override
    public int size() {
        return 2;
    }

    /** Mirrors C++ {@code FdmZabrOp::setTime} — both parts are no-ops. */
    @Override
    public void setTime(final double t1, final double t2) {
        dxMap_.setTime(t1, t2);
        dyMap_.setTime(t1, t2);
    }

    @Override
    public Array apply(final Array u) {
        return dyMap_.getMap().apply(u).add(dxMap_.getMap().apply(u)).add(dxyMap_.apply(u));
    }

    @Override
    public Array applyMixed(final Array r) {
        return dxyMap_.apply(r);
    }

    @Override
    public Array applyDirection(final int direction, final Array r) {
        if ( direction == 0 ) {
            return dxMap_.getMap().apply(r);
        }
        if ( direction == 1 ) {
            return dyMap_.getMap().apply(r);
        }
        QL.error("direction too large: " + direction);
        return null;
    }

    @Override
    public Array solveSplitting(final int direction, final Array r, final double a) {
        if ( direction == 0 ) {
            return dxMap_.getMap().solveSplitting(r, a, 1.0);
        }
        if ( direction == 1 ) {
            return dyMap_.getMap().solveSplitting(r, a, 1.0);
        }
        QL.error("direction too large: " + direction);
        return null;
    }

    @Override
    public Array preconditioner(final Array r, final double dt) {
        return solveSplitting(0, r, dt);
    }

    @Override
    public Matrix toMatrix() {
        return dxMap_.getMap().toMatrix().add(dyMap_.getMap().toMatrix()).add(dxyMap_.toMatrix());
    }

    @Override
    public List< Matrix > toMatrixDecomp() {
        final List< Matrix > ret = new ArrayList< Matrix >(3);
        ret.add(dxMap_.getMap().toMatrix());
        ret.add(dyMap_.getMap().toMatrix());
        ret.add(dxyMap_.toMatrix());
        return Collections.unmodifiableList(ret);
    }

    // ------------------------------------------------------------------
    // Inner classes mirror C++ FdmZabrUnderlyingPart / FdmZabrVolatilityPart
    // ------------------------------------------------------------------

    /**
     * Forward (f) direction part — mirrors C++ {@code FdmZabrUnderlyingPart}. Coefficient:
     * {@code 0.5 * vol^2 * f^{2*beta}}.
     */
    static final class FdmZabrUnderlyingPart {
        private final TripleBandLinearOp mapT_;

        FdmZabrUnderlyingPart(final FdmMesher mesher, final double beta, final double nu, final double rho,
                final double gamma) {
            final Array forwardValues = mesher.locations(0);
            final Array volatilityValues = mesher.locations(1);
            final int n = mesher.layout().size();
            final Array coeff = new Array(n);
            for ( int i = 0; i < n; ++i ) {
                final double vol = volatilityValues.get(i);
                final double f = forwardValues.get(i);
                coeff.set(i, 0.5 * vol * vol * Math.pow(f, 2.0 * beta));
            }
            this.mapT_ = new SecondDerivativeOp(0, mesher).mult(coeff);
        }

        void setTime(final double t1, final double t2) {
            // no-op (constant coefficients) — mirrors C++.
        }

        TripleBandLinearOp getMap() {
            return mapT_;
        }
    }

    /**
     * Volatility (alpha) direction part — mirrors C++ {@code FdmZabrVolatilityPart}. Coefficient:
     * {@code 0.5 * nu^2 * |vol|^{2*gamma}}.
     */
    static final class FdmZabrVolatilityPart {
        private final TripleBandLinearOp mapT_;

        FdmZabrVolatilityPart(final FdmMesher mesher, final double beta, final double nu, final double rho,
                final double gamma) {
            final Array volatilityValues = mesher.locations(1);
            final int n = mesher.layout().size();
            final Array coeff = new Array(n);
            for ( int i = 0; i < n; ++i ) {
                final double vol = volatilityValues.get(i);
                // C++ uses Pow(volatilityValues_, 2*gamma) — for the typical
                // FullFD grid alpha ranges are positive, so this matches |vol|^{2*gamma}
                // (and Pow on Array calls std::pow elementwise).
                coeff.set(i, 0.5 * nu * nu * Math.pow(vol, 2.0 * gamma));
            }
            this.mapT_ = new SecondDerivativeOp(1, mesher).mult(coeff);
        }

        void setTime(final double t1, final double t2) {
            // no-op — mirrors C++.
        }

        TripleBandLinearOp getMap() {
            return mapT_;
        }
    }
}
