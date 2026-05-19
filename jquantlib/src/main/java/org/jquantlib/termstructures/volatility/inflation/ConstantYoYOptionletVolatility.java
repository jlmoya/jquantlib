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

/*
 Copyright (C) 2009 Chris Kenyon

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.termstructures.volatility.inflation;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.time.*;

/**
 * Constant YoY-inflation optionlet volatility surface — no T or K dependence.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/termstructures/volatility/inflation/yoyinflationoptionletvolatilitystructure.{hpp,cpp}} — class
 * {@code ConstantYoYOptionletVolatility}.
 */
public class ConstantYoYOptionletVolatility extends YoYOptionletVolatilitySurface {

    private final Handle< ? extends Quote > volatility_;
    private final double minStrike_;
    private final double maxStrike_;

    //
    // public constructors — both forms in C++ (literal and quote-handle)
    //

    /** Constant volatility from a literal value. Defaults to ShiftedLognormal/0. */
    public ConstantYoYOptionletVolatility(final double v, final int settlementDays, final Calendar cal,
            final BusinessDayConvention bdc, final DayCounter dc, final Period observationLag,
            final Frequency frequency, final boolean indexIsInterpolated) {
        this(v, settlementDays, cal, bdc, dc, observationLag, frequency, indexIsInterpolated, -1.0, 100.0,
                VolatilityType.ShiftedLognormal, 0.0);
    }

    /** Constant volatility from a literal value with full strike/type knobs. */
    public ConstantYoYOptionletVolatility(final double v, final int settlementDays, final Calendar cal,
            final BusinessDayConvention bdc, final DayCounter dc, final Period observationLag,
            final Frequency frequency, final boolean indexIsInterpolated, final double minStrike,
            final double maxStrike, final VolatilityType volType, final double displacement) {
        super(settlementDays, cal, bdc, dc, observationLag, frequency, indexIsInterpolated, volType, displacement);
        this.volatility_ = new Handle< Quote >(new SimpleQuote(v));
        this.minStrike_ = minStrike;
        this.maxStrike_ = maxStrike;
    }

    /** Constant volatility taking a quote handle. Defaults to ShiftedLognormal/0. */
    public ConstantYoYOptionletVolatility(final Handle< ? extends Quote > v, final int settlementDays,
            final Calendar cal, final BusinessDayConvention bdc, final DayCounter dc, final Period observationLag,
            final Frequency frequency, final boolean indexIsInterpolated) {
        this(v, settlementDays, cal, bdc, dc, observationLag, frequency, indexIsInterpolated, -1.0, 100.0,
                VolatilityType.ShiftedLognormal, 0.0);
    }

    /** Constant volatility taking a quote handle with full knobs. */
    public ConstantYoYOptionletVolatility(final Handle< ? extends Quote > v, final int settlementDays,
            final Calendar cal, final BusinessDayConvention bdc, final DayCounter dc, final Period observationLag,
            final Frequency frequency, final boolean indexIsInterpolated, final double minStrike,
            final double maxStrike, final VolatilityType volType, final double displacement) {
        super(settlementDays, cal, bdc, dc, observationLag, frequency, indexIsInterpolated, volType, displacement);
        this.volatility_ = v;
        this.volatility_.addObserver(this);
        this.minStrike_ = minStrike;
        this.maxStrike_ = maxStrike;
    }

    //
    // overrides
    //

    @Override
    public Date maxDate() {
        return Date.maxDate();
    }

    @Override
    public double minStrike() {
        return minStrike_;
    }

    @Override
    public double maxStrike() {
        return maxStrike_;
    }

    @Override
    protected double volatilityImpl(final double length, final double strike) {
        return volatility_.currentLink().value();
    }
}
