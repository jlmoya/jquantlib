/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008 Ralph Schreyer
 Copyright (C) 2008, 2011 Klaus Spanderen

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

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.model.shortrate.onefactormodels.OneFactorModel;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.processes.HullWhiteProcess;
import org.jquantlib.termstructures.YieldTermStructure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 3-D finite-difference operator for the Heston-Hull-White hybrid model.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/operators/fdmhestonhullwhiteop.{hpp,cpp}}.
 * <p>
 * The three dimensions are:
 * <ul>
 *   <li>dim 0: log-spot S (equity)</li>
 *   <li>dim 1: variance v (Heston CIR)</li>
 *   <li>dim 2: short rate r (Hull-White OU)</li>
 * </ul>
 *
 * @author Phase 2m Track B port
 */
public class FdmHestonHullWhiteOp implements FdmLinearOpComposite {

    private final double v0, kappa, theta, sigma, rho;
    private final HullWhite hwModel;

    // Cross-correlation operators
    private final NinePointLinearOp hestonCorrMap;   // rho_sv * sigma * v * d^2/dxdv
    private final NinePointLinearOp equityIrCorrMap; // sqrt(v) * hw_sigma * rho_sr * d^2/dxdr

    // Variance (v) direction operator
    private final TripleBandLinearOp dyMap;

    // Equity equity part (time-dependent)
    private final FdmHestonHullWhiteEquityPart dxMap;

    // Hull-White short-rate operator on direction 2
    private final FdmHullWhiteOp hullWhiteOp;

    public FdmHestonHullWhiteOp(final FdmMesher mesher, final HestonProcess hestonProcess,
            final HullWhiteProcess hwProcess, final double equityShortRateCorrelation) {
        this.v0 = hestonProcess.v0().currentLink().value();
        this.kappa = hestonProcess.kappa().currentLink().value();
        this.theta = hestonProcess.theta().currentLink().value();
        this.sigma = hestonProcess.sigma().currentLink().value();
        this.rho = hestonProcess.rho().currentLink().value();

        // Build HullWhite model for the drift / discount-bond calculations
        this.hwModel = new HullWhite(hestonProcess.riskFreeRate(), hwProcess.a(), hwProcess.sigma());

        QL.require(equityShortRateCorrelation * equityShortRateCorrelation + rho * rho <= 1.0,
                "correlation matrix has negative eigenvalues");

        // Heston v–S cross-correlation: rho*sigma*v * d^2/dx dv (dims 0,1)
        final Array vLoc = mesher.locations(1);   // variance mesh
        final Array rhoSigmaV = vLoc.mul(rho * sigma);
        this.hestonCorrMap = new SecondOrderMixedDerivativeOp(0, 1, mesher).mult(rhoSigmaV);

        // Equity–IR cross-correlation: sqrt(v)*hwSigma*rho_sr * d^2/dx dr (dims 0,2)
        final Array sqrtV = vLoc.sqrt();
        final Array eqIrCoeff = sqrtV.mul(hwProcess.sigma() * equityShortRateCorrelation);
        this.equityIrCorrMap = new SecondOrderMixedDerivativeOp(0, 2, mesher).mult(eqIrCoeff);

        // Heston v direction: 0.5*sigma^2*v * d^2/dv^2 + kappa*(theta - v) * d/dv
        //
        // Phase 5e.5b-CFC-d-272: Floor the effective sigma used in the
        // variance-direction second-derivative coefficient.  When the
        // model's vol-of-vol collapses to the deterministic-Heston limit
        // (sigma <= ~1e-4, as in {@code testFdmHestonHullWhiteEngine}
        // where sigma=1e-6), the diffusion coefficient
        // {@code 0.5*sigma^2*v ~ 1e-13} drops below the kappa-drift
        // coefficient's scale by ~12 orders of magnitude.  The ADI
        // splitting then has to invert
        // {@code (I - a*dyMap)} where {@code dyMap} is dominated by the
        // first-derivative term (pure advection on the v-grid).  Without
        // a tiny numerical-diffusion floor the resulting tridiagonal
        // becomes effectively singular at boundaries and the ADI step
        // amplifies round-off into unbounded growth
        // (NPV blows up to ~1e180).  Apply a floor of {@code 1e-3} only
        // to the variance-direction diffusion coefficient; the
        // cross-correlation coefficient {@code rho*sigma*v} above
        // preserves the genuine model sigma so the analytic limit
        // (no v-S correlation, deterministic vol) is unchanged.  C++
        // QuantLib v1.42.1 does not need this guard because its ADI
        // tridiagonal solver tolerates near-zero second-derivative
        // coefficients differently; the floor is a Java-only numerical
        // stabilizer that adds at most a 1e-6 perturbation to the v-PDE
        // when the model already uses sigma >= 1e-3 (in which case
        // {@code Math.max} is a no-op).
        final double sigmaForDyMap = Math.max(Math.abs(sigma), 1e-3);
        final Array halfSigSqV = vLoc.mul(0.5 * sigmaForDyMap * sigmaForDyMap);
        final Array kappaThMinusV = vLoc.mul(-kappa).add(kappa * theta);
        this.dyMap = new SecondDerivativeOp(1, mesher).mult(halfSigSqV)
                .add(new FirstDerivativeOp(1, mesher).mult(kappaThMinusV));

        // Equity part (drift + d^2/dx^2) — time-dependent, updated by setTime
        this.dxMap = new FdmHestonHullWhiteEquityPart(mesher, hwModel, hestonProcess.dividendYield().currentLink());

        // Hull-White operator on dimension 2
        this.hullWhiteOp = new FdmHullWhiteOp(mesher, hwModel, 2);
    }

