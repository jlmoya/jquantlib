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

import static org.jquantlib.time.Month.December;
import static org.jquantlib.time.Month.January;
import static org.jquantlib.time.Month.April;
import static org.jquantlib.time.Month.May;
import static org.jquantlib.time.Month.July;
import static org.jquantlib.time.Month.August;
import static org.jquantlib.time.Month.September;
import static org.jquantlib.time.Month.October;

import org.jquantlib.lang.annotation.QualityAssurance;
import org.jquantlib.lang.annotation.QualityAssurance.Quality;
import org.jquantlib.lang.annotation.QualityAssurance.Version;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Weekday;

/**
 * Costa Rican calendar Holidays for the Costa Rican stock exchange (data from <http://www.bolsacr.com/>):
 * <ul>
 * <li>Saturdays</li>
 * <li>Sundays</li>
 * <li>New Year's Day, JANUARY 1st</li>
 * <li>Rivas' Battle Day, April 11th</li>
 * <li>Holy Thursday</li>
 * <li>Good Friday</li>
 * <li>Labour Day, May 1st</li>
 * <li>Annexion of Guanacaste, July 25th</li>
 * <li>Virgin of the Angels Day, August 2nd</li>
 * <li>Mother's Day, August 15th</li>
 * <li>Independence Day, September 15th</li>
 * <li>Cultures' Day, October 12th</li>
 * <li>Christmas, December 25th</li>
 * </ul>
 *
 * @category calendars
 * @see <a href="http://www.bolsacr.com/">Bolsa Nacional de Valores</a>
 *
 * @author Jose Luis Moya Sobrado
 */

@QualityAssurance(quality = Quality.Q3_DOCUMENTATION, version = Version.V097, reviewers = { "Jose Luis Moya Sobrado" })
public class CostaRica extends Calendar {

    public enum Market {
        /**
         * Costa Rican stock exchange
         */
        BNV
    };

    //
    // public constructors
    //

    public CostaRica() {
        this(Market.BNV);
    }

    public CostaRica(final Market m) {
        switch (m) {
        case BNV:
            impl = new BnvImpl();
            break;
        default:
            throw new LibraryException(UNKNOWN_MARKET);
        }
    }

    //
    // private final inner classes
    //

    private final class BnvImpl extends WesternImpl {

        @Override
        public String name() {
            return "Costa Rican stock exchange";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth(), dd = date.dayOfYear();
            final Month m = date.month();
            final int y = date.year();
            final int em = easterMonday(y);
            if (isWeekend(w)
                    // New Year's Day
                    || (d == 1 && m == January)
                    // Rivas' Battle Day
                    || (d == 11 && m == April)
                    // Holy Thursday
                    || (dd == em - 4)
                    // Good Friday
                    || (dd == em - 3)
                    // Labour Day
                    || (d == 1 && m == May)
                    // Annexion of Guanacaste
                    || (d == 25 && m == July)
                    // Virgin of the Angels Day
                    || (d == 2 && m == August)
                    // Mother's Day
                    || (d == 15 && m == August)
                    // Independence Day
                    || (d == 15 && m == September)
                    // Cultures' Day (moved to next Monday)
                    || ((d >= 12 && d <= 18) && w == Weekday.Monday && m == Month.October)
                    // Christmas
                    || (d == 25 && m == December)) {
                return false;
            }
            return true;
        }
    }
}
