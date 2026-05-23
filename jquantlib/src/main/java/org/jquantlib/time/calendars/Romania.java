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

package org.jquantlib.time.calendars;

import static org.jquantlib.time.Month.August;
import static org.jquantlib.time.Month.December;
import static org.jquantlib.time.Month.January;
import static org.jquantlib.time.Month.June;
import static org.jquantlib.time.Month.May;
import static org.jquantlib.time.Month.November;

import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Weekday;

/**
 * Romanian calendars.
 * <p>
 * Faithful port of {@code ql/time/calendars/romania.{hpp,cpp}} from QuantLib
 * v1.42.1 @ {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Public holidays:
 * <ul>
 * <li>Saturdays</li>
 * <li>Sundays</li>
 * <li>New Year's Day, January 1st</li>
 * <li>Day after New Year's Day, January 2nd</li>
 * <li>Unification Day, January 24th</li>
 * <li>Orthodox Easter (only Sunday and Monday)</li>
 * <li>Labour Day, May 1st</li>
 * <li>Pentecost with Monday (50th and 51st days after the Orthodox Easter)</li>
 * <li>Children's Day, June 1st (since 2017)</li>
 * <li>St Mary's Day, August 15th</li>
 * <li>Feast of St Andrew, November 30th</li>
 * <li>National Day, December 1st</li>
 * <li>Christmas, December 25th</li>
 * <li>2nd Day of Christmas, December 26th</li>
 * </ul>
 *
 * <p>Holidays for the Bucharest Stock Exchange (BVB):
 * all public holidays, plus a few one-off closing days (2014 only).
 *
 * @author Jose Moya
 */
public class Romania extends Calendar {

    public Romania() {
        this(Market.BVB);
    }

    public Romania(final Market market) {
        impl = switch (market) {
            case Public -> new PublicImpl();
            case BVB -> new BVBImpl();
        };
        if (impl == null) {
            throw new LibraryException(UNKNOWN_MARKET);
        }
    }

    public enum Market {
        /** Public holidays */
        Public,
        /** Bucharest stock exchange */
        BVB
    }

    private class PublicImpl extends OrthodoxImpl {
        @Override
        public String name() {
            return "Romania";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth();
            final int dd = date.dayOfYear();
            final Month m = date.month();
            final int y = date.year();
            final int em = easterMonday(y);
            if (isWeekend(w)
                    // New Year's Day
                    || (d == 1 && m == January)
                    // Day after New Year's Day
                    || (d == 2 && m == January)
                    // Unification Day
                    || (d == 24 && m == January)
                    // Orthodox Easter Monday
                    || (dd == em)
                    // Labour Day
                    || (d == 1 && m == May)
                    // Pentecost
                    || (dd == em + 49)
                    // Children's Day (since 2017)
                    || (d == 1 && m == June && y >= 2017)
                    // St Mary's Day
                    || (d == 15 && m == August)
                    // Feast of St Andrew
                    || (d == 30 && m == November)
                    // National Day
                    || (d == 1 && m == December)
                    // Christmas
                    || (d == 25 && m == December)
                    // 2nd Day of Christmas
                    || (d == 26 && m == December)) {
                return false;
            }
            return true;
        }
    }

    private final class BVBImpl extends PublicImpl {
        @Override
        public String name() {
            return "Bucharest stock exchange";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            if (!super.isBusinessDay(date)) {
                return false;
            }
            final int d = date.dayOfMonth();
            final Month m = date.month();
            final int y = date.year();
            // one-off closing days
            if ((d == 24 && m == December && y == 2014)
                    || (d == 31 && m == December && y == 2014)) {
                return false;
            }
            return true;
        }
    }
}
