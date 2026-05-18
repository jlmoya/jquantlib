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
import org.jquantlib.termstructures.LocalVolTermStructure;
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
 * <h3>Local-vol branch</h3>
 * When {@code localVol == true} the op uses {@code process.localVolatility()}
 * to evaluate the spot-dependent variance at each grid node (mirroring the
 * C++ {@code FdmBlackScholesOp::setTime} localVol branch). The mesher's
 * log-space locations are exponentiated once at construction
 * ({@code x = exp(ln S)}) and reused on every {@link #setTime} call.
 *
 * <h3>Deviations from C++</h3>
 * <ul>
 *   <li>Quanto-helper branch is deferred (Phase 2m.5).</li>
 * </ul>
 *
 * @author Phase 2m Track A port
 */
public class FdmBlackScholesOp implements FdmLinearOpComposite {

    private final FdmMesher mesher;
    private final YieldTermStructure rTS;
    private final YieldTermStructure qTS;
    private final BlackVolTermStructure volTS;
    /** Non-null iff localVol is enabled. */
    private final LocalVolTermStructure localVol;
    /** Spot-space locations {@code S = exp(ln S)}; non-null iff localVol enabled. */
    private final Array x;
    /** Sentinel meaning "no override"; negative non-{@link Double#isNaN} value. */
    private final double illegalLocalVolOverwrite;
    private final int direction;
    private final double strike;

    private final FirstDerivativeOp  dxMap;
    private final SecondDerivativeOp dxxMap;
    private final TripleBandLinearOp mapT;

    /**
     * Convenience ctor (no local-vol).
     *
     * @param mesher    1D FDM mesh (log-space)
     * @param process   GBS process (provides r, q, vol term structures)
     * @param strike    option strike used for blackForwardVariance lookup
     * @param direction mesh dimension index (0 for single-asset)
     */
    public FdmBlackScholesOp(final FdmMesher mesher,
                              final GeneralizedBlackScholesProcess process,
                              final double strike,
                              final int direction) {
        this(mesher, process, strike, false, Double.NaN, direction);
    }

    /**
     * Full ctor mirroring C++ {@code FdmBlackScholesOp(mesher, process, strike,
     * localVol, illegalLocalVolOverwrite, direction)}.
     *
     * @param mesher                    1D FDM mesh (log-space)
     * @param process                   GBS process (provides r, q, vol term
     *                                  structures, and {@code localVolatility()}
     *                                  when {@code localVol == true})
     * @param strike                    option strike used for the constant-vol
     *                                  branch's {@code blackForwardVariance}
     *                                  lookup; ignored when {@code localVol == true}
     * @param localVol                  when {@code true}, evaluate variance via
     *                                  {@code process.localVolatility().localVol(t, S)}
     *                                  at every grid node
     * @param illegalLocalVolOverwrite  fallback {@code sigma} value substituted
     *                                  whenever {@code localVol(...)} throws;
     *                                  {@link Double#NaN} or any negative value
     *                                  disables the fallback (i.e. the exception
     *                                  propagates), matching C++'s sentinel
     *                                  {@code -Null<Real>}
     * @param direction                 mesh dimension index (0 for single-asset)
     */
    public FdmBlackScholesOp(final FdmMesher mesher,
                              final GeneralizedBlackScholesProcess process,
                              final double strike,
                              final boolean localVol,
                              final double illegalLocalVolOverwrite,
                              final int direction) {
        this.mesher    = mesher;
        this.rTS       = process.riskFreeRate().currentLink();
        this.qTS       = process.dividendYield().currentLink();
        this.volTS     = process.blackVolatility().currentLink();
        this.direction = direction;
        this.strike    = strike;
        this.illegalLocalVolOverwrite = illegalLocalVolOverwrite;

        if (localVol) {
            this.localVol = process.localVolatility().currentLink();
            // x = exp(log-space locations) -> spot-space grid nodes
            this.x = mesher.locations(direction).exp();
        } else {
            this.localVol = null;
            this.x        = null;
        }

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

        if (localVol != null) {
            // Per-node variance from the local-vol surface, evaluated at
            // mid-step time (0.5 * (t1 + t2)) and the spot-space grid node.
            // Mirrors C++ FdmBlackScholesOp::setTime localVol branch.
            final boolean haveOverride =
                    !Double.isNaN(illegalLocalVolOverwrite) && illegalLocalVolOverwrite >= 0.0;
            final double tMid = 0.5 * (t1 + t2);
            final Array v = new Array(n);
            final Array drift = new Array(n);
            for (int i = 0; i < n; ++i) {
                double sigma;
                if (haveOverride) {
                    try {
                        sigma = localVol.localVol(tMid, x.get(i), true);
                    } catch (final RuntimeException e) {
                        sigma = illegalLocalVolOverwrite;
                    }
                } else {
                    sigma = localVol.localVol(tMid, x.get(i), true);
                }
                final double sigma2 = sigma * sigma;
                v.set(i, sigma2);
                drift.set(i, r - q - 0.5 * sigma2);
            }
            final Array halfV = new Array(n);
            for (int i = 0; i < n; ++i) {
                halfV.set(i, 0.5 * v.get(i));
            }
            final Array minusR = new Array(1).fill(-r);
            mapT.axpyb(drift, dxMap, dxxMap.mult(halfV), minusR);
            return;
        }

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
