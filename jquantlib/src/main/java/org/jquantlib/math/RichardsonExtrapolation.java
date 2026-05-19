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

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2012 Klaus Spanderen
*/

package org.jquantlib.math;

import org.jquantlib.QL;
import org.jquantlib.math.solvers1D.Brent;

/**
 * Richardson Extrapolation.
 *
 * <p>Sequence acceleration technique for
 * {@code f(Δh) = f_0 + α · (Δh)^n + O((Δh)^{n+1})}.
 *
 * <p>Java port of {@code ql/math/richardsonextrapolation.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * <p>The unknown-order overload uses {@link Brent} to solve for the
 * order of convergence, mirroring the C++ algorithm.
 *
 * <p>References:
 * <a href="http://en.wikipedia.org/wiki/Richardson_extrapolation">Wikipedia</a>.
 *
 * <p>Phase 5e.5b-CFC-d-91.
 */
public final class RichardsonExtrapolation {

    private final double delta_h_;
    private final double fdelta_h_;
    private final double n_;
    private final Ops.DoubleOp f_;

    /**
     * Richardson Extrapolation with known order of convergence omitted (use
     * {@link #RichardsonExtrapolation(Ops.DoubleOp, double, double)} with {@code n = Double.NaN} to defer to the
     * unknown-order overload).
     *
     * @param f       function to be extrapolated to {@code delta_h -> 0}
     * @param delta_h step size
     */
    public RichardsonExtrapolation(final Ops.DoubleOp f, final double delta_h) {
        this(f, delta_h, Double.NaN);
    }

    /**
     * Richardson Extrapolation.
     *
     * @param f       function to be extrapolated to {@code delta_h -> 0}
     * @param delta_h step size
     * @param n       if known, the order of convergence; pass {@code Double.NaN} for the QuantLib {@code Null<Real>()}
     *                sentinel (forces use of the two-argument {@link #valueAt(double, double)} overload).
     */
    public RichardsonExtrapolation(final Ops.DoubleOp f, final double delta_h, final double n) {
        this.delta_h_ = delta_h;
        this.fdelta_h_ = f.op(delta_h);
        this.n_ = n;
        this.f_ = f;
    }

    /**
     * Extrapolation for known order of convergence.
     *
     * @param t scaling factor for the step size (must be {@code > 1})
     * @return the Richardson-extrapolated value
     */
    public double valueAt(final double t) {
        QL.require(t > 1, "scaling factor must be greater than 1");
        QL.require(!Double.isNaN(n_), "order of convergence must be known");

        final double tk = Math.pow(t, n_);
        return (tk * f_.op(delta_h_ / t) - fdelta_h_) / (tk - 1.0);
    }

    /**
     * Extrapolation for known order of convergence, with default scaling factor {@code t = 2.0} (matches C++ default
     * argument).
     *
     * @return the Richardson-extrapolated value
     */
    public double valueAt() {
        return valueAt(2.0);
    }

    /**
     * Extrapolation for unknown order of convergence.
     *
     * @param t first scaling factor for the step size (must satisfy {@code t > s > 1})
     * @param s second scaling factor for the step size
     * @return the Richardson-extrapolated value
     */
    public double valueAt(final double t, final double s) {
        QL.require(t > 1 && s > 1, "scaling factors must be greater than 1");
        QL.require(t > s, "t must be greater than s");

        final double ft = f_.op(delta_h_ / t);
        final double fs = f_.op(delta_h_ / s);

        final RichardsonEqn eqn = new RichardsonEqn(fdelta_h_, ft, fs, t, s);

        final double step = 0.1;
        double left = 0.05;
        double fr = eqn.op(left + step);
        double fl = eqn.op(left);
        while ( fr * fl > 0.0 && left < 15.1 ) {
            left += step;
            fl = fr;
            fr = eqn.op(left + step);
        }

        QL.require(left < 15.1, "could not estimate the order of convergence");

        final double k = new Brent().solve(eqn, 1e-8, left + 0.5 * step, left, left + step);

        final double ts = Math.pow(s, k);
        return (ts * fs - fdelta_h_) / (ts - 1.0);
    }

    /**
     * Internal residual functor used by the Brent solver in the unknown-order overload. Mirrors the anonymous
     * {@code RichardsonEqn} class in {@code richardsonextrapolation.cpp}.
     */
    private static final class RichardsonEqn implements Ops.DoubleOp {
        private final double fdelta_h_;
        private final double ft_;
        private final double fs_;
        private final double t_;
        private final double s_;

        RichardsonEqn(final double fh, final double ft, final double fs, final double t, final double s) {
            this.fdelta_h_ = fh;
            this.ft_ = ft;
            this.fs_ = fs;
            this.t_ = t;
            this.s_ = s;
        }

        @Override
        public double op(final double k) {
            return ft_ + (ft_ - fdelta_h_) / (Math.pow(t_, k) - 1.0) - (fs_ + (fs_ - fdelta_h_) / (Math.pow(s_, k)
                    - 1.0));
        }
    }
}
