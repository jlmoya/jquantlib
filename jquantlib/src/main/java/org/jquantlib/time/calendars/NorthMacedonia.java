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
import static org.jquantlib.time.Month.October;
import static org.jquantlib.time.Month.September;

import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Weekday;

/**
 * Macedonian Stock Exchange calendar.
 * <p>
 * Faithful port of {@code ql/time/calendars/northmacedonia.{hpp,cpp}} from
 * QuantLib v1.43 @ {@code 6b57206e04598f092efee66e3b367efc84771995}. New in
 * v1.43. Uses the tabulated moon-sighting Islamic holidays — see
 * {@link IslamicHolidays.MoonSightingMethod}.
 *
 * @author Jose Moya
 */
public class NorthMacedonia extends Calendar {

    public NorthMacedonia() {
        this(Market.MSE);
    }

    public NorthMacedonia(final Market market) {
        impl = switch (market) {
            case MSE -> new MseImpl();
        };
    }

    /**
     * Macedonian Stock Exchange markets.
     */
    public enum Market {
        /** Macedonian Stock Exchange */
        MSE
    }

    // C++ declares 'class NorthMacedonia::MseImpl final : public Calendar::OrthodoxImpl'
    // — North Macedonia is an Orthodox country, so Easter Monday follows the
    // Julian/Orthodox computation, not the Western one.
    private final class MseImpl extends OrthodoxImpl {
        @Override
        public String name() {
            return "Macedonian Stock Exchange";
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
                    || IslamicHolidays.MoonSightingMethod.isEidAlFitr(date)
                    || IslamicHolidays.MoonSightingMethod.isEidAlAdha(date)
                    // New Year
                    || (d == 1 && m == January)
                    // Orthodox Christmas
                    || (d == 7 && m == January)
                    // Easter Monday
                    || (dd == em)
                    // Labour Day
                    || (d == 1 && m == May)
                    // Saints Cyril and Methodius Day
                    || (d == 24 && m == May)
                    // Republic Day
                    || (d == 2 && m == August)
                    // Independence Day
                    || (d == 8 && m == September)
                    // Day of the People's Uprising
                    || (d == 11 && m == October)
                    // Day of the Macedonian Revolutionary Struggle
                    || (d == 23 && m == October)
                    // Saint Clement of Ohrid Day
                    || (d == 8 && m == December)) {
                return false;
            }
            return true;
        }
    }
}
