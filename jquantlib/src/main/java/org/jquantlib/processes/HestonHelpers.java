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
package org.jquantlib.processes;

import org.jquantlib.QL;
import org.jquantlib.math.Complex;
import org.jquantlib.math.Constants;
import org.jquantlib.math.ModifiedBesselFunction;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.integrals.GaussLaguerreIntegration;
import org.jquantlib.math.integrals.GaussLobattoIntegral;

/**
 * Static helpers for the Heston process Fourier-inversion machinery
 * used by the BroadieKaya exact-simulation discretizations. Phase 2f
 * WI-3 C.4-C.6 port of the anonymous-namespace functions in C++
 * v1.42.1 {@code ql/processes/hestonprocess.cpp} (lines ~112-358).
 *
 * <p>Public methods are static and pure (no shared state) so they can
 * be called freely from {@link HestonProcess#evolve}. The expensive
 * {@link GaussLaguerreIntegration}{@code (128)} instance is created
 * once per call site rather than memoized statically — the cost is
 * dwarfed by the integrand evaluations.
 */
public final class HestonHelpers {

    private static final double M_2_PI = 2.0 / Math.PI;

    private HestonHelpers() {}

    /**
     * Continuous Heston characteristic function (Broadie/Kaya 2006,
     * formula 13). Returns the complex value used by the Fourier
     * inversion of the conditional integrated-variance distribution.
     * Mirrors the {@code Phi} lambda in C++ hestonprocess.cpp.
     */
    public static Complex phi(final HestonProcess process, final Complex a,
                              final double nu_0, final double nu_t, final double dt) {
        final double theta = process.theta().currentLink().value();
        final double kappa = process.kappa().currentLink().value();
        final double sigma = process.sigma().currentLink().value();
        final double sigma2 = sigma * sigma;

        // ga = sqrt(kappa^2 - 2*sigma^2 * a * i)
        final Complex i = Complex.I;
        final Complex ga = Complex.real(kappa * kappa)
                .sub(a.mul(i).mul(2.0 * sigma2)).sqrt();

        final double d = 4.0 * theta * kappa / sigma2;
        final double nu = 0.5 * d - 1.0;

        // z = ga*exp(-0.5*ga*dt) / (1 - exp(-ga*dt))
        final Complex egaDt    = ga.mul(-dt).exp();             // e^{-ga*dt}
        final Complex eGa05Dt  = ga.mul(-0.5 * dt).exp();       // e^{-0.5*ga*dt}
        final Complex oneMinus = Complex.ONE.sub(egaDt);
        final Complex z = ga.mul(eGa05Dt).div(oneMinus);
        // log_z = -0.5*ga*dt + log(ga / (1 - exp(-ga*dt)))
        final Complex logZ = ga.mul(-0.5 * dt).add(ga.div(oneMinus).log());

        // alpha = 4*ga*exp(-0.5*ga*dt) / (sigma2 * (1 - exp(-ga*dt)))
        final Complex alpha = ga.mul(eGa05Dt).mul(4.0)
                .div(oneMinus.mul(sigma2));
        // beta = 4*kappa*exp(-0.5*kappa*dt) / (sigma2 * (1 - exp(-kappa*dt)))
        final double  ekDt   = Math.exp(-kappa * dt);
        final double  ek05Dt = Math.exp(-0.5 * kappa * dt);
        final double  beta   = 4.0 * kappa * ek05Dt
                / (sigma2 * (1.0 - ekDt));

        // Prefactor: ga * exp(-0.5*(ga - kappa)*dt) * (1 - exp(-kappa*dt))
        //           / (kappa * (1 - exp(-ga*dt)))
        final Complex prefac = ga.mul(ga.sub(kappa).mul(-0.5 * dt).exp())
                .mul(1.0 - ekDt)
                .div(oneMinus.mul(kappa));

        // exp((nu_0 + nu_t)/sigma2 *
        //   (kappa*(1+e^{-kappa dt})/(1-e^{-kappa dt})
        //    - ga*(1+e^{-ga dt})/(1-e^{-ga dt})))
        final double sumNu = (nu_0 + nu_t) / sigma2;
        final Complex bracket = Complex.real(
                kappa * (1.0 + ekDt) / (1.0 - ekDt))
                .sub(ga.mul(Complex.ONE.add(egaDt)).div(oneMinus));
        final Complex expBracket = bracket.mul(sumNu).exp();

        // exp(nu * log_z) / z^nu
        final Complex expNuLogZ = logZ.mul(nu).exp();
        final Complex zPowNu = z.pow(nu);

        // Bessel ratio for nu_t > 1e-8, else (alpha/beta)^nu
        final Complex besselFactor;
        if (nu_t > 1e-8) {
            final Complex sqrtNu = Complex.real(Math.sqrt(nu_0 * nu_t));
            final Complex num = ModifiedBesselFunction.i(nu, sqrtNu.mul(alpha));
            final Complex den = ModifiedBesselFunction.i(nu, sqrtNu.mul(beta));
            besselFactor = num.div(den);
        } else {
            besselFactor = alpha.div(beta).pow(nu);
        }

        return prefac.mul(expBracket).mul(expNuLogZ).div(zPowNu).mul(besselFactor);
    }

