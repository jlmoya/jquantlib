/*
 Copyright (C) 2008 Richard Gomes
 Copyright (C) 2010 Neel Sheyal

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

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.Futures;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.RateHelper;
import org.jquantlib.time.*;

/**
 * FuturesRateHelper
 *
 * @author Srinivas Hasti
 * @author Neel Sheyal
 *
 * Rate helper for bootstrapping over IborIndex futures prices {@link <BootstrapHelper>} {@link <RateHelper>}
 */

public class FuturesRateHelper extends RateHelper {

    private static final String NOT_A_VALID_IMM_DATE = "not a valid IMM date";
    private static final String TERMSTRUCT_NOT_SET = "term structure not set";
    //
    // private fields
    // 
    private final double yearFraction;
    private final Handle< Quote > convAdj;

    //
    // public constructors
    //

    /**
     * @param Handle<Quote>         price
     * @param Date                  immDate
     * @param int                   lengthInMonths
     * @param Calendar              calendar
     * @param BusinessDayConvention convention
     * @param boolean               endOfMonth
     * @param DayCounter            dayCounter
     */
    public FuturesRateHelper(final Handle< Quote > price, final Date immDate, final/* Natural */int lengthInMonths,
            final Calendar calendar, final BusinessDayConvention convention, final boolean endOfMonth,
            final DayCounter dayCounter) {
        this(price, immDate, lengthInMonths, calendar, convention, endOfMonth, dayCounter, new Handle< Quote >());
    }

    /**
     * @param Handle                <Quote> price
     * @param Date                  immDate
     * @param int                   lengthInMonths
     * @param Calendar              calendar
     * @param BusinessDayConvention convention
     * @param boolean               endOfMonth
     * @param DayCounter            dayCounter
     * @param Handle                <Quote> convAdj
     */
    public FuturesRateHelper(final Handle< Quote > price, final Date immDate, final/* Natural */int lengthInMonths,
            final Calendar calendar, final BusinessDayConvention convention, final boolean endOfMonth,
            final DayCounter dayCounter, final Handle< Quote > convAdj) {
        super(price);
        this.convAdj = convAdj;

        QL.require(IMM.isIMMdate(immDate, false), NOT_A_VALID_IMM_DATE);

        earliestDate = immDate;
        latestDate = calendar.advance(immDate, new Period(lengthInMonths, TimeUnit.Months), convention, endOfMonth);
        yearFraction = dayCounter.yearFraction(earliestDate, latestDate);

        convAdj.addObserver(this);
    }

    /**
     * @param Handle                <Quote> price
     * @param Date                  immDate
     * @param int                   lengthInMonths
     * @param Calendar              calendar
     * @param BusinessDayConvention convention
     * @param boolean               endOfMonth
     * @param DayCounter            dayCounter
     */
    public FuturesRateHelper(final/* Real */double price, final Date immDate, final/* Natural */ int lengthInMonths,
            final Calendar calendar, final BusinessDayConvention convention, final boolean endOfMonth,
            final DayCounter dayCounter) {
        this(price, immDate, lengthInMonths, calendar, convention, endOfMonth, dayCounter, 0.0);
    }

    /**
     * @param double                price
     * @param Date                  immDate
     * @param int                   lengthInMonths
     * @param Calendar              calendar
     * @param BusinessDayConvention convention
     * @param boolean               endOfMonth
     * @param DayCounter            dayCounter
     * @param double                convAdj
     */
    public FuturesRateHelper(final/* Real */double price, final Date immDate, final/* Natural */int lengthInMonths,
            final Calendar calendar, final BusinessDayConvention convention, final boolean endOfMonth,
            final DayCounter dayCounter, final/* Rate */double convAdj) {
        super(price);
        this.convAdj = new Handle< Quote >(new SimpleQuote(convAdj));

        QL.require(IMM.isIMMdate(immDate, false), NOT_A_VALID_IMM_DATE);

        earliestDate = immDate;
        latestDate = calendar.advance(immDate, new Period(lengthInMonths, TimeUnit.Months), convention, endOfMonth);
        yearFraction = dayCounter.yearFraction(earliestDate, latestDate);
    }

    /**
     * @param Handle    <Quote> price
     * @param Date      immDate
     * @param IborIndex index
     */
    public FuturesRateHelper(final Handle< Quote > price, final Date immDate, final IborIndex i) {
        this(price, immDate, i, new Handle< Quote >());
    }

    /**
     * @param Handle    <Quote> price
     * @param Date      immDate
     * @param IborIndex index
     * @param Handle    <Quote> convAdj
     */
    public FuturesRateHelper(final Handle< Quote > price, final Date immDate, final IborIndex i,
            final Handle< Quote > convAdj) {

        super(price);
        this.convAdj = convAdj;

        QL.require(IMM.isIMMdate(immDate, false), NOT_A_VALID_IMM_DATE);

        this.earliestDate = immDate;
        this.latestDate = i.fixingCalendar().advance(immDate, i.tenor(), i.businessDayConvention());
        this.yearFraction = i.dayCounter().yearFraction(this.earliestDate, this.latestDate);

        convAdj.addObserver(this);
    }

    /**
     * @param double    price
     * @param Date      immDate
     * @param IborIndex index
     */
    public FuturesRateHelper(final /* Real */ double price, final Date immDate, final IborIndex i) {
        this(price, immDate, i, 0.0);
    }

