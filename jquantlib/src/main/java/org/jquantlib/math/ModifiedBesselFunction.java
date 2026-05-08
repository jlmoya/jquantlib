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

import org.jquantlib.QL;
import org.jquantlib.math.distributions.GammaFunction;
import org.jquantlib.math.transcendental.JQuantMath;

/**
 * Modified Bessel functions of the first and second kind for both real
 * and complex arguments. Phase 2f WI-3 line-by-line port of
 * {@code QuantLib::modifiedBesselFunction_*} (v1.42.1
 * ql/math/modifiedbessel.{hpp,cpp}).
 *
 * <p>Two regimes per the C++ implementation:
 * <ul>
 *   <li>{@code |x| < 13.0}: Taylor series in {@code Y = x²/4}, weighted
 *       by {@code (x/2)^ν / Γ(1+ν)}.</li>
 *   <li>{@code |x| >= 13.0}: 30-term asymptotic expansion of
 *       Hankel/Olver type with two complementary sub-series.</li>
 * </ul>
 *
 * <p>Templated unweighted vs exponentially-weighted variants in C++ are
 * implemented in Java as two pairs of static methods sharing a common
 * private kernel. Only the cases actually consumed by JQuantLib (the
 * Heston Fourier-inversion path needs unweighted complex {@code I_ν})
 * are implemented; real-only and exponentially-weighted variants are
 * included for parity with the C++ public surface.
 */
public final class ModifiedBesselFunction {

    private ModifiedBesselFunction() {}

    // --- public surface (real) -------------------------------------------

    public static double i(final double nu, final double x) {
        QL.require(x >= 0.0, "negative argument requires complex version of "
                + "modifiedBesselFunction");
        return realImpl(nu, x, /*expWeighted=*/false);
    }

    public static double iExpWeighted(final double nu, final double x) {
        QL.require(x >= 0.0, "negative argument requires complex version of "
                + "modifiedBesselFunction");
        return realImpl(nu, x, /*expWeighted=*/true);
    }

    public static double k(final double nu, final double x) {
        return Math.PI / 2.0 * (realImpl(-nu, x, false) - realImpl(nu, x, false))
                / Math.sin(Math.PI * nu);
    }

    public static double kExpWeighted(final double nu, final double x) {
        return Math.PI / 2.0 * (realImpl(-nu, x, true) - realImpl(nu, x, true))
                / Math.sin(Math.PI * nu);
    }

    // --- public surface (complex) ----------------------------------------

    public static Complex i(final double nu, final Complex z) {
        if (z.imag() == 0.0 && z.real() >= 0.0) {
            return Complex.real(i(nu, z.real()));
        }
        return complexImpl(nu, z, /*expWeighted=*/false);
    }

    public static Complex iExpWeighted(final double nu, final Complex z) {
        if (z.imag() == 0.0 && z.real() >= 0.0) {
            return Complex.real(iExpWeighted(nu, z.real()));
        }
        return complexImpl(nu, z, /*expWeighted=*/true);
    }

    public static Complex k(final double nu, final Complex z) {
        if (z.imag() == 0.0 && z.real() >= 0.0) {
            return Complex.real(k(nu, z.real()));
        }
        return complexImpl(-nu, z, false).sub(complexImpl(nu, z, false))
                .mul(Math.PI / 2.0).div(Math.sin(Math.PI * nu));
    }

    public static Complex kExpWeighted(final double nu, final Complex z) {
        if (z.imag() == 0.0 && z.real() >= 0.0) {
            return Complex.real(kExpWeighted(nu, z.real()));
        }
        return complexImpl(-nu, z, true).sub(complexImpl(nu, z, true))
                .mul(Math.PI / 2.0).div(Math.sin(Math.PI * nu));
    }

    // --- kernels ---------------------------------------------------------

    private static double realImpl(final double nu, final double x,
                                   final boolean expWeighted) {
        if (Math.abs(x) < 13.0) {
            // Taylor series. expWeighted's weightSmallX is exp(-x);
            // unweighted is 1.0.
            final double alpha = JQuantMath.pow(0.5 * x, nu)
                    / new GammaFunction().value(1.0 + nu);
            final double Y = 0.25 * x * x;
            int k = 1;
            double sum = alpha;
            double bk = alpha;
            while (true) {
                bk *= Y / (k * (k + nu));
                if (Math.abs(bk) <= Math.abs(sum) * Constants.QL_EPSILON) {
                    break;
                }
                sum += bk;
                ++k;
                QL.require(k < 1000, "max iterations exceeded");
            }
            return sum * (expWeighted ? Math.exp(-x) : 1.0);
        } else {
            // Asymptotic expansion (real, real-x).
            double naK = 1.0;
            double sign = 1.0;
            double daK = 1.0;
            double s1 = 1.0;
            double s2 = 1.0;
            for (int k = 1; k < 30; ++k) {
                sign *= -1;
                naK *= 4.0 * nu * nu - (2.0 * k - 1.0) * (2.0 * k - 1.0);
                daK *= (8.0 * k) * x;
                final double aK = naK / daK;
                s2 += aK;
                s1 += sign * aK;
            }
            // C++ template specialisation I<Real>::value() returns 0.0,
            // so the i*exp(i*nu*pi)*w2*s2 term cancels out for real x.
            // Result is purely w1*s1 / sqrt(2*pi*x).
            final double w1 = expWeighted ? 1.0 : Math.exp(x);
            return (1.0 / Math.sqrt(2.0 * Math.PI * x)) * (w1 * s1);
        }
    }

    private static Complex complexImpl(final double nu, final Complex x,
                                       final boolean expWeighted) {
        if (x.abs() < 13.0) {
            final Complex alpha = x.mul(0.5).pow(nu)
                    .div(new GammaFunction().value(1.0 + nu));
            final Complex Y = x.mul(x).mul(0.25);
            int k = 1;
            Complex sum = alpha;
            Complex bk = alpha;
            while (true) {
                bk = bk.mul(Y).div(k * (k + nu));
                if (bk.abs() <= sum.abs() * Constants.QL_EPSILON) {
                    break;
                }
                sum = sum.add(bk);
                ++k;
                QL.require(k < 1000, "max iterations exceeded");
            }
            return sum.mul(expWeighted ? x.neg().exp() : Complex.ONE);
        } else {
            double naK = 1.0;
            double sign = 1.0;
            Complex daK = Complex.ONE;
            Complex s1 = Complex.ONE;
            Complex s2 = Complex.ONE;
            for (int k = 1; k < 30; ++k) {
                sign *= -1;
                naK *= 4.0 * nu * nu - (2.0 * k - 1.0) * (2.0 * k - 1.0);
                daK = daK.mul(x).mul(8.0 * k);
                final Complex aK = Complex.real(naK).div(daK);
                s2 = s2.add(aK);
                s1 = s1.add(aK.mul(sign));
            }
            final Complex i = Complex.I;
            final Complex w1 = expWeighted ? Complex.ONE : x.exp();
            final Complex w2 = expWeighted ? x.mul(-2.0).exp() : x.neg().exp();
            // 1 / sqrt(2*pi*x) * (w1*s1 + i * exp(i*nu*pi) * w2*s2)
            final Complex pref = x.mul(2.0 * Math.PI).sqrt();
            final Complex inuPi = i.mul(nu * Math.PI).exp();
            final Complex t1 = w1.mul(s1);
            final Complex t2 = i.mul(inuPi).mul(w2).mul(s2);
            return t1.add(t2).div(pref);
        }
    }
}
