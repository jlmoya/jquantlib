/*
 Copyright (C) 2012, 2013, 2015 Klaus Spanderen
 Copyright (C) 2014, 2015 Johannes Goettker-Schnetmann

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
import org.jquantlib.methods.finitedifferences.operators.FdmSquareRootFwdOp.TransformationType;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.LocalVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Frequency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 2D Heston Fokker-Planck forward operator.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/operators/fdmhestonfwdop.{hpp,cpp}}.
 * <p>
 * Discretizes the forward (Kolmogorov) equation associated with the Heston stochastic-volatility process. The two grid
 * dimensions are:
 * <ul>
 *   <li>direction 0: log-spot {@code x = log(S/F)}.</li>
 *   <li>direction 1: variance {@code v} (or its transformed coordinate).</li>
 * </ul>
 * Optionally accepts a {@link LocalVolTermStructure} leverage function so
 * the operator can be used inside a Heston stochastic-local-vol calibration
 * loop (Phase 5h.5-SLV).
 *
 * @author Phase 5h.5-SLV port
 */
public class FdmHestonFwdOp implements FdmLinearOpComposite {

    private final TransformationType type;
    private final double kappa, theta, sigma, rho, mixedSigma;
    @SuppressWarnings( "unused" )
    private final double v0;

    private final YieldTermStructure rTS;
    private final YieldTermStructure qTS;

    private final Array varianceValues;
    private final FirstDerivativeOp dxMap;
    private final ModTripleBandLinearOp dxxMap;
    private final ModTripleBandLinearOp boundary;
    private final TripleBandLinearOp mapX;
    private final FdmSquareRootFwdOp mapY;
    private final NinePointLinearOp correlation;
    private final LocalVolTermStructure leverageFct;
    private final FdmMesher mesher;
    private Array L;

    public FdmHestonFwdOp(final FdmMesher mesher, final HestonProcess process) {
        this(mesher, process, TransformationType.Plain, null, 1.0);
    }

    public FdmHestonFwdOp(final FdmMesher mesher, final HestonProcess process, final TransformationType type) {
        this(mesher, process, type, null, 1.0);
    }

    public FdmHestonFwdOp(final FdmMesher mesher, final HestonProcess process, final TransformationType type,
            final LocalVolTermStructure leverageFct) {
        this(mesher, process, type, leverageFct, 1.0);
    }

    public FdmHestonFwdOp(final FdmMesher mesher, final HestonProcess process, final TransformationType type,
            final LocalVolTermStructure leverageFct, final double mixingFactor) {
        this.mesher = mesher;
        this.type = type;
        this.kappa = process.kappa().currentLink().value();
        this.theta = process.theta().currentLink().value();
        this.sigma = process.sigma().currentLink().value();
        this.rho = process.rho().currentLink().value();
        this.v0 = process.v0().currentLink().value();
        this.mixedSigma = mixingFactor * sigma;
        this.rTS = process.riskFreeRate().currentLink();
        this.qTS = process.dividendYield().currentLink();
        this.leverageFct = leverageFct;

        // varianceValues_ = 0.5 * v-mesh
        this.varianceValues = mesher.locations(1).mul(0.5);

        // dxMap_ = FirstDerivativeOp(0, mesher)
        this.dxMap = new FirstDerivativeOp(0, mesher);

        // dxxMap_ = SecondDeriv(0).mult(0.5*v) (or 0.5*exp(v) for Log)
        final Array vLoc1 = mesher.locations(1);
        final Array dxxCoeff;
        if ( type == TransformationType.Log ) {
            dxxCoeff = vLoc1.exp().mul(0.5);
        } else {
            dxxCoeff = vLoc1.mul(0.5);
        }
        this.dxxMap = new ModTripleBandLinearOp(new SecondDerivativeOp(0, mesher).mult(dxxCoeff));

        // boundary_ = SecondDeriv(0).mult(0) — effectively a zero op container
        // sized like dxxMap so we can patch boundary cells in setLowerBC/setUpperBC
        final Array zero0 = new Array(mesher.locations(0).size());
        this.boundary = new ModTripleBandLinearOp(new SecondDerivativeOp(0, mesher).mult(zero0));

        // mapX_ = TripleBandLinearOp(0, mesher) — placeholder, populated each setTime
        this.mapX = new TripleBandLinearOp(0, mesher);

        // mapY_ = FdmSquareRootFwdOp on direction 1
        this.mapY = new FdmSquareRootFwdOp(mesher, kappa, theta, mixedSigma, 1, type);

        // correlation_ = SecondOrderMixedDerivativeOp(0, 1)
        //   .mult( rho*mixedSigma * v )   (or rho*mixedSigma*1 for Log)
        final Array corrCoeff;
        if ( type == TransformationType.Log ) {
            corrCoeff = new Array(mesher.layout().size()).fill(rho * mixedSigma);
        } else {
            corrCoeff = vLoc1.mul(rho * mixedSigma);
        }
        this.correlation = new SecondOrderMixedDerivativeOp(0, 1, mesher).mult(corrCoeff);

        // Zero-flux boundary condition
        final int n = mesher.layout().dim()[1];
        final double lowerBoundaryFactor = mapY.lowerBoundaryFactor(type);
        final double upperBoundaryFactor = mapY.upperBoundaryFactor(type);

        final double logFacLow = (type == TransformationType.Log) ? Math.exp(mapY.v(0)) : 1.0;
        final double logFacUpp = (type == TransformationType.Log) ? Math.exp(mapY.v(n + 1)) : 1.0;

        final double alpha = -2 * rho / mixedSigma * lowerBoundaryFactor * logFacLow;
        final double beta = -2 * rho / mixedSigma * upperBoundaryFactor * logFacUpp;

        final ModTripleBandLinearOp fDx = new ModTripleBandLinearOp(new FirstDerivativeOp(0, mesher));

        for ( final FdmLinearOpIterator iter : mesher.layout() ) {
            if ( iter.coordinates()[1] == 0 ) {
                final int idx = iter.index();
                if ( leverageFct == null ) {
                    dxxMap.addUpper(idx, alpha * fDx.upperAt(idx));
                    dxxMap.addDiag(idx, alpha * fDx.diagAt(idx));
                    dxxMap.addLower(idx, alpha * fDx.lowerAt(idx));
                }
                boundary.setUpper(idx, alpha * fDx.upperAt(idx));
                boundary.setDiag(idx, alpha * fDx.diagAt(idx));
                boundary.setLower(idx, alpha * fDx.lowerAt(idx));
            } else if ( iter.coordinates()[1] == n - 1 ) {
                final int idx = iter.index();
                if ( leverageFct == null ) {
                    dxxMap.addUpper(idx, beta * fDx.upperAt(idx));
                    dxxMap.addDiag(idx, beta * fDx.diagAt(idx));
                    dxxMap.addLower(idx, beta * fDx.lowerAt(idx));
                }
                boundary.setUpper(idx, beta * fDx.upperAt(idx));
                boundary.setDiag(idx, beta * fDx.diagAt(idx));
                boundary.setLower(idx, beta * fDx.lowerAt(idx));
            }
        }
    }