    /**
     * Real kernel for the Fourier inversion of the conditional
     * integrated-variance CDF. Mirrors {@code ch} in C++.
     */
    public static double ch(final HestonProcess process,
                            final double x, final double u,
                            final double nu_0, final double nu_t, final double dt) {
        return M_2_PI * Math.sin(u * x) / u
                * phi(process, Complex.real(u), nu_0, nu_t, dt).real();
    }

    /**
     * Cornish-Fisher upper-tail quantile estimate for the conditional
     * integrated-variance distribution, used to set the integration
     * bounds for {@link #cdfNuDs}. Mirrors {@code cornishFisherEps}.
     */
    public static double cornishFisherEps(final HestonProcess process,
                                          final double nu_0, final double nu_t,
                                          final double dt, final double eps) {
        final double d = 1e-2;
        final double p2  = phi(process, Complex.of(0.0, -2 * d), nu_0, nu_t, dt).real();
        final double p1  = phi(process, Complex.of(0.0, -d),     nu_0, nu_t, dt).real();
        final double p0  = phi(process, Complex.of(0.0, 0.0),    nu_0, nu_t, dt).real();
        final double pm1 = phi(process, Complex.of(0.0, d),      nu_0, nu_t, dt).real();
        final double pm2 = phi(process, Complex.of(0.0, 2 * d),  nu_0, nu_t, dt).real();

        final double avg    = (pm2 - 8 * pm1 + 8 * p1 - p2) / (12 * d);
        final double m2     = (-pm2 + 16 * pm1 - 30 * p0 + 16 * p1 - p2) / (12 * d * d);
        final double var    = m2 - avg * avg;
        final double stdDev = Math.sqrt(var);

        final double m3 = (-0.5 * pm2 + pm1 - p1 + 0.5 * p2) / (d * d * d);
        final double skew = (m3 - 3 * var * avg - avg * avg * avg) / (var * stdDev);

        final double m4 = (pm2 - 4 * pm1 + 6 * p0 - 4 * p1 + p2) / (d * d * d * d);
        final double kurt = (m4 - 4 * m3 * avg + 6 * m2 * avg * avg
                - 3 * avg * avg * avg * avg) / (var * var);

        final double q = new InverseCumulativeNormal().op(1.0 - eps);
        final double w = q + (q * q - 1) / 6 * skew + (q * q * q - 3 * q) / 24 * (kurt - 3)
                - (2 * q * q * q - 5 * q) / 36 * skew * skew;

        return avg + w * stdDev;
    }

    /**
     * Fourier-inverted conditional integrated-variance CDF evaluated
     * at point {@code x}, dispatched on the BroadieKaya integration
     * method. Mirrors {@code cdf_nu_ds} in C++.
     */
    public static double cdfNuDs(final HestonProcess process,
                                 final double x,
                                 final double nu_0, final double nu_t,
                                 final double dt,
                                 final HestonProcess.Discretization discretization) {
        final double eps = 1e-4;
        final double u_eps = Math.min(100.0,
                Math.max(0.1, cornishFisherEps(process, nu_0, nu_t, dt, eps)));

        switch (discretization) {
            case BroadieKayaExactSchemeLaguerre: {
                final GaussLaguerreIntegration gaussLaguerre = new GaussLaguerreIntegration(128);
                double upper = u_eps / 2.0;
                while (Math.abs(phi(process, Complex.real(upper), nu_0, nu_t, dt).abs() / upper)
                        > eps) {
                    upper *= 2.0;
                }
                if (x < upper) {
                    final double upperFinal = upper;
                    final double v = gaussLaguerre.op(new Ops.DoubleOp() {
                        public double op(double u) {
                            return ch(process, x, u, nu_0, nu_t, dt);
                        }
                    });
                    return Math.max(0.0, Math.min(1.0, v));
                } else {
                    return 1.0;
                }
            }
            case BroadieKayaExactSchemeLobatto: {
                double upper = u_eps / 2.0;
                while (Math.abs(phi(process, Complex.real(upper), nu_0, nu_t, dt).abs() / upper)
                        > eps) {
                    upper *= 2.0;
                }
                if (x < upper) {
                    final double upperFinal = upper;
                    final GaussLobattoIntegral lobatto =
                            new GaussLobattoIntegral(Integer.MAX_VALUE, eps);
                    final double v = lobatto.op(new Ops.DoubleOp() {
                        public double op(double xi) {
                            return ch(process, x, xi, nu_0, nu_t, dt);
                        }
                    }, Constants.QL_EPSILON, upperFinal);
                    return Math.max(0.0, Math.min(1.0, v));
                } else {
                    return 1.0;
                }
            }
            case BroadieKayaExactSchemeTrapezoidal: {
                final double h = 0.05;
                double si = sineIntegral(0.5 * h * x);
                double s = M_2_PI * si;
                Complex f;
                int j = 0;
                do {
                    ++j;
                    final double u = h * j;
                    final double si_n = sineIntegral(x * (u + 0.5 * h));
                    f = phi(process, Complex.real(u), nu_0, nu_t, dt);
                    s += M_2_PI * f.real() * (si_n - si);
                    si = si_n;
                } while (M_2_PI * f.abs() / j > eps);
                return s;
            }
            default:
                throw new IllegalArgumentException("unknown integration method: " + discretization);
        }
    }

