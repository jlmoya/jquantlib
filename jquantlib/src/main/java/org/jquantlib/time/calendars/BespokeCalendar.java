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

/*
 Copyright (C) 2008 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.time.calendars;

import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Weekday;

/**
 * Bespoke calendar.
 * <p>
 * This calendar has no predefined set of business days. Holidays and weekdays
 * can be defined by means of the provided interface.
 * <p>
 * Phase 5e.5b-CFC-d-14: ported from C++ v1.42.1
 * ql/time/calendars/bespokecalendar.{hpp,cpp}.
 * <p>
 * Note on linked-instances semantics: the C++ port keeps a {@code shared_ptr}
 * to a single {@code BespokeCalendar::Impl} so that copies remain linked. Java
 * does not have copy constructors; each {@code new BespokeCalendar(name)}
 * carries its own {@code Impl}. Passing the same instance around (the common
 * use case in test-suite/indexes.cpp) preserves identity.
 *
 * @author Jose Moya
 */
public class BespokeCalendar extends Calendar {

    /**
     * Constructs a BespokeCalendar with no holidays and no weekends.
     * <p>
     * <b>Warning:</b> different bespoke calendars created with the same name
     * (or different bespoke calendars created with no name) will compare as
     * equal under {@link Calendar#eq(Calendar, Calendar)} (mirrors C++
     * v1.42.1 ql/time/calendars/bespokecalendar.hpp:55-58).
     *
     * @param name the calendar name; defaults to empty string
     */
    public BespokeCalendar(final String name) {
        this.impl = new Impl(name);
    }

    /**
     * Constructs an unnamed BespokeCalendar.
     */
    public BespokeCalendar() {
        this("");
    }

    /**
     * Marks the passed day as part of the weekend.
     * <p>
     * Mirrors C++ v1.42.1 ql/time/calendars/bespokecalendar.cpp:51-53.
     */
    public void addWeekend(final Weekday w) {
        ((Impl) impl).addWeekend(w);
    }

    //
    // private inner class
    //

    private final class Impl extends Calendar.Impl {

        private final String name;
        // Bitmask: bit i (1-based, Sunday=1..Saturday=7) is set when that
        // weekday is part of the weekend. Mirrors C++ v1.42.1
        // ql/time/calendars/bespokecalendar.hpp:48-52 (unsigned int weekend_mask_).
        private int weekendMask = 0;

        Impl(final String name) {
            this.name = (name == null) ? "" : name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean isWeekend(final Weekday w) {
            return (weekendMask & (1 << w.value())) != 0;
        }

        @Override
        public boolean isBusinessDay(final Date d) {
            return !isWeekend(d.weekday());
        }

        void addWeekend(final Weekday w) {
            weekendMask |= (1 << w.value());
        }
    }
}
