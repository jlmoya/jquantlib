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
import static org.jquantlib.time.Month.March;
import static org.jquantlib.time.Month.May;
import static org.jquantlib.time.Month.October;
import static org.jquantlib.time.Month.September;

import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Weekday;

/**
 * Uzbekistan Stock Exchange calendar.
 * <p>
 * Faithful port of {@code ql/time/calendars/uzbekistan.{hpp,cpp}} from
 * QuantLib v1.43 @ {@code 6b57206e04598f092efee66e3b367efc84771995}. New in
 * v1.43. Uses the tabulated moon-sighting Islamic holidays — see
 * {@link IslamicHolidays.MoonSightingMethod}.
 *
 * @author Jose Moya
 */
public class Uzbekistan extends Calendar {

    public Uzbekistan() {
        this(Market.UZSE);
    }

    public Uzbekistan(final Market market) {
        impl = switch (market) {
            case UZSE -> new Impl2();
        };
    }

    /**
     * Uzbekistan Stock Exchange markets.
     */
    public enum Market {
        /** Uzbekistan Stock Exchange */
        UZSE
    }

    private final class Impl2 extends WesternImpl {
        @Override
        public String name() {
            return "Uzbekistan Stock Exchange";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth();
            final Month m = date.month();
            if (isWeekend(w)
                    || IslamicHolidays.MoonSightingMethod.isEidAlFitr(date)
                    || IslamicHolidays.MoonSightingMethod.isEidAlAdha(date)
                    // New Year's Day
                    || (d == 1 && m == January)
                    // International Women's Day
                    || (d == 8 && m == March)
                    // Navruz (Persian New Year)
                    || (d == 21 && m == March)
                    // Day of Remembrance and Honors
                    || (d == 9 && m == May)
                    // Independence Day
                    || (d == 1 && m == September)
                    // Teachers Day
                    || (d == 1 && m == October)
                    // Constitution Day
                    || (d == 8 && m == December)) {
                return false;
            }
            return true;
        }
    }
}
