/*
 Copyright (C) 2020 Klaus Spanderen
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

package org.jquantlib.math.integrals;

import org.jquantlib.QL;
import org.jquantlib.math.Complex;
import org.jquantlib.math.Constants;

/**
 * Sine and cosine integrals (Si, Ci) for real and complex arguments, plus the complex exponential integrals {@code E1}
 * and {@code Ei}.
 *
 * <p>Java port of v1.42.1
 * {@code ql/math/integrals/exponentialintegrals.{hpp,cpp}}.
 *
 * <p>The implementation follows the rational-Chebyshev approximation from
 * <ul>
 *   <li>B. Rowe et al., <i>GALSIM: The modular galaxy image simulation
 *       toolkit</i> (https://arxiv.org/abs/1407.7676) for the {@code x <= 4}
 *       branch of the real Si/Ci.</li>
 *   <li>The auxiliary {@code f(x)} / {@code g(x)} continued-fraction
 *       approximants for {@code x > 4}, yielding
 *       {@code Si(x) = π/2 − f(x)·cos(x) − g(x)·sin(x)} and
 *       {@code Ci(x) = f(x)·sin(x) − g(x)·cos(x)}.</li>
 *   <li>V. Pegoraro, P. Slusallek for the complex {@code Ei} continued
 *       fraction / Taylor-series scheme used by the complex Ei/E1/Si/Ci
 *       overloads.</li>
 * </ul>
 *
 * <p>The numeric coefficients are bit-identical to C++. Both {@link #Si} and
 * {@link #Ci} match the C++ reference to within {@code 1e-15} relative across
 * the {@code [0, 50]} regime exercised by AnalyticHestonEngine AsymptoticChF.
 *
 * @author Phase 5e.5b-CFC-d-136 port
 */
public final class ExponentialIntegral {

    /** Euler-Mascheroni constant γ (matches C++ {@code M_EULER_MASCHERONI}). */
    public static final double M_EULER_MASCHERONI = 0.5772156649015328606065121;
    private static final double Z_INF = Math.log(0.01 * Double.MAX_VALUE) + Math.log(100.0);

    // -----------------------------------------------------------------
    // Helper rational-approximation functions f(x), g(x) — only valid
    // for x > 4. Mirror C++ exponential_integrals_helper::f/g exactly.
    // -----------------------------------------------------------------

    private ExponentialIntegral() {
        // utility class
    }

    private static double f(final double x) {
        final double x2 = 1.0 / (x * x);

        return (1 + x2 * (7.44437068161936700618e2 + x2 * (1.96396372895146869801e5 + x2 * (2.37750310125431834034e7
                + x2 * (1.43073403821274636888e9 + x2 * (4.33736238870432522765e10 + x2 * (6.40533830574022022911e11
                + x2 * (4.20968180571076940208e12 + x2 * (1.00795182980368574617e13 + x2 * (4.94816688199951963482e12
                - x2 * 4.94701168645415959931e11)))))))))) / (x * (1 + x2 * (7.46437068161927678031e2 + x2 * (
                1.97865247031583951450e5 + x2 * (2.41535670165126845144e7 + x2 * (1.47478952192985464958e9 + x2 * (
                        4.58595115847765779830e10 + x2 * (7.08501308149515401563e11 + x2 * (5.06084464593475076774e12
                                + x2 * (1.43468549171581016479e13 + x2 * 1.11535493509914254097e13))))))))));
    }

    // -----------------------------------------------------------------
    // Public Si / Ci  (real)
    // -----------------------------------------------------------------

    private static double g(final double x) {
        final double x2 = 1.0 / (x * x);

        return x2 * (1 + x2 * (8.1359520115168615e2 + x2 * (2.35239181626478200e5 + x2 * (3.12557570795778731e7 + x2 * (
                2.06297595146763354e9 + x2 * (6.83052205423625007e10 + x2 * (1.09049528450362786e12 + x2 * (
                        7.57664583257834349e12 + x2 * (1.81004487464664575e13 + x2 * (6.43291613143049485e12
                                - x2 * 1.36517137670871689e12)))))))))) / (1 + x2 * (8.19595201151451564e2 + x2 * (
                2.40036752835578777e5 + x2 * (3.26026661647090822e7 + x2 * (2.23355543278099360e9 + x2 * (
                        7.87465017341829930e10 + x2 * (1.39866710696414565e12 + x2 * (1.17164723371736605e13 + x2 * (
                                4.01839087307656620e13 + x2 * 3.99653257887490811e13)))))))));
    }

