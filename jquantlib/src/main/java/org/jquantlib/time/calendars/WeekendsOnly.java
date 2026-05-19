/*
 Copyright (C) 2026 JQuantLib migration contributors.

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

/*
 Copyright (C) 2009 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.time.calendars;

import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;

/**
 * "Weekends only" calendar.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code QuantLib::WeekendsOnly} ({@code ql/time/calendars/weekendsonly.{hpp,cpp}}).
 *
 * <p>This calendar has no bank holidays except for Saturdays and Sundays. It
 * is the default calendar used by {@code MakeCreditDefaultSwap} and shows up in CDS schedule construction (Phase 3c L0
 * A.1) — the C++ {@code WeekendsOnly} treats every Saturday and Sunday as a holiday, with no other exceptions.
 *
 * @category calendars
 */
public class WeekendsOnly extends Calendar {

    public WeekendsOnly() {
        impl = new Impl();
    }

    private final class Impl extends Calendar.WesternImpl {

        @Override
        public String name() {
            return "weekends only";
        }

        @Override
        public boolean isBusinessDay(final Date d) {
            return !isWeekend(d.weekday());
        }
    }
}
