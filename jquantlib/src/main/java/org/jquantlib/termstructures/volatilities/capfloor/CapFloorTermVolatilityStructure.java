/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
/*
 Copyright (C) 2002, 2003 RiskMap srl
 Copyright (C) 2003, 2004, 2005, 2006 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.termstructures.volatilities.capfloor;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.termstructures.volatilities.VolatilityTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;

/**
 * Cap/floor term-volatility structure (purely abstract).
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/termstructures/volatility/capfloor/capfloortermvolatilitystructure.{hpp,cpp}}.
 * The class defines the public interface for any concrete cap/floor term-volatility surface; concrete subclasses (curve
 * / surface) implement {@link #volatilityImpl(double, double)}.
 */
public abstract class CapFloorTermVolatilityStructure extends VolatilityTermStructure {

    //
    // public constructors
    //

    /** "Default" constructor — concrete subclass must override referenceDate(). */
    public CapFloorTermVolatilityStructure(final Calendar cal, final BusinessDayConvention bdc, final DayCounter dc) {
        super(cal, bdc, dc);
    }

    /** Initialize with a fixed reference date. */
    public CapFloorTermVolatilityStructure(final Date refDate, final Calendar cal, final BusinessDayConvention bdc,
            final DayCounter dc) {
        super(refDate, cal, bdc, dc);
    }

    /** Calculate the reference date based on the global evaluation date. */
    public CapFloorTermVolatilityStructure(final int settlementDays, final Calendar cal,
            final BusinessDayConvention bdc, final DayCounter dc) {
        super(settlementDays, cal, bdc, dc);
    }

    //
    // public volatility lookups
    //

    public double volatility(final Period length, final double strike) {
        return volatility(length, strike, false);
    }

    public double volatility(final Date end, final double strike) {
        return volatility(end, strike, false);
    }

    public double volatility(final double t, final double strike) {
        return volatility(t, strike, false);
    }

    public double volatility(final Period length, final double strike, final boolean extrapolate) {
        final Date d = optionDateFromTenor(length);
        return volatility(d, strike, extrapolate);
    }

    public double volatility(final Date end, final double strike, final boolean extrapolate) {
        super.checkRange(timeFromReference(end), extrapolate);
        return volatility(timeFromReference(end), strike, extrapolate);
    }

    public double volatility(final double t, final double strike, final boolean extrapolate) {
        super.checkRange(t, extrapolate);
        checkStrike(strike, extrapolate);
        return volatilityImpl(t, strike);
    }

    //
    // protected
    //

    /** Concrete subclass volatility implementation. */
    protected abstract double volatilityImpl(double length, double strike);
}
