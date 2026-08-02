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
import static org.jquantlib.time.Month.October;

import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Weekday;

/**
 * Ljubljana stock exchange calendar.
 * <p>
 * Faithful port of {@code ql/time/calendars/slovenia.{hpp,cpp}} from
 * QuantLib v1.43 @ {@code 6b57206e04598f092efee66e3b367efc84771995}. This
 * calendar is new in v1.43.
 *
 * @author Jose Moya
 */
public class Slovenia extends Calendar {

    public Slovenia() {
        this(Market.LSE);
    }

    public Slovenia(final Market market) {
        impl = switch (market) {
            case LSE -> new LseImpl();
        };
    }

    /**
     * Ljubljana stock exchange markets.
     */
    public enum Market {
        /** Ljubljana stock exchange */
        LSE
    }

    private final class LseImpl extends WesternImpl {
        @Override
        public String name() {
            return "Ljubljana stock exchange";
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
                    // New Year's Holiday
                    || (d == 2 && m == January)
                    // Good Friday
                    || (dd == em - 3)
                    // Easter Monday
                    || (dd == em)
                    // May Day
                    || (d == 1 && m == May)
                    // May Day Holiday
                    || (d == 2 && m == May)
                    // Statehood Day
                    || (d == 25 && m == June)
                    // Assumption of Mary
                    || (d == 15 && m == August)
                    // Reformation Day
                    || (d == 31 && m == October)
                    // Christmas Eve
                    || (d == 24 && m == December)
                    // Christmas
                    || (d == 25 && m == December)
                    // St. Stephen
                    || (d == 26 && m == December)
                    // New Year's Eve
                    || (d == 31 && m == December)) {
                return false;
            }
            return true;
        }
    }
}
