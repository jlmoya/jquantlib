/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008 Ralph Schreyer
 Copyright (C) 2008, 2014, 2015 Klaus Spanderen
 Copyright (C) 2015 Johannes Göttker-Schnetmann

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

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.LocalVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Frequency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 2-D finite-difference operator for the Heston model PDE.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/operators/fdmhestonop.{hpp,cpp}}.
 * <p>
 * The two dimensions are:
 * <ul>
 *   <li>dim 0: log-spot S (equity)</li>
 *   <li>dim 1: variance v (Heston CIR)</li>
 * </ul>
 * <p>
 * The PDE for {@code V(S, v, t)} is
 * <pre>
 *   dV/dt + 0.5*v*S^2 d^2V/dS^2 + rho*sigma*v*S d^2V/dSdv
 *         + 0.5*sigma^2*v d^2V/dv^2 + (r-q-0.5*v) dV/dlogS
 *         + kappa*(theta - v) dV/dv - r*V = 0
 * </pre>
 * decomposed into:
 * <ul>
 *   <li>dim-0 (equity): drift {@code (r - q - 0.5*v)} and {@code 0.5*v} d^2/dlogS^2 + diag {@code -0.5*r}</li>
 *   <li>dim-1 (variance): {@code 0.5*sigma^2*v} d^2/dv^2 + {@code kappa*(theta - v)} d/dv + diag {@code -0.5*r}</li>
 *   <li>mixed: {@code rho*sigma*v} d^2/dSdv  (acting through the equity-part L slice = 1.0 here, no leverage / quanto)</li>
 * </ul>
 * <p>
 * <strong>Limitations vs. C++:</strong>
 * <ul>
 *   <li>{@code FdmQuantoHelper} is not yet ported; only the no-quanto path is implemented.</li>
 *   <li>{@code LocalVolTermStructure} leverage function <em>is</em> supported
 *       (Phase 5e.5b-CFC-d-254) via the
 *       {@link #FdmHestonOp(FdmMesher, HestonProcess, double, LocalVolTermStructure)}
 *       overload. When non-null, the equity-part {@code setTime} samples
 *       {@code L(t, S)} per equity grid point (mirroring C++
 *       {@code FdmHestonEquityPart::getLeverageFctSlice}). When null,
 *       {@code L ≡ 1.0} and the operator collapses to pure Heston.</li>
 *   <li>{@code mixingFactor} is supported (defaults to 1.0).</li>
 * </ul>
 *
 * @author Phase 4n.5 port
 */
public class FdmHestonOp implements FdmLinearOpComposite {

    private final NinePointLinearOp correlationMap;
    private final FdmHestonVariancePart dyMap;
    private final FdmHestonEquityPart dxMap;

    /**
     * Construct the Heston FD operator with default {@code mixingFactor=1.0}, no quanto adjustment and no leverage
     * function.
     */
    public FdmHestonOp(final FdmMesher mesher, final HestonProcess hestonProcess) {
        this(mesher, hestonProcess, 1.0, null);
    }

    /**
     * Construct the Heston FD operator with no leverage function.
     *
     * @param mesher        2-D mesher (dim 0 = log-spot, dim 1 = variance)
     * @param hestonProcess the Heston process supplying r, q, kappa, theta, sigma, rho
     * @param mixingFactor  mixing factor applied to {@code sigma} (default 1.0)
     */
    public FdmHestonOp(final FdmMesher mesher, final HestonProcess hestonProcess, final double mixingFactor) {
        this(mesher, hestonProcess, mixingFactor, null);
    }

    /**
     * Construct the Heston FD operator with an optional leverage function.
     * <p>
     * Mirrors C++ v1.42.1 {@code FdmHestonOp(mesher, hestonProcess, quantoHelper, leverageFct, mixingFactor)} with
     * {@code quantoHelper = null}.
     *
     * @param mesher        2-D mesher (dim 0 = log-spot, dim 1 = variance)
     * @param hestonProcess the Heston process supplying r, q, kappa, theta, sigma, rho
     * @param mixingFactor  mixing factor applied to {@code sigma} (default 1.0)
     * @param leverageFct   optional Heston-SLV leverage surface {@code L(t, S)} (may be {@code null}; null ⇒
     *                      pure-Heston)
     */
    public FdmHestonOp(final FdmMesher mesher, final HestonProcess hestonProcess, final double mixingFactor,
            final LocalVolTermStructure leverageFct) {

        final double rho = hestonProcess.rho().currentLink().value();
        final double sigma = hestonProcess.sigma().currentLink().value();
        final double kappa = hestonProcess.kappa().currentLink().value();
        final double theta = hestonProcess.theta().currentLink().value();
        final double sigmaTimesMix = sigma * mixingFactor;

        // Heston v-S correlation: rho * sigma * v * d^2/dxdv  on the (0,1) mesh
        final Array vLoc = mesher.locations(1);
        final Array rhoSigmaV = vLoc.mul(rho * sigmaTimesMix);
        this.correlationMap = new SecondOrderMixedDerivativeOp(0, 1, mesher).mult(rhoSigmaV);

        this.dyMap = new FdmHestonVariancePart(mesher, hestonProcess.riskFreeRate().currentLink(), sigmaTimesMix, kappa,
                theta);

        this.dxMap = new FdmHestonEquityPart(mesher, hestonProcess.riskFreeRate().currentLink(),
                hestonProcess.dividendYield().currentLink(), leverageFct);
    }

    @Override
    public int size() {
        return 2;
    }

    @Override
    public void setTime(final double t1, final double t2) {
        dxMap.setTime(t1, t2);
        dyMap.setTime(t1, t2);
    }

    @Override
    public Array apply(final Array u) {
        return dyMap.getMap().apply(u).add(dxMap.getMap().apply(u)).add(correlationMap.apply(u).mul(dxMap.getL()));
    }

    @Override
    public Array applyDirection(final int direction, final Array r) {
        if ( direction == 0 )
            return dxMap.getMap().apply(r);
        if ( direction == 1 )
            return dyMap.getMap().apply(r);
        throw new IllegalArgumentException("direction too large");
    }

    @Override
    public Array applyMixed(final Array r) {
        return correlationMap.apply(r).mul(dxMap.getL());
    }

    @Override
    public Array solveSplitting(final int direction, final Array r, final double a) {
        if ( direction == 0 )
            return dxMap.getMap().solveSplitting(r, a, 1.0);
        if ( direction == 1 )
            return dyMap.getMap().solveSplitting(r, a, 1.0);
        throw new IllegalArgumentException("direction too large");
    }

    @Override
    public Array preconditioner(final Array r, final double dt) {
        return solveSplitting(1, solveSplitting(0, r, dt), dt);
    }

    @Override
    public Matrix toMatrix() {
        throw new UnsupportedOperationException("FdmHestonOp.toMatrix() not implemented; use toMatrixDecomp()");
    }

    @Override
    public List< Matrix > toMatrixDecomp() {
        final List< Matrix > ret = new ArrayList< Matrix >(3);
        ret.add(dxMap.getMap().toMatrix());
        ret.add(dyMap.getMap().toMatrix());
        ret.add(correlationMap.toMatrix());
        return Collections.unmodifiableList(ret);
    }

    // ------------------------------------------------------------------
    // Inner part: equity (log-spot) component, time-dependent.
    // Mirrors C++ FdmHestonEquityPart in
    // ql/methods/finitedifferences/operators/fdmhestonop.{hpp,cpp}.
    // ------------------------------------------------------------------

    /** Equity (log-spot) part of the Heston operator, time-dependent. */
    static class FdmHestonEquityPart {
        private final Array varianceValues;     // 0.5 * v (boundaries zeroed)
        private final Array volatilityValues;   // sqrt(2 * varianceValues) — for quanto helper (unused here)
        private final FirstDerivativeOp dxMap;
        private final TripleBandLinearOp dxxMap;
        private final FdmMesher mesher;
        private final YieldTermStructure rTS, qTS;
        private final LocalVolTermStructure leverageFct;
        private Array L;                        // leverage slice (refreshed each setTime when leverageFct != null)
        private final TripleBandLinearOp mapT;

        FdmHestonEquityPart(final FdmMesher mesher, final YieldTermStructure rTS, final YieldTermStructure qTS) {
            this(mesher, rTS, qTS, null);
        }

        FdmHestonEquityPart(final FdmMesher mesher, final YieldTermStructure rTS, final YieldTermStructure qTS,
                final LocalVolTermStructure leverageFct) {
            this.mesher = mesher;
            this.rTS = rTS;
            this.qTS = qTS;
            this.leverageFct = leverageFct;

            // C++: varianceValues_ = 0.5 * mesher->locations(1)
            final Array varVals = mesher.locations(1).mul(0.5);
            // On the boundary s_min and s_max the second derivative
            // d^2V/dS^2 is zero and due to Ito's Lemma the variance term
            // in the drift should vanish.
            for ( final FdmLinearOpIterator iter : mesher.layout() ) {
                final int c0 = iter.coordinates()[0];
                if ( c0 == 0 || c0 == mesher.layout().dim()[0] - 1 ) {
                    varVals.set(iter.index(), 0.0);
                }
            }
            this.varianceValues = varVals;
            // volatilityValues_ = sqrt(2 * varianceValues_)  (= sqrt(v) for interior)
            this.volatilityValues = varVals.mul(2.0).sqrt();
            // L starts at 1.0 (no leverage); refreshed in setTime when leverageFct != null.
            this.L = new Array(mesher.layout().size()).fill(1.0);

            this.dxMap = new FirstDerivativeOp(0, mesher);
            // dxxMap_ = SecondDerivativeOp(0,mesher).mult(0.5*mesher->locations(1))
            //        but with the boundary-zeroed varianceValues.
            this.dxxMap = new SecondDerivativeOp(0, mesher).mult(varianceValues);
            this.mapT = new TripleBandLinearOp(0, mesher);
        }

        void setTime(final double t1, final double t2) {
            final double r = rTS.forwardRate(t1, t2, Compounding.Continuous, Frequency.NoFrequency).rate();
            final double q = qTS.forwardRate(t1, t2, Compounding.Continuous, Frequency.NoFrequency).rate();

            // Refresh L(t,S) slice — identically 1 if no leverage fct.
            this.L = getLeverageFctSlice(t1, t2);
            final Array Lsquare = L.mul(L);
            // C++: dxxMap_.mult(Lsquare)  (with L=1, it's just dxxMap_)
            final TripleBandLinearOp dxxScaled = dxxMap.mult(Lsquare);

            // drift = r - q - varianceValues * Lsquare  (per grid point)
            // varianceValues already = 0.5 * v (boundaries zeroed).
            final Array drift = varianceValues.mul(Lsquare).mul(-1.0).add(r - q);
            // mapT_.axpyb(drift, dxMap_, dxxMap_*Lsquare, Array(1, -0.5*r))
            final Array constDiag = new Array(1).fill(-0.5 * r);
            mapT.axpyb(drift, dxMap, dxxScaled, constDiag);
        }

        /**
         * Compute the L(t,S) slice over all grid points.
         * <p>
         * Mirrors C++ v1.42.1 {@code FdmHestonEquityPart::getLeverageFctSlice}: for {@code v}-coordinate zero, evaluate
         * {@code L(t̄, max(min, min(max, exp(x))))} with a {@code 0.01} floor and
         * {@code t̄ = min(maxTime, 0.5*(t1+t2))}; other variance coordinates copy the equity-row value (L is
         * variance-independent in C++).
         */
        Array getLeverageFctSlice(final double t1, final double t2) {
            final Array v = new Array(mesher.layout().size()).fill(1.0);
            if ( leverageFct == null ) {
                return v;
            }
            final double t = 0.5 * (t1 + t2);
            final double time = Math.min(leverageFct.maxTime(), t);

            for ( final FdmLinearOpIterator iter : mesher.layout() ) {
                final int nx = iter.coordinates()[0];
                if ( iter.coordinates()[1] == 0 ) {
                    final double x = Math.exp(mesher.location(iter, 0));
                    final double spot = Math.min(leverageFct.maxStrike(), Math.max(leverageFct.minStrike(), x));
                    v.set(nx, Math.max(0.01, leverageFct.localVol(time, spot, true)));
                } else {
                    v.set(iter.index(), v.get(nx));
                }
            }
            return v;
        }

        TripleBandLinearOp getMap() {
            return mapT;
        }

        Array getL() {
            return L;
        }
    }

    // ------------------------------------------------------------------
    // Inner part: variance component, time-dependent only via discount.
    // Mirrors C++ FdmHestonVariancePart.
    // ------------------------------------------------------------------

    /** Variance part of the Heston operator. */
    static class FdmHestonVariancePart {
        private final TripleBandLinearOp dyMap;
        private final YieldTermStructure rTS;
        private final TripleBandLinearOp mapT;

        FdmHestonVariancePart(final FdmMesher mesher, final YieldTermStructure rTS, final double mixedSigma,
                final double kappa, final double theta) {
            this.rTS = rTS;
            // dyMap_ =   SecondDerivativeOp(1, mesher).mult(0.5 * mixedSigma^2 * v)
            //          + FirstDerivativeOp(1, mesher).mult(kappa * (theta - v))
            final Array vLoc = mesher.locations(1);
            final Array halfSigSqV = vLoc.mul(0.5 * mixedSigma * mixedSigma);
            final Array kappaThMinusV = vLoc.mul(-kappa).add(kappa * theta);
            this.dyMap = new SecondDerivativeOp(1, mesher).mult(halfSigSqV)
                    .add(new FirstDerivativeOp(1, mesher).mult(kappaThMinusV));
            this.mapT = new TripleBandLinearOp(1, mesher);
        }

        void setTime(final double t1, final double t2) {
            final double r = rTS.forwardRate(t1, t2, Compounding.Continuous, Frequency.NoFrequency).rate();
            // C++: mapT_.axpyb(Array(), dyMap_, dyMap_, Array(1, -0.5*r))
            // a==Array() means "no per-row scaling"; the helper mirrors that
            // by leaving dyMap untouched and adding the constant diagonal.
            final Array constDiag = new Array(1).fill(-0.5 * r);
            mapT.axpyb(new Array(0), dyMap, dyMap, constDiag);
        }

        TripleBandLinearOp getMap() {
            return mapT;
        }
    }
}