    @Override
    public int size() {
        return 2;
    }

    @Override
    public void setTime(final double t1, final double t2) {
        final double r = rTS.forwardRate(t1, t2, Compounding.Continuous, Frequency.NoFrequency).rate();
        final double q = qTS.forwardRate(t1, t2, Compounding.Continuous, Frequency.NoFrequency).rate();

        if ( leverageFct != null ) {
            this.L = getLeverageFctSlice(t1, t2);
            final Array Lsquare = L.mul(L);
            final Array minusRplusQ = new Array(new double[] { -r + q });
            if ( type == TransformationType.Plain ) {
                final TripleBandLinearOp y = dxxMap.multR(Lsquare).add(boundary.multR(L))
                        .add(dxMap.multR(L.mul(rho * mixedSigma))).add(dxMap.mult(varianceValues).multR(Lsquare));
                mapX.axpyb(minusRplusQ, dxMap, y, new Array(0));
            } else if ( type == TransformationType.Power ) {
                final TripleBandLinearOp y = dxxMap.multR(Lsquare).add(boundary.multR(L))
                        .add(dxMap.multR(L.mul(rho * 2.0 * kappa * theta / mixedSigma)))
                        .add(dxMap.mult(varianceValues).multR(Lsquare));
                mapX.axpyb(minusRplusQ, dxMap, y, new Array(0));
            } else { // Log
                final Array halfExp2v = varianceValues.mul(2.0).exp().mul(0.5);
                final TripleBandLinearOp y = dxxMap.multR(Lsquare).add(boundary.multR(L))
                        .add(dxMap.mult(halfExp2v).multR(Lsquare));
                mapX.axpyb(minusRplusQ, dxMap, y, new Array(0));
            }
        } else {
            if ( type == TransformationType.Plain ) {
                final Array a = varianceValues.add(-r + q + rho * mixedSigma);
                mapX.axpyb(a, dxMap, dxxMap, new Array(0));
            } else if ( type == TransformationType.Power ) {
                final Array a = varianceValues.add(-r + q + rho * 2.0 * kappa * theta / mixedSigma);
                mapX.axpyb(a, dxMap, dxxMap, new Array(0));
            } else { // Log
                final Array halfExp2v = varianceValues.mul(2.0).exp().mul(0.5);
                final Array a = halfExp2v.add(-r + q);
                mapX.axpyb(a, dxMap, dxxMap, new Array(0));
            }
        }
    }

    @Override
    public Array apply(final Array u) {
        if ( leverageFct != null ) {
            return mapX.apply(u).add(mapY.apply(u)).add(correlation.apply(L.mul(u)));
        } else {
            return mapX.apply(u).add(mapY.apply(u)).add(correlation.apply(u));
        }
    }

    @Override
    public Array applyMixed(final Array u) {
        if ( leverageFct != null ) {
            return correlation.apply(L.mul(u));
        } else {
            return correlation.apply(u);
        }
    }

    @Override
    public Array applyDirection(final int direction, final Array u) {
        if ( direction == 0 )
            return mapX.apply(u);
        if ( direction == 1 )
            return mapY.apply(u);
        throw new IllegalArgumentException("direction too large");
    }

    @Override
    public Array solveSplitting(final int direction, final Array u, final double s) {
        if ( direction == 0 )
            return mapX.solveSplitting(u, s, 1.0);
        if ( direction == 1 )
            return mapY.solveSplitting(1, u, s);
        throw new IllegalArgumentException("direction too large");
    }

    @Override
    public Array preconditioner(final Array u, final double dt) {
        return solveSplitting(1, u, dt);
    }

    private Array getLeverageFctSlice(final double t1, final double t2) {
        final Array v = new Array(mesher.layout().size()).fill(1.0);
        if ( leverageFct == null )
            return v;

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

    @Override
    public Matrix toMatrix() {
        // C++ does not implement this either — composite of three matrices.
        throw new UnsupportedOperationException("FdmHestonFwdOp.toMatrix() not implemented (use toMatrixDecomp)");
    }

    @Override
    public List< Matrix > toMatrixDecomp() {
        final List< Matrix > ret = new ArrayList< Matrix >(3);
        ret.add(mapX.toMatrix());
        ret.add(mapY.toMatrix());
        ret.add(correlation.toMatrix());
        return Collections.unmodifiableList(ret);
    }
}
