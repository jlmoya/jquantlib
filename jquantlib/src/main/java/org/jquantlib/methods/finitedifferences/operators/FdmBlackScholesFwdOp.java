/*
 Copyright (C) 2012, 2013 Klaus Spanderen
 Copyright (C) 2014 Johannes Göttker-Schnetmann
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
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.LocalVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Frequency;

/**
 * Black-Scholes linear operator for the Fokker-Planck <strong>forward</strong>
 * Kolmogorov equation.
 *
 * <p>Java port of v1.42.1
 * {@code ql/methods/finitedifferences/operators/fdmblackscholesfwdop.{hpp,cpp}}.
 *
 * <p>Discretises the forward PDE for the transition density of the GBM (or
 * local-vol) process on a 1D log-spot mesh ({@code x = log S}):
 * <pre>
 *   dp/dt = - d/dx [(r - q - 0.5 sigma^2) p] + 0.5 d^2/dx^2 [sigma^2 p]
 * </pre>
 * The implementation matches C++ {@code FdmBlackScholesFwdOp::setTime}:
 * <pre>
 *   // local-vol branch (per-cell v[i] = sigma^2(t_mid, S_i)):
 *   mapT = dxMap.multR(- r + q + 0.5 v) + dxxMap.multR(0.5 v)
 *   // constant-vol branch (forward variance v from blackForwardVariance):
 *   mapT = (- r + q + 0.5 v) * dxMap + dxxMap.mult(0.5 v * 1)
 * </pre>
 *
 * <p>This is the <strong>forward</strong> companion to
 * {@link FdmBlackScholesOp} (backward PDE). Used by the Heston-SLV calibration
 * test infrastructure ({@code test-suite/hestonslvmodel.cpp}) for the
 * BS-process Fokker-Planck checks.
 *
 * <h3>illegalLocalVolOverwrite</h3>
 * Sentinel: a strictly negative value disables the per-cell try/catch wrap of
 * {@code localVol(...)} (matching C++ behaviour of
 * {@code -Null<Real>()}). With a non-negative value, any
 * {@code RuntimeException} thrown by the local-vol surface is swallowed and
 * replaced by {@code (illegalLocalVolOverwrite)^2} (see {@link
 * org.jquantlib.termstructures.volatilities.equityfx.NoExceptLocalVolSurface}
 * for the equivalent surface-level wrapper).
 *
 * @author Phase 5e.5b-CFC-d-131 port
 */
public class FdmBlackScholesFwdOp implements FdmLinearOpComposite {

    private final FdmMesher mesher;
    private final YieldTermStructure rTS;
    private final YieldTermStructure qTS;
    private final BlackVolTermStructure volTS;
    private final LocalVolTermStructure localVol; // null when localVol == false
    private final Array x;                        // empty when localVol == false
    private final FirstDerivativeOp  dxMap;
    private final TripleBandLinearOp dxxMap;
    private final TripleBandLinearOp mapT;
    private final double strike;
    private final double illegalLocalVolOverwrite;
    private final int direction;

    /**
     * Full-args ctor matching C++ {@code FdmBlackScholesFwdOp(...)}.
     *
     * @param mesher                   1D log-space mesh
     * @param process                  GBS process (provides r, q, vol, optionally local-vol)
     * @param strike                   strike for {@code blackForwardVariance} lookup (non-localVol branch)
     * @param localVol                 if {@code true} use {@code process.localVolatility()} per-cell
     * @param illegalLocalVolOverwrite negative ⇒ disable; ≥0 ⇒ fallback vol when surface throws
     * @param direction                mesh dimension (0 for single-asset)
     */
    public FdmBlackScholesFwdOp(final FdmMesher mesher,
                                final GeneralizedBlackScholesProcess process,
                                final double strike,
                                final boolean localVol,
                                final double illegalLocalVolOverwrite,
                                final int direction) {
        this.mesher    = mesher;
        this.rTS       = process.riskFreeRate().currentLink();
        this.qTS       = process.dividendYield().currentLink();
        this.volTS     = process.blackVolatility().currentLink();
        this.localVol  = localVol ? process.localVolatility().currentLink() : null;

        // C++: x_((localVol) ? Array(Exp(mesher->locations(direction))) : Array())
        this.x = localVol ? mesher.locations(direction).exp() : new Array(0);

        this.dxMap  = new FirstDerivativeOp(direction, mesher);
        this.dxxMap = new SecondDerivativeOp(direction, mesher);
        this.mapT   = new TripleBandLinearOp(direction, mesher);

        this.strike    = strike;
        this.illegalLocalVolOverwrite = illegalLocalVolOverwrite;
        this.direction = direction;
    }

