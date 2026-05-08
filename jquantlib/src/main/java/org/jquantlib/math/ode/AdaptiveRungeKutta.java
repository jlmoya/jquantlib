/*
 Copyright (C) 2012 Peter Caspers

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.math.ode;

import java.util.function.BiFunction;

import org.jquantlib.QL;
import org.jquantlib.math.transcendental.JQuantMath;

/**
 * Runge-Kutta ODE integration with adaptive stepsize.
 * <p>
 * Java port of v1.42.1
 * {@code ql/math/ode/adaptiverungekutta.hpp}.
 * <p>
 * Implements the Runge-Kutta-Cash-Karp method with adaptive step-size
 * control (Numerical Recipes in C, Chapter 16.2). The integrator advances
 * the solution of {@code y' = F(t, y)} from {@code t1} to {@code t2}.
 *
 * @author Phase 2l Track C.5 prerequisite port
 */
public class AdaptiveRungeKutta {

    /** ODE right-hand-side function type: {@code f(t, y) → dy/dt}. */
    @FunctionalInterface
    public interface OdeFct {
        double[] apply(double t, double[] y);
    }

    private static final int MAX_STEPS = 10000;
    private static final double TINY = 1.0e-30;
    private static final double SAFETY = 0.9;
    private static final double PGROW = -0.2;
    private static final double PSHRINK = -0.25;
    private static final double ERRCON = 1.89e-4;

    // Cash-Karp coefficients
    private static final double a2 = 0.2, a3 = 0.3, a4 = 0.6, a5 = 1.0, a6 = 0.875;
    private static final double b21 = 0.2;
    private static final double b31 = 3.0 / 40.0,  b32 = 9.0 / 40.0;
    private static final double b41 = 0.3,          b42 = -0.9,          b43 = 1.2;
    private static final double b51 = -11.0 / 54.0, b52 = 2.5,          b53 = -70.0 / 27.0;
    private static final double b54 = 35.0 / 27.0;
    private static final double b61 = 1631.0 / 55296.0, b62 = 175.0 / 512.0;
    private static final double b63 = 575.0 / 13824.0,  b64 = 44275.0 / 110592.0;
    private static final double b65 = 253.0 / 4096.0;

    // 5th-order weights
    private static final double c1 = 37.0 / 378.0, c3 = 250.0 / 621.0;
    private static final double c4 = 125.0 / 594.0, c6 = 512.0 / 1771.0;

    // Error weights (difference 4th vs 5th order)
    private static final double dc1 = c1 - 2825.0 / 27648.0;
    private static final double dc3 = c3 - 18575.0 / 48384.0;
    private static final double dc4 = c4 - 13525.0 / 55296.0;
    private static final double dc5 = -277.0 / 14336.0;
    private static final double dc6 = c6 - 0.25;

    private final double eps;
    private final double h1;
    private final double hmin;

    /**
     * Create integrator with given tolerances.
     *
     * @param eps          target error per step (relative)
     * @param relInitStep  initial step size
     */
    public AdaptiveRungeKutta(final double eps, final double relInitStep) {
        this(eps, relInitStep, 0.0);
    }

    public AdaptiveRungeKutta(final double eps, final double relInitStep, final double hmin) {
        this.eps = eps;
        this.h1 = relInitStep;
        this.hmin = hmin;
    }

    /**
     * Integrate the ODE from {@code x1} to {@code x2} with
     * initial condition {@code y1}.
     */
    public double[] solve(final OdeFct ode, final double[] y1, final double x1, final double x2) {
        final int n = y1.length;
        final double[] y = y1.clone();
        final double[] yScale = new double[n];

        double x = x1;
        double h = h1 * (x1 <= x2 ? 1.0 : -1.0);
        double hnext;
        final double[] hdid = new double[1];

        for (int nstp = 1; nstp <= MAX_STEPS; nstp++) {
            final double[] dydx = ode.apply(x, y);
            for (int i = 0; i < n; i++) {
                yScale[i] = Math.abs(y[i]) + Math.abs(dydx[i] * h) + TINY;
            }
            if ((x + h - x2) * (x + h - x1) > 0.0) {
                h = x2 - x;
            }
            hnext = rkqs(y, dydx, x, h, yScale, hdid, ode);
            x += hdid[0];

            if ((x - x2) * (x2 - x1) >= 0.0) {
                return y;
            }

            QL.require(Math.abs(hnext) > hmin,
                    "Step size (" + hnext + ") too small (" + hmin + " min) in AdaptiveRungeKutta");
            h = hnext;
        }
        throw new IllegalStateException("Too many steps (" + MAX_STEPS + ") in AdaptiveRungeKutta");
    }

