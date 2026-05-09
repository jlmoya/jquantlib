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
package org.jquantlib.processes;

import org.jquantlib.math.Constants;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.InverseCumulativePoisson;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * Square-root stochastic-volatility Bates process — Heston SV plus a
 * compound-Poisson process with log-normal jump sizes.
 *
 * <p>Phase 5h.5-Bates port of QuantLib v1.42.1
 * {@code ql/processes/batesprocess.{hpp,cpp}}.
 *
 * <p>Stochastic differential equations:
 * <pre>
 *   dS(t,S)  = (r - d - lambda * m) S dt + sqrt(v) S dW1 + (e^J - 1) S dN
 *   dv(t,S)  = kappa (theta - v) dt + sigma sqrt(v) dW2
 *   dW1 dW2  = rho dt
 *   omega(J) = (1 / sqrt(2 pi delta^2)) exp(-(J - nu)^2 / (2 delta^2))
 * </pre>
 * where {@code m = exp(nu + 0.5*delta^2) - 1} is the mean jump size.
 *
 * <p>Inherits drift and diffusion structure from {@link HestonProcess}; the
 * Bates extension subtracts {@code lambda * m} from the equity drift and adds
 * a compound-Poisson jump term to the equity leg in {@link #evolve}. The
 * factor count grows by 2 (one Poisson uniform draw, one normal jump-size
 * draw) over the Heston base.
 *
 * @see HestonProcess
 */
public class BatesProcess extends HestonProcess {

    private final double lambda_;
    private final double delta_;
    private final double nu_;
    private final double m_;
    private final CumulativeNormalDistribution cumNormalDist_ = new CumulativeNormalDistribution();

    public BatesProcess(
            final Handle<YieldTermStructure> riskFreeRate,
            final Handle<YieldTermStructure> dividendYield,
            final Handle<Quote> s0,
            final double v0, final double kappa,
            final double theta, final double sigma, final double rho,
            final double lambda, final double nu, final double delta) {
        this(riskFreeRate, dividendYield, s0, v0, kappa, theta, sigma, rho,
             lambda, nu, delta, Discretization.FullTruncation);
    }

    public BatesProcess(
            final Handle<YieldTermStructure> riskFreeRate,
            final Handle<YieldTermStructure> dividendYield,
            final Handle<Quote> s0,
            final double v0, final double kappa,
            final double theta, final double sigma, final double rho,
            final double lambda, final double nu, final double delta,
            final Discretization d) {
        super(riskFreeRate, dividendYield, s0, v0, kappa, theta, sigma, rho, d);
        this.lambda_ = lambda;
        this.delta_  = delta;
        this.nu_     = nu;
        this.m_      = Math.exp(nu + 0.5 * delta * delta) - 1.0;
    }

    /**
     * Number of Brownian / uniform factors. Bates adds two extra draws (one
     * uniform for the Poisson inversion, one normal for the jump magnitude)
     * to the underlying Heston factor count.
     */
    @Override
    public int factors() {
        return super.factors() + 2;
    }

    @Override
    public Array drift(final double t, final Array x) {
        final Array retVal = super.drift(t, x);
        // adjust equity drift for the jump-induced compensator
        final double[] data = new double[retVal.size()];
        for (int i = 0; i < data.length; ++i) {
            data[i] = retVal.get(i);
        }
        data[0] -= lambda_ * m_;
        return new Array(data);
    }

    @Override
    public Array evolve(final double t0, final Array x0,
                        final double dt, final Array dw) {
        final int hestonFactors = super.factors();

        double p = cumNormalDist_.op(dw.get(hestonFactors));
        if (p < 0.0) {
            p = 0.0;
        } else if (p >= 1.0) {
            p = 1.0 - Constants.QL_EPSILON;
        }

        final double n = new InverseCumulativePoisson(lambda_ * dt).op(p);
        final Array retVal = super.evolve(t0, x0, dt, dw);
        final double[] data = new double[retVal.size()];
        for (int i = 0; i < data.length; ++i) {
            data[i] = retVal.get(i);
        }
        data[0] *= Math.exp(-lambda_ * m_ * dt
                            + nu_ * n
                            + delta_ * Math.sqrt(n) * dw.get(hestonFactors + 1));
        return new Array(data);
    }

    public double lambda() { return lambda_; }
    public double nu()     { return nu_; }
    public double delta()  { return delta_; }
}