    /** Default {@code direction=0}, mirrors C++ default. */
    public FdmBlackScholesFwdOp(final FdmMesher mesher,
                                final GeneralizedBlackScholesProcess process,
                                final double strike,
                                final boolean localVol,
                                final double illegalLocalVolOverwrite) {
        this(mesher, process, strike, localVol, illegalLocalVolOverwrite, 0);
    }

    /**
     * Convenience: {@code localVol=false}, default sentinel disables overwrite.
     * Matches the (most common) C++ call from
     * {@code testBlackScholesFokkerPlanckFwdEquation}:
     * {@code FdmBlackScholesFwdOp(mesher, process, strike, false)}.
     */
    public FdmBlackScholesFwdOp(final FdmMesher mesher,
                                final GeneralizedBlackScholesProcess process,
                                final double strike,
                                final boolean localVol) {
        // C++ default arg is -Null<Real>() → negative sentinel (= no overwrite).
        this(mesher, process, strike, localVol, -1.0, 0);
    }

    @Override
    public int size() { return 1; }

    @Override
    public void setTime(final double t1, final double t2) {
        // C++ uses InterestRate.rate() default compounding == Continuous.
        final double r = rTS.forwardRate(t1, t2, Compounding.Continuous,
                Frequency.NoFrequency, true).rate();
        final double q = qTS.forwardRate(t1, t2, Compounding.Continuous,
                Frequency.NoFrequency, true).rate();

        final int n = mesher.layout().size();

        if (localVol != null) {
            // Per-cell v[i] = sigma^2(midpoint, exp(x[i])).  C++:
            //   v[i] = squared(localVol_->localVol(0.5*(t1+t2), x_[i], true));
            // with try/catch ⇒ illegalLocalVolOverwrite^2 when negative.
            final Array v = new Array(n);
            final double tmid = 0.5 * (t1 + t2);
            for (final FdmLinearOpIterator iter : mesher.layout()) {
                final int i = iter.index();
                double sig;
                if (illegalLocalVolOverwrite < 0.0) {
                    sig = localVol.localVol(tmid, x.get(i), true);
                } else {
                    try {
                        sig = localVol.localVol(tmid, x.get(i), true);
                    } catch (final RuntimeException e) {
                        sig = illegalLocalVolOverwrite;
                    }
                }
                v.set(i, sig * sig);
            }

            // mapT = dxMap.multR(- r + q + 0.5 v) + dxxMap.multR(0.5 v)
            // C++ axpyb: this = a*x + y + b   with a={1.0}, b={0.0}
            final Array convCoef = new Array(n);
            for (int i = 0; i < n; ++i) {
                convCoef.set(i, -r + q + 0.5 * v.get(i));
            }
            final Array diffCoef = v.mul(0.5);

            final TripleBandLinearOp dx  = dxMap.multR(convCoef);
            final TripleBandLinearOp dxx = dxxMap.multR(diffCoef);

            mapT.axpyb(new Array(1).fill(1.0), dx, dxx, new Array(1).fill(0.0));
        } else {
            // Constant-vol branch: scalar forward-variance v.
            // C++: v = blackForwardVariance(t1, t2, strike)/(t2-t1);
            //      mapT_.axpyb(Array(1, - r + q + 0.5*v), dxMap_,
            //                  dxxMap_.mult(0.5*Array(layout.size(), v)),
            //                  Array(1, 0.0));
            final double v = volTS.blackForwardVariance(t1, t2, strike, true)
                    / (t2 - t1);

            final Array drift = new Array(1).fill(-r + q + 0.5 * v);
            final Array halfV = new Array(n).fill(0.5 * v);
            final Array bZero = new Array(1).fill(0.0);

            mapT.axpyb(drift, dxMap, dxxMap.mult(halfV), bZero);
        }
    }

    @Override
    public Array apply(final Array u) {
        return mapT.apply(u);
    }

    @Override
    public Array applyDirection(final int direction, final Array r) {
        if (direction == this.direction) {
            return mapT.apply(r);
        }
        return new Array(r.size()).fill(0.0);
    }

    @Override
    public Array applyMixed(final Array r) {
        return new Array(r.size()).fill(0.0);
    }

    @Override
    public Array solveSplitting(final int direction, final Array r, final double dt) {
        if (direction == this.direction) {
            return mapT.solveSplitting(r, dt, 1.0);
        }
        // C++ returns r unchanged for the inactive direction.
        return r;
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
