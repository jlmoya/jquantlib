/*
 Copyright (C) 2020 Lew Wei Hao
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

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.processes.CoxIngersollRossProcess;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Frequency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 2-D finite-difference operator for the equity/CIR-short-rate model PDE.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/operators/fdmcirop.{hpp,cpp}}.
 *
 * <p>The two dimensions are:
 * <ul>
 *   <li>dim 0: log-spot S (equity)</li>
 *   <li>dim 1: short-rate r (CIR)</li>
 * </ul>
 *
 * <p>The PDE is decomposed into three parts:
 * <ul>
 *   <li>Equity part: drift {@code (r - q - 0.5*v)} and
 *       {@code 0.5*v} d^2/dlogS^2 + diag {@code -0.5*r}, with
 *       {@code v = blackForwardVariance(t1,t2)/(t2-t1)}.</li>
 *   <li>Rates part: {@code sigma^2 * r} d^2/dr^2 + {@code k*(theta - r)}
 *       d/dr + diag {@code -0.5*r}.</li>
 *   <li>Mixed part: {@code 2 * rho * sigma * sqrt(v)} d^2/dlogSdr.</li>
 * </ul>
 *
 * <p><strong>Limitations vs. C++:</strong>
 * <ul>
 *   <li>{@code FdmQuantoHelper} is not yet ported; only the no-quanto path
 *       is implemented.</li>
 *   <li>{@code LocalVolTermStructure} leverage function is not yet ported;
 *       pure constant-vol Black-Scholes path only.</li>
 * </ul>
 *
 * @author Phase 5e.5b-CFC-d-86 port
 */
public class FdmCIROp implements FdmLinearOpComposite {

    private final FdmCIREquityPart dxMap;
    private final FdmCIRRatesPart dyMap;
    private final FdmCIRMixedPart dzMap;

    public FdmCIROp(final FdmMesher mesher, final CoxIngersollRossProcess cirProcess,
            final GeneralizedBlackScholesProcess bsProcess, final double rho, final double strike) {
        this.dxMap = new FdmCIREquityPart(mesher, bsProcess, strike);
        this.dyMap = new FdmCIRRatesPart(mesher, cirProcess.volatility(), cirProcess.speed(), cirProcess.level());
        this.dzMap = new FdmCIRMixedPart(mesher, cirProcess, bsProcess, rho, strike);
    }

    @Override
    public int size() {
        return 2;
    }

    @Override
    public void setTime(final double t1, final double t2) {
        dxMap.setTime(t1, t2);
        dyMap.setTime(t1, t2);
        dzMap.setTime(t1, t2);
    }

    @Override
    public Array apply(final Array u) {
        return dyMap.getMap().apply(u).add(dxMap.getMap().apply(u)).add(dzMap.getMap().apply(u));
    }

    @Override
    public Array applyMixed(final Array r) {
        return dzMap.getMap().apply(r);
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
        // Mirrors C++ v1.42.1 FdmLinearOpComposite::toMatrix default:
        //   std::accumulate(dcmp.begin()+1, dcmp.end(), SparseMatrix(dcmp.front()))
        // Sum of the per-direction matrices returned by toMatrixDecomp().
        final List< Matrix > dcmp = toMatrixDecomp();
        final Matrix acc = new Matrix(dcmp.get(0));
        for ( int i = 1; i < dcmp.size(); ++i ) {
            acc.addAssign(dcmp.get(i));
        }
        return acc;
    }

    @Override
    public List< Matrix > toMatrixDecomp() {
        final List< Matrix > ret = new ArrayList<>(3);
        ret.add(dxMap.getMap().toMatrix());
        ret.add(dyMap.getMap().toMatrix());
        ret.add(dzMap.getMap().toMatrix());
        return Collections.unmodifiableList(ret);
    }

    // ------------------------------------------------------------------
    // Inner part: equity (log-spot) component, time-dependent.
    // Mirrors C++ FdmCIREquityPart in fdmcirop.{hpp,cpp}.
    // ------------------------------------------------------------------

    /** Equity (log-spot) part of the CIR-BS operator, time-dependent. */
    static class FdmCIREquityPart {
        private final FirstDerivativeOp dxMap;
        private final TripleBandLinearOp dxxMap;
        private final TripleBandLinearOp mapT;

        private final FdmMesher mesher;
        private final YieldTermStructure qTS;
        private final double strike;
        private final BlackVolTermStructure sigma1;

        FdmCIREquityPart(final FdmMesher mesher, final GeneralizedBlackScholesProcess bsProcess, final double strike) {
            this.mesher = mesher;
            this.qTS = bsProcess.dividendYield().currentLink();
            this.strike = strike;
            this.sigma1 = bsProcess.blackVolatility().currentLink();

            this.dxMap = new FirstDerivativeOp(0, mesher);
            this.dxxMap = new SecondDerivativeOp(0, mesher);
            this.mapT = new TripleBandLinearOp(0, mesher);
        }

