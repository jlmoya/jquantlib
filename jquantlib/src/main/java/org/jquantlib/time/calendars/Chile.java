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

import static org.jquantlib.time.Month.April;
import static org.jquantlib.time.Month.August;
import static org.jquantlib.time.Month.December;
import static org.jquantlib.time.Month.January;
import static org.jquantlib.time.Month.July;
import static org.jquantlib.time.Month.June;
import static org.jquantlib.time.Month.May;
import static org.jquantlib.time.Month.November;
import static org.jquantlib.time.Month.October;
import static org.jquantlib.time.Month.September;
import static org.jquantlib.time.Weekday.Friday;
import static org.jquantlib.time.Weekday.Monday;
import static org.jquantlib.time.Weekday.Tuesday;
import static org.jquantlib.time.Weekday.Wednesday;

import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Weekday;

/**
 * Chilean calendars.
 * <p>
 * Faithful port of {@code ql/time/calendars/chile.{hpp,cpp}} from QuantLib
 * v1.42.1 @ {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Holidays for the Santiago Stock Exchange
 * (data from <a href="https://en.wikipedia.org/wiki/Public_holidays_in_Chile">Wikipedia</a>):
 * <ul>
 * <li>Saturdays</li>
 * <li>Sundays</li>
 * <li>New Year's Day, January 1st</li>
 * <li>January 2nd, when falling on a Monday (since 2017)</li>
 * <li>Good Friday</li>
 * <li>Easter Saturday</li>
 * <li>Labour Day, May 1st</li>
 * <li>Navy Day, May 21st</li>
 * <li>Day of Aboriginal People, around June 21st (observed on each Winter Solstice) (since 2021)</li>
 * <li>Saint Peter and Saint Paul, June 29th (moved to the nearest Monday if it falls on a weekday)</li>
 * <li>Our Lady of Mount Carmel, July 16th</li>
 * <li>Assumption Day, August 15th</li>
 * <li>Independence Day, September 18th (also the 17th if the latter falls on a Monday or Friday)</li>
 * <li>Army Day, September 19th (also the 20th if the latter falls on a Friday)</li>
 * <li>Discovery of Two Worlds, October 12th (moved to the nearest Monday if it falls on a weekday)</li>
 * <li>Reformation Day, October 31st (since 2008; moved to the preceding Friday if it falls on a Tuesday,
 *     or to the following Friday if it falls on a Wednesday)</li>
 * <li>All Saints' Day, November 1st</li>
 * <li>Immaculate Conception, December 8th</li>
 * <li>Christmas Day, December 25th</li>
 * <li>New Year's Eve, December 31st</li>
 * </ul>
 *
 * @author Jose Moya
 */
public class Chile extends Calendar {

    public Chile() {
        this(Market.SSE);
    }

    public Chile(final Market market) {
        // C++ ignores the market parameter and always uses SseImpl, since
        // SSE is currently the only Market value. We mirror that here.
        impl = switch (market) {
            case SSE -> new SseImpl();
        };
        if (impl == null) {
            throw new LibraryException(UNKNOWN_MARKET);
        }
    }

    public enum Market {
        /** Santiago Stock Exchange */
        SSE
    }

