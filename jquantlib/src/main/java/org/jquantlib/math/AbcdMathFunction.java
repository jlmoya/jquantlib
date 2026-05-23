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
 Copyright (C) 2006, 2007, 2015 Ferdinando Ametrano
 Copyright (C) 2006 Cristina Duminuco
 Copyright (C) 2005, 2006 Klaus Spanderen
 Copyright (C) 2007 Giorgio Facchinetti
 Copyright (C) 2015 Paolo Mazzocchi

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.math;

import org.jquantlib.QL;

/**
 * Abcd functional form: {@code f(t) = (a + b*t) * exp(-c*t) + d}.
 * <p>
 * Mirrors C++ {@code AbcdMathFunction} in {@code ql/math/abcdmathfunction.hpp} (v1.42.1).
 * <p>
 * This is a math-root utility distinct from {@code AbcdFunction} in
 * {@code org.jquantlib.termstructures.volatility}, which is a thin wrapper used by volatility
 * term structures. {@code AbcdMathFunction} exposes the complete coefficient API
 * ({@link #coefficients()}, {@link #derivativeCoefficients()},
 * {@link #definiteIntegralCoefficients(double, double)},
 * {@link #definiteDerivativeCoefficients(double, double)}) required by
 * {@code AbcdInterpolation} and other math-level callers.
 *
 * @author JQuantLib migration contributors
 */
public class AbcdMathFunction {

    protected double a_;
    protected double b_;
    protected double c_;
    protected double d_;

    private final double[] abcd_ = new double[4];
    private final double[] dabcd_ = new double[4];
    private double da_;
    private double db_;
    private double pa_;
    private double pb_;
    private double K_;
    private double dibc_;
    private double diacplusbcc_;

    /**
     * Default coefficients matching the C++ default ctor.
     */
    public AbcdMathFunction() {
        this(0.002, 0.001, 0.16, 0.0005);
    }

    public AbcdMathFunction(final double a, final double b, final double c, final double d) {
        this.a_ = a;
        this.b_ = b;
        this.c_ = c;
        this.d_ = d;
        abcd_[0] = a_;
        abcd_[1] = b_;
        abcd_[2] = c_;
        abcd_[3] = d_;
        initialize();
    }

    public AbcdMathFunction(final double[] abcd) {
        QL_REQUIRE(abcd != null && abcd.length == 4, "abcd vector must have 4 elements");
        this.a_ = abcd[0];
        this.b_ = abcd[1];
        this.c_ = abcd[2];
        this.d_ = abcd[3];
        abcd_[0] = a_;
        abcd_[1] = b_;
        abcd_[2] = c_;
        abcd_[3] = d_;
        initialize();
    }

    /**
     * Validates that the four coefficients describe a non-negative function on {@code [0, +inf)}.
     */
    public static void validate(final double a, final double b, final double c, final double d) {
        QL_REQUIRE(c > 0.0, "c (" + c + ") must be positive");
        QL_REQUIRE(d >= 0.0, "d (" + d + ") must be non negative");
        QL_REQUIRE(a + d >= 0.0, "a+d (" + a + "+" + d + ") must be non negative");
        if (b >= 0.0) {
            return;
        }
        final double zeroFirstDerivative = 1.0 / c - a / b;
        if (zeroFirstDerivative >= 0.0) {
            final double lowerBound = -(d * c) / Math.exp(c * a / b - 1.0);
            QL_REQUIRE(b >= lowerBound,
                    "b (" + b + ") less than " + lowerBound + ": negative function value at stationary point "
                            + zeroFirstDerivative);
        }
    }

    private void initialize() {
        validate(a_, b_, c_, d_);
        da_ = b_ - c_ * a_;
        db_ = -c_ * b_;
        dabcd_[0] = da_;
        dabcd_[1] = db_;
        dabcd_[2] = c_;
        dabcd_[3] = 0.0;

        pa_ = -(a_ + b_ / c_) / c_;
        pb_ = -b_ / c_;
        K_ = 0.0;

        dibc_ = b_ / c_;
        diacplusbcc_ = a_ / c_ + dibc_ / c_;
    }

    /** Function value at time {@code t}. */
    public double op(final double t) {
        return t < 0.0 ? 0.0 : (a_ + b_ * t) * Math.exp(-c_ * t) + d_;
    }

    /** First derivative at time {@code t}. */
    public double derivative(final double t) {
        return t < 0.0 ? 0.0 : (da_ + db_ * t) * Math.exp(-c_ * t);
    }

    /** Indefinite integral at time {@code t}. */
    public double primitive(final double t) {
        return t < 0.0 ? 0.0 : (pa_ + pb_ * t) * Math.exp(-c_ * t) + d_ * t + K_;
    }

    /** Definite integral between {@code t1} and {@code t2}. */
    public double definiteIntegral(final double t1, final double t2) {
        return primitive(t2) - primitive(t1);
    }

    /** Time at which the function reaches a stationary maximum (if any). */
    public double maximumLocation() {
        if (b_ == 0.0) {
            return a_ >= 0.0 ? 0.0 : Constants.QL_MAX_REAL;
        }
        final double zeroFirstDerivative = 1.0 / c_ - a_ / b_;
        return zeroFirstDerivative > 0.0 ? zeroFirstDerivative : 0.0;
    }

    /** Maximum value of the function. */
    public double maximumValue() {
        if (b_ == 0.0 && a_ >= 0.0) {
            return a_ + d_;
        }
        return op(maximumLocation());
    }

    /** Function value at {@code +inf}. */
    public double longTermValue() {
        return d_;
    }

    public double a() { return a_; }
    public double b() { return b_; }
    public double c() { return c_; }
    public double d() { return d_; }

    public double[] coefficients() {
        return abcd_.clone();
    }

    public double[] derivativeCoefficients() {
        return dabcd_.clone();
    }

    /**
     * Coefficients of an AbcdMathFunction defined as a definite integral on the
     * rolling window {@code [t, t+tau]} with {@code tau = t2-t}.
     */
    public double[] definiteIntegralCoefficients(final double t, final double t2) {
        final double dt = t2 - t;
        final double expcdt = Math.exp(-c_ * dt);
        final double[] result = new double[4];
        result[0] = diacplusbcc_ - (diacplusbcc_ + dibc_ * dt) * expcdt;
        result[1] = dibc_ * (1.0 - expcdt);
        result[2] = c_;
        result[3] = d_ * dt;
        return result;
    }

    /**
     * Coefficients of an AbcdMathFunction defined as a definite derivative on
     * the rolling window {@code [t, t+tau]} with {@code tau = t2-t}.
     */
    public double[] definiteDerivativeCoefficients(final double t, final double t2) {
        final double dt = t2 - t;
        final double expcdt = Math.exp(-c_ * dt);
        final double[] result = new double[4];
        result[1] = b_ * c_ / (1.0 - expcdt);
        result[0] = a_ * c_ - b_ + result[1] * dt * expcdt;
        result[0] /= 1.0 - expcdt;
        result[2] = c_;
        result[3] = d_ / dt;
        return result;
    }

    private static void QL_REQUIRE(final boolean condition, final String message) {
        QL.require(condition, message);
    }
}