    /**
     * Solve {@code cdf_nu_ds(xi) - x0 == 0} for {@code xi} via Brent.
     * Helper for {@link HestonProcess#evolve} BroadieKaya branches.
     * Mirrors {@code cdf_nu_ds_minus_x} + the Brent call site in C++.
     */
    public static double cdfNuDsMinusX(final HestonProcess process,
                                       final double x,
                                       final double nu_0, final double nu_t,
                                       final double dt,
                                       final HestonProcess.Discretization discretization,
                                       final double x0) {
        return cdfNuDs(process, x, nu_0, nu_t, dt, discretization) - x0;
    }

    // ------------------------------------------------------------------
    // Sine integral via Padé approximation — port of C++ hestonprocess.cpp
    // anonymous-namespace `Si(Real x)` function with the same coefficient
    // tables. Used by the Trapezoidal BroadieKaya branch only.
    // ------------------------------------------------------------------

    private static final double[] SI_N4 = {
            -4.54393409816329991e-2,  1.15457225751016682e-3,
            -1.41018536821330254e-5,  9.43280809438713025e-8,
            -3.53201978997168357e-10, 7.08240282274875911e-13,
            -6.05338212010422477e-16
    };
    private static final double[] SI_D4 = {
             1.01162145739225565e-2, 4.99175116169755106e-5,
             1.55654986308745614e-7, 3.28067571055789734e-10,
             4.5049097575386581e-13, 3.21107051193712168e-16,
             0.0
    };
    private static final double[] SI_FN = {
            7.44437068161936700618e2, 1.96396372895146869801e5,
            2.37750310125431834034e7, 1.43073403821274636888e9,
            4.33736238870432522765e10, 6.40533830574022022911e11,
            4.20968180571076940208e12, 1.00795182980368574617e13,
            4.94816688199951963482e12, -4.94701168645415959931e11
    };
    private static final double[] SI_FD = {
            7.46437068161927678031e2, 1.97865247031583951450e5,
            2.41535670165126845144e7, 1.47478952192985464958e9,
            4.58595115847765779830e10, 7.08501308149515401563e11,
            5.06084464593475076774e12, 1.43468549171581016479e13,
            1.11535493509914254097e13, 0.0
    };
    private static final double[] SI_GN = {
            8.1359520115168615e2,  2.35239181626478200e5,
            3.12557570795778731e7, 2.06297595146763354e9,
            6.83052205423625007e10,1.09049528450362786e12,
            7.57664583257834349e12,1.81004487464664575e13,
            6.43291613143049485e12,-1.36517137670871689e12
    };
    private static final double[] SI_GD = {
            8.19595201151451564e2, 2.40036752835578777e5,
            3.26026661647090822e7, 2.23355543278099360e9,
            7.87465017341829930e10,1.39866710696414565e12,
            1.17164723371736605e13,4.01839087307656620e13,
            3.99653257887490811e13,0.0
    };

    private static double pade(final double x, final double[] num, final double[] den, final int m) {
        double n = 0.0, d = 0.0;
        for (int i = m - 1; i >= 0; --i) {
            n = (n + num[i]) * x;
            d = (d + den[i]) * x;
        }
        return (1.0 + n) / (1.0 + d);
    }

    /** Sine integral Si(x) via Padé approximation; matches C++ {@code Si} verbatim. */
    public static double sineIntegral(final double x) {
        if (x <= 4.0) {
            return x * pade(x * x, SI_N4, SI_D4, SI_N4.length);
        } else {
            final double y = 1.0 / (x * x);
            final double f = pade(y, SI_FN, SI_FD, 10) / x;
            final double g = y * pade(y, SI_GN, SI_GD, 10);
            return Math.PI / 2.0 - f * Math.cos(x) - g * Math.sin(x);
        }
    }

    /** Hide unused import warnings for QL.require. */
    @SuppressWarnings("unused")
    private static void touchQl() { QL.require(true, "ignore"); }
}
