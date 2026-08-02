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
import static org.jquantlib.time.Month.February;
import static org.jquantlib.time.Month.January;
import static org.jquantlib.time.Month.June;
import static org.jquantlib.time.Month.March;
import static org.jquantlib.time.Month.May;
import static org.jquantlib.time.Month.September;

import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Weekday;

/**
 * Malta Stock Exchange calendar.
 * <p>
 * Faithful port of {@code ql/time/calendars/malta.{hpp,cpp}} from
 * QuantLib v1.43 @ {@code 6b57206e04598f092efee66e3b367efc84771995}. This
 * calendar is new in v1.43.
 *
 * @author Jose Moya
 */
public class Malta extends Calendar {

    public Malta() {
        this(Market.MSE);
    }

    public Malta(final Market market) {
        impl = switch (market) {
            case MSE -> new MseImpl();
        };
    }

    /**
     * Malta Stock Exchange markets.
     */
    public enum Market {
        /** Malta Stock Exchange */
        MSE
    }

    private final class MseImpl extends WesternImpl {
        @Override
        public String name() {
            return "Malta Stock Exchange";
        }

        /**
         * C++ {@code Malta::MseImpl::isWeekend} overrides the Western
         * Saturday+Sunday weekend with Friday+Saturday, even though the impl
         * derives from {@code Calendar::WesternImpl}. That is almost certainly
         * an upstream quirk — Malta observes a Saturday/Sunday weekend — but
         * v1.43 defines it this way and C++ is the ground truth, so the port
         * mirrors it exactly.
         */
        @Override
        public boolean isWeekend(final Weekday w) {
            return w == Weekday.Friday || w == Weekday.Saturday;
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
                    // St. Paul's Shipwreck
                    || (d == 10 && m == February)
                    // St. Joseph's Day
                    || (d == 19 && m == March)
                    // Freedom Day
                    || (d == 31 && m == March)
                    // Good Friday
                    || (dd == em - 3)
                    // Easter Monday (exchange holiday)
                    || (dd == em)
                    // Labour Day
                    || (d == 1 && m == May)
                    // Imnarja (Sts Peter & Paul)
                    || (d == 29 && m == June)
                    // Assumption of Mary
                    || (d == 15 && m == August)
                    // Our Lady of Victories
                    || (d == 8 && m == September)
                    // Independence Day
                    || (d == 21 && m == September)
                    // Immaculate Conception
                    || (d == 8 && m == December)
                    // Republic Day
                    || (d == 13 && m == December)
                    // Christmas Vigil
                    || (d == 24 && m == December)
                    // Christmas Day
                    || (d == 25 && m == December)
                    // Boxing Day
                    || (d == 26 && m == December)
                    // New Year's Eve (non-trading)
                    || (d == 31 && m == December)) {
                return false;
            }
            return true;
        }
    }
}
