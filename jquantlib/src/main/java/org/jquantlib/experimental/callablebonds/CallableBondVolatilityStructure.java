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

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.termstructures.AbstractTermStructure;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.util.Pair;

/**
 * Callable-bond volatility structure (abstract).
 * <p>
 * Port of C++ v1.42.1 {@code ql/experimental/callablebonds/callablebondvolstructure.{hpp,cpp}}.
 * <p>
 * This class is purely abstract and defines the interface of concrete callable-bond volatility structures which derive
 * from it.
 */
public abstract class CallableBondVolatilityStructure extends AbstractTermStructure {

    private final BusinessDayConvention bdc_;

    public CallableBondVolatilityStructure(final DayCounter dc, final BusinessDayConvention bdc) {
        super(dc);
        this.bdc_ = bdc;
    }

    public CallableBondVolatilityStructure(final DayCounter dc) {
        this(dc, BusinessDayConvention.Following);
    }

    public CallableBondVolatilityStructure(final Date referenceDate, final Calendar calendar, final DayCounter dc,
            final BusinessDayConvention bdc) {
        super(referenceDate, calendar, dc);
        this.bdc_ = bdc;
    }

    public CallableBondVolatilityStructure(final Date referenceDate, final Calendar calendar, final DayCounter dc) {
        this(referenceDate, calendar, dc, BusinessDayConvention.Following);
    }

    public CallableBondVolatilityStructure(final Date referenceDate) {
        this(referenceDate, new org.jquantlib.time.calendars.NullCalendar(),
                new org.jquantlib.daycounters.Actual365Fixed());
    }

    public CallableBondVolatilityStructure(final int settlementDays, final Calendar calendar, final DayCounter dc,
            final BusinessDayConvention bdc) {
        super(settlementDays, calendar, dc);
        this.bdc_ = bdc;
    }

    public CallableBondVolatilityStructure(final int settlementDays, final Calendar calendar, final DayCounter dc) {
        this(settlementDays, calendar, dc, BusinessDayConvention.Following);
    }

    /** returns the volatility for a given option time and bondLength */
    public final double volatility(final double optionTime, final double bondLength, final double strike,
            final boolean extrapolate) {
        checkRange(optionTime, bondLength, strike, extrapolate);
        return volatilityImpl(optionTime, bondLength, strike);
    }

    public final double volatility(final double optionTime, final double bondLength, final double strike) {
        return volatility(optionTime, bondLength, strike, false);
    }

    /** returns the Black variance for a given option time and bondLength */
    public final double blackVariance(final double optionTime, final double bondLength, final double strike,
            final boolean extrapolate) {
        checkRange(optionTime, bondLength, strike, extrapolate);
        final double vol = volatilityImpl(optionTime, bondLength, strike);
        return vol * vol * optionTime;
    }

    public final double blackVariance(final double optionTime, final double bondLength, final double strike) {
        return blackVariance(optionTime, bondLength, strike, false);
    }

    /** returns the volatility for a given option date and bond tenor */
    public final double volatility(final Date optionDate, final Period bondTenor, final double strike,
            final boolean extrapolate) {
        checkRange(optionDate, bondTenor, strike, extrapolate);
        return volatilityImpl(optionDate, bondTenor, strike);
    }

    public final double volatility(final Date optionDate, final Period bondTenor, final double strike) {
        return volatility(optionDate, bondTenor, strike, false);
    }

    /** returns the Black variance for a given option date and bond tenor */
    public final double blackVariance(final Date optionDate, final Period bondTenor, final double strike,
            final boolean extrapolate) {
        final double vol = volatility(optionDate, bondTenor, strike, extrapolate);
        final Pair< Double, Double > p = convertDates(optionDate, bondTenor);
        return vol * vol * p.first();
    }

    public final double blackVariance(final Date optionDate, final Period bondTenor, final double strike) {
        return blackVariance(optionDate, bondTenor, strike, false);
    }

    public SmileSection smileSection(final Date optionDate, final Period bondTenor) {
        final Pair< Double, Double > p = convertDates(optionDate, bondTenor);
        return smileSectionImpl(p.first(), p.second());
    }

