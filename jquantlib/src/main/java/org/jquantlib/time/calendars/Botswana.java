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

import static org.jquantlib.time.Month.December;
import static org.jquantlib.time.Month.January;
import static org.jquantlib.time.Month.July;
import static org.jquantlib.time.Month.May;
import static org.jquantlib.time.Month.October;
import static org.jquantlib.time.Month.September;
import static org.jquantlib.time.Weekday.Monday;
import static org.jquantlib.time.Weekday.Tuesday;

import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Weekday;

/**
 * Botswana calendar.
 * <p>
 * Faithful port of {@code ql/time/calendars/botswana.{hpp,cpp}} from QuantLib
 * v1.42.1 @ {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Holidays from the Botswana
 * <a href="http://www.ilo.org/dyn/travail/docs/1766/Public%20Holidays%20Act.pdf">Public Holidays Act</a>.
 * The days named in the Schedule shall be public holidays within Botswana:
 * Provided that
 * <ul>
 *   <li>when any of the said days fall on a Sunday the following Monday shall be observed as a public holiday;</li>
 *   <li>if 2nd January, 1st October or Boxing Day falls on a Monday, the following Tuesday shall be observed as a public holiday;</li>
 *   <li>when Botswana Day referred to in the Schedule falls on a Saturday, the next following Monday shall be observed as a public holiday.</li>
 * </ul>
 * <ul>
 * <li>Saturdays</li>
 * <li>Sundays</li>
 * <li>New Year's Day, January 1st</li>
 * <li>Good Friday</li>
 * <li>Easter Monday</li>
 * <li>Labour Day, May 1st</li>
 * <li>Ascension</li>
 * <li>Sir Seretse Khama Day, July 1st</li>
 * <li>Presidents' Day</li>
 * <li>Independence Day, September 30th</li>
 * <li>Botswana Day, October 1st</li>
 * <li>Christmas, December 25th</li>
 * <li>Boxing Day, December 26th</li>
 * </ul>
 *
 * @author Jose Moya
 */
public class Botswana extends Calendar {

    public Botswana() {
        impl = new Impl();
    }

    private final class Impl extends WesternImpl {
        @Override
        public String name() {
            return "Botswana";
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
                    // New Year's Day (possibly moved to Monday or Tuesday)
                    || ((d == 1 || (d == 2 && w == Monday) || (d == 3 && w == Tuesday))
                            && m == January)
                    // Good Friday
                    || (dd == em - 3)
                    // Easter Monday
                    || (dd == em)
                    // Labour Day, May 1st (possibly moved to Monday)
                    || ((d == 1 || (d == 2 && w == Monday)) && m == May)
                    // Ascension
                    || (dd == em + 38)
                    // Sir Seretse Khama Day, July 1st (possibly moved to Monday)
                    || ((d == 1 || (d == 2 && w == Monday)) && m == July)
                    // Presidents' Day (third Monday of July)
                    || ((d >= 15 && d <= 21) && w == Monday && m == July)
                    // Independence Day, September 30th (possibly moved to Monday)
                    || ((d == 30 && m == September)
                            || (d == 1 && w == Monday && m == October))
                    // Botswana Day, October 1st (possibly moved to Monday or Tuesday)
                    || ((d == 1 || (d == 2 && w == Monday) || (d == 3 && w == Tuesday))
                            && m == October)
                    // Christmas
                    || (d == 25 && m == December)
                    // Boxing Day (possibly moved to Monday)
                    || ((d == 26 || (d == 27 && w == Monday)) && m == December)) {
                return false;
            }
            return true;
        }
    }
}