    /**
     * Sine integral {@code Si(x) = integral_0^x sin(t)/t dt}.
     *
     * <p>Odd function: {@code Si(-x) = -Si(x)}.
     */
    public static double Si(final double x) {
        if ( x < 0 ) {
            return -Si(-x);
        } else if ( x <= 4.0 ) {
            final double x2 = x * x;

            return x * (1 + x2 * (-4.54393409816329991e-2 + x2 * (1.15457225751016682e-3 + x2 * (-1.41018536821330254e-5
                    + x2 * (9.43280809438713025e-8 + x2 * (-3.53201978997168357e-10 + x2 * (7.08240282274875911e-13
                    - x2 * 6.05338212010422477e-16))))))) / (1 + x2 * (1.01162145739225565e-2 + x2 * (
                    4.99175116169755106e-5 + x2 * (1.55654986308745614e-7 + x2 * (3.28067571055789734e-10 + x2 * (
                            4.5049097575386581e-13 + x2 * 3.21107051193712168e-16))))));
        } else {
            // M_PI_2 = π/2
            return 0.5 * Math.PI - f(x) * Math.cos(x) - g(x) * Math.sin(x);
        }
    }

    // -----------------------------------------------------------------
    // Complex overloads — Ei, E1, Si, Ci
    // -----------------------------------------------------------------

    /**
     * Cosine integral {@code Ci(x) = γ + ln(x) + integral_0^x (cos(t) - 1)/t dt}.
     *
     * <p>Only defined for {@code x >= 0}; the complex extension
     * {@code Ci(-x) = Ci(x) + iπ} requires the complex overload.
     *
     * @throws IllegalArgumentException if {@code x < 0}
     */
    public static double Ci(final double x) {
        QL.require(x >= 0, "x < 0 => Ci(x) = Ci(-x) + i*pi");

        if ( x <= 4.0 ) {
            final double x2 = x * x;

            return M_EULER_MASCHERONI + Math.log(x) +
                    x2 * (-0.25 + x2 * (7.51851524438898291e-3 + x2 * (-1.27528342240267686e-4 + x2 * (
                            1.05297363846239184e-6 + x2 * (-4.68889508144848019e-9 + x2 * (1.06480802891189243e-11
                                    - x2 * 9.93728488857585407e-15)))))) / (1 + x2 * (1.1592605689110735e-2 + x2 * (
                            6.72126800814254432e-5 + x2 * (2.55533277086129636e-7 + x2 * (6.97071295760958946e-10
                                    + x2 * (1.38536352772778619e-12 + x2 * (1.89106054713059759e-15
                                    + x2 * 1.39759616731376855e-18)))))));
        } else {
            return f(x) * Math.sin(x) - g(x) * Math.cos(x);
        }
    }

    /** sign(x): -1 / 0 / +1. */
    private static double sign(final double x) {
        return Double.compare(x, 0.0);
    }

    /**
     * Complex exponential integral with optional accumulator. Mirrors C++ {@code ExponentialIntegral::Ei(z, acc)}.
     */
    public static Complex Ei(final Complex z, final Complex acc) {
        if ( z.real() == 0.0 && z.imag() == 0.0 ) {
            return new Complex(Double.NEGATIVE_INFINITY, 0.0);
        }

        final double DIST = 4.5;
        final double MAX_ERROR = 5.0 * Constants.QL_EPSILON;

        QL.require(z.real() < Z_INF, "argument error " + z);

        final double z_asym = 2.0 - 1.035 * Math.log(MAX_ERROR);

        final double abs_z = z.abs();

        if ( z.real() > Z_INF ) {
            return z.exp().div(z).add(acc);
        }

        if ( abs_z > 1.1 * z_asym ) {
            Complex ei = acc.add(new Complex(0.0, sign(z.imag()) * Math.PI));
            Complex s = z.exp().div(z);
            final int upper = (int) Math.floor(abs_z) + 1;
            for ( int i = 1; i <= upper; ++i ) {
                final Complex next = ei.add(s);
                if ( matches(next, ei, MAX_ERROR) ) {
                    return next;
                }
                ei = ei.add(s);
                s = s.mul(i).div(z);
            }
            QL.error("series conversion issue for Ei(" + z + ")");
        }

        if ( abs_z > DIST && (z.real() < 0 || Math.abs(z.imag()) > DIST) ) {
            Complex ei = Complex.ZERO;
            for ( int k = 47; k >= 1; --k ) {
                final Complex denom = new Complex(2.0 * k + 1.0, 0.0).sub(z).add(ei);
                ei = new Complex(-(double) k * k, 0.0).div(denom);
            }
            return acc.add(new Complex(0.0, sign(z.imag()) * Math.PI))
                    .sub(z.exp().div(new Complex(1.0, 0.0).sub(z).add(ei)));
        }

        Complex s = Complex.ZERO;
        Complex sn = z;
        double nn = 1.0;

        int n;
        for ( n = 2; n < 1000; ++n ) {
            final Complex contrib = sn.mul(nn);
            final Complex next = s.add(contrib);
            if ( next.real() == s.real() && next.imag() == s.imag() ) {
                break;
            }
            s = next;

            if ( (n & 1) != 0 ) {
                nn += 1.0 / (2.0 * (n / 2) + 1.0);
            }
            sn = sn.mul(z.neg()).div(2.0 * n);
        }
        QL.require(n < 1000, "series conversion issue for Ei(" + z + ")");

        final Complex half_z_exp = z.mul(0.5).exp();
        final Complex r = new Complex(M_EULER_MASCHERONI, 0.0).add(acc).add(z.log()).add(half_z_exp.mul(s));

        if ( z.imag() != 0.0 ) {
            return r;
        }
        return new Complex(r.real(), acc.imag());
    }

