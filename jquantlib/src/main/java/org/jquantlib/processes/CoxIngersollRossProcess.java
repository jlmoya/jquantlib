/*
 Copyright (C) 2020 Lew Wei Hao
 Copyright (C) 2021 Magnus Mencke
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
package org.jquantlib.processes;

import org.jquantlib.QL;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;

/**
 * Cox-Ingersoll-Ross process.
 *
 * <p>Governed by:
 * <pre>  dx(t) = k (theta - x(t)) dt + sigma sqrt(x(t)) dW(t)</pre>
 *
 * <p>The process is discretised using the Quadratic-Exponential scheme of
 * Leif Andersen ("Efficient Simulation of the Heston Stochastic Volatility
 * Model", J. Comput. Finance, 2008).
 *
 * <p>Java port of v1.42.1
 * {@code ql/processes/coxingersollrossprocess.{hpp,cpp}}.
 *
 * <p><b>Note vs. {@link SquareRootProcess}.</b> {@code SquareRootProcess}
 * is a sibling type that mirrors C++ {@code SquareRootProcess} (Cox-Ross
 * volatility process used by jump-diffusion models). The CIR process here
 * matches the same SDE but ships the analytic mean and variance closed-form
 * solutions and the Andersen QE evolution rule — these are required by
 * {@code FdmSimpleProcess1dMesher} when building the short-rate FD grid.
 *
 * @author Phase 5e.5b-CFC-d-86 port
 */
public class CoxIngersollRossProcess extends StochasticProcess1D {

    private final double x0_;
    private final double speed_;
    private final double level_;
    private final double volatility_;

    /**
     * Construct a CIR process.
     *
     * @param speed       mean-reversion speed {@code k}
     * @param vol         diffusion coefficient {@code sigma}
     * @param x0          initial value
     * @param level       long-run mean {@code theta}
     */
    public CoxIngersollRossProcess(final double speed,
                                   final double vol,
                                   final double x0,
                                   final double level) {
        super(new EulerDiscretization());
        QL.require(vol >= 0.0, "negative volatility given");
        this.x0_         = x0;
        this.speed_      = speed;
        this.level_      = level;
        this.volatility_ = vol;
    }

    /** Convenience constructor: x0 = 0, level = 0. */
    public CoxIngersollRossProcess(final double speed, final double vol) {
        this(speed, vol, 0.0, 0.0);
    }

    @Override
    public double x0() { return x0_; }

    /** Mean-reversion speed {@code k}. */
    public double speed() { return speed_; }

    /** Diffusion coefficient {@code sigma}. */
    public double volatility() { return volatility_; }

    /** Long-run mean {@code theta}. */
    public double level() { return level_; }

    @Override
    public double drift(final double t, final double x) {
        return speed_ * (level_ - x);
    }

    @Override
    public double diffusion(final double t, final double x) {
        return volatility_;
    }

    @Override
    public double expectation(final double t0, final double x0, final double dt) {
        return level_ + (x0 - level_) * Math.exp(-speed_ * dt);
    }

    @Override
    public double stdDeviation(final double t, final double x0, final double dt) {
        return Math.sqrt(variance(t, x0, dt));
    }

    @Override
    public double variance(final double t, final double x0, final double dt) {
        // Mirrors C++ v1.42.1 CoxIngersollRossProcess::variance.
        // Note: uses {@code x0_} (initial), not the {@code x0} argument —
        // matches C++ verbatim (the runtime argument is ignored in the
        // closed-form expression).
        final double e1 = Math.exp(-speed_ * dt);
        final double e2 = Math.exp(-2.0 * speed_ * dt);
        final double frac = (volatility_ * volatility_) / speed_;
        return x0_ * frac * (e1 - e2)
                + level_ * frac * (1.0 - e1) * (1.0 - e1);
    }

    @Override
    public double evolve(final double t0, final double x0, final double dt, final double dw) {
        // Quadratic-Exponential discretisation (Andersen 2008).
        final double ex = Math.exp(-speed_ * dt);

        final double m  = level_ + (x0 - level_) * ex;
        final double s2 = x0 * volatility_ * volatility_ * ex / speed_ * (1.0 - ex)
                        + level_ * volatility_ * volatility_ / (2.0 * speed_)
                          * (1.0 - ex) * (1.0 - ex);
        final double psi = s2 / (m * m);

        if (psi <= 1.5) {
            final double b2 = 2.0 / psi - 1.0
                            + Math.sqrt(2.0 / psi * (2.0 / psi - 1.0));
            final double b  = Math.sqrt(b2);
            final double a  = m / (1.0 + b2);
            return a * (b + dw) * (b + dw);
        } else {
            final double p = (psi - 1.0) / (psi + 1.0);
            final double beta = (1.0 - p) / m;

            final double u = new CumulativeNormalDistribution().op(dw);

            return (u <= p) ? 0.0
                            : Math.log((1.0 - p) / (1.0 - u)) / beta;
        }
    }
}