    /**
     * Aboriginal People's Day — celebrated on the Winter Solstice day,
     * except in 2021 when it was the day after.
     *
     * <p>Faithful port of the C++ {@code aboriginalPeopleDay[]} lookup table
     * spanning 2021-2199.
     */
    private static final int[] ABORIGINAL_PEOPLE_DAY = {
        21, 21, 21, 20, 20, 21, 21, 20, 20,   // 2021-2029
        21, 21, 20, 20, 21, 21, 20, 20, 21, 21,   // 2030-2039
        20, 20, 21, 21, 20, 20, 21, 21, 20, 20,   // 2040-2049
        20, 21, 20, 20, 20, 21, 20, 20, 20, 21,   // 2050-2059
        20, 20, 20, 21, 20, 20, 20, 21, 20, 20,   // 2060-2069
        20, 21, 20, 20, 20, 21, 20, 20, 20, 20,   // 2070-2079
        20, 20, 20, 20, 20, 20, 20, 20, 20, 20,   // 2080-2089
        20, 20, 20, 20, 20, 20, 20, 20, 20, 20,   // 2090-2099
        21, 21, 21, 21, 21, 21, 21, 21, 20, 21,   // 2100-2109
        21, 21, 20, 21, 21, 21, 20, 21, 21, 21,   // 2110-2119
        20, 21, 21, 21, 20, 21, 21, 21, 20, 21,   // 2120-2129
        21, 21, 20, 21, 21, 21, 20, 20, 21, 21,   // 2130-2139
        20, 20, 21, 21, 20, 20, 21, 21, 20, 20,   // 2140-2149
        21, 21, 20, 20, 21, 21, 20, 20, 21, 21,   // 2150-2159
        20, 20, 21, 21, 20, 20, 21, 21, 20, 20,   // 2160-2169
        20, 21, 20, 20, 20, 21, 20, 20, 20, 21,   // 2170-2179
        20, 20, 20, 21, 20, 20, 20, 21, 20, 20,   // 2180-2189
        20, 21, 20, 20, 20, 21, 20, 20, 20, 20    // 2190-2199
    };

    private static boolean isAboriginalPeopleDay(final int d, final Month m, final int y) {
        if (m != June || y < 2021 || y > 2199) {
            return false;
        }
        return d == ABORIGINAL_PEOPLE_DAY[y - 2021];
    }

    private final class SseImpl extends WesternImpl {
        @Override
        public String name() {
            return "Santiago Stock Exchange";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth();
            final Month m = date.month();
            final int y = date.year();
            final int dd = date.dayOfYear();
            final int em = easterMonday(y);

            if (isWeekend(w)
                    // New Year's Day
                    || (d == 1 && m == January)
                    || (d == 2 && m == January && w == Monday && y > 2016)
                    // Papal visit in 2018
                    || (d == 16 && m == January && y == 2018)
                    // Good Friday
                    || (dd == em - 3)
                    // Easter Saturday
                    || (dd == em - 2)
                    // Census Day in 2017
                    || (d == 19 && m == April && y == 2017)
                    // Labour Day
                    || (d == 1 && m == May)
                    // Navy Day
                    || (d == 21 && m == May)
                    // Day of Aboriginal People
                    || isAboriginalPeopleDay(d, m, y)
                    // St. Peter and St. Paul
                    || (d >= 26 && d <= 29 && m == June && w == Monday)
                    || (d == 2 && m == July && w == Monday)
                    // Our Lady of Mount Carmel
                    || (d == 16 && m == July)
                    // Assumption Day
                    || (d == 15 && m == August)
                    // Independence Day
                    || (d == 16 && m == September && y == 2022)
                    || (d == 17 && m == September
                            && ((w == Monday && y >= 2007) || (w == Friday && y > 2016)))
                    || (d == 18 && m == September)
                    // Army Day
                    || (d == 19 && m == September)
                    || (d == 20 && m == September && w == Friday && y >= 2007)
                    // Discovery of Two Worlds
                    || (d >= 9 && d <= 12 && m == October && w == Monday)
                    || (d == 15 && m == October && w == Monday)
                    // Reformation Day
                    || (((d == 27 && m == October && w == Friday)
                            || (d == 31 && m == October && w != Tuesday && w != Wednesday)
                            || (d == 2 && m == November && w == Friday)) && y >= 2008)
                    // All Saints' Day
                    || (d == 1 && m == November)
                    // Immaculate Conception
                    || (d == 8 && m == December)
                    // Christmas Day
                    || (d == 25 && m == December)
                    // New Year's Eve
                    || (d == 31 && m == December)) {
                return false;
            }
            return true;
        }
    }
}
