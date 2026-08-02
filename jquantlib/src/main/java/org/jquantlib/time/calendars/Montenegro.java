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

import static org.jquantlib.time.Month.January;
import static org.jquantlib.time.Month.July;
import static org.jquantlib.time.Month.May;
import static org.jquantlib.time.Month.November;

import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Weekday;

/**
 * Montenegro Stock Exchange calendar.
 * <p>
 * Faithful port of {@code ql/time/calendars/montenegro.{hpp,cpp}} from
 * QuantLib v1.43 @ {@code 6b57206e04598f092efee66e3b367efc84771995}. This
 * calendar is new in v1.43.
 *
 * @author Jose Moya
 */
public class Montenegro extends Calendar {

    public Montenegro() {
        this(Market.MNSE);
    }

    public Montenegro(final Market market) {
        impl = switch (market) {
            case MNSE -> new MnseImpl();
        };
    }

    /**
     * Montenegro Stock Exchange markets.
     */
    public enum Market {
        /** Montenegro Stock Exchange */
        MNSE
    }

    private final class MnseImpl extends WesternImpl {
        @Override
        public String name() {
            return "Montenegro Stock Exchange";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth();
            final Month m = date.month();
            if (isWeekend(w)
                    // New Year's Day
                    || (d == 1 && m == January)
                    // New Year Holiday
                    || (d == 2 && m == January)
                    // Labour Day
                    || (d == 1 && m == May)
                    // Labour Day Holiday
                    || (d == 2 && m == May)
                    // Independence Day
                    || (d == 21 && m == May)
                    // Independence Day Holiday
                    || (d == 22 && m == May)
                    // Statehood Day
                    || (d == 13 && m == July)
                    // Statehood Day Holiday
                    || (d == 14 && m == July)
                    // Njegos Day
                    || (d == 13 && m == November)
                    // Njegos Day Holiday
                    || (d == 14 && m == November)) {
                return false;
            }
            return true;
        }
    }
}
