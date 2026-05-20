/*
 Copyright (c)  Q Boiler

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

import org.jquantlib.lang.annotation.QualityAssurance;
import org.jquantlib.lang.annotation.QualityAssurance.Quality;
import org.jquantlib.lang.annotation.QualityAssurance.Version;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Weekday;

import static org.jquantlib.time.Month.*;
import static org.jquantlib.time.Weekday.Monday;

/**
 * Mexican calendars. Holidays for the Mexican stock exchange
 * (data from <http://www.bmv.com.mx/>):
 * <ul>
 * <li>Saturdays</li>
 * <li>Sundays</li>
 * <li>New Year's Day, January 1st</li>
 * <li>Constitution Day, first Monday of February (since 2006); February 5th (until 2005)</li>
 * <li>Birthday of Benito Juarez, Monday on/after March 15th (since 2006); March 21st (until 2005)</li>
 * <li>Holy Thursday</li>
 * <li>Good Friday</li>
 * <li>Labour Day, May 1st</li>
 * <li>National Day, September 16th</li>
 * <li>Inauguration Day, October 1st (every 6 years starting in 2024)</li>
 * <li>All Souls Day, November 2nd</li>
 * <li>Revolution Day, third Monday of November (since 2006); November 20th (until 2005)</li>
 * <li>Our Lady of Guadalupe, December 12th</li>
 * <li>Christmas, December 25th</li>
 * </ul>
 *
 * <p>Aligned to C++ QuantLib v1.42.1 ({@code ql/time/calendars/mexico.cpp}).
 * Compared to the legacy 2008-era JQuantLib rules:
 * <ul>
 *   <li>Constitution Day moved to first Monday of February for {@code y >= 2006};</li>
 *   <li>Birthday of Benito Juarez moved to Monday on/after March 15th for {@code y >= 2006};</li>
 *   <li>Inauguration Day added (1 Oct every 6 years starting 2024);</li>
 *   <li>All Souls Day (November 2nd) added;</li>
 *   <li>Revolution Day added (3rd Monday of November since 2006, Nov 20th until 2005).</li>
 * </ul>
 *
 * @author Q Boiler
 * @author Zahid Hussain
 * @author Jose Moya
 * @category calendars
 * @see <a href="http://www.bmv.com.mx/">Bolsa Mexicana de Valores</a>
 */
@QualityAssurance(quality = Quality.Q3_DOCUMENTATION, version = Version.V097, reviewers = { "Zahid Hussain" })
public class Mexico extends Calendar {

    public Mexico() {
        this(Market.BMV);
    }

    public Mexico(final Market m) {
        switch (m) {
        case BMV:
            impl = new BmvImpl();
            break;
        default:
            throw new LibraryException(UNKNOWN_MARKET);
        }
    }

    public enum Market {
        /** Mexican stock exchange */
        BMV
    }

    //
    // private final inner classes
    //

    private final class BmvImpl extends WesternImpl {

        @Override
        public String name() {
            return "Mexican stock exchange";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth();
            final int dd = date.dayOfYear();
            final Month m = date.month();
            final int y = date.year();
            final int em = easterMonday(y);
            return !(isWeekend(w)
                    // New Year's Day
                    || (d == 1 && m == January)
                    // Constitution Day
                    || (y <= 2005 && d == 5 && m == February)
                    || (y >= 2006 && d <= 7 && w == Monday && m == February)
                    // Birthday of Benito Juarez
                    || (y <= 2005 && d == 21 && m == March)
                    || (y >= 2006 && (d >= 15 && d <= 21) && w == Monday && m == March)
                    // Holy Thursday
                    || (dd == em - 4)
                    // Good Friday
                    || (dd == em - 3)
                    // Labour Day
                    || (d == 1 && m == May)
                    // National Day
                    || (d == 16 && m == September)
                    // Inauguration Day
                    || (d == 1 && m == October && y >= 2024 && (y - 2024) % 6 == 0)
                    // All Souls Day
                    || (d == 2 && m == November)
                    // Revolution Day
                    || (y <= 2005 && d == 20 && m == November)
                    || (y >= 2006 && (d >= 15 && d <= 21) && w == Monday && m == November)
                    // Our Lady of Guadalupe
                    || (d == 12 && m == December)
                    // Christmas
                    || (d == 25 && m == December));
        }
    }
}
