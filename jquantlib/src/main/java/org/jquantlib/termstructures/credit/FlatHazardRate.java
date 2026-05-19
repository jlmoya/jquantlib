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
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2008 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.credit;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.annotation.Natural;
import org.jquantlib.lang.annotation.Rate;
import org.jquantlib.lang.annotation.Time;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Flat hazard-rate curve — Java port of QuantLib v1.42.1 {@code FlatHazardRate}
 * ({@code ql/termstructures/credit/flathazardrate.{hpp,cpp}}).
 *
 * <p>Concrete {@link HazardRateStructure} with a single, time-invariant hazard
 * rate {@code h}. Survival probability is the closed form {@code S(t) = exp(-h t)}.
 */
public class FlatHazardRate extends HazardRateStructure {

    private final Handle< Quote > hazardRate;

    //
    // public constructors
    //

    public FlatHazardRate(final Date referenceDate, final Handle< Quote > hazardRate, final DayCounter dayCounter) {
        super(referenceDate, new NullCalendar(), dayCounter);
        this.hazardRate = hazardRate;
        this.hazardRate.addObserver(this);
    }

    public FlatHazardRate(final Date referenceDate, final @Rate double hazardRate, final DayCounter dayCounter) {
        super(referenceDate, new NullCalendar(), dayCounter);
        this.hazardRate = new Handle< Quote >(new SimpleQuote(hazardRate));
    }

    public FlatHazardRate(final @Natural int settlementDays, final Calendar calendar, final Handle< Quote > hazardRate,
            final DayCounter dayCounter) {
        super(settlementDays, calendar, dayCounter);
        this.hazardRate = hazardRate;
        this.hazardRate.addObserver(this);
    }

    public FlatHazardRate(final @Natural int settlementDays, final Calendar calendar, final @Rate double hazardRate,
            final DayCounter dayCounter) {
        super(settlementDays, calendar, dayCounter);
        this.hazardRate = new Handle< Quote >(new SimpleQuote(hazardRate));
    }

    //
    // TermStructure
    //

    @Override
    public Date maxDate() {
        return Date.maxDate();
    }

    //
    // HazardRateStructure
    //

    @Override
    protected @Rate double hazardRateImpl(final @Time double t) {
        return hazardRate.currentLink().value();
    }

    //
    // DefaultProbabilityTermStructure
    //

    @Override
    protected double survivalProbabilityImpl(final @Time double t) {
        return Math.exp(-hazardRate.currentLink().value() * t);
    }
}
