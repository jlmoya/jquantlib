/*
 Copyright (C) 2015 Andres Hernandez
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

package org.jquantlib.experimental.math;

import org.jquantlib.QL;

/**
 * Levy Flight (a.k.a. Pareto Type I) distribution.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/math/levyflightdistribution.hpp}.
 *
 * <p>The PDF is
 * <pre>
 *   p(x) = alpha * x_m^alpha / x^(alpha+1)
 * </pre>
 * with support over {@code x &gt;= x_m} and parameter {@code alpha &gt; 0}.
 *
 * <p>Levy Flight is normally defined as {@code x_m = 1} and {@code 0 &lt; alpha &lt; 2}
 * (where the variance is infinite). The general Pareto Type I version is well
 * defined for {@code alpha &gt; 2}, so this implementation does not restrict
 * {@code alpha &lt; 2}.
 */
public class LevyFlightDistribution {

    /** Parameter holder. */
    public static final class ParamType {
        private final double xm_;
        private final double alpha_;

        public ParamType() {
            this(1.0, 1.0);
        }

        public ParamType(final double xm, final double alpha) {
            QL.require(alpha > 0.0, "alpha must be larger than 0");
            this.xm_ = xm;
            this.alpha_ = alpha;
        }

        public double xm() {
            return xm_;
        }

        public double alpha() {
            return alpha_;
        }
    }

    private double xm_;
    private double alpha_;

    public LevyFlightDistribution() {
        this(1.0, 1.0);
    }

    public LevyFlightDistribution(final double xm, final double alpha) {
        QL.require(alpha > 0.0, "alpha must be larger than 0");
        this.xm_ = xm;
        this.alpha_ = alpha;
    }

    public LevyFlightDistribution(final ParamType parm) {
        this.xm_ = parm.xm();
        this.alpha_ = parm.alpha();
    }

    /** Returns the {@code xm} parameter. */
    public double xm() {
        return xm_;
    }

    /** Returns the {@code alpha} parameter. */
    public double alpha() {
        return alpha_;
    }

    /** Smallest value the distribution can produce. */
    public double min() {
        return xm_;
    }

    /** Largest value the distribution can produce. */
    public double max() {
        return Double.MAX_VALUE;
    }

    /** Returns the parameters of the distribution. */
    public ParamType param() {
        return new ParamType(xm_, alpha_);
    }

    /** Sets the parameters of the distribution. */
    public void param(final ParamType parm) {
        this.xm_ = parm.xm();
        this.alpha_ = parm.alpha();
    }

    /** Resets internal state (no-op for this distribution). */
    public void reset() {
    }

    /** Returns the PDF evaluated at {@code x}. */
    public double op(final double x) {
        if (x < xm_) {
            return 0.0;
        }
        return alpha_ * Math.pow(xm_ / x, alpha_) / x;
    }

    /**
     * Returns a random variate distributed according to the Levy flight
     * distribution given a uniform variate {@code u} in {@code (0, 1)}. The
     * inverse-transform formula is
     * <pre>
     *   x = x_m * u^{-1/alpha}
     * </pre>
     *
     * @param u uniform variate in {@code (0, 1)}
     */
    public double draw(final double u) {
        return xm_ * Math.pow(u, -1.0 / alpha_);
    }
}