    /**
     * @param double    price
     * @param Date      immDate
     * @param IborIndex index
     * @param double    convAdj
     *
     */
    public FuturesRateHelper(final /* Real */ double price, final Date immDate, final IborIndex i,
            final /* Rate */ double convAdj) {
        super(price);
        this.convAdj = new Handle< Quote >(new SimpleQuote(convAdj));

        QL.require(IMM.isIMMdate(immDate, false), NOT_A_VALID_IMM_DATE);

        earliestDate = immDate;
        final Calendar cal = i.fixingCalendar();
        latestDate = cal.advance(immDate, i.tenor(), i.businessDayConvention());
        yearFraction = i.dayCounter().yearFraction(earliestDate, latestDate);
    }

    //
    // Futures.Type-aware ctors — mirror C++ v1.42.1 ratehelpers.hpp:53-72
    // + ratehelpers.cpp:69-154. The {@code type} argument controls the start-date
    // validation: Futures.IMM enforces isIMMdate, Futures.ASX enforces isASXdate,
    // Futures.Custom accepts any date (caller-supplied schedule).
    //

    /**
     * Mirrors C++ ratehelpers.hpp:53-61 — {@code (price, startDate, lengthInMonths, calendar,
     * convention, endOfMonth, dayCounter, convAdj, type)}. With {@code type=Custom}, no
     * IMM/ASX date check is performed.
     */
    public FuturesRateHelper(final /* Real */ double price, final Date startDate,
            final /* Natural */ int lengthInMonths,
            final Calendar calendar, final BusinessDayConvention convention, final boolean endOfMonth,
            final DayCounter dayCounter, final /* Rate */ double convAdj, final Futures.Type type) {
        super(price);
        this.convAdj = new Handle< Quote >(new SimpleQuote(convAdj));

        checkStartDate(startDate, type);

        earliestDate = startDate;
        latestDate = calendar.advance(startDate, new Period(lengthInMonths, TimeUnit.Months), convention, endOfMonth);
        yearFraction = dayCounter.yearFraction(earliestDate, latestDate);
    }

    /**
     * Mirrors C++ ratehelpers.hpp:62-67 — {@code (price, startDate, endDate, dayCounter, convAdj, type)}.
     * With {@code type=Custom}, {@code maturityDate = endDate} directly; {@code endDate=null}
     * is not supported in this Java overload (use the {@code IborIndex} ctor instead).
     */
    public FuturesRateHelper(final /* Real */ double price, final Date startDate, final Date endDate,
            final DayCounter dayCounter, final /* Rate */ double convAdj, final Futures.Type type) {
        super(price);
        this.convAdj = new Handle< Quote >(new SimpleQuote(convAdj));

        checkStartDate(startDate, type);

        switch (type) {
        case IMM:
            // C++ supports endDate=Date(); we require an explicit endDate here for simplicity.
            QL.require(endDate != null && endDate.gt(startDate),
                    "end date (" + endDate + ") must be greater than start date (" + startDate + ")");
            latestDate = endDate;
            break;
        case ASX:
            QL.require(endDate != null && endDate.gt(startDate),
                    "end date (" + endDate + ") must be greater than start date (" + startDate + ")");
            latestDate = endDate;
            break;
        case Custom:
            QL.require(endDate != null && endDate.gt(startDate),
                    "end date (" + endDate + ") must be greater than start date (" + startDate + ")");
            latestDate = endDate;
            break;
        default:
            throw new IllegalArgumentException("unsupported futures type (" + type + ")");
        }
        earliestDate = startDate;
        yearFraction = dayCounter.yearFraction(earliestDate, latestDate);
    }

    /**
     * Mirrors C++ ratehelpers.hpp:68-72 — {@code (price, startDate, iborIndex, convAdj, type)}.
     */
    public FuturesRateHelper(final /* Real */ double price, final Date startDate, final IborIndex index,
            final /* Rate */ double convAdj, final Futures.Type type) {
        super(price);
        this.convAdj = new Handle< Quote >(new SimpleQuote(convAdj));

        checkStartDate(startDate, type);

        earliestDate = startDate;
        final Calendar cal = index.fixingCalendar();
        latestDate = cal.advance(startDate, index.tenor(), index.businessDayConvention());
        yearFraction = index.dayCounter().yearFraction(earliestDate, latestDate);
    }

    /** Mirrors C++ ratehelpers.cpp:45-58 namespace-local CheckDate(). */
    private static void checkStartDate(final Date startDate, final Futures.Type type) {
        switch (type) {
        case IMM:
            QL.require(IMM.isIMMdate(startDate, false), startDate + " is not a valid IMM date");
            break;
        case ASX:
            QL.require(ASX.isASXdate(startDate, false), startDate + " is not a valid ASX date");
            break;
        case Custom:
            break;
        default:
            throw new IllegalArgumentException("unknown futures type (" + type + ")");
        }
    }

    /**
     * /* Implements the abstract method defined in {@link <BootstrapHelper>} Returns the futures value of the
     * instrument
     *
     * @return double
     */
    @Override
    public/* Real */double impliedQuote() {

        QL.require(termStructure != null, TERMSTRUCT_NOT_SET);
        final/* Rate */ double forwardRate =
                termStructure.discount(earliestDate) / (termStructure.discount(latestDate) - 1.0) / yearFraction;
        final/* Rate */ double convA = this.convAdj.empty() ? 0.0 : this.convAdj.currentLink().value();
        QL.ensure(convA >= 0.0, "Negative (" + convAdj + ") futures convexity adjustment");
        final/* Rate */ double futureRate = forwardRate + convA;
        return 100.0 * (1.0 - futureRate);
    }

    //
    // public methods
    //

    /**
     * @return double value of the adjusted convexity
     */
    public /* Real */ double getConvexityAdjustment() {
        return convAdj.empty() ? 0.0 : convAdj.currentLink().value();
    }

}
