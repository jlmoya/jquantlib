/*
 Copyright (C) 2026 Jose Moya

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

package org.jquantlib.indexes.ibor;

import org.jquantlib.QL;
import org.jquantlib.currencies.Currency;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * LIBOR-like index that allows specifying custom calendars for value and
 * maturity date calculations.
 * <ul>
 *   <li>{@code valueDate()} advances on the {@code valueCalendar} and adjusts
 *       on the {@code maturityCalendar}.</li>
 *   <li>{@code maturityDate()} advances on the {@code maturityCalendar}.</li>
 *   <li>{@code fixingDate()} goes back on the {@code valueCalendar}.</li>
 * </ul>
 *
 * <p>Phase 5e.5b-CFC-d-14: ported from C++ v1.42.1
 * ql/indexes/ibor/custom.{hpp,cpp}.
 *
 * @author Jose Moya
 */
public class CustomIborIndex extends IborIndex {

    private final Calendar valueCalendar;
    private final Calendar maturityCalendar;

    public CustomIborIndex(final String familyName,
                           final Period tenor,
                           final int settlementDays,
                           final Currency currency,
                           final Calendar fixingCalendar,
                           final Calendar valueCalendar,
                           final Calendar maturityCalendar,
                           final BusinessDayConvention convention,
                           final boolean endOfMonth,
                           final DayCounter dayCounter,
                           final Handle<YieldTermStructure> h) {
        super(familyName, tenor, settlementDays, currency, fixingCalendar,
                convention, endOfMonth, dayCounter, h);
        this.valueCalendar = valueCalendar;
        this.maturityCalendar = maturityCalendar;
    }

    public CustomIborIndex(final String familyName,
                           final Period tenor,
                           final int settlementDays,
                           final Currency currency,
                           final Calendar fixingCalendar,
                           final Calendar valueCalendar,
                           final Calendar maturityCalendar,
                           final BusinessDayConvention convention,
                           final boolean endOfMonth,
                           final DayCounter dayCounter) {
        this(familyName, tenor, settlementDays, currency, fixingCalendar,
                valueCalendar, maturityCalendar, convention, endOfMonth,
                dayCounter, new Handle<YieldTermStructure>());
    }

    //
    // InterestRateIndex overrides
    //

    /**
     * Mirrors C++ v1.42.1 ql/indexes/ibor/custom.cpp:23-27.
     * Walks back {@code fixingDays} on the {@code valueCalendar}, then snaps
     * the result to a {@code fixingCalendar} business day with
     * {@link BusinessDayConvention#Preceding}.
     */
    @Override
    public Date fixingDate(final Date valueDate) {
        final Date fixing = valueCalendar.advance(valueDate, -fixingDays(), TimeUnit.Days);
        return fixingCalendar().adjust(fixing, BusinessDayConvention.Preceding);
    }

    /**
     * Mirrors C++ v1.42.1 ql/indexes/ibor/custom.cpp:29-36.
     * Advances forward {@code fixingDays} on the {@code valueCalendar}, then
     * adjusts onto a {@code maturityCalendar} business day (Following).
     */
    @Override
    public Date valueDate(final Date fixingDate) {
        QL.require(isValidFixingDate(fixingDate),
                "Fixing date " + fixingDate + " is not valid");
        final Date d = valueCalendar.advance(fixingDate, fixingDays(), TimeUnit.Days);
        return maturityCalendar.adjust(d);
    }

    /**
     * Mirrors C++ v1.42.1 ql/indexes/ibor/custom.cpp:38-41.
     * Advances {@code tenor} on the {@code maturityCalendar} using the
     * configured business-day convention and end-of-month flag.
     */
    @Override
    public Date maturityDate(final Date valueDate) {
        return maturityCalendar.advance(valueDate, tenor(), businessDayConvention(),
                endOfMonth());
    }

    //
    // IborIndex overrides
    //

    /**
     * Mirrors C++ v1.42.1 ql/indexes/ibor/custom.cpp:43-49.
     */
    @Override
    public Handle<IborIndex> clone(final Handle<YieldTermStructure> h) {
        final CustomIborIndex clone = new CustomIborIndex(
                familyName(),
                tenor(),
                fixingDays(),
                currency(),
                fixingCalendar(),
                valueCalendar,
                maturityCalendar,
                businessDayConvention(),
                endOfMonth(),
                dayCounter(),
                h);
        return new Handle<IborIndex>(clone);
    }

    //
    // accessors
    //

    public Calendar valueCalendar() {
        return valueCalendar;
    }

    public Calendar maturityCalendar() {
        return maturityCalendar;
    }
}