        void setTime(final double t1, final double t2) {
            final double q = qTS.forwardRate(t1, t2, Compounding.Continuous, Frequency.NoFrequency).rate();
            final double v = sigma1.blackForwardVariance(t1, t2, strike, true) / (t2 - t1);

            // C++: mapT_.axpyb(mesher_->locations(1) - q - 0.5*v, dxMap_,
            //                  dxxMap_.mult(Array(layout.size(), v/2)),
            //                  -0.5*mesher_->locations(1));
            // i.e. mapT_ = (r - q - 0.5*v) * dxMap_  +  (v/2) * dxxMap_
            //              + diag(-0.5 * r)        where r = locations(1).
            final Array rLoc = mesher.locations(1);
            final Array drift = rLoc.add(-q - 0.5 * v);
            final Array halfV = new Array(mesher.layout().size()).fill(0.5 * v);
            final TripleBandLinearOp dxxScaled = dxxMap.mult(halfV);
            final Array minusHalfR = rLoc.mul(-0.5);
            mapT.axpyb(drift, dxMap, dxxScaled, minusHalfR);
        }

        TripleBandLinearOp getMap() {
            return mapT;
        }
    }

    // ------------------------------------------------------------------
    // Inner part: CIR short-rate (dim 1) component, time-dependent only
    // via discount diag.
    // Mirrors C++ FdmCIRRatesPart.
    // ------------------------------------------------------------------

    /** Short-rate part of the CIR-BS operator. */
    static class FdmCIRRatesPart {
        private final TripleBandLinearOp dyMap;
        private final TripleBandLinearOp mapT;
        private final FdmMesher mesher;

        FdmCIRRatesPart(final FdmMesher mesher, final double sigma, final double kappa, final double theta) {
            this.mesher = mesher;
            // dyMap_ =   SecondDerivativeOp(1, mesher).mult(sigma^2 * r)
            //          + FirstDerivativeOp(1, mesher).mult(kappa * (theta - r))
            final Array rLoc = mesher.locations(1);
            final Array sigSqR = rLoc.mul(sigma * sigma);
            final Array kappaThMinusR = rLoc.mul(-kappa).add(kappa * theta);
            this.dyMap = new SecondDerivativeOp(1, mesher).mult(sigSqR)
                    .add(new FirstDerivativeOp(1, mesher).mult(kappaThMinusR));
            this.mapT = new TripleBandLinearOp(1, mesher);
        }

        void setTime(final double t1, final double t2) {
            // C++: mapT_.axpyb(Array(), dyMap_, dyMap_, -0.5*locations(1));
            final Array minusHalfR = mesher.locations(1).mul(-0.5);
            mapT.axpyb(new Array(0), dyMap, dyMap, minusHalfR);
        }

        TripleBandLinearOp getMap() {
            return mapT;
        }
    }

    // ------------------------------------------------------------------
    // Inner part: mixed (equity/short-rate) component, time-dependent
    // via forward variance.
    // Mirrors C++ FdmCIRMixedPart.
    // ------------------------------------------------------------------

    /** Mixed (cross-derivative) part of the CIR-BS operator. */
    static class FdmCIRMixedPart {
        private final NinePointLinearOp dyMap;
        private final FdmMesher mesher;
        private final BlackVolTermStructure sigma1;
        private final double strike;
        private NinePointLinearOp mapT;

        FdmCIRMixedPart(final FdmMesher mesher, final CoxIngersollRossProcess cirProcess,
                final GeneralizedBlackScholesProcess bsProcess, final double rho, final double strike) {
            this.mesher = mesher;
            this.sigma1 = bsProcess.blackVolatility().currentLink();
            this.strike = strike;

            // C++: dyMap_ = SecondOrderMixedDerivativeOp(0, 1, mesher)
            //               .mult(Array(layout.size(), 2*rho*cirProcess->volatility()));
            final Array twoRhoSigma = new Array(mesher.layout().size()).fill(2.0 * rho * cirProcess.volatility());
            this.dyMap = new SecondOrderMixedDerivativeOp(0, 1, mesher).mult(twoRhoSigma);
            this.mapT = new NinePointLinearOp(0, 1, mesher);
        }

        void setTime(final double t1, final double t2) {
            // C++: v = sqrt(blackForwardVariance(t1, t2, strike)/(t2 - t1))
            //      mapT_.swap(NinePointLinearOp(dyMap_.mult(Array(size, v))))
            final double v = Math.sqrt(sigma1.blackForwardVariance(t1, t2, strike, true) / (t2 - t1));
            final Array vArr = new Array(mesher.layout().size()).fill(v);
            // No swap method on Java NinePointLinearOp — simply rebuild.
            this.mapT = dyMap.mult(vArr);
        }

        NinePointLinearOp getMap() {
            return mapT;
        }
    }
}
