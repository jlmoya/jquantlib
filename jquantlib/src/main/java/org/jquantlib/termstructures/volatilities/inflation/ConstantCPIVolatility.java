/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
/*
 Copyright (C) 2009, 2011 Chris Kenyon

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.termstructures.volatilities.inflation;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;

/**
 * Constant CPI-volatility surface — no K or T dependence.
 *
 * <p>Java port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/volatility/inflation/constantcpivolatility.{hpp,cpp}} — class
 * {@code ConstantCPIVolatility}.
 */
public class ConstantCPIVolatility extends CPIVolatilitySurface {

    private final Handle< ? extends Quote > volatility_;

    //
    // public constructors — both forms in C++ (quote-handle and literal)
    //

    /** Constant volatility taking a quote handle. */
    public ConstantCPIVolatility(final Handle< ? extends Quote > vol, final /*@Natural*/ int settlementDays,
            final Calendar cal, final BusinessDayConvention bdc, final DayCounter dc, final Period observationLag,
            final Frequency frequency, final boolean indexIsInterpolated) {
        super(settlementDays, cal, bdc, dc, observationLag, frequency, indexIsInterpolated);
        this.volatility_ = vol;
    }

    /** Constant volatility from a literal value. */
    public ConstantCPIVolatility(final /*@Volatility*/ double vol, final /*@Natural*/ int settlementDays,
            final Calendar cal, final BusinessDayConvention bdc, final DayCounter dc, final Period observationLag,
            final Frequency frequency, final boolean indexIsInterpolated) {
        super(settlementDays, cal, bdc, dc, observationLag, frequency, indexIsInterpolated);
        this.volatility_ = new Handle< Quote >(new SimpleQuote(vol));
    }

    //
    // Limits
    //

    @Override
    public Date maxDate() {
        return Date.maxDate();
    }

    @Override
    public double minStrike() {
        return Constants.QL_MIN_REAL;
    }

    @Override
    public double maxStrike() {
        return Constants.QL_MAX_REAL;
    }

    //
    // private
    //

    @Override
    protected double volatilityImpl(final double length, final double strike) {
        return volatility_.currentLink().value();
    }
}
