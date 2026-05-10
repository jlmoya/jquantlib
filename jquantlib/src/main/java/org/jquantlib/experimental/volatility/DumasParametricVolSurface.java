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

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.experimental.volatility;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.BlackVolatilityTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Five-parameter Dumas parametric Black-volatility surface
 * (Borovkova / Permana, "Implied volatility in oil markets").
 *
 * <p>Java port of the inline reference helper in v1.42.1
 * {@code test-suite/riskneutraldensitycalculator.cpp} (lines 213-249).
 * QuantLib does not ship a public {@code DumasParametricVolSurface} in
 * {@code ql/...}; the class lives only in the test suite. We promote it
 * to {@code org.jquantlib.experimental.volatility} so the Java port of
 * {@code RiskNeutralDensityCalculatorTest} (and any downstream user)
 * can wire it up the same way.
 *
 * <p>Surface formula:
 * <pre>
 *   blackVol(t, K) = b1 + b2 * mn + b3 * mn^2 + b4 * t + b5 * mn * t
 * </pre>
 * where {@code mn = ln(F / K) / sqrt(t)} is moneyness on the
 * standard-deviation scale and {@code F = spot * q.discount(t) /
 * r.discount(t)} is the forward.
 *
 * <p>At {@code t = 0} the formula reduces to the at-the-money level
 * {@code b1}. Negative blackVol is mathematically possible for
 * pathological parameter sets — callers should pair this with
 * {@link NoExceptLocalVolSurface} when feeding into a Dupire
 * derivation.
 *
 * <p>Reference: Svetlana Borovkova, Ferry J. Permana,
 * <em>Implied volatility in oil markets</em>,
 * <a href="http://www.researchgate.net/publication/46493859_Implied_volatility_in_oil_markets">researchgate</a>.
 *
 * @author Phase Production-Audit
 */
public class DumasParametricVolSurface extends BlackVolatilityTermStructure {

    private final double b1_, b2_, b3_, b4_, b5_;
    private final Handle<? extends Quote> spot_;
    private final Handle<YieldTermStructure> rTS_;
    private final Handle<YieldTermStructure> qTS_;

    public DumasParametricVolSurface(final double b1,
                                     final double b2,
                                     final double b3,
                                     final double b4,
                                     final double b5,
                                     final Handle<? extends Quote> spot,
                                     final Handle<YieldTermStructure> rTS,
                                     final Handle<YieldTermStructure> qTS) {
        // C++: BlackVolatilityTermStructure(0, NullCalendar(), Following, rTS->dayCounter())
        super(0 /* settlement days */,
              new NullCalendar(),
              BusinessDayConvention.Following,
              rTS.currentLink().dayCounter());
        this.b1_ = b1;
        this.b2_ = b2;
        this.b3_ = b3;
        this.b4_ = b4;
        this.b5_ = b5;
        this.spot_ = spot;
        this.rTS_  = rTS;
        this.qTS_  = qTS;

        // Observer wiring so callers re-pricing on quote changes notice.
        this.spot_.addObserver(this);
        this.rTS_.addObserver(this);
        this.qTS_.addObserver(this);
    }

    @Override
    public Date maxDate() {
        return Date.maxDate();
    }

    @Override
    public /*@Real*/ double minStrike() {
        return 0.0;
    }

    @Override
    public /*@Real*/ double maxStrike() {
        // C++: QL_MAX_REAL == std::numeric_limits<Real>::max()
        return Double.MAX_VALUE;
    }

    /**
     * Implements the Dumas parametric surface:
     * {@code b1 + b2*mn + b3*mn^2 + b4*t + b5*mn*t}.
     *
     * <p>Mirrors v1.42.1 verbatim, including the {@code t < QL_EPSILON}
     * short-circuit to {@code b1}.
     */
    @Override
    protected /*@Volatility*/ double blackVolImpl(final /*@Time*/ double t,
                                                  final /*@Real*/ double strike) {
        QL.require(t >= 0.0, "t must be >= 0");

        if (t < Constants.QL_EPSILON) {
            return b1_;
        }

        final double fwd = spot_.currentLink().value()
                * qTS_.currentLink().discount(t) / rTS_.currentLink().discount(t);
        final double mn  = Math.log(fwd / strike) / Math.sqrt(t);

        return b1_ + b2_ * mn + b3_ * mn * mn + b4_ * t + b5_ * mn * t;
    }
}
