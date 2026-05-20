/*
 Copyright (C) 2025 JQuantLib Migration Project

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
 Copyright (C) 2015 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.daycounters;

import org.jquantlib.time.Date;

/**
 * 30/365 day count convention.
 *
 * <p>Port of v1.42.1 {@code ql/time/daycounters/thirty365.{hpp,cpp}}. The day
 * adjustment rule matches ISO 20022: if either {@code dayOfMonth} is 31, it is
 * collapsed to 30 before the count {@code 360*(yy2-yy1) + 30*(mm2-mm1) + (dd2-dd1)}.
 * Year fraction is {@code dayCount/365.0}.
 */
public class Thirty365 extends DayCounter {

    public Thirty365() {
        super.impl = new Impl();
    }

    private final class Impl extends DayCounter.Impl {

        @Override
        public String name() /* @ReadOnly */ {
            return "30/365";
        }

        @Override
        protected long dayCount(final Date d1, final Date d2) /* @ReadOnly */ {
            int dd1 = d1.dayOfMonth();
            int dd2 = d2.dayOfMonth();
            final int mm1 = d1.month().value();
            final int mm2 = d2.month().value();
            final int yy1 = d1.year();
            final int yy2 = d2.year();

            // date adjustment rules as in ISO 20022
            if (dd1 == 31) { dd1 = 30; }
            if (dd2 == 31) { dd2 = 30; }

            return 360L * (yy2 - yy1) + 30L * (mm2 - mm1) + (dd2 - dd1);
        }

        @Override
        public /*@Time*/ double yearFraction(final Date d1, final Date d2,
                final Date refPeriodStart, final Date refPeriodEnd) /* @ReadOnly */ {
            return dayCount(d1, d2) / 365.0;
        }
    }
}
