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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jquantlib.time.Date;
import org.jquantlib.time.Month;

/**
 * Islamic holiday date tables, new in C++ QuantLib v1.43.
 * <p>
 * Faithful port of {@code ql/time/calendars/islamicholidays.{hpp,cpp}} from
 * QuantLib v1.43 @ {@code 6b57206e04598f092efee66e3b367efc84771995}.
 *
 * <p>C++ exposes these as free functions in the
 * {@code QuantLib::MoonSightingMethod} namespace; Java models the namespace as
 * a final utility class. Eid al-Fitr and Eid al-Adha are moon-sighting based
 * and cannot be computed arithmetically, so upstream tabulates them — here for
 * 2026-2040, exactly as C++ does.
 *
 * @author Jose Moya
 */
public final class IslamicHolidays {

    private IslamicHolidays() {
        throw new AssertionError("no instances");
    }

    /**
     * Moon-sighting calendar, used across South Asia, Central Asia, the Middle
     * East and North Africa. (Saudi Arabia and some Gulf states instead use the
     * Umm al-Qura calendar, which C++ v1.43 does not tabulate here.)
     */
    public static final class MoonSightingMethod {

        private MoonSightingMethod() {
            throw new AssertionError("no instances");
        }

        private static final Set<Date> EID_AL_FITR = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            new Date(20, Month.March, 2026),
            new Date(10, Month.March, 2027),
            new Date(27, Month.February, 2028),
            new Date(15, Month.February, 2029),
            new Date(5, Month.February, 2030),
            new Date(25, Month.January, 2031),
            new Date(14, Month.January, 2032),
            new Date(2, Month.January, 2033),
            new Date(23, Month.December, 2033),
            new Date(12, Month.December, 2034),
            new Date(1, Month.December, 2035),
            new Date(19, Month.November, 2036),
            new Date(8, Month.November, 2037),
            new Date(29, Month.October, 2038),
            new Date(19, Month.October, 2039),
            new Date(7, Month.October, 2040))));

        private static final Set<Date> EID_AL_ADHA = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            new Date(27, Month.May, 2026),
            new Date(17, Month.May, 2027),
            new Date(5, Month.May, 2028),
            new Date(24, Month.April, 2029),
            new Date(13, Month.April, 2030),
            new Date(3, Month.April, 2031),
            new Date(22, Month.March, 2032),
            new Date(11, Month.March, 2033),
            new Date(28, Month.February, 2034),
            new Date(18, Month.February, 2035),
            new Date(7, Month.February, 2036),
            new Date(27, Month.January, 2037),
            new Date(16, Month.January, 2038),
            new Date(5, Month.January, 2039),
            new Date(26, Month.December, 2039),
            new Date(15, Month.December, 2040))));

        public static boolean isEidAlFitr(final Date d) {
            return EID_AL_FITR.contains(d);
        }

        public static boolean isEidAlAdha(final Date d) {
            return EID_AL_ADHA.contains(d);
        }
    }
}
