/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2008 Allen Kuo

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.experimental.callablebonds;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.volatilities.FlatSmileSection;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * Constant callable-bond volatility, no time-strike dependence.
 * <p>
 * Port of C++ v1.42.1 {@code ql/experimental/callablebonds/callablebondconstantvol.{hpp,cpp}}.
 */
public class CallableBondConstantVolatility extends CallableBondVolatilityStructure {

    private final Handle< Quote > volatility_;
    private final DayCounter dayCounter_;
    private final Period maxBondTenor_;

    public CallableBondConstantVolatility(final Date referenceDate, final double volatility,
            final DayCounter dayCounter) {
        super(referenceDate);
        this.volatility_ = new Handle< Quote >(new SimpleQuote(volatility));
        this.dayCounter_ = dayCounter;
        this.maxBondTenor_ = new Period(100, TimeUnit.Years);
    }

    public CallableBondConstantVolatility(final Date referenceDate, final Handle< Quote > volatility,
            final DayCounter dayCounter) {
        super(referenceDate);
        this.volatility_ = volatility;
        this.dayCounter_ = dayCounter;
        this.maxBondTenor_ = new Period(100, TimeUnit.Years);
        this.volatility_.addObserver(this);
    }

    public CallableBondConstantVolatility(final int settlementDays, final Calendar calendar, final double volatility,
            final DayCounter dayCounter) {
        super(settlementDays, calendar, dayCounter);
        this.volatility_ = new Handle< Quote >(new SimpleQuote(volatility));
        this.dayCounter_ = dayCounter;
        this.maxBondTenor_ = new Period(100, TimeUnit.Years);
    }

    public CallableBondConstantVolatility(final int settlementDays, final Calendar calendar,
            final Handle< Quote > volatility, final DayCounter dayCounter) {
        super(settlementDays, calendar, dayCounter);
        this.volatility_ = volatility;
        this.dayCounter_ = dayCounter;
        this.maxBondTenor_ = new Period(100, TimeUnit.Years);
        this.volatility_.addObserver(this);
    }

    @Override
    public DayCounter dayCounter() {
        return dayCounter_;
    }

    @Override
    public Date maxDate() {
        return Date.maxDate();
    }

    @Override
    public Period maxBondTenor() {
        return maxBondTenor_;
    }

    @Override
    public double maxBondLength() {
        return Constants.QL_MAX_REAL;
    }

    @Override
    public double minStrike() {
        return Constants.QL_MIN_REAL;
    }

    @Override
    public double maxStrike() {
        return Constants.QL_MAX_REAL;
    }

    @Override
    protected double volatilityImpl(final double t1, final double t2, final double strike) {
        return volatility_.currentLink().value();
    }

    @Override
    protected double volatilityImpl(final Date optionDate, final Period bondTenor, final double strike) {
        return volatility_.currentLink().value();
    }

    @Override
    protected SmileSection smileSectionImpl(final double optionTime, final double bondLength) {
        final double atmVol = volatility_.currentLink().value();
        return new FlatSmileSection(optionTime, atmVol, dayCounter_);
    }
}
