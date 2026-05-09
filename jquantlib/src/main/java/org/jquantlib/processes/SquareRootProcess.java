/*
 Copyright (C) 2003 Ferdinando Ametrano
 Copyright (C) 2001, 2002, 2003 Sadruddin Rejeb
 Copyright (C) 2004, 2005 StatPro Italia srl
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

package org.jquantlib.processes;

/**
 * Square-root process (CIR-type).
 *
 * <p>Governed by:
 * <pre>  dx = a (b - x_t) dt + sigma sqrt(x_t) dW_t</pre>
 * where {@code b} is the long-run mean, {@code a} is the speed of mean reversion,
 * and {@code sigma} is the volatility.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/processes/squarerootprocess.{hpp,cpp}}.
 *
 * @author Phase 4j port
 */
public class SquareRootProcess extends StochasticProcess1D {

    private final double x0_;
    private final double mean_;   // b — long-run mean
    private final double speed_;  // a — speed of mean reversion
    private final double volatility_; // sigma

    /**
     * @param b      long-run mean
     * @param a      speed of mean reversion
     * @param sigma  diffusion coefficient
     * @param x0     initial value
     */
    public SquareRootProcess(final double b, final double a, final double sigma,
                             final double x0) {
        super(new EulerDiscretization());
        this.x0_         = x0;
        this.mean_       = b;
        this.speed_      = a;
        this.volatility_ = sigma;
    }

    /**
     * @param b      long-run mean
     * @param a      speed of mean reversion
     * @param sigma  diffusion coefficient
     */
    public SquareRootProcess(final double b, final double a, final double sigma) {
        this(b, a, sigma, 0.0);
    }

    @Override
    public double x0() {
        return x0_;
    }

    @Override
    public double drift(final double t, final double x) {
        return speed_ * (mean_ - x);
    }

    @Override
    public double diffusion(final double t, final double x) {
        return volatility_ * Math.sqrt(x);
    }

    /** Speed of mean reversion ({@code a}). */
    public double a() {
        return speed_;
    }

    /** Long-run mean ({@code b}). */
    public double b() {
        return mean_;
    }

    /** Volatility coefficient ({@code sigma}). */
    public double sigma() {
        return volatility_;
    }
}
