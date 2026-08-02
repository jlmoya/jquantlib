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
import static org.jquantlib.time.Month.February;
import static org.jquantlib.time.Month.January;
import static org.jquantlib.time.Month.May;
import static org.jquantlib.time.Month.November;

import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Weekday;

/**
 * Belgrade stock exchange calendar.
 * <p>
 * Faithful port of {@code ql/time/calendars/serbia.{hpp,cpp}} from
 * QuantLib v1.43 @ {@code 6b57206e04598f092efee66e3b367efc84771995}. This
 * calendar is new in v1.43.
 *
 * @author Jose Moya
 */
public class Serbia extends Calendar {

    public Serbia() {
        this(Market.BSE);
    }

    public Serbia(final Market market) {
        impl = switch (market) {
            case BSE -> new BseImpl();
        };
    }

    /**
     * Belgrade stock exchange markets.
     */
    public enum Market {
        /** Belgrade stock exchange */
        BSE
    }

    private final class BseImpl extends WesternImpl {
        @Override
        public String name() {
            return "Belgrade stock exchange";
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
                    // New Year
                    || (d == 1 && m == January)
                    // New Year Holiday
                    || (d == 2 && m == January)
                    // Serbian Orthodox Christmas
                    || (d == 7 && m == January)
                    // Statehood Day
                    || (d == 15 && m == February)
                    // Statehood Day (2nd)
                    || (d == 16 && m == February)
                    // Statehood Day observed (when 15+16 Feb both fall at a weekend)
                    || ((d == 17 && m == February)
                            && isWeekend(new Date(15, February, y).weekday())
                            && isWeekend(new Date(16, February, y).weekday()))
                    // Good Friday
                    || (dd == em - 3 && y >= 2016)
                    // Easter Monday
                    || (dd == em)
                    // Labour Day
                    || (d == 1 && m == May)
                    // Armistice Day in World War I
                    || (d == 11 && m == November)
                    // Trading system maintenance
                    || (d == 31 && m == December)) {
                return false;
            }
            return true;
        }
    }
}