    /**
     * Adaptive single step: returns hnext, updates y and hdid[0].
     */
    private double rkqs(final double[] y, final double[] dydx,
                        final double x, final double htry,
                        final double[] yScale, final double[] hdid,
                        final OdeFct derivs) {
        final int n = y.length;
        final double[] yerr = new double[n];
        final double[] ytemp = new double[n];

        double h = htry;
        double hnext;

        for (;;) {
            rkck(y, dydx, x, h, ytemp, yerr, derivs);

            double errmax = 0.0;
            for (int i = 0; i < n; i++) {
                errmax = Math.max(errmax, Math.abs(yerr[i] / yScale[i]));
            }
            errmax /= eps;

            if (errmax > 1.0) {
                final double htemp1 = SAFETY * h * JQuantMath.pow(errmax, PSHRINK);
                final double htemp2 = h / 10.0;
                final double maxPositive = htemp1 > htemp2 ? htemp1 : htemp2;
                final double maxNegative = htemp1 < htemp2 ? htemp1 : htemp2;
                h = (h >= 0.0) ? maxPositive : maxNegative;

                final double xnew = x + h;
                QL.require(xnew != x, "Stepsize underflow in AdaptiveRungeKutta::rkqs");
            } else {
                hnext = (errmax > ERRCON)
                        ? SAFETY * h * JQuantMath.pow(errmax, PGROW)
                        : 5.0 * h;
                hdid[0] = h;
                System.arraycopy(ytemp, 0, y, 0, n);
                return hnext;
            }
        }
    }

    /**
     * Cash-Karp step: fills {@code yout} (5th-order) and {@code yerr} (error estimate).
     */
    private void rkck(final double[] y, final double[] dydx,
                      final double x, final double h,
                      final double[] yout, final double[] yerr,
                      final OdeFct derivs) {
        final int n = y.length;
        final double[] ytemp = new double[n];

        // Step 1 (dydx already computed)
        for (int i = 0; i < n; i++) {
            ytemp[i] = y[i] + b21 * h * dydx[i];
        }
        final double[] ak2 = derivs.apply(x + a2 * h, ytemp);

        for (int i = 0; i < n; i++) {
            ytemp[i] = y[i] + h * (b31 * dydx[i] + b32 * ak2[i]);
        }
        final double[] ak3 = derivs.apply(x + a3 * h, ytemp);

        for (int i = 0; i < n; i++) {
            ytemp[i] = y[i] + h * (b41 * dydx[i] + b42 * ak2[i] + b43 * ak3[i]);
        }
        final double[] ak4 = derivs.apply(x + a4 * h, ytemp);

        for (int i = 0; i < n; i++) {
            ytemp[i] = y[i] + h * (b51 * dydx[i] + b52 * ak2[i] + b53 * ak3[i] + b54 * ak4[i]);
        }
        final double[] ak5 = derivs.apply(x + a5 * h, ytemp);

        for (int i = 0; i < n; i++) {
            ytemp[i] = y[i] + h * (b61 * dydx[i] + b62 * ak2[i] + b63 * ak3[i]
                    + b64 * ak4[i] + b65 * ak5[i]);
        }
        final double[] ak6 = derivs.apply(x + a6 * h, ytemp);

        for (int i = 0; i < n; i++) {
            yout[i] = y[i] + h * (c1 * dydx[i] + c3 * ak3[i] + c4 * ak4[i] + c6 * ak6[i]);
            yerr[i] = h * (dc1 * dydx[i] + dc3 * ak3[i] + dc4 * ak4[i]
                    + dc5 * ak5[i] + dc6 * ak6[i]);
        }
    }
}
