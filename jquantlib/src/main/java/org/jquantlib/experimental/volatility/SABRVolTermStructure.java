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

/*
 Copyright (C) 2017 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.volatility;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.termstructures.BlackVolatilityTermStructure;
import org.jquantlib.termstructures.volatilities.Sabr;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Implied vol surface backed by an analytic SABR model.
 *
 * <p>Faithful header-only port of QuantLib v1.42.1
 * {@code ql/experimental/volatility/sabrvoltermstructure.hpp}. Computes the
 * Black implied vol via the standard Hagan SABR closed form
 * {@link Sabr#sabrVolatility(double, double, double, double, double, double, double)}.
 *
 * <p>The C++ class names the third SABR parameter {@code gamma} (instead of
 * {@code nu}), but the substance is identical: {@code gamma} maps to the
 * {@code nu} (vol-of-vol) argument of the Hagan formula.
 */
public class SABRVolTermStructure extends BlackVolatilityTermStructure {

    private final double alpha_;
    private final double beta_;
    private final double gamma_; // == nu in the Hagan SABR formula
    private final double rho_;
    private final double s0_;
    private final double r_;

    public SABRVolTermStructure(final double alpha, final double beta,
            final double gamma, final double rho,
            final double s0, final double r,
            final Date referenceDate, final DayCounter dc) {
        super(referenceDate, new NullCalendar(), BusinessDayConvention.Following, dc);
        this.alpha_ = alpha;
        this.beta_  = beta;
        this.gamma_ = gamma;
        this.rho_   = rho;
        this.s0_    = s0;
        this.r_     = r;
    }

    @Override
    public Date maxDate() {
        return Date.maxDate();
    }

    @Override
    public double minStrike() {
        return 0.0;
    }

    @Override
    public double maxStrike() {
        return Double.MAX_VALUE;
    }

    @Override
    protected double blackVolImpl(final double t, final double strike) {
        // C++ sabrvoltermstructure.hpp lines 51-54.
        final double fwd = s0_ * Math.exp(r_ * t);
        return new Sabr().sabrVolatility(strike, fwd, t, alpha_, beta_, gamma_, rho_);
    }
}
