/*
 Copyright (C) 2008 Tim Swetonic

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
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Weekday;

import static org.jquantlib.time.Month.*;
import static org.jquantlib.time.Weekday.Monday;
import static org.jquantlib.time.Weekday.Tuesday;

/**
 * Australian calendar
 * <p>
 * Faithful port of {@code ql/time/calendars/australia.{hpp,cpp}} from C++
 * QuantLib v1.43.
 * <p>
 * Holidays:
 * <ul>
 * <li>Saturdays</li>
 * <li>Sundays</li>
 * <li>New Year's Day, January 1st (possibly moved to Monday)</li>
 * <li>Australia Day, January 26th (possibly moved to Monday)</li>
 * <li>Good Friday</li>
 * <li>Easter Monday</li>
 * <li>ANZAC Day, April 25th</li>
 * <li>Queen's Birthday, second Monday in June</li>
 * <li>Bank Holiday, first Monday in August</li>
 * <li>Labour Day, first Monday in October</li>
 * <li>Christmas, December 25th (possibly moved to Monday or Tuesday)</li>
 * <li>Boxing Day, December 26th (possibly moved to Monday or Tuesday)</li>
 * <li>National Day of Mourning for Her Majesty, September 22, 2022</li>
 * </ul>
 *
 * @author Tim Swetonic
 * @author Richard Gomes
 * @author Jose Moya
 *
 */
@QualityAssurance( quality = Quality.Q3_DOCUMENTATION, version = Version.V097, reviewers = { "Zahid Hussain" } )

public class Australia extends Calendar {

    //
    // public constructors
    //

    public Australia() {
        impl = new SettlementImpl();
    }

    //
    // private final inner classes
    //

    /**
     * Port of C++ {@code Australia::SettlementImpl}
     * (ql/time/calendars/australia.cpp:41-75).
     */
    private final class SettlementImpl extends WesternImpl {

        @Override
        public String name() {
            return "Australia settlement";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth(), dd = date.dayOfYear();
            final Month m = date.month();
            final int y = date.year();
            final int em = easterMonday(y);
            return !isWeekend(w)
                    // New Year's Day (possibly moved to Monday)
                    && ((d != 1 && ((d != 2 && d != 3) || w != Monday)) || m != January)
                    // Australia Day, January 26th (possibly moved to Monday)
                    && ((d != 26 && ((d != 27 && d != 28) || w != Monday)) || m != January)
                    // Good Friday
                    && (dd != em - 3)
                    // Easter Monday
                    && (dd != em)
                    // ANZAC Day, April 25th
                    && (d != 25 || m != April)
                    // Queen's Birthday, second Monday in June
                    && ((d <= 7 || d > 14) || w != Monday || m != June)
                    // Bank Holiday, first Monday in August
                    && (d > 7 || w != Monday || m != August)
                    // Labour Day, first Monday in October
                    && (d > 7 || w != Monday || m != October)
                    // Christmas, December 25th (possibly Monday or Tuesday)
                    && ((d != 25 && (d != 27 || (w != Monday && w != Tuesday))) || m != December)
                    // Boxing Day, December 26th (possibly Monday or Tuesday)
                    && ((d != 26 && (d != 28 || (w != Monday && w != Tuesday))) || m != December)
                    // National Day of Mourning for Her Majesty, September 22 (only 2022)
                    && (d != 22 || m != September || y != 2022);
        }
    }

}
