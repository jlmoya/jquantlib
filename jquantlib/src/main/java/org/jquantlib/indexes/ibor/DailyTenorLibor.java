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
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2003, 2004, 2005, 2006 StatPro Italia srl

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

package org.jquantlib.indexes.ibor;

import org.jquantlib.QL;
import org.jquantlib.currencies.Currency;
import org.jquantlib.currencies.Europe.EURCurrency;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.JointCalendar;
import org.jquantlib.time.calendars.JointCalendar.JointCalendarRule;
import org.jquantlib.time.calendars.UnitedKingdom;
import org.jquantlib.time.calendars.UnitedKingdom.Market;

/**
 * base class for all BBA LIBOR indexes but the EUR, O/N, and S/N ones
 * <p>
 * LIBOR fixed by BBA.
 *
 * @see <a
 * href="http://www.bba.org.uk/bba/jsp/polopoly.jsp?d=225&a=1414">http://www.bba.org.uk/bba/jsp/polopoly.jsp?d=225&a=1414</a>
 */
public class DailyTenorLibor extends IborIndex {

    public DailyTenorLibor(final String familyName, final int settlementDays, final Currency currency,
            final Calendar financialCenterCalendar, final DayCounter dayCounter, final Handle< YieldTermStructure > h) {
        // align(indexes.ibor): match C++ v1.42.1 libor.cpp DailyTenorLibor —
        // fixingCalendar must be JointCalendar(UK::Exchange,
        // financialCenterCalendar, JoinHolidays). Java port previously
        // passed `new Target()` (Europe TARGET) instead of the
        // financialCenterCalendar argument, AND used JoinBusinessDays
        // (open if either is open) instead of JoinHolidays (closed if
        // either is closed). Per BBA spec quoted in C++:
        //   "no o/n or s/n fixings (as the case may be) will take place
        //    when the principal centre of the currency concerned is
        //    closed but London is open on the fixing day."
        // — i.e. the joint calendar must be the strict (holiday-merging)
        // intersection of London and the financial-center calendar.
        super(familyName, new Period(1, TimeUnit.Days), settlementDays, currency,
                new JointCalendar(new UnitedKingdom(Market.Exchange), financialCenterCalendar,
                        JointCalendarRule.JoinHolidays), liborConvention(new Period(1, TimeUnit.Days)),
                liborEOM(new Period(1, TimeUnit.Days)), dayCounter, h);

        QL.require(!currency.eq(new EURCurrency()), "for EUR Libor dedicated EurLibor constructor must be used");

    }
}
