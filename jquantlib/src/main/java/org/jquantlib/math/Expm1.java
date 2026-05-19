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
 * Complex-valued {@code expm1} and {@code log1p} — accuracy-preserving companions to the scalar
 * {@link Math#expm1(double)} / {@link Math#log1p(double)} for arguments close to zero.
 *
 * <p>Line-by-line port of QuantLib v1.42.1 {@code ql/math/expm1.{hpp,cpp}}
 * (Phase 5e.5b-CFC-d-43).
 *
 * <p>For small {@code |z|} (heuristic: {@code |z| < 1} for {@code expm1};
 * {@code |Re(z)| < 0.5 && |Im(z)| < 0.5} for {@code log1p}) the routines use catastrophic-cancellation-free
 * reformulations from the scalar {@code expm1}/{@code log1p}. Outside that regime they fall back to the straightforward
 * {@code exp(z)-1} / {@code log(1+z)}.
 */
public final class Expm1 {

    private Expm1() {
    }

    /**
     * Complex {@code e^z - 1}. Accurate near the origin where the naive {@code exp(z) - 1} suffers catastrophic
     * cancellation.
     */
    public static Complex expm1(final Complex z) {
        if ( z.abs() < 1.0 ) {
            final double a = z.real();
            final double b = z.imag();
            final double exp_1 = Math.expm1(a);
            final double sinHalfB = Math.sin(0.5 * b);
            final double cos_1 = -2.0 * sinHalfB * sinHalfB;
            // e^a (cos b + i sin b) - 1
            //   = e^a cos b - 1 + i e^a sin b
            //   = (e^a - 1)(cos b - 1) + (e^a - 1) + (cos b - 1) + i sin b e^a
            //   = exp_1*cos_1 + exp_1 + cos_1                        + i sin(b)*e^a
            return new Complex(exp_1 * cos_1 + exp_1 + cos_1, Math.sin(b) * Math.exp(a));
        } else {
            return z.exp().sub(1.0);
        }
    }

    /**
     * Complex {@code log(1 + z)}. Accurate near the origin where the naive {@code log(1+z)} suffers catastrophic
     * cancellation in {@code |1+z|}.
     */
    public static Complex log1p(final Complex z) {
        final double a = z.real();
        final double b = z.imag();
        if ( Math.abs(a) < 0.5 && Math.abs(b) < 0.5 ) {
            // |1+z|^2 = (1+a)^2 + b^2 = 1 + 2a + a^2 + b^2
            // log|1+z| = 0.5 * log1p(2a + a^2 + b^2)
            // arg(1+z) computed directly (no cancellation issue).
            return new Complex(0.5 * Math.log1p(a * a + 2.0 * a + b * b), Math.atan2(b, 1.0 + a));
        } else {
            return z.add(1.0).log();
        }
    }
}
