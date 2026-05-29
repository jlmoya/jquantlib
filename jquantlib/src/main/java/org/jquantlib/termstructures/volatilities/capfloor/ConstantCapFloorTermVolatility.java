/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
/*
 Copyright (C) 2008 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.termstructures.volatilities.capfloor;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;

/**
 * Constant cap/floor term volatility — no time-strike dependence.
 *
 * <p>Java port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/volatility/capfloor/constantcapfloortermvol.{hpp,cpp}} — class
 * {@code ConstantCapFloorTermVolatility}.
 *
 * <p>Four constructors mirror C++ (floating/fixed reference date x floating/fixed market data):
 * <ul>
 *   <li>floating reference date, floating market data (quote handle)</li>
 *   <li>fixed reference date, floating market data (quote handle)</li>
 *   <li>floating reference date, fixed market data (literal vol)</li>
 *   <li>fixed reference date, fixed market data (literal vol)</li>
 * </ul>
 */
public class ConstantCapFloorTermVolatility extends CapFloorTermVolatilityStructure {

    private final Handle< ? extends Quote > volatility_;

    //
    // public constructors
    //

    /** Floating reference date, floating market data. */
    public ConstantCapFloorTermVolatility(final int settlementDays, final Calendar cal,
            final BusinessDayConvention bdc, final Handle< ? extends Quote > volatility, final DayCounter dc) {
        super(settlementDays, cal, bdc, dc);
        this.volatility_ = volatility;
        this.volatility_.addObserver(this);
    }

    /** Fixed reference date, floating market data. */
    public ConstantCapFloorTermVolatility(final Date referenceDate, final Calendar cal,
            final BusinessDayConvention bdc, final Handle< ? extends Quote > volatility, final DayCounter dc) {
        super(referenceDate, cal, bdc, dc);
        this.volatility_ = volatility;
        this.volatility_.addObserver(this);
    }

    /** Floating reference date, fixed market data. */
    public ConstantCapFloorTermVolatility(final int settlementDays, final Calendar cal,
            final BusinessDayConvention bdc, final /*@Volatility*/ double volatility, final DayCounter dc) {
        super(settlementDays, cal, bdc, dc);
        this.volatility_ = new Handle< Quote >(new SimpleQuote(volatility));
    }

    /** Fixed reference date, fixed market data. */
    public ConstantCapFloorTermVolatility(final Date referenceDate, final Calendar cal,
            final BusinessDayConvention bdc, final /*@Volatility*/ double volatility, final DayCounter dc) {
        super(referenceDate, cal, bdc, dc);
        this.volatility_ = new Handle< Quote >(new SimpleQuote(volatility));
    }

    //
    // TermStructure interface
    //

    @Override
    public Date maxDate() {
        return Date.maxDate();
    }

    //
    // VolatilityTermStructure interface
    //

    @Override
    public double minStrike() {
        return Constants.QL_MIN_REAL;
    }

    @Override
    public double maxStrike() {
        return Constants.QL_MAX_REAL;
    }

    //
    // protected
    //

    @Override
    protected double volatilityImpl(final double length, final double strike) {
        return volatility_.currentLink().value();
    }
}
