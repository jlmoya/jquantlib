/*
 Copyright (C) 2026 Jose Moya

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
 Copyright (C) 2006, 2007 Ferdinando Ametrano
 Copyright (C) 2006 Cristina Duminuco
 Copyright (C) 2007 Giorgio Facchinetti
*/

package org.jquantlib.termstructures.volatility;

import org.jquantlib.QL;

/**
 * Abcd functional form for instantaneous volatility (Rebonato's form).
 *
 * <p>Java port of {@code ql/termstructures/volatility/abcd.{hpp,cpp}}
 * (QuantLib v1.42.1). Combines {@code AbcdFunction} and the part of its {@code AbcdMathFunction} base class used by
 * Phase 3j calibration.
 *
 * <p>Form: {@code f(t) = (a + b*t) * exp(-c*t) + d}
 *
 * <p>Phase 3j L0.2 — Track B forward-declared port. Track A may extend or
 * supplement this class; the present surface is sufficient for
 * {@link org.jquantlib.model.marketmodels.models.PiecewiseConstantAbcdVariance} and the calibration framework.
 */
public class AbcdFunction {

    private final double a_, b_, c_, d_;
    // derivative coefficients
    @SuppressWarnings( "unused" )
    private final double da_, db_;
    // primitive coefficients
    private final double pa_, pb_, K_;

    public AbcdFunction(final double a, final double b, final double c, final double d) {
        validate(a, b, c, d);
        this.a_ = a;
        this.b_ = b;
        this.c_ = c;
        this.d_ = d;

        this.da_ = b - c * a;
        this.db_ = -c * b;

        this.pa_ = -(a / c + b / (c * c));
        this.pb_ = -b / c;
        // primitive at t=0: pa_ * exp(0) + 0 + K_ = 0 → K_ = -pa_
        this.K_ = -pa_;
    }

    /** Default constructor matching C++ {@code AbcdFunction(-0.06, 0.17, 0.54, 0.17)}. */
    public AbcdFunction() {
        this(-0.06, 0.17, 0.54, 0.17);
    }

    /** Validation per C++ {@code AbcdMathFunction::validate}. */
    public static void validate(final double a, final double b, final double c, final double d) {
        QL.require(c >= 0, "c (" + c + ") must be non negative");
        QL.require(d >= 0, "d (" + d + ") must be non negative");
        QL.require(a + d >= 0, "a+d (" + (a + d) + ") must be non negative");
        if ( b >= 0.0 ) {
            return;
        }
        // the condition a+d >= -b/c, equivalently a+d+b/c >= 0
        QL.require(a + d + b / c >= 0, "a+d+b/c (" + (a + d + b / c) + ") must be non negative");
    }

    public double a() {
        return a_;
    }

    public double b() {
        return b_;
    }

    public double c() {
        return c_;
    }

    public double d() {
        return d_;
    }

    /** {@code f(t) = (a + b*t) * exp(-c*t) + d}; returns 0 for t &lt; 0. */
    public double apply(final double t) {
        return t < 0 ? 0.0 : (a_ + b_ * t) * Math.exp(-c_ * t) + d_;
    }

    /** Indefinite integral of the function at t (with K_ chosen so primitive(0)=0). */
    public double primitive(final double t) {
        return t < 0 ? 0.0 : (pa_ + pb_ * t) * Math.exp(-c_ * t) + d_ * t + K_;
    }

    /** Long-term value: {@code lim_{t->inf} f(t) = d}. */
    public double longTermValue() {
        return d_;
    }

    /** {@code f(0)}. */
    public double shortTermVolatility() {
        return apply(0.0);
    }

    // ---- AbcdFunction surface used by calibration ----

    /** Instantaneous covariance at time t between T-fixing and S-fixing rates. */
    public double covariance(final double t, final double T, final double S) {
        return apply(T - t) * apply(S - t);
    }

    /**
     * Definite integral of {@code f(T-u) * f(S-u)} between t1 and t2.
     *
     * <p>C++ uses an analytic primitive; Java mirrors that derivation.
     */
    public double covariance(final double t1, final double t2, final double T, final double S) {
        QL.require(t1 <= t2, "integration bounds (" + t1 + "," + t2 + ") are in reverse order");
        double cutOff = Math.min(S, T);
        if ( t1 >= cutOff ) {
            return 0.0;
        }
        cutOff = Math.min(t2, cutOff);
        return primitiveOfProduct(cutOff, T, S) - primitiveOfProduct(t1, T, S);
    }

    /** Variance between tMin and tMax of T-fixing rate. */
    public double variance(final double tMin, final double tMax, final double T) {
        return covariance(tMin, tMax, T, T);
    }

    /** Average volatility in [tMin, tMax] of T-fixing rate. */
    public double volatility(final double tMin, final double tMax, final double T) {
        if ( tMax == tMin ) {
            return Math.sqrt(covariance(tMax, T, T));
        }
        QL.require(tMax > tMin, "tMax must be > tMin");
        return Math.sqrt(variance(tMin, tMax, T) / (tMax - tMin));
    }

    /** Indefinite integral of {@code f(T-t) * f(S-t)} at time t. Mirrors C++ {@code AbcdFunction::primitive}. */
    private double primitiveOfProduct(final double t, final double T, final double S) {
        if ( T < t || S < t )
            return 0.0;

        // close(c_, 0.0) — match C++ tolerance
        if ( Math.abs(c_) < 1e-15 ) {
            final double v = a_ + d_;
            return t * (v * v + v * b_ * S + v * b_ * T - v * b_ * t + b_ * b_ * S * T - 0.5 * b_ * b_ * t * (S + T)
                    + b_ * b_ * t * t / 3.0);
        }

        final double k1 = Math.exp(c_ * t);
        final double k2 = Math.exp(c_ * S);
        final double k3 = Math.exp(c_ * T);

        return (b_ * b_ * (-1 - 2 * c_ * c_ * S * T - c_ * (S + T) + k1 * k1 * (1 + c_ * (S + T - 2 * t)
                + 2 * c_ * c_ * (S - t) * (T - t))) + 2 * c_ * c_ * (2 * d_ * a_ * (k2 + k3) * (k1 - 1) + a_ * a_ * (
                k1 * k1 - 1) + 2 * c_ * d_ * d_ * k2 * k3 * t) + 2 * b_ * c_ * (
                a_ * (-1 - c_ * (S + T) + k1 * k1 * (1 + c_ * (S + T - 2 * t))) - 2 * d_ * (
                        k3 * (1 + c_ * S) + k2 * (1 + c_ * T) - k1 * k3 * (1 + c_ * (S - t)) - k1 * k2 * (1 + c_ * (T
                                - t))))) / (4 * c_ * c_ * c_ * k2 * k3);
    }
}
