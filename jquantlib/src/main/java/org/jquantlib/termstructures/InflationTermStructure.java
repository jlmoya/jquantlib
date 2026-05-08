/*
 Copyright (C) 2011 Tim Blackler

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
 Copyright (C) 2004, 2005, 2006, 2007 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/
package org.jquantlib.termstructures;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.annotation.Natural;
import org.jquantlib.lang.annotation.Rate;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.inflation.Seasonality;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.util.Pair;

/**
 * 
 * Base Class for inflation term structures
 * 
 * @author Tim Blackler
 *
 */

public abstract class InflationTermStructure extends AbstractTermStructure {
	
    protected Handle<YieldTermStructure> nominalTermStructure;

    // connection with base index:
    //  lag to base date
    //  index
    //  whether or not to connect with the index at the short end
    //  (don't if you have no index set up)
    protected Period lag;
    protected Frequency frequency;
    protected @Rate double  baseRate;

    /**
     * Optional seasonality correction. When non-null, derived classes
     * (Zero/YoY) apply it in their {@code zeroRate}/{@code yoyRate} accessor
     * after computing the raw rate. Mirrors C++ v1.42.1
     * {@code InflationTermStructure::seasonality_}.
     *
     * <p>Phase 2q L1 Track C addition.
     */
    protected Seasonality seasonality_;
    
    public InflationTermStructure(final Period lag,
    							  final Frequency frequency,
    							  final @Rate double baseRate,
    							  final Handle<YieldTermStructure> yTS) {

    	this(lag, frequency, baseRate, yTS, new Actual365Fixed());
 	
    }

    public InflationTermStructure(final Period lag,
			  final Frequency frequency,
			  final @Rate double baseRate,
			  final Handle<YieldTermStructure> yTS,
			  final DayCounter dayCounter) {
    	
    	super(dayCounter);
    	this.nominalTermStructure = yTS;
    	this.lag = lag;
    	this.frequency = frequency;
    	this.baseRate = baseRate;
    	
    	this.nominalTermStructure.addObserver(this); 	
    }

    public InflationTermStructure(final Date referenceDate,
    		  final Period lag,
			  final Frequency frequency,
			  final @Rate double baseRate,
			  final Handle<YieldTermStructure> yTS) {

    	this(referenceDate, lag, frequency, baseRate, yTS, new NullCalendar() ,new Actual365Fixed());
    }

    public InflationTermStructure(final Date referenceDate,
  		  	  final Period lag,
			  final Frequency frequency,
			  final @Rate double baseRate,
			  final Handle<YieldTermStructure> yTS,
			  final Calendar calendar,
			  final DayCounter dayCounter) {

    	super(referenceDate, calendar, dayCounter);
    	this.nominalTermStructure = yTS;
    	this.lag = lag;
    	this.frequency = frequency;
    	this.baseRate = baseRate;
    	
    	this.nominalTermStructure.addObserver(this);
    }

    public InflationTermStructure( final @Natural int settlementDays,
			  final Calendar calendar,
			  final Period lag,
			  final Frequency frequency,
			  final @Rate double baseRate,
			  final Handle<YieldTermStructure> yTS) {

    	this(settlementDays, calendar, lag, frequency, baseRate, yTS, new Actual365Fixed());

    }

    
    public InflationTermStructure( final @Natural int settlementDays,
    							   final Calendar calendar,
    							   final Period lag,
    							   final Frequency frequency,
    							   final @Rate double baseRate,
    							   final Handle<YieldTermStructure> yTS,
    							   final DayCounter dayCounter) {

    	super(settlementDays, calendar, dayCounter);
    	this.nominalTermStructure = yTS;
    	this.lag = lag;
    	this.frequency = frequency;
    	this.baseRate = baseRate;
    	
    	this.nominalTermStructure.addObserver(this);
    }

    public Period lag() {
    	return lag;
    }

    public Frequency frequency() {
    	return frequency;
    }

    public Handle<YieldTermStructure> nominalTermStructure() {
    	return nominalTermStructure;
    }

    public /*@Rate*/ double baseRate() {
    	return baseRate;
    }

    //
    // Seasonality (Phase 2q L1 Track C). Mirrors C++ v1.42.1
    // InflationTermStructure::{setSeasonality, seasonality, hasSeasonality}.
    //

    /**
     * Install/clear the seasonality correction. Pass {@code null} to clear.
     * Triggers an observer notification.
     */
    public void setSeasonality(final Seasonality seasonality) {
        this.seasonality_ = seasonality;
        if (seasonality_ != null) {
            QL.require(seasonality_.isConsistent(this),
                    "Seasonality inconsistent with inflation term structure");
        }
        update();
    }

    public Seasonality seasonality() {
        return seasonality_;
    }

    public boolean hasSeasonality() {
        return seasonality_ != null;
    }
    
    //! minimum (base) date
    /*! Important in inflation since it starts before nominal
        reference date.
    */
    public Date baseDate() {
    	return new Date(0);
    }

    @Override
    public Date maxDate() {
    	return new Date(0);
    }
    
	// This next part is required for piecewise- constructors
	// because, for inflation, they need more than just the
	// instruments to build the term structure, since the rate at
	// time 0-lag is non-zero, since we deal (effectively) with
	// "forwards".
    protected void setBaseRate (final @Rate double r) {
    	baseRate = r;
    
    }
 
    //! utility function giving the inflation period for a given date
    /**
     * Mirrors C++ v1.42.1 {@code inflationPeriod(const Date&, Frequency)}
     * ({@code termstructures/inflationtermstructure.cpp:163-188}).
     *
     * <p>For sub-annual frequencies the C++ formula is
     * {@code startMonth = month - (month - 1) % nMonths} where
     * {@code nMonths = 12 / frequency}. The earlier Java formula
     * ({@code 6*(month-1)/6 + 1} / {@code 3*(month-1)/3 + 1}) was
     * wrong: for February under Quarterly it yields {@code Feb..Apr},
     * but the inflation period must be the calendar quarter
     * {@code Jan..Mar}. Phase 2t aligns to C++ (testPeriod regression).
     */
    public static Pair<Date,Date> inflationPeriod(final Date date,
    									   final Frequency frequency) {

        Month month = date.month();
        int year = date.year();

        Month startMonth, endMonth;
        switch (frequency) {
          case Annual:
          case Semiannual:
          case EveryFourthMonth:
          case Quarterly:
          case Bimonthly: {
            final int nMonths = 12 / frequency.toInteger();
            final int startMonthValue = month.value() - (month.value() - 1) % nMonths;
            startMonth = Month.valueOf(startMonthValue);
            endMonth = Month.valueOf(startMonthValue + nMonths - 1);
            break;
          }
          case Monthly:
            startMonth = endMonth = month;
            break;
          default:
        	  throw new LibraryException("Frequency not handled: " + frequency);

        };

        Date startDate = new Date(1, startMonth, year);
        Date endDate = Date.endOfMonth(new Date(1, endMonth, year));

        return new Pair<Date,Date>(startDate, endDate);
    }
    

}
