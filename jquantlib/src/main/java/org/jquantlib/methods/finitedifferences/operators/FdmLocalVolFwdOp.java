/*
 Copyright (C) 2015 Johannes Göttker-Schnetmann
 Copyright (C) 2015 Klaus Spanderen
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

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.LocalVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Local-volatility linear operator for the Fokker-Planck forward equation.
 *
 * <p>Java port of v1.42.1
 * {@code ql/methods/finitedifferences/operators/fdmlocalvolfwdop.{hpp,cpp}}.
 *
 * <p>Discretises the forward Kolmogorov equation
 * <pre>
 *   dp/dt = - d/dx [(r - q - 0.5 sigma^2(t,S)) p] + 0.5 d^2/dx^2 [sigma^2(t,S) p]
 * </pre>
 * on a 1D log-spot mesh ({@code x = log(S)}) using a TripleBandLinearOp formulation that matches C++:
 * <pre>
 *   mapT = dxMap.multR(- r + q + 0.5 v) + dxxMap.multR(0.5 v)
 * </pre>
 * where {@code v[i] = sigma^2(0.5*(t1+t2), exp(x[i]))}.
 *
 * <p>Used by {@link
 * org.jquantlib.methods.finitedifferences.utilities.LocalVolRNDCalculator} and the (deferred to Phase 5h.5-SLV)
 * HestonSLVFDMModel.
 *
 * @author Phase 5h.5-RND-b port
 */
public final class FdmLocalVolFwdOp implements FdmLinearOpComposite {

    private final FdmMesher mesher;
    private final YieldTermStructure rTS;
    private final YieldTermStructure qTS;
    private final LocalVolTermStructure localVol;
    private final Array x;
    private final FirstDerivativeOp dxMap;
    private final TripleBandLinearOp dxxMap;
    private final TripleBandLinearOp mapT;
    private final int direction;

    public FdmLocalVolFwdOp(final FdmMesher mesher, @SuppressWarnings( "unused" ) final Quote spot,
            final YieldTermStructure rTS, final YieldTermStructure qTS, final LocalVolTermStructure localVol,
            final int direction) {
        this.mesher = mesher;
        this.rTS = rTS;
        this.qTS = qTS;
        this.localVol = localVol;
        this.direction = direction;

        // C++: x_((localVol)!=nullptr ? Array(Exp(mesher->locations(direction))) : Array())
        // localVol is required for normal use; an empty x is only for default-constructed
        // op (not used in production). We always require localVol here.
        this.x = mesher.locations(direction).exp();

        this.dxMap = new FirstDerivativeOp(direction, mesher);
        this.dxxMap = new SecondDerivativeOp(direction, mesher);
        this.mapT = new TripleBandLinearOp(direction, mesher);
    }

    /** Convenience constructor matching C++ default {@code direction = 0}. */
    public FdmLocalVolFwdOp(final FdmMesher mesher, final Quote spot, final YieldTermStructure rTS,
            final YieldTermStructure qTS, final LocalVolTermStructure localVol) {
        this(mesher, spot, rTS, qTS, localVol, 0);
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public void setTime(final double t1, final double t2) {
        // C++ uses InterestRate.rate() default compounding == Continuous.
        final double r = rTS.forwardRate(t1, t2, Compounding.Continuous).rate();
        final double q = qTS.forwardRate(t1, t2, Compounding.Continuous).rate();

        // v[i] = sigma^2(midpoint, exp(x[i]))   note: x_ in C++ already holds exp(log-spot)
        final int n = mesher.layout().size();
        final Array v = new Array(n);
        final double tmid = 0.5 * (t1 + t2);
        for ( final FdmLinearOpIterator iter : mesher.layout() ) {
            final int i = iter.index();
            final double sig = localVol.localVol(tmid, x.get(i), true);
            v.set(i, sig * sig);
        }

        // mapT = dxMap.multR(- r + q + 0.5 v)  +  dxxMap.multR(0.5 v)
        // Java axpyb signature: this = a*x + y + b (per-cell on diagonals).
        // C++ call: mapT_.axpyb(Array(1, 1.0), dxMap_.multR(- r + q + 0.5*v),
        //                       dxxMap_.multR(0.5*v),  Array(1, 0.0));
        // → with a={1.0}, x=dxMap.multR(...), y=dxxMap.multR(...), b={0.0}:
        //   diag = y.diag + 1.0 * x.diag + 0.0  (per cell)
        // Java implementation handles size-1 a/b via the broadcast `binc`/`ainc` flags.
        final Array convCoef = new Array(n);
        for ( int i = 0; i < n; ++i ) {
            convCoef.set(i, -r + q + 0.5 * v.get(i));
        }
        final Array diffCoef = v.mul(0.5);

        final TripleBandLinearOp dx = dxMap.multR(convCoef);
        final TripleBandLinearOp dxx = dxxMap.multR(diffCoef);

        // Use scalar-array shapes matching C++ Array(1, c).
        final Array aOne = new Array(1).fill(1.0);
        final Array bZero = new Array(1).fill(0.0);
        mapT.axpyb(aOne, dx, dxx, bZero);
    }

    @Override
    public Array apply(final Array u) {
        return mapT.apply(u);
    }

    @Override
    public Array applyDirection(final int direction, final Array r) {
        if ( direction == this.direction ) {
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
        if ( direction == this.direction ) {
            return mapT.solveSplitting(r, dt, 1.0);
        }
        // C++ returns r unchanged for the inactive direction here; mirror that.
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
    public List< Matrix > toMatrixDecomp() {
        final List< Matrix > ret = new ArrayList<>(1);
        ret.add(mapT.toMatrix());
        return Collections.unmodifiableList(ret);
    }
}