    /** returns the volatility for a given option tenor and bond tenor */
    public final double volatility(final Period optionTenor, final Period bondTenor, final double strike,
            final boolean extrapolate) {
        final Date optionDate = optionDateFromTenor(optionTenor);
        return volatility(optionDate, bondTenor, strike, extrapolate);
    }

    public final double volatility(final Period optionTenor, final Period bondTenor, final double strike) {
        return volatility(optionTenor, bondTenor, strike, false);
    }

    /** returns the Black variance for a given option tenor and bond tenor */
    public final double blackVariance(final Period optionTenor, final Period bondTenor, final double strike,
            final boolean extrapolate) {
        final Date optionDate = optionDateFromTenor(optionTenor);
        final double vol = volatility(optionDate, bondTenor, strike, extrapolate);
        final Pair< Double, Double > p = convertDates(optionDate, bondTenor);
        return vol * vol * p.first();
    }

    public final double blackVariance(final Period optionTenor, final Period bondTenor, final double strike) {
        return blackVariance(optionTenor, bondTenor, strike, false);
    }

    public SmileSection smileSection(final Period optionTenor, final Period bondTenor) {
        final Date optionDate = optionDateFromTenor(optionTenor);
        return smileSection(optionDate, bondTenor);
    }

    /** the largest length for which the term structure can return vols */
    public abstract Period maxBondTenor();

    /** the largest bondLength for which the term structure can return vols */
    public double maxBondLength() {
        return timeFromReference(referenceDate().add(maxBondTenor()));
    }

    /** the minimum strike for which the term structure can return vols */
    public abstract double minStrike();

    /** the maximum strike for which the term structure can return vols */
    public abstract double maxStrike();

    /** implements the conversion between dates and times */
    public Pair< Double, Double > convertDates(final Date optionDate, final Period bondTenor) {
        final Date end = optionDate.add(bondTenor);
        QL.require(end.gt(optionDate), "negative bond tenor given");
        final double optionTime = timeFromReference(optionDate);
        final double timeLength = dayCounter().yearFraction(optionDate, end);
        return new Pair< Double, Double >(optionTime, timeLength);
    }

    /** the business day convention used for option date calculation */
    public BusinessDayConvention businessDayConvention() {
        return bdc_;
    }

    /** implements the conversion between optionTenors and optionDates */
    public Date optionDateFromTenor(final Period optionTenor) {
        return calendar().advance(referenceDate(), optionTenor, businessDayConvention());
    }

    /** return smile section */
    protected abstract SmileSection smileSectionImpl(double optionTime, double bondLength);

    /** implements the actual volatility calculation in derived classes */
    protected abstract double volatilityImpl(double optionTime, double bondLength, double strike);

    protected double volatilityImpl(final Date optionDate, final Period bondTenor, final double strike) {
        final Pair< Double, Double > p = convertDates(optionDate, bondTenor);
        return volatilityImpl(p.first(), p.second(), strike);
    }

    protected void checkRange(final double optionTime, final double bondLength, final double k,
            final boolean extrapolate) {
        super.checkRange(optionTime, extrapolate);
        QL.require(bondLength >= 0.0, "negative bondLength given");
        QL.require(extrapolate || allowsExtrapolation() || bondLength <= maxBondLength(),
                "bondLength is past max curve bondLength");
        QL.require(extrapolate || allowsExtrapolation() || (k >= minStrike() && k <= maxStrike()),
                "strike is outside the curve domain");
    }

    protected void checkRange(final Date optionDate, final Period bondTenor, final double k,
            final boolean extrapolate) {
        super.checkRange(timeFromReference(optionDate), extrapolate);
        QL.require(bondTenor.length() > 0, "negative bond tenor given");
        QL.require(extrapolate || allowsExtrapolation() || bondTenor.le(maxBondTenor()),
                "bond tenor is past max tenor");
        QL.require(extrapolate || allowsExtrapolation() || (k >= minStrike() && k <= maxStrike()),
                "strike is outside the curve domain");
    }
}
