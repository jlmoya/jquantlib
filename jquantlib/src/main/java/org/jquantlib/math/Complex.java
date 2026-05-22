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
 */
package org.jquantlib.math;

/**
 * Minimal immutable double-precision complex number.
 *
 * <p>Phase 2f WI-3 A14 chose to port a small in-tree class rather than
 * pull in the Apache Commons Math dependency for the single use site (Heston BroadieKaya Fourier-inversion in
 * {@link org.jquantlib.processes.HestonProcess}). API surface is intentionally limited to what the Heston harness
 * needs: construction, components, +-*&#47; with both Complex and double, abs, exp, log, sqrt, pow(double), and unary
 * negation.
 *
 * <p>Branch-cut conventions match {@code std::complex<double>} on
 * libc++ (used by the C++ probe) — i.e. principal branch with cut along the negative real axis for log/sqrt and cut at
 * the negative real-axis-and-zero for abs2 (same as Java {@link Math#log}/{@link Math#sqrt} extended to complex via
 * Euler).
 *
 * <p><b>Note on bit-faithfulness.</b> Per the same A13 phenomenon
 * documented in {@link org.jquantlib.math.distributions.NonCentralCumulativeChiSquaredDistribution}, results of
 * {@link #exp()} / {@link #log()} / {@link #sqrt()} can drift by a few ULPs from libc++ because Java's
 * {@code Math.exp}, {@code Math.cos}, {@code Math.sin} are within-1-ULP rather than correctly-rounded. Downstream tests
 * use the loose tier accordingly.
 */
public final class Complex {

    public static final Complex ZERO = new Complex(0.0, 0.0);
    public static final Complex ONE = new Complex(1.0, 0.0);
    public static final Complex I = new Complex(0.0, 1.0);

    private final double re;
    private final double im;

    public Complex(final double real, final double imag) {
        this.re = real;
        this.im = imag;
    }

    public static Complex of(final double real, final double imag) {
        return new Complex(real, imag);
    }

    public static Complex real(final double r) {
        return new Complex(r, 0.0);
    }

    public double real() {
        return re;
    }

    public double imag() {
        return im;
    }

    public Complex add(final Complex o) {
        return new Complex(re + o.re, im + o.im);
    }

    public Complex add(final double s) {
        return new Complex(re + s, im);
    }

    public Complex sub(final Complex o) {
        return new Complex(re - o.re, im - o.im);
    }

    public Complex sub(final double s) {
        return new Complex(re - s, im);
    }

    public Complex mul(final Complex o) {
        return new Complex(re * o.re - im * o.im, re * o.im + im * o.re);
    }

    public Complex mul(final double s) {
        return new Complex(re * s, im * s);
    }

    public Complex div(final Complex o) {
        // Smith's robust algorithm — avoids over/underflow when |o|
        // is very large or very small. Mirrors libc++'s std::complex
        // division.
        if ( Math.abs(o.re) >= Math.abs(o.im) ) {
            final double r = o.im / o.re;
            final double d = o.re + r * o.im;
            return new Complex((re + im * r) / d, (im - re * r) / d);
        } else {
            final double r = o.re / o.im;
            final double d = o.im + r * o.re;
            return new Complex((re * r + im) / d, (im * r - re) / d);
        }
    }

    public Complex div(final double s) {
        return new Complex(re / s, im / s);
    }

    public Complex neg() {
        return new Complex(-re, -im);
    }

    public double abs() {
        return Math.hypot(re, im);
    }

    public double abs2() {
        return re * re + im * im;
    }

    public double arg() {
        return Math.atan2(im, re);
    }

    public Complex conj() {
        return new Complex(re, -im);
    }

    /** Principal-branch complex exp: exp(re) * (cos(im) + i*sin(im)). */
    public Complex exp() {
        final double er = Math.exp(re);
        return new Complex(er * Math.cos(im), er * Math.sin(im));
    }

    /** Principal-branch complex log: ln|z| + i*arg(z). Cut along (-inf, 0]. */
    public Complex log() {
        return new Complex(Math.log(abs()), arg());
    }

    /**
     * Principal-branch complex sqrt. Cut along (-inf, 0). Matches the libc++ algorithm: sqrt(z) = sqrt((|z|+re)/2) +
     * i*sign(im)*sqrt((|z|-re)/2).
     */
    public Complex sqrt() {
        if ( re == 0.0 && im == 0.0 ) {
            return ZERO;
        }
        final double r = abs();
        final double w = Math.sqrt((r + Math.abs(re)) * 0.5);
        if ( re >= 0.0 ) {
            return new Complex(w, im / (2.0 * w));
        } else {
            final double y = (im >= 0.0 ? w : -w);
            return new Complex(im / (2.0 * y), y);
        }
    }

    /** Real-exponent power: z^p = exp(p * log(z)). */
    public Complex pow(final double p) {
        if ( re == 0.0 && im == 0.0 ) {
            return ZERO;
        }
        return this.log().mul(p).exp();
    }

    /**
     * Complex-exponent power: {@code z^w = exp(w * log(z))}. Principal branch (cut along the negative real axis,
     * inherited from {@link #log()}). Mirrors {@code std::pow(std::complex, std::complex)} on libc++.
     */
    public Complex pow(final Complex w) {
        if ( re == 0.0 && im == 0.0 ) {
            return ZERO;
        }
        return this.log().mul(w).exp();
    }

    @Override
    public String toString() {
        return "(" + re + (im < 0 ? "" : "+") + im + "i)";
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof Complex c))
            return false;
        return Double.doubleToLongBits(re) == Double.doubleToLongBits(c.re)
                && Double.doubleToLongBits(im) == Double.doubleToLongBits(c.im);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(Double.doubleToLongBits(re)) ^ Long.hashCode(Double.doubleToLongBits(im));
    }
}
