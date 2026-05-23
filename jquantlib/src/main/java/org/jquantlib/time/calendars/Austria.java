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
import static org.jquantlib.time.Month.May;
import static org.jquantlib.time.Month.November;
import static org.jquantlib.time.Month.October;

import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Weekday;

/**
 * Austrian calendars.
 * <p>
 * Faithful port of {@code ql/time/calendars/austria.{hpp,cpp}} from QuantLib
 * v1.42.1 @ {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Public holidays:
 * <ul>
 * <li>Saturdays</li>
 * <li>Sundays</li>
 * <li>New Year's Day, January 1st</li>
 * <li>Epiphany, January 6th</li>
 * <li>Easter Monday</li>
 * <li>Ascension Thursday</li>
 * <li>Whit Monday</li>
 * <li>Corpus Christi</li>
 * <li>Labour Day, May 1st</li>
 * <li>Assumption Day, August 15th</li>
 * <li>National Holiday, October 26th, since 1967</li>
 * <li>All Saints Day, November 1st</li>
 * <li>National Holiday, November 12th, 1919-1934</li>
 * <li>Immaculate Conception Day, December 8th</li>
 * <li>Christmas, December 25th</li>
 * <li>St. Stephen, December 26th</li>
 * </ul>
 *
 * <p>Holidays for the Vienna Stock Exchange (data from
 * <a href="https://www.wienerborse.at/en/trading/trading-information/trading-calendar/">Wiener Börse</a>):
 * <ul>
 * <li>Saturdays</li>
 * <li>Sundays</li>
 * <li>New Year's Day, January 1st</li>
 * <li>Good Friday</li>
 * <li>Easter Monday</li>
 * <li>Whit Monday</li>
 * <li>Labour Day, May 1st</li>
 * <li>National Holiday, October 26th, since 1967</li>
 * <li>National Holiday, November 12th, 1919-1934</li>
 * <li>Christmas Eve, December 24th</li>
 * <li>Christmas, December 25th</li>
 * <li>St. Stephen, December 26th</li>
 * <li>Exchange Holiday, December 31st</li>
 * </ul>
 *
 * @author Jose Moya
 */
public class Austria extends Calendar {

    public Austria() {
        this(Market.Settlement);
    }

    public Austria(final Market market) {
        impl = switch (market) {
            case Settlement -> new SettlementImpl();
            case Exchange -> new ExchangeImpl();
        };
        if (impl == null) {
            throw new LibraryException(UNKNOWN_MARKET);
        }
    }

    /**
     * Austrian calendars.
     */
    public enum Market {
        /** generic settlement calendar */
        Settlement,
        /** Vienna stock-exchange calendar */
        Exchange
    }

    private final class SettlementImpl extends WesternImpl {
        @Override
        public String name() {
            return "Austrian settlement";
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
                    // Epiphany
                    || (d == 6 && m == January)
                    // Easter Monday
                    || (dd == em)
                    // Ascension Thursday
                    || (dd == em + 38)
                    // Whit Monday
                    || (dd == em + 49)
                    // Corpus Christi
                    || (dd == em + 59)
                    // Labour Day
                    || (d == 1 && m == May)
                    // Assumption
                    || (d == 15 && m == August)
                    // National Holiday since 1967
                    || (d == 26 && m == October && y >= 1967)
                    // National Holiday 1919-1934
                    || (d == 12 && m == November && y >= 1919 && y <= 1934)
                    // All Saints' Day
                    || (d == 1 && m == November)
                    // Immaculate Conception
                    || (d == 8 && m == December)
                    // Christmas
                    || (d == 25 && m == December)
                    // St. Stephen
                    || (d == 26 && m == December)) {
                return false;
            }
            return true;
        }
    }

    private final class ExchangeImpl extends WesternImpl {
        @Override
        public String name() {
            return "Vienna stock exchange";
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
                    // Good Friday
                    || (dd == em - 3)
                    // Easter Monday
                    || (dd == em)
                    // Whit Monday
                    || (dd == em + 49)
                    // Labour Day
                    || (d == 1 && m == May)
                    // National Holiday since 1967
                    || (d == 26 && m == October && y >= 1967)
                    // National Holiday 1919-1934
                    || (d == 12 && m == November && y >= 1919 && y <= 1934)
                    // Christmas Eve
                    || (d == 24 && m == December)
                    // Christmas
                    || (d == 25 && m == December)
                    // St. Stephen
                    || (d == 26 && m == December)
                    // Exchange Holiday
                    || (d == 31 && m == December)) {
                return false;
            }
            return true;
        }
    }
}
