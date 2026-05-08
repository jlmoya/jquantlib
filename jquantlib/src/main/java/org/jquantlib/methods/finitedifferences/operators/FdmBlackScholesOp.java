/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008, 2009 Ralph Schreyer
 Copyright (C) 2008, 2009 Klaus Spanderen

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
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.time.Frequency;

/**
 * Finite-difference operator for the Black-Scholes PDE in log-space.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/operators/fdmblackscholesop.{hpp,cpp}}.
 * <p>
 * The PDE in log-space ({@code x = ln S}) reads:
 * <pre>
 *   dV/dt + (r - q - 0.5 sigma^2) dV/dx + 0.5 sigma^2 d^2V/dx^2 - r V = 0
 * </pre>
 * {@link #setTime} recomputes the time-dependent coefficients using
 * {@code r = rTS.forwardRate(t1, t2, Continuous)}, etc.
 *
 * <h3>Deviations from C++</h3>
 * <ul>
 *   <li>Local-vol and Quanto-helper branches are deferred (Phase 2m.5).</li>
 * </ul>
 *
 * @author Phase 2m Track A port
 */
public class FdmBlackScholesOp implements FdmLinearOpComposite {

    private final FdmMesher mesher;
    private final YieldTermStructure rTS;
    private final YieldTermStructure qTS;
    private final BlackVolTermStructure volTS;
    private final int direction;
    private final double strike;

    private final FirstDerivativeOp  dxMap;
    private final SecondDerivativeOp dxxMap;
    private final TripleBandLinearOp mapT;

    /**
     * @param mesher    1D FDM mesh (log-space)
     * @param process   GBS process (provides r, q, vol term structures)
     * @param strike    option strike used for blackForwardVariance lookup
     * @param direction mesh dimension index (0 for single-asset)
     */
    public FdmBlackScholesOp(final FdmMesher mesher,
                              final GeneralizedBlackScholesProcess process,
                              final double strike,
                              final int direction) {
        this.mesher    = mesher;
        this.rTS       = process.riskFreeRate().currentLink();
        this.qTS       = process.dividendYield().currentLink();
        this.volTS     = process.blackVolatility().currentLink();
        this.direction = direction;
        this.strike    = strike;

        this.dxMap  = new FirstDerivativeOp(direction, mesher);
        this.dxxMap = new SecondDerivativeOp(direction, mesher);
        this.mapT   = new TripleBandLinearOp(direction, mesher);
    }

    @Override
    public int size() {
        return 1;
    }

    /**
     * Update time-dependent coefficients for the step {@code [t1, t2]}.
     * <p>
     * Computes continuous forward rates {@code r} and {@code q}, and the
     * forward variance {@code v = sigma_fwd^2} over the same interval.
     * Then:
     * <pre>
     *   mapT = (r - q - 0.5 v) * d/dx + 0.5 v * d^2/dx^2 - r
     * </pre>
     * Matches C++ {@code FdmBlackScholesOp::setTime} (non-localVol,
     * non-quanto branch).
     */
    @Override
    public void setTime(final double t1, final double t2) {
        final double r = rTS.forwardRate(t1, t2, Compounding.Continuous,
                Frequency.NoFrequency, true).rate();
        final double q = qTS.forwardRate(t1, t2, Compounding.Continuous,
                Frequency.NoFrequency, true).rate();

        final int n = mesher.layout().size();
        final double v = volTS.blackForwardVariance(t1, t2, strike, true) / (t2 - t1);

        final Array drift = new Array(1).fill(r - q - 0.5 * v);
        final Array halfV = new Array(n).fill(0.5 * v);
        final Array minusR = new Array(1).fill(-r);

        // mapT = (r-q-0.5v)*dxMap + (0.5v)*dxxMap - r*I
        mapT.axpyb(drift, dxMap, dxxMap.mult(halfV), minusR);
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