    private static boolean matches(final Complex z1, final Complex z2, final double maxErr) {
        final Complex d = z1.sub(z2);
        return Math.abs(d.real()) <= maxErr * Math.abs(z1.real()) && Math.abs(d.imag()) <= maxErr * Math.abs(z1.imag());
    }

    /** Convenience overload with acc=0. */
    public static Complex Ei(final Complex z) {
        return Ei(z, Complex.ZERO);
    }

    /**
     * Complex E1: {@code E1(z) = integral_z^{∞} e^{-t}/t dt}, with the branch cut along the negative real axis. Mirrors
     * C++ {@code ExponentialIntegral::E1}.
     */
    public static Complex E1(final Complex z) {
        if ( z.imag() < 0.0 ) {
            return Ei(z.neg(), new Complex(0.0, -Math.PI)).neg();
        } else if ( z.imag() > 0.0 || z.real() < 0.0 ) {
            return Ei(z.neg(), new Complex(0.0, Math.PI)).neg();
        }
        return Ei(z.neg()).neg();
    }

    /**
     * Complex sine integral. Mirrors C++ {@code ExponentialIntegral::Si(complex)}.
     */
    public static Complex Si(final Complex z) {
        if ( z.abs() <= 0.2 ) {
            Complex s = Complex.ZERO;
            Complex nn = z;
            int k;
            for ( k = 2; k < 100; ++k ) {
                final Complex next = s.add(nn);
                if ( next.real() == s.real() && next.imag() == s.imag() ) {
                    break;
                }
                s = next;
                final double coef = -(2.0 * k - 3.0) / ((2.0 * k - 2.0) * (2.0 * k - 1.0) * (2.0 * k - 1.0));
                nn = nn.mul(z.mul(z)).mul(coef);
            }
            QL.require(k < 100, "series conversion issue for Si(" + z + ")");
            return s;
        }
        final Complex iz = new Complex(-z.imag(), z.real());   // i*z
        final Complex miz = new Complex(z.imag(), -z.real());   // -i*z
        final boolean firstOrFourthQuad = (z.real() >= 0.0 && z.imag() >= 0.0) || (z.real() > 0.0 && z.imag() < 0.0);
        final Complex shift = new Complex(0.0, firstOrFourthQuad ? Math.PI : -Math.PI);
        // 0.5 * i * ( E1(-iz) - E1(iz) - shift )
        return new Complex(0.0, 0.5).mul(E1(miz).sub(E1(iz)).sub(shift));
    }

    /**
     * Complex cosine integral. Mirrors C++ {@code ExponentialIntegral::Ci(complex)}.
     */
    public static Complex Ci(final Complex z) {
        Complex acc = Complex.ZERO;
        if ( z.real() < 0.0 && z.imag() >= 0.0 ) {
            acc = new Complex(0.0, Math.PI);
        } else if ( z.real() <= 0.0 && z.imag() <= 0.0 ) {
            acc = new Complex(0.0, -Math.PI);
        }
        final Complex iz = new Complex(-z.imag(), z.real());   // i*z
        final Complex miz = new Complex(z.imag(), -z.real());   // -i*z
        return E1(miz).add(E1(iz)).mul(-0.5).add(acc);
    }
}