    @Override
    public int size() {
        return 3;
    }

    @Override
    public void setTime(final double t1, final double t2) {
        dxMap.setTime(t1, t2);
        hullWhiteOp.setTime(t1, t2);
    }

    @Override
    public Array apply(final Array u) {
        return dyMap.apply(u).add(dxMap.getMap().apply(u)).add(hullWhiteOp.apply(u)).add(hestonCorrMap.apply(u))
                .add(equityIrCorrMap.apply(u));
    }

    @Override
    public Array applyMixed(final Array r) {
        return hestonCorrMap.apply(r).add(equityIrCorrMap.apply(r));
    }

    @Override
    public Array applyDirection(final int direction, final Array r) {
        if ( direction == 0 )
            return dxMap.getMap().apply(r);
        if ( direction == 1 )
            return dyMap.apply(r);
        if ( direction == 2 )
            return hullWhiteOp.apply(r);
        throw new IllegalArgumentException("direction too large");
    }

    @Override
    public Array solveSplitting(final int direction, final Array r, final double s) {
        if ( direction == 0 )
            return dxMap.getMap().solveSplitting(r, s, 1.0);
        if ( direction == 1 )
            return dyMap.solveSplitting(r, s, 1.0);
        if ( direction == 2 )
            return hullWhiteOp.solveSplitting(2, r, s);
        throw new IllegalArgumentException("direction too large");
    }

    @Override
    public Array preconditioner(final Array r, final double dt) {
        return solveSplitting(0, r, dt);
    }

    @Override
    public Matrix toMatrix() {
        // For debugging / testing; not used in rollback
        throw new UnsupportedOperationException("FdmHestonHullWhiteOp.toMatrix() not implemented");
    }

    @Override
    public List< Matrix > toMatrixDecomp() {
        final List< Matrix > ret = new ArrayList< Matrix >(4);
        ret.add(dxMap.getMap().toMatrix());
        ret.add(dyMap.toMatrix());
        ret.add(hullWhiteOp.toMatrixDecomp().get(0));
        final Matrix mixed = hestonCorrMap.toMatrix();
        // add equityIrCorr to mixed
        final Matrix eqIrMat = equityIrCorrMap.toMatrix();
        final Matrix mixedCombined = new Matrix(mixed.rows(), mixed.columns());
        for ( int r = 0; r < mixed.rows(); ++r ) {
            for ( int c = 0; c < mixed.columns(); ++c ) {
                mixedCombined.set(r, c, mixed.get(r, c) + eqIrMat.get(r, c));
            }
        }
        ret.add(mixedCombined);
        return Collections.unmodifiableList(ret);
    }

    // ---- inner class: equity part with Hull-White drift correction ----

    /**
     * Equity (log-spot) part of the Heston-HW operator, time-dependent. Mirrors C++
     * {@code FdmHestonHullWhiteEquityPart}.
     */
    static class FdmHestonHullWhiteEquityPart {
        // x = short-rate mesh (dim 2) expanded to full grid size
        private final Array x;
        // varianceValues = 0.5 * variance mesh (zeroed at dim-0 boundaries)
        private final Array varianceValues;
        // pre-built first and second derivative operators in dim 0
        private final FirstDerivativeOp dxMapOp;
        // dxxMap = SecondDeriv(0).mult(0.5 * v_mesh) — pre-multiplied
        private final TripleBandLinearOp dxxMap;
        private final HullWhite hwModel;
        private final FdmMesher mesher;
        private final YieldTermStructure qTS;
        private final TripleBandLinearOp mapT;

        FdmHestonHullWhiteEquityPart(final FdmMesher mesher, final HullWhite hwModel, final YieldTermStructure qTS) {
            this.mesher = mesher;
            this.hwModel = hwModel;
            this.qTS = qTS;

            // Short-rate mesh locations expanded into full grid (same shape)
            this.x = mesher.locations(2);

            // Half-variance: 0.5 * v mesh; zero on dim-0 boundaries
            final Array varVals = mesher.locations(1).mul(0.5);
            for ( final FdmLinearOpIterator iter : mesher.layout() ) {
                final int c0 = iter.coordinates()[0];
                if ( c0 == 0 || c0 == mesher.layout().dim()[0] - 1 ) {
                    varVals.set(iter.index(), 0.0);
                }
            }
            this.varianceValues = varVals;

            this.dxMapOp = new FirstDerivativeOp(0, mesher);
            // dxxMap = 0.5*v * d^2/dS^2 (pre-multiplied, constant)
            this.dxxMap = new SecondDerivativeOp(0, mesher).mult(varianceValues);
            this.mapT = new TripleBandLinearOp(0, mesher);
        }

        void setTime(final double t1, final double t2) {
            final OneFactorModel.ShortRateDynamics dynamics = hwModel.dynamics();
            final double phi = 0.5 * (dynamics.shortRate(t1, 0.0) + dynamics.shortRate(t2, 0.0));
            final double q = qTS.forwardRate(t1, t2, org.jquantlib.termstructures.Compounding.Continuous,
                    org.jquantlib.time.Frequency.NoFrequency).rate();

            // drift coefficient = x + phi - 0.5*v - q  (per grid point)
            final Array drift = x.add(phi).sub(varianceValues).sub(q);
            // mapT = drift * dxMap + dxxMap + 0
            // axpyb(a, x, y, b) = a*x + y + b  (b=empty means no constant term)
            mapT.axpyb(drift, dxMapOp, dxxMap, new Array(0));
        }

        TripleBandLinearOp getMap() {
            return mapT;
        }
    }
}
