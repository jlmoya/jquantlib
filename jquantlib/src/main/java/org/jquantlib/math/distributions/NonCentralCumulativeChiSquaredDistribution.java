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
package org.jquantlib.math.distributions;

import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;

/**
 * Non-central chi-squared cumulative distribution.
 * <p>
 * Direct port of {@code QuantLib::NonCentralCumulativeChiSquareDistribution}
 * (v1.42.1, ql/math/distributions/chisquaredistribution.{hpp,cpp}). Implements
 * the AS-275-style series for the non-central chi-squared CDF with
 * {@code df} degrees of freedom and non-centrality {@code ncp}.
 * <p>
 * The class name uses the Java convention "ChiSquared" (rather than C++'s
 * "ChiSquare") to align with {@link NonCentralChiSquaredDistribution} and
 * other JQuantLib distribution classes.
 */
public class NonCentralCumulativeChiSquaredDistribution implements Ops.DoubleOp {

    private static final String FAILED_TO_CONVERGE = "didn't converge";

    private final double df_;
    private final double ncp_;
    private final GammaFunction gammaFunction_ = new GammaFunction();

    public NonCentralCumulativeChiSquaredDistribution(final double df, final double ncp) {
        this.df_ = df;
        this.ncp_ = ncp;
    }

    @Override
    public double op(final double x) {
        // Ported line-by-line from
        //   QuantLib::NonCentralCumulativeChiSquareDistribution::operator()(Real x)
        // in v1.42.1 chisquaredistribution.cpp. The original uses two
        // gotos (L10 and L_End) to thread an outer-loop bracket-search
        // phase (flag=false) into a convergence-checking phase
        // (flag=true). Java has no goto, so the structure is preserved
        // via a single labeled outer loop with explicit branches.
        if (x <= 0.0) {
            return 0.0;
        }

        final double errmax = 1.0e-12;
        final int itrmax = 10000;
        final double lam = 0.5 * ncp_;

        double u = Math.exp(-lam);
        double v = u;
        final double x2 = 0.5 * x;
        final double f2 = 0.5 * df_;
        double f_x_2n = df_ - x;

        double t;
        if (f2 * Constants.QL_EPSILON > 0.125
                && Math.abs(x2 - f2) < Math.sqrt(Constants.QL_EPSILON) * f2) {
            // Asymptotic branch in C++ when (df, x) are both very large
            // and very close together. NB: the C++ uses an uninitialised
            // local `t` here as part of the formula — preserve the value
            // (zero in C++ given no prior assignment at this call-site).
            t = Math.exp((1.0 - 0.0) * (2.0 - 0.0 / (f2 + 1.0)))
                    / Math.sqrt(2.0 * Math.PI * (f2 + 1.0));
        } else {
            t = Math.exp(f2 * Math.log(x2) - x2 - gammaFunction_.logValue(f2 + 1.0));
        }

        double ans = v * t;
        boolean flag = false;
        int n = 1;
        double f_2n = df_ + 2.0;
        f_x_2n += 2.0;

        double bound = Double.POSITIVE_INFINITY;

        // Mirror the C++ outer/inner loop structure with the L10 label
        // (bound check + convergence test) and L_End fall-through.
        outer:
        for (;;) {
            if (f_x_2n > 0.0) {
                flag = true;
                // Skip the inner step on first entry; jump straight to
                // the bound check (the C++ "goto L10" behaviour).
                bound = t * x / f_x_2n;
                if (bound <= errmax || n > itrmax) {
                    break outer;
                }
                // Fall through to the inner-loop step+L10 cycle.
            }
            // Inner loop. With flag=false we step once and break back
            // to the outer to re-check f_x_2n (matching the inner break
            // condition `!flag && n<=itrmax`). With flag=true we step
            // then test bound at L10, looping forever until convergence.
            for (;;) {
                u *= lam / n;
                v += u;
                t *= x / f_2n;
                ans += v * t;
                n++;
                f_2n += 2.0;
                f_x_2n += 2.0;
                if (!flag && n <= itrmax) {
                    // Break inner; fall back to outer top, which will
                    // re-test f_x_2n and decide whether to flip flag.
                    continue outer;
                }
                // L10 label: bound check + convergence test.
                bound = t * x / f_x_2n;
                if (bound <= errmax || n > itrmax) {
                    break outer;
                }
                // Loop inner top → step again.
            }
        }

        if (bound > errmax) {
            throw new ArithmeticException(FAILED_TO_CONVERGE);
        }
        return ans;
    }
}
