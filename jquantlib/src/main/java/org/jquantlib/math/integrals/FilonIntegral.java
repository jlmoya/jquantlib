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
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.math.integrals;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;

/**
 * Filon's formulae for sine and cosine integrals.
 *
 * <p>Phase 1 closure A4-B-v4 port of {@code QuantLib::FilonIntegral}
 * (v1.42.1 ql/math/integrals/filonintegral.{hpp,cpp}; pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>Given a number {@code N} of even intervals, the integral of
 * {@code f(x) * cos(t*x)} (or {@code f(x) * sin(t*x)}) between {@code a}
 * and {@code b} is computed by Filon's quadrature, which accounts
 * analytically for the oscillatory factor and is much more accurate than a
 * naïve Simpson rule on oscillatory integrands.
 *
 * <p>References:
 * <ul>
 *   <li>Abramowitz, M. and Stegun, I. A. (Eds.). <i>Handbook of Mathematical
 *       Functions with Formulas, Graphs, and Mathematical Tables</i>, 9th
 *       printing. New York: Dover, pp. 890-891, 1972.</li>
 * </ul>
 */
public class FilonIntegral extends Integrator {

    /** Filon quadrature kind. {@code Cosine}: integrates {@code f(x)*cos(t*x)};
     * {@code Sine}: integrates {@code f(x)*sin(t*x)}. */
    public enum Type { Sine, Cosine }

    private final Type type_;
    private final double t_;
    private final int intervals_;
    private final int n_;

    /**
     * @param type whether to evaluate the sine or cosine variant.
     * @param t   the frequency multiplier in {@code sin(t*x)} / {@code cos(t*x)}.
     * @param intervals the number of intervals {@code N}; must be even.
     */
    public FilonIntegral(final Type type, final double t, final int intervals) {
        // C++: Integrator(Null<Real>(), intervals+1) — the absoluteAccuracy
        // is unused; QuantLib stores it as Null<Real>(). The Java
        // Integrator base requires accuracy > QL_EPSILON, so we use
        // QL_MAX_REAL as a placeholder (matches TanhSinhIntegral's choice).
        super(Constants.QL_MAX_REAL, intervals + 1);
        QL.require((intervals & 1) == 0, "number of intervals must be even");
        this.type_ = type;
        this.t_ = t;
        this.intervals_ = intervals;
        this.n_ = intervals / 2;
    }

    public Type type() { return type_; }
    public double t() { return t_; }
    public int intervals() { return intervals_; }

    @Override
    protected double integrate(final Ops.DoubleOp f, final double a, final double b) {
        final double h = (b - a) / (2.0 * n_);

        // x[i] = a + i*h, i = 0..2n.
        final int size = 2 * n_ + 1;
        final double[] x = new double[size];
        for (int i = 0; i < size; ++i) {
            x[i] = a + i * h;
        }

        final double theta  = t_ * h;
        final double theta2 = theta * theta;
        final double theta3 = theta2 * theta;

        final double sinTheta = Math.sin(theta);
        final double cosTheta = Math.cos(theta);

        final double alpha = 1.0 / theta
                + Math.sin(2.0 * theta) / (2.0 * theta2)
                - 2.0 * (sinTheta * sinTheta) / theta3;
        final double beta  = 2.0 * ((1.0 + cosTheta * cosTheta) / theta2
                - Math.sin(2.0 * theta) / theta3);
        final double gamma = 4.0 * (sinTheta / theta3 - cosTheta / theta2);

        // v[i] = f(x[i]).
        final double[] v = new double[size];
        for (int i = 0; i < size; ++i) {
            v[i] = f.op(x[i]);
        }

        // f1, f2 selectors per type.
        // Cosine integral: prefactor sin (i.e. f1 = sin), summed factor cos.
        // Sine integral:   prefactor cos (i.e. f1 = cos), summed factor sin.
        final boolean cosineType = (type_ == Type.Cosine);

        // c_2n: even-index sum, with endpoints half-weighted.
        // Start with v[0]*f2(t*a) - 0.5*(v[2n]*f2(t*b) + v[0]*f2(t*a))
        // which simplifies to 0.5*(v[0]*f2(t*a) - v[2n]*f2(t*b)).
        double c2n   = v[0] * f2(t_ * a, cosineType)
                - 0.5 * (v[2 * n_] * f2(t_ * b, cosineType) + v[0] * f2(t_ * a, cosineType));
        double c2n1  = 0.0;

        for (int i = 1; i <= n_; ++i) {
            c2n  += v[2 * i]     * f2(t_ * x[2 * i],     cosineType);
            c2n1 += v[2 * i - 1] * f2(t_ * x[2 * i - 1], cosineType);
        }

        final double sign = cosineType ? 1.0 : -1.0;
        return h * (alpha * (v[2 * n_] * f1(t_ * x[2 * n_], cosineType)
                            - v[0]     * f1(t_ * x[0],      cosineType)) * sign
                  + beta  * c2n
                  + gamma * c2n1);
    }

    /** Returns {@code sin(x)} for the cosine variant, {@code cos(x)} for the sine variant. */
    private static double f1(final double x, final boolean cosineType) {
        return cosineType ? Math.sin(x) : Math.cos(x);
    }

    /** Returns {@code cos(x)} for the cosine variant, {@code sin(x)} for the sine variant. */
    private static double f2(final double x, final boolean cosineType) {
        return cosineType ? Math.cos(x) : Math.sin(x);
    }
}
